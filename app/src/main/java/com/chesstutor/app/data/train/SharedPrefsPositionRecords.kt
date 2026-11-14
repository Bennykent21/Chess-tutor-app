package com.chesstutor.app.data.train

import android.content.Context
import androidx.core.content.edit
import com.chesstutor.app.domain.train.PositionRecord
import com.chesstutor.app.domain.train.PositionRecords
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Train's position results (Phase 12, step 3), one key per position: "won", or "due:<epoch day>". */
class SharedPrefsPositionRecords(context: Context) : PositionRecords {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(prefs.all.mapNotNull { (id, value) -> (value as? String)?.let(::decode)?.let { id to it } }.toMap())

    override fun all(): StateFlow<Map<String, PositionRecord>> = state.asStateFlow()

    @Synchronized
    override fun save(id: String, record: PositionRecord) {
        prefs.edit { putString(id, encode(record)) }
        state.value = state.value + (id to record)
    }

    /** Forgets every result ("Erase everything"). */
    fun clear() {
        prefs.edit { clear() }
        state.value = emptyMap()
    }

    private fun encode(record: PositionRecord): String = if (record.won) WON else "$DUE${record.dueDay ?: 0}"

    private fun decode(text: String): PositionRecord? = when {
        text == WON -> PositionRecord(won = true)
        text.startsWith(DUE) -> PositionRecord(dueDay = text.removePrefix(DUE).toLongOrNull())
        else -> null
    }

    private companion object {
        const val PREFS_NAME = "train_positions"
        const val WON = "won"
        const val DUE = "due:"
    }
}
