package com.chesstutor.app.feature.analysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.chesstutor.app.board.AnalysisMoveList
import com.chesstutor.app.board.AnalysisSession
import com.chesstutor.app.board.AnalysisToolbar
import com.chesstutor.app.board.BoardArrow
import com.chesstutor.app.board.BoardEvalBar
import com.chesstutor.app.board.BoardFeedback
import com.chesstutor.app.board.BoardHost
import com.chesstutor.app.board.BoardWithEvalBar
import com.chesstutor.app.board.EngineLinesPanel
import com.chesstutor.app.board.MarkKind
import com.chesstutor.app.board.SquareRing
import com.chesstutor.app.board.toggleArrow
import com.chesstutor.app.board.toggleRing
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.data.settings.AppSettings
import com.chesstutor.app.design.CtBadge
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.engine.EngineLine
import com.chesstutor.app.engine.EngineService
import com.chesstutor.app.engine.EngineStatus

/** A quick first look, then a deeper one, so lines appear fast and then firm up. */
private val ANALYSIS_STEPS_MS = listOf(300, 1500)

private data class Drawn(val arrows: List<BoardArrow> = emptyList(), val rings: List<SquareRing> = emptyList())

/**
 * Free analysis from any position (plan section 4.2), opened from a puzzle's "Analyse":
 * engine lines with an eval bar, variations, and arrows you draw yourself.
 */
@Composable
fun AnalysisScreen(
    startFen: String,
    settings: AppSettings,
    engine: EngineService?,
    onFeedback: (BoardFeedback) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val start = remember(startFen) { Position.tryFromFen(startFen).getOrDefault(Position.initial()) }
    val session = remember(start) { AnalysisSession(start) }
    val state by session.state.collectAsState()
    val view by session.view.collectAsState()
    var whiteAtBottom by remember { mutableStateOf(start.sideToMove == PieceColor.WHITE) }
    var drawing by remember { mutableStateOf(false) }
    val drawn = remember { mutableStateMapOf<Int, Drawn>() }
    val engineStatus by (engine?.status?.collectAsState() ?: remember { mutableStateOf(EngineStatus()) })

    var lines by remember { mutableStateOf<List<EngineLine>>(emptyList()) }
    LaunchedEffect(state.version) {
        lines = emptyList()
        if (engine == null || state.legalMoves.isEmpty()) return@LaunchedEffect
        val fen = state.position.toFen()
        for (movetime in ANALYSIS_STEPS_MS) {
            val result = engine.analyse(fen, lines = 3, movetimeMs = movetime)
            if (result.isNotEmpty()) lines = result
        }
    }
    val best = lines.firstOrNull()
    val engineArrows = lines.mapIndexedNotNull { i, line ->
        val move = line.firstMove?.let { runCatching { Move.fromUci(it) }.getOrNull() } ?: return@mapIndexedNotNull null
        BoardArrow(move.from, move.to, if (i == 0) MarkKind.BEST else MarkKind.ALTERNATIVE)
    }
    val mine = drawn[session.currentId] ?: Drawn()

    CtScreenBackground(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            CtTopBar(title = "Analysis", subtitle = "Try moves; the engine follows along", onBack = onBack)
            Column(
                modifier = Modifier.padding(horizontal = CtSpacing.gutter),
                verticalArrangement = Arrangement.spacedBy(CtSpacing.sm),
            ) {
                BoardWithEvalBar(
                    evalBar = { barModifier -> BoardEvalBar(best?.centipawns, best?.mateInMoves, whiteAtBottom, barModifier) },
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
                        drawMode = drawing,
                        onDrawArrow = { from, to ->
                            val now = drawn[session.currentId] ?: Drawn()
                            drawn[session.currentId] = now.copy(arrows = toggleArrow(now.arrows, BoardArrow(from, to, MarkKind.USER)))
                        },
                        onDrawRing = { square ->
                            val now = drawn[session.currentId] ?: Drawn()
                            drawn[session.currentId] = now.copy(rings = toggleRing(now.rings, SquareRing(square)))
                        },
                    )
                }
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
                CtButton(text = "Flip board", icon = Icons.Rounded.SwapVert, style = CtButtonStyle.Secondary, onClick = { whiteAtBottom = !whiteAtBottom })
                if (drawing) {
                    Text("Drag between squares to draw an arrow, tap a square to ring it. Draw it again to erase.", style = CtType.Caption)
                }
                Spacer(Modifier.height(CtSpacing.lg))
            }
        }
    }
}
