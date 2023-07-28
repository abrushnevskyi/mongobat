package com.github.mongobat;

import com.github.mongobat.changelog.environments.EnvironmentsChangeLog;
import com.github.mongobat.changelog.params.CustomParamsChangeLog;
import com.github.mongobat.changelog.postponed.PostponedChangeLog;
import com.github.mongobat.changelog.repeatable.RepeatableChangeLog;
import com.github.mongobat.changeset.ChangeEntry;
import com.github.mongobat.dao.ChangeEntryDao;
import com.github.mongobat.dao.ChangeEntryIndexDao;
import com.github.mongobat.exception.MongoBatChangeSetException;
import com.github.mongobat.exception.MongoBatConfigurationException;
import com.github.mongobat.exception.MongoBatException;
import com.github.mongobat.test.changelogs.MongoBatTestResource;
import com.github.mongobat.utils.ChangeSetExecutionChecker;
import com.github.mongobat.utils.Environment;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MongoBatTest {

  private static final String CHANGELOG_COLLECTION_NAME = "dbchangelog";

  @Mock
  private ChangeEntryDao dao;

  @Mock
  private ChangeEntryIndexDao indexDao;

  @Mock
  private MongoDatabase fakeMongoDatabase;

  @Mock
  private MongoCollection<Document> mongoCollection;

  @Mock
  private MongoClient mongoClient;

  @Mock
  private ChangeSetExecutionChecker executionChecker;

  @InjectMocks
  private MongoBat runner = new MongoBat(mongoClient);

  @BeforeEach
  public void init() throws MongoBatException {
    lenient().when(dao.connectMongoDb(any(MongoClient.class), anyString()))
        .thenReturn(fakeMongoDatabase);
    lenient().when(dao.getMongoDatabase()).thenReturn(fakeMongoDatabase);
    lenient().doCallRealMethod().when(dao).save(any(ChangeEntry.class));
    doCallRealMethod().when(dao).setChangelogCollectionName(anyString());
    doCallRealMethod().when(dao).setIndexDao(any(ChangeEntryIndexDao.class));
    dao.setIndexDao(indexDao);
    dao.setChangelogCollectionName(CHANGELOG_COLLECTION_NAME);

    runner.setDbName("mongobeetest");
    runner.setEnabled(true);
    runner.setChangeLogsScanPackage(MongoBatTestResource.class.getPackage().getName());
  }

  @Test
  public void shouldThrowAnExceptionIfNoDbNameSet() {
    runner.setDbName(null);
    runner.setEnabled(true);
    runner.setChangeLogsScanPackage(MongoBatTestResource.class.getPackage().getName());
    assertThrows(MongoBatConfigurationException.class, runner::execute);
  }

  @Test
  public void shouldExecuteAllChangeSets() throws Exception {
    // given
    when(fakeMongoDatabase.getCollection(CHANGELOG_COLLECTION_NAME)).thenReturn(mongoCollection);
    when(dao.acquireProcessLock()).thenReturn(true);
    when(dao.isNewChange(any(ChangeEntry.class))).thenReturn(true);

    // when
    runner.execute();

    // then
    verify(dao, times(8)).save(any(ChangeEntry.class)); // 8 changesets saved to dbchangelog
  }

  @Test
  public void shouldPassOverChangeSets() throws Exception {
    // given
    lenient().when(dao.isNewChange(any(ChangeEntry.class))).thenReturn(false);

    // when
    runner.execute();

    // then
    verify(dao, times(0)).save(any(ChangeEntry.class)); // no changesets saved to dbchangelog
  }

  @Test
  public void shouldExecuteProcessWhenLockAcquired() throws Exception {
    // given
    when(dao.acquireProcessLock()).thenReturn(true);

    // when
    runner.execute();

    // then
    verify(dao, atLeastOnce()).isNewChange(any(ChangeEntry.class));
  }

  @Test
  public void shouldReleaseLockAfterWhenLockAcquired() throws Exception {
    // given
    when(dao.acquireProcessLock()).thenReturn(true);

    // when
    runner.execute();

    // then
    verify(dao).releaseProcessLock();
  }

  @Test
  public void shouldNotExecuteProcessWhenLockNotAcquired() throws Exception {
    // given
    when(dao.acquireProcessLock()).thenReturn(false);

    // when
    runner.execute();

    // then
    verify(dao, never()).isNewChange(any(ChangeEntry.class));
  }

  @Test
  public void shouldReturnExecutionStatusBasedOnDao() throws Exception {
    // given
    when(dao.isProccessLockHeld()).thenReturn(true);

    boolean inProgress = runner.isExecutionInProgress();

    // then
    assertTrue(inProgress);
  }

  @Test
  public void shouldReleaseLockWhenExceptionInMigration() throws Exception {
    // given
    // would be nicer with a mock for the whole execution, but this would mean breaking out to separate class..
    // this should be "good enough"
    when(dao.acquireProcessLock()).thenReturn(true);
    when(dao.isNewChange(any(ChangeEntry.class))).thenThrow(RuntimeException.class);

    // when
    // have to catch the exception to be able to verify after
    try {
      runner.execute();
    } catch (Exception e) {
      // do nothing
    }
    // then
    verify(dao).releaseProcessLock();
  }

  @Test
  public void shouldIgnoreChangeSetsWithUnsupportedParameterType() throws Exception {
    runner.setChangeLogsScanPackage(CustomParamsChangeLog.class.getPackage().getName());
    runner.setChangeSetMethodParams(Map.of(ChangeSetExecutionChecker.class, executionChecker));

    when(dao.acquireProcessLock()).thenReturn(true);
    when(dao.isNewChange(any(ChangeEntry.class))).thenReturn(true);
    when(fakeMongoDatabase.getCollection(CHANGELOG_COLLECTION_NAME)).thenReturn(mongoCollection);

    runner.execute();

    verify(executionChecker).execute(CustomParamsChangeLog.CHANGESET5);
    verify(dao).save(any(ChangeEntry.class));
  }

  @Test
  public void shouldRunChangeSetsWhenCustomParametersWereDefined() throws Exception {
    runner.setChangeLogsScanPackage(CustomParamsChangeLog.class.getPackage().getName());
    runner.setChangeSetMethodParams(Map.of(Document.class, new Document(), MongoClient.class, mongoClient, ChangeSetExecutionChecker.class, executionChecker));

    when(dao.acquireProcessLock()).thenReturn(true);
    when(dao.isNewChange(any(ChangeEntry.class))).thenReturn(true);
    when(fakeMongoDatabase.getCollection(CHANGELOG_COLLECTION_NAME)).thenReturn(mongoCollection);

    runner.execute();

    verify(executionChecker, times(5)).execute(anyString());
    verify(dao, times(5)).save(any(ChangeEntry.class));
  }

  @Test
  public void shouldRunChangeSetsForSelectedEnvironment() throws Exception {
    runner.setChangeLogsScanPackage(EnvironmentsChangeLog.class.getPackage().getName());
    runner.setEnvironment(Environment.PROD);
    runner.setChangeSetMethodParams(Map.of(ChangeSetExecutionChecker.class, executionChecker));

    when(dao.acquireProcessLock()).thenReturn(true);
    when(dao.isNewChange(any(ChangeEntry.class))).thenReturn(true);
    when(fakeMongoDatabase.getCollection(CHANGELOG_COLLECTION_NAME)).thenReturn(mongoCollection);
    doCallRealMethod().when(executionChecker).execute(anyString());

    runner.execute();

    verify(executionChecker).execute(Environment.PROD);
    verify(executionChecker).execute(Environment.ANY);
    verify(dao, times(2)).save(any(ChangeEntry.class));
  }

  @Test
  public void shouldRunAllChangeSetsWhenEnvironmentIsNotDefined() throws Exception {
    runner.setChangeLogsScanPackage(EnvironmentsChangeLog.class.getPackage().getName());
    runner.setChangeSetMethodParams(Map.of(ChangeSetExecutionChecker.class, executionChecker));

    when(dao.acquireProcessLock()).thenReturn(true);
    when(dao.isNewChange(any(ChangeEntry.class))).thenReturn(true);
    when(fakeMongoDatabase.getCollection(CHANGELOG_COLLECTION_NAME)).thenReturn(mongoCollection);
    doCallRealMethod().when(executionChecker).execute(anyString());

    runner.execute();

    verify(executionChecker, times(5)).execute(anyString());
    verify(dao, times(5)).save(any(ChangeEntry.class));
  }

  @Test
  public void shouldNotExecutePostponedChangeSets() throws Exception {
    runner.setChangeLogsScanPackage(PostponedChangeLog.class.getPackage().getName());
    runner.setChangeSetMethodParams(Map.of(ChangeSetExecutionChecker.class, executionChecker));

    when(dao.acquireProcessLock()).thenReturn(true);
    when(dao.isNewChange(any(ChangeEntry.class))).thenReturn(true);
    when(fakeMongoDatabase.getCollection(CHANGELOG_COLLECTION_NAME)).thenReturn(mongoCollection);
    doCallRealMethod().when(executionChecker).execute(anyString());

    runner.execute();

    verify(executionChecker).execute(PostponedChangeLog.NOT_POSTPONED);
    verify(dao, times(3)).save(any(ChangeEntry.class));
  }

  @Test
  public void shouldIgnoreRunAlwaysFlagForPostponedChangeSets() throws Exception {
    runner.setChangeLogsScanPackage(PostponedChangeLog.class.getPackage().getName());
    runner.setChangeSetMethodParams(Map.of(ChangeSetExecutionChecker.class, executionChecker));

    when(dao.acquireProcessLock()).thenReturn(true);
    when(dao.isNewChange(any(ChangeEntry.class))).thenReturn(false);

    runner.execute();

    verify(executionChecker, never()).execute(anyString());
    verify(dao, never()).save(any(ChangeEntry.class));
  }

  @Test
  public void shouldRunSingleChangeSetRegardlessOfRepeatableWhenIsNew() throws Exception {
    runner.setChangeLogsScanPackage(RepeatableChangeLog.class.getPackage().getName());
    runner.setChangeSetMethodParams(Map.of(ChangeSetExecutionChecker.class, executionChecker));

    when(dao.acquireProcessLock()).thenReturn(true);
    when(dao.isNewChange(any(ChangeEntry.class))).thenReturn(true);
    when(fakeMongoDatabase.getCollection(CHANGELOG_COLLECTION_NAME)).thenReturn(mongoCollection);
    doCallRealMethod().when(executionChecker).execute(anyString());

    ChangeEntry changeEntry = createChangeEntry("id1", "changeSet1", false);
    runner.executeSingle(changeEntry);

    verify(executionChecker).execute("111");
    verify(dao).save(any(ChangeEntry.class));
  }

  @Test
  public void shouldNotRerunSingleChangeSetWhenRepeatableIsFalse() throws Exception {
    runner.setChangeLogsScanPackage(RepeatableChangeLog.class.getPackage().getName());

    when(dao.acquireProcessLock()).thenReturn(true);
    when(dao.isNewChange(any(ChangeEntry.class))).thenReturn(false);

    ChangeEntry changeEntry = createChangeEntry("id1", "changeSet1", false);
    assertThrows(MongoBatChangeSetException.class, () -> runner.executeSingle(changeEntry));
  }

  @Test
  public void shouldRerunSingleChangeSetWhenRepeatableIsTrue() throws Exception {
    runner.setChangeLogsScanPackage(RepeatableChangeLog.class.getPackage().getName());
    runner.setChangeSetMethodParams(Map.of(ChangeSetExecutionChecker.class, executionChecker));

    when(dao.acquireProcessLock()).thenReturn(true);
    when(dao.isNewChange(any(ChangeEntry.class))).thenReturn(false);
    when(fakeMongoDatabase.getCollection(CHANGELOG_COLLECTION_NAME)).thenReturn(mongoCollection);

    ChangeEntry changeEntry = createChangeEntry("id2", "changeSet2", true);
    runner.executeSingle(changeEntry);

    verify(dao).save(any(ChangeEntry.class));
  }

  private ChangeEntry createChangeEntry(String changeId, String changeSetMethodName, boolean repeatable) {
    return new ChangeEntry(changeId, "testUser", new Date(), RepeatableChangeLog.class.getName(), changeSetMethodName, "", "", Environment.ANY, false, repeatable);
  }

}
