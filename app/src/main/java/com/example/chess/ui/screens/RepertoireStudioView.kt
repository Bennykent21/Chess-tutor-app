package com.example.chess.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.example.chess.audio.VoiceCoach
import com.example.chess.core.LegalMoveGenerator
import com.example.chess.core.Move
import com.example.chess.core.PieceColor
import com.example.chess.core.Position
import com.example.chess.core.Square
import com.example.chess.data.ChessDao
import com.example.chess.data.RepertoireEntity
import com.example.chess.data.RepertoirePositionEntity
import com.example.chess.repertoire.RepertoireRepository
import com.example.chess.ui.components.InteractiveChessBoard
import com.example.chess.ui.theme.CoachAccentGold
import com.example.chess.ui.theme.CoachBadgeBg
import com.example.chess.ui.theme.CoachPrimary
import com.example.chess.ui.theme.LiquidGlassBorder
import com.example.chess.ui.theme.LiquidGlassSurface
import com.example.chess.ui.theme.LiquidGlassSurfaceElevated
import com.example.chess.ui.theme.StatusBlunder
import com.example.chess.ui.theme.StatusExcellent
import com.example.chess.ui.theme.StatusMistake
import com.example.chess.ui.theme.TextBody
import com.example.chess.ui.theme.TextMuted
import com.example.chess.ui.theme.TextTitle
import com.example.chess.ui.theme.liquidGlassCard
import com.example.chess.ui.theme.liquidGlassPill
import kotlinx.coroutines.launch

