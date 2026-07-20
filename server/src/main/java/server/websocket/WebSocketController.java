package server.websocket;

import chess.ChessGame;
import chess.ChessMove;
import chess.InvalidMoveException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dataaccess.DataAccessException;
import io.javalin.websocket.WsContext;
import io.javalin.websocket.WsMessageContext;
import model.AuthData;
import model.GameData;
import server.websocket.ConnectionManager.ConnectionInfo;
import service.AuthService;
import service.GameService;
import websocket.commands.UserGameCommand;
import websocket.messages.ErrorMessage;
import websocket.messages.LoadGameMessage;
import websocket.messages.NotificationMessage;

import java.util.Set;

public class WebSocketController {
    private final AuthService authService;
    private final GameService gameService;
    private final ConnectionManager connectionManager;
    private final Gson gson = new GsonBuilder().serializeNulls().create();
    private final Set<Integer> completedGames = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public WebSocketController(AuthService authService, GameService gameService, ConnectionManager connectionManager) {
        this.authService = authService;
        this.gameService = gameService;
        this.connectionManager = connectionManager;
    }

    public void onMessage(WsMessageContext ctx) {
        try {
            UserGameCommand command = gson.fromJson(ctx.message(), UserGameCommand.class);
            if (command == null || command.getCommandType() == null) {
                sendError(ctx, "Error: bad request");
                return;
            }

            switch (command.getCommandType()) {
                case CONNECT -> handleConnect(ctx, command);
                case MAKE_MOVE -> handleMakeMove(ctx, command);
                case LEAVE -> handleLeave(ctx, command);
                case RESIGN -> handleResign(ctx, command);
            }
        } catch (DataAccessException e) {
            sendError(ctx, formatError(e));
        } catch (InvalidMoveException e) {
            sendError(ctx, "Error: invalid move");
        } catch (RuntimeException e) {
            sendError(ctx, "Error: bad request");
        }
    }

    public void onClose(WsContext ctx) {
        connectionManager.remove(ctx.sessionId());
    }

    public void onConnect(WsContext ctx) {
        // Gameplay connections are established by CONNECT commands.
    }

    public void onError(WsContext ctx) {
        // No-op; the command handlers manage protocol errors.
    }

    private void handleConnect(WsContext ctx, UserGameCommand command) throws DataAccessException {
        AuthData auth = authService.getAuth(command.getAuthToken());
        GameData game = gameService.getGame(command.getGameID());
        String username = auth.username();
        ChessGame.TeamColor color = roleColor(game, username);
        boolean observer = color == null;

        connectionManager.connect(ctx.sessionId(), ctx, game.gameID(), username, color, observer);
        ctx.send(gson.toJson(new LoadGameMessage(game.game())));

        String message = observer
                ? username + " joined as an observer."
                : username + " joined as " + color.name().toLowerCase() + ".";
        broadcastToGame(game.gameID(), new NotificationMessage(message), ctx.sessionId());
    }

    private void handleMakeMove(WsContext ctx, UserGameCommand command) throws DataAccessException, InvalidMoveException {
        ConnectionInfo connection = requireConnection(ctx);
        if (connection.observer()) {
            sendError(ctx, "Error: observers cannot make moves");
            return;
        }
        if (completedGames.contains(connection.gameId())) {
            sendError(ctx, "Error: game is over");
            return;
        }

        AuthData auth = authService.getAuth(command.getAuthToken());
        if (!auth.username().equals(connection.username())) {
            sendError(ctx, "Error: unauthorized");
            return;
        }

        GameData game = gameService.getGame(connection.gameId());
        ChessMove move = command.getMove();
        if (move == null) {
            sendError(ctx, "Error: bad request");
            return;
        }

        ChessGame gameState = game.game();
        ChessGame.TeamColor movingPieceColor = pieceColor(gameState, move.getStartPosition());
        if (movingPieceColor == null || movingPieceColor != connection.color()) {
            sendError(ctx, "Error: invalid move");
            return;
        }

        gameState.makeMove(move);
        gameService.updateGame(new GameData(game.gameID(), game.whiteUsername(), game.blackUsername(), game.gameName(), gameState));

        broadcastToGame(game.gameID(), new LoadGameMessage(gameState), null);
        broadcastToGame(game.gameID(), new NotificationMessage(auth.username() + " moved " + move), ctx.sessionId());

        ChessGame.TeamColor opponent = gameState.getTeamTurn();
        if (gameState.isInCheckmate(opponent)) {
            completedGames.add(game.gameID());
            broadcastToGame(game.gameID(), new NotificationMessage(auth.username() + " is in checkmate."), null);
        } else if (gameState.isInStalemate(opponent)) {
            completedGames.add(game.gameID());
            broadcastToGame(game.gameID(), new NotificationMessage("Game ended in stalemate."), null);
        } else if (gameState.isInCheck(opponent)) {
            broadcastToGame(game.gameID(), new NotificationMessage(opponent.name().toLowerCase() + " is in check."), null);
        }
    }

