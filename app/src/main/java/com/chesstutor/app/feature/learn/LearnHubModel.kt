package com.chesstutor.app.feature.learn

import com.chesstutor.app.domain.LearnCategory
import com.chesstutor.app.domain.LearnTopic

/** One big button on the Learn hub (plan section 6.2). */
data class LearnHubTile(
    val key: String,
    val title: String,
    val subtitle: String,
    /** The old curriculum category this opens, or null for content that does not exist yet. */
    val category: LearnCategory?,
    val totalLessons: Int,
    val completedLessons: Int,
    val startedLessons: Int,
    /** Shown instead of progress when the content is not built yet. */
    val comingInPhase: Int? = null,
) {
    val isAvailable: Boolean get() = category != null && totalLessons > 0
    val progress: Float get() = if (totalLessons == 0) 0f else completedLessons.toFloat() / totalLessons
}

object LearnHubModel {

    private data class Spec(val key: String, val title: String, val subtitle: String, val category: LearnCategory?)

    private val order = listOf(
        Spec("basics", "Basics", "How pieces move, check and mate", null),
        Spec("openings", "Openings", "Start every game well", LearnCategory.OPENING),
        Spec("tactics", "Tactics", "Forks, pins and combinations", LearnCategory.TACTICS),
        Spec("middlegame", "Middlegame", "Plans and piece activity", LearnCategory.MIDDLEGAME),
        Spec("endgames", "Endgames", "Turn advantages into wins", LearnCategory.ENDGAME),
        Spec("blunders", "Blunders", "Spot the traps before they spring", LearnCategory.BLUNDER_PATTERNS),
    )

    /**
     * Builds the six hub tiles from the current curriculum. A lesson counts as completed
     * when mastered, and as started when practised but not yet mastered.
     */
    fun tiles(
        topics: List<LearnTopic>,
        masteredIds: Set<String>,
        practicedIds: Set<String>,
    ): List<LearnHubTile> = order.map { spec ->
        val inCategory = if (spec.category == null) emptyList() else topics.filter { it.category == spec.category }
        val completed = inCategory.count { it.id in masteredIds }
        val started = inCategory.count { it.id in practicedIds && it.id !in masteredIds }
        LearnHubTile(
            key = spec.key,
            title = spec.title,
            subtitle = spec.subtitle,
            category = spec.category,
            totalLessons = inCategory.size,
            completedLessons = completed,
            startedLessons = started,
            comingInPhase = if (spec.category == null) BASICS_PHASE else null,
        )
    }

    /** Overall progress across every available lesson, 0..1. */
    fun overallProgress(tiles: List<LearnHubTile>): Float {
        val total = tiles.sumOf { it.totalLessons }
        return if (total == 0) 0f else tiles.sumOf { it.completedLessons }.toFloat() / total
    }

    /** The Basics path is written in Phase 4 (plan section 8). */
    const val BASICS_PHASE = 4
}
