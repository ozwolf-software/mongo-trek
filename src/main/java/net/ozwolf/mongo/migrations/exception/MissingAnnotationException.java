package net.ozwolf.mongo.migrations.exception;

import net.ozwolf.mongo.migrations.MigrationCommand;
import net.ozwolf.mongo.migrations.MongoMigration;

public class MissingAnnotationException extends Exception {
    private final static String MESSAGE_TEMPLATE = "Migration command [ %s ] is missing the [ @%s ] annotation.";

    public MissingAnnotationException(Class<? extends MigrationCommand> commandClass) {
        super(String.format(MESSAGE_TEMPLATE, commandClass.getSimpleName(), MongoMigration.class.getSimpleName()));
    }
}
