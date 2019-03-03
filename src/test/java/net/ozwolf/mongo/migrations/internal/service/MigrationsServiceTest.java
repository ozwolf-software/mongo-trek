package net.ozwolf.mongo.migrations.internal.service;

import net.ozwolf.mongo.migrations.MongoTrekState;
import net.ozwolf.mongo.migrations.exception.DuplicateVersionException;
import net.ozwolf.mongo.migrations.internal.dao.SchemaVersionDAO;
import net.ozwolf.mongo.migrations.internal.domain.Migration;
import net.ozwolf.mongo.migrations.internal.domain.MigrationCommand;
import net.ozwolf.mongo.migrations.internal.domain.MigrationCommands;
import net.ozwolf.mongo.migrations.internal.domain.MigrationStatus;
import org.bson.Document;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static net.ozwolf.mongo.migrations.matchers.MigrationMatchers.migrationOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MigrationsServiceTest {
    private final SchemaVersionDAO schemaVersionDAO = mock(SchemaVersionDAO.class);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void shouldReturnPendingStateFromMigrationState() {
        Migration previous1 = record("1.0.0", MigrationStatus.Successful);
        Migration previous2 = record("1.0.1", MigrationStatus.Successful);
        Migration previous3 = record("1.0.2", MigrationStatus.Failed);

        List<Migration> previouslyRun = migrations(previous1, previous3, previous2);

        when(schemaVersionDAO.findAll()).thenReturn(previouslyRun);

        MigrationCommands commands = commands(
                new V1_0_0__FirstMigration(),
                new V1_0_1__SecondMigration(),
                new V2_0_0__FourthMigration(),
                new V1_0_2__ThirdMigration()
        );

        MongoTrekState.Pending pendingMigrations = new MigrationsService(schemaVersionDAO).getState(commands).getPending();

        assertThat(pendingMigrations.getMigrations())
                .hasSize(2)
                .areAtLeastOne(migrationOf("1.0.2"))
                .areAtLeastOne(migrationOf("2.0.0"));

        assertThat(pendingMigrations.getNextPendingVersion()).isEqualTo("1.0.2");
        assertThat(pendingMigrations.getLastPendingVersion()).isEqualTo("2.0.0");
    }

    @Test
    public void shouldReturnAllMigrations() {
        Migration previous1 = record("1.0.0", MigrationStatus.Successful);
        Migration previous2 = record("1.0.1", MigrationStatus.Successful);
        Migration previous3 = record("1.0.2", MigrationStatus.Failed);

        List<Migration> previouslyRun = migrations(previous1, previous3, previous2);

        when(schemaVersionDAO.findAll()).thenReturn(previouslyRun);

        MigrationCommands commands = commands(
                new V1_0_0__FirstMigration(),
                new V1_0_1__SecondMigration(),
                new V2_0_0__FourthMigration(),
                new V1_0_2__ThirdMigration()
        );

        List<Migration> migrations = new MigrationsService(schemaVersionDAO).getState(commands).getMigrations();

        assertThat(migrations)
                .hasSize(4)
                .areAtLeastOne(migrationOf("1.0.0"))
                .areAtLeastOne(migrationOf("1.0.1"))
                .areAtLeastOne(migrationOf("1.0.2"))
                .areAtLeastOne(migrationOf("2.0.0"));
    }

    @Test
    public void shouldThrowDuplicateVersionException() {
        when(schemaVersionDAO.findAll()).thenReturn(migrations());

        MigrationCommands commands = commands(
                new V2_0_0__FourthMigration(),
                new V1_0_2__ThirdMigration(),
                new V2_0_0__DuplicateMigration()
        );

        exception.expect(DuplicateVersionException.class);
        exception.expectMessage("Migration [ 2.0.0 ] has duplicate commands.");
        new MigrationsService(schemaVersionDAO).getState(commands);
    }

    private Migration record(String version, MigrationStatus status) {
        return new Migration(
                version,
                String.format("Migration %s", version),
                Migration.DEFAULT_AUTHOR,
                Instant.now(),
                (status == MigrationStatus.Successful) ? Instant.now() : null,
                status,
                (status == MigrationStatus.Failed) ? "Failure" : null,
                (status == MigrationStatus.Failed) ? null : new Document("n", 1)
        );
    }

    private static List<Migration> migrations(Migration... migrations) {
        return Arrays.asList(migrations);
    }

    private static MigrationCommands commands(MigrationCommand... commands) {
        return new MigrationCommands(null, Arrays.asList(commands));
    }

    private static class V1_0_0__FirstMigration extends MigrationCommand {
        V1_0_0__FirstMigration() {
            super("1.0.0", "First Migration", Migration.DEFAULT_AUTHOR, new HashMap<>());
        }
    }

    private static class V1_0_1__SecondMigration extends MigrationCommand {
        V1_0_1__SecondMigration() {
            super("1.0.1", "Second Migration", Migration.DEFAULT_AUTHOR, new HashMap<>());
        }

    }

    private static class V1_0_2__ThirdMigration extends MigrationCommand {
        V1_0_2__ThirdMigration() {
            super("1.0.2", "Third Migration", Migration.DEFAULT_AUTHOR, new HashMap<>());
        }
    }

    private static class V2_0_0__FourthMigration extends MigrationCommand {
        V2_0_0__FourthMigration() {
            super("2.0.0", "Fourth Migration", Migration.DEFAULT_AUTHOR, new HashMap<>());
        }
    }

    private static class V2_0_0__DuplicateMigration extends MigrationCommand {
        V2_0_0__DuplicateMigration() {
            super("2.0.0", "Duplicate Migration", Migration.DEFAULT_AUTHOR, new HashMap<>());
        }
    }
}