    private void handleLeave(WsContext ctx, UserGameCommand command) throws DataAccessException {
        ConnectionInfo connection = requireConnection(ctx);
        AuthData auth = authService.getAuth(command.getAuthToken());
        if (!auth.username().equals(connection.username())) {
            sendError(ctx, "Error: unauthorized");
            return;
        }

        GameData game = gameService.getGame(connection.gameId());
        if (!connection.observer()) {
            GameData updated = new GameData(game.gameID(), replaceIfMatch(game.whiteUsername(), connection.username()),
                    replaceIfMatch(game.blackUsername(), connection.username()), game.gameName(), game.game());
            gameService.updateGame(updated);
        }

        connectionManager.remove(ctx.sessionId());
        broadcastToGame(game.gameID(), new NotificationMessage(auth.username() + " left the game."), ctx.sessionId());
    }

    private void handleResign(WsContext ctx, UserGameCommand command) throws DataAccessException {
        ConnectionInfo connection = requireConnection(ctx);
        if (connection.observer()) {
            sendError(ctx, "Error: observers cannot resign");
            return;
        }
        AuthData auth = authService.getAuth(command.getAuthToken());
        if (!auth.username().equals(connection.username())) {
            sendError(ctx, "Error: unauthorized");
            return;
        }

        if (completedGames.add(connection.gameId())) {
            broadcastToGame(connection.gameId(), new NotificationMessage(auth.username() + " resigned."), null);
        } else {
            sendError(ctx, "Error: game is over");
        }
    }

    private ConnectionInfo requireConnection(WsContext ctx) throws DataAccessException {
        ConnectionInfo connection = connectionManager.get(ctx.sessionId());
        if (connection == null) {
            throw new DataAccessException("unauthorized");
        }
        return connection;
    }

    private void broadcastToGame(int gameId, Object payload, String excludeSessionId) {
        String json = gson.toJson(payload);
        for (String sessionId : connectionManager.sessionIdsForGame(gameId)) {
            if (excludeSessionId != null && excludeSessionId.equals(sessionId)) {
                continue;
            }
            WsContext context = connectionManager.context(sessionId);
            if (context != null) {
                context.send(json);
            }
        }
    }

    private void sendError(WsContext ctx, String message) {
        ctx.send(gson.toJson(new ErrorMessage(message)));
    }

    private String formatError(DataAccessException e) {
        String message = e.getMessage() == null ? "bad request" : e.getMessage();
        return message.startsWith("Error:") ? message : "Error: " + message;
    }

    private ChessGame.TeamColor roleColor(GameData game, String username) {
        if (username != null && username.equals(game.whiteUsername())) {
            return ChessGame.TeamColor.WHITE;
        }
        if (username != null && username.equals(game.blackUsername())) {
            return ChessGame.TeamColor.BLACK;
        }
        return null;
    }

    private ChessGame.TeamColor pieceColor(ChessGame game, chess.ChessPosition position) {
        chess.ChessPiece piece = game.getBoard().getPiece(position);
        return piece == null ? null : piece.getTeamColor();
    }

    private String replaceIfMatch(String currentValue, String username) {
        return username != null && username.equals(currentValue) ? null : currentValue;
    }
}