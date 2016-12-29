package net.ozwolf.mongo.migrations.internal.factory;

import com.mongodb.DBObject;
import net.ozwolf.mongo.migrations.exception.MongoTrekFailureException;
import net.ozwolf.mongo.migrations.internal.domain.Migration;
import net.ozwolf.mongo.migrations.internal.domain.MigrationCommand;
import net.ozwolf.mongo.migrations.internal.domain.MigrationCommands;
import org.assertj.core.api.Condition;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings({"OptionalGetWithoutIsPresent", "unchecked"})
public class MigrationCommandsFactoryTest {
    @Test
    public void shouldDeserializeMigrationsFileCorrectly() throws MongoTrekFailureException {
        MigrationCommands commands = new MigrationCommandsFactory().getCommands("fixtures/migrations-deserialization-test.yml");

        assertThat(commands.hasMigrations()).isTrue();
        assertThat(commands.getMigrations()).hasSize(2);

        assertFirstMigration(commands.getMigrations().stream().filter(c -> c.getVersion().equalsIgnoreCase("1.0.0")).findFirst().get());
        assertSecondMigration(commands.getMigrations().stream().filter(c -> c.getVersion().equalsIgnoreCase("1.0.1")).findFirst().get());
    }

    // Test that the migration is deserialized correctly and that the JSON-structured command is parsed.
    private static void assertFirstMigration(MigrationCommand firstMigration) {
        assertThat(firstMigration.getDescription()).isEqualTo("My first migration");
        assertThat(firstMigration.getAuthor()).isEqualTo("Homer Simpson");

        DBObject firstCommand = firstMigration.getCommand();

        assertThat(firstCommand.get("insert")).isEqualTo("test");

        List<Map<String, Object>> documents = (List<Map<String, Object>>) firstCommand.get("documents");
        assertThat(documents)
                .hasSize(2)
                .areAtLeastOne(insertDocument(1, "test1"))
                .areAtLeastOne(insertDocument(2, "test2"));
    }

    // Test that the migration is deserialized correctly and that the YAML-structured command is parsed.
    private static void assertSecondMigration(MigrationCommand secondMigration) {
        assertThat(secondMigration.getDescription()).isEqualTo("My second migration");
        assertThat(secondMigration.getAuthor()).isEqualTo(Migration.DEFAULT_AUTHOR);

        DBObject secondCommand = secondMigration.getCommand();

        assertThat(secondCommand.get("update")).isEqualTo("test");

        List<Map<String, Object>> updates = (List<Map<String, Object>>) secondCommand.get("updates");

        assertThat(updates).hasSize(1);

        Map<String, Object> update = updates.get(0);

        assertThat(update.get("multi")).isEqualTo(true);

        Map<String, Object> u = (Map<String, Object>) update.get("u");
        Map<String, Object> set = (Map<String, Object>) u.get("$set");

        assertThat(set.get("value3")).isEqualTo(false);
    }

    private static Condition<Map<String, Object>> insertDocument(Integer value1, String value2) {
        Predicate<Map<String, Object>> predicate = m -> m.get("value1").equals(value1) && m.get("value2").equals(value2);

        return new Condition<>(predicate, "Is Insert Document Matching");
    }
}