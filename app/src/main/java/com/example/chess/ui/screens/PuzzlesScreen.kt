package com.example.chess.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chess.audio.ChessSoundEffects
import com.example.chess.core.LegalMoveGenerator
import com.example.chess.core.Move
import com.example.chess.core.PieceColor
import com.example.chess.core.PieceType
import com.example.chess.core.Position
import com.example.chess.core.Square
import com.example.chess.tactics.TacticalPuzzle
import com.example.chess.tactics.TacticsRepository
import com.example.chess.ui.components.ChessBoardTheme
import com.example.chess.ui.components.InteractiveChessBoard
import com.example.chess.ui.theme.CoachPrimary
import com.example.chess.ui.theme.StatusBestMove
import com.example.chess.ui.theme.StatusBlunder
import com.example.chess.ui.theme.StatusExcellent

@Composable
fun PuzzlesScreen() {
  val soundEffects = remember { ChessSoundEffects() }
  val allPuzzles = remember { TacticsRepository.getAllPuzzles() }

  DisposableEffect(Unit) {
    onDispose { soundEffects.release() }
  }

  var puzzleIndex by remember { mutableStateOf(0) }
  var currentPuzzle by remember { mutableStateOf(allPuzzles[0]) }
  var currentPosition by remember { mutableStateOf(Position.tryFromFen(currentPuzzle.fen).getOrNull() ?: Position.initial()) }

  var selectedSquare by remember { mutableStateOf<Square?>(null) }
  var legalTargets by remember { mutableStateOf<Set<Square>>(emptySet()) }
  var lastMove by remember { mutableStateOf<Move?>(null) }

  var streakCount by remember { mutableStateOf(0) }
  var userRating by remember { mutableStateOf(1200) }

  var isSolved by remember { mutableStateOf(false) }
  var isFailed by remember { mutableStateOf(false) }
  var showHint by remember { mutableStateOf(false) }
  var showSolution by remember { mutableStateOf(false) }

  fun loadPuzzle(index: Int) {
    val clamped = index % allPuzzles.size
    puzzleIndex = clamped
    val puzzle = allPuzzles[clamped]
    currentPuzzle = puzzle
    currentPosition = Position.tryFromFen(puzzle.fen).getOrNull() ?: Position.initial()
    selectedSquare = null
    legalTargets = emptySet()
    lastMove = null
    isSolved = false
    isFailed = false
    showHint = false
    showSolution = false
  }

  fun handleSquareTap(square: Square) {
    if (isSolved) return

    val currentSelected = selectedSquare
    if (currentSelected == null) {
      val piece = currentPosition.pieceAt(square)
      if (piece != null && piece.color == currentPosition.sideToMove) {
        selectedSquare = square
        val allLegal = LegalMoveGenerator.generateLegalMoves(currentPosition)
        legalTargets = allLegal.filter { it.from == square }.map { it.to }.toSet()
      }
    } else {
      if (square in legalTargets) {
        val isPromotion = (currentPosition.pieceAt(currentSelected)?.type == PieceType.PAWN) &&
          ((currentPosition.sideToMove == PieceColor.WHITE && square.rank == 7) ||
           (currentPosition.sideToMove == PieceColor.BLACK && square.rank == 0))
        val move = Move(
          from = currentSelected,
          to = square,
          promotion = if (isPromotion) PieceType.QUEEN else null,
          isEnPassant = (currentPosition.pieceAt(currentSelected)?.type == PieceType.PAWN) &&
            (square == currentPosition.enPassantSquare)
        )

        val next = LegalMoveGenerator.makeMove(currentPosition, move)
        currentPosition = next
        lastMove = move
        selectedSquare = null
        legalTargets = emptySet()

        val expected = currentPuzzle.solutionMoves.firstOrNull()
        val isCorrect = expected != null && move.from == expected.from && move.to == expected.to

        if (isCorrect) {
          isSolved = true
          isFailed = false
          streakCount += 1
          userRating += 12
          soundEffects.play(ChessSoundEffects.Cue.VICTORY)
        } else {
          isFailed = true
          streakCount = 0
          soundEffects.play(ChessSoundEffects.Cue.BLUNDER)
        }
      } else {
        val piece = currentPosition.pieceAt(square)
        if (piece != null && piece.color == currentPosition.sideToMove) {
          selectedSquare = square
          val allLegal = LegalMoveGenerator.generateLegalMoves(currentPosition)
          legalTargets = allLegal.filter { it.from == square }.map { it.to }.toSet()
        } else {
          selectedSquare = null
          legalTargets = emptySet()
        }
      }
    }
  }

  fun retryPuzzle() {
    currentPosition = Position.tryFromFen(currentPuzzle.fen).getOrNull() ?: Position.initial()
    selectedSquare = null
    legalTargets = emptySet()
    lastMove = null
    isSolved = false
    isFailed = false
    showHint = false
    showSolution = false
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp)
      .verticalScroll(rememberScrollState()),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Spacer(modifier = Modifier.height(10.dp))

    // Top Header: Rating & Streak
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Extension,
          contentDescription = "Tactics",
          tint = CoachPrimary,
          modifier = Modifier.size(20.dp)
        )
        Text(
          text = "Daily Tactics Trainer",
          color = Color.White,
          fontSize = 17.sp,
          fontWeight = FontWeight.Bold
        )
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Rating pill
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E242B))
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
          Text(
            text = "Rating: $userRating",
            color = CoachPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
          )
        }

        // Streak pill
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E242B))
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
          Text(
            text = "🔥 $streakCount",
            color = Color(0xFFF97316),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Objective Banner
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(Color(0xFF181B20))
        .border(1.dp, Color(0xFF2D323B), RoundedCornerShape(12.dp))
        .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          val sideText = if (currentPuzzle.sideToPlay == PieceColor.WHITE) "White to move" else "Black to move"
          Text(
            text = sideText,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = currentPuzzle.title,
            color = Color(0xFF94A3B8),
            fontSize = 12.sp
          )
        }

        // Theme tag
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x22F59E0B))
            .border(1.dp, Color(0x55F59E0B), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Text(
            text = currentPuzzle.theme.label.split(" ").first(),
            color = CoachPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Chessboard
    InteractiveChessBoard(
      position = currentPosition,
      flipped = currentPuzzle.sideToPlay == PieceColor.BLACK,
      boardTheme = ChessBoardTheme.CLASSIC_TOURNAMENT,
      selectedSquare = selectedSquare,
      onSquareTapped = { handleSquareTap(it) },
      legalTargetSquares = legalTargets,
      recommendedArrow = if (showSolution || isSolved) {
        currentPuzzle.solutionMoves.firstOrNull()?.let { Pair(it.from, it.to) }
      } else if (showHint) {
        currentPuzzle.hintLadder.level4DirectMove?.let { Pair(it.from, it.to) }
      } else null,
      lastMove = lastMove,
      modifier = Modifier.size(320.dp)
    )

    Spacer(modifier = Modifier.height(12.dp))

    // Feedback Banner (Success / Failure)
    AnimatedVisibility(
      visible = isSolved || isFailed || showHint,
      enter = fadeIn() + slideInVertically()
    ) {
      val (bgColor, borderColor, text) = when {
        isSolved -> Triple(
          StatusExcellent.copy(alpha = 0.2f),
          StatusExcellent,
          "★ Correct! ${currentPuzzle.explanation}"
        )
        isFailed -> Triple(
          StatusBlunder.copy(alpha = 0.2f),
          StatusBlunder,
          "Not quite! That move does not win decisive advantage. Try again!"
        )
        showHint -> Triple(
          CoachPrimary.copy(alpha = 0.2f),
          CoachPrimary,
          "Hint: ${currentPuzzle.hintLadder.level1Concept}"
        )
        else -> Triple(Color.Transparent, Color.Transparent, "")
      }

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(bgColor)
          .border(1.dp, borderColor, RoundedCornerShape(12.dp))
          .padding(12.dp)
      ) {
        Text(
          text = text,
          color = Color.White,
          fontSize = 13.sp,
          lineHeight = 18.sp
        )
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Actions Bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(Color(0xFF181B20))
        .border(1.dp, Color(0xFF2D323B), RoundedCornerShape(14.dp))
        .padding(horizontal = 12.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceAround,
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Retry
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .clickable { retryPuzzle() }
          .padding(8.dp)
          .testTag("puzzle_retry_btn")
      ) {
        Icon(
          imageVector = Icons.Default.Refresh,
          contentDescription = "Retry",
          tint = Color(0xFF94A3B8),
          modifier = Modifier.size(20.dp)
        )
        Text("Retry", color = Color(0xFFCBD5E1), fontSize = 11.sp)
      }

      // Hint
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .clickable {
            showHint = true
            soundEffects.play(ChessSoundEffects.Cue.HINT)
          }
          .padding(8.dp)
          .testTag("puzzle_hint_btn")
      ) {
        Icon(
          imageVector = Icons.Default.Lightbulb,
          contentDescription = "Hint",
          tint = if (showHint) CoachPrimary else Color(0xFF94A3B8),
          modifier = Modifier.size(20.dp)
        )
        Text("Hint", color = if (showHint) CoachPrimary else Color(0xFFCBD5E1), fontSize = 11.sp)
      }

      // Show Solution
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .clickable {
            showSolution = true
            isSolved = true
          }
          .padding(8.dp)
          .testTag("puzzle_solution_btn")
      ) {
        Icon(
          imageVector = Icons.Default.HelpOutline,
          contentDescription = "Show Solution",
          tint = if (showSolution) CoachPrimary else Color(0xFF94A3B8),
          modifier = Modifier.size(20.dp)
        )
        Text("Solution", color = if (showSolution) CoachPrimary else Color(0xFFCBD5E1), fontSize = 11.sp)
      }

      // Next Puzzle
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .clickable { loadPuzzle(puzzleIndex + 1) }
          .padding(8.dp)
          .testTag("puzzle_next_btn")
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowForward,
          contentDescription = "Next Puzzle",
          tint = CoachPrimary,
          modifier = Modifier.size(20.dp)
        )
        Text("Next", color = CoachPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
      }
    }

    Spacer(modifier = Modifier.height(100.dp))
  }
}
