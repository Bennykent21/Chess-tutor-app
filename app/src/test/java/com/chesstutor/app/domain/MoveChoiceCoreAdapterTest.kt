package com.chesstutor.app.domain

import com.example.chess.core.PieceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoveChoiceCoreAdapterTest {

    @Test
    fun promotionRoundTripUsesCoreUciSemantics() {
        val choice = MoveChoice.fromUci(
            uci = "a7a8n",
            piece = 'p',
            san = "a8=N"
        )

        assertEquals("a7a8n", choice.uci)
        assertEquals(PieceType.KNIGHT, choice.toCoreMove().promotion)
        assertEquals("a8=N", choice.san)
    }

    @Test
    fun nonPromotionRoundTripHasNoPromotionType() {
        val choice = MoveChoice.fromUci("e2e4", piece = 'p', san = "e4")

        assertEquals("e2e4", choice.toCoreMove().uci)
        assertNull(choice.toCoreMove().promotion)
    }
}
