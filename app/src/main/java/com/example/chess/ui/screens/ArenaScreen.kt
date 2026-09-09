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
import androidx.compose.foundation.layout.aspectRatio
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
import com.example.chess.ui.components.BoardThemeDialog
import com.example.chess.ui.components.ChessBoardTheme
import com.example.chess.ui.components.ExportGameDialog
import com.example.chess.ui.components.GameAccuracyReportDialog
import com.example.chess.ui.components.MoveEvaluationPoint
import com.example.chess.ui.components.TimeControl
import com.example.chess.ui.components.TimeControlDialog
import com.example.chess.ui.components.formatClockTime
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.chess.audio.rememberChessSoundEffects
import com.example.chess.audio.rememberVoiceCoach
import com.example.chess.core.GameStatus
import com.example.chess.core.LegalMoveGenerator
import com.example.chess.core.Move
import com.example.chess.core.PieceColor
import com.example.chess.core.PieceType
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

  // Board Theme
  var currentBoardTheme by remember { mutableStateOf(ChessBoardTheme.CLASSIC_TOURNAMENT) }
  var showThemeDialog by remember { mutableStateOf(false) }

  // Game Clocks & Time Controls
  var selectedTimeControl by remember { mutableStateOf(TimeControl.UNLIMITED) }
  var showTimeControlDialog by remember { mutableStateOf(false) }
  var whiteTimeMillis by remember { mutableStateOf(0L) }
  var blackTimeMillis by remember { mutableStateOf(0L) }
  var isClockRunning by remember { mutableStateOf(false) }
  var timeoutWinner by remember { mutableStateOf<PieceColor?>(null) }

  // Accuracy Report & Export
  var showExportDialog by remember { mutableStateOf(false) }
  var showAccuracyReportDialog by remember { mutableStateOf(false) }
  val evalPointsHistory = remember { mutableStateListOf<MoveEvaluationPoint>() }

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
  var pendingPromotionMoves by remember { mutableStateOf<List<Move>?>(null) }

  // Game End State
  val gameStatus = remember(position) { LegalMoveGenerator.getGameStatus(position) }

  // Reset clocks when TimeControl changes
  LaunchedEffect(selectedTimeControl) {
    whiteTimeMillis = selectedTimeControl.baseSeconds * 1000L
    blackTimeMillis = selectedTimeControl.baseSeconds * 1000L
    isClockRunning = false
    timeoutWinner = null
  }

  // Real-time Clock Countdown
  LaunchedEffect(isClockRunning, position.sideToMove, gameStatus, timeoutWinner, selectedTimeControl) {
    if (isClockRunning && gameStatus == GameStatus.IN_PROGRESS && timeoutWinner == null && selectedTimeControl != TimeControl.UNLIMITED) {
      while (isClockRunning && gameStatus == GameStatus.IN_PROGRESS && timeoutWinner == null) {
        delay(100)
        if (position.sideToMove == PieceColor.WHITE) {
          whiteTimeMillis = (whiteTimeMillis - 100).coerceAtLeast(0)
          if (whiteTimeMillis <= 0) {
            timeoutWinner = PieceColor.BLACK
            isClockRunning = false
            if (soundEnabled) soundEffects.playDefeat()
            if (voiceEnabled) voiceCoach.speak("White flagged! Black wins on time.")
            break
          }
        } else {
          blackTimeMillis = (blackTimeMillis - 100).coerceAtLeast(0)
          if (blackTimeMillis <= 0) {
            timeoutWinner = PieceColor.WHITE
            isClockRunning = false
            if (soundEnabled) soundEffects.playVictory()
            if (voiceEnabled) voiceCoach.speak("Black flagged! White wins on time.")
            break
          }
        }
      }
    }
  }

  fun restartGame(newFen: String = Position.STARTING_FEN) {
    position = Position.fromFen(newFen)
    playedMoves.clear()
    positionHistory.clear()
    evalPointsHistory.clear()
    selectedSquare = null
    legalTargetSquares = emptySet()
    lastMove = null
    whisperArrow = null
    whisperText = null
    whisperLevel = 0
    recordedBlunderAlert = null
    pendingPromotionMoves = null
    isEngineThinking = false
    timeoutWinner = null
    isClockRunning = false
    whiteTimeMillis = selectedTimeControl.baseSeconds * 1000L
    blackTimeMillis = selectedTimeControl.baseSeconds * 1000L
    if (soundEnabled) soundEffects.playHint()
  }

  fun generatePgnString(): String {
    val sb = StringBuilder()
    val date = SimpleDateFormat("yyyy.MM.dd", Locale.US).format(Date())
    sb.append("[Event \"Chess Tutor Sparring Arena\"]\n")
    sb.append("[Site \"Chess Tutor\"]\n")
    sb.append("[Date \"$date\"]\n")
    sb.append("[White \"${if (playerColor == PieceColor.WHITE) "Player" else currentLevel.title}\"]\n")
    sb.append("[Black \"${if (playerColor == PieceColor.BLACK) "Player" else currentLevel.title}\"]\n")
    val resultStr = when {
      timeoutWinner == PieceColor.WHITE -> "1-0"
      timeoutWinner == PieceColor.BLACK -> "0-1"
      gameStatus == GameStatus.CHECKMATE -> if (position.sideToMove == PieceColor.BLACK) "1-0" else "0-1"
      gameStatus == GameStatus.STALEMATE || gameStatus == GameStatus.DRAW_INSUFFICIENT_MATERIAL -> "1/2-1/2"
      else -> "*"
    }
    sb.append("[Result \"$resultStr\"]\n")
    if (selectedTimeControl != TimeControl.UNLIMITED) {
      sb.append("[TimeControl \"${selectedTimeControl.baseSeconds}+${selectedTimeControl.incrementSeconds}\"]\n")
    }
    sb.append("\n")

    for (i in playedMoves.indices) {
      if (i % 2 == 0) {
        sb.append("${(i / 2) + 1}. ")
      }
      sb.append("${playedMoves[i].san} ")
    }
    sb.append(resultStr)
    return sb.toString()
  }

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
      pendingPromotionMoves = null
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
      pendingPromotionMoves = null
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

  // Handle Bot Turn (Auto-plays if it's the bot's turn to move)
  LaunchedEffect(position, playerColor, gameStatus) {
    if (position.sideToMove != playerColor && gameStatus == GameStatus.IN_PROGRESS && !isEngineThinking) {
      isEngineThinking = true
      delay(300) // Brief natural human-like pause
      val botMove = withContext(Dispatchers.Default) {
        engine.selectMove(position, currentLevel)
      }
      val destOccupant = position.pieceAt(botMove.to)
      val isCapture = destOccupant != null || botMove.isEnPassant
      positionHistory.add(position)
      val nextPos = LegalMoveGenerator.makeMove(position, botMove)
      val isCheck = LegalMoveGenerator.isKingInCheck(nextPos, nextPos.sideToMove)
      position = nextPos
      lastMove = botMove
      playedMoves.add(botMove)
      isEngineThinking = false

      if (selectedTimeControl != TimeControl.UNLIMITED) {
        if (!isClockRunning) isClockRunning = true
        if (playerColor == PieceColor.WHITE) {
          blackTimeMillis += selectedTimeControl.incrementSeconds * 1000L
        } else {
          whiteTimeMillis += selectedTimeControl.incrementSeconds * 1000L
        }
      }

      coroutineScope.launch {
        val botNewEval = engine.evaluatePosition(nextPos, depth = 3)
        val botEvalPawns = (botNewEval.centipawns ?: 0) / 100f
        val botSwing = ((currentEval.centipawns ?: 0) - (botNewEval.centipawns ?: 0)) / 100f
        evalPointsHistory.add(
          MoveEvaluationPoint(
            moveIndex = playedMoves.size - 1,
            san = botMove.san,
            moveNumber = (playedMoves.size + 1) / 2,
            color = playerColor.opposite(),
            centipawns = botNewEval.centipawns ?: 0,
            evalPawns = botEvalPawns,
            swingDeltaPawns = botSwing
          )
        )
      }

      if (soundEnabled) {
        soundEffects.playMove(isCapture = isCapture, isCheck = isCheck)
      }
    }
  }

  fun executePlayerMove(moveAttempt: Move) {
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
    pendingPromotionMoves = null

    if (selectedTimeControl != TimeControl.UNLIMITED) {
      if (!isClockRunning) isClockRunning = true
      if (playerColor == PieceColor.WHITE) {
        whiteTimeMillis += selectedTimeControl.incrementSeconds * 1000L
      } else {
        blackTimeMillis += selectedTimeControl.incrementSeconds * 1000L
      }
    }

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

      val newEvalPawns = (newEval.centipawns ?: 0) / 100f
      val playerSwing = delta / 100f
      evalPointsHistory.add(
        MoveEvaluationPoint(
          moveIndex = playedMoves.size - 1,
          san = moveAttempt.san,
          moveNumber = (playedMoves.size + 1) / 2,
          color = playerColor,
          centipawns = newEval.centipawns ?: 0,
          evalPawns = newEvalPawns,
          swingDeltaPawns = playerSwing
        )
      )

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
      if (piece != null && piece.color == playerColor) {
        if (square == src) {
          selectedSquare = null
          legalTargetSquares = emptySet()
        } else {
          selectedSquare = square
          val allLegal = LegalMoveGenerator.generateLegalMoves(position)
          legalTargetSquares = allLegal.filter { it.from == square }.map { it.to }.toSet()
        }
        return
      }

      val allLegal = LegalMoveGenerator.generateLegalMoves(position)
      val movesForTarget = allLegal.filter { it.from == src && it.to == square }

      if (movesForTarget.size > 1 && movesForTarget.any { it.promotion != null }) {
        pendingPromotionMoves = movesForTarget
      } else if (movesForTarget.isNotEmpty()) {
        executePlayerMove(movesForTarget.first())
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
    // Opponent Header & Actions Row
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
            text = if (isEngineThinking) "Thinking..." else if (position.sideToMove != playerColor) "Bot to move" else "Waiting...",
            color = if (isEngineThinking) CoachPrimary else TextMuted,
            fontSize = 11.sp
          )
        }
      }

      // Action buttons: Theme, Clock/Timer, Export, Import, Sound, Voice
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
      ) {
        // Bot Clock if clock enabled
        if (selectedTimeControl != TimeControl.UNLIMITED) {
          val botTime = if (playerColor == PieceColor.WHITE) blackTimeMillis else whiteTimeMillis
          val isBotTurn = position.sideToMove != playerColor && gameStatus == GameStatus.IN_PROGRESS && timeoutWinner == null
          val isLowTime = botTime < 30_000L
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(if (isBotTurn) CoachPrimary.copy(alpha = 0.15f) else LiquidGlassSurface)
              .border(
                1.dp,
                if (isLowTime) StatusBlunder else if (isBotTurn) CoachPrimary else LiquidGlassSurfaceSubtle,
                RoundedCornerShape(8.dp)
              )
              .padding(horizontal = 8.dp, vertical = 4.dp)
          ) {
            Text(
              text = formatClockTime(botTime),
              color = if (isLowTime) StatusBlunder else if (isBotTurn) CoachPrimary else TextTitle,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }

        // Time Control Selector
        Box(
          modifier = Modifier
            .liquidGlassPill(shape = RoundedCornerShape(9.dp), isActive = selectedTimeControl != TimeControl.UNLIMITED)
            .clickable { showTimeControlDialog = true }
            .padding(horizontal = 7.dp, vertical = 5.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Timer,
              contentDescription = "Time Controls",
              tint = if (selectedTimeControl != TimeControl.UNLIMITED) CoachPrimary else TextMuted,
              modifier = Modifier.size(15.dp)
            )
            Text(
              text = selectedTimeControl.formatSeconds(),
              color = if (selectedTimeControl != TimeControl.UNLIMITED) CoachPrimary else TextMuted,
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }

        // Board Theme Selector
        Box(
          modifier = Modifier
            .liquidGlassPill(shape = RoundedCornerShape(9.dp), isActive = false)
            .clickable { showThemeDialog = true }
            .padding(horizontal = 6.dp, vertical = 5.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Palette,
            contentDescription = "Board Theme",
            tint = CoachAccentGold,
            modifier = Modifier.size(15.dp)
          )
        }

        // Export Game PGN/FEN
        Box(
          modifier = Modifier
            .liquidGlassPill(shape = RoundedCornerShape(9.dp), isActive = false)
            .clickable { showExportDialog = true }
            .padding(horizontal = 6.dp, vertical = 5.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Share,
            contentDescription = "Export PGN",
            tint = CoachAccentGold,
            modifier = Modifier.size(15.dp)
          )
        }

        // Import FEN / PGN Dialog Launcher
        Box(
          modifier = Modifier
            .liquidGlassPill(shape = RoundedCornerShape(9.dp), isActive = false)
            .clickable { showImportDialog = true }
            .padding(horizontal = 6.dp, vertical = 5.dp)
        ) {
          Icon(
            imageVector = Icons.Default.FileUpload,
            contentDescription = "Import FEN or PGN",
            tint = TextMuted,
            modifier = Modifier.size(15.dp)
          )
        }

        // Sound FX Toggle
        Box(
          modifier = Modifier
            .liquidGlassPill(shape = RoundedCornerShape(9.dp), isActive = soundEnabled)
            .clickable {
              soundEnabled = !soundEnabled
              soundEffects.isSoundEnabled = soundEnabled
            }
            .padding(horizontal = 6.dp, vertical = 5.dp)
        ) {
          Icon(
            imageVector = if (soundEnabled) Icons.Default.MusicNote else Icons.Default.MusicOff,
            contentDescription = "Sound Effects",
            tint = if (soundEnabled) CoachPrimary else TextMuted,
            modifier = Modifier.size(15.dp)
          )
        }

        // Voice Coach Toggle
        Box(
          modifier = Modifier
            .liquidGlassPill(shape = RoundedCornerShape(9.dp), isActive = voiceEnabled)
            .clickable {
              voiceEnabled = !voiceEnabled
              voiceCoach.isSpeechEnabled = voiceEnabled
              if (!voiceEnabled) voiceCoach.stop()
            }
            .padding(horizontal = 6.dp, vertical = 5.dp)
        ) {
          Icon(
            imageVector = if (voiceEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
            contentDescription = "Voice Coach",
            tint = if (voiceEnabled) CoachPrimary else TextMuted,
            modifier = Modifier.size(15.dp)
          )
        }
      }
    }

    // Dynamic Chessboard with Live Vertical Evaluation Gauge
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(1.04f),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      LiveEvaluationBar(
        evaluation = currentEval,
        flipped = (playerColor == PieceColor.BLACK),
        modifier = Modifier
          .width(26.dp)
          .fillMaxHeight()
      )

      InteractiveChessBoard(
        position = position,
        flipped = (playerColor == PieceColor.BLACK),
        boardTheme = currentBoardTheme,
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

    // Player Status Row & Player Clock
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
            .size(14.dp)
            .clip(CircleShape)
            .background(if (playerColor == PieceColor.WHITE) Color.White else Color(0xFF1E293B))
            .border(1.dp, CoachAccentGold, CircleShape)
        )
        Text(
          text = "You (${if (playerColor == PieceColor.WHITE) "White" else "Black"})",
          color = TextTitle,
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold
        )
        if (position.sideToMove == playerColor && gameStatus == GameStatus.IN_PROGRESS && timeoutWinner == null) {
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .background(CoachPrimary.copy(alpha = 0.2f))
              .padding(horizontal = 6.dp, vertical = 2.dp)
          ) {
            Text(text = "Your Turn", color = CoachPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
          }
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        // Live Centipawn Eval Badge
        Box(
          modifier = Modifier
            .liquidGlassPill(shape = RoundedCornerShape(8.dp), isActive = true)
            .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
          Text(
            text = currentEval.format(),
            color = CoachAccentGold,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
          )
        }

        // Player Clock if clock enabled
        if (selectedTimeControl != TimeControl.UNLIMITED) {
          val playerTime = if (playerColor == PieceColor.WHITE) whiteTimeMillis else blackTimeMillis
          val isPlayerTurn = position.sideToMove == playerColor && gameStatus == GameStatus.IN_PROGRESS && timeoutWinner == null
          val isLowTime = playerTime < 30_000L
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(if (isPlayerTurn) CoachPrimary.copy(alpha = 0.2f) else LiquidGlassSurface)
              .border(
                1.dp,
                if (isLowTime) StatusBlunder else if (isPlayerTurn) CoachPrimary else LiquidGlassSurfaceSubtle,
                RoundedCornerShape(8.dp)
              )
              .padding(horizontal = 8.dp, vertical = 4.dp)
          ) {
            Text(
              text = formatClockTime(playerTime),
              color = if (isLowTime) StatusBlunder else if (isPlayerTurn) CoachPrimary else TextTitle,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }
    }

    // Pawn Promotion Modal Dialog
    if (pendingPromotionMoves != null) {
      androidx.compose.ui.window.Dialog(
        onDismissRequest = { pendingPromotionMoves = null }
      ) {
        Box(
          modifier = Modifier
            .liquidGlassCard(
              shape = RoundedCornerShape(16.dp),
              borderBrush = LiquidGlassBorderCyan
            )
            .padding(20.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Text(
              text = "Promote Pawn",
              color = CoachPrimary,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "Choose a promotion piece",
              color = TextBody,
              fontSize = 12.sp
            )
            Row(
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              val moves = pendingPromotionMoves!!
              val queenMove = moves.find { it.promotion == PieceType.QUEEN }
              val knightMove = moves.find { it.promotion == PieceType.KNIGHT }
              val rookMove = moves.find { it.promotion == PieceType.ROOK }
              val bishopMove = moves.find { it.promotion == PieceType.BISHOP }

              val promoOptions = listOfNotNull(
                queenMove?.let { "♛" to it },
                knightMove?.let { "♞" to it },
                rookMove?.let { "♜" to it },
                bishopMove?.let { "♝" to it }
              )

              promoOptions.forEach { (symbol, move) ->
                Box(
                  modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E2430))
                    .border(1.5.dp, CoachPrimary, RoundedCornerShape(12.dp))
                    .clickable { executePlayerMove(move) },
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = symbol,
                    fontSize = 28.sp,
                    color = Color.White
                  )
                }
              }
            }
          }
        }
      }
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

    // Action Controls: Takeback, Flip Side, Reset, Accuracy Report, Review Game
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
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
        Text("Undo", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
      }

      OutlinedButton(
        onClick = {
          playerColor = playerColor.opposite()
          if (soundEnabled) soundEffects.playHint()
        },
        enabled = !isEngineThinking,
        modifier = Modifier
          .weight(1.05f)
          .height(38.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = CoachAccentGold)
      ) {
        Icon(imageVector = Icons.Default.SwapVert, contentDescription = "Flip", modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(if (playerColor == PieceColor.WHITE) "White" else "Black", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
      }

      OutlinedButton(
        onClick = { restartGame() },
        enabled = !isEngineThinking && playedMoves.isNotEmpty(),
        modifier = Modifier
          .weight(1f)
          .height(38.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted)
      ) {
        Icon(imageVector = Icons.Default.Refresh, contentDescription = "New Game", modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Reset", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
      }

      OutlinedButton(
        onClick = { showAccuracyReportDialog = true },
        enabled = playedMoves.isNotEmpty(),
        modifier = Modifier
          .weight(1.15f)
          .height(38.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = CoachAccentGold)
      ) {
        Icon(imageVector = Icons.Default.AutoGraph, contentDescription = "Report", modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Report", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
      }

      Button(
        onClick = {
          onGameFinished(position, playedMoves.toList())
        },
        enabled = playedMoves.isNotEmpty(),
        modifier = Modifier
          .weight(1.25f)
          .height(38.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = CoachPrimary, contentColor = Color(0xFF0F1115))
      ) {
        Text("Review", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

    // Game End Banner if checkmate/stalemate/timeout
    val isGameEnded = gameStatus != GameStatus.IN_PROGRESS || timeoutWinner != null
    if (isGameEnded) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .liquidGlassCard(
            shape = RoundedCornerShape(16.dp),
            borderBrush = LiquidGlassBorderGold
          )
          .padding(16.dp)
      ) {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = when {
                  timeoutWinner == PieceColor.WHITE -> "White Wins on Time!"
                  timeoutWinner == PieceColor.BLACK -> "Black Wins on Time!"
                  gameStatus == GameStatus.CHECKMATE -> "Checkmate! ${if (position.sideToMove == PieceColor.BLACK) "White" else "Black"} Wins"
                  gameStatus == GameStatus.STALEMATE -> "Stalemate — Draw"
                  else -> "Game Ended"
                },
                color = CoachAccentGold,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "Match complete. View accuracy or launch deep review.",
                color = TextBody,
                fontSize = 12.sp
              )
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            OutlinedButton(
              onClick = { showAccuracyReportDialog = true },
              colors = ButtonDefaults.outlinedButtonColors(contentColor = CoachAccentGold),
              border = ButtonDefaults.outlinedButtonBorder.copy(brush = LiquidGlassBorderGold),
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.weight(1f)
            ) {
              Text("Accuracy Graph", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
              onClick = { onGameFinished(position, playedMoves.toList()) },
              colors = ButtonDefaults.outlinedButtonColors(contentColor = CoachPrimary),
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.weight(1f)
            ) {
              Text("Review Game", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }

            Button(
              onClick = { restartGame() },
              colors = ButtonDefaults.buttonColors(containerColor = CoachPrimary, contentColor = Color(0xFF0F1115)),
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.weight(1f)
            ) {
              Text("Play Again", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
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

  // Time Control Dialog
  if (showTimeControlDialog) {
    TimeControlDialog(
      current = selectedTimeControl,
      onSelect = { tc ->
        selectedTimeControl = tc
        restartGame()
      },
      onDismiss = { showTimeControlDialog = false }
    )
  }

  // Board Theme Dialog
  if (showThemeDialog) {
    BoardThemeDialog(
      currentTheme = currentBoardTheme,
      onSelectTheme = { theme -> currentBoardTheme = theme },
      onDismiss = { showThemeDialog = false }
    )
  }

  // Export Game Dialog (PGN / FEN)
  if (showExportDialog) {
    ExportGameDialog(
      pgnText = generatePgnString(),
      fenText = position.toFen(),
      onDismiss = { showExportDialog = false }
    )
  }

  // Post-Match Accuracy & Advantage Graph Dialog
  if (showAccuracyReportDialog) {
    val resultSummary = when {
      timeoutWinner == PieceColor.WHITE -> "White Wins on Time!"
      timeoutWinner == PieceColor.BLACK -> "Black Wins on Time!"
      gameStatus == GameStatus.CHECKMATE -> if (position.sideToMove == PieceColor.BLACK) "White Wins by Checkmate!" else "Black Wins by Checkmate!"
      gameStatus == GameStatus.STALEMATE -> "Draw by Stalemate"
      else -> "Game in Progress (${playedMoves.size} moves)"
    }
    GameAccuracyReportDialog(
      gameTitle = arenaGameTitle ?: "Sparring vs ${currentLevel.title}",
      gameResultSummary = resultSummary,
      evalPoints = evalPointsHistory.toList(),
      playerColor = playerColor,
      botName = "Sparring Bot (${currentLevel.title})",
      onRematch = { restartGame() },
      onDismiss = { showAccuracyReportDialog = false }
    )
  }
}

/**
 * Vertical Evaluation Gauge showing real-time White vs Black advantage.
 * Smoothly interpolates using a sigmoid conversion from centipawns.
 * Supports flipped board perspective and displays score text directly on the gauge.
 */
@Composable
fun LiveEvaluationBar(
  evaluation: Evaluation,
  modifier: Modifier = Modifier,
  flipped: Boolean = false
) {
  val animatedCp by animateFloatAsState(
    targetValue = (evaluation.centipawns ?: 0).toFloat(),
    animationSpec = spring(stiffness = Spring.StiffnessLow),
    label = "eval_gauge"
  )

  val rawWhiteFraction = remember(animatedCp, evaluation.mateInMoves) {
    if (evaluation.mateInMoves != null) {
      if (evaluation.mateInMoves > 0) 0.96f else 0.04f
    } else {
      val sigmoid = 1.0f / (1.0f + Math.pow(10.0, -animatedCp.toDouble() / 400.0).toFloat())
      sigmoid.coerceIn(0.04f, 0.96f)
    }
  }

  // If flipped (playing Black), the bottom represents Black and top represents White
  val bottomFraction = if (flipped) 1.0f - rawWhiteFraction else rawWhiteFraction
  val topFraction = 1.0f - bottomFraction

  val topColor = if (flipped) Color(0xFFE8EDF2) else Color(0xFF1E222A)
  val bottomColor = if (flipped) Color(0xFF1E222A) else Color(0xFFE8EDF2)

  Box(
    modifier = modifier
      .clip(RoundedCornerShape(8.dp))
      .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
      .background(Color(0xFF181C24)),
    contentAlignment = Alignment.Center
  ) {
    Column(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(topFraction.coerceAtLeast(0.02f))
          .background(topColor)
      )
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(bottomFraction.coerceAtLeast(0.02f))
          .background(bottomColor)
      )
    }

    // Centipawn text displayed vertically or concisely on the gauge
    val displayScore = evaluation.format()
    val isWhiteAdvantage = (evaluation.centipawns ?: 0) >= 0
    val textBgColor = if (isWhiteAdvantage) Color(0xCCFFFFFF) else Color(0xCC111827)
    val textFgColor = if (isWhiteAdvantage) Color(0xFF111827) else Color(0xFFF3F4F6)

    Box(
      modifier = Modifier
        .clip(RoundedCornerShape(4.dp))
        .background(textBgColor)
        .padding(horizontal = 3.dp, vertical = 2.dp),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = displayScore,
        fontSize = 8.5.sp,
        fontWeight = FontWeight.Bold,
        color = textFgColor,
        maxLines = 1
      )
    }
  }
}
