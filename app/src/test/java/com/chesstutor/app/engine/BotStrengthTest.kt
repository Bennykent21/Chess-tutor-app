package com.chesstutor.app.engine

import com.example.chess.engine.BotStrength
import com.example.chess.engine.StockfishProfile
import com.example.chess.engine.TrainingLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BotStrengthTest {

    @Test
    fun canonicalPresetsAreStrictlyAscending() {
        val ratings = BotStrength.presets.map { it.rating }
        assertEquals(listOf(250, 600, 1000, 1300, 1600, 2000, 2400, 3200), ratings)
        assertTrue(ratings.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun stockfishProfilesMirrorCanonicalPresetRatings() {
        assertEquals(
            BotStrength.presets.map { it.rating },
            StockfishProfile.PRESETS.map { it.elo }
        )
    }

    @Test
    fun trainingLevelsMirrorCanonicalPresetRatings() {
        assertEquals(
            BotStrength.presets.map { it.rating },
            TrainingLevel.entries.map { it.elo }
        )
    }

    @Test
    fun eloMappingUsesNearestCanonicalPreset() {
        assertEquals(250, StockfishProfile.forElo(250).elo)
        assertEquals(600, StockfishProfile.forElo(720).elo)
        assertEquals(1000, StockfishProfile.forElo(1100).elo)
        assertEquals(3200, StockfishProfile.forElo(3200).elo)
        assertNotNull(BotStrength.presetForKey("Grandmaster"))
    }
}
