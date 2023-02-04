package net.ozwolf.mongo.migrations.extension;

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.embed.process.io.ProcessOutput;
import de.flapdoodle.reverse.Transition;
import de.flapdoodle.reverse.TransitionWalker;
import de.flapdoodle.reverse.transitions.Start;
import org.junit.jupiter.api.extension.*;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.function.Consumer;

public class MongoDBServerExtension implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback {
    private TransitionWalker.ReachedState<RunningMongodProcess> process;
    private MongoClient client;

    public final static String SCHEMA_NAME = "mongo_trek_test";

    public MongoDatabase getDatabase() {
        return client.getDatabase(SCHEMA_NAME);
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        this.process = new Mongod(){
            @Override
            public Transition<ProcessOutput> processOutput() {
                return Start.to(ProcessOutput.class).initializedWith(ProcessOutput.silent());
            }
        }.start(Version.Main.V6_0);


        ConnectionString uri = new ConnectionString("mongodb://localhost:" + this.process.current().getServerAddress().getPort() + "/" + SCHEMA_NAME);
        client = MongoClients.create(uri);
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        if (client != null) client.close();
        if (process != null) process.close();

        client = null;
        process = null;
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        MongoDatabase database = client.getDatabase(SCHEMA_NAME);
        database.listCollectionNames().forEach(c -> database.getCollection(c).drop());
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        MongoDatabase database = client.getDatabase(SCHEMA_NAME);
        database.listCollectionNames().forEach(c -> database.getCollection(c).drop());
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
