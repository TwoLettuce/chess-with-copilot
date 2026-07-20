package client;

import chess.ChessGame;
import chess.ChessMove;
import chess.ChessPiece;
import chess.ChessPosition;
import client.websocket.GameWebSocketClient;
import ui.BoardPrinter;
import websocket.commands.UserGameCommand;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Scanner;
import java.util.stream.Collectors;

public class ChessClient {
    private final ServerFacade serverFacade;
    private final Scanner scanner = new Scanner(System.in);
    private final BoardPrinter boardPrinter = new BoardPrinter();
    private final GameListener gameListener = new GameListener();
    private List<GameSummary> lastGames = new ArrayList<>();
    private String authToken;
    private GameWebSocketClient webSocketClient;
    private ChessGame currentGame;
    private ChessGame.TeamColor currentPerspective;
    private int currentGameId;
    private boolean currentIsObserver;
    private boolean currentGameOver;

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
        startGameSession(game, color, false);
    }

    private void observeGame() throws ServerFacadeException {
        if (lastGames.isEmpty()) {
            System.out.println("List games first so you can choose a number.");
            return;
        }
        GameSummary game = chooseGame();
        if (game == null) {
            return;
        }
        startGameSession(game, ChessGame.TeamColor.WHITE, true);
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

    private void startGameSession(GameSummary game, ChessGame.TeamColor perspective, boolean observer) throws ServerFacadeException {
        currentGame = new ChessGame();
        currentPerspective = perspective;
        currentGameId = game.gameID();
        currentIsObserver = observer;
        currentGameOver = false;
        try {
            webSocketClient = new GameWebSocketClient(gameListener);
            webSocketClient.connect(serverFacade.getWebSocketUri());
            webSocketClient.send(new UserGameCommand(UserGameCommand.CommandType.CONNECT, authToken, game.gameID()));
            System.out.println((observer ? "Observing '" : "Joined '") + game.gameName() + (observer ? "'." : "' as " + perspective.name().toLowerCase() + "."));
            runGameplayLoop();
        } catch (Exception e) {
            throw new ServerFacadeException(500, e.getMessage() == null ? "Failed to connect to websocket." : e.getMessage());
        } finally {
            closeGameSession();
        }
    }

    private String prompt(String message) {
        System.out.print(message);
        return scanner.nextLine().trim();
    }

    private void runGameplayLoop() throws ServerFacadeException {
        boolean playing = true;
        while (playing) {
            try {
                printGameplayPrompt();
                String command = scanner.nextLine().trim().toLowerCase();
                if (command.isEmpty()) {
                    continue;
                }
                playing = handleGameplayCommand(command);
            } catch (NoSuchElementException e) {
                return;
            } catch (ServerFacadeException e) {
                System.out.println(e.getMessage() == null ? "An error occurred." : e.getMessage());
            }
        }
    }

    private boolean handleGameplayCommand(String command) throws ServerFacadeException {
        switch (command) {
            case "help" -> printGameplayHelp();
            case "redraw" -> redrawBoard();
            case "leave" -> leaveGame();
            case "make move" -> makeMove();
            case "resign" -> resign();
            case "highlight" -> highlightLegalMoves();
            case "quit" -> { return false; }
            default -> System.out.println("Unknown command. Type help to see available commands.");
        }
        return true;
    }

    private void redrawBoard() {
        if (currentGame == null) {
            System.out.println("No board is currently loaded.");
            return;
        }
        System.out.println(boardPrinter.render(currentGame, currentPerspective));
    }

    private void leaveGame() throws ServerFacadeException {
        sendGameplayCommand(UserGameCommand.CommandType.LEAVE, null);
        closeGameSession();
    }

    private void makeMove() throws ServerFacadeException {
        if (currentIsObserver) {
            System.out.println("Observers cannot make moves.");
            return;
        }
        if (currentGameOver) {
            System.out.println("The game is over.");
            return;
        }
        ChessMove move = promptForMove();
        if (move == null) {
            return;
        }
        sendGameplayCommand(UserGameCommand.CommandType.MAKE_MOVE, move);
    }

    private void resign() throws ServerFacadeException {
        if (currentIsObserver) {
            System.out.println("Observers cannot resign.");
            return;
        }
        if (currentGameOver) {
            System.out.println("The game is already over.");
            return;
        }
        String confirm = prompt("Are you sure you want to resign? (y/n): ").toLowerCase();
        if (!confirm.startsWith("y")) {
            return;
        }
        sendGameplayCommand(UserGameCommand.CommandType.RESIGN, null);
    }

    private void highlightLegalMoves() {
        if (currentGame == null) {
            System.out.println("No board is currently loaded.");
            return;
        }
        ChessPosition start = promptForPosition("Piece row: ", "Piece column: ");
        if (start == null) {
            return;
        }
        Collection<ChessMove> moves = currentGame.validMoves(start);
        if (moves == null || moves.isEmpty()) {
            System.out.println("That piece has no legal moves.");
            return;
        }
        Set<ChessPosition> highlighted = moves.stream()
                .map(ChessMove::getEndPosition)
                .collect(Collectors.toSet());
        highlighted.add(start);
        System.out.println(boardPrinter.render(currentGame, currentPerspective, highlighted));
    }

    private ChessMove promptForMove() {
        ChessPosition start = promptForPosition("Start row: ", "Start column: ");
        if (start == null) {
            return null;
        }
        ChessPosition end = promptForPosition("End row: ", "End column: ");
        if (end == null) {
            return null;
        }
        ChessPiece.PieceType promotion = null;
        ChessPiece startPiece = currentGame.getBoard().getPiece(start);
        if (startPiece != null && startPiece.getPieceType() == ChessPiece.PieceType.PAWN
                && (end.getRow() == 1 || end.getRow() == 8)) {
            String input = prompt("Promotion piece (queen, rook, bishop, knight) or blank for queen: ").toLowerCase();
            promotion = switch (input) {
                case "rook" -> ChessPiece.PieceType.ROOK;
                case "bishop" -> ChessPiece.PieceType.BISHOP;
                case "knight" -> ChessPiece.PieceType.KNIGHT;
                case "", "queen" -> ChessPiece.PieceType.QUEEN;
                default -> {
                    System.out.println("Unknown promotion piece.");
                    yield null;
                }
            };
            if (promotion == null) {
                return null;
            }
        }
        return new ChessMove(start, end, promotion);
    }

    private ChessPosition promptForPosition(String rowPrompt, String columnPrompt) {
        Integer row = promptForInteger(rowPrompt);
        if (row == null) {
            return null;
        }
        Integer column = promptForInteger(columnPrompt);
        if (column == null) {
            return null;
        }
        if (row < 1 || row > 8 || column < 1 || column > 8) {
            System.out.println("Rows and columns must be between 1 and 8.");
            return null;
        }
        return new ChessPosition(row, column);
    }

    private Integer promptForInteger(String prompt) {
        String input = prompt(prompt);
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            System.out.println("Please enter a valid number.");
            return null;
        }
    }

    private void sendGameplayCommand(UserGameCommand.CommandType type, ChessMove move) throws ServerFacadeException {
        try {
            UserGameCommand command = move == null
                    ? new UserGameCommand(type, authToken, currentGameId)
                    : new UserGameCommand(type, authToken, currentGameId, move);
            webSocketClient.send(command);
        } catch (Exception e) {
            throw new ServerFacadeException(500, e.getMessage() == null ? "Failed to send websocket command." : e.getMessage());
        }
    }

    private void closeGameSession() {
        if (webSocketClient != null) {
            webSocketClient.close();
            webSocketClient = null;
        }
        currentGame = null;
        currentPerspective = null;
        currentIsObserver = false;
        currentGameOver = false;
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

    private void printGameplayHelp() {
        System.out.println("Commands: help, redraw, leave, make move, resign, highlight, quit");
    }

    private void printGameplayPrompt() {
        System.out.print("[gameplay] ");
    }

    private String displayName(String value) {
        return value == null || value.isBlank() ? "<empty>" : value;
    }

    private void setLoadedGame(ChessGame game) {
        currentGame = game;
        System.out.println(boardPrinter.render(game, currentPerspective));
    }

    private class GameListener implements GameWebSocketClient.Listener {
        @Override
        public void onLoadGame(ChessGame game) {
            setLoadedGame(game);
        }

        @Override
        public void onNotification(String message) {
            System.out.println(message);
            if (message != null && (message.toLowerCase().contains("resigned")
                    || message.toLowerCase().contains("checkmate")
                    || message.toLowerCase().contains("stalemate"))) {
                currentGameOver = true;
            }
        }

        @Override
        public void onError(String message) {
            System.out.println(message == null ? "Error" : message);
        }

        @Override
        public void onClose() {
            currentGameOver = true;
        }
    }
}