package com.chesstutor.app.domain.puzzle

import com.chesstutor.app.domain.ChessPosition
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzleLogicTest {

    // 1.f3 e5 and White is about to play 2.g4; Black then mates with Qh4.
    private val foolsMate = Puzzle(
        id = "fool",
        fen = "rnbqkbnr/pppp1ppp/8/4p3/8/5P2/PPPPP1PP/RNBQKBNR w KQkq - 0 2",
        moves = listOf("g2g4", "d8h4"),
        rating = 500, primaryTheme = "mateIn1", themes = setOf("mateIn1", "short")
    )

    // 1.e4 e5 2.Bc4, Black plays ...Nc6, White Qh5, Black ...Nf6, White Qxf7#.
    private val scholars = Puzzle(
        id = "scholar",
        fen = "rnbqkbnr/pppp1ppp/8/4p3/2B1P3/8/PPPP1PPP/RNBQK1NR b KQkq - 1 2",
        moves = listOf("b8c6", "d1h5", "g8f6", "h5f7"),
        rating = 800, primaryTheme = "mateIn2", themes = setOf("mateIn2")
    )

    // After ...a6 both rooks give back-rank mate (Rb8# listed, Rc8# also mates).
    private val tworooks = Puzzle(
        id = "rooks",
        fen = "6k1/p4ppp/8/8/8/8/8/1RR3K1 b - - 0 1",
        moves = listOf("a7a6", "b1b8"),
        rating = 600, primaryTheme = "backRankMate", themes = setOf("backRankMate")
    )

    private fun start(p: Puzzle) = PuzzleSession.start(p)!!

    // ---------- parsing ----------

    @Test
    fun parser_readsAValidLine() {
        val p = PuzzleParser.parseLine("ab12\tfenfenfen a b c d\te2e4 e7e5 g1f3\t1234\tfork\tfork short\t97")!!
        assertEquals("ab12", p.id)
        assertEquals(listOf("e2e4", "e7e5", "g1f3"), p.moves)
        assertEquals(1234, p.rating)
        assertEquals("fork", p.primaryTheme)
        assertEquals(setOf("fork", "short"), p.themes)
        assertEquals(97, p.popularity)
        assertEquals(1, p.solverMoveCount)
    }

    @Test
    fun parser_rejectsMalformedLines() {
        val fen = "8/8/8/8/8/8/8/8 w - - 0 1"
        assertNull(PuzzleParser.parseLine(""))
        assertNull(PuzzleParser.parseLine("only\tthree\tcols"))
        assertNull(PuzzleParser.parseLine("id\t$fen\te2e4\t900\tfork\tfork\t50"))            // one move only
        assertNull(PuzzleParser.parseLine("id\t$fen\te2e4 zz99\t900\tfork\tfork\t50"))       // bad UCI
        assertNull(PuzzleParser.parseLine("id\t$fen\te2e4 e7e5\tabc\tfork\tfork\t50"))       // bad rating
        assertNull(PuzzleParser.parseLine("id\tshort\te2e4 e7e5\t900\tfork\tfork\t50"))      // bad FEN
    }

    @Test
    fun pack_skipsBadAndDuplicateLines_andSortsByRating() {
        val fen = "8/8/8/8/8/8/8/8 w - - 0 1"
        val text = listOf(
            "b\t$fen\te2e4 e7e5\t1500\tpin\tpin\t60",
            "a\t$fen\te2e4 e7e5\t700\tfork\tfork\t80",
            "garbage line",
            "a\t$fen\te2e4 e7e5\t700\tfork\tfork\t80"            // duplicate id
        ).joinToString("\n")
        val pack = PuzzlePack.fromText(text)
        assertEquals(listOf("a", "b"), pack.puzzles.map { it.id })
        assertEquals(2, pack.skippedLines)
        assertEquals("b", pack.get("b")!!.id)
        assertEquals(1, pack.countFor("fork"))
    }

    @Test
    fun pack_loadsFromGzipStream() {
        val fen = "8/8/8/8/8/8/8/8 w - - 0 1"
        val bytes = ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write("p1\t$fen\te2e4 e7e5\t900\tfork\tfork\t70\n".toByteArray()) }
        }.toByteArray()
        val pack = PuzzlePack.fromGzipStream(ByteArrayInputStream(bytes))
        assertEquals(1, pack.size)
        assertEquals("fork", pack.themes.single())
        assertTrue(PuzzlePack.EMPTY.isEmpty)
    }

    @Test
    fun themes_haveReadableNames() {
        assertEquals("Mate in 1", PuzzleThemes.displayName("mateIn1"))
        assertEquals("Back-rank mate", PuzzleThemes.displayName("backRankMate"))
        assertEquals("Some new theme", PuzzleThemes.displayName("someNewTheme"))
    }

    // ---------- solving ----------

    @Test
    fun session_playsOpeningMoveAndOrientsToSolver() {
        val s = start(foolsMate)
        assertEquals("g2g4", s.openingMove)
        assertEquals("g2g4", s.lastMove)
        assertEquals('b', s.solverColor)
        assertEquals(PuzzleStatus.SOLVING, s.status)
        assertNull(s.outcome())
    }

    @Test
    fun session_correctFinalMoveSolvesCleanly() {
        val s = start(foolsMate)
        val r = s.submit("d8h4")
        assertEquals(PuzzleMoveResult.Correct(reply = null, solved = true), r)
        assertEquals(PuzzleStatus.SOLVED, s.status)
        assertTrue(s.outcome()!!.clean)
        assertTrue(ChessPosition(s.fen).isCheckmate)
    }

    @Test
    fun session_multiMovePuzzleAutoPlaysOpponentReply() {
        val s = start(scholars)
        assertEquals(PuzzleMoveResult.Correct(reply = "g8f6", solved = false), s.submit("d1h5"))
        assertEquals(PuzzleStatus.SOLVING, s.status)
        assertEquals("g8f6", s.lastMove)
        assertEquals(PuzzleMoveResult.Correct(reply = null, solved = true), s.submit("h5f7"))
        assertTrue(s.outcome()!!.solved)
    }

    @Test
    fun session_wrongMoveLeavesBoardAndCountsMistake() {
        val s = start(foolsMate)
        val before = s.fen
        assertEquals(PuzzleMoveResult.Wrong(1), s.submit("a7a6"))
        assertEquals(before, s.fen)
        assertEquals(PuzzleStatus.SOLVING, s.status)
        assertEquals(PuzzleMoveResult.Wrong(2), s.submit("h7h6"))
        s.submit("d8h4")
        val o = s.outcome()!!
        assertTrue(o.solved)
        assertFalse(o.clean)
        assertEquals(2, o.mistakes)
    }

    @Test
    fun session_illegalMoveIsRejectedWithoutPenalty() {
        val s = start(foolsMate)
        assertEquals(PuzzleMoveResult.Illegal, s.submit("a8a1"))
        assertEquals(PuzzleMoveResult.Illegal, s.submit("nonsense"))
        assertEquals(0, s.mistakes)
    }

    @Test
    fun session_acceptsAnyCheckmateNotJustTheListedOne() {
        val s = start(tworooks)
        assertEquals(PuzzleMoveResult.Correct(reply = null, solved = true), s.submit("c1c8"))
        assertTrue(s.outcome()!!.clean)
    }

    @Test
    fun session_nonMatingAlternativeIsWrong() {
        val s = start(tworooks)
        assertEquals(PuzzleMoveResult.Wrong(1), s.submit("g1f1"))   // legal, but not mate
    }

    @Test
    fun session_hintsEscalateAndCostCleanSolve() {
        val s = start(tworooks)
        assertEquals(PuzzleHint.Piece("b1"), s.hint())
        assertEquals(PuzzleHint.Move("b1b8"), s.hint())
        s.submit("b1b8")
        val o = s.outcome()!!
        assertTrue(o.solved)
        assertFalse(o.clean)
        assertEquals(2, o.hintsUsed)
        assertNull(s.hint())
    }

    @Test
    fun session_revealReturnsRemainingSolutionAndEndsPuzzle() {
        val s = start(scholars)
        s.submit("d1h5")
        assertEquals(listOf("h5f7"), s.reveal())
        assertEquals(PuzzleStatus.REVEALED, s.status)
        val o = s.outcome()!!
        assertFalse(o.solved)
        assertTrue(o.revealed)
        assertEquals(PuzzleMoveResult.NotSolving, s.submit("h5f7"))
        assertTrue(s.reveal().isEmpty())
    }

    @Test
    fun session_start_skipsUnusablePuzzles() {
        assertNull(PuzzleSession.start(foolsMate.copy(fen = "not a fen")))
        assertNull(PuzzleSession.start(foolsMate.copy(moves = listOf("a1a8", "d8h4"))))   // illegal opening move
        assertNull(PuzzleSession.start(foolsMate.copy(moves = listOf("g2g4", "a1a8"))))   // unplayable solution
        assertNotNull(PuzzleSession.start(foolsMate))
    }

    // ---------- selection & rating ----------

    private fun packOf(vararg specs: Triple<String, Int, String>): PuzzlePack {
        val fen = "8/8/8/8/8/8/8/8 w - - 0 1"
        return PuzzlePack.fromText(specs.joinToString("\n") { (id, rating, theme) ->
            "$id\t$fen\te2e4 e7e5\t$rating\t$theme\t$theme\t80"
        })
    }

    @Test
    fun selector_prefersPuzzlesNearTheRating() {
        val pack = packOf(Triple("low", 600, "fork"), Triple("mid", 1500, "fork"), Triple("high", 2400, "fork"))
        val sel = PuzzleSelector(pack, Random(1))
        repeat(20) { assertEquals("mid", sel.next(userRating = 1480)!!.id) }
    }

    @Test
    fun selector_widensWindowAndHonoursThemeAndExclusions() {
        val pack = packOf(Triple("a", 600, "fork"), Triple("b", 2400, "pin"), Triple("c", 2300, "pin"))
        val sel = PuzzleSelector(pack, Random(2))
        assertEquals("a", sel.next(userRating = 1500, theme = "fork")!!.id)       // far away, window widens
        assertEquals("b", sel.next(userRating = 2400, theme = "pin", excludeIds = setOf("c"))!!.id)
        assertNull(sel.next(userRating = 1500, theme = "pin", excludeIds = setOf("b", "c")))
        assertNull(sel.next(userRating = 1500, theme = "skewer"))
    }

    @Test
    fun rating_movesTowardsResultAndStaysInBounds() {
        assertEquals(1516, PuzzleRating.update(1500, 1500, solved = true))
        assertEquals(1484, PuzzleRating.update(1500, 1500, solved = false))
        assertTrue(PuzzleRating.update(1200, 1800, solved = true) - 1200 > 16)   // beating a harder puzzle pays more than an even one
        assertEquals(PuzzleRating.MIN, PuzzleRating.update(PuzzleRating.MIN, 3000, solved = false))
        assertEquals(PuzzleRating.MAX, PuzzleRating.update(PuzzleRating.MAX, 400, solved = true))
    }
}
