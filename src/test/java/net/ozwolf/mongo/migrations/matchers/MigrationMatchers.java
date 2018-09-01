package net.ozwolf.mongo.migrations.matchers;

import net.ozwolf.mongo.migrations.internal.domain.Migration;
import net.ozwolf.mongo.migrations.internal.domain.MigrationStatus;
import org.assertj.core.api.Condition;

import java.util.Map;

public class MigrationMatchers {
    public static Condition<Migration> migrationOf(String version,
                                                   String description,
                                                   String author,
                                                   MigrationStatus status) {
        return new Condition<>(
                m -> m.getVersion().equals(version) && m.getDescription().equals(description) && m.getAuthor().equals(author) && m.getStatus().equals(status),
                String.format("version = <%s>, description = <%s>, author = <%s>, status = <%s>", version, description, author, status)
        );
    }

    public static Condition<Migration> migrationOf(String version, MigrationStatus status) {
        return new Condition<>(
                m -> m.getVersion().equals(version) && m.getStatus().equals(status),
                String.format("version = <%s>, status = <%s>", version, status)
        );
    }

    public static Condition<Migration> migrationOf(String version, MigrationStatus status, Map<String, Object> result) {
        return new Condition<>(
                m -> m.getVersion().equals(version) &&
                        m.getStatus().equals(status) &&
                        m.getResult() != null &&
                        result.entrySet().stream().allMatch(e -> m.getResult().get(e.getKey()).equals(e.getValue())),
                String.format("version = <%s>, status = <%s>, result = <%s>", version, status, result)
        );
    }

    public static Condition<Migration> migrationOf(String version) {
        return new Condition<>(
                m -> m.getVersion().equals(version),
                String.format("version = <%s>", version)
        );
    }
}
