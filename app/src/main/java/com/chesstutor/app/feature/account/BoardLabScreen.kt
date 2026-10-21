package com.chesstutor.app.feature.account

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.chesstutor.app.board.Availability
import com.chesstutor.app.board.BoardArrow
import com.chesstutor.app.board.BoardClock
import com.chesstutor.app.board.BoardConfig
import com.chesstutor.app.board.BoardEvalBar
import com.chesstutor.app.board.BoardFeedback
import com.chesstutor.app.board.BoardHost
import com.chesstutor.app.board.BoardMoveList
import com.chesstutor.app.board.BoardOptions
import com.chesstutor.app.board.BoardWithEvalBar
import com.chesstutor.app.board.FreePlaySession
import com.chesstutor.app.board.MarkKind
import com.chesstutor.app.board.moveListOf
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.data.settings.AppSettings
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtChip
import com.chesstutor.app.design.CtCoachStrip
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.engine.EngineService
import kotlinx.coroutines.delay

/** Positions that exercise every kind of move animation. */
private val LAB_POSITIONS = listOf(
    "Start" to Position.STARTING_FEN,
    "Castling" to "r3k2r/pppq1ppp/2npbn2/2b1p3/2B1P3/2NPBN2/PPPQ1PPP/R3K2R w KQkq - 4 8",
    "Promotion" to "3r4/4P1k1/8/8/8/8/1p4K1/8 w - - 0 1",
    "En passant" to "rnbqkbnr/ppp1p1pp/8/3pPp2/8/8/PPPP1PPP/RNBQKBNR w KQkq f6 0 3",
    "Check" to "rnbqkbnr/pppp1ppp/8/4p3/6P1/5P2/PPPPP2P/RNBQKBNR b KQkq - 0 2",
)

private const val LAB_CLOCK_MS = 3 * 60_000L

/** What the engine said about the current position. */
private data class LabEval(val centipawns: Int?, val mateIn: Int?, val bestMove: Move?)

/**
 * The Phase 1 demo screen: one board in every configuration of plan section 4.2, with
 * tap-tap or drag and drop, sounds, haptics, takebacks and the fade-through flip. Uses the
 * player's board theme and settings. During development only (Account > During development).
 */
