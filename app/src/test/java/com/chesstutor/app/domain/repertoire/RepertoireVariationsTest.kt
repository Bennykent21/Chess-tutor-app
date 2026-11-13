package com.chesstutor.app.domain.repertoire

import com.chesstutor.app.domain.repertoire.RepertoireLevel.CORE
import com.chesstutor.app.domain.repertoire.RepertoireLevel.STANDARD
import com.chesstutor.app.domain.repertoire.TestRepertoire.P
import com.chesstutor.app.domain.repertoire.TestRepertoire.key
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class RepertoireVariationsTest {
    private val book = TestRepertoire.italian()
    private val index = RepertoireIndex(book)
    private val day0: Instant = Instant.parse("2026-11-13T09:00:00Z")

    private fun state(path: String, stage: Int) =
        RepertoireCards.introduce(book.id, book.nodes.getValue(key(path)), day0).copy(stage = stage)

    @Test
    fun theVariationsAreTheRepliesAtTheFirstBranchMostCommonFirst() {
        val rows = RepertoireVariations.of(index, band = null, open = CORE, states = emptyMap())
        assertEquals(listOf("3...Bc5", "3...Nf6", "3...Nd4"), rows.map { it.move })
        assertEquals(listOf(0.5, 0.4, 0.1), rows.map { it.share })
        assertEquals("$P f8c5".split(' '), rows.first().path)
        assertEquals("only Core cards while Core is open", listOf(2, 1, 0), rows.map { it.cards })
        assertEquals("no database names in the test tree", "3...Bc5", rows.first().label)
        assertEquals("Standard adds 5.O-O against ...Be7 and 4.Nxd4", listOf(2, 2, 1), RepertoireVariations.of(index, null, STANDARD, emptyMap()).map { it.cards })
    }

    @Test
    fun theSharesFollowTheLearnersLevel() {
        val under1000 = RepertoireVariations.of(index, band = 0, open = CORE, states = emptyMap())
        assertEquals("under 1000, the blunder 3...Nd4 is the most common reply", listOf("3...Nd4", "3...Bc5", "3...Nf6"), under1000.map { it.move })
        assertEquals(listOf(0.4, 0.3, 0.3), under1000.map { it.share })
    }

    @Test
    fun aVariationCountsWhatTheLearnerHasMetAndWhatIsSolid() {
        val states = listOf(state("$P f8c5", stage = 1), state("$P f8c5 c2c3 g8f6", stage = 0)).associateBy { it.key }
        val giuoco = RepertoireVariations.of(index, null, CORE, states).first()
        assertEquals(2, giuoco.introduced)
        assertEquals(1, giuoco.solid)
        assertEquals(0.5 / 1.4, RepertoireVariations.readiness(book, CORE, states), 1e-9)
        assertEquals(0.0, RepertoireVariations.readiness(book, CORE, emptyMap()), 0.0)
    }

    @Test
    fun aReplyMostGamesTakeIsSplitIntoTheVariationsAfterIt() {
        val caro = RepertoireIndex(TestRepertoire.caroKann())
        val rows = RepertoireVariations.of(caro, null, CORE, emptyMap())
        // 2.d4 is the only reply, so the first branch is after 2...d5: the Advance and 3.Nc3.
        assertEquals(listOf("3.e5", "3.Nc3"), rows.map { it.move })
        assertEquals(listOf(1, 1), rows.map { it.cards })
    }
}
