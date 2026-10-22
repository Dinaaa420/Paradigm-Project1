package com.chat.client.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Socket logic of the client. No UI code: results are pushed to a {@link ClientListener},
 * so the same class works with JavaFX, a console UI, or a unit test.
 */
public class ChatClient {

    private static final int CONNECT_TIMEOUT_MS = 5000;

    private final String host;
    private final int port;
    private final ClientListener listener;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private Socket socket;
    private PrintWriter out;
    private volatile boolean connected;
    private volatile boolean readOnly;
    private volatile String username;

    public ChatClient(String host, int port, ClientListener listener) {
        this.host = host;
        this.port = port;
        this.listener = listener;
    }

    /**
     * Opens the TCP connection and sends the username (blank = READ-ONLY MODE).
     * Returns once connected; {@link ClientListener#onConnected} fires when the server answers.
     */
    public void connect(String requestedUsername) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), UTF_8));
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), UTF_8), true);

        out.println(requestedUsername == null ? "" : requestedUsername.strip());
        connected = true;

        Thread.ofVirtual().name("chat-reader").start(() -> readLoop(in));
    }

    private void readLoop(BufferedReader in) {
        String reason = "Connection closed by server";
        try {
            String line;
            while ((line = in.readLine()) != null) {
                if (!handle(line)) {
                    reason = "You left the chat";
                    break;
                }
            }
        } catch (IOException e) {
            reason = closed.get() ? "You left the chat" : "Connection lost: " + e.getMessage();
        } finally {
            shutdown(reason);
        }
    }

    /** @return false when the server said BYE */
    private boolean handle(String line) {
        int sep = line.indexOf(Protocol.SEP);
        String type = sep < 0 ? line : line.substring(0, sep);
        String payload = sep < 0 ? "" : line.substring(sep + 1);

        switch (type) {
            case Protocol.WELCOME -> username = payload;
            case Protocol.MODE -> {
                readOnly = Protocol.READ_ONLY.equals(payload);
                listener.onConnected(username, readOnly);
            }
            case Protocol.MSG -> listener.onMessage(payload);
            case Protocol.INFO -> listener.onInfo(payload);
            case Protocol.USERS -> listener.onUserList(
                    payload.isEmpty() ? List.of() : Arrays.asList(payload.split(",")));
            case Protocol.BYE -> {
                listener.onInfo(payload);
                return false;
            }
            default -> listener.onInfo(line);
        }
        return true;
    }

    /**
     * Sends user input. Handles the commands too:
     * "allUsers" asks for the user list, "end"/"bye" disconnects.
     */
    public void send(String text) {
        if (!connected || text == null) return;
        String clean = text.replace('\r', ' ').replace('\n', ' ').strip();
        if (clean.isEmpty()) return;

        if (Protocol.isExitCommand(clean)) {
            disconnect();
        } else if (clean.equalsIgnoreCase(Protocol.CMD_ALL_USERS)) {
            out.println(Protocol.CMD_ALL_USERS);
        } else if (readOnly) {
            listener.onInfo("READ-ONLY MODE: you cannot send messages.");
        } else {
            out.println(clean);
        }
    }

    public void requestActiveUsers() {
        send(Protocol.CMD_ALL_USERS);
    }

    /** Notifies the server ("end") and closes the socket. Safe to call more than once. */
    public void disconnect() {
        if (connected && out != null) out.println("end");
        shutdown("You left the chat");
    }

    private void shutdown(String reason) {
        if (!closed.compareAndSet(false, true)) return;
        connected = false;
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) { }
        listener.onDisconnected(reason);
    }

    public boolean isConnected() { return connected; }

    public boolean isReadOnly() { return readOnly; }

    public String getUsername() { return username; }

    public String getHost() { return host; }

    public int getPort() { return port; }
}
