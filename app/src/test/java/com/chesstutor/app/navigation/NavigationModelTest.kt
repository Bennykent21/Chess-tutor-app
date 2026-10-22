package com.chesstutor.app.navigation

import com.chesstutor.app.domain.LearnCategory
import com.chesstutor.app.domain.LearnCurriculumRepository
import com.chesstutor.app.feature.learn.LearnHubModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationModelTest {

    @Test
    fun fourTabsInPlanOrderWithUniqueRoutes() {
        assertEquals(listOf("Home", "Learn", "Puzzles", "Account"), TopTab.entries.map { it.label })
        val routes = TopTab.entries.flatMap { listOf(it.graphRoute, it.startRoute) }
        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun bottomBarShowsOnPagesAndHidesOnFullScreenActivities() {
        TopTab.entries.forEach { assertTrue(AppRoutes.showsBottomBar(it.startRoute)) }
        assertTrue(AppRoutes.showsBottomBar(AppRoutes.ENGINE_HEALTH))
        assertFalse(AppRoutes.showsBottomBar(AppRoutes.LEGACY_PATTERN))
        assertFalse(AppRoutes.showsBottomBar("some/new/activity"))
    }

    @Test
    fun accountPagesHighlightTheAccountTab() {
        assertEquals(TopTab.ACCOUNT, AppRoutes.tabFor(AppRoutes.DEVELOPER_MENU))
        assertEquals(TopTab.LEARN, AppRoutes.tabFor("learn"))
        assertNull(AppRoutes.tabFor(AppRoutes.LEGACY_PATTERN))
    }

    @Test
    fun legacyRouteCarriesTheLearnCategory() {
        assertEquals("legacy", AppRoutes.legacy())
        val route = AppRoutes.legacy(LearnCategory.ENDGAME)
        assertEquals("legacy?category=ENDGAME", route)
        assertEquals(LearnCategory.ENDGAME, AppRoutes.parseCategory(route.substringAfter("=")))
        assertNull(AppRoutes.parseCategory("NOT_A_CATEGORY"))
        assertNull(AppRoutes.parseCategory(null))
    }

    @Test
    fun legacyTabIndicesMatchTheOldScreens() {
        assertEquals("the old puzzle trainer is replaced by the Puzzles tab", listOf(0, 1, 2, 3), LegacyTab.ALL)
    }
}

class LearnHubModelTest {

    private val topics = LearnCurriculumRepository.topics

    @Test
    fun sixTilesWithBasicsComingInPhase4() {
        val tiles = LearnHubModel.tiles(topics, emptySet(), emptySet())
        assertEquals(listOf("Basics", "Openings", "Tactics", "Middlegame", "Endgames", "Blunders"), tiles.map { it.title })
        val basics = tiles.first()
        assertFalse(basics.isAvailable)
        assertEquals(LearnHubModel.BASICS_PHASE, basics.comingInPhase)
        assertTrue(tiles.drop(1).all { it.isAvailable })
        // Every existing lesson appears in exactly one tile.
        assertEquals(topics.size, tiles.sumOf { it.totalLessons })
    }

    @Test
    fun countsCompletedAndStartedLessonsPerCategory() {
        val openings = topics.filter { it.category == LearnCategory.OPENING }
        val mastered = setOf(openings[0].id)
        val practiced = setOf(openings[0].id, openings[1].id)

        val tile = LearnHubModel.tiles(topics, mastered, practiced).first { it.key == "openings" }

        assertEquals(1, tile.completedLessons)
        assertEquals(1, tile.startedLessons) // mastered lessons are not also "started"
        assertEquals(1f / openings.size, tile.progress, 0.0001f)
        assertEquals(1f / topics.size, LearnHubModel.overallProgress(LearnHubModel.tiles(topics, mastered, practiced)), 0.0001f)
    }
}
