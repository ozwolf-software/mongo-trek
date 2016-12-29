package net.ozwolf.mongo.migrations.internal.factory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import net.ozwolf.mongo.migrations.exception.MongoTrekFailureException;
import net.ozwolf.mongo.migrations.internal.domain.MigrationCommands;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Optional;

public class MigrationCommandsFactory {
    private final static ObjectMapper MAPPER = new YAMLMapper();

    @SuppressWarnings("unchecked")
    public MigrationCommands getCommands(String migrationsFile) throws MongoTrekFailureException {
        try {
            String source = load(migrationsFile).orElseThrow(() -> new MongoTrekFailureException(new IllegalArgumentException(String.format("Could not find migrations file [ %s ] on classpath or file system.", migrationsFile))));
            return MAPPER.readValue(source, MigrationCommands.class);
        } catch (IOException e) {
            throw new MongoTrekFailureException(e);
        }
    }

    private static Optional<String> load(String migrationsFile) throws MongoTrekFailureException {
        URL url = MigrationCommandsFactory.class.getClassLoader().getResource(migrationsFile);
        File file = new File(migrationsFile);
        try {
            if (url != null) {
                return Optional.ofNullable(IOUtils.toString(url.openStream()));
            } else if (file.exists()) {
                return Optional.ofNullable(FileUtils.readFileToString(file));
            } else {
                return Optional.empty();
            }
        } catch (IOException e) {
            throw new MongoTrekFailureException(e);
        }
    }
}
