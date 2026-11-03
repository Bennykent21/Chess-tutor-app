package com.chesstutor.app.domain.learn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A lesson's picture should show a position worth looking at. */
class LessonPictureTest {

    private val start = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    private val developed = "r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R b KQkq - 3 3"
    private val rookEnding = "8/5pk1/6p1/3R4/8/6P1/r4PK1/8 w - - 0 40"
    private val kings = "4k3/8/8/8/8/8/8/4K3 w - - 0 1"

    private fun lesson(vararg fens: String) =
        Lesson(id = "l", title = "L", summary = "", steps = fens.map { LessonStep.Explain(text = "", fen = it) })

    @Test
    fun anOpeningShowsWhereItGotTo() {
        assertEquals(developed, lesson(start, developed).pictureFen)
    }

    @Test
    fun aLessonEndingOnANearlyEmptyBoardShowsItsLastFullOne() {
        assertEquals(start, lesson(start, kings).pictureFen)
    }

    @Test
    fun anEndgameLessonShowsItsFullestBoard() {
        assertEquals(rookEnding, lesson(kings, rookEnding, kings).pictureFen)
    }

    @Test
    fun noStepsNoPicture() {
        assertNull(lesson().pictureFen)
    }
}
