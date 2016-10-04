package net.ozwolf.mongo.migrations;

import com.mongodb.DB;
import com.mongodb.Mongo;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import net.ozwolf.mongo.migrations.exception.MongoMigrationsFailureException;
import net.ozwolf.mongo.migrations.internal.dao.DefaultSchemaVersionDAO;
import net.ozwolf.mongo.migrations.internal.dao.SchemaVersionDAO;
import net.ozwolf.mongo.migrations.internal.domain.Migration;
import net.ozwolf.mongo.migrations.internal.service.MigrationsService;
import org.joda.time.DateTime;
import org.joda.time.Seconds;
import org.jongo.Jongo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <h1>Mongo Migrations</h1>
 *
 * The Mongo Migrations class allows an application to provide its own Mongo schema connection and a collection of migration commands to be
 * executed against the schema in question.  It persists migration state in the provides schema version collection, allowing retries of failed commands
 * and a later points.
 *
 * Any connection used by the MongoMigrations library will be closed.
 *
 * *Example Usage*
 *
 * ```java
 * public class MyApplication {
 * public void start(){
 * MongoClientUri uri = new MongoClientUri("mongo://root:password@localhost:27017/my_application");
 *
 * List<MongoCommand> commands = new ArrayList<>();
 * commands.add(new V1_0_0__FirstCommand());
 * commands.add(new V1_0_1__SecondCommand());
 *
 * try {
 * MongoMigrations migrations = new MongoMigrations(uri);
 * migrations.setSchemaVersionCollection("_schema_version_my_application");
 * migrations.migrate(commands);
 * } catch (MongoMigrationFailureException e) {
 * LOGGER.error("Error migrating database, e);
 * }
 * }
 * }
 * ```
 */
public class MongoMigrations {
    private final Jongo jongo;

    private Optional<SchemaVersionDAO> schemaVersionDAO;
    private Optional<MigrationsService> migrationsServices;
    private String schemaVersionCollection;

    private final static Logger LOGGER = LoggerFactory.getLogger(MongoMigrations.class);
    private final static String DEFAULT_SCHEMA_VERSION_COLLECTION = "_schema_version";

    /**
     * Create a new Mongo migrations instance that will connect to the provided URI.  This method is self-contained and
     * will open a connection, run the migrations and close the connection again.  Ideal for projects that don't normally use
     * Jongo as a connection library.
     *
     * @param uri The Mongo instance connection URI
     */
    public MongoMigrations(String uri) {
        this.jongo = new Jongo(connectTo(new MongoClientURI(uri)));
        this.schemaVersionCollection = DEFAULT_SCHEMA_VERSION_COLLECTION;
        this.schemaVersionDAO = Optional.empty();
        this.migrationsServices = Optional.empty();
    }

    /**
     * Create a new MongoMigrations using a database factory definition to connect.
     *
     * *Note* MongoMigrations assumes it owns the associated connection and __will__ close it on completion.
     *
     * @param factory The database factory.
     */
    public MongoMigrations(DBFactory factory) {
        this.jongo = new Jongo(factory.connectTo());
        this.schemaVersionCollection = DEFAULT_SCHEMA_VERSION_COLLECTION;
        this.schemaVersionDAO = Optional.empty();
        this.migrationsServices = Optional.empty();
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
        if (commands.isEmpty()) {
            LOGGER.info("   No migrations to apply.");
            return;
        }

        DateTime start = DateTime.now();
        AtomicInteger successfulCount = new AtomicInteger(0);

        try {
            Optional<Migration> lastSuccessful = migrationsService().getLastSuccessful();
            List<Migration> pendingMigrations = migrationsService().getPendingMigrations(commands);
            if (pendingMigrations.isEmpty()) {
                LOGGER.info("   No migrations to apply.");
                return;
            }

            Migration firstMigration = pendingMigrations.stream().findFirst().get();
            Migration lastMigration = pendingMigrations.stream().reduce((a, b) -> b).get();

            logStatus("migrate", lastSuccessful);
            LOGGER.info(String.format("       Applying : [ %s ] -> [ %s ]", firstMigration.getVersion(), lastMigration.getVersion()));
            LOGGER.info("     Migrations :");

            pendingMigrations.stream().forEach(m -> applyMigration(jongo, successfulCount, m));
        } catch (Exception e) {
            LOGGER.error("Error applying migration(s)", e);
            throw new MongoMigrationsFailureException(e);
        } finally {
            DateTime finish = DateTime.now();
            LOGGER.info(String.format(">>> [ %d ] migrations applied in [ %d seconds ] <<<", successfulCount.get(), Seconds.secondsBetween(start, finish).getSeconds()));
            this.jongo.getDatabase().getMongo().close();
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
            Optional<Migration> lastSuccessful = migrationsService().getLastSuccessful();
            List<Migration> migrations = migrationsService().getFullState(commands);

            logStatus("status", lastSuccessful);
            LOGGER.info("     Migrations :");

            migrations.stream().forEach(this::reportMigration);
        } catch (Exception e) {
            LOGGER.error("Error in commands and cannot provide status", e);
            throw new MongoMigrationsFailureException(e);
        } finally {
            this.jongo.getDatabase().getMongo().close();
        }
    }

    private void logStatus(String action, Optional<Migration> lastSuccessful) {
        LOGGER.info(String.format("       Database : [ %s ]", this.jongo.getDatabase().getName()));
        LOGGER.info(String.format(" Schema Version : [ %s ]", schemaVersionCollection));
        LOGGER.info(String.format("         Action : [ %s ]", action));
        LOGGER.info(String.format("Current Version : [ %s ]", (lastSuccessful.isPresent()) ? lastSuccessful.get().getVersion() : "n/a"));
    }

    private void applyMigration(Jongo jongo, AtomicInteger successfulCount, Migration migration) {
        try {
            LOGGER.info(String.format("       %s : %s", migration.getVersion(), migration.getDescription()));
            schemaVersionDAO().save(migration.running());
            migration.getCommand().migrate(jongo);
            schemaVersionDAO().save(migration.successful());
            successfulCount.incrementAndGet();
        } catch (Exception e) {
            schemaVersionDAO().save(migration.failed(e));
            throw e;
        }
    }

    private void reportMigration(Migration migration) {
        LOGGER.info(String.format("       %s : %s", migration.getVersion(), migration.getDescription()));
        LOGGER.info(String.format("          Tags: %s", migration.getTags()));
    }

    private DB connectTo(final MongoClientURI uri) {
        Mongo mongo = new MongoClient(uri);
        return mongo.getDB(uri.getDatabase());
    }

    private MigrationsService migrationsService() {
        if (!migrationsServices.isPresent())
            migrationsServices = Optional.of(new MigrationsService(schemaVersionDAO()));
        return migrationsServices.get();
    }

    private SchemaVersionDAO schemaVersionDAO() {
        if (!schemaVersionDAO.isPresent())
            schemaVersionDAO = Optional.of(new DefaultSchemaVersionDAO(this.jongo, this.schemaVersionCollection));
        return schemaVersionDAO.get();
    }

    public static interface DBFactory {
        DB connectTo();
    }
}
