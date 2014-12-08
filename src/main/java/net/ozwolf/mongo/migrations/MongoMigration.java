package net.ozwolf.mongo.migrations;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface MongoMigration {
    String version();

    String description();
}
