package com.chesstutor.app.domain

import com.example.chess.core.LegalMoveGenerator
import com.example.chess.core.Move
import com.example.chess.core.PieceColor
import com.example.chess.core.Position
import com.example.chess.core.Square
import org.junit.Assert.assertTrue
import org.junit.Test

class CurriculumValidationTest {

    @Test
    fun everyLearnTopicHasAValidFenAndLegalRecommendedMove() {
        LearnCurriculumRepository.topics.forEach { topic ->
            val position = Position.fromFen(topic.demoFen)
            val move = Move.fromUci(topic.recommendedMoveUci)
            assertTrue(
                "Illegal recommended move for ${topic.id}: ${move.uci}",
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

    @Test
    fun curriculumMateClaimsActuallyProduceCheckmate() {
        LearnCurriculumRepository.topics
            .filter { it.validation == TopicValidation.CHECKMATE }
            .forEach { topic ->
                val position = Position.fromFen(topic.demoFen)
                val move = Move.fromUci(topic.recommendedMoveUci)
                val legal = LegalMoveGenerator.generateLegalMoves(position)
                    .firstOrNull { it.uci == move.uci }

                assertTrue("Mate move is illegal for ${topic.id}", legal != null)

                val after = LegalMoveGenerator.makeMove(position, legal!!)
                assertTrue(
                    "Topic ${topic.id} claims mate but resulting position is not checkmate",
                    LegalMoveGenerator.isCheckmate(after)
                )
            }
    }

    @Test
    fun knightForkClaimActuallyAttacksBothNamedTargets() {
        val topic = LearnCurriculumRepository.topics.first { it.id == "tactics_knight_fork" }
        val position = Position.fromFen(topic.demoFen)
        val move = Move.fromUci(topic.recommendedMoveUci)
        val legal = LegalMoveGenerator.generateLegalMoves(position)
            .firstOrNull { it.uci == move.uci }

        assertTrue("Fork move is illegal", legal != null)

        val after = LegalMoveGenerator.makeMove(position, legal!!)
        assertTrue(
            "Nc2 should attack the a1 rook",
            LegalMoveGenerator.isSquareAttacked(after, Square.fromAlgebraic("a1"), PieceColor.BLACK)
        )
        assertTrue(
            "Nc2 should attack the e1 king",
            LegalMoveGenerator.isSquareAttacked(after, Square.fromAlgebraic("e1"), PieceColor.BLACK)
        )
    }
}
