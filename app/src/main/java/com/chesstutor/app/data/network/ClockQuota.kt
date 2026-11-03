package com.chesstutor.app.data.network

import com.chesstutor.app.domain.account.TimeClass

/** The clocks a linked account has ratings for. */
val MAIN_CLOCKS: Set<TimeClass> = setOf(TimeClass.RAPID, TimeClass.BLITZ, TimeClass.BULLET)

/**
 * Counts an import's games clock by clock, so a player's favourite clock cannot use up the whole
 * import and leave the others with nothing to graph (D59). Every clock may take up to [perClock]
 * games; the import is [full] once each of the [required] clocks has.
 */
class ClockQuota(private val perClock: Int, private val required: Set<TimeClass> = MAIN_CLOCKS) {
    private val taken = HashMap<TimeClass, Int>()

    /** The most games the required clocks can bring. */
    val target: Int get() = perClock * required.size

    val full: Boolean get() = required.none(::wants)

    fun wants(clock: TimeClass): Boolean = (taken[clock] ?: 0) < perClock

    /** Counts one game; false when its clock already has enough. */
    fun take(clock: TimeClass): Boolean {
        if (!wants(clock)) return false
        taken[clock] = (taken[clock] ?: 0) + 1
        return true
    }

    companion object {
        /** "RAPID:123,BLITZ:456", for saving with a background import. */
        fun encode(cutoffs: Map<TimeClass, Long>): String = cutoffs.entries.joinToString(",") { (clock, millis) -> "${clock.name}:$millis" }

        fun decode(text: String): Map<TimeClass, Long> = text.split(',').filter { it.isNotBlank() }.associate { entry ->
            val (clock, millis) = entry.split(':')
            TimeClass.valueOf(clock) to millis.toLong()
        }
    }
}
