package client;

public class ServerFacadeException extends Exception {
    private final int statusCode;

    public ServerFacadeException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}