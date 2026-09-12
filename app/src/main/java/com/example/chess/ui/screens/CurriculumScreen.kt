package com.example.chess.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chess.audio.rememberChessSoundEffects
import com.example.chess.audio.rememberVoiceCoach
import com.example.chess.coaching.CurriculumLesson
import com.example.chess.coaching.CurriculumRepository
import com.example.chess.core.Move
import com.example.chess.core.Position
import com.example.chess.data.ChessDatabaseProvider
import com.example.chess.openings.OpeningBranch
import com.example.chess.openings.OpeningTreeRepository
import com.example.chess.repertoire.RepertoireRepository
import com.example.chess.ui.components.CoachDialogueDeck
import com.example.chess.ui.components.InteractiveChessBoard

import com.example.chess.ui.theme.CoachAccentGold
import com.example.chess.ui.theme.CoachBadgeBg
import com.example.chess.ui.theme.CoachPrimary
import com.example.chess.ui.theme.LiquidGlassBorder
import com.example.chess.ui.theme.LiquidGlassBorderGold
import com.example.chess.ui.theme.LiquidGlassSurface
import com.example.chess.ui.theme.LiquidGlassSurfaceElevated
import com.example.chess.ui.theme.LiquidGlassSurfaceSubtle
import com.example.chess.ui.theme.liquidGlassCard
import com.example.chess.ui.theme.liquidGlassPill
import com.example.chess.ui.theme.StatusExcellent
import com.example.chess.ui.theme.TextBody
import com.example.chess.ui.theme.TextMuted
import com.example.chess.ui.theme.TextTitle

enum class CurriculumSubTab {
  REPERTOIRE,
  MASTERCLASSES,
  OPENING_TREE
}

/**
 * Tab 2: Curriculum & Interactive Opening Tree Explorer.
 * Allows exploring Masterclasses or browsing ECO opening tree branches with win rates,
 * coach commentary, and 1-tap practice transition.
 */
