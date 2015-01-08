package net.ozwolf.mongo.migrations.internal.dao;

import net.ozwolf.mongo.migrations.internal.domain.Migration;
import net.ozwolf.mongo.migrations.internal.domain.MigrationStatus;
import org.jongo.Jongo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DefaultSchemaVersionDAO implements SchemaVersionDAO {
    private final Jongo jongo;
    private final String schemaVersionCollection;

    public DefaultSchemaVersionDAO(Jongo jongo, String schemaVersionCollection) {
        this.jongo = jongo;
        this.schemaVersionCollection = schemaVersionCollection;
    }

    @Override
    public List<Migration> findAll() {
        List<Migration> migrations = new ArrayList<>();
        jongo.getCollection(schemaVersionCollection)
                .find()
                .as(Migration.class)
                .forEach(migrations::add);
        return migrations;
    }

    @Override
    public void save(Migration record) {
        jongo.getCollection(schemaVersionCollection).save(record);
    }

    @Override
    public Optional<Migration> findLastSuccessful() {
        return this.findAll().stream()
                .filter(m -> m.getStatus() == MigrationStatus.Successful)
                .sorted((m1, m2) -> m1.getVersion().compareTo(m2.getVersion()))
                .reduce((p, c) -> c);
    }
}
