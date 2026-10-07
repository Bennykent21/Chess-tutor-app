package com.chesstutor.app.data.network

import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.data.model.LinkedChessProfile
import com.chesstutor.app.data.model.RatingPlatform
import com.chesstutor.app.data.model.RatingTimeControl
import com.chesstutor.app.domain.ChessPosition
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
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

class RatingApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
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
                .header("User-Agent", "ChessTutorAndroid/1.0 (contact: support@chesstutor.app)")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code == 404) {
                    return@withContext Result.failure(NoSuchElementException("Chess.com player '$username' not found."))
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
                .header("User-Agent", "ChessTutorAndroid/1.0 (contact: support@chesstutor.app)")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code == 404) {
                    return@withContext Result.failure(NoSuchElementException("Lichess user '$username' not found."))
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
     * Fetches recent public standard chess games from Lichess (NDJSON) or Chess.com (monthly archives)
     * and converts them into local [GameRecord] entries for offline statistics and review.
     */
    suspend fun fetchRecentGames(
        platform: RatingPlatform,
        username: String,
        maxGames: Int = 20
    ): Result<List<GameRecord>> = withContext(Dispatchers.IO) {
        val cleanUsername = username.trim()
        if (cleanUsername.isEmpty()) {
            return@withContext Result.success(emptyList())
        }
        runCatching {
            when (platform) {
                RatingPlatform.LICHESS -> fetchLichessGames(cleanUsername, maxGames)
                RatingPlatform.CHESS_COM -> fetchChessComGames(cleanUsername, maxGames)
            }
        }
    }

    private fun fetchLichessGames(username: String, maxGames: Int): List<GameRecord> {
        val encoded = URLEncoder.encode(username, "UTF-8")
        val url = "https://lichess.org/api/games/user/$encoded?max=$maxGames&opening=true&pgnInJson=true"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/x-ndjson")
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val body = resp.body?.string().orEmpty()
            return parseLichessNdjson(body, username)
        }
    }

    fun parseLichessNdjson(ndjson: String, username: String): List<GameRecord> {
        val records = mutableListOf<GameRecord>()
        for (line in ndjson.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            runCatching {
                val json = JSONObject(trimmed)
                val variant = json.optString("variant", "standard")
                if (variant != "standard") return@runCatching
                val id = json.optString("id", "").ifBlank { return@runCatching }
                val createdAt = json.optLong("createdAt", System.currentTimeMillis())
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
                val result = when (winner) {
                    "white" -> "1-0"
                    "black" -> "0-1"
                    else -> "1/2-1/2"
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

                val (uciMoves, finalFen, moveCount) = convertSanStreamToUci(
                    if (movesSan.isNotBlank()) movesSan else extractMovesFromPgn(pgn)
                )
                if (uciMoves.isNotBlank()) {
                    records += GameRecord(
                        id = "lichess_$id",
                        dateMillis = createdAt,
                        botName = oppName,
                        botRating = oppRating,
                        result = result,
                        pgn = enrichedPgn,
                        moveCount = moveCount,
                        userColor = if (userIsWhite) "white" else "black",
                        finalFen = finalFen,
                        uciMoves = uciMoves
                    )
                }
            }
        }
        return records
    }

    private fun fetchChessComGames(username: String, maxGames: Int): List<GameRecord> {
        val encoded = URLEncoder.encode(username.lowercase(), "UTF-8")
        val archivesUrl = "https://api.chess.com/pub/player/$encoded/games/archives"
        val archivesReq = Request.Builder()
            .url(archivesUrl)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        val latestArchiveUrl = client.newCall(archivesReq).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val body = resp.body?.string().orEmpty()
            val arr = JSONObject(body).optJSONArray("archives") ?: return emptyList()
            if (arr.length() == 0) return emptyList()
            arr.optString(arr.length() - 1, "")
        }
        if (latestArchiveUrl.isBlank()) return emptyList()

        val monthReq = Request.Builder()
            .url(latestArchiveUrl)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        client.newCall(monthReq).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val body = resp.body?.string().orEmpty()
            return parseChessComArchiveJson(body, username, maxGames)
        }
    }

    fun parseChessComArchiveJson(jsonStr: String, username: String, maxGames: Int = 20): List<GameRecord> {
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
            val whiteResult = whiteObj?.optString("result", "") ?: ""
            val blackResult = blackObj?.optString("result", "") ?: ""

            val userIsWhite = whiteUser.equals(username, ignoreCase = true)
            val oppName = if (userIsWhite) blackUser else whiteUser
            val oppRating = if (userIsWhite) blackRating else whiteRating

            val result = when {
                whiteResult == "win" -> "1-0"
                blackResult == "win" -> "0-1"
                else -> "1/2-1/2"
            }

            val endTimeSec = g.optLong("end_time", System.currentTimeMillis() / 1000L)
            val urlId = g.optString("url", "").substringAfterLast("/").ifBlank { "$endTimeSec" }

            val (uciMoves, finalFen, moveCount) = convertSanStreamToUci(extractMovesFromPgn(pgn))
            if (uciMoves.isNotBlank()) {
                records += GameRecord(
                    id = "chesscom_$urlId",
                    dateMillis = endTimeSec * 1000L,
                    botName = oppName,
                    botRating = oppRating,
                    result = result,
                    pgn = pgn,
                    moveCount = moveCount,
                    userColor = if (userIsWhite) "white" else "black",
                    finalFen = finalFen,
                    uciMoves = uciMoves
                )
            }
        }
        return records
    }

    fun extractMovesFromPgn(pgn: String): String {
        return pgn.lineSequence()
            .filter { !it.trim().startsWith("[") }
            .joinToString(" ")
            .replace(Regex("\\{[^}]*\\}"), " ")
            .replace(Regex("\\([^)]*\\)"), " ")
    }

    fun convertSanStreamToUci(sanStream: String): Triple<String, String, Int> {
        val pos = ChessPosition()
        val uciList = mutableListOf<String>()
        val tokens = sanStream
            .replace(Regex("\\d+\\.+"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        for (rawToken in tokens) {
            if (rawToken in setOf("1-0", "0-1", "1/2-1/2", "*")) break
            val clean = rawToken.trimEnd('+', '#', '!', '?')
            if (clean.isEmpty()) continue
            val match = pos.legalMoves.firstOrNull { choice ->
                val choiceClean = choice.san.trimEnd('+', '#')
                choiceClean == clean
            } ?: break
            if (!pos.play(match.uci)) break
            uciList += match.uci
        }
        return Triple(uciList.joinToString(" "), pos.fen, (uciList.size + 1) / 2)
    }

    companion object {
        private const val USER_AGENT = "ChessTutorAndroid/1.0 (https://github.com/Bennykent21/Chess-tutor)"
    }
}
