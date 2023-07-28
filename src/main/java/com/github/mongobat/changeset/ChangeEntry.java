package com.github.mongobat.changeset;

import java.util.Date;

import com.github.mongobat.MongoBat;
import org.bson.Document;

/**
 * Entry in the changes collection log {@link MongoBat#DEFAULT_CHANGELOG_COLLECTION_NAME}
 * Type: entity class.
 *
 * @author lstolowski
 * @since 27/07/2014
 */
public class ChangeEntry {

  public static final String KEY_CHANGE_ID = "changeId";
  public static final String KEY_AUTHOR = "author";
  public static final String KEY_TIMESTAMP = "timestamp";
  public static final String KEY_CHANGELOG_CLASS = "changeLogClass";
  public static final String KEY_CHANGESET_METHOD = "changeSetMethod";
  public static final String KEY_DESCRIPTION = "description";
  public static final String KEY_GROUP = "group";
  public static final String KEY_ENVIRONMENT = "environment";
  public static final String KEY_POSTPONED = "postponed";
  public static final String KEY_REPEATABLE = "repeatable";

  private final String changeId;
  private final String author;
  private final Date timestamp;
  private final String changeLogClass;
  private final String changeSetMethodName;
  private final String description;
  private final String group;
  private final String environment;
  private final boolean postponed;
  private final boolean repeatable;

  public ChangeEntry(
      String changeId,
      String author,
      Date timestamp,
      String changeLogClass,
      String changeSetMethodName,
      String description,
      String group,
      String environment,
      boolean postponed,
      boolean repeatable
  ) {
    this.changeId = changeId;
    this.author = author;
    this.timestamp = new Date(timestamp.getTime());
    this.changeLogClass = changeLogClass;
    this.changeSetMethodName = changeSetMethodName;
    this.description = description;
    this.group = group;
    this.environment = environment;
    this.postponed = postponed;
    this.repeatable = repeatable;
  }

  public Document buildFullDBObject() {
    Document entry = new Document();

    entry.append(KEY_CHANGE_ID, this.changeId)
        .append(KEY_AUTHOR, this.author)
        .append(KEY_TIMESTAMP, this.timestamp)
        .append(KEY_CHANGELOG_CLASS, this.changeLogClass)
        .append(KEY_CHANGESET_METHOD, this.changeSetMethodName)
        .append(KEY_DESCRIPTION, this.description)
        .append(KEY_GROUP, this.group)
        .append(KEY_ENVIRONMENT, this.environment)
        .append(KEY_POSTPONED, this.postponed)
        .append(KEY_REPEATABLE, this.repeatable);

    return entry;
  }

  public Document buildSearchQueryDBObject() {
    return new Document()
        .append(KEY_CHANGE_ID, this.changeId)
        .append(KEY_AUTHOR, this.author);
  }

  @Override
  public String toString() {
    return "ChangeEntry{" +
        "changeId='" + changeId + '\'' +
        ", author='" + author + '\'' +
        ", timestamp=" + timestamp +
        ", changeLogClass='" + changeLogClass + '\'' +
        ", changeSetMethodName='" + changeSetMethodName + '\'' +
        ", description='" + description + '\'' +
        ", group='" + group + '\'' +
        ", environment='" + environment + '\'' +
        ", postponed=" + postponed +
        ", repeatable=" + repeatable +
        '}';
  }

  public String getChangeId() {
    return this.changeId;
  }

  public String getAuthor() {
    return this.author;
  }

  public Date getTimestamp() {
    return this.timestamp;
  }

  public String getChangeLogClass() {
    return this.changeLogClass;
  }

  public String getChangeSetMethodName() {
    return this.changeSetMethodName;
  }

  public String getDescription() {
    return description;
  }

  public String getGroup() {
    return group;
  }

  public String getEnvironment() {
    return environment;
  }

  public boolean isPostponed() {
    return postponed;
  }

  public boolean isRepeatable() {
    return repeatable;
  }
}
