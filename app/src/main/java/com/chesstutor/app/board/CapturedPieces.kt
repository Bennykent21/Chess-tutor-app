package com.chesstutor.app.board

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtShapes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chesstutor.app.core.Piece
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.PieceType
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType

/**
 * The pieces one player has taken, in small overlapping groups by kind, and "+N" when that
 * player is ahead on material (as a chess app shows beside each player's name). [color] is
 * the colour of the taken pieces, so the opponent's colour. Shows nothing before a capture.
 */
@Composable
fun CapturedPieces(
    taken: List<PieceType>,
    color: PieceColor,
    lead: Int?,
    pieceSet: PieceSet,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
) {
    if (taken.isEmpty() && lead == null) return
    val groups = taken.groupBy { it }.toList()
    // A light pill, so black pieces stay visible on the dark screen (and white ones keep their outline).
    Row(
        modifier = modifier
            .clip(CtShapes.Pill)
            .background(CtColors.Text)
            .padding(horizontal = CtSpacing.sm, vertical = 1.dp)
            .height(size)
            .semantics { contentDescription = describe(taken, lead) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.xs),
    ) {
        groups.forEach { (type, pieces) ->
            // Each further piece of a kind overlaps the one before by half its width.
            val step = size / 2
            Box(Modifier.width(size + step * (pieces.size - 1)).height(size)) {
                pieces.indices.forEach { i ->
                    Image(
                        painter = painterResource(PieceArt.drawable(pieceSet, Piece(type, color))),
                        contentDescription = null,
                        modifier = Modifier.offset(x = step * i).size(size),
                    )
                }
            }
        }
        if (lead != null) Text("+$lead", style = CtType.Label.copy(color = CtColors.BgDeep))
    }
}

/** "Taken: 2 pawns and a knight. Ahead by 4." for screen readers. */
internal fun describe(taken: List<PieceType>, lead: Int?): String {
    val parts = taken.groupingBy { it }.eachCount().map { (type, n) ->
        val name = type.name.lowercase()
        if (n == 1) "a $name" else "$n ${name}s"
    }
    val list = when (parts.size) {
        0 -> null
        1 -> parts[0]
        else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
    }
    return listOfNotNull(list?.let { "Taken: $it." }, lead?.let { "Ahead by $it." }).joinToString(" ")
}
