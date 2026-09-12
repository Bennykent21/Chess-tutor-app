package com.example.chess.repertoire

import com.example.chess.core.LegalMoveGenerator
import com.example.chess.core.Move
import com.example.chess.core.PieceColor
import com.example.chess.core.Position
import com.example.chess.data.ChessDao
import com.example.chess.data.RepertoireEntity
import com.example.chess.data.RepertoirePositionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository orchestrating Player Repertoires (White & Black),
 * variation branching, critical position tagging, and known/weak spaced drilling.
 */
object RepertoireRepository {

  /**
   * Seeds high-quality standard study repertoires for White and Black if the player has none yet.
   */
  suspend fun seedDefaultsIfEmpty(dao: ChessDao) = withContext(Dispatchers.IO) {
    val existing = dao.getPositionsForRepertoireSync(1L)
    if (existing.isNotEmpty()) return@withContext

    // 1. Seed White Repertoire: Italian Game (C50)
    val whiteRepId = dao.insertRepertoire(
      RepertoireEntity(
        name = "Italian Game: Mainline & Giuoco Piano",
        side = "WHITE",
        ecoFamily = "C50",
        description = "Classical central initiative controlling d5/f7 with harmonious piece development.",
        totalLines = 5,
        masteredLines = 2,
        weakLines = 1
      )
    )

    // Sequence for 1. e4 e5 2. Nf3 Nc6 3. Bc4
    val p0 = Position.initial()
    val m1 = Move.fromUci("e2e4")
    val p1 = LegalMoveGenerator.makeMove(p0, m1)
    val pos1Id = dao.insertRepertoirePosition(
      RepertoirePositionEntity(
        repertoireId = whiteRepId,
        fen = p1.toFen(),
        parentFen = p0.toFen(),
        moveUci = "e2e4",
        moveSan = "e4",
        coachNote = "Claim the center and liberate the Queen and light-squared Bishop.",
        isCritical = true,
        status = "KNOWN",
        reviewCount = 5
      )
    )

    val m2 = Move.fromUci("e7e5")
    val p2 = LegalMoveGenerator.makeMove(p1, m2)
    dao.insertRepertoirePosition(
      RepertoirePositionEntity(
        repertoireId = whiteRepId,
        fen = p2.toFen(),
        parentFen = p1.toFen(),
        moveUci = "e7e5",
        moveSan = "e5",
        coachNote = "Black matches classical central presence.",
        isCritical = false,
        status = "KNOWN",
        reviewCount = 4
      )
    )

    val m3 = Move.fromUci("g1f3")
    val p3 = LegalMoveGenerator.makeMove(p2, m3)
    dao.insertRepertoirePosition(
      RepertoirePositionEntity(
        repertoireId = whiteRepId,
        fen = p3.toFen(),
        parentFen = p2.toFen(),
        moveUci = "g1f3",
        moveSan = "Nf3",
        coachNote = "Attack e5 and prepare rapid kingside castling.",
        isCritical = true,
        status = "KNOWN",
        reviewCount = 4
      )
    )

    val m4 = Move.fromUci("b8c6")
    val p4 = LegalMoveGenerator.makeMove(p3, m4)
    dao.insertRepertoirePosition(
      RepertoirePositionEntity(
        repertoireId = whiteRepId,
        fen = p4.toFen(),
        parentFen = p3.toFen(),
        moveUci = "b8c6",
        moveSan = "Nc6",
        coachNote = "Black develops and defends e5.",
        isCritical = false,
        status = "KNOWN",
        reviewCount = 4
      )
    )

    val m5 = Move.fromUci("f1c4")
    val p5 = LegalMoveGenerator.makeMove(p4, m5)
    dao.insertRepertoirePosition(
      RepertoirePositionEntity(
        repertoireId = whiteRepId,
        fen = p5.toFen(),
        parentFen = p4.toFen(),
        moveUci = "f1c4",
        moveSan = "Bc4",
        coachNote = "Key Repertoire Tabia: Eye f7 and maintain fluid central flexibility.",
        isCritical = true,
        status = "LEARNING",
        reviewCount = 2
      )
    )

    // Branch 1: 3... Bc5 4. c3 (Giuoco Piano)
    val m6a = Move.fromUci("f8c5")
    val p6a = LegalMoveGenerator.makeMove(p5, m6a)
    dao.insertRepertoirePosition(
      RepertoirePositionEntity(
        repertoireId = whiteRepId,
        fen = p6a.toFen(),
        parentFen = p5.toFen(),
        moveUci = "f8c5",
        moveSan = "Bc5",
        coachNote = "Main Giuoco Piano continuation.",
        isCritical = false,
        status = "KNOWN"
      )
    )

    val m7a = Move.fromUci("c2c3")
    val p7a = LegalMoveGenerator.makeMove(p6a, m7a)
    dao.insertRepertoirePosition(
      RepertoirePositionEntity(
        repertoireId = whiteRepId,
        fen = p7a.toFen(),
        parentFen = p6a.toFen(),
        moveUci = "c2c3",
        moveSan = "c3",
        coachNote = "Preparing the critical d2-d4 central pawn wedge. Pay attention to d5 counter-breaks!",
        isCritical = true,
        status = "WEAK",
        reviewCount = 1
      )
    )

    // 2. Seed Black Repertoire: Sicilian Defense (B70)
    val blackRepId = dao.insertRepertoire(
      RepertoireEntity(
        name = "Sicilian Defense: Dragon & Open Lines",
        side = "BLACK",
        ecoFamily = "B70",
        description = "Dynamic counter-attacking weapon fighting for c-file control and queenside play.",
        totalLines = 6,
        masteredLines = 3,
        weakLines = 2
      )
    )

    val bp0 = Position.initial()
    val bm1 = Move.fromUci("e2e4")
    val bp1 = LegalMoveGenerator.makeMove(bp0, bm1)
    val bm2 = Move.fromUci("c7c5")
    val bp2 = LegalMoveGenerator.makeMove(bp1, bm2)
    dao.insertRepertoirePosition(
      RepertoirePositionEntity(
        repertoireId = blackRepId,
        fen = bp2.toFen(),
        parentFen = bp1.toFen(),
        moveUci = "c7c5",
        moveSan = "c5",
        coachNote = "The Sicilian response: asymmetrical fight for dynamic initiative.",
        isCritical = true,
        status = "KNOWN",
        reviewCount = 6
      )
    )

    val bm3 = Move.fromUci("g1f3")
    val bp3 = LegalMoveGenerator.makeMove(bp2, bm3)
    val bm4 = Move.fromUci("d7d6")
    val bp4 = LegalMoveGenerator.makeMove(bp3, bm4)
    dao.insertRepertoirePosition(
      RepertoirePositionEntity(
        repertoireId = blackRepId,
        fen = bp4.toFen(),
        parentFen = bp3.toFen(),
        moveUci = "d7d6",
        moveSan = "d6",
        coachNote = "Dragon setup: Controls e5 and frees the knight to develop to f6.",
        isCritical = false,
        status = "KNOWN",
        reviewCount = 5
      )
    )

    val bm5 = Move.fromUci("d2d4")
    val bp5 = LegalMoveGenerator.makeMove(bp4, bm5)
    val bm6 = Move.fromUci("c5d4")
    val bp6 = LegalMoveGenerator.makeMove(bp5, bm6)
    dao.insertRepertoirePosition(
      RepertoirePositionEntity(
        repertoireId = blackRepId,
        fen = bp6.toFen(),
        parentFen = bp5.toFen(),
        moveUci = "c5d4",
        moveSan = "cxd4",
        coachNote = "Trading a flank pawn for White's central d-pawn. Secures the half-open c-file.",
        isCritical = true,
        status = "KNOWN",
        reviewCount = 4
      )
    )

    val bm7 = Move.fromUci("f3d4")
    val bp7 = LegalMoveGenerator.makeMove(bp6, bm7)
    val bm8 = Move.fromUci("g8f6")
    val bp8 = LegalMoveGenerator.makeMove(bp7, bm8)
    dao.insertRepertoirePosition(
      RepertoirePositionEntity(
        repertoireId = blackRepId,
        fen = bp8.toFen(),
        parentFen = bp7.toFen(),
        moveUci = "g8f6",
        moveSan = "Nf6",
        coachNote = "Hits the e4 pawn immediately, forcing White to commit with 6. Nc3.",
        isCritical = true,
        status = "WEAK",
        reviewCount = 2
      )
    )

    val bm9 = Move.fromUci("b1c3")
    val bp9 = LegalMoveGenerator.makeMove(bp8, bm9)
    val bm10 = Move.fromUci("g7g6")
    val bp10 = LegalMoveGenerator.makeMove(bp9, bm10)
    dao.insertRepertoirePosition(
      RepertoirePositionEntity(
        repertoireId = blackRepId,
        fen = bp10.toFen(),
        parentFen = bp9.toFen(),
        moveUci = "g7g6",
        moveSan = "g6",
        coachNote = "Dragon Bishop: Fianchetto to g7 applying brutal diagonal pressure to the queenside.",
        isCritical = true,
        status = "WEAK",
        reviewCount = 1
      )
    )
  }

