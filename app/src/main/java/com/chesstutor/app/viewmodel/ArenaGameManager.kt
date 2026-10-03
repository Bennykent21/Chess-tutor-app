package com.chesstutor.app.viewmodel

import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.data.repository.GameRepository
import com.chesstutor.app.data.repository.InMemoryLearningRepository
import com.chesstutor.app.data.repository.LearningRepository
import com.chesstutor.app.data.repository.ReviewRepository
import com.chesstutor.app.domain.ChessPosition
import com.chesstutor.app.domain.MoveAssessment
import com.chesstutor.app.domain.MoveChoice
import com.chesstutor.app.domain.PgnFormatter
import com.chesstutor.app.domain.ReviewItem
import com.chesstutor.app.domain.SearchConfidence
import com.chesstutor.app.domain.VerifiedConsequence
import com.chesstutor.app.engine.AnalysisRequest
import com.chesstutor.app.engine.BlunderClassifier
import com.chesstutor.app.engine.BlunderKind
import com.chesstutor.app.engine.ChessEngineManager
import com.chesstutor.app.engine.EngineClient
import com.chesstutor.app.engine.LocalFallbackEngineClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

class ArenaGameManager(
    private val chessEngineManager: ChessEngineManager,
    private val reviewRepository: ReviewRepository,
    private val gameRepository: GameRepository,
    private val blunderClassifier: BlunderClassifier,
    private val learningRepository: LearningRepository = InMemoryLearningRepository(),
    private val scope: CoroutineScope,
    private val soundManager: com.chesstutor.app.audio.ChessSoundManager? = null,
    private val analysisEngineClient: EngineClient = LocalFallbackEngineClient()
) {

    fun startNewGame(
        botName: String,
        updateState: ((AppUiState) -> AppUiState) -> Unit
    ) {
        updateState {
            it.copy(
                fen = ChessPosition.STARTING_FEN,
                evaluationCp = 0,
                mateIn = null,
                message = "New game against $botName. Make your first move!",
                lastMove = null,
                moveHistory = emptyList(),
                arenaUciHistory = emptyList(),
                recommendedArrow = null,
                assessment = null,
                analysis = null,
                mistakeDetected = false,
                arenaStatusText = "",
                busy = false,
                opponentThinking = false
            )
        }
        chessEngineManager.startEvaluation(ChessPosition.STARTING_FEN)
    }

    fun playArenaMove(
        move: MoveChoice,
        currentState: AppUiState,
        loadReviews: () -> Unit,
        updateState: ((AppUiState) -> AppUiState) -> Unit
    ): Job? {
        val beforeFen = currentState.fen
        val afterPos = ChessPosition(beforeFen)
        if (!afterPos.play(move)) return null

        val afterFen = afterPos.fen
        val newHistory = currentState.moveHistory + move.san
        val newUciHistory = currentState.arenaUciHistory + move.uci

        soundManager?.playMove(currentState.isSoundEnabled, isCapture = move.isCapture, isCheck = afterPos.isCheck)

        updateState {
            it.copy(
                fen = afterFen,
                lastMove = Pair(move.from, move.to),
                moveHistory = newHistory,
                arenaUciHistory = newUciHistory,
                busy = true,
                message = "Move ${move.san} played. Analyzing..."
            )
        }
        chessEngineManager.startEvaluation(afterFen)

        return scope.launch {
            val depth = when (currentState.arenaDifficulty) {
                "Beginner" -> 2
                "Casual" -> 2
                "Intermediate" -> 3
                else -> 3
            }

            val localEngine = analysisEngineClient
            val analysisResult = runCatching {
                val analysisBefore = localEngine.analyze(AnalysisRequest(1001, beforeFen, depth = depth))
                val analysisAfter = localEngine.analyze(AnalysisRequest(1002, afterFen, depth = depth))
                analysisBefore to analysisAfter
            }.onFailure {
                android.util.Log.w(
                    "ArenaGameManager",
                    "Move analysis failed; continuing game without coaching analysis.",
                    it
                )
            }.getOrNull()

            var mateLessonOutcome: Boolean? = if (afterPos.isCheckmate) true else null

            if (analysisResult != null) {
                val analysisBefore = analysisResult.first
                val analysisAfter = analysisResult.second
                val verdict = blunderClassifier.classify(analysisBefore, analysisAfter)
                val consequences = mutableListOf<VerifiedConsequence>()

                when (verdict.kind) {
                    BlunderKind.MISSED_FORCED_MATE -> {
                        consequences.add(VerifiedConsequence.MISSED_FORCED_MATE)
                        if (!afterPos.isCheckmate) mateLessonOutcome = false
                    }
                    BlunderKind.WALKED_INTO_FORCED_MATE -> {
                        consequences.add(VerifiedConsequence.WALKED_INTO_FORCED_MATE)
                        mateLessonOutcome = false
                    }
                    BlunderKind.CENTIPAWN_LOSS -> consequences.add(VerifiedConsequence.MATERIAL_LOST_BY_FORCE)
                    else -> {}
                }

                val coachingLabel = when (verdict.kind) {
                    BlunderKind.MISSED_FORCED_MATE -> "Verified Fact: Missed forced checkmate!"
                    BlunderKind.WALKED_INTO_FORCED_MATE -> "Verified Fact: Walked into opponent forced checkmate!"
                    BlunderKind.CENTIPAWN_LOSS -> "Verified Blunder: Material or evaluation drop of ${verdict.centipawnLoss} cp."
                    else -> "Solid move: Position maintained."
                }

                val assessment = MoveAssessment(
                    evaluationBeforeCp = analysisBefore.centipawns,
                    evaluationAfterCp = analysisAfter.centipawns,
                    mateInMovesBefore = analysisBefore.mateInMoves,
                    mateInMovesAfter = analysisAfter.mateInMoves,
                    verifiedConsequences = consequences,
                    confidence = SearchConfidence(depth = depth, nodes = null),
                    coachingLabel = coachingLabel
                )

                if (verdict.isBlunder) {
                    soundManager?.playBlunder(currentState.isSoundEnabled)
                    val item = ReviewItem(
                        id = UUID.randomUUID().toString(),
                        fen = beforeFen,
                        dueAt = Instant.now(),
                        stage = -1,
                        attempts = 0,
                        mistakeUci = move.uci,
                        bestMoveUci = analysisBefore.bestMoveUci,
                        explanation = coachingLabel
                    )
                    reviewRepository.upsert(item)
                    loadReviews()
                }

                updateState {
                    it.copy(
                        assessment = assessment,
                        message = coachingLabel,
                        mistakeDetected = verdict.isBlunder,
                        mistakeFen = if (verdict.isBlunder) beforeFen else null,
                        canRetryMistake = verdict.isBlunder
                    )
                }
            } else {
                updateState {
                    it.copy(
                        assessment = null,
                        message = "Move ${move.san} played. Coaching analysis unavailable; continuing game."
                    )
                }
            }

            mateLessonOutcome?.let { correct ->
                try {
                    learningRepository.recordModuleAttempt("lesson_mate_1", correct)
                } catch (error: Exception) {
                    android.util.Log.w(
                        "ArenaGameManager",
                        "Learning progress update failed; continuing game.",
                        error
                    )
                }
            }

            if (!afterPos.isOver) {
                if (currentState.isAutoOpponentEnabled) {
                    updateState { it.copy(opponentThinking = true) }
                    delay(350)
                    val elo = currentState.effectiveBotElo
                    val botDifficulty = currentState.arenaDifficulty
                    val engineMove = chessEngineManager.calculateBotMove(afterFen, elo, botDifficulty)

                    if (engineMove != null) {
                        val engineMovePos = ChessPosition(afterFen)
                        engineMovePos.play(engineMove)
                        val updatedHistory = newHistory + engineMove.san
                        val updatedUciHistory = newUciHistory + engineMove.uci

                        soundManager?.playMove(currentState.isSoundEnabled, isCapture = engineMove.isCapture, isCheck = engineMovePos.isCheck)

                        updateState {
                            it.copy(
                                fen = engineMovePos.fen,
                                lastMove = Pair(engineMove.from, engineMove.to),
                                moveHistory = updatedHistory,
                                arenaUciHistory = updatedUciHistory,
                                busy = false,
                                opponentThinking = false,
                                arenaStatusText = "${it.botTuningDescription} played ${engineMove.san}."
                            )
                        }
                        chessEngineManager.startEvaluation(engineMovePos.fen)

                        if (engineMovePos.isOver) {
                            val result = if (engineMovePos.isCheckmate) "0-1" else "1/2-1/2"
                            if (engineMovePos.isCheckmate) {
                                soundManager?.playBlunder(currentState.isSoundEnabled)
                            }
                            saveMatchRecord(
                                moves = updatedHistory,
                                uciMoves = updatedUciHistory,
                                botName = currentState.arenaBotName,
                                botRating = elo,
                                result = result,
                                finalFen = engineMovePos.fen,
                                userColor = currentState.arenaPlayerSide
                            )
                            updateState {
                                it.copy(
                                    busy = false,
                                    opponentThinking = false,
                                    arenaStatusText = if (engineMovePos.isCheckmate) {
                                        "Game Over by Checkmate!"
                                    } else {
                                        "Draw!"
                                    }
                                )
                            }
                        }
                    } else {
                        updateState { it.copy(busy = false, opponentThinking = false) }
                    }
                } else {
                    updateState { it.copy(busy = false, opponentThinking = false) }
                }
            } else {
                chessEngineManager.startEvaluation(afterFen)
                if (afterPos.isCheckmate) {
                    soundManager?.playSuccess(currentState.isSoundEnabled)
                }
                val result = if (afterPos.isCheckmate) "1-0" else "1/2-1/2"
                saveMatchRecord(
                    moves = newHistory,
                    uciMoves = newUciHistory,
                    botName = currentState.arenaBotName,
                    botRating = currentState.effectiveBotElo,
                    result = result,
                    finalFen = afterFen,
                    userColor = currentState.arenaPlayerSide
                )
                updateState {
                    it.copy(
                        busy = false,
                        opponentThinking = false,
                        arenaStatusText = if (afterPos.isCheckmate) "Game Over by Checkmate!" else "Draw!"
                    )
                }
            }
        }
    }

    private suspend fun saveMatchRecord(
        moves: List<String>,
        uciMoves: List<String>,
        botName: String,
        botRating: Int,
        result: String,
        finalFen: String,
        userColor: Char
    ) {
        val userIsWhite = userColor.lowercaseChar() == 'w'
        val pgn = PgnFormatter.formatPgn(
            moves = moves,
            whitePlayer = if (userIsWhite) "You" else botName,
            blackPlayer = if (userIsWhite) botName else "You",
            whiteElo = if (userIsWhite) null else botRating,
            blackElo = if (userIsWhite) botRating else null,
            result = result
        )
        val record = GameRecord(
                id = UUID.randomUUID().toString(),
                dateMillis = System.currentTimeMillis(),
                botName = botName,
                botRating = botRating,
                result = result,
                pgn = pgn,
                moveCount = moves.size,
                userColor = if (userIsWhite) "white" else "black",
                finalFen = finalFen,
                uciMoves = uciMoves.joinToString(" ")
            )
        gameRepository.saveGame(record)
    }
}
