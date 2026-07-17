package server;

import chess.ChessGame;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import dataaccess.DataAccess;
import dataaccess.DataAccessException;
import dataaccess.MySqlDataAccess;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import model.AuthData;
import model.GameData;
import model.UserData;
import service.AuthService;
import service.GameService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class Server {
    private final Javalin javalin;
    private final Gson gson = new Gson();
    private final AuthService authService;
    private final GameService gameService;

    public Server() {
        DataAccess dataAccess = new MySqlDataAccess();
        authService = new AuthService(dataAccess);
        gameService = new GameService(dataAccess);

        javalin = Javalin.create(config -> config.staticFiles.add("web"));

        javalin.delete("/db", this::clearDatabase);
        javalin.post("/user", this::register);
        javalin.post("/session", this::login);
        javalin.delete("/session", this::logout);
        javalin.get("/game", this::listGames);
        javalin.post("/game", this::createGame);
        javalin.put("/game", this::joinGame);

        javalin.exception(DataAccessException.class, this::handleDataAccessException);
    }

    public int run(int desiredPort) {
        javalin.start(desiredPort);
        return javalin.port();
    }

    public void stop() {
        javalin.stop();
    }

    private void clearDatabase(Context ctx) {
        try {
            gameService.clear();
            authService.clear();
            ctx.status(HttpStatus.OK).result("{}");
        } catch (DataAccessException e) {
            sendError(ctx, HttpStatus.INTERNAL_SERVER_ERROR, "Error: " + e.getMessage());
        }
    }

    private void register(Context ctx) {
        try {
            UserData request = parseBody(ctx, UserData.class);
            AuthData auth = authService.register(request);
            Map<String, String> response = new LinkedHashMap<>();
            response.put("username", auth.username());
            response.put("authToken", auth.authToken());
            ctx.status(HttpStatus.OK).result(gson.toJson(response));
        } catch (DataAccessException e) {
            handleDataAccessException(e, ctx);
        }
    }

    private void login(Context ctx) {
        try {
            UserData request = parseBody(ctx, UserData.class);
            AuthData auth = authService.login(request);
            Map<String, String> response = new LinkedHashMap<>();
            response.put("username", auth.username());
            response.put("authToken", auth.authToken());
            ctx.status(HttpStatus.OK).result(gson.toJson(response));
        } catch (DataAccessException e) {
            handleDataAccessException(e, ctx);
        }
    }

    private void logout(Context ctx) {
        try {
            String authToken = ctx.header("authorization");
            authService.logout(authToken);
            ctx.status(HttpStatus.OK).result("{}");
        } catch (DataAccessException e) {
            handleDataAccessException(e, ctx);
        }
    }

    private void listGames(Context ctx) {
        try {
            String authToken = ctx.header("authorization");
            Collection<GameData> games = gameService.listGames(authToken);
            Map<String, Object> response = new LinkedHashMap<>();
            Collection<Map<String, Object>> payload = new ArrayList<>();
            for (GameData game : games) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("gameID", game.gameID());
                entry.put("whiteUsername", game.whiteUsername());
                entry.put("blackUsername", game.blackUsername());
                entry.put("gameName", game.gameName());
                payload.add(entry);
            }
            response.put("games", payload);
            ctx.status(HttpStatus.OK).result(gson.toJson(response));
        } catch (DataAccessException e) {
            handleDataAccessException(e, ctx);
        }
    }

    private void createGame(Context ctx) {
        try {
            String authToken = ctx.header("authorization");
            Map<String, String> request = parseBody(ctx, Map.class);
            GameData game = gameService.createGame(request.get("gameName"), authToken);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("gameID", game.gameID());
            ctx.status(HttpStatus.OK).result(gson.toJson(response));
        } catch (DataAccessException e) {
            handleDataAccessException(e, ctx);
        }
    }

    private void joinGame(Context ctx) {
        try {
            String authToken = ctx.header("authorization");
            Map<String, Object> request = parseBody(ctx, Map.class);
            String playerColor = (String) request.get("playerColor");
            Object gameIDObject = request.get("gameID");
            if (playerColor == null || gameIDObject == null || !(gameIDObject instanceof Number)) {
                throw new DataAccessException("bad request");
            }
            ChessGame.TeamColor color = ChessGame.TeamColor.valueOf(playerColor);
            gameService.joinGame(authToken, ((Number) gameIDObject).intValue(), color);
            ctx.status(HttpStatus.OK).result("{}");
        } catch (DataAccessException e) {
            handleDataAccessException(e, ctx);
        } catch (IllegalArgumentException e) {
            sendError(ctx, HttpStatus.BAD_REQUEST, "Error: bad request");
        }
    }

    private <T> T parseBody(Context ctx, Class<T> bodyClass) throws DataAccessException {
        try {
            T body = gson.fromJson(ctx.body(), bodyClass);
            if (body == null) {
                throw new DataAccessException("bad request");
            }
            return body;
        } catch (JsonSyntaxException | IllegalStateException e) {
            throw new DataAccessException("bad request");
        }
    }

    private void handleDataAccessException(DataAccessException e, Context ctx) {
        String message = e.getMessage();
        if ("bad request".equals(message)) {
            sendError(ctx, HttpStatus.BAD_REQUEST, "Error: bad request");
        } else if ("unauthorized".equals(message)) {
            sendError(ctx, HttpStatus.UNAUTHORIZED, "Error: unauthorized");
        } else if ("already taken".equals(message)) {
            sendError(ctx, HttpStatus.FORBIDDEN, "Error: already taken");
        } else if ("failed to get connection".equals(message) || "failed to create database".equals(message)
                || "failed to clear database".equals(message) || "failed to create user".equals(message)
                || "failed to get user".equals(message) || "failed to create auth".equals(message)
                || "failed to get auth".equals(message) || "failed to delete auth".equals(message)
                || "failed to create game".equals(message) || "failed to get game".equals(message)
                || "failed to list games".equals(message) || "failed to update game".equals(message)) {
            sendError(ctx, HttpStatus.INTERNAL_SERVER_ERROR, "Error: " + message);
        } else {
            sendError(ctx, HttpStatus.INTERNAL_SERVER_ERROR, "Error: " + message);
        }
    }

    private void sendError(Context ctx, HttpStatus status, String message) {
        Map<String, String> error = new LinkedHashMap<>();
        error.put("message", message);
        ctx.status(status).result(gson.toJson(error));
    }
}
