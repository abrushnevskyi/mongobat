package com.github.mongobee;

import com.github.mongobee.changeset.ChangeEntry;
import com.github.mongobee.changeset.ChangeSet;
import com.github.mongobee.dao.ChangeEntryDao;
import com.github.mongobee.exception.MongobeeChangeSetException;
import com.github.mongobee.exception.MongobeeConfigurationException;
import com.github.mongobee.exception.MongobeeConnectionException;
import com.github.mongobee.exception.MongobeeException;
import com.github.mongobee.utils.ChangeService;
import com.github.mongobee.utils.Environment;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.github.mongobee.utils.StringUtils.hasText;

/**
 * Mongobee runner
 *
 * @author lstolowski
 * @since 26/07/2014
 */
public class Mongobee {
  private static final Logger logger = LoggerFactory.getLogger(Mongobee.class);

  private static final String DEFAULT_CHANGELOG_COLLECTION_NAME = "dbchangelog";
  private static final String DEFAULT_LOCK_COLLECTION_NAME = "mongobeelock";
  private static final boolean DEFAULT_WAIT_FOR_LOCK = false;
  private static final long DEFAULT_CHANGE_LOG_LOCK_WAIT_TIME = 5L;
  private static final long DEFAULT_CHANGE_LOG_LOCK_POLL_RATE = 10L;
  private static final boolean DEFAULT_THROW_EXCEPTION_IF_CANNOT_OBTAIN_LOCK = false;

  private ChangeEntryDao dao;

  private boolean enabled = true;
  private String changeLogsScanPackage;
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
  public Mongobee(MongoClient mongoClient) {
    this.mongoClient = mongoClient;
    this.dao = new ChangeEntryDao(DEFAULT_CHANGELOG_COLLECTION_NAME, DEFAULT_LOCK_COLLECTION_NAME, DEFAULT_WAIT_FOR_LOCK,
        DEFAULT_CHANGE_LOG_LOCK_WAIT_TIME, DEFAULT_CHANGE_LOG_LOCK_POLL_RATE, DEFAULT_THROW_EXCEPTION_IF_CANNOT_OBTAIN_LOCK);
  }

  /**
   * Executing migration
   *
   * @throws MongobeeException exception
   */
  public void execute() throws MongobeeException {
    if (!isEnabled()) {
      logger.info("Mongobee is disabled. Exiting.");
      return;
    }

    validateConfig();

    dao.connectMongoDb(this.mongoClient, dbName);

    if (!dao.acquireProcessLock()) {
      logger.info("Mongobee did not acquire process lock. Exiting.");
      return;
    }

    logger.info("Mongobee acquired process lock, starting the data migration sequence..");

    try {
      executeMigration();
    } finally {
      logger.info("Mongobee is releasing process lock.");
      dao.releaseProcessLock();
    }

    logger.info("Mongobee has finished his job.");
  }

  private void executeMigration() throws MongobeeException {

    ChangeService service = new ChangeService(changeLogsScanPackage);

    for (Class<?> changelogClass : service.fetchChangeLogs()) {

      Object changelogInstance;
      try {
        changelogInstance = changelogClass.getConstructor().newInstance();
        List<Method> changesetMethods = service.fetchChangeSets(changelogInstance.getClass());

        for (Method changesetMethod : changesetMethods) {
          ChangeEntry changeEntry = service.createChangeEntry(changesetMethod);

          try {
            if (!changeEntry.getEnvironment().equals(this.environment) && !Environment.ANY.equals(this.environment) && !Environment.ANY.equals(changeEntry.getEnvironment())) {
              logger.info(changeEntry + " skipped (wrong environment)");
              continue;
            }
            if (dao.isNewChange(changeEntry)) {
              if (!service.isPostponed(changesetMethod)) {
                executeChangeSetMethod(changesetMethod, changelogInstance);
              }
              dao.save(changeEntry);
              logger.info(changeEntry + " applied");
            } else if (service.isRunAlwaysChangeSet(changesetMethod) && service.isRepeatable(changesetMethod) && !service.isPostponed(changesetMethod)) {
              executeChangeSetMethod(changesetMethod, changelogInstance);
              logger.info(changeEntry + " reapplied");
            } else {
              logger.info(changeEntry + " passed over");
            }
          } catch (MongobeeChangeSetException e) {
            logger.error(e.getMessage());
          }
        }
      } catch (NoSuchMethodException | IllegalAccessException | InstantiationException e) {
        throw new MongobeeException(e.getMessage(), e);
      } catch (InvocationTargetException e) {
        Throwable targetException = e.getTargetException();
        throw new MongobeeException(targetException.getMessage(), e);
      }

    }
  }

  private Object executeChangeSetMethod(Method changeSetMethod, Object changeLogInstance)
      throws IllegalAccessException, InvocationTargetException, MongobeeChangeSetException {

    if (changeSetMethod.getParameterCount() == 0) {
      logger.debug("method with no params");
      return changeSetMethod.invoke(changeLogInstance);
    }

    return changeSetMethod.invoke(changeLogInstance, getParameters(changeSetMethod));
  }