@Composable
fun CurriculumScreen(
  onPracticePositionInArena: (String, String) -> Unit, // (startingFen, lessonTitle)
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val dao = remember { ChessDatabaseProvider.getDatabase(context).chessDao() }
  LaunchedEffect(Unit) {
    RepertoireRepository.seedDefaultsIfEmpty(dao)
  }

  val voiceCoach = rememberVoiceCoach()
  val soundEffects = rememberChessSoundEffects()
  var soundEnabled by remember { mutableStateOf(true) }
  var selectedSubTab by remember { mutableStateOf(CurriculumSubTab.REPERTOIRE) }
  var activeLesson by remember { mutableStateOf<CurriculumLesson?>(null) }
  var currentStepIndex by remember { mutableStateOf(0) }
  var currentHintLevel by remember { mutableStateOf(0) }


  // State for Opening Tree Explorer
  var explorerPosition by remember { mutableStateOf(Position.initial()) }
  var selectedBranch by remember { mutableStateOf<OpeningBranch?>(null) }

  if (activeLesson != null) {
    val lesson = activeLesson!!
    val step = lesson.steps[currentStepIndex]
    val pos = Position.fromFen(step.startingFen)

    val coachingState = com.example.chess.coaching.CoachingState(
      title = "${lesson.title} (Step ${currentStepIndex + 1}/${lesson.steps.size})",
      conceptTitle = step.conceptTitle,
      explanationText = step.explanation,
      position = pos,
      sideToPlay = pos.sideToMove,
      targetMove = step.playedMove,
      recommendedArrow = step.recommendedArrow,
      highlightedSquares = step.highlightedSquares.toSet(),
      hintLevel = currentHintLevel,
      currentHintText = when (currentHintLevel) {
        1 -> step.hintLadder.level1Concept
        2 -> step.hintLadder.level2FocusZone
        3 -> "Piece: ${step.hintLadder.level3CandidatePiece.algebraic}"
        4 -> "Play ${step.hintLadder.level4DirectMove.uci}"
        else -> null
      },
      hintLadder = step.hintLadder,
      canPracticeInArena = true,
      practiceFen = step.startingFen
    )

    Column(
      modifier = modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp)
        .padding(top = 12.dp, bottom = 96.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          modifier = Modifier
            .liquidGlassPill(shape = RoundedCornerShape(10.dp), isActive = false)
            .clickable {
              activeLesson = null
              currentStepIndex = 0
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Icon(
            imageVector = Icons.Default.ArrowBack,
            contentDescription = "Back",
            tint = CoachPrimary,
            modifier = Modifier.size(18.dp)
          )
          Text(
            text = "Back to Curriculum",
            color = CoachPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          // Sound Toggle Pill
          Box(
            modifier = Modifier
              .liquidGlassPill(shape = RoundedCornerShape(10.dp), isActive = soundEnabled)
              .clickable { soundEnabled = !soundEnabled }
              .padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = if (soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
              contentDescription = "Sound FX",
              tint = if (soundEnabled) CoachAccentGold else TextMuted,
              modifier = Modifier.size(16.dp)
            )
          }

          Box(
            modifier = Modifier
              .liquidGlassPill(shape = RoundedCornerShape(10.dp), isActive = true)
              .padding(horizontal = 10.dp, vertical = 6.dp)
          ) {
            Text(
              text = lesson.ecoCode,
              color = CoachAccentGold,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }

      InteractiveChessBoard(
        position = pos,
        recommendedArrow = step.recommendedArrow,
        highlightedSquares = step.highlightedSquares.toSet(),
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f, fill = false)
      )

      // Step Navigator Ribbon for multi-step masterclasses
      if (lesson.steps.size > 1) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .liquidGlassCard(shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          OutlinedButton(
            onClick = {
              if (currentStepIndex > 0) {
                currentStepIndex--
                currentHintLevel = 0
                if (soundEnabled) soundEffects.playMove()
              }
            },
            enabled = currentStepIndex > 0,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = CoachPrimary),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier.height(34.dp)
          ) {
            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.size(4.dp))
            Text("Previous", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
          }

          Text(
            text = "Step ${currentStepIndex + 1} of ${lesson.steps.size}",
            color = CoachAccentGold,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
          )

          Button(
            onClick = {
              if (currentStepIndex < lesson.steps.size - 1) {
                currentStepIndex++
                currentHintLevel = 0
                if (soundEnabled) soundEffects.playMove()
              } else {
                if (soundEnabled) soundEffects.playVictory()
                activeLesson = null
                currentStepIndex = 0
              }
            },
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CoachPrimary, contentColor = Color(0xFF0F1115)),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier.height(34.dp)
          ) {
            Text(
              text = if (currentStepIndex < lesson.steps.size - 1) "Next Step" else "Complete ✓",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold
            )
            if (currentStepIndex < lesson.steps.size - 1) {
              Spacer(modifier = Modifier.size(4.dp))
              Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp))
            }
          }
        }
      }

      CoachDialogueDeck(
        state = coachingState,
        onNextHint = {
          if (currentHintLevel < 4) {
            val nextLvl = currentHintLevel + 1
            currentHintLevel = nextLvl
            if (soundEnabled) soundEffects.playHint()
            val hintSpoken = when (nextLvl) {
              1 -> step.hintLadder.level1Concept
              2 -> step.hintLadder.level2FocusZone
              3 -> "Candidate piece on square ${step.hintLadder.level3CandidatePiece.algebraic}"
              4 -> "Direct plan: play ${step.hintLadder.level4DirectMove.uci}"
              else -> null
            }
            if (hintSpoken != null) voiceCoach.speak(hintSpoken)
          }
        },
        onPracticeInArena = {
          onPracticePositionInArena(step.startingFen, lesson.title)
        },
        onSpeak = {
          voiceCoach.speak("${step.conceptTitle}. ${step.explanation}")
        },
        modifier = Modifier.fillMaxWidth()
      )
    }
    return
  }

  // Curriculum Main View with Tabs
  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp)
      .padding(top = 12.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // Top Bar Selector
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column {
        Text(
          text = "ACADEMY SYLLABUS",
          color = CoachPrimary,
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 1.sp
        )
        Text(
          text = when (selectedSubTab) {
            CurriculumSubTab.REPERTOIRE -> "Repertoire Studio"
            CurriculumSubTab.MASTERCLASSES -> "Masterclasses"
            CurriculumSubTab.OPENING_TREE -> "Opening Explorer"
          },
          color = TextTitle,
          fontSize = 20.sp,
          fontWeight = FontWeight.Bold
        )
      }

      // Mode Switch Glass Dock
      Row(
        modifier = Modifier
          .liquidGlassCard(shape = RoundedCornerShape(14.dp), elevation = 4.dp)
          .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selectedSubTab == CurriculumSubTab.REPERTOIRE) CoachPrimary else Color.Transparent)
            .clickable { selectedSubTab = CurriculumSubTab.REPERTOIRE }
            .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
          Text(
            text = "Repertoire",
            color = if (selectedSubTab == CurriculumSubTab.REPERTOIRE) Color(0xFF0F1115) else TextMuted,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold
          )
        }

        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selectedSubTab == CurriculumSubTab.MASTERCLASSES) CoachPrimary else Color.Transparent)
            .clickable { selectedSubTab = CurriculumSubTab.MASTERCLASSES }
            .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
          Text(
            text = "Classes",
            color = if (selectedSubTab == CurriculumSubTab.MASTERCLASSES) Color(0xFF0F1115) else TextMuted,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold
          )
        }

        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selectedSubTab == CurriculumSubTab.OPENING_TREE) CoachPrimary else Color.Transparent)
            .clickable { selectedSubTab = CurriculumSubTab.OPENING_TREE }
            .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
          Text(
            text = "Tree",
            color = if (selectedSubTab == CurriculumSubTab.OPENING_TREE) Color(0xFF0F1115) else TextMuted,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }

    when (selectedSubTab) {
      CurriculumSubTab.REPERTOIRE -> {
        RepertoireStudioView(
          dao = dao,
          soundEnabled = soundEnabled,
          soundEffects = soundEffects,
          voiceCoach = voiceCoach,
          onPracticePositionInArena = onPracticePositionInArena,
          modifier = Modifier.fillMaxSize()
        )
      }
      CurriculumSubTab.MASTERCLASSES -> {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          items(CurriculumRepository.allLessons) { lesson ->
            CurriculumLessonCard(
              lesson = lesson,
              onClick = {
                activeLesson = lesson
                currentStepIndex = 0
                currentHintLevel = 0
              }
            )
          }
        }
      }
      CurriculumSubTab.OPENING_TREE -> {

      // Opening Tree Explorer Screen
      val branches = OpeningTreeRepository.getBranchesForPosition(explorerPosition)

      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        // Board & reset button
        item {
          Box(modifier = Modifier.fillMaxWidth()) {
            InteractiveChessBoard(
              position = explorerPosition,
              modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
            )

            Button(
              onClick = {
                explorerPosition = Position.initial()
                selectedBranch = null
              },
              modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(38.dp)
                .liquidGlassPill(shape = CircleShape, isActive = true),
              shape = CircleShape,
              colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
              contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
            ) {
              Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reset", tint = CoachAccentGold, modifier = Modifier.size(16.dp))
            }
          }
        }

        // Branch Selection Header
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "CANDIDATE MOVES IN THIS POSITION",
              color = TextMuted,
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              letterSpacing = 1.sp
            )
            Text(
              text = "${branches.size} Theory Lines",
              color = CoachAccentGold,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }

        if (branches.isEmpty()) {
          item {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .liquidGlassCard(shape = RoundedCornerShape(14.dp))
                .padding(16.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = "Position outside common book lines. Explore from 1. e4 or reset the board.",
                color = TextMuted,
                fontSize = 12.sp
              )
            }
          }
        }

        items(branches) { branch ->
          val isBranchSelected = selectedBranch == branch
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .liquidGlassCard(
                shape = RoundedCornerShape(16.dp),
                borderBrush = if (isBranchSelected) LiquidGlassBorderGold else LiquidGlassBorder,
                backgroundColor = if (isBranchSelected) Color(0x28F59E0B) else LiquidGlassSurface
              )
              .clickable {
                selectedBranch = branch
                explorerPosition = Position.fromFen(branch.resultingFen)
              }
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
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Box(
                    modifier = Modifier
                      .clip(RoundedCornerShape(8.dp))
                      .background(CoachPrimary)
                      .padding(horizontal = 8.dp, vertical = 4.dp)
                  ) {
                    Text(
                      text = branch.moveSan,
                      color = Color(0xFF0F1115),
                      fontSize = 12.5.sp,
                      fontWeight = FontWeight.ExtraBold
                    )
                  }

                  Text(
                    text = branch.openingName,
                    color = TextTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                  )
                }

                Box(
                  modifier = Modifier
                    .liquidGlassPill(shape = RoundedCornerShape(8.dp), isActive = false)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                  Text(
                    text = branch.ecoCode,
                    color = CoachAccentGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                  )
                }
              }

              // Win rate bar indicator
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(6.dp)
                  .clip(RoundedCornerShape(3.dp))
              ) {
                Box(modifier = Modifier.weight(branch.whiteWinPct.toFloat()).fillMaxSize().background(Color.White))
                Box(modifier = Modifier.weight(branch.drawPct.toFloat()).fillMaxSize().background(Color.Gray))
                Box(modifier = Modifier.weight(branch.blackWinPct.toFloat()).fillMaxSize().background(Color(0xFF1E2128)))
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(text = "White: ${branch.whiteWinPct}%", color = TextMuted, fontSize = 10.sp)
                Text(text = "Draw: ${branch.drawPct}%", color = TextMuted, fontSize = 10.sp)
                Text(text = "Black: ${branch.blackWinPct}%", color = TextMuted, fontSize = 10.sp)
              }

              Text(
                text = branch.coachExplanation,
                color = TextBody,
                fontSize = 12.5.sp,
                lineHeight = 17.sp
              )

              // 1-Tap Practice button for this opening branch
              Button(
                onClick = {
                  onPracticePositionInArena(branch.resultingFen, branch.openingName)
                },
                modifier = Modifier
                  .fillMaxWidth()
                  .height(40.dp)
                  .liquidGlassCard(shape = RoundedCornerShape(10.dp), elevation = 2.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                  containerColor = Color.Transparent,
                  contentColor = CoachAccentGold
                )
              ) {
                Icon(imageVector = Icons.Default.SportsEsports, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.size(6.dp))
                Text("Practice this branch in Arena", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }
    }
  }
}
}

@Composable
private fun CurriculumLessonCard(
  lesson: CurriculumLesson,
  onClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .liquidGlassCard(shape = RoundedCornerShape(18.dp))
      .clickable { onClick() }
      .padding(18.dp)
      .testTag("curriculum_card_${lesson.id}")
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .liquidGlassPill(shape = RoundedCornerShape(8.dp), isActive = true)
            .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Text(
            text = lesson.category,
            color = CoachAccentGold,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
          )
        }

        Text(
          text = lesson.ecoCode,
          color = TextMuted,
          fontSize = 11.sp,
          fontWeight = FontWeight.SemiBold
        )
      }

      Text(
        text = lesson.title,
        color = TextTitle,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold
      )

      Text(
        text = lesson.summary,
        color = TextBody,
        fontSize = 12.5.sp,
        lineHeight = 17.sp
      )

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "${lesson.steps.size} Masterclass Steps",
          color = CoachPrimary,
          fontSize = 12.sp,
          fontWeight = FontWeight.SemiBold
        )

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Text("Start", color = CoachPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
          Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "Start Lesson",
            tint = CoachPrimary,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}
