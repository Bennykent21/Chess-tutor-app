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
        val update: ((com.chesstutor.app.viewmodel.AppUiState) -> com.chesstutor.app.viewmodel.AppUiState) -> Unit = { f -> state.value = f(state.value) }
        coordinator.observeImports(update) {}
        coordinator.refreshProfile(user, update, ImportDepth.ALL)
        kotlinx.coroutines.delay(500)
        while (state.value.isLinkingLoading) kotlinx.coroutines.delay(500)
        println("coordinator: saved=${games.getRecentGames(5000).size} message=${state.value.linkingSuccessMessage} error=${state.value.linkingError}")
    }
}

/**
 * Link, then "Import more", as a player does it, on both sites (opt-in, never in CI):
 * `$env:LIVE_IMPORT_USER="someone"; $env:LIVE_LICHESS_USER="someone"; .\gradlew.bat testDebugUnitTest --tests "*LiveLinkThenMoreCheck*"`
 * It prints how many games each step saved, how long it took, and any duplicates or gaps.
 */
@RunWith(AndroidJUnit4::class)
class LiveLinkThenMoreCheck {

    private fun run(platform: RatingPlatform, user: String) = runBlocking {
        val games = com.chesstutor.app.data.repository.InMemoryGameRepository()
        val state = kotlinx.coroutines.flow.MutableStateFlow(com.chesstutor.app.viewmodel.AppUiState())
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
        val ratings = com.chesstutor.app.data.repository.InMemoryRatingRepository(null)
        val coordinator = com.chesstutor.app.viewmodel.RatingLinkCoordinator(ratings, scope, games)
        val update: ((com.chesstutor.app.viewmodel.AppUiState) -> com.chesstutor.app.viewmodel.AppUiState) -> Unit = { f -> state.value = f(state.value) }
        coordinator.observeImports(update) {}
        suspend fun waitDone(label: String, started: Long) {
            kotlinx.coroutines.delay(300)
            while (state.value.isLinkingLoading) kotlinx.coroutines.delay(300)
            val all = games.getRecentGames(100_000)
            val ids = all.map { it.id }
            println("[$platform] $label: saved=${all.size} unique=${ids.toSet().size} in ${(System.currentTimeMillis() - started) / 1000}s " +
                "oldest=${all.minOfOrNull { it.dateMillis }?.let { java.time.Instant.ofEpochMilli(it) }} newest=${all.maxOfOrNull { it.dateMillis }?.let { java.time.Instant.ofEpochMilli(it) }} " +
                "message=${state.value.linkingSuccessMessage} error=${state.value.linkingError}")
            println("[$platform] $label by clock: " + all.groupBy { com.chesstutor.app.domain.account.TimeClass.of(it.timeClass) }.mapValues { it.value.size })
        }
        var t = System.currentTimeMillis()
        coordinator.linkAccount(platform, user, com.chesstutor.app.data.model.RatingTimeControl.RAPID, update, ImportDepth.LAST_100)
        waitDone("link, last 100", t)
        val first = games.getRecentGames(100_000).filter { it.source.name.contains(if (platform == RatingPlatform.LICHESS) "LICHESS" else "CHESS_COM") }
        t = System.currentTimeMillis()
        val cutoffs = first.groupBy { com.chesstutor.app.domain.account.TimeClass.of(it.timeClass) }.mapValues { (_, list) -> list.minOf { it.dateMillis } }
        coordinator.refreshProfile(user, update, ImportDepth.LAST_100, beforeMillis = first.minOfOrNull { it.dateMillis }, sinceMillis = first.maxOfOrNull { it.dateMillis }?.plus(1), clockCutoffs = cutoffs)
        waitDone("import 100 more", t)
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    /** The whole way to the real site with the phone's own lookup failing every time. */
    @Test
    fun theFallbackLookupReachesBothSites() = runBlocking {
        val chessCom = System.getenv("LIVE_IMPORT_USER")
        val lichess = System.getenv("LIVE_LICHESS_USER")
        assumeTrue("set LIVE_IMPORT_USER and LIVE_LICHESS_USER to run", !chessCom.isNullOrBlank() && !lichess.isNullOrBlank())
        val http = okhttp3.OkHttpClient.Builder()
            .dns(FallbackDns(system = object : okhttp3.Dns {
                override fun lookup(hostname: String): List<java.net.InetAddress> = throw java.net.UnknownHostException(hostname)
            }))
            .build()
        val client = RatingApiClient(http)
        println("Chess.com through the fallback: ${client.fetchChessComStats(chessCom!!)}")
        println("Lichess through the fallback: ${client.fetchLichessStats(lichess!!)}")
        val games = client.fetchRecentGames(RatingPlatform.CHESS_COM, chessCom, maxGames = 5).getOrThrow()
        org.junit.Assert.assertTrue("up to 5 of each clock, got ${games.size}", games.size in 5..25)
    }

    @Test
    fun chessCom() {
        val user = System.getenv("LIVE_IMPORT_USER")
        assumeTrue("set LIVE_IMPORT_USER to run", !user.isNullOrBlank())
        run(RatingPlatform.CHESS_COM, user!!)
    }

    @Test
    fun lichess() {
        val user = System.getenv("LIVE_LICHESS_USER")
        assumeTrue("set LIVE_LICHESS_USER to run", !user.isNullOrBlank())
        run(RatingPlatform.LICHESS, user!!)
    }
}
