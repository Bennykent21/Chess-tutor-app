package com.chesstutor.app

import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.data.model.PlacementAssessment
import com.chesstutor.app.domain.ChessPosition
import com.chesstutor.app.domain.GamePhase
import com.chesstutor.app.domain.LearnCurriculumRepository
import com.chesstutor.app.domain.OpeningBook
import com.chesstutor.app.domain.ReviewItem
import com.chesstutor.app.domain.ReviewScheduler
import com.chesstutor.app.domain.ReviewStatsCalculator
import com.chesstutor.app.domain.TrainDrillsRepository
import com.chesstutor.app.engine.BlunderClassifier
import com.chesstutor.app.engine.BlunderKind
import com.chesstutor.app.engine.PositionAnalysis
import com.example.chess.core.LegalMoveGenerator
import com.example.chess.core.Position
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessTutorSpecVerificationTest {

    @Test
    fun blunderClassifier_symmetricAcrossWhiteAndBlackForMateAndCentipawn() {
        val classifier = BlunderClassifier()

        // 1. White had forced mate (+M2) and kept forced mate (+M1) -> NONE
        val whiteKeptMate = classifier.classify(
            beforeMove = PositionAnalysis(requestId = 1, bestMoveUci = "d1h5", centipawns = 1000, mateInMoves = 2),
            afterMove = PositionAnalysis(requestId = 2, bestMoveUci = "h5f7", centipawns = 1000, mateInMoves = 1),
            moverIsWhite = true
        )
        assertEquals(BlunderKind.NONE, whiteKeptMate.kind)

        // 2. Black had forced mate (-M2 from White's perspective) and kept forced mate (-M1) -> NONE
        val blackKeptMate = classifier.classify(
            beforeMove = PositionAnalysis(requestId = 1, bestMoveUci = "d8h4", centipawns = -1000, mateInMoves = -2),
            afterMove = PositionAnalysis(requestId = 2, bestMoveUci = "h4f2", centipawns = -1000, mateInMoves = -1),
            moverIsWhite = false
        )
        assertEquals(BlunderKind.NONE, blackKeptMate.kind)

        // 3. White had forced mate (+M2) and dropped to +50 cp -> MISSED_FORCED_MATE
        val whiteMissedMate = classifier.classify(
            beforeMove = PositionAnalysis(requestId = 1, bestMoveUci = "f7f8q", centipawns = 1000, mateInMoves = 2),
            afterMove = PositionAnalysis(requestId = 2, bestMoveUci = "g8f8", centipawns = 50, mateInMoves = null),
            moverIsWhite = true
        )
        assertEquals(BlunderKind.MISSED_FORCED_MATE, whiteMissedMate.kind)

        // 4. Black had forced mate (-M2) and dropped to -50 cp -> MISSED_FORCED_MATE
        val blackMissedMate = classifier.classify(
            beforeMove = PositionAnalysis(requestId = 1, bestMoveUci = "f2f1q", centipawns = -1000, mateInMoves = -2),
            afterMove = PositionAnalysis(requestId = 2, bestMoveUci = "g1f1", centipawns = -50, mateInMoves = null),
            moverIsWhite = false
        )
        assertEquals(BlunderKind.MISSED_FORCED_MATE, blackMissedMate.kind)

        // 5. White walked into forced mate (0 cp -> -M2) -> WALKED_INTO_FORCED_MATE
        val whiteWalkedIntoMate = classifier.classify(
            beforeMove = PositionAnalysis(requestId = 1, bestMoveUci = "g1h1", centipawns = 0, mateInMoves = null),
            afterMove = PositionAnalysis(requestId = 2, bestMoveUci = "d8h4", centipawns = -1000, mateInMoves = -2),
            moverIsWhite = true
        )
        assertEquals(BlunderKind.WALKED_INTO_FORCED_MATE, whiteWalkedIntoMate.kind)

        // 6. Black walked into forced mate (0 cp -> +M2) -> WALKED_INTO_FORCED_MATE
        val blackWalkedIntoMate = classifier.classify(
            beforeMove = PositionAnalysis(requestId = 1, bestMoveUci = "g8h8", centipawns = 0, mateInMoves = null),
            afterMove = PositionAnalysis(requestId = 2, bestMoveUci = "d1h5", centipawns = 1000, mateInMoves = 2),
            moverIsWhite = false
        )
        assertEquals(BlunderKind.WALKED_INTO_FORCED_MATE, blackWalkedIntoMate.kind)

        // 7. Centipawn / win-probability blunder symmetry for White (+150 -> -200) and Black (-150 -> +200)
        val whiteCpBlunder = classifier.classify(
            beforeMove = PositionAnalysis(requestId = 1, bestMoveUci = "e2e4", centipawns = 150),
            afterMove = PositionAnalysis(requestId = 2, bestMoveUci = "e7e5", centipawns = -200),
            moverIsWhite = true
        )
        val blackCpBlunder = classifier.classify(
            beforeMove = PositionAnalysis(requestId = 1, bestMoveUci = "e7e5", centipawns = -150),
            afterMove = PositionAnalysis(requestId = 2, bestMoveUci = "e2e4", centipawns = 200),
            moverIsWhite = false
        )
        assertEquals(BlunderKind.CENTIPAWN_LOSS, whiteCpBlunder.kind)
        assertEquals(whiteCpBlunder.kind, blackCpBlunder.kind)
    }

    @Test
    fun contentCertification_noDeadDrawsAndAllRecommendedMovesAreLegal() {
        // 1. PlacementAssessment positions
        for (q in PlacementAssessment.questions) {
            val corePos = Position.fromFen(q.fen)
            assertFalse(
                "Placement question ${q.id} must not be insufficient material",
                LegalMoveGenerator.isInsufficientMaterial(corePos)
            )
            val pos = ChessPosition(q.fen)
            assertTrue(
                "Placement question ${q.id} solution ${q.expectedMoveUci} must be legal in ${q.fen}",
                pos.legalMoves.any { it.uci.equals(q.expectedMoveUci, ignoreCase = true) }
            )
        }

        // 2. LearnCurriculumRepository topics
        for (topic in LearnCurriculumRepository.topics) {
            val corePos = Position.fromFen(topic.demoFen)
            assertFalse(
                "Learn topic ${topic.id} must not be insufficient material",
                LegalMoveGenerator.isInsufficientMaterial(corePos)
            )
            val pos = ChessPosition(topic.demoFen)
            assertTrue(
                "Learn topic ${topic.id} recommendedMoveUci ${topic.recommendedMoveUci} must be legal in ${topic.demoFen}",
                pos.legalMoves.any { it.uci.equals(topic.recommendedMoveUci, ignoreCase = true) }
            )
        }

        // 3. TrainDrillsRepository drills
        for (drill in TrainDrillsRepository.drills) {
            val corePos = Position.fromFen(drill.fen)
            assertFalse(
                "Drill ${drill.id} must not be insufficient material",
                LegalMoveGenerator.isInsufficientMaterial(corePos)
            )
            val pos = ChessPosition(drill.fen)
            assertTrue(
                "Drill ${drill.id} solution ${drill.solutionUci} must be legal in ${drill.fen}",
                pos.legalMoves.any { it.uci.equals(drill.solutionUci, ignoreCase = true) }
            )
        }
    }

    @Test
    fun reviewStatsCalculator_computesScorePercentWilsonBoundAndPhaseCorrectly() {
        assertEquals(62.5, ReviewStatsCalculator.scorePercent(wins = 5, draws = 0, games = 8), 0.01)
        assertEquals(50.0, ReviewStatsCalculator.scorePercent(wins = 3, draws = 2, games = 8), 0.01)

        val highSampleBound = ReviewStatsCalculator.wilsonLowerBound(wins = 14, draws = 2, games = 20)
        val tinySampleBound = ReviewStatsCalculator.wilsonLowerBound(wins = 2, draws = 0, games = 2)
        assertTrue("Wilson lower bound should be in [0, 1]", highSampleBound in 0.0..1.0)
        assertTrue("Tiny sample 2/2 has wide uncertainty", tinySampleBound < 0.40)

        // Phase detection
        val startPhase = ReviewStatsCalculator.detectPhase(ChessPosition.STARTING_FEN, ply = 4)
        assertEquals(GamePhase.OPENING, startPhase)

        val kpEndgamePhase = ReviewStatsCalculator.detectPhase("8/8/4k3/8/4P3/4K3/8/8 w - - 0 1", ply = 40)
        assertEquals(GamePhase.ENDGAME, kpEndgamePhase)

        // Dashboard aggregation over 8 Sicilian games as Black
        val games = (1..8).map { i ->
            GameRecord(
                id = "g$i",
                dateMillis = 1_700_000_000_000L + i * 1000L,
                botName = "Opponent$i",
                botRating = 1500,
                userColor = "black",
                result = if (i <= 2) "0-1" else "1-0", // 2 wins, 6 losses as Black (25% score)
                pgn = "[ECO \"B90\"]\n[Opening \"Sicilian Defense: Open Najdorf\"]\n1. e4 c5 2. Nf3 d6",
                moveCount = 20,
                finalFen = ChessPosition.STARTING_FEN,
                uciMoves = "e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4 g8f6 b1c3 a7a6"
            )
        }
        val stats = ReviewStatsCalculator.calculate(games = games, dueReviewCount = 2)
        assertEquals(8, stats.totalGames)
        assertEquals(2, stats.wins)
        assertEquals(6, stats.losses)
        assertEquals(25, stats.scorePct)
        assertTrue(stats.openings.isNotEmpty())
        val sicilian = stats.openings.first()
        assertEquals("Sicilian Defense", sicilian.family)
        assertTrue(sicilian.hasSufficientSample)
        assertTrue(sicilian.needsWork)
        assertTrue(stats.insights.isNotEmpty())
    }

    @Test
    fun openingBook_matchesAlongMovesAndDetectsOutOfTheoryPly() {
        val najdorf = OpeningBook.byId("sicilian_najdorf")
        assertNotNull(najdorf)

        val matchInBook = OpeningBook.matchAlongMoves(
            uciMoves = listOf("e2e4", "c7c5", "g1f3", "d7d6"),
            targetLine = najdorf
        )
        assertEquals(4, matchInBook.lastBookPly)
        assertNull(matchInBook.outOfTheoryPly)
        assertEquals("d2d4", matchInBook.nextBookMoveUci)

        val matchDeviated = OpeningBook.matchAlongMoves(
            uciMoves = listOf("e2e4", "c7c5", "b2b4"),
            targetLine = najdorf
        )
        assertEquals(2, matchDeviated.lastBookPly)
        assertEquals(3, matchDeviated.outOfTheoryPly)
        assertEquals("g1f3", matchDeviated.nextBookMoveUci)
    }

    @Test
    fun reviewScheduler_returnsImmutableUpdatedCopy() {
        val now = Instant.parse("2026-10-07T00:00:00Z")
        val original = ReviewItem(
            id = "rev1",
            fen = ChessPosition.STARTING_FEN,
            dueAt = now,
            stage = 0,
            attempts = 0,
            mistakeUci = "g1h3",
            bestMoveUci = "g1f3",
            explanation = "Develop knights toward the center.",
            intervalDays = 1
        )
        val updated = ReviewScheduler.recordAttempt(
            item = original,
            correct = true,
            usedHint = false,
            now = now
        )
        assertNotEquals(original, updated)
        assertEquals(0, original.stage)
        assertEquals(1, updated.stage)
        assertTrue(updated.intervalDays > original.intervalDays)
    }
}
