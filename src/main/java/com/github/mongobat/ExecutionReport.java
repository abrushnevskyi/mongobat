package com.github.mongobat;

public class ExecutionReport {

  private final String installationId;
  private int scanned = 0;
  private int executed = 0;
  private int reExecuted = 0;
  private int skipped = 0;
  private int postponed = 0;
  private int failed = 0;

  public ExecutionReport(String installationId) {
    this.installationId = installationId;
  }

  public void merge(ExecutionReport report) {
    if (report == null) {
      return;
    }
    this.scanned += report.getScanned();
    this.executed += report.getExecuted();
    this.reExecuted += report.getReExecuted();
    this.skipped += report.getSkipped();
    this.postponed += report.getPostponed();
    this.failed += report.getFailed();
  }

  public String getInstallationId() {
    return installationId;
  }

  public int getScanned() {
    return scanned;
  }

  public void addScanned() {
    this.scanned++;
  }

  public void addScanned(int number) {
    this.scanned += number;
  }

  public int getExecuted() {
    return executed;
  }

  public void addExecuted() {
    this.executed++;
  }

  public int getReExecuted() {
    return reExecuted;
  }

  public void addReExecuted() {
    this.reExecuted++;
  }

  public int getSkipped() {
    return skipped;
  }

  public void addSkipped() {
    this.skipped++;
  }

  public int getPostponed() {
    return postponed;
  }

  public void addPostponed() {
    this.postponed++;
  }

  public int getFailed() {
    return failed;
  }

  public void addFailed() {
    this.failed++;
  }
}
