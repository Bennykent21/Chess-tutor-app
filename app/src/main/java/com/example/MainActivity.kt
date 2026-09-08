package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.chess.coaching.CurriculumRepository
import com.example.chess.core.Move
import com.example.chess.core.Position
import com.example.chess.data.ChessDatabaseProvider
import com.example.chess.data.UserProgress
import com.example.chess.engine.TrainingLevel
import com.example.chess.ui.components.LiquidGlassCanvas
import com.example.chess.ui.navigation.ChessAppTab
import com.example.chess.ui.navigation.ChessBottomNavigationBar
import com.example.chess.ui.screens.ArenaScreen
import com.example.chess.ui.screens.AssessmentScreen
import com.example.chess.ui.screens.CoachHomeScreen
import com.example.chess.ui.screens.CurriculumScreen
import com.example.chess.ui.screens.ReviewScreen
import com.example.chess.ui.screens.TacticsDojoScreen
import com.example.chess.ui.theme.CanvasBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MainNavigationDestination {
  TABS,
  PLACEMENT_ASSESSMENT,
  TACTICS_DOJO
}

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    setContent {
      ChessTutorApp()
    }
  }
}

@Composable
fun ChessTutorApp() {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }

  val dao = remember { ChessDatabaseProvider.getDatabase(context).chessDao() }
  val activeMistakesFlow = remember { dao.getActiveMistakes() }
  val dueMistakes by activeMistakesFlow.collectAsState(initial = emptyList())

  val userProgressFlow = remember { dao.getUserProgressFlow() }
  val userProgress by userProgressFlow.collectAsState(initial = null)
  val userRating = userProgress?.estimatedRating ?: 1200

  var currentDestination by remember { mutableStateOf(MainNavigationDestination.TABS) }
  var currentTab by remember { mutableStateOf(ChessAppTab.COACH) }
  var arenaStartingFen by remember { mutableStateOf(Position.STARTING_FEN) }
  var arenaSelectedLevel by remember { mutableStateOf(TrainingLevel.INTERMEDIATE_1200) }

  val activeCurriculumLesson = remember { CurriculumRepository.allLessons.first() }

  Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
    containerColor = CanvasBackground,
    contentColor = Color.White
  ) { paddingValues ->
    LiquidGlassCanvas(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
    ) {
      if (currentDestination == MainNavigationDestination.PLACEMENT_ASSESSMENT) {
        AssessmentScreen(
          onAssessmentCompleted = { calculatedRating ->
            coroutineScope.launch {
              withContext(Dispatchers.IO) {
                dao.upsertUserProgress(
                  (userProgress ?: UserProgress()).copy(estimatedRating = calculatedRating)
                )
              }
              // Map calculated ELO to nearest bot sparring tier
              arenaSelectedLevel = when {
                calculatedRating < 1000 -> TrainingLevel.BEGINNER_800
                calculatedRating < 1200 -> TrainingLevel.CASUAL_1000
                calculatedRating < 1400 -> TrainingLevel.INTERMEDIATE_1200
                calculatedRating < 1600 -> TrainingLevel.CLUB_1400
                calculatedRating < 1800 -> TrainingLevel.ADVANCED_1600
                else -> TrainingLevel.EXPERT_1800
              }
              currentDestination = MainNavigationDestination.TABS
              currentTab = ChessAppTab.COACH
              snackbarHostState.showSnackbar("Skill placed at $calculatedRating ELO. Training calibrated!")
            }
          },
          onCancel = {
            currentDestination = MainNavigationDestination.TABS
          }
        )
      } else if (currentDestination == MainNavigationDestination.TACTICS_DOJO) {
        TacticsDojoScreen(
          userTacticsRating = userProgress?.tacticsRating ?: 1100,
          puzzlesSolvedCount = userProgress?.puzzlesSolved ?: 0,
          onPuzzleSolved = { newRating, solvedCount ->
            coroutineScope.launch {
              withContext(Dispatchers.IO) {
                dao.upsertUserProgress(
                  (userProgress ?: UserProgress()).copy(
                    tacticsRating = newRating,
                    puzzlesSolved = solvedCount
                  )
                )
              }
            }
          },
          onPracticeInArena = { fen, title ->
            arenaStartingFen = fen
            arenaSelectedLevel = TrainingLevel.INTERMEDIATE_1200
            currentDestination = MainNavigationDestination.TABS
            currentTab = ChessAppTab.ARENA
            coroutineScope.launch {
              snackbarHostState.showSnackbar("Tactical Sparring: $title")
            }
          },
          onClose = {
            currentDestination = MainNavigationDestination.TABS
          }
        )
      } else {
        AnimatedContent(
          targetState = currentTab,
          transitionSpec = { fadeIn() togetherWith fadeOut() },
          label = "tab_content_transition"
        ) { tab ->
          when (tab) {
            ChessAppTab.COACH -> CoachHomeScreen(
              userEstimatedRating = userRating,
              userTacticsRating = userProgress?.tacticsRating ?: 1100,
              puzzlesSolvedCount = userProgress?.puzzlesSolved ?: 0,
              dueMistakes = dueMistakes,
              activeLesson = activeCurriculumLesson,
              onStartPlacementAssessment = {
                currentDestination = MainNavigationDestination.PLACEMENT_ASSESSMENT
              },
              onOpenTacticsDojo = {
                currentDestination = MainNavigationDestination.TACTICS_DOJO
              },
              onStartSpacedReview = {
                currentTab = ChessAppTab.REVIEW
                coroutineScope.launch {
                  snackbarHostState.showSnackbar("Loaded Spaced-Repetition Review Queue")
                }
              },
              onResumeLesson = {
                currentTab = ChessAppTab.CURRICULUM
              },
              onLaunchSparring = { level ->
                arenaSelectedLevel = level
                arenaStartingFen = Position.STARTING_FEN
                currentTab = ChessAppTab.ARENA
                coroutineScope.launch {
                  snackbarHostState.showSnackbar("Sparring loaded: ${level.title}")
                }
              }
            )

            ChessAppTab.CURRICULUM -> CurriculumScreen(
              onPracticePositionInArena = { fen, lessonTitle ->
                arenaStartingFen = fen
                arenaSelectedLevel = TrainingLevel.INTERMEDIATE_1200
                currentTab = ChessAppTab.ARENA
                coroutineScope.launch {
                  snackbarHostState.showSnackbar("Contextual Practice: $lessonTitle")
                }
              }
            )

            ChessAppTab.ARENA -> ArenaScreen(
              initialFen = arenaStartingFen,
              selectedLevel = arenaSelectedLevel,
              onGameFinished = { finalPos, moves ->
                currentTab = ChessAppTab.REVIEW
                coroutineScope.launch {
                  snackbarHostState.showSnackbar("Match completed! Debrief loaded.")
                }
              }
            )

            ChessAppTab.REVIEW -> ReviewScreen(
              onRetryPosition = { fen, targetMove ->
                arenaStartingFen = fen
                currentTab = ChessAppTab.ARENA
                coroutineScope.launch {
                  snackbarHostState.showSnackbar("Retrying position: Find ${targetMove.uci}")
                }
              },
              onPracticeInArena = { fen, title ->
                arenaStartingFen = fen
                currentTab = ChessAppTab.ARENA
                coroutineScope.launch {
                  snackbarHostState.showSnackbar("Loaded into Arena: $title")
                }
              }
            )
          }
        }

        ChessBottomNavigationBar(
          currentTab = currentTab,
          onTabSelected = { newTab -> currentTab = newTab },
          modifier = Modifier.align(Alignment.BottomCenter)
        )
      }
    }
  }
}
