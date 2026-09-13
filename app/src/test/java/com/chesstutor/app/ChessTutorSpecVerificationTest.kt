package com.chesstutor.app

import com.chesstutor.app.data.repository.InMemoryReviewRepository
import com.chesstutor.app.domain.ChessPosition
import com.chesstutor.app.domain.ForkTactic
import com.chesstutor.app.domain.HangingPiece
import com.chesstutor.app.domain.MoveChoice
import com.chesstutor.app.domain.ReviewItem
import com.chesstutor.app.domain.ReviewScheduler
import com.chesstutor.app.domain.TacticalAnalysis
import com.chesstutor.app.domain.VerifiedConsequence
import com.example.chess.core.PieceColor
import com.example.chess.core.Square
import com.chesstutor.app.engine.AnalysisRequest
import com.chesstutor.app.engine.BlunderClassifier
import com.chesstutor.app.engine.BlunderKind
import com.chesstutor.app.engine.LocalFallbackEngineClient
import com.chesstutor.app.engine.PositionAnalysis
import com.chesstutor.app.engine.StockfishProcessEngineClient
import com.chesstutor.app.engine.UciProtocol
import com.chesstutor.app.viewmodel.AppViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class ChessTutorSpecVerificationTest {

    // 1. Domain: Legal Move Generation & Mate-in-One Detection
    @Test
    fun testStartingPositionLegalMoves() {
        val position = ChessPosition(ChessPosition.STARTING_FEN)
        assertEquals(20, position.legalMoves.size) // 16 pawn pushes + 4 knight jumps
        assertFalse(position.isCheck)
        assertFalse(position.isCheckmate)
        assertFalse(position.isOver)
        assertTrue(position.matesInOne.isEmpty())
    }

    @Test
    fun testMateInOneDetection() {
        // Scholar's mate position: White has Queen on f3, Bishop on c4, attacking f7. Qf7# is mate in 1!
        val position = ChessPosition(AppViewModel.FEN_MATE_IN_ONE)
        val mates = position.matesInOne
        assertEquals(1, mates.size)
        assertEquals("f3f7", mates.first().uci)

        // Verify executing mate in 1 leads to isCheckmate = true
        val checkmatePos = ChessPosition(AppViewModel.FEN_MATE_IN_ONE)
        val success = checkmatePos.play(mates.first())
        assertTrue(success)
        assertTrue(checkmatePos.isCheckmate)
        assertTrue(checkmatePos.isOver)
    }

    @Test
    fun testCastlingEnPassantAndPromotion() {
        // Castling test
        val castlingFen = "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"
        val castlingPos = ChessPosition(castlingFen)
        val castlingMoves = castlingPos.legalMoves.map { it.uci }
        assertTrue("White kingside castle e1g1", castlingMoves.contains("e1g1"))
        assertTrue("White queenside castle e1c1", castlingMoves.contains("e1c1"))

        // En passant test
        val epFen = "rnbqkbnr/ppp1p1pp/8/3pPp2/8/8/PPPP1PPP/RNBQKBNR w KQkq f6 0 3"
        val epPos = ChessPosition(epFen)
        val epMoves = epPos.legalMoves.map { it.uci }
        assertTrue("En passant capture e5f6", epMoves.contains("e5f6"))

        // Promotion test
        val promoFen = "8/4P3/8/8/8/8/8/4K2k w - - 0 1"
        val promoPos = ChessPosition(promoFen)
        val promoMoves = promoPos.legalMoves.map { it.uci }
        assertTrue("Queen promotion", promoMoves.contains("e7e8q"))
        assertTrue("Rook promotion", promoMoves.contains("e7e8r"))
    }

    // 2. Domain: Review Scheduling Math (1, 3, 7, 14, 30 days)
    @Test
    fun testReviewSchedulerIntervals() {
        val now = Instant.now()
        val item = ReviewItem(
            id = "test-item-1",
            fen = AppViewModel.FEN_MATE_IN_ONE,
            dueAt = now,
            stage = -1,
            attempts = 0
        )

        // First unassisted correct attempt -> moves to stage 0 (1 day)
        ReviewScheduler.recordAttempt(item, correct = true, usedHint = false, now = now)
        assertEquals(0, item.stage)
        assertEquals(1, item.attempts)
        assertEquals(now.plus(1, ChronoUnit.DAYS), item.dueAt)

        // Second unassisted correct attempt -> moves to stage 1 (3 days)
        val day1 = now.plus(1, ChronoUnit.DAYS)
        ReviewScheduler.recordAttempt(item, correct = true, usedHint = false, now = day1)
        assertEquals(1, item.stage)
        assertEquals(2, item.attempts)
        assertEquals(day1.plus(3, ChronoUnit.DAYS), item.dueAt)

        // Third attempt with hint -> resets stage to 0 (1 day)
        ReviewScheduler.recordAttempt(item, correct = true, usedHint = true, now = day1)
        assertEquals(0, item.stage)
        assertEquals(3, item.attempts)

        // Fourth attempt incorrect -> resets stage to -1 (due immediately)
        ReviewScheduler.recordAttempt(item, correct = false, usedHint = false, now = day1)
        assertEquals(-1, item.stage)
        assertEquals(day1, item.dueAt)
    }

    // 3. Engine: UciProtocol Parsing
    @Test
    fun testUciProtocolInfoParsing() {
        val infoLine = "info depth 12 seldepth 16 score cp 142 nodes 42100 nps 842000 pv e2e4 e7e5 g1f3"
        val parsed = UciProtocol.parseInfoLine(infoLine)
        assertNotNull(parsed)
        assertEquals(12, parsed?.depth)
        assertEquals(142, parsed?.centipawns)
        assertNull(parsed?.mateInMoves)
        assertEquals(listOf("e2e4", "e7e5", "g1f3"), parsed?.pv)

        // Mate score parsing
        val mateLine = "info depth 8 score mate 2 pv f3f7"
        val mateParsed = UciProtocol.parseInfoLine(mateLine)
        assertNotNull(mateParsed)
        assertEquals(2, mateParsed?.mateInMoves)
        assertNull(mateParsed?.centipawns)
        assertEquals(listOf("f3f7"), mateParsed?.pv)
    }

    @Test
    fun testUciProtocolBestMoveParsing() {
        assertEquals("e2e4", UciProtocol.parseBestMove("bestmove e2e4 ponder e7e5"))
        assertEquals("f3f7", UciProtocol.parseBestMove("bestmove f3f7"))
        assertNull(UciProtocol.parseBestMove("bestmove (none)"))
        assertNull(UciProtocol.parseBestMove("info depth 10"))
    }

    // 4. Engine: BlunderClassifier
    @Test
    fun testBlunderClassificationMissedForcedMate() {
        val classifier = BlunderClassifier(thresholdCentipawns = 150)

        // Position before: White had mate in 1
        val before = PositionAnalysis(
            requestId = 1,
            bestMoveUci = "f3f7",
            mateInMoves = 1
        )
        // Position after bad move: No forced mate
        val after = PositionAnalysis(
            requestId = 2,
            bestMoveUci = "d7d5",
            centipawns = 50,
            mateInMoves = null
        )

        val verdict = classifier.classify(before, after)
        assertEquals(BlunderKind.MISSED_FORCED_MATE, verdict.kind)
        assertTrue(verdict.isBlunder)
    }

    @Test
    fun testBlunderClassificationCentipawnDrop() {
        val classifier = BlunderClassifier(thresholdCentipawns = 150)

        // Position before: +200 cp
        val before = PositionAnalysis(
            requestId = 1,
            bestMoveUci = "e2e4",
            centipawns = 200
        )
        // Position after bad move: from opponent's view cp is -50 (we are at +50)
        // Loss is before - (-after) = 200 - (-(-50)) = 200 - 50 = 150 cp
        val after = PositionAnalysis(
            requestId = 2,
            bestMoveUci = "a7a6",
            centipawns = -50
        )

        val verdict = classifier.classify(before, after)
        assertEquals(BlunderKind.CENTIPAWN_LOSS, verdict.kind)
        assertEquals(150, verdict.centipawnLoss)
        assertTrue(verdict.isBlunder)
    }

    // 5. ViewModel & Mistake -> Guided Retry -> Review Loop
    @Test
    fun testAppViewModelMistakeAndRetryFlow() = runBlocking {
        val fakeRepo = InMemoryReviewRepository()
        val fakeEngine = LocalFallbackEngineClient()
        val viewModel = AppViewModel(fakeRepo, fakeEngine)
        viewModel.loadCoachPosition(AppViewModel.FEN_MATE_IN_ONE)

        // Initially in Coach with Mate in One
        assertEquals(0, viewModel.state.value.tab)
        assertFalse(viewModel.state.value.mistakeDetected)

        // Select Queen on f3
        viewModel.onSquareTapped("f3")
        assertEquals("f3", viewModel.state.value.selectedSquare)
        assertTrue(viewModel.state.value.legalTargets.contains("f7"))

        // Pick an inferior legal move from legal targets that misses mate
        val inferiorTarget = viewModel.state.value.legalTargets.first { it != "f7" }
        viewModel.onSquareTapped(inferiorTarget)

        // Mistake should be detected!
        val stateAfterMistake = viewModel.state.value
        assertTrue("Mistake must be detected", stateAfterMistake.mistakeDetected)
        assertTrue("Must allow guided retry", stateAfterMistake.canRetryMistake)
        assertEquals(
            VerifiedConsequence.MISSED_FORCED_MATE,
            stateAfterMistake.assessment?.verifiedConsequences?.firstOrNull()
        )

        // Allow viewModelScope coroutine to complete persistence
        kotlinx.coroutines.delay(200)

        // Verify mistake automatically queued in Repository
        val savedItems = fakeRepo.loadAll()
        assertEquals(1, savedItems.size)
        assertEquals(AppViewModel.FEN_MATE_IN_ONE, savedItems.first().fen)

        // Test Guided Retry
        viewModel.retryMistake()
        val stateAfterRetry = viewModel.state.value
        assertFalse("Retry resets mistake state", stateAfterRetry.mistakeDetected)
        assertFalse("Retry resets canRetry state", stateAfterRetry.canRetryMistake)
        assertEquals(AppViewModel.FEN_MATE_IN_ONE, stateAfterRetry.fen)

        // Test Hint Ladder capping (1 to 4)
        assertEquals(0, stateAfterRetry.hintLevel)
        viewModel.showHint()
        assertEquals(1, viewModel.state.value.hintLevel)
        viewModel.showHint()
        assertEquals(2, viewModel.state.value.hintLevel)
        viewModel.showHint()
        assertEquals(3, viewModel.state.value.hintLevel)
        viewModel.showHint()
        assertEquals(4, viewModel.state.value.hintLevel)
        viewModel.showHint() // capped at 4
        assertEquals(4, viewModel.state.value.hintLevel)
        assertNotNull(viewModel.state.value.recommendedArrow)
    }

    // 6. Real Stockfish Process Smoke Test
    @Test
    fun testStockfishRealProcessExecution() = runBlocking {
        val binaryFile = java.io.File("src/main/jniLibs/x86_64/libstockfish.so")
        if (!binaryFile.exists()) {
            println("Skipping real binary test because x86_64 binary not found at ${binaryFile.absolutePath}")
            return@runBlocking
        }
        val client = StockfishProcessEngineClient(binaryFile.absolutePath)
        client.initialize()
        assertTrue("Stockfish process must be alive", client.isAlive)

        val diag = client.runDiagnostics(movetimeMs = 600)
        println("=== REAL STOCKFISH DIAGNOSTICS RESULT ===")
        println("Engine: ${diag.engineName}")
        println("Best Move: ${diag.bestMove}")
        println("Centipawns: ${diag.centipawns}")
        println("Depth: ${diag.depth}")
        println("PV: ${diag.pv}")
        println("Latency: ${diag.latencyMs}ms")
        println("=========================================")

        assertTrue(diag.isAlive)
        assertTrue(diag.bestMove.isNotBlank())
        assertNotNull(diag.depth)
        assertTrue("Depth must be >= 1", (diag.depth ?: 0) >= 1)
        assertNotNull(diag.centipawns)

        client.dispose()
        assertFalse("Stockfish process should be disposed", client.isAlive)
    }

    // 7. Subprocess Crash & Dying Mid-Search Resilience Test
    @Test
    fun testStockfishSubprocessCrashGracefulFallback() = runBlocking {
        val binaryFile = java.io.File("src/main/jniLibs/x86_64/libstockfish.so")
        if (!binaryFile.exists()) return@runBlocking
        val client = StockfishProcessEngineClient(binaryFile.absolutePath)
        client.initialize()
        assertTrue(client.isAlive)

        // Simulate abrupt process termination
        client.dispose()
        assertFalse(client.isAlive)

        // Request must complete immediately via fallback without hanging
        val analysis = client.analyze(
            AnalysisRequest(
                requestId = 101,
                fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                movetimeMs = 200
            )
        )
        assertNotNull(analysis)
        assertTrue("Fallback returns a legal move without hanging", analysis.bestMoveUci.isNotBlank())
    }

    // 8. Domain: Hanging Piece Detection (Positive and Negative Examples)
    @Test
    fun testHangingPieceDetectionPositiveAndNegative() {
        // Negative example: Starting position has zero hanging pieces
        val startPos = ChessPosition(ChessPosition.STARTING_FEN)
        assertTrue("Starting position must have zero hanging pieces", startPos.hangingPieces.isEmpty())

        // Positive example: Black knight on e4 is attacked by Q(f3) and undefended, and f7 pawn is attacked by Q(f3) & B(c4)
        val hangingKnightFen = "r1bqkb1r/pppp1ppp/2n5/4p3/2B1n3/5Q2/PPPP1PPP/RNB1K1NR w KQkq - 0 4"
        val posWithHanging = ChessPosition(hangingKnightFen)
        val blackHanging = TacticalAnalysis.findHangingPieces(posWithHanging, PieceColor.BLACK)

        assertEquals("Both e4 knight and f7 pawn are tactically hanging", 2, blackHanging.size)
        assertTrue(blackHanging.any { it.square == "e4" && it.piece == 'n' && it.isCompletelyUndefended })
        assertTrue(blackHanging.any { it.square == "f7" && it.piece == 'p' })
    }

    // 9. Domain: Geometric Double-Attack (Fork) Detection
    @Test
    fun testForkDetectionPositiveAndNegative() {
        // Negative example: Starting position has zero fork moves
        val startPos = ChessPosition(ChessPosition.STARTING_FEN)
        assertTrue("Starting position must have no geometric forks", startPos.forks.isEmpty())

        // Positive example: White knight on d5 can jump to c7 checking King on e8 and attacking Rook on a8
        val forkFen = "r1bqk2r/pppp1ppp/2n5/3N4/4n3/8/PPPP1PPP/R1BQK2R w KQkq - 0 1"
        val forkPos = ChessPosition(forkFen)
        val forks = forkPos.forks

        assertTrue("Forks should be detected", forks.isNotEmpty())
        val knightFork = forks.firstOrNull { it.move.uci == "d5c7" }
        assertNotNull("Knight jump d5c7 should be identified as a geometric fork", knightFork)
        assertTrue(knightFork!!.attackedSquares.contains("e8")) // King
        assertTrue(knightFork.attackedSquares.contains("a8")) // Rook
    }

    // 10. Domain: Static Exchange Evaluation (SEE)
    @Test
    fun testStaticExchangeEvaluationSEE() {
        // Test winning capture: Pawn attacks Queen
        val fen = "4k3/8/8/3q4/4P3/8/8/4K3 w - - 0 1"
        val pos = ChessPosition(fen)
        val gain = TacticalAnalysis.staticExchangeEvaluation(
            pos.internalPosition,
            Square.fromAlgebraic("d5"),
            PieceColor.WHITE
        )
        // Capturing Queen (900) with Pawn (100) yields net gain of at least 800-900 cp
        assertTrue("SEE must evaluate winning capture with positive centipawns", gain >= 800)
    }

    // 11. Engine: UCI_Elo and UCI_LimitStrength Calibration
    @Test
    fun testUciEloCalibrationConfiguration() = runBlocking {
        val binaryFile = java.io.File("src/main/jniLibs/x86_64/libstockfish.so")
        if (!binaryFile.exists()) return@runBlocking
        val client = StockfishProcessEngineClient(binaryFile.absolutePath)
        client.initialize()
        assertTrue(client.isAlive)

        // Calibrate to CCRL 40/4 calibrated Elo: 1600
        client.setElo(1600)
        assertTrue("Engine must stay alive after setting UCI_Elo", client.isAlive)

        // Sub-1320 rating calibration uses Skill Level
        client.setElo(800)
        assertTrue("Engine must stay alive after sub-1320 Elo adjustment", client.isAlive)

        client.dispose()
    }
}
