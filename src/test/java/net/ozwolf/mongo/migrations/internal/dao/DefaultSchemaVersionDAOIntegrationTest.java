package net.ozwolf.mongo.migrations.internal.dao;

import com.github.fakemongo.junit.FongoRule;
import com.mongodb.client.MongoCollection;
import net.ozwolf.mongo.migrations.internal.domain.Migration;
import net.ozwolf.mongo.migrations.internal.domain.MigrationStatus;
import org.bson.Document;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static com.mongodb.client.model.Filters.eq;
import static net.ozwolf.mongo.migrations.matchers.MigrationMatchers.migrationOf;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("OptionalGetWithoutIsPresent")
public class DefaultSchemaVersionDAOIntegrationTest {
    @Rule
    public final FongoRule fongo = new FongoRule("migrations_test");

    private MongoCollection<Document> collection;

    private final static String SCHEMA_VERSION_COLLECTION = "_schema_version";

    @Before
    public void setUp() {
        this.collection = fongo.getDatabase().getCollection(SCHEMA_VERSION_COLLECTION);

        this.collection.drop();

        persistMigration("1.0.0", "First migration", "Homer Simpson", "2014-12-05T09:00:00.000+1100", "2014-12-05T09:00:02.000+1100", MigrationStatus.Successful, null);
        persistMigration("1.0.1", "Second migration", "Homer Simpson", "2014-12-05T09:03:00.000+1100", null, MigrationStatus.Failed, "failure");
    }

    @Test
    public void shouldReturnAllMigrations() {
        SchemaVersionDAO dao = new DefaultSchemaVersionDAO(this.collection);

        List<Migration> result = dao.findAll();

        assertThat(result)
                .hasSize(2)
                .areAtLeastOne(migrationOf("1.0.0", "First migration", "Homer Simpson", MigrationStatus.Successful))
                .areAtLeastOne(migrationOf("1.0.1", "Second migration", "Homer Simpson", MigrationStatus.Failed));
    }

    @Test
    public void shouldReturnLatestSuccessfulMigration() {
        SchemaVersionDAO dao = new DefaultSchemaVersionDAO(this.collection);

        Optional<Migration> latest = dao.findLastSuccessful();

        assertThat(latest.isPresent()).isTrue();
        assertThat(latest.get()).is(migrationOf("1.0.0"));
    }

    @Test
    public void shouldInsertVersionToDatabase() {
        Migration migration = new Migration("1.0.2", "Failed migration", "Bart Simpson", DateTime.parse("2014-12-05T09:05:00.000+1100"), null, MigrationStatus.Failed, "This failed");

        assertThat(this.collection.count(eq("version", "1.0.2"))).isEqualTo(0L);

        SchemaVersionDAO dao = new DefaultSchemaVersionDAO(this.collection);

        dao.save(migration);

        Document afterQuery = new Document("version", "1.0.2")
                .append("status", MigrationStatus.Failed.name())
                .append("failureMessage", "This failed");

        assertThat(this.collection.count(afterQuery)).isEqualTo(1L);
    }

    @Test
    public void shouldUpdateVersionInDatabase() {
        Document beforeQuery = new Document("version", "1.0.1")
                .append("status", MigrationStatus.Failed.name())
                .append("failureMessage", "failure");

        assertThat(this.collection.count(beforeQuery)).isEqualTo(1L);

        Migration migration = new Migration("1.0.1", "Second migration", "Homer Simpson", DateTime.parse("2014-12-05T09:03:00.000+1100"), DateTime.parse("2014-12-05T09:00:04.000+1100"), MigrationStatus.Successful, null);

        SchemaVersionDAO dao = new DefaultSchemaVersionDAO(this.collection);

        dao.save(migration);

        Document afterQuery = new Document("version", "1.0.1")
                .append("status", MigrationStatus.Successful.name())
                .append("failureMessage", null);

        assertThat(this.collection.count(afterQuery)).isEqualTo(1L);
    }

    private void persistMigration(String version,
                                  String description,
                                  String author,
                                  String started,
                                  String finished,
                                  MigrationStatus status,
                                  String failureMessage) {
        Document document = new Document();
        document.put("version", version);
        document.put("description", description);
        document.put("author", Optional.ofNullable(author).orElse(Migration.DEFAULT_AUTHOR));
        document.put("started", Optional.ofNullable(started).map(DateTime::parse).map(DateTime::toDate).orElse(null));
        document.put("finished", Optional.ofNullable(finished).map(DateTime::parse).map(DateTime::toDate).orElse(null));
        document.put("status", status.name());
        document.put("failureMessage", failureMessage);

        this.collection.insertOne(document);
    }
}