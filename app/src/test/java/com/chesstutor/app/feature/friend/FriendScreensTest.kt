package com.chesstutor.app.feature.friend

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.board.BoardTheme
import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.data.repository.InMemoryGameRepository
import com.chesstutor.app.data.settings.AppSettings
import com.chesstutor.app.design.Appearance
import com.chesstutor.app.design.AppTheme
import com.chesstutor.app.design.CtBoardScreenTags
import com.chesstutor.app.design.CtTheme
import com.chesstutor.app.domain.game.FriendSetup
import com.chesstutor.app.domain.game.PlayerClock
import com.chesstutor.app.domain.opening.OpeningTree
import com.chesstutor.app.feature.play.FriendGame
import com.chesstutor.app.feature.play.FriendPhase
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Play a friend (Phase 11): the setup page and the game on one phone, at phone size. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
class FriendScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())

    @After
    fun tearDown() = scope.cancel()

    private fun game(setup: FriendSetup, vararg ucis: String): FriendGame {
        val game = FriendGame(setup, { OpeningTree.EMPTY }, InMemoryGameRepository(), scope, clock = { 0L }).also { it.start() }
        for (uci in ucis) {
            game.session.onMove(LegalMoveGenerator.generateLegalMoves(game.session.game.position).first { it.uci == uci })
        }
        return game
    }

    private val noActions = FriendPlayActions(onFeedback = {}, onRematch = {}, onReview = {}, onAnalyse = { _, _ -> }, onBack = {})

    @Test
    fun theSetupPageOffersClocksStartsAndSeats() {
        var draft by mutableStateOf(FriendDraft(friendName = "Ada"))
        var started = false
        compose.setContent {
            CtTheme(reduceMotion = true) {
                FriendSetupScreen(
                    draft = draft, ownerName = "Sam", theme = BoardTheme(), onChange = { draft = it(draft) },
                    onPickOpening = {}, onSetPosition = {}, onStart = { started = true }, onBack = {},
                )
            }
        }
        compose.onNodeWithText("Play a friend").assertExists()
        compose.onNodeWithText("Sam").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/friend_setup.png")

        compose.onNodeWithText("3 | 2").performClick()
        assertEquals(PlayerClock.of(3, 2), draft.clock)
        compose.onNodeWithText("Opening").performClick()
        compose.onNodeWithText("Choose an opening").assertExists()
        compose.onNodeWithText("Start").assertIsNotEnabled()
        compose.onNodeWithText("Normal").performClick()
        compose.onNodeWithText("Start").assertIsEnabled().performClick()
        assertEquals(true, started)
    }

    @Test
    fun theSetupPageInTheDarkWithTimeOdds() {
        compose.setContent {
            CtTheme(theme = AppTheme.PARCHMENT, appearance = Appearance.DARK, reduceMotion = true) {
                FriendSetupScreen(
                    draft = FriendDraft(friendName = "Ada", ownerColor = PieceColor.BLACK, friendClock = PlayerClock.of(3, 2), start = FriendStart.POSITION),
                    ownerName = "Sam", theme = BoardTheme(), onChange = {}, onPickOpening = {}, onSetPosition = {}, onStart = {}, onBack = {},
                )
            }
        }
        compose.onNodeWithText("A different clock for Ada").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/friend_setup_dark.png")
    }

    @Test
    fun theBoardStaysPutFromTheFirstMoveToTheResult() {
        val setup = FriendSetup("Sam", "Ada", whiteClock = PlayerClock.of(10), blackClock = PlayerClock.of(10))
        val g = game(setup, "e2e4", "e7e5", "g1f3", "b8c6", "f1c4")
        compose.setContent {
            CtTheme(reduceMotion = true) { FriendPlayScreen(g, AppSettings(), noActions) }
        }
        compose.onNodeWithText("Ada").assertExists()
        compose.onNodeWithText("Sam").assertExists()
        val board = compose.onNodeWithTag(CtBoardScreenTags.BOARD).getBoundsInRoot()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/friend_game.png")

        compose.onNodeWithText("Seats").performClick()
        compose.waitForIdle()
        assertEquals(board, compose.onNodeWithTag(CtBoardScreenTags.BOARD).getBoundsInRoot())
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/friend_game_side_by_side.png")

        compose.onNodeWithText("Resign").performClick()
        compose.onNodeWithText("Ada resigns?").assertExists()
        g.resign()
        compose.waitForIdle()
        assertEquals(FriendPhase.OVER, g.state.value.phase)
        compose.onNodeWithText("Sam won").assertExists()
        compose.onNodeWithText("Rematch").assertExists()
        assertEquals(board, compose.onNodeWithTag(CtBoardScreenTags.BOARD).getBoundsInRoot())
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/friend_result.png")
    }
}
