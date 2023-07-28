package com.github.mongobat.test.changelogs;

import com.github.mongobat.changeset.ChangeLog;
import com.github.mongobat.changeset.ChangeSet;
import com.mongodb.client.MongoDatabase;

/**
 * @author lstolowski
 * @since 30.07.14
 */
@ChangeLog(order = "2")
public class AnotherMongoBatTestResource {

  @ChangeSet(author = "testuser", id = "Btest1", order = "01", description = "")
  public void testChangeSet(){
    System.out.println("invoked B1");
  }

  @ChangeSet(author = "testuser", id = "Btest2", order = "02", description = "")
  public void testChangeSet2(){
    System.out.println("invoked B2");
  }

  @ChangeSet(author = "testuser", id = "Btest3", order = "03", description = "")
  public void testChangeSet3(MongoDatabase mongoDatabase){
    System.out.println("invoked B3 with db=" + mongoDatabase);
  }

  @ChangeSet(author = "testuser", id = "Btest6", order = "06", description = "")
  public void testChangeSet6(MongoDatabase mongoDatabase) {
    System.out.println("invoked B6 with db=" + mongoDatabase.toString());
  }

}
