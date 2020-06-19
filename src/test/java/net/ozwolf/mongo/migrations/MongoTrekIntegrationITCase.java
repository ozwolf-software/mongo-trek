package net.ozwolf.mongo.migrations;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.mongodb.MongoCommandException;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import net.ozwolf.mongo.migrations.exception.MongoTrekFailureException;
import net.ozwolf.mongo.migrations.internal.domain.Migration;
import net.ozwolf.mongo.migrations.internal.domain.MigrationStatus;
import net.ozwolf.mongo.migrations.extension.MongoDBServerExtension;
import net.ozwolf.mongo.migrations.testutils.DateTimeUtils;
import org.assertj.core.api.Condition;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static com.mongodb.client.model.Filters.and;
import static net.ozwolf.mongo.migrations.matchers.LoggingMatchers.loggedMessage;
import static net.ozwolf.mongo.migrations.matchers.MigrationMatchers.migrationOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;

class MongoTrekIntegrationITCase {
    @RegisterExtension
    final static MongoDBServerExtension DATABASE = new MongoDBServerExtension();

    private MongoDatabase database;

    private final static Logger LOGGER = (Logger) LoggerFactory.getLogger(MongoTrek.class);
    private final static String SCHEMA_VERSION_COLLECTION = "_schema_version";

    @SuppressWarnings("unchecked")
    private final Appender<ILoggingEvent> appender = mock(Appender.class);
    private final ArgumentCaptor<ILoggingEvent> captor = ArgumentCaptor.forClass(ILoggingEvent.class);

