package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.chess.core.*
import com.example.chess.data.ChessDatabase
import com.example.chess.data.RepertoireEntity
import com.example.chess.data.RepertoirePositionEntity
import com.example.chess.engine.LocalChessEngine
import com.example.chess.engine.TrainingLevel
import com.example.chess.repertoire.RepertoireRepository
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
    for (puzzle in TacticsRepository.builtInPuzzles) {
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

  @Test
  fun testEngineFindBestMoveReturnsValidEvaluation() = runBlocking {
    val engine = LocalChessEngine()
    val initial = Position.initial()
    val (bestMove, eval) = engine.findBestMove(initial, depth = 2)

    assertNotNull(bestMove)
    assertNotNull(eval)
    val legalMoves = LegalMoveGenerator.generateLegalMoves(initial)
    assertTrue("Best move must be legal", legalMoves.any { it.from == bestMove.from && it.to == bestMove.to })
    assertTrue("Initial position centipawns should be balanced between -150 and 150",
      (eval.centipawns ?: 0) in -150..150)
  }

  @Test
  fun testRepertoireDatabasePersistenceAndDrillFlow() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, ChessDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    val dao = db.chessDao()

    try {
      // 1. Seed defaults
      RepertoireRepository.seedDefaultsIfEmpty(dao)
      val repertoires = dao.getPositionsForRepertoireSync(1L)
      assertTrue("Default repertoire should be seeded with positions", repertoires.isNotEmpty())

      // 2. Test status transition to KNOWN
      val firstPos = repertoires.first()
      dao.updatePositionStatus(firstPos.id, "KNOWN")
      var updated = dao.getPositionsForRepertoireSync(1L).first { it.id == firstPos.id }
      assertEquals("KNOWN", updated.status)
      assertTrue("Review count should have incremented", updated.reviewCount > firstPos.reviewCount)

      // 3. Test toggle critical
      val initialCritical = updated.isCritical
      dao.togglePositionCritical(updated.id)
      updated = dao.getPositionsForRepertoireSync(1L).first { it.id == firstPos.id }
      assertEquals(!initialCritical, updated.isCritical)

      // 4. Test status transition to WEAK
      dao.updatePositionStatus(firstPos.id, "WEAK")
      updated = dao.getPositionsForRepertoireSync(1L).first { it.id == firstPos.id }
      assertEquals("WEAK", updated.status)
    } finally {
      db.close()
    }
  }
}

