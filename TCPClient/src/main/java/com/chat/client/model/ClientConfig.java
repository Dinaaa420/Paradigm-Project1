package com.chat.client.model;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/**
 * Server address used by the client.
 *
 * Priority (highest first):
 *   1. command-line arguments:  java TCPClient <ServerIPAddress> <PortNumber>
 *   2. -Dclient.config=/path/to/file.properties
 *   3. client.properties in the working directory
 *   4. client.properties bundled in the JAR
 */
public record ClientConfig(String host, int port) {

    public static final String FILE_NAME = "client.properties";
    public static final String USAGE = "Usage: java TCPClient <ServerIPAddress> <PortNumber>   (e.g. java TCPClient localhost 3000)";

    public static ClientConfig load(List<String> args) throws IOException {
        Properties props = new Properties();
        try (InputStream in = ClientConfig.class.getResourceAsStream("/" + FILE_NAME)) {
            if (in != null) props.load(in);
        }
        loadIfExists(props, Path.of(FILE_NAME));
        String custom = System.getProperty("client.config");
        if (custom != null) loadIfExists(props, Path.of(custom));

        String host = props.getProperty("server.host", "localhost").trim();
        String portText = props.getProperty("server.port", "3000").trim();

        if (args.size() == 2) {
            host = args.get(0).trim();
            portText = args.get(1).trim();
        } else if (!args.isEmpty()) {
            throw new IOException(USAGE);
        }

        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid port '" + portText + "'. " + USAGE);
        }
        if (port < 1 || port > 65535) throw new IOException("Port must be between 1 and 65535");
        return new ClientConfig(host, port);
    }

    private static void loadIfExists(Properties props, Path path) throws IOException {
        if (Files.isRegularFile(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                props.load(in);
            }
        }
    }
}
