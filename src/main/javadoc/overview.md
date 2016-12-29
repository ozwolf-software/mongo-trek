# mongoTrek

**mongoTrek** is a a Java library inspired by [Liquibase](http://www.liquibase.org/) for managing collection and document migrations within your application's database.

## Java Mongo Migrations Upgrade

mongoTrek is a fork from the [Java Mongo Migrations](https://github.com/ozwolf-software/java-mongo-migrations) project.  As such, projects that have previously managed migrations using this project can upgrade to mongoTrek and it will understand the previous migrations schema version collection documents.

## Dependency

```xml
<dependency>
    <groupId>net.ozwolf</groupId>
    <artifactId>mongo-trek</artifactId>
    <version>${current.version}</version>
</dependency>
```

```gradle
compile 'au.com.ioof.asis.app:core:${current.version}'
```

### Provided Dependencies

As part of your own project, you will need to include the following dependencies:

#### Mongo Java Driver

Build Version: `3.2.0`

```xml
<dependency>
    <groupId>org.mongodb</groupId>
    <artifactId>mongo-java-driver</artifactId>
    <version>[3.2.0,)</version>
</dependency>
```

## MongoDB Database Commands

mongoTrek uses the MongoDB database commands framework to execute commands.  

Refer to the [MongoDB Database Commands](https://docs.mongodb.com/manual/reference/command/) documentation.

## Usage

### Define Your Migrations File

The migrations file for mongoTrek is a YAML or JSON file that is either a resource in your classpath or a file on your file system.  The library will first scan the classpath before the file system.

Each migration entry consists of:
 
+ `version` [ `REQUIRED` ] - A unique version identifier.  Migrations will be played in schemantic version order, regardless of their order in the migrations file (eg. version `2.0.0` will always be played ahead of `2.0.0.1`)
+ `description` [ `REQUIRED` ] - A short description of the migrations purpose
+ `author` [ `OPTIONAL` ] - The author of the migration.  If not supplied, the author will be recorded as `trekBot`
+ `command` [ `REQUIRED` ] - The database command to run.  Because mongoTrek uses YAML, this can be in the form of a direct JSON or YAML structure, as long as it meets the MongoDB Database Command requirements.

#### Example Migrations File

```yaml
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

### Running Your Migrations

To run your migrations, provide either a [MongoDB Connection String URI](https://docs.mongodb.com/manual/reference/connection-string/) or a `MongoDatabase` instance on initialization.

You can then either migrate your database (`MongoTrek.migrate(<file>)`) or request a status update (`MongoTrek.status(<file>)`).  Both methods will return a `MongoTrekState`, allowing you to query applied, pending and current migration versions.
 
#### Example Usage

```java
public class MyApplication {
    private final static Logger LOGGER = LoggerFactory.getLogger(MyApplication.class);
    
    public void start() {
        try {
            MongoTrek trek = new MongoTrek("mongodb://localhost:27017/my_app_schema");
                    
            trek.setSchemaVersionCollection("_my_custom_schema_version");
            
            MongoTrekState state = trek.migrate("mongodb/trek.yml");
            
            LOGGER.info("Successfully migrated schema to version: " + state.getCurrentVersion());
        } catch (MongoTrekFailureException e) {
            LOGGER.error("Failed to migrate database", e);
            
            System.exit(-1);
        }
    }
}
```

### Logging Configuration

Java Mongo Migrations uses the [LOGBack](http://logback.qos.ch) project log outputs.

The logger in question is the `MongoTrek` class logger (ie. `Logger migrationsLogger = LoggerFactory.getLogger(MongoTrek.class);`)

You can configure the output of migrations logger using this class.

Messages are logged via the following levels:

+ `INFO` - All migration information (ie. configuration, versions, migration information)
+ `ERROR` - If an error occurs (ie. invalid migration command definition or general connection/execution errors)

## Acknowledgements

+ [Fongo](https://github.com/foursquare/fongo)
+ [LOGBack](http://logback.qos.ch)