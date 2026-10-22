package com.chesstutor.app.board

/** Whether an activity shows an extra: never, when the player switches it on, or always. */
enum class Availability { NEVER, OPTIONAL, ALWAYS }

/** The optional extras the player has switched on for the current activity. */
data class BoardOptions(
    val evalBar: Boolean = false,
    val arrows: Boolean = false,
    val clocks: Boolean = false,
    val engineLines: Boolean = false,
    val takebacks: Boolean = false,
)

/** What is actually on screen once a [BoardConfig] meets the player's [BoardOptions]. */
data class BoardExtras(
    val evalBar: Boolean,
    val arrows: Boolean,
    val moveList: Boolean,
    val clocks: Boolean,
    val coachStrip: Boolean,
    val engineLines: Boolean,
    val takebacks: Boolean,
)

/**
 * What an activity turns on (plan section 4.2). Coordinates, legal-move dots, auto-queen and
 * orientation are user settings and are not part of it.
 */
data class BoardConfig(
    val name: String,
    val evalBar: Availability,
    val arrows: Availability,
    val moveList: Availability,
    val clocks: Availability,
    val coachStrip: Availability,
    val engineLines: Availability,
    val takebacks: Availability,
) {
    fun resolve(options: BoardOptions = BoardOptions()): BoardExtras = BoardExtras(
        evalBar = evalBar.shows(options.evalBar),
        arrows = arrows.shows(options.arrows),
        moveList = moveList.shows(false),
        clocks = clocks.shows(options.clocks),
        coachStrip = coachStrip.shows(false),
        engineLines = engineLines.shows(options.engineLines),
        takebacks = takebacks.shows(options.takebacks),
    )

    private fun Availability.shows(switchedOn: Boolean): Boolean = when (this) {
        Availability.NEVER -> false
        Availability.OPTIONAL -> switchedOn
        Availability.ALWAYS -> true
    }

    companion object {
        private val N = Availability.NEVER
        private val O = Availability.OPTIONAL
        private val A = Availability.ALWAYS

        val PLAY = BoardConfig("Play vs bot", evalBar = O, arrows = O, moveList = A, clocks = O, coachStrip = N, engineLines = O, takebacks = O)
        val LESSON = BoardConfig("Lesson", evalBar = N, arrows = A, moveList = N, clocks = N, coachStrip = A, engineLines = N, takebacks = N)
        /** While solving: never an eval bar or engine lines, hints only, retry always offered. */
        val PUZZLE = BoardConfig("Puzzle", evalBar = N, arrows = O, moveList = N, clocks = O, coachStrip = A, engineLines = N, takebacks = A)
        /** After solving, the engine may explain the position. */
        val PUZZLE_SOLVED = BoardConfig("Puzzle solved", evalBar = O, arrows = O, moveList = N, clocks = N, coachStrip = A, engineLines = O, takebacks = A)
        /** Clocks in review show the times recorded in the game, and only when it has them. */
        val REVIEW = BoardConfig("Game review", evalBar = A, arrows = A, moveList = A, clocks = A, coachStrip = N, engineLines = A, takebacks = N)
        val ANALYSIS = BoardConfig("Free analysis", evalBar = A, arrows = A, moveList = A, clocks = N, coachStrip = N, engineLines = A, takebacks = N)

        val ALL = listOf(PLAY, LESSON, PUZZLE, PUZZLE_SOLVED, REVIEW, ANALYSIS)
    }
}
