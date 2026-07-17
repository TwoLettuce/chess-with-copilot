package client;

public class ClientMain {
    public static void main(String[] args) {
        int port = 8080;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
                port = 8080;
            }
        }

        System.out.println("♕ 240 Chess Client");
        new ChessClient(new ServerFacade(port)).run();
    }
}
