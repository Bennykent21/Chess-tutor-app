package com.chesstutor.app.domain.game

import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position

/** One player's clock: [initialMs] to start with, and [incrementMs] added after each of their moves. */
data class PlayerClock(val initialMs: Long, val incrementMs: Long = 0) {
    init {
        require(initialMs > 0) { "a clock needs time on it" }
        require(incrementMs >= 0) { "the increment cannot be negative" }
    }

    /** "10 min", "3 | 2", or "0:30" for less than a minute. */
    val label: String
        get() {
            val seconds = initialMs / 1000
            val start = if (seconds % 60 == 0L) "${seconds / 60}" else "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
            return when {
                incrementMs > 0 -> "$start | ${incrementMs / 1000}"
                seconds % 60 == 0L -> "$start min"
                else -> start
            }
        }

    /** The time a 40-move game is expected to take, as the sites sort their clocks. */
    val estimatedMs: Long get() = initialMs + 40 * incrementMs

    companion object {
        fun of(minutes: Int, incrementSeconds: Int = 0) = PlayerClock(minutes * 60_000L, incrementSeconds * 1000L)

        /** The quick choices on the friend setup screen; anything else is a custom clock. */
        val PRESETS = listOf(of(3, 2), of(5), of(10), of(15, 10))
    }
}

/** How two people sit at one phone. */
enum class Seating(val label: String) {
    /** Across the phone: the far player's pieces, name and clock are turned round for them. */
    FACE_TO_FACE("Face to face"),
    SIDE_BY_SIDE("Side by side"),
}

/**
 * Everything needed to start a game between two people (Phase 11). [ownerColor] is the side of the
 * phone's owner, so the saved game is theirs in the games list and in review.
 */
data class FriendSetup(
    val whiteName: String,
    val blackName: String,
    val ownerColor: PieceColor = PieceColor.WHITE,
    /** A clock each (they may differ, for time odds), or none at all. */
    val whiteClock: PlayerClock? = null,
    val blackClock: PlayerClock? = null,
    val startFen: String = Position.STARTING_FEN,
    /** Moves already on the board when the game starts, for "start from an opening". */
    val openingMoves: List<String> = emptyList(),
    val takebacks: Boolean = true,
    val seating: Seating = Seating.FACE_TO_FACE,
) {
    init {
        require((whiteClock == null) == (blackClock == null)) { "both players have a clock, or neither" }
    }

    fun nameOf(color: PieceColor): String = if (color == PieceColor.WHITE) whiteName else blackName
    fun clockOf(color: PieceColor): PlayerClock? = if (color == PieceColor.WHITE) whiteClock else blackClock

    /** "untimed", "bullet", "blitz", "rapid" or "classical", as saved games record it; the slower clock decides. */
    val timeClass: String
        get() {
            val white = whiteClock ?: return "untimed"
            val estimate = maxOf(white.estimatedMs, blackClock?.estimatedMs ?: 0)
            return when {
                estimate < 3 * 60_000L -> "bullet"
                estimate < 8 * 60_000L -> "blitz"
                estimate < 25 * 60_000L -> "rapid"
                else -> "classical"
            }
        }
}

/**
 * Two clocks, of which at most one runs. Time is charged when a move is made, so [remaining]
 * counts the turn in progress from [now]. No timers here: the game asks when it wants to know.
 */
class GameClock(white: PlayerClock?, black: PlayerClock?, private val now: () -> Long) {
    private val clocks = mapOf(PieceColor.WHITE to white, PieceColor.BLACK to black)
    private val left = mutableMapOf(PieceColor.WHITE to white?.initialMs, PieceColor.BLACK to black?.initialMs)
    private var running: PieceColor? = null
    private var turnStartedAt = 0L

    val timed: Boolean get() = left[PieceColor.WHITE] != null

    /** Starts [side]'s clock from this moment; time already used stays used. */
    fun start(side: PieceColor) {
        running = side
        turnStartedAt = now()
    }

    /** [mover] has moved: their thinking time comes off, their increment goes on, the other clock starts. */
    fun moved(mover: PieceColor) {
        charge(mover, increment = clocks[mover]?.incrementMs ?: 0)
        start(mover.opposite())
    }

    /** Stops both clocks; the turn in progress still costs its time. */
    fun stop() {
        running?.let { charge(it, increment = 0) }
        running = null
    }

    /** Time left for [side] right now; null without clocks, and never below zero. */
    fun remaining(side: PieceColor): Long? {
        val stored = left[side] ?: return null
        val spent = if (running == side) now() - turnStartedAt else 0
        return (stored - spent).coerceAtLeast(0)
    }

    /** The side whose time has run out, if any. */
    fun flagged(): PieceColor? = running?.takeIf { remaining(it) == 0L }

    /** Sets [side]'s time to [ms], as the other phone of a nearby game counted it. */
    fun set(side: PieceColor, ms: Long) {
        if (left[side] == null) return
        left[side] = ms.coerceAtLeast(0)
        if (running == side) turnStartedAt = now()
    }

    private fun charge(side: PieceColor, increment: Long) {
        val stored = left[side] ?: return
        val spent = if (running == side) now() - turnStartedAt else 0
        left[side] = (stored - spent).coerceAtLeast(0) + increment
    }
}
