package com.chesstutor.app.data.network

import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.data.model.ImportDepth
import com.chesstutor.app.data.model.ImportProgress
import com.chesstutor.app.data.model.LinkedChessProfile
import com.chesstutor.app.data.model.RatingPlatform
import com.chesstutor.app.data.model.RatingTimeControl
import com.chesstutor.app.domain.account.TimeClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject

class RatingApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .dns(FallbackDns())
        .build(),
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
) {
    private val chessComAdapter = moshi.adapter(ChessComStatsDto::class.java)
    private val lichessAdapter = moshi.adapter(LichessUserDto::class.java)

    suspend fun fetchChessComStats(
        username: String,
        preferredTimeControl: RatingTimeControl = RatingTimeControl.RAPID
    ): Result<LinkedChessProfile> = withContext(Dispatchers.IO) {
        try {
            val cleanUser = username.trim().lowercase()
            if (cleanUser.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Username cannot be empty"))
            }

            val request = Request.Builder()
                .url("https://api.chess.com/pub/player/$cleanUser/stats")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            retryImportRequest("your Chess.com profile") { client.newCall(request).execute() }.use { response ->
                if (response.code == 404) {
                    return@withContext Result.failure(NoSuchElementException("Chess.com player '$username' not found."))
                }
                if (response.code == 429) {
                    return@withContext Result.failure(IOException("Chess.com rate limit reached. Please retry in a moment."))
                }
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Chess.com API returned HTTP ${response.code}"))
                }
                val bodyStr = response.body?.string()
                    ?: return@withContext Result.failure(IOException("Empty response from Chess.com"))

                val dto = chessComAdapter.fromJson(bodyStr)
                    ?: return@withContext Result.failure(IOException("Failed to parse Chess.com stats"))

                val rapid = dto.rapid?.last?.rating
                val blitz = dto.blitz?.last?.rating
                val bullet = dto.bullet?.last?.rating

                if (rapid == null && blitz == null && bullet == null) {
                    return@withContext Result.failure(IllegalStateException("No rated games found for '$username'."))
                }

                Result.success(
                    LinkedChessProfile(
                        platform = RatingPlatform.CHESS_COM,
                        username = username.trim(),
                        rapidRating = rapid,
                        blitzRating = blitz,
                        bulletRating = bullet,
                        selectedTimeControl = preferredTimeControl,
                        lastUpdated = System.currentTimeMillis()
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * The picture on a Chess.com profile, or a success with null when the player has none.
     * Lichess has no profile pictures.
     */
    suspend fun fetchChessComPicture(username: String): Result<ByteArray?> = withContext(Dispatchers.IO) {
        runCatching {
            val profile = Request.Builder()
                .url("https://api.chess.com/pub/player/${username.trim().lowercase()}")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()
            val picture = fetchWithRetry(profile, "your Chess.com profile") { resp ->
                JSONObject(resp.body?.string().orEmpty()).optString("avatar")
            }.takeIf { it.startsWith("https://") } ?: return@runCatching null
            val image = Request.Builder().url(picture).header("User-Agent", USER_AGENT).get().build()
            fetchWithRetry(image, "your Chess.com picture") { resp -> resp.body?.bytes() }
        }
    }

    suspend fun fetchLichessStats(
        username: String,
        preferredTimeControl: RatingTimeControl = RatingTimeControl.RAPID
    ): Result<LinkedChessProfile> = withContext(Dispatchers.IO) {
        try {
            val cleanUser = username.trim().lowercase()
            if (cleanUser.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Username cannot be empty"))
            }

            val request = Request.Builder()
                .url("https://lichess.org/api/user/$cleanUser")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            retryImportRequest("your Lichess profile") { client.newCall(request).execute() }.use { response ->
                if (response.code == 404) {
                    return@withContext Result.failure(NoSuchElementException("Lichess user '$username' not found."))
                }
                if (response.code == 429) {
                    return@withContext Result.failure(IOException("Lichess rate limit reached. Please wait ~45 seconds and retry."))
                }
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Lichess API returned HTTP ${response.code}"))
                }
                val bodyStr = response.body?.string()
                    ?: return@withContext Result.failure(IOException("Empty response from Lichess"))

                val dto = lichessAdapter.fromJson(bodyStr)
                    ?: return@withContext Result.failure(IOException("Failed to parse Lichess user"))

                val perfs = dto.perfs
                val rapid = perfs?.rapid?.rating
                val blitz = perfs?.blitz?.rating
                val bullet = perfs?.bullet?.rating

                if (rapid == null && blitz == null && bullet == null) {
                    return@withContext Result.failure(IllegalStateException("No rated games found for '$username' on Lichess."))
                }

                Result.success(
                    LinkedChessProfile(
                        platform = RatingPlatform.LICHESS,
                        username = dto.username ?: username.trim(),
                        rapidRating = rapid,
                        blitzRating = blitz,
                        bulletRating = bullet,
                        selectedTimeControl = preferredTimeControl,
                        lastUpdated = System.currentTimeMillis()
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Runs a GET with retries (timeouts, dropped connections, HTTP 429/5xx). Unrecoverable
     * responses such as 404 or 403 fail immediately. Every failure surfaces as an
     * [ImportFailedException] with a player-readable message instead of an empty result.
     */
    private suspend fun <T> fetchWithRetry(
        request: Request,
        what: String,
        parse: suspend (Response) -> T
    ): T = retryImportRequest(what) {
        client.newCall(request).execute().use { resp ->
            when {
                resp.isSuccessful -> parse(resp)
                resp.code == 429 || resp.code >= 500 -> throw RetryableHttpException(resp.code)
                else -> throw ImportFailedException(httpFailureMessage(what, resp.code))
            }
        }
    }

    /**
     * Fetches public standard chess games from Lichess (streaming NDJSON) or Chess.com
     * (paging backwards through monthly archives) up to [maxGames] of each clock, reporting progress
     * via [onProgress]. With [sinceMillis] only games from then on are fetched (the automatic sync);
     * with [beforeMillis] only older games, or with [clockCutoffs] only games older than their own
     * clock's cut-off, so each clock carries on from its own oldest game. [onBatch] gets
     * the games as they arrive (each Chess.com month, every [BATCH] Lichess games), so they can be
     * saved and shown while the import runs.
     */
    suspend fun fetchRecentGames(
        platform: RatingPlatform,
        username: String,
        maxGames: Int = ImportDepth.LAST_100.maxGames,
        onProgress: ((ImportProgress) -> Unit)? = null,
        sinceMillis: Long? = null,
        onBatch: (suspend (List<GameRecord>) -> Unit)? = null,
        beforeMillis: Long? = null,
        clockCutoffs: Map<TimeClass, Long> = emptyMap(),
    ): Result<List<GameRecord>> = withContext(Dispatchers.IO) {
        val cleanUsername = username.trim()
        if (cleanUsername.isEmpty()) {
            return@withContext Result.success(emptyList())
        }
        runCatching {
            when (platform) {
                RatingPlatform.LICHESS -> fetchLichessGames(cleanUsername, maxGames, onProgress, sinceMillis, onBatch, beforeMillis, clockCutoffs)
                RatingPlatform.CHESS_COM -> fetchChessComGames(cleanUsername, maxGames, onProgress, sinceMillis, onBatch, beforeMillis, clockCutoffs)
            }
        }
    }

    private suspend fun fetchLichessGames(
        username: String,
        maxGames: Int,
        onProgress: ((ImportProgress) -> Unit)?,
        sinceMillis: Long? = null,
        onBatch: (suspend (List<GameRecord>) -> Unit)? = null,
        beforeMillis: Long? = null,
        clockCutoffs: Map<TimeClass, Long> = emptyMap(),
    ): List<GameRecord> {
        val encoded = URLEncoder.encode(username, "UTF-8")
        val since = sinceMillis?.let { "&since=$it" }.orEmpty()
        val target = maxGames * MAIN_CLOCKS.size
        onProgress?.invoke(
            ImportProgress(
                importedCount = 0,
                targetCount = target,
                currentPeriod = "Lichess stream",
                phase = "Streaming games from Lichess..."
            )
        )

        val records = mutableListOf<GameRecord>()
        var failedStreams = 0
        var lastFailure: ImportFailedException? = null
        for ((clock, perfTypes) in LICHESS_CLOCKS) {
            currentCoroutineContext().ensureActive()
            val cutoff = if (clockCutoffs.isEmpty()) beforeMillis else clockCutoffs[clock]
            val until = cutoff?.let { "&until=${it - 1}" }.orEmpty()
            val url = "https://lichess.org/api/games/user/$encoded?max=$maxGames&perfType=$perfTypes&opening=true&pgnInJson=true$since$until"
            try {
                val stream = streamLichessGames(url, username, maxGames, records.size, target, onProgress, onBatch)
                records += stream.games
                if (stream.interrupted) failedStreams++
            } catch (e: ImportFailedException) {
                failedStreams++
                lastFailure = e
            }
        }
        // Nothing downloaded and something failed: say why instead of returning "0 games".
        if (records.isEmpty()) lastFailure?.let { throw it }

        onProgress?.invoke(
            ImportProgress(
                importedCount = records.size,
                targetCount = target,
                currentPeriod = "Complete",
                incompleteCount = records.count { it.incompleteParse },
                phase = "Imported ${records.size} games",
                failedPeriods = failedStreams,
                fraction = 1f,
            )
        )
        return records
    }

    private class LichessStream(val games: List<GameRecord>, val interrupted: Boolean)

    /** One clock's games: up to [maxGames], counted on from the [earlier] games of this import. */
    private suspend fun streamLichessGames(
        url: String,
        username: String,
        maxGames: Int,
        earlier: Int,
        target: Int,
        onProgress: ((ImportProgress) -> Unit)?,
        onBatch: (suspend (List<GameRecord>) -> Unit)?,
    ): LichessStream {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/x-ndjson")
            .get()
            .build()

        var interrupted = false
        val records = fetchWithRetry(req, "your Lichess games") { resp ->
            val reader = resp.body?.charStream()?.buffered()
                ?: return@fetchWithRetry emptyList<GameRecord>()
            val collected = mutableListOf<GameRecord>()
            val pending = mutableListOf<GameRecord>()
            var incompleteCount = 0
            try {
                reader.useLines { lines ->
                    for (line in lines) {
                        currentCoroutineContext().ensureActive()
                        if (collected.size >= maxGames) break
                        val parsed = LichessGames.record(line, username) ?: continue
                        if (parsed.incompleteParse) incompleteCount++
                        collected += parsed
                        pending += parsed
                        if (pending.size >= BATCH) {
                            onBatch?.invoke(pending.toList())
                            pending.clear()
                        }
                        if (collected.size % 5 == 0 || collected.size == 1) {
                            val soFar = earlier + collected.size
                            onProgress?.invoke(
                                ImportProgress(
                                    importedCount = soFar,
                                    targetCount = target,
                                    currentPeriod = "Lichess",
                                    incompleteCount = incompleteCount,
                                    phase = "Imported $soFar games..."
                                )
                            )
                        }
                    }
                }
            } catch (e: IOException) {
                // The stream broke part-way: keep what already arrived rather than losing it all.
                if (collected.isEmpty()) throw e
                interrupted = true
            }
            if (pending.isNotEmpty()) onBatch?.invoke(pending.toList())
            collected
        }
        return LichessStream(records, interrupted)
    }


    /**
     * Pages backwards through Chess.com monthly archives (newest -> oldest) until each clock the
     * account plays has [maxGames] standard chess games, or all archives are exhausted.
     *
     * Each request is retried. A month that still fails is skipped and counted in
     * [ImportProgress.failedPeriods]; if nothing at all could be downloaded the failure is thrown
     * so the player sees why, and after [MAX_CONSECUTIVE_FAILED_MONTHS] failures in a row the
     * import stops early (the connection is almost certainly down or rate-limited).
     */
    private suspend fun fetchChessComGames(
        username: String,
        maxGames: Int,
        onProgress: ((ImportProgress) -> Unit)?,
        sinceMillis: Long? = null,
        onBatch: (suspend (List<GameRecord>) -> Unit)? = null,
        beforeMillis: Long? = null,
        clockCutoffs: Map<TimeClass, Long> = emptyMap(),
    ): List<GameRecord> {
        // A sync looks at recent months only; anything else must not read a whole history for a clock never played.
        val quota = ClockQuota(maxGames, if (sinceMillis != null) MAIN_CLOCKS else clocksPlayed(username))
        val target = quota.target
        val encoded = URLEncoder.encode(username.lowercase(), "UTF-8")
        val archivesUrl = "https://api.chess.com/pub/player/$encoded/games/archives"
        val archivesReq = Request.Builder()
            .url(archivesUrl)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        onProgress?.invoke(
            ImportProgress(
                importedCount = 0,
                targetCount = target,
                currentPeriod = "Archives",
                phase = "Fetching Chess.com monthly archive index..."
            )
        )

        val archiveUrls = fetchWithRetry(archivesReq, "your Chess.com game list") { resp ->
            val body = resp.body?.string().orEmpty()
            val root = runCatching { JSONObject(body) }.getOrNull()
                ?: throw ImportFailedException("Chess.com sent an unreadable game list. Try again in a moment.")
            val arr = root.optJSONArray("archives")
            buildList {
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val u = arr.optString(i, "")
                        if (u.isNotBlank()) add(u)
                    }
                }
            }
        }
        if (archiveUrls.isEmpty()) return emptyList()

        val allRecords = mutableListOf<GameRecord>()
        val seenIds = mutableSetOf<String>()
        var incompleteCount = 0
        var failedPeriods = 0
        var consecutiveFailures = 0
        var lastFailure: ImportFailedException? = null

        // Newest month first, only those that can hold games in the wanted window.
        val latestCutoff = if (clockCutoffs.isEmpty()) beforeMillis else clockCutoffs.takeIf { it.keys.containsAll(MAIN_CLOCKS) }?.values?.max()
        val months = archiveUrls.asReversed().filter { url ->
            (sinceMillis == null || ChessComArchive.monthEnd(url)?.let { it >= sinceMillis } != false) &&
                (latestCutoff == null || ChessComArchive.monthStart(url)?.let { it < latestCutoff } != false)
        }
        var furthest = 0f

        /** How far along: by games wanted, or by months read when the account has fewer. Never falls back. */
        fun fraction(monthsDone: Float, games: Int = allRecords.size): Float {
            furthest = maxOf(furthest, games.toFloat() / target.coerceAtLeast(1), monthsDone / months.size.coerceAtLeast(1)).coerceAtMost(1f)
            return furthest
        }

        for ((monthIndex, monthUrl) in months.withIndex()) {
            currentCoroutineContext().ensureActive()
            if (quota.full) break
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILED_MONTHS) break
            val periodLabel = ChessComArchive.periodLabel(monthUrl)

            onProgress?.invoke(
                ImportProgress(
                    importedCount = allRecords.size,
                    targetCount = target,
                    currentPeriod = periodLabel,
                    incompleteCount = incompleteCount,
                    phase = "Importing $periodLabel (${allRecords.size} games)...",
                    failedPeriods = failedPeriods,
                    fraction = fraction(monthIndex.toFloat()),
                )
            )

            val monthReq = Request.Builder()
                .url(monthUrl)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            val monthGames = try {
                fetchWithRetry(monthReq, "your Chess.com games for $periodLabel") { resp ->
                    ChessComArchive.parse(resp.body?.string().orEmpty(), username, Int.MAX_VALUE, sinceMillis, beforeMillis, clockCutoffs, quota) { done, of ->
                        val read = minOf(allRecords.size + done, target)
                        onProgress?.invoke(
                            ImportProgress(
                                importedCount = read,
                                targetCount = target,
                                currentPeriod = periodLabel,
                                incompleteCount = incompleteCount,
                                phase = "Reading $periodLabel...",
                                failedPeriods = failedPeriods,
                                fraction = fraction(monthIndex + done.toFloat() / of, read),
                            )
                        )
                    }
                }.also { consecutiveFailures = 0 }
            } catch (e: ImportFailedException) {
                failedPeriods++
                consecutiveFailures++
                lastFailure = e
                emptyList()
            }

            val fresh = mutableListOf<GameRecord>()
            for (g in monthGames) {
                if (seenIds.add(g.id)) {
                    if (g.incompleteParse) incompleteCount++
                    allRecords += g
                    fresh += g
                }
            }
            if (fresh.isNotEmpty()) onBatch?.invoke(fresh)

            onProgress?.invoke(
                ImportProgress(
                    importedCount = allRecords.size,
                    targetCount = target,
                    currentPeriod = periodLabel,
                    incompleteCount = incompleteCount,
                    phase = "Imported ${allRecords.size} games ($periodLabel)",
                    failedPeriods = failedPeriods,
                    fraction = fraction(monthIndex + 1f),
                )
            )
        }

        // Nothing downloaded and something failed: say why instead of returning "0 games".
        if (allRecords.isEmpty()) lastFailure?.let { throw it }
        return allRecords
    }

    /** The clocks the account has a rating in, so a clock never played is not searched for. */
    private suspend fun clocksPlayed(username: String): Set<TimeClass> {
        val profile = fetchChessComStats(username, RatingTimeControl.RAPID).getOrNull() ?: return MAIN_CLOCKS
        return buildSet {
            if (profile.rapidRating != null) add(TimeClass.RAPID)
            if (profile.blitzRating != null) add(TimeClass.BLITZ)
            if (profile.bulletRating != null) add(TimeClass.BULLET)
        }.ifEmpty { MAIN_CLOCKS }
    }

    companion object {
        /** Lichess's game kinds, one stream for each clock. */
        private val LICHESS_CLOCKS = listOf(
            TimeClass.RAPID to "rapid",
            TimeClass.BLITZ to "blitz",
            TimeClass.BULLET to "bullet,ultraBullet",
            TimeClass.CLASSICAL to "classical",
            TimeClass.DAILY to "correspondence",
        )

        /** Lichess games handed over per [fetchRecentGames] batch while streaming. */
        const val BATCH = 20

        /** Stop a Chess.com import after this many months in a row fail (connection down / rate-limited). */
        private const val MAX_CONSECUTIVE_FAILED_MONTHS = 3
        /** Chess.com and Lichess ask API callers to say who they are and where to find them. */
        private val USER_AGENT = "ChessTutorAndroid/${com.chesstutor.app.BuildConfig.VERSION_NAME} (+https://github.com/Bennykent21/Chess-tutor-app)"
    }
}
