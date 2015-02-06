package net.ozwolf.mongo.migrations.internal.service;

import net.ozwolf.mongo.migrations.MigrationCommand;
import net.ozwolf.mongo.migrations.exception.DuplicateVersionException;
import net.ozwolf.mongo.migrations.internal.dao.SchemaVersionDAO;
import net.ozwolf.mongo.migrations.internal.domain.Migration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;

public class MigrationsService {
    private final SchemaVersionDAO schemaVersionDAO;

    public MigrationsService(SchemaVersionDAO schemaVersionDAO) {
        this.schemaVersionDAO = schemaVersionDAO;
    }

    public Optional<Migration> getLastSuccessful() {
        return this.schemaVersionDAO.findLastSuccessful();
    }

    public List<Migration> getPendingMigrations(Collection<MigrationCommand> commands) throws DuplicateVersionException {
        if (commands.isEmpty()) return new ArrayList<>();

        List<Migration> alreadyRun = schemaVersionDAO.findAll();

        List<Migration> commandMigrations = commands
                .stream()
                .map(Migration::new)
                .collect(toList());

        checkForDuplicateVersions(commandMigrations);

        return commandMigrations.stream()
                .map(joinWith(alreadyRun))
                .filter(m -> !m.isSuccessful())
                .sorted((m1, m2) -> m1.getComparableVersion().compareTo(m2.getComparableVersion()))
                .collect(toList());
    }

    public List<Migration> getFullState(Collection<MigrationCommand> commands) throws DuplicateVersionException {
        List<Migration> alreadyRun = schemaVersionDAO.findAll();

        List<Migration> commandMigrations = commands
                .stream()
                .map(Migration::new)
                .collect(toList());

        checkForDuplicateVersions(commandMigrations);

        return commandMigrations
                .stream()
                .map(joinWith(alreadyRun))
                .sorted((m1, m2) -> m1.getComparableVersion().compareTo(m2.getComparableVersion()))
                .collect(toList());

    }

    private void checkForDuplicateVersions(List<Migration> migrations) throws DuplicateVersionException {
        List<Migration> duplicateVersions = migrations.stream()
                .filter(m -> migrations.stream().filter(cm -> cm.getVersion().equals(m.getVersion())).count() > 1)
                .collect(toList());

        if (!duplicateVersions.isEmpty())
            throw new DuplicateVersionException(duplicateVersions.get(0));
    }

    private static Function<Migration, Migration> joinWith(final List<Migration> alreadyRun) {
        return migration -> {
            Optional<Migration> found = alreadyRun.stream().filter(o -> o.getVersion().equals(migration.getVersion())).findFirst();
            return found
                    .orElse(migration)
                    .assign(migration.getCommand());
        };
    }

}
