package com.chesstutor.app.data.network

import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.data.model.GameSource
import com.chesstutor.app.data.model.ImportDepth
import com.chesstutor.app.data.model.ImportProgress
import com.chesstutor.app.data.model.LinkedChessProfile
import com.chesstutor.app.data.model.RatingPlatform
import com.chesstutor.app.data.model.RatingTimeControl
import com.chesstutor.app.domain.ChessPosition
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class ChessComStatsDto(
    @param:Json(name = "chess_rapid") val rapid: ChessComCategoryDto? = null,
    @param:Json(name = "chess_blitz") val blitz: ChessComCategoryDto? = null,
    @param:Json(name = "chess_bullet") val bullet: ChessComCategoryDto? = null
)

@JsonClass(generateAdapter = true)
data class ChessComCategoryDto(
    @param:Json(name = "last") val last: ChessComLastDto? = null
)

@JsonClass(generateAdapter = true)
data class ChessComLastDto(
    @param:Json(name = "rating") val rating: Int? = null
)

@JsonClass(generateAdapter = true)
data class LichessUserDto(
    @param:Json(name = "id") val id: String? = null,
    @param:Json(name = "username") val username: String? = null,
    @param:Json(name = "perfs") val perfs: LichessPerfsDto? = null
)

@JsonClass(generateAdapter = true)
data class LichessPerfsDto(
    @param:Json(name = "rapid") val rapid: LichessPerfDto? = null,
    @param:Json(name = "blitz") val blitz: LichessPerfDto? = null,
    @param:Json(name = "bullet") val bullet: LichessPerfDto? = null
)

@JsonClass(generateAdapter = true)
data class LichessPerfDto(
    @param:Json(name = "rating") val rating: Int? = null,
    @param:Json(name = "games") val games: Int? = null
)

data class SanParseOutcome(
    val uciMoves: String,
    val finalFen: String,
    val moveCount: Int,
    val incomplete: Boolean
)

class RatingApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
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

            client.newCall(request).execute().use { response ->
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

            client.newCall(request).execute().use { response ->
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
     * (paging backwards through monthly archives) up to [maxGames], reporting progress via [onProgress].
     * With [sinceMillis] only games from then on are fetched (the automatic sync). [onBatch] gets
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
    ): Result<List<GameRecord>> = withContext(Dispatchers.IO) {
        val cleanUsername = username.trim()
        if (cleanUsername.isEmpty()) {
            return@withContext Result.success(emptyList())
        }
        runCatching {
            when (platform) {
                RatingPlatform.LICHESS -> fetchLichessGames(cleanUsername, maxGames, onProgress, sinceMillis, onBatch)
                RatingPlatform.CHESS_COM -> fetchChessComGames(cleanUsername, maxGames, onProgress, sinceMillis, onBatch)
            }
        }
    }

    private suspend fun fetchLichessGames(
        username: String,
        maxGames: Int,
        onProgress: ((ImportProgress) -> Unit)?,
        sinceMillis: Long? = null,
        onBatch: (suspend (List<GameRecord>) -> Unit)? = null,
    ): List<GameRecord> {
        val encoded = URLEncoder.encode(username, "UTF-8")
        val since = sinceMillis?.let { "&since=$it" }.orEmpty()
        val url = "https://lichess.org/api/games/user/$encoded?max=$maxGames&opening=true&pgnInJson=true$since"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/x-ndjson")
            .get()
            .build()

        onProgress?.invoke(
            ImportProgress(
                importedCount = 0,
                targetCount = maxGames,
                currentPeriod = "Lichess stream",
                phase = "Streaming games from Lichess..."
            )
        )

        var streamInterrupted = false
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
                        val parsed = parseSingleLichessJsonLine(line, username) ?: continue
                        if (parsed.incompleteParse) incompleteCount++
                        collected += parsed
                        pending += parsed
                        if (pending.size >= BATCH) {
                            onBatch?.invoke(pending.toList())
                            pending.clear()
                        }
                        if (collected.size % 5 == 0 || collected.size == 1) {
                            onProgress?.invoke(
                                ImportProgress(
                                    importedCount = collected.size,
                                    targetCount = maxGames,
                                    currentPeriod = "Lichess",
                                    incompleteCount = incompleteCount,
                                    phase = "Imported ${collected.size} games..."
                                )
                            )
                        }
                    }
                }
            } catch (e: IOException) {
                // The stream broke part-way: keep what already arrived rather than losing it all.
                if (collected.isEmpty()) throw e
                streamInterrupted = true
            }
            if (pending.isNotEmpty()) onBatch?.invoke(pending.toList())
            collected
        }

        onProgress?.invoke(
            ImportProgress(
                importedCount = records.size,
                targetCount = maxGames,
                currentPeriod = "Complete",
                incompleteCount = records.count { it.incompleteParse },
                phase = "Imported ${records.size} games",
                failedPeriods = if (streamInterrupted) 1 else 0
            )
        )
        return records
    }

    fun parseLichessNdjson(ndjson: String, username: String, maxGames: Int = Int.MAX_VALUE): List<GameRecord> {
        val records = mutableListOf<GameRecord>()
        for (line in ndjson.lineSequence()) {
            if (records.size >= maxGames) break
            val parsed = parseSingleLichessJsonLine(line, username) ?: continue
            records += parsed
        }
        return records
    }

    private fun parseSingleLichessJsonLine(line: String, username: String): GameRecord? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        return runCatching {
            val json = JSONObject(trimmed)
            val variant = json.optString("variant", "standard")
            if (variant != "standard") return@runCatching null

            // Check status so aborted/noStart games are never recorded as draws (Review 2 §2.1 & §4.7)
            val status = json.optString("status", "started").lowercase()
            if (status in setOf("aborted", "nostart", "no_start", "unknownfinish", "cheat", "created")) {
                return@runCatching null
            }

            val id = json.optString("id", "").ifBlank { return@runCatching null }
            val createdAt = json.optLong("createdAt", System.currentTimeMillis())
            val speed = json.optString("speed", "rapid").lowercase()
            val players = json.optJSONObject("players")
            val whiteObj = players?.optJSONObject("white")
            val blackObj = players?.optJSONObject("black")
            val whiteName = whiteObj?.optJSONObject("user")?.optString("name", "White") ?: "White"
            val blackName = blackObj?.optJSONObject("user")?.optString("name", "Black") ?: "Black"
            val whiteRating = whiteObj?.optInt("rating", 1200) ?: 1200
            val blackRating = blackObj?.optInt("rating", 1200) ?: 1200

            val userIsWhite = whiteName.equals(username, ignoreCase = true)
            val oppName = if (userIsWhite) blackName else whiteName
            val oppRating = if (userIsWhite) blackRating else whiteRating

            val winner = json.optString("winner", "")
            val result = when {
                winner == "white" -> "1-0"
                winner == "black" -> "0-1"
                status in setOf("draw", "stalemate", "outoftime", "timeout", "mate", "resign") -> "1/2-1/2"
                else -> "1/2-1/2"
            }

            val termination = when (status) {
                "mate" -> "checkmate"
                "resign" -> "resigned"
                "outoftime", "timeout" -> "timeout"
                "stalemate" -> "stalemate"
                "draw" -> "draw"
                else -> status
            }

            val movesSan = json.optString("moves", "")
            val pgn = json.optString("pgn", "")
            val openingObj = json.optJSONObject("opening")
            val openingName = openingObj?.optString("name", "") ?: ""
            val openingEco = openingObj?.optString("eco", "") ?: ""
            val enrichedPgn = buildString {
                if (openingName.isNotBlank() && !pgn.contains("[Opening ")) {
                    append("[Opening \"$openingName\"]\n")
                }
                if (openingEco.isNotBlank() && !pgn.contains("[ECO ")) {
                    append("[ECO \"$openingEco\"]\n")
                }
                append(if (pgn.isNotBlank()) pgn else movesSan)
            }

            val outcome = convertSanStreamToUciWithStatus(
                if (movesSan.isNotBlank()) movesSan else extractMovesFromPgn(pgn)
            )
            if (outcome.uciMoves.isBlank()) return@runCatching null

            GameRecord(
                id = "lichess_$id",
                dateMillis = createdAt,
                botName = oppName,
                botRating = oppRating,
                result = result,
                pgn = enrichedPgn,
                moveCount = outcome.moveCount,
                userColor = if (userIsWhite) "white" else "black",
                finalFen = outcome.finalFen,
                uciMoves = outcome.uciMoves,
                source = GameSource.LICHESS,
                termination = termination,
                timeClass = speed,
                incompleteParse = outcome.incomplete
            )
        }.getOrNull()
    }

    /**
     * Pages backwards through Chess.com monthly archives (newest -> oldest) until
     * [maxGames] standard chess games are collected or all archives are exhausted.
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
    ): List<GameRecord> {
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
                targetCount = maxGames,
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

        // Iterate newest -> oldest monthly archive
        for (idx in archiveUrls.size - 1 downTo 0) {
            currentCoroutineContext().ensureActive()
            if (allRecords.size >= maxGames) break
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILED_MONTHS) break
            val monthUrl = archiveUrls[idx]
            // Newest first, so once a month ended before [sinceMillis] every older one did too.
            if (sinceMillis != null && archiveMonthEnd(monthUrl)?.let { it < sinceMillis } == true) break
            val periodLabel = extractArchivePeriodLabel(monthUrl)

            onProgress?.invoke(
                ImportProgress(
                    importedCount = allRecords.size,
                    targetCount = maxGames,
                    currentPeriod = periodLabel,
                    incompleteCount = incompleteCount,
                    phase = "Importing $periodLabel (${allRecords.size} games)...",
                    failedPeriods = failedPeriods
                )
            )

            val remaining = maxGames - allRecords.size
            val monthReq = Request.Builder()
                .url(monthUrl)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            val monthGames = try {
                fetchWithRetry(monthReq, "your Chess.com games for $periodLabel") { resp ->
                    parseChessComArchiveJson(resp.body?.string().orEmpty(), username, remaining)
                }.also { consecutiveFailures = 0 }
            } catch (e: ImportFailedException) {
                failedPeriods++
                consecutiveFailures++
                lastFailure = e
                emptyList()
            }

            val fresh = mutableListOf<GameRecord>()
            for (g in monthGames) {
                if (sinceMillis != null && g.dateMillis < sinceMillis) continue
                if (seenIds.add(g.id)) {
                    if (g.incompleteParse) incompleteCount++
                    allRecords += g
                    fresh += g
                    if (allRecords.size >= maxGames) break
                }
            }
            if (fresh.isNotEmpty()) onBatch?.invoke(fresh)

            onProgress?.invoke(
                ImportProgress(
                    importedCount = allRecords.size,
                    targetCount = maxGames,
                    currentPeriod = periodLabel,
                    incompleteCount = incompleteCount,
                    phase = "Imported ${allRecords.size} games ($periodLabel)",
                    failedPeriods = failedPeriods
                )
            )
        }

        // Nothing downloaded and something failed: say why instead of returning "0 games".
        if (allRecords.isEmpty()) lastFailure?.let { throw it }
        return allRecords
    }

    /** When an archive month (".../2026/10") ends, in epoch millis (UTC), or null if the URL is unusual. */
    internal fun archiveMonthEnd(url: String): Long? {
        val parts = url.trimEnd('/').split('/')
        if (parts.size < 2) return null
        val year = parts[parts.size - 2].toIntOrNull() ?: return null
        val month = parts.last().toIntOrNull()?.takeIf { it in 1..12 } ?: return null
        return java.time.YearMonth.of(year, month).plusMonths(1).atDay(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    }

    private fun extractArchivePeriodLabel(url: String): String {
        val parts = url.trimEnd('/').split('/')
        return if (parts.size >= 2) {
            "${parts[parts.size - 2]}-${parts.last()}"
        } else {
            "Archive"
        }
    }

    fun parseChessComArchiveJson(jsonStr: String, username: String, maxGames: Int = 100): List<GameRecord> {
        val root = runCatching { JSONObject(jsonStr) }.getOrNull() ?: return emptyList()
        val gamesArr = root.optJSONArray("games") ?: return emptyList()
        val records = mutableListOf<GameRecord>()

        for (i in gamesArr.length() - 1 downTo 0) {
            if (records.size >= maxGames) break
            val g = gamesArr.optJSONObject(i) ?: continue
            if (g.optString("rules", "chess") != "chess") continue
            val pgn = g.optString("pgn", "")
            if (pgn.isBlank()) continue

            val whiteObj = g.optJSONObject("white")
            val blackObj = g.optJSONObject("black")
            val whiteUser = whiteObj?.optString("username", "White") ?: "White"
            val blackUser = blackObj?.optString("username", "Black") ?: "Black"
            val whiteRating = whiteObj?.optInt("rating", 1200) ?: 1200
            val blackRating = blackObj?.optInt("rating", 1200) ?: 1200
            val whiteResult = whiteObj?.optString("result", "")?.lowercase() ?: ""
            val blackResult = blackObj?.optString("result", "")?.lowercase() ?: ""

            if (whiteResult == "aborted" || blackResult == "aborted") continue

            val userIsWhite = whiteUser.equals(username, ignoreCase = true)
            val oppName = if (userIsWhite) blackUser else whiteUser
            val oppRating = if (userIsWhite) blackRating else whiteRating

            val result = when {
                whiteResult == "win" -> "1-0"
                blackResult == "win" -> "0-1"
                else -> "1/2-1/2"
            }

            val decisiveCode = if (whiteResult == "win") blackResult else if (blackResult == "win") whiteResult else whiteResult
            val termination = when (decisiveCode) {
                "checkmated" -> "checkmate"
                "resigned" -> "resigned"
                "timeout", "abandoned" -> "timeout"
                "stalemate" -> "stalemate"
                "repetition", "insufficient", "agreed", "50move", "timevsinsufficient" -> "draw"
                else -> decisiveCode
            }

            val timeClass = g.optString("time_class", "rapid").lowercase()
            val endTimeSec = g.optLong("end_time", System.currentTimeMillis() / 1000L)
            val urlId = g.optString("url", "").substringAfterLast("/").ifBlank { "$endTimeSec" }

            val outcome = convertSanStreamToUciWithStatus(extractMovesFromPgn(pgn))
            if (outcome.uciMoves.isNotBlank()) {
                records += GameRecord(
                    id = "chesscom_$urlId",
                    dateMillis = endTimeSec * 1000L,
                    botName = oppName,
                    botRating = oppRating,
                    result = result,
                    pgn = pgn,
                    moveCount = outcome.moveCount,
                    userColor = if (userIsWhite) "white" else "black",
                    finalFen = outcome.finalFen,
                    uciMoves = outcome.uciMoves,
                    source = GameSource.CHESS_COM,
                    termination = termination,
                    timeClass = timeClass,
                    incompleteParse = outcome.incomplete
                )
            }
        }
        return records
    }

    /**
     * Strips PGN headers, `{...}` block comments, `;...` line comments, and nested `(...)` RAVs.
     */
    fun extractMovesFromPgn(pgn: String): String {
        val withoutHeadersAndLineComments = pgn.lineSequence()
            .filter { !it.trim().startsWith("[") && !it.trim().startsWith("%") }
            .map { line -> line.substringBefore(";") }
            .joinToString(" ")

        // Strip {...} comments
        val withoutBraces = withoutBracesComments(withoutHeadersAndLineComments)
        // Strip nested (...) variations
        return withoutNestedVariations(withoutBraces)
    }

    private fun withoutBracesComments(input: String): String {
        val sb = StringBuilder(input.length)
        var depth = 0
        for (ch in input) {
            when (ch) {
                '{' -> depth++
                '}' -> if (depth > 0) depth--
                else -> if (depth == 0) sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun withoutNestedVariations(input: String): String {
        val sb = StringBuilder(input.length)
        var depth = 0
        for (ch in input) {
            when (ch) {
                '(' -> depth++
                ')' -> if (depth > 0) depth--
                else -> if (depth == 0) sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun convertSanStreamToUci(sanStream: String): Triple<String, String, Int> {
        val res = convertSanStreamToUciWithStatus(sanStream)
        return Triple(res.uciMoves, res.finalFen, res.moveCount)
    }

    /**
     * Tokenizes a SAN move stream (handling NAGs `$1`, move numbers `1.` / `1...`,
     * annotations `!`/`?`, and `0-0`/`0-0-0` castling), converts to UCI, and flags
     * `incomplete = true` if any token fails to match before the game result token.
     */
    fun convertSanStreamToUciWithStatus(sanStream: String): SanParseOutcome {
        val pos = ChessPosition()
        val uciList = mutableListOf<String>()
        var incomplete = false

        val normalized = withoutNestedVariations(withoutBracesComments(sanStream))
            .replace(Regex("\\$\\d+"), " ")
            .replace(Regex("\\d+\\.+"), " ")
            .trim()

        val tokens = normalized
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        for (rawToken in tokens) {
            if (rawToken in setOf("1-0", "0-1", "1/2-1/2", "*")) break
            // Skip standalone move number fragments or NAGs
            if (rawToken.startsWith("$") || rawToken.all { it.isDigit() || it == '.' }) continue

            val cleanedAnnotation = rawToken
                .replace("0-0-0", "O-O-O")
                .replace("0-0", "O-O")
                .trimEnd('+', '#', '!', '?')
                .trim()
            if (cleanedAnnotation.isEmpty()) continue

            val match = pos.legalMoves.firstOrNull { choice ->
                val choiceClean = choice.san.trimEnd('+', '#')
                choiceClean == cleanedAnnotation ||
                    choiceClean.replace("=", "") == cleanedAnnotation.replace("=", "")
            }

            if (match == null) {
                incomplete = true
                break
            }
            if (!pos.play(match.uci)) {
                incomplete = true
                break
            }
            uciList += match.uci
        }

        return SanParseOutcome(
            uciMoves = uciList.joinToString(" "),
            finalFen = pos.fen,
            moveCount = (uciList.size + 1) / 2,
            incomplete = incomplete
        )
    }

    companion object {
        /** Lichess games handed over per [fetchRecentGames] batch while streaming. */
        const val BATCH = 20

        /** Stop a Chess.com import after this many months in a row fail (connection down / rate-limited). */
        private const val MAX_CONSECUTIVE_FAILED_MONTHS = 3
        private const val USER_AGENT = "ChessTutorAndroid/1.0 (https://github.com/Bennykent21/Chess-tutor; contact: support@chesstutor.app)"
    }
}
