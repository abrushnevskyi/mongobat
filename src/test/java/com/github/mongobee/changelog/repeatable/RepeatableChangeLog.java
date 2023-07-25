package com.github.mongobee.changelog.repeatable;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.github.mongobee.utils.ChangeSetExecutionChecker;

@ChangeLog(order = "1")
public class RepeatableChangeLog {

  @ChangeSet(author = "testUser", id = "id1", order = "01", description = "", runAlways = true, repeatable = false)
  public void changeSet1(ChangeSetExecutionChecker executionChecker) {
    executionChecker.execute("111");
  }

  @ChangeSet(author = "testUser", id = "id2", order = "02", description = "", runAlways = true)
  public void changeSet2(ChangeSetExecutionChecker executionChecker) {
    executionChecker.execute("222");
  }

}
