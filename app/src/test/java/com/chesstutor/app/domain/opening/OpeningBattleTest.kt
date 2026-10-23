package com.chesstutor.app.domain.opening

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.repetitionKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class OpeningBattleTest {

    private val tree by lazy { File("src/main/assets/openings.tsv.gz").inputStream().use(OpeningTree::fromStream) }
    private val najdorf by lazy { tree.openings.first { it.name == "Sicilian Defense: Najdorf Variation" } }

    private fun keyAfter(vararg moves: String): String {
        var position = Position.initial()
        moves.forEach { uci -> position = LegalMoveGenerator.makeMove(position, LegalMoveGenerator.generateLegalMoves(position).first { it.uci == uci }) }
        return position.repetitionKey()
    }

    @Test
    fun exactRepliesFollowTheChosenLineOnly() {
        val plan = OpeningBattles.plan(tree, BattleChoice(najdorf, BattleMode.PLAY, exactReplies = true))
        assertEquals(najdorf, plan.target)
        assertEquals("Sicilian Defense: Najdorf Variation", plan.title)
        assertEquals(listOf("c7c5"), plan.repertoire.movesFor(PieceColor.BLACK, keyAfter("e2e4")).map { it.uci })
        assertEquals(listOf("g1f3"), plan.repertoire.movesFor(PieceColor.WHITE, keyAfter("e2e4", "c7c5")).map { it.uci })
    }

    @Test
    fun familyRepliesAllowEveryLineOfTheFamily() {
        val plan = OpeningBattles.plan(tree, BattleChoice(najdorf, BattleMode.PLAY, exactReplies = false))
        assertEquals("Sicilian Defense", plan.title)
        val whiteSecondMoves = plan.repertoire.movesFor(PieceColor.WHITE, keyAfter("e2e4", "c7c5")).map { it.uci }
        assertTrue("the Alapin and the Closed Sicilian are in the family", whiteSecondMoves.containsAll(listOf("g1f3", "c2c3", "b1c3")))
    }

    @Test
    fun surpriseMePicksAVariationOfTheSameFamily() {
        val sicilian = tree.openings.first { it.name == "Sicilian Defense" }
        val picks = (0 until 10).map { OpeningBattles.plan(tree, BattleChoice(sicilian, BattleMode.SURPRISE), Random(it)).target }
        assertTrue(picks.all { it.family == "Sicilian Defense" && it.moves.size > sicilian.moves.size })
        assertTrue("different seeds, different lines", picks.distinct().size > 1)
        assertNotEquals(sicilian, picks.first())
    }

    @Test
    fun theFamilyListIsSearchableByAnyLineName() {
        assertEquals(60, OpeningBattles.searchFamilies(tree, "").size)
        assertEquals("Sicilian Defense", OpeningBattles.searchFamilies(tree, "").first().name)
        assertTrue(OpeningBattles.searchFamilies(tree, "najdorf").any { it.name == "Sicilian Defense" })
        assertTrue(OpeningBattles.searchFamilies(tree, "zzzz").isEmpty())
    }

    @Test
    fun familiesGroupIntoOpeningsMainLineFirst() {
        val groups = tree.families.first { it.name == "Sicilian Defense" }.groups()
        assertEquals(MAIN_LINE, groups.first().name)
        assertTrue(groups.any { it.name == "Najdorf Variation" && it.lines.contains(najdorf) })
        assertEquals("every line lands in one group", tree.families.first { it.name == "Sicilian Defense" }.lines.size, groups.sumOf { it.lines.size })
    }
}
