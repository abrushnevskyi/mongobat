package com.github.mongobee.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.github.fakemongo.Fongo;
import com.mongodb.client.MongoDatabase;

/**
 * @author colsson11
 * @since 13.01.15
 */
public class LockDaoTest {

  private static final String TEST_SERVER = "testServer";
  private static final String DB_NAME = "mongobeetest";
  private static final String LOCK_COLLECTION_NAME = "mongobeelock";

  @Test
  public void shouldGetLockWhenNotPreviouslyHeld() {

    // given
    MongoDatabase db = new Fongo(TEST_SERVER).getDatabase(DB_NAME);
    LockDao dao = new LockDao(LOCK_COLLECTION_NAME);
    dao.intitializeLock(db);

    // when
    boolean hasLock = dao.acquireLock(db);

    // then
    assertTrue(hasLock);

  }

  @Test
  public void shouldNotGetLockWhenPreviouslyHeld() {

    // given
    MongoDatabase db = new Fongo(TEST_SERVER).getDatabase(DB_NAME);
    LockDao dao = new LockDao(LOCK_COLLECTION_NAME);
    dao.intitializeLock(db);

    // when
    dao.acquireLock(db);
    boolean hasLock = dao.acquireLock(db);
    // then
    assertFalse(hasLock);

  }

  @Test
  public void shouldGetLockWhenPreviouslyHeldAndReleased() {

    // given
    MongoDatabase db = new Fongo(TEST_SERVER).getDatabase(DB_NAME);
    LockDao dao = new LockDao(LOCK_COLLECTION_NAME);
    dao.intitializeLock(db);

    // when
    dao.acquireLock(db);
    dao.releaseLock(db);
    boolean hasLock = dao.acquireLock(db);
    // then
    assertTrue(hasLock);

  }

  @Test
  public void releaseLockShouldBeIdempotent() {
    // given
    MongoDatabase db = new Fongo(TEST_SERVER).getDatabase(DB_NAME);
    LockDao dao = new LockDao(LOCK_COLLECTION_NAME);
    
    
    dao.intitializeLock(db);

    // when
    dao.releaseLock(db);
    dao.releaseLock(db);
    boolean hasLock = dao.acquireLock(db);
    // then
    assertTrue(hasLock);

  }

  @Test
  public void whenLockNotHeldCheckReturnsFalse() {

    MongoDatabase db = new Fongo(TEST_SERVER).getDatabase(DB_NAME);
    LockDao dao = new LockDao(LOCK_COLLECTION_NAME);
    dao.intitializeLock(db);

    assertFalse(dao.isLockHeld(db));

  }

  @Test
  public void whenLockHeldCheckReturnsTrue() {

    MongoDatabase db = new Fongo(TEST_SERVER).getDatabase(DB_NAME);
    LockDao dao = new LockDao(LOCK_COLLECTION_NAME);
    dao.intitializeLock(db);

    dao.acquireLock(db);

    assertTrue(dao.isLockHeld(db));

  }

}
