package com.chesstutor.app.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.core.Position
import com.chesstutor.app.data.settings.AppSettings
import com.chesstutor.app.domain.bot.BotDraft
import com.chesstutor.app.domain.bot.BotRoster
import com.chesstutor.app.domain.learn.LessonLibrary
import com.chesstutor.app.domain.learn.LessonParser
import com.chesstutor.app.domain.opening.OpeningTrainer
import com.chesstutor.app.domain.opening.OpeningTree
import com.chesstutor.app.domain.puzzle.PuzzleStats
import com.chesstutor.app.feature.account.AccountActions
import com.chesstutor.app.feature.account.AccountScreen
import com.chesstutor.app.feature.account.BoardSettingsActions
import com.chesstutor.app.feature.account.BoardSettingsScreen
import com.chesstutor.app.feature.account.DesignGalleryScreen
import com.chesstutor.app.feature.account.GamesState
import com.chesstutor.app.feature.account.LicencesScreen
import com.chesstutor.app.feature.account.SettingsActions
import com.chesstutor.app.feature.account.SettingsScreen
import com.chesstutor.app.feature.analysis.AnalysisScreen
import com.chesstutor.app.feature.home.BotBuilderScreen
import com.chesstutor.app.feature.home.BotSetupScreen
import com.chesstutor.app.feature.home.ExploreDraft
import com.chesstutor.app.feature.home.ExploreSetupScreen
import com.chesstutor.app.feature.home.HomeActions
import com.chesstutor.app.feature.home.HomeScreen
import com.chesstutor.app.feature.home.HomeUiState
import com.chesstutor.app.feature.learn.CourseScreen
import com.chesstutor.app.feature.learn.LearnHubActions
import com.chesstutor.app.feature.learn.LearnScreen
import com.chesstutor.app.feature.learn.LearnState
import com.chesstutor.app.feature.learn.OpeningTrainerScreen
import com.chesstutor.app.feature.learn.TrainerOpeningState
import com.chesstutor.app.feature.learn.TrainerState
import com.chesstutor.app.feature.puzzles.PuzzleModeActions
import com.chesstutor.app.feature.puzzles.PuzzleModesScreen
import com.chesstutor.app.feature.puzzles.PuzzlesHomeScreen
import com.chesstutor.app.feature.puzzles.PuzzlesHomeState
import com.chesstutor.app.viewmodel.AppUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Every tappable thing on the main screens has words for a screen reader and is at least
 * 48 dp across (plan Phase 6D). Fails with the list of problems.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w400dp-h3000dp-xxhdpi")
class AccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    private val library by lazy {
        LessonLibrary(File("src/main/assets/lessons").listFiles()!!.filter { it.name.endsWith(".json") }.map { LessonParser.parseCourse(it.readText(), it.name) })
    }
    private val tree by lazy { File("src/main/assets/openings.tsv.gz").inputStream().use(OpeningTree::fromStream) }

    private fun audit(name: String, content: @Composable () -> Unit): List<String> {
        compose.setContent { CtTheme(reduceMotion = true) { content() } }
        compose.waitForIdle()
        assertTrue("$name has tappable items to check", compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().isNotEmpty())
        return A11yAudit.problems(compose, compose.density).map { "$name: $it" }
    }

    @Test
    fun theAuditCatchesAnUnlabelledButton() {
        val problems = audit("Bad") { Box(Modifier.size(20.dp).clickable {}) }
        assertTrue(problems.toString(), problems.single().contains("no label"))
    }

    /** Compose widens the touch area of a small tappable item to 48 dp by itself, so a small icon is still easy to hit. */
    @Test
    fun aSmallButtonStillGetsAFingerSizedTouchArea() {
        val problems = audit("Small") { Box(Modifier.size(20.dp).clickable(onClickLabel = "Close") {}) }
        assertEquals(emptyList<String>(), problems)
    }

    private fun check(name: String, content: @Composable () -> Unit) {
        val problems = audit(name, content)
        assertEquals(problems.joinToString("\n"), emptyList<String>(), problems)
    }

    private val noHome = HomeActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {})

    /** Wide enough that the sideways "More ways to play" row is all on screen. */
    @Config(qualifiers = "w600dp-h3000dp-xxhdpi")
    @Test fun home() = check("Home") { HomeScreen(state = AppUiState(), home = HomeUiState(), actions = noHome) }

    /** Every design-system component at once (Phase 7C2c), on a screen big enough to show all of it. */
    @Config(qualifiers = "w600dp-h6000dp-xxhdpi")
    @Test fun designGallery() = check("Design gallery") { DesignGalleryScreen(onBack = {}) }

    @Test fun learnHub() = check("Learn") { LearnScreen(learn = LearnState(loading = false, library = library), actions = LearnHubActions({}, {})) }

    @Test fun coursePage() = check("Course") {
        CourseScreen(course = library.course("tactics-forks"), progress = emptyMap(), onOpenLesson = {}, onBack = {}, onRelatedPuzzles = {}, onPracticeSet = {})
    }

    @Test fun openingTrainer() = check("Opening trainer") {
        OpeningTrainerScreen(TrainerState(false, OpeningTrainer.openings.map { TrainerOpeningState(it, OpeningTrainer.linesFor(it, tree), 0, 0, 0) }), onOpen = {}, onBack = {})
    }

    @Test fun puzzlesHome() = check("Puzzles") {
        PuzzlesHomeScreen(PuzzlesHomeState(loading = false, stats = PuzzleStats()), 0, onPlayLevel = {}, onOpenModes = {}, onReviewMistakes = {})
    }

    @Test fun puzzleModes() = check("Puzzle modes") { PuzzleModesScreen(patternsFor = { emptyList() }, actions = PuzzleModeActions({}, {}, { _, _, _ -> }, { _, _, _, _ -> }, {}), onBack = {}) }

    @Test fun account() = check("Account") {
        AccountScreen(state = AppUiState(), games = GamesState(loading = false), stats = PuzzleStats(), dueReviewCount = 0, actions = AccountActions())
    }

    @Test fun settings() = check("Settings") { SettingsScreen(state = AppUiState(), accountActions = AccountActions(), actions = SettingsActions(supportUrl = "")) }

    @Test fun boardSettings() = check("Board settings") { BoardSettingsScreen(settings = AppSettings(), actions = BoardSettingsActions({}, {}, {}, {}, {}, {}), onBack = {}) }

    @Test fun licences() = check("Licences") { LicencesScreen(onBack = {}) }

    @Test fun explore() = check("Explore") { AnalysisScreen(startFen = Position.STARTING_FEN, settings = AppSettings(), engine = null, onFeedback = {}, onBack = {}) }

    @Test fun exploreSetup() = check("Explore setup") { ExploreSetupScreen(draft = ExploreDraft(), theme = AppSettings().boardTheme, onChange = {}, onStart = {}, onBack = {}) }

    @Test fun botSetup() = check("Bot page") { BotSetupScreen(bot = BotRoster.bots.first(), onPlay = { _, _ -> }, onBack = {}) }

    @Test fun botBuilder() = check("Bot builder") { BotBuilderScreen(draft = BotDraft(), onChange = {}, onSave = {}, onBack = {}) }
}
