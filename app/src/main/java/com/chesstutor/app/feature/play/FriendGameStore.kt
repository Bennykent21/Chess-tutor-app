package com.chesstutor.app.feature.play

import android.content.Context
import androidx.core.content.edit
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.domain.game.FriendSetup
import com.chesstutor.app.domain.game.PlayerClock
import com.chesstutor.app.domain.game.Seating
import com.squareup.moshi.Moshi

/**
 * An unfinished friend game on one phone (Phase 13): its setup, every move so far (the opening's
 * too) and the time each player had left. Saved after every move, so closing the app loses nothing.
 */
data class FriendSnapshot(
    val setup: FriendSetup,
    val moves: List<String>,
    val whiteMs: Long?,
    val blackMs: Long?,
    val savedAt: Long,
)

/** Keeps at most one unfinished friend game. */
interface FriendGameStore {
    fun load(): FriendSnapshot?
    fun save(snapshot: FriendSnapshot)
    fun clear()
}

class InMemoryFriendGameStore(private var snapshot: FriendSnapshot? = null) : FriendGameStore {
    override fun load() = snapshot
    override fun save(snapshot: FriendSnapshot) { this.snapshot = snapshot }
    override fun clear() { snapshot = null }
}

/** One JSON value in SharedPreferences ("friend_game"). */
class SharedPrefsFriendGameStore(context: Context) : FriendGameStore {
    private val prefs = context.applicationContext.getSharedPreferences("friend_game", Context.MODE_PRIVATE)

    override fun load(): FriendSnapshot? = prefs.getString(KEY, null)?.let(::decode)

    override fun save(snapshot: FriendSnapshot) {
        prefs.edit { putString(KEY, encode(snapshot)) }
    }

    override fun clear() {
        prefs.edit { clear() }
    }

    companion object {
        private const val KEY = "game"
        private val adapter = Moshi.Builder().build().adapter(Any::class.java)

        fun encode(snapshot: FriendSnapshot): String {
            val setup = snapshot.setup
            return adapter.toJson(
                mapOf(
                    "white" to setup.whiteName,
                    "black" to setup.blackName,
                    "owner" to setup.ownerColor.name,
                    "wMs" to setup.whiteClock?.initialMs,
                    "wInc" to setup.whiteClock?.incrementMs,
                    "bMs" to setup.blackClock?.initialMs,
                    "bInc" to setup.blackClock?.incrementMs,
                    "fen" to setup.startFen,
                    "opening" to setup.openingMoves,
                    "takebacks" to setup.takebacks,
                    "seating" to setup.seating.name,
                    "moves" to snapshot.moves,
                    "whiteLeft" to snapshot.whiteMs,
                    "blackLeft" to snapshot.blackMs,
                    "savedAt" to snapshot.savedAt,
                ).filterValues { it != null },
            )
        }

        fun decode(json: String): FriendSnapshot? = runCatching {
            val map = adapter.fromJson(json) as Map<*, *>
            fun clock(ms: String, inc: String) = (map[ms] as? Number)?.let { PlayerClock(it.toLong(), (map[inc] as? Number)?.toLong() ?: 0) }
            fun strings(key: String) = (map[key] as? List<*>).orEmpty().map { it as String }
            FriendSnapshot(
                setup = FriendSetup(
                    whiteName = map["white"] as String,
                    blackName = map["black"] as String,
                    ownerColor = PieceColor.valueOf(map["owner"] as String),
                    whiteClock = clock("wMs", "wInc"),
                    blackClock = clock("bMs", "bInc"),
                    startFen = map["fen"] as String,
                    openingMoves = strings("opening"),
                    takebacks = map["takebacks"] as? Boolean ?: true,
                    seating = (map["seating"] as? String)?.let(Seating::valueOf) ?: Seating.FACE_TO_FACE,
                ),
                moves = strings("moves"),
                whiteMs = (map["whiteLeft"] as? Number)?.toLong(),
                blackMs = (map["blackLeft"] as? Number)?.toLong(),
                savedAt = (map["savedAt"] as? Number)?.toLong() ?: 0,
            )
        }.getOrNull()
    }
}
