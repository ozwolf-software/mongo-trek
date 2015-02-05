package net.ozwolf.mongo.migrations;

import net.ozwolf.mongo.migrations.exception.InvalidMigrationNameException;
import org.apache.commons.lang.StringUtils;
import org.jongo.Jongo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class MigrationCommand {
    private final String version;
    private final String description;
    private final static Pattern NAME_PATTERN = Pattern.compile("V(?<version>.+)__(?<description>.+)");

    protected MigrationCommand() {
        Matcher matcher = NAME_PATTERN.matcher(this.getClass().getSimpleName());
        if (!matcher.matches())
            throw new InvalidMigrationNameException(this.getClass(), NAME_PATTERN.pattern());

        this.version = StringUtils.replace(matcher.group("version"), "_", ".");
        this.description = makeDescriptionFrom(matcher.group("description"));
    }

    public String getVersion() {
        return version;
    }

    public String getDescription() {
        return description;
    }

    public abstract void migrate(Jongo jongo);

    private static String makeDescriptionFrom(String value) {
        return value.replaceAll(
                String.format("%s|%s|%s",
                        "(?<=[A-Z])(?=[A-Z][a-z])",
                        "(?<=[^A-Z])(?=[A-Z])",
                        "(?<=[A-Za-z])(?=[^A-Za-z])"
                ),
                " "
        );
    }
}