  suspend fun createRepertoire(
    dao: ChessDao,
    name: String,
    side: PieceColor,
    ecoFamily: String,
    description: String
  ): Long = withContext(Dispatchers.IO) {
    dao.insertRepertoire(
      RepertoireEntity(
        name = name,
        side = if (side == PieceColor.WHITE) "WHITE" else "BLACK",
        ecoFamily = ecoFamily,
        description = description
      )
    )
  }

  suspend fun addMoveToRepertoire(
    dao: ChessDao,
    repertoireId: Long,
    parentFen: String,
    move: Move,
    resultingPosition: Position,
    coachNote: String = "",
    isCritical: Boolean = false,
    status: String = "LEARNING"
  ): Long = withContext(Dispatchers.IO) {
    val id = dao.insertRepertoirePosition(
      RepertoirePositionEntity(
        repertoireId = repertoireId,
        fen = resultingPosition.toFen(),
        parentFen = parentFen,
        moveUci = move.uci,
        moveSan = move.san,
        coachNote = coachNote,
        isCritical = isCritical,
        status = status
      )
    )
    refreshRepertoireCounts(dao, repertoireId)
    id
  }

  suspend fun updatePositionStatus(dao: ChessDao, positionId: Long, newStatus: String) = withContext(Dispatchers.IO) {
    dao.updatePositionStatus(positionId, newStatus)
  }

