package com.chesstutor.app.board

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.max
import com.chesstutor.app.core.Position

/** Positions used purely as pictures, on tiles that have no position of their own. */
object BoardScenes {
    const val OPENING = "r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R b KQkq - 3 3"
    const val MIDDLEGAME = "r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/2NP1N2/PPP2PPP/R1BQK2R b KQkq - 0 5"
    const val ATTACK = "r1b2rk1/ppq2ppp/2p5/4n3/4P3/2N1BP2/PPP3PP/R2Q1RK1 w - - 0 14"
    const val SETUP = "2r3k1/pp3ppp/2n1b3/3p4/3P4/2N1B3/PP3PPP/2R3K1 w - - 0 20"
}

/** A board filling its box, cropped to the middle: a picture, with no coordinates and nothing to tap. */
@Composable
fun BoardPicture(fen: String?, theme: BoardTheme, whiteAtBottom: Boolean = true, zoom: Float = 1.5f) {
    val position = remember(fen) { fen?.let { Position.tryFromFen(it).getOrNull() } ?: Position.initial() }
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        BoardView(
            position = position,
            theme = theme,
            modifier = Modifier.requiredSize(max(maxWidth, maxHeight) * zoom),
            whiteAtBottom = whiteAtBottom,
            showCoordinates = false,
            showLegalDots = false,
        )
    }
}
