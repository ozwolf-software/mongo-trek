package net.ozwolf.mongo.migrations.internal.factory;

import com.googlecode.totallylazy.*;
import net.ozwolf.mongo.migrations.MigrationCommand;
import net.ozwolf.mongo.migrations.MongoMigration;
import net.ozwolf.mongo.migrations.exception.DuplicateVersionException;
import net.ozwolf.mongo.migrations.exception.MissingAnnotationException;
import net.ozwolf.mongo.migrations.internal.dao.SchemaVersionDAO;
import net.ozwolf.mongo.migrations.internal.domain.Migration;

import java.util.Collection;

import static com.googlecode.totallylazy.Sequences.sequence;
import static net.ozwolf.mongo.migrations.internal.domain.Migration.Helpers.getVersion;
import static net.ozwolf.mongo.migrations.internal.domain.Migration.Helpers.notSuccessful;
import static net.ozwolf.mongo.migrations.internal.domain.Migration.forVersion;

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
                    .groupBy(getVersion())
                    .map(checkDuplicates())
                    .map(joinWith(alreadyRun))
                    .filter(notSuccessful())
                    .sortBy(getVersion())
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
                    .groupBy(getVersion())
                    .map(checkDuplicates())
                    .map(joinWith(alreadyRun))
                    .sortBy(getVersion());
        } catch (LazyException e) {
            throw e.getCause();
        }
    }

    private static Callable1<Migration, Migration> joinWith(final Sequence<Migration> alreadyRun) {
        return new Callable1<Migration, Migration>() {
            @Override
            public Migration call(Migration migration) throws Exception {
                Option<Migration> found = alreadyRun.find(forVersion(migration.getVersion()));
                return found
                        .getOrElse(migration)
                        .assign(migration.getCommand());
            }
        };
    }

    private static Callable1<Group<String, Migration>, Migration> checkDuplicates() {
        return new Callable1<Group<String, Migration>, Migration>() {
            @Override
            public Migration call(Group<String, Migration> migrations) throws Exception {
                if (migrations.size() > 1)
                    throw new DuplicateVersionException(migrations.first());

                return migrations.first();
            }
        };
    }

    private static Callable1<MigrationCommand, Migration> asMigration() {
        return new Callable1<MigrationCommand, Migration>() {
            @Override
            public Migration call(MigrationCommand migrationCommand) throws Exception {
                MongoMigration annotation = migrationCommand.getClass().getAnnotation(MongoMigration.class);
                if (annotation == null)
                    throw new MissingAnnotationException(migrationCommand.getClass());

                return new Migration(annotation, migrationCommand);
            }
        };
    }
}
