package com.github.mongobat.test.changelogs;

import com.github.mongobat.changeset.ChangeLog;
import com.github.mongobat.changeset.ChangeSet;
import com.mongodb.client.MongoDatabase;

/**
 * @author lstolowski
 * @since 27/07/2014
 */
@ChangeLog(order = "1")
public class MongoBatTestResource {

  @ChangeSet(author = "testuser", id = "test1", order = "01", description = "")
  public void testChangeSet() {
    System.out.println("invoked 1");
  }

  @ChangeSet(author = "testuser", id = "test2", order = "02", description = "")
  public void testChangeSet2() {
    System.out.println("invoked 2");
  }

  @ChangeSet(author = "testuser", id = "test3", order = "03", description = "")
  public void testChangeSet3(MongoDatabase mongoDatabase) {
    System.out.println("invoked 3 with db=" + mongoDatabase);
  }

  @ChangeSet(author = "testuser", id = "test5", order = "05", description = "")
  public void testChangeSet5(MongoDatabase mongoDatabase) {
    System.out.println("invoked 5 with mongoDatabase=" + mongoDatabase.toString());
  }

}
