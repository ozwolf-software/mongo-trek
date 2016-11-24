package net.ozwolf.mongo.migrations.internal.domain;

import java.util.*;

import static java.util.stream.Collectors.toList;

/**
 * <h1>Migrations State</h1>
 *
 * This class provides the collection of migrations and an ability to query the overall state of the process.
 *
 * This includes what the current version is, what the pending state is as well as a list of failed and applied migrations.
 *
 */
public class MigrationsState {
    private final Map<String, Migration> migrations;

    public MigrationsState(Collection<Migration> migrations) {
        this.migrations = new HashMap<>();
        migrations.stream().forEach(m -> this.migrations.put(m.getVersion(), m));
    }

    /**
     * Get the currently applied migration version.
     * @return The currently applied version
     */
    public String getCurrentVersion() {
        return getLastSuccessfulMigration().map(Migration::getVersion).orElse("N/A");
    }

    /**
     * Get the full list of migrations.  This includes the entire history of applied migrations, even if the command source has since been removed from the project.
     * @return The full list of migrations, both applied history and pending commands.
     */
    public List<Migration> getMigrations() {
        return migrations.values().stream().sorted(Migration.sortByVersionAscending()).collect(toList());
    }

    /**
     * Return the pending migrations state.
     * @return The pending migration state
     */
    public Pending getPending() {
        List<Migration> migrations = this.migrations.values()
                .stream()
                .filter(m -> m.isPending() || m.isFailed())
                .collect(toList());

        return new Pending(migrations);
    }

    /**
     * Get the list of migrations that have failed.
     * @return The failed migrations
     */
    public List<Migration> getFailed() {
        return this.migrations.values()
                .stream()
                .filter(Migration::isFailed)
                .collect(toList());
    }

    /**
     * Get the list of successfully applied migrations
     * @return The applied migrations
     */
    public List<Migration> getApplied() {
        return this.migrations.values()
                .stream()
                .filter(Migration::isSuccessful)
                .collect(toList());
    }

    private Optional<Migration> getLastSuccessfulMigration() {
        return migrations.values()
                .stream()
                .filter(Migration::isSuccessful)
                .sorted(Migration.sortByVersionDescending())
                .findFirst();
    }

    /**
     * <h1>Migrations State - Pending State</h1>
     *
     * This class provides the collection of migrations pending application to the system.
     */
    public static class Pending {
        private final List<Migration> migrations;

        private Pending(List<Migration> migrations) {
            this.migrations = migrations;
        }

        /**
         * Flag to determine if there is any pending migrations
         * @return true if there are pending migrations
         */
        public boolean hasPendingMigrations() {
            return !this.migrations.isEmpty();
        }

        /**
         * The next migration version to be applied
         * @return the next pending migration version or ```N/A``` if none to be applied
         */
        public String getNextPendingVersion() {
            return migrations.stream().sorted(Migration.sortByVersionAscending()).findFirst().map(Migration::getVersion).orElse("N/A");
        }

        /**
         * The last migration version to be applied
         * @return the next pending migration version or ```N/A``` if none to be applied
         */
        public String getLastPendingVersion() {
            return migrations.stream().sorted(Migration.sortByVersionDescending()).findFirst().map(Migration::getVersion).orElse("N/A");
        }

        /**
         * The list of pending migrations
         * @return The list of pending migrations
         */
        public List<Migration> getMigrations() {
            return migrations.stream().sorted(Migration.sortByVersionAscending()).collect(toList());
        }
    }
}
