package com.chesstutor.app.engine

import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.domain.ChessPosition
import com.chesstutor.app.domain.TacticalAnalysis
import com.example.chess.core.PieceColor
import java.util.Locale
import kotlin.math.abs

enum class MoveClassification(val label: String, val annotation: String) {
    NORMAL("Normal", ""),
    INACCURACY("Inaccuracy", "?!"),
    MISTAKE("Mistake", "?"),
    BLUNDER("Blunder", "??"),
    MISSED_FORCED_MATE("Missed Forced Mate", "??"),
    WALKED_INTO_FORCED_MATE("Walked Into Forced Mate", "??");

    val isSuboptimal: Boolean
        get() = this != NORMAL
}

enum class GameAnalysisStatus {
    COMPLETE,
    PARTIALLY_COMPLETE,
    LEGACY_UNAVAILABLE,
    UNAVAILABLE
}

data class GameMoveAnalysis(
    val ply: Int,
    val uci: String,
    val before: PositionAnalysis,
    val after: PositionAnalysis,
    val verdict: BlunderVerdict,
    val userMove: Boolean,
    val san: String = uci,
    val bestMoveSan: String = before.bestMoveUci,
    val beforeFen: String = ChessPosition.STARTING_FEN,
    val afterFen: String = ChessPosition.STARTING_FEN,
    val moverIsWhite: Boolean = (ply % 2 == 1),
    val centipawnLoss: Int = verdict.centipawnLoss ?: 0,
    val classification: MoveClassification = MoveClassification.NORMAL,
    val consequence: String = "",
    val recommendedModuleId: String? = null
) {
    val moveNumber: Int
        get() = (ply + 1) / 2

    val bestMoveUci: String
        get() = before.bestMoveUci

    val annotatedSan: String
        get() = "$san${classification.annotation}"

    val formattedEvalBefore: String
        get() = formatEval(before)

    val formattedEvalAfter: String
        get() = formatEval(after)

    companion object {
        fun formatEval(analysis: PositionAnalysis): String {
            analysis.mateInMoves?.let { mate ->
                return when {
                    mate > 0 -> "+M$mate"
                    mate < 0 -> "-M${abs(mate)}"
                    else -> "Mate"
                }
            }
            val cp = analysis.centipawns ?: return "0.0"
            val pawns = cp / 100.0
            val sign = if (cp > 0) "+" else ""
            return String.format(Locale.US, "%s%.1f", sign, pawns)
        }
    }
}

data class GameAnalysisResult(
    val gameId: String,
    val analyzedMoves: List<GameMoveAnalysis>,
    val skippedMoves: Int,
    val status: GameAnalysisStatus = when {
        analyzedMoves.isEmpty() && skippedMoves > 0 -> GameAnalysisStatus.UNAVAILABLE
        skippedMoves > 0 -> GameAnalysisStatus.PARTIALLY_COMPLETE
        else -> GameAnalysisStatus.COMPLETE
    }
) {
    val userMoves: List<GameMoveAnalysis>
        get() = analyzedMoves.filter { it.userMove }

    val userMoveCount: Int
        get() = userMoves.size

    val userBlunders: List<GameMoveAnalysis>
        get() = analyzedMoves.filter { it.userMove && it.verdict.isBlunder }

    val userMistakes: List<GameMoveAnalysis>
        get() = analyzedMoves.filter { it.userMove && it.classification == MoveClassification.MISTAKE }

    val userInaccuracies: List<GameMoveAnalysis>
        get() = analyzedMoves.filter { it.userMove && it.classification == MoveClassification.INACCURACY }

    val userSuboptimalMoves: List<GameMoveAnalysis>
        get() = analyzedMoves.filter { it.userMove && it.classification.isSuboptimal }

    val missedForcedMates: Int
        get() = userBlunders.count { it.verdict.kind == BlunderKind.MISSED_FORCED_MATE }

    val walkedIntoForcedMates: Int
        get() = userBlunders.count { it.verdict.kind == BlunderKind.WALKED_INTO_FORCED_MATE }

    val centipawnLoss: Int
        get() = userBlunders.sumOf { it.verdict.centipawnLoss ?: 0 }

    val totalUserCentipawnLoss: Int
        get() = userMoves.sumOf { it.centipawnLoss }

    val defaultSelectedMove: GameMoveAnalysis?
        get() = userBlunders.firstOrNull()
            ?: userMistakes.firstOrNull()
            ?: userInaccuracies.firstOrNull()
            ?: userMoves.firstOrNull()

    val detectedWeaknessModules: Map<String, Int>
        get() = userSuboptimalMoves
            .mapNotNull { it.recommendedModuleId }
            .groupingBy { it }
            .eachCount()
}

