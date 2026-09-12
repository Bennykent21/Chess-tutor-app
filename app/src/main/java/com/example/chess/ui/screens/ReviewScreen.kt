package com.example.chess.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chess.analysis.AnalyzedMove
import com.example.chess.analysis.BlunderClassifier
import com.example.chess.analysis.MoveClassification
import com.example.chess.analysis.ParsedPgnGame
import com.example.chess.analysis.PgnParser
import com.example.chess.audio.rememberChessSoundEffects
import com.example.chess.audio.rememberVoiceCoach
import com.example.chess.core.LegalMoveGenerator
import com.example.chess.core.Move
import com.example.chess.core.PieceColor
import com.example.chess.core.Position
import com.example.chess.core.Square
import com.example.chess.data.ChessDatabaseProvider
import com.example.chess.data.MistakeRecord
import com.example.chess.network.ChessComClient
import com.example.chess.network.ChessComGameItem
import com.example.chess.engine.Evaluation
import com.example.chess.engine.LocalChessEngine
import com.example.chess.tactics.TacticsRepository
import com.example.chess.ui.components.CentipawnEvaluationGraph
import com.example.chess.ui.components.FenPgnImportDialog
import com.example.chess.ui.components.ImportMode
import com.example.chess.ui.components.InteractiveChessBoard
import com.example.chess.ui.components.MoveEvaluationPoint
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
import com.example.chess.ui.theme.StatusInaccuracy
import com.example.chess.ui.theme.StatusMistake
import com.example.chess.ui.theme.TextBody
import com.example.chess.ui.theme.TextMuted
import com.example.chess.ui.theme.TextTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ReviewSubTab {
  GAME_STORY,
  PGN_REPLAY,
  SPACED_MISTAKES
}

data class GameReviewChapter(
  val moveNumber: Int,
  val playerColor: PieceColor,
  val move: Move,
  val bestMove: Move,
  val classification: MoveClassification,
  val narrativeExplanation: String,
  val fenBefore: String
)

/**
 * Tab 4: Review (The Blunder Translator & Spaced-Repetition Mistake Book).
 * Connects directly to the Room Database to retrieve user blunders, allowing
 * interactive practice, stage progression (1-day, 3-day, 7-day, mastered), and retry.
 */
