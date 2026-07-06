package chess;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Represents a single chess piece
 * <p>
 * Note: You can add to this class, but you may not alter
 * signature of the existing methods.
 */
public class ChessPiece {

    private final ChessGame.TeamColor teamColor;
    private final PieceType pieceType;

    public ChessPiece(ChessGame.TeamColor pieceColor, ChessPiece.PieceType type) {
        this.teamColor = pieceColor;
        this.pieceType = type;
    }

    /**
     * The various different chess piece options
     */
    public enum PieceType {
        KING,
        QUEEN,
        BISHOP,
        KNIGHT,
        ROOK,
        PAWN
    }

    /**
     * @return Which team this chess piece belongs to
     */
    public ChessGame.TeamColor getTeamColor() {
        return teamColor;
    }

    /**
     * @return which type of chess piece this piece is
     */
    public PieceType getPieceType() {
        return pieceType;
    }

    /**
     * Calculates all the positions a chess piece can move to
     * Does not take into account moves that are illegal due to leaving the king in
     * danger
     *
     * @return Collection of valid moves
     */
    public Collection<ChessMove> pieceMoves(ChessBoard board, ChessPosition myPosition) {
        List<ChessMove> moves = new ArrayList<>();
        int row = myPosition.getRow();
        int col = myPosition.getColumn();

        switch (pieceType) {
            case KING -> addKingMoves(board, myPosition, row, col, moves);
            case KNIGHT -> addKnightMoves(board, myPosition, row, col, moves);
            case ROOK -> addDirectionalMoves(board, myPosition, moves, new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}});
            case BISHOP -> addDirectionalMoves(board, myPosition, moves, new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}});
            case QUEEN -> addDirectionalMoves(board, myPosition, moves, new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}});
            case PAWN -> addPawnMoves(board, myPosition, row, col, moves);
        }

        return moves;
    }

    private void addKingMoves(ChessBoard board, ChessPosition myPosition, int row, int col, List<ChessMove> moves) {
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) {
                    continue;
                }
                int r = row + dr;
                int c = col + dc;
                if (!isOnBoard(r, c)) {
                    continue;
                }
                ChessPosition target = new ChessPosition(r, c);
                ChessPiece occupant = board.getPiece(target);
                if (occupant == null || occupant.getTeamColor() != this.teamColor) {
                    moves.add(new ChessMove(myPosition, target, null));
                }
            }
        }
    }

    private void addKnightMoves(ChessBoard board, ChessPosition myPosition, int row, int col, List<ChessMove> moves) {
        int[][] deltas = {{2, 1}, {1, 2}, {-1, 2}, {-2, 1}, {-2, -1}, {-1, -2}, {1, -2}, {2, -1}};
        for (var d : deltas) {
            int r = row + d[0];
            int c = col + d[1];
            if (!isOnBoard(r, c)) {
                continue;
            }
            ChessPosition target = new ChessPosition(r, c);
            ChessPiece occupant = board.getPiece(target);
            if (occupant == null || occupant.getTeamColor() != this.teamColor) {
                moves.add(new ChessMove(myPosition, target, null));
            }
        }
    }

    private void addDirectionalMoves(ChessBoard board, ChessPosition myPosition, List<ChessMove> moves, int[][] dirs) {
        for (var d : dirs) {
            traverseDirection(board, myPosition, d[0], d[1], moves);
        }
    }

    private void addPawnMoves(ChessBoard board, ChessPosition myPosition, int row, int col, List<ChessMove> moves) {
        int dir = (this.teamColor == ChessGame.TeamColor.WHITE) ? 1 : -1;
        int forwardRow = row + dir;

        addSingleForwardMove(board, myPosition, col, forwardRow, moves);
        addDoubleForwardMove(board, myPosition, row, col, dir, moves);
        addPawnCaptures(board, myPosition, row, col, dir, moves);
    }

    private void addSingleForwardMove(ChessBoard board, ChessPosition myPosition, int col, int forwardRow, List<ChessMove> moves) {
        if (!isOnBoard(forwardRow, col)) {
            return;
        }
        ChessPosition forwardPos = new ChessPosition(forwardRow, col);
        if (board.getPiece(forwardPos) != null) {
            return;
        }
        if (forwardRow == 1 || forwardRow == 8) {
            addPromotions(moves, myPosition, forwardPos);
        } else {
            moves.add(new ChessMove(myPosition, forwardPos, null));
        }
    }

    private void addDoubleForwardMove(ChessBoard board, ChessPosition myPosition, int row, int col, int dir, List<ChessMove> moves) {
        if (!((this.teamColor == ChessGame.TeamColor.WHITE && row == 2)
                || (this.teamColor == ChessGame.TeamColor.BLACK && row == 7))) {
            return;
        }
        int twoRow = row + 2 * dir;
        if (!isOnBoard(twoRow, col)) {
            return;
        }
        ChessPosition mid = new ChessPosition(row + dir, col);
        ChessPosition dest = new ChessPosition(twoRow, col);
        if (board.getPiece(mid) == null && board.getPiece(dest) == null) {
            moves.add(new ChessMove(myPosition, dest, null));
        }
    }

    private void addPawnCaptures(ChessBoard board, ChessPosition myPosition, int row, int col, int dir, List<ChessMove> moves) {
        int[] dc = {-1, 1};
        for (int dcol : dc) {
            int c = col + dcol;
            int r = row + dir;
            if (!isOnBoard(r, c)) {
                continue;
            }
            ChessPosition target = new ChessPosition(r, c);
            ChessPiece occ = board.getPiece(target);
            if (occ == null || occ.getTeamColor() == this.teamColor) {
                continue;
            }
            if (r == 1 || r == 8) {
                addPromotions(moves, myPosition, target);
            } else {
                moves.add(new ChessMove(myPosition, target, null));
            }
        }
    }

    private boolean isOnBoard(int row, int col) {
        return row >= 1 && row <= 8 && col >= 1 && col <= 8;
    }

    private void traverseDirection(ChessBoard board, ChessPosition myPosition, int dr, int dc, List<ChessMove> moves) {
        int r = myPosition.getRow() + dr;
        int c = myPosition.getColumn() + dc;
        while (r >= 1 && r <= 8 && c >= 1 && c <= 8) {
            ChessPosition pos = new ChessPosition(r, c);
            ChessPiece occ = board.getPiece(pos);
            if (occ == null) {
                moves.add(new ChessMove(myPosition, pos, null));
            } else {
                if (occ.getTeamColor() != this.teamColor) {
                    moves.add(new ChessMove(myPosition, pos, null));
                }
                break;
            }
            r += dr;
            c += dc;
        }
    }

    private void addPromotions(List<ChessMove> moves, ChessPosition start, ChessPosition end) {
        moves.add(new ChessMove(start, end, PieceType.QUEEN));
        moves.add(new ChessMove(start, end, PieceType.BISHOP));
        moves.add(new ChessMove(start, end, PieceType.ROOK));
        moves.add(new ChessMove(start, end, PieceType.KNIGHT));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ChessPiece that = (ChessPiece) o;

        if (teamColor != that.teamColor) {
            return false;
        }
        return pieceType == that.pieceType;
    }

    @Override
    public int hashCode() {
        int result = teamColor != null ? teamColor.hashCode() : 0;
        result = 31 * result + (pieceType != null ? pieceType.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return teamColor + " " + pieceType;
    }
}
