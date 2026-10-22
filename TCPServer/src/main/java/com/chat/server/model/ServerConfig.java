package com.chat.server.model;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Network settings loaded at runtime from server.properties.
 *
 * Lookup order (later wins):
 *   1. server.properties bundled in the JAR (defaults)
 *   2. server.properties in the current working directory
 *   3. file given with -Dserver.config=/path/to/file.properties
 *
 * So the IP/port can be changed without recompiling.
 */
public record ServerConfig(String host, int port) {

    public static final String FILE_NAME = "server.properties";

    public static ServerConfig load() throws IOException {
        Properties props = new Properties();

        try (InputStream in = ServerConfig.class.getResourceAsStream("/" + FILE_NAME)) {
            if (in != null) props.load(in);
        }
        loadIfExists(props, Path.of(FILE_NAME));
        String custom = System.getProperty("server.config");
        if (custom != null) loadIfExists(props, Path.of(custom));

        String host = props.getProperty("server.host", "0.0.0.0").trim();
        int port;
        try {
            port = Integer.parseInt(props.getProperty("server.port", "3000").trim());
        } catch (NumberFormatException e) {
            throw new IOException("server.port must be a number", e);
        }
        if (port < 1 || port > 65535) throw new IOException("server.port must be between 1 and 65535");
        return new ServerConfig(host, port);
    }

    private static void loadIfExists(Properties props, Path path) throws IOException {
        if (Files.isRegularFile(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                props.load(in);
            }
        }
    }
}