class GameAnalysisService(
    private val engine: EngineClient,
    private val blunderClassifier: BlunderClassifier = BlunderClassifier()
) {
    suspend fun analyze(
        game: GameRecord,
        depth: Int = 5
    ): GameAnalysisResult {
        require(depth > 0) { "Analysis depth must be positive." }

        val moves = game.uciMoves
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        if (moves.isEmpty()) {
            val isLegacy = game.moveCount > 0 || game.pgn.isNotBlank()
            return GameAnalysisResult(
                gameId = game.id,
                analyzedMoves = emptyList(),
                skippedMoves = 0,
                status = if (isLegacy) GameAnalysisStatus.LEGACY_UNAVAILABLE else GameAnalysisStatus.COMPLETE
            )
        }

        val userIsWhite = game.userColor.equals("white", ignoreCase = true)
        val position = ChessPosition()
        val results = mutableListOf<GameMoveAnalysis>()
        var skipped = 0

        for ((index, uci) in moves.withIndex()) {
            val moverIsWhite = position.sideToMove == 'w'
            val userMove = moverIsWhite == userIsWhite
            val beforeFen = position.fen
            val beforeSnapshot = ChessPosition(beforeFen)
            val moveSan = beforeSnapshot.toSan(uci)

            if (moveSan == null || !position.play(uci)) {
                skipped++
                break
            }

            val afterFen = position.fen
            val afterSnapshot = ChessPosition(afterFen)

            val before = runCatching {
                engine.analyze(
                    AnalysisRequest(
                        requestId = index * 2 + 1,
                        fen = beforeFen,
                        depth = depth
                    )
                )
            }.getOrNull()

            val after = if (before != null) {
                runCatching {
                    engine.analyze(
                        AnalysisRequest(
                            requestId = index * 2 + 2,
                            fen = afterFen,
                            depth = depth
                        )
                    )
                }.getOrNull()
            } else {
                null
            }

            if (before == null || after == null) {
                skipped++
                continue
            }

            val bestMoveSan = beforeSnapshot.toSan(before.bestMoveUci) ?: before.bestMoveUci
            val verdict = blunderClassifier.classify(
                before,
                after,
                moverIsWhite = moverIsWhite
            )

            val rawLossCp = computeCentipawnLoss(before, after, verdict, moverIsWhite)
            val classification = classifyMove(
                uci = uci,
                bestMoveUci = before.bestMoveUci,
                verdict = verdict,
                centipawnLoss = rawLossCp
            )
            val (consequence, recommendedModuleId) = buildVerifiedConsequenceAndModule(
                beforeSnapshot = beforeSnapshot,
                afterSnapshot = afterSnapshot,
                uci = uci,
                bestMoveUci = before.bestMoveUci,
                bestMoveSan = bestMoveSan,
                moverIsWhite = moverIsWhite,
                before = before,
                after = after,
                classification = classification,
                centipawnLoss = rawLossCp
            )

            results += GameMoveAnalysis(
                ply = index + 1,
                uci = uci,
                before = before,
                after = after,
                verdict = verdict,
                userMove = userMove,
                san = moveSan,
                bestMoveSan = bestMoveSan,
                beforeFen = beforeFen,
                afterFen = afterFen,
                moverIsWhite = moverIsWhite,
                centipawnLoss = rawLossCp,
                classification = classification,
                consequence = consequence,
                recommendedModuleId = recommendedModuleId
            )
        }

        val status = when {
            results.isEmpty() && skipped > 0 -> GameAnalysisStatus.UNAVAILABLE
            skipped > 0 -> GameAnalysisStatus.PARTIALLY_COMPLETE
            else -> GameAnalysisStatus.COMPLETE
        }

        return GameAnalysisResult(
            gameId = game.id,
            analyzedMoves = results,
            skippedMoves = skipped,
            status = status
        )
    }

    private fun computeCentipawnLoss(
        before: PositionAnalysis,
        after: PositionAnalysis,
        verdict: BlunderVerdict,
        moverIsWhite: Boolean
    ): Int {
        verdict.centipawnLoss?.let { return it.coerceAtLeast(0) }
        val beforeCp = before.centipawns
        val afterCp = after.centipawns
        if (beforeCp != null && afterCp != null) {
            val diff = if (moverIsWhite) beforeCp - afterCp else afterCp - beforeCp
            return diff.coerceAtLeast(0)
        }
        return when (verdict.kind) {
            BlunderKind.MISSED_FORCED_MATE -> 300
            BlunderKind.WALKED_INTO_FORCED_MATE -> 500
            else -> 0
        }
    }

    private fun classifyMove(
        uci: String,
        bestMoveUci: String,
        verdict: BlunderVerdict,
        centipawnLoss: Int
    ): MoveClassification {
        return when (verdict.kind) {
            BlunderKind.MISSED_FORCED_MATE -> MoveClassification.MISSED_FORCED_MATE
            BlunderKind.WALKED_INTO_FORCED_MATE -> MoveClassification.WALKED_INTO_FORCED_MATE
            BlunderKind.CENTIPAWN_LOSS -> MoveClassification.BLUNDER
            BlunderKind.NONE, BlunderKind.UNKNOWN -> when {
                uci == bestMoveUci -> MoveClassification.NORMAL
                centipawnLoss >= 150 -> MoveClassification.BLUNDER
                centipawnLoss >= 100 -> MoveClassification.MISTAKE
                centipawnLoss >= 50 -> MoveClassification.INACCURACY
                else -> MoveClassification.NORMAL
            }
        }
    }

    private fun buildVerifiedConsequenceAndModule(
        beforeSnapshot: ChessPosition,
        afterSnapshot: ChessPosition,
        uci: String,
        bestMoveUci: String,
        bestMoveSan: String,
        moverIsWhite: Boolean,
        before: PositionAnalysis,
        after: PositionAnalysis,
        classification: MoveClassification,
        centipawnLoss: Int
    ): Pair<String, String?> {
        when (classification) {
            MoveClassification.MISSED_FORCED_MATE -> {
                val mateIn = abs(before.mateInMoves ?: 1).coerceAtLeast(1)
                val targetRank = bestMoveUci.getOrNull(3)
                val moduleId = if (targetRank == '8' || targetRank == '1') "lesson_back_rank" else "lesson_mate_1"
                return "You missed a forced checkmate. The engine found a mate in $mateIn ($bestMoveSan)." to moduleId
            }
            MoveClassification.WALKED_INTO_FORCED_MATE -> {
                val mateIn = abs(after.mateInMoves ?: 1).coerceAtLeast(1)
                val replyRank = after.bestMoveUci.getOrNull(3)
                val moduleId = if (replyRank == '8' || replyRank == '1') "lesson_back_rank" else "lesson_interpose_fail"
                return "This move allows a forced checkmate in $mateIn. Better was $bestMoveSan." to moduleId
            }
            MoveClassification.NORMAL -> {
                if (afterSnapshot.isCheckmate) {
                    return "Delivered checkmate." to null
                }
                if (uci == bestMoveUci) {
                    return "Matches the engine's top choice." to null
                }
                return "Solid move that maintains the evaluation." to null
            }
            else -> {
                val moverColor = if (moverIsWhite) PieceColor.WHITE else PieceColor.BLACK
                val hangingBefore = TacticalAnalysis.findHangingPieces(beforeSnapshot, moverColor)
                    .map { it.square }
                    .toSet()
                val hangingAfter = TacticalAnalysis.findHangingPieces(afterSnapshot, moverColor)
                val destSquare = if (uci.length >= 4) uci.substring(2, 4) else ""
                val newlyHanging = hangingAfter
                    .filter { it.square !in hangingBefore || it.square == destSquare }
                    .maxByOrNull { it.netLossCp }

                if (newlyHanging != null) {
                    val name = pieceName(newlyHanging.piece)
                    val msg = if (newlyHanging.isCompletelyUndefended) {
                        "This move leaves your $name on ${newlyHanging.square} undefended. Your opponent can win the piece immediately."
                    } else {
                        "This move leaves your $name on ${newlyHanging.square} insufficiently defended. Prefer $bestMoveSan."
                    }
                    return msg to "lesson_hanging_piece"
                }

                val missedFork = beforeSnapshot.forks.firstOrNull { it.move.uci == bestMoveUci }
                if (missedFork != null) {
                    val targets = missedFork.attackedSquares.joinToString(" and ")
                    return "You missed a tactical fork ($bestMoveSan) attacking $targets." to "tactics_knight_fork"
                }

                val allowedFork = afterSnapshot.forks.firstOrNull()
                if (allowedFork != null) {
                    val targets = allowedFork.attackedSquares.joinToString(" and ")
                    return "This move allows your opponent to play ${allowedFork.move.san}, forking $targets." to "tactics_knight_fork"
                }

                if (centipawnLoss >= 300) {
                    return "This move loses material without compensation ($centipawnLoss cp loss). Best move was $bestMoveSan." to "lesson_hanging_piece"
                }

                return when (classification) {
                    MoveClassification.BLUNDER ->
                        "Missed a critical tactical or positional resource ($centipawnLoss cp loss). Best move was $bestMoveSan." to "lesson_overworked_piece"
                    MoveClassification.MISTAKE ->
                        "Surrenders significant advantage ($centipawnLoss cp loss). Prefer $bestMoveSan." to "lesson_overworked_piece"
                    MoveClassification.INACCURACY ->
                        "Slight inaccuracy ($centipawnLoss cp loss). $bestMoveSan keeps a stronger position." to "tactics_pin_and_skewer"
                    else ->
                        "Best move was $bestMoveSan." to null
                }
            }
        }
    }

    private fun pieceName(pieceChar: Char): String = when (pieceChar.lowercaseChar()) {
        'p' -> "pawn"
        'n' -> "knight"
        'b' -> "bishop"
        'r' -> "rook"
        'q' -> "queen"
        'k' -> "king"
        else -> "piece"
    }
}

