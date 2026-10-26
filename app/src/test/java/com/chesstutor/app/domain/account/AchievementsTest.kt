package com.chesstutor.app.domain.account

import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.data.model.GameSource
import com.chesstutor.app.domain.opening.OpeningTree
import com.chesstutor.app.domain.puzzle.PuzzleHistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class AchievementsTest {

    private val utc = ZoneOffset.UTC
    private fun dayMillis(day: Int, hour: Int = 12) = LocalDate.of(2026, 10, day).atTime(hour, 0).toInstant(utc).toEpochMilli()

    private fun game(id: String, result: String, rating: Int, at: Long, source: GameSource = GameSource.ARENA) = GameLibrary.summarise(
        GameRecord(id = id, dateMillis = at, botName = "Bot$rating", botRating = rating, result = result, pgn = "", moveCount = 2,
            userColor = "white", finalFen = "", uciMoves = "e2e4 e7e5", source = source),
        OpeningTree.EMPTY,
    )

    private fun puzzle(day: Int, clean: Boolean = true, solved: Boolean = true) =
        PuzzleHistoryEntry("p$day", solved = solved, clean = clean, ratingAfter = 1000, finishedAt = dayMillis(day))

    @Test
    fun badgesComeFromWhatIsAlreadySaved() {
        val inputs = AchievementInputs(
            games = listOf(
                game("bot_a", "1-0", 1700, dayMillis(1)),
                game("bot_b", "0-1", 2100, dayMillis(2)),
                game("lichess_c", "1-0", 2400, dayMillis(3), GameSource.LICHESS),
            ),
            analysed = mapOf("bot_a" to AnalysisSummary("bot_a", 2, whiteAccuracy = 93.0, blackAccuracy = 40.0, lite = false, analysedAt = dayMillis(4))),
            puzzles = (1..8).map { puzzle(it) } + puzzle(9, clean = false),
            playerLevel = 4,
            lessonsFinished = mapOf("l1" to dayMillis(5)),
            lessonTitles = mapOf("l1" to "Check", "l2" to "Mate"),
        )
        val badges = Achievements.badges(inputs, utc).associateBy { it.id }
        assertTrue("beat a bot", badges.getValue("first-win").earned)
        assertTrue("beat a 1700 bot", badges.getValue("giant-slayer").earned)
        assertFalse("lost to the 2100 bot; the Lichess win does not count", badges.getValue("bot-master").earned)
        assertEquals(9, badges.getValue("puzzles-10").progress)
        assertTrue("8 clean in a row, then a slip", !badges.getValue("hot-hand").earned && badges.getValue("hot-hand").progress == 8)
        assertTrue("puzzles on 9 days in a row", badges.getValue("week").earned)
        assertTrue(badges.getValue("first-lesson").earned)
        assertEquals(2, badges.getValue("all-lessons").goal)
        assertTrue(badges.getValue("first-review").earned)
        assertTrue("93% accuracy as White", badges.getValue("sharp").earned)
        assertFalse(badges.getValue("brilliant").earned)
        assertTrue("earned badges come first", Achievements.badges(inputs, utc).takeWhile { it.earned }.size == badges.values.count { it.earned })
    }

    @Test
    fun recentActivityIsNewestFirstWithPuzzlesGroupedByDay() {
        val inputs = AchievementInputs(
            games = listOf(game("bot_a", "1-0", 1200, dayMillis(1)), game("chesscom_b", "0-1", 1500, dayMillis(3), GameSource.CHESS_COM)),
            analysed = mapOf("bot_a" to AnalysisSummary("bot_a", 2, whiteAccuracy = 81.6, blackAccuracy = 40.0, lite = false, analysedAt = dayMillis(4))),
            puzzles = listOf(puzzle(2), puzzle(2, clean = false)),
            lessonsFinished = mapOf("l1" to dayMillis(5)),
            lessonTitles = mapOf("l1" to "Check and mate"),
        )
        assertEquals(
            listOf(
                "Finished the lesson \"Check and mate\"",
                "Reviewed your game against Bot1200 (82% accuracy)",
                "Lost to Bot1500 on Chess.com",
                "Solved 2 puzzles (1 without a slip)",
                "Beat Bot1200",
            ),
            Achievements.recent(inputs, zone = utc).map { it.text },
        )
    }

    @Test
    fun runsCountConsecutiveValuesAndDays() {
        assertEquals(3, Achievements.longestRun(listOf(true, true, false, true, true, true)))
        assertEquals(0, Achievements.longestRun(emptyList()))
        assertEquals(2, Achievements.longestDayRun(listOf(dayMillis(1), dayMillis(2, 9), dayMillis(2, 20), dayMillis(5)), utc))
    }
}
