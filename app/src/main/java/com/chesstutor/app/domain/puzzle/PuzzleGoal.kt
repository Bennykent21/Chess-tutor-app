package com.chesstutor.app.domain.puzzle

/**
 * What a puzzle asks for (plan sections 6.3.3 and 6.3.6). The prompt names the goal, never
 * the pattern, so it does not give the answer away. Also the four big "By theme" choices.
 */
enum class PuzzleGoal(val title: String) {
    CHECKMATE("Checkmate"),
    WIN_MATERIAL("Win material"),
    DEFEND("Defend"),
    ENDGAME("Endgame");

    companion object {
        private val ENDGAMES = setOf("pawnEndgame", "rookEndgame", "bishopEndgame", "knightEndgame", "queenEndgame", "queenRookEndgame", "endgame")
        private val DEFENCE = setOf("defensiveMove", "equality")

        fun of(puzzle: Puzzle): PuzzleGoal {
            val tags = puzzle.themes + puzzle.primaryTheme
            return when {
                tags.any { it == "mate" || it.startsWith("mateIn") || it.endsWith("Mate") } -> CHECKMATE
                tags.any { it in DEFENCE } -> DEFEND
                tags.any { it in ENDGAMES } -> ENDGAME
                else -> WIN_MATERIAL
            }
        }

        /** The coach's question for [puzzle], for example "Find mate in 2" or "Can you win material?". */
        fun prompt(puzzle: Puzzle): String {
            val mateIn = (puzzle.themes + puzzle.primaryTheme).firstNotNullOfOrNull { tag ->
                tag.removePrefix("mateIn").takeIf { tag.startsWith("mateIn") }?.toIntOrNull()
            }
            return when (of(puzzle)) {
                CHECKMATE -> if (mateIn == 1) "Find the checkmate in one move" else if (mateIn != null) "Find mate in $mateIn" else "Find the checkmate"
                WIN_MATERIAL -> "Can you win material?"
                DEFEND -> "Save the game: find the best defence"
                ENDGAME -> "Find the winning plan in this endgame"
            }
        }
    }
}
