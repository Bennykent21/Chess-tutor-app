package com.example.chess.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class ChessComArchivesResponse(
  @Json(name = "archives") val archives: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ChessComGamesResponse(
  @Json(name = "games") val games: List<ChessComGameItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ChessComPlayer(
  @Json(name = "username") val username: String? = null,
  @Json(name = "rating") val rating: Int? = null,
  @Json(name = "result") val result: String? = null
)

@JsonClass(generateAdapter = true)
data class ChessComGameItem(
  @Json(name = "url") val url: String? = null,
  @Json(name = "pgn") val pgn: String? = null,
  @Json(name = "time_control") val timeControl: String? = null,
  @Json(name = "end_time") val endTime: Long? = null,
  @Json(name = "rated") val rated: Boolean? = true,
  @Json(name = "time_class") val timeClass: String? = null,
  @Json(name = "rules") val rules: String? = null,
  @Json(name = "white") val white: ChessComPlayer? = null,
  @Json(name = "black") val black: ChessComPlayer? = null,
  @Json(name = "fen") val fen: String? = null
)

interface ChessComApiService {
  @Headers("User-Agent: ChessMasterCoachApp/1.0 (contact: user@chesscoach.app)")
  @GET("pub/player/{username}/games/archives")
  suspend fun getArchives(@Path("username") username: String): ChessComArchivesResponse

  @Headers("User-Agent: ChessMasterCoachApp/1.0 (contact: user@chesscoach.app)")
  @GET("{archivePath}")
  suspend fun getGamesForArchive(@Path("archivePath", encoded = true) archivePath: String): ChessComGamesResponse
}

object ChessComClient {
  private val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build()

  private val moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .build()

  private val retrofit = Retrofit.Builder()
    .baseUrl("https://api.chess.com/")
    .client(okHttpClient)
    .addConverterFactory(MoshiConverterFactory.create(moshi))
    .build()

  val api: ChessComApiService = retrofit.create(ChessComApiService::class.java)

  suspend fun fetchRecentGames(username: String): Result<List<ChessComGameItem>> = withContext(Dispatchers.IO) {
    try {
      val trimmedUser = username.trim().lowercase()
      val archivesResponse = api.getArchives(trimmedUser)
      val archives = archivesResponse.archives
      if (archives.isEmpty()) {
        return@withContext Result.success(emptyList())
      }

      // Take the most recent monthly archive URL
      val latestArchive = archives.last()
      // e.g. "https://api.chess.com/pub/player/erik/games/2024/04" -> "pub/player/erik/games/2024/04"
      val path = latestArchive.removePrefix("https://api.chess.com/")
      val gamesResponse = api.getGamesForArchive(path)
      Result.success(gamesResponse.games.reversed().take(20))
    } catch (e: Exception) {
      Result.failure(e)
    }
  }
}
