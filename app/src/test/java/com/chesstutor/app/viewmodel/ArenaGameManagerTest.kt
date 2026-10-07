package com.chesstutor.app.viewmodel

import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.data.repository.InMemoryGameRepository
import com.chesstutor.app.data.repository.InMemoryReviewRepository
import com.chesstutor.app.domain.ChessPosition
import com.chesstutor.app.domain.PgnFormatter
import com.chesstutor.app.engine.BlunderClassifier
import com.chesstutor.app.engine.ChessEngineManager
import com.chesstutor.app.engine.LocalFallbackEngineClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArenaGameManagerTest {

    @Test
    fun startNewGameResetsStateCorrectly() = runTest {
        val engineClient = LocalFallbackEngineClient()
        val engineManager = ChessEngineManager(engineClient)
        val reviewRepo = InMemoryReviewRepository()
        val gameRepo = InMemoryGameRepository()
        val blunderClassifier = BlunderClassifier()

        val manager = ArenaGameManager(
            chessEngineManager = engineManager,
            reviewRepository = reviewRepo,
            gameRepository = gameRepo,
            blunderClassifier = blunderClassifier,
            scope = this
        )

        var state = AppUiState(
            fen = "8/8/8/8/8/8/8/8 w - - 0 1",
            moveHistory = listOf("e4", "e5"),
            arenaStatusText = "Previous game"
        )

        manager.startNewGame("Wayne") { update ->
            state = update(state)
        }

        assertEquals(ChessPosition.STARTING_FEN, state.fen)
        assertTrue(state.moveHistory.isEmpty())
        assertEquals("", state.arenaStatusText)
        assertTrue(state.message.contains("Wayne"))
    }

    @Test
    fun pgnFormatterFormatsStandardPgnWithHeaders() {
        val moves = listOf("e4", "e5", "Nf3", "Nc6", "Bc4", "Bc5")
        val pgn = PgnFormatter.formatPgn(
            moves = moves,
            whitePlayer = "Hero",
            blackPlayer = "Wayne",
            whiteElo = 1500,
            blackElo = 600,
            result = "1-0"
        )

        assertTrue(pgn.contains("[White \"Hero\"]"))
        assertTrue(pgn.contains("[Black \"Wayne\"]"))
        assertTrue(pgn.contains("[WhiteElo \"1500\"]"))
        assertTrue(pgn.contains("[BlackElo \"600\"]"))
        assertTrue(pgn.contains("1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 1-0"))
    }

    @Test
    fun gameRepositorySavesAndObservesGames() = runTest {
        val repo = InMemoryGameRepository()
        val record = GameRecord(
            id = "test-1",
            dateMillis = 1000L,
            botName = "Wayne",
            botRating = 600,
            result = "1-0",
            pgn = "[Result \"1-0\"] 1. e4 e5",
            moveCount = 2,
            userColor = "white",
            finalFen = ChessPosition.STARTING_FEN
        )

        repo.saveGame(record)

        val recent = repo.getRecentGames(10)
        assertEquals(1, recent.size)
        assertEquals("Wayne", recent.first().botName)
        assertEquals("1-0", recent.first().result)

        val flowValue = repo.observeGames().first()
        assertEquals(1, flowValue.size)
        assertEquals("test-1", flowValue.first().id)

        repo.deleteGame("test-1")
        assertEquals(0, repo.getRecentGames(10).size)
    }

    @Test
    fun completedUserMoveSavesArenaGameRecord() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val engineManager = ChessEngineManager(
            LocalFallbackEngineClient(calculationDispatcher = testDispatcher),
            calculationDispatcher = testDispatcher
        )
        val reviewRepo = InMemoryReviewRepository()
        val gameRepo = InMemoryGameRepository()
        val learningRepo = com.chesstutor.app.data.repository.InMemoryLearningRepository()
        val manager = ArenaGameManager(
            chessEngineManager = engineManager,
            reviewRepository = reviewRepo,
            gameRepository = gameRepo,
            blunderClassifier = BlunderClassifier(),
            learningRepository = learningRepo,
            scope = this,
            analysisEngineClient = LocalFallbackEngineClient(calculationDispatcher = testDispatcher)
        )

        val position = ChessPosition(AppViewModel.FEN_BACK_RANK_MATE)
        val mateMove = position.legalMoves.firstOrNull { it.san.contains("#") }
        assertNotNull(mateMove)

        var state = AppUiState(
            fen = AppViewModel.FEN_BACK_RANK_MATE,
            arenaBotName = "Wayne",
            customBotElo = 600,
            arenaPlayerSide = 'w',
            isAutoOpponentEnabled = false
        )

        val moveJob = manager.playArenaMove(
            move = mateMove!!,
            currentState = state,
            loadReviews = {},
            updateState = { update ->
                state = update(state)
            }
        )

        moveJob?.join()

        val saved = gameRepo.getRecentGames(10)
        assertEquals(1, saved.size)
        assertEquals("1-0", saved.first().result)
        assertEquals(1, saved.first().moveCount)
        assertEquals(mateMove!!.uci, saved.first().uciMoves)
        assertTrue(saved.first().pgn.contains("#"))
        assertEquals(false, state.busy)
        assertEquals(false, state.opponentThinking)
        val mateProgress = learningRepo.getModuleProgress().first { it.moduleId == "lesson_mate_1" }
        assertEquals(1, mateProgress.attempts)
        assertEquals(1, mateProgress.correctAttempts)
    }

    @Test
    fun automaticBotResponseCompletesItsTurnWithoutLeavingThinkingState() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val engineManager = ChessEngineManager(
            LocalFallbackEngineClient(calculationDispatcher = testDispatcher),
            calculationDispatcher = testDispatcher
        )
        val reviewRepo = InMemoryReviewRepository()
        val gameRepo = InMemoryGameRepository()
        val manager = ArenaGameManager(
            chessEngineManager = engineManager,
            reviewRepository = reviewRepo,
            gameRepository = gameRepo,
            blunderClassifier = BlunderClassifier(),
            scope = this,
            analysisEngineClient = LocalFallbackEngineClient(calculationDispatcher = testDispatcher)
        )

        val position = ChessPosition(ChessPosition.STARTING_FEN)
        val playerMove = position.legalMoves.first { it.uci == "e2e4" }

        var state = AppUiState(
            fen = ChessPosition.STARTING_FEN,
            arenaBotName = "Wayne",
            customBotElo = 600,
            arenaPlayerSide = 'w',
            isAutoOpponentEnabled = true
        )

        val moveJob = manager.playArenaMove(
            move = playerMove,
            currentState = state,
            loadReviews = {},
            updateState = { update ->
                state = update(state)
            }
        )

        moveJob?.join()

        assertEquals(2, state.moveHistory.size)
        assertEquals(false, state.opponentThinking)
        assertEquals(false, state.busy)
        assertTrue(state.fen != ChessPosition.STARTING_FEN)
    }

    @Test
    fun botCheckmateSavesLossAndStopsFurtherMoves() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val mateBotEngine = object : com.chesstutor.app.engine.EngineClient {
            override suspend fun initialize() = Unit
            override suspend fun analyze(request: com.chesstutor.app.engine.AnalysisRequest): com.chesstutor.app.engine.PositionAnalysis {
                return com.chesstutor.app.engine.PositionAnalysis(
                    requestId = request.requestId,
                    bestMoveUci = "a2a1",
                    centipawns = -10000,
                    mateInMoves = -1,
                    depth = 5
                )
            }
            override suspend fun stop() = Unit
            override suspend fun dispose() = Unit
        }
        val engineManager = ChessEngineManager(
            mateBotEngine,
            calculationDispatcher = testDispatcher
        )
        val reviewRepo = InMemoryReviewRepository()
        val gameRepo = InMemoryGameRepository()
        val manager = ArenaGameManager(
            chessEngineManager = engineManager,
            reviewRepository = reviewRepo,
            gameRepository = gameRepo,
            blunderClassifier = BlunderClassifier(),
            scope = this,
            analysisEngineClient = LocalFallbackEngineClient(calculationDispatcher = testDispatcher)
        )

        val fenBeforeWhiteMove = "6k1/5ppp/8/8/8/2P5/r4PPP/6K1 w - - 0 1"
        val pos = ChessPosition(fenBeforeWhiteMove)
        val whiteMove = pos.legalMoves.first { it.uci == "c3c4" }

        var state = AppUiState(
            fen = fenBeforeWhiteMove,
            arenaBotName = "MasterBot",
            arenaDifficulty = "Custom",
            customBotElo = 2800,
            arenaPlayerSide = 'w',
            isAutoOpponentEnabled = true
        )

        val job = manager.playArenaMove(
            move = whiteMove,
            currentState = state,
            loadReviews = {},
            updateState = { update -> state = update(state) }
        )
        job?.join()

        val saved = gameRepo.getRecentGames(10)
        assertEquals(1, saved.size)
        assertEquals("0-1", saved.first().result)
        assertEquals(false, state.busy)
        assertEquals(false, state.opponentThinking)
        assertEquals("Game Over by Checkmate!", state.arenaStatusText)

        // Attempting another move after game completion is rejected
        val extraMove = manager.playArenaMove(
            move = whiteMove,
            currentState = state,
            loadReviews = {},
            updateState = { update -> state = update(state) }
        )
        assertEquals(null, extraMove)
    }

    @Test
    fun stalemateSavesDrawRecordCleanly() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val engineManager = ChessEngineManager(
            LocalFallbackEngineClient(calculationDispatcher = testDispatcher),
            calculationDispatcher = testDispatcher
        )
        val gameRepo = InMemoryGameRepository()
        val manager = ArenaGameManager(
            chessEngineManager = engineManager,
            reviewRepository = InMemoryReviewRepository(),
            gameRepository = gameRepo,
            blunderClassifier = BlunderClassifier(),
            scope = this,
            analysisEngineClient = LocalFallbackEngineClient(calculationDispatcher = testDispatcher)
        )

        // White plays Qc7 stalemate against lone Black king on a8
        val stalemateSetupFen = "k7/8/1K6/8/8/8/2Q5/8 w - - 0 1"
        val pos = ChessPosition(stalemateSetupFen)
        val stalemateMove = pos.legalMoves.first { it.uci == "c2c7" }

        var state = AppUiState(
            fen = stalemateSetupFen,
            arenaBotName = "Wayne",
            customBotElo = 600,
            arenaPlayerSide = 'w',
            isAutoOpponentEnabled = true
        )

        val job = manager.playArenaMove(
            move = stalemateMove,
            currentState = state,
            loadReviews = {},
            updateState = { update -> state = update(state) }
        )
        job?.join()

        val saved = gameRepo.getRecentGames(10)
        assertEquals(1, saved.size)
        assertEquals("1/2-1/2", saved.first().result)
        assertEquals("Draw!", state.arenaStatusText)
        assertEquals(false, state.busy)
        assertEquals(false, state.opponentThinking)
    }

    @Test
    fun rapidRestartCancelsInFlightBotTurnAndPreventsStaleStateWrites() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val engineManager = ChessEngineManager(
            LocalFallbackEngineClient(calculationDispatcher = testDispatcher),
            calculationDispatcher = testDispatcher
        )
        val manager = ArenaGameManager(
            chessEngineManager = engineManager,
            reviewRepository = InMemoryReviewRepository(),
            gameRepository = InMemoryGameRepository(),
            blunderClassifier = BlunderClassifier(),
            scope = this,
            analysisEngineClient = LocalFallbackEngineClient(calculationDispatcher = testDispatcher)
        )

        val startPos = ChessPosition(ChessPosition.STARTING_FEN)
        val e4 = startPos.legalMoves.first { it.uci == "e2e4" }
        val d4 = startPos.legalMoves.first { it.uci == "d2d4" }

        var state = AppUiState(
            fen = ChessPosition.STARTING_FEN,
            arenaBotName = "Wayne",
            isAutoOpponentEnabled = true
        )

        // 1. Start move e4 (coroutine is launched, waiting on delay/engine)
        val firstJob = manager.playArenaMove(
            move = e4,
            currentState = state,
            loadReviews = {},
            updateState = { update -> state = update(state) }
        )
        assertNotNull(firstJob)

        // 2. Immediately restart game before bot responds
        manager.startNewGame("Wayne") { update -> state = update(state) }
        testScheduler.advanceUntilIdle()

        assertEquals(ChessPosition.STARTING_FEN, state.fen)
        assertTrue(state.moveHistory.isEmpty())
        assertEquals(false, state.busy)
        assertEquals(false, state.opponentThinking)

        // 3. Play d4 in the fresh game and let bot respond
        val secondJob = manager.playArenaMove(
            move = d4,
            currentState = state,
            loadReviews = {},
            updateState = { update -> state = update(state) }
        )
        secondJob?.join()

        assertEquals("d4", state.moveHistory.first())
        assertEquals(2, state.moveHistory.size)
        assertEquals(false, state.busy)
        assertEquals(false, state.opponentThinking)
    }

    @Test
    fun analysisAndPersistenceExceptionsNeverLeaveGameBusy() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val failingAnalysisEngine = object : com.chesstutor.app.engine.EngineClient {
            override suspend fun initialize() = Unit
            override suspend fun analyze(request: com.chesstutor.app.engine.AnalysisRequest): com.chesstutor.app.engine.PositionAnalysis {
                error("Simulated analysis engine crash")
            }
            override suspend fun stop() = Unit
            override suspend fun dispose() = Unit
        }
        val failingGameRepo = object : com.chesstutor.app.data.repository.GameRepository {
            override fun observeGames() = kotlinx.coroutines.flow.flowOf(emptyList<GameRecord>())
            override suspend fun getRecentGames(limit: Int): List<GameRecord> = emptyList()
            override suspend fun saveGame(game: GameRecord) {
                error("Simulated database write failure")
            }
            override suspend fun deleteGame(id: String) = Unit
        }

        val engineManager = ChessEngineManager(
            LocalFallbackEngineClient(calculationDispatcher = testDispatcher),
            calculationDispatcher = testDispatcher
        )
        val manager = ArenaGameManager(
            chessEngineManager = engineManager,
            reviewRepository = InMemoryReviewRepository(),
            gameRepository = failingGameRepo,
            blunderClassifier = BlunderClassifier(),
            scope = this,
            analysisEngineClient = failingAnalysisEngine
        )

        val position = ChessPosition(AppViewModel.FEN_BACK_RANK_MATE)
        val mateMove = position.legalMoves.first { it.san.contains("#") }

        var state = AppUiState(
            fen = AppViewModel.FEN_BACK_RANK_MATE,
            arenaBotName = "Wayne",
            isAutoOpponentEnabled = false
        )

        val job = manager.playArenaMove(
            move = mateMove,
            currentState = state,
            loadReviews = {},
            updateState = { update -> state = update(state) }
        )
        job?.join()

        assertEquals(false, state.busy)
        assertEquals(false, state.opponentThinking)
        assertEquals("Game Over by Checkmate!", state.arenaStatusText)
    }
}
