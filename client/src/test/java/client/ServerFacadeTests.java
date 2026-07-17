package client;

import chess.ChessGame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.*;
import server.Server;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;


public class ServerFacadeTests {

    private static Server server;
    private static ServerFacade facade;
    private static int port;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeAll
    public static void init() {
        server = new Server();
        port = server.run(0);
        System.out.println("Started test HTTP server on " + port);
        facade = new ServerFacade(port);
    }

    @BeforeEach
    public void clearDatabase() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/db"))
                .DELETE()
                .build();
        client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @AfterAll
    static void stopServer() {
        server.stop();
    }


    @Test
    public void registerSuccess() throws Exception {
        var auth = facade.register("alice", "password", "alice@example.com");

        Assertions.assertEquals("alice", auth.username());
        Assertions.assertNotNull(auth.authToken());
        Assertions.assertFalse(auth.authToken().isBlank());
    }

    @Test
    public void registerFailsForDuplicateUser() throws Exception {
        facade.register("alice", "password", "alice@example.com");

        ServerFacadeException exception = Assertions.assertThrows(ServerFacadeException.class,
                () -> facade.register("alice", "password", "alice@example.com"));
        Assertions.assertEquals(403, exception.getStatusCode());
    }

    @Test
    public void loginSuccess() throws Exception {
        facade.register("alice", "password", "alice@example.com");

        var auth = facade.login("alice", "password");

        Assertions.assertEquals("alice", auth.username());
        Assertions.assertNotNull(auth.authToken());
    }

    @Test
    public void loginFailsForBadPassword() throws Exception {
        facade.register("alice", "password", "alice@example.com");

        ServerFacadeException exception = Assertions.assertThrows(ServerFacadeException.class,
                () -> facade.login("alice", "wrong-password"));
        Assertions.assertEquals(401, exception.getStatusCode());
    }

    @Test
    public void logoutSuccess() throws Exception {
        var auth = facade.register("alice", "password", "alice@example.com");

        facade.logout(auth.authToken());

        ServerFacadeException exception = Assertions.assertThrows(ServerFacadeException.class,
                () -> facade.logout(auth.authToken()));
        Assertions.assertEquals(401, exception.getStatusCode());
    }

    @Test
    public void logoutFailsForBadToken() {
        ServerFacadeException exception = Assertions.assertThrows(ServerFacadeException.class,
                () -> facade.logout("bad-token"));
        Assertions.assertEquals(401, exception.getStatusCode());
    }

    @Test
    public void createGameSuccess() throws Exception {
        var auth = facade.register("alice", "password", "alice@example.com");

        int gameID = facade.createGame(auth.authToken(), "Test Game");

        Assertions.assertTrue(gameID > 0);
    }

    @Test
    public void createGameFailsForBadToken() throws Exception {
        facade.register("alice", "password", "alice@example.com");

        ServerFacadeException exception = Assertions.assertThrows(ServerFacadeException.class,
                () -> facade.createGame("bad-token", "Test Game"));
        Assertions.assertEquals(401, exception.getStatusCode());
    }

    @Test
    public void listGamesSuccess() throws Exception {
        var auth = facade.register("alice", "password", "alice@example.com");
        int gameID = facade.createGame(auth.authToken(), "Test Game");

        List<GameSummary> games = facade.listGames(auth.authToken());

        Assertions.assertEquals(1, games.size());
        Assertions.assertEquals(gameID, games.get(0).gameID());
        Assertions.assertEquals("Test Game", games.get(0).gameName());
    }

    @Test
    public void listGamesFailsForBadToken() {
        ServerFacadeException exception = Assertions.assertThrows(ServerFacadeException.class,
                () -> facade.listGames("bad-token"));
        Assertions.assertEquals(401, exception.getStatusCode());
    }

    @Test
    public void joinGameSuccess() throws Exception {
        var auth = facade.register("alice", "password", "alice@example.com");
        int gameID = facade.createGame(auth.authToken(), "Test Game");

        facade.joinGame(auth.authToken(), gameID, ChessGame.TeamColor.WHITE);

        List<GameSummary> games = facade.listGames(auth.authToken());
        Assertions.assertEquals("alice", games.get(0).whiteUsername());
    }

    @Test
    public void joinGameFailsForBadToken() throws Exception {
        var auth = facade.register("alice", "password", "alice@example.com");
        int gameID = facade.createGame(auth.authToken(), "Test Game");

        ServerFacadeException exception = Assertions.assertThrows(ServerFacadeException.class,
                () -> facade.joinGame("bad-token", gameID, ChessGame.TeamColor.WHITE));
        Assertions.assertEquals(401, exception.getStatusCode());
    }

}