@Composable
fun RepertoireStudioView(
  dao: ChessDao,
  soundEnabled: Boolean,
  soundEffects: ChessSoundEffects,
  voiceCoach: VoiceCoach,
  onPracticePositionInArena: (String, String) -> Unit,
  modifier: Modifier = Modifier
) {
  val coroutineScope = rememberCoroutineScope()
  val allRepertoires by dao.getAllRepertoiresFlow().collectAsState(initial = emptyList())

  var selectedSide by remember { mutableStateOf(PieceColor.WHITE) }
  val sideRepertoires = remember(allRepertoires, selectedSide) {
    allRepertoires.filter { it.side.equals(selectedSide.name, ignoreCase = true) }
  }

  var selectedRepertoireId by remember { mutableStateOf<Long?>(null) }
  val activeRepertoire = remember(sideRepertoires, selectedRepertoireId) {
    sideRepertoires.find { it.id == selectedRepertoireId } ?: sideRepertoires.firstOrNull()
  }

  val positionsFlow = remember(activeRepertoire?.id) {
    activeRepertoire?.let { dao.getPositionsForRepertoireFlow(it.id) }
  }
  val positions by (positionsFlow?.collectAsState(initial = emptyList())
    ?: remember { mutableStateOf(emptyList()) })

  var currentPositionIndex by remember { mutableStateOf(0) }
  val currentRepertoirePos = positions.getOrNull(currentPositionIndex)

  // Weak Lines Drill Mode State
  var isDrillModeActive by remember { mutableStateOf(false) }
  var drillQueue by remember { mutableStateOf<List<RepertoirePositionEntity>>(emptyList()) }
  var drillIndex by remember { mutableStateOf(0) }
  var drillSelectedSquare by remember { mutableStateOf<Square?>(null) }
  var drillLegalTargets by remember { mutableStateOf<Set<Square>>(emptySet()) }
  var drillSuccess by remember { mutableStateOf<Boolean?>(null) }
  var drillFeedback by remember { mutableStateOf<String?>(null) }

  // Derive board position safely
  val currentBoardPosition = remember(currentRepertoirePos, positions) {
    if (currentRepertoirePos != null) {
      try {
        Position.fromFen(currentRepertoirePos.fen)
      } catch (_: Exception) {
        Position.initial()
      }
    } else {
      Position.initial()
    }
  }

  // Recommended arrow for next candidate move
  val recommendedArrow = remember(currentRepertoirePos) {
    if (currentRepertoirePos != null && currentRepertoirePos.moveUci.length >= 4) {
      try {
        val from = Square.fromAlgebraic(currentRepertoirePos.moveUci.substring(0, 2))
        val to = Square.fromAlgebraic(currentRepertoirePos.moveUci.substring(2, 4))
        Pair(from, to)
      } catch (_: Exception) {
        null
      }
    } else null
  }

  // Stats calculation
  val totalPos = positions.size
  val knownPos = positions.count { it.status == "KNOWN" }
  val learningPos = positions.count { it.status == "LEARNING" }
  val weakPos = positions.count { it.status == "WEAK" }
  val masteredRatio = if (totalPos > 0) knownPos.toFloat() / totalPos else 0f

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // 1. Side Switcher Dock (White vs Black)
    item {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .liquidGlassCard(shape = RoundedCornerShape(14.dp))
          .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Box(
          modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selectedSide == PieceColor.WHITE) CoachPrimary else Color.Transparent)
            .clickable {
              selectedSide = PieceColor.WHITE
              currentPositionIndex = 0
            }
            .padding(vertical = 8.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "White Repertoire",
            color = if (selectedSide == PieceColor.WHITE) Color(0xFF0F1115) else TextMuted,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold
          )
        }

        Box(
          modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selectedSide == PieceColor.BLACK) CoachPrimary else Color.Transparent)
            .clickable {
              selectedSide = PieceColor.BLACK
              currentPositionIndex = 0
            }
            .padding(vertical = 8.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "Black Repertoire",
            color = if (selectedSide == PieceColor.BLACK) Color(0xFF0F1115) else TextMuted,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }

    // 2. Repertoire Picker Pills
    if (sideRepertoires.isNotEmpty()) {
      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          sideRepertoires.forEach { rep ->
            val isSelected = rep.id == activeRepertoire?.id
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (isSelected) LiquidGlassSurfaceElevated else LiquidGlassSurface)
                .border(
                  width = 1.dp,
                  color = if (isSelected) CoachAccentGold else Color(0x334E5D6C),
                  shape = RoundedCornerShape(10.dp)
                )
                .clickable {
                  selectedRepertoireId = rep.id
                  currentPositionIndex = 0
                }
                .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {

              Column {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Text(
                    text = rep.ecoFamily,
                    color = CoachAccentGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                  )
                  Text(
                    text = rep.name,
                    color = if (isSelected) TextTitle else TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                  )
                }
              }
            }
          }
        }
      }
    }

    // 3. Repertoire Health Hero Card
    if (activeRepertoire != null) {
      item {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .liquidGlassCard(shape = RoundedCornerShape(16.dp))
            .padding(16.dp)
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = activeRepertoire.name,
                  color = TextTitle,
                  fontSize = 16.sp,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = activeRepertoire.description,
                  color = TextMuted,
                  fontSize = 11.5.sp,
                  lineHeight = 16.sp
                )
              }

              // Drill Weak Lines CTA Button
              Button(
                onClick = {
                  val weakList = positions.filter { it.status == "WEAK" || it.status == "LEARNING" }
                  if (weakList.isNotEmpty()) {
                    drillQueue = weakList
                    drillIndex = 0
                    drillFeedback = null
                    drillSuccess = null
                    drillSelectedSquare = null
                    drillLegalTargets = emptySet()
                    isDrillModeActive = true
                  }
                },
                enabled = weakPos > 0 || learningPos > 0,
                colors = ButtonDefaults.buttonColors(
                  containerColor = CoachAccentGold,
                  contentColor = Color(0xFF0F1115),
                  disabledContainerColor = LiquidGlassSurfaceElevated,
                  disabledContentColor = TextMuted
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("drill_weak_lines_button")
              ) {
                Icon(imageVector = Icons.Default.FitnessCenter, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Drill Weak", fontSize = 11.sp, fontWeight = FontWeight.Bold)
              }
            }

            // Progress Bar & Counts
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  text = "Coverage & Mastery",
                  color = TextMuted,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Medium
                )
                Text(
                  text = "${(masteredRatio * 100).toInt()}% Known",
                  color = CoachAccentGold,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold
                )
              }
              LinearProgressIndicator(
                progress = { masteredRatio.coerceIn(0f, 1f) },
                modifier = Modifier
                  .fillMaxWidth()
                  .height(6.dp)
                  .clip(RoundedCornerShape(3.dp)),
                color = CoachAccentGold,
                trackColor = LiquidGlassSurfaceElevated
              )
            }

            // Stat Badges
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Box(
                modifier = Modifier
                  .weight(1f)
                  .liquidGlassPill(shape = RoundedCornerShape(8.dp), isActive = false)
                  .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = "$totalPos Positions",
                  color = TextTitle,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold
                )
              }

              Box(
                modifier = Modifier
                  .weight(1f)
                  .background(CoachBadgeBg, shape = RoundedCornerShape(8.dp))
                  .border(1.dp, StatusExcellent.copy(alpha = 0.4f), shape = RoundedCornerShape(8.dp))
                  .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = "$knownPos Known",
                  color = StatusExcellent,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold
                )
              }

              Box(
                modifier = Modifier
                  .weight(1f)
                  .background(LiquidGlassSurfaceElevated, shape = RoundedCornerShape(8.dp))
                  .border(
                    width = 1.dp,
                    color = if (weakPos > 0) StatusBlunder.copy(alpha = 0.5f) else Color(0x334E5D6C),
                    shape = RoundedCornerShape(8.dp)
                  )
                  .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = "$weakPos Weak",
                  color = if (weakPos > 0) StatusBlunder else TextMuted,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }
        }
      }
    }

    // 4. Drill Mode Active View
    if (isDrillModeActive && drillQueue.isNotEmpty() && drillIndex in drillQueue.indices) {
      item {
        val drillItem = drillQueue[drillIndex]
        val drillPos = remember(drillItem.id) {
          try {
            val fenToUse = if (!drillItem.parentFen.isNullOrEmpty()) drillItem.parentFen else drillItem.fen
            Position.fromFen(fenToUse)
          } catch (_: Exception) {
            Position.initial()
          }
        }


        Box(
          modifier = Modifier
            .fillMaxWidth()
            .liquidGlassCard(shape = RoundedCornerShape(16.dp))
            .padding(16.dp)
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Icon(imageVector = Icons.Default.Bolt, contentDescription = null, tint = CoachAccentGold, modifier = Modifier.size(16.dp))
                Text(
                  text = "REPERTOIRE DRILL (${drillIndex + 1}/${drillQueue.size})",
                  color = CoachAccentGold,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  letterSpacing = 1.sp
                )
              }

              IconButton(
                onClick = { isDrillModeActive = false },
                modifier = Modifier.size(28.dp)
              ) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Exit Drill", tint = TextMuted, modifier = Modifier.size(16.dp))
              }
            }

            Text(
              text = "What is your prepared repertoire move here as ${selectedSide.name.lowercase().replaceFirstChar { it.uppercase() }}?",
              color = TextTitle,
              fontSize = 13.sp,
              fontWeight = FontWeight.SemiBold
            )

            // Drill Interactive Chess Board
            InteractiveChessBoard(
              position = drillPos,
              selectedSquare = drillSelectedSquare,
              legalTargetSquares = drillLegalTargets,
              onSquareTapped = { sq ->
                val occupant = drillPos.pieceAt(sq)
                if (drillSelectedSquare == null) {
                  if (occupant != null && occupant.color == drillPos.sideToMove) {
                    drillSelectedSquare = sq
                    drillLegalTargets = LegalMoveGenerator.generateLegalMoves(drillPos)
                      .filter { it.from == sq }
                      .map { it.to }
                      .toSet()
                  }
                } else {
                  val from = drillSelectedSquare!!
                  val matchingMove = LegalMoveGenerator.generateLegalMoves(drillPos)
                    .firstOrNull { it.from == from && it.to == sq }

                  if (matchingMove != null) {
                    val isCorrect = matchingMove.uci == drillItem.moveUci ||
                      matchingMove.san.equals(drillItem.moveSan, ignoreCase = true)

                    if (isCorrect) {
                      drillSuccess = true
                      drillFeedback = "Correct! You played ${drillItem.moveSan}. Repertoire line confirmed."
                      if (soundEnabled) soundEffects.playVictory()
                      coroutineScope.launch {
                        RepertoireRepository.updatePositionStatus(dao, drillItem.id, "KNOWN")
                      }
                    } else {
                      drillSuccess = false
                      drillFeedback = "Incorrect: You played ${matchingMove.san}. The prepared move is ${drillItem.moveSan} (${drillItem.coachNote})."
                      if (soundEnabled) soundEffects.playBlunder()
                    }
                  } else if (occupant != null && occupant.color == drillPos.sideToMove) {
                    drillSelectedSquare = sq
                    drillLegalTargets = LegalMoveGenerator.generateLegalMoves(drillPos)
                      .filter { it.from == sq }
                      .map { it.to }
                      .toSet()
                  } else {
                    drillSelectedSquare = null
                    drillLegalTargets = emptySet()
                  }
                }
              },
              modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
            )


            // Feedback Card
            drillFeedback?.let { fb ->
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .background(
                    if (drillSuccess == true) CoachBadgeBg else LiquidGlassSurfaceElevated,
                    shape = RoundedCornerShape(10.dp)
                  )
                  .border(
                    1.dp,
                    if (drillSuccess == true) StatusExcellent else StatusBlunder,
                    shape = RoundedCornerShape(10.dp)
                  )
                  .padding(10.dp)
              ) {
                Text(
                  text = fb,
                  color = if (drillSuccess == true) StatusExcellent else StatusBlunder,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Medium
                )
              }
            }

            // Next Drill Button
            if (drillSuccess != null) {
              Button(
                onClick = {
                  if (drillIndex < drillQueue.size - 1) {
                    drillIndex++
                    drillFeedback = null
                    drillSuccess = null
                    drillSelectedSquare = null
                    drillLegalTargets = emptySet()
                  } else {
                    isDrillModeActive = false
                  }
                },
                modifier = Modifier.fillMaxWidth().height(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CoachAccentGold, contentColor = Color(0xFF0F1115)),
                shape = RoundedCornerShape(10.dp)
              ) {
                Text(
                  text = if (drillIndex < drillQueue.size - 1) "Next Position" else "Complete Drill Session",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }
        }
      }
    } else {
      // 5. Interactive Repertoire Line Board
      item {
        Box(modifier = Modifier.fillMaxWidth()) {
          InteractiveChessBoard(
            position = currentBoardPosition,
            recommendedArrow = recommendedArrow,
            modifier = Modifier
              .fillMaxWidth()
              .height(270.dp)
          )
        }
      }

      // 6. Step Navigator & Variation Breadcrumbs
      item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          // Breadcrumbs
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            positions.forEachIndexed { idx, p ->
              val isCurrent = idx == currentPositionIndex
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(8.dp))
                  .background(if (isCurrent) CoachPrimary else LiquidGlassSurface)
                  .clickable {
                    currentPositionIndex = idx
                    if (soundEnabled) soundEffects.playMove()
                  }
                  .padding(horizontal = 8.dp, vertical = 4.dp)
              ) {
                Text(
                  text = "${idx + 1}. ${p.moveSan}",
                  color = if (isCurrent) Color(0xFF0F1115) else TextMuted,
                  fontSize = 11.5.sp,
                  fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                )
              }
            }
          }

          // Step Controls
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .liquidGlassCard(shape = RoundedCornerShape(12.dp))
              .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            OutlinedButton(
              onClick = {
                if (currentPositionIndex > 0) {
                  currentPositionIndex--
                  if (soundEnabled) soundEffects.playMove()
                }
              },
              enabled = currentPositionIndex > 0,
              shape = RoundedCornerShape(8.dp),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = CoachPrimary),
              contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
              modifier = Modifier.height(34.dp)
            ) {
              Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Prev", modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Prev", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }

            Text(
              text = if (positions.isNotEmpty()) "Move ${currentPositionIndex + 1} of ${positions.size}" else "0 moves",
              color = CoachAccentGold,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold
            )

            Button(
              onClick = {
                if (currentPositionIndex < positions.size - 1) {
                  currentPositionIndex++
                  if (soundEnabled) soundEffects.playMove()
                }
              },
              enabled = currentPositionIndex < positions.size - 1,
              shape = RoundedCornerShape(8.dp),
              colors = ButtonDefaults.buttonColors(containerColor = CoachPrimary, contentColor = Color(0xFF0F1115)),
              contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
              modifier = Modifier.height(34.dp)
            ) {
              Text("Next", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
              Spacer(modifier = Modifier.width(4.dp))
              Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next", modifier = Modifier.size(14.dp))
            }
          }
        }
      }

      // 7. Current Position Inspector (Critical, Status, Strategic Note, Sparring)
      if (currentRepertoirePos != null) {
        item {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .liquidGlassCard(shape = RoundedCornerShape(16.dp))
              .padding(16.dp)
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Text(
                    text = "CANDIDATE: ${currentRepertoirePos.moveSan}",
                    color = CoachAccentGold,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                  )

                  // Critical Position Star Toggle
                  IconButton(
                    onClick = {
                      coroutineScope.launch {
                        RepertoireRepository.toggleCritical(dao, currentRepertoirePos.id)
                      }
                    },
                    modifier = Modifier.size(28.dp)
                  ) {
                    Icon(
                      imageVector = if (currentRepertoirePos.isCritical) Icons.Default.Star else Icons.Default.StarBorder,
                      contentDescription = "Toggle Critical",
                      tint = if (currentRepertoirePos.isCritical) CoachAccentGold else TextMuted,
                      modifier = Modifier.size(18.dp)
                    )
                  }
                }

                // Practice in Sparring Arena Button
                Button(
                  onClick = {
                    val lessonTitle = "${activeRepertoire?.name ?: "Repertoire"}: ${currentRepertoirePos.moveSan}"
                    onPracticePositionInArena(currentRepertoirePos.fen, lessonTitle)
                  },
                  colors = ButtonDefaults.buttonColors(containerColor = CoachPrimary, contentColor = Color(0xFF0F1115)),
                  shape = RoundedCornerShape(8.dp),
                  contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                  modifier = Modifier.height(32.dp)
                ) {
                  Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Spar Arena", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
              }

              // Status Selector Chips
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                listOf("KNOWN", "LEARNING", "WEAK").forEach { statusKey ->
                  val isSelected = currentRepertoirePos.status == statusKey
                  val statusColor = when (statusKey) {
                    "KNOWN" -> StatusExcellent
                    "LEARNING" -> CoachAccentGold
                    else -> StatusBlunder
                  }
                  Box(
                    modifier = Modifier
                      .weight(1f)
                      .clip(RoundedCornerShape(8.dp))
                      .background(if (isSelected) statusColor.copy(alpha = 0.2f) else LiquidGlassSurface)
                      .border(
                        width = 1.dp,
                        color = if (isSelected) statusColor else Color(0x334E5D6C),
                        shape = RoundedCornerShape(8.dp)
                      )

                      .clickable {
                        coroutineScope.launch {
                          RepertoireRepository.updatePositionStatus(dao, currentRepertoirePos.id, statusKey)
                        }
                      }
                      .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                  ) {
                    Text(
                      text = statusKey,
                      color = if (isSelected) statusColor else TextMuted,
                      fontSize = 10.5.sp,
                      fontWeight = FontWeight.Bold
                    )
                  }
                }
              }

              // Strategic Coach Commentary
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
              ) {
                Text(
                  text = currentRepertoirePos.coachNote,
                  color = TextBody,
                  fontSize = 12.sp,
                  lineHeight = 17.sp,
                  modifier = Modifier.weight(1f)
                )

                IconButton(
                  onClick = { voiceCoach.speak(currentRepertoirePos.coachNote) },
                  modifier = Modifier.size(30.dp)
                ) {
                  Icon(imageVector = Icons.Default.VolumeUp, contentDescription = "Voice Coach", tint = CoachAccentGold, modifier = Modifier.size(16.dp))
                }
              }
            }
          }
        }
      }
    }
  }
}
