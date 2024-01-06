package com.github.mongobat;

import com.github.mongobat.changeset.ChangeEntry;
import com.github.mongobat.changeset.ChangeSet;
import com.github.mongobat.changeset.ChangeStatus;
import com.github.mongobat.dao.ChangeEntryDao;
import com.github.mongobat.exception.MongoBatChangeSetException;
import com.github.mongobat.exception.MongoBatConfigurationException;
import com.github.mongobat.exception.MongoBatConnectionException;
import com.github.mongobat.exception.MongoBatException;
import com.github.mongobat.utils.ChangeService;
import com.github.mongobat.utils.Environment;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

import static com.github.mongobat.utils.StringUtils.hasText;

/**
 * Mongobee runner
 *
 * @author lstolowski
 * @since 26/07/2014
 */
public class MongoBat {
  private static final Logger log = LoggerFactory.getLogger(MongoBat.class);

  private static final String DEFAULT_CHANGELOG_COLLECTION_NAME = "dbchangelog";
  private static final String DEFAULT_LOCK_COLLECTION_NAME = "mongobatlock";
  private static final boolean DEFAULT_WAIT_FOR_LOCK = false;
  private static final long DEFAULT_CHANGE_LOG_LOCK_WAIT_TIME = 5L;
  private static final long DEFAULT_CHANGE_LOG_LOCK_POLL_RATE = 10L;
  private static final boolean DEFAULT_THROW_EXCEPTION_IF_CANNOT_OBTAIN_LOCK = false;
  private static final String FAILED_CHANGE_ID_TEMPLATE = "%s (failed, %s)";

  private ChangeEntryDao dao;

  private boolean enabled = true;
  private List<String> changeLogsScanPackages;
  private MongoClient mongoClient;
  private String dbName;
  private String environment = Environment.ANY;

  private Map<Class<?>, Object> changeSetMethodParams = Map.of();

  /**
   * <p>Constructor takes db.mongodb.MongoClient object as a parameter.
   * </p><p>For more details about <tt>MongoClient</tt> please see com.mongodb.MongoClient docs
   * </p>
   *
   * @param mongoClient database connection client
   * @see MongoClient
   */
  public MongoBat(MongoClient mongoClient) {
    this(mongoClient, String.valueOf(new Date().getTime()));
  }

  public MongoBat(MongoClient mongoClient, String installationId) {
    this.mongoClient = mongoClient;
    this.dao = createChangeEntryDao();
    this.dao.setInstallationId(installationId);
  }

  private ChangeEntryDao createChangeEntryDao() {
    return new ChangeEntryDao(DEFAULT_CHANGELOG_COLLECTION_NAME, DEFAULT_LOCK_COLLECTION_NAME, DEFAULT_WAIT_FOR_LOCK,
        DEFAULT_CHANGE_LOG_LOCK_WAIT_TIME, DEFAULT_CHANGE_LOG_LOCK_POLL_RATE, DEFAULT_THROW_EXCEPTION_IF_CANNOT_OBTAIN_LOCK);
  }

