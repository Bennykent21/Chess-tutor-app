package com.chesstutor.app.feature.puzzles

import com.chesstutor.app.board.BoardSound
import com.chesstutor.app.board.MoveEffects
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.domain.puzzle.Puzzle
import com.chesstutor.app.domain.puzzle.PuzzleHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzleBoardSessionTest {

    // 1.e4 e5 2.Bc4: Black plays ...Nc6 (set-up), White Qh5, Black ...Nf6, White Qxf7#.
    private val scholars = Puzzle(
        id = "scholar",
        fen = "rnbqkbnr/pppp1ppp/8/4p3/2B1P3/8/PPPP1PPP/RNBQK1NR b KQkq - 1 2",
        moves = listOf("b8c6", "d1h5", "g8f6", "h5f7"),
        rating = 800, primaryTheme = "mateIn2", themes = setOf("mateIn2"),
    )

    private fun session(p: Puzzle = scholars) = PuzzleBoardSession.start(p)!!
    private fun m(uci: String) = Move.fromUci(uci)

    @Test
    fun theOpponentsSetUpMoveSlidesInThenThePlayerMayMove() {
        val s = session()
        assertEquals(PieceColor.WHITE, s.solverColor)
        assertEquals(Position.fromFen(scholars.fen), s.state.value.position)
        assertNull("nobody moves before the set-up move", s.state.value.movable)

        s.playOpening()
        val state = s.state.value
        assertEquals(m("b8c6").uci, state.lastMove?.uci)
        assertTrue(state.animateLastMove)
        assertEquals(PieceColor.WHITE, state.movable)
        assertEquals(s.solvingFen, state.position.toFen())
    }

    @Test
    fun aCorrectMoveWaitsForTheReplyThenTheMateSolves() {
        val s = session()
        val outcomes = mutableListOf<PuzzleMoveOutcome>()
        s.onOutcome = { outcomes += it }
        s.playOpening()

        assertEquals(BoardSound.MOVE, s.onMove(m("d1h5")).sound)
        assertEquals(PuzzleMoveOutcome.Continue, outcomes.last())
        assertNull("the board waits for the reply", s.state.value.movable)
        assertTrue(s.playReply())
        assertEquals("g8f6", s.state.value.lastMove?.uci)
        assertEquals(PieceColor.WHITE, s.state.value.movable)

        assertEquals(MoveEffects.CORRECT, s.onMove(m("h5f7")))
        assertEquals(PuzzleMoveOutcome.Solved(alternative = false), outcomes.last())
        assertTrue(s.isFinished)
        assertTrue(s.outcome()!!.clean)
    }

    /** Regression: "Analyse" opened the final position because this followed the game. */
    @Test
    fun theStartingPositionStaysPutWhileThePuzzleIsPlayed() {
        val s = session()
        s.playOpening()
        val start = s.state.value.position.toFen()
        assertEquals(start, s.solvingFen)
        s.onMove(m("d1h5"))
        s.playReply()
        s.onMove(m("h5f7"))
        assertTrue(s.isFinished)
        assertEquals("still the puzzle's starting position", start, s.solvingFen)
        // The solution replays from there, which is what the analysis board does.
        var position = Position.fromFen(s.solvingFen)
        scholars.moves.drop(1).forEach { uci ->
            val legal = com.chesstutor.app.core.LegalMoveGenerator.generateLegalMoves(position).first { it.uci == uci }
            position = com.chesstutor.app.core.LegalMoveGenerator.makeMove(position, legal)
        }
    }

    @Test
    fun aWrongMoveLeavesTheBoardAndBuzzes() {
        val s = session()
        s.playOpening()
        val before = s.state.value
        assertEquals(MoveEffects.WRONG, s.onMove(m("a2a3")))
        assertEquals(PuzzleMoveOutcome.Wrong(m("a2a3")), s.lastOutcome)
        assertEquals(before, s.state.value)
        assertEquals(1, s.mistakes)
    }

    @Test
    fun afterAGreatAlternativeTheMainLineCanBeReplayed() {
        val s = session(scholars.copy(alternatives = listOf(setOf("d1f3"))))
        s.playOpening()
        val solving = s.state.value.position
        s.onMove(m("d1f3"))
        assertEquals(PuzzleMoveOutcome.Solved(alternative = true), s.lastOutcome)

        s.prepareMainLine()
        assertEquals("back to before the alternative", solving, s.state.value.position)
        val played = generateSequence { if (s.stepLine()) s.state.value.lastMove?.uci else null }.toList()
        assertEquals(listOf("d1h5", "g8f6", "h5f7"), played)
    }

    @Test
    fun revealPlaysTheRestOfTheSolution() {
        val s = session()
        s.playOpening()
        s.onMove(m("d1h5"))
        s.playReply()
        s.reveal()
        assertNull(s.state.value.movable)
        assertTrue(s.stepLine())
        assertEquals("h5f7", s.state.value.lastMove?.uci)
        assertFalse(s.stepLine())
        assertTrue(s.outcome()!!.revealed)
    }

    @Test
    fun hintsGoFromThePieceToTheMove() {
        val s = session()
        s.playOpening()
        assertEquals(PuzzleHint.Piece("d1"), s.hint())
        assertEquals(PuzzleHint.Move("d1h5"), s.hint())
        assertEquals(2, s.hintsUsed)
    }

    @Test
    fun unplayablePuzzlesAreRefused() {
        assertNull(PuzzleBoardSession.start(scholars.copy(moves = listOf("b8c6", "a1a8"))))
        assertNull(PuzzleBoardSession.start(scholars.copy(fen = "not a fen")))
    }
}
