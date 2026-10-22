package com.chat.client.model;

import java.util.List;

/**
 * Callbacks from the client model to the presentation layer.
 * Called from the background reader thread.
 */
public interface ClientListener {
    /** Handshake finished; the server assigned this name and mode. */
    void onConnected(String username, boolean readOnly);

    void onMessage(String formattedMessage);

    void onInfo(String text);

    void onUserList(List<String> users);

    void onDisconnected(String reason);
}
