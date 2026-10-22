package com.chat.server.view;

import com.chat.server.model.ChatServer;
import com.chat.server.model.ServerConfig;
import com.chat.server.model.ServerListener;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.stage.Stage;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * JavaFX view of the server. It only displays what the model reports,
 * so the UI can be replaced without touching {@link ChatServer}.
 */
public class ServerApp extends Application implements ServerListener {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ObservableList<String> users = FXCollections.observableArrayList();
    private final Map<String, String> userColors = new HashMap<>();
    private final Random random = new Random();

    private TextArea logArea;
    private Label countLabel;
    private ChatServer server;

    @Override
    public void start(Stage stage) {
        ServerConfig config;
        try {
            config = ServerConfig.load();
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "Cannot load configuration: " + e.getMessage()).showAndWait();
            Platform.exit();
            return;
        }

        GridPane root = new GridPane();
        root.getStyleClass().add("root-pane");
        root.setHgap(14);
        root.setVgap(10);
        root.setPadding(new Insets(18));

        Label title = new Label("TCP Chat Server");
        title.getStyleClass().add("title");
        Label address = new Label("Listening on " + config.host() + ":" + config.port());
        address.getStyleClass().add("subtitle");

        countLabel = new Label("Connected Users (0)");
        countLabel.getStyleClass().add("section");
        Label logTitle = new Label("Server Log");
        logTitle.getStyleClass().add("section");

        ListView<String> userList = new ListView<>(users);
        userList.getStyleClass().add("user-list");
        userList.setPlaceholder(new Label("No clients connected"));
        userList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                if (empty || name == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(name);
                    setStyle("-fx-background-color: " + colorFor(name) + "; -fx-text-fill: #1f2937;");
                }
            }
        });

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.getStyleClass().add("log-area");

        root.add(title, 0, 0, 2, 1);
        root.add(address, 0, 1, 2, 1);
        root.add(countLabel, 0, 2);
        root.add(logTitle, 1, 2);
        root.add(userList, 0, 3);
        root.add(logArea, 1, 3);

        ColumnConstraints left = new ColumnConstraints();
        left.setPercentWidth(32);
        ColumnConstraints right = new ColumnConstraints();
        right.setPercentWidth(68);
        root.getColumnConstraints().addAll(left, right);
        RowConstraints fixed = new RowConstraints();
        RowConstraints grow = new RowConstraints();
        grow.setVgrow(Priority.ALWAYS);
        root.getRowConstraints().addAll(fixed, fixed, fixed, grow);

        Scene scene = new Scene(root, 860, 540);
        scene.getStylesheets().add(getClass().getResource("/server.css").toExternalForm());
        stage.setTitle("Chat Server");
        stage.setScene(scene);
        stage.show();

        server = new ChatServer(config, this);
        server.start();
    }

    @Override
    public void stop() {
        if (server != null) server.stop();
    }

    /** Random pastel colour per user, kept stable for that user. */
    private String colorFor(String name) {
        return userColors.computeIfAbsent(name,
                n -> String.format("hsb(%d, 35%%, 96%%)", random.nextInt(360)));
    }

    // ---- ServerListener: called from background threads -> hop to FX thread ----

    @Override
    public void onLog(String message) {
        String line = "[" + LocalTime.now().format(TIME) + "] " + message + "\n";
        Platform.runLater(() -> logArea.appendText(line));
    }

    @Override
    public void onClientConnected(String username) {
        Platform.runLater(() -> {
            users.add(username);
            countLabel.setText("Connected Users (" + users.size() + ")");
        });
    }

    @Override
    public void onClientDisconnected(String username) {
        Platform.runLater(() -> {
            users.remove(username);
            userColors.remove(username);
            countLabel.setText("Connected Users (" + users.size() + ")");
        });
    }
}
