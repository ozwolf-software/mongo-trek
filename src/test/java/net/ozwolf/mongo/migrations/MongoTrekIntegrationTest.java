package net.ozwolf.mongo.migrations;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.github.fakemongo.FongoException;
import com.mongodb.MongoCommandException;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import net.ozwolf.mongo.migrations.exception.MongoTrekFailureException;
import net.ozwolf.mongo.migrations.internal.domain.Migration;
import net.ozwolf.mongo.migrations.internal.domain.MigrationStatus;
import net.ozwolf.mongo.migrations.rule.MongoDBServerRule;
import org.assertj.core.api.Condition;
import org.bson.Document;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static com.mongodb.client.model.Filters.and;
import static net.ozwolf.mongo.migrations.matchers.LoggingMatchers.loggedMessage;
import static net.ozwolf.mongo.migrations.matchers.MigrationMatchers.migrationOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

public class MongoTrekIntegrationTest {
    @ClassRule
    @Rule
    public final static MongoDBServerRule DATABASE = new MongoDBServerRule();

    private MongoDatabase database;

    private final static Logger LOGGER = (Logger) LoggerFactory.getLogger(MongoTrek.class);
    private final static String SCHEMA_VERSION_COLLECTION = "_schema_version";

    @SuppressWarnings("unchecked")
    private final Appender<ILoggingEvent> appender = mock(Appender.class);
    private final ArgumentCaptor<ILoggingEvent> captor = ArgumentCaptor.forClass(ILoggingEvent.class);