  public ExecutionReport executeSingle(ChangeEntry changeEntry) throws MongoBatException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, InstantiationException {
    ExecutionReport report = new ExecutionReport(dao.getInstallationId());
    if (!isEnabled()) {
      log.info("Mongobee is disabled. Exiting.");
      return null;
    }

    validateConfig();

    dao.connectMongoDb(this.mongoClient, dbName);

    if (!dao.acquireProcessLock()) {
      log.info("Mongobee did not acquire process lock. Exiting.");
      return null;
    }

    log.info("Mongobee acquired process lock, starting the data migration sequence..");

    try {
      Class<?> changeLogClass = Class.forName(changeEntry.getChangeLogClass());
      Method method = Arrays.stream(changeLogClass.getDeclaredMethods())
          .filter(m -> m.getName().equals(changeEntry.getChangeSetMethodName()))
          .findFirst()
          .orElseThrow(() -> new MongoBatException("No method " + changeEntry.getChangeSetMethodName() + " found."));

      ChangeSet changeSet = method.getAnnotation(ChangeSet.class);
      if (!changeSet.environment().equals(this.environment) && !Environment.ANY.equals(this.environment) && !Environment.ANY.equals(changeSet.environment())) {
        throw new MongoBatException(changeEntry.getChangeId() + " can be executed only on " + changeSet.environment() + " environment");
      }

      Object changeLogInstance = changeLogClass.getConstructor().newInstance();
      report.addScanned();

      if (dao.isNewChange(changeEntry)) {
        executeChangeSetMethod(method, changeLogInstance);
        dao.save(changeEntry);
        report.addExecuted();
        log.info("{} applied", changeEntry);
      } else if (changeSet.repeatable()) {
        executeChangeSetMethod(method, changeLogInstance);
        dao.save(changeEntry);
        report.addReExecuted();
        log.info("{} reapplied", changeEntry);
      } else {
        throw new MongoBatChangeSetException("Changeset " + changeEntry.getChangeId() + " cannot be executed");
      }

    } catch (MongoBatException e) {
      report.addFailed();
      log.error(e.getMessage(), e);
      dao.save(prepareFailedChangeEntry(changeEntry, e));
    } finally {
      log.info("Mongobee is releasing process lock.");
      dao.releaseProcessLock();
    }

    log.info("Mongobee has finished his job.");
    return report;
  }

  /**
   * Executing migration
   *
   * @throws MongoBatException exception
   */
  public ExecutionReport execute() throws MongoBatException {
    if (!isEnabled()) {
      log.info("Mongobee is disabled. Exiting.");
      return null;
    }

    validateConfig();

    dao.connectMongoDb(this.mongoClient, dbName);

    if (!dao.acquireProcessLock()) {
      log.info("Mongobee did not acquire process lock. Exiting.");
      return null;
    }

    log.info("Mongobee acquired process lock, starting the data migration sequence..");

    ExecutionReport report;
    try {
      report = executeMigration();
    } finally {
      log.info("Mongobee is releasing process lock.");
      dao.releaseProcessLock();
    }

    log.info("Mongobee has finished his job.");
    return report;
  }

  private ExecutionReport executeMigration() throws MongoBatException {
    ExecutionReport report = new ExecutionReport(dao.getInstallationId());
    for (String scanPackage : changeLogsScanPackages) {
      ExecutionReport migrationReport = executeMigration(scanPackage);
      report.merge(migrationReport);
    }
    return report;
  }

  private ExecutionReport executeMigration(String changeLogsScanPackage) throws MongoBatException {
    ExecutionReport report = new ExecutionReport(dao.getInstallationId());
    ChangeService service = new ChangeService(changeLogsScanPackage);

    for (Class<?> changelogClass : service.fetchChangeLogs()) {

      Object changelogInstance;
      try {
        changelogInstance = changelogClass.getConstructor().newInstance();
        List<Method> changesetMethods = service.fetchChangeSets(changelogInstance.getClass());
        report.addScanned(changesetMethods.size());

        for (Method changesetMethod : changesetMethods) {
          ChangeEntry changeEntry = service.createChangeEntry(changesetMethod);

          try {
            if (!changeEntry.getEnvironment().equals(this.environment) && !Environment.ANY.equals(this.environment) && !Environment.ANY.equals(changeEntry.getEnvironment())) {
              log.info("{} skipped (wrong environment)", changeEntry);
              report.addSkipped();
              continue;
            }
            if (dao.isNewChange(changeEntry)) {
              if (!service.isPostponed(changesetMethod)) {
                executeChangeSetMethod(changesetMethod, changelogInstance);
                report.addExecuted();
                log.info("{} applied", changeEntry);
              } else {
                report.addPostponed();
                log.info("{} postponed", changeEntry);
              }
              dao.save(changeEntry);
            } else if (service.isRunAlwaysChangeSet(changesetMethod) && service.isRepeatable(changesetMethod) && !service.isPostponed(changesetMethod)) {
              executeChangeSetMethod(changesetMethod, changelogInstance);
              dao.save(changeEntry);
              report.addReExecuted();
              log.info("{} reapplied", changeEntry);
            } else {
              report.addSkipped();
              log.info("{} passed over", changeEntry);
            }
          } catch (MongoBatChangeSetException e) {
            report.addFailed();
            log.error(e.getMessage(), e);
            dao.save(prepareFailedChangeEntry(changeEntry, e));
          }
        }
      } catch (NoSuchMethodException | IllegalAccessException | InstantiationException e) {
        throw new MongoBatException(e.getMessage(), e);
      } catch (InvocationTargetException e) {
        Throwable targetException = e.getTargetException();
        throw new MongoBatException(targetException.getMessage(), e);
      }

    }

    return report;
  }

