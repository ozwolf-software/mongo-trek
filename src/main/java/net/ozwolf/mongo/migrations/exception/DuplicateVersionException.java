package net.ozwolf.mongo.migrations.exception;

import net.ozwolf.mongo.migrations.internal.domain.Migration;

/**
 * <h1>Duplicate Version Exception</h1>
 *
 * This unchecked exception is thrown by mongoTrek when it detects that a migration version number has been used more than one time.
 */
public class DuplicateVersionException extends RuntimeException {
    private final static String MESSAGE_TEMPLATE = "Migration [ %s ] has duplicate commands.";

    public DuplicateVersionException(Migration migration) {
        super(String.format(MESSAGE_TEMPLATE, migration.getVersion()));
    }
}
