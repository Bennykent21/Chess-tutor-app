package com.chesstutor.app.feature.train

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.board.BoardTheme
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.design.Appearance
import com.chesstutor.app.design.AppTheme
import com.chesstutor.app.design.CtTheme
import com.chesstutor.app.domain.repertoire.LevelProgress
import com.chesstutor.app.domain.repertoire.RepertoireIndex
import com.chesstutor.app.domain.repertoire.TestRepertoire
import com.chesstutor.app.feature.learn.RepertoireBookUi
import com.chesstutor.app.feature.learn.RepertoireState
import com.chesstutor.app.feature.learn.TrainerState
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Train tab (Phase 12, mock-up round 3) at phone size. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
class TrainScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun ui(book: com.chesstutor.app.domain.repertoire.RepertoireBook, due: Int, readiness: Double) = RepertoireBookUi(
        book = book, index = RepertoireIndex(book), levels = LevelProgress(book.id), mastery = emptyMap(),
        due = due, tricky = 0, fresh = 3, chart = null, readiness = readiness,
    )

    private val state = RepertoireState(
        loading = false,
        books = listOf(ui(TestRepertoire.italian(), 8, 0.42), ui(TestRepertoire.caroKann(), 4, 0.18)),
        dueSamples = listOf(
            "r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R b KQkq - 3 3" to PieceColor.WHITE,
            "rnbqkbnr/pp2pppp/2p5/3pP3/3P4/8/PPP2PPP/RNBQKBNR b KQkq - 0 3" to PieceColor.BLACK,
        ),
    )

    @Test
    fun trainLeadsWithTodayThenTheSidesAndOpensThem() {
        var today = false
        var side: PieceColor? = null
        var battle = false
        compose.setContent {
            CtTheme(reduceMotion = true) {
                TrainScreen(state, TrainerState(loading = false), BoardTheme(), TrainActions(onToday = { today = true }, onSide = { side = it }, onVsStockfish = { battle = true }))
            }
        }
        compose.onNodeWithText("12").assertExists()
        compose.onNodeWithText("About 6 minutes").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/train_home.png")
        compose.onNodeWithText("Start").performClick()
        assertEquals(true, today)
        compose.onNodeWithText("As Black").performClick()
        assertEquals(PieceColor.BLACK, side)
        compose.onNodeWithText("Openings vs Stockfish").performClick()
        assertEquals(true, battle)
    }

    @Test
    fun trainInTheDarkWithNothingDue() {
        compose.setContent {
            CtTheme(theme = AppTheme.SLATE, appearance = Appearance.DARK, reduceMotion = true) {
                TrainScreen(state.copy(books = state.books.map { it.copy(due = 0) }, dueSamples = emptyList()), TrainerState(loading = false), BoardTheme(), TrainActions({}, {}, {}))
            }
        }
        compose.onNodeWithText("Nothing due").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/train_home_dark.png")
    }

    @Test
    fun aboutThirtySecondsAPosition() {
        assertEquals(1, minutesFor(1))
        assertEquals(6, minutesFor(12))
    }
}