  private Object executeChangeSetMethod(Method changeSetMethod, Object changeLogInstance)
      throws IllegalAccessException, InvocationTargetException, MongoBatChangeSetException {

    if (changeSetMethod.getParameterCount() == 0) {
      log.debug("method with no params");
      return changeSetMethod.invoke(changeLogInstance);
    }

    return changeSetMethod.invoke(changeLogInstance, getParameters(changeSetMethod));
  }

  private Object[] getParameters(Method changeSetMethod) throws MongoBatChangeSetException {
    Object[] parameters = new Object[changeSetMethod.getParameterCount()];

    for (int i = 0; i < changeSetMethod.getParameterCount(); i++) {
      Class<?> type = changeSetMethod.getParameterTypes()[i];
      if (type.equals(MongoDatabase.class)) {
        parameters[i] = dao.getMongoDatabase();
      } else if (changeSetMethodParams.containsKey(type)) {
        parameters[i] = changeSetMethodParams.get(type);
      } else {
        throw new MongoBatChangeSetException("ChangeSet method " + changeSetMethod.getName() +
            " has wrong arguments list. Unsupported type: " + type.getSimpleName());
      }
    }

    String paramsTypes = Arrays.stream(parameters)
        .map(o -> o.getClass().getSimpleName())
        .collect(Collectors.joining(", "));
    log.debug("method with arguments: {}", paramsTypes);

    return parameters;
  }

  private void validateConfig() throws MongoBatConfigurationException {
    if (!hasText(dbName)) {
      throw new MongoBatConfigurationException("DB name is not set. It should be defined in MongoDB URI or via setter");
    }
    if (changeLogsScanPackages == null || changeLogsScanPackages.isEmpty()) {
      throw new MongoBatConfigurationException("Scan package for changelogs is not set: use appropriate setter");
    }
    if (this.mongoClient == null) {
      throw new MongoBatConfigurationException("MongoClient is not set");
    }
  }

  private ChangeEntry prepareFailedChangeEntry(ChangeEntry entry, Exception exception) {
    String error = Optional.ofNullable(exception.getCause())
        .map(Object::toString)
        .orElse(exception.getMessage());

    String changeId = String.format(FAILED_CHANGE_ID_TEMPLATE, entry.getChangeId(), new Date().getTime());
    ChangeEntry result = new ChangeEntry(changeId, entry);
    result.setStatus(ChangeStatus.FAILED);
    result.setError(error);
    result.setOriginalChangeId(entry.getChangeId());
    return result;
  }

  /**
   * @return true if an execution is in progress, in any process.
   * @throws MongoBatConnectionException exception
   */
  public boolean isExecutionInProgress() throws MongoBatConnectionException {
    return dao.isProccessLockHeld();
  }

  /**
   * Used DB name should be set here
   *
   * @param dbName database name
   * @return Mongobee object for fluent interface
   */
  public MongoBat setDbName(String dbName) {
    this.dbName = dbName;
    return this;
  }

  /**
   * Initialize environment type. Only {@link com.github.mongobat.changeset.ChangeSet} with environment marked as ANY (default) or the same value will be executed
   *
   * @param environment environment type
   * @return Mongobee object for fluent interface
   */
  public MongoBat setEnvironment(String environment) {
    this.environment = environment;
    return this;
  }

