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

/** The game review screen (step 5B). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h2200dp-xxhdpi")
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
    fun reviewShowsSummaryKeyMomentsAndPractice() {
        var practised: ReviewedMove? = null
        compose.setContent {
            CtTheme(reduceMotion = true) {
                GameReviewScreen(state = state(evals), settings = AppSettings(), engine = null, actions = ReviewActions(onPractise = { practised = it }))
            }
        }
        compose.onNodeWithText("Summary").assertExists()
        compose.onNodeWithText("The starting position. Step forward, or tap the graph to jump.").assertExists()
        compose.onNodeWithText("Key moments").assertExists()
        compose.onNodeWithText("3... Nf6").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/account_review.png")

        compose.onNodeWithText("Practise this mistake").performClick()
        assertEquals(6, practised?.ply)
        compose.onNodeWithText("Added to Mistakes review").assertExists()

        // The key moment jumps the board to the blunder.
        compose.onNodeWithText("3... Nf6").performClick()
        // Shown on the move verdict under the board and on the key moment.
        compose.onAllNodesWithText("Best was g6. Winning chances 45% → 0%.").assertCountEquals(2)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/account_review_blunder.png")
    }

    @Test
    fun aRunningAnalysisShowsProgressAndStops() {
        var stopped = false
        compose.setContent {
            CtTheme(reduceMotion = true) {
                GameReviewScreen(
                    state = state(evals.take(3), job = AnalysisJob(done = 3, total = 8, waiting = false)),
                    settings = AppSettings(),
                    engine = null,
                    actions = ReviewActions(onStop = { stopped = true }),
                )
            }
        }
        compose.onNodeWithText("Analysing position 3 of 8").assertExists()
        compose.onNodeWithContentDescription("Evaluation graph. Tap to jump to a move.").assertExists()
        compose.onNodeWithText("Stop").performClick()
        assertEquals(true, stopped)
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
    fun verdictsExplainEachQuality() {
        val review = GameReviewer.review(Position.STARTING_FEN, moves, evals)
        assertEquals("The best move.", verdictText(review.move(1)!!))
        assertEquals("Best was g6. Winning chances 45% → 0%.", verdictText(review.move(6)!!))
        assertEquals("91.5%", accuracyText(91.46))
        assertEquals("–", accuracyText(null))
    }
}
