![mongo-trek](https://raw.githubusercontent.com/ozwolf-software/mongo-trek/master/misc/mongotrek-logo.png)

[![Status](https://img.shields.io/badge/status-active-brightgreen.svg?style=flat-square)](https://img.shields.io/badge/status-active-brightgreen.svg)
[![Travis](https://img.shields.io/travis/ozwolf-software/mongo-trek.svg?style=flat-square)](https://travis-ci.org/ozwolf-software/mongo-trek)
[![Maven Central](https://img.shields.io/maven-central/v/net.ozwolf/mongo-trek.svg?style=flat-square)](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22net.ozwolf%22%20AND%20a%3A%22mongo-trek%22)
[![Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg?style=flat-square)](LICENSE)

**mongoTrek** is a a Java library inspired by [Liquibase](http://www.liquibase.org/) for managing collection and document migrations within your application's database.

This library is a "roll-forward" migration tool, meaning that to rollback changes, new migrations are required to undertake this task.

**Note**: As of version `2.0.0` mongoTrek only supports MongoDB version `3.4` and later.

## Java Mongo Migrations Upgrade

mongoTrek is a fork from the [Java Mongo Migrations](https://github.com/ozwolf-software/java-mongo-migrations) project.  As such, projects that have previously managed migrations using this project can upgrade to mongoTrek and it will understand the previous migrations schema version collection documents.

## Dependency

### Maven

```xml
<dependency>
    <groupId>net.ozwolf</groupId>
    <artifactId>mongo-trek</artifactId>
    <version>4.0.0</version>
</dependency>
```

### Gradle

```gradle
compile 'net.ozwolf:mongo-trek:4.0.0'
```

### Provided Dependencies

As part of your own project, you will need to include the following dependencies:

#### Mongo Java Driver

This library was tested against the `3.8.0` MongoDB library version.  If you require support for `3.4.3`, refer to the `2.0.1` version of the library. 

Build Version: `3.8.0`

```xml
<dependency>
    <groupId>org.mongodb</groupId>
    <artifactId>mongo-java-driver</artifactId>
    <version>[3.4.3,)</version>
</dependency>
```

#### Slf4j Implementation

This library is tested against the Logback Classic library at version `1.2.3` 

## MongoDB Database Commands

mongoTrek uses the MongoDB database commands framework to execute commands.  

Refer to the [MongoDB Database Commands](https://docs.mongodb.com/manual/reference/command/) documentation.

## Usage

### Define Your Migrations File

The migrations file for mongoTrek is a YAML or JSON file that is either a resource in your classpath or a file on your file system.  The library will first scan the classpath before the file system.

The file can define the `schemaVersionCollection` here.  If the schema version collection is _also_ defined via the `MongoTrek.setSchemaVersionCollection(<string>)` command, the value in the file will be ignored.

Each migration entry consists of:
 
+ `version` [ `REQUIRED` ] - A unique version identifier.  Migrations will be played in schemantic version order, regardless of their order in the migrations file (eg. version `2.0.0` will always be played ahead of `2.0.0.1`)
+ `description` [ `REQUIRED` ] - A short description of the migrations purpose
+ `author` [ `OPTIONAL` ] - The author of the migration.  If not supplied, the author will be recorded as `trekBot`
+ `command` [ `REQUIRED` ] - The database command to run.  Because mongoTrek uses YAML, this can be in the form of a direct JSON or YAML structure, as long as it meets the MongoDB Database Command requirements.

#### Example Migrations File

```yaml
schemaVersionCollection: my_schema_version
migrations:
    - version: 1.0.0
      description: populate people base data
      author: John Trek
      command: {
        insert: "people",
        documents: [
            { name: "Homer Simpson", age: 37 },
            { name: "Marge Simpson", age: 36 }
        ]
      }
    - version: 1.0.1
      description: populate town base data
      command: {
        insert: "town",
        documents: [
            { name: "Springfield", country: "USA" },
            { name: "Shelbyville", country: "USA" }
        ]
      }
```

#### Map-Reduce Forced Collection Creation

If mongoTrek encouters a `mapReduce` command, it will ensure the collection being reduced exists.  If it doesn't, it will run a simple `createCollection` call.  It will use the default collection settings defined [here](https://docs.mongodb.com/manual/reference/command/create/#dbcmd.create) in the MongoDB documentation.

#### Embedded Functions

For commands such as the [MapReduce Command](https://docs.mongodb.com/manual/reference/command/mapReduce/#mapreduce-reduce-cmd), functions should be enclosed as strings.  For example:

```yaml
migrations:
    - version: 1.0.0
      description: run a map-reduce
      command: {
        mapReduce: "towns",
        map: "function() { emit(this.country, 1); }",
        reduce: "function(country, towns) { return Array.sum(towns); }",
        out: "town_counts"
       }
```

#### Dates & Times

This library is designed to handle the `$date` strict JSON operator.  Internally, it will convert the value into a valid Java `Date` object for the Java driver to interpret correctly.

The library currently supports date, time and date-time strings in the formats compatible with following [DateTimeFormatter](https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html) predefined formatters.

### Running Your Migrations

To run your migrations, provide either a [MongoDB Connection String URI](https://docs.mongodb.com/manual/reference/connection-string/) or a `MongoDatabase` instance on initialization.

You can then either migrate your database (`MongoTrek.migrate(<file>)`) or request a status update (`MongoTrek.status(<file>)`).  Both methods will return a `MongoTrekState`, allowing you to query applied, pending and current migration versions.
 
#### Example Usage

```java
public class MyApplication {
    private final static Logger LOGGER = LoggerFactory.getLogger(MyApplication.class);
    
    public void start() {
        try {
            MongoTrek trek = new MongoTrek("mongodb/trek.yml", "mongodb://localhost:27017/my_app_schema");
                    
            trek.setSchemaVersionCollection("_my_custom_schema_version");
            
            MongoTrekState state = trek.migrate();
            
            LOGGER.info("Successfully migrated schema to version: " + state.getCurrentVersion());
        } catch (MongoTrekFailureException e) {
            LOGGER.error("Failed to migrate database", e);
            
            System.exit(-1);
        }
    }
}
```

### Migration Results

As of version `3.0.0` of this library, the `Migration` class now contains the migration result as a `Map<String, Object>`.

With a `MongoTrekState` you can get the list of applied transactions and review the migration command result.

### Logging Configuration

mongoTrek uses the [LOGBack](http://logback.qos.ch) project log outputs.

The logger in question is the `MongoTrek` class logger (ie. `Logger migrationsLogger = LoggerFactory.getLogger(MongoTrek.class);`)

You can configure the output of migrations logger using this class.

Messages are logged via the following levels:

+ `INFO` - All migration information (ie. configuration, versions, migration information)
+ `ERROR` - If an error occurs (ie. invalid migration command definition or general connection/execution errors)
