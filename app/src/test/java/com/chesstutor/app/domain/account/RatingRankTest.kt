package com.chesstutor.app.domain.account

import org.junit.Assert.assertEquals
import org.junit.Test

class RatingRankTest {

    @Test
    fun aRatingEarnsThePieceItHasReached() {
        assertEquals(RatingRank.PAWN, RatingRank.of(600))
        assertEquals(RatingRank.PAWN, RatingRank.of(799))
        assertEquals(RatingRank.KNIGHT, RatingRank.of(800))
        assertEquals(RatingRank.BISHOP, RatingRank.of(1499))
        assertEquals(RatingRank.ROOK, RatingRank.of(1526))
        assertEquals(RatingRank.QUEEN, RatingRank.of(2199))
        assertEquals(RatingRank.KING, RatingRank.of(2200))
        assertEquals(RatingRank.KING, RatingRank.of(3200))
    }

    @Test
    fun aNegativeRatingIsStillAPawn() {
        assertEquals(RatingRank.PAWN, RatingRank.of(-5))
    }
}
