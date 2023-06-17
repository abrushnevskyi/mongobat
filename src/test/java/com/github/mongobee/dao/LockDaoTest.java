package com.github.mongobee.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.mongodb.MongoWriteException;
import com.mongodb.ServerAddress;
import com.mongodb.WriteError;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import org.bson.BsonDocument;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mongodb.client.MongoDatabase;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author colsson11
 * @since 13.01.15
 */
@ExtendWith(MockitoExtension.class)
public class LockDaoTest {

  private static final String LOCK_COLLECTION_NAME = "mongobeelock";

  @Mock
  private MongoDatabase db;

  @Mock
  private MongoCollection<Document> lockCollection;

  @BeforeEach
  public void beforeEach() {
    when(db.getCollection(LOCK_COLLECTION_NAME)).thenReturn(lockCollection);
  }

  @Test
  public void shouldGetLockWhenNotPreviouslyHeld() {
    // given
    LockDao dao = new LockDao(LOCK_COLLECTION_NAME);
    dao.intitializeLock(db);

    // when
    boolean hasLock = dao.acquireLock(db);

    // then
    assertTrue(hasLock);
    verify(lockCollection).createIndex(any(Document.class), any(IndexOptions.class));
    verify(lockCollection).insertOne(any(Document.class));
  }

  @Test
  public void shouldNotGetLockWhenPreviouslyHeld() {
    // given
    WriteError writeError = new WriteError(1, "ERROR", new BsonDocument());
    MongoWriteException exception = new MongoWriteException(writeError, new ServerAddress());
    doNothing().doThrow(exception).when(lockCollection).insertOne(any(Document.class));

    LockDao dao = new LockDao(LOCK_COLLECTION_NAME);
    dao.intitializeLock(db);

    // when
    dao.acquireLock(db);
    boolean hasLock = dao.acquireLock(db);
    // then
    verify(lockCollection, times(2)).insertOne(any(Document.class));
    assertFalse(hasLock);
  }

  @Test
  public void shouldGetLockWhenPreviouslyHeldAndReleased() {
    // given
    LockDao dao = new LockDao(LOCK_COLLECTION_NAME);
    dao.intitializeLock(db);

    // when
    dao.acquireLock(db);
    dao.releaseLock(db);
    boolean hasLock = dao.acquireLock(db);
    // then
    assertTrue(hasLock);
    verify(lockCollection, times(2)).insertOne(any(Document.class));
    verify(lockCollection).deleteMany(any(Document.class));
  }

  @Test
  public void releaseLockShouldBeIdempotent() {
    // given
    LockDao dao = new LockDao(LOCK_COLLECTION_NAME);

    dao.intitializeLock(db);

    // when
    dao.releaseLock(db);
    dao.releaseLock(db);
    boolean hasLock = dao.acquireLock(db);
    // then
    assertTrue(hasLock);
    verify(lockCollection, times(2)).deleteMany(any(Document.class));
    verify(lockCollection).insertOne(any(Document.class));
  }

  @Test
  public void whenLockNotHeldCheckReturnsFalse() {
    when(lockCollection.countDocuments()).thenReturn(0L);

    LockDao dao = new LockDao(LOCK_COLLECTION_NAME);
    dao.intitializeLock(db);

    assertFalse(dao.isLockHeld(db));
    verify(lockCollection, times(0)).insertOne(any(Document.class));
  }

  @Test
  public void whenLockHeldCheckReturnsTrue() {
    when(lockCollection.countDocuments()).thenReturn(1L);

    LockDao dao = new LockDao(LOCK_COLLECTION_NAME);
    dao.intitializeLock(db);

    dao.acquireLock(db);

    assertTrue(dao.isLockHeld(db));
    verify(lockCollection).insertOne(any(Document.class));
  }

}
