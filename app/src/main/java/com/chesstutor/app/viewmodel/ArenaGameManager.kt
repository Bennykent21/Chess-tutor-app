package com.chesstutor.app.viewmodel

import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.data.repository.GameRepository
import com.chesstutor.app.data.repository.InMemoryLearningRepository
import com.chesstutor.app.data.repository.LearningRepository
import com.chesstutor.app.data.repository.ReviewRepository
import com.chesstutor.app.domain.ChessPosition
import com.chesstutor.app.domain.MoveAssessment
import com.chesstutor.app.domain.MoveChoice
import com.chesstutor.app.domain.OpeningBook
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
import com.chesstutor.app.navigation.OpeningMode
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

    private var activeMoveJob: Job? = null
    private var gameSessionId: Long = 0L

    fun startNewGame(
        botName: String,
        updateState: ((AppUiState) -> AppUiState) -> Unit
    ) {
        startNewGame(
            botName = botName,
            updateState = updateState,
            playerSide = null,
            openingMode = null,
            openingLineId = null
        )
    }

    fun startNewGame(
        botName: String,
        updateState: ((AppUiState) -> AppUiState) -> Unit,
        playerSide: Char? = null,
        openingMode: OpeningMode? = null,
        openingLineId: String? = null
    ) {
        gameSessionId++
        val sessionAtStart = gameSessionId
        activeMoveJob?.cancel()
        activeMoveJob = null

        var capturedState: AppUiState? = null
        updateState { current ->
            val resolvedSide = playerSide ?: current.arenaPlayerSide
            val resolvedMode = openingMode ?: current.selectedOpeningMode
            val resolvedLineId = when (resolvedMode) {
                OpeningMode.FREE -> null
                OpeningMode.SURPRISE_ME -> openingLineId ?: OpeningBook.lines.random().id
                else -> openingLineId ?: current.selectedOpeningLineId ?: OpeningBook.lines.first().id
            }
            val targetLine = resolvedLineId?.let { OpeningBook.byId(it) }
            val pos = ChessPosition()
            val initialHistory = mutableListOf<String>()
            val initialUci = mutableListOf<String>()
            var initialLastMove: Pair<String, String>? = null

            if (resolvedMode == OpeningMode.START_FROM_LINE && targetLine != null) {
                for (uci in targetLine.uciMoves) {
                    val san = pos.toSan(uci) ?: break
                    if (!pos.play(uci)) break
                    initialHistory += san
                    initialUci += uci
                    if (uci.length >= 4) {
                        initialLastMove = Pair(uci.substring(0, 2), uci.substring(2, 4))
                    }
                }
            }

            val match = OpeningBook.matchAlongMoves(initialUci, targetLine)
            val isGuidedOpening = targetLine != null && (resolvedMode == OpeningMode.LEARN_LINE || resolvedMode == OpeningMode.SURPRISE_ME)
            val nextBookUci = if (isGuidedOpening && pos.sideToMove == resolvedSide) match.nextBookMoveUci else null
            val nextBookSan = if (isGuidedOpening && pos.sideToMove == resolvedSide) match.nextBookMoveSan else null
            val startMsg = when {
                isGuidedOpening && nextBookSan != null ->
                    "Your move: $nextBookSan — ${OpeningBook.coachingNoteForMove(targetLine, nextBookSan)}"
                isGuidedOpening && targetLine != null ->
                    "Opening Practice: ${targetLine.name} (${targetLine.eco})"
                targetLine != null && resolvedMode == OpeningMode.START_FROM_LINE ->
                    "Starting from ${targetLine.name} (${targetLine.eco}). Play freely from here!"
                else -> "New game against $botName. ${if (resolvedSide == 'w') "Make your first move!" else "$botName opens as White..."}"
            }

            current.copy(
                arenaInitialized = true,
                fen = pos.fen,
                arenaFen = pos.fen,
                arenaPlayerSide = resolvedSide,
                selectedOpeningMode = resolvedMode,
                selectedOpeningLineId = resolvedLineId,
                liveOpeningName = match.opening?.name,
                liveOpeningEco = match.opening?.eco,
                outOfTheoryPly = match.outOfTheoryPly,
                bookContinuationHint = match.nextBookMoveSan,
                arenaNextBookMoveUci = nextBookUci,
                arenaNextBookMoveSan = nextBookSan,
                arenaDeviatedUserMoveSan = null,
                arenaDeviatedBookMoveSan = null,
                arenaDeviatedBookMoveUci = null,
                evaluationCp = 0,
                mateIn = null,
                message = startMsg,
                arenaMessage = startMsg,
                lastMove = initialLastMove,
                arenaLastMove = initialLastMove,
                moveHistory = initialHistory,
                arenaUciHistory = initialUci,
                recommendedArrow = null,
                arenaRecommendedArrow = null,
                assessment = null,
                analysis = null,
                mistakeDetected = false,
                canRetryMistake = false,
                arenaStatusText = "",
                busy = false,
                opponentThinking = false
            ).also { capturedState = it }
        }

        val initialState = capturedState ?: return
        chessEngineManager.startEvaluation(initialState.arenaFen)
        val pos = ChessPosition(initialState.arenaFen)
        if (initialState.isAutoOpponentEnabled && !pos.isOver && pos.sideToMove != initialState.arenaPlayerSide) {
            activeMoveJob = scope.launch {
                try {
                    updateState { it.copy(busy = true, opponentThinking = true) }
                    delay(250)
                    if (sessionAtStart != gameSessionId) return@launch
                    val targetLine = initialState.selectedOpeningLineId?.let { OpeningBook.byId(it) }
                    val bookUci = if (initialState.selectedOpeningMode == OpeningMode.LEARN_LINE ||
                        initialState.selectedOpeningMode == OpeningMode.SURPRISE_ME
                    ) {
                        targetLine?.uciMoves?.getOrNull(initialState.arenaUciHistory.size)
                    } else null
                    val bookChoice = bookUci?.let { uci -> pos.legalMoves.firstOrNull { it.uci == uci } }
                    val botMove = bookChoice ?: runCatching {
                        chessEngineManager.calculateBotMove(
                            initialState.arenaFen,
                            initialState.effectiveBotElo,
                            initialState.arenaDifficulty
                        )
                    }.getOrNull()
                    if (sessionAtStart != gameSessionId || botMove == null) return@launch
                    val nextPos = ChessPosition(initialState.arenaFen)
                    if (nextPos.play(botMove)) {
                        val updatedHistory = initialState.moveHistory + botMove.san
                        val updatedUci = initialState.arenaUciHistory + botMove.uci
                        val match = OpeningBook.matchAlongMoves(updatedUci, targetLine)
                        val isGuided = targetLine != null && (initialState.selectedOpeningMode == OpeningMode.LEARN_LINE ||
                            initialState.selectedOpeningMode == OpeningMode.SURPRISE_ME)
                        val userNextSan = if (isGuided && match.outOfTheoryPly == null) match.nextBookMoveSan else null
                        val userNextUci = if (isGuided && match.outOfTheoryPly == null) match.nextBookMoveUci else null
                        soundManager?.playMove(initialState.isSoundEnabled, isCapture = botMove.isCapture, isCheck = nextPos.isCheck)
                        updateState {
                            it.copy(
                                fen = nextPos.fen,
                                arenaFen = nextPos.fen,
                                lastMove = Pair(botMove.from, botMove.to),
                                arenaLastMove = Pair(botMove.from, botMove.to),
                                moveHistory = updatedHistory,
                                arenaUciHistory = updatedUci,
                                liveOpeningName = match.opening?.name ?: it.liveOpeningName,
                                liveOpeningEco = match.opening?.eco ?: it.liveOpeningEco,
                                outOfTheoryPly = match.outOfTheoryPly ?: it.outOfTheoryPly,
                                bookContinuationHint = match.nextBookMoveSan,
                                arenaNextBookMoveUci = userNextUci,
                                arenaNextBookMoveSan = userNextSan,
                                busy = false,
                                opponentThinking = false,
                                arenaStatusText = "${it.botTuningDescription} played ${botMove.san}."
                            )
                        }
                        chessEngineManager.startEvaluation(nextPos.fen)
                    }
                } finally {
                    if (sessionAtStart == gameSessionId) {
                        updateState { it.copy(busy = false, opponentThinking = false) }
                    }
                }
            }
        }
    }

    fun resignGame(
        currentState: AppUiState,
        updateState: ((AppUiState) -> AppUiState) -> Unit
    ) {
        val activeFen = if (currentState.arenaInitialized) currentState.arenaFen else currentState.fen
        val pos = runCatching { ChessPosition(activeFen) }.getOrNull() ?: return
        if (pos.isOver || currentState.moveHistory.isEmpty()) return
        gameSessionId++
        activeMoveJob?.cancel()
        val result = if (currentState.arenaPlayerSide == 'b') "1-0" else "0-1"
        scope.launch {
            runCatching {
                saveMatchRecord(
                    moves = currentState.moveHistory,
                    uciMoves = currentState.arenaUciHistory,
                    botName = currentState.arenaBotName,
                    botRating = currentState.effectiveBotElo,
                    result = result,
                    finalFen = activeFen,
                    userColor = currentState.arenaPlayerSide
                )
            }
        }
        updateState {
            it.copy(
                busy = false,
                opponentThinking = false,
                arenaStatusText = "You resigned — ${currentState.arenaBotName} wins.",
                message = "Game Over by resignation ($result).",
                arenaMessage = "Game Over by resignation ($result)."
            )
        }
    }

    fun claimDraw(
        currentState: AppUiState,
        updateState: ((AppUiState) -> AppUiState) -> Unit
    ) {
        val activeFen = if (currentState.arenaInitialized) currentState.arenaFen else currentState.fen
        val pos = runCatching { ChessPosition(activeFen) }.getOrNull() ?: return
        if (!pos.canClaimThreefoldRepetition && !pos.canClaimFiftyMoveRule) return
        gameSessionId++
        activeMoveJob?.cancel()
        scope.launch {
            runCatching {
                saveMatchRecord(
                    moves = currentState.moveHistory,
                    uciMoves = currentState.arenaUciHistory,
                    botName = currentState.arenaBotName,
                    botRating = currentState.effectiveBotElo,
                    result = "1/2-1/2",
                    finalFen = activeFen,
                    userColor = currentState.arenaPlayerSide
                )
            }
        }
        updateState {
            it.copy(
                busy = false,
                opponentThinking = false,
                arenaStatusText = "Draw claimed (1/2-1/2).",
                message = "Draw claimed under FIDE rules.",
                arenaMessage = "Draw claimed under FIDE rules."
            )
        }
    }

    fun takebackMove(
        currentState: AppUiState,
        updateState: ((AppUiState) -> AppUiState) -> Unit
    ) {
        if (currentState.arenaUciHistory.isEmpty()) return
        gameSessionId++
        activeMoveJob?.cancel()
        val dropCount = if (currentState.arenaUciHistory.size >= 2) 2 else 1
        val remainingUci = currentState.arenaUciHistory.dropLast(dropCount)
        val pos = ChessPosition()
        val remainingSan = mutableListOf<String>()
        var lastMovePair: Pair<String, String>? = null
        for (uci in remainingUci) {
            val san = pos.toSan(uci) ?: break
            if (!pos.play(uci)) break
            remainingSan += san
            if (uci.length >= 4) {
                lastMovePair = Pair(uci.substring(0, 2), uci.substring(2, 4))
            }
        }
        val targetLine = currentState.selectedOpeningLineId?.let { OpeningBook.byId(it) }
        val match = OpeningBook.matchAlongMoves(remainingUci, targetLine)
        val isGuided = targetLine != null && (currentState.selectedOpeningMode == OpeningMode.LEARN_LINE ||
            currentState.selectedOpeningMode == OpeningMode.SURPRISE_ME)
        val nextBookUci = if (isGuided && match.outOfTheoryPly == null) match.nextBookMoveUci else null
        val nextBookSan = if (isGuided && match.outOfTheoryPly == null) match.nextBookMoveSan else null
        updateState {
            it.copy(
                fen = pos.fen,
                arenaFen = pos.fen,
                lastMove = lastMovePair,
                arenaLastMove = lastMovePair,
                moveHistory = remainingSan,
                arenaUciHistory = remainingUci,
                liveOpeningName = match.opening?.name,
                liveOpeningEco = match.opening?.eco,
                outOfTheoryPly = match.outOfTheoryPly,
                bookContinuationHint = match.nextBookMoveSan,
                arenaNextBookMoveUci = nextBookUci,
                arenaNextBookMoveSan = nextBookSan,
                arenaDeviatedUserMoveSan = null,
                arenaDeviatedBookMoveSan = null,
                arenaDeviatedBookMoveUci = null,
                mistakeDetected = false,
                canRetryMistake = false,
                recommendedArrow = null,
                arenaRecommendedArrow = null,
                analysis = null,
                assessment = null,
                busy = false,
                opponentThinking = false,
                arenaStatusText = "Takeback applied.",
                message = "Takeback applied — choose a better continuation.",
                arenaMessage = "Takeback applied — choose a better continuation."
            )
        }
        chessEngineManager.startEvaluation(pos.fen)
    }

    fun playArenaMove(
        move: MoveChoice,
        currentState: AppUiState,
        loadReviews: () -> Unit,
        updateState: ((AppUiState) -> AppUiState) -> Unit
    ): Job? {
        if (currentState.busy || currentState.opponentThinking) return null
        val beforeFen = if (currentState.arenaInitialized) currentState.arenaFen else currentState.fen
        val beforePos = runCatching { ChessPosition(beforeFen) }.getOrNull() ?: return null
        if (beforePos.isOver) return null

        val afterPos = ChessPosition(beforeFen)
        if (!afterPos.play(move)) return null

        val moverIsWhite = beforePos.sideToMove == 'w'
        val sessionAtStart = gameSessionId
        val afterFen = afterPos.fen
        val newHistory = currentState.moveHistory + move.san
        val newUciHistory = currentState.arenaUciHistory + move.uci
        val targetLine = currentState.selectedOpeningLineId?.let { OpeningBook.byId(it) }
        val isGuidedOpening = targetLine != null && (currentState.selectedOpeningMode == OpeningMode.LEARN_LINE ||
            currentState.selectedOpeningMode == OpeningMode.SURPRISE_ME)
        val expectedBookUciBeforeUserMove = if (isGuidedOpening && currentState.outOfTheoryPly == null) {
            targetLine?.uciMoves?.getOrNull(currentState.arenaUciHistory.size)
        } else null
        val userDeviatedFromBook = expectedBookUciBeforeUserMove != null &&
            !move.uci.equals(expectedBookUciBeforeUserMove, ignoreCase = true)
        val expectedBookSanBeforeUserMove = expectedBookUciBeforeUserMove?.let { beforePos.toSan(it) ?: it }
        val matchAfterUser = OpeningBook.matchAlongMoves(newUciHistory, targetLine)

        soundManager?.playMove(currentState.isSoundEnabled, isCapture = move.isCapture, isCheck = afterPos.isCheck)

        updateState {
            it.copy(
                arenaInitialized = true,
                fen = afterFen,
                arenaFen = afterFen,
                lastMove = Pair(move.from, move.to),
                arenaLastMove = Pair(move.from, move.to),
                moveHistory = newHistory,
                arenaUciHistory = newUciHistory,
                liveOpeningName = matchAfterUser.opening?.name ?: it.liveOpeningName,
                liveOpeningEco = matchAfterUser.opening?.eco ?: it.liveOpeningEco,
                outOfTheoryPly = matchAfterUser.outOfTheoryPly ?: it.outOfTheoryPly,
                bookContinuationHint = matchAfterUser.nextBookMoveSan,
                arenaNextBookMoveUci = null,
                arenaNextBookMoveSan = null,
                arenaDeviatedUserMoveSan = if (userDeviatedFromBook) move.san else it.arenaDeviatedUserMoveSan,
                arenaDeviatedBookMoveSan = if (userDeviatedFromBook) expectedBookSanBeforeUserMove else it.arenaDeviatedBookMoveSan,
                arenaDeviatedBookMoveUci = if (userDeviatedFromBook) expectedBookUciBeforeUserMove else it.arenaDeviatedBookMoveUci,
                recommendedArrow = null,
                arenaRecommendedArrow = null,
                busy = true,
                message = "Move ${move.san} played. Analyzing...",
                arenaMessage = "Move ${move.san} played. Analyzing..."
            )
        }
        chessEngineManager.startEvaluation(afterFen)

        val job = scope.launch {
            try {
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
                }.onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    runCatching {
                        android.util.Log.w(
                            "ArenaGameManager",
                            "Move analysis failed; continuing game without coaching analysis.",
                            error
                        )
                    }
                }.getOrNull()

                if (sessionAtStart != gameSessionId) return@launch

                var mateLessonOutcome: Boolean? = if (afterPos.isCheckmate) true else null

                if (analysisResult != null) {
                    val analysisBefore = analysisResult.first
                    val analysisAfter = analysisResult.second
                    val verdict = blunderClassifier.classify(
                        analysisBefore,
                        analysisAfter,
                        moverIsWhite = moverIsWhite
                    )
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
                        runCatching {
                            reviewRepository.upsert(item)
                            loadReviews()
                        }.onFailure { error ->
                            if (error is kotlinx.coroutines.CancellationException) throw error
                            runCatching {
                                android.util.Log.w("ArenaGameManager", "Review persistence failed; continuing game.", error)
                            }
                        }
                    }

                    val bestArrow = if (analysisBefore.bestMoveUci.length >= 4) {
                        Pair(analysisBefore.bestMoveUci.substring(0, 2), analysisBefore.bestMoveUci.substring(2, 4))
                    } else null
                    val bestSan = beforePos.toSan(analysisBefore.bestMoveUci) ?: analysisBefore.bestMoveUci
                    val issueSummary = if (verdict.isBlunder) {
                        TacticalIssueSummary(
                            tacticalIssue = when (verdict.kind) {
                                BlunderKind.MISSED_FORCED_MATE -> "Missed Forced Checkmate"
                                BlunderKind.WALKED_INTO_FORCED_MATE -> "Walked Into Checkmate"
                                else -> "Tactical Blunder (${verdict.centipawnLoss ?: 200} cp loss)"
                            },
                            explanation = "$coachingLabel Best continuation was $bestSan.",
                            bestAlternativeMove = bestArrow
                        )
                    } else null

                    if (sessionAtStart != gameSessionId) return@launch
                    updateState {
                        it.copy(
                            assessment = assessment,
                            analysis = issueSummary,
                            recommendedArrow = if (verdict.isBlunder) bestArrow else null,
                            arenaRecommendedArrow = if (verdict.isBlunder) bestArrow else null,
                            message = coachingLabel,
                            arenaMessage = coachingLabel,
                            mistakeDetected = verdict.isBlunder,
                            mistakeFen = if (verdict.isBlunder) beforeFen else null,
                            canRetryMistake = verdict.isBlunder
                        )
                    }
                } else {
                    if (sessionAtStart != gameSessionId) return@launch
                    updateState {
                        it.copy(
                            assessment = null,
                            message = "Move ${move.san} played. Coaching analysis unavailable; continuing game.",
                            arenaMessage = "Move ${move.san} played. Coaching analysis unavailable; continuing game."
                        )
                    }
                }

                mateLessonOutcome?.let { correct ->
                    try {
                        learningRepository.recordModuleAttempt("lesson_mate_1", correct)
                    } catch (error: Exception) {
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        runCatching {
                            android.util.Log.w(
                                "ArenaGameManager",
                                "Learning progress update failed; continuing game.",
                                error
                            )
                        }
                    }
                }

                if (sessionAtStart != gameSessionId) return@launch

                if (!afterPos.isOver) {
                    if (currentState.isAutoOpponentEnabled) {
                        updateState { it.copy(opponentThinking = true) }
                        delay(350)
                        if (sessionAtStart != gameSessionId) return@launch

                        val elo = currentState.effectiveBotElo
                        val botDifficulty = currentState.arenaDifficulty
                        val bookReplyChoice = if ((currentState.selectedOpeningMode == OpeningMode.LEARN_LINE ||
                                currentState.selectedOpeningMode == OpeningMode.SURPRISE_ME) &&
                            targetLine != null && matchAfterUser.outOfTheoryPly == null
                        ) {
                            val bookUci = targetLine.uciMoves.getOrNull(newUciHistory.size)
                            afterPos.legalMoves.firstOrNull { it.uci == bookUci }
                        } else null

                        val engineMove = bookReplyChoice ?: runCatching {
                            chessEngineManager.calculateBotMove(afterFen, elo, botDifficulty)
                        }.onFailure { error ->
                            if (error is kotlinx.coroutines.CancellationException) throw error
                            runCatching {
                                android.util.Log.w("ArenaGameManager", "Bot move calculation failed.", error)
                            }
                        }.getOrNull()

                        if (sessionAtStart != gameSessionId) return@launch

                        if (engineMove != null) {
                            val engineMovePos = ChessPosition(afterFen)
                            val botMoved = engineMovePos.play(engineMove)
                            if (!botMoved) {
                                updateState { it.copy(busy = false, opponentThinking = false) }
                                return@launch
                            }
                            val updatedHistory = newHistory + engineMove.san
                            val updatedUciHistory = newUciHistory + engineMove.uci
                            val matchAfterBot = OpeningBook.matchAlongMoves(updatedUciHistory, targetLine)
                            val nextBookUciForUser = if (isGuidedOpening && matchAfterBot.outOfTheoryPly == null) {
                                matchAfterBot.nextBookMoveUci
                            } else null
                            val nextBookSanForUser = if (isGuidedOpening && matchAfterBot.outOfTheoryPly == null) {
                                matchAfterBot.nextBookMoveSan
                            } else null

                            soundManager?.playMove(currentState.isSoundEnabled, isCapture = engineMove.isCapture, isCheck = engineMovePos.isCheck)

                            updateState {
                                it.copy(
                                    fen = engineMovePos.fen,
                                    arenaFen = engineMovePos.fen,
                                    lastMove = Pair(engineMove.from, engineMove.to),
                                    arenaLastMove = Pair(engineMove.from, engineMove.to),
                                    moveHistory = updatedHistory,
                                    arenaUciHistory = updatedUciHistory,
                                    liveOpeningName = matchAfterBot.opening?.name ?: it.liveOpeningName,
                                    liveOpeningEco = matchAfterBot.opening?.eco ?: it.liveOpeningEco,
                                    outOfTheoryPly = matchAfterBot.outOfTheoryPly ?: it.outOfTheoryPly,
                                    bookContinuationHint = matchAfterBot.nextBookMoveSan,
                                    arenaNextBookMoveUci = nextBookUciForUser,
                                    arenaNextBookMoveSan = nextBookSanForUser,
                                    busy = false,
                                    opponentThinking = false,
                                    arenaStatusText = "${it.botTuningDescription} played ${engineMove.san}."
                                )
                            }
                            chessEngineManager.startEvaluation(engineMovePos.fen)

                            if (engineMovePos.isOver) {
                                val botIsWhite = afterPos.sideToMove == 'w'
                                val result = if (engineMovePos.isCheckmate) {
                                    if (botIsWhite) "1-0" else "0-1"
                                } else {
                                    "1/2-1/2"
                                }
                                if (engineMovePos.isCheckmate) {
                                    soundManager?.playBlunder(currentState.isSoundEnabled)
                                }
                                runCatching {
                                    saveMatchRecord(
                                        moves = updatedHistory,
                                        uciMoves = updatedUciHistory,
                                        botName = currentState.arenaBotName,
                                        botRating = elo,
                                        result = result,
                                        finalFen = engineMovePos.fen,
                                        userColor = currentState.arenaPlayerSide
                                    )
                                }.onFailure { error ->
                                    if (error is kotlinx.coroutines.CancellationException) throw error
                                    runCatching {
                                        android.util.Log.w("ArenaGameManager", "Saving match record failed.", error)
                                    }
                                }
                                if (sessionAtStart != gameSessionId) return@launch
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
                    val result = if (afterPos.isCheckmate) {
                        if (moverIsWhite) "1-0" else "0-1"
                    } else {
                        "1/2-1/2"
                    }
                    runCatching {
                        saveMatchRecord(
                            moves = newHistory,
                            uciMoves = newUciHistory,
                            botName = currentState.arenaBotName,
                            botRating = currentState.effectiveBotElo,
                            result = result,
                            finalFen = afterFen,
                            userColor = currentState.arenaPlayerSide
                        )
                    }.onFailure { error ->
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        runCatching {
                            android.util.Log.w("ArenaGameManager", "Saving match record failed.", error)
                        }
                    }
                    if (sessionAtStart != gameSessionId) return@launch
                    updateState {
                        it.copy(
                            busy = false,
                            opponentThinking = false,
                            arenaStatusText = if (afterPos.isCheckmate) "Game Over by Checkmate!" else "Draw!"
                        )
                    }
                }
            } finally {
                if (sessionAtStart == gameSessionId) {
                    updateState { it.copy(busy = false, opponentThinking = false) }
                }
            }
        }
        activeMoveJob = job
        return job
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
