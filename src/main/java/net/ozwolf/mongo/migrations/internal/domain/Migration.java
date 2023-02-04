package net.ozwolf.mongo.migrations.internal.domain;

import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.semver4j.Semver;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Migration {
    private final String version;
    private final String description;
    private final String author;
    private Instant started;
    private Instant finished;
    private MigrationStatus status;
    private String failureMessage;
    private Map<String, Object> result;

    private MigrationCommand command;

    public final static String DEFAULT_AUTHOR = "trekBot";

    public Migration(String version,
                     String description,
                     String author,
                     Instant started,
                     Instant finished,
                     MigrationStatus status,
                     String failureMessage,
                     Map<String, Object> result) {
        this.version = version;
        this.description = description;
        this.author = Optional.ofNullable(author).orElse(DEFAULT_AUTHOR);
        this.started = started;
        this.finished = finished;
        this.status = status;
        this.failureMessage = failureMessage;
        this.result = result;
    }

    public Migration(MigrationCommand command) {
        this(command.getVersion(), command.getDescription(), command.getAuthor(), null, null, MigrationStatus.Pending, null, null);
        this.command = command;
    }

    public String getVersion() {
        return version;
    }

    public Semver getSemanticVersion() {
        return Semver.parse(version);
    }

    public String getDescription() {
        return description;
    }

    public String getAuthor() {
        return author;
    }

    public Instant getStarted() {
        return started;
    }

    public Instant getFinished() {
        return finished;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public String getDuration() {
        if (status != MigrationStatus.Successful) return "";
        return String.format("%d seconds", Duration.between(this.started, this.finished).getSeconds());
    }

    public MigrationStatus getStatus() {
        return status;
    }

    public Map<String, Object> getResult() {
        if (status != MigrationStatus.Successful) return null;

        return Optional.ofNullable(result).orElseGet(HashMap::new);
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
        return Optional.ofNullable(command).orElseThrow(() -> new IllegalStateException(String.format("No command attached to migration [ %s ]", version)));
    }

    public Migration assign(MigrationCommand command) {
        this.command = command;
        return this;
    }

    public Migration running() {
        this.started = Instant.now();
        this.finished = null;
        this.failureMessage = null;
        this.status = MigrationStatus.Running;
        return this;
    }

    public Migration successful(Document result) {
        this.finished = Instant.now();
        this.status = MigrationStatus.Successful;
        this.result = result;
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
            tags.add(String.format("[ %s ]", started.atZone(ZoneOffset.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));

        if (this.status == MigrationStatus.Successful)
            tags.add(String.format("[ %s ]", getDuration()));

        if (this.status == MigrationStatus.Failed)
            tags.add(String.format("[ ERROR: %s ]", failureMessage));
        return StringUtils.join(tags, " ");
    }

    @Override
    public String toString() {
        return String.format("version = <%s>, description = <%s>, author = <%s>, status = <%s>", version, description, author, status);
    }

    public static Comparator<Migration> sortByVersionAscending() {
        return Comparator.comparing(Migration::getSemanticVersion);
    }

    public static Comparator<Migration> sortByVersionDescending() {
        return (m1, m2) -> m2.getSemanticVersion().compareTo(m1.getSemanticVersion());
    }
}
