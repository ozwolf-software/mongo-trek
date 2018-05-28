package net.ozwolf.mongo.migrations.rule;

import com.mongodb.Mongo;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoDatabase;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.IMongodConfig;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.AbstractProcess;
import de.flapdoodle.embed.process.runtime.Executable;
import de.flapdoodle.embed.process.runtime.Network;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Optional;
import java.util.function.Consumer;

public class MongoDBServerRule implements TestRule {
    private MongodExecutable executable;
    private MongodProcess process;
    private MongoClient client;

    private final Integer port;

    public final static String SCHEMA_NAME = "mongo_trek_test";

    public MongoDBServerRule() {
        this.port = getAvailablePort();
    }

    public MongoDatabase getDatabase(){
        return client.getDatabase(SCHEMA_NAME);
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return description.isSuite() ? doClassRule(base) : doRule(base);
    }

    private Statement doClassRule(Statement base) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                MongodStarter starter = MongodStarter.getDefaultInstance();

                IMongodConfig config = new MongodConfigBuilder()
                        .version(Version.Main.V3_4)
                        .net(new Net("localhost", port, Network.localhostIsIPv6()))
                        .build();

                executable = starter.prepare(config);
                process = executable.start();

                MongoClientURI uri = new MongoClientURI("mongodb://localhost:" + port + "/" + SCHEMA_NAME);
                client = new MongoClient(uri);

                try {
                    base.evaluate();
                } finally {
                    Optional.ofNullable(client).ifPresent(Mongo::close);
                    Optional.ofNullable(process).ifPresent(AbstractProcess::stop);
                    Optional.ofNullable(executable).ifPresent(Executable::stop);
                }
            }
        };
    }

    private Statement doRule(Statement base) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                MongoDatabase database = client.getDatabase(SCHEMA_NAME);
                database.listCollectionNames().forEach((Consumer<String>) c -> database.getCollection(c).drop());
                try {
                    base.evaluate();
                } finally {
                    database.listCollectionNames().forEach((Consumer<String>) c -> database.getCollection(c).drop());
                }
            }
        };
    }

    private static int getAvailablePort() {
        try {
            try (ServerSocket socket = new ServerSocket(0)) {
                return socket.getLocalPort();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
