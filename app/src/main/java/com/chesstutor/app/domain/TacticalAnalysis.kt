package com.chesstutor.app.domain

import com.example.chess.core.LegalMoveGenerator
import com.example.chess.core.Move
import com.example.chess.core.Piece
import com.example.chess.core.PieceColor
import com.example.chess.core.PieceType
import com.example.chess.core.Position
import com.example.chess.core.Square

data class HangingPiece(
    val square: String,
    val piece: Char,
    val pieceValueCp: Int,
    val netLossCp: Int,
    val isCompletelyUndefended: Boolean
)

data class ForkTactic(
    val move: MoveChoice,
    val attackedSquares: List<String>,
    val targetPieces: List<Char>
)

object TacticalAnalysis {

    private val PIECE_VALUES = mapOf(
        PieceType.PAWN to 100,
        PieceType.KNIGHT to 300,
        PieceType.BISHOP to 300,
        PieceType.ROOK to 500,
        PieceType.QUEEN to 900,
        PieceType.KING to 20000
    )

    fun pieceValue(type: PieceType): Int = PIECE_VALUES[type] ?: 0

    /**
     * Identifies all squares containing pieces of [targetColor] that are currently hanging:
     * either completely undefended and attacked, or under-defended with a positive SEE capture for the opponent.
     */
    fun findHangingPieces(chessPos: ChessPosition, targetColor: PieceColor? = null): List<HangingPiece> {
        val pos = chessPos.internalPosition
        val color = targetColor ?: pos.sideToMove
        val enemyColor = color.opposite()
        val hanging = mutableListOf<HangingPiece>()

        for (rank in 0..7) {
            for (file in 0..7) {
                val sq = Square.of(file, rank)
                val piece = pos.pieceAt(sq) ?: continue
                if (piece.color != color || piece.type == PieceType.KING) continue

                // Check if attacked by enemy
                val enemyAttackers = getAttackers(pos, sq, enemyColor)
                if (enemyAttackers.isEmpty()) continue

                val friendlyDefenders = getAttackers(pos, sq, color)
                val isUndefended = friendlyDefenders.isEmpty()

                val seeGain = staticExchangeEvaluation(pos, sq, enemyColor)
                if (isUndefended || seeGain > 0) {
                    val pieceVal = pieceValue(piece.type)
                    hanging.add(
                        HangingPiece(
                            square = sq.algebraic,
                            piece = piece.type.notation.lowercaseChar(),
                            pieceValueCp = pieceVal,
                            netLossCp = if (isUndefended) pieceVal else seeGain,
                            isCompletelyUndefended = isUndefended
                        )
                    )
                }
            }
        }
        return hanging
    }

    /**
     * Identifies candidate moves that produce a geometric fork (double-attack)
     * against two or more enemy targets (e.g. King + Queen, King + Rook, or undefended pieces).
     */
    fun findForks(chessPos: ChessPosition): List<ForkTactic> {
        val pos = chessPos.internalPosition
        val movingColor = pos.sideToMove
        val enemyColor = movingColor.opposite()
        val legalMoves = chessPos.legalMoves
        val forks = mutableListOf<ForkTactic>()

        for (moveChoice in legalMoves) {
            val testPos = ChessPosition(chessPos.fen)
            val moved = testPos.play(moveChoice)
            if (!moved) continue

            val afterPos = testPos.internalPosition
            val toSquare = Square.fromAlgebraic(moveChoice.to)

            // Pieces attacked by the newly moved piece at `toSquare`
            val attackedSquares = getSquaresAttackedByPieceAt(afterPos, toSquare)
            val highValueOrUndefendedTargets = mutableListOf<Square>()
            val targetPieces = mutableListOf<Char>()

            val movingPiece = afterPos.pieceAt(toSquare) ?: continue
            val movingVal = pieceValue(movingPiece.type)

            for (targetSq in attackedSquares) {
                val targetPiece = afterPos.pieceAt(targetSq) ?: continue
                if (targetPiece.color != enemyColor) continue

                val targetVal = pieceValue(targetPiece.type)
                val defenders = getAttackers(afterPos, targetSq, enemyColor)
                val isUndefended = defenders.isEmpty()

                // Target is considered significant if it's King (check), higher value than attacker,
                // or undefended, or equal value undefended.
                if (targetPiece.type == PieceType.KING ||
                    targetVal > movingVal ||
                    (targetVal >= movingVal && isUndefended) ||
                    (isUndefended && targetVal >= 300)
                ) {
                    highValueOrUndefendedTargets.add(targetSq)
                    targetPieces.add(targetPiece.type.notation.lowercaseChar())
                }
            }

            if (highValueOrUndefendedTargets.size >= 2) {
                forks.add(
                    ForkTactic(
                        move = moveChoice,
                        attackedSquares = highValueOrUndefendedTargets.map { it.algebraic },
                        targetPieces = targetPieces
                    )
                )
            }
        }
        return forks
    }

