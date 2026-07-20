package ui;

import chess.ChessGame;
import chess.ChessPiece;
import chess.ChessPosition;

import java.util.Collection;
import java.util.Set;

public class BoardPrinter {
    private static final String LIGHT_SQUARE = EscapeSequences.SET_BG_COLOR_WHITE;
    private static final String DARK_SQUARE = EscapeSequences.SET_BG_COLOR_DARK_GREEN;
    private static final String RESET = EscapeSequences.RESET_BG_COLOR + EscapeSequences.RESET_TEXT_COLOR;

    public String render(ChessGame game, ChessGame.TeamColor perspective) {
        return render(game, perspective, Set.of());
    }

    public String render(ChessGame game, ChessGame.TeamColor perspective, Collection<ChessPosition> highlights) {
        StringBuilder out = new StringBuilder();
        int[] rows = perspective == ChessGame.TeamColor.BLACK ? ascending() : descending();
        int[] columns = perspective == ChessGame.TeamColor.BLACK ? descending() : ascending();

        out.append("   ");
        appendColumnLabels(out, columns);
        out.append(System.lineSeparator());

        for (int row : rows) {
            out.append(' ').append(row).append(' ');
            for (int column : columns) {
                appendSquare(out, game, row, column, highlights);
            }
            out.append(' ').append(row).append(System.lineSeparator());
        }

        out.append("   ");
        appendColumnLabels(out, columns);
        out.append(System.lineSeparator());
        return out.toString();
    }

    private void appendSquare(StringBuilder out, ChessGame game, int row, int column, Collection<ChessPosition> highlights) {
        boolean lightSquare = (row + column) % 2 == 1;
        boolean highlighted = highlights != null && highlights.contains(new ChessPosition(row, column));
        out.append(highlighted ? EscapeSequences.SET_BG_COLOR_YELLOW : (lightSquare ? LIGHT_SQUARE : DARK_SQUARE));
        out.append(pieceSymbol(game.getBoard().getPiece(new ChessPosition(row, column))));
        out.append(RESET);
    }

    private void appendColumnLabels(StringBuilder out, int[] columns) {
        for (int column : columns) {
            out.append(' ').append((char) ('a' + column - 1)).append(' ');
        }
    }

    private String pieceSymbol(ChessPiece piece) {
        if (piece == null) {
            return EscapeSequences.EMPTY;
        }

        String textColor = piece.getTeamColor() == ChessGame.TeamColor.WHITE
                ? EscapeSequences.SET_TEXT_COLOR_RED
                : EscapeSequences.SET_TEXT_COLOR_BLUE;
        return textColor + switch (piece.getPieceType()) {
            case KING -> EscapeSequences.WHITE_KING;
            case QUEEN -> EscapeSequences.WHITE_QUEEN;
            case BISHOP -> EscapeSequences.WHITE_BISHOP;
            case KNIGHT -> EscapeSequences.WHITE_KNIGHT;
            case ROOK -> EscapeSequences.WHITE_ROOK;
            case PAWN -> EscapeSequences.WHITE_PAWN;
        };
    }

    private int[] ascending() {
        return new int[]{1, 2, 3, 4, 5, 6, 7, 8};
    }

    private int[] descending() {
        return new int[]{8, 7, 6, 5, 4, 3, 2, 1};
    }
}