package com.example.chess.ui.screens

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chess.audio.rememberChessSoundEffects
import com.example.chess.audio.rememberVoiceCoach
import com.example.chess.core.GameStatus
import com.example.chess.core.LegalMoveGenerator
import com.example.chess.core.Move
import com.example.chess.core.PieceColor
import com.example.chess.core.Position
import com.example.chess.core.Square
import com.example.chess.data.ChessDatabaseProvider
import com.example.chess.data.MistakeRecord
import com.example.chess.engine.Evaluation
import com.example.chess.engine.LocalChessEngine
import com.example.chess.engine.TrainingLevel
import com.example.chess.ui.components.FenPgnImportDialog
import com.example.chess.ui.components.ImportMode
import com.example.chess.ui.components.InteractiveChessBoard
import com.example.chess.ui.theme.CoachAccentGold
import com.example.chess.ui.theme.CoachPrimary
import com.example.chess.ui.theme.LiquidGlassBorder
import com.example.chess.ui.theme.LiquidGlassBorderGold
import com.example.chess.ui.theme.LiquidGlassBorderCyan
import com.example.chess.ui.theme.LiquidGlassSurface
import com.example.chess.ui.theme.LiquidGlassSurfaceElevated
import com.example.chess.ui.theme.LiquidGlassSurfaceSubtle
import com.example.chess.ui.theme.liquidGlassCard
import com.example.chess.ui.theme.liquidGlassPill
import com.example.chess.ui.theme.StatusBlunder
import com.example.chess.ui.theme.TextBody
import com.example.chess.ui.theme.TextMuted
import com.example.chess.ui.theme.TextTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tab 3: Arena (Calibrated Sparring Arena) with Live Blunder Recording.
 * Evaluates player moves in real-time. If eval drops sharply (> 1.8 pawns),
 * automatically saves the position to the Room Mistake Book for Spaced-Repetition Review.
 */