    @Before
    public void setUp() {
        this.database = DATABASE.getDatabase();

        this.database.getCollection(SCHEMA_VERSION_COLLECTION).drop();
        this.database.getCollection("first_migrations").drop();
        this.database.getCollection("second_migrations").drop();

        persistMigration("1.0.0", "Applied migration", "Homer Simpson", "2014-12-05T09:00:00.000+1100", "2014-12-05T09:00:02.000+1100", MigrationStatus.Successful, null);
        persistMigration("1.0.1", "Another applied migration", null, "2014-12-05T09:10:00.000+1100", "2014-12-05T09:11:00.000+1100", MigrationStatus.Successful, null);
        persistMigration("1.0.2", "Failed last time migration", "Marge Simpson", "2014-12-05T09:11:01.000+1100", null, MigrationStatus.Failed, "Something went horribly wrong!");

        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.INFO);
        LOGGER.setLevel(Level.INFO);
        LOGGER.addAppender(appender);
    }

    @Test
    public void shouldHandleZeroPendingMigrations() throws MongoTrekFailureException {
        MongoTrek migrations = new MongoTrek("fixtures/zero-pending-migrations.yml", this.database);
        migrations.setSchemaVersionCollection(SCHEMA_VERSION_COLLECTION);
        migrations.migrate();

        verify(appender, atLeastOnce()).doAppend(captor.capture());

        List<ILoggingEvent> events = captor.getAllValues();

        assertThat(events)
                .areAtLeastOne(loggedMessage("DATABASE MIGRATIONS"))
                .areAtLeastOne(loggedMessage("   No migrations to apply."));
    }

    @Test
    public void shouldHandleZeroCommandsProvided() throws MongoTrekFailureException {
        MongoTrek migrations = new MongoTrek("fixtures/zero-migrations.yml", this.database);
        migrations.setSchemaVersionCollection(SCHEMA_VERSION_COLLECTION);
        migrations.migrate();

        verify(appender, atLeastOnce()).doAppend(captor.capture());

        List<ILoggingEvent> events = captor.getAllValues();

        assertThat(events)
                .areAtLeastOne(loggedMessage("DATABASE MIGRATIONS"))
                .areAtLeastOne(loggedMessage("   No migrations to apply."));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldFailMigrationsOnLastMigration() {
        try {
            MongoTrek migrations = new MongoTrek("fixtures/last-failure-migrations.yml", this.database);
            migrations.setSchemaVersionCollection(SCHEMA_VERSION_COLLECTION);
            migrations.migrate();

            fail(String.format("Expected exception of [ %s ], but got [ none ]", MongoTrekFailureException.class.getSimpleName()));
        } catch (Exception e) {
            if (!(e instanceof MongoTrekFailureException))
                fail(String.format("Expected exception of [ %s ], but got [ %s ]", MongoTrekFailureException.class.getSimpleName(), e.getClass().getSimpleName()));

            assertThat(e.getCause()).isInstanceOf(MongoCommandException.class);
            assertThat(e.getMessage()).contains("mongoTrek failed: Command failed with error 59: 'no such command: 'rubbish', bad cmd: '{ rubbish: \"this should be unrecognised\" }''");

            validateMigrations(
                    migrationOf("1.0.0", MigrationStatus.Successful),
                    migrationOf("1.0.1", MigrationStatus.Successful),
                    migrationOf("1.0.2", MigrationStatus.Successful),
                    migrationOf("2.0.0", MigrationStatus.Successful),
                    migrationOf("2.0.1", MigrationStatus.Successful),
                    migrationOf("3.0.0", MigrationStatus.Failed)
            );

            assertThat(this.database.getCollection("first_migrations").count(and(Filters.eq("name", "Homer Simpson"), Filters.eq("age", 37)))).isEqualTo(1L);
            assertThat(this.database.getCollection("second_migrations").count(and(Filters.eq("town", "Shelbyville"), Filters.eq("country", "United States")))).isEqualTo(1L);

            assertThat(this.database.listCollectionNames())
                    .areAtLeastOne(new Condition<>(v -> v.equalsIgnoreCase("third_migration"), "collection_name=third_migration"))
                    .areAtLeastOne(new Condition<>(v -> v.equalsIgnoreCase("third_migration_work"), "collection_name=third_migration_work"));

            verify(appender, atLeastOnce()).doAppend(captor.capture());

            List<ILoggingEvent> events = captor.getAllValues();
            assertThat(events)
                    .areAtLeastOne(loggedMessage("DATABASE MIGRATIONS"))
                    .areAtLeastOne(loggedMessage("       Database : [ " + MongoDBServerRule.SCHEMA_NAME + " ]"))
                    .areAtLeastOne(loggedMessage(" Schema Version : [ _schema_version ]"))
                    .areAtLeastOne(loggedMessage("         Action : [ migrate ]"))
                    .areAtLeastOne(loggedMessage("Current Version : [ 1.0.1 ]"))
                    .areAtLeastOne(loggedMessage("       Applying : [ 1.0.2 ] -> [ 3.0.0 ]"))
                    .areAtLeastOne(loggedMessage("     Migrations :"))
                    .areAtLeastOne(loggedMessage("       1.0.2 : Failed last time migration"))
                    .areAtLeastOne(loggedMessage("       2.0.0 : Brand new migration"))
                    .areAtLeastOne(loggedMessage("       2.0.1 : Map reduce on non-existent collection"))
                    .areAtLeastOne(loggedMessage("       3.0.0 : I will always fail"))
                    .areAtLeastOne(loggedMessage("Error applying migration(s)"))
                    .areAtLeastOne(loggedMessage(">>> [ 3 ] migrations applied in [ 0 seconds ] <<<"));
        }
    }


    @Test
    public void shouldReportOnMigrations() throws MongoTrekFailureException {
        MongoTrek migrations = new MongoTrek("fixtures/last-failure-migrations.yml", this.database);
        migrations.setSchemaVersionCollection(SCHEMA_VERSION_COLLECTION);
        migrations.status(true);

        verify(appender, atLeastOnce()).doAppend(captor.capture());
        List<ILoggingEvent> events = captor.getAllValues();
        assertThat(events).areAtLeastOne(loggedMessage("DATABASE MIGRATIONS"))
                .areAtLeastOne(loggedMessage("       Database : [ " + MongoDBServerRule.SCHEMA_NAME + " ]"))
                .areAtLeastOne(loggedMessage(" Schema Version : [ _schema_version ]"))
                .areAtLeastOne(loggedMessage("         Action : [ status ]"))
                .areAtLeastOne(loggedMessage("Current Version : [ 1.0.1 ]"))
                .areAtLeastOne(loggedMessage("     Migrations :"))
                .areAtLeastOne(loggedMessage("       1.0.0 : Applied migration"))
                .areAtLeastOne(loggedMessage(String.format("          Tags: [ Successful ] [ %s ] [ 2 seconds ]", toTimeStamp("2014-12-05T09:00:00+1100"))))
                .areAtLeastOne(loggedMessage("       1.0.1 : Another applied migration"))
                .areAtLeastOne(loggedMessage(String.format("          Tags: [ Successful ] [ %s ] [ 60 seconds ]", toTimeStamp("2014-12-05T09:10:00+1100"))))
                .areAtLeastOne(loggedMessage("       1.0.2 : Failed last time migration"))
                .areAtLeastOne(loggedMessage(String.format("          Tags: [ Failed ] [ %s ] [ ERROR: Something went horribly wrong! ]", toTimeStamp("2014-12-05T09:11:01+1100"))))
                .areAtLeastOne(loggedMessage("       2.0.0 : Brand new migration"))
                .areAtLeastOne(loggedMessage("          Tags: [ Pending ]"))
                .areAtLeastOne(loggedMessage("       2.0.1 : Map reduce on non-existent collection"))
                .areAtLeastOne(loggedMessage("          Tags: [ Pending ]"))
                .areAtLeastOne(loggedMessage("       3.0.0 : I will always fail"))
                .areAtLeastOne(loggedMessage("          Tags: [ Pending ]"));
    }

    private static String toTimeStamp(String timeStamp) {
        return DateTime.parse(timeStamp).toDateTime(DateTimeZone.getDefault()).toString("yyyy-MM-dd HH:mm:ss");
    }

    @SafeVarargs
    private final void validateMigrations(Condition<Migration>... migrations) {
        List<Migration> records = new ArrayList<>();

        this.database.getCollection(SCHEMA_VERSION_COLLECTION)
                .find()
                .forEach((Consumer<Document>) d ->
                        records.add(
                                new Migration(
                                        d.getString("version"),
                                        d.getString("description"),
                                        d.getString("author"),
                                        Optional.ofNullable(d.getDate("started")).map(DateTime::new).orElse(null),
                                        Optional.ofNullable(d.getDate("finished")).map(DateTime::new).orElse(null),
                                        MigrationStatus.valueOf(d.getString("status")),
                                        d.getString("failureMessage")
                                )
                        )
                );

        assertThat(records).hasSize(migrations.length);

        for (Condition<Migration> checker : migrations)
            assertThat(records).areAtLeastOne(checker);
    }

    private void persistMigration(String version,
                                  String description,
                                  String author,
                                  String started,
                                  String finished,
                                  MigrationStatus status,
                                  String failureMessage) {
        Document document = new Document();
        document.put("version", version);
        document.put("description", description);
        document.put("author", Optional.ofNullable(author).orElse(Migration.DEFAULT_AUTHOR));
        document.put("started", Optional.ofNullable(started).map(DateTime::parse).map(DateTime::toDate).orElse(null));
        document.put("finished", Optional.ofNullable(finished).map(DateTime::parse).map(DateTime::toDate).orElse(null));
        document.put("status", status.name());
        document.put("failureMessage", failureMessage);

        this.database.getCollection(SCHEMA_VERSION_COLLECTION).insertOne(document);
    }
}