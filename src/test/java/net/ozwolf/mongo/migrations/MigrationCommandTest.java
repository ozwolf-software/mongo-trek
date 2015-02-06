package net.ozwolf.mongo.migrations;

import net.ozwolf.mongo.migrations.exception.InvalidMigrationNameException;
import org.jongo.Jongo;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class MigrationCommandTest {
    @Test
    public void shouldCreateNewCommandCorrectly(){
        MigrationCommand command = new V1_0_0__MyValidMigration();
        assertThat(command.getVersion(), is("1.0.0"));
        assertThat(command.getDescription(), is("My valid migration"));
    }

    @Test(expected = InvalidMigrationNameException.class)
    public void shouldThrowInvalidMigrationNameException(){
        new InvalidMigration();
    }

    private static class V1_0_0__MyValidMigration extends MigrationCommand {
        @Override
        public void migrate(Jongo jongo) {
        }
    }

    private static class InvalidMigration extends MigrationCommand {
        @Override
        public void migrate(Jongo jongo) {
        }
    }
}