@Composable
fun ArenaScreen(
  initialFen: String = Position.STARTING_FEN,
  selectedLevel: TrainingLevel = TrainingLevel.INTERMEDIATE_1200,
  onGameFinished: (Position, List<Move>) -> Unit = { _, _ -> },
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val engine = remember { LocalChessEngine() }
  val coroutineScope = rememberCoroutineScope()
  val voiceCoach = rememberVoiceCoach()
  val soundEffects = rememberChessSoundEffects()
  var voiceEnabled by remember { mutableStateOf(true) }
  var soundEnabled by remember { mutableStateOf(true) }
  var showImportDialog by remember { mutableStateOf(false) }
  var arenaGameTitle by remember { mutableStateOf<String?>(null) }

  var position by remember(initialFen) { mutableStateOf(Position.fromFen(initialFen)) }
  var currentLevel by remember { mutableStateOf(selectedLevel) }
  var playerColor by remember { mutableStateOf(PieceColor.WHITE) }

  val playedMoves = remember { mutableStateListOf<Move>() }
  val positionHistory = remember { mutableStateListOf<Position>() }

  var selectedSquare by remember { mutableStateOf<Square?>(null) }
  var legalTargetSquares by remember { mutableStateOf<Set<Square>>(emptySet()) }
  var lastMove by remember { mutableStateOf<Move?>(null) }

  var isEngineThinking by remember { mutableStateOf(false) }
  var currentEval by remember { mutableStateOf(Evaluation.EVEN) }

  // Coach Whisper (4-Level Ladder in-game)
  var whisperLevel by remember { mutableStateOf(0) }
  var whisperText by remember { mutableStateOf<String?>(null) }
  var whisperArrow by remember { mutableStateOf<Pair<Square, Square>?>(null) }

  // Toast / Banner alert when a mistake is auto-recorded to Room database
  var recordedBlunderAlert by remember { mutableStateOf<String?>(null) }

  // Game End State
  val gameStatus = remember(position) { LegalMoveGenerator.getGameStatus(position) }

  fun takebackMove() {
    if (isEngineThinking) return
    if (playedMoves.size >= 2 && positionHistory.size >= 2) {
      playedMoves.removeAt(playedMoves.size - 1)
      positionHistory.removeAt(positionHistory.size - 1)
      playedMoves.removeAt(playedMoves.size - 1)
      val restored = positionHistory.removeAt(positionHistory.size - 1)
      position = restored
      lastMove = playedMoves.lastOrNull()
      selectedSquare = null
      legalTargetSquares = emptySet()
      whisperArrow = null
      whisperText = null
      if (soundEnabled) soundEffects.playHint()
    } else if (playedMoves.size == 1 && positionHistory.size >= 1) {
      playedMoves.removeAt(0)
      val restored = positionHistory.removeAt(0)
      position = restored
      lastMove = null
      selectedSquare = null
      legalTargetSquares = emptySet()
      whisperArrow = null
      whisperText = null
      if (soundEnabled) soundEffects.playHint()
    }
  }

  // Sound cues on game finish
  LaunchedEffect(gameStatus) {
    if (soundEnabled && gameStatus == GameStatus.CHECKMATE) {
      val winner = position.sideToMove.opposite()
      if (playerColor == winner) soundEffects.playVictory() else soundEffects.playDefeat()
    }
  }

  // Update evaluation when position updates
  LaunchedEffect(position) {
    val eval = engine.evaluatePosition(position, depth = 3)
    currentEval = eval
  }

  // Handle Bot Turn
  LaunchedEffect(position, isEngineThinking) {
    if (position.sideToMove != playerColor && gameStatus == GameStatus.IN_PROGRESS && !isEngineThinking) {
      isEngineThinking = true
      delay(400) // Brief natural human-like pause
      val botMove = engine.selectMove(position, currentLevel)
      val destOccupant = position.pieceAt(botMove.to)
      val isCapture = destOccupant != null || botMove.isEnPassant
      positionHistory.add(position)
      val nextPos = LegalMoveGenerator.makeMove(position, botMove)
      val isCheck = LegalMoveGenerator.isKingInCheck(nextPos, nextPos.sideToMove)
      position = nextPos
      lastMove = botMove
      playedMoves.add(botMove)
      isEngineThinking = false

      if (soundEnabled) {
        soundEffects.playMove(isCapture = isCapture, isCheck = isCheck)
      }
    }
  }

  fun onSquareClicked(square: Square) {
    if (position.sideToMove != playerColor || isEngineThinking || gameStatus != GameStatus.IN_PROGRESS) return

    val piece = position.pieceAt(square)

    if (selectedSquare == null) {
      if (piece != null && piece.color == playerColor) {
        selectedSquare = square
        val allLegal = LegalMoveGenerator.generateLegalMoves(position)
        legalTargetSquares = allLegal.filter { it.from == square }.map { it.to }.toSet()
      }
    } else {
      val src = selectedSquare!!
      val allLegal = LegalMoveGenerator.generateLegalMoves(position)
      val moveAttempt = allLegal.find { it.from == src && it.to == square }

      if (moveAttempt != null) {
        val fenBefore = position.toFen()
        val prevEval = currentEval

        // Execute player move
        val destOccupant = position.pieceAt(moveAttempt.to)
        val isCapture = destOccupant != null || moveAttempt.isEnPassant
        positionHistory.add(position)
        val nextPos = LegalMoveGenerator.makeMove(position, moveAttempt)
        val isCheck = LegalMoveGenerator.isKingInCheck(nextPos, nextPos.sideToMove)
        position = nextPos
        lastMove = moveAttempt
        playedMoves.add(moveAttempt)
        selectedSquare = null
        legalTargetSquares = emptySet()
        whisperLevel = 0
        whisperText = null
        whisperArrow = null

        if (soundEnabled) {
          soundEffects.playMove(isCapture = isCapture, isCheck = isCheck)
        }

        // Evaluate whether this move was a blunder & record to Room database
        coroutineScope.launch {
          val bestEngineMove = engine.selectMove(Position.fromFen(fenBefore), TrainingLevel.ADVANCED_1600)
          val newEval = engine.evaluatePosition(nextPos, depth = 3)
          val prevCp = prevEval.centipawns ?: 0
          val newCp = newEval.centipawns ?: 0
          val delta = prevCp - newCp

          // If evaluation dropped significantly (> 180 centipawns) and was not the best move
          if (delta > 180 && moveAttempt != bestEngineMove) {
            val deltaPawns = delta / 100f
            val explanation = "In this position, playing ${moveAttempt.uci} surrendered $deltaPawns pawns of evaluation. Best move was ${bestEngineMove.uci}."
            
            withContext(Dispatchers.IO) {
              val dao = ChessDatabaseProvider.getDatabase(context).chessDao()
              dao.insertMistake(
                MistakeRecord(
                  fenBefore = fenBefore,
                  playedMoveUci = moveAttempt.uci,
                  bestMoveUci = bestEngineMove.uci,
                  evalDeltaPawns = deltaPawns,
                  pedagogicalExplanation = explanation,
                  reviewDueTimestampMs = System.currentTimeMillis() + 86400000L, // Due in 1 day
                  repetitionStage = 0
                )
              )
            }
            recordedBlunderAlert = "Mistake logged to Spaced-Repetition Review Book (-${String.format("%.1f", deltaPawns)})"
            if (soundEnabled) {
              soundEffects.playBlunder()
            }
            if (voiceEnabled) {
              voiceCoach.speak("That surrendered positional evaluation. It has been filed to your mistake review book.")
            }
            delay(4500)
            recordedBlunderAlert = null
          }
        }
      } else if (piece != null && piece.color == playerColor) {
        selectedSquare = square
        val allLegal = LegalMoveGenerator.generateLegalMoves(position)
        legalTargetSquares = allLegal.filter { it.from == square }.map { it.to }.toSet()
      } else {
        selectedSquare = null
        legalTargetSquares = emptySet()
      }
    }
  }

  fun requestCoachWhisper() {
    if (position.sideToMove != playerColor) return
    if (soundEnabled) {
      soundEffects.playHint()
    }
    coroutineScope.launch {
      val bestMove = engine.selectMove(position, TrainingLevel.EXPERT_1800)
      val nextLevel = (whisperLevel + 1).coerceAtMost(4)
      whisperLevel = nextLevel

      when (nextLevel) {
        1 -> {
          whisperText = "Concept: Focus on piece harmony, active files, and protecting undefended pieces."
          whisperArrow = null
          if (voiceEnabled) voiceCoach.speak("Focus on piece harmony and protecting undefended pieces.")
        }
        2 -> {
          whisperText = "Zone: Watch square ${bestMove.to.algebraic} closely."
          whisperArrow = null
          if (voiceEnabled) voiceCoach.speak("Watch square ${bestMove.to.algebraic} closely.")
        }
        3 -> {
          whisperText = "Piece: Consider moving the piece on ${bestMove.from.algebraic}."
          whisperArrow = null
          if (voiceEnabled) voiceCoach.speak("Consider moving the piece on ${bestMove.from.algebraic}.")
        }
        4 -> {
          whisperText = "Direct Plan: Play ${bestMove.uci}."
          whisperArrow = Pair(bestMove.from, bestMove.to)
          if (voiceEnabled) voiceCoach.speak("Play ${bestMove.uci}.")
        }
      }
    }
  }

  val scrollState = rememberScrollState()

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(scrollState)
      .padding(horizontal = 16.dp)
      .padding(top = 12.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    // Opponent Header & Live Eval Indicator
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Box(
          modifier = Modifier
            .size(38.dp)
            .liquidGlassPill(shape = CircleShape, isActive = true),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.SportsEsports,
            contentDescription = null,
            tint = CoachAccentGold,
            modifier = Modifier.size(20.dp)
          )
        }
        Column {
          Text(
            text = "Sparring Bot (${currentLevel.title})",
            color = TextTitle,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = if (isEngineThinking) "Thinking..." else "Ready",
            color = if (isEngineThinking) CoachPrimary else TextMuted,
            fontSize = 11.sp
          )
        }
      }

      // Voice Toggle, Sound FX, Import, and Eval Pill Row
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        // Import FEN / PGN Dialog Launcher
        Box(
          modifier = Modifier
            .liquidGlassPill(shape = RoundedCornerShape(10.dp), isActive = false)
            .clickable { showImportDialog = true }
            .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Tune,
              contentDescription = "Import FEN or PGN",
              tint = CoachAccentGold,
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = "Import",
              color = CoachAccentGold,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }

        // Sound FX Toggle
        Box(
          modifier = Modifier
            .liquidGlassPill(shape = RoundedCornerShape(10.dp), isActive = soundEnabled)
            .clickable {
              soundEnabled = !soundEnabled
              soundEffects.isSoundEnabled = soundEnabled
            }
            .padding(horizontal = 7.dp, vertical = 6.dp)
        ) {
          Icon(
            imageVector = if (soundEnabled) Icons.Default.MusicNote else Icons.Default.MusicOff,
            contentDescription = "Sound Effects",
            tint = if (soundEnabled) CoachPrimary else TextMuted,
            modifier = Modifier.size(16.dp)
          )
        }

        // Voice Coach Toggle
        Box(
          modifier = Modifier
            .liquidGlassPill(shape = RoundedCornerShape(10.dp), isActive = voiceEnabled)
            .clickable {
              voiceEnabled = !voiceEnabled
              voiceCoach.isSpeechEnabled = voiceEnabled
              if (!voiceEnabled) voiceCoach.stop()
            }
            .padding(horizontal = 7.dp, vertical = 6.dp)
        ) {
          Icon(
            imageVector = if (voiceEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
            contentDescription = "Voice Coach",
            tint = if (voiceEnabled) CoachPrimary else TextMuted,
            modifier = Modifier.size(16.dp)
          )
        }

        // Live Centipawn Eval
        Box(
          modifier = Modifier
            .liquidGlassPill(shape = RoundedCornerShape(10.dp), isActive = true)
            .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
          Text(
            text = currentEval.format(),
            color = CoachAccentGold,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }

    // Dynamic Chessboard with Live Vertical Evaluation Gauge
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(320.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      LiveEvaluationBar(
        evaluation = currentEval,
        modifier = Modifier
          .width(10.dp)
          .fillMaxHeight()
      )

      InteractiveChessBoard(
        position = position,
        selectedSquare = selectedSquare,
        legalTargetSquares = legalTargetSquares,
        recommendedArrow = whisperArrow,
        lastMove = lastMove,
        onSquareTapped = { sq -> onSquareClicked(sq) },
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
      )
    }

    // Move History Ribbon
    if (playedMoves.isNotEmpty()) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .liquidGlassCard(shape = RoundedCornerShape(10.dp))
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        var num = 1
        for (i in playedMoves.indices step 2) {
          val w = playedMoves[i]
          val b = if (i + 1 < playedMoves.size) playedMoves[i + 1] else null
          Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(text = "$num.", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(
              text = w.uci,
              color = if (i == playedMoves.size - 1) CoachPrimary else TextTitle,
              fontSize = 12.sp,
              fontWeight = if (i == playedMoves.size - 1) FontWeight.Bold else FontWeight.Normal
            )
            if (b != null) {
              Text(
                text = b.uci,
                color = if (i + 1 == playedMoves.size - 1) CoachPrimary else TextTitle,
                fontSize = 12.sp,
                fontWeight = if (i + 1 == playedMoves.size - 1) FontWeight.Bold else FontWeight.Normal
              )
            }
          }
          num++
        }
      }
    }

    // Action Controls: Takeback, Flip Side, Review Game
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      OutlinedButton(
        onClick = { takebackMove() },
        enabled = playedMoves.isNotEmpty() && !isEngineThinking,
        modifier = Modifier
          .weight(1f)
          .height(38.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = CoachPrimary)
      ) {
        Icon(imageVector = Icons.Default.Undo, contentDescription = "Takeback", modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Takeback", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
      }

      OutlinedButton(
        onClick = {
          playerColor = playerColor.opposite()
          if (soundEnabled) soundEffects.playHint()
        },
        enabled = !isEngineThinking,
        modifier = Modifier
          .weight(1f)
          .height(38.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = CoachAccentGold)
      ) {
        Icon(imageVector = Icons.Default.SwapVert, contentDescription = "Flip", modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(if (playerColor == PieceColor.WHITE) "Side: White" else "Side: Black", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
      }

      Button(
        onClick = {
          onGameFinished(position, playedMoves.toList())
        },
        enabled = playedMoves.isNotEmpty(),
        modifier = Modifier
          .weight(1.2f)
          .height(38.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = CoachPrimary, contentColor = Color(0xFF0F1115))
      ) {
        Icon(imageVector = Icons.Default.AutoGraph, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Review Game", fontSize = 11.sp, fontWeight = FontWeight.Bold)
      }
    }

    // Live Blunder Notification Banner
    if (recordedBlunderAlert != null) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .liquidGlassCard(
            shape = RoundedCornerShape(12.dp),
            backgroundColor = Color(0x33EF4444),
            borderBrush = androidx.compose.ui.graphics.SolidColor(StatusBlunder)
          )
          .padding(12.dp)
      ) {
        Text(
          text = recordedBlunderAlert!!,
          color = StatusBlunder,
          fontSize = 12.5.sp,
          fontWeight = FontWeight.Bold
        )
      }
    }

    // Game End Banner if checkmate/stalemate
    if (gameStatus != GameStatus.IN_PROGRESS) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .liquidGlassCard(
            shape = RoundedCornerShape(16.dp),
            borderBrush = LiquidGlassBorderGold
          )
          .padding(16.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = when (gameStatus) {
                GameStatus.CHECKMATE -> "Checkmate! Match Complete"
                GameStatus.STALEMATE -> "Stalemate — Draw"
                else -> "Game Ended"
              },
              color = CoachAccentGold,
              fontSize = 14.5.sp,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "Ready for Coach Debrief & Mistake Analysis",
              color = TextBody,
              fontSize = 12.sp
            )
          }

          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            OutlinedButton(
              onClick = {
                onGameFinished(position, playedMoves.toList())
              },
              colors = ButtonDefaults.outlinedButtonColors(contentColor = CoachAccentGold),
              border = ButtonDefaults.outlinedButtonBorder.copy(brush = LiquidGlassBorderGold),
              shape = RoundedCornerShape(10.dp)
            ) {
              Text("Review Game", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Button(
              onClick = {
                position = Position.initial()
                lastMove = null
                whisperLevel = 0
                whisperText = null
                playedMoves.clear()
                positionHistory.clear()
                positionHistory.add(Position.initial())
              },
              colors = ButtonDefaults.buttonColors(containerColor = CoachPrimary, contentColor = Color(0xFF0F1115)),
              shape = RoundedCornerShape(10.dp)
            ) {
              Text("Play Again", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }

    // Coach Whisper Hint Deck
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .liquidGlassCard(
          shape = RoundedCornerShape(18.dp),
          borderBrush = LiquidGlassBorderGold
        )
        .padding(16.dp)
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Lightbulb,
              contentDescription = null,
              tint = CoachPrimary,
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = "COACH WHISPER",
              color = CoachPrimary,
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              letterSpacing = 1.sp
            )
          }

          if (whisperLevel > 0) {
            Box(
              modifier = Modifier
                .liquidGlassPill(shape = RoundedCornerShape(8.dp), isActive = true)
                .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
              Text(
                text = "Hint Level $whisperLevel/4",
                color = CoachAccentGold,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }

        Text(
          text = whisperText ?: "Stuck? Request a progressive hint. The coach will point you to the right strategic idea without spoiling the move.",
          color = if (whisperText != null) TextTitle else TextBody,
          fontSize = 12.5.sp,
          lineHeight = 17.sp
        )

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedButton(
            onClick = { requestCoachWhisper() },
            modifier = Modifier
              .weight(1f)
              .height(44.dp)
              .testTag("coach_whisper_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = CoachPrimary),
            border = ButtonDefaults.outlinedButtonBorder.copy(brush = LiquidGlassBorderGold)
          ) {
            Text(
              text = if (whisperLevel == 0) "Ask Coach Whisper" else "Deeper Hint (${whisperLevel + 1}/4)",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold
            )
          }

          Button(
            onClick = {
              position = Position.initial()
              lastMove = null
              whisperLevel = 0
              whisperText = null
            },
            modifier = Modifier
              .height(44.dp)
              .liquidGlassCard(shape = RoundedCornerShape(12.dp), elevation = 2.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = TextTitle)
          ) {
            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Restart", modifier = Modifier.size(16.dp))
          }
        }
      }
    }
  }

  // Import Dialog (FEN & PGN)
  if (showImportDialog) {
    FenPgnImportDialog(
      initialMode = ImportMode.FEN,
      onDismiss = { showImportDialog = false },
      onPlayFenInArena = { fen, title ->
        position = Position.fromFen(fen)
        playerColor = position.sideToMove
        lastMove = null
        selectedSquare = null
        legalTargetSquares = emptySet()
        whisperLevel = 0
        whisperText = null
        whisperArrow = null
        arenaGameTitle = title
        if (voiceEnabled) {
          voiceCoach.speak("Position loaded: $title. ${if (position.sideToMove == PieceColor.WHITE) "White" else "Black"} to move.")
        }
        if (soundEnabled) {
          soundEffects.playHint()
        }
      }
    )
  }
}

/**
 * Vertical Evaluation Gauge showing real-time White vs Black advantage.
 * Smoothly interpolates using a sigmoid conversion from centipawns.
 */
@Composable
fun LiveEvaluationBar(
  evaluation: Evaluation,
  modifier: Modifier = Modifier
) {
  val animatedCp by animateFloatAsState(
    targetValue = (evaluation.centipawns ?: 0).toFloat(),
    animationSpec = spring(stiffness = Spring.StiffnessLow),
    label = "eval_gauge"
  )

  val whiteFraction = remember(animatedCp, evaluation.mateInMoves) {
    if (evaluation.mateInMoves != null) {
      if (evaluation.mateInMoves > 0) 0.96f else 0.04f
    } else {
      val sigmoid = 1.0f / (1.0f + Math.pow(10.0, -animatedCp.toDouble() / 400.0).toFloat())
      sigmoid.coerceIn(0.04f, 0.96f)
    }
  }

  Column(
    modifier = modifier
      .clip(RoundedCornerShape(6.dp))
      .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(6.dp))
      .background(Color(0xFF1F242E)),
    verticalArrangement = Arrangement.SpaceBetween
  ) {
    // Black advantage portion at the top
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .weight((1f - whiteFraction).coerceAtLeast(0.02f))
        .background(Color(0xFF1E222A))
    )
    // White advantage portion at the bottom
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .weight(whiteFraction.coerceAtLeast(0.02f))
        .background(
          androidx.compose.ui.graphics.Brush.verticalGradient(
            colors = listOf(Color(0xFFFFFFFF), Color(0xFFD6DEE7))
          )
        )
    )
  }
}
