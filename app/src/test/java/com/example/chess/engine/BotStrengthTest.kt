package com.example.chess.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BotStrengthTest {

    @Test
    fun canonicalPresetsAreOrderedAndUnique() {
        val ratings = BotStrength.presets.map { it.rating }
        assertEquals(ratings.sorted(), ratings)
        assertEquals(ratings.size, ratings.toSet().size)
    }

    @Test
    fun stockfishProfilesUseCanonicalRatings() {
        assertEquals(
            BotStrength.presets.map { it.rating },
            StockfishProfile.PRESETS.map { it.elo }
        )
    }

    @Test
    fun knownKeysResolveToExpectedRatings() {
        assertEquals(250, BotStrength.presetForKey("Beginner")?.rating)
        assertEquals(600, BotStrength.presetForKey("Casual")?.rating)
        assertEquals(1000, BotStrength.presetForKey("Intermediate")?.rating)
        assertEquals(1300, BotStrength.presetForKey("Club")?.rating)
        assertEquals(1600, BotStrength.presetForKey("Advanced")?.rating)
        assertEquals(2000, BotStrength.presetForKey("Expert")?.rating)
        assertEquals(2400, BotStrength.presetForKey("Master")?.rating)
        assertEquals(3200, BotStrength.presetForKey("Grandmaster")?.rating)
        assertNotNull(BotStrength.presetForKey("beginner"))
    }
}
