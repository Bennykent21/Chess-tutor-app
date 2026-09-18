package com.example.chess.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessCoreTest {

  @Test
  fun testStartingPositionLegalMovesCount() {
    val initialPos = Position.initial()
    val moves = LegalMoveGenerator.generateLegalMoves(initialPos)
    // In chess, starting position has exactly 20 legal moves: 16 pawn advances (8 single, 8 double) + 4 knight hops
    assertEquals(20, moves.size)
    assertEquals(GameStatus.IN_PROGRESS, LegalMoveGenerator.getGameStatus(initialPos))
  }

  @Test
  fun testScholarsMateCheckmate() {
    // 1. e4 e5 2. Qh5 Nc6 3. Bc4 Nf6 4. Qxf7#
    var pos = Position.initial()

    // 1. e4
    pos = LegalMoveGenerator.makeMove(pos, Move(Square.fromAlgebraic("e2"), Square.fromAlgebraic("e4")))
    // 1... e5
    pos = LegalMoveGenerator.makeMove(pos, Move(Square.fromAlgebraic("e7"), Square.fromAlgebraic("e5")))
    // 2. Qh5
    pos = LegalMoveGenerator.makeMove(pos, Move(Square.fromAlgebraic("d1"), Square.fromAlgebraic("h5")))
    // 2... Nc6
    pos = LegalMoveGenerator.makeMove(pos, Move(Square.fromAlgebraic("b8"), Square.fromAlgebraic("c6")))
    // 3. Bc4
    pos = LegalMoveGenerator.makeMove(pos, Move(Square.fromAlgebraic("f1"), Square.fromAlgebraic("c4")))
    // 3... Nf6
    pos = LegalMoveGenerator.makeMove(pos, Move(Square.fromAlgebraic("g8"), Square.fromAlgebraic("f6")))
    // 4. Qxf7#
    pos = LegalMoveGenerator.makeMove(pos, Move(Square.fromAlgebraic("h5"), Square.fromAlgebraic("f7")))

    assertEquals(PieceColor.BLACK, pos.sideToMove)
    assertTrue(LegalMoveGenerator.isKingInCheck(pos, PieceColor.BLACK))
    val blackLegalMoves = LegalMoveGenerator.generateLegalMoves(pos)
    assertEquals(0, blackLegalMoves.size)
    assertEquals(GameStatus.CHECKMATE, LegalMoveGenerator.getGameStatus(pos))
  }

  @Test
  fun testEnPassantExecution() {
    // 1. e4 a6 2. e5 d5 -> White can capture en passant on d6!
    var pos = Position.initial()
    pos = LegalMoveGenerator.makeMove(pos, Move(Square.fromAlgebraic("e2"), Square.fromAlgebraic("e4")))
    pos = LegalMoveGenerator.makeMove(pos, Move(Square.fromAlgebraic("a7"), Square.fromAlgebraic("a6")))
    pos = LegalMoveGenerator.makeMove(pos, Move(Square.fromAlgebraic("e4"), Square.fromAlgebraic("e5")))
    pos = LegalMoveGenerator.makeMove(pos, Move(Square.fromAlgebraic("d7"), Square.fromAlgebraic("d5")))

    assertEquals(Square.fromAlgebraic("d6"), pos.enPassantSquare)

    val whiteMoves = LegalMoveGenerator.generateLegalMoves(pos)
    val epMove = whiteMoves.find { it.from == Square.fromAlgebraic("e5") && it.to == Square.fromAlgebraic("d6") }
    assertNotNull(epMove)
    assertTrue(epMove!!.isEnPassant)

    // Apply en passant
    pos = LegalMoveGenerator.makeMove(pos, epMove)
    // Black's pawn on d5 should be gone!
    assertEquals(null, pos.pieceAt(Square.fromAlgebraic("d5")))
    // White's pawn should be on d6
    assertEquals(Piece(PieceType.PAWN, PieceColor.WHITE), pos.pieceAt(Square.fromAlgebraic("d6")))
  }

  @Test
  fun testCastlingExecution() {
    // FEN with clear path for White Kingside castle
    val fen = "r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4"
    val pos = Position.fromFen(fen)
    val moves = LegalMoveGenerator.generateLegalMoves(pos)

    val castleMove = moves.find { it.from == Square.fromAlgebraic("e1") && it.to == Square.fromAlgebraic("g1") }
    assertNotNull("White should be able to castle kingside", castleMove)
    assertTrue(castleMove!!.isCastling)

    val postCastle = LegalMoveGenerator.makeMove(pos, castleMove)
    // King on g1, Rook on f1
    assertEquals(Piece(PieceType.KING, PieceColor.WHITE), postCastle.pieceAt(Square.fromAlgebraic("g1")))
    assertEquals(Piece(PieceType.ROOK, PieceColor.WHITE), postCastle.pieceAt(Square.fromAlgebraic("f1")))
    // Original e1 and h1 squares should be empty
    assertEquals(null, postCastle.pieceAt(Square.fromAlgebraic("e1")))
    assertEquals(null, postCastle.pieceAt(Square.fromAlgebraic("h1")))
    // Castling rights for White should now be gone
    assertFalse(postCastle.castlingRights.whiteKingside)
    assertFalse(postCastle.castlingRights.whiteQueenside)
  }

  @Test
  fun testFenRoundtrip() {
    val fen = "r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4"
    val pos = Position.fromFen(fen)
    assertEquals(fen, pos.toFen())
  }

  @Test
  fun testFenParserRejectsMalformedFields() {
    val malformed = listOf(
      "8/8/8/8/8/8/8/K6k w - - 0",
      "8/8/8/8/8/8/8/K6k x - - 0 1",
      "8/8/8/8/8/8/8/K6k w KK - 0 1",
      "8/8/8/8/8/8/8/K6k w - -1 1",
      "8/8/8/8/8/8/8/K6k w - - 0 0",
      "8/8/8/8/8/8/8/K7 w - - 0 1",
      "9/8/8/8/8/8/8/K6k w - - 0 1"
    )

    malformed.forEach { fen ->
      assertFalse("FEN should be rejected: $fen", Position.tryFromFen(fen).isSuccess)
    }
  }

  @Test
  fun testPromotionGeneratesAllFourChoices() {
    val pos = Position.fromFen("4k3/P7/8/8/8/8/8/4K3 w - - 0 1")
    val promotions = LegalMoveGenerator.generateLegalMoves(pos)
      .filter { it.from == Square.fromAlgebraic("a7") && it.to == Square.fromAlgebraic("a8") }

    assertEquals(4, promotions.size)
    assertTrue(promotions.any { it.promotion == PieceType.QUEEN })
    assertTrue(promotions.any { it.promotion == PieceType.ROOK })
    assertTrue(promotions.any { it.promotion == PieceType.BISHOP })
    assertTrue(promotions.any { it.promotion == PieceType.KNIGHT })
  }

  @Test
  fun testCastlingRequiresActualRook() {
    val pos = Position.fromFen("4k3/8/8/8/8/8/8/4K3 w KQ - 0 1")
    val moves = LegalMoveGenerator.generateLegalMoves(pos)

    assertFalse(moves.any { it.isCastling })
  }

  @Test
  fun testKingCannotBeCapturedAsALegalMove() {
    val pos = Position.fromFen("4k3/8/8/8/8/8/8/4R1K1 w - - 0 1")
    val moves = LegalMoveGenerator.generateLegalMoves(pos)

    assertFalse(
      moves.any {
        it.to == Square.fromAlgebraic("e8")
      }
    )
  }

  @Test
  fun testSameColorBishopsAreInsufficientMaterial() {
    val pos = Position.fromFen("4k3/8/8/8/8/8/6B1/4K2b w - - 0 1")
    assertEquals(
      GameStatus.DRAW_INSUFFICIENT_MATERIAL,
      LegalMoveGenerator.getGameStatus(pos)
    )
  }

  @Test
  fun testCastlingOutOfCheckIsIllegal() {
    val pos = Position.fromFen("4k3/8/8/8/8/8/4r3/R3K2R w KQ - 0 1")
    val moves = LegalMoveGenerator.generateLegalMoves(pos)

    assertFalse(moves.any { it.isCastling })
  }

  @Test
  fun testCastlingThroughCheckIsIllegal() {
    val pos = Position.fromFen("4k3/8/8/8/8/8/5r2/R3K2R w KQ - 0 1")
    val moves = LegalMoveGenerator.generateLegalMoves(pos)

    assertFalse(moves.any { it.from == Square.fromAlgebraic("e1") && it.to == Square.fromAlgebraic("g1") })
  }

  @Test
  fun testCastlingIntoCheckIsIllegal() {
    val pos = Position.fromFen("4k3/8/8/8/8/8/6r1/R3K2R w KQ - 0 1")
    val moves = LegalMoveGenerator.generateLegalMoves(pos)

    assertFalse(moves.any { it.from == Square.fromAlgebraic("e1") && it.to == Square.fromAlgebraic("g1") })
  }

  @Test
  fun testPinnedPieceCannotExposeOwnKing() {
    val pos = Position.fromFen("4r1k1/8/8/8/8/8/4R3/4K3 w - - 0 1")
    val moves = LegalMoveGenerator.generateLegalMoves(pos)

    assertFalse(
      moves.any {
        it.from == Square.fromAlgebraic("e2") &&
          it.to == Square.fromAlgebraic("a2")
      }
    )
  }

  @Test
  fun testEnPassantCannotExposeOwnKing() {
    val pos = Position.fromFen("4r1k1/3p4/8/4P3/8/8/8/4K3 w - d6 0 1")
    val moves = LegalMoveGenerator.generateLegalMoves(pos)

    assertFalse(
      moves.any {
        it.from == Square.fromAlgebraic("e5") &&
          it.to == Square.fromAlgebraic("d6") &&
          it.isEnPassant
      }
    )
  }

  @Test
  fun testPromotionMoveUpdatesBoard() {
    val pos = Position.fromFen("4k3/P7/8/8/8/8/8/4K3 w - - 0 1")
    val promotion = LegalMoveGenerator.generateLegalMoves(pos)
      .first { it.from == Square.fromAlgebraic("a7") &&
        it.to == Square.fromAlgebraic("a8") &&
        it.promotion == PieceType.KNIGHT }

    val next = LegalMoveGenerator.makeMove(pos, promotion)

    assertEquals(
      Piece(PieceType.KNIGHT, PieceColor.WHITE),
      next.pieceAt(Square.fromAlgebraic("a8"))
    )
    assertEquals(null, next.pieceAt(Square.fromAlgebraic("a7")))
    assertEquals(PieceColor.BLACK, next.sideToMove)
  }


  @Test
  fun testSanUsesFileDisambiguation() {
    val pos = Position.fromFen("4k3/8/8/8/8/1N3N2/8/4K3 w - - 0 1")
    val move = LegalMoveGenerator.generateLegalMoves(pos).first {
      it.from == Square.fromAlgebraic("b3") && it.to == Square.fromAlgebraic("d2")
    }

    assertEquals("Nbd2", SanFormatter.format(pos, move))
  }

  @Test
  fun testSanUsesRankDisambiguation() {
    val pos = Position.fromFen("4k3/8/8/8/N7/8/N7/4K3 w - - 0 1")
    val move = LegalMoveGenerator.generateLegalMoves(pos).first {
      it.from == Square.fromAlgebraic("a2") && it.to == Square.fromAlgebraic("c3")
    }

    assertEquals("N2c3", SanFormatter.format(pos, move))
  }

  @Test
  fun testSanFormatsCastlingAndPromotion() {
    val castlePos = Position.fromFen("4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1")
    val castle = LegalMoveGenerator.generateLegalMoves(castlePos).first {
      it.from == Square.fromAlgebraic("e1") && it.to == Square.fromAlgebraic("g1")
    }
    assertEquals("O-O", SanFormatter.format(castlePos, castle))

    val promotionPos = Position.fromFen("4k3/P7/8/8/8/8/8/4K3 w - - 0 1")
    val promotion = LegalMoveGenerator.generateLegalMoves(promotionPos).first {
      it.from == Square.fromAlgebraic("a7") &&
        it.to == Square.fromAlgebraic("a8") &&
        it.promotion == PieceType.QUEEN
    }
    assertEquals("a8=Q+", SanFormatter.format(promotionPos, promotion))
  }


}
