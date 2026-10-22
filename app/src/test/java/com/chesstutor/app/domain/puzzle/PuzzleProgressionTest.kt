package com.chesstutor.app.domain.puzzle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.abs

class PuzzleProgressionTest {

    /** A pack with [perRating] puzzles every 25 points from 300 to 2600, themes rotating. */
    private fun pack(perRating: Int = 3): PuzzlePack {
        val themes = listOf("fork", "pin", "mateIn1", "skewer", "hangingPiece")
        val puzzles = (300..2600 step 25).flatMap { rating ->
            (0 until perRating).map { i ->
                Puzzle("p$rating-$i", "8/8/8/8/8/8/8/K6k w - - 0 1", listOf("a1a2", "h1h2"), rating, themes[(rating / 25 + i) % themes.size], emptySet())
            }
        }
        return PuzzlePack(puzzles)
    }

    @Test
    fun levelsRiseFrom400To2400AcrossFiveWorldsOfTen() {
        assertEquals(400, PuzzleLevels.targetRating(1))
        assertEquals(2400, PuzzleLevels.targetRating(50))
        assertTrue((1 until 50).all { PuzzleLevels.targetRating(it + 1) > PuzzleLevels.targetRating(it) })
        assertEquals(PuzzleWorld.MEADOW, PuzzleWorld.of(1))
        assertEquals(PuzzleWorld.MEADOW, PuzzleWorld.of(10))
        assertEquals(PuzzleWorld.TIDES, PuzzleWorld.of(11))
        assertEquals(PuzzleWorld.SUMMIT, PuzzleWorld.of(50))
        assertEquals(50, PuzzleWorld.entries.sumOf { it.levels.count() })
    }

    @Test
    fun aLevelServesFiveDifferentPuzzlesNearItsRatingWithVariedThemes() {
        val p = pack()
        val puzzles = PuzzleLevels.puzzlesFor(level = 20, playIndex = 0, pack = p)
        assertEquals(5, puzzles.size)
        assertEquals(5, puzzles.map { it.id }.toSet().size)
        val target = PuzzleLevels.targetRating(20)
        assertTrue(puzzles.all { abs(it.rating - target) <= 60 })
        assertEquals("all different themes when the pack allows", 5, puzzles.map { it.primaryTheme }.toSet().size)
        assertEquals("easiest first", puzzles.sortedBy { it.rating }, puzzles)
    }

    @Test
    fun theSamePlayGivesTheSameSetAndAReplayGivesNewPuzzles() {
        val p = pack()
        assertEquals(PuzzleLevels.puzzlesFor(7, 0, p), PuzzleLevels.puzzlesFor(7, 0, p))
        assertNotEquals(PuzzleLevels.puzzlesFor(7, 0, p).map { it.id }, PuzzleLevels.puzzlesFor(7, 1, p).map { it.id })
    }

    @Test
    fun seenPuzzlesAreAvoidedWhileEnoughRemain() {
        val p = pack()
        val first = PuzzleLevels.puzzlesFor(12, 0, p)
        val again = PuzzleLevels.puzzlesFor(12, 0, p, avoid = first.map { it.id }.toSet())
        assertTrue(again.none { it in first })
        assertTrue(PuzzleLevels.puzzlesFor(3, 0, PuzzlePack.EMPTY).isEmpty())
    }

    @Test
    fun levelsUnlockOneAfterAnother() {
        assertTrue(PuzzleLevels.isUnlocked(1, emptySet()))
        assertFalse(PuzzleLevels.isUnlocked(2, emptySet()))
        assertTrue(PuzzleLevels.isUnlocked(2, setOf(1)))
        assertEquals(1, PuzzleLevels.current(emptySet()))
        assertEquals(4, PuzzleLevels.current(setOf(1, 2, 3)))
        assertEquals(50, PuzzleLevels.current(PuzzleLevels.range.toSet()))
    }

    @Test
    fun passingNeedsFourOfFiveWithoutRevealing() {
        assertTrue(LevelRun(1, solvedWithoutReveal = 4, played = 5).passed)
        assertFalse(LevelRun(1, solvedWithoutReveal = 3, played = 5).passed)
        assertFalse("not finished yet", LevelRun(1, solvedWithoutReveal = 4, played = 4).passed)
    }

