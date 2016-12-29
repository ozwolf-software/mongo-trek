package net.ozwolf.mongo.migrations.internal.dao;

import net.ozwolf.mongo.migrations.internal.domain.Migration;

import java.util.List;
import java.util.Optional;

public interface SchemaVersionDAO {
    List<Migration> findAll();

    void save(Migration migration);

    Optional<Migration> findLastSuccessful();
}
