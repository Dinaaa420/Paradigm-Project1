package com.chat.client.view;

import com.chat.client.model.ChatClient;
import com.chat.client.model.ClientConfig;
import com.chat.client.model.ClientListener;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;

/**
 * JavaFX view of the client. Talks to the network only through {@link ChatClient}.
 */
public class ClientApp extends Application implements ClientListener {

    private enum Kind { MESSAGE, INFO, USERS }

    private record ChatLine(String text, Kind kind) { }

    private final ObservableList<ChatLine> lines = FXCollections.observableArrayList();

    private ClientConfig config;
    private String configError;
    private ChatClient client;
    private Stage stage;

    // chat-screen controls
    private Label statusLabel;
    private Circle statusDot;
    private TextArea input;
    private Button sendButton;
    private Button leaveButton;

    @Override
    public void init() {
        try {
            config = ClientConfig.load(getParameters().getRaw());
        } catch (IOException e) {
            configError = e.getMessage();
        }
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        if (configError != null) {
            System.err.println(configError);
            new Alert(Alert.AlertType.ERROR, configError).showAndWait();
            Platform.exit();
            return;
        }
        stage.setTitle("Chat Client");
        stage.setScene(buildLoginScene());
        stage.show();
    }

    @Override
    public void stop() {
        if (client != null) client.disconnect();
    }

    // ------------------------------------------------------------------ login screen

    private Scene buildLoginScene() {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("root-pane");
        grid.setAlignment(Pos.CENTER);
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(30));

        Label title = new Label("Join the Group Chat");
        title.getStyleClass().add("title");
        Label server = new Label("Server: " + config.host() + ":" + config.port());
        server.getStyleClass().add("subtitle");

        TextField nameField = new TextField();
        nameField.setPromptText("Username");
        nameField.setPrefWidth(260);
        Button joinButton = new Button("JOIN");
        joinButton.getStyleClass().add("primary");
        joinButton.setDefaultButton(true);

        Label hint = new Label("Leave the username empty to join in READ-ONLY MODE.");
        hint.getStyleClass().add("hint");
        Label error = new Label();
        error.getStyleClass().add("error");

        grid.add(title, 0, 0, 2, 1);
        grid.add(server, 0, 1, 2, 1);
        grid.add(nameField, 0, 2);
        grid.add(joinButton, 1, 2);
        grid.add(hint, 0, 3, 2, 1);
        grid.add(error, 0, 4, 2, 1);

        joinButton.setOnAction(e -> {
            joinButton.setDisable(true);
            error.setText("Connecting...");
            String name = nameField.getText();
            client = new ChatClient(config.host(), config.port(), this);
            Thread.ofVirtual().start(() -> {
                try {
                    client.connect(name);
                } catch (IOException ex) {
                    Platform.runLater(() -> {
                        error.setText("Cannot reach " + config.host() + ":" + config.port() + " (" + ex.getMessage() + ")");
                        joinButton.setDisable(false);
                    });
                }
            });
        });

