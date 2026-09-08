package com.example.chess.ui.components

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.chess.analysis.ParsedPgnGame
import com.example.chess.analysis.PgnParser
import com.example.chess.core.PieceColor
import com.example.chess.core.Position
import com.example.chess.tactics.TacticalPuzzle
import com.example.chess.tactics.TacticalTheme
import com.example.chess.tactics.TacticsRepository
import com.example.chess.ui.theme.CanvasBackground
import com.example.chess.ui.theme.CanvasCardBorder
import com.example.chess.ui.theme.CoachAccentGold
import com.example.chess.ui.theme.CoachPrimary
import com.example.chess.ui.theme.LiquidGlassSurface
import com.example.chess.ui.theme.LiquidGlassSurfaceSubtle
import com.example.chess.ui.theme.StatusBlunder
import com.example.chess.ui.theme.StatusExcellent
import com.example.chess.ui.theme.TextBody
import com.example.chess.ui.theme.TextMuted
import com.example.chess.ui.theme.TextTitle

enum class ImportMode {
  FEN,
  PGN
}

data class FenPreset(
  val label: String,
  val fen: String,
  val description: String
)

data class PgnPreset(
  val label: String,
  val pgn: String,
  val description: String
)

private val FEN_PRESETS = listOf(
  FenPreset(
    label = "Standard Start",
    fen = Position.STARTING_FEN,
    description = "Initial standard chess board configuration"
  ),
  FenPreset(
    label = "Knight Fork Attack",
    fen = "r1bqkb1r/pppp1ppp/2n5/4p3/2B1n3/5N2/PPPP1PPP/RNBQK2R w KQkq - 0 4",
    description = "White to exploit the vulnerable f7 square"
  ),
  FenPreset(
    label = "Opera Decoy Mate",
    fen = "4r1k1/5ppp/8/8/8/8/5PPP/4R1K1 w - - 0 1",
    description = "Undefended back-rank tactical deflection"
  ),
  FenPreset(
    label = "Lucena Rook Bridge",
    fen = "1K6/1P1k4/8/8/8/8/7r/2R5 w - - 0 1",
    description = "Building the decisive rook bridge to promote"
  ),
  FenPreset(
    label = "Sicilian Open Fight",
    fen = "r1bqkbnr/pp1ppppp/2n5/8/3NP3/8/PPP2PPP/RNBQKB1R b KQkq - 2 4",
    description = "Dynamic open Sicilian center with sharp counterplay"
  )
)

private val PGN_PRESETS = listOf(
  PgnPreset(
    label = "Morphy Opera (1858)",
    pgn = """[Event "Paris Opera"]
[Site "Paris FRA"]
[Date "1858.11.02"]
[White "Paul Morphy"]
[Black "Duke Karl / Count Isouard"]
[Result "1-0"]

1. e4 e5 2. Nf3 d6 3. d4 Bg4 4. dxe5 Bxf3 5. Qxf3 dxe5 6. Bc4 Nf6 7. Qb3 Qe7 8. Nc3 c6 9. Bg5 b5 10. Nxb5 cxb5 11. Bxb5+ Nbd7 12. O-O-O Rd8 13. Rxd7 Rxd7 14. Rd1 Qe6 15. Bxd7+ Nxd7 16. Qb8+ Nxb8 17. Rd8# 1-0""",
    description = "Paul Morphy's world-renowned masterpiece of development and Queen sacrifice"
  ),
  PgnPreset(
    label = "Kasparov Immortal (1999)",
    pgn = """[Event "Hoogovens Wijk aan Zee"]
[Site "Wijk aan Zee NED"]
[Date "1999.01.20"]
[White "Garry Kasparov"]
[Black "Veselin Topalov"]
[Result "1-0"]

1. e4 d6 2. d4 Nf6 3. Nc3 g6 4. Be3 Bg7 5. Qd2 c6 6. f3 b5 7. Nge2 Nbd7 8. Bh6 Bxh6 9. Qxh6 Bb7 10. a3 e5 11. O-O-O Qe7 12. Kb1 a6 13. Nc1 O-O-O 14. Nb3 exd4 15. Rxd4 c5 16. Rd1 Nb6 17. g3 Kb8 18. Na5 Ba8 19. Bh3 d5 20. Qf4+ Ka7 21. Rhe1 d4 22. Nd5 Nbxd5 23. exd5 Qd6 24. Rxd4 cxd4 25. Re7+ Kb6 26. Qxd4+ Kxa5 27. b4+ Ka4 28. Qc3 Qxd5 29. Ra7 Bb7 30. Rxb7 Qc4 31. Qxf6 Kxa3 32. Qxa6+ Kxb4 33. c3+ Kxc3 34. Qa1+ Kd2 35. Qb2+ Kd1 36. Bf1 Rd2 37. Rd7 Rxd7 38. Bxc4 bxc4 39. Qxh8 Rd3 40. Qa8 c3 41. Qa4+ Ke1 42. f4 f5 43. Kc1 Rd2 44. Qa7 1-0""",
    description = "Kasparov's immortal rook sacrifice on e7 chasing the Black King across the board"
  )
)

