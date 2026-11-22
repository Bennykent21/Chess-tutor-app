package com.chesstutor.app.domain.game

import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.domain.account.ThemeScore
import com.chesstutor.app.domain.bot.BotRoster
import com.chesstutor.app.domain.train.EndingKind
import com.chesstutor.app.domain.train.GameFinding
import org.junit.Assert.assertEquals
import org.junit.Test

class CoachingIntelligenceTest {
    private val bot = PlayRecommendation(BotRoster.bots.first(), "A game is a useful next step.")

    private val position = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    private fun repeatedMistake(times: Int, reached: Int) = GameFinding.RepeatedMistake(
        fen = position,
        opening = "Italian Game",
        after = "2...Nf6",
        played = "3.Nxe5",
        better = "3.d4",
        times = times,
        reached = reached,
    )

    private fun lineSlip(misses: Int, reached: Int) = GameFinding.LineSlip(
        bookId = "caro-kann",
        bookTitle = "Caro-Kann",
        side = PieceColor.BLACK,
        fen = position,
        path = listOf("e2e4", "c7c6"),
        after = "1.e4",
        played = "1...e5",
        right = "1...c6",
        misses = misses,
        reached = reached,
        note = null,
    )

    @Test
    fun scheduledMistakesTakePriorityOverEveryOtherSignal() {
        val repeated = repeatedMistake(times = 4, reached = 4)

        val result = CoachingIntelligence.recommend(
            dueMistakes = 3,
            weaknesses = listOf(ThemeScore("fork", 10, 1)),
            findings = listOf(repeated),
            bot = bot,
        )

        assertEquals(NextStep.Mistakes(3), result)
    }

    @Test
    fun recurringMistakeIsSelectedWhenTheSameMoveFailsRepeatedly() {
        val repeated = repeatedMistake(times = 3, reached = 4)

        val result = CoachingIntelligence.recommend(
            dueMistakes = 0,
            weaknesses = listOf(ThemeScore("fork", 10, 1)),
            findings = listOf(lineSlip(2, 3), repeated),
            bot = bot,
        )

        assertEquals(NextStep.RepeatedMistake(repeated), result)
        assertEquals("The same mistake appeared in 3 of 4 reviewed games.", result.reason)
    }

    @Test
    fun openingSlipIsSelectedWhenNoStrongerRepeatedMistakeExists() {
        val slip = lineSlip(misses = 3, reached = 4)

        val result = CoachingIntelligence.recommend(
            dueMistakes = 0,
            weaknesses = emptyList(),
            findings = listOf(slip),
            bot = bot,
        )

        assertEquals(NextStep.LineSlip(slip), result)
        assertEquals("You left Caro-Kann the same way in 3 of 4 games.", result.reason)
    }

    @Test
    fun endgameConversionIsSurfacedOnlyWhenTheWinRateIsBelowHalf() {
        val ending = GameFinding.EndingResult(EndingKind.ROOK, won = 1, reached = 4)

        val result = CoachingIntelligence.recommend(
            dueMistakes = 0,
            weaknesses = emptyList(),
            findings = listOf(ending),
            bot = bot,
        )

        assertEquals(NextStep.Ending(ending), result)
    }

    @Test
    fun weakPuzzleThemeIsUsedWhenThereIsNoStrongGameFinding() {
        val theme = ThemeScore("fork", attempts = 10, clean = 2)

        val result = CoachingIntelligence.recommend(
            dueMistakes = 0,
            weaknesses = listOf(ThemeScore("pin", 10, 6), theme),
            findings = listOf(
                repeatedMistake(times = 2, reached = 6),
                lineSlip(misses = 2, reached = 6),
                GameFinding.EndingResult(EndingKind.PAWN, won = 2, reached = 4),
            ),
            bot = bot,
        )

        assertEquals(NextStep.Theme(theme), result)
    }

    @Test
    fun sparseOrWeakEvidenceDoesNotBecomeAConfidentRecommendation() {
        val result = CoachingIntelligence.recommend(
            dueMistakes = 0,
            weaknesses = listOf(ThemeScore("fork", attempts = 2, clean = 0)),
            findings = listOf(
                repeatedMistake(times = 2, reached = 5),
                lineSlip(misses = 2, reached = 5),
                GameFinding.EndingResult(EndingKind.PAWN, won = 2, reached = 4),
            ),
            bot = bot,
        )

        assertEquals(NextStep.Bot(bot), result)
    }
}
