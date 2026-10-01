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
import kotlinx.coroutines.test.TestScope
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
}
