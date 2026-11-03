package com.chesstutor.app.data.network

import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.data.model.GameSource
import org.json.JSONObject

/** Lichess games, one JSON object per line, as the games export streams them. */
object LichessGames {
    fun parse(ndjson: String, username: String, maxGames: Int = Int.MAX_VALUE): List<GameRecord> {
        val records = mutableListOf<GameRecord>()
        for (line in ndjson.lineSequence()) {
            if (records.size >= maxGames) break
            val parsed = record(line, username) ?: continue
            records += parsed
        }
        return records
    }

    /** One standard game, or null for another variant, an aborted game or a line that does not read. */
    fun record(line: String, username: String): GameRecord? {
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

            val outcome = PgnMoves.toUci(
                if (movesSan.isNotBlank()) movesSan else PgnMoves.movesText(pgn)
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
}