/**
 * Modern Liquid Glass Modal Dialog for importing custom FEN positions or PGN games.
 * Supports:
 * - Real-time FEN syntax and legality parsing with active-side indicator
 * - Instant clipboard pasting
 * - 1-tap famous presets
 * - Scrubbing PGN games to launch sparring from any move
 * - Creating custom Tactics Dojo puzzles directly from FEN
 */
@Composable
fun FenPgnImportDialog(
  initialMode: ImportMode = ImportMode.FEN,
  onDismiss: () -> Unit,
  onPlayFenInArena: (fen: String, title: String) -> Unit,
  onAddTacticsPuzzle: ((puzzle: TacticalPuzzle) -> Unit)? = null,
  onLoadPgnForReview: ((pgn: String) -> Unit)? = null
) {
  val context = LocalContext.current
  var currentMode by remember { mutableStateOf(initialMode) }

  // FEN State
  var fenText by remember { mutableStateOf(Position.STARTING_FEN) }
  val parsedFenResult = remember(fenText) { Position.tryFromFen(fenText) }

  // Custom Puzzle Creator Toggle
  var isCreatingPuzzle by remember { mutableStateOf(false) }
  var puzzleTitle by remember { mutableStateOf("Custom Tactical Challenge") }
  var puzzleTheme by remember { mutableStateOf(TacticalTheme.FORK) }
  var puzzleSolutionMove by remember { mutableStateOf("") }
  var puzzleRatingText by remember { mutableStateOf("1300") }
  var isThemeDropdownExpanded by remember { mutableStateOf(false) }

  // PGN State
  var pgnText by remember { mutableStateOf(PGN_PRESETS[0].pgn) }
  val parsedPgnGame = remember(pgnText) { runCatching { PgnParser.parse(pgnText) }.getOrNull() }
  var selectedPgnPly by remember(parsedPgnGame) {
    mutableIntStateOf(parsedPgnGame?.moves?.lastIndex?.coerceAtLeast(0) ?: 0)
  }

  fun getClipboardText(): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = clipboard?.primaryClip
    if (clip != null && clip.itemCount > 0 && clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true) {
      return clip.getItemAt(0)?.text?.toString()
    }
    return null
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth(0.94f)
        .padding(vertical = 24.dp),
      shape = RoundedCornerShape(22.dp),
      color = CanvasBackground.copy(alpha = 0.95f),
      border = androidx.compose.foundation.BorderStroke(1.dp, CanvasCardBorder)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // Header
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
                .size(36.dp)
                .background(CoachPrimary.copy(alpha = 0.15f), CircleShape),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = null,
                tint = CoachPrimary,
                modifier = Modifier.size(20.dp)
              )
            }
            Column {
              Text(
                text = "IMPORT POSITION / GAME",
                color = CoachAccentGold,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
              )
              Text(
                text = if (currentMode == ImportMode.FEN) "Custom FEN Setup" else "PGN Game Library",
                color = TextTitle,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }

          IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(32.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Close",
              tint = TextMuted,
              modifier = Modifier.size(18.dp)
            )
          }
        }

        // Segmented Tabs: FEN vs PGN
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(LiquidGlassSurface)
            .border(1.dp, LiquidGlassSurfaceSubtle, RoundedCornerShape(12.dp))
            .padding(4.dp),
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(9.dp))
              .background(if (currentMode == ImportMode.FEN) CoachPrimary else Color.Transparent)
              .clickable { currentMode = ImportMode.FEN }
              .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "FEN Notation",
              color = if (currentMode == ImportMode.FEN) Color(0xFF0F1115) else TextMuted,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold
            )
          }

          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(9.dp))
              .background(if (currentMode == ImportMode.PGN) CoachPrimary else Color.Transparent)
              .clickable { currentMode = ImportMode.PGN }
              .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "PGN Game",
              color = if (currentMode == ImportMode.PGN) Color(0xFF0F1115) else TextMuted,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }

        if (currentMode == ImportMode.FEN) {
          // --- FEN SECTION ---
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "Paste FEN String",
                color = TextTitle,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
              )
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                  onClick = {
                    getClipboardText()?.let { fenText = it.trim() }
                  },
                  colors = ButtonDefaults.outlinedButtonColors(contentColor = CoachPrimary),
                  shape = RoundedCornerShape(8.dp),
                  contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                  Icon(imageVector = Icons.Default.ContentPaste, contentDescription = "Paste", modifier = Modifier.size(13.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Paste", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                  onClick = { fenText = "" },
                  colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted),
                  shape = RoundedCornerShape(8.dp),
                  contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                  Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "Clear", modifier = Modifier.size(13.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Clear", fontSize = 11.sp)
                }
              }
            }

            OutlinedTextField(
              value = fenText,
              onValueChange = { fenText = it },
              modifier = Modifier
                .fillMaxWidth()
                .height(84.dp),
              placeholder = { Text("e.g. rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", color = TextMuted, fontSize = 11.sp) },
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (parsedFenResult.isSuccess) CoachPrimary else StatusBlunder,
                unfocusedBorderColor = LiquidGlassSurfaceSubtle,
                focusedTextColor = TextTitle,
                unfocusedTextColor = TextTitle
              ),
              shape = RoundedCornerShape(10.dp)
            )

            // FEN Validity Indicator Card
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(
                  if (parsedFenResult.isSuccess) StatusExcellent.copy(alpha = 0.12f)
                  else StatusBlunder.copy(alpha = 0.12f)
                )
                .border(
                  width = 1.dp,
                  color = if (parsedFenResult.isSuccess) StatusExcellent.copy(alpha = 0.4f) else StatusBlunder.copy(alpha = 0.4f),
                  shape = RoundedCornerShape(10.dp)
                )
                .padding(10.dp)
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(
                  imageVector = if (parsedFenResult.isSuccess) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                  contentDescription = null,
                  tint = if (parsedFenResult.isSuccess) StatusExcellent else StatusBlunder,
                  modifier = Modifier.size(16.dp)
                )
                if (parsedFenResult.isSuccess) {
                  val pos = parsedFenResult.getOrThrow()
                  val activeTurn = if (pos.sideToMove == PieceColor.WHITE) "White to move" else "Black to move"
                  val piecesCount = pos.squares.count { it != null }
                  Text(
                    text = "Valid FEN • $activeTurn • $piecesCount pieces on board",
                    color = StatusExcellent,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                  )
                } else {
                  val errorMsg = parsedFenResult.exceptionOrNull()?.localizedMessage ?: "Invalid FEN notation"
                  Text(
                    text = errorMsg,
                    color = StatusBlunder,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                  )
                }
              }
            }

            // Quick Presets
            Text(
              text = "Quick Presets",
              color = TextTitle,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold
            )
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              for (preset in FEN_PRESETS) {
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(LiquidGlassSurface)
                    .border(1.dp, LiquidGlassSurfaceSubtle, RoundedCornerShape(8.dp))
                    .clickable { fenText = preset.fen }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                  Column {
                    Text(
                      text = preset.label,
                      color = CoachAccentGold,
                      fontSize = 11.5.sp,
                      fontWeight = FontWeight.Bold
                    )
                    Text(
                      text = preset.description,
                      color = TextMuted,
                      fontSize = 9.5.sp,
                      maxLines = 1
                    )
                  }
                }
              }
            }

            // Optional: Create as Custom Puzzle in Dojo
            if (onAddTacticsPuzzle != null) {
              Spacer(modifier = Modifier.height(4.dp))
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(8.dp))
                  .clickable { isCreatingPuzzle = !isCreatingPuzzle }
                  .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  Icon(imageVector = Icons.Default.Extension, contentDescription = null, tint = CoachPrimary, modifier = Modifier.size(16.dp))
                  Text("Add as Custom Puzzle to Tactics Dojo", color = TextTitle, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(if (isCreatingPuzzle) "Hide" else "Configure", color = CoachPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
              }

              AnimatedVisibility(visible = isCreatingPuzzle) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(LiquidGlassSurface)
                    .padding(12.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  OutlinedTextField(
                    value = puzzleTitle,
                    onValueChange = { puzzleTitle = it },
                    label = { Text("Puzzle Title", color = TextMuted, fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CoachPrimary, unfocusedBorderColor = LiquidGlassSurfaceSubtle, focusedTextColor = TextTitle, unfocusedTextColor = TextTitle),
                    shape = RoundedCornerShape(8.dp)
                  )

                  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Theme selector
                    Box(modifier = Modifier.weight(1f)) {
                      OutlinedButton(
                        onClick = { isThemeDropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextTitle)
                      ) {
                        Text(puzzleTheme.label, fontSize = 11.sp, maxLines = 1)
                      }
                      DropdownMenu(
                        expanded = isThemeDropdownExpanded,
                        onDismissRequest = { isThemeDropdownExpanded = false }
                      ) {
                        TacticalTheme.values().forEach { theme ->
                          DropdownMenuItem(
                            text = { Text(theme.label, fontSize = 12.sp) },
                            onClick = {
                              puzzleTheme = theme
                              isThemeDropdownExpanded = false
                            }
                          )
                        }
                      }
                    }

                    OutlinedTextField(
                      value = puzzleRatingText,
                      onValueChange = { puzzleRatingText = it.filter { char -> char.isDigit() }.take(4) },
                      label = { Text("Rating", color = TextMuted, fontSize = 10.sp) },
                      modifier = Modifier.weight(0.7f),
                      colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CoachPrimary, unfocusedBorderColor = LiquidGlassSurfaceSubtle, focusedTextColor = TextTitle, unfocusedTextColor = TextTitle),
                      shape = RoundedCornerShape(8.dp)
                    )
                  }

                  OutlinedTextField(
                    value = puzzleSolutionMove,
                    onValueChange = { puzzleSolutionMove = it },
                    label = { Text("Solution Move (UCI e.g. e2e4 or SAN e.g. Nf3, optional)", color = TextMuted, fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CoachPrimary, unfocusedBorderColor = LiquidGlassSurfaceSubtle, focusedTextColor = TextTitle, unfocusedTextColor = TextTitle),
                    shape = RoundedCornerShape(8.dp)
                  )
                }
              }
            }

            // Action Buttons
            Spacer(modifier = Modifier.height(4.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              if (onAddTacticsPuzzle != null && isCreatingPuzzle) {
                Button(
                  onClick = {
                    val res = TacticsRepository.createCustomPuzzle(
                      fen = fenText,
                      title = puzzleTitle,
                      theme = puzzleTheme,
                      solutionInput = puzzleSolutionMove.ifBlank { null },
                      rating = puzzleRatingText.toIntOrNull() ?: 1200
                    )
                    res.onSuccess { puzzle ->
                      TacticsRepository.addCustomPuzzle(puzzle)
                      onAddTacticsPuzzle(puzzle)
                      onDismiss()
                    }
                  },
                  enabled = parsedFenResult.isSuccess,
                  colors = ButtonDefaults.buttonColors(containerColor = CoachAccentGold, contentColor = Color(0xFF0F1115)),
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier.weight(1f)
                ) {
                  Icon(imageVector = Icons.Default.Extension, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Add Puzzle", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
              }

              Button(
                onClick = {
                  if (parsedFenResult.isSuccess) {
                    onPlayFenInArena(fenText, "Custom FEN Position")
                    onDismiss()
                  }
                },
                enabled = parsedFenResult.isSuccess,
                colors = ButtonDefaults.buttonColors(containerColor = CoachPrimary, contentColor = Color(0xFF0F1115)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
              ) {
                Icon(imageVector = Icons.Default.SportsEsports, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Play in Arena", fontSize = 12.sp, fontWeight = FontWeight.Bold)
              }
            }
          }
        } else {
          // --- PGN SECTION ---
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "Paste PGN Game",
                color = TextTitle,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
              )

              OutlinedButton(
                onClick = {
                  getClipboardText()?.let { pgnText = it.trim() }
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CoachPrimary),
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
              ) {
                Icon(imageVector = Icons.Default.ContentPaste, contentDescription = "Paste", modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Paste PGN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
              }
            }

            OutlinedTextField(
              value = pgnText,
              onValueChange = { pgnText = it },
              modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
              placeholder = { Text("1. e4 e5 2. Nf3 Nc6...", color = TextMuted, fontSize = 11.sp) },
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (parsedPgnGame != null && parsedPgnGame.moves.isNotEmpty()) CoachPrimary else StatusBlunder,
                unfocusedBorderColor = LiquidGlassSurfaceSubtle,
                focusedTextColor = TextTitle,
                unfocusedTextColor = TextTitle
              ),
              shape = RoundedCornerShape(10.dp)
            )

            // PGN Preset Chips
            Text(
              text = "Famous Games",
              color = TextTitle,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold
            )
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              for (preset in PGN_PRESETS) {
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(LiquidGlassSurface)
                    .border(1.dp, LiquidGlassSurfaceSubtle, RoundedCornerShape(8.dp))
                    .clickable { pgnText = preset.pgn }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                  Column {
                    Text(
                      text = preset.label,
                      color = CoachAccentGold,
                      fontSize = 11.5.sp,
                      fontWeight = FontWeight.Bold
                    )
                    Text(
                      text = preset.description,
                      color = TextMuted,
                      fontSize = 9.5.sp,
                      maxLines = 1
                    )
                  }
                }
              }
            }

            // PGN Parsed Info & Ply Scrubber
            if (parsedPgnGame != null && parsedPgnGame.moves.isNotEmpty()) {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(12.dp))
                  .background(LiquidGlassSurface)
                  .border(1.dp, LiquidGlassSurfaceSubtle, RoundedCornerShape(12.dp))
                  .padding(12.dp)
              ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Text(
                      text = "${parsedPgnGame.white} vs ${parsedPgnGame.black}",
                      color = TextTitle,
                      fontSize = 12.5.sp,
                      fontWeight = FontWeight.Bold
                    )
                    Text(
                      text = parsedPgnGame.result,
                      color = CoachAccentGold,
                      fontSize = 12.sp,
                      fontWeight = FontWeight.Bold
                    )
                  }

                  Text(
                    text = "Total Moves: ${parsedPgnGame.moves.size} plies • Select starting point:",
                    color = TextMuted,
                    fontSize = 10.5.sp
                  )

                  val totalPlies = parsedPgnGame.moves.size
                  val currentPly = selectedPgnPly.coerceIn(0, totalPlies - 1)
                  val selectedMove = parsedPgnGame.moves.getOrNull(currentPly)

                  Slider(
                    value = currentPly.toFloat(),
                    onValueChange = { selectedPgnPly = it.toInt() },
                    valueRange = 0f..(totalPlies - 1).toFloat(),
                    steps = if (totalPlies > 2) totalPlies - 2 else 0,
                    colors = SliderDefaults.colors(
                      thumbColor = CoachPrimary,
                      activeTrackColor = CoachPrimary,
                      inactiveTrackColor = LiquidGlassSurfaceSubtle
                    )
                  )

                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(
                      text = "Move ${selectedMove?.moveNumber ?: 1}: ${selectedMove?.san ?: "-"}",
                      color = CoachPrimary,
                      fontSize = 12.sp,
                      fontWeight = FontWeight.Bold
                    )

                    Row(
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      if (onLoadPgnForReview != null) {
                        OutlinedButton(
                          onClick = {
                            onLoadPgnForReview(pgnText)
                            onDismiss()
                          },
                          shape = RoundedCornerShape(8.dp),
                          colors = ButtonDefaults.outlinedButtonColors(contentColor = CoachAccentGold)
                        ) {
                          Icon(imageVector = Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp))
                          Spacer(modifier = Modifier.width(4.dp))
                          Text("Review Game", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                      }

                      Button(
                        onClick = {
                          val posToPlay = selectedMove?.positionAfter ?: Position.initial()
                          val title = "Game (${parsedPgnGame.white} vs ${parsedPgnGame.black} Move ${selectedMove?.moveNumber ?: 1})"
                          onPlayFenInArena(posToPlay.toFen(), title)
                          onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CoachPrimary, contentColor = Color(0xFF0F1115)),
                        shape = RoundedCornerShape(8.dp)
                      ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Spar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                      }
                    }
                  }
                }
              }
            } else {
              Text(
                text = if (pgnText.isBlank()) "Enter or paste PGN game text above" else "Unable to parse valid moves from PGN",
                color = TextMuted,
                fontSize = 11.sp
              )
            }
          }
        }
      }
    }
  }
}
