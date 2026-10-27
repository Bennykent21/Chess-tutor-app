package com.chesstutor.app.viewmodel

import com.chesstutor.app.data.model.ImportDepth
import com.chesstutor.app.data.model.LearningProfile
import com.chesstutor.app.data.repository.InMemoryLearningRepository
import com.chesstutor.app.data.repository.InMemoryReviewRepository
import com.chesstutor.app.data.settings.InMemorySettingsStore
import com.chesstutor.app.domain.Mascot
import com.chesstutor.app.domain.ReviewItem
import com.chesstutor.app.engine.ChessEngineManager
import com.chesstutor.app.engine.LocalFallbackEngineClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun review(id: String) = ReviewItem(
        id = id, fen = "8/8/8/8/8/8/8/K6k w - - 0 1", dueAt = Instant.EPOCH, stage = 0, attempts = 0,
        mistakeUci = "a1a2", bestMoveUci = "a1b1", explanation = "", intervalDays = 1,
    )

    @Test
    fun readsTheProfileAndMistakesThenSavesTheWelcomeAnswer() = runTest(dispatcher) {
        val learning = InMemoryLearningRepository(LearningProfile(estimatedRating = 700))
        val reviews = InMemoryReviewRepository().apply { upsert(review("a")) }
        val vm = AppViewModel(reviews, ChessEngineManager(LocalFallbackEngineClient()), learningRepository = learning)
        assertFalse("nothing read yet", vm.state.value.profileLoaded)
        advanceUntilIdle()

        val loaded = vm.state.value
        assertTrue(loaded.profileLoaded)
        assertEquals(700, loaded.effectiveUserRating)
        assertEquals("NOT_STARTED", loaded.assessmentState)
        assertEquals(listOf("a"), loaded.reviews.map { it.id })

        vm.chooseStartingLevel(1200)
        reviews.upsert(review("b"))
        vm.refreshReviews()
        advanceUntilIdle()
        assertEquals(1200, vm.state.value.effectiveUserRating)
        assertEquals("COMPLETE", learning.getProfile().assessmentState)
        assertEquals(1200, learning.getProfile().estimatedRating)
        assertEquals(2, vm.state.value.reviews.size)
    }

    @Test
    fun settingsChangesShowAtOnceAndAreSaved() = runTest(dispatcher) {
        val store = InMemorySettingsStore()
        val vm = AppViewModel(InMemoryReviewRepository(), ChessEngineManager(LocalFallbackEngineClient()), settingsStore = store)
        advanceUntilIdle()
        vm.setMascot(Mascot.SAGE)
        vm.setSoundEnabled(false)
        assertEquals(Mascot.SAGE, vm.state.value.appSettings.mascot)
        advanceUntilIdle()
        val saved = store.settings.first()
        assertEquals(Mascot.SAGE.id, saved.mascotId)
        assertFalse(saved.soundEnabled)

        vm.setImportDepth(ImportDepth.ALL)
        assertEquals(ImportDepth.ALL, vm.state.value.selectedImportDepth)
    }

    @Test
    fun autoSyncNeedsALinkedAccount() = runTest(dispatcher) {
        val vm = AppViewModel(InMemoryReviewRepository(), ChessEngineManager(LocalFallbackEngineClient()))
        advanceUntilIdle()
        vm.autoSyncLinkedAccount(now = 10 * AppViewModel.AUTO_SYNC_INTERVAL_MS)
        advanceUntilIdle()
        assertFalse(vm.state.value.isAutoSyncing)
        assertFalse(vm.state.value.isLinkingLoading)
    }
}
