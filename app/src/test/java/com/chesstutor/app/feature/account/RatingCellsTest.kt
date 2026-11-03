package com.chesstutor.app.feature.account

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.data.model.LinkedChessProfile
import com.chesstutor.app.data.model.RatingPlatform
import com.chesstutor.app.domain.account.Achievements
import com.chesstutor.app.domain.account.AchievementInputs
import com.chesstutor.app.domain.account.BadgeTier
import com.chesstutor.app.viewmodel.AppUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RatingCellsTest {

    @Test
    fun aLinkedAccountShowsEachClockAndThePuzzles() {
        val state = AppUiState(linkedProfile = LinkedChessProfile(RatingPlatform.LICHESS, "kenny", rapidRating = 1700, blitzRating = 1100, bulletRating = null))
        val cells = ratingCells(state, puzzleRating = 1233)
        assertEquals(listOf("Rapid", "Blitz", "Bullet", "Puzzles"), cells.map { it.label })
        assertEquals(listOf("1700", "1100", "–", "1233"), cells.map { it.value })
    }

    @Test
    fun theAppFollowsTheAverageOfTheClocksPlayed() {
        fun state(rapid: Int?, blitz: Int?, bullet: Int?) = AppUiState(estimatedRating = 900, linkedProfile = LinkedChessProfile(RatingPlatform.LICHESS, "kenny", rapidRating = rapid, blitzRating = blitz, bulletRating = bullet))
        assertEquals(1600, state(1700, 1100, 2000).effectiveUserRating)
        assertEquals("a clock never played does not drag it down", 1400, state(1700, 1100, null).effectiveUserRating)
        assertEquals("an account with no ratings yet keeps the app's own", 900, state(null, null, null).effectiveUserRating)
    }

    @Test
    fun withNoAccountThereIsTheRatingAndThePuzzles() {
        val cells = ratingCells(AppUiState(), puzzleRating = 1233)
        assertEquals(listOf("Rating", "Puzzles"), cells.map { it.label })
    }

    @Test
    fun harderBadgesAreMadeOfBetterMetal() {
        val tiers = Achievements.badges(AchievementInputs()).associate { it.id to it.tier }
        assertEquals(BadgeTier.BRONZE, tiers["first-win"])
        assertEquals(BadgeTier.SILVER, tiers["giant-slayer"])
        assertEquals(BadgeTier.GOLD, tiers["bot-master"])
        assertTrue(tiers.isNotEmpty())
    }
}