    @Test
    fun xpRewardsCleanFastStreakySolvesAndNothingForRevealing() {
        fun xp(solved: Boolean = true, clean: Boolean = true, revealed: Boolean = false, ms: Long = 60_000, streak: Int = 1) =
            XpRules.forSolve(SolveRecord(solved, clean, revealed, ms, streak))
        assertEquals(10, xp())
        assertEquals(15, xp(ms = 12_000))
        assertEquals(12, xp(ms = 35_000))
        assertEquals("helped: mistakes or hints", 4, xp(clean = false, streak = 0))
        assertEquals(0, xp(solved = false, clean = false, revealed = true, streak = 0))
        assertEquals("streak bonus grows", 12, xp(streak = 3))
        assertEquals("and is capped", 15, xp(streak = 40))
    }

    @Test
    fun playerLevelsNeedALittleMoreXpEachTime() {
        assertEquals(PlayerLevel(1, 0, 50), PlayerLevel.fromXp(0))
        assertEquals(PlayerLevel(1, 49, 50), PlayerLevel.fromXp(49))
        assertEquals(PlayerLevel(2, 0, 60), PlayerLevel.fromXp(50))
        assertEquals(PlayerLevel(3, 5, 70), PlayerLevel.fromXp(115))
        assertEquals(0.5f, PlayerLevel(2, 30, 60).progress, 0.001f)
    }

    @Test
    fun dayStreakCountsConsecutiveDaysAndSurvivesUntilMidnight() {
        val today = LocalDate.of(2026, 10, 22)
        val days = setOf(today.minusDays(1), today.minusDays(2), today.minusDays(3), today.minusDays(5))
        assertEquals("not played yet today: yesterday's streak still stands", 3, DayStreak.compute(days, today))
        assertEquals(4, DayStreak.compute(days + today, today))
        assertEquals("a missed day breaks it", 0, DayStreak.compute(setOf(today.minusDays(2)), today))
        assertEquals(0, DayStreak.compute(emptySet(), today))
    }

    @Test
    fun firstPassEarnsABonusAndTheLastLevelOfAWorldCompletesIt() {
        val first = PuzzleBookkeeping.afterLevelRun(LevelRun(3, 5, 5), emptyMap())
        assertEquals(LevelRunResult(newlyPassed = true, worldCompleted = null, bonusXp = XpRules.LEVEL_PASSED), first)

        val again = PuzzleBookkeeping.afterLevelRun(LevelRun(3, 5, 5), mapOf(3 to LevelProgress(3, 5, true, 1)))
        assertFalse("no bonus for passing again", again.newlyPassed)

        val nineDone = (1..9).associateWith { LevelProgress(it, 4, true, 1) }
        val world = PuzzleBookkeeping.afterLevelRun(LevelRun(10, 4, 5), nineDone)
        assertEquals(PuzzleWorld.MEADOW, world.worldCompleted)
        assertEquals(XpRules.LEVEL_PASSED + XpRules.WORLD_COMPLETED, world.bonusXp)

        assertNull(PuzzleBookkeeping.afterLevelRun(LevelRun(10, 2, 5), nineDone).worldCompleted)
    }

    @Test
    fun anAttemptUpdatesRatingAndStreakAndEarnsXp() {
        val puzzle = Puzzle("x", "8/8/8/8/8/8/8/K6k w - - 0 1", listOf("a1a2", "h1h2"), 1200, "fork", emptySet())
        val clean = PuzzleOutcome(solved = true, clean = true, mistakes = 0, hintsUsed = 0, revealed = false)
        val result = PuzzleBookkeeping.afterAttempt(
            PuzzleProgress(rating = 1200, streak = 2),
            AttemptRecord(puzzle, clean, PuzzleMode.LEVEL, level = 21, elapsedMs = 10_000, finishedAt = 0),
        )
        assertEquals(3, result.progress.streak)
        assertTrue(result.ratingDelta > 0)
        assertEquals("10 base + 5 fast + 2 for a streak of 3", 17, result.xpEarned)
    }
}
