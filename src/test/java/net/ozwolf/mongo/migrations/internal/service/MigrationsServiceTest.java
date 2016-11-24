package net.ozwolf.mongo.migrations.internal.service;

import net.ozwolf.mongo.migrations.MigrationCommand;
import net.ozwolf.mongo.migrations.exception.DuplicateVersionException;
import net.ozwolf.mongo.migrations.internal.dao.SchemaVersionDAO;
import net.ozwolf.mongo.migrations.internal.domain.Migration;
import net.ozwolf.mongo.migrations.internal.domain.MigrationStatus;
import net.ozwolf.mongo.migrations.internal.domain.MigrationsState;
import org.joda.time.DateTime;
import org.jongo.Jongo;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MigrationsServiceTest {
    private final SchemaVersionDAO schemaVersionDAO = mock(SchemaVersionDAO.class);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void shouldReturnPendingStateFromMigrationState() throws Throwable {
        Migration previous1 = record("1.0.0", MigrationStatus.Successful);
        Migration previous2 = record("1.0.1", MigrationStatus.Successful);
        Migration previous3 = record("1.0.2", MigrationStatus.Failed);

        List<Migration> previouslyRun = migrations(previous1, previous3, previous2);

        when(schemaVersionDAO.findAll()).thenReturn(previouslyRun);

        List<MigrationCommand> commands = commands(
                new V1_0_0__FirstMigration(),
                new V1_0_1__SecondMigration(),
                new V2_0_0__FourthMigration(),
                new V1_0_2__ThirdMigration()
        );

        MigrationsState.Pending pendingMigrations = new MigrationsService(schemaVersionDAO).getState(commands).getPending();

        assertThat(pendingMigrations.getMigrations().size(), is(2));
        assertThat(pendingMigrations.getNextPendingVersion(), is("1.0.2"));
        assertThat(pendingMigrations.getLastPendingVersion(), is("2.0.0"));
        assertThat(pendingMigrations.getMigrations().get(0).getVersion(), is("1.0.2"));
        assertThat(pendingMigrations.getMigrations().get(1).getVersion(), is("2.0.0"));
    }

    @Test
    public void shouldReturnAllMigrations() throws Throwable {
        Migration previous1 = record("1.0.0", MigrationStatus.Successful);
        Migration previous2 = record("1.0.1", MigrationStatus.Successful);
        Migration previous3 = record("1.0.2", MigrationStatus.Failed);

        List<Migration> previouslyRun = migrations(previous1, previous3, previous2);

        when(schemaVersionDAO.findAll()).thenReturn(previouslyRun);

        List<MigrationCommand> commands = commands(
                new V1_0_0__FirstMigration(),
                new V1_0_1__SecondMigration(),
                new V2_0_0__FourthMigration(),
                new V1_0_2__ThirdMigration()
        );

        List<Migration> migrations = new MigrationsService(schemaVersionDAO).getState(commands).getMigrations();

        assertThat(migrations.size(), is(4));
        assertThat(migrations.get(0).getVersion(), is("1.0.0"));
        assertThat(migrations.get(1).getVersion(), is("1.0.1"));
        assertThat(migrations.get(2).getVersion(), is("1.0.2"));
        assertThat(migrations.get(3).getVersion(), is("2.0.0"));
    }

    @Test
    public void shouldThrowDuplicateVersionException() throws Throwable {
        when(schemaVersionDAO.findAll()).thenReturn(migrations());

        List<MigrationCommand> commands = commands(
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
                DateTime.now(),
                (status == MigrationStatus.Successful) ? DateTime.now() : null,
                status,
                (status == MigrationStatus.Failed) ? "Failure" : null
        );
    }

    private static List<Migration> migrations(Migration... migrations) {
        return Arrays.asList(migrations);
    }

    private static List<MigrationCommand> commands(MigrationCommand... commands) {
        return Arrays.asList(commands);
    }

    public static class V1_0_0__FirstMigration extends MigrationCommand {
        @Override
        public void migrate(Jongo jongo) {
        }
    }

    public static class V1_0_1__SecondMigration extends MigrationCommand {
        @Override
        public void migrate(Jongo jongo) {
        }
    }

    public static class V1_0_2__ThirdMigration extends MigrationCommand {
        @Override
        public void migrate(Jongo jongo) {
        }
    }

    public static class V2_0_0__FourthMigration extends MigrationCommand {
        @Override
        public void migrate(Jongo jongo) {
        }
    }

    public static class V2_0_0__DuplicateMigration extends MigrationCommand {
        @Override
        public void migrate(Jongo jongo) {
        }
    }
}