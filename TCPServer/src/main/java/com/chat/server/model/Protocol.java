package com.chat.server.model;

/**
 * Line-based text protocol shared by server and client.
 *
 * Client -> Server
 *   1st line : requested username (empty line = READ-ONLY MODE)
 *   then     : chat text, or the commands "allUsers", "end", "bye"
 *
 * Server -> Client  (TYPE|payload)
 *   WELCOME|name        assigned username
 *   MODE|READ_ONLY      or MODE|READ_WRITE
 *   MSG|[time] user: x  a chat message
 *   INFO|text           system notice
 *   USERS|a,b,c         reply to "allUsers"
 *   BYE|text            server confirms disconnection
 */
public final class Protocol {
    public static final char SEP = '|';

    public static final String WELCOME = "WELCOME";
    public static final String MODE = "MODE";
    public static final String MSG = "MSG";
    public static final String INFO = "INFO";
    public static final String USERS = "USERS";
    public static final String BYE = "BYE";

    public static final String READ_ONLY = "READ_ONLY";
    public static final String READ_WRITE = "READ_WRITE";

    public static final String CMD_ALL_USERS = "allUsers";

    private Protocol() { }

    public static String frame(String type, String payload) {
        return type + SEP + payload;
    }

    public static boolean isExitCommand(String text) {
        return text.equalsIgnoreCase("end") || text.equalsIgnoreCase("bye");
    }
}
