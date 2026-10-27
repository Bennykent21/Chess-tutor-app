package com.chesstutor.app.navigation

import com.chesstutor.app.viewmodel.AppUiState
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
        assertFalse(AppRoutes.showsBottomBar("some/new/activity"))
    }

    @Test
    fun accountPagesHighlightTheAccountTab() {
        assertEquals(TopTab.ACCOUNT, AppRoutes.tabFor(AppRoutes.SETTINGS))
        assertEquals(TopTab.LEARN, AppRoutes.tabFor("learn"))
        assertNull(AppRoutes.tabFor(AppRoutes.MISTAKE_REVIEW))
    }

    @Test
    fun learnPagesKeepTheBottomBarButTheLessonPlayerHidesIt() {
        assertTrue(AppRoutes.showsBottomBar(AppRoutes.LEARN_CATEGORY_PATTERN))
        assertTrue(AppRoutes.showsBottomBar(AppRoutes.COURSE_PATTERN))
        assertFalse(AppRoutes.showsBottomBar(AppRoutes.LESSON))
        assertEquals(TopTab.LEARN, AppRoutes.tabFor(AppRoutes.COURSE_PATTERN))
        assertEquals("learn/category/tactics", AppRoutes.learnCategory("tactics"))
        assertEquals("learn/course/italian", AppRoutes.course("italian"))
        assertEquals("learn/open/italian-1", AppRoutes.openLesson("italian-1"))
    }

    @Test
    fun gamePagesBelongToTheAccountTab() {
        assertEquals("account/game/chesscom_123", AppRoutes.game("chesscom_123"))
        assertEquals("account/game/a%2Fb", AppRoutes.game("a/b"))
        assertTrue(AppRoutes.showsBottomBar(AppRoutes.GAME_PATTERN))
        assertEquals(TopTab.ACCOUNT, AppRoutes.tabFor(AppRoutes.GAME_PATTERN))
        assertEquals("account/review/lichess_x", AppRoutes.review("lichess_x"))
        assertEquals("account/review/bot_x?analyse=true", AppRoutes.review("bot_x", analyse = true))
        assertFalse("the review is a board activity", AppRoutes.showsBottomBar(AppRoutes.REVIEW_PATTERN))
        assertTrue(AppRoutes.showsBottomBar(AppRoutes.SETTINGS))
        assertEquals(TopTab.ACCOUNT, AppRoutes.tabFor(AppRoutes.SETTINGS))
        assertFalse("the mistakes review is a board activity", AppRoutes.showsBottomBar(AppRoutes.MISTAKE_REVIEW))
    }

    @Test
    fun relatedPuzzlesRouteCarriesTheThemes() {
        val route = AppRoutes.puzzleRelated(listOf("pin", "skewer"))
        assertEquals("puzzles/related/pin%2Cskewer", route)
        assertEquals(listOf("pin", "skewer"), AppRoutes.parseThemes(java.net.URLDecoder.decode(route.substringAfterLast('/'), "UTF-8")))
        assertEquals(emptyList<String>(), AppRoutes.parseThemes(null))
    }

    @Test
    fun theAppWaitsForTheSavedProfileThenWelcomesNewPlayersOnHome() {
        assertEquals(StartScreen.LOADING, StartScreen.of(profileLoaded = false))
        assertEquals(StartScreen.APP, StartScreen.of(profileLoaded = true))
        assertFalse("a fresh state has not read the profile yet", AppUiState().profileLoaded)
        assertTrue(needsWelcome("NOT_STARTED"))
        assertTrue(needsWelcome("IN_PROGRESS"))
        assertFalse(needsWelcome("COMPLETE"))
        assertEquals(listOf(400, 800, 1200), StartingLevel.entries.map { it.rating })
    }
}
