import com.chat.server.view.ServerApp;
import javafx.application.Application;

/**
 * Entry point:  java TCPServer
 * (IP/port are read from server.properties.)
 *
 * Kept in the default package so the documented command works literally,
 * and outside the Application subclass so the fat JAR can start JavaFX.
 */
public class TCPServer {
    public static void main(String[] args) {
        Application.launch(ServerApp.class, args);
    }
}
