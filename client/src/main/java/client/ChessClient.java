package client;

import chess.ChessGame;
import chess.ChessPiece;
import ui.BoardPrinter;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class ChessClient {
    private final ServerFacade serverFacade;
    private final Scanner scanner = new Scanner(System.in);
    private final BoardPrinter boardPrinter = new BoardPrinter();
    private List<GameSummary> lastGames = new ArrayList<>();
    private String authToken;

    public ChessClient(ServerFacade serverFacade) {
        this.serverFacade = serverFacade;
    }

    public void run() {
        boolean running = true;
        while (running) {
            try {
                printPrompt();
                String command = scanner.nextLine().trim().toLowerCase();
                if (command.isEmpty()) {
                    continue;
                }
                running = handleCommand(command);
            } catch (NoSuchElementException e) {
                return;
            } catch (Exception e) {
                System.out.println(e.getMessage() == null ? "An error occurred." : e.getMessage());
            }
        }
    }

    private boolean handleCommand(String command) throws ServerFacadeException {
        return authToken == null ? handlePreloginCommand(command) : handlePostloginCommand(command);
    }

    private boolean handlePreloginCommand(String command) throws ServerFacadeException {
        switch (command) {
            case "help" -> printPreloginHelp();
            case "login" -> login();
            case "register" -> register();
            case "quit" -> { return false; }
            default -> System.out.println("Unknown command. Type help to see available commands.");
        }
        return true;
    }

    private boolean handlePostloginCommand(String command) throws ServerFacadeException {
        switch (command) {
            case "help" -> printPostloginHelp();
            case "logout" -> logout();
            case "create game" -> createGame();
            case "list games" -> listGames();
            case "play game" -> playGame();
            case "observe game" -> observeGame();
            case "quit" -> { return false; }
            default -> System.out.println("Unknown command. Type help to see available commands.");
        }
        return true;
    }

    private void login() throws ServerFacadeException {
        String username = prompt("Username: ");
        String password = prompt("Password: ");
        var auth = serverFacade.login(username, password);
        authToken = auth.authToken();
        System.out.println("Logged in as " + auth.username() + ".");
    }

    private void register() throws ServerFacadeException {
        String username = prompt("Username: ");
        String password = prompt("Password: ");
        String email = prompt("Email: ");
        var auth = serverFacade.register(username, password, email);
        authToken = auth.authToken();
        System.out.println("Registered and logged in as " + auth.username() + ".");
    }

    private void logout() throws ServerFacadeException {
        serverFacade.logout(authToken);
        authToken = null;
        lastGames = new ArrayList<>();
        System.out.println("Logged out.");
    }

    private void createGame() throws ServerFacadeException {
        String gameName = prompt("Game name: ");
        serverFacade.createGame(authToken, gameName);
        System.out.println("Created game '" + gameName + "'.");
    }

    private void listGames() throws ServerFacadeException {
        lastGames = new ArrayList<>(serverFacade.listGames(authToken));
        if (lastGames.isEmpty()) {
            System.out.println("No games are currently available.");
            return;
        }

        for (int index = 0; index < lastGames.size(); index++) {
            GameSummary game = lastGames.get(index);
            System.out.printf("%d. %s | White: %s | Black: %s%n", index + 1, game.gameName(),
                    displayName(game.whiteUsername()), displayName(game.blackUsername()));
        }
    }

    private void playGame() throws ServerFacadeException {
        if (lastGames.isEmpty()) {
            System.out.println("List games first so you can choose a number.");
            return;
        }
        GameSummary game = chooseGame();
        if (game == null) {
            return;
        }
        ChessGame.TeamColor color = chooseColor();
        if (color == null) {
            return;
        }
        serverFacade.joinGame(authToken, game.gameID(), color);
        drawBoard(color);
        System.out.println("Joined '" + game.gameName() + "' as " + color.name().toLowerCase() + ".");
    }

    private void observeGame() {
        if (lastGames.isEmpty()) {
            System.out.println("List games first so you can choose a number.");
            return;
        }
        GameSummary game = chooseGame();
        if (game == null) {
            return;
        }
        drawBoard(ChessGame.TeamColor.WHITE);
        System.out.println("Observing '" + game.gameName() + "'.");
    }

    private GameSummary chooseGame() {
        String input = prompt("Game number: ");
        try {
            int choice = Integer.parseInt(input);
            if (choice < 1 || choice > lastGames.size()) {
                System.out.println("That number is not on the list.");
                return null;
            }
            return lastGames.get(choice - 1);
        } catch (NumberFormatException e) {
            System.out.println("Please enter a valid number.");
            return null;
        }
    }

    private ChessGame.TeamColor chooseColor() {
        String input = prompt("Color (white/black): ").trim().toLowerCase();
        return switch (input) {
            case "white" -> ChessGame.TeamColor.WHITE;
            case "black" -> ChessGame.TeamColor.BLACK;
            default -> {
                System.out.println("Please choose white or black.");
                yield null;
            }
        };
    }

    private void drawBoard(ChessGame.TeamColor perspective) {
        System.out.println(boardPrinter.render(new ChessGame(), perspective));
    }

    private String prompt(String message) {
        System.out.print(message);
        return scanner.nextLine().trim();
    }

    private void printPrompt() {
        if (authToken == null) {
            System.out.print("[prelogin] ");
        } else {
            System.out.print("[postlogin] ");
        }
    }

    private void printPreloginHelp() {
        System.out.println("Commands: help, login, register, quit");
    }

    private void printPostloginHelp() {
        System.out.println("Commands: help, logout, create game, list games, play game, observe game, quit");
    }

    private String displayName(String value) {
        return value == null || value.isBlank() ? "<empty>" : value;
    }
}