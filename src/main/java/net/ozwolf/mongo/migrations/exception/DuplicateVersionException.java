package net.ozwolf.mongo.migrations.exception;

import net.ozwolf.mongo.migrations.internal.domain.Migration;

public class DuplicateVersionException extends RuntimeException {
    private final static String MESSAGE_TEMPLATE = "Migration [ %s ] has duplicate commands.";

    public DuplicateVersionException(Migration migration) {
        super(String.format(MESSAGE_TEMPLATE, migration.getVersion()));
    }
}