    @BeforeEach
    void setUp() {
        this.database = DATABASE.getDatabase();

        this.database.getCollection(SCHEMA_VERSION_COLLECTION).drop();
        this.database.getCollection("first_migrations").drop();
        this.database.getCollection("second_migrations").drop();

        persistMigration("1.0.0", "Applied migration", "Homer Simpson", "2014-12-04T22:00:00.000Z", "2014-12-04T22:00:02.000Z", MigrationStatus.Successful, null, new Document("n", 1));
        persistMigration("1.0.1", "Another applied migration", null, "2014-12-04T22:10:00.000Z", "2014-12-04T22:11:00.000Z", MigrationStatus.Successful, null, new Document("n", 1));
        persistMigration("1.0.2", "Failed last time migration", "Marge Simpson", "2014-12-04T22:11:01.000Z", null, MigrationStatus.Failed, "Something went horribly wrong!", null);

        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.INFO);
        LOGGER.setLevel(Level.INFO);
        LOGGER.addAppender(appender);
    }

    @Test
    void shouldHandleZeroPendingMigrations() throws MongoTrekFailureException {
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
    void shouldHandleZeroCommandsProvided() throws MongoTrekFailureException {
        MongoTrek migrations = new MongoTrek("fixtures/zero-migrations.yml", this.database);
        migrations.setSchemaVersionCollection(SCHEMA_VERSION_COLLECTION);
        migrations.migrate();

        verify(appender, atLeastOnce()).doAppend(captor.capture());

        List<ILoggingEvent> events = captor.getAllValues();

        assertThat(events)
                .areAtLeastOne(loggedMessage("DATABASE MIGRATIONS"))
                .areAtLeastOne(loggedMessage("   No migrations to apply."));
    }

    @Test
    void shouldFailMigrationsOnLastMigration() {
        try {
            MongoTrek migrations = new MongoTrek("fixtures/last-failure-migrations.yml", this.database);
            migrations.setSchemaVersionCollection(SCHEMA_VERSION_COLLECTION);
            migrations.migrate();

            fail(String.format("Expected exception of [ %s ], but got [ none ]", MongoTrekFailureException.class.getSimpleName()));
        } catch (Exception e) {
            if (!(e instanceof MongoTrekFailureException))
                fail(String.format("Expected exception of [ %s ], but got [ %s ]", MongoTrekFailureException.class.getSimpleName(), e.getClass().getSimpleName()));

            assertThat(e.getCause()).isInstanceOf(MongoCommandException.class);
            assertThat(e.getMessage()).contains("mongoTrek failed: Command failed with error 59 (CommandNotFound): 'no such command: 'rubbish', bad cmd: '{ rubbish: \"this should be unrecognised\" }''");

            validateMigrations(
                    migrationOf("1.0.0", MigrationStatus.Successful, new Document("n", 1)),
                    migrationOf("1.0.1", MigrationStatus.Successful, new Document("n", 1)),
                    migrationOf("1.0.2", MigrationStatus.Successful, new Document("n", 2).append("ok", 1.0)),
                    migrationOf("2.0.0", MigrationStatus.Successful, new Document("n", 2).append("ok", 1.0)),
                    migrationOf("2.0.1", MigrationStatus.Successful, new Document("result", "third_migration_work").append("ok", 1.0)),
                    migrationOf("3.0.0", MigrationStatus.Failed)
            );

            Date updatedAt = Date.from(Instant.parse("2018-12-18T00:29:33.123Z"));
            assertThat(this.database.getCollection("first_migrations").countDocuments(and(Filters.eq("name", "Homer Simpson"), Filters.eq("age", 37), Filters.eq("updatedAt", updatedAt)))).isEqualTo(1L);
            assertThat(this.database.getCollection("second_migrations").countDocuments(and(Filters.eq("town", "Shelbyville"), Filters.eq("country", "United States")))).isEqualTo(1L);

            assertThat(this.database.listCollectionNames())
                    .areAtLeastOne(new Condition<>(v -> v.equalsIgnoreCase("third_migration"), "collection_name=third_migration"))
                    .areAtLeastOne(new Condition<>(v -> v.equalsIgnoreCase("third_migration_work"), "collection_name=third_migration_work"));

            verify(appender, atLeastOnce()).doAppend(captor.capture());

            List<ILoggingEvent> events = captor.getAllValues();
            assertThat(events)
                    .areAtLeastOne(loggedMessage("DATABASE MIGRATIONS"))
                    .areAtLeastOne(loggedMessage("       Database : [ " + MongoDBServerExtension.SCHEMA_NAME + " ]"))
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
    void shouldReportOnMigrations() throws MongoTrekFailureException {
        MongoTrek migrations = new MongoTrek("fixtures/last-failure-migrations.yml", this.database);
        migrations.setSchemaVersionCollection(SCHEMA_VERSION_COLLECTION);
        migrations.status(true);

        verify(appender, atLeastOnce()).doAppend(captor.capture());
        List<ILoggingEvent> events = captor.getAllValues();
        assertThat(events).areAtLeastOne(loggedMessage("DATABASE MIGRATIONS"))
                .areAtLeastOne(loggedMessage("       Database : [ " + MongoDBServerExtension.SCHEMA_NAME + " ]"))
                .areAtLeastOne(loggedMessage(" Schema Version : [ _schema_version ]"))
                .areAtLeastOne(loggedMessage("         Action : [ status ]"))
                .areAtLeastOne(loggedMessage("Current Version : [ 1.0.1 ]"))
                .areAtLeastOne(loggedMessage("     Migrations :"))
                .areAtLeastOne(loggedMessage("       1.0.0 : Applied migration"))
                .areAtLeastOne(loggedMessage(String.format("          Tags: [ Successful ] [ %s ] [ 2 seconds ]", DateTimeUtils.formatDisplay("2014-12-05T09:00:00.000+1100"))))
                .areAtLeastOne(loggedMessage("       1.0.1 : Another applied migration"))
                .areAtLeastOne(loggedMessage(String.format("          Tags: [ Successful ] [ %s ] [ 60 seconds ]", DateTimeUtils.formatDisplay("2014-12-05T09:10:00.000+1100"))))
                .areAtLeastOne(loggedMessage("       1.0.2 : Failed last time migration"))
                .areAtLeastOne(loggedMessage(String.format("          Tags: [ Failed ] [ %s ] [ ERROR: Something went horribly wrong! ]", DateTimeUtils.formatDisplay("2014-12-05T09:11:01.000+1100"))))
                .areAtLeastOne(loggedMessage("       2.0.0 : Brand new migration"))
                .areAtLeastOne(loggedMessage("          Tags: [ Pending ]"))
                .areAtLeastOne(loggedMessage("       2.0.1 : Map reduce on non-existent collection"))
                .areAtLeastOne(loggedMessage("          Tags: [ Pending ]"))
                .areAtLeastOne(loggedMessage("       3.0.0 : I will always fail"))
                .areAtLeastOne(loggedMessage("          Tags: [ Pending ]"));
    }

    @SuppressWarnings("Duplicates")
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
                                        Optional.ofNullable(d.getDate("started")).map(Date::toInstant).orElse(null),
                                        Optional.ofNullable(d.getDate("finished")).map(Date::toInstant).orElse(null),
                                        MigrationStatus.valueOf(d.getString("status")),
                                        d.getString("failureMessage"),
                                        d.get("result", Document.class)
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
                                  String failureMessage,
                                  Document result) {
        Document document = new Document();
        document.put("version", version);
        document.put("description", description);
        document.put("author", Optional.ofNullable(author).orElse(Migration.DEFAULT_AUTHOR));
        document.put("started", Optional.ofNullable(started).map(Instant::parse).orElse(null));
        document.put("finished", Optional.ofNullable(finished).map(Instant::parse).orElse(null));
        document.put("status", status.name());
        document.put("failureMessage", failureMessage);
        document.put("result", result);

        this.database.getCollection(SCHEMA_VERSION_COLLECTION).insertOne(document);
    }
}