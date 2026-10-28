package com.chesstutor.app.feature.account

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
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
        compose.onNodeWithText("Puzzle rating").assertExists()
        compose.onNodeWithText("Level ${stats.playerLevel.level}").assertExists()
        compose.onNodeWithContentDescription("5-day streak").assertExists()
        compose.onNodeWithText("Link your account").assertExists()
        compose.onNodeWithText("Recent games").performScrollTo()
        compose.onNodeWithText("@rookie99").assertExists()
        compose.onNodeWithText("Recent activity").assertDoesNotExist()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/account_overview.png")

        compose.onNodeWithText("Pip").performClick()
        assertEquals("bot_1", opened)
    }

    @Test
    fun overviewShowsBadgesAndOpensOne() {
        val overview = OverviewState(
            badges = listOf(
                com.chesstutor.app.domain.account.Badge("first-win", "First win", "Beat a bot", com.chesstutor.app.domain.account.BadgeKind.GAMES, 1, 1),
                com.chesstutor.app.domain.account.Badge("puzzles-10", "Puzzle starter", "Solve 10 puzzles", com.chesstutor.app.domain.account.BadgeKind.PUZZLES, 4, 10),
            ),
        )
        compose.setContent {
            CtTheme(reduceMotion = true) {
                AccountScreen(state = AppUiState(), games = games, stats = stats, dueReviewCount = 0, actions = AccountActions(), overview = overview)
            }
        }
        val list = compose.onNode(androidx.compose.ui.test.hasScrollToNodeAction())
        list.performScrollToNode(hasText("Badges"))
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/account_badges.png")
        compose.onNode(androidx.compose.ui.test.hasContentDescription("Puzzle starter: Solve 10 puzzles. 4 of 10.")).performClick()
        compose.onNodeWithText("4 / 10").assertExists()
        compose.onNodeWithText("Solve 10 puzzles").assertExists()
    }

    @Test
    fun gamesPageFiltersInOneSheet() {
        compose.setContent {
            CtTheme(reduceMotion = true) {
                AccountScreen(state = AppUiState(), games = games, stats = stats, dueReviewCount = 0, actions = AccountActions(), initialTab = AccountTab.GAMES)
            }
        }
        compose.onNodeWithText("3 games").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/account_games.png")

        compose.onNodeWithText("Filter").performClick()
        // The link card above the list has a "Chess.com" chip too; the sheet's comes last.
        compose.onAllNodesWithText("Chess.com").onLast().performClick()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/account_games_filter.png")
        compose.onNodeWithText("Show 1 game").performClick()
        compose.onNodeWithText("1 of 3 games").assertExists()
        compose.onNodeWithText("@rookie99").assertExists()
        compose.onNodeWithText("Pip").assertDoesNotExist()

        compose.onNodeWithText("Filters (1)").performClick()
        compose.onNodeWithText("Clear").performClick()
        compose.onNodeWithText("Losses").performClick()
        compose.onNodeWithText("Show 1 game").assertExists()
        compose.onNodeWithText("Draws").performClick()
        compose.onNodeWithText("Show 1 game").performClick()
        compose.onNodeWithText("@sagefan").assertExists()
        compose.onNodeWithText("@rookie99").assertDoesNotExist()

        compose.onNodeWithText("Filters (1)").performClick()
        compose.onNodeWithText("Clear").performClick()
        compose.onNodeWithContentDescription("Search games").performTextInput("nobody")
        compose.onNodeWithText("Show 0 games").performClick()
        compose.onNodeWithText("Nothing matches").assertExists()
    }

    @Test
    fun statsShowOpeningsThatOpenTheirCourseOrTheirGames() {
        val italian = com.chesstutor.app.domain.learn.Course(
            id = "openings-italian", category = com.chesstutor.app.domain.learn.LessonCategory.OPENINGS, title = "Italian Game",
            intro = "", level = com.chesstutor.app.domain.learn.LessonLevel.BEGINNER, order = 20, lessons = emptyList(),
        )
        var course: String? = null
        compose.setContent {
            CtTheme(reduceMotion = true) {
                AccountScreen(
                    state = AppUiState(), games = games, stats = stats, dueReviewCount = 0,
                    actions = AccountActions(onOpenCourse = { course = it }),
                    initialTab = AccountTab.STATS, openingCourses = listOf(italian),
                )
            }
        }
        compose.onNodeWithText("Score").assertExists()
        compose.onNodeWithText("Accuracy").assertDoesNotExist()
        compose.onNodeWithText("Your openings").performScrollTo()
        compose.onNodeWithText("By colour").performScrollTo()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/account_stats.png")

        compose.onNodeWithText("Italian Game").performScrollTo().performClick()
        assertEquals("openings-italian", course)
        compose.onNodeWithText("Sicilian Defense").performScrollTo().performClick()
        compose.onNodeWithText("1 of 3 games").assertExists()
        compose.onNodeWithText("@sagefan").assertExists()
    }

    @Test
    fun trainingShowsThemesToWorkOnAndDueMistakes() {
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
        var practised: String? = null
        compose.setContent {
            CtTheme(reduceMotion = true) {
                AccountScreen(
                    state = AppUiState(), games = games, stats = stats, dueReviewCount = 2,
                    actions = AccountActions(onReviewMistakes = { reviewed = true }, onPracticeTheme = { practised = it }),
                    initialTab = AccountTab.TRAINING, training = training,
                )
            }
        }
        compose.onNodeWithText("Themes to work on").performScrollTo()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/account_training.png")
        compose.onNodeWithText("0% clean").performScrollTo().performClick()
        assertEquals("pin", practised)
        compose.onNodeWithText("Mistakes to review").performScrollTo().performClick()
        assertTrue(reviewed)
    }

    @Test
    fun settingsPageHoldsCoachBoardAccountEngineDataAndAbout() {
        var boardOpened = false
        var chat: com.chesstutor.app.domain.bot.ChatLevel? = null
        var depth: com.chesstutor.app.data.settings.ReviewDepth? = null
        var online: Boolean? = null
        var licences = false
        var crashCleared = false
        var erased = false
        compose.setContent {
            CtTheme(reduceMotion = true) {
                SettingsScreen(
                    state = AppUiState(),
                    accountActions = AccountActions(),
                    actions = SettingsActions(
                        onOpenBoardSettings = { boardOpened = true },
                        onChatLevel = { chat = it },
                        onReviewDepth = { depth = it },
                        onOnlineEngine = { online = it },
                        onOpenLicences = { licences = true },
                        crashLog = { if (crashCleared) "" else "=== Crash 2026-10-26 10:00:00 UTC ===\nboom\n" },
                        clearCrashLog = { crashCleared = true },
                        eraseEverything = { erased = true },
                        supportUrl = "",
                    ),
                )
            }
        }
        compose.onNodeWithText("No account linked. Link Chess.com or Lichess on the Account page to bring in your games.").assertExists()
        compose.onNodeWithText("Support the developer").assertDoesNotExist()
        compose.onNodeWithText("Export my games").performScrollTo().assertExists()
        compose.onNodeWithText("Board lab").assertDoesNotExist()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/account_settings.png")
        compose.onNodeWithText("Board and pieces").performScrollTo().performClick()
        assertTrue(boardOpened)
        compose.onNodeWithText("Lots").performScrollTo().performClick()
        assertEquals(com.chesstutor.app.domain.bot.ChatLevel.LOTS, chat)
        compose.onNodeWithText("Deep").performScrollTo().performClick()
        assertEquals(com.chesstutor.app.data.settings.ReviewDepth.DEEP, depth)
        compose.onNodeWithText("Online engine as a backup").performScrollTo().performClick()
        assertEquals(false, online)
        compose.onNodeWithText("The app crashed 1 time recently.", substring = true).performScrollTo().assertExists()
        compose.onNodeWithText("Reports of another move that works appear here.").assertExists()
        compose.onNodeWithText("Clear").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(crashCleared)
        compose.onNodeWithText("No crashes recorded on this phone.").assertExists()
        compose.onNodeWithText("Open-source licences").performScrollTo().performClick()
        assertTrue(licences)
        compose.onNodeWithText("Erase everything").performScrollTo().performClick()
        compose.onNodeWithText("Erase everything?").assertExists()
        compose.onNodeWithText("Erase").performClick()
        compose.waitForIdle()
        assertTrue(erased)
    }

    @Test
    fun settingsPicksTheCoachInASheetAndFlipsTheEverydaySwitches() {
        var mascot: com.chesstutor.app.domain.Mascot? = null
        var sound: Boolean? = null
        var reduceMotion: Boolean? = null
        compose.setContent {
            CtTheme(reduceMotion = true) {
                SettingsScreen(
                    state = AppUiState(),
                    accountActions = AccountActions(),
                    actions = SettingsActions(onMascot = { mascot = it }, onSound = { sound = it }, onReduceMotion = { reduceMotion = it }, supportUrl = ""),
                )
            }
        }
        compose.onNodeWithText("Sound").performScrollTo().performClick()
        assertEquals("sound starts on, so a tap turns it off", false, sound)
        compose.onNodeWithText("Reduce motion").performScrollTo().performClick()
        assertEquals(true, reduceMotion)
        compose.onNodeWithText("Coach").performScrollTo().performClick()
        compose.onNode(hasText("Sage") and hasClickAction()).performClick()
        compose.waitForIdle()
        assertEquals(com.chesstutor.app.domain.Mascot.SAGE, mascot)
        compose.onNodeWithText("Your coach").assertDoesNotExist()
    }

    @Test
    fun theLicencesPageCreditsEveryPartAndOpensLicenceTexts() {
        compose.setContent { CtTheme(reduceMotion = true) { LicencesScreen(onBack = {}) } }
        compose.onNodeWithText("Stockfish 19 chess engine").assertExists()
        compose.onNodeWithText("Kenney (kenney.nl)").performScrollTo().assertExists()
        compose.onNode(hasText("GNU General Public License v3.0") and hasClickAction()).performScrollTo().performClick()
        // The text is read from the app's assets off the main thread.
        compose.waitUntil(5_000) { compose.onAllNodesWithText("TERMS AND CONDITIONS", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun aLinkedAccountIsManagedInSettingsNotOnTheAccountPage() {
        val state = AppUiState(
            linkedProfile = LinkedChessProfile(RatingPlatform.LICHESS, "kenny", rapidRating = 1848, blitzRating = 1720, bulletRating = null, selectedTimeControl = RatingTimeControl.RAPID),
        )
        var page by mutableStateOf("account")
        var rating: RatingTimeControl? = null
        var imported = false
        compose.setContent {
            CtTheme(reduceMotion = true) {
                val actions = AccountActions(onRatingTimeControl = { rating = it }, onImportMore = { imported = true })
                if (page == "account") {
                    AccountScreen(state = state, games = games, stats = stats, dueReviewCount = 0, actions = actions)
                } else {
                    SettingsScreen(state = state, accountActions = actions, actions = SettingsActions(supportUrl = "https://example.org/support"))
                }
            }
        }
        compose.onNodeWithText("@kenny").assertExists()
        compose.onNodeWithText("Lichess, rapid").assertExists()
        compose.onNodeWithText("1848").assertExists()
        compose.onNodeWithText("Link your account").assertDoesNotExist()
        compose.onNodeWithContentDescription("Settings").assertExists()

        page = "settings"
        compose.onNodeWithText("@kenny on Lichess").assertExists()
        compose.onNodeWithText("Rapid 1848 · Blitz 1720 · Bullet –").assertExists()
        compose.onNodeWithText("Blitz").performClick()
        assertEquals(RatingTimeControl.BLITZ, rating)
        compose.onNodeWithText("Import 100 more games").performClick()
        assertTrue(imported)
        compose.onNodeWithText("Support the developer").assertExists()
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
