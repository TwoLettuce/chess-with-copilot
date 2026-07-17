package dataaccess;

import chess.ChessGame;
import model.AuthData;
import model.GameData;
import model.UserData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

public class MySqlDataAccessTests {
    private MySqlDataAccess dataAccess;

    @BeforeEach
    void setUp() throws DataAccessException {
        dataAccess = new MySqlDataAccess();
        dataAccess.clear();
    }

    @Test
    void createUserAndGetUserSucceed() throws DataAccessException {
        UserData user = new UserData("alice", "password", "alice@example.com");

        dataAccess.createUser(user);
        UserData stored = dataAccess.getUser("alice");

        assertEquals("alice", stored.username());
        assertNotEquals("password", stored.password());
        assertEquals("alice@example.com", stored.email());
    }

    @Test
    void createUserRejectsDuplicateUsername() throws DataAccessException {
        UserData user = new UserData("alice", "password", "alice@example.com");
        dataAccess.createUser(user);

        DataAccessException exception = assertThrows(DataAccessException.class,
                () -> dataAccess.createUser(user));
        assertTrue(exception.getMessage().contains("already taken"));
    }

    @Test
    void createAuthAndGetAuthSucceed() throws DataAccessException {
        dataAccess.createUser(new UserData("alice", "password", "alice@example.com"));
        AuthData auth = new AuthData("token-1", "alice");

        dataAccess.createAuth(auth);
        AuthData stored = dataAccess.getAuth("token-1");

        assertEquals("token-1", stored.authToken());
        assertEquals("alice", stored.username());
    }

    @Test
    void getAuthThrowsForUnknownToken() {
        DataAccessException exception = assertThrows(DataAccessException.class,
                () -> dataAccess.getAuth("missing"));
        assertTrue(exception.getMessage().contains("unauthorized"));
    }

    @Test
    void deleteAuthRemovesToken() throws DataAccessException {
        dataAccess.createUser(new UserData("alice", "password", "alice@example.com"));
        dataAccess.createAuth(new AuthData("token-1", "alice"));

        dataAccess.deleteAuth("token-1");

        assertThrows(DataAccessException.class, () -> dataAccess.getAuth("token-1"));
    }

    @Test
    void deleteAuthThrowsForMissingToken() {
        DataAccessException exception = assertThrows(DataAccessException.class,
                () -> dataAccess.deleteAuth("missing"));
        assertTrue(exception.getMessage().contains("unauthorized"));
    }

    @Test
    void createAuthRejectsUnknownUser() throws DataAccessException {
        DataAccessException exception = assertThrows(DataAccessException.class,
                () -> dataAccess.createAuth(new AuthData("token-1", "missing-user")));
        assertTrue(exception.getMessage().contains("unauthorized"));
    }

    @Test
    void createGameAndGetGameSucceed() throws DataAccessException {
        GameData game = new GameData(0, null, null, "Test Game", new ChessGame());

        GameData created = dataAccess.createGame(game);
        GameData retrieved = dataAccess.getGame(created.gameID());

        assertEquals(created.gameID(), retrieved.gameID());
        assertEquals("Test Game", retrieved.gameName());
        assertNotNull(retrieved.game());
    }

    @Test
    void getGameThrowsForUnknownId() {
        DataAccessException exception = assertThrows(DataAccessException.class,
                () -> dataAccess.getGame(999));
        assertTrue(exception.getMessage().contains("game not found"));
    }

    @Test
    void listGamesReturnsPersistedGames() throws DataAccessException {
        dataAccess.createGame(new GameData(0, null, null, "One", new ChessGame()));
        dataAccess.createGame(new GameData(0, null, null, "Two", new ChessGame()));

        Collection<GameData> games = dataAccess.listGames();

        assertEquals(2, games.size());
        assertTrue(games.stream().anyMatch(g -> g.gameName().equals("One")));
        assertTrue(games.stream().anyMatch(g -> g.gameName().equals("Two")));
    }

    @Test
    void listGamesReturnsEmptyAfterClear() throws DataAccessException {
        dataAccess.createGame(new GameData(0, null, null, "One", new ChessGame()));
        dataAccess.clear();

        assertTrue(dataAccess.listGames().isEmpty());
    }

    @Test
    void updateGamePersistsChanges() throws DataAccessException {
        GameData created = dataAccess.createGame(new GameData(0, null, null, "Test", new ChessGame()));
        ChessGame updatedGame = new ChessGame();
        updatedGame.setTeamTurn(ChessGame.TeamColor.BLACK);
        GameData updated = new GameData(created.gameID(), "white", "black", "Test", updatedGame);

        dataAccess.updateGame(updated);
        GameData stored = dataAccess.getGame(created.gameID());

        assertEquals("white", stored.whiteUsername());
        assertEquals("black", stored.blackUsername());
        assertEquals(ChessGame.TeamColor.BLACK, stored.game().getTeamTurn());
    }

    @Test
    void updateGameThrowsForMissingGame() {
        ChessGame game = new ChessGame();
        GameData missing = new GameData(999, null, null, "Nope", game);

        DataAccessException exception = assertThrows(DataAccessException.class,
                () -> dataAccess.updateGame(missing));
        assertTrue(exception.getMessage().contains("game not found"));
    }
}
