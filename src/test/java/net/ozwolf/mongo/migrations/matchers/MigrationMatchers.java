package net.ozwolf.mongo.migrations.matchers;

import net.ozwolf.mongo.migrations.internal.domain.Migration;
import net.ozwolf.mongo.migrations.internal.domain.MigrationStatus;
import org.assertj.core.api.Condition;

public class MigrationMatchers {
    public static Condition<Migration> migrationOf(String version,
                                                   String description,
                                                   String author,
                                                   MigrationStatus status){
        return new Condition<>(
                m -> m.getVersion().equals(version) && m.getDescription().equals(description) && m.getAuthor().equals(author) && m.getStatus().equals(status),
                String.format("version = <%s>, description = <%s>, author = <%s>, status = <%s>", version, description, author, status)
        );
    }

    public static Condition<Migration> migrationOf(String version, MigrationStatus status){
        return new Condition<>(
                m -> m.getVersion().equals(version) && m.getStatus().equals(status),
                String.format("version = <%s>, status = <%s>", version, status)
        );
    }

    public static Condition<Migration> migrationOf(String version){
        return new Condition<>(
                m -> m.getVersion().equals(version),
                String.format("version = <%s>", version)
        );
    }
}
