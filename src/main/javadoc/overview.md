# ${project.name} - ${project.version}

This library is designed to allow tracked Mongo schema migrations inside a Java application, creating the ability to write code-based database migrations utilising your own Java driver connection to achieve this.

This library utilises the [Jongo](http://jongo.org) library for executing migrations against the Mongo database schema, while keeping track of migration state.
 
## Compile and Install

To use this library, clone it locally and run either of the following commands:

+ Local Repository: `mvn clean install`
+ Central Repository: `mvn clean deploy`

To create the JAR, simply run `mvn clean package`

## Usage

### Define Your Migrations

Migrations require to be two components:

1. Implement the `MigrationCommand` interface
2. Be annotated with the `{@literal @}MongoMigration` annotation describing version information

For example:

```java
{@literal @}MongoMigration(version = "1.0.0", description = "My first migration")
public class MyFirstMigration implements MigrationCommand {
    public void migrate(Jongo jongo) {
        jongo.getCollection("cities").insert("{'city': 'Sydney', 'country': 'Australia'});
        jongo.getCollection("cities").insert("{'city': 'Melbourne', 'country': 'Australia'});
        jongo.getCollection("cities").insert("{'city': 'London', 'country': 'United Kingdom'});
        jongo.getCollection("cities").insert("{'city': 'New York', 'country': 'United States'});
    }
}
```

### Running Your Migrations

This tool is meant to be run as part of your application's startup process (similar in theme to the (Flyway)[http://flywaydb.org] toolset for MySQL in Java).  First, create a Mongo DB object that is a connection to your schema then create a `MongoMigrations` instance.  Finally, pass in your initialized command objects to the `migrate` command.
  
Commands passed to the `MongoMigrations` object must be instantiated.  This approach has been taken to allow you to define _how_ you instantiate your commands yourself (ie. Spring, Guice, etc.)

For example:

```java
public class MyApplication {
    public void start(){
        MongoClientUri uri = new MongoClientUri("mongo://localhost:27017/my_application_schema");
        Mongo mongo = new MongoClient(uri);
        DB db = mongo.getDatabase(uri.getDatabase());
        
        List<MongoCommand> commands = new ArrayList<>();
        commands.add(new FirstMigration());
        commands.add(new SecondMigration());
        
        try {
            MongoMigrations migrations = new MongoMigrations(db);
            migrations.setSchemaVersionCollection("_my_custom_schema_version");
            migrations.migrate(commands);
        } catch (MongoMigrationsFailureException e) {
            LOGGER.error("Failed to migrate database", e);
        }
    }
}
```

### Logging Configuration

Java Mongo Migrations uses the (LOGBack)[http://logback.qos.ch] project log outputs.

The logger in question is the `MongoMigrations` class logger (ie. `Logger migrationsLogger = LoggerFactory.getLogger(MongoMigrations.class);`)

You can configure the output of migrations logger using this class.

Messages are logged via the following levels:

+ `INFO` - All migration information (ie. configuration, versions, migration information)
+ `ERROR` - If an error occurs (ie. invalid migration command definition or general connection/execution errors)