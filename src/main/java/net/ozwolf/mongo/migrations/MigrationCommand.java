package net.ozwolf.mongo.migrations;

import net.ozwolf.mongo.migrations.exception.InvalidMigrationNameException;
import org.apache.commons.lang.StringUtils;
import org.jongo.Jongo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <h1>Migration Command</h1>
 *
 * The command object all migrations should extend.  Commands need to implement the `migrate` command.
 *
 * ## Naming Convention
 *
 * All migration commands should be named using the format V<version>__<description>.  For example, `public class V1_0_0__MyFirstMigration...`
 *
 * ## Example Usage
 *
 * ```java
 * public class V2_1_0__AddUniqueEmailIndexToPersonCollection extends MigrationCommand {
 *     {@literal @}Override
 *     public void migrate(Jongo jongo){
 *         jongo.getCollection("person").ensureIndex("{email: 1}", "{unique: 1, name: 'email_idx'}");
 *     }
 * }
 * ```
 */
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

    public final String getVersion() {
        return version;
    }

    public final String getDescription() {
        return description;
    }

    public abstract void migrate(Jongo jongo);

    private static String makeDescriptionFrom(String value) {
        return StringUtils.capitalize(
                value.replaceAll(
                        String.format("%s|%s|%s",
                                "(?<=[A-Z])(?=[A-Z][a-z])",
                                "(?<=[^A-Z])(?=[A-Z])",
                                "(?<=[A-Za-z])(?=[^A-Za-z])"
                        ),
                        " "
                ).toLowerCase()
        );
    }
}
