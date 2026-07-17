package service;

import dataaccess.DataAccess;
import dataaccess.DataAccessException;
import model.AuthData;
import model.UserData;
import org.mindrot.jbcrypt.BCrypt;

import java.util.UUID;

public class AuthService {
    private final DataAccess dataAccess;

    public AuthService(DataAccess dataAccess) {
        this.dataAccess = dataAccess;
    }

    public AuthData register(UserData user) throws DataAccessException {
        if (hasMissingRegistrationFields(user)) {
            throw new DataAccessException("bad request");
        }
        dataAccess.createUser(user);
        return createAuth(user.username());
    }

    public AuthData login(UserData user) throws DataAccessException {
        if (hasMissingLoginFields(user)) {
            throw new DataAccessException("bad request");
        }
        UserData storedUser;
        try {
            storedUser = dataAccess.getUser(user.username());
        } catch (DataAccessException e) {
            if ("user not found".equals(e.getMessage())) {
                throw new DataAccessException("unauthorized");
            }
            throw e;
        }
        String storedPassword = storedUser.password();
        boolean passwordMatches = storedPassword != null && storedPassword.equals(user.password());
        if (!passwordMatches) {
            try {
                passwordMatches = BCrypt.checkpw(user.password(), storedPassword);
            } catch (IllegalArgumentException e) {
                passwordMatches = false;
            }
        }
        if (!passwordMatches) {
            throw new DataAccessException("unauthorized");
        }
        return createAuth(user.username());
    }

    public void logout(String authToken) throws DataAccessException {
        dataAccess.deleteAuth(authToken);
    }

    public void clear() throws DataAccessException {
        dataAccess.clear();
    }

    private boolean hasMissingRegistrationFields(UserData user) {
        return user == null || user.username() == null || user.password() == null || user.email() == null
                || user.username().isBlank() || user.password().isBlank() || user.email().isBlank();
    }

    private boolean hasMissingLoginFields(UserData user) {
        return user == null || user.username() == null || user.password() == null
                || user.username().isBlank() || user.password().isBlank();
    }

    private AuthData createAuth(String username) throws DataAccessException {
        String token = UUID.randomUUID().toString();
        AuthData auth = new AuthData(token, username);
        dataAccess.createAuth(auth);
        return auth;
    }
}
