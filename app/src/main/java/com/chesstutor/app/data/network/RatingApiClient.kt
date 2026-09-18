package com.chesstutor.app.data.network

import com.chesstutor.app.data.model.LinkedChessProfile
import com.chesstutor.app.data.model.RatingPlatform
import com.chesstutor.app.data.model.RatingTimeControl
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
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
}
