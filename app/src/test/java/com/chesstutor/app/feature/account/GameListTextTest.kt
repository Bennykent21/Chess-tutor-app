package com.chesstutor.app.feature.account

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.ThumbUp
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.domain.account.GameFilter
import com.chesstutor.app.domain.account.GameOutcome
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/** The words on Account's game rows and filter button (D44). */
class GameListTextTest {

    private val utc = ZoneOffset.UTC
    private fun at(day: Int, hour: Int = 12) = LocalDateTime.of(2026, 10, day, hour, 0).toInstant(utc).toEpochMilli()

    @Test
    fun daysReadAsWordsForAWeekThenAsDates() {
        val now = at(28, 9)
        assertEquals("today", relativeDay(at(28, 1), now, utc))
        assertEquals("yesterday", relativeDay(at(27, 23), now, utc))
        assertEquals("6 days ago", relativeDay(at(22), now, utc))
        assertEquals("21 Oct 2026", relativeDay(at(21), now, utc))
    }

    @Test
    fun theFilterButtonCountsEachFilterOnce() {
        assertEquals(0, activeFilters(GameFilter()))
        assertEquals(0, activeFilters(GameFilter(query = "  ")))
        assertEquals(3, activeFilters(GameFilter(outcome = GameOutcome.WIN, color = PieceColor.BLACK, query = "juno")))
    }

    @Test
    fun eachGradeHasOneIconEverywhere() {
        assertEquals(Icons.Rounded.ThumbUp, qualityIcon(com.chesstutor.app.domain.account.MoveQuality.EXCELLENT))
        assertEquals(Icons.Rounded.Star, qualityIcon(com.chesstutor.app.domain.account.MoveQuality.BEST))
        assertEquals(Icons.Rounded.Check, qualityIcon(com.chesstutor.app.domain.account.MoveQuality.GOOD))
        assertEquals("symbol grades keep their notation", null, qualityIcon(com.chesstutor.app.domain.account.MoveQuality.BLUNDER))
    }
}
