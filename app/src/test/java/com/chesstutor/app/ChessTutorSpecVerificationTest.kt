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

    @Test
    fun lessonTrainSession_everyTopicCreatesMultiStepQueueWithVerifiedLegalSolutionsAndSpecificFeedback() {
        val topics = LearnCurriculumRepository.topics
        val drills = TrainDrillsRepository.drills
        assertTrue(topics.isNotEmpty())
        assertTrue(drills.isNotEmpty())

        for (topic in topics) {
            val session = com.chesstutor.app.domain.LessonSessionFactory.buildSessionForTopic(topic)
            assertEquals(topic.id, session.topicId)
            assertEquals(topic.title, session.topicTitle)
            assertTrue("Session for ${topic.id} must have at least 4 steps", session.steps.size >= 4)
            assertEquals(com.chesstutor.app.domain.SessionStepKind.EXPLAIN, session.steps.first().kind)
            assertFalse("New session must not start on summary", session.isSummary)

            for (step in session.steps) {
                val pos = ChessPosition(step.fen)
                assertTrue(
                    "Step ${step.id} in lesson ${topic.id} must have a legal solution ${step.solutionUci} in ${step.fen}",
                    pos.legalMoves.any { it.uci.equals(step.solutionUci, ignoreCase = true) }
                )
            }
        }

        val firstTopic = topics.first()
        val firstSession = com.chesstutor.app.domain.LessonSessionFactory.buildSessionForTopic(firstTopic)
        val firstStep = firstSession.currentStep
        val pos = ChessPosition(firstStep.fen)
        val wrongMove = pos.legalMoves.first { !it.uci.equals(firstStep.solutionUci, ignoreCase = true) }
        val explanation = com.chesstutor.app.domain.LessonSessionFactory.buildSpecificWrongMoveFeedback(
            fen = firstStep.fen,
            playedUci = wrongMove.uci,
            solutionUci = firstStep.solutionUci,
            conceptHint = firstStep.conceptHint
        )
        assertTrue("Wrong move explanation must mention the played SAN", explanation.contains(wrongMove.san))
    }

    @Test
    fun hintLegalityGuard_refusesIllegalOrMismatchedSolutionInsteadOfInventingMove() {
        val startFen = ChessPosition.STARTING_FEN
        assertTrue(com.chesstutor.app.viewmodel.AppViewModel.isSolutionLegalInFen(startFen, "e2e4"))
        // Back-rank mate move a1a8 is NOT legal in the starting position
        assertFalse(com.chesstutor.app.viewmodel.AppViewModel.isSolutionLegalInFen(startFen, "a1a8"))
        assertFalse(com.chesstutor.app.viewmodel.AppViewModel.isSolutionLegalInFen(startFen, null))
        assertFalse(com.chesstutor.app.viewmodel.AppViewModel.isSolutionLegalInFen(startFen, ""))
    }

    @Test
    fun capturesFromMoveHistory_emptyOnCustomPositionWithNoMoves_andAccurateAlongPlayedMoves() {
        val emptyCaptures = com.chesstutor.app.ui.arena.calculateCapturesFromMoveHistory(emptyList())
        assertEquals("", emptyCaptures.whiteCapturedPieces)
        assertEquals("", emptyCaptures.blackCapturedPieces)
        assertEquals(0, emptyCaptures.userAdvantage)
        assertEquals(0, emptyCaptures.opponentAdvantage)

        // 1. e4 d5 2. exd5 (White captures Black pawn on d5)
        val scandinavianCaptures = com.chesstutor.app.ui.arena.calculateCapturesFromMoveHistory(
            listOf("e2e4", "d7d5", "e4d5")
        )
        assertTrue(scandinavianCaptures.blackCapturedPieces.contains("♟"))
        assertEquals(1, scandinavianCaptures.userAdvantage)
        assertEquals(0, scandinavianCaptures.opponentAdvantage)
    }

    @Test
    fun pgnSanTokenizer_stripsCommentsVariationsAndNags_andFlagsIncompleteOnCorruptToken() {
        val client = com.chesstutor.app.data.network.RatingApiClient()
        val annotatedPgn = """
            [Event "Live Chess"]
            [Result "1-0"]
            1. e4 { [%clk 0:09:59] } 1... e5 $1 (1... c5 2. Nf3) 2. Nf3! Nc6? 3. Bb5 Nf6 4. 0-0 1-0
        """.trimIndent()
        val cleanStream = client.extractMovesFromPgn(annotatedPgn)
        val parsed = client.convertSanStreamToUciWithStatus(cleanStream)
        assertFalse("Valid annotated PGN should not be flagged incomplete", parsed.incomplete)
        assertEquals("e2e4 e7e5 g1f3 b8c6 f1b5 g8f6 e1g1", parsed.uciMoves)

        val corruptStream = "1. e4 e5 2. INVALID_TOKEN Nc6 1-0"
        val corruptParsed = client.convertSanStreamToUciWithStatus(corruptStream)
        assertTrue("Corrupt token must flag incomplete = true", corruptParsed.incomplete)
        assertEquals("e2e4 e7e5", corruptParsed.uciMoves)
    }

    @Test
    fun reviewStatsCalculator_separatesAccountGamesFromArenaBotGames_andSplitsOpeningsYouPlayVsFace() {
        val arenaBotGame = GameRecord(
            id = "arena_1",
            dateMillis = 1_700_000_000_000L,
            botName = "Wayne",
            botRating = 600,
            userColor = "white",
            result = "1-0",
            pgn = "1. e4 e5",
            moveCount = 20,
            finalFen = ChessPosition.STARTING_FEN,
            uciMoves = "e2e4 e7e5",
            source = com.chesstutor.app.data.model.GameSource.ARENA
        )
        // User plays White against King's Indian Defense (an opening Black chooses -> Openings You Face)
        val accountKidFaced = GameRecord(
            id = "chesscom_101",
            dateMillis = 1_700_000_010_000L,
            botName = "GM_Opponent",
            botRating = 1850,
            userColor = "white",
            result = "0-1",
            pgn = "[ECO \"E60\"]\n[Opening \"King's Indian Defense\"]\n1. d4 Nf6 2. c4 g6",
            moveCount = 30,
            finalFen = ChessPosition.STARTING_FEN,
            uciMoves = "d2d4 g8f6 c2c4 g7g6",
            source = com.chesstutor.app.data.model.GameSource.CHESS_COM
        )
        // User plays Black in Sicilian Defense (an opening Black chooses -> Openings You Play)
        val accountSicilianPlayed = GameRecord(
            id = "chesscom_102",
            dateMillis = 1_700_000_020_000L,
            botName = "Club_Player",
            botRating = 1800,
            userColor = "black",
            result = "0-1",
            pgn = "[ECO \"B20\"]\n[Opening \"Sicilian Defense\"]\n1. e4 c5",
            moveCount = 28,
            finalFen = ChessPosition.STARTING_FEN,
            uciMoves = "e2e4 c7c5",
            source = com.chesstutor.app.data.model.GameSource.CHESS_COM
        )

        val stats = ReviewStatsCalculator.compute(
            games = listOf(arenaBotGame, accountKidFaced, accountSicilianPlayed),
            reviews = emptyList(),
            accountOnly = true
        )

        // Account stats exclude the arena bot game
        assertEquals(2, stats.accountGameCount)
        assertEquals(1, stats.arenaGameCount)
        assertEquals(2, stats.totalGames)
        assertEquals(1, stats.wins)
        assertEquals(1, stats.losses)

        // Sicilian as Black is in openingsPlayed; King's Indian as White is in openingsFaced
        assertTrue(stats.openingsPlayed.any { it.family.contains("Sicilian") })
        assertTrue(stats.openingsFaced.any { it.family.contains("King's Indian") })
    }
}
