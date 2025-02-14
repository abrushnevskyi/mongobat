package com.github.mongobat.dao;

import static com.github.mongobat.utils.StringUtils.hasText;

import java.util.Date;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.mongobat.changeset.ChangeEntry;
import com.github.mongobat.exception.MongoBatConfigurationException;
import com.github.mongobat.exception.MongoBatConnectionException;
import com.github.mongobat.exception.MongoBatLockException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

/**
 * @author lstolowski
 * @since 27/07/2014
 */
public class ChangeEntryDao {
  private static final Logger log = LoggerFactory.getLogger(ChangeEntryDao.class);

  private MongoDatabase mongoDatabase;
  private MongoClient mongoClient;
  private ChangeEntryIndexDao indexDao;
  private String changelogCollectionName;
  private boolean waitForLock;
  private long changeLogLockWaitTime;
  private long changeLogLockPollRate;
  private boolean throwExceptionIfCannotObtainLock;
  private String installationId;

  private LockDao lockDao;

  public ChangeEntryDao(String changelogCollectionName, String lockCollectionName, boolean waitForLock, long changeLogLockWaitTime,
      long changeLogLockPollRate, boolean throwExceptionIfCannotObtainLock) {
    this.indexDao = new ChangeEntryIndexDao(changelogCollectionName);
    this.lockDao = new LockDao(lockCollectionName);
    this.changelogCollectionName = changelogCollectionName;
    this.waitForLock = waitForLock;
    this.changeLogLockWaitTime = changeLogLockWaitTime;
    this.changeLogLockPollRate = changeLogLockPollRate;
    this.throwExceptionIfCannotObtainLock = throwExceptionIfCannotObtainLock;
  }

  public MongoDatabase getMongoDatabase() {
    return mongoDatabase;
  }

  public MongoDatabase connectMongoDb(MongoClient mongo, String dbName) throws MongoBatConfigurationException {
    if (!hasText(dbName)) {
      throw new MongoBatConfigurationException("DB name is not set. Should be defined in MongoDB URI or via setter");
    } else {

      this.mongoClient = mongo;

      mongoDatabase = mongo.getDatabase(dbName);

      ensureChangeLogCollectionIndex(mongoDatabase.getCollection(changelogCollectionName));
      initializeLock();
      return mongoDatabase;
    }
  }

  /**
   * Try to acquire process lock
   *
   * @return true if successfully acquired, false otherwise
   * @throws MongoBatConnectionException exception
   * @throws MongoBatLockException exception
   */
  public boolean acquireProcessLock() throws MongoBatConnectionException, MongoBatLockException {
    verifyDbConnection();
    boolean acquired = lockDao.acquireLock(getMongoDatabase());

    if (!acquired && waitForLock) {
      long timeToGiveUp = new Date().getTime() + (changeLogLockWaitTime * 1000 * 60);
      while (!acquired && new Date().getTime() < timeToGiveUp) {
        acquired = lockDao.acquireLock(getMongoDatabase());
        if (!acquired) {
          log.info("Waiting for changelog lock....");
          try {
            Thread.sleep(changeLogLockPollRate * 1000);
          } catch (InterruptedException e) {
            // nothing
          }
        }
      }
    }

    if (!acquired && throwExceptionIfCannotObtainLock) {
      log.info("Mongobee did not acquire process lock. Throwing exception.");
      throw new MongoBatLockException("Could not acquire process lock");
    }

    return acquired;
  }

  public void releaseProcessLock() throws MongoBatConnectionException {
    verifyDbConnection();
    lockDao.releaseLock(getMongoDatabase());
  }

  public boolean isProccessLockHeld() throws MongoBatConnectionException {
    verifyDbConnection();
    return lockDao.isLockHeld(getMongoDatabase());
  }

  public boolean isNewChange(ChangeEntry changeEntry) throws MongoBatConnectionException {
    verifyDbConnection();

    MongoCollection<Document> mongobeeChangeLog = getMongoDatabase().getCollection(changelogCollectionName);
    Document entry = mongobeeChangeLog.find(changeEntry.buildSearchQueryDBObject()).first();

    return entry == null;
  }

  public void save(ChangeEntry changeEntry) throws MongoBatConnectionException {
    verifyDbConnection();

    MongoCollection<Document> mongobeeLog = getMongoDatabase().getCollection(changelogCollectionName);

    Document documentChangeEntry = changeEntry.buildFullDBObject();
    documentChangeEntry.append("installationId", installationId);

    mongobeeLog.insertOne(documentChangeEntry);
  }

  private void verifyDbConnection() throws MongoBatConnectionException {
    if (getMongoDatabase() == null) {
      throw new MongoBatConnectionException("Database is not connected. Mongobee has thrown an unexpected error",
          new NullPointerException());
    }
  }

  private void ensureChangeLogCollectionIndex(MongoCollection<Document> collection) {
    Document index = indexDao.findRequiredChangeAndAuthorIndex(mongoDatabase);
    if (index == null) {
      indexDao.createRequiredUniqueIndex(collection);
      log.debug("Index in collection {} was created", changelogCollectionName);
    } else if (!indexDao.isUnique(index)) {
      indexDao.dropIndex(collection, index);
      indexDao.createRequiredUniqueIndex(collection);
      log.debug("Index in collection {} was recreated", changelogCollectionName);
    }

  }

  public void close() {
      this.mongoClient.close();
  }

  private void initializeLock() {
    lockDao.intitializeLock(mongoDatabase);
  }

  public void setIndexDao(ChangeEntryIndexDao changeEntryIndexDao) {
    this.indexDao = changeEntryIndexDao;
  }

  /* Visible for testing */
  void setLockDao(LockDao lockDao) {
    this.lockDao = lockDao;
  }

  public void setChangelogCollectionName(String changelogCollectionName) {
	this.indexDao.setChangelogCollectionName(changelogCollectionName);
	this.changelogCollectionName = changelogCollectionName;
  }

  public void setLockCollectionName(String lockCollectionName) {
	this.lockDao.setLockCollectionName(lockCollectionName);
  }

  public boolean isWaitForLock() {
    return waitForLock;
  }

  public void setWaitForLock(boolean waitForLock) {
    this.waitForLock = waitForLock;
  }

  public long getChangeLogLockWaitTime() {
    return changeLogLockWaitTime;
  }

  public void setChangeLogLockWaitTime(long changeLogLockWaitTime) {
    this.changeLogLockWaitTime = changeLogLockWaitTime;
  }

  public long getChangeLogLockPollRate() {
    return changeLogLockPollRate;
  }

  public void setChangeLogLockPollRate(long changeLogLockPollRate) {
    this.changeLogLockPollRate = changeLogLockPollRate;
  }

  public boolean isThrowExceptionIfCannotObtainLock() {
    return throwExceptionIfCannotObtainLock;
  }

  public void setThrowExceptionIfCannotObtainLock(boolean throwExceptionIfCannotObtainLock) {
    this.throwExceptionIfCannotObtainLock = throwExceptionIfCannotObtainLock;
  }

  public String getInstallationId() {
    return installationId;
  }

  public void setInstallationId(String installationId) {
    this.installationId = installationId;
  }

}
