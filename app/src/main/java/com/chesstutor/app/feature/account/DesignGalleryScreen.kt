package com.chesstutor.app.feature.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.dp
import com.chesstutor.app.board.BoardColors
import com.chesstutor.app.board.BoardHost
import com.chesstutor.app.board.FreePlaySession
import com.chesstutor.app.board.BoardMarks
import com.chesstutor.app.board.BoardTheme
import com.chesstutor.app.board.BoardView
import com.chesstutor.app.board.PieceSet
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.Square
import com.chesstutor.app.design.AppTheme
import com.chesstutor.app.design.Appearance
import com.chesstutor.app.design.CtBadge
import com.chesstutor.app.design.CtBoardScreen
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtCard
import com.chesstutor.app.design.CtChip
import com.chesstutor.app.design.CtCoachStrip
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtComingSoon
import com.chesstutor.app.design.CtDropdownChip
import com.chesstutor.app.design.CtEmptyState
import com.chesstutor.app.design.CtHeroCard
import com.chesstutor.app.design.CtListRow
import com.chesstutor.app.design.CtMascotArt
import com.chesstutor.app.design.CtModeCard
import com.chesstutor.app.design.CtProgressBar
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtSegmented
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSheet
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtSpeechBubble
import com.chesstutor.app.design.CtStatTile
import com.chesstutor.app.design.CtTail
import com.chesstutor.app.design.CtThemeCard
import com.chesstutor.app.design.CtTile
import com.chesstutor.app.design.CtToggleRow
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.domain.Mascot
import com.chesstutor.app.domain.MascotMood

/**
 * Every design-system piece on one page, in the current theme, to judge the look on a real
 * phone (Phase 7C2c). Reached from Settings > About while the redesign is under way.
 */
