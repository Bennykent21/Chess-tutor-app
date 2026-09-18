package com.chesstutor.app.domain

import org.junit.Assert.assertTrue
import org.junit.Test

class ChessPositionPromotionTest {

    private val promotionFen = "8/P6k/8/8/8/8/6K1/8 w - - 0 1"

    @Test
    fun allFourPromotionChoicesAreAcceptedByDomainAdapter() {
        listOf('q', 'r', 'b', 'n').forEach { promotion ->
            val position = ChessPosition(promotionFen)
            val move = "a7a8$promotion"

            assertTrue("Promotion $promotion should be legal", position.play(move))
            assertTrue(
                "Promotion $promotion should place the promoted piece on a8",
                position.pieceAt("a8") == promotion.uppercaseChar()
            )
        }
    }
}
