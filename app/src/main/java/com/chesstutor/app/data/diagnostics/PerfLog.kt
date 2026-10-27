package com.chesstutor.app.data.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The last game analysis: how many positions Stockfish searched, how long it took, at what depth. */
data class AnalysisTiming(val positions: Int, val millis: Long, val depth: Int) {
    val msPerPosition: Long get() = if (positions == 0) 0 else millis / positions
}

/** The last import: how many games arrived, how long it took, from where. */
data class ImportTiming(val games: Int, val millis: Long, val source: String)

/** Everything measured on this phone since the app started (plan Phase 6D). */
data class PerfSnapshot(
    /** From the process starting to the first screen with the player's profile. */
    val startupMillis: Long? = null,
    /** Asset loads by name ("Puzzles", "Openings", "Lessons"). */
    val loads: Map<String, Long> = emptyMap(),
    val analysis: AnalysisTiming? = null,
    val import: ImportTiming? = null,
)

/**
 * Speed measured on the player's phone (plan Phase 6D: start-up, imports and analysis "tuned on
 * the phone"). Nothing leaves the phone: Settings > Engine health shows it, so the player can
 * read the numbers out. Kept for this run of the app only.
 */
object PerfLog {
    private val mutable = MutableStateFlow(PerfSnapshot())
    val snapshot: StateFlow<PerfSnapshot> = mutable.asStateFlow()

    fun startup(millis: Long) = mutable.update { if (it.startupMillis == null) it.copy(startupMillis = millis) else it }

    fun load(name: String, millis: Long) = mutable.update { it.copy(loads = it.loads + (name to millis)) }

    fun analysis(timing: AnalysisTiming) = mutable.update { it.copy(analysis = timing) }

    fun import(timing: ImportTiming) = mutable.update { it.copy(import = timing) }

    /** Runs [block] and records how long it took under [name]. */
    inline fun <T> timed(name: String, block: () -> T): T {
        val start = System.nanoTime()
        return block().also { load(name, (System.nanoTime() - start) / 1_000_000) }
    }

    /** For tests. */
    fun reset() {
        mutable.value = PerfSnapshot()
    }
}