    /**
     * Static Exchange Evaluation (SEE):
     * Determines whether capturing on [targetSquare] is winning, equal, or losing material.
     * Returns net centipawns won by [attackerColor] (positive = winning capture, negative = losing capture).
     */
    fun staticExchangeEvaluation(
        position: Position,
        targetSquare: Square,
        attackerColor: PieceColor
    ): Int {
        val targetPiece = position.pieceAt(targetSquare) ?: return 0
        val targetValue = pieceValue(targetPiece.type)

        // Find least valuable attacker of attackerColor
        val attackers = getAttackers(position, targetSquare, attackerColor)
        if (attackers.isEmpty()) return 0

        val bestAttackerSq = attackers.minByOrNull {
            pieceValue(position.pieceAt(it)?.type ?: PieceType.QUEEN)
        } ?: return 0

        // Make capture in a temporary position
        val attackerPiece = position.pieceAt(bestAttackerSq) ?: return 0
        val tempPos = makeSimpleCapture(position, bestAttackerSq, targetSquare, attackerPiece)

        // The opponent will recapture if advantageous
        val responseGain = staticExchangeEvaluation(tempPos, targetSquare, attackerColor.opposite())
        return (targetValue - responseGain).coerceAtLeast(0)
    }

    private fun makeSimpleCapture(
        position: Position,
        from: Square,
        to: Square,
        piece: Piece
    ): Position {
        val newSquares = position.squares.copyOf()
        newSquares[from.index] = null
        newSquares[to.index] = piece
        return Position(
            squares = newSquares,
            sideToMove = position.sideToMove.opposite(),
            castlingRights = position.castlingRights,
            enPassantSquare = null,
            halfmoveClock = 0,
            fullmoveNumber = position.fullmoveNumber
        )
    }

    /**
     * Returns all squares of [attackerColor] that attack [square].
     */
    fun getAttackers(position: Position, square: Square, attackerColor: PieceColor): List<Square> {
        val file = square.file
        val rank = square.rank
        val attackers = mutableListOf<Square>()

        // 1. Pawns
        val pawnPushedRank = if (attackerColor == PieceColor.WHITE) rank - 1 else rank + 1
        if (pawnPushedRank in 0..7) {
            if (file - 1 >= 0) {
                val p = position.pieceAt(file - 1, pawnPushedRank)
                if (p?.color == attackerColor && p.type == PieceType.PAWN) {
                    attackers.add(Square.of(file - 1, pawnPushedRank))
                }
            }
            if (file + 1 <= 7) {
                val p = position.pieceAt(file + 1, pawnPushedRank)
                if (p?.color == attackerColor && p.type == PieceType.PAWN) {
                    attackers.add(Square.of(file + 1, pawnPushedRank))
                }
            }
        }

        // 2. Knights
        val knightDeltas = listOf(
            -2 to -1, -2 to 1, -1 to -2, -1 to 2,
            1 to -2, 1 to 2, 2 to -1, 2 to 1
        )
        for ((df, dr) in knightDeltas) {
            val nf = file + df
            val nr = rank + dr
            if (nf in 0..7 && nr in 0..7) {
                val p = position.pieceAt(nf, nr)
                if (p?.color == attackerColor && p.type == PieceType.KNIGHT) {
                    attackers.add(Square.of(nf, nr))
                }
            }
        }

        // 3. Rooks and Queens
        val rookDeltas = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        for ((df, dr) in rookDeltas) {
            var cf = file + df
            var cr = rank + dr
            while (cf in 0..7 && cr in 0..7) {
                val p = position.pieceAt(cf, cr)
                if (p != null) {
                    if (p.color == attackerColor && (p.type == PieceType.ROOK || p.type == PieceType.QUEEN)) {
                        attackers.add(Square.of(cf, cr))
                    }
                    break
                }
                cf += df
                cr += dr
            }
        }

        // 4. Bishops and Queens
        val bishopDeltas = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
        for ((df, dr) in bishopDeltas) {
            var cf = file + df
            var cr = rank + dr
            while (cf in 0..7 && cr in 0..7) {
                val p = position.pieceAt(cf, cr)
                if (p != null) {
                    if (p.color == attackerColor && (p.type == PieceType.BISHOP || p.type == PieceType.QUEEN)) {
                        attackers.add(Square.of(cf, cr))
                    }
                    break
                }
                cf += df
                cr += dr
            }
        }

        // 5. King
        val kingDeltas = listOf(
            -1 to -1, -1 to 0, -1 to 1,
            0 to -1, 0 to 1,
            1 to -1, 1 to 0, 1 to 1
        )
        for ((df, dr) in kingDeltas) {
            val kf = file + df
            val kr = rank + dr
            if (kf in 0..7 && kr in 0..7) {
                val p = position.pieceAt(kf, kr)
                if (p?.color == attackerColor && p.type == PieceType.KING) {
                    attackers.add(Square.of(kf, kr))
                }
            }
        }

        return attackers
    }

