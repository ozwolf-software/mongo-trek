package net.ozwolf.mongo.migrations;

import com.mongodb.DB;
import com.mongodb.Mongo;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import net.ozwolf.mongo.migrations.exception.MongoMigrationsFailureException;
import net.ozwolf.mongo.migrations.internal.dao.DefaultSchemaVersionDAO;
import net.ozwolf.mongo.migrations.internal.dao.SchemaVersionDAO;
import net.ozwolf.mongo.migrations.internal.domain.Migration;
import net.ozwolf.mongo.migrations.internal.domain.MigrationsState;
import net.ozwolf.mongo.migrations.internal.service.MigrationsService;
import org.joda.time.DateTime;
import org.joda.time.Seconds;
import org.jongo.Jongo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
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
 * ##Example Usage
 *
 * ```java
 * public class MyApplication {
 *      public void start(){
 *          MongoClientUri uri = new MongoClientUri("mongo://root:password@localhost:27017/my_application");
 *
 *          List<MongoCommand> commands = new ArrayList<>();
 *          commands.add(new V1_0_0__FirstCommand());
 *          commands.add(new V1_0_1__SecondCommand());
 *
 *          try {
 *              MongoMigrations migrations = new MongoMigrations(uri);
 *              migrations.setSchemaVersionCollection("_schema_version_my_application");
 *              MigrationsState state = migrations.migrate(commands);
 *
 *              LOGGER.info("Completed migrations to version " + state.getCurrentVersion());
 *          } catch (MongoMigrationFailureException e) {
 *              LOGGER.error("Error migrating database, e);
 *          }
 *      }
 * }
 * ```
 */
@SuppressWarnings({"OptionalGetWithoutIsPresent", "WeakerAccess", "unused"})
public class MongoMigrations {
    private final Jongo jongo;

    private final boolean providedJongo;

    private SchemaVersionDAO schemaVersionDAO;
    private MigrationsService migrationsServices;
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
        this.providedJongo = false;
        this.schemaVersionCollection = DEFAULT_SCHEMA_VERSION_COLLECTION;
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
        this.providedJongo = false;
        this.schemaVersionCollection = DEFAULT_SCHEMA_VERSION_COLLECTION;
    }

    /**
     * Create a new MongoMigrations using a provided and existing Jongo connection.
     *
     * *Note* MongoMigrations assumes it does not own the Jongo connection and __will not__ close it on completion.
     * @param jongo The Jongo connection
     */
    public MongoMigrations(Jongo jongo){
        this.jongo = jongo;
        this.providedJongo = true;
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
     * @return MigrationsState The final state of the migrations upon completion.
     * @throws MongoMigrationsFailureException If the migration fails for whatever reason.
     */
    public MigrationsState migrate(Collection<MigrationCommand> commands) throws MongoMigrationsFailureException {
        LOGGER.info("DATABASE MIGRATIONS");
        MigrationsState state = migrationsService().getState(commands);
        if (commands.isEmpty()) {
            LOGGER.info("   No migrations to apply.");
            return state;
        }

        DateTime start = DateTime.now();
        AtomicInteger successfulCount = new AtomicInteger(0);

        try {
            MigrationsState.Pending pending = state.getPending();

            if (!pending.hasPendingMigrations()){
                LOGGER.info("   No migrations to apply.");
                return state;
            }

            logStatus("migrate", state.getCurrentVersion());
            LOGGER.info(String.format("       Applying : [ %s ] -> [ %s ]", pending.getNextPendingVersion(), pending.getLastPendingVersion()));
            LOGGER.info("     Migrations :");

            pending.getMigrations().stream().forEach(m -> applyMigration(jongo, successfulCount, m));

            // Get state after migrations have been applied.
            return migrationsService().getState(commands);
        } catch (Exception e) {
            LOGGER.error("Error applying migration(s)", e);
            throw new MongoMigrationsFailureException(e);
        } finally {
            DateTime finish = DateTime.now();
            LOGGER.info(String.format(">>> [ %d ] migrations applied in [ %d seconds ] <<<", successfulCount.get(), Seconds.secondsBetween(start, finish).getSeconds()));
            if (!this.providedJongo)
                this.jongo.getDatabase().getMongo().close();
        }
    }

    /**
     * Report the status of the migrations and provided commands.  Does not apply the migrations.
     *
     * @param commands The commands to report the status against.
     * @throws MongoMigrationsFailureException If the status report fails for whatever reason.
     */
    public MigrationsState status(Collection<MigrationCommand> commands) throws MongoMigrationsFailureException {
        LOGGER.info("DATABASE MIGRATIONS");
        MigrationsState state = migrationsService().getState(commands);
        try {
            logStatus("status", state.getCurrentVersion());
            LOGGER.info("     Migrations :");

            state.getMigrations().stream().forEach(this::reportMigration);

            return state;
        } catch (Exception e) {
            LOGGER.error("Error in commands and cannot provide status", e);
            throw new MongoMigrationsFailureException(e);
        } finally {
            if (!this.providedJongo)
                this.jongo.getDatabase().getMongo().close();
        }
    }

    private void logStatus(String action, String currentVersion) {
        LOGGER.info(String.format("       Database : [ %s ]", this.jongo.getDatabase().getName()));
        LOGGER.info(String.format(" Schema Version : [ %s ]", schemaVersionCollection));
        LOGGER.info(String.format("         Action : [ %s ]", action));
        LOGGER.info(String.format("Current Version : [ %s ]", currentVersion));
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

    @SuppressWarnings("deprecation")
    private DB connectTo(final MongoClientURI uri) {
        Mongo mongo = new MongoClient(uri);
        return mongo.getDB(uri.getDatabase());
    }

    private MigrationsService migrationsService() {
        if (migrationsServices == null)
            migrationsServices = new MigrationsService(schemaVersionDAO());
        return migrationsServices;
    }

    private SchemaVersionDAO schemaVersionDAO() {
        if (schemaVersionDAO == null)
            schemaVersionDAO = new DefaultSchemaVersionDAO(this.jongo, this.schemaVersionCollection);
        return schemaVersionDAO;
    }

    public interface DBFactory {
        DB connectTo();
    }
}
