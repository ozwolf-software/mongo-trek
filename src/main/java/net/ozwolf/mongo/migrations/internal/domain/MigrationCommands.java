package net.ozwolf.mongo.migrations.internal.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.trimToNull;

@JsonDeserialize
@JsonIgnoreProperties(ignoreUnknown = true)
public class MigrationCommands {
    private final String schemaVersionCollection;
    private final List<MigrationCommand> migrations;

    @JsonCreator
    public MigrationCommands(@JsonProperty("collectionName") String schemaVersionCollection,
                             @JsonProperty("migrations") List<MigrationCommand> migrations) {
        this.schemaVersionCollection = schemaVersionCollection;
        this.migrations = Optional.ofNullable(migrations).orElse(new ArrayList<>());
    }

    public Optional<String> getSchemaVersionCollection() {
        return Optional.ofNullable(trimToNull(schemaVersionCollection));
    }

    public List<MigrationCommand> getMigrations() {
        return migrations;
    }

    public boolean hasMigrations() {
        return !this.migrations.isEmpty();
    }
}
