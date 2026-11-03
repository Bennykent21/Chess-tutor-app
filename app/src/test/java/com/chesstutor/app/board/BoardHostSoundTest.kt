package com.chesstutor.app.board

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.Position
import com.chesstutor.app.design.CtTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A puzzle's or a lesson's own moves sound as they slide; the player's are sounded once. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w400dp-h900dp-xxhdpi")
class BoardHostSoundTest {

    @get:Rule
    val compose = createComposeRule()

    private val feedback = mutableListOf<BoardFeedback>()

    /** Both sides can move; [reply] plays a move the way a puzzle plays its answer. */
    private class ScriptedSession : BoardSession {
        private val flow = MutableStateFlow(BoardSessionState(Position.initial()))
        override val state: StateFlow<BoardSessionState> = flow

        override fun onMove(move: Move): BoardFeedback {
            val before = flow.value.position
            reply(move.uci)
            return MoveEffects.feedbackFor(before, move, flow.value.position)
        }

        fun reply(uci: String) {
            val now = flow.value
            val move = LegalMoveGenerator.generateLegalMoves(now.position).first { it.uci == uci }
            val next = LegalMoveGenerator.makeMove(now.position, move)
            flow.value = BoardSessionState(next, previous = now.position, lastMove = move, version = now.version + 1, animateLastMove = true, start = now.start, moves = now.moves + move)
        }
    }

    private fun show(session: BoardSession, soundsOtherMoves: Boolean) {
        compose.setContent {
            CtTheme(reduceMotion = true) {
                BoardHost(
                    session = session,
                    theme = BoardTheme(),
                    modifier = Modifier.width(320.dp),
                    onFeedback = { feedback += it },
                    soundsOtherMoves = soundsOtherMoves,
                )
            }
        }
        compose.waitForIdle()
    }

    private fun tap(description: String) =
        compose.onNode(hasContentDescription(description, substring = true)).performSemanticsAction(SemanticsActions.OnClick)

    @Test
    fun aReplySoundsAndThePlayersMoveSoundsOnce() {
        val session = ScriptedSession()
        show(session, soundsOtherMoves = true)
        session.reply("e2e4")
        compose.waitForIdle()
        assertEquals(listOf(BoardFeedback(BoardSound.MOVE, HapticPattern.TICK)), feedback)

        tap("e7, black pawn")
        tap("e5, empty")
        compose.waitForIdle()
        assertEquals("the player's move is sounded by its own feedback only", 2, feedback.size)
    }

    @Test
    fun withoutTheOptionOnlyThePlayersMovesSound() {
        val session = ScriptedSession()
        show(session, soundsOtherMoves = false)
        session.reply("e2e4")
        compose.waitForIdle()
        assertEquals(emptyList<BoardFeedback>(), feedback)
    }

    @Test
    fun theMoveOnTheBoardWhenItOpensIsNotSounded() {
        val session = ScriptedSession().apply { reply("e2e4") }
        show(session, soundsOtherMoves = true)
        assertEquals(emptyList<BoardFeedback>(), feedback)
    }
}
