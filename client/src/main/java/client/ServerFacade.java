package client;

import chess.ChessGame;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import model.AuthData;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServerFacade {
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private final HttpClient client = HttpClient.newHttpClient();
    private final String baseUrl;

    public ServerFacade(int port) {
        this.baseUrl = "http://localhost:" + port;
    }

    public AuthData register(String username, String password, String email) throws ServerFacadeException {
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("password", password);
        body.put("email", email);
        return sendJson("/user", "POST", null, body, AuthData.class);
    }

    public AuthData login(String username, String password) throws ServerFacadeException {
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("password", password);
        return sendJson("/session", "POST", null, body, AuthData.class);
    }

    public void logout(String authToken) throws ServerFacadeException {
        sendNoBody("/session", "DELETE", authToken, Void.class);
    }

    public int createGame(String authToken, String gameName) throws ServerFacadeException {
        Map<String, Object> body = new HashMap<>();
        body.put("gameName", gameName);
        GameIdResponse response = sendJson("/game", "POST", authToken, body,
                GameIdResponse.class);
        return response.gameID();
    }

    public List<GameSummary> listGames(String authToken) throws ServerFacadeException {
        GamesResponse response = sendNoBody("/game", "GET", authToken, GamesResponse.class);
        return response.games() == null ? List.of() : response.games();
    }

    public void joinGame(String authToken, int gameID, ChessGame.TeamColor playerColor) throws ServerFacadeException {
        Map<String, Object> body = new HashMap<>();
        body.put("playerColor", playerColor == null ? null : playerColor.name());
        body.put("gameID", gameID);
        sendJson("/game", "PUT", authToken, body, Void.class);
    }

    private <T> T sendJson(String path, String method, String authToken, Map<String, Object> body, Class<T> responseType)
            throws ServerFacadeException {
        HttpRequest request = buildJsonRequest(path, method, authToken, body);
        return send(request, responseType);
    }

    private <T> T sendNoBody(String path, String method, String authToken, Class<T> responseType)
            throws ServerFacadeException {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(baseUrl + path));
        if (authToken != null) {
            builder.header("authorization", authToken);
        }
        HttpRequest request = switch (method) {
            case "GET" -> builder.GET().build();
            case "DELETE" -> builder.DELETE().build();
            default -> throw new IllegalArgumentException("Unsupported method: " + method);
        };
        return send(request, responseType);
    }

    private HttpRequest buildJsonRequest(String path, String method, String authToken, Map<String, Object> body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(baseUrl + path));
        if (authToken != null) {
            builder.header("authorization", authToken);
        }
        builder.header("Content-Type", "application/json");
        String json = GSON.toJson(body);
        return switch (method) {
            case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build();
            case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build();
            default -> throw new IllegalArgumentException("Unsupported method: " + method);
        };
    }

    private <T> T send(HttpRequest request, Class<T> responseType) throws ServerFacadeException {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                String message = extractErrorMessage(response.body());
                if (message == null || message.isBlank()) {
                    message = "HTTP " + response.statusCode();
                }
                throw new ServerFacadeException(response.statusCode(), message);
            }
            if (responseType == Void.class) {
                return null;
            }
            return GSON.fromJson(response.body(), responseType);
        } catch (IOException | InterruptedException | JsonSyntaxException e) {
            String detail = e.getClass().getSimpleName();
            if (e.getMessage() != null && !e.getMessage().isBlank()) {
                detail += ": " + e.getMessage();
            }
            throw new ServerFacadeException(500, detail);
        }
    }

    private String extractErrorMessage(String body) {
        try {
            ErrorResponse response = GSON.fromJson(body, ErrorResponse.class);
            if (response != null && response.message() != null) {
                return response.message();
            }
        } catch (JsonSyntaxException ignored) {
        }
        return body == null || body.isBlank() ? "request failed" : body;
    }

    private record GameIdResponse(int gameID) {
    }

    private record GamesResponse(List<GameSummary> games) {
    }

    private record ErrorResponse(String message) {
    }
}