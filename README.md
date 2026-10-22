# Multi-Client Chat Application — Java Sockets (TCP) + JavaFX

A group chat where many clients connect to one central server over TCP.
The server receives a message from any client, stamps it with the sender's
username and time, and distributes it to every connected client.

| | |
|---|---|
| Language | Java 21 |
| Networking | `java.net.Socket` / `ServerSocket` (TCP) |
| Concurrency | **Virtual threads** — one per client (`Executors.newVirtualThreadPerTaskExecutor()`) |
| GUI | JavaFX 21 (`GridPane` layouts + CSS) |
| Build | Maven (two independent projects) |
| Config | `server.properties` / `client.properties`, loaded at runtime |

## Features

**Client**
- Login screen asks for a username before showing the chat.
- Empty username → **READ-ONLY MODE**: messages are visible but the input and SEND button are disabled (also enforced by the client model and the server).
- Send with the **SEND** button or **Enter** (Shift+Enter = new line).
- `allUsers` → the server replies with the list of active users, to that user only.
- `end`, `bye` or the **LEAVE** button → notifies the server and closes the connection.
- **Online** label with a green circle; switches to red **Offline** when disconnected.

**Server**
- Accepts many concurrent clients (virtual thread per client).
- Formats each message as `[HH:mm:ss] username: text` and broadcasts it.
- Live `ListView` of connected users, each with a random background colour.
- Activity log: `Server Started`, `Waiting for Client...`, `Welcome <user>`, messages, disconnections.

## Project structure

```
Paradigm-Project1/
├── TCPServer/                                          Maven project #1 (server)
│   ├── pom.xml
│   └── src/main/
│       ├── java/
│       │   ├── TCPServer.java                          entry point  (java TCPServer)
│       │   └── com/chat/server/
│       │       ├── model/                              no JavaFX imports
│       │       │   ├── ChatServer.java                 accept loop, broadcast, user registry
│       │       │   ├── ClientHandler.java              one per client (virtual thread)
│       │       │   ├── ServerConfig.java               loads server.properties
│       │       │   ├── ServerListener.java             model → view callbacks
│       │       │   └── Protocol.java                   message format constants
│       │       └── view/
│       │           └── ServerApp.java                  JavaFX UI (log, user ListView)
│       └── resources/
│           ├── server.properties                       IP and port configuration
│           └── server.css
│
│
├── TCPClient/                                          Maven project #2 (client)
│   ├── pom.xml
│   └── src/main/
│       ├── java/
│       │   ├── TCPClient.java                          entry point  (java TCPClient <ip> <port>)
│       │   └── com/chat/client/
│       │       ├── model/                              no JavaFX imports
│       │       │   ├── ChatClient.java                 socket + reader thread
│       │       │   ├── ClientConfig.java               command-line args / client.properties
│       │       │   ├── ClientListener.java             model → view callbacks
│       │       │   └── Protocol.java
│       │       └── view/
│       │           └── ClientApp.java                  JavaFX UI (login + chat screens)
│       └── resources/
│           ├── client.properties
│           └── client.css
├── artifacts/                                         Executable JARs
│   ├── TCPServer.jar
│   └── TCPClient.jar
├── docs/                                              UML diagrams (.puml source + .png export)
│   ├── class diagram
│   └── deployment diagram
│
├── .gitignore
└── README.md

> **Note:** The JARs bundle JavaFX libraries built for Windows, so they run on Windows only. On another OS, rebuild with `mvn clean package` in each project folder.
```

### Separation of Model and View
The `model` packages contain all socket and business logic and never import JavaFX.
They report events through an interface (`ServerListener`, `ClientListener`).
The JavaFX classes implement that interface and only update the screen
(using `Platform.runLater`, because callbacks arrive on network threads).
You could replace the JavaFX UI with a console UI without changing a single model class.

### Protocol (one line of text per message, UTF-8)

| Direction | Line | Meaning |
|---|---|---|
| client → server | first line | requested username (empty = read-only) |
| client → server | any text | chat message |
| client → server | `allUsers` / `end` / `bye` | commands |
| server → client | `WELCOME\|name` | name assigned (duplicates get `_2`, guests get `Guest-N`) |
| server → client | `MODE\|READ_ONLY` or `MODE\|READ_WRITE` | |
| server → client | `MSG\|[time] user: text` | chat message |
| server → client | `INFO\|text` | join/leave and system notices |
| server → client | `USERS\|a,b,c` | answer to `allUsers` |
| server → client | `BYE\|text` | disconnection confirmed |

## Requirements
- JDK **21** or newer (virtual threads)
- Maven 3.8+ (bundled with IntelliJ IDEA)
- Internet access the first time, so Maven can download JavaFX

## Build

```bash
cd TCPServer && mvn clean package     # -> TCPServer/target/TCPServer.jar
cd ../TCPClient && mvn clean package  # -> TCPClient/target/TCPClient.jar
```

The JARs are executable "fat" JARs that already contain JavaFX for the OS you
built on (build on Windows → runs on Windows, etc.).

## Run

**Server**
```bash
java -jar TCPServer/target/TCPServer.jar
# or, exactly as in the spec:
java -cp TCPServer/target/TCPServer.jar TCPServer
```

**Clients** (start as many as you like)
```bash
java -jar TCPClient/target/TCPClient.jar localhost 3000
# or
java -cp TCPClient/target/TCPClient.jar TCPClient localhost 3000
```

**From IntelliJ / Maven without building a JAR**
```bash
mvn javafx:run                                  # server
mvn javafx:run -Djavafx.args="localhost 3000"   # client
```

### Changing IP / port without recompiling
Put a `server.properties` file next to where you start the server (or pass
`-Dserver.config=path/to/file`):

```properties
server.host=0.0.0.0
server.port=4000
```

The client reads `client.properties` the same way when started without arguments;
command-line arguments always win.

### Running on several computers
1. Start the server on one machine and find its IP (`ipconfig` / `ip a`), e.g. `192.168.1.10`.
2. Allow the port through that machine's firewall.
3. On other machines: `java -jar TCPClient.jar 192.168.1.10 3000`.

## UML diagrams
Source files are in `docs/` (`.puml`). To get images: install the **PlantUML Integration**
plugin in IntelliJ and use *Save diagram*, or paste the file into
<https://www.plantuml.com/plantuml>.

- `class-diagram.puml` — classes and relationships
- `deployment-diagram.puml` — server node, client nodes, TCP links
- `sequence-diagram.puml` — join → message → allUsers → leave
- `use-case-diagram.puml` — actors and use cases

## Author
Dina Daoudi
