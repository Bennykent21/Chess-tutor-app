package com.chesstutor.app.board

import com.chesstutor.app.core.Move
import com.chesstutor.app.core.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisTreeTest {

    private fun AnalysisTree.playAll(vararg uci: String) = uci.forEach { assertTrue(it, play(Move.fromUci(it))) }

    /** The notation as plain text, for readable assertions. */
    private fun AnalysisTree.text(): String = notation().joinToString(" ") { token ->
        when (token) {
            is NotationToken.MoveToken -> listOfNotNull(token.numberLabel, token.san).joinToString(" ")
            is NotationToken.VariationStart -> "("
            is NotationToken.VariationEnd -> ")"
        }
    }.replace("( ", "(").replace(" )", ")")

    @Test
    fun aMoveFromAnEarlierPositionBecomesAVariation() {
        val tree = AnalysisTree()
        tree.playAll("e2e4", "e7e5", "g1f3", "b8c6")
        repeat(2) { tree.back() }
        tree.playAll("f1c4", "g8f6")
        assertEquals("1. e4 e5 2. Nf3 (2. Bc4 Nf6) 2... Nc6", tree.text())
    }

    @Test
    fun playingAMoveThatAlreadyExistsFollowsItInsteadOfDuplicating() {
        val tree = AnalysisTree()
        tree.playAll("e2e4", "e7e5")
        tree.toStart()
        tree.playAll("e2e4")
        assertEquals(1, tree.root.children.size)
        assertTrue(tree.forward())
        assertEquals("e5", tree.current.san)
    }

    @Test
    fun illegalMovesChangeNothing() {
        val tree = AnalysisTree()
        assertFalse(tree.play(Move.fromUci("e2e5")))
        assertTrue(tree.current.isRoot)
        assertEquals("", tree.text())
    }

    @Test
    fun stepAndJumpThroughTheTree() {
        val tree = AnalysisTree()
        tree.playAll("d2d4", "d7d5", "c2c4")
        tree.toStart()
        assertFalse(tree.back())
        tree.toEnd()
        assertEquals("c4", tree.current.san)
        val d5 = tree.current.parent!!.id
        assertTrue(tree.goTo(d5))
        assertEquals(listOf("d2d4", "d7d5"), tree.movesToCurrent().map { it.uci })
        assertFalse(tree.goTo(999))
    }

    @Test
    fun promotingASideLineMakesItTheMainLine() {
        val tree = AnalysisTree()
        tree.playAll("e2e4", "e7e5")
        tree.back()
        tree.playAll("c7c5")
        tree.promote(tree.current.id)
        assertEquals("1. e4 c5 (1... e5)", tree.text())
    }

    @Test
    fun deletingALineMovesTheBoardOutOfIt() {
        val tree = AnalysisTree()
        tree.playAll("e2e4", "e7e5")
        tree.back()
        tree.playAll("c7c5", "g1f3")
        val c5 = tree.current.parent!!.id
        tree.delete(c5)
        assertEquals("1. e4 e5", tree.text())
        assertEquals("e4", tree.current.san)
        assertTrue(tree.node(c5) == null)
    }

    @Test
    fun takeBackRemovesTheLastMoveOnlyWhenNothingFollowsIt() {
        val tree = AnalysisTree()
        tree.playAll("e2e4", "e7e5")
        assertTrue(tree.takeBack())
        assertEquals("1. e4", tree.text())
        tree.playAll("e7e5")
        tree.back()
        assertTrue(tree.takeBack())
        assertEquals("a move with a continuation is kept", "1. e4 e5", tree.text())
        assertTrue(tree.current.isRoot)
        assertFalse(tree.takeBack())
    }

    @Test
    fun startingWithBlackToMoveNumbersFromTheGivenMove() {
        val tree = AnalysisTree(Position.fromFen("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"))
        tree.playAll("c7c5", "g1f3")
        assertEquals("1... c5 2. Nf3", tree.text())
    }

    @Test
    fun nestedVariationsKeepTheirDepth() {
        val tree = AnalysisTree()
        tree.playAll("e2e4", "e7e5")
        tree.back()
        tree.playAll("c7c5", "g1f3")
        tree.back()
        tree.playAll("b1c3")
        assertEquals("1. e4 e5 (1... c5 2. Nf3 (2. Nc3))", tree.text())
        val depths = tree.notation().filterIsInstance<NotationToken.MoveToken>().associate { it.san to it.depth }
        assertEquals(mapOf("e4" to 0, "e5" to 0, "c5" to 1, "Nf3" to 1, "Nc3" to 2), depths)
    }
}
