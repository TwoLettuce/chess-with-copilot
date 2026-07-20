package client.websocket;

import chess.ChessGame;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import jakarta.websocket.DeploymentException;
import org.glassfish.tyrus.client.ClientManager;
import websocket.commands.UserGameCommand;
import websocket.messages.ErrorMessage;
import websocket.messages.LoadGameMessage;
import websocket.messages.NotificationMessage;
import websocket.messages.ServerMessage;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;

public class GameWebSocketClient extends Endpoint {
    public interface Listener {
        void onLoadGame(ChessGame game);

        void onNotification(String message);

        void onError(String message);

        void onClose();
    }

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private final Listener listener;
    private Session session;

    public GameWebSocketClient(Listener listener) {
        this.listener = Objects.requireNonNull(listener);
    }

    public void connect(URI uri) throws IOException, DeploymentException {
        ClientManager client = ClientManager.createClient();
        client.connectToServer(this, ClientEndpointConfig.Builder.create().build(), uri);
    }

    @Override
    public void onOpen(Session session, EndpointConfig config) {
        this.session = session;
        session.addMessageHandler(String.class, this::handleMessage);
    }

    public void send(UserGameCommand command) throws IOException {
        ensureSession();
        session.getBasicRemote().sendText(GSON.toJson(command));
    }

    public void close() {
        if (session != null && session.isOpen()) {
            try {
                session.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void handleMessage(String message) {
        ServerMessage baseMessage = GSON.fromJson(message, ServerMessage.class);
        if (baseMessage == null || baseMessage.getServerMessageType() == null) {
            listener.onError(message);
            return;
        }

        switch (baseMessage.getServerMessageType()) {
            case LOAD_GAME -> listener.onLoadGame(GSON.fromJson(message, LoadGameMessage.class).getGame());
            case ERROR -> listener.onError(GSON.fromJson(message, ErrorMessage.class).getErrorMessage());
            case NOTIFICATION -> listener.onNotification(GSON.fromJson(message, NotificationMessage.class).getMessage());
        }
    }

    private void ensureSession() {
        if (session == null || !session.isOpen()) {
            throw new IllegalStateException("WebSocket is not connected.");
        }
    }
}