  suspend fun toggleCritical(dao: ChessDao, positionId: Long) = withContext(Dispatchers.IO) {
    dao.togglePositionCritical(positionId)
  }

  suspend fun updateStatus(dao: ChessDao, positionId: Long, newStatus: String, repertoireId: Long) = withContext(Dispatchers.IO) {

    val positions = dao.getPositionsForRepertoireSync(repertoireId)
    val target = positions.find { it.id == positionId } ?: return@withContext
    dao.updateRepertoirePosition(
      target.copy(
        status = newStatus,
        lastReviewedAt = System.currentTimeMillis(),
        reviewCount = target.reviewCount + 1
      )
    )
    refreshRepertoireCounts(dao, repertoireId)
  }

  suspend fun toggleCritical(dao: ChessDao, positionId: Long, repertoireId: Long) = withContext(Dispatchers.IO) {
    val positions = dao.getPositionsForRepertoireSync(repertoireId)
    val target = positions.find { it.id == positionId } ?: return@withContext
    dao.updateRepertoirePosition(target.copy(isCritical = !target.isCritical))
  }

  private suspend fun refreshRepertoireCounts(dao: ChessDao, repertoireId: Long) {
    val rep = dao.getRepertoireById(repertoireId) ?: return
    val positions = dao.getPositionsForRepertoireSync(repertoireId)
    val total = positions.size
    val mastered = positions.count { it.status == "KNOWN" }
    val weak = positions.count { it.status == "WEAK" }
    dao.updateRepertoire(rep.copy(totalLines = total, masteredLines = mastered, weakLines = weak))
  }
}
