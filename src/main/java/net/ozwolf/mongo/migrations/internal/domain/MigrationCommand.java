package net.ozwolf.mongo.migrations.internal.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.client.MongoDatabase;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.Optional;

@JsonDeserialize
@JsonIgnoreProperties(ignoreUnknown = true)
public class MigrationCommand {
    private final String version;
    private final String description;
    private final String author;
    private final BasicDBObject command;

    @JsonCreator
    public MigrationCommand(@JsonProperty("version") String version,
                            @JsonProperty("description") String description,
                            @JsonProperty("author") String author,
                            @JsonProperty("command") Map<String, Object> command) {
        if (StringUtils.trimToNull(version) == null || StringUtils.trimToNull(description) == null || command == null)
            throw new IllegalStateException("A migration command requires at least a version, description and a command!");

        this.version = version;
        this.description = description;
        this.author = Optional.ofNullable(author).orElse(Migration.DEFAULT_AUTHOR);
        this.command = new BasicDBObject(command);
    }

    public final String getVersion() {
        return version;
    }

    public final String getDescription() {
        return description;
    }

    public String getAuthor() {
        return author;
    }

    public DBObject getCommand() {
        return command;
    }

    public void migrate(MongoDatabase database) {
        database.runCommand(command);
    }


}
