package websocket.commands;

import chess.ChessMove;

import java.util.Objects;

/**
 * Represents a command a user can send the server over a websocket
 * <p>
 * Note: You can add to this class, but you should not alter the existing
 * methods.
 */
public class UserGameCommand {

    private final CommandType commandType;

    private final String authToken;

    private final Integer gameID;

    private final ChessMove move;

    public UserGameCommand(CommandType commandType, String authToken, Integer gameID) {
        this(commandType, authToken, gameID, null);
    }

    public UserGameCommand(CommandType commandType, String authToken, Integer gameID, ChessMove move) {
        this.commandType = commandType;
        this.authToken = authToken;
        this.gameID = gameID;
        this.move = move;
    }

    public enum CommandType {
        CONNECT,
        MAKE_MOVE,
        LEAVE,
        RESIGN
    }

    public CommandType getCommandType() {
        return commandType;
    }

    public String getAuthToken() {
        return authToken;
    }

    public Integer getGameID() {
        return gameID;
    }

    public ChessMove getMove() {
        return move;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserGameCommand that)) {
            return false;
        }
        return getCommandType() == that.getCommandType() &&
                Objects.equals(getAuthToken(), that.getAuthToken()) &&
                Objects.equals(getGameID(), that.getGameID()) &&
                Objects.equals(getMove(), that.getMove());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getCommandType(), getAuthToken(), getGameID(), getMove());
    }
}