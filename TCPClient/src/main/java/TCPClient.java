import com.chat.client.view.ClientApp;
import javafx.application.Application;

/**
 * Entry point:  java TCPClient <ServerIPAddress> <PortNumber>
 * e.g.          java TCPClient localhost 3000
 *
 * Without arguments the address is taken from client.properties.
 */
public class TCPClient {
    public static void main(String[] args) {
        if (args.length != 0 && args.length != 2) {
            System.err.println("Usage: java TCPClient <ServerIPAddress> <PortNumber>");
            System.exit(1);
        }
        Application.launch(ClientApp.class, args);
    }
}
