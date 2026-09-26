package com.chesstutor.app.domain

import com.chesstutor.app.data.model.LearningProfile
import com.chesstutor.app.data.model.ModuleProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSkillModelTest {
    @Test
    fun skillModelUsesCanonicalRatingBands() {
        val model = PlayerSkillEstimator.estimate(
            LearningProfile(estimatedRating = 1000),
            listOf(
                ModuleProgress("opening_fundamentals", attempts = 10, correctAttempts = 9),
                ModuleProgress("endgame_opposition", attempts = 10, correctAttempts = 2)
            )
        )
        assertEquals(1000, model.overallRating)
        assertEquals(1300, model.domain(SkillDomain.OPENING).rating)
        assertEquals(1000, model.domain(SkillDomain.ENDGAME).rating)
    }

    @Test
    fun unusedDomainsHaveLowConfidence() {
        val model = PlayerSkillEstimator.estimate(LearningProfile(estimatedRating = 1000), emptyList())
        assertEquals(0f, model.confidence)
        assertTrue(model.domains.all { it.confidence == 0f })
        assertEquals(SkillDomain.TACTICS, AdaptiveAssessment.chooseDomain(model, emptySet()))
    }

    @Test
    fun assessmentMovesRatingBandUpAfterCorrectAndDownAfterMiss() {
        assertEquals(1300, AdaptiveAssessment.nextRatingBand(1000, true))
        assertEquals(600, AdaptiveAssessment.nextRatingBand(1000, false))
    }

    @Test
    fun assessmentPrefersUncoveredDomain() {
        val model = PlayerSkillEstimator.estimate(LearningProfile(estimatedRating = 1000), emptyList())
        val candidates = listOf(
            AssessmentItem("opening", SkillDomain.OPENING, 1000, "fen", "e2e4"),
            AssessmentItem("endgame", SkillDomain.ENDGAME, 1000, "fen", "e1e2")
        )
        val selected = AdaptiveAssessment.chooseItem(candidates, model, setOf(SkillDomain.OPENING))
        assertEquals("endgame", selected?.id)
    }
}