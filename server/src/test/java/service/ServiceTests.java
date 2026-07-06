package service;

import chess.ChessGame;
import dataaccess.DataAccessException;
import dataaccess.MemoryDataAccess;
import model.AuthData;
import model.GameData;
import model.UserData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

public class ServiceTests {
    private MemoryDataAccess dataAccess;
    private AuthService authService;
    private GameService gameService;

    @BeforeEach
    void setUp() {
        dataAccess = new MemoryDataAccess();
        authService = new AuthService(dataAccess);
        gameService = new GameService(dataAccess);
    }

    @Test
    void registerAndLoginFlowWorks() throws DataAccessException {
        UserData user = new UserData("player", "secret", "player@example.com");

        AuthData auth = authService.register(user);

        assertEquals("player", auth.username());
        assertNotNull(auth.authToken());

        AuthData loggedIn = authService.login(new UserData("player", "secret", null));
        assertEquals("player", loggedIn.username());
        assertNotEquals(auth.authToken(), loggedIn.authToken());

        authService.logout(auth.authToken());

        assertThrows(DataAccessException.class, () -> authService.logout(auth.authToken()));
        assertThrows(DataAccessException.class, () -> authService.login(new UserData("player", "wrong", null)));
    }

    @Test
    void registerRejectsInvalidInput() {
        assertThrows(DataAccessException.class, () -> authService.register(null));
        assertThrows(DataAccessException.class, () -> authService.register(new UserData("", "pw", "e")));
        assertThrows(DataAccessException.class, () -> authService.register(new UserData("user", "", "e")));
        assertThrows(DataAccessException.class, () -> authService.register(new UserData("user", "pw", "")));
    }

    @Test
    void createAndListGamesRequireValidAuth() throws DataAccessException {
        AuthData auth = authService.register(new UserData("owner", "pw", "owner@example.com"));

        GameData created = gameService.createGame("My Game", auth.authToken());
        assertEquals("My Game", created.gameName());
        assertEquals(1, created.gameID());

        Collection<GameData> games = gameService.listGames(auth.authToken());
        assertEquals(1, games.size());
        assertTrue(games.stream().anyMatch(game -> game.gameName().equals("My Game")));

        assertThrows(DataAccessException.class, () -> gameService.createGame("Bad", "bad-token"));
        assertThrows(DataAccessException.class, () -> gameService.listGames("bad-token"));
    }

    @Test
    void joinGameAssignsColorAndRejectsTakenSeats() throws DataAccessException {
        AuthData auth = authService.register(new UserData("owner", "pw", "owner@example.com"));
        GameData created = gameService.createGame("Join Game", auth.authToken());

        gameService.joinGame(auth.authToken(), created.gameID(), ChessGame.TeamColor.WHITE);
        assertEquals("owner", dataAccess.getGame(created.gameID()).whiteUsername());

        assertThrows(DataAccessException.class,
                () -> gameService.joinGame(auth.authToken(), created.gameID(), ChessGame.TeamColor.WHITE));

        assertThrows(DataAccessException.class,
                () -> gameService.joinGame(auth.authToken(), 0, ChessGame.TeamColor.WHITE));
        assertThrows(DataAccessException.class,
                () -> gameService.joinGame(auth.authToken(), created.gameID(), null));
    }

    @Test
    void clearRemovesUsersAuthsAndGames() throws DataAccessException {
        AuthData auth = authService.register(new UserData("player", "pw", "player@example.com"));
        gameService.createGame("Temp", auth.authToken());

        authService.clear();
        gameService.clear();

        assertThrows(DataAccessException.class, () -> authService.login(new UserData("player", "pw", null)));
        assertThrows(DataAccessException.class, () -> gameService.listGames(auth.authToken()));
    }
}
