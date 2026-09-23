package com.chesstutor.app.data

import com.chesstutor.app.data.model.PlacementAssessment
import com.example.chess.core.LegalMoveGenerator
import com.example.chess.core.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacementAssessmentTest {

    @Test
    fun everyPlacementQuestionHasValidFenAndLegalExpectedMove() {
        PlacementAssessment.questions.forEach { question ->
            val position = Position.tryFromFen(question.fen).getOrThrow()
            assertTrue(
                "Expected move is not legal for " + question.id,
                LegalMoveGenerator.generateLegalMoves(position).any { it.uci == question.expectedMoveUci }
            )
        }
    }

    @Test
    fun questionsIncreaseInTargetDifficulty() {
        val ratings = PlacementAssessment.questions.map { it.targetRating }
        assertEquals(
            ratings.sorted(),
            ratings
        )
    }

    @Test
    fun estimateIsClampedToCanonicalRange() {
        assertEquals(250, PlacementAssessment.estimateRating(0, 8))
        assertEquals(3200, PlacementAssessment.estimateRating(8, 8))
        assertTrue(PlacementAssessment.estimateRating(4, 8) in 250..3200)
    }
}
