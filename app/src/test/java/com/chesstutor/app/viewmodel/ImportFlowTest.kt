package com.chesstutor.app.viewmodel

import com.chesstutor.app.data.model.ImportDepth
import com.chesstutor.app.data.model.ImportProgress
import com.chesstutor.app.data.model.RatingPlatform
import com.chesstutor.app.data.network.ImportKind
import com.chesstutor.app.data.network.ImportRequest
import com.chesstutor.app.data.network.ImportRunner
import com.chesstutor.app.data.network.ImportStatus
import com.chesstutor.app.data.repository.InMemoryReviewRepository
import com.chesstutor.app.data.settings.InMemorySettingsStore
import com.chesstutor.app.engine.ChessEngineManager
import com.chesstutor.app.engine.LocalFallbackEngineClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Imports run in the importer (WorkManager in the app); the screen follows its status. */
@OptIn(ExperimentalCoroutinesApi::class)
class ImportFlowTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Records what it was asked to import; the test decides how it goes. */
    private class FakeImporter : ImportRunner {
        val started = mutableListOf<ImportRequest>()
        override val status = MutableStateFlow<ImportStatus?>(null)
        override fun start(request: ImportRequest) {
            started += request
            status.value = ImportStatus.Running(request, ImportProgress(targetCount = request.maxGames, phase = "Downloading"))
        }
        override fun cancel() { status.value = null }
        override fun acknowledge() { if (status.value is ImportStatus.Finished) status.value = null }
        override suspend fun isRunning() = status.value is ImportStatus.Running
    }

    @Test
    fun linkingHandsTheImportToTheImporterAndShowsItsResult() = runTest(dispatcher) {
        val importer = FakeImporter()
        val settings = InMemorySettingsStore()
        val vm = AppViewModel(InMemoryReviewRepository(), ChessEngineManager(LocalFallbackEngineClient(), ioDispatcher = dispatcher), settingsStore = settings, importer = { importer })
        advanceUntilIdle()

        vm.linkRatingAccount(RatingPlatform.LICHESS, "kenny", importDepth = ImportDepth.LAST_500)
        advanceUntilIdle()
        val request = importer.started.single()
        assertEquals(ImportKind.LINK, request.kind)
        assertEquals(500, request.maxGames)
        assertTrue("the progress card shows", vm.state.value.isLinkingLoading)
        assertEquals(500, settings.settings.first().importTarget)

        importer.status.value = ImportStatus.Finished(request, imported = 480, incomplete = 0, failedPeriods = 0, error = null, saveFailed = false)
        advanceUntilIdle()
        val state = vm.state.value
        assertFalse(state.isLinkingLoading)
        assertEquals("Connected Lichess '@kenny' · Imported 480 games", state.linkingSuccessMessage)
        assertNull("a complete import forgets its target", settings.settings.first().importTarget)
        assertNull("the result is shown once", importer.status.value)
    }

    @Test
    fun aFailedImportSaysWhyAndKeepsTheTargetToCarryOn() {
        val request = ImportRequest(ImportKind.MORE, RatingPlatform.CHESS_COM, "kenny", 1500)
        val state = RatingLinkCoordinator.finishedState(
            AppUiState(isLinkingLoading = true),
            ImportStatus.Finished(request, imported = 0, incomplete = 0, failedPeriods = 0, error = "No internet", saveFailed = false),
        )
        assertEquals("Ratings updated, but your older games weren't imported. No internet", state.linkingError)
        assertFalse(ImportStatus.Finished(request, 0, 0, 0, "No internet", false).complete)
        assertFalse(ImportStatus.Finished(request, 10, 0, 2, null, false).complete)
    }
}