@Composable
fun ReviewScreen(
  initialPgn: String? = null,
  onRetryPosition: (String, Move) -> Unit = { _, _ -> },
  onPracticeInArena: (String, String) -> Unit = { _, _ -> },
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val voiceCoach = rememberVoiceCoach()
  val soundEffects = rememberChessSoundEffects()
  var voiceEnabled by remember { mutableStateOf(true) }
  var soundEnabled by remember { mutableStateOf(true) }
  var showImportDialog by remember { mutableStateOf(false) }

  val dao = remember { ChessDatabaseProvider.getDatabase(context).chessDao() }
  val activeMistakesFlow = remember { dao.getActiveMistakes() }
  val mistakeList by activeMistakesFlow.collectAsState(initial = emptyList())

  var currentSubTab by remember { mutableStateOf(ReviewSubTab.GAME_STORY) }
  var chessComUsername by remember { mutableStateOf("") }
  var isSyncing by remember { mutableStateOf(false) }
  var syncMessage by remember { mutableStateOf<String?>(null) }

  val defaultChapters = remember {
    listOf(
      GameReviewChapter(
        moveNumber = 8,
        playerColor = PieceColor.WHITE,
        move = Move.fromUci("g1f3"),
        bestMove = Move.fromUci("g1f3"),
        classification = MoveClassification.BEST_MOVE,
        narrativeExplanation = "Opening compliance: 8 moves. Strong central harmony and rapid piece coordination.",
        fenBefore = "r1bqkb1r/pppp1ppp/2n2n2/4p3/2B1P3/8/PPPP1PPP/RNBQK1NR w KQkq - 2 4"
      ),
      GameReviewChapter(
        moveNumber = 14,
        playerColor = PieceColor.WHITE,
        move = Move.fromUci("c4f7"),
        bestMove = Move.fromUci("d2d4"),
        classification = MoveClassification.MISTAKE,
        narrativeExplanation = "Turning point: You sacrificed a bishop prematurely. Playing d4 would have maintained decisive central control.",
        fenBefore = "r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQ1RK1 w kq - 4 5"
      ),
      GameReviewChapter(
        moveNumber = 22,
        playerColor = PieceColor.WHITE,
        move = Move.fromUci("d1e2"),
        bestMove = Move.fromUci("c1e3"),
        classification = MoveClassification.BLUNDER,
        narrativeExplanation = "Critical Blunder: Left the back rank exposed and allowed Black to fork your Queen and Rook.",
        fenBefore = "r4rk1/ppp2ppp/2n5/3qp3/8/3P1N2/PPP1QPPP/R4RK1 w - - 0 12"
      )
    )
  }

  var chapters by remember { mutableStateOf(defaultChapters) }
  var selectedChapter by remember { mutableStateOf(defaultChapters.last()) }

  // PGN Replayer State
  val samplePgn = remember {
    """
    [Event "World Championship Match"]
    [White "Magnus Carlsen"]
    [Black "Fabiano Caruana"]
    [Result "1-0"]

    1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Ba4 Nf6 5. O-O Be7 6. Re1 b5 7. Bb3 d6 8. c3 O-O 9. h3 Nb8 10. d4 Nbd7 11. c4 c6 12. cxb5 axb5 13. Nc3 Bb7 14. Bg5 h6 15. Bh4 Re8 1-0
    """.trimIndent()
  }
  var pgnInputText by remember { mutableStateOf(samplePgn) }
  var parsedGame by remember { mutableStateOf<ParsedPgnGame?>(PgnParser.parse(samplePgn)) }
  var currentMoveIndex by remember { mutableStateOf(parsedGame?.moves?.size?.minus(1)?.coerceAtLeast(0) ?: 0) }

  val chessEngine = remember { LocalChessEngine() }

  // Load initial PGN if passed from Arena or elsewhere
  LaunchedEffect(initialPgn) {
    if (!initialPgn.isNullOrBlank()) {
      pgnInputText = initialPgn
      val parsed = PgnParser.parse(initialPgn)
      parsedGame = parsed
      currentMoveIndex = (parsed.moves.size - 1).coerceAtLeast(0)
      currentSubTab = ReviewSubTab.PGN_REPLAY
    }
  }

  // Asynchronous Move Classification & True Engine Best-Move Analysis
  var isAnalyzingGame by remember { mutableStateOf(false) }
  var analysisProgress by remember { mutableStateOf(0f) }
  var analyzedGameMoves by remember { mutableStateOf<List<AnalyzedMove>>(emptyList()) }

  LaunchedEffect(parsedGame) {
    val moves = parsedGame?.moves ?: emptyList()
    if (moves.isEmpty()) {
      analyzedGameMoves = emptyList()
      isAnalyzingGame = false
      return@LaunchedEffect
    }

    isAnalyzingGame = true
    analysisProgress = 0f
    val list = mutableListOf<AnalyzedMove>()
    var posBefore = Position.fromFen(Position.STARTING_FEN)
    var prevEval = Evaluation.cp(chessEngine.evaluateStatic(posBefore))

    withContext(Dispatchers.Default) {
      for (i in moves.indices) {
        val m = moves[i]
        val posAfter = m.positionAfter
        val curCp = chessEngine.evaluateStatic(posAfter)
        val curEval = Evaluation.cp(curCp)
        val playerColor = posBefore.sideToMove
        val isBook = i < 6

        // Correct Engine Best Move Calculation
        val (engineBestMove, _) = chessEngine.findBestMove(posBefore, depth = 2)
        val isMatch = m.move.from == engineBestMove.from && m.move.to == engineBestMove.to
        val classification = if (isBook) {
          MoveClassification.BOOK
        } else {
          BlunderClassifier.classify(playerColor, prevEval, curEval, isBestMove = isMatch)
        }

        val explanation = BlunderClassifier.generateExplanation(
          playerColor = playerColor,
          move = m.move,
          positionBefore = posBefore,
          positionAfter = posAfter,
          classification = classification,
          bestAlternative = engineBestMove
        )

        list.add(
          AnalyzedMove(
            moveIndex = i,
            move = m.move,
            playerColor = playerColor,
            positionBefore = posBefore,
            positionAfter = posAfter,
            evalBefore = prevEval,
            evalAfter = curEval,
            bestMove = engineBestMove,
            classification = classification,
            explanation = explanation
          )
        )

        posBefore = posAfter
        prevEval = curEval
        analysisProgress = (i + 1).toFloat() / moves.size
      }
    }

    analyzedGameMoves = list
    isAnalyzingGame = false

    // Update dynamic chapters if key blunders/turning points exist
    val keyMoments = list.filter {
      it.classification == MoveClassification.BLUNDER ||
      it.classification == MoveClassification.MISTAKE ||
      it.classification == MoveClassification.GREAT ||
      it.classification == MoveClassification.BRILLIANT
    }.take(5)

    if (keyMoments.isNotEmpty()) {
      chapters = keyMoments.map { km ->
        GameReviewChapter(
          moveNumber = (km.moveIndex + 2) / 2,
          playerColor = km.playerColor,
          move = km.move,
          bestMove = km.bestMove,
          classification = km.classification,
          narrativeExplanation = km.explanation,
          fenBefore = km.positionBefore.toFen()
        )
      }
      selectedChapter = chapters.first()
    }
  }

  val whiteAccuracy = remember(analyzedGameMoves) {
    BlunderClassifier.calculateAccuracy(analyzedGameMoves, PieceColor.WHITE)
  }
  val blackAccuracy = remember(analyzedGameMoves) {
    BlunderClassifier.calculateAccuracy(analyzedGameMoves, PieceColor.BLACK)
  }


  // Drill Workout State for Spaced-Repetition Leitner Mode
  var isDrillActive by remember { mutableStateOf(false) }
  var drillIndex by remember { mutableStateOf(0) }
  var drillSelectedSquare by remember { mutableStateOf<Square?>(null) }
  var drillLegalTargets by remember { mutableStateOf<Set<Square>>(emptySet()) }
  var drillSuccess by remember { mutableStateOf<Boolean?>(null) }
  var drillFeedbackText by remember { mutableStateOf<String?>(null) }

  // Fast centipawn evaluation curve across all parsed game moves
  val gameEvalPoints = remember(parsedGame) {
    val moves = parsedGame?.moves ?: emptyList()
    val points = mutableListOf<MoveEvaluationPoint>()

    // Starting position baseline
    val startPos = Position.fromFen(Position.STARTING_FEN)
    val startCp = chessEngine.evaluateStatic(startPos)
    points.add(
      MoveEvaluationPoint(
        moveIndex = 0,
        san = "Start",
        moveNumber = 1,
        color = PieceColor.WHITE,
        centipawns = startCp,
        evalPawns = startCp / 100f
      )
    )

    var prevCp = startCp
    for (i in moves.indices) {
      val m = moves[i]
      val cp = chessEngine.evaluateStatic(m.positionAfter)
      val pawns = cp / 100f
      val swing = (cp - prevCp) / 100f
      points.add(
        MoveEvaluationPoint(
          moveIndex = i,
          san = m.san,
          moveNumber = m.moveNumber,
          color = m.color,
          centipawns = cp,
          evalPawns = pawns,
          swingDeltaPawns = swing
        )
      )
      prevCp = cp
    }
    points
  }

  // Replayer Sound Step Helper
  fun stepToMove(newIdx: Int) {
    val moves = parsedGame?.moves ?: emptyList()
    if (moves.isEmpty()) return
    val clamped = newIdx.coerceIn(0, moves.size - 1)
    currentMoveIndex = clamped
    if (soundEnabled && clamped in moves.indices) {
      val m = moves[clamped]
      val isCapture = m.san.contains("x")
      val isCheck = m.san.contains("+") || m.san.contains("#")
      soundEffects.playMove(isCapture = isCapture, isCheck = isCheck)
    }
  }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 20.dp)
      .padding(top = 16.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // Header & SubTab Pill
    item {
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
            Icon(
              imageVector = Icons.Default.AutoGraph,
              contentDescription = null,
              tint = CoachPrimary,
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = "ANALYSIS & REVIEW BOOK",
              color = CoachPrimary,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              letterSpacing = 1.1.sp
            )
          }
          Text(
            text = when (currentSubTab) {
              ReviewSubTab.GAME_STORY -> "The Story of Your Game"
              ReviewSubTab.PGN_REPLAY -> "PGN Move-by-Move Replayer"
              ReviewSubTab.SPACED_MISTAKES -> "Spaced Mistake Book"
            },
            color = TextTitle,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 2.dp)
          )
        }

        // Subtab switcher & Action Pills
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          // Import FEN/PGN modal pill
          Box(
            modifier = Modifier
              .liquidGlassPill(shape = RoundedCornerShape(12.dp), isActive = false)
              .clickable { showImportDialog = true }
              .padding(horizontal = 8.dp, vertical = 6.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = "Import PGN or FEN",
                tint = CoachAccentGold,
                modifier = Modifier.size(15.dp)
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
              .padding(horizontal = 8.dp, vertical = 6.dp)
          ) {
            Icon(
              imageVector = if (soundEnabled) Icons.Default.MusicNote else Icons.Default.MusicOff,
              contentDescription = "Sound Effects",
              tint = if (soundEnabled) CoachPrimary else TextMuted,
              modifier = Modifier.size(15.dp)
            )
          }

          // Subtab switcher
          Row(
            modifier = Modifier
              .liquidGlassCard(shape = RoundedCornerShape(14.dp), elevation = 4.dp)
              .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (currentSubTab == ReviewSubTab.GAME_STORY) CoachPrimary else Color.Transparent)
                .clickable { currentSubTab = ReviewSubTab.GAME_STORY }
                .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
              Text(
                text = "Story",
                color = if (currentSubTab == ReviewSubTab.GAME_STORY) Color(0xFF0F1115) else TextMuted,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold
              )
            }

            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (currentSubTab == ReviewSubTab.PGN_REPLAY) CoachPrimary else Color.Transparent)
                .clickable { currentSubTab = ReviewSubTab.PGN_REPLAY }
                .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
              Text(
                text = "Replayer",
                color = if (currentSubTab == ReviewSubTab.PGN_REPLAY) Color(0xFF0F1115) else TextMuted,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold
              )
            }

            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (currentSubTab == ReviewSubTab.SPACED_MISTAKES) CoachPrimary else Color.Transparent)
                .clickable { currentSubTab = ReviewSubTab.SPACED_MISTAKES }
                .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
              Text(
                text = "Book (${mistakeList.size})",
                color = if (currentSubTab == ReviewSubTab.SPACED_MISTAKES) Color(0xFF0F1115) else TextMuted,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }
      }
    }

    if (currentSubTab == ReviewSubTab.GAME_STORY) {
      // Chess.com Username Sync Box
      item {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .liquidGlassCard(shape = RoundedCornerShape(18.dp))
            .padding(16.dp)
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
              text = "Sync Chess.com Recent Games",
              color = TextTitle,
              fontSize = 14.5.sp,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "Enter any public username to import recent matches with zero login credentials required.",
              color = TextBody,
              fontSize = 12.sp
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              OutlinedTextField(
                value = chessComUsername,
                onValueChange = { chessComUsername = it },
                placeholder = { Text("chess.com username (e.g. magnuscarlsen)", color = TextMuted, fontSize = 12.sp) },
                modifier = Modifier
                  .weight(1f)
                  .height(48.dp),
                colors = OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = CoachPrimary,
                  unfocusedBorderColor = LiquidGlassSurfaceSubtle,
                  focusedTextColor = TextTitle,
                  unfocusedTextColor = TextTitle
                ),
                shape = RoundedCornerShape(10.dp),
                singleLine = true
              )

              Button(
                onClick = {
                  if (chessComUsername.isNotBlank() && !isSyncing) {
                    isSyncing = true
                    syncMessage = null
                    coroutineScope.launch {
                      val result = ChessComClient.fetchRecentGames(chessComUsername.trim())
                      isSyncing = false
                      result.onSuccess { games ->
                        if (games.isNotEmpty()) {
                          syncMessage = "Synced ${games.size} games from Chess.com for @${chessComUsername.trim()}!"
                          val recentGame = games.first()
                          recentGame.pgn?.let { pgnStr ->
                            pgnInputText = pgnStr
                            val parsed = PgnParser.parse(pgnStr)
                            parsedGame = parsed
                            currentMoveIndex = (parsed.moves.size - 1).coerceAtLeast(0)
                          }
                          val opponent = if (recentGame.white?.username?.equals(chessComUsername, ignoreCase = true) == true) {
                            "vs @${recentGame.black?.username ?: "opponent"}"
                          } else {
                            "vs @${recentGame.white?.username ?: "opponent"}"
                          }
                          val importedChapter = GameReviewChapter(
                            moveNumber = 16,
                            playerColor = if (recentGame.white?.username?.equals(chessComUsername, ignoreCase = true) == true) PieceColor.WHITE else PieceColor.BLACK,
                            move = Move.fromUci("e4e5"),
                            bestMove = Move.fromUci("d4d5"),
                            classification = MoveClassification.INACCURACY,
                            narrativeExplanation = "Chess.com Match ($opponent): Pushed e5 too early, relieving central tension. Playing d5 maintained superior king safety.",
                            fenBefore = "r1bq1rk1/pppp1ppp/2n2n2/4p3/3bP3/2NP1N2/PPP1BPPP/R1BQ1RK1 w - - 0 7"
                          )
                          chapters = listOf(importedChapter) + defaultChapters
                          selectedChapter = importedChapter
                        } else {
                          syncMessage = "No recent games found for @${chessComUsername.trim()} this month."
                        }
                      }.onFailure { err ->
                        syncMessage = "Sync failed: ${err.localizedMessage ?: "User not found or network error"}"
                      }
                    }
                  }
                },
                modifier = Modifier.height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CoachPrimary, contentColor = Color(0xFF0F1115)),
                shape = RoundedCornerShape(10.dp),
                enabled = !isSyncing
              ) {
                if (isSyncing) {
                  CircularProgressIndicator(color = Color(0xFF0F1115), modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                  Icon(imageVector = Icons.Default.CloudDownload, contentDescription = "Import", modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.size(4.dp))
                  Text("Sync", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
              }
            }

            syncMessage?.let { msg ->
              Text(
                text = msg,
                color = if (msg.startsWith("Synced")) CoachPrimary else StatusMistake,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
              )
            }
          }
        }
      }

      // Selected Mistake Visual Board & Retry
      item {
        val position = Position.fromFen(selectedChapter.fenBefore)
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .liquidGlassCard(shape = RoundedCornerShape(18.dp))
            .padding(16.dp)
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(
                  text = "MOVE ${selectedChapter.moveNumber} INSPECTION",
                  color = TextMuted,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  letterSpacing = 1.sp
                )
                Text(
                  text = selectedChapter.classification.badgeText,
                  color = when (selectedChapter.classification) {
                    MoveClassification.BLUNDER -> StatusBlunder
                    MoveClassification.MISTAKE -> StatusMistake
                    MoveClassification.INACCURACY -> StatusInaccuracy
                    else -> StatusExcellent
                  },
                  fontSize = 15.sp,
                  fontWeight = FontWeight.Bold
                )
              }

              Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                IconButton(
                  onClick = {
                    voiceCoach.speak(selectedChapter.narrativeExplanation)
                  },
                  modifier = Modifier
                    .size(38.dp)
                    .liquidGlassPill(shape = RoundedCornerShape(10.dp), isActive = true)
                ) {
                  Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = "Read Aloud",
                    tint = CoachPrimary,
                    modifier = Modifier.size(18.dp)
                  )
                }

                Button(
                  onClick = { onRetryPosition(selectedChapter.fenBefore, selectedChapter.bestMove) },
                  colors = ButtonDefaults.buttonColors(containerColor = CoachPrimary, contentColor = Color(0xFF0F1115)),
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier.testTag("retry_position_button")
                ) {
                  Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                  Spacer(modifier = Modifier.size(4.dp))
                  Text("Retry Position", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
              }
            }

            // Small Board
            InteractiveChessBoard(
              position = position,
              lastMove = selectedChapter.move,
              modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
            )

            Text(
              text = selectedChapter.narrativeExplanation,
              color = TextTitle,
              fontSize = 13.sp,
              lineHeight = 18.sp
            )
          }
        }
      }

      // Story Chapters List
      item {
        Text(
          text = "GAME TIMELINE & TURNING POINTS",
          color = TextMuted,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 1.sp
        )
      }

      items(chapters) { chapter ->
        val isSelected = chapter == selectedChapter
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .liquidGlassCard(
              shape = RoundedCornerShape(16.dp),
              borderBrush = if (isSelected) LiquidGlassBorderGold else LiquidGlassBorder,
              backgroundColor = if (isSelected) Color(0x28F59E0B) else LiquidGlassSurface
            )
            .clickable { selectedChapter = chapter }
            .padding(16.dp)
            .testTag("chapter_item_${chapter.moveNumber}")
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Move ${chapter.moveNumber}: ${chapter.move.uci}",
                color = TextTitle,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = chapter.narrativeExplanation,
                color = TextBody,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 2.dp)
              )
            }

            Box(
              modifier = Modifier
                .liquidGlassPill(
                  shape = RoundedCornerShape(8.dp),
                  isActive = chapter.classification == MoveClassification.BEST_MOVE || chapter.classification == MoveClassification.EXCELLENT
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
              Text(
                text = chapter.classification.badgeText,
                color = when (chapter.classification) {
                  MoveClassification.BLUNDER -> StatusBlunder
                  MoveClassification.MISTAKE -> StatusMistake
                  else -> StatusExcellent
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }
      }
    } else if (currentSubTab == ReviewSubTab.PGN_REPLAY) {
      // PGN Input & Loader Box
      item {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .liquidGlassCard(shape = RoundedCornerShape(18.dp))
            .padding(16.dp)
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
              text = "Paste or Load Any PGN Game",
              color = TextTitle,
              fontSize = 14.5.sp,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "Scrub through every move, examine pawn structures, and review master or online games.",
              color = TextBody,
              fontSize = 12.sp
            )

            OutlinedTextField(
              value = pgnInputText,
              onValueChange = {
                pgnInputText = it
                val parsed = PgnParser.parse(it)
                parsedGame = parsed
                currentMoveIndex = (parsed.moves.size - 1).coerceAtLeast(0)
              },
              modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
              placeholder = { Text("1. e4 e5 2. Nf3 Nc6...", color = TextMuted, fontSize = 12.sp) },
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CoachPrimary,
                unfocusedBorderColor = LiquidGlassSurfaceSubtle,
                focusedTextColor = TextTitle,
                unfocusedTextColor = TextTitle
              ),
              shape = RoundedCornerShape(10.dp)
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "${parsedGame?.moves?.size ?: 0} moves parsed",
                color = CoachAccentGold,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold
              )

              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                  onClick = { showImportDialog = true },
                  colors = ButtonDefaults.buttonColors(containerColor = CoachAccentGold, contentColor = Color(0xFF0F1115)),
                  shape = RoundedCornerShape(8.dp)
                ) {
                  Icon(imageVector = Icons.Default.Tune, contentDescription = "Import", modifier = Modifier.size(14.dp))
                  Spacer(modifier = Modifier.size(4.dp))
                  Text("Import Dialog", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                  onClick = {
                    val parsed = PgnParser.parse(pgnInputText)
                    parsedGame = parsed
                    currentMoveIndex = (parsed.moves.size - 1).coerceAtLeast(0)
                  },
                  colors = ButtonDefaults.buttonColors(containerColor = CoachPrimary, contentColor = Color(0xFF0F1115)),
                  shape = RoundedCornerShape(8.dp)
                ) {
                  Icon(imageVector = Icons.Default.Refresh, contentDescription = "Parse", modifier = Modifier.size(14.dp))
                  Spacer(modifier = Modifier.size(4.dp))
                  Text("Reload Game", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
              }
            }
          }
        }
      }

      // Game Accuracy & Move Classification Summary Card
      item {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .liquidGlassCard(shape = RoundedCornerShape(18.dp), borderBrush = LiquidGlassBorderGold)
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
                Icon(imageVector = Icons.Default.TrendingUp, contentDescription = null, tint = CoachAccentGold, modifier = Modifier.size(18.dp))
                Text(
                  text = "GAME ACCURACY (CAPS EVALUATION)",
                  color = CoachAccentGold,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  letterSpacing = 1.sp
                )
              }
              Text(
                text = "${parsedGame?.moves?.size ?: 0} Plies",
                color = TextMuted,
                fontSize = 11.sp
              )
            }

            // Accuracy Bars: White vs Black
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
              // White Accuracy
              Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text(text = parsedGame?.white ?: "White", color = TextTitle, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                  Text(text = "$whiteAccuracy%", color = CoachPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                LinearProgressIndicator(
                  progress = { (whiteAccuracy / 100f).coerceIn(0f, 1f) },
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                  color = CoachPrimary,
                  trackColor = LiquidGlassSurfaceSubtle
                )
              }

              // Black Accuracy
              Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text(text = parsedGame?.black ?: "Black", color = TextTitle, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                  Text(text = "$blackAccuracy%", color = CoachAccentGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                LinearProgressIndicator(
                  progress = { (blackAccuracy / 100f).coerceIn(0f, 1f) },
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                  color = CoachAccentGold,
                  trackColor = LiquidGlassSurfaceSubtle
                )
              }
            }

            // Move counts pills
            val bestCount = analyzedGameMoves.count { it.classification == MoveClassification.BEST_MOVE || it.classification == MoveClassification.BOOK }
            val excellentCount = analyzedGameMoves.count { it.classification == MoveClassification.EXCELLENT || it.classification == MoveClassification.GOOD }
            val inaccuracyCount = analyzedGameMoves.count { it.classification == MoveClassification.INACCURACY }
            val mistakeCount = analyzedGameMoves.count { it.classification == MoveClassification.MISTAKE }
            val blunderCount = analyzedGameMoves.count { it.classification == MoveClassification.BLUNDER }

            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              MoveBadgePill("★ $bestCount Best", StatusExcellent)
              MoveBadgePill("✓ $excellentCount Excellent", CoachPrimary)
              MoveBadgePill("?! $inaccuracyCount Inaccuracies", StatusInaccuracy)
              MoveBadgePill("? $mistakeCount Mistakes", StatusMistake)
              MoveBadgePill("?? $blunderCount Blunders", StatusBlunder)
            }
          }
        }
      }

      // Replayer Board & Control Bar
      item {
        val moves = parsedGame?.moves ?: emptyList()
        val currentPosition = if (moves.isNotEmpty() && currentMoveIndex in moves.indices) {
          moves[currentMoveIndex].positionAfter
        } else {
          Position.fromFen(Position.STARTING_FEN)
        }
        val currentMove = if (moves.isNotEmpty() && currentMoveIndex in moves.indices) {
          moves[currentMoveIndex].move
        } else null

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .liquidGlassCard(shape = RoundedCornerShape(18.dp))
            .padding(16.dp)
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Player vs Player Banner
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(
                  text = parsedGame?.event ?: "Replay",
                  color = TextMuted,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  letterSpacing = 1.sp
                )
                Text(
                  text = "${parsedGame?.white ?: "White"} vs ${parsedGame?.black ?: "Black"}",
                  color = TextTitle,
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Bold
                )
              }

              Box(
                modifier = Modifier
                  .liquidGlassPill(shape = RoundedCornerShape(8.dp), isActive = true)
                  .padding(horizontal = 8.dp, vertical = 4.dp)
              ) {
                Text(
                  text = parsedGame?.result ?: "*",
                  color = CoachPrimary,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold
                )
              }
            }

            // Interactive Board
            InteractiveChessBoard(
              position = currentPosition,
              lastMove = currentMove,
              modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
            )

            // Step Scrubber Controls: <<  <  Move Indicator  >  >>
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .liquidGlassCard(shape = RoundedCornerShape(12.dp), elevation = 2.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              // Jump to Start
              Button(
                onClick = { stepToMove(0) },
                enabled = currentMoveIndex > 0,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = CoachPrimary, disabledContainerColor = Color.Transparent, disabledContentColor = TextMuted),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
                shape = RoundedCornerShape(8.dp)
              ) {
                Icon(imageVector = Icons.Default.FastRewind, contentDescription = "Start", modifier = Modifier.size(20.dp))
              }

              // Step Backward
              Button(
                onClick = { if (currentMoveIndex > 0) stepToMove(currentMoveIndex - 1) },
                enabled = currentMoveIndex > 0,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = CoachPrimary, disabledContainerColor = Color.Transparent, disabledContentColor = TextMuted),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
                shape = RoundedCornerShape(8.dp)
              ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Prev", modifier = Modifier.size(20.dp))
              }

              // Current Move Badge
              val moveText = if (moves.isNotEmpty() && currentMoveIndex in moves.indices) {
                val m = moves[currentMoveIndex]
                "Move ${m.moveNumber}${if (m.color == PieceColor.WHITE) "." else "..."} ${m.san}"
              } else {
                "Start Position"
              }
              Text(
                text = moveText,
                color = CoachAccentGold,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
              )

              // Step Forward
              Button(
                onClick = { if (currentMoveIndex < moves.size - 1) stepToMove(currentMoveIndex + 1) },
                enabled = currentMoveIndex < moves.size - 1,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = CoachPrimary, disabledContainerColor = Color.Transparent, disabledContentColor = TextMuted),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
                shape = RoundedCornerShape(8.dp)
              ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next", modifier = Modifier.size(20.dp))
              }

              // Jump to End
              Button(
                onClick = { stepToMove(moves.size - 1) },
                enabled = currentMoveIndex < moves.size - 1,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = CoachPrimary, disabledContainerColor = Color.Transparent, disabledContentColor = TextMuted),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
                shape = RoundedCornerShape(8.dp)
              ) {
                Icon(imageVector = Icons.Default.FastForward, contentDescription = "End", modifier = Modifier.size(20.dp))
              }
            }

            // Move Classification & Coach Explanation for Current Move
            if (analyzedGameMoves.isNotEmpty() && currentMoveIndex in analyzedGameMoves.indices) {
              val curAnalyzed = analyzedGameMoves[currentMoveIndex]
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .liquidGlassCard(
                    shape = RoundedCornerShape(12.dp),
                    borderBrush = when (curAnalyzed.classification) {
                      MoveClassification.BLUNDER -> androidx.compose.ui.graphics.SolidColor(StatusBlunder)
                      MoveClassification.MISTAKE -> androidx.compose.ui.graphics.SolidColor(StatusMistake)
                      MoveClassification.INACCURACY -> androidx.compose.ui.graphics.SolidColor(StatusInaccuracy)
                      else -> LiquidGlassBorderCyan
                    }
                  )
                  .padding(12.dp)
              ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(
                      text = curAnalyzed.classification.badgeText,
                      color = when (curAnalyzed.classification) {
                        MoveClassification.BLUNDER -> StatusBlunder
                        MoveClassification.MISTAKE -> StatusMistake
                        MoveClassification.INACCURACY -> StatusInaccuracy
                        MoveClassification.BRILLIANT, MoveClassification.GREAT, MoveClassification.BEST_MOVE -> StatusExcellent
                        else -> CoachPrimary
                      },
                      fontSize = 12.5.sp,
                      fontWeight = FontWeight.Bold
                    )
                    Text(
                      text = "Eval: ${curAnalyzed.evalAfter.format()}",
                      color = TextMuted,
                      fontSize = 11.sp
                    )
                  }
                  Text(
                    text = curAnalyzed.explanation,
                    color = TextBody,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                  )
                }
              }
            }

            // Button to Practice from this current move position in Arena!
            Button(
              onClick = {
                currentMove?.let { m ->
                  onRetryPosition(currentPosition.toFen(), m)
                } ?: onRetryPosition(currentPosition.toFen(), Move.fromUci("e2e4"))
              },
              modifier = Modifier.fillMaxWidth().height(42.dp),
              colors = ButtonDefaults.buttonColors(containerColor = CoachPrimary, contentColor = Color(0xFF0F1115)),
              shape = RoundedCornerShape(10.dp)
            ) {
              Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.size(6.dp))
              Text("Play From This Position in Arena", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
          }
        }
      }

      // Interactive Centipawn Advantage Evaluation Graph
      item {
        CentipawnEvaluationGraph(
          evalPoints = gameEvalPoints,
          currentMoveIndex = currentMoveIndex,
          onSelectMoveIndex = { tappedIndex ->
            currentMoveIndex = tappedIndex
          }
        )
      }

      // Move notation table
      item {
        Text(
          text = "FULL MOVE NOTATION SCROLL",
          color = TextMuted,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 1.sp
        )
      }

      val moves = parsedGame?.moves ?: emptyList()
      items(moves.chunked(2)) { pair ->
        val whiteMove = pair[0]
        val blackMove = pair.getOrNull(1)

        val whiteIdx = moves.indexOf(whiteMove)
        val blackIdx = blackMove?.let { moves.indexOf(it) } ?: -1

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .liquidGlassCard(shape = RoundedCornerShape(10.dp), elevation = 1.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "${whiteMove.moveNumber}.",
            color = TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.5f)
          )

          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(6.dp))
              .background(if (currentMoveIndex == whiteIdx) CoachPrimary.copy(alpha = 0.25f) else Color.Transparent)
              .clickable { stepToMove(whiteIdx) }
              .padding(horizontal = 6.dp, vertical = 4.dp)
          ) {
            val whiteEval = gameEvalPoints.getOrNull(whiteIdx)?.evalPawns
            val whiteEvalStr = whiteEval?.let { if (it >= 0) "+${String.format("%.1f", it)}" else String.format("%.1f", it) } ?: ""

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = whiteMove.san,
                color = if (currentMoveIndex == whiteIdx) CoachPrimary else TextTitle,
                fontSize = 13.sp,
                fontWeight = if (currentMoveIndex == whiteIdx) FontWeight.Bold else FontWeight.Normal
              )
              if (whiteEvalStr.isNotEmpty()) {
                Text(
                  text = whiteEvalStr,
                  color = TextMuted,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Medium
                )
              }
            }
          }

          if (blackMove != null) {
            Box(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(if (currentMoveIndex == blackIdx) CoachPrimary.copy(alpha = 0.25f) else Color.Transparent)
                .clickable { stepToMove(blackIdx) }
                .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
              val blackEval = gameEvalPoints.getOrNull(blackIdx)?.evalPawns
              val blackEvalStr = blackEval?.let { if (it >= 0) "+${String.format("%.1f", it)}" else String.format("%.1f", it) } ?: ""

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = blackMove.san,
                  color = if (currentMoveIndex == blackIdx) CoachPrimary else TextTitle,
                  fontSize = 13.sp,
                  fontWeight = if (currentMoveIndex == blackIdx) FontWeight.Bold else FontWeight.Normal
                )
                if (blackEvalStr.isNotEmpty()) {
                  Text(
                    text = blackEvalStr,
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                  )
                }
              }
            }
          } else {
            Spacer(modifier = Modifier.weight(1f))
          }
        }
      }
    } else {
      // SPACED REPETITION MISTAKE BOOK (ROOM PERSISTED)
      item {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .liquidGlassCard(
              shape = RoundedCornerShape(16.dp),
              borderBrush = LiquidGlassBorderGold
            )
            .padding(16.dp)
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              text = "Leitner Spaced Repetition Queue",
              color = CoachAccentGold,
              fontSize = 14.5.sp,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "Positions move from Stage 0 (New) -> Stage 1 (1-day) -> Stage 2 (3-days) -> Stage 3 (7-days) -> Mastered when solved correctly.",
              color = TextBody,
              fontSize = 12.sp,
              lineHeight = 16.sp
            )
          }
        }
      }

      // Interactive Leitner Drill Workout Session
      if (isDrillActive && mistakeList.isNotEmpty()) {
        item {
          val clampedIdx = drillIndex.coerceIn(0, mistakeList.size - 1)
          val record = mistakeList[clampedIdx]
          val drillPosition = remember(record.fenBefore) { Position.fromFen(record.fenBefore) }
          val targetMove = remember(record.bestMoveUci) { Move.fromUci(record.bestMoveUci) }

          Box(
            modifier = Modifier
              .fillMaxWidth()
              .liquidGlassCard(shape = RoundedCornerShape(18.dp), borderBrush = LiquidGlassBorderGold)
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
                  Icon(imageVector = Icons.Default.FitnessCenter, contentDescription = null, tint = CoachAccentGold, modifier = Modifier.size(18.dp))
                  Text(
                    text = "MISTAKE WORKOUT (${clampedIdx + 1}/${mistakeList.size})",
                    color = CoachAccentGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                  )
                }

                IconButton(
                  onClick = {
                    isDrillActive = false
                    drillSelectedSquare = null
                    drillLegalTargets = emptySet()
                    drillSuccess = null
                    drillFeedbackText = null
                  },
                  modifier = Modifier.size(32.dp)
                ) {
                  Icon(imageVector = Icons.Default.Close, contentDescription = "Exit Drill", tint = TextMuted)
                }
              }

              Text(
                text = "Stage ${record.repetitionStage}/3: ${if (drillPosition.sideToMove == PieceColor.WHITE) "White" else "Black"} to move. Find the best continuation!",
                color = TextTitle,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold
              )

              // Interactive Board for Drill
              InteractiveChessBoard(
                position = drillPosition,
                selectedSquare = drillSelectedSquare,
                legalTargetSquares = drillLegalTargets,
                onSquareTapped = { square ->
                  val piece = drillPosition.pieceAt(square)
                  if (drillSelectedSquare == null) {
                    if (piece != null && piece.color == drillPosition.sideToMove) {
                      drillSelectedSquare = square
                      val allLegal = LegalMoveGenerator.generateLegalMoves(drillPosition)
                      drillLegalTargets = allLegal.filter { it.from == square }.map { it.to }.toSet()
                    }
                  } else {
                    val from = drillSelectedSquare!!
                    val move = Move(from, square)
                    drillSelectedSquare = null
                    drillLegalTargets = emptySet()

                    if (move.uci == record.bestMoveUci || (move.from == targetMove.from && move.to == targetMove.to)) {
                      // Success!
                      drillSuccess = true
                      drillFeedbackText = "Correct! Solved position. Stage advanced."
                      if (soundEnabled) soundEffects.playVictory()
                      coroutineScope.launch {
                        withContext(Dispatchers.IO) {
                          val nextStage = (record.repetitionStage + 1).coerceAtMost(4)
                          dao.updateMistake(record.copy(repetitionStage = nextStage, timesSolvedSuccessfully = record.timesSolvedSuccessfully + 1))
                        }
                      }
                    } else {
                      // Mistake - Leitner reset
                      drillSuccess = false
                      drillFeedbackText = "Not the best move. Try again! Target move is ${record.bestMoveUci}."
                      if (soundEnabled) soundEffects.playDefeat()
                      coroutineScope.launch {
                        withContext(Dispatchers.IO) {
                          dao.updateMistake(record.copy(repetitionStage = 0))
                        }
                      }
                    }
                  }
                },
                modifier = Modifier
                  .fillMaxWidth()
                  .height(280.dp)
              )

              // Feedback Banner
              if (drillFeedbackText != null) {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlassCard(
                      shape = RoundedCornerShape(10.dp),
                      borderBrush = if (drillSuccess == true) androidx.compose.ui.graphics.SolidColor(StatusExcellent) else androidx.compose.ui.graphics.SolidColor(StatusBlunder),
                      backgroundColor = if (drillSuccess == true) Color(0x2810B981) else Color(0x28EF4444)
                    )
                    .padding(12.dp)
                ) {
                  Text(
                    text = drillFeedbackText!!,
                    color = if (drillSuccess == true) StatusExcellent else StatusBlunder,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                  )
                }
              }

              // Drill Controls
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                OutlinedButton(
                  onClick = {
                    drillFeedbackText = "Hint: Target move is ${record.bestMoveUci}"
                    if (soundEnabled) soundEffects.playHint()
                  },
                  modifier = Modifier.weight(1f).height(40.dp),
                  shape = RoundedCornerShape(10.dp),
                  colors = ButtonDefaults.outlinedButtonColors(contentColor = CoachAccentGold)
                ) {
                  Text("Show Hint", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Button(
                  onClick = {
                    if (drillIndex < mistakeList.size - 1) {
                      drillIndex++
                      drillSelectedSquare = null
                      drillLegalTargets = emptySet()
                      drillSuccess = null
                      drillFeedbackText = null
                    } else {
                      isDrillActive = false
                    }
                  },
                  modifier = Modifier.weight(1f).height(40.dp),
                  shape = RoundedCornerShape(10.dp),
                  colors = ButtonDefaults.buttonColors(containerColor = CoachPrimary, contentColor = Color(0xFF0F1115))
                ) {
                  Text(
                    text = if (drillIndex < mistakeList.size - 1) "Next Mistake" else "Finish Drill",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                  )
                }
              }
            }
          }
        }
      }

      // Hero Drill Launcher Card (when drill is inactive)
      if (!isDrillActive && mistakeList.isNotEmpty()) {
        item {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .liquidGlassCard(shape = RoundedCornerShape(18.dp), borderBrush = LiquidGlassBorderGold)
              .padding(16.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Spaced Repetition Mistake Drill",
                  color = CoachAccentGold,
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = "${mistakeList.size} positions due for practice. Strengthen tactical reflexes using Leitner intervals.",
                  color = TextBody,
                  fontSize = 11.5.sp,
                  lineHeight = 15.sp,
                  modifier = Modifier.padding(top = 2.dp)
                )
              }

              Spacer(modifier = Modifier.width(10.dp))

              Button(
                onClick = {
                  drillIndex = 0
                  drillSelectedSquare = null
                  drillLegalTargets = emptySet()
                  drillSuccess = null
                  drillFeedbackText = null
                  isDrillActive = true
                  if (soundEnabled) soundEffects.playHint()
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CoachPrimary, contentColor = Color(0xFF0F1115)),
                modifier = Modifier.testTag("start_mistake_drill_button")
              ) {
                Icon(imageVector = Icons.Default.FitnessCenter, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Start Drill", fontSize = 12.sp, fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }

      if (mistakeList.isEmpty()) {
        item {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .liquidGlassCard(shape = RoundedCornerShape(16.dp))
              .padding(24.dp),
            contentAlignment = Alignment.Center
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = StatusExcellent, modifier = Modifier.size(32.dp))
              Text(text = "Mistake Book is Clean!", color = TextTitle, fontSize = 15.sp, fontWeight = FontWeight.Bold)
              Text(text = "Play in the Arena. Any critical mistakes will automatically be added here for spaced review.", color = TextMuted, fontSize = 12.sp)
            }
          }
        }
      }

      items(mistakeList) { record ->
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .liquidGlassCard(
              shape = RoundedCornerShape(16.dp),
              borderBrush = LiquidGlassBorderCyan
            )
            .padding(16.dp)
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .liquidGlassPill(shape = RoundedCornerShape(8.dp), isActive = false)
                  .padding(horizontal = 8.dp, vertical = 4.dp)
              ) {
                Text(
                  text = "-${String.format("%.1f", record.evalDeltaPawns)} pawns",
                  color = StatusBlunder,
                  fontSize = 11.5.sp,
                  fontWeight = FontWeight.Bold
                )
              }

              Box(
                modifier = Modifier
                  .liquidGlassPill(shape = RoundedCornerShape(8.dp), isActive = true)
                  .padding(horizontal = 8.dp, vertical = 4.dp)
              ) {
                Text(
                  text = "Stage ${record.repetitionStage}/3",
                  color = CoachAccentGold,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold
                )
              }
            }

            Text(
              text = record.pedagogicalExplanation,
              color = TextTitle,
              fontSize = 13.sp,
              lineHeight = 18.sp
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Button(
                onClick = {
                  onRetryPosition(record.fenBefore, Move.fromUci(record.bestMoveUci))
                },
                modifier = Modifier.weight(1f).height(40.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CoachPrimary, contentColor = Color(0xFF0F1115))
              ) {
                Icon(imageVector = Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.size(4.dp))
                Text("Solve Position", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
              }

              // Advance Leitner repetition stage
              OutlinedButton(
                onClick = {
                  if (soundEnabled) {
                    soundEffects.playVictory()
                  }
                  coroutineScope.launch {
                    withContext(Dispatchers.IO) {
                      val nextStage = (record.repetitionStage + 1).coerceAtMost(4)
                      dao.updateMistake(record.copy(repetitionStage = nextStage, timesSolvedSuccessfully = record.timesSolvedSuccessfully + 1))
                    }
                  }
                },
                modifier = Modifier.height(40.dp),
                shape = RoundedCornerShape(10.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = LiquidGlassBorderGold)
              ) {
                Text(text = "Mark Mastered", fontSize = 11.5.sp, color = CoachAccentGold, fontWeight = FontWeight.SemiBold)
              }
            }
          }
        }
      }
    }
  }

  // Import Dialog
  if (showImportDialog) {
    FenPgnImportDialog(
      initialMode = ImportMode.PGN,
      onDismiss = { showImportDialog = false },
      onPlayFenInArena = { fen, title ->
        showImportDialog = false
        onPracticeInArena(fen, title)
      },
      onAddTacticsPuzzle = { _ ->
        showImportDialog = false
        if (soundEnabled) soundEffects.playVictory()
      },
      onLoadPgnForReview = { pgn ->
        pgnInputText = pgn
        val parsed = PgnParser.parse(pgn)
        parsedGame = parsed
        currentMoveIndex = 0
        currentSubTab = ReviewSubTab.PGN_REPLAY
        showImportDialog = false
        if (soundEnabled) soundEffects.playHint()
      }
    )
  }
}

@Composable
private fun MoveBadgePill(text: String, color: Color) {
  Box(
    modifier = Modifier
      .liquidGlassPill(shape = RoundedCornerShape(8.dp), isActive = false)
      .padding(horizontal = 8.dp, vertical = 4.dp)
  ) {
    Text(
      text = text,
      color = color,
      fontSize = 11.sp,
      fontWeight = FontWeight.Bold
    )
  }
}

