package chess;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

/**
 * A class that can manage a chess game, making moves on a board
 * <p>
 * Note: You can add to this class, but you may not alter
 * signature of the existing methods.
 */
public class ChessGame {
    private ChessBoard board;
    private TeamColor teamTurn;

    public ChessGame() {
        board = new ChessBoard();
        board.resetBoard();
        teamTurn = TeamColor.WHITE;
    }

    /**
     * @return Which team's turn it is
     */
    public TeamColor getTeamTurn() {
        return teamTurn;
    }

    /**
     * Sets which teams turn it is
     *
     * @param team the team whose turn it is
     */
    public void setTeamTurn(TeamColor team) {
        this.teamTurn = team;
    }

    /**
     * Enum identifying the 2 possible teams in a chess game
     */
    public enum TeamColor {
        WHITE,
        BLACK
    }

    /**
     * Gets all valid moves for a piece at the given location
     *
     * @param startPosition the piece to get valid moves for
     * @return Set of valid moves for requested piece, or null if no piece at
     * startPosition
     */
    public Collection<ChessMove> validMoves(ChessPosition startPosition) {
        ChessPiece piece = board.getPiece(startPosition);
        if (piece == null) {
            return null;
        }

        Collection<ChessMove> rawMoves = piece.pieceMoves(board, startPosition);
        Collection<ChessMove> legalMoves = new ArrayList<>();
        for (ChessMove move : rawMoves) {
            if (!wouldLeaveKingInCheck(piece.getTeamColor(), move)) {
                legalMoves.add(move);
            }
        }
        return legalMoves;
    }

    /**
     * Makes a move in the chess game
     *
     * @param move chess move to perform
     * @throws InvalidMoveException if move is invalid
     */
    public void makeMove(ChessMove move) throws InvalidMoveException {
        ChessPosition start = move.getStartPosition();
        ChessPiece piece = board.getPiece(start);
        if (piece == null || piece.getTeamColor() != teamTurn) {
            throw new InvalidMoveException();
        }

        Collection<ChessMove> legalMoves = validMoves(start);
        if (legalMoves == null || !legalMoves.contains(move)) {
            throw new InvalidMoveException();
        }

        ChessPosition end = move.getEndPosition();
        ChessPiece movedPiece = createPieceForMove(piece, move);
        board.addPiece(start, null);
        board.addPiece(end, movedPiece);
        teamTurn = (teamTurn == TeamColor.WHITE) ? TeamColor.BLACK : TeamColor.WHITE;
    }

    /**
     * Determines if the given team is in check
     *
     * @param teamColor which team to check for check
     * @return True if the specified team is in check
     */
    public boolean isInCheck(TeamColor teamColor) {
        return isInCheckOnBoard(board, teamColor);
    }

    /**
     * Determines if the given team is in checkmate
     *
     * @param teamColor which team to check for checkmate
     * @return True if the specified team is in checkmate
     */
    public boolean isInCheckmate(TeamColor teamColor) {
        if (!isInCheck(teamColor)) {
            return false;
        }
        return !hasAnyValidMoves(teamColor);
    }

    /**
     * Determines if the given team is in stalemate, which here is defined as having
     * no valid moves while not in check.
     *
     * @param teamColor which team to check for stalemate
     * @return True if the specified team is in stalemate, otherwise false
     */
    public boolean isInStalemate(TeamColor teamColor) {
        if (isInCheck(teamColor)) {
            return false;
        }
        return !hasAnyValidMoves(teamColor);
    }

    /**
     * Sets this game's chessboard to a given board
     *
     * @param board the new board to use
     */
    public void setBoard(ChessBoard board) {
        this.board = board;
    }

    /**
     * Gets the current chessboard
     *
     * @return the chessboard
     */
    public ChessBoard getBoard() {
        return board;
    }

    private boolean wouldLeaveKingInCheck(TeamColor teamColor, ChessMove move) {
        ChessBoard simulatedBoard = copyBoard(board);
        ChessPosition start = move.getStartPosition();
        ChessPosition end = move.getEndPosition();
        ChessPiece movingPiece = simulatedBoard.getPiece(start);
        if (movingPiece == null) {
            return true;
        }

        simulatedBoard.addPiece(start, null);
        simulatedBoard.addPiece(end, createPieceForMove(movingPiece, move));
        return isInCheckOnBoard(simulatedBoard, teamColor);
    }

