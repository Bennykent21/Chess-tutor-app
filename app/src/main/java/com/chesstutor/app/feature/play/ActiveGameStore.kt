package com.chesstutor.app.feature.play

import android.content.Context
import androidx.core.content.edit
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.domain.game.GameRules
import com.chesstutor.app.domain.game.TimeControl

/** An unfinished game, saved after every move so it can be resumed from Home. */
data class ActiveGameSnapshot(
    val botId: String,
    val playerColor: PieceColor,
    val rules: GameRules,
    val startFen: String,
    val moves: List<String>,
    val whiteMs: Long?,
    val blackMs: Long?,
    val savedAt: Long,
)

/** Keeps at most one unfinished game. */
interface ActiveGameStore {
    fun load(): ActiveGameSnapshot?
    fun save(snapshot: ActiveGameSnapshot)
    fun clear()
}

class InMemoryActiveGameStore : ActiveGameStore {
    private var snapshot: ActiveGameSnapshot? = null
    override fun load() = snapshot
    override fun save(snapshot: ActiveGameSnapshot) { this.snapshot = snapshot }
    override fun clear() { snapshot = null }
}

/** Plain SharedPreferences keys: small, synchronous, and readable in unit tests. */
class SharedPrefsActiveGameStore(context: Context) : ActiveGameStore {
    private val prefs = context.applicationContext.getSharedPreferences("active_game", Context.MODE_PRIVATE)

    override fun load(): ActiveGameSnapshot? = runCatching {
        val botId = prefs.getString(BOT, null) ?: return null
        ActiveGameSnapshot(
            botId = botId,
            playerColor = PieceColor.valueOf(prefs.getString(COLOR, null)!!),
            rules = GameRules(
                timeControl = TimeControl.valueOf(prefs.getString(TIME, TimeControl.NONE.name)!!),
                takebacks = prefs.getBoolean(TAKEBACKS, true),
                hints = prefs.getBoolean(HINTS, true),
                evalBar = prefs.getBoolean(EVAL, false),
            ),
            startFen = prefs.getString(FEN, null)!!,
            moves = prefs.getString(MOVES, "")!!.split(' ').filter { it.isNotBlank() },
            whiteMs = prefs.getLong(WHITE_MS, -1).takeIf { it >= 0 },
            blackMs = prefs.getLong(BLACK_MS, -1).takeIf { it >= 0 },
            savedAt = prefs.getLong(SAVED_AT, 0),
        )
    }.getOrNull()

    override fun save(snapshot: ActiveGameSnapshot) {
        prefs.edit {
            putString(BOT, snapshot.botId)
            putString(COLOR, snapshot.playerColor.name)
            putString(TIME, snapshot.rules.timeControl.name)
            putBoolean(TAKEBACKS, snapshot.rules.takebacks)
            putBoolean(HINTS, snapshot.rules.hints)
            putBoolean(EVAL, snapshot.rules.evalBar)
            putString(FEN, snapshot.startFen)
            putString(MOVES, snapshot.moves.joinToString(" "))
            putLong(WHITE_MS, snapshot.whiteMs ?: -1)
            putLong(BLACK_MS, snapshot.blackMs ?: -1)
            putLong(SAVED_AT, snapshot.savedAt)
        }
    }

    override fun clear() {
        prefs.edit { clear() }
    }

    private companion object {
        const val BOT = "bot"
        const val COLOR = "color"
        const val TIME = "time"
        const val TAKEBACKS = "takebacks"
        const val HINTS = "hints"
        const val EVAL = "eval"
        const val FEN = "fen"
        const val MOVES = "moves"
        const val WHITE_MS = "white_ms"
        const val BLACK_MS = "black_ms"
        const val SAVED_AT = "saved_at"
    }
}
