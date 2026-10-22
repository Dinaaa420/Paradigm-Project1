package com.chat.server.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;

import static java.nio.charset.StandardCharsets.UTF_8;

/** Serves one connected client. Runs on its own virtual thread. */
class ClientHandler implements Runnable {

    private final Socket socket;
    private final ChatServer server;
    private PrintWriter out;
    private volatile String username;
    private volatile boolean readOnly;

    ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), UTF_8));
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), UTF_8), true)) {

            out = writer;

            // 1) Handshake: first line is the username (empty -> READ-ONLY MODE)
            String requested = in.readLine();
            if (requested == null) return;
            readOnly = requested.isBlank();
            username = server.register(this, requested);
            send(Protocol.frame(Protocol.WELCOME, username));
            send(Protocol.frame(Protocol.MODE, readOnly ? Protocol.READ_ONLY : Protocol.READ_WRITE));
            server.announceJoin(this);

            // 2) Message loop
            String line;
            while ((line = in.readLine()) != null) {
                String text = line.strip();
                if (text.isEmpty()) continue;

                if (text.equalsIgnoreCase(Protocol.CMD_ALL_USERS)) {
                    send(Protocol.frame(Protocol.USERS, String.join(",", server.getActiveUsers())));
                } else if (Protocol.isExitCommand(text)) {
                    send(Protocol.frame(Protocol.BYE, "Goodbye " + username + "!"));
                    break;
                } else if (readOnly) {
                    send(Protocol.frame(Protocol.INFO, "You are in READ-ONLY MODE and cannot send messages."));
                } else {
                    server.broadcastMessage(username, text);
                }
            }
        } catch (IOException e) {
            // client dropped the connection — handled in finally
        } finally {
            server.remove(this);
        }
    }

    synchronized void send(String line) {
        if (out != null) out.println(line);
    }

    void close() {
        try {
            socket.close();
        } catch (IOException ignored) { }
    }

    String getUsername() {
        return username;
    }

    boolean isReadOnly() {
        return readOnly;
    }
}
