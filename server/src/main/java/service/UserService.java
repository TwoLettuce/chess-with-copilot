package service;

import chess.ChessGame;
import dataaccess.DataAccess;
import dataaccess.DataAccessException;
import model.AuthData;
import model.GameData;
import model.UserData;

import java.util.UUID;

public class UserService {
    private final DataAccess dataAccess;

    public UserService(DataAccess dataAccess) {
        this.dataAccess = dataAccess;
    }

    public AuthData register(UserData user) throws DataAccessException {
        if (user == null || user.username() == null || user.password() == null || user.email() == null
                || user.username().isBlank() || user.password().isBlank() || user.email().isBlank()) {
            throw new DataAccessException("bad request");
        }
        dataAccess.createUser(user);
        String token = UUID.randomUUID().toString();
        AuthData auth = new AuthData(token, user.username());
        dataAccess.createAuth(auth);
        return auth;
    }

    public AuthData login(UserData user) throws DataAccessException {
        if (user == null || user.username() == null || user.password() == null
                || user.username().isBlank() || user.password().isBlank()) {
            throw new DataAccessException("bad request");
        }
        UserData storedUser = dataAccess.getUser(user.username());
        if (!user.password().equals(storedUser.password())) {
            throw new DataAccessException("unauthorized");
        }
        String token = UUID.randomUUID().toString();
        AuthData auth = new AuthData(token, user.username());
        dataAccess.createAuth(auth);
        return auth;
    }

    public void logout(String authToken) throws DataAccessException {
        dataAccess.deleteAuth(authToken);
    }

    public GameData createGame(String gameName, String authToken) throws DataAccessException {
        if (gameName == null || gameName.isBlank()) {
            throw new DataAccessException("bad request");
        }
        dataAccess.getAuth(authToken);
        return dataAccess.createGame(new GameData(0, null, null, gameName, new ChessGame()));
    }

    public void joinGame(String authToken, int gameID, ChessGame.TeamColor playerColor) throws DataAccessException {
        if (playerColor == null || gameID <= 0) {
            throw new DataAccessException("bad request");
        }
        dataAccess.getAuth(authToken);
        GameData game = dataAccess.getGame(gameID);
        if (playerColor == ChessGame.TeamColor.WHITE) {
            if (game.whiteUsername() != null && !game.whiteUsername().isBlank()) {
                throw new DataAccessException("already taken");
            }
            dataAccess.updateGame(new GameData(game.gameID(), authUsername(authToken), game.blackUsername(), game.gameName(), game.game()));
        } else {
            if (game.blackUsername() != null && !game.blackUsername().isBlank()) {
                throw new DataAccessException("already taken");
            }
            dataAccess.updateGame(new GameData(game.gameID(), game.whiteUsername(), authUsername(authToken), game.gameName(), game.game()));
        }
    }

    public java.util.Collection<GameData> listGames(String authToken) throws DataAccessException {
        dataAccess.getAuth(authToken);
        return dataAccess.listGames();
    }

    public void clear() throws DataAccessException {
        dataAccess.clear();
    }

    private String authUsername(String authToken) throws DataAccessException {
        return dataAccess.getAuth(authToken).username();
    }
}
