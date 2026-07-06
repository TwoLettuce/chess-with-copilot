package service;

import chess.ChessGame;
import dataaccess.DataAccess;
import dataaccess.DataAccessException;
import model.GameData;

import java.util.Collection;

public class UserService extends AuthService {
    private final GameService gameService;

    public UserService(DataAccess dataAccess) {
        super(dataAccess);
        this.gameService = new GameService(dataAccess);
    }

    public GameData createGame(String gameName, String authToken) throws DataAccessException {
        return gameService.createGame(gameName, authToken);
    }

    public void joinGame(String authToken, int gameID, ChessGame.TeamColor playerColor) throws DataAccessException {
        gameService.joinGame(authToken, gameID, playerColor);
    }

    public Collection<GameData> listGames(String authToken) throws DataAccessException {
        return gameService.listGames(authToken);
    }

    public void clear() throws DataAccessException {
        super.clear();
        gameService.clear();
    }
}
