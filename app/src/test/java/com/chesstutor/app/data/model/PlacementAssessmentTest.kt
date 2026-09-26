package com.chesstutor.app.data.model

import com.example.chess.core.LegalMoveGenerator
import com.example.chess.core.Move
import com.example.chess.core.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacementAssessmentTest {

    @Test
    fun everyPlacementQuestionHasALegalExpectedMove() {
        PlacementAssessment.questions.forEach { question ->
            val position = Position.fromFen(question.fen)
            val expected = Move.fromUci(question.expectedMoveUci)

            assertTrue(
                "Illegal expected move for ${question.id}: ${expected.uci}",
                LegalMoveGenerator.generateLegalMoves(position).any { it.uci == expected.uci }
            )
        }
    }

    @Test
    fun placementQuestionsIncreaseByTheirDeclaredTargetBands() {
        assertEquals(
            PlacementAssessment.questions.sortedBy { it.targetRating }.map { it.targetRating },
            PlacementAssessment.questions.map { it.targetRating }
        )
    }

    @Test
    fun ratingEstimateClampsToSupportedRange() {
        assertEquals(250, PlacementAssessment.estimateRating(0, 0))
        assertEquals(250, PlacementAssessment.estimateRating(0, 8))
        assertEquals(3200, PlacementAssessment.estimateRating(8, 8))
    }

    @Test
    fun ratingEstimateScalesLinearlyAcrossEightQuestions() {
        assertEquals(1725, PlacementAssessment.estimateRating(4, 8))
    }
}
