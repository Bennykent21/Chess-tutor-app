package com.chesstutor.app.domain.puzzle

/** How the player does at one goal (D95): puzzles played, and how many were solved at the first try. */
data class GoalStats(val played: Int, val firstTry: Int) {
    /** 0 to 100, or null before any puzzle of the goal. */
    val firstTryPercent: Int? get() = if (played == 0) null else firstTry * 100 / played

    companion object {
        /** Each goal's numbers from [history]; puzzles no longer in [pack] are left out. */
        fun of(history: List<PuzzleHistoryEntry>, pack: PuzzlePack?): Map<PuzzleGoal, GoalStats> {
            if (pack == null) return emptyMap()
            return history.mapNotNull { entry -> pack.get(entry.puzzleId)?.let { PuzzleGoal.of(it) to entry } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, entries) -> GoalStats(played = entries.size, firstTry = entries.count { it.clean }) }
        }
    }
}
