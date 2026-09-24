package com.chesstutor.app.domain

import com.chesstutor.app.data.model.LearningProfile
import com.chesstutor.app.data.model.ModuleProgress
import kotlin.math.roundToInt

enum class SkillDomain {
    TACTICS,
    OPENING,
    MIDDLEGAME,
    ENDGAME,
    CALCULATION
}

data class DomainSkill(
    val domain: SkillDomain,
    val rating: Int,
    val confidence: Float,
    val attempts: Int,
    val accuracy: Float
)

data class PlayerSkillModel(
    val overallRating: Int,
    val confidence: Float,
    val domains: List<DomainSkill>
) {
    fun weakestDomain(): SkillDomain =
        domains.minWithOrNull(
            compareBy<DomainSkill> { it.rating }.thenBy { it.confidence }
        )?.domain ?: SkillDomain.TACTICS

    fun domain(domain: SkillDomain): DomainSkill =
        domains.first { it.domain == domain }
}

object PlayerSkillEstimator {
    private val canonicalRatings = intArrayOf(250, 600, 1000, 1300, 1600, 2000, 2400, 3200)

    fun estimate(profile: LearningProfile, modules: List<ModuleProgress>): PlayerSkillModel {
        val overall = profile.estimatedRating.coerceIn(250, 3200)
        val domains = SkillDomain.entries.map { domain ->
            val relevant = modules.filter { moduleBelongsTo(it.moduleId, domain) }
            val attempts = relevant.sumOf { it.attempts } +
                if (domain == SkillDomain.TACTICS) profile.totalTacticalAttempts else 0
            val correct = relevant.sumOf { it.correctAttempts } +
                if (domain == SkillDomain.TACTICS) profile.totalTacticalCorrect else 0
            val accuracy = if (attempts == 0) 0.5f else correct.toFloat() / attempts
            val evidence = (attempts / 10f).coerceIn(0f, 1f)
            val ratingAdjustment = ((accuracy - 0.5f) * 500f * evidence).roundToInt()
            val rating = snapToCanonical((overall + ratingAdjustment).coerceIn(250, 3200))
            DomainSkill(
                domain = domain,
                rating = rating,
                confidence = evidence,
                attempts = attempts,
                accuracy = accuracy
            )
        }
        val confidence = domains.map { it.confidence }.average().toFloat()
        return PlayerSkillModel(overall, confidence, domains)
    }

    private fun snapToCanonical(rating: Int): Int =
        canonicalRatings.minByOrNull { kotlin.math.abs(it - rating) } ?: 600

    private fun moduleBelongsTo(moduleId: String, domain: SkillDomain): Boolean {
        val id = moduleId.lowercase()
        return when (domain) {
            SkillDomain.TACTICS -> id.contains("mate") || id.contains("fork") ||
                id.contains("pin") || id.contains("skewer") || id.contains("hanging") ||
                id.contains("blunder") || id.contains("tactic") || id.contains("attack")
            SkillDomain.OPENING -> id.contains("opening")
            SkillDomain.MIDDLEGAME -> id.contains("middlegame") || id.contains("outpost") ||
                id.contains("pawn") || id.contains("file")
            SkillDomain.ENDGAME -> id.contains("endgame") || id.contains("lucena") ||
                id.contains("philidor") || id.contains("opposition")
            SkillDomain.CALCULATION -> id.contains("calculation") || id.contains("interpose") ||
                id.contains("overworked") || id.contains("discovered")
        }
    }
}