  private Object[] getParameters(Method changeSetMethod) throws MongobeeChangeSetException {
    Object[] parameters = new Object[changeSetMethod.getParameterCount()];

    for (int i = 0; i < changeSetMethod.getParameterCount(); i++) {
      Class<?> type = changeSetMethod.getParameterTypes()[i];
      if (type.equals(MongoDatabase.class)) {
        parameters[i] = dao.getMongoDatabase();
      } else if (changeSetMethodParams.containsKey(type)) {
        parameters[i] = changeSetMethodParams.get(type);
      } else {
        throw new MongobeeChangeSetException("ChangeSet method " + changeSetMethod.getName() +
            " has wrong arguments list. Unsupported type: " + type.getSimpleName());
      }
    }

    String paramsTypes = Arrays.stream(parameters)
        .map(o -> o.getClass().getSimpleName())
        .collect(Collectors.joining(", "));
    logger.debug("method with arguments: {}", paramsTypes);

    return parameters;
  }

  private void validateConfig() throws MongobeeConfigurationException {
    if (!hasText(dbName)) {
      throw new MongobeeConfigurationException("DB name is not set. It should be defined in MongoDB URI or via setter");
    }
    if (!hasText(changeLogsScanPackage)) {
      throw new MongobeeConfigurationException("Scan package for changelogs is not set: use appropriate setter");
    }
  }

  /**
   * @return true if an execution is in progress, in any process.
   * @throws MongobeeConnectionException exception
   */
  public boolean isExecutionInProgress() throws MongobeeConnectionException {
    return dao.isProccessLockHeld();
  }

  /**
   * Used DB name should be set here
   *
   * @param dbName database name
   * @return Mongobee object for fluent interface
   */
  public Mongobee setDbName(String dbName) {
    this.dbName = dbName;
    return this;
  }

  /**
   * Initialize environment type. Only {@link com.github.mongobee.changeset.ChangeSet} with environment marked as ANY (default) or the same value will be executed
   *
   * @param environment environment type
   * @return Mongobee object for fluent interface
   */
  public Mongobee setEnvironment(String environment) {
    this.environment = environment;
    return this;
  }

  /**
   * Optional params for ChangeSet methods
   *
   * @param changeSetMethodParams instances of additional params which can be used in ChangeSet methods
   * @return Mongobee object for fluent interface
   */
  public Mongobee setChangeSetMethodParams(Map<Class<?>, Object> changeSetMethodParams) {
    this.changeSetMethodParams = Map.copyOf(changeSetMethodParams);
    return this;
  }

  /**
   * Package name where @ChangeLog-annotated classes are kept.
   *
   * @param changeLogsScanPackage package where your changelogs are
   * @return Mongobee object for fluent interface
   */
  public Mongobee setChangeLogsScanPackage(String changeLogsScanPackage) {
    this.changeLogsScanPackage = changeLogsScanPackage;
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
  public Mongobee setEnabled(boolean enabled) {
    this.enabled = enabled;
    return this;
  }

  /**
   * Feature which enables/disables waiting for lock if it's already obtained
   *
   * @param waitForLock Mongobee will be waiting for lock if it's already obtained if this option is set to true
   * @return Mongobee object for fluent interface
   */
  public Mongobee setWaitForLock(boolean waitForLock) {
    this.dao.setWaitForLock(waitForLock);
    return this;
  }

  /**
   * Waiting time for acquiring lock if waitForLock is true
   *
   * @param changeLogLockWaitTime Waiting time in minutes for acquiring lock
   * @return Mongobee object for fluent interface
   */
  public Mongobee setChangeLogLockWaitTime(long changeLogLockWaitTime) {
    this.dao.setChangeLogLockWaitTime(changeLogLockWaitTime);
    return this;
  }

  /**
   * Poll rate for acquiring lock if waitForLock is true
   *
   * @param changeLogLockPollRate Poll rate in seconds for acquiring lock
   * @return Mongobee object for fluent interface
   */
  public Mongobee setChangeLogLockPollRate(long changeLogLockPollRate) {
    this.dao.setChangeLogLockPollRate(changeLogLockPollRate);
    return this;
  }

  /**
   * Feature which enables/disables throwing MongobeeLockException if Mongobee can not obtain lock
   *
   * @param throwExceptionIfCannotObtainLock Mongobee will throw MongobeeLockException if lock can not be obtained
   * @return Mongobee object for fluent interface
   */
  public Mongobee setThrowExceptionIfCannotObtainLock(boolean throwExceptionIfCannotObtainLock) {
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
  public Mongobee setChangelogCollectionName(String changelogCollectionName) {
    this.dao.setChangelogCollectionName(changelogCollectionName);
    return this;
  }

  /**
   * Overwrites a default mongobee lock collection hardcoded in DEFAULT_LOCK_COLLECTION_NAME
   *
   * @param lockCollectionName a new lock collection name
   * @return Mongobee object for fluent interface
   */
  public Mongobee setLockCollectionName(String lockCollectionName) {
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
