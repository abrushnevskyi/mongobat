package com.github.mongobat.changeset;

public enum ChangeStatus {

  INSTALLED("installed"),
  FAILED("failed");

  private final String status;

  ChangeStatus(String status) {
    this.status = status;
  }

  public String getStatus() {
    return status;
  }
}
