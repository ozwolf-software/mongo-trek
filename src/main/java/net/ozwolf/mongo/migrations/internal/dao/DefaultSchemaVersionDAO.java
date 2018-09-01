package net.ozwolf.mongo.migrations.internal.dao;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import net.ozwolf.mongo.migrations.internal.domain.Migration;
import net.ozwolf.mongo.migrations.internal.domain.MigrationStatus;
import org.bson.Document;
import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static com.mongodb.client.model.Filters.eq;

public class DefaultSchemaVersionDAO implements SchemaVersionDAO {
    private final MongoCollection<Document> collection;

    public DefaultSchemaVersionDAO(MongoCollection<Document> collection) {
        this.collection = collection;
    }

    @Override
    public List<Migration> findAll() {
        List<Migration> migrations = new ArrayList<>();

        collection.find()
                .forEach((Consumer<Document>) d ->
                        migrations.add(
                                new Migration(
                                        d.getString("version"),
                                        d.getString("description"),
                                        d.getString("author"),
                                        Optional.ofNullable(d.getDate("started")).map(DateTime::new).orElse(null),
                                        Optional.ofNullable(d.getDate("finished")).map(DateTime::new).orElse(null),
                                        MigrationStatus.valueOf(d.getString("status")),
                                        d.getString("failureMessage"),
                                        d.get("result", Document.class)
                                )
                        )
                );
        return migrations;
    }

    @Override
    public void save(Migration migration) {
        Document d = new Document("version", migration.getVersion())
                .append("description", migration.getDescription())
                .append("author", migration.getAuthor())
                .append("started", Optional.ofNullable(migration.getStarted()).map(DateTime::toDate).orElse(null))
                .append("finished", Optional.ofNullable(migration.getFinished()).map(DateTime::toDate).orElse(null))
                .append("status", migration.getStatus().name())
                .append("failureMessage", migration.getFailureMessage())
                .append("result", migration.getResult());

        collection.replaceOne(eq("version", migration.getVersion()), d, new ReplaceOptions().upsert(true));
    }

    @Override
    public Optional<Migration> findLastSuccessful() {
        return this.findAll().stream()
                .filter(m -> m.getStatus() == MigrationStatus.Successful)
                .sorted(Comparator.comparing(Migration::getVersion))
                .reduce((p, c) -> c);
    }
}
