package com.chesstutor.app.data.learn

import android.content.Context
import androidx.core.content.edit
import com.chesstutor.app.domain.learn.OpeningTests
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The openings whose test was passed, kept in SharedPreferences (Phase 12). */
class SharedPrefsOpeningTests(context: Context) : OpeningTests {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(prefs.getStringSet(KEY, emptySet()).orEmpty().toSet())

    override fun passed(): StateFlow<Set<String>> = state.asStateFlow()

    @Synchronized
    override fun pass(openingId: String) {
        if (openingId in state.value) return
        val next = state.value + openingId
        prefs.edit { putStringSet(KEY, next) }
        state.value = next
    }

    /** Forgets every passed test ("Erase everything"). */
    fun clear() {
        prefs.edit { clear() }
        state.value = emptySet()
    }

    private companion object {
        const val PREFS_NAME = "opening_tests"
        const val KEY = "passed"
    }
}
