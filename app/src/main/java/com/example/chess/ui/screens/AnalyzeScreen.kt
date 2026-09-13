package com.example.chess.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import com.example.chess.ui.components.ChessBoardTheme
import com.example.chess.ui.components.InteractiveChessBoard
import com.example.chess.ui.components.StockfishEvalBar
import com.example.chess.ui.theme.CoachPrimary
import com.example.chess.ui.theme.StatusBestMove
import com.example.chess.ui.theme.StatusBlunder
import com.example.chess.ui.theme.StatusExcellent
import com.example.chess.ui.theme.StatusInaccuracy
import com.example.chess.ui.theme.StatusMistake
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun AnalyzeScreen(
  initialFen: String = Position.STARTING_FEN
) {
  val coroutineScope = rememberCoroutineScope()
  val soundEffects = remember { ChessSoundEffects() }
  val engine = remember { LocalChessEngine() }

  DisposableEffect(Unit) {
    onDispose { soundEffects.release() }
  }

  var position by remember { mutableStateOf(Position.tryFromFen(initialFen).getOrElse { Position.initial() }) }
  val historyPositions = remember { mutableStateListOf(position) }
  val historyMoves = remember { mutableStateListOf<Move>() }
  var historyIndex by remember { mutableStateOf(0) }

  var selectedSquare by remember { mutableStateOf<Square?>(null) }
  var legalTargets by remember { mutableStateOf<Set<Square>>(emptySet()) }
  var isFlipped by remember { mutableStateOf(false) }

  var currentEvaluation by remember { mutableStateOf(Evaluation.EVEN) }
  var bestMove by remember { mutableStateOf<Move?>(null) }
  var isAnalyzing by remember { mutableStateOf(false) }
  var activeAnalysisJob by remember { mutableStateOf<Job?>(null) }

  var lastMoveAnalysis by remember { mutableStateOf<MoveAnalysisResult?>(null) }
  var showFenDialog by remember { mutableStateOf(false) }
  var fenInputText by remember { mutableStateOf("") }
  var fenError by remember { mutableStateOf<String?>(null) }

  fun evaluateCurrentPosition(pos: Position) {
    activeAnalysisJob?.cancel()
    activeAnalysisJob = coroutineScope.launch {
      isAnalyzing = true
      try {
        val (best, eval) = engine.findBestMove(pos, depth = 3)
        bestMove = best
        currentEvaluation = eval
      } catch (_: Exception) {
        // Position terminal
      } finally {
        isAnalyzing = false
      }
    }
  }

  LaunchedEffect(position) {
    evaluateCurrentPosition(position)
  }

  fun makePlayerMove(move: Move) {
    val before = position
    val isCapture = before.pieceAt(move.to) != null || move.isEnPassant
    val next = LegalMoveGenerator.makeMove(before, move)

    // Truncate forward history if branching
    if (historyIndex < historyPositions.lastIndex) {
      val dropCount = historyPositions.lastIndex - historyIndex
      repeat(dropCount) {
        historyPositions.removeAt(historyPositions.lastIndex)
        if (historyMoves.isNotEmpty()) historyMoves.removeAt(historyMoves.lastIndex)
      }
    }

    historyPositions.add(next)
    historyMoves.add(move)
    historyIndex = historyPositions.lastIndex
    position = next
    selectedSquare = null
    legalTargets = emptySet()

    if (isCapture) {
      soundEffects.play(ChessSoundEffects.Cue.CAPTURE)
    } else {
      soundEffects.play(ChessSoundEffects.Cue.MOVE)
    }

    coroutineScope.launch {
      val analysis = engine.analyzeMove(before, move)
      lastMoveAnalysis = analysis
    }
  }

  fun handleSquareTap(square: Square) {
    val currentSelected = selectedSquare
    if (currentSelected == null) {
      val piece = position.pieceAt(square)
      if (piece != null && piece.color == position.sideToMove) {
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
        makePlayerMove(move)
      } else {
        val piece = position.pieceAt(square)
        if (piece != null && piece.color == position.sideToMove) {
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

  fun jumpToHistory(index: Int) {
    if (index in 0..historyPositions.lastIndex) {
      historyIndex = index
      position = historyPositions[index]
      selectedSquare = null
      legalTargets = emptySet()
      lastMoveAnalysis = null
    }
  }

  fun resetBoard() {
    position = Position.initial()
    historyPositions.clear()
    historyPositions.add(position)
    historyMoves.clear()
    historyIndex = 0
    selectedSquare = null
    legalTargets = emptySet()
    lastMoveAnalysis = null
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp)
      .verticalScroll(rememberScrollState()),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Spacer(modifier = Modifier.height(10.dp))

    // Top Header: Stockfish Evaluation & Status
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
          imageVector = Icons.Default.AutoGraph,
          contentDescription = "Analysis",
          tint = CoachPrimary,
          modifier = Modifier.size(20.dp)
        )
        Text(
          text = "Stockfish Evaluation",
          color = Color.White,
          fontSize = 17.sp,
          fontWeight = FontWeight.Bold
        )
      }

      // Eval badge chip
      val evalText = currentEvaluation.format()
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(12.dp))
          .background(Color(0xFF1E242B))
          .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
          .padding(horizontal = 10.dp, vertical = 4.dp)
      ) {
        Text(
          text = "Eval: $evalText",
          color = if ((currentEvaluation.centipawns ?: 0) >= 0) CoachPrimary else Color(0xFF38BDF8),
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold
        )
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Board + Stockfish Eval Bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically
    ) {
      StockfishEvalBar(
        evaluation = currentEvaluation,
        flipped = isFlipped,
        modifier = Modifier
          .height(310.dp)
          .padding(end = 8.dp)
      )

      InteractiveChessBoard(
        position = position,
        flipped = isFlipped,
        boardTheme = ChessBoardTheme.CLASSIC_TOURNAMENT,
        selectedSquare = selectedSquare,
        onSquareTapped = { handleSquareTap(it) },
        legalTargetSquares = legalTargets,
        recommendedArrow = bestMove?.let { Pair(it.from, it.to) },
        lastMove = historyMoves.getOrNull(historyIndex - 1),
        modifier = Modifier.size(310.dp)
      )
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Engine recommendation card
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(Color(0xFF181B20))
        .border(1.dp, Color(0xFF2D323B), RoundedCornerShape(14.dp))
        .padding(12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Box(
              modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (isAnalyzing) CoachPrimary else Color(0xFF10B981))
            )
            Text(
              text = if (isAnalyzing) "CALCULATING BEST MOVE..." else "BEST ENGINE MOVE",
              color = Color(0xFF94A3B8),
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              letterSpacing = 0.6.sp
            )
          }

          Spacer(modifier = Modifier.height(4.dp))

          Text(
            text = bestMove?.let { "Play ${it.uci} (${currentEvaluation.format()})" } ?: "Position Terminal",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
          )
        }

        bestMove?.let { move ->
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(10.dp))
              .background(CoachPrimary.copy(alpha = 0.2f))
              .border(1.dp, CoachPrimary, RoundedCornerShape(10.dp))
              .clickable { makePlayerMove(move) }
              .padding(horizontal = 10.dp, vertical = 6.dp)
              .testTag("play_best_move_btn")
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = CoachPrimary,
                modifier = Modifier.size(16.dp)
              )
              Text(
                text = "Play",
                color = CoachPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Move History Tape
    if (historyMoves.isNotEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(Color(0xFF14171C))
          .border(1.dp, Color(0xFF242933), RoundedCornerShape(12.dp))
          .padding(8.dp)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Starting position chip
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .background(if (historyIndex == 0) CoachPrimary.copy(alpha = 0.3f) else Color.Transparent)
              .clickable { jumpToHistory(0) }
              .padding(horizontal = 8.dp, vertical = 4.dp)
          ) {
            Text("Start", color = if (historyIndex == 0) CoachPrimary else Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }

          historyMoves.forEachIndexed { idx, move ->
            val isCurrent = (historyIndex == idx + 1)
            val moveNumber = (idx / 2) + 1
            val isWhiteMove = (idx % 2 == 0)
            val label = if (isWhiteMove) "$moveNumber. ${move.uci}" else move.uci

            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isCurrent) CoachPrimary.copy(alpha = 0.3f) else Color(0xFF1E242B))
                .border(1.dp, if (isCurrent) CoachPrimary else Color(0xFF2E3846), RoundedCornerShape(6.dp))
                .clickable { jumpToHistory(idx + 1) }
                .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
              Text(
                text = label,
                color = if (isCurrent) CoachPrimary else Color(0xFFCBD5E1),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))
    }

    // Step navigation and utility action bar
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
      // Step back
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .clickable(enabled = historyIndex > 0) { jumpToHistory(historyIndex - 1) }
          .padding(8.dp)
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = "Previous Move",
          tint = if (historyIndex > 0) Color(0xFF94A3B8) else Color(0xFF475569),
          modifier = Modifier.size(20.dp)
        )
        Text("Prev", color = if (historyIndex > 0) Color(0xFFCBD5E1) else Color(0xFF475569), fontSize = 11.sp)
      }

      // Step forward
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .clickable(enabled = historyIndex < historyPositions.lastIndex) { jumpToHistory(historyIndex + 1) }
          .padding(8.dp)
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowForward,
          contentDescription = "Next Move",
          tint = if (historyIndex < historyPositions.lastIndex) Color(0xFF94A3B8) else Color(0xFF475569),
          modifier = Modifier.size(20.dp)
        )
        Text("Next", color = if (historyIndex < historyPositions.lastIndex) Color(0xFFCBD5E1) else Color(0xFF475569), fontSize = 11.sp)
      }

      // Flip perspective
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .clickable { isFlipped = !isFlipped }
          .padding(8.dp)
      ) {
        Icon(
          imageVector = Icons.Default.FlipCameraAndroid,
          contentDescription = "Flip Board",
          tint = Color(0xFF94A3B8),
          modifier = Modifier.size(20.dp)
        )
        Text("Flip", color = Color(0xFFCBD5E1), fontSize = 11.sp)
      }

      // Set FEN
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .clickable {
            fenInputText = position.toFen()
            showFenDialog = true
          }
          .padding(8.dp)
      ) {
        Icon(
          imageVector = Icons.Default.ContentPaste,
          contentDescription = "Load FEN",
          tint = Color(0xFF94A3B8),
          modifier = Modifier.size(20.dp)
        )
        Text("FEN", color = Color(0xFFCBD5E1), fontSize = 11.sp)
      }

      // Reset
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .clickable { resetBoard() }
          .padding(8.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Refresh,
          contentDescription = "Reset Position",
          tint = Color(0xFF94A3B8),
          modifier = Modifier.size(20.dp)
        )
        Text("Reset", color = Color(0xFFCBD5E1), fontSize = 11.sp)
      }
    }

    Spacer(modifier = Modifier.height(100.dp))
  }

  // FEN Position Input Dialog
  if (showFenDialog) {
    AlertDialog(
      onDismissRequest = { showFenDialog = false },
      title = { Text("Load Position from FEN", color = Color.White, fontWeight = FontWeight.Bold) },
      text = {
        Column {
          Text("Paste standard Forsyth-Edwards Notation (FEN) to evaluate:", color = Color(0xFF94A3B8), fontSize = 13.sp)
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = fenInputText,
            onValueChange = {
              fenInputText = it
              fenError = null
            },
            placeholder = { Text("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", fontSize = 12.sp) },
            singleLine = false,
            modifier = Modifier.fillMaxWidth()
          )
          fenError?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(it, color = StatusBlunder, fontSize = 12.sp)
          }
        }
      },
      confirmButton = {
        TextButton(onClick = {
          val parsed = Position.tryFromFen(fenInputText.trim())
          if (parsed.isSuccess) {
            val newPos = parsed.getOrThrow()
            position = newPos
            historyPositions.clear()
            historyPositions.add(newPos)
            historyMoves.clear()
            historyIndex = 0
            showFenDialog = false
          } else {
            fenError = "Invalid FEN string format."
          }
        }) {
          Text("Load", color = CoachPrimary, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showFenDialog = false }) {
          Text("Cancel", color = Color(0xFF94A3B8))
        }
      },
      containerColor = Color(0xFF181B20)
    )
  }
}
