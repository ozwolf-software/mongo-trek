package net.ozwolf.mongo.migrations.exception;

public class InvalidMigrationNameException extends RuntimeException {
    private final static String MESSAGE_TEMPLATE = "Migration command [ %s ] does not meet the expected naming pattern of [ %s ].";

    public InvalidMigrationNameException(Class<?> migrationCommand, String namePattern) {
        super(String.format(MESSAGE_TEMPLATE, migrationCommand.getSimpleName(), namePattern));
    }
}
