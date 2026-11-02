package com.chesstutor.app.domain.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun progressRunsFromOnePieceToTheNext() {
        assertEquals(0f, RatingRank.ROOK.progress(1500), 0.001f)
        assertEquals(0.5f, RatingRank.ROOK.progress(1650), 0.001f)
        assertEquals("the top piece has nothing left to climb", 1f, RatingRank.KING.progress(2300), 0.001f)
    }

    @Test
    fun theKingIsTheTop() {
        assertEquals(RatingRank.QUEEN, RatingRank.ROOK.next)
        assertNull(RatingRank.KING.next)
    }
}
