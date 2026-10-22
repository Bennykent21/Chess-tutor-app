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
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.chesstutor.app.board.AnalysisMoveList
import com.chesstutor.app.board.AnalysisSession
import com.chesstutor.app.board.AnalysisToolbar
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
import com.chesstutor.app.board.EngineLinesPanel
import com.chesstutor.app.board.MarkKind
import com.chesstutor.app.board.SquareRing
import com.chesstutor.app.board.moveListOf
import com.chesstutor.app.board.toggleArrow
import com.chesstutor.app.board.toggleRing
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.data.settings.AppSettings
import com.chesstutor.app.design.CtBadge
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
import com.chesstutor.app.engine.EngineLine
import com.chesstutor.app.engine.EngineService
import com.chesstutor.app.engine.EngineStatus
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

/** A quick first look, then a deeper one, so lines appear fast and then firm up. */
private val ANALYSIS_STEPS_MS = listOf(300, 1500)

/** Arrows and rings the player drew on one position. */
private data class Drawn(val arrows: List<BoardArrow> = emptyList(), val rings: List<SquareRing> = emptyList())

/**
 * The Phase 1 demo screen: one board in every configuration of plan section 4.2, with
 * tap-tap or drag and drop, sounds, haptics, takebacks and the fade-through flip. Review
 * and analysis add engine lines, variations and drawn arrows. Uses the player's board
 * theme and settings. During development only (Account > During development).
 */
