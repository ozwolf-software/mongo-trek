package net.ozwolf.mongo.migrations;

import org.jongo.Jongo;

public interface MigrationCommand {
    void migrate(Jongo jongo);
}
