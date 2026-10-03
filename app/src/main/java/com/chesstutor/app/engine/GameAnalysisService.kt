package com.chesstutor.app.engine

import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.domain.ChessPosition

data class GameMoveAnalysis(
    val ply: Int,
    val uci: String,
    val before: PositionAnalysis,
    val after: PositionAnalysis,
    val verdict: BlunderVerdict,
    val userMove: Boolean
)

data class GameAnalysisResult(
    val gameId: String,
    val analyzedMoves: List<GameMoveAnalysis>,
    val skippedMoves: Int
) {
    val userMoveCount: Int
        get() = analyzedMoves.count { it.userMove }

    val userBlunders: List<GameMoveAnalysis>
        get() = analyzedMoves.filter { it.userMove && it.verdict.isBlunder }

    val missedForcedMates: Int
        get() = userBlunders.count { it.verdict.kind == BlunderKind.MISSED_FORCED_MATE }

    val walkedIntoForcedMates: Int
        get() = userBlunders.count { it.verdict.kind == BlunderKind.WALKED_INTO_FORCED_MATE }

    val centipawnLoss: Int
        get() = userBlunders.sumOf { it.verdict.centipawnLoss ?: 0 }
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
            return GameAnalysisResult(game.id, emptyList(), skippedMoves = 0)
        }

        val userIsWhite = game.userColor.equals("white", ignoreCase = true)
        val position = ChessPosition()
        val results = mutableListOf<GameMoveAnalysis>()
        var skipped = 0

        for ((index, uci) in moves.withIndex()) {
            val userMove = position.sideToMove == if (userIsWhite) 'w' else 'b'
            val beforeFen = position.fen

            val before = runCatching {
                engine.analyze(
                    AnalysisRequest(
                        requestId = index * 2 + 1,
                        fen = beforeFen,
                        depth = depth
                    )
                )
            }.getOrNull()

            if (!position.play(uci)) {
                skipped++
                break
            }

            val after = if (before != null) {
                runCatching {
                    engine.analyze(
                        AnalysisRequest(
                            requestId = index * 2 + 2,
                            fen = position.fen,
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

            results += GameMoveAnalysis(
                ply = index + 1,
                uci = uci,
                before = before,
                after = after,
                verdict = blunderClassifier.classify(before, after),
                userMove = userMove
            )
        }

        return GameAnalysisResult(
            gameId = game.id,
            analyzedMoves = results,
            skippedMoves = skipped
        )
    }
}
