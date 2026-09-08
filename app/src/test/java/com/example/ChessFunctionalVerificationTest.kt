package com.example

import com.example.chess.core.*
import com.example.chess.engine.LocalChessEngine
import com.example.chess.engine.TrainingLevel
import com.example.chess.tactics.TacticsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChessFunctionalVerificationTest {

  @Test
  fun testInitialPositionLegalMovesCount() {
    val initial = Position.initial()
    val moves = LegalMoveGenerator.generateLegalMoves(initial)
    // In standard chess, White has 20 opening moves (16 pawn moves + 4 knight moves)
    assertEquals(20, moves.size)
  }

  @Test
  fun testAllTacticalPuzzlesAreSolvable() {
    for (puzzle in TacticsRepository.allPuzzles) {
      val pos = Position.fromFen(puzzle.fen)
      assertEquals("Puzzle ${puzzle.title} sideToMove must match sideToPlay", puzzle.sideToPlay, pos.sideToMove)
      assertFalse("Puzzle ${puzzle.title} must not start in checkmate", LegalMoveGenerator.getGameStatus(pos) == GameStatus.CHECKMATE)

      val legalMoves = LegalMoveGenerator.generateLegalMoves(pos)
      assertTrue("Puzzle ${puzzle.title} must have legal moves", legalMoves.isNotEmpty())

      val solution = puzzle.solutionMoves.first()
      val matchingLegalMove = legalMoves.find { it.from == solution.from && it.to == solution.to }
      assertNotNull("Solution move ${solution.uci} for puzzle '${puzzle.title}' MUST be a strictly legal move in position", matchingLegalMove)

      // Test that makeMove works
      val nextPos = LegalMoveGenerator.makeMove(pos, matchingLegalMove!!)
      assertNotNull(nextPos)
    }
  }

  @Test
  fun testEngineCanSelectLegalMove() = runBlocking {
    val engine = LocalChessEngine()
    val initial = Position.initial()
    val move = engine.selectMove(initial, TrainingLevel.BEGINNER_800)
    assertNotNull(move)
    val legalMoves = LegalMoveGenerator.generateLegalMoves(initial)
    assertTrue("Engine move must be in legal moves list", legalMoves.any { it.from == move.from && it.to == move.to })
  }
}