@Composable
fun DesignGalleryScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var progress by remember { mutableFloatStateOf(0.375f) }
    var selectedChip by remember { mutableIntStateOf(0) }
    var mode by remember { mutableStateOf(Appearance.SYSTEM) }
    var theme by remember { mutableStateOf(AppTheme.PARCHMENT) }
    var sound by remember { mutableStateOf(true) }
    var reduceMotion by remember { mutableStateOf(false) }
    var sheetOpen by remember { mutableStateOf(false) }
    var boardDemo by remember { mutableStateOf(false) }

    if (boardDemo) {
        BoardScreenDemo(onClose = { boardDemo = false }, modifier = modifier)
        return
    }

    CtScreenBackground(modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            CtTopBar(title = "Design gallery", onBack = onBack)
            Column(
                modifier = Modifier.padding(horizontal = CtSpacing.gutter),
                verticalArrangement = Arrangement.spacedBy(CtSpacing.md),
            ) {
                Text("Good evening", style = CtType.Display)
                CtHeroCard(
                    kicker = "Basics",
                    title = "The chessboard",
                    detail = "Lesson 3 of 8",
                    progress = progress,
                    onClick = { progress = if (progress >= 1f) 0.125f else (progress + 0.125f).coerceAtMost(1f) },
                ) {
                    BoardView(position = START, theme = BoardTheme(BoardColors.DEFAULT, PieceSet.DEFAULT), showCoordinates = false, showLegalDots = false)
                }
                CtButton(text = "Quick match", onClick = {}, modifier = Modifier.fillMaxWidth())
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
                    CtTile(modifier = Modifier.weight(1f).fillMaxHeight(), onClick = {}) {
                        Box(Modifier.size(72.dp).clip(CtShapes.Chip)) {
                            BoardView(position = SAMPLE_POSITION, theme = BoardTheme(BoardColors.DEFAULT, PieceSet.DEFAULT), showCoordinates = false, showLegalDots = false)
                        }
                        TileText("Daily puzzle", "White to move")
                    }
                    CtTile(modifier = Modifier.weight(1f).fillMaxHeight(), onClick = {}) {
                        Text("5", style = CtType.BigNumber, modifier = Modifier.weight(1f))
                        TileText("Mistakes to fix", "From your games")
                    }
                }

                CtSectionHeader(title = "More ways to play")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CtModeCard(Icons.Rounded.SmartToy, "Custom bot", onClick = {})
                    CtModeCard(Icons.AutoMirrored.Rounded.MenuBook, "Opening battle", onClick = {})
                    CtModeCard(Icons.Rounded.GridOn, "From a position", onClick = {})
                    CtModeCard(Icons.Rounded.Explore, "Explore", onClick = {})
                }

                CtSectionHeader(title = "Buttons")
                CtButton(text = "Secondary", style = CtButtonStyle.Secondary, onClick = {}, modifier = Modifier.fillMaxWidth())
                CtButton(text = "Ghost", style = CtButtonStyle.Ghost, onClick = {}, modifier = Modifier.fillMaxWidth())
                CtButton(text = "Disabled", enabled = false, onClick = {}, modifier = Modifier.fillMaxWidth())
                CtButton(text = "Open a sheet", style = CtButtonStyle.Secondary, onClick = { sheetOpen = true }, modifier = Modifier.fillMaxWidth())
                CtButton(text = "Open a board screen", style = CtButtonStyle.Secondary, onClick = { boardDemo = true }, modifier = Modifier.fillMaxWidth())

                CtSectionHeader(title = "Chips and badges")
                Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                    CtDropdownChip(text = "All levels", onClick = {})
                    CtDropdownChip(text = "White", onClick = {})
                }
                Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                    listOf("All", "White", "Black").forEachIndexed { index, label ->
                        CtChip(text = label, selected = selectedChip == index, onClick = { selectedChip = index })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                    CtChip(text = "Solved", color = CtColors.Success)
                    CtChip(text = "Missed", color = CtColors.Error)
                    CtBadge(text = "Lite engine", icon = Icons.Rounded.Warning)
                }
                CtComingSoon(8)

                CtSectionHeader(title = "Settings parts")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppTheme.entries.take(2).forEach { t ->
                        CtThemeCard(theme = t, dark = CtColors.IsDark, selected = theme == t, onClick = { theme = t }, modifier = Modifier.weight(1f))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppTheme.entries.drop(2).forEach { t ->
                        CtThemeCard(theme = t, dark = CtColors.IsDark, selected = theme == t, onClick = { theme = t }, modifier = Modifier.weight(1f))
                    }
                }
                CtSegmented(options = Appearance.entries, selected = mode, label = { it.label }, onSelect = { mode = it })
                Column {
                    CtListRow(title = "Board and pieces", subtitle = "Classic wood", icon = Icons.Rounded.GridOn, onClick = {})
                    CtToggleRow(title = "Sound", checked = sound, onCheckedChange = { sound = it })
                    CtToggleRow(title = "Reduce motion", checked = reduceMotion, onCheckedChange = { reduceMotion = it }, divider = false)
                }

                CtSectionHeader(title = "Coaches")
                Mascot.entries.forEach { coach ->
                    Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                        MascotMood.entries.forEach { mood ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                CtMascotArt(mascot = coach, mood = mood, height = 72.dp, modifier = Modifier.size(72.dp))
                                Text(mood.description, style = CtType.Caption)
                            }
                        }
                    }
                }
                CtCoachStrip(mascot = Mascot.SAGE, label = "You play White", text = "Find the move that wins the queen.", mood = MascotMood.THINKING)
                Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
                    CtMascotArt(mascot = Mascot.PIP, height = 60.dp, modifier = Modifier.width(56.dp))
                    CtSpeechBubble(modifier = Modifier.weight(1f)) {
                        Text("Knights jump in an L: two squares one way, then one to the side.", style = CtType.Chat)
                    }
                }
                CtSpeechBubble(tail = CtTail.Top) {
                    Text("Watch closely...", style = CtType.Chat)
                }

                CtSectionHeader(title = "Numbers and progress")
                Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
                    CtStatTile("3,420", "XP", Icons.Rounded.Star, CtColors.Accent, Modifier.weight(1f))
                    CtStatTile("5", "Day streak", Icons.Rounded.LocalFireDepartment, CtColors.Accent, Modifier.weight(1f))
                }
                CtProgressBar(progress = progress)
                CtCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Raised card", style = CtType.Headline)
                    Text("Shadow on light themes, a hairline on dark ones.", style = CtType.BodySmall)
                }
                CtEmptyState(
                    icon = Icons.Rounded.EmojiEvents,
                    title = "No games yet",
                    body = "Play a bot or import your games to see them here.",
                    actionLabel = "Play a bot",
                    onAction = {},
                )

                CtSectionHeader(title = "Colours")
                Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                    listOf(CtColors.Bg, CtColors.Surface, CtColors.Raised, CtColors.Track, CtColors.Accent, CtColors.Primary, CtColors.Success, CtColors.Error)
                        .forEach { Swatch(it) }
                }

                CtSectionHeader(title = "Type")
                Text("Display 30", style = CtType.Display)
                Text("Headline 22", style = CtType.Headline)
                Text("Top bar 20", style = CtType.TopBar)
                Text("Title 19", style = CtType.Title)
                Text("Title small 16", style = CtType.TitleSmall)
                Text("Body 16: the quick brown fox jumps over the lazy dog.", style = CtType.Body)
                Text("Body small 14: secondary information.", style = CtType.BodySmall)
                Text("Label 14", style = CtType.Label)
                Text("Caption 13", style = CtType.Caption)
                Text("Nav label 12", style = CtType.NavLabel)
                Text("1,482", style = CtType.BigNumber)
                Text("Number 22: 1,111 and 1,888 line up", style = CtType.Number)

                CtSectionHeader(title = "Board")
                BoardSample()
                Spacer(Modifier.height(CtSpacing.lg))
            }
        }
        CtSheet(visible = sheetOpen, onDismiss = { sheetOpen = false }) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("You won", style = CtType.Headline)
                Text("Juno resigned after 32 moves", style = CtType.Caption)
            }
            CtButton(text = "Review this game", onClick = { sheetOpen = false }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CtButton(text = "Rematch", style = CtButtonStyle.Secondary, onClick = { sheetOpen = false }, modifier = Modifier.weight(1f))
                CtButton(text = "Other bots", style = CtButtonStyle.Secondary, onClick = { sheetOpen = false }, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** What the coach says after each move in the demo: long and short, so the area below the board changes size. */
private val DEMO_LINES = listOf(
    "Make any move. The board stays exactly where it is, however long this message gets.",
    "Good. Here is a much longer message, to show that the text below the board can grow and shrink while the board above it never moves at all.",
    "Short one.",
)

/**
 * The board screen layout with a real board (Phase 7C2d): the status row and the coach's
 * words change length after every move, and the board stays put (D39).
 */
@Composable
private fun BoardScreenDemo(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val session = remember { FreePlaySession() }
    val state by session.state.collectAsState()
    var finished by remember { mutableStateOf(false) }
    BackHandler(onBack = onClose)
    CtBoardScreen(
        title = "Board screen",
        onBack = onClose,
        modifier = modifier,
        status = {
            Column {
                Text(if (state.position.sideToMove == PieceColor.WHITE) "White to move" else "Black to move", style = CtType.Title)
                Text("${state.moves.size} moves played", style = CtType.Caption)
            }
        },
        board = { BoardHost(session = session, theme = BoardTheme(BoardColors.DEFAULT, PieceSet.DEFAULT)) },
        controls = {
            CtButton(text = "Undo", style = CtButtonStyle.Secondary, enabled = state.moves.isNotEmpty(), onClick = session::undo, modifier = Modifier.weight(1f))
            CtButton(text = "Finish", onClick = { finished = true }, modifier = Modifier.weight(1f))
        },
        overlay = {
            CtSheet(visible = finished, onDismiss = { finished = false }) {
                Text("Nicely done", style = CtType.Headline, modifier = Modifier.align(Alignment.CenterHorizontally))
                CtButton(text = "Back to the gallery", onClick = onClose, modifier = Modifier.fillMaxWidth())
            }
        },
    ) {
        // The coach's words scroll if a message is ever too long; the buttons below never move.
        Row(
            horizontalArrangement = Arrangement.spacedBy(CtSpacing.xs),
            modifier = Modifier.padding(top = CtSpacing.lg).verticalScroll(rememberScrollState()),
        ) {
            CtMascotArt(mascot = Mascot.PIP, height = 52.dp, modifier = Modifier.width(48.dp))
            CtSpeechBubble(modifier = Modifier.weight(1f)) {
                Text(DEMO_LINES[state.moves.size % DEMO_LINES.size], style = CtType.Chat)
            }
        }
    }
}

@Composable
private fun TileText(title: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = CtType.TitleSmall)
        Text(detail, style = CtType.Caption)
    }
}

