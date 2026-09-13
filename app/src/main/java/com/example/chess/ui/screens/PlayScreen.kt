package com.example.chess.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.chess.engine.Evaluation
import com.example.chess.engine.LocalChessEngine
import com.example.chess.engine.MoveAnalysisResult
import com.example.chess.engine.MoveQuality
import com.example.chess.engine.StockfishProfile
import com.example.chess.ui.components.ChessBoardTheme
import com.example.chess.ui.components.InteractiveChessBoard
import com.example.chess.ui.components.StockfishEvalBar
import com.example.chess.ui.theme.CoachPrimary
import com.example.chess.ui.theme.StatusBestMove
import com.example.chess.ui.theme.StatusBlunder
import com.example.chess.ui.theme.StatusExcellent
import com.example.chess.ui.theme.StatusInaccuracy
import com.example.chess.ui.theme.StatusMistake
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PlayerSide {
  WHITE,
  BLACK,
  PASS_AND_PLAY
}

@Composable
fun PlayScreen(
  initialFen: String = Position.STARTING_FEN,
  onOpenAnalysis: ((Position, List<Move>) -> Unit)? = null
) {
  val coroutineScope = rememberCoroutineScope()
  val soundEffects = remember { ChessSoundEffects() }
  val engine = remember { LocalChessEngine() }

  DisposableEffect(Unit) {
    onDispose { soundEffects.release() }
  }

  // Game state
  var position by remember { mutableStateOf(Position.tryFromFen(initialFen).getOrElse { Position.initial() }) }
  val positionHistory = remember { mutableStateListOf(position) }
  val moveHistory = remember { mutableStateListOf<Move>() }

  var selectedSquare by remember { mutableStateOf<Square?>(null) }
  var legalTargets by remember { mutableStateOf<Set<Square>>(emptySet()) }
  var lastMove by remember { mutableStateOf<Move?>(null) }

  // Stockfish settings
  var stockfishElo by remember { mutableStateOf(1200) }
  var playerSide by remember { mutableStateOf(PlayerSide.WHITE) }
  var isFlipped by remember { mutableStateOf(playerSide == PlayerSide.BLACK) }
  var showEvalBar by remember { mutableStateOf(true) }
  var isSoundEnabled by remember { mutableStateOf(true) }
  var boardTheme by remember { mutableStateOf(ChessBoardTheme.CLASSIC_TOURNAMENT) }

  // Engine evaluation and analysis
  var currentEvaluation by remember { mutableStateOf(Evaluation.EVEN) }
  var recommendedMove by remember { mutableStateOf<Move?>(null) }
  var showHintArrow by remember { mutableStateOf(false) }
  var lastMoveAnalysis by remember { mutableStateOf<MoveAnalysisResult?>(null) }
  var isEngineThinking by remember { mutableStateOf(false) }
  var coachComment by remember { mutableStateOf("Game started. Control the center and develop your pieces!") }

  // Dialogs
  var showSettingsDialog by remember { mutableStateOf(false) }
  var showDifficultyDialog by remember { mutableStateOf(false) }
  var showGameOverDialog by remember { mutableStateOf(false) }
  var gameOverReason by remember { mutableStateOf("") }

  var activeAnalysisJob by remember { mutableStateOf<Job?>(null) }

  // Re-run evaluation when position changes
  fun triggerEvaluation(pos: Position) {
    activeAnalysisJob?.cancel()
    activeAnalysisJob = coroutineScope.launch {
      try {
        val (best, eval) = engine.findBestMove(pos, depth = 3)
        currentEvaluation = eval
        recommendedMove = best
      } catch (_: Exception) {
        // Fallback
      }
    }
  }

  LaunchedEffect(position) {
    triggerEvaluation(position)
  }

  // Check for game over (Checkmate / Stalemate)
  fun checkGameOver(pos: Position) {
    val legal = LegalMoveGenerator.generateLegalMoves(pos)
    if (legal.isEmpty()) {
      val inCheck = LegalMoveGenerator.isKingInCheck(pos, pos.sideToMove)
      if (inCheck) {
        val winner = if (pos.sideToMove == PieceColor.WHITE) "Black" else "White"
        gameOverReason = "Checkmate! $winner wins the match."
        soundEffects.play(if (playerSide == PlayerSide.WHITE && winner == "White" || playerSide == PlayerSide.BLACK && winner == "Black") ChessSoundEffects.Cue.VICTORY else ChessSoundEffects.Cue.DEFEAT)
      } else {
        gameOverReason = "Draw by stalemate! No legal moves available."
      }
      showGameOverDialog = true
    }
  }

  // Engine turn logic
  LaunchedEffect(position, playerSide, isEngineThinking) {
    if (showGameOverDialog) return@LaunchedEffect
    val isEngineTurn = when (playerSide) {
      PlayerSide.WHITE -> position.sideToMove == PieceColor.BLACK
      PlayerSide.BLACK -> position.sideToMove == PieceColor.WHITE
      PlayerSide.PASS_AND_PLAY -> false
    }

    if (isEngineTurn && !isEngineThinking) {
      isEngineThinking = true
      coachComment = "Stockfish (${StockfishProfile.forElo(stockfishElo).title}) is calculating..."
      delay(400) // Realistic human pacing

      coroutineScope.launch {
        try {
          val selectedMove = withContext(Dispatchers.Default) {
            engine.selectMoveForElo(position, stockfishElo)
          }
          val isCapture = position.pieceAt(selectedMove.to) != null || selectedMove.isEnPassant
          val nextPos = LegalMoveGenerator.makeMove(position, selectedMove)

          position = nextPos
          positionHistory.add(nextPos)
          moveHistory.add(selectedMove)
          lastMove = selectedMove
          showHintArrow = false

          if (isCapture) {
            soundEffects.play(ChessSoundEffects.Cue.CAPTURE)
          } else {
            soundEffects.play(ChessSoundEffects.Cue.MOVE)
          }

          if (LegalMoveGenerator.isKingInCheck(nextPos, nextPos.sideToMove)) {
            soundEffects.play(ChessSoundEffects.Cue.CHECK)
            coachComment = "Check! Defend your King."
          } else {
            coachComment = "Your turn to move."
          }

          checkGameOver(nextPos)
        } catch (_: Exception) {
          // No moves or ended
        } finally {
          isEngineThinking = false
        }
      }
    }
  }

  // Handle player tapping square
  fun handleSquareTap(square: Square) {
    if (isEngineThinking || showGameOverDialog) return
    val isPlayerTurn = when (playerSide) {
      PlayerSide.WHITE -> position.sideToMove == PieceColor.WHITE
      PlayerSide.BLACK -> position.sideToMove == PieceColor.BLACK
      PlayerSide.PASS_AND_PLAY -> true
    }
    if (!isPlayerTurn) return

    val currentSelected = selectedSquare
    if (currentSelected == null) {
      val occupant = position.pieceAt(square)
      if (occupant != null && occupant.color == position.sideToMove) {
        selectedSquare = square
        val allLegal = LegalMoveGenerator.generateLegalMoves(position)
        legalTargets = allLegal.filter { it.from == square }.map { it.to }.toSet()
      }
    } else {
      if (square in legalTargets) {
        val isPromotion = (position.pieceAt(currentSelected)?.type == PieceType.PAWN) &&
          ((position.sideToMove == PieceColor.WHITE && square.rank == 7) ||
           (position.sideToMove == PieceColor.BLACK && square.rank == 0))
        val promotionType = if (isPromotion) PieceType.QUEEN else null
        val move = Move(
          from = currentSelected,
          to = square,
          promotion = promotionType,
          isEnPassant = (position.pieceAt(currentSelected)?.type == PieceType.PAWN) &&
            (square == position.enPassantSquare)
        )

        val beforePos = position
        val isCapture = position.pieceAt(square) != null || move.isEnPassant
        val nextPos = LegalMoveGenerator.makeMove(beforePos, move)

        position = nextPos
        positionHistory.add(nextPos)
        moveHistory.add(move)
        lastMove = move
        selectedSquare = null
        legalTargets = emptySet()
        showHintArrow = false

        if (isCapture) {
          soundEffects.play(ChessSoundEffects.Cue.CAPTURE)
        } else {
          soundEffects.play(ChessSoundEffects.Cue.MOVE)
        }

        if (LegalMoveGenerator.isKingInCheck(nextPos, nextPos.sideToMove)) {
          soundEffects.play(ChessSoundEffects.Cue.CHECK)
        }

        // Fast asynchronous move analysis
        coroutineScope.launch {
          val analysis = engine.analyzeMove(beforePos, move)
          lastMoveAnalysis = analysis
          coachComment = when (analysis.quality) {
            MoveQuality.BEST -> "★ Brilliant move! Optimal engine choice."
            MoveQuality.EXCELLENT -> "✦ Excellent move! Solid plan."
            MoveQuality.GOOD -> "✓ Good move."
            MoveQuality.INACCURACY -> "⚠️ Slight inaccuracy: ${analysis.explanation}"
            MoveQuality.MISTAKE -> "❌ Mistake: ${analysis.explanation}"
            MoveQuality.BLUNDER -> {
              soundEffects.play(ChessSoundEffects.Cue.BLUNDER)
              "💥 Blunder: ${analysis.explanation}"
            }
          }
        }

        checkGameOver(nextPos)
      } else {
        val occupant = position.pieceAt(square)
        if (occupant != null && occupant.color == position.sideToMove) {
          selectedSquare = square
          val allLegal = LegalMoveGenerator.generateLegalMoves(position)
          legalTargets = allLegal.filter { it.from == square }.map { it.to }.toSet()
        } else {
          selectedSquare = null
          legalTargets = emptySet()
        }
      }
    }
  }

  // Reset Game
  fun startNewGame() {
    position = Position.initial()
    positionHistory.clear()
    positionHistory.add(position)
    moveHistory.clear()
    selectedSquare = null
    legalTargets = emptySet()
    lastMove = null
    lastMoveAnalysis = null
    showHintArrow = false
    showGameOverDialog = false
    coachComment = "New game! Control the center and develop pieces."
    isFlipped = (playerSide == PlayerSide.BLACK)
    triggerEvaluation(position)
  }

  // Undo Move
  fun undoMove() {
    if (positionHistory.size > 1 && !isEngineThinking) {
      // In player vs engine mode, undo both engine reply and player move
      val stepsToUndo = if (playerSide != PlayerSide.PASS_AND_PLAY && positionHistory.size > 2) 2 else 1
      repeat(stepsToUndo) {
        if (positionHistory.size > 1) {
          positionHistory.removeAt(positionHistory.lastIndex)
          if (moveHistory.isNotEmpty()) moveHistory.removeAt(moveHistory.lastIndex)
        }
      }
      position = positionHistory.last()
      lastMove = moveHistory.lastOrNull()
      selectedSquare = null
      legalTargets = emptySet()
      showHintArrow = false
      lastMoveAnalysis = null
      coachComment = "Move undone. Try a stronger path!"
      triggerEvaluation(position)
    }
  }

  // Layout
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp)
      .verticalScroll(rememberScrollState()),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Spacer(modifier = Modifier.height(10.dp))

    // 1. Top Bar: Stockfish Difficulty Pill & Actions
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Difficulty selector pill
      val currentProfile = StockfishProfile.forElo(stockfishElo)
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(20.dp))
          .background(Color(0xFF1E242B))
          .border(1.dp, Color(0x334E5D6C), RoundedCornerShape(20.dp))
          .clickable { showDifficultyDialog = true }
          .padding(horizontal = 12.dp, vertical = 6.dp)
          .testTag("stockfish_difficulty_pill")
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Icon(
            imageVector = Icons.Default.SmartToy,
            contentDescription = "Stockfish",
            tint = CoachPrimary,
            modifier = Modifier.size(18.dp)
          )
          Text(
            text = "Stockfish · ${currentProfile.elo} ELO (${currentProfile.title})",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
          )
        }
      }

      // Quick actions: Settings & Sound
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
          onClick = {
            isSoundEnabled = !isSoundEnabled
            soundEffects.isSoundEnabled = isSoundEnabled
          },
          modifier = Modifier.size(36.dp)
        ) {
          Icon(
            imageVector = if (isSoundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
            contentDescription = "Toggle Sound",
            tint = Color(0xFF94A3B8),
            modifier = Modifier.size(20.dp)
          )
        }

        IconButton(
          onClick = { showSettingsDialog = true },
          modifier = Modifier.size(36.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "Game Settings",
            tint = Color(0xFF94A3B8),
            modifier = Modifier.size(20.dp)
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // 2. Main Chessboard + Live Stockfish Eval Bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically
    ) {
      if (showEvalBar) {
        StockfishEvalBar(
          evaluation = currentEvaluation,
          flipped = isFlipped,
          modifier = Modifier
            .height(310.dp)
            .padding(end = 8.dp)
        )
      }

      InteractiveChessBoard(
        position = position,
        flipped = isFlipped,
        boardTheme = boardTheme,
        selectedSquare = selectedSquare,
        onSquareTapped = { handleSquareTap(it) },
        legalTargetSquares = legalTargets,
        recommendedArrow = if (showHintArrow && recommendedMove != null) {
          Pair(recommendedMove!!.from, recommendedMove!!.to)
        } else null,
        lastMove = lastMove,
        modifier = Modifier.size(310.dp)
      )
    }

    Spacer(modifier = Modifier.height(12.dp))

    // 3. Move Quality Badge (if analyzed)
    lastMoveAnalysis?.let { analysis ->
      AnimatedVisibility(
        visible = true,
        enter = fadeIn() + slideInVertically()
      ) {
        val badgeColor = when (analysis.quality) {
          MoveQuality.BEST -> StatusBestMove
          MoveQuality.EXCELLENT -> StatusExcellent
          MoveQuality.GOOD -> Color(0xFF38BDF8)
          MoveQuality.INACCURACY -> StatusInaccuracy
          MoveQuality.MISTAKE -> StatusMistake
          MoveQuality.BLUNDER -> StatusBlunder
        }

        Box(
          modifier = Modifier
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(badgeColor.copy(alpha = 0.18f))
            .border(1.dp, badgeColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Text(
              text = analysis.quality.badge,
              color = badgeColor,
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp
            )
            Text(
              text = "${analysis.quality.label} (${analysis.playedMove.uci})",
              color = Color.White,
              fontWeight = FontWeight.SemiBold,
              fontSize = 12.sp
            )
          }
        }
      }
    }

    // 4. Action Controls Bar: New Game, Undo, Hint, Flip
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
      // New Game
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .clickable { startNewGame() }
          .padding(horizontal = 8.dp, vertical = 4.dp)
          .testTag("action_new_game")
      ) {
        Icon(
          imageVector = Icons.Default.Refresh,
          contentDescription = "New Game",
          tint = Color(0xFF94A3B8),
          modifier = Modifier.size(20.dp)
        )
        Text(
          text = "New Game",
          color = Color(0xFFCBD5E1),
          fontSize = 11.sp,
          fontWeight = FontWeight.Medium
        )
      }

      // Undo Move
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .clickable(enabled = positionHistory.size > 1 && !isEngineThinking) { undoMove() }
          .padding(horizontal = 8.dp, vertical = 4.dp)
          .testTag("action_undo")
      ) {
        Icon(
          imageVector = Icons.Default.ArrowBack,
          contentDescription = "Take Back",
          tint = if (positionHistory.size > 1 && !isEngineThinking) Color(0xFF94A3B8) else Color(0xFF475569),
          modifier = Modifier.size(20.dp)
        )
        Text(
          text = "Undo",
          color = if (positionHistory.size > 1 && !isEngineThinking) Color(0xFFCBD5E1) else Color(0xFF475569),
          fontSize = 11.sp,
          fontWeight = FontWeight.Medium
        )
      }

      // Hint (Best Move)
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .clickable(enabled = !isEngineThinking) {
            showHintArrow = true
            soundEffects.play(ChessSoundEffects.Cue.HINT)
            recommendedMove?.let {
              coachComment = "Hint: Stockfish suggests ${it.uci} to gain dynamic control."
            }
          }
          .padding(horizontal = 8.dp, vertical = 4.dp)
          .testTag("action_hint")
      ) {
        Icon(
          imageVector = Icons.Default.Lightbulb,
          contentDescription = "Best Move Hint",
          tint = if (showHintArrow) CoachPrimary else Color(0xFF94A3B8),
          modifier = Modifier.size(20.dp)
        )
        Text(
          text = "Hint",
          color = if (showHintArrow) CoachPrimary else Color(0xFFCBD5E1),
          fontSize = 11.sp,
          fontWeight = FontWeight.Medium
        )
      }

      // Flip Board
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .clickable { isFlipped = !isFlipped }
          .padding(horizontal = 8.dp, vertical = 4.dp)
          .testTag("action_flip")
      ) {
        Icon(
          imageVector = Icons.Default.FlipCameraAndroid,
          contentDescription = "Flip Perspective",
          tint = Color(0xFF94A3B8),
          modifier = Modifier.size(20.dp)
        )
        Text(
          text = "Flip",
          color = Color(0xFFCBD5E1),
          fontSize = 11.sp,
          fontWeight = FontWeight.Medium
        )
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // 5. Coach Advice / Engine Status Card
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(Color(0xFF181B20))
        .border(1.dp, Color(0xFF2D323B), RoundedCornerShape(14.dp))
        .padding(14.dp)
    ) {
      Column {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Box(
            modifier = Modifier
              .size(8.dp)
              .clip(CircleShape)
              .background(if (isEngineThinking) CoachPrimary else Color(0xFF10B981))
          )
          Text(
            text = if (isEngineThinking) "STOCKFISH IS CALCULATING..." else "COACH ADVICE",
            color = Color(0xFF94A3B8),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
          )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
          text = coachComment,
          color = Color(0xFFF1F5F9),
          fontSize = 13.sp,
          lineHeight = 19.sp
        )
      }
    }

    Spacer(modifier = Modifier.height(100.dp)) // Nav bar safe clearance
  }

  // ==========================================
  // Stockfish Difficulty Adjustment Dialog
  // ==========================================
  if (showDifficultyDialog) {
    AlertDialog(
      onDismissRequest = { showDifficultyDialog = false },
      title = {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(Icons.Default.SmartToy, contentDescription = null, tint = CoachPrimary)
          Text("Adjust Stockfish Engine", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
      },
      text = {
        Column(modifier = Modifier.fillMaxWidth()) {
          val activeProfile = StockfishProfile.forElo(stockfishElo)

          Text(
            text = "Current: ${activeProfile.title} (${activeProfile.elo} ELO)",
            color = CoachPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = "Category: ${activeProfile.category} · Depth: ${activeProfile.depth}",
            color = Color(0xFF94A3B8),
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 12.dp)
          )

          // Slider
          Slider(
            value = stockfishElo.toFloat(),
            onValueChange = { stockfishElo = it.toInt() },
            valueRange = 600f..2600f,
            steps = 19,
            colors = SliderDefaults.colors(
              thumbColor = CoachPrimary,
              activeTrackColor = CoachPrimary,
              inactiveTrackColor = Color(0xFF334155)
            )
          )

          Spacer(modifier = Modifier.height(8.dp))

          // Quick Presets chips
          Text(
            text = "Quick Presets:",
            color = Color(0xFFCBD5E1),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp)
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            listOf(600, 1000, 1400, 1800, 2400).forEach { elo ->
              val isSelected = StockfishProfile.forElo(stockfishElo).elo == StockfishProfile.forElo(elo).elo
              Box(
                modifier = Modifier
                  .weight(1f)
                  .clip(RoundedCornerShape(8.dp))
                  .background(if (isSelected) CoachPrimary.copy(alpha = 0.25f) else Color(0xFF1E242B))
                  .border(
                    1.dp,
                    if (isSelected) CoachPrimary else Color(0xFF334155),
                    RoundedCornerShape(8.dp)
                  )
                  .clickable { stockfishElo = elo }
                  .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = "$elo",
                  color = if (isSelected) CoachPrimary else Color.White,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showDifficultyDialog = false }) {
          Text("Done", color = CoachPrimary, fontWeight = FontWeight.Bold)
        }
      },
      containerColor = Color(0xFF181B20)
    )
  }

  // ==========================================
  // Game Settings Dialog
  // ==========================================
  if (showSettingsDialog) {
    AlertDialog(
      onDismissRequest = { showSettingsDialog = false },
      title = {
        Text("Game Settings", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
      },
      text = {
        Column(modifier = Modifier.fillMaxWidth()) {
          // Play Side Selector
          Text(
            text = "Play As",
            color = Color(0xFFCBD5E1),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp)
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            listOf(
              PlayerSide.WHITE to "White ⚪",
              PlayerSide.BLACK to "Black ⚫",
              PlayerSide.PASS_AND_PLAY to "2-Player 👥"
            ).forEach { (side, label) ->
              val isSelected = playerSide == side
              Box(
                modifier = Modifier
                  .weight(1f)
                  .clip(RoundedCornerShape(8.dp))
                  .background(if (isSelected) CoachPrimary.copy(alpha = 0.25f) else Color(0xFF1E242B))
                  .border(1.dp, if (isSelected) CoachPrimary else Color(0xFF334155), RoundedCornerShape(8.dp))
                  .clickable {
                    playerSide = side
                    isFlipped = (side == PlayerSide.BLACK)
                  }
                  .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = label,
                  color = if (isSelected) CoachPrimary else Color.White,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          // Toggle Live Eval Bar
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text("Stockfish Advantage Bar", color = Color.White, fontSize = 14.sp)
              Text("Show real-time winning evaluation", color = Color(0xFF94A3B8), fontSize = 11.sp)
            }
            Switch(
              checked = showEvalBar,
              onCheckedChange = { showEvalBar = it },
              colors = SwitchDefaults.colors(
                checkedThumbColor = CoachPrimary,
                checkedTrackColor = CoachPrimary.copy(alpha = 0.5f)
              )
            )
          }

          Spacer(modifier = Modifier.height(12.dp))

          // Board Theme Selector
          Text(
            text = "Board Theme",
            color = Color(0xFFCBD5E1),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp)
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            ChessBoardTheme.values().forEach { theme ->
              val isSelected = boardTheme == theme
              Box(
                modifier = Modifier
                  .weight(1f)
                  .clip(RoundedCornerShape(8.dp))
                  .background(if (isSelected) CoachPrimary.copy(alpha = 0.25f) else Color(0xFF1E242B))
                  .border(1.dp, if (isSelected) CoachPrimary else Color(0xFF334155), RoundedCornerShape(8.dp))
                  .clickable { boardTheme = theme }
                  .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = theme.label.split(" ").first(),
                  color = if (isSelected) CoachPrimary else Color.White,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showSettingsDialog = false }) {
          Text("Close", color = CoachPrimary, fontWeight = FontWeight.Bold)
        }
      },
      containerColor = Color(0xFF181B20)
    )
  }

  // ==========================================
  // Game Over Dialog
  // ==========================================
  if (showGameOverDialog) {
    AlertDialog(
      onDismissRequest = { showGameOverDialog = false },
      title = {
        Text("Game Complete", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
      },
      text = {
        Column {
          Text(
            text = gameOverReason,
            color = Color(0xFFE2E8F0),
            fontSize = 15.sp,
            lineHeight = 22.sp
          )
        }
      },
      confirmButton = {
        TextButton(onClick = {
          showGameOverDialog = false
          startNewGame()
        }) {
          Text("Play Again", color = CoachPrimary, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showGameOverDialog = false }) {
          Text("Inspect Board", color = Color(0xFF94A3B8))
        }
      },
      containerColor = Color(0xFF181B20)
    )
  }
}
