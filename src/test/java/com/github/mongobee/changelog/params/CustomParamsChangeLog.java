package com.github.mongobee.changelog.params;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.github.mongobee.utils.ChangeSetExecutionChecker;
import com.mongodb.client.MongoClient;
import org.bson.Document;

@ChangeLog(order = "1")
public class CustomParamsChangeLog {

  public static final String CHANGESET5 = "555";

  @ChangeSet(author = "testUser", id = "id1", order = "01", description = "")
  public void changeSet1(Document document, MongoClient mongoClient, ChangeSetExecutionChecker executionChecker) {
    executionChecker.execute("document + mongoClient");
  }

  @ChangeSet(author = "testUser", id = "id2", order = "02", description = "")
  public void changeSet2(ChangeSetExecutionChecker executionChecker, MongoClient mongoClient, Document document) {
    executionChecker.execute("mongoClient + document");
  }

  @ChangeSet(author = "testUser", id = "id3", order = "03", description = "")
  public void changeSet3(MongoClient mongoClient, ChangeSetExecutionChecker executionChecker) {
    executionChecker.execute("mongoClient");
  }

  @ChangeSet(author = "testUser", id = "id4", order = "04", description = "")
  public void changeSet4(ChangeSetExecutionChecker executionChecker, Document document) {
    executionChecker.execute("document");
  }

  @ChangeSet(author = "testUser", id = "id5", order = "05", description = "")
  public void changeSet5(ChangeSetExecutionChecker executionChecker) {
    executionChecker.execute(CHANGESET5);
  }

}