@Composable
private fun Swatch(color: Color) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CtShapes.Pill)
            .background(color)
            .border(1.dp, CtColors.Line, CtShapes.Pill),
    )
}

private val START = Position.fromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")

/** Italian Game after 3...Bc5, with every kind of mark, so a theme can be judged at a glance. */
private val SAMPLE_POSITION = Position.fromFen("r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4")
private val SAMPLE_MARKS = BoardMarks(
    lastMove = Square.fromAlgebraic("f8") to Square.fromAlgebraic("c5"),
    selected = Square.fromAlgebraic("f3"),
    legalTargets = setOf("g5", "e5", "h4", "d4", "g1").map(Square::fromAlgebraic).toSet(),
)

/** The board in every piece set and colour theme. */
@Composable
private fun BoardSample() {
    var pieceSet by remember { mutableStateOf(PieceSet.DEFAULT) }
    var colors by remember { mutableStateOf(BoardColors.DEFAULT) }
    var whiteAtBottom by remember { mutableStateOf(true) }

    BoardView(
        position = SAMPLE_POSITION,
        theme = BoardTheme(colors, pieceSet),
        whiteAtBottom = whiteAtBottom,
        marks = SAMPLE_MARKS,
    )
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm),
    ) {
        PieceSet.entries.forEach { set ->
            CtChip(text = set.displayName, selected = set == pieceSet, onClick = { pieceSet = set })
        }
    }
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm),
    ) {
        BoardColors.entries.forEach { option ->
            CtChip(text = option.displayName, selected = option == colors, onClick = { colors = option })
        }
    }
    CtButton(
        text = if (whiteAtBottom) "Show Black at the bottom" else "Show White at the bottom",
        style = CtButtonStyle.Secondary,
        onClick = { whiteAtBottom = !whiteAtBottom },
        modifier = Modifier.fillMaxWidth(),
    )
    Text("Pieces: ${pieceSet.credit}", style = CtType.Caption)
}
