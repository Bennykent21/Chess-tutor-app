package com.chesstutor.app.feature.account

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.data.model.GameSource
import com.chesstutor.app.data.model.LinkedChessProfile
import com.chesstutor.app.data.model.RatingPlatform
import com.chesstutor.app.data.model.RatingTimeControl
import com.chesstutor.app.data.settings.AppSettings
import com.chesstutor.app.design.CtTheme
import com.chesstutor.app.domain.account.AnalysisSummary
import com.chesstutor.app.domain.account.GameLibrary
import com.chesstutor.app.domain.opening.OpeningTree
import com.chesstutor.app.domain.puzzle.PlayerLevel
import com.chesstutor.app.domain.puzzle.PuzzleStats
import com.chesstutor.app.viewmodel.AppUiState
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The Account tab's Overview and Games pages and the game page (step 5A). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1600dp-xxhdpi")
class AccountScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val tree by lazy { File("src/main/assets/openings.tsv.gz").inputStream().use(OpeningTree::fromStream) }

    private val italianFinal = "r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4"

    private fun record(id: String, opponent: String, result: String, color: String, source: GameSource, moves: String, date: Long, timeClass: String = "rapid") =
        GameRecord(
            id = id, dateMillis = date, botName = opponent, botRating = 1450, result = result, pgn = "",
            moveCount = moves.split(' ').size, userColor = color, finalFen = italianFinal, uciMoves = moves,
            source = source, termination = "checkmate", timeClass = timeClass,
        )

    private val games by lazy {
        GamesState(
            loading = false,
            games = GameLibrary.summarise(
                listOf(
                    record("bot_1", "Pip", "1-0", "white", GameSource.ARENA, "e2e4 e7e5 g1f3 b8c6 f1c4 f8c5", date = 1_760_000_000_000),
                    record("chesscom_2", "rookie99", "1-0", "black", GameSource.CHESS_COM, "d2d4 d7d5 c2c4 e7e6", date = 1_759_000_000_000, timeClass = "blitz"),
                    record("lichess_3", "sagefan", "1/2-1/2", "white", GameSource.LICHESS, "e2e4 c7c5 g1f3 d7d6", date = 1_758_000_000_000, timeClass = "bullet"),
                ),
                tree,
            ),
        )
    }

    private val stats = PuzzleStats(rating = 1310, totalXp = 640, playerLevel = PlayerLevel.fromXp(640), dayStreak = 5)

    @Test
    fun overviewShowsTheHeaderLinkFormAndRecentGames() {
        var opened: String? = null
        compose.setContent {
            CtTheme(reduceMotion = true) {
                AccountScreen(state = AppUiState(), games = games, stats = stats, dueReviewCount = 2, actions = AccountActions(onOpenGame = { opened = it }))
            }
        }
        compose.onNodeWithText("Link your account").assertExists()
        compose.onNodeWithText("Recent games").assertExists()
        compose.onNodeWithText("1 won").assertExists()
        compose.onNodeWithText("1 lost").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/account_overview.png")

        compose.onNodeWithText("vs Pip (1450) · White").performClick()
        assertEquals("bot_1", opened)
    }

    @Test
    fun gamesPageFiltersBySourceResultAndSearch() {
        compose.setContent {
            CtTheme(reduceMotion = true) {
                AccountScreen(state = AppUiState(), games = games, stats = stats, dueReviewCount = 0, actions = AccountActions(), initialTab = AccountTab.GAMES)
            }
        }
        compose.onNodeWithText("3 games").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/account_games.png")

        compose.onNodeWithText("Chess.com").performClick()
        compose.onNodeWithText("1 of 3 games").assertExists()
        compose.onNodeWithText("vs rookie99 (1450) · Black").assertExists()
        compose.onNodeWithText("vs Pip (1450) · White").assertDoesNotExist()

        compose.onNodeWithText("Clear filters").performClick()
        compose.onNodeWithText("Losses").performClick()
        compose.onNodeWithText("vs rookie99 (1450) · Black").assertExists()
        compose.onNodeWithText("Losses").performClick()
        compose.onNodeWithText("Draws").performClick()
        compose.onNodeWithText("vs sagefan (1450) · White").assertExists()

        compose.onNodeWithText("Clear filters").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("nobody")
        compose.onNodeWithText("Nothing matches").assertExists()
    }

    @Test
    fun performancePageShowsResultsAndOpensTheGamesBehindABar() {
        compose.setContent {
            CtTheme(reduceMotion = true) {
                AccountScreen(state = AppUiState(), games = games, stats = stats, dueReviewCount = 0, actions = AccountActions(), initialTab = AccountTab.PERFORMANCE)
            }
        }
        compose.onNodeWithText("Score 50%").assertExists()
        compose.onNodeWithText("By time control").assertExists()
        compose.onNodeWithText("Open a game and tap Review to analyse it. Accuracy by phase and your common mistakes then show here.").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/account_performance.png")

        compose.onNodeWithText("Blitz").performClick()
        compose.onNodeWithText("1 of 3 games").assertExists()
        compose.onNodeWithText("vs rookie99 (1450) · Black").assertExists()
    }

    @Test
    fun trainingPageShowsPuzzlesThemesLessonsAndDueMistakes() {
        val history = (1..6).map { i ->
            com.chesstutor.app.domain.puzzle.PuzzleHistoryEntry("p$i", solved = true, clean = i % 2 == 0, ratingAfter = 1000 + i * 15, finishedAt = i.toLong())
        }
        val training = com.chesstutor.app.domain.account.TrainingCalculator.report(
            history,
            { id -> if (id.removePrefix("p").toInt() % 2 == 0) setOf("fork") else setOf("pin") },
            lessonsDone = 12,
            lessonsTotal = 48,
        )
        var reviewed = false
        compose.setContent {
            CtTheme(reduceMotion = true) {
                AccountScreen(
                    state = AppUiState(), games = games, stats = stats, dueReviewCount = 2,
                    actions = AccountActions(onReviewMistakes = { reviewed = true }),
                    initialTab = AccountTab.TRAINING, training = training,
                )
            }
        }
        compose.onNodeWithText("Your strengths").assertExists()
        compose.onNodeWithText("Worth practising").assertExists()
        compose.onNodeWithText("12 of 48 lessons done").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/account_training.png")
        compose.onNodeWithText("Review now").performClick()
        assertTrue(reviewed)
    }

    @Test
    fun settingsPageHoldsCoachBoardAccountEngineAndAbout() {
        var boardOpened = false
        var chat: com.chesstutor.app.domain.bot.ChatLevel? = null
        compose.setContent {
            CtTheme(reduceMotion = true) {
                SettingsScreen(
                    state = AppUiState(),
                    accountActions = AccountActions(),
                    actions = SettingsActions(onOpenBoardSettings = { boardOpened = true }, onChatLevel = { chat = it }),
                )
            }
        }
        compose.onNodeWithText("Link your account").assertExists()
        compose.onNodeWithText("Export as PGN").assertExists()
        compose.onNodeWithText("Stockfish 19 chess engine").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/account_settings.png")
        compose.onNodeWithText("Board, pieces and sound").performClick()
        assertTrue(boardOpened)
        compose.onNodeWithText("Lots").performClick()
        assertEquals(com.chesstutor.app.domain.bot.ChatLevel.LOTS, chat)
    }

    @Test
    fun linkedAccountOffersSyncAndUnlink() {
        val state = AppUiState(
            linkedProfile = LinkedChessProfile(RatingPlatform.LICHESS, "kenny", rapidRating = 1848, blitzRating = 1720, bulletRating = null, selectedTimeControl = RatingTimeControl.RAPID),
        )
        var synced = false
        compose.setContent {
            CtTheme(reduceMotion = true) {
                AccountScreen(state = state, games = games, stats = stats, dueReviewCount = 0, actions = AccountActions(onSync = { synced = true }))
            }
        }
        compose.onNodeWithText("@kenny on Lichess").assertExists()
        compose.onNodeWithText("Rapid 1848 · Blitz 1720 · Bullet –").assertExists()
        compose.onNodeWithText("1848 rapid on Lichess · 5-day streak · Level ${stats.playerLevel.level}").assertExists()
        compose.onNodeWithText("Sync games").performClick()
        assertTrue(synced)
    }

    @Test
    fun gamePageShowsOpeningBoardAndActions() {
        val game = games.game("bot_1")!!
        var reviewed = false
        var deleted = false
        compose.setContent {
            CtTheme(reduceMotion = true) {
                GameDetailScreen(
                    game = game,
                    settings = AppSettings(),
                    actions = GameDetailActions(onReview = { reviewed = true }, onDelete = { deleted = true }),
                    analysis = AnalysisSummary("bot_1", plies = 6, whiteAccuracy = 92.34, blackAccuracy = 71.0, lite = false, analysedAt = 1),
                )
            }
        }
        compose.onNodeWithText("You won").assertExists()
        compose.onNodeWithText("By checkmate · You played White · 3 moves").assertExists()
        compose.onNodeWithText("Every move was in the opening book.").assertExists()
        compose.onNodeWithText("1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/account_game.png")

        compose.onNodeWithText("You 92.3% · Pip 71.0%").assertExists()
        compose.onNodeWithText("Open the review").performClick()
        assertTrue(reviewed)
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Delete this game?").assertExists()
        compose.onAllNodesWithText("Delete").onLast().performClick()
        assertTrue(deleted)
    }

    @Test
    fun aDeletedGameShowsAPlainPage() {
        compose.setContent { CtTheme(reduceMotion = true) { GameDetailScreen(game = null, settings = AppSettings(), actions = GameDetailActions()) } }
        compose.onNodeWithText("This game is no longer saved").assertExists()
    }

    @Test
    fun bookLineSaysWhoLeftTheBook() {
        val strange = GameLibrary.summarise(
            record("x", "Pip", "0-1", "black", GameSource.ARENA, "e2e4 e7e5 g1f3 b8c6 f1c4 f8c5 e1f1 g8f6", date = 1),
            tree,
        )
        assertEquals("Your opponent left the book on move 4.", bookLine(strange))
        assertEquals("You lost", resultTitle(strange.copy(outcome = com.chesstutor.app.domain.account.GameOutcome.LOSS)))
    }
}
