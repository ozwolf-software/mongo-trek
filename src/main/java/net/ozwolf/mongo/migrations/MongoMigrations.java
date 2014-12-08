package net.ozwolf.mongo.migrations;

import com.googlecode.totallylazy.Option;
import com.googlecode.totallylazy.Sequence;
import com.mongodb.DB;
import net.ozwolf.mongo.migrations.exception.MongoMigrationsFailureException;
import net.ozwolf.mongo.migrations.internal.dao.DefaultSchemaVersionDAO;
import net.ozwolf.mongo.migrations.internal.dao.SchemaVersionDAO;
import net.ozwolf.mongo.migrations.internal.domain.Migration;
import net.ozwolf.mongo.migrations.internal.factory.MigrationsService;
import org.joda.time.DateTime;
import org.joda.time.Seconds;
import org.jongo.Jongo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * # Mongo Migrations
 *
 * The Mongo Migrations class allows an application to provide its own Mongo schema connection and a collection of migration commands to be
 * executed against the schema in question.  It persists migration state in the provides schema version collection, allowing retries of failed commands
 * and a later points.
 *
 * *Example Usage*
 *
 * ```java
 * public class MyApplication {
 *      public void start(){
 *          MongoClientUri uri = new MongoClientUri("mongo://root:password@localhost:27017/my_application");
 *
 *          Mongo mongo = new MongoClient(uri);
 *
 *          DB database = mongo.getDatabase(uri.getDatabase());
 *
 *          List<MongoCommand> commands = new ArrayList<>();
 *          commands.add(new FirstCommand());
 *          commands.add(new SecondCommand());
 *
 *          try {
 *              MongoMigrations migrations = new MongoMigration(database, uri.getUsername(), uri.getPassword());
 *              migrations.setSchemaVersionCollection("_schema_version_my_application");
 *              migrations.migrate(commands);
 *          } catch (MongoMigrationFailureException e) {
 *              LOGGER.error("Error migrating database, e);
 *          }
 *      }
 * }
 * ```
 */
public class MongoMigrations {
    private final DB db;
    private String schemaVersionCollection;

    private Jongo jongo;
    private SchemaVersionDAO schemaVersionDAO;
    private MigrationsService migrationsService;

    private final static Logger LOGGER = LoggerFactory.getLogger(MongoMigrations.class);
    private final static String DEFAULT_SCHEMA_VERSION_COLLECTION = "_schema_version";

    /**
     * Instantiate a new Mongo migrations class connecting to the provided database
     *
     * @param database The Mongo schema to connect to
     */
    public MongoMigrations(DB database) {
        this.db = database;
        this.schemaVersionCollection = DEFAULT_SCHEMA_VERSION_COLLECTION;
    }

    /**
     * Change the schema version collection from the default *_schema_version*
     *
     * @param collectionName The schema version collection name
     */
    public void setSchemaVersionCollection(String collectionName) {
        this.schemaVersionCollection = collectionName;
    }

    /**
     * Migrate the Mongo database using the provided collection of commands.  Will not apply versions already applied successfully.
     *
     * @param commands The commands to apply against the database.
     * @throws MongoMigrationsFailureException If the migration fails for whatever reason.
     */
    public void migrate(Collection<MigrationCommand> commands) throws MongoMigrationsFailureException {
        LOGGER.info("DATABASE MIGRATIONS");
        DateTime start = DateTime.now();
        AtomicInteger successfulCount = new AtomicInteger(0);
        try {
            connectTo();

            Option<Migration> lastSuccessful = this.schemaVersionDAO.findLastSuccessful();
            Sequence<Migration> pendingMigrations = migrationsService.getPendingMigrations(commands);

            logStatus("migrate", lastSuccessful);
            LOGGER.info(String.format("       Applying : [ %s ] -> [ %s ]", pendingMigrations.first().getVersion(), pendingMigrations.last().getVersion()));
            LOGGER.info("     Migrations :");
            for (Migration migration : pendingMigrations) applyMigration(successfulCount, migration);
        } catch (Throwable e) {
            LOGGER.error("Error applying migration(s)", e);
            throw new MongoMigrationsFailureException(e);
        } finally {
            DateTime finish = DateTime.now();
            LOGGER.info(String.format(">>> [ %d ] migrations applied in [ %d seconds ] <<<", successfulCount.get(), Seconds.secondsBetween(start, finish).getSeconds()));
            this.jongo = null;
            this.schemaVersionDAO = null;
        }
    }

    /**
     * Report the status of the migrations and provided commands.  Does not apply the migrations.
     *
     * @param commands The commands to report the status against.
     * @throws MongoMigrationsFailureException If the status report fails for whatever reason.
     */
    public void status(Collection<MigrationCommand> commands) throws MongoMigrationsFailureException {
        LOGGER.info("DATABASE MIGRATIONS");
        try {
            connectTo();

            Option<Migration> lastSuccessful = this.schemaVersionDAO.findLastSuccessful();
            Sequence<Migration> migrations = migrationsService.getFullState(commands);

            logStatus("status", lastSuccessful);
            LOGGER.info("     Migrations :");

            for (Migration migration : migrations) reportMigration(migration);
        } catch (Throwable e) {
            LOGGER.error("Error in commands and cannot provide status", e);
            throw new MongoMigrationsFailureException(e);
        } finally {
            this.jongo = null;
            this.schemaVersionDAO = null;
        }
    }

    private void logStatus(String action, Option<Migration> lastSuccessful) {
        LOGGER.info(String.format("       Database : [ %s ]", db.getName()));
        LOGGER.info(String.format(" Schema Version : [ %s ]", schemaVersionCollection));
        LOGGER.info(String.format("         Action : [ %s ]", action));
        LOGGER.info(String.format("Current Version : [ %s ]", (lastSuccessful.isDefined()) ? lastSuccessful.get().getVersion() : "n/a"));
    }

    private void applyMigration(AtomicInteger successfulCount, Migration migration) {
        try {
            LOGGER.info(String.format("       %s : %s", migration.getVersion(), migration.getDescription()));
            this.schemaVersionDAO.save(migration.running());
            migration.getCommand().migrate(jongo);
            this.schemaVersionDAO.save(migration.successful());
            successfulCount.incrementAndGet();
        } catch (Exception e) {
            this.schemaVersionDAO.save(migration.failed(e));
            throw e;
        }
    }

    private void reportMigration(Migration migration) {
        LOGGER.info(String.format("       %s : %s", migration.getVersion(), migration.getDescription()));
        LOGGER.info(String.format("          Tags: %s", migration.getTags()));
    }

    private void connectTo() throws MongoMigrationsFailureException {
        this.jongo = new Jongo(db);
        this.schemaVersionDAO = new DefaultSchemaVersionDAO(jongo, this.schemaVersionCollection);
        this.migrationsService = new MigrationsService(this.schemaVersionDAO);
    }
}
