package net.ozwolf.mongo.migrations.internal.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.ozwolf.mongo.migrations.MigrationCommand;
import net.ozwolf.mongo.migrations.MongoMigration;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.jongo.marshall.jackson.oid.Id;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.joda.time.Seconds.secondsBetween;

public class Migration {
    @Id
    private final String version;
    private final String description;
    @JsonSerialize(using = DateTimeSerializer.class)
    private DateTime started;
    @JsonSerialize(using = DateTimeSerializer.class)
    private DateTime finished;
    private MigrationStatus status;
    private String failureMessage;

    @JsonIgnore
    private Optional<MigrationCommand> command;

    @JsonCreator
    public Migration(@JsonProperty("_id") String version,
                     @JsonProperty("description") String description,
                     @JsonProperty("started") @JsonDeserialize(using = DateTimeDeserializer.class) DateTime started,
                     @JsonProperty("finished") @JsonDeserialize(using = DateTimeDeserializer.class) DateTime finished,
                     @JsonProperty("status") MigrationStatus status,
                     @JsonProperty("failureMessage") String failureMessage) {
        this.version = version;
        this.description = description;
        this.started = started;
        this.finished = finished;
        this.status = status;
        this.failureMessage = failureMessage;
    }

    public Migration(MigrationCommand command) {
        this(command.getVersion(), command.getDescription(), null, null, MigrationStatus.Pending, null);
        this.command = Optional.of(command);
    }

    public String getVersion() {
        return version;
    }

    public ComparableVersion getComparableVersion() {
        return new ComparableVersion(version);
    }

    public String getDescription() {
        return description;
    }

    public String getDuration() {
        if (status != MigrationStatus.Successful) return "";
        return String.format("%d seconds", secondsBetween(this.started, this.finished).getSeconds());
    }

    public MigrationStatus getStatus() {
        return status;
    }

    public boolean isSuccessful() {
        return status == MigrationStatus.Successful;
    }

    public boolean isFailed() {
        return status == MigrationStatus.Failed;
    }

    public boolean isPending() {
        return status == MigrationStatus.Pending;
    }

    public boolean isRunning() {
        return status == MigrationStatus.Running;
    }

    public MigrationCommand getCommand() {
        return command.orElseThrow(() -> new IllegalStateException(String.format("No command attached to migration [ %s ]", version)));
    }

    public Migration assign(MigrationCommand command) {
        this.command = Optional.of(command);
        return this;
    }

    public Migration running() {
        this.started = DateTime.now();
        this.finished = null;
        this.failureMessage = null;
        this.status = MigrationStatus.Running;
        return this;
    }

    public Migration successful() {
        this.finished = DateTime.now();
        this.status = MigrationStatus.Successful;
        return this;
    }

    public Migration failed(Exception e) {
        this.finished = null;
        this.status = MigrationStatus.Failed;
        this.failureMessage = e.getMessage();
        return this;
    }

    public String getTags() {
        List<String> tags = new ArrayList<>();
        tags.add(String.format("[ %s ]", status.name()));
        if (this.status == MigrationStatus.Successful || this.status == MigrationStatus.Failed)
            tags.add(String.format("[ %s ]", started.toDateTime(DateTimeZone.getDefault()).toString("yyyy-MM-dd HH:mm:ss")));

        if (this.status == MigrationStatus.Successful)
            tags.add(String.format("[ %s ]", getDuration()));

        if (this.status == MigrationStatus.Failed)
            tags.add(String.format("[ ERROR: %s ]", failureMessage));
        return StringUtils.join(tags, " ");
    }

    public static class DateTimeSerializer extends JsonSerializer<DateTime> {
        @Override
        public void serialize(DateTime dateTime, JsonGenerator generator, SerializerProvider serializerProvider) throws IOException {
            if (dateTime == null) {
                generator.writeObject(null);
            } else {
                generator.writeString(dateTime.toString("yyyy-MM-dd HH:mm:ss.SSSZ"));
            }
        }
    }

    public static class DateTimeDeserializer extends JsonDeserializer<DateTime> {
        @Override
        public DateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (parser.getValueAsString() == null) return null;
            return DateTime.parse(parser.getValueAsString(), DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSSZ"));
        }
    }
}
