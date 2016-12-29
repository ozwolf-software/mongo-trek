package net.ozwolf.mongo.migrations.internal.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@JsonDeserialize
@JsonIgnoreProperties(ignoreUnknown = true)
public class MigrationCommands {
    private final List<MigrationCommand> migrations;

    @JsonCreator
    public MigrationCommands(@JsonProperty("migrations") List<MigrationCommand> migrations) {
        this.migrations = Optional.ofNullable(migrations).orElse(new ArrayList<>());
    }

    public List<MigrationCommand> getMigrations() {
        return migrations;
    }

    public boolean hasMigrations() {
        return !this.migrations.isEmpty();
    }
}
