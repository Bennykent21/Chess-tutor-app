package com.chesstutor.app.data.network

import com.chesstutor.app.data.model.ImportDepth
import com.chesstutor.app.data.model.RatingPlatform
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * A manual check of a real import, for diagnosing import problems: it talks to Chess.com, so
 * it only runs when LIVE_IMPORT_USER is set (never in CI). Example (PowerShell):
 * `$env:LIVE_IMPORT_USER="someone"; .\gradlew.bat testDebugUnitTest --tests "*LiveImportCheck*"`
 */
@RunWith(AndroidJUnit4::class)
class LiveImportCheck {

    @Test
    fun importsEveryMonth() = runBlocking {
        val user = System.getenv("LIVE_IMPORT_USER")
        assumeTrue("set LIVE_IMPORT_USER to run", !user.isNullOrBlank())
        var batches = 0
        val result = RatingApiClient().fetchRecentGames(
            platform = RatingPlatform.CHESS_COM,
            username = user!!,
            maxGames = ImportDepth.ALL.maxGames,
            onProgress = { println("progress: ${it.phase} · ${it.currentPeriod} · failed=${it.failedPeriods}") },
            onBatch = { batches++; println("batch ${it.size}") },
        )
        println("result: ${result.map { it.size }} batches=$batches error=${result.exceptionOrNull()} cause=${result.exceptionOrNull()?.cause}")
    }
}

/** The same, through the coordinator the Import button uses (saving each batch). */
@RunWith(AndroidJUnit4::class)
class LiveCoordinatorImportCheck {

    @Test
    fun importAllThroughTheCoordinator() = runBlocking {
        val user = System.getenv("LIVE_IMPORT_USER")
        assumeTrue("set LIVE_IMPORT_USER to run", !user.isNullOrBlank())
        val profile = com.chesstutor.app.data.model.LinkedChessProfile(RatingPlatform.CHESS_COM, user!!, 1800, 1700, 1600)
        val games = com.chesstutor.app.data.repository.InMemoryGameRepository()
        val state = kotlinx.coroutines.flow.MutableStateFlow(com.chesstutor.app.viewmodel.AppUiState())
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
        val coordinator = com.chesstutor.app.viewmodel.RatingLinkCoordinator(
            com.chesstutor.app.data.repository.InMemoryRatingRepository(profile), scope, games,
        )
        coordinator.refreshProfile(user, {}, { f -> state.value = f(state.value) }, ImportDepth.ALL)
        kotlinx.coroutines.delay(500)
        while (state.value.isLinkingLoading) kotlinx.coroutines.delay(500)
        println("coordinator: saved=${games.getRecentGames(5000).size} message=${state.value.linkingSuccessMessage} error=${state.value.linkingError}")
    }
}
