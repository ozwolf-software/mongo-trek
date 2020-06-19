package net.ozwolf.mongo.migrations.extension;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoDatabase;
import de.flapdoodle.embed.mongo.Command;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.IMongodConfig;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.config.RuntimeConfigBuilder;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.config.IRuntimeConfig;
import de.flapdoodle.embed.process.config.io.ProcessOutput;
import de.flapdoodle.embed.process.io.Processors;
import de.flapdoodle.embed.process.runtime.Network;
import org.junit.jupiter.api.extension.*;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.function.Consumer;

public class MongoDBServerExtension implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback {
    private MongodExecutable executable;
    private MongodProcess process;
    private MongoClient client;

    private final Integer port;

    public final static String SCHEMA_NAME = "mongo_trek_test";

    public MongoDBServerExtension() {
        this.port = getAvailablePort();
    }

    public MongoDatabase getDatabase() {
        return client.getDatabase(SCHEMA_NAME);
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        IRuntimeConfig runtimeConfig = new RuntimeConfigBuilder()
                .defaults(Command.MongoD)
                .processOutput(new ProcessOutput(Processors.silent(), Processors.silent(), Processors.silent()))
                .build();

        MongodStarter starter = MongodStarter.getInstance(runtimeConfig);

        IMongodConfig config = new MongodConfigBuilder()
                .version(Version.Main.V3_4)
                .net(new Net("localhost", port, Network.localhostIsIPv6()))
                .build();

        executable = starter.prepare(config);
        process = executable.start();

        MongoClientURI uri = new MongoClientURI("mongodb://localhost:" + port + "/" + SCHEMA_NAME);
        client = new MongoClient(uri);
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        if (client != null) client.close();
        if (process != null) process.stop();
        if (executable != null) executable.stop();

        client = null;
        process = null;
        executable = null;
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        MongoDatabase database = client.getDatabase(SCHEMA_NAME);
        database.listCollectionNames().forEach((Consumer<String>) c -> database.getCollection(c).drop());
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        MongoDatabase database = client.getDatabase(SCHEMA_NAME);
        database.listCollectionNames().forEach((Consumer<String>) c -> database.getCollection(c).drop());
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
