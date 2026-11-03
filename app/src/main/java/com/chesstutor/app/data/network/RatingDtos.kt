package com.chesstutor.app.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// What Chess.com and Lichess send for a player's ratings.

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
