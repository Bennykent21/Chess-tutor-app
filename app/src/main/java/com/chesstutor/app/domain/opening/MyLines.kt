package com.chesstutor.app.domain.opening

import com.chesstutor.app.core.PieceColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A line the player built themselves (Phase 12, "your openings"): its moves from the start, played
 * as [side]. It is drilled like the trainer's lines and played out against Stockfish in Opening Battle.
 */
data class MyLine(val id: String, val name: String, val side: PieceColor, val moves: List<String>) {
    /** As the line trainer drills it; the prefix keeps its progress apart from the book's lines. */
    fun toTrainerLine(): TrainerLine = TrainerLine("$PREFIX$name", moves, side)

    /** As Opening Battle plays it. */
    fun toOpening(): Opening = Opening("", name, moves)

    companion object {
        const val PREFIX = "Mine: "

        /** The trainer opening every one of the player's lines is drilled under. */
        fun trainerOpening(side: PieceColor) = TrainerOpening(id = TRAINER_ID, family = "Your lines", side = side, variations = emptyList())
        const val TRAINER_ID = "mine"

        /** [wanted], or "[wanted] 2" and so on when that name is taken: names keep drill progress apart. */
        fun freeName(wanted: String, taken: Collection<String>): String {
            val base = wanted.trim().ifEmpty { "My line" }
            if (base !in taken) return base
            return generateSequence(2) { it + 1 }.map { "$base $it" }.first { it !in taken }
        }
    }
}

/** The player's own lines, kept on the phone. */
interface MyLineStore {
    fun lines(): StateFlow<List<MyLine>>

    fun save(line: MyLine)

    fun delete(id: String)
}

/** For tests and previews. */
class InMemoryMyLineStore(initial: List<MyLine> = emptyList()) : MyLineStore {
    private val state = MutableStateFlow(initial)

    override fun lines(): StateFlow<List<MyLine>> = state.asStateFlow()

    override fun save(line: MyLine) {
        state.value = state.value.filterNot { it.id == line.id } + line
    }

    override fun delete(id: String) {
        state.value = state.value.filterNot { it.id == id }
    }
}
