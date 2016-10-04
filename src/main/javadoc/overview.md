# Java Mongo Migrations

[![Build Status](https://travis-ci.org/ozwolf-software/java-mongo-migrations.svg?branch=master)](https://travis-ci.org/ozwolf-software/java-mongo-migrations)

This library is designed to allow tracked Mongo schema migrations inside a Java application, creating the ability to write code-based database migrations utilising your own Java driver connection to achieve this.

This library utilises the [Jongo](http://jongo.org) library for executing migrations against the Mongo database schema, while keeping track of migration state.
 
## Dependency

```xml
<dependency>
    <groupId>net.ozwolf</groupId>
    <artifactId>java-mongo-migrations</artifactId>
    <version>4.0.0</version>
</dependency>
```

### Provided Dependencies

As part of your own project, you will need to include the following dependencies:

#### Mongo Java Driver

Build Version: `3.3.0`

```xml
<dependency>
    <groupId>org.mongodb</groupId>
    <artifactId>mongo-java-driver</artifactId>
    <version>[3.3.0,)</version>
</dependency>
```

#### Jongo

Build Version: `1.3.0`

```xml
<dependency>
    <groupId>org.jongo</groupId>
    <artifactId>jongo</artifactId>
    <version>[1.3,)</version>
</dependency>
```

## Usage

### Define Your Migrations

Migrations need to extend the Migration command and be named as such `V<version>__<name>`.

For example, `V1_0_0__MyFirstMigration` will be interpreted as version `1.0.0` with a description of `My first migration`.

For example:

```java
public class V1_0_0__MyFirstMigration extends MigrationCommand {
    {@literal @}Override
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