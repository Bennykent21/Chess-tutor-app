package com.chesstutor.app.domain

import com.chesstutor.app.data.model.LearningProfile
import com.chesstutor.app.data.model.ModuleProgress
import kotlin.math.abs

data class TrainingRecommendation(val topic: LearnTopic, val reason: String, val domain: SkillDomain, val skillRating: Int)

object AdaptiveTrainingPlanner {
    fun recommend(profile: LearningProfile, modules: List<ModuleProgress>): TrainingRecommendation {
        val skill = PlayerSkillEstimator.estimate(profile, modules)
        val preferred = preferredDomains(profile.learningGoal)
        val candidates = LearnCurriculumRepository.topics.mapNotNull { topic ->
            val domain = topicDomain(topic) ?: return@mapNotNull null
            Triple(topic, domain, modules.firstOrNull { it.moduleId == topic.id })
        }
        val filtered = candidates.filter { it.second in preferred }
        val source = if (filtered.isNotEmpty()) filtered else candidates
        val unpracticed = source.filter { it.third?.practiced != true }
        val pool = if (unpracticed.isNotEmpty()) unpracticed else source
        val chosen = pool.minWithOrNull(compareBy<Triple<LearnTopic, SkillDomain, ModuleProgress?>> {
            val index = preferred.indexOf(it.second)
            if (index < 0) preferred.size else index
        }.thenBy { abs(topicRatingBand(it.first) - skill.domain(it.second).rating) }
         .thenBy { it.third?.accuracy ?: 0f }.thenBy { it.first.id })
            ?: candidates.first()
        val domainSkill = skill.domain(chosen.second)
        val progress = chosen.third
        val reason = when {
            progress != null && progress.attempts > 0 -> "Continue ${chosen.first.title.lowercase()}; your current accuracy is ${(progress.accuracy * 100).toInt()}%."
            profile.learningGoal == "TACTICS" -> "Your training goal is tactics, so we’re prioritizing tactical pattern recognition."
            profile.learningGoal == "ENDGAMES" -> "Your training goal is endgames, so we’re prioritizing endgame technique."
            profile.learningGoal == "GAME_ANALYSIS" -> "Your training goal is game analysis, so we’re building the calculation and mistake-recognition skills behind review."
            else -> "This is the closest unpracticed lesson to your current ${domainSkill.rating} ${chosen.second.name.lowercase()} skill estimate."
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

    private fun topicRatingBand(topic: LearnTopic) = when (topic.id) {
        "lesson_mate_1", "tactics_pin_and_skewer" -> 600
        "tactics_knight_fork", "lesson_hanging_piece" -> 1000
        "tactics_discovered_attack", "lesson_back_rank", "lesson_overworked_piece" -> 1300
        "lesson_interpose_fail" -> 1600
        "endgame_opposition", "endgame_lucena_bridge" -> 2000
        "middlegame_pawn_breaks" -> 2400
        else -> 1300
    }
}