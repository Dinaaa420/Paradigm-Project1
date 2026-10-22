package com.chat.server.model;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The central server: accepts connections and distributes messages.
 * Each client is served by its own virtual thread (Java 21).
 * Contains no UI code — it reports everything through {@link ServerListener}.
 */
public class ChatServer {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MAX_NAME_LENGTH = 20;

    private final ServerConfig config;
    private final ServerListener listener;
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final AtomicInteger guestCounter = new AtomicInteger();

    private volatile boolean running;
    private volatile ServerSocket serverSocket;
    private ExecutorService executor;

    public ChatServer(ServerConfig config, ServerListener listener) {
        this.config = config;
        this.listener = listener;
    }

    /** Starts listening in the background; returns immediately. */
    public void start() {
        if (running) return;
        running = true;
        executor = Executors.newVirtualThreadPerTaskExecutor();
        executor.submit(this::acceptLoop);
    }

    private void acceptLoop() {
        try (ServerSocket ss = new ServerSocket()) {
            ss.setReuseAddress(true);
            ss.bind(new InetSocketAddress(config.host(), config.port()));
            serverSocket = ss;
            listener.onLog("Server Started on " + config.host() + ":" + config.port());
            while (running) {
                listener.onLog("Waiting for Client...");
                Socket socket = ss.accept();
                listener.onLog("New connection from " + socket.getRemoteSocketAddress());
                executor.submit(new ClientHandler(socket, this));   // one virtual thread per client
            }
        } catch (IOException e) {
            if (running) listener.onLog("Server error: " + e.getMessage());
        } finally {
            running = false;
            listener.onLog("Server Stopped");
        }
    }

    /** Reserves a unique username for the handler and returns it. */
    synchronized String register(ClientHandler handler, String requested) {
        String base = sanitize(requested);
        if (base.isEmpty()) base = "Guest-" + guestCounter.incrementAndGet();
        String name = base;
        for (int i = 2; clients.containsKey(name); i++) name = base + "_" + i;
        clients.put(name, handler);
        return name;
    }

    void announceJoin(ClientHandler handler) {
        String name = handler.getUsername();
        listener.onLog("Welcome " + name + (handler.isReadOnly() ? " (READ-ONLY MODE)" : ""));
        listener.onClientConnected(name);
        broadcast(Protocol.frame(Protocol.INFO, name + " joined the chat"));
    }

    /** Formats a message with the sender name and time, then sends it to everyone. */
    void broadcastMessage(String sender, String text) {
        String formatted = "[" + LocalTime.now().format(TIME) + "] " + sender + ": " + text;
        listener.onLog(formatted);
        broadcast(Protocol.frame(Protocol.MSG, formatted));
    }

    private void broadcast(String line) {
        for (ClientHandler c : clients.values()) c.send(line);
    }

    void remove(ClientHandler handler) {
        String name = handler.getUsername();
        if (name != null && clients.remove(name, handler)) {
            listener.onLog(name + " disconnected");
            listener.onClientDisconnected(name);
            broadcast(Protocol.frame(Protocol.INFO, name + " left the chat"));
        }
    }

    public List<String> getActiveUsers() {
        List<String> names = new ArrayList<>();
        clients.forEach((name, h) -> names.add(h.isReadOnly() ? name + " (read-only)" : name));
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public boolean isRunning() {
        return running;
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) { }
        clients.values().forEach(ClientHandler::close);
        if (executor != null) executor.shutdownNow();
    }

    private static String sanitize(String name) {
        if (name == null) return "";
        String s = name.replace(String.valueOf(Protocol.SEP), "").replace(",", "").strip();
        return s.length() > MAX_NAME_LENGTH ? s.substring(0, MAX_NAME_LENGTH) : s;
    }
}
