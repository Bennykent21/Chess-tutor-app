package com.chesstutor.app.navigation

import com.chesstutor.app.domain.LearnCategory
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
    fun learnPagesKeepTheBottomBarButTheLessonPlayerHidesIt() {
        assertTrue(AppRoutes.showsBottomBar(AppRoutes.LEARN_CATEGORY_PATTERN))
        assertTrue(AppRoutes.showsBottomBar(AppRoutes.COURSE_PATTERN))
        assertFalse(AppRoutes.showsBottomBar(AppRoutes.LESSON))
        assertEquals(TopTab.LEARN, AppRoutes.tabFor(AppRoutes.COURSE_PATTERN))
        assertEquals("learn/category/tactics", AppRoutes.learnCategory("tactics"))
        assertEquals("learn/course/italian", AppRoutes.course("italian"))
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
    fun onboardingShowsOnlyOnceTheSavedProfileIsKnown() {
        // Before the profile is read, the default "NOT_STARTED" must not flash onboarding.
        assertEquals(StartScreen.LOADING, StartScreen.of(profileLoaded = false, assessmentState = "NOT_STARTED"))
        assertEquals(StartScreen.LOADING, StartScreen.of(profileLoaded = false, assessmentState = "COMPLETE"))
        assertEquals(StartScreen.ONBOARDING, StartScreen.of(profileLoaded = true, assessmentState = "NOT_STARTED"))
        assertEquals(StartScreen.ONBOARDING, StartScreen.of(profileLoaded = true, assessmentState = "IN_PROGRESS"))
        assertEquals(StartScreen.APP, StartScreen.of(profileLoaded = true, assessmentState = "COMPLETE"))
        assertFalse("a fresh state has not read the profile yet", AppUiState().profileLoaded)
    }

    @Test
    fun legacyTabIndicesMatchTheOldScreens() {
        assertEquals("the old puzzle trainer, Arena and Learn list are replaced (Phases 2 to 4)", listOf(0, 3), LegacyTab.ALL)
    }
}
