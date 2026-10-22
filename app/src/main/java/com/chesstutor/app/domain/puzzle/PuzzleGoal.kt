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

        /** Below this rating the prompt gives the whole goal, including how many moves a mate takes. */
        const val FULL_PROMPT_BELOW = 1200

        /** From this rating the prompt says only "Find the best move", as in a real game. */
        const val NEUTRAL_FROM = 1800

        /**
         * The coach's question for [puzzle]. It fades with difficulty (developer's choice,
         * 22 Oct 2026): easy puzzles get the whole goal ("Find mate in 2"), middle ones only
         * the goal ("Find the checkmate"), hard ones just "Find the best move".
         */
        fun prompt(puzzle: Puzzle): String {
            if (puzzle.rating >= NEUTRAL_FROM) return "Find the best move"
            val mateIn = (puzzle.themes + puzzle.primaryTheme).firstNotNullOfOrNull { tag ->
                tag.removePrefix("mateIn").takeIf { tag.startsWith("mateIn") }?.toIntOrNull()
            }?.takeIf { puzzle.rating < FULL_PROMPT_BELOW }
            return when (of(puzzle)) {
                CHECKMATE -> if (mateIn == 1) "Find the checkmate in one move" else if (mateIn != null) "Find mate in $mateIn" else "Find the checkmate"
                WIN_MATERIAL -> "Can you win material?"
                DEFEND -> "Save the game: find the best defence"
                ENDGAME -> "Find the winning plan in this endgame"
            }
        }
    }
}