@Composable
fun BoardLabScreen(
    settings: AppSettings,
    engine: EngineService?,
    onFeedback: (BoardFeedback) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = remember { FreePlaySession() }
    val state by session.state.collectAsState()
    var whiteAtBottom by remember { mutableStateOf(true) }
    var positionName by remember { mutableStateOf(LAB_POSITIONS.first().first) }
    var config by remember { mutableStateOf(BoardConfig.PLAY) }
    var options by remember { mutableStateOf(BoardOptions()) }
    val extras = config.resolve(options)

    // ---- engine view of the position, only while something shows it
    var eval by remember { mutableStateOf<LabEval?>(null) }
    LaunchedEffect(state.version, extras.evalBar, extras.arrows) {
        eval = null
        if (engine == null || !(extras.evalBar || extras.arrows) || state.legalMoves.isEmpty()) return@LaunchedEffect
        val result = engine.evaluate(state.position.toFen(), movetimeMs = 400) ?: return@LaunchedEffect
        val best = result.bestMoveUci.takeIf { it.length in 4..5 }?.let { uci -> runCatching { Move.fromUci(uci) }.getOrNull() }
        eval = LabEval(result.centipawns, result.mateInMoves, best)
    }

    // ---- clocks: the side to move ticks down
    var whiteClock by remember { mutableLongStateOf(LAB_CLOCK_MS) }
    var blackClock by remember { mutableLongStateOf(LAB_CLOCK_MS) }
    LaunchedEffect(extras.clocks, state.movable) {
        if (!extras.clocks) return@LaunchedEffect
        while (true) {
            delay(100)
            when (state.movable) {
                PieceColor.WHITE -> whiteClock = (whiteClock - 100).coerceAtLeast(0)
                PieceColor.BLACK -> blackClock = (blackClock - 100).coerceAtLeast(0)
                null -> return@LaunchedEffect
            }
        }
    }

    val arrowKind = if (config == BoardConfig.LESSON || config == BoardConfig.PUZZLE) MarkKind.HINT else MarkKind.BEST
    val arrows = if (extras.arrows) eval?.bestMove?.let { listOf(BoardArrow(it.from, it.to, arrowKind)) }.orEmpty() else emptyList()
    val topIsWhite = !whiteAtBottom

    CtScreenBackground(modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            CtTopBar(title = "Board lab", subtitle = config.name, onBack = onBack)
            Column(
                modifier = Modifier.padding(horizontal = CtSpacing.gutter),
                verticalArrangement = Arrangement.spacedBy(CtSpacing.sm),
            ) {
                if (extras.coachStrip) {
                    val side = if (state.start.sideToMove == PieceColor.WHITE) "White" else "Black"
                    CtCoachStrip(
                        mascot = settings.mascot,
                        label = "You play $side",
                        text = coachLine(config),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (extras.clocks) {
                    PlayerRow(name = if (topIsWhite) "White" else "Black", millis = if (topIsWhite) whiteClock else blackClock, running = state.movable == (if (topIsWhite) PieceColor.WHITE else PieceColor.BLACK))
                }
                BoardWithEvalBar(
                    evalBar = if (extras.evalBar) {
                        { barModifier -> BoardEvalBar(eval?.centipawns, eval?.mateIn, whiteAtBottom, barModifier) }
                    } else null,
                ) { boardModifier ->
                    BoardHost(
                        session = session,
                        theme = settings.boardTheme,
                        modifier = boardModifier,
                        whiteAtBottom = whiteAtBottom,
                        showCoordinates = settings.showCoordinates,
                        showLegalDots = settings.showLegalDots,
                        autoQueen = settings.autoQueen,
                        arrows = arrows,
                        onFeedback = onFeedback,
                    )
                }
                if (extras.clocks) {
                    PlayerRow(name = if (topIsWhite) "Black" else "White", millis = if (topIsWhite) blackClock else whiteClock, running = state.movable == (if (topIsWhite) PieceColor.BLACK else PieceColor.WHITE))
                }
                if (extras.moveList) {
                    val entries = remember(state.version) { moveListOf(state.start, state.moves) }
                    BoardMoveList(entries = entries, currentPly = entries.lastOrNull()?.ply ?: 0)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                    CtButton(
                        text = "Flip",
                        icon = Icons.Rounded.SwapVert,
                        style = CtButtonStyle.Secondary,
                        onClick = { whiteAtBottom = !whiteAtBottom },
                        modifier = Modifier.weight(1f),
                    )
                    if (extras.takebacks) {
                        CtButton(
                            text = if (config == BoardConfig.PUZZLE || config == BoardConfig.PUZZLE_SOLVED) "Retry" else "Take back",
                            icon = Icons.AutoMirrored.Rounded.Undo,
                            style = CtButtonStyle.Secondary,
                            enabled = session.canUndo && state.version > 0,
                            onClick = { session.undo() },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                CtSectionHeader(title = "Configuration")
                ChipRow {
                    BoardConfig.ALL.forEach { option ->
                        CtChip(text = option.name, color = CtColors.Gold, selected = option == config, onClick = {
                            config = option
                            options = BoardOptions()
                        })
                    }
                }
                OptionalToggles(config, options) { options = it }

                CtSectionHeader(title = "Test positions")
                ChipRow {
                    LAB_POSITIONS.forEach { (name, fen) ->
                        CtChip(text = name, selected = name == positionName, onClick = {
                            positionName = name
                            whiteClock = LAB_CLOCK_MS
                            blackClock = LAB_CLOCK_MS
                            session.reset(Position.fromFen(fen))
                        })
                    }
                }
                Text(
                    text = "Pieces, colours, coordinates, legal-move dots and auto-queen follow your settings. " +
                        "Turn on Reduce motion to check that everything snaps.",
                    style = CtType.Caption,
                )
                Spacer(Modifier.height(CtSpacing.lg))
            }
        }
    }
}

private fun coachLine(config: BoardConfig): String = when (config) {
    BoardConfig.LESSON -> "Follow the arrow: it shows the idea of this lesson."
    BoardConfig.PUZZLE -> "Find the best move. Switch on hints if you are stuck."
    BoardConfig.PUZZLE_SOLVED -> "Solved! Switch on the eval bar to see why it works."
    else -> "Your move."
}

/** Chips for the extras this configuration lets the player switch on. */
@Composable
private fun OptionalToggles(config: BoardConfig, options: BoardOptions, onChange: (BoardOptions) -> Unit) {
    val toggles = buildList {
        if (config.evalBar == Availability.OPTIONAL) add(Triple("Eval bar", options.evalBar) { v: Boolean -> options.copy(evalBar = v) })
        if (config.arrows == Availability.OPTIONAL) add(Triple(if (config == BoardConfig.PUZZLE) "Hints" else "Arrows", options.arrows) { v: Boolean -> options.copy(arrows = v) })
        if (config.clocks == Availability.OPTIONAL) add(Triple("Clocks", options.clocks) { v: Boolean -> options.copy(clocks = v) })
        if (config.takebacks == Availability.OPTIONAL) add(Triple("Takebacks", options.takebacks) { v: Boolean -> options.copy(takebacks = v) })
    }
    if (toggles.isEmpty()) {
        Text("Nothing optional here: this configuration's extras are fixed.", style = CtType.Caption)
        return
    }
    ChipRow {
        toggles.forEach { (label, on, update) ->
            CtChip(text = if (on) "$label: on" else "$label: off", selected = on, onClick = { onChange(update(!on)) })
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm),
    ) { content() }
}

@Composable
private fun PlayerRow(name: String, millis: Long, running: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = CtType.TitleSmall, modifier = Modifier.weight(1f))
        BoardClock(millis = millis, running = running)
    }
}