    private boolean isInCheckOnBoard(ChessBoard boardToCheck, TeamColor teamColor) {
        ChessPosition kingPosition = findKing(boardToCheck, teamColor);
        if (kingPosition == null) {
            return false;
        }
        return isPositionAttacked(boardToCheck, kingPosition, teamColor);
    }

    private boolean isPositionAttacked(ChessBoard boardToCheck, ChessPosition target, TeamColor teamColor) {
        for (int row = 1; row <= 8; row++) {
            for (int col = 1; col <= 8; col++) {
                ChessPosition position = new ChessPosition(row, col);
                ChessPiece piece = boardToCheck.getPiece(position);
                if (piece == null || piece.getTeamColor() == teamColor) {
                    continue;
                }
                if (isAttackingPosition(piece, boardToCheck, position, target)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isAttackingPosition(ChessPiece piece, ChessBoard boardToCheck,
                                         ChessPosition from, ChessPosition target) {
        Collection<ChessMove> moves = piece.pieceMoves(boardToCheck, from);
        for (ChessMove move : moves) {
            if (move.getEndPosition().equals(target)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnyValidMoves(TeamColor teamColor) {
        for (int row = 1; row <= 8; row++) {
            for (int col = 1; col <= 8; col++) {
                ChessPosition position = new ChessPosition(row, col);
                ChessPiece piece = board.getPiece(position);
                if (piece != null && piece.getTeamColor() == teamColor) {
                    Collection<ChessMove> moves = validMoves(position);
                    if (moves != null && !moves.isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private ChessPosition findKing(ChessBoard boardToCheck, TeamColor teamColor) {
        for (int row = 1; row <= 8; row++) {
            for (int col = 1; col <= 8; col++) {
                ChessPosition position = new ChessPosition(row, col);
                ChessPiece piece = boardToCheck.getPiece(position);
                if (piece != null && piece.getPieceType() == ChessPiece.PieceType.KING
                        && piece.getTeamColor() == teamColor) {
                    return position;
                }
            }
        }
        return null;
    }

    private ChessBoard copyBoard(ChessBoard source) {
        ChessBoard copy = new ChessBoard();
        for (int row = 1; row <= 8; row++) {
            for (int col = 1; col <= 8; col++) {
                ChessPosition position = new ChessPosition(row, col);
                ChessPiece piece = source.getPiece(position);
                if (piece != null) {
                    copy.addPiece(position, new ChessPiece(piece.getTeamColor(), piece.getPieceType()));
                }
            }
        }
        return copy;
    }

    private ChessPiece createPieceForMove(ChessPiece piece, ChessMove move) {
        ChessPiece.PieceType pieceType = piece.getPieceType();
        if (pieceType == ChessPiece.PieceType.PAWN && move.getPromotionPiece() != null) {
            pieceType = move.getPromotionPiece();
        } else if (pieceType == ChessPiece.PieceType.PAWN && isPromotionMove(piece, move)) {
            pieceType = ChessPiece.PieceType.QUEEN;
        }
        return new ChessPiece(piece.getTeamColor(), pieceType);
    }

    private boolean isPromotionMove(ChessPiece piece, ChessMove move) {
        if (piece.getPieceType() != ChessPiece.PieceType.PAWN) {
            return false;
        }
        int startRow = move.getStartPosition().getRow();
        int endRow = move.getEndPosition().getRow();
        return (piece.getTeamColor() == TeamColor.WHITE && startRow < endRow && endRow == 8)
                || (piece.getTeamColor() == TeamColor.BLACK && startRow > endRow && endRow == 1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ChessGame chessGame = (ChessGame) o;
        return teamTurn == chessGame.teamTurn && Objects.equals(board, chessGame.board);
    }

    @Override
    public int hashCode() {
        return Objects.hash(teamTurn, board);
    }
}
