package com.chesstutor.app.domain

data class AssessmentItem(
    val id: String,
    val domain: SkillDomain,
    val ratingBand: Int,
    val fen: String,
    val solutionUci: String
)

data class AssessmentState(
    val items: List<AssessmentItem>,
    val currentIndex: Int = 0,
    val correct: Int = 0
) {
    val isComplete: Boolean get() = currentIndex >= items.size
}

object AdaptiveAssessment {
    private val bands = intArrayOf(250, 600, 1000, 1300, 1600, 2000, 2400, 3200)

    fun nextRatingBand(currentRating: Int, correct: Boolean): Int {
        val index = bands.indexOfFirst { it >= currentRating }.let { if (it < 0) bands.lastIndex else it }
        val next = if (correct) (index + 1).coerceAtMost(bands.lastIndex)
        else (index - 1).coerceAtLeast(0)
        return bands[next]
    }

    fun chooseDomain(skill: PlayerSkillModel, covered: Set<SkillDomain>): SkillDomain {
        val uncovered = skill.domains.filter { it.domain !in covered }
        if (uncovered.isNotEmpty()) {
            return uncovered.minWithOrNull(compareBy<DomainSkill> { it.confidence }.thenBy { it.rating })!!.domain
        }
        return skill.weakestDomain()
    }

    fun chooseItem(
        candidates: List<AssessmentItem>,
        skill: PlayerSkillModel,
        covered: Set<SkillDomain>,
        targetRating: Int = skill.overallRating
    ): AssessmentItem? {
        if (candidates.isEmpty()) return null
        val domain = chooseDomain(skill, covered)
        val domainCandidates = candidates.filter { it.domain == domain }
        val pool = if (domainCandidates.isNotEmpty()) domainCandidates else candidates
        return pool.minWithOrNull(
            compareBy<AssessmentItem> { kotlin.math.abs(it.ratingBand - targetRating) }.thenBy { it.id }
        )
    }

    fun update(state: AssessmentState, correct: Boolean): AssessmentState {
        if (state.isComplete) return state
        return state.copy(
            currentIndex = state.currentIndex + 1,
            correct = state.correct + if (correct) 1 else 0
        )
    }
}