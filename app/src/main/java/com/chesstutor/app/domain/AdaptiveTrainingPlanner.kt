package com.chesstutor.app.domain

import com.chesstutor.app.data.model.LearningProfile
import com.chesstutor.app.data.model.ModuleProgress
import kotlin.math.abs

data class TrainingRecommendation(
    val topic: LearnTopic,
    val reason: String,
    val domain: SkillDomain,
    val skillRating: Int
)

object AdaptiveTrainingPlanner {
    fun recommend(profile: LearningProfile, modules: List<ModuleProgress>): TrainingRecommendation {
        val skill = PlayerSkillEstimator.estimate(profile, modules)
        val preferred = preferredDomains(profile.learningGoal)
        val candidates = LearnCurriculumRepository.topics.mapNotNull { topic ->
            val domain = topicDomain(topic) ?: return@mapNotNull null
            Triple(topic, domain, modules.firstOrNull { it.moduleId == topic.id })
        }
        val preferredCandidates = candidates.filter { it.second in preferred }
        val preferredUnmastered = preferredCandidates.filter { it.third?.mastered != true }
        val unmastered = candidates.filter { it.third?.mastered != true }
        val source = when {
            preferredUnmastered.isNotEmpty() -> preferredUnmastered
            unmastered.isNotEmpty() -> unmastered
            preferredCandidates.isNotEmpty() -> preferredCandidates
            else -> candidates
        }
        val remediation = source.filter {
            val module = it.third
            module != null && module.attempts > 0 && module.accuracy < 0.8f
        }
        val unpracticed = source.filter { it.third?.practiced != true }
        val pool = when {
            remediation.isNotEmpty() -> remediation
            unpracticed.isNotEmpty() -> unpracticed
            else -> source
        }
        val chosen = pool.minWithOrNull(
            compareBy<Triple<LearnTopic, SkillDomain, ModuleProgress?>> {
                val index = preferred.indexOf(it.second)
                if (index < 0) preferred.size else index
            }.thenBy { abs(it.first.skillRating - skill.domain(it.second).rating) }
                .thenBy { it.third?.accuracy ?: 0f }
                .thenBy { it.first.id }
        ) ?: candidates.first()
        val domainSkill = skill.domain(chosen.second)
        val progress = chosen.third
        val reason = when {
            progress != null && progress.attempts > 0 ->
                "Continue ${chosen.first.title.lowercase()}; your current accuracy is ${(progress.accuracy * 100).toInt()}%."
            profile.learningGoal == "TACTICS" ->
                "Your training goal is tactics, so we’re prioritizing tactical pattern recognition."
            profile.learningGoal == "ENDGAMES" ->
                "Your training goal is endgames, so we’re prioritizing endgame technique."
            profile.learningGoal == "GAME_ANALYSIS" ->
                "Your training goal is game analysis, so we’re building the calculation and mistake-recognition skills behind review."
            else ->
                "This is the closest unpracticed lesson to your current ${domainSkill.rating} ${chosen.second.name.lowercase()} skill estimate."
        }
        return TrainingRecommendation(chosen.first, reason, chosen.second, domainSkill.rating)
    }

    private fun preferredDomains(goal: String) = when (goal) {
        "TACTICS" -> listOf(SkillDomain.TACTICS, SkillDomain.CALCULATION, SkillDomain.MIDDLEGAME)
        "ENDGAMES" -> listOf(SkillDomain.ENDGAME, SkillDomain.CALCULATION, SkillDomain.MIDDLEGAME)
        "GAME_ANALYSIS" -> listOf(SkillDomain.CALCULATION, SkillDomain.TACTICS, SkillDomain.MIDDLEGAME, SkillDomain.ENDGAME)
        else -> listOf(SkillDomain.TACTICS, SkillDomain.MIDDLEGAME, SkillDomain.ENDGAME, SkillDomain.OPENING, SkillDomain.CALCULATION)
    }

    private fun topicDomain(topic: LearnTopic) = when (topic.category) {
        LearnCategory.TACTICS, LearnCategory.BLUNDER_PATTERNS -> SkillDomain.TACTICS
        LearnCategory.OPENING -> SkillDomain.OPENING
        LearnCategory.MIDDLEGAME -> SkillDomain.MIDDLEGAME
        LearnCategory.ENDGAME -> SkillDomain.ENDGAME
    }
}
