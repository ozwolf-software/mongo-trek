# Java Mongo Migrations

[![Build Status](https://travis-ci.org/ozwolf-software/java-mongo-migrations.svg?branch=master)](https://travis-ci.org/ozwolf-software/java-mongo-migrations)

This library is designed to allow tracked Mongo schema migrations inside a Java application, creating the ability to write code-based database migrations utilising your own Java driver connection to achieve this.

This library utilises the [Jongo](http://jongo.org) library for executing migrations against the Mongo database schema, while keeping track of migration state.
 
## Compile and Install

To use this library, clone it locally and run either of the following commands:

+ Local Repository: `mvn clean install`
+ Shared Repository: `mvn clean package` the upload into your shared repository.

To create the JAR, simply run `mvn clean package`

### Java 7 Version

For a Java 7 compilable version, see the `legacy-java7` branch.  The main reason for this is that Java 7 does not have the `Stream` object available and we use the `TotallyLazy` library to achieve this.
 
As of Java 8, the `Stream` object means we do not need the TotallyLazy library.

The Java 7 version is not maintained.

The version number for the Java 7 version is suffixed with `-jdk7`. 

## Usage

### Define Your Migrations

Migrations need to extend the Migration command and be named as such `V<version>__<name>`.

For example, `V1_0_0__MyFirstMigration` will be interpreted as version `1.0.0` with a description of `My first migration`.

For example:

```java
public class V1_0_0__MyFirstMigration extends MigrationCommand {
    @Override
    public void migrate(Jongo jongo) {
        jongo.getCollection("cities").insert("{'city': 'Sydney', 'country': 'Australia'}");
        jongo.getCollection("cities").insert("{'city': 'Melbourne', 'country': 'Australia'}");
        jongo.getCollection("cities").insert("{'city': 'London', 'country': 'United Kingdom'}");
        jongo.getCollection("cities").insert("{'city': 'New York', 'country': 'United States'}");
    }
}
```

### Running Your Migrations

This tool is meant to be run as part of your application's startup process (similar in theme to the [Flyway](http://flywaydb.org) toolset for MySQL in Java).  First, create a Mongo DB object that is a connection to your schema then create a `MongoMigrations` instance.  Finally, pass in your initialized command objects to the `migrate` command.
  
Commands passed to the `MongoMigrations` object must be instantiated.  This approach has been taken to allow you to define _how_ you instantiate your commands yourself (ie. Spring, Guice, etc.)

For example:

```java
public class MyApplication {
    public void start(){
        List<MongoCommand> commands = new ArrayList<>();
        commands.add(new FirstMigration());
        commands.add(new SecondMigration());
        
        try {
            MongoMigrations migrations = new MongoMigrations("mongo://localhost:27017/my_application_schema");
            migrations.setSchemaVersionCollection("_my_custom_schema_version");
            migrations.migrate(commands);
        } catch (MongoMigrationsFailureException e) {
            LOGGER.error("Failed to migrate database", e);
        }
    }
}
```

### Logging Configuration

Java Mongo Migrations uses the [LOGBack](http://logback.qos.ch) project log outputs.

The logger in question is the `MongoMigrations` class logger (ie. `Logger migrationsLogger = LoggerFactory.getLogger(MongoMigrations.class);`)

You can configure the output of migrations logger using this class.

Messages are logged via the following levels:

+ `INFO` - All migration information (ie. configuration, versions, migration information)
+ `ERROR` - If an error occurs (ie. invalid migration command definition or general connection/execution errors)

## Acknowledgements

+ [Jongo](http://jongo.org)
+ [Fongo](https://github.com/foursquare/fongo)
+ [LOGBack](http://logback.qos.ch)
+ [Flyway](http://flywaydb.org) _for inspiration_