  /**
   * Optional params for ChangeSet methods
   *
   * @param changeSetMethodParams instances of additional params which can be used in ChangeSet methods
   * @return Mongobee object for fluent interface
   */
  public MongoBat setChangeSetMethodParams(Map<Class<?>, Object> changeSetMethodParams) {
    this.changeSetMethodParams = Map.copyOf(changeSetMethodParams);
    return this;
  }

  /**
   * Package name where @ChangeLog-annotated classes are kept.
   *
   * @param changeLogsScanPackage package where your changelogs are
   * @return Mongobee object for fluent interface
   */
  public MongoBat setChangeLogsScanPackage(String changeLogsScanPackage) {
    this.changeLogsScanPackages = List.of(changeLogsScanPackage);
    return this;
  }

  public MongoBat setChangeLogsScanPackages(List<String> changeLogsScanPackage) {
    this.changeLogsScanPackages = changeLogsScanPackage;
    return this;
  }

  /**
   * @return true if Mongobee runner is enabled and able to run, otherwise false
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Feature which enables/disables Mongobee runner execution
   *
   * @param enabled MOngobee will run only if this option is set to true
   * @return Mongobee object for fluent interface
   */
  public MongoBat setEnabled(boolean enabled) {
    this.enabled = enabled;
    return this;
  }

  /**
   * Feature which enables/disables waiting for lock if it's already obtained
   *
   * @param waitForLock Mongobee will be waiting for lock if it's already obtained if this option is set to true
   * @return Mongobee object for fluent interface
   */
  public MongoBat setWaitForLock(boolean waitForLock) {
    this.dao.setWaitForLock(waitForLock);
    return this;
  }

  /**
   * Waiting time for acquiring lock if waitForLock is true
   *
   * @param changeLogLockWaitTime Waiting time in minutes for acquiring lock
   * @return Mongobee object for fluent interface
   */
  public MongoBat setChangeLogLockWaitTime(long changeLogLockWaitTime) {
    this.dao.setChangeLogLockWaitTime(changeLogLockWaitTime);
    return this;
  }

  /**
   * Poll rate for acquiring lock if waitForLock is true
   *
   * @param changeLogLockPollRate Poll rate in seconds for acquiring lock
   * @return Mongobee object for fluent interface
   */
  public MongoBat setChangeLogLockPollRate(long changeLogLockPollRate) {
    this.dao.setChangeLogLockPollRate(changeLogLockPollRate);
    return this;
  }

  /**
   * Feature which enables/disables throwing MongobeeLockException if Mongobee can not obtain lock
   *
   * @param throwExceptionIfCannotObtainLock Mongobee will throw MongobeeLockException if lock can not be obtained
   * @return Mongobee object for fluent interface
   */
  public MongoBat setThrowExceptionIfCannotObtainLock(boolean throwExceptionIfCannotObtainLock) {
    this.dao.setThrowExceptionIfCannotObtainLock(throwExceptionIfCannotObtainLock);
    return this;
  }

  /**
   * Overwrites a default mongobee changelog collection hardcoded in DEFAULT_CHANGELOG_COLLECTION_NAME.
   * <p>
   * CAUTION! Use this method carefully - when changing the name on a existing system,
   * your changelogs will be executed again on your MongoDB instance
   *
   * @param changelogCollectionName a new changelog collection name
   * @return Mongobee object for fluent interface
   */
  public MongoBat setChangelogCollectionName(String changelogCollectionName) {
    this.dao.setChangelogCollectionName(changelogCollectionName);
    return this;
  }

  /**
   * Overwrites a default mongobee lock collection hardcoded in DEFAULT_LOCK_COLLECTION_NAME
   *
   * @param lockCollectionName a new lock collection name
   * @return Mongobee object for fluent interface
   */
  public MongoBat setLockCollectionName(String lockCollectionName) {
    this.dao.setLockCollectionName(lockCollectionName);
    return this;
  }

  /**
   * Closes the Mongo instance used by Mongobee.
   * This will close either the connection Mongobee was initiated with or that which was internally created.
   */
  public void close() {
    dao.close();
  }
}
