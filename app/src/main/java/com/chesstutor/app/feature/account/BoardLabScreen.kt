package com.chesstutor.app.feature.account

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.chesstutor.app.board.BoardFeedback
import com.chesstutor.app.board.BoardHost
import com.chesstutor.app.board.FreePlaySession
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.data.settings.AppSettings
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtChip
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType

/** Positions that exercise every kind of move animation. */
private val LAB_POSITIONS = listOf(
    "Start" to Position.STARTING_FEN,
    "Castling" to "r3k2r/pppq1ppp/2npbn2/2b1p3/2B1P3/2NPBN2/PPPQ1PPP/R3K2R w KQkq - 4 8",
    "Promotion" to "3r4/4P1k1/8/8/8/8/1p4K1/8 w - - 0 1",
    "En passant" to "rnbqkbnr/ppp1p1pp/8/3pPp2/8/8/PPPP1PPP/RNBQKBNR w KQkq f6 0 3",
    "Check" to "rnbqkbnr/pppp1ppp/8/4p3/6P1/5P2/PPPPP2P/RNBQKBNR b KQkq - 0 2",
)

/**
 * A free board to try the step 1B interaction and motion: both sides move, with tap-tap or
 * drag and drop, sounds, haptics, takebacks and the fade-through flip. Uses the player's
 * board theme and settings. During development only (Account > During development).
 */
@Composable
fun BoardLabScreen(
    settings: AppSettings,
    onFeedback: (BoardFeedback) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = remember { FreePlaySession() }
    val state by session.state.collectAsState()
    var whiteAtBottom by remember { mutableStateOf(true) }
    var positionName by remember { mutableStateOf(LAB_POSITIONS.first().first) }

    CtScreenBackground(modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            CtTopBar(title = "Board lab", subtitle = "Try moves, sounds and motion", onBack = onBack)
            Column(
                modifier = Modifier.padding(horizontal = CtSpacing.gutter),
                verticalArrangement = Arrangement.spacedBy(CtSpacing.md),
            ) {
                BoardHost(
                    session = session,
                    theme = settings.boardTheme,
                    whiteAtBottom = whiteAtBottom,
                    showCoordinates = settings.showCoordinates,
                    showLegalDots = settings.showLegalDots,
                    autoQueen = settings.autoQueen,
                    onFeedback = onFeedback,
                )
                val status = when (state.movable) {
                    PieceColor.WHITE -> "White to move"
                    PieceColor.BLACK -> "Black to move"
                    null -> "Game over"
                }
                Text(status, style = CtType.Label)
                Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                    CtButton(
                        text = "Flip",
                        icon = Icons.Rounded.SwapVert,
                        style = CtButtonStyle.Secondary,
                        onClick = { whiteAtBottom = !whiteAtBottom },
                        modifier = Modifier.weight(1f),
                    )
                    CtButton(
                        text = "Take back",
                        icon = Icons.AutoMirrored.Rounded.Undo,
                        style = CtButtonStyle.Secondary,
                        enabled = state.version > 0 && session.canUndo,
                        onClick = { session.undo() },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm),
                ) {
                    LAB_POSITIONS.forEach { (name, fen) ->
                        CtChip(
                            text = name,
                            selected = name == positionName,
                            onClick = {
                                positionName = name
                                session.reset(Position.fromFen(fen))
                            },
                        )
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
