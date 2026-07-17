package dataaccess;

import chess.ChessGame;
import com.google.gson.Gson;
import model.AuthData;
import model.GameData;
import model.UserData;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MySqlDataAccess implements DataAccess {
    private static final Gson GSON = new Gson();

    public MySqlDataAccess() {
        try {
            initialize();
        } catch (DataAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void clear() throws DataAccessException {
        initialize();
        try (Connection conn = DatabaseManager.getConnection()) {
            try (Statement statement = conn.createStatement()) {
                statement.executeUpdate("DELETE FROM auths");
                statement.executeUpdate("DELETE FROM games");
                statement.executeUpdate("DELETE FROM users");
            }
        } catch (SQLException e) {
            throw new DataAccessException("failed to clear database", e);
        }
    }

    @Override
    public void createUser(UserData user) throws DataAccessException {
        initialize();
        String hashedPassword = BCrypt.hashpw(user.password(), BCrypt.gensalt());
        String sql = "INSERT INTO users (username, password, email) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, user.username());
            statement.setString(2, hashedPassword);
            statement.setString(3, user.email());
            statement.executeUpdate();
        } catch (SQLException e) {
            if (isDuplicateKey(e)) {
                throw new DataAccessException("already taken");
            }
            throw new DataAccessException("failed to create user", e);
        }
    }

    @Override
    public UserData getUser(String username) throws DataAccessException {
        initialize();
        String sql = "SELECT username, password, email FROM users WHERE username = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new DataAccessException("user not found");
                }
                return new UserData(resultSet.getString("username"), resultSet.getString("password"),
                        resultSet.getString("email"));
            }
        } catch (SQLException e) {
            throw new DataAccessException("failed to get user", e);
        }
    }

    @Override
    public void createAuth(AuthData auth) throws DataAccessException {
        initialize();
        String sql = "INSERT INTO auths (auth_token, username) VALUES (?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement statement = conn.prepareStatement(sql)) {
            if (!userExists(conn, auth.username())) {
                throw new DataAccessException("unauthorized");
            }
            statement.setString(1, auth.authToken());
            statement.setString(2, auth.username());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("failed to create auth", e);
        }
    }

    @Override
    public AuthData getAuth(String authToken) throws DataAccessException {
        initialize();
        String sql = "SELECT auth_token, username FROM auths WHERE auth_token = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, authToken);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new DataAccessException("unauthorized");
                }
                return new AuthData(resultSet.getString("auth_token"), resultSet.getString("username"));
            }
        } catch (SQLException e) {
            throw new DataAccessException("failed to get auth", e);
        }
    }

    @Override
    public void deleteAuth(String authToken) throws DataAccessException {
        initialize();
        String sql = "DELETE FROM auths WHERE auth_token = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, authToken);
            int updated = statement.executeUpdate();
            if (updated == 0) {
                throw new DataAccessException("unauthorized");
            }
        } catch (SQLException e) {
            throw new DataAccessException("failed to delete auth", e);
        }
    }

    @Override
    public GameData createGame(GameData game) throws DataAccessException {
        initialize();
        String sql = "INSERT INTO games (white_username, black_username, game_name, game_state) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement statement = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, game.whiteUsername());
            statement.setString(2, game.blackUsername());
            statement.setString(3, game.gameName());
            statement.setString(4, GSON.toJson(game.game()));
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return new GameData(generatedKeys.getInt(1), game.whiteUsername(), game.blackUsername(),
                            game.gameName(), game.game());
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("failed to create game", e);
        }
        throw new DataAccessException("failed to create game");
    }

    @Override
    public GameData getGame(int gameID) throws DataAccessException {
        initialize();
        String sql = "SELECT game_id, white_username, black_username, game_name, game_state FROM games WHERE game_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setInt(1, gameID);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new DataAccessException("game not found");
                }
                ChessGame game = GSON.fromJson(resultSet.getString("game_state"), ChessGame.class);
                return new GameData(resultSet.getInt("game_id"), resultSet.getString("white_username"),
                        resultSet.getString("black_username"), resultSet.getString("game_name"), game);
            }
        } catch (SQLException e) {
            throw new DataAccessException("failed to get game", e);
        }
    }

    @Override
    public Collection<GameData> listGames() throws DataAccessException {
        initialize();
        String sql = "SELECT game_id, white_username, black_username, game_name, game_state FROM games";
        List<GameData> games = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement statement = conn.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                ChessGame game = GSON.fromJson(resultSet.getString("game_state"), ChessGame.class);
                games.add(new GameData(resultSet.getInt("game_id"), resultSet.getString("white_username"),
                        resultSet.getString("black_username"), resultSet.getString("game_name"), game));
            }
        } catch (SQLException e) {
            throw new DataAccessException("failed to list games", e);
        }
        return games;
    }

    @Override
    public void updateGame(GameData game) throws DataAccessException {
        initialize();
        String sql = "UPDATE games SET white_username = ?, black_username = ?, game_name = ?, game_state = ? WHERE game_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, game.whiteUsername());
            statement.setString(2, game.blackUsername());
            statement.setString(3, game.gameName());
            statement.setString(4, GSON.toJson(game.game()));
            statement.setInt(5, game.gameID());
            int updated = statement.executeUpdate();
            if (updated == 0) {
                throw new DataAccessException("game not found");
            }
        } catch (SQLException e) {
            throw new DataAccessException("failed to update game", e);
        }
    }

    private void initialize() throws DataAccessException {
        try {
            createDatabaseIfNeeded();
            createTablesIfNeeded();
        } catch (SQLException e) {
            throw new DataAccessException("failed to initialize database", e);
        }
    }

    private void createDatabaseIfNeeded() throws DataAccessException {
        DatabaseManager.createDatabase();
    }

    private void createTablesIfNeeded() throws DataAccessException, SQLException {
        try (Connection conn = DatabaseManager.getConnection();
             Statement statement = conn.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS users (
                        username VARCHAR(255) PRIMARY KEY,
                        password VARCHAR(255) NOT NULL,
                        email VARCHAR(255) NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS auths (
                        auth_token VARCHAR(255) PRIMARY KEY,
                        username VARCHAR(255) NOT NULL,
                        FOREIGN KEY (username) REFERENCES users(username)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS games (
                        game_id INT AUTO_INCREMENT PRIMARY KEY,
                        white_username VARCHAR(255),
                        black_username VARCHAR(255),
                        game_name VARCHAR(255) NOT NULL,
                        game_state TEXT NOT NULL
                    )
                    """);
        }
    }

    private boolean isDuplicateKey(SQLException e) {
        return e.getErrorCode() == 1062 || e.getMessage() != null && e.getMessage().contains("duplicate");
    }

    private boolean userExists(Connection conn, String username) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE username = ?";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }
}
