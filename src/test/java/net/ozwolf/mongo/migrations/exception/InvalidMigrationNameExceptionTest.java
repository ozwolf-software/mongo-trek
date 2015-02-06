package net.ozwolf.mongo.migrations.exception;

import net.ozwolf.mongo.migrations.MigrationCommand;
import org.jongo.Jongo;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class InvalidMigrationNameExceptionTest {
    @Test
    public void shouldProvidedExpectedExceptionMessage(){
        assertThat(new InvalidMigrationNameException(InvalidMigration.class, "V(?<version>.+)__(?<description>.+)").getMessage(), is("Migration command [ InvalidMigration ] does not meet the expected naming pattern of [ V(?<version>.+)__(?<description>.+) ]."));
    }

    private static class InvalidMigration extends MigrationCommand {
        @Override
        public void migrate(Jongo jongo) {
        }
    }
}