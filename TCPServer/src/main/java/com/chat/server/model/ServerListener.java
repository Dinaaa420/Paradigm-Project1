package com.chat.server.model;

/**
 * Callbacks from the model to whatever presentation layer is attached
 * (JavaFX, console, tests...). Methods are called from background threads.
 */
public interface ServerListener {
    void onLog(String message);

    void onClientConnected(String username);

    void onClientDisconnected(String username);
}
