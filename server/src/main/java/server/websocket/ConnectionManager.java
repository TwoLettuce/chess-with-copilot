package server.websocket;

import chess.ChessGame;
import io.javalin.websocket.WsContext;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionManager {
    private final Map<String, WsContext> contextsBySessionId = new ConcurrentHashMap<>();
    private final Map<String, ConnectionInfo> connectionsBySessionId = new ConcurrentHashMap<>();
    private final Map<Integer, Set<String>> sessionIdsByGameId = new ConcurrentHashMap<>();

    public void connect(String sessionId, WsContext context, int gameId, String username,
                        ChessGame.TeamColor color, boolean observer) {
        contextsBySessionId.put(sessionId, context);
        connectionsBySessionId.put(sessionId, new ConnectionInfo(gameId, username, color, observer));
        sessionIdsByGameId.computeIfAbsent(gameId, ignored -> ConcurrentHashMap.newKeySet()).add(sessionId);
    }

    public void remove(String sessionId) {
        ConnectionInfo connection = connectionsBySessionId.remove(sessionId);
        contextsBySessionId.remove(sessionId);
        if (connection != null) {
            Set<String> sessionIds = sessionIdsByGameId.get(connection.gameId());
            if (sessionIds != null) {
                sessionIds.remove(sessionId);
                if (sessionIds.isEmpty()) {
                    sessionIdsByGameId.remove(connection.gameId());
                }
            }
        }
    }

    public ConnectionInfo get(String sessionId) {
        return connectionsBySessionId.get(sessionId);
    }

    public Set<String> sessionIdsForGame(int gameId) {
        return sessionIdsByGameId.getOrDefault(gameId, Set.of());
    }

    public WsContext context(String sessionId) {
        return contextsBySessionId.get(sessionId);
    }

    public record ConnectionInfo(int gameId, String username, ChessGame.TeamColor color, boolean observer) {
    }
}