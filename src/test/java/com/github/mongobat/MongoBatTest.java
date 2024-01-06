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
import com.github.mongobat.exception.MongoBatLockException;
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
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MongoBatTest {

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
  void init() throws MongoBatException {
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
  void shouldThrowAnExceptionIfNoDbNameSet() {
    runner.setDbName(null);
    runner.setEnabled(true);
    runner.setChangeLogsScanPackage(MongoBatTestResource.class.getPackage().getName());

    Executable execute = () -> {
      ExecutionReport report = runner.execute();
      assertNull(report);
    };
    assertThrows(MongoBatConfigurationException.class, execute);
  }

  @Test
  void shouldExecuteAllChangeSets() throws Exception {
    // given
    when(fakeMongoDatabase.getCollection(CHANGELOG_COLLECTION_NAME)).thenReturn(mongoCollection);
    when(dao.acquireProcessLock()).thenReturn(true);
    when(dao.isNewChange(any(ChangeEntry.class))).thenReturn(true);

    // when
    ExecutionReport report = runner.execute();

    // then
    verify(dao, times(8)).save(any(ChangeEntry.class));
    assertEquals(8, report.getScanned());
    assertEquals(8, report.getExecuted());
  }

  @Test
  void shouldPassOverChangeSets() throws Exception {
    // given
    when(dao.acquireProcessLock()).thenReturn(true);
    lenient().when(dao.isNewChange(any(ChangeEntry.class))).thenReturn(false);

    // when
    ExecutionReport report = runner.execute();

    // then
    verify(dao, times(0)).save(any(ChangeEntry.class));
    assertEquals(8, report.getScanned());
    assertEquals(8, report.getSkipped());
  }

  @Test
  void shouldExecuteProcessWhenLockAcquired() throws Exception {
    // given
    when(dao.acquireProcessLock()).thenReturn(true);

    // when
    runner.execute();

    // then
    verify(dao, atLeastOnce()).isNewChange(any(ChangeEntry.class));
  }

  @Test
  void shouldReleaseLockAfterWhenLockAcquired() throws Exception {
    // given
    when(dao.acquireProcessLock()).thenReturn(true);

    // when
    runner.execute();

    // then
    verify(dao).releaseProcessLock();
  }

  @Test
  void shouldNotExecuteProcessWhenLockNotAcquired() throws Exception {
    // given
    when(dao.acquireProcessLock()).thenReturn(false);

    // when
    ExecutionReport report = runner.execute();

    // then
    verify(dao, never()).isNewChange(any(ChangeEntry.class));
    assertNull(report);
  }

  @Test
  void shouldReturnExecutionStatusBasedOnDao() throws Exception {
    // given
    when(dao.isProccessLockHeld()).thenReturn(true);

    boolean inProgress = runner.isExecutionInProgress();

    // then
    assertTrue(inProgress);
  }

  @Test
  void shouldReleaseLockWhenExceptionInMigration() throws Exception {
    // given
    when(dao.acquireProcessLock()).thenReturn(true);
    when(dao.isNewChange(any(ChangeEntry.class))).thenThrow(RuntimeException.class);

    // when & then
    Executable execute = () -> {
      ExecutionReport report = runner.execute();
      assertEquals(8, report.getScanned());
      assertEquals(8, report.getFailed());
    };
    assertThrows(RuntimeException.class, execute);
    verify(dao).releaseProcessLock();
  }

  @Test
  void shouldIgnoreChangeSetsWithUnsupportedParameterType() throws Exception {
    runner.setChangeLogsScanPackage(CustomParamsChangeLog.class.getPackage().getName());
    runner.setChangeSetMethodParams(Map.of(ChangeSetExecutionChecker.class, executionChecker));

    when(dao.acquireProcessLock()).thenReturn(true);
    when(dao.isNewChange(any(ChangeEntry.class))).thenReturn(true);
    when(fakeMongoDatabase.getCollection(CHANGELOG_COLLECTION_NAME)).thenReturn(mongoCollection);

    ExecutionReport report = runner.execute();

    verify(executionChecker).execute(CustomParamsChangeLog.CHANGESET5);
    verify(dao, times(5)).save(any(ChangeEntry.class));
    assertEquals(5, report.getScanned());
    assertEquals(1, report.getExecuted());
    assertEquals(4, report.getFailed());
  }

  @Test
  void shouldRunChangeSetsWhenCustomParametersWereDefined() throws Exception {
    runner.setChangeLogsScanPackage(CustomParamsChangeLog.class.getPackage().getName());
    runner.setChangeSetMethodParams(Map.of(Document.class, new Document(), MongoClient.class, mongoClient, ChangeSetExecutionChecker.class, executionChecker));

    when(dao.acquireProcessLock()).thenReturn(true);
    when(dao.isNewChange(any(ChangeEntry.class))).thenReturn(true);
    when(fakeMongoDatabase.getCollection(CHANGELOG_COLLECTION_NAME)).thenReturn(mongoCollection);

    ExecutionReport report = runner.execute();

    verify(executionChecker, times(5)).execute(anyString());
    verify(dao, times(5)).save(any(ChangeEntry.class));
    assertEquals(5, report.getScanned());
    assertEquals(5, report.getExecuted());
  }

  @Test
  void shouldRunChangeSetsForSelectedEnvironment() throws Exception {
    runner.setChangeLogsScanPackage(EnvironmentsChangeLog.class.getPackage().getName());
    runner.setEnvironment(Environment.PROD);
    runner.setChangeSetMethodParams(Map.of(ChangeSetExecutionChecker.class, executionChecker));

    when(dao.acquireProcessLock()).thenReturn(true);
    when(dao.isNewChange(any(ChangeEntry.class))).thenReturn(true);
    when(fakeMongoDatabase.getCollection(CHANGELOG_COLLECTION_NAME)).thenReturn(mongoCollection);
    doCallRealMethod().when(executionChecker).execute(anyString());

    ExecutionReport report = runner.execute();

    verify(executionChecker).execute(Environment.PROD);
    verify(executionChecker).execute(Environment.ANY);
    verify(dao, times(2)).save(any(ChangeEntry.class));
    assertEquals(5, report.getScanned());
    assertEquals(2, report.getExecuted());
  }

  @Test
  void shouldRunAllChangeSetsWhenEnvironmentIsNotDefined() throws Exception {
    runner.setChangeLogsScanPackage(EnvironmentsChangeLog.class.getPackage().getName());
    runner.setChangeSetMethodParams(Map.of(ChangeSetExecutionChecker.class, executionChecker));

    when(dao.acquireProcessLock()).thenReturn(true);
    when(dao.isNewChange(any(ChangeEntry.class))).thenReturn(true);
    when(fakeMongoDatabase.getCollection(CHANGELOG_COLLECTION_NAME)).thenReturn(mongoCollection);
    doCallRealMethod().when(executionChecker).execute(anyString());

    ExecutionReport report = runner.execute();

    verify(executionChecker, times(5)).execute(anyString());
    verify(dao, times(5)).save(any(ChangeEntry.class));
    assertEquals(5, report.getScanned());
    assertEquals(5, report.getExecuted());
  }

  @Test
  void shouldNotExecutePostponedChangeSets() throws Exception {
    runner.setChangeLogsScanPackage(PostponedChangeLog.class.getPackage().getName());
    runner.setChangeSetMethodParams(Map.of(ChangeSetExecutionChecker.class, executionChecker));

    when(dao.acquireProcessLock()).thenReturn(true);
    when(dao.isNewChange(any(ChangeEntry.class))).thenReturn(true);
    when(fakeMongoDatabase.getCollection(CHANGELOG_COLLECTION_NAME)).thenReturn(mongoCollection);
    doCallRealMethod().when(executionChecker).execute(anyString());

    ExecutionReport report = runner.execute();

    verify(executionChecker).execute(PostponedChangeLog.NOT_POSTPONED);
    verify(dao, times(3)).save(any(ChangeEntry.class));
    assertEquals(3, report.getScanned());
    assertEquals(1, report.getExecuted());
    assertEquals(2, report.getPostponed());
  }

  @Test
  void shouldIgnoreRunAlwaysFlagForPostponedChangeSets() throws Exception {
    runner.setChangeLogsScanPackage(PostponedChangeLog.class.getPackage().getName());
    runner.setChangeSetMethodParams(Map.of(ChangeSetExecutionChecker.class, executionChecker));

    when(dao.acquireProcessLock()).thenReturn(true);
    when(dao.isNewChange(any(ChangeEntry.class))).thenReturn(false);

    ExecutionReport report = runner.execute();

    verify(executionChecker, never()).execute(anyString());
    verify(dao, never()).save(any(ChangeEntry.class));
    assertEquals(3, report.getScanned());
    assertEquals(3, report.getSkipped());
  }

  @Test
  void shouldRunSingleChangeSetRegardlessOfRepeatableWhenIsNew() throws Exception {
    runner.setChangeLogsScanPackage(RepeatableChangeLog.class.getPackage().getName());
    runner.setChangeSetMethodParams(Map.of(ChangeSetExecutionChecker.class, executionChecker));

    when(dao.acquireProcessLock()).thenReturn(true);
    when(dao.isNewChange(any(ChangeEntry.class))).thenReturn(true);
    when(fakeMongoDatabase.getCollection(CHANGELOG_COLLECTION_NAME)).thenReturn(mongoCollection);
    doCallRealMethod().when(executionChecker).execute(anyString());

    ChangeEntry changeEntry = createChangeEntry("id1", "changeSet1", false);
    ExecutionReport report = runner.executeSingle(changeEntry);

    verify(executionChecker).execute("111");
    verify(dao).save(any(ChangeEntry.class));
    assertEquals(1, report.getScanned());
    assertEquals(1, report.getExecuted());
  }

  @Test
  void shouldNotRerunSingleChangeSetWhenRepeatableIsFalse() throws Exception {
    runner.setChangeLogsScanPackage(RepeatableChangeLog.class.getPackage().getName());

    when(dao.acquireProcessLock()).thenReturn(true);
    when(dao.isNewChange(any(ChangeEntry.class))).thenReturn(false);
    when(fakeMongoDatabase.getCollection(CHANGELOG_COLLECTION_NAME)).thenReturn(mongoCollection);

    ChangeEntry changeEntry = createChangeEntry("id1", "changeSet1", false);
    ExecutionReport report = runner.executeSingle(changeEntry);

    assertEquals(1, report.getScanned());
    assertEquals(1, report.getFailed());
  }

  @Test
  void shouldRerunSingleChangeSetWhenRepeatableIsTrue() throws Exception {
    runner.setChangeLogsScanPackage(RepeatableChangeLog.class.getPackage().getName());
    runner.setChangeSetMethodParams(Map.of(ChangeSetExecutionChecker.class, executionChecker));

    when(dao.acquireProcessLock()).thenReturn(true);
    when(dao.isNewChange(any(ChangeEntry.class))).thenReturn(false);
    when(fakeMongoDatabase.getCollection(CHANGELOG_COLLECTION_NAME)).thenReturn(mongoCollection);

    ChangeEntry changeEntry = createChangeEntry("id2", "changeSet2", true);
    ExecutionReport report = runner.executeSingle(changeEntry);

    verify(dao).save(any(ChangeEntry.class));
    assertEquals(1, report.getScanned());
    assertEquals(1, report.getReExecuted());
  }

  private ChangeEntry createChangeEntry(String changeId, String changeSetMethodName, boolean repeatable) {
    return new ChangeEntry(changeId, "testUser", new Date(), RepeatableChangeLog.class.getName(), changeSetMethodName, "", "", Environment.ANY, false, repeatable);
  }

}
