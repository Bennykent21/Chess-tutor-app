package com.chesstutor.app.domain

import com.example.chess.core.LegalMoveGenerator
import com.example.chess.core.Move
import com.example.chess.core.Position
import org.junit.Assert.assertTrue
import org.junit.Test

class CurriculumValidationTest {

    @Test
    fun everyLearnTopicHasAValidFenAndLegalRecommendedMove() {
        LearnCurriculumRepository.topics.forEach { topic ->
            val position = Position.fromFen(topic.demoFen)
            val move = Move.fromUci(topic.recommendedMoveUci)

            assertTrue(
                "Illegal recommended move for topic ${topic.id}: ${move.uci}",
                LegalMoveGenerator.generateLegalMoves(position).any { it.uci == move.uci }
            )
        }
    }

    @Test
    fun everyTacticalDrillHasAValidFenAndLegalSolutionMove() {
        TrainDrillsRepository.drills.forEach { drill ->
            val position = Position.fromFen(drill.fen)
            val move = Move.fromUci(drill.solutionUci)

            assertTrue(
                "Illegal solution move for drill ${drill.id}: ${move.uci}",
                LegalMoveGenerator.generateLegalMoves(position).any { it.uci == move.uci }
            )
        }
    }
}
