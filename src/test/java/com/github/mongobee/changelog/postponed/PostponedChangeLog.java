package com.github.mongobee.changelog.postponed;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.github.mongobee.utils.ChangeSetExecutionChecker;

@ChangeLog(order = "1")
public class PostponedChangeLog {

  public static final String NOT_POSTPONED = "NOT_POSTPONED";

  @ChangeSet(author = "testUser", id = "id1", order = "01", description = "")
  public void changeSet1(ChangeSetExecutionChecker executionChecker) {
    executionChecker.execute(NOT_POSTPONED);
  }

  @ChangeSet(author = "testUser", id = "id2", order = "02", description = "", postponed = true)
  public void changeSet2(ChangeSetExecutionChecker executionChecker) {
    executionChecker.execute("postponed");
  }

  @ChangeSet(author = "testUser", id = "id3", order = "03", description = "", postponed = true, runAlways = true)
  public void changeSet3(ChangeSetExecutionChecker executionChecker) {
    executionChecker.execute("postponed + runAlways");
  }

}
