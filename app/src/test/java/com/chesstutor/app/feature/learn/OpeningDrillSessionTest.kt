package com.chesstutor.app.feature.learn

import com.chesstutor.app.board.MoveEffects
import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.domain.opening.TrainerLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpeningDrillSessionTest {

    private fun OpeningDrillSession.play(uci: String) =
        onMove(LegalMoveGenerator.generateLegalMoves(state.value.position).first { it.uci == uci })

    @Test
    fun aCleanRunAsWhiteWithTheBookAnswering() {
        val session = OpeningDrillSession(TrainerLine("Italian Game", listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1c4"), PieceColor.WHITE))
        assertFalse(session.replyDue)
        assertEquals(PieceColor.WHITE, session.state.value.movable)
        session.play("e2e4")
        assertTrue(session.replyDue)
        assertNull("the book's turn: the player cannot move", session.state.value.movable)
        assertTrue(session.playReply())
        session.play("g1f3")
        session.playReply()
        assertEquals(2 to 3, session.progress)
        assertEquals(MoveEffects.CORRECT, session.play("f1c4"))
        assertTrue(session.isFinished)
        assertTrue(session.isClean)
    }

    @Test
    fun aWrongMoveIsRefusedAndTheAnswerShown() {
        val session = OpeningDrillSession(TrainerLine("Sicilian Defense", listOf("e2e4", "c7c5"), PieceColor.BLACK))
        assertTrue("White's book move comes first", session.replyDue)
        session.playReply()
        val before = session.state.value.position
        assertEquals(MoveEffects.WRONG, session.play("e7e5"))
        assertEquals("the board did not change", before, session.state.value.position)
        assertEquals("c7c5", session.shownAnswer?.uci)
        assertEquals(1, session.slips)
        session.play("c7c5")
        assertTrue(session.isFinished)
        assertFalse(session.isClean)
        assertNull(session.shownAnswer)
    }

    @Test
    fun showMeCountsAsASlipOnce() {
        val session = OpeningDrillSession(TrainerLine("Queen's Pawn", listOf("d2d4"), PieceColor.WHITE))
        assertEquals(Move.fromUci("d2d4").uci, session.showAnswer()?.uci)
        session.showAnswer()
        assertEquals(1, session.slips)
    }
}
