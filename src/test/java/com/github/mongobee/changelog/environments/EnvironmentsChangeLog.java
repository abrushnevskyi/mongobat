package com.github.mongobee.changelog.environments;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.github.mongobee.utils.Environment;

@ChangeLog(order = "2")
public class EnvironmentsChangeLog {

  @ChangeSet(author = "testUser", id = "id1", order = "01", description = "", environment = Environment.DEV)
  public void changeSet1() {
    System.out.println("environment = " + Environment.DEV);
  }

  @ChangeSet(author = "testUser", id = "id2", order = "02", description = "", environment = Environment.TEST)
  public void changeSet2() {
    System.out.println("environment = " + Environment.TEST);
  }

  @ChangeSet(author = "testUser", id = "id3", order = "03", description = "", environment = Environment.STAGE)
  public void changeSet3() {
    System.out.println("environment = " + Environment.STAGE);
  }

  @ChangeSet(author = "testUser", id = "id4", order = "04", description = "", environment = Environment.PROD)
  public void changeSet4() {
    System.out.println("environment = " + Environment.PROD);
  }

  @ChangeSet(author = "testUser", id = "id5", order = "05", description = "")
  public void changeSet5() {
    System.out.println("environment = " + Environment.ANY);
  }

}
