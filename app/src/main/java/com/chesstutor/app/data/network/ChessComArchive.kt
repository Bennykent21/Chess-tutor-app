package com.chesstutor.app.data.network

import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.data.model.GameSource
import com.chesstutor.app.domain.account.GameLibrary
import com.chesstutor.app.domain.account.TimeClass
import java.util.stream.Collectors
import org.json.JSONObject

/** Chess.com's monthly game archives: which month a URL holds, and the games in one month. */
object ChessComArchive {
    /** When an archive month (".../2026/10") starts, in epoch millis (UTC). */
    fun monthStart(url: String): Long? = monthEnd(url)?.let {
        java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).minusMonths(1).toInstant().toEpochMilli()
    }

    /** When an archive month (".../2026/10") ends, in epoch millis (UTC), or null if the URL is unusual. */
    fun monthEnd(url: String): Long? {
        val parts = url.trimEnd('/').split('/')
        if (parts.size < 2) return null
        val year = parts[parts.size - 2].toIntOrNull() ?: return null
        val month = parts.last().toIntOrNull()?.takeIf { it in 1..12 } ?: return null
        return java.time.YearMonth.of(year, month).plusMonths(1).atDay(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    }

    /** "2026-10", for the import's progress line. */
    fun periodLabel(url: String): String {
        val parts = url.trimEnd('/').split('/')
        return if (parts.size >= 2) {
            "${parts[parts.size - 2]}-${parts.last()}"
        } else {
            "Archive"
        }
    }

    /**
     * The games of one month, newest first, up to [maxGames]. Games outside [sinceMillis] and
     * [beforeMillis] are dropped before the limit is counted: they used to use it up, so "Import
     * more" in a half-imported month skipped its older games. [clockCutoffs], when given, replaces
     * [beforeMillis] clock by clock. [quota], when given, decides which games are still wanted and
     * counts the ones taken. [onScanned] hears (done, of).
     */
    fun parse(
        jsonStr: String,
        username: String,
        maxGames: Int = 100,
        sinceMillis: Long? = null,
        beforeMillis: Long? = null,
        clockCutoffs: Map<TimeClass, Long> = emptyMap(),
        quota: ClockQuota? = null,
        onScanned: ((done: Int, of: Int) -> Unit)? = null,
    ): List<GameRecord> {
        val root = runCatching { JSONObject(jsonStr) }.getOrNull() ?: return emptyList()
        val gamesArr = root.optJSONArray("games") ?: return emptyList()
        val wanted = ArrayList<JSONObject>()
        for (i in gamesArr.length() - 1 downTo 0) {
            val g = gamesArr.optJSONObject(i) ?: continue
            if (g.optString("rules", "chess") != "chess") continue
            val endMillis = g.optLong("end_time", System.currentTimeMillis() / 1000L) * 1000L
            if (sinceMillis != null && endMillis < sinceMillis) continue
            val clock = TimeClass.of(g.optString("time_class", "rapid"))
            if (quota != null && !quota.wants(clock)) continue
            val cutoff = if (clockCutoffs.isEmpty()) beforeMillis else clockCutoffs[clock]
            if (cutoff != null && endMillis >= cutoff) continue
            wanted += g
        }

        val records = ArrayList<GameRecord>()
        var done = 0
        while (done < wanted.size && records.size < maxGames && quota?.full != true) {
            val chunk = wanted.subList(done, minOf(done + PARSE_CHUNK, wanted.size))
            val parsed = chunk.parallelStream().map { listOfNotNull(record(it, username)) }.collect(Collectors.toList()).flatten()
            records += parsed.filter { quota?.take(TimeClass.of(it.timeClass)) != false }.take(maxGames - records.size)
            done += chunk.size
            onScanned?.invoke(done, wanted.size)
        }
        return records
    }

    /** One Chess.com game, or null when it was aborted or has no moves to read. */
    private fun record(g: JSONObject, username: String): GameRecord? {
        val pgn = g.optString("pgn", "")
        if (pgn.isBlank()) return null

        val whiteObj = g.optJSONObject("white")
        val blackObj = g.optJSONObject("black")
        val whiteUser = whiteObj?.optString("username", "White") ?: "White"
        val blackUser = blackObj?.optString("username", "Black") ?: "Black"
        val whiteRating = whiteObj?.optInt("rating", 1200) ?: 1200
        val blackRating = blackObj?.optInt("rating", 1200) ?: 1200
        val whiteResult = whiteObj?.optString("result", "")?.lowercase() ?: ""
        val blackResult = blackObj?.optString("result", "")?.lowercase() ?: ""

        if (whiteResult == "aborted" || blackResult == "aborted") return null

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

        // Odds games and the like start from their own position, named in the PGN.
        val outcome = PgnMoves.toUci(PgnMoves.movesText(pgn), GameLibrary.startFenOf(pgn))
        if (outcome.uciMoves.isBlank()) return null
        return GameRecord(
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

        /** Games replayed together on all cores, between progress reports. */
        private const val PARSE_CHUNK = 32
}