@Composable
fun BoardLabScreen(
    settings: AppSettings,
    engine: EngineService?,
    onFeedback: (BoardFeedback) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = remember { AnalysisSession() }
    val state by session.state.collectAsState()
    val view by session.view.collectAsState()
    var whiteAtBottom by remember { mutableStateOf(true) }
    var positionName by remember { mutableStateOf(LAB_POSITIONS.first().first) }
    var config by remember { mutableStateOf(BoardConfig.PLAY) }
    var options by remember { mutableStateOf(BoardOptions()) }
    val extras = config.resolve(options)
    val analysisTools = config == BoardConfig.ANALYSIS || config == BoardConfig.REVIEW
    var drawing by remember { mutableStateOf(false) }
    val drawn = remember { mutableStateMapOf<Int, Drawn>() }
    val engineStatus by (engine?.status?.collectAsState() ?: remember { mutableStateOf(EngineStatus()) })

    // ---- engine lines, only while something shows them
    var lines by remember { mutableStateOf<List<EngineLine>>(emptyList()) }
    LaunchedEffect(state.version, extras.evalBar, extras.arrows, extras.engineLines) {
        lines = emptyList()
        if (engine == null || !(extras.evalBar || extras.arrows || extras.engineLines) || state.legalMoves.isEmpty()) return@LaunchedEffect
        val fen = state.position.toFen()
        val wanted = if (extras.engineLines) 3 else 1
        for (movetime in ANALYSIS_STEPS_MS) {
            val result = engine.analyse(fen, lines = wanted, movetimeMs = movetime)
            if (result.isNotEmpty()) lines = result
        }
    }

    // ---- clocks: the side to move ticks down
    var whiteClock by remember { mutableLongStateOf(LAB_CLOCK_MS) }
    var blackClock by remember { mutableLongStateOf(LAB_CLOCK_MS) }
    LaunchedEffect(extras.clocks, config, state.movable) {
        if (!extras.clocks || config == BoardConfig.REVIEW) return@LaunchedEffect
        while (true) {
            delay(100)
            when (state.movable) {
                PieceColor.WHITE -> whiteClock = (whiteClock - 100).coerceAtLeast(0)
                PieceColor.BLACK -> blackClock = (blackClock - 100).coerceAtLeast(0)
                null -> return@LaunchedEffect
            }
        }
    }

    val best = lines.firstOrNull()
    val engineArrows = if (!extras.arrows) emptyList() else {
        val bestKind = if (config == BoardConfig.LESSON || config == BoardConfig.PUZZLE) MarkKind.HINT else MarkKind.BEST
        val alternatives = if (extras.engineLines) lines.drop(1) else emptyList()
        listOfNotNull(best.arrow(bestKind)) + alternatives.mapNotNull { it.arrow(MarkKind.ALTERNATIVE) }
    }
    val mine = drawn[session.currentId] ?: Drawn()
    // Review clocks show a game's recorded times. The lab's test game has none, so none show.
    val showClocks = extras.clocks && config != BoardConfig.REVIEW
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
                    CtCoachStrip(settings.mascot, coachLine(config), label = "You play $side", modifier = Modifier.fillMaxWidth())
                }
                if (showClocks) {
                    PlayerRow(if (topIsWhite) "White" else "Black", if (topIsWhite) whiteClock else blackClock, state.movable == (if (topIsWhite) PieceColor.WHITE else PieceColor.BLACK))
                }
                BoardWithEvalBar(
                    evalBar = if (extras.evalBar) {
                        { barModifier -> BoardEvalBar(best?.centipawns, best?.mateInMoves, whiteAtBottom, barModifier) }
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
                        arrows = engineArrows + mine.arrows,
                        rings = mine.rings,
                        onFeedback = onFeedback,
                        drawMode = analysisTools && drawing,
                        onDrawArrow = { from, to ->
                            val id = session.currentId
                            val now = drawn[id] ?: Drawn()
                            drawn[id] = now.copy(arrows = toggleArrow(now.arrows, BoardArrow(from, to, MarkKind.USER)))
                        },
                        onDrawRing = { square ->
                            val id = session.currentId
                            val now = drawn[id] ?: Drawn()
                            drawn[id] = now.copy(rings = toggleRing(now.rings, SquareRing(square)))
                        },
                    )
                }
                if (showClocks) {
                    PlayerRow(if (topIsWhite) "Black" else "White", if (topIsWhite) blackClock else whiteClock, state.movable == (if (topIsWhite) PieceColor.BLACK else PieceColor.WHITE))
                }
                if (extras.engineLines) {
                    if (engineStatus.isDegraded) {
                        CtBadge(text = "Lite engine: Stockfish is not answering", icon = Icons.Rounded.Warning)
                    }
                    EngineLinesPanel(
                        position = state.position,
                        lines = lines,
                        engineName = if (engineStatus.isDegraded) "Lite engine" else "Stockfish",
                        onPlayLine = { line ->
                            line.firstMove?.let { uci -> runCatching { Move.fromUci(uci) }.getOrNull() }
                                ?.takeIf { move -> state.legalMoves.any { it.uci == move.uci } }
                                ?.let { onFeedback(session.onMove(it)) }
                        },
                    )
                }
                if (analysisTools) {
                    AnalysisToolbar(
                        view = view,
                        drawing = drawing,
                        onStart = session::toStart,
                        onBack = session::back,
                        onForward = session::forward,
                        onEnd = session::toEnd,
                        onToggleDrawing = { drawing = !drawing },
                    )
                    AnalysisMoveList(view = view, onSelect = session::goTo)
                    if (view.inVariation) {
                        Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                            CtButton("Make main line", onClick = session::promoteCurrent, style = CtButtonStyle.Secondary, modifier = Modifier.weight(1f))
                            CtButton("Delete line", onClick = session::deleteCurrent, style = CtButtonStyle.Ghost, modifier = Modifier.weight(1f))
                        }
                    }
                    if (config == BoardConfig.REVIEW) {
                        Text("Review shows clocks only for games with recorded times. This test game has none.", style = CtType.Caption)
                    }
                    if (drawing) {
                        Text("Drag between squares to draw an arrow, tap a square to ring it. Draw it again to erase.", style = CtType.Caption)
                    }
                } else if (extras.moveList) {
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
                            enabled = view.canGoBack,
                            onClick = { session.takeBack() },
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
                            drawing = false
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
                            drawn.clear()
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

private fun EngineLine?.arrow(kind: MarkKind): BoardArrow? {
    val uci = this?.firstMove?.takeIf { it.length in 4..5 } ?: return null
    val move = runCatching { Move.fromUci(uci) }.getOrNull() ?: return null
    return BoardArrow(move.from, move.to, kind)
}

private fun coachLine(config: BoardConfig): String = when (config) {
    BoardConfig.LESSON -> "Follow the arrow: it shows the idea of this lesson."
    BoardConfig.PUZZLE -> "Find the best move. Switch on hints if you are stuck."
    BoardConfig.PUZZLE_SOLVED -> "Solved! Switch on the eval bar or engine lines to see why it works."
    else -> "Your move."
}

/** Chips for the extras this configuration lets the player switch on. */
@Composable
private fun OptionalToggles(config: BoardConfig, options: BoardOptions, onChange: (BoardOptions) -> Unit) {
    val toggles = buildList {
        if (config.evalBar == Availability.OPTIONAL) add(Triple("Eval bar", options.evalBar) { v: Boolean -> options.copy(evalBar = v) })
        if (config.arrows == Availability.OPTIONAL) add(Triple(if (config == BoardConfig.PUZZLE) "Hints" else "Arrows", options.arrows) { v: Boolean -> options.copy(arrows = v) })
        if (config.engineLines == Availability.OPTIONAL) add(Triple("Engine lines", options.engineLines) { v: Boolean -> options.copy(engineLines = v) })
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
