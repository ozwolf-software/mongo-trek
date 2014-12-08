package net.ozwolf.mongo.migrations.exception;

public class MongoMigrationsFailureException extends Exception {
    public MongoMigrationsFailureException(Throwable e) {
        super(String.format("Mongo migrations failed: %s", e.getMessage()), e);
    }
}
