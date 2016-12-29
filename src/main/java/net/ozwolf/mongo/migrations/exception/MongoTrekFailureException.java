package net.ozwolf.mongo.migrations.exception;

/**
 * <h1>Mongo Trek Failure Exception</h1>
 *
 * This is the wrapping, checked exception that `MongoTrek` will throw in the case of any exception being encountered.
 *
 * @see net.ozwolf.mongo.migrations.MongoTrek
 */
public class MongoTrekFailureException extends Exception {
    public MongoTrekFailureException(Throwable e) {
        super(String.format("mongoTrek failed: %s", e.getMessage()), e);
    }
}
