![mongobat]()

[![Licence](https://img.shields.io/hexpm/l/plug.svg)](https://github.com/mongobat/mongobat/blob/dev/LICENSE)
---


**MongoBat** is a Java tool which helps you to *manage changes* in your MongoDB and *synchronize* them with your application.
The concept is very similar to other db migration tools such as [Liquibase](http://www.liquibase.org) or [Flyway](http://flywaydb.org) but *without using XML/JSON/YML files*.

The goal is to keep this tool simple and comfortable to use.


**MongoBat** provides new approach for adding changes (change sets) based on Java classes and methods with appropriate annotations.

## Getting started

You need to instantiate MongoBumblebee object and provide some configuration. Then you have to run `execute()` method to start a migration process.

```java
MongoBat runner = new MongoBat("mongodb://YOUR_DB_HOST:27017/DB_NAME");
runner.setDbName("yourDbName");         // host must be set if not set in URI
runner.setChangeLogsScanPackage(
     "com.example.yourapp.changelogs"); // package to scan for changesets
runner.execute();                       //  ------> starts migration changesets
```

Above examples provide minimal configuration. `MongoBat` object provides some other possibilities (setters) to make the tool more flexible:

```java
runner.setChangelogCollectionName(logColName);   // default is dbchangelog, collection with applied change sets
runner.setLockCollectionName(lockColName);       // default is mongobatlock, collection used during migration process
runner.setEnabled(shouldBeEnabled);              // default is true, migration won't start if set to false
```

MongoDB URI format:
```
mongodb://[username:password@]host1[:port1][,host2[:port2],...[,hostN[:portN]]][/[database[.collection]][?options]]
```
[More about URI](http://mongodb.github.io/mongo-java-driver/3.5/javadoc/)


### Creating change logs

`ChangeLog` contains bunch of `ChangeSet`s. `ChangeSet` is a single task (set of instructions made on a database). In other words `ChangeLog` is a class annotated with `@ChangeLog` and containing methods annotated with `@ChangeSet`.

```java 
package com.example.yourapp.changelogs;

@ChangeLog
public class DatabaseChangelog {
  
    @ChangeSet(order = "001", id = "someChangeId", author = "testAuthor", description = "description")
    public void importantWorkToDo(){
        // task implementation
    }

}
```
#### @ChangeLog

Class with change sets must be annotated by `@ChangeLog`. There can be more than one change log class but in that case `order` argument should be provided:

```java
@ChangeLog(order = "001")
public class DatabaseChangelog {
  //...
}
```
ChangeLogs are sorted alphabetically by `order` argument and changesets are applied due to this order.

#### @ChangeSet

Method annotated by @ChangeSet is taken and applied to the database. History of applied change sets is stored in a collection called `dbchangelog` (by default) in your MongoDB

##### Annotation parameters:

`order` - string for sorting change sets in one changelog. Sorting in alphabetical order, ascending. It can be a number, a date etc.

`id` - name of a change set, **must be unique** for all change logs in a database

`author` - author of a change set

`description` - description of a change set

`runAlways` - _[optional, default: false]_ changeset will always be executed, information about execution will be updated in dbchangelog collection

`group` - _[optional, default: ""]_ group of a changeset

`environment` - _[optional, default: "ANY"]_ changeset will be executed only if environment match

`postponed` - _[optional, default: false]_ changeset marked as postponed will be skipped during global execution, _runAlways_ is ignored for postponed changesets


##### Defining ChangeSet methods
Method annotated by `@ChangeSet` can have one of the following definition:

```java
@ChangeSet(order = "001", id = "someChangeWithoutArgs", author = "testAuthor", description = "description")
public void someChange1() {
    // method without arguments can do some non-db changes
}

@ChangeSet(order = "002", id = "someChangeWithMongoDatabase", author = "testAuthor", description = "description")
public void someChange2(MongoDatabase db) {
    // type: com.mongodb.client.MongoDatabase : original MongoDB driver, operations allowed by driver are possible
    MongoCollection<Document> mycollection = db.getCollection("mycollection");
    Document doc = new Document("testName", "example").append("test", "1");
    mycollection.insertOne(doc);
}

@ChangeSet(order = "003", id = "someChangeWithCustomArgs", author = "testAuthor", description = "description")
public void someChange3(CustomService service) {
    // Any custom serwice can be used if defined during initialization via MongoBat::setChangeSetMethodParams
    service.doSomething();
}
```