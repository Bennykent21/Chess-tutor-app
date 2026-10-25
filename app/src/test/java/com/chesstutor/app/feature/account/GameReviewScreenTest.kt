package com.chesstutor.app.feature.account

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.core.Position
import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.data.settings.AppSettings
import com.chesstutor.app.design.CtTheme
import com.chesstutor.app.domain.account.GameLibrary
import com.chesstutor.app.domain.account.GameReviewer
import com.chesstutor.app.domain.account.PlyEval
import com.chesstutor.app.domain.account.ReviewedMove
import com.chesstutor.app.domain.opening.OpeningTree
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The game review: summary first, then the board walk-through (step 5B, redesigned in review R6). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1400dp-xxhdpi")
class GameReviewScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val moves = listOf("e2e4", "e7e5", "f1c4", "b8c6", "d1h5", "g8f6", "h5f7")

    /** The player had Black and fell for Scholar's mate. */
    private val game = GameLibrary.summarise(
        GameRecord(
            id = "bot_9", dateMillis = 1, botName = "Pip", botRating = 800, result = "1-0", pgn = "",
            moveCount = moves.size, userColor = "black", finalFen = "", uciMoves = moves.joinToString(" "), termination = "checkmate",
        ),
        OpeningTree.EMPTY,
    )

    private val evals = listOf(
        PlyEval(0, 20, null, "e2e4", listOf("e2e4", "e7e5")),
        PlyEval(1, 20, null, "e7e5"),
        PlyEval(2, 20, null, "g1f3"),
        PlyEval(3, 20, null, "g8f6"),
        PlyEval(4, 30, null, "d1h5"),
        PlyEval(5, 50, null, "g7g6"),
        PlyEval(6, null, 1, "h5f7"),
        GameReviewer.terminalEval(7, GameReviewer.positions(Position.STARTING_FEN, moves).last())!!,
    )

    private fun state(evals: List<PlyEval>, job: AnalysisJob? = null) = ReviewState(
        game = game,
        review = GameReviewer.review(Position.STARTING_FEN, moves, evals),
        job = job,
        evals = evals.associateBy { it.ply },
    )

    @Test
    fun summaryShowsTheVerdictPlayersCountsAndKeyMoments() {
        var practised: ReviewedMove? = null
        compose.setContent {
            CtTheme(reduceMotion = true) {
                GameReviewScreen(state = state(evals), settings = AppSettings(), engine = null, actions = ReviewActions(onPractise = { practised = it }))
            }
        }
        compose.onNodeWithText("The turning point was 3... Nf6. g6 would have kept you in the game.").assertExists()
        compose.onNodeWithText("Key moments").assertExists()
        compose.onNodeWithText("Start review").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/account_review.png")

        compose.onNodeWithText("Practise").performClick()
        assertEquals(6, practised?.ply)
        compose.onNodeWithText("Added").assertExists()
    }

    @Test
    fun theBoardWalkThroughCommentsOnEachMove() {
        compose.setContent {
            CtTheme(reduceMotion = true) {
                GameReviewScreen(state = state(evals), settings = AppSettings(), engine = null, actions = ReviewActions(), startPly = 6)
            }
        }
        compose.onNodeWithText("Nf6 is a blunder").assertExists()
        compose.onNodeWithText("It allows mate in 1, starting with Qxf7#. Best was g6.").assertExists()
        // The evaluation shows in the coach bubble and on the eval bar.
        compose.onAllNodesWithText("M1").assertCountEquals(2)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/account_review_blunder.png")

        compose.onNodeWithContentDescription("Next move").performClick()
        compose.onNodeWithText("Qxf7# is best").assertExists()
        compose.onNodeWithText("Checkmate!").assertExists()

        compose.onNodeWithText("Summary").performClick()
        compose.onNodeWithText("Start review").assertExists()
    }

    @Test
    fun showBestPlaysTheEnginesMoveAsASideLine() {
        compose.setContent {
            CtTheme(reduceMotion = true) {
                GameReviewScreen(state = state(evals), settings = AppSettings(), engine = null, actions = ReviewActions(), startPly = 6)
            }
        }
        compose.onNodeWithText("Show best").performClick()
        compose.onNodeWithText("A side line").assertExists()
        compose.onNodeWithText("Back to the game").performClick()
        compose.onNodeWithText("Nf6 is a blunder").assertExists()
    }

    @Test
    fun aRunningAnalysisFillsTheGraphWithoutAProgressCard() {
        compose.setContent {
            CtTheme(reduceMotion = true) {
                GameReviewScreen(
                    state = state(evals.take(3), job = AnalysisJob(done = 3, total = 8, waiting = false)),
                    settings = AppSettings(),
                    engine = null,
                    actions = ReviewActions(),
                )
            }
        }
        compose.onNodeWithText("Analysing position 3 of 8").assertDoesNotExist()
        compose.onNodeWithText("I'm going through your game move by move. The graph fills in as I go.").assertExists()
        compose.onNodeWithContentDescription("Evaluation graph. Tap to jump to a move.").assertExists()
    }

    @Test
    fun playStopsAtTheNextKeyMoment() {
        compose.setContent {
            CtTheme(reduceMotion = true) {
                GameReviewScreen(state = state(evals), settings = AppSettings(), engine = null, actions = ReviewActions(), startPly = 1)
            }
        }
        compose.onNodeWithContentDescription("Play to the next key moment").performClick()
        compose.mainClock.advanceTimeBy(10_000)
        compose.waitForIdle()
        // e5, Bc4, Nc6 and Qh5 pass; 3...Nf6 (a blunder) stops it.
        compose.onNodeWithText("Nf6 is a blunder").assertExists()
    }

    @Test
    fun keyMomentsAndShowBestRules() {
        val review = GameReviewer.review(Position.STARTING_FEN, moves, evals)
        assertEquals(false, canShowBest(review.move(1)!!))
        assertEquals("Bc4 loses nothing, so it counts as best", false, canShowBest(review.move(3)!!))
        assertEquals("a good move that was not the engine's", true, canShowBest(review.move(3)!!.copy(quality = com.chesstutor.app.domain.account.MoveQuality.GOOD)))
        assertEquals(true, canShowBest(review.move(6)!!))
        assertEquals(true, isKeyMoment("Qxf7#", null))
        assertEquals(true, isKeyMoment("O-O", null))
        assertEquals(true, isKeyMoment("Nf6", com.chesstutor.app.domain.account.MoveQuality.BLUNDER))
        assertEquals(false, isKeyMoment("Nf3", com.chesstutor.app.domain.account.MoveQuality.GOOD))
    }

    @Test
    fun anUnanalysedGameOffersAnalysis() {
        var asked = false
        compose.setContent {
            CtTheme(reduceMotion = true) {
                GameReviewScreen(state = state(emptyList()), settings = AppSettings(), engine = null, actions = ReviewActions(onAnalyse = { asked = true }))
            }
        }
        compose.onNodeWithText("Analyse this game").assertExists()
        compose.onNodeWithText("Analyse").performClick()
        assertEquals(true, asked)
    }

    @Test
    fun backToTheGameFindsTheBranchingMove() {
        // Notation: 1 (main, ply 1), 2 (main, ply 2), 9 (side line), 3 (main, ply 3).
        assertEquals(2, backToGamePly(mainIds = listOf(0, 1, 2, 3), notationIds = listOf(1, 2, null, 9, null, 3), current = 9))
        assertEquals(0, backToGamePly(listOf(0, 1), listOf(1), current = 42))
        assertEquals("91.5%", accuracyText(91.46))
        assertEquals("–", accuracyText(null))
    }
}
