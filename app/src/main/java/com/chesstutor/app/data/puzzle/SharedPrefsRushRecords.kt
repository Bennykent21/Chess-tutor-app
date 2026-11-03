package com.chesstutor.app.data.puzzle

import android.content.Context
import androidx.core.content.edit
import com.chesstutor.app.domain.puzzle.RushKind
import com.chesstutor.app.domain.puzzle.RushRecords
import com.chesstutor.app.domain.puzzle.withBest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Keeps each rush's best score in SharedPreferences: three numbers need no table. */
class SharedPrefsRushRecords(context: Context) : RushRecords {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val mutableBest = MutableStateFlow(
        RushKind.entries.associateWith { prefs.getInt(it.name, 0) }.filterValues { it > 0 }
    )
    override val best: StateFlow<Map<RushKind, Int>> = mutableBest.asStateFlow()

    @Synchronized
    override fun record(kind: RushKind, score: Int) {
        val next = mutableBest.value.withBest(kind, score)
        if (next == mutableBest.value) return
        prefs.edit { putInt(kind.name, score) }
        mutableBest.value = next
    }

    /** Forgets every best ("Erase everything"). */
    fun clear() {
        prefs.edit { clear() }
        mutableBest.value = emptyMap()
    }

    private companion object {
        const val PREFS_NAME = "rush_records"
    }
}
