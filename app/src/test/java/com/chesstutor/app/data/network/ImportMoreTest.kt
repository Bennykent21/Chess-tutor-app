package com.chesstutor.app.data.network

import com.chesstutor.app.data.model.ImportDepth
import com.chesstutor.app.data.model.ImportProgress
import com.chesstutor.app.data.model.LinkedChessProfile
import com.chesstutor.app.data.model.RatingPlatform
import com.chesstutor.app.data.repository.InMemoryGameRepository
import com.chesstutor.app.data.repository.InMemoryRatingRepository
import com.chesstutor.app.data.repository.InMemoryReviewRepository
import com.chesstutor.app.data.settings.InMemorySettingsStore
import com.chesstutor.app.engine.ChessEngineManager
import com.chesstutor.app.engine.LocalFallbackEngineClient
import com.chesstutor.app.viewmodel.AppUiState
import com.chesstutor.app.viewmodel.AppViewModel
import com.chesstutor.app.viewmodel.RatingLinkCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

/**
 * "Import more" goes back from the oldest saved game (Phase 7 import fix): before, it asked
 * for the newest games again, so with 257 saved "Import 500" could bring nothing new.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ImportMoreTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Two games a month, August to October 2026, oldest first like Chess.com's archives. */
    private val months = linkedMapOf(
        "2026/08" to listOf("a1" to "2026-08-10", "a2" to "2026-08-20"),
        "2026/09" to listOf("s1" to "2026-09-10", "s2" to "2026-09-20"),
        "2026/10" to listOf("o1" to "2026-10-10", "o2" to "2026-10-20"),
    )

    private fun millis(day: String) = Instant.parse("${day}T12:00:00Z").toEpochMilli()

    private fun gameJson(id: String, day: String) = JSONObject()
        .put("url", "https://www.chess.com/game/live/$id")
        .put("end_time", millis(day) / 1000)
        .put("time_class", "rapid")
        .put("rules", "chess")
        .put("pgn", "1. e4 e5 2. Nf3 Nc6 3. Bb5 a6")
        .put("white", JSONObject().put("username", "kenny").put("rating", 1500).put("result", "win"))
        .put("black", JSONObject().put("username", "rival").put("rating", 1450).put("result", "resigned"))

    /** A [RatingApiClient] whose "Chess.com" is [months]; [requests] lists the URLs asked for. */
    private fun fakeChessCom(requests: MutableList<String> = mutableListOf()): RatingApiClient {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val url = chain.request().url.toString()
            requests += url
            val body = when {
                url.endsWith("/games/archives") ->
                    JSONObject().put("archives", JSONArray(months.keys.map { "https://api.chess.com/pub/player/kenny/games/$it" }))
                else -> {
                    val month = months.entries.first { url.endsWith(it.key) }
                    JSONObject().put("games", JSONArray(month.value.map { (id, day) -> gameJson(id, day) }))
                }
            }.toString()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        return RatingApiClient(http)
    }

    /** The October games, as if imported earlier. */
    private suspend fun repositoryWithOctober(client: RatingApiClient): InMemoryGameRepository {
        val games = InMemoryGameRepository()
        val october = client.parseChessComArchiveJson(
            JSONObject().put("games", JSONArray(months.getValue("2026/10").map { (id, day) -> gameJson(id, day) })).toString(),
            "kenny",
        )
        games.saveGames(october)
        return games
    }

    @Test
    fun importMoreBringsTheGamesBeforeTheOldestSavedOne() = runBlocking {
        val requests = mutableListOf<String>()
        val client = fakeChessCom(requests)
        val games = repositoryWithOctober(client)

        val request = ImportRequest(ImportKind.MORE, RatingPlatform.CHESS_COM, "kenny", maxGames = 3, beforeMillis = millis("2026-10-10"))
        val done = runImport(request, client, games) {}

        assertEquals(3, done.imported)
        assertEquals("every downloaded game is new", 3, done.added)
        assertEquals(
            "the three games just before October arrive",
            listOf("chesscom_a2", "chesscom_o1", "chesscom_o2", "chesscom_s1", "chesscom_s2"),
            games.getRecentGames(50).map { it.id }.sorted(),
        )
    }

    @Test
    fun theCountOnlyIncludesGamesThatWereNotSavedBefore() = runBlocking {
        val client = fakeChessCom()
        val games = repositoryWithOctober(client)

        // Without a starting point (the old behaviour) the saved October games use up the request.
        val done = runImport(ImportRequest(ImportKind.MORE, RatingPlatform.CHESS_COM, "kenny", maxGames = 4), client, games) {}

        assertEquals(4, done.imported)
        assertEquals(2, done.added)
        assertEquals("asking for 4 brought only 2 new games", 4, games.getRecentGames(50).size)
    }

    @Test
    fun theMessageSaysWhatWasAddedOrThatNothingOlderExists() {
        val older = ImportRequest(ImportKind.MORE, RatingPlatform.CHESS_COM, "kenny", 500, beforeMillis = 1L)
        fun message(added: Int, newGames: Int = 0) = RatingLinkCoordinator.finishedState(
            AppUiState(isLinkingLoading = true),
            ImportStatus.Finished(older, imported = added, incomplete = 0, failedPeriods = 0, error = null, saveFailed = false, added = added),
            newGames,
        ).linkingSuccessMessage

        assertEquals("No older games found for '@kenny': every game from Chess.com is here already", message(0))
        assertEquals("Added 243 older games for '@kenny'", message(243))
        assertEquals("Added 2 new games and 1 older game for '@kenny'", message(1, newGames = 2))
        assertEquals("Added 1 new game for '@kenny'", message(0, newGames = 1))
    }

    @Test
    fun theViewModelStartsFromTheOldestSavedGameAndAimsForTheNewTotal() = runTest(dispatcher) {
        val client = fakeChessCom()
        val games = repositoryWithOctober(client)
        val settings = InMemorySettingsStore()
        val importer = object : ImportRunner {
            val started = mutableListOf<ImportRequest>()
            override val status = MutableStateFlow<ImportStatus?>(null)
            override fun start(request: ImportRequest) {
                started += request
                status.value = ImportStatus.Running(request, ImportProgress(targetCount = request.maxGames))
            }
            override fun cancel() = Unit
            override fun acknowledge() = Unit
            override suspend fun isRunning() = false
        }
        val profile = LinkedChessProfile(RatingPlatform.CHESS_COM, "kenny", 1500, 1450, 1400)
        val vm = AppViewModel(
            InMemoryReviewRepository(),
            ChessEngineManager(LocalFallbackEngineClient()),
            ratingRepository = InMemoryRatingRepository(profile),
            gameRepository = games,
            settingsStore = settings,
            importer = { importer },
            ratingApiClient = client,
        )
        advanceUntilIdle()

        vm.refreshLinkedRating(ImportDepth.LAST_500)
        // The quick check for new games runs on the IO dispatcher.
        var waited = 0
        while (importer.started.isEmpty() && waited++ < 200) {
            advanceUntilIdle()
            Thread.sleep(20)
        }

        val request = importer.started.single()
        assertEquals(ImportKind.MORE, request.kind)
        assertEquals(500, request.maxGames)
        assertEquals("starts from the oldest saved game", millis("2026-10-10"), request.beforeMillis)
        assertEquals("saved games plus the 500 asked for", 502, settings.settings.first().importTarget)
    }
}