    /**
     * Returns squares attacked by the single piece at [fromSquare].
     */
    fun getSquaresAttackedByPieceAt(position: Position, fromSquare: Square): List<Square> {
        val piece = position.pieceAt(fromSquare) ?: return emptyList()
        val file = fromSquare.file
        val rank = fromSquare.rank
        val attacked = mutableListOf<Square>()

        when (piece.type) {
            PieceType.PAWN -> {
                val forward = if (piece.color == PieceColor.WHITE) 1 else -1
                val targetRank = rank + forward
                if (targetRank in 0..7) {
                    if (file - 1 >= 0) attacked.add(Square.of(file - 1, targetRank))
                    if (file + 1 <= 7) attacked.add(Square.of(file + 1, targetRank))
                }
            }
            PieceType.KNIGHT -> {
                val knightDeltas = listOf(
                    -2 to -1, -2 to 1, -1 to -2, -1 to 2,
                    1 to -2, 1 to 2, 2 to -1, 2 to 1
                )
                for ((df, dr) in knightDeltas) {
                    val nf = file + df
                    val nr = rank + dr
                    if (nf in 0..7 && nr in 0..7) attacked.add(Square.of(nf, nr))
                }
            }
            PieceType.BISHOP -> {
                val bishopDeltas = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
                for ((df, dr) in bishopDeltas) {
                    var cf = file + df
                    var cr = rank + dr
                    while (cf in 0..7 && cr in 0..7) {
                        attacked.add(Square.of(cf, cr))
                        if (position.pieceAt(cf, cr) != null) break
                        cf += df
                        cr += dr
                    }
                }
            }
            PieceType.ROOK -> {
                val rookDeltas = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
                for ((df, dr) in rookDeltas) {
                    var cf = file + df
                    var cr = rank + dr
                    while (cf in 0..7 && cr in 0..7) {
                        attacked.add(Square.of(cf, cr))
                        if (position.pieceAt(cf, cr) != null) break
                        cf += df
                        cr += dr
                    }
                }
            }
            PieceType.QUEEN -> {
                val queenDeltas = listOf(
                    -1 to 0, 1 to 0, 0 to -1, 0 to 1,
                    -1 to -1, -1 to 1, 1 to -1, 1 to 1
                )
                for ((df, dr) in queenDeltas) {
                    var cf = file + df
                    var cr = rank + dr
                    while (cf in 0..7 && cr in 0..7) {
                        attacked.add(Square.of(cf, cr))
                        if (position.pieceAt(cf, cr) != null) break
                        cf += df
                        cr += dr
                    }
                }
            }
            PieceType.KING -> {
                val kingDeltas = listOf(
                    -1 to -1, -1 to 0, -1 to 1,
                    0 to -1, 0 to 1,
                    1 to -1, 1 to 0, 1 to 1
                )
                for ((df, dr) in kingDeltas) {
                    val kf = file + df
                    val kr = rank + dr
                    if (kf in 0..7 && kr in 0..7) attacked.add(Square.of(kf, kr))
                }
            }
        }
        return attacked
    }
}
