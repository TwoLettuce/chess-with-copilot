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
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class GameWebSocketClient extends Endpoint {
    public interface Listener {
        void onLoadGame(ChessGame game);

        void onNotification(String message);

        void onError(String message);

        void onClose();
    }

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private static final long HEARTBEAT_INTERVAL_SECONDS = 10;

    private final Listener listener;
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ws-heartbeat");
        thread.setDaemon(true);
        return thread;
    });
    private Session session;
    private ScheduledFuture<?> heartbeatTask;
    private UserGameCommand initialCommand;

    public GameWebSocketClient(Listener listener) {
        this.listener = Objects.requireNonNull(listener);
    }

    public void connect(URI uri, UserGameCommand initialCommand) throws IOException, DeploymentException {
        this.initialCommand = initialCommand;
        ClientManager client = ClientManager.createClient();
        client.connectToServer(this, ClientEndpointConfig.Builder.create().build(), uri);
    }

    @Override
    public void onOpen(Session session, EndpointConfig config) {
        this.session = session;
        session.setMaxIdleTimeout(0);
        session.addMessageHandler(String.class, this::handleMessage);
        startHeartbeat();
        if (initialCommand != null) {
            try {
                send(initialCommand);
            } catch (IOException e) {
                listener.onError(e.getMessage() == null ? "WebSocket error" : e.getMessage());
            } finally {
                initialCommand = null;
            }
        }
    }

    public void send(UserGameCommand command) throws IOException {
        ensureSession();
        session.getBasicRemote().sendText(GSON.toJson(command));
    }

    public boolean isConnected() {
        return session != null && session.isOpen();
    }

    public void close() {
        shutdownHeartbeat();
        if (session != null && session.isOpen()) {
            try {
                session.close();
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public void onError(Session session, Throwable thr) {
        shutdownHeartbeat();
        listener.onError(thr == null || thr.getMessage() == null ? "WebSocket error" : thr.getMessage());
    }

    @Override
    public void onClose(Session session, jakarta.websocket.CloseReason closeReason) {
        shutdownHeartbeat();
        listener.onClose();
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

    private void startHeartbeat() {
        cancelHeartbeat();
        heartbeatTask = heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                if (session != null && session.isOpen()) {
                    session.getBasicRemote().sendPing(ByteBuffer.allocate(0));
                }
            } catch (Exception ignored) {
                // The normal close/error path will surface the disconnect.
            }
        }, 0, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void cancelHeartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(true);
            heartbeatTask = null;
        }
    }

    private void shutdownHeartbeat() {
        cancelHeartbeat();
        heartbeatExecutor.shutdownNow();
    }
}