package net.ozwolf.mongo.migrations;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.github.fakemongo.Fongo;
import com.googlecode.totallylazy.Sequence;
import com.googlecode.totallylazy.Sequences;
import com.mongodb.DB;
import net.ozwolf.mongo.migrations.exception.MongoMigrationsFailureException;
import net.ozwolf.mongo.migrations.internal.domain.Migration;
import net.ozwolf.mongo.migrations.internal.domain.MigrationStatus;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.joda.time.DateTime;
import org.jongo.Jongo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.googlecode.totallylazy.Sequences.sequence;
import static net.ozwolf.mongo.migrations.matchers.LoggingMatchers.loggedMessage;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

public class MongoMigrationsIntegrationTest {
    private final static Fongo FONGO = new Fongo("migration test");
    private final static DB DATABASE = FONGO.getDB("migration_test");
    private final static Jongo JONGO = new Jongo(DATABASE);

    private final static Logger LOGGER = (Logger) LoggerFactory.getLogger(MongoMigrations.class);
    private final static String SCHEMA_VERSION_COLLECTION = "_schema_version";

    @SuppressWarnings("unchecked")
    private final Appender<ILoggingEvent> appender = mock(Appender.class);
    private final ArgumentCaptor<ILoggingEvent> captor = ArgumentCaptor.forClass(ILoggingEvent.class);

    @Before
    public void setUp() {
        JONGO.getCollection(SCHEMA_VERSION_COLLECTION).drop();
        JONGO.getCollection("first_migrations").drop();
        JONGO.getCollection("second_migrations").drop();
        Migration migration100 = new Migration("1.0.0", "Applied migration", DateTime.parse("2014-12-05T09:00:00.000+1100"), DateTime.parse("2014-12-05T09:00:02.000+1100"), MigrationStatus.Successful, null);
        Migration migration101 = new Migration("1.0.1", "Another applied migration", DateTime.parse("2014-12-05T09:10:00.000+1100"), DateTime.parse("2014-12-05T09:11:00.000+1100"), MigrationStatus.Successful, null);
        Migration migration102 = new Migration("1.0.2", "Failed last time migration", DateTime.parse("2014-12-05T09:11:01.000+1100"), null, MigrationStatus.Failed, "Something went horribly wrong!");

        JONGO.getCollection(SCHEMA_VERSION_COLLECTION).save(migration100);
        JONGO.getCollection(SCHEMA_VERSION_COLLECTION).save(migration101);
        JONGO.getCollection(SCHEMA_VERSION_COLLECTION).save(migration102);

        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.INFO);
        LOGGER.setLevel(Level.INFO);
        LOGGER.addAppender(appender);
    }

    @After
    public void tearDown() {
        JONGO.getCollection(SCHEMA_VERSION_COLLECTION).drop();
        JONGO.getCollection("first_migrations").drop();
        JONGO.getCollection("second_migrations").drop();
        LOGGER.detachAppender(appender);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldRetryFailedMigrationsAndApplyNewOnesAndCompleteSuccessfully() throws MongoMigrationsFailureException {
        Sequence<MigrationCommand> commands = sequence(
                new Migration100(),
                new Migration200(),
                new Migration102(),
                new Migration101()
        );

        MongoMigrations migrations = new MongoMigrations(DATABASE);
        migrations.setSchemaVersionCollection(SCHEMA_VERSION_COLLECTION);
        migrations.migrate(commands);

        validateMigrations(
                migrationOf("1.0.0", MigrationStatus.Successful),
                migrationOf("1.0.1", MigrationStatus.Successful),
                migrationOf("1.0.2", MigrationStatus.Successful),
                migrationOf("2.0.0", MigrationStatus.Successful)
        );

        assertThat(JONGO.getCollection("first_migrations").findOne("{'name':'Homer Simpson'}").map(r -> (Integer) r.get("age")), is(37));
        assertThat(JONGO.getCollection("second_migrations").findOne("{'town':'Shelbyville'}").map(r -> (String) r.get("country")), is("United States"));

        verify(appender, atLeastOnce()).doAppend(captor.capture());

        List<ILoggingEvent> events = captor.getAllValues();
        assertThat(events, hasItem(loggedMessage("DATABASE MIGRATIONS")));
        assertThat(events, hasItem(loggedMessage("       Database : [ migration_test ]")));
        assertThat(events, hasItem(loggedMessage(" Schema Version : [ _schema_version ]")));
        assertThat(events, hasItem(loggedMessage("         Action : [ migrate ]")));
        assertThat(events, hasItem(loggedMessage("Current Version : [ 1.0.1 ]")));
        assertThat(events, hasItem(loggedMessage("       Applying : [ 1.0.2 ] -> [ 2.0.0 ]")));
        assertThat(events, hasItem(loggedMessage("     Migrations :")));
        assertThat(events, hasItem(loggedMessage("       1.0.2 : Failed last time migration")));
        assertThat(events, hasItem(loggedMessage("       2.0.0 : Brand new migration")));
        assertThat(events, hasItem(loggedMessage(">>> [ 2 ] migrations applied in [ 0 seconds ] <<<")));
    }

    @Test
    public void shouldHandleZeroCommandsProvided() throws MongoMigrationsFailureException {
        MongoMigrations migrations = new MongoMigrations(DATABASE);
        migrations.setSchemaVersionCollection(SCHEMA_VERSION_COLLECTION);
        migrations.migrate(Sequences.<MigrationCommand>sequence());

        verify(appender, atLeastOnce()).doAppend(captor.capture());

        List<ILoggingEvent> events = captor.getAllValues();

        assertThat(events, hasItem(loggedMessage("DATABASE MIGRATIONS")));
        assertThat(events, hasItem(loggedMessage("   No migrations to apply.")));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldFailMigrationsOnLastMigration() {
        Sequence<MigrationCommand> commands = sequence(
                new Migration100(),
                new Migration200(),
                new Migration2001(),
                new Migration102(),
                new Migration101()
        );

        try {
            MongoMigrations migrations = new MongoMigrations(DATABASE);
            migrations.setSchemaVersionCollection(SCHEMA_VERSION_COLLECTION);
            migrations.migrate(commands);

            fail(String.format("Expected exception of [ %s ], but got [ none ]", MongoMigrationsFailureException.class.getSimpleName()));
        } catch (Exception e) {
            if (!(e instanceof MongoMigrationsFailureException))
                fail(String.format("Expected exception of [ %s ], but got [ %s ]", MongoMigrationsFailureException.class.getSimpleName(), e.getClass().getSimpleName()));

            assertThat(e.getCause(), instanceOf(IllegalArgumentException.class));
            assertThat(e.getMessage(), is("Mongo migrations failed: This is an exception that never ends!"));

            validateMigrations(
                    migrationOf("1.0.0", MigrationStatus.Successful),
                    migrationOf("1.0.1", MigrationStatus.Successful),
                    migrationOf("1.0.2", MigrationStatus.Successful),
                    migrationOf("2.0.0", MigrationStatus.Successful),
                    migrationOf("2.0.0.1", MigrationStatus.Failed)
            );

            assertThat(JONGO.getCollection("first_migrations").findOne("{'name':'Homer Simpson'}").map(r -> (Integer) r.get("age")), is(37));
            assertThat(JONGO.getCollection("second_migrations").findOne("{'town':'Shelbyville'}").map(r -> (String) r.get("country")), is("United States"));

            verify(appender, atLeastOnce()).doAppend(captor.capture());

            List<ILoggingEvent> events = captor.getAllValues();
            assertThat(events, hasItem(loggedMessage("DATABASE MIGRATIONS")));
            assertThat(events, hasItem(loggedMessage("       Database : [ migration_test ]")));
            assertThat(events, hasItem(loggedMessage(" Schema Version : [ _schema_version ]")));
            assertThat(events, hasItem(loggedMessage("         Action : [ migrate ]")));
            assertThat(events, hasItem(loggedMessage("Current Version : [ 1.0.1 ]")));
            assertThat(events, hasItem(loggedMessage("       Applying : [ 1.0.2 ] -> [ 2.0.0.1 ]")));
            assertThat(events, hasItem(loggedMessage("     Migrations :")));
            assertThat(events, hasItem(loggedMessage("       1.0.2 : Failed last time migration")));
            assertThat(events, hasItem(loggedMessage("       2.0.0 : Brand new migration")));
            assertThat(events, hasItem(loggedMessage("       2.0.0.1 : I will always fail")));
            assertThat(events, hasItem(loggedMessage("Error applying migration(s)")));
            assertThat(events, hasItem(loggedMessage(">>> [ 2 ] migrations applied in [ 0 seconds ] <<<")));
        }
    }

    @Test
    public void shouldReportOnMigrations() throws MongoMigrationsFailureException {
        Sequence<MigrationCommand> commands = sequence(
                new Migration100(),
                new Migration200(),
                new Migration2001(),
                new Migration102(),
                new Migration101()
        );

        MongoMigrations migrations = new MongoMigrations(DATABASE);
        migrations.setSchemaVersionCollection(SCHEMA_VERSION_COLLECTION);
        migrations.status(commands);

        verify(appender, atLeastOnce()).doAppend(captor.capture());
        List<ILoggingEvent> events = captor.getAllValues();
        assertThat(events, hasItem(loggedMessage("DATABASE MIGRATIONS")));
        assertThat(events, hasItem(loggedMessage("       Database : [ migration_test ]")));
        assertThat(events, hasItem(loggedMessage(" Schema Version : [ _schema_version ]")));
        assertThat(events, hasItem(loggedMessage("         Action : [ status ]")));
        assertThat(events, hasItem(loggedMessage("Current Version : [ 1.0.1 ]")));
        assertThat(events, hasItem(loggedMessage("     Migrations :")));
        assertThat(events, hasItem(loggedMessage("       1.0.0 : Applied migration")));
        assertThat(events, hasItem(loggedMessage("          Tags: [ Successful ] [ 2014-12-05 09:00:00 ] [ 2 seconds ]")));
        assertThat(events, hasItem(loggedMessage("       1.0.1 : Another applied migration")));
        assertThat(events, hasItem(loggedMessage("          Tags: [ Successful ] [ 2014-12-05 09:10:00 ] [ 60 seconds ]")));
        assertThat(events, hasItem(loggedMessage("       1.0.2 : Failed last time migration")));
        assertThat(events, hasItem(loggedMessage("          Tags: [ Failed ] [ 2014-12-05 09:11:01 ] [ ERROR: Something went horribly wrong! ]")));
        assertThat(events, hasItem(loggedMessage("       2.0.0 : Brand new migration")));
        assertThat(events, hasItem(loggedMessage("          Tags: [ Pending ]")));
        assertThat(events, hasItem(loggedMessage("       2.0.0.1 : I will always fail")));
        assertThat(events, hasItem(loggedMessage("          Tags: [ Pending ]")));
    }

    @SuppressWarnings("unchecked")
    private void validateMigrations(TypeSafeMatcher<Migration>... migrations) {
        Sequence<Migration> records = sequence(JONGO.getCollection(SCHEMA_VERSION_COLLECTION).find().as(Migration.class));
        assertThat(records.size(), is(migrations.length));
        for (TypeSafeMatcher<Migration> checker : migrations)
            assertThat(records, hasItem(checker));
    }

    @MongoMigration(version = "1.0.0", description = "Applied migration")
    public static class Migration100 implements MigrationCommand {
        @Override
        public void migrate(Jongo jongo) {
            throw new UnsupportedOperationException("This should never be called!");
        }
    }

    @MongoMigration(version = "1.0.1", description = "Another applied migration")
    public static class Migration101 implements MigrationCommand {
        @Override
        public void migrate(Jongo jongo) {
            throw new UnsupportedOperationException("This should never be called!");
        }
    }

    @MongoMigration(version = "1.0.2", description = "Failed last time migration")
    public static class Migration102 implements MigrationCommand {
        @Override
        public void migrate(Jongo jongo) {
            jongo.getCollection("first_migrations").insert("{'name': 'Homer Simpson', 'age': 37}");
            jongo.getCollection("first_migrations").insert("{'name': 'Marge Simpson', 'age': 36}");
        }
    }

    @MongoMigration(version = "2.0.0", description = "Brand new migration")
    public static class Migration200 implements MigrationCommand {
        @Override
        public void migrate(Jongo jongo) {
            jongo.getCollection("second_migrations").insert("{'town': 'Springfield', 'country': 'United States'}");
            jongo.getCollection("second_migrations").insert("{'town': 'Shelbyville', 'country': 'United States'}");
        }
    }

    @MongoMigration(version = "2.0.0.1", description = "I will always fail")
    public static class Migration2001 implements MigrationCommand {
        @Override
        public void migrate(Jongo jongo) {
            throw new IllegalArgumentException("This is an exception that never ends!");
        }
    }

    private static TypeSafeMatcher<Migration> migrationOf(final String version, final MigrationStatus status) {
        return new TypeSafeMatcher<Migration>() {
            @Override
            protected boolean matchesSafely(Migration record) {
                return record.getVersion().equals(version) && record.getStatus() == status;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText(String.format("version = <%s>, status = <%s>", version, status));
            }
        };
    }
}