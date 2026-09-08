package com.example.chess.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.chess.audio.rememberChessSoundEffects
import com.example.chess.audio.rememberVoiceCoach
import com.example.chess.core.LegalMoveGenerator
import com.example.chess.core.Move
import com.example.chess.core.PieceColor
import com.example.chess.core.Position
import com.example.chess.core.Square
import com.example.chess.tactics.TacticalPuzzle
import com.example.chess.tactics.TacticsRepository
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
import com.example.chess.ui.theme.StatusExcellent
import com.example.chess.ui.theme.StatusMistake
import com.example.chess.ui.theme.TextBody
import com.example.chess.ui.theme.TextMuted
import com.example.chess.ui.theme.TextTitle

/**
 * Tactics Dojo Screen featuring calibrated Elo tactical progression,
 * 4-step progressive pedagogical hint ladder, and integrated Text-to-Speech Voice Coach commentary.
 */
@Composable
fun TacticsDojoScreen(
  userTacticsRating: Int,
  puzzlesSolvedCount: Int,
  onPuzzleSolved: (newRating: Int, solvedCount: Int) -> Unit,
  onPracticeInArena: (fen: String, title: String) -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier
) {
  val voiceCoach = rememberVoiceCoach()
  val soundEffects = rememberChessSoundEffects()
  var voiceEnabled by remember { mutableStateOf(true) }
  var soundEnabled by remember { mutableStateOf(true) }
  var showImportDialog by remember { mutableStateOf(false) }

  var puzzles by remember { mutableStateOf(TacticsRepository.getAllPuzzles()) }
  var currentPuzzleIndex by remember { mutableStateOf(0) }
  val puzzle = puzzles.getOrElse(currentPuzzleIndex) { puzzles.first() }

  var currentPosition by remember(puzzle) { mutableStateOf(Position.fromFen(puzzle.fen)) }
  var selectedSquare by remember(puzzle) { mutableStateOf<Square?>(null) }
  var legalTargetSquares by remember(puzzle) { mutableStateOf<Set<Square>>(emptySet()) }
  var lastMove by remember(puzzle) { mutableStateOf<Move?>(null) }

  var hintLevel by remember(puzzle) { mutableStateOf(0) }
  var isSolved by remember(puzzle) { mutableStateOf(false) }
  var isFailedAttempt by remember(puzzle) { mutableStateOf(false) }
  var statusMessage by remember(puzzle) { mutableStateOf("Find the winning tactical move") }

  // Dynamic user Elo
  var dynamicRating by remember { mutableStateOf(userTacticsRating) }
  var dynamicSolvedCount by remember { mutableStateOf(puzzlesSolvedCount) }

  // Trigger audio coach greeting on puzzle load
  LaunchedEffect(puzzle, voiceEnabled) {
    if (voiceEnabled) {
      voiceCoach.speak("${puzzle.title}. ${puzzle.theme.label}. ${if (puzzle.sideToPlay == PieceColor.WHITE) "White" else "Black"} to move.")
    }
  }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 20.dp)
      .padding(top = 16.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // Header Bar
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          IconButton(
            onClick = onClose,
            modifier = Modifier
              .size(38.dp)
              .liquidGlassPill(shape = RoundedCornerShape(10.dp), isActive = false)
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
              tint = TextTitle,
              modifier = Modifier.size(20.dp)
            )
          }

          Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Icon(imageVector = Icons.Default.Bolt, contentDescription = null, tint = CoachAccentGold, modifier = Modifier.size(14.dp))
              Text(
                text = "TACTICAL PUZZLE DOJO",
                color = CoachAccentGold,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp
              )
            }
            Text(
              text = "Rating $dynamicRating • ${puzzle.theme.label}",
              color = TextTitle,
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }

        // Action Pills: Import, Sound FX, Voice Coach
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          // Import FEN / PGN or Create Puzzle
          Box(
            modifier = Modifier
              .liquidGlassPill(shape = RoundedCornerShape(12.dp), isActive = false)
              .clickable { showImportDialog = true }
              .padding(horizontal = 8.dp, vertical = 7.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = "Import or Create Puzzle",
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

          // Sound FX Toggle Pill
          Box(
            modifier = Modifier
              .liquidGlassPill(shape = RoundedCornerShape(12.dp), isActive = soundEnabled)
              .clickable {
                soundEnabled = !soundEnabled
                soundEffects.isSoundEnabled = soundEnabled
              }
              .padding(horizontal = 8.dp, vertical = 7.dp)
          ) {
            Icon(
              imageVector = if (soundEnabled) Icons.Default.MusicNote else Icons.Default.MusicOff,
              contentDescription = "Sound Effects",
              tint = if (soundEnabled) CoachPrimary else TextMuted,
              modifier = Modifier.size(16.dp)
            )
          }

          // Voice Coach Toggle Pill
          Box(
            modifier = Modifier
              .liquidGlassPill(shape = RoundedCornerShape(12.dp), isActive = voiceEnabled)
              .clickable {
                voiceEnabled = !voiceEnabled
                voiceCoach.isSpeechEnabled = voiceEnabled
                if (!voiceEnabled) voiceCoach.stop()
              }
              .padding(horizontal = 8.dp, vertical = 7.dp)
          ) {
            Icon(
              imageVector = if (voiceEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
              contentDescription = "Voice Coach",
              tint = if (voiceEnabled) CoachPrimary else TextMuted,
              modifier = Modifier.size(16.dp)
            )
          }
        }
      }
    }

    // Puzzle Banner Card
    item {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .liquidGlassCard(shape = RoundedCornerShape(18.dp))
          .padding(16.dp)
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Puzzle #${currentPuzzleIndex + 1} of ${puzzles.size}",
              color = TextMuted,
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold
            )

            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(
                  if (isSolved) StatusExcellent.copy(alpha = 0.25f)
                  else if (isFailedAttempt) StatusBlunder.copy(alpha = 0.25f)
                  else CoachPrimary.copy(alpha = 0.2f)
                )
                .border(
                  width = 1.dp,
                  color = if (isSolved) StatusExcellent.copy(alpha = 0.6f) else if (isFailedAttempt) StatusBlunder.copy(alpha = 0.6f) else CoachPrimary.copy(alpha = 0.5f),
                  shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
              Text(
                text = if (isSolved) "SOLVED (+15 Elo)" else if (isFailedAttempt) "TRY AGAIN" else "${puzzle.rating} ELO",
                color = if (isSolved) StatusExcellent else if (isFailedAttempt) StatusBlunder else CoachPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }

          Text(
            text = puzzle.title,
            color = TextTitle,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
          )

          Text(
            text = statusMessage,
            color = if (isSolved) StatusExcellent else if (isFailedAttempt) StatusMistake else TextBody,
            fontSize = 12.5.sp,
            fontWeight = if (isSolved) FontWeight.Bold else FontWeight.Normal
          )
        }
      }
    }

    // Interactive Chess Board
    item {
      val highlightedSquares = mutableSetOf<Square>()
      selectedSquare?.let { highlightedSquares.add(it) }

      // Apply hint level visual square highlights
      if (hintLevel >= 2) {
        // Highlight critical focus zone
        highlightedSquares.add(puzzle.hintLadder.level4DirectMove.to)
      }
      if (hintLevel >= 3) {
        // Highlight candidate piece
        highlightedSquares.add(puzzle.hintLadder.level3CandidatePiece)
      }

      var arrow: Pair<Square, Square>? = null
      if (hintLevel >= 4 || isSolved) {
        arrow = Pair(puzzle.hintLadder.level4DirectMove.from, puzzle.hintLadder.level4DirectMove.to)
      }

      InteractiveChessBoard(
        position = currentPosition,
        selectedSquare = selectedSquare,
        legalTargetSquares = legalTargetSquares,
        lastMove = lastMove,
        highlightedSquares = highlightedSquares,
        recommendedArrow = arrow,
        onSquareTapped = { clickedSquare ->
          if (isSolved) return@InteractiveChessBoard

          val piece = currentPosition.pieceAt(clickedSquare)

          if (selectedSquare == null) {
            // First click: select piece if belongs to side to move
            if (piece != null && piece.color == puzzle.sideToPlay) {
              selectedSquare = clickedSquare
              val legalMoves = LegalMoveGenerator.generateLegalMoves(currentPosition)
                .filter { it.from == clickedSquare }
              legalTargetSquares = legalMoves.map { it.to }.toSet()
            }
          } else {
            // Second click: attempt move
            val from = selectedSquare!!
            if (clickedSquare == from) {
              // Deselect
              selectedSquare = null
              legalTargetSquares = emptySet()
            } else if (piece != null && piece.color == puzzle.sideToPlay) {
              // Switch selected piece
              selectedSquare = clickedSquare
              val legalMoves = LegalMoveGenerator.generateLegalMoves(currentPosition)
                .filter { it.from == clickedSquare }
              legalTargetSquares = legalMoves.map { it.to }.toSet()
            } else {
              // Move attempt
              val attemptedMove = Move(from, clickedSquare)
              val expectedTargetMove = puzzle.solutionMoves.first()

              if (attemptedMove.from == expectedTargetMove.from && attemptedMove.to == expectedTargetMove.to) {
                // Correct solution move!
                val destPiece = currentPosition.pieceAt(attemptedMove.to)
                val isCapture = destPiece != null
                val nextPos = LegalMoveGenerator.makeMove(currentPosition, attemptedMove)
                val isCheck = LegalMoveGenerator.isKingInCheck(nextPos, nextPos.sideToMove)
                currentPosition = nextPos
                lastMove = attemptedMove
                selectedSquare = null
                legalTargetSquares = emptySet()
                isSolved = true
                isFailedAttempt = false
                statusMessage = "Brilliant! ${puzzle.solutionSanDisplay}: ${puzzle.explanation}"

                val newRating = dynamicRating + if (hintLevel == 0) 15 else 5
                val newSolvedCount = dynamicSolvedCount + 1
                dynamicRating = newRating
                dynamicSolvedCount = newSolvedCount
                onPuzzleSolved(newRating, newSolvedCount)

                if (soundEnabled) {
                  soundEffects.playMove(isCapture = isCapture, isCheck = isCheck)
                  soundEffects.playVictory()
                }

                if (voiceEnabled) {
                  voiceCoach.speak("Brilliant move! ${puzzle.explanation}")
                }
              } else {
                // Incorrect move
                isFailedAttempt = true
                selectedSquare = null
                legalTargetSquares = emptySet()
                statusMessage = "Incorrect. Take another look or request a hint from the coach."
                if (soundEnabled) {
                  soundEffects.playBlunder()
                }
                if (voiceEnabled) {
                  voiceCoach.speak("Not quite. Take another look or request a hint.")
                }
              }
            }
          }
        },
        modifier = Modifier
          .fillMaxWidth()
          .height(310.dp)
      )
    }

    // 4-Tier Progressive Hint Ladder Bar
    item {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .liquidGlassCard(
            shape = RoundedCornerShape(18.dp),
            borderBrush = LiquidGlassBorderGold,
            backgroundColor = Color(0x24F59E0B)
          )
          .padding(16.dp)
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Icon(imageVector = Icons.Default.Lightbulb, contentDescription = null, tint = CoachAccentGold, modifier = Modifier.size(16.dp))
              Text(
                text = "COACH HINT LADDER (${hintLevel}/4)",
                color = CoachAccentGold,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
              )
            }

            if (!isSolved && hintLevel < 4) {
              Button(
                onClick = {
                  hintLevel = (hintLevel + 1).coerceAtMost(4)
                  if (soundEnabled) {
                    soundEffects.playHint()
                  }
                  val hintSpeech = when (hintLevel) {
                    1 -> puzzle.hintLadder.level1Concept
                    2 -> puzzle.hintLadder.level2FocusZone
                    3 -> "Focus on your piece on ${puzzle.hintLadder.level3CandidatePiece.algebraic}"
                    4 -> "Play ${puzzle.hintLadder.level4DirectMove.uci}"
                    else -> ""
                  }
                  if (voiceEnabled && hintSpeech.isNotBlank()) {
                    voiceCoach.speak(hintSpeech)
                  }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CoachPrimary, contentColor = Color(0xFF0F1115)),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                shape = RoundedCornerShape(10.dp)
              ) {
                Text(
                  text = if (hintLevel == 0) "Get Hint 1" else "Next Hint (${hintLevel + 1})",
                  fontSize = 11.5.sp,
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }

          // Progressive hints displayed
          if (hintLevel >= 1) {
            HintStepCard(number = 1, title = "Concept", text = puzzle.hintLadder.level1Concept)
          }
          if (hintLevel >= 2) {
            HintStepCard(number = 2, title = "Focus Zone", text = puzzle.hintLadder.level2FocusZone)
          }
          if (hintLevel >= 3) {
            HintStepCard(number = 3, title = "Candidate Piece", text = "Examine your piece on ${puzzle.hintLadder.level3CandidatePiece.algebraic.uppercase()}")
          }
          if (hintLevel >= 4) {
            HintStepCard(number = 4, title = "Direct Solution", text = "Play ${puzzle.hintLadder.level4DirectMove.uci.uppercase()} (${puzzle.solutionSanDisplay})")
          }
        }
      }
    }

    // Navigation & Next Puzzle Controls
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Reset position button
        OutlinedButton(
          onClick = {
            currentPosition = Position.fromFen(puzzle.fen)
            selectedSquare = null
            legalTargetSquares = emptySet()
            lastMove = null
            isSolved = false
            isFailedAttempt = false
            hintLevel = 0
            statusMessage = "Board reset. Find the tactical breakthrough."
          },
          modifier = Modifier.weight(1f).height(46.dp),
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = CoachPrimary),
          border = ButtonDefaults.outlinedButtonBorder.copy(brush = LiquidGlassBorderGold)
        ) {
          Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.size(4.dp))
          Text("Reset", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
        }

        // Next Puzzle Button
        Button(
          onClick = {
            if (currentPuzzleIndex < puzzles.size - 1) {
              currentPuzzleIndex++
            } else {
              currentPuzzleIndex = 0
            }
          },
          modifier = Modifier.weight(1.5f).height(46.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = if (isSolved) StatusExcellent else CoachPrimary,
            contentColor = Color(0xFF0F1115)
          ),
          shape = RoundedCornerShape(12.dp)
        ) {
          Text(
            text = if (isSolved) "Next Puzzle (+Elo)" else "Skip / Next",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold
          )
          Spacer(modifier = Modifier.size(6.dp))
          Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
        }
      }
    }

    // Practice in Arena Shortcut
    item {
      Button(
        onClick = { onPracticeInArena(puzzle.fen, puzzle.title) },
        modifier = Modifier
          .fillMaxWidth()
          .height(44.dp)
          .liquidGlassCard(shape = RoundedCornerShape(12.dp), elevation = 2.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = CoachPrimary),
        shape = RoundedCornerShape(12.dp)
      ) {
        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.size(6.dp))
        Text("Spar from this Position in Arena", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
      }
    }
  }

  // Import / Custom Puzzle Dialog
  if (showImportDialog) {
    FenPgnImportDialog(
      initialMode = ImportMode.FEN,
      onDismiss = { showImportDialog = false },
      onPlayFenInArena = { fen, title ->
        showImportDialog = false
        onPracticeInArena(fen, title)
      },
      onAddTacticsPuzzle = { newPuzzle ->
        puzzles = TacticsRepository.getAllPuzzles()
        currentPuzzleIndex = 0
        showImportDialog = false
        if (soundEnabled) {
          soundEffects.playVictory()
        }
      }
    )
  }
}

@Composable
private fun HintStepCard(number: Int, title: String, text: String) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .liquidGlassCard(
        shape = RoundedCornerShape(10.dp),
        elevation = 2.dp,
        backgroundColor = LiquidGlassSurfaceSubtle
      )
      .padding(12.dp)
  ) {
    Row(
      verticalAlignment = Alignment.Top,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Box(
        modifier = Modifier
          .size(20.dp)
          .clip(CircleShape)
          .background(CoachAccentGold.copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = "$number",
          color = CoachAccentGold,
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold
        )
      }

      Column {
        Text(
          text = title,
          color = CoachAccentGold,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = text,
          color = TextTitle,
          fontSize = 12.sp,
          lineHeight = 16.sp
        )
      }
    }
  }
}
