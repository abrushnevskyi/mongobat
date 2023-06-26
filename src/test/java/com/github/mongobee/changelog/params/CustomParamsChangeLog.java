package com.github.mongobee.changelog.params;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoClient;
import org.bson.Document;

@ChangeLog(order = "1")
public class CustomParamsChangeLog {

  @ChangeSet(author = "testUser", id = "id1", order = "01", description = "")
  public void changeSet1(Document document, MongoClient mongoClient) {
    System.out.println("document + mongoClient");
  }

  @ChangeSet(author = "testUser", id = "id2", order = "02", description = "")
  public void changeSet2(MongoClient mongoClient, Document document) {
    System.out.println("mongoClient + document");
  }

  @ChangeSet(author = "testUser", id = "id3", order = "03", description = "")
  public void changeSet3(MongoClient mongoClient) {
    System.out.println("mongoClient");
  }

  @ChangeSet(author = "testUser", id = "id4", order = "04", description = "")
  public void changeSet4(Document document) {
    System.out.println("document");
  }

  @ChangeSet(author = "testUser", id = "id5", order = "05", description = "")
  public void changeSet5() {
    System.out.println("none");
  }

}
