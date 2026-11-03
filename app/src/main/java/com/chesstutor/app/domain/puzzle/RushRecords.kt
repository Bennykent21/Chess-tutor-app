package com.chesstutor.app.domain.puzzle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The three rushes on Puzzle modes: 3 or 5 minutes on the clock, or Survival (three strikes). */
enum class RushKind(val minutes: Int?) {
    THREE(3),
    FIVE(5),
    SURVIVAL(null),
    ;

    companion object {
        fun of(minutes: Int?): RushKind = entries.first { it.minutes == minutes }
    }
}

/** The best score of each rush: the most puzzles solved in one run (D66). */
interface RushRecords {
    val best: StateFlow<Map<RushKind, Int>>

    /** Keeps [score] for [kind] when it beats the best so far. */
    fun record(kind: RushKind, score: Int)
}

/** Rush bests kept in memory, for tests and previews. */
class InMemoryRushRecords(initial: Map<RushKind, Int> = emptyMap()) : RushRecords {
    private val mutableBest = MutableStateFlow(initial)
    override val best: StateFlow<Map<RushKind, Int>> = mutableBest.asStateFlow()

    override fun record(kind: RushKind, score: Int) {
        mutableBest.update { it.withBest(kind, score) }
    }
}

/** [this] with [score] as [kind]'s best when it is higher than the one there. */
internal fun Map<RushKind, Int>.withBest(kind: RushKind, score: Int): Map<RushKind, Int> =
    if (score > (this[kind] ?: 0)) this + (kind to score) else this