        Scene scene = new Scene(grid, 520, 300);
        scene.getStylesheets().add(getClass().getResource("/client.css").toExternalForm());
        return scene;
    }

    // ------------------------------------------------------------------ chat screen

    private Scene buildChatScene(String username, boolean readOnly) {
        GridPane root = new GridPane();
        root.getStyleClass().add("root-pane");
        root.setHgap(10);
        root.setVgap(10);
        root.setPadding(new Insets(16));

        // header: title + "Online" indicator
        Label title = new Label("Group Chat");
        title.getStyleClass().add("title");
        Label who = new Label("Signed in as " + username);
        who.getStyleClass().add("subtitle");
        VBox titles = new VBox(2, title, who);

        statusDot = new Circle(7);
        statusDot.getStyleClass().add("dot-online");
        statusLabel = new Label("Online");
        statusLabel.getStyleClass().add("status-online");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, titles, spacer, statusDot, statusLabel);
        header.setAlignment(Pos.CENTER_LEFT);

        Label readOnlyBanner = new Label("READ-ONLY MODE — you can read messages but not send them. Rejoin with a username to chat.");
        readOnlyBanner.getStyleClass().add("readonly-banner");
        readOnlyBanner.setMaxWidth(Double.MAX_VALUE);
        readOnlyBanner.setWrapText(true);
        readOnlyBanner.setVisible(readOnly);
        readOnlyBanner.setManaged(readOnly);

        // messages
        ListView<ChatLine> chatList = new ListView<>(lines);
        chatList.getStyleClass().add("chat-list");
        chatList.setFocusTraversable(false);
        chatList.setCellFactory(lv -> new ListCell<>() {
            {
                setWrapText(true);
                prefWidthProperty().bind(lv.widthProperty().subtract(24));
            }

            @Override
            protected void updateItem(ChatLine line, boolean empty) {
                super.updateItem(line, empty);
                getStyleClass().removeAll("line-message", "line-info", "line-users");
                if (empty || line == null) {
                    setText(null);
                } else {
                    setText(line.text());
                    getStyleClass().add(switch (line.kind()) {
                        case MESSAGE -> "line-message";
                        case INFO -> "line-info";
                        case USERS -> "line-users";
                    });
                }
            }
        });
        lines.addListener((ListChangeListener<ChatLine>) c -> chatList.scrollTo(lines.size() - 1));

        // input
        input = new TextArea();
        input.setPromptText(readOnly ? "READ-ONLY MODE" : "Type a message... (Enter to send, Shift+Enter for a new line)");
        input.setPrefRowCount(2);
        input.setWrapText(true);
        input.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                e.consume();
                if (e.isShiftDown()) input.insertText(input.getCaretPosition(), "\n");
                else sendCurrentText();
            }
        });

        sendButton = new Button("SEND");
        sendButton.getStyleClass().add("primary");
        sendButton.setMaxWidth(Double.MAX_VALUE);
        sendButton.setOnAction(e -> sendCurrentText());

        leaveButton = new Button("LEAVE");
        leaveButton.getStyleClass().add("danger");
        leaveButton.setMaxWidth(Double.MAX_VALUE);
        leaveButton.setOnAction(e -> client.disconnect());

        if (readOnly) {
            input.setDisable(true);
            sendButton.setDisable(true);
        }

        VBox buttons = new VBox(8, sendButton, leaveButton);
        buttons.setPrefWidth(100);

        Label hint = new Label("Commands: allUsers = list active users   ·   end / bye = leave");
        hint.getStyleClass().add("hint");

        root.add(header, 0, 0, 2, 1);
        root.add(readOnlyBanner, 0, 1, 2, 1);
        root.add(chatList, 0, 2, 2, 1);
        root.add(input, 0, 3);
        root.add(buttons, 1, 3);
        root.add(hint, 0, 4, 2, 1);

        ColumnConstraints grow = new ColumnConstraints();
        grow.setHgrow(Priority.ALWAYS);
        ColumnConstraints fixed = new ColumnConstraints();
        root.getColumnConstraints().addAll(grow, fixed);
        RowConstraints auto = new RowConstraints();
        RowConstraints growRow = new RowConstraints();
        growRow.setVgrow(Priority.ALWAYS);
        root.getRowConstraints().addAll(auto, auto, growRow, auto, auto);

        Scene scene = new Scene(root, 640, 560);
        scene.getStylesheets().add(getClass().getResource("/client.css").toExternalForm());
        return scene;
    }

    private void sendCurrentText() {
        String text = input.getText();
        if (text == null || text.isBlank()) return;
        input.clear();
        client.send(text);
    }

    private void setOffline() {
        statusLabel.setText("Offline");
        statusLabel.getStyleClass().setAll("label", "status-offline");
        statusDot.getStyleClass().setAll("dot-offline");
        input.setDisable(true);
        sendButton.setDisable(true);
        leaveButton.setDisable(true);
    }

    // ------------------------------------------------------------------ ClientListener (background thread)

    @Override
    public void onConnected(String username, boolean readOnly) {
        Platform.runLater(() -> {
            stage.setTitle("Chat Client — " + username + (readOnly ? " (READ-ONLY)" : ""));
            stage.setScene(buildChatScene(username, readOnly));
            if (!readOnly) input.requestFocus();
        });
    }

    @Override
    public void onMessage(String formattedMessage) {
        Platform.runLater(() -> lines.add(new ChatLine(formattedMessage, Kind.MESSAGE)));
    }

    @Override
    public void onInfo(String text) {
        Platform.runLater(() -> lines.add(new ChatLine(text, Kind.INFO)));
    }

    @Override
    public void onUserList(List<String> users) {
        String text = "Active users (" + users.size() + "): " + String.join(", ", users);
        Platform.runLater(() -> lines.add(new ChatLine(text, Kind.USERS)));
    }

    @Override
    public void onDisconnected(String reason) {
        Platform.runLater(() -> {
            lines.add(new ChatLine(reason + " — disconnected from server.", Kind.INFO));
            if (statusLabel != null) setOffline();
        });
    }
}
