package net.ozwolf.mongo.migrations.internal.dao;

import com.googlecode.totallylazy.Option;
import com.googlecode.totallylazy.Sequence;
import net.ozwolf.mongo.migrations.internal.domain.Migration;
import net.ozwolf.mongo.migrations.internal.domain.MigrationStatus;
import org.jongo.Jongo;

import static com.googlecode.totallylazy.Option.none;
import static com.googlecode.totallylazy.Option.option;
import static com.googlecode.totallylazy.Sequences.sequence;

public class DefaultSchemaVersionDAO implements SchemaVersionDAO {
    private final Jongo jongo;
    private final String schemaVersionCollection;

    public DefaultSchemaVersionDAO(Jongo jongo, String schemaVersionCollection) {
        this.jongo = jongo;
        this.schemaVersionCollection = schemaVersionCollection;
    }

    @Override
    public Sequence<Migration> findAll() {
        return sequence(jongo.getCollection(schemaVersionCollection).find().as(Migration.class));
    }

    @Override
    public void save(Migration record) {
        jongo.getCollection(schemaVersionCollection).save(record);
    }

    @Override
    public Option<Migration> findLastSuccessful() {
        Sequence<Migration> migrations = this.findAll()
                .filter(m -> m.getStatus() == MigrationStatus.Successful)
                .sortBy(Migration::getVersion);
        return migrations.isEmpty() ? none(Migration.class) : option(migrations.last());
    }
}
