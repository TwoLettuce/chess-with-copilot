package dataaccess;

import chess.ChessGame;
import model.AuthData;
import model.GameData;
import model.UserData;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class MemoryDataAccess implements DataAccess {
    private final Map<String, UserData> users = new LinkedHashMap<>();
    private final Map<String, AuthData> auths = new LinkedHashMap<>();
    private final Map<Integer, GameData> games = new LinkedHashMap<>();
    private int nextGameId = 1;

    @Override
    public void clear() {
        users.clear();
        auths.clear();
        games.clear();
        nextGameId = 1;
    }

    @Override
    public void createUser(UserData user) throws DataAccessException {
        if (users.containsKey(user.username())) {
            throw new DataAccessException("already taken");
        }
        users.put(user.username(), user);
    }

    @Override
    public UserData getUser(String username) throws DataAccessException {
        if (username == null || !users.containsKey(username)) {
            throw new DataAccessException("user not found");
        }
        return users.get(username);
    }

    @Override
    public void createAuth(AuthData auth) throws DataAccessException {
        auths.put(auth.authToken(), auth);
    }

    @Override
    public AuthData getAuth(String authToken) throws DataAccessException {
        if (authToken == null || !auths.containsKey(authToken)) {
            throw new DataAccessException("unauthorized");
        }
        return auths.get(authToken);
    }

    @Override
    public void deleteAuth(String authToken) throws DataAccessException {
        if (authToken == null || !auths.containsKey(authToken)) {
            throw new DataAccessException("unauthorized");
        }
        auths.remove(authToken);
    }

    @Override
    public GameData createGame(GameData game) throws DataAccessException {
        int id = nextGameId++;
        GameData stored = new GameData(id, game.whiteUsername(), game.blackUsername(), game.gameName(), game.game());
        games.put(id, stored);
        return stored;
    }

    @Override
    public GameData getGame(int gameID) throws DataAccessException {
        GameData game = games.get(gameID);
        if (game == null) {
            throw new DataAccessException("game not found");
        }
        return game;
    }

    @Override
    public Collection<GameData> listGames() {
        return games.values();
    }

    @Override
    public void updateGame(GameData game) throws DataAccessException {
        if (!games.containsKey(game.gameID())) {
            throw new DataAccessException("game not found");
        }
        games.put(game.gameID(), game);
    }
}
