package net.ozwolf.mongo.migrations;

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import net.ozwolf.mongo.migrations.exception.MongoTrekFailureException;
import net.ozwolf.mongo.migrations.internal.dao.DefaultSchemaVersionDAO;
import net.ozwolf.mongo.migrations.internal.dao.SchemaVersionDAO;
import net.ozwolf.mongo.migrations.internal.domain.Migration;
import net.ozwolf.mongo.migrations.internal.domain.MigrationCommands;
import net.ozwolf.mongo.migrations.internal.factory.MigrationCommandsFactory;
import net.ozwolf.mongo.migrations.internal.service.MigrationsService;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * <h1>Mongo Trek</h1>
 *
 * The mongoTrek main class allows an application to provide it's own `MongoDatabase` instance or MongoDB Connection string to then apply migrations to or report on the migration status of their database schema.
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class MongoTrek {
    private final MongoClient mongo;
    private final MongoDatabase database;
    private final String migrationsFile;

    private final boolean providedDatabase;

    private SchemaVersionDAO schemaVersionDAO;
    private MigrationsService migrationsServices;
    private MigrationCommandsFactory commandsFactory;
    private String schemaVersionCollection;

    private final static Logger LOGGER = LoggerFactory.getLogger(MongoTrek.class);
    private final static String DEFAULT_SCHEMA_VERSION_COLLECTION = "_schema_version";

    private final static AtomicReference<ClassLoader> CLASS_LOADER = new AtomicReference<>(MongoTrek.class.getClassLoader());

    /**
     * Create a new MongoTrek instance that will connect to the provided connection string.
     *
     * @param migrationsFile The YAML or JSON file containing your MongoDB migrations.
     * @param uri            The Mongo instance connection string
     * @see [MongoDB Connection String](https://docs.mongodb.com/manual/reference/connection-string/)
     */
    public MongoTrek(String migrationsFile, String uri) {
        this.migrationsFile = migrationsFile;
        ConnectionString clientURI = new ConnectionString(uri);
        if (clientURI.getDatabase() == null)
            throw new IllegalArgumentException("URI [ " + uri + " ] must contain a database schema to connect to.");

        this.mongo = MongoClients.create(clientURI);
        this.database = this.mongo.getDatabase(clientURI.getDatabase());
        this.providedDatabase = false;
        this.schemaVersionCollection = DEFAULT_SCHEMA_VERSION_COLLECTION;
    }

    /**
     * Create a new MongoTrek instance using a provided `MongoDatabase` instance.  MongoTrek will not close this connection.
     *
     * @param migrationsFile The YAML or JSON file containing your MongoDB migrations.
     * @param database       The `MongoDatabase` instance.
     */
    public MongoTrek(String migrationsFile, MongoDatabase database) {
        this.migrationsFile = migrationsFile;
        this.mongo = null;
        this.database = database;
        this.providedDatabase = true;
        this.schemaVersionCollection = DEFAULT_SCHEMA_VERSION_COLLECTION;
    }

    /**
     * Change the schema version collection from the default `_schema_version`
     *
     * @param collectionName The schema version collection name
     */
    public void setSchemaVersionCollection(String collectionName) {
        this.schemaVersionCollection = collectionName;
    }

    /**
     * Migrate the Mongo database using the provided collection of commands.  Will not apply versions already applied successfully.
     *
     * @return The trek state
     * @throws MongoTrekFailureException If the migration fails for whatever reason.
     */
    public MongoTrekState migrate() throws MongoTrekFailureException {
        LOGGER.info("DATABASE MIGRATIONS");
        MigrationCommands commands = commandsFactory().getCommands(migrationsFile, CLASS_LOADER.get());
        commands.getSchemaVersionCollection().ifPresent(n -> {
            if (schemaVersionCollection.equalsIgnoreCase(DEFAULT_SCHEMA_VERSION_COLLECTION))
                schemaVersionCollection = n;
        });

        MongoTrekState state = migrationsService().getState(commands);

        if (!commands.hasMigrations()) {
            LOGGER.info("   No migrations to apply.");
            return state;
        }

        Instant start = Instant.now();
        AtomicInteger successfulCount = new AtomicInteger(0);

        try {
            MongoTrekState.Pending pending = state.getPending();

            if (!pending.hasPendingMigrations()) {
                LOGGER.info("   No migrations to apply.");
                return state;
            }

            logStatus("migrate", state.getCurrentVersion());
            LOGGER.info(String.format("       Applying : [ %s ] -> [ %s ]", pending.getNextPendingVersion(), pending.getLastPendingVersion()));
            LOGGER.info("     Migrations :");

            pending.getMigrations().forEach(m -> applyMigration(successfulCount, m));

            // Get state after migrations have been applied.
            return migrationsService().getState(commands);
        } catch (Exception e) {
            LOGGER.error("Error applying migration(s)", e);
            throw new MongoTrekFailureException(e);
        } finally {
            Instant finish = Instant.now();
            LOGGER.info(String.format(">>> [ %d ] migrations applied in [ %d seconds ] <<<", successfulCount.get(), Duration.between(start, finish).getSeconds()));
            if (!this.providedDatabase) this.mongo.close();
        }
    }

    /**
     * Report the status of the migrations and provided commands.  Does not apply the migrations.
     *
     * By default, this method will not log status to the logger.  Refer to the {@link #status(boolean) status(boolean)} method.
     *
     * @return The trek state
     * @throws MongoTrekFailureException If the status report fails for whatever reason.
     */
    public MongoTrekState status() throws MongoTrekFailureException {
        return status(false);
    }

    /**
     * Report the status of the migrations and provided commands.  Does not apply the migrations.
     *
     * @param logStatus flag indicating if the state should be logged
     * @return The trek state
     * @throws MongoTrekFailureException If the status report fails for whatever reason.
     */
    public MongoTrekState status(boolean logStatus) throws MongoTrekFailureException {
        if (logStatus) LOGGER.info("DATABASE MIGRATIONS");

        MigrationCommands commands = commandsFactory().getCommands(migrationsFile, CLASS_LOADER.get());
        MongoTrekState state = migrationsService().getState(commands);

        try {
            if (logStatus) {
                logStatus("status", state.getCurrentVersion());
                LOGGER.info("     Migrations :");
                state.getMigrations().forEach(this::reportMigration);
            }

            return state;
        } catch (Exception e) {
            if (logStatus)
                LOGGER.error("Error in commands and cannot provide status", e);
            throw new MongoTrekFailureException(e);
        } finally {
            if (!this.providedDatabase) this.mongo.close();
        }
    }

    /**
     * Set the class loader for mongoTrek to use when loading migrations files from resource paths.
     *
     * By default, mongoTrek uses the class loader from `MongoTrek.class.getClassLoader()`, but sometimes this class loader is not relevant.
     *
     * @param classLoader the class loader for mongoTrek to use when loading resources
     */
    public static void setClassLoader(ClassLoader classLoader) {
        CLASS_LOADER.set(classLoader);
    }

    private void logStatus(String action, String currentVersion) {
        LOGGER.info(String.format("       Database : [ %s ]", this.database.getName()));
        LOGGER.info(String.format(" Schema Version : [ %s ]", schemaVersionCollection));
        LOGGER.info(String.format("         Action : [ %s ]", action));
        LOGGER.info(String.format("Current Version : [ %s ]", currentVersion));
    }

    private void applyMigration(AtomicInteger successfulCount, Migration migration) {
        try {
            LOGGER.info(String.format("       %s : %s", migration.getVersion(), migration.getDescription()));
            schemaVersionDAO().save(migration.running());
            Document result = migration.getCommand().migrate(this.database);
            schemaVersionDAO().save(migration.successful(result));
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

    private MigrationsService migrationsService() {
        if (migrationsServices == null)
            migrationsServices = new MigrationsService(schemaVersionDAO());
        return migrationsServices;
    }

    private SchemaVersionDAO schemaVersionDAO() {
        if (schemaVersionDAO == null)
            schemaVersionDAO = new DefaultSchemaVersionDAO(this.database.getCollection(schemaVersionCollection));
        return schemaVersionDAO;
    }

    private MigrationCommandsFactory commandsFactory() {
        if (commandsFactory == null)
            commandsFactory = new MigrationCommandsFactory();

        return commandsFactory;
    }
}
