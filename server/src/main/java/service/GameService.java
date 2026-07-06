package service;

import chess.ChessGame;
import dataaccess.DataAccess;
import dataaccess.DataAccessException;
import model.GameData;

import java.util.Collection;

public class GameService {
    private final DataAccess dataAccess;

    public GameService(DataAccess dataAccess) {
        this.dataAccess = dataAccess;
    }

    public GameData createGame(String gameName, String authToken) throws DataAccessException {
        if (gameName == null || gameName.isBlank()) {
            throw new DataAccessException("bad request");
        }
        requireAuth(authToken);
        return dataAccess.createGame(new GameData(0, null, null, gameName, new ChessGame()));
    }

    public Collection<GameData> listGames(String authToken) throws DataAccessException {
        requireAuth(authToken);
        return dataAccess.listGames();
    }

    public void joinGame(String authToken, int gameID, ChessGame.TeamColor playerColor) throws DataAccessException {
        if (gameID <= 0 || playerColor == null) {
            throw new DataAccessException("bad request");
        }
        requireAuth(authToken);
        GameData existing = dataAccess.getGame(gameID);
        if (playerColor == ChessGame.TeamColor.WHITE) {
            joinAsWhite(existing, authToken);
        } else {
            joinAsBlack(existing, authToken);
        }
    }

    public void clear() throws DataAccessException {
        dataAccess.clear();
    }

    private void requireAuth(String authToken) throws DataAccessException {
        dataAccess.getAuth(authToken);
    }

    private void joinAsWhite(GameData existing, String authToken) throws DataAccessException {
        if (existing.whiteUsername() != null && !existing.whiteUsername().isBlank()) {
            throw new DataAccessException("already taken");
        }
        dataAccess.updateGame(new GameData(existing.gameID(), authUsername(authToken), existing.blackUsername(),
                existing.gameName(), existing.game()));
    }

    private void joinAsBlack(GameData existing, String authToken) throws DataAccessException {
        if (existing.blackUsername() != null && !existing.blackUsername().isBlank()) {
            throw new DataAccessException("already taken");
        }
        dataAccess.updateGame(new GameData(existing.gameID(), existing.whiteUsername(), authUsername(authToken),
                existing.gameName(), existing.game()));
    }

    private String authUsername(String authToken) throws DataAccessException {
        return dataAccess.getAuth(authToken).username();
    }
}
