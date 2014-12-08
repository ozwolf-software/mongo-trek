package net.ozwolf.mongo.migrations.internal.dao;

import com.googlecode.totallylazy.Option;
import com.googlecode.totallylazy.Sequence;
import net.ozwolf.mongo.migrations.internal.domain.Migration;

public interface SchemaVersionDAO {
    Sequence<Migration> findAll();

    void save(Migration record);

    Option<Migration> findLastSuccessful();
}
