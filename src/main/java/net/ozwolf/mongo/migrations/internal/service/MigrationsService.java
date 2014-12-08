package net.ozwolf.mongo.migrations.internal.service;

import com.googlecode.totallylazy.*;
import net.ozwolf.mongo.migrations.MigrationCommand;
import net.ozwolf.mongo.migrations.MongoMigration;
import net.ozwolf.mongo.migrations.exception.DuplicateVersionException;
import net.ozwolf.mongo.migrations.exception.MissingAnnotationException;
import net.ozwolf.mongo.migrations.internal.dao.SchemaVersionDAO;
import net.ozwolf.mongo.migrations.internal.domain.Migration;
import net.ozwolf.mongo.migrations.internal.domain.MigrationStatus;

import java.util.Collection;

import static com.googlecode.totallylazy.Sequences.sequence;

public class MigrationsService {
    private final SchemaVersionDAO schemaVersionDAO;

    public MigrationsService(SchemaVersionDAO schemaVersionDAO) {
        this.schemaVersionDAO = schemaVersionDAO;
    }

    public Sequence<Migration> getPendingMigrations(Collection<MigrationCommand> commands) throws Throwable {
        Sequence<Migration> alreadyRun = schemaVersionDAO.findAll();

        try {
            return sequence(commands)
                    .map(asMigration())
                    .groupBy(Migration::getVersion)
                    .map(checkDuplicates())
                    .map(joinWith(alreadyRun))
                    .filter(m -> !(m.getStatus() == MigrationStatus.Successful))
                    .sortBy(Migration::getComparableVersion)
                    .realise();
        } catch (LazyException e) {
            throw e.getCause();
        }
    }

    public Sequence<Migration> getFullState(Collection<MigrationCommand> commands) throws Throwable {
        Sequence<Migration> alreadyRun = schemaVersionDAO.findAll();

        try {
            return sequence(commands)
                    .map(asMigration())
                    .groupBy(Migration::getVersion)
                    .map(checkDuplicates())
                    .map(joinWith(alreadyRun))
                    .sortBy(Migration::getComparableVersion);
        } catch (LazyException e) {
            throw e.getCause();
        }
    }

    private static Function<Migration, Migration> joinWith(final Sequence<Migration> alreadyRun) {
        return migration -> {
            Option<Migration> found = alreadyRun.find(o -> o.getVersion().equals(migration.getVersion()));
            return found
                    .getOrElse(migration)
                    .assign(migration.getCommand());
        };
    }

    private static Function<Group<String, Migration>, Migration> checkDuplicates() {
        return migrations -> {
            if (migrations.size() > 1)
                throw new DuplicateVersionException(migrations.first());

            return migrations.first();
        };
    }

    private static Function<MigrationCommand, Migration> asMigration() {
        return command -> {
            MongoMigration annotation = command.getClass().getAnnotation(MongoMigration.class);
            if (annotation == null)
                throw new MissingAnnotationException(command.getClass());

            return new Migration(annotation, command);
        };
    }
}
