package com.chesstutor.app.feature.account

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chesstutor.app.board.BoardColors
import com.chesstutor.app.board.BoardInput
import com.chesstutor.app.board.BoardMarks
import com.chesstutor.app.board.BoardView
import com.chesstutor.app.board.PieceArt
import com.chesstutor.app.board.PieceSet
import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Piece
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.PieceType
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.Square
import com.chesstutor.app.data.settings.AppSettings
import com.chesstutor.app.data.settings.BoardOrientation
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtToggleRow
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType

/** Changes from the board settings page. Each one is saved straight away. */
class BoardSettingsActions(
    val onPieceSet: (PieceSet) -> Unit,
    val onBoardColors: (BoardColors) -> Unit,
    val onShowCoordinates: (Boolean) -> Unit,
    val onShowLegalDots: (Boolean) -> Unit,
    val onAutoQueen: (Boolean) -> Unit,
    val onOrientation: (BoardOrientation) -> Unit,
)

private val PREVIEW_POSITION = Position.fromFen("r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4")
private val PREVIEW_SELECTED = Square.fromAlgebraic("f3")
private val PREVIEW_MARKS = BoardMarks(
    lastMove = Square.fromAlgebraic("f8") to Square.fromAlgebraic("c5"),
    selected = PREVIEW_SELECTED,
    legalTargets = BoardInput.targetsFrom(PREVIEW_SELECTED, LegalMoveGenerator.generateLegalMoves(PREVIEW_POSITION)),
)

/**
 * Board and pieces (mock-up round 12): a preview board that stays put at the top while the
 * choices scroll below it, so every change shows at once. Changes apply to every board.
 * Sound, vibration and reduce motion live in Settings only.
 */
@Composable
fun BoardSettingsScreen(
    settings: AppSettings,
    actions: BoardSettingsActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = settings.boardTheme
    CtScreenBackground(modifier) {
        Column(Modifier.fillMaxSize()) {
            CtTopBar(title = "Board and pieces", onBack = onBack)
            BoardView(
                position = PREVIEW_POSITION,
                theme = theme,
                showCoordinates = settings.showCoordinates,
                showLegalDots = settings.showLegalDots,
                marks = PREVIEW_MARKS,
                modifier = Modifier.padding(horizontal = CtSpacing.gutter, vertical = CtSpacing.xs),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = CtSpacing.gutter, end = CtSpacing.gutter, bottom = CtSpacing.xl),
            ) {
                CtSectionHeader(title = "Pieces")
                Row(
                    modifier = Modifier.selectableGroup().padding(top = CtSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PieceSet.entries.forEach { set ->
                        PieceSetCard(set, selected = set == theme.pieceSet, onSelect = { actions.onPieceSet(set) }, modifier = Modifier.weight(1f))
                    }
                }

                CtSectionHeader(title = "Board")
                Row(
                    modifier = Modifier.fillMaxWidth().selectableGroup().padding(top = CtSpacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    BoardColors.entries.forEach { colors ->
                        ColorSwatch(colors, selected = colors == theme.colors, onSelect = { actions.onBoardColors(colors) })
                    }
                }

                Column(Modifier.padding(top = CtSpacing.lg)) {
                    CtToggleRow("Coordinates", settings.showCoordinates, actions.onShowCoordinates)
                    CtToggleRow("Move dots", settings.showLegalDots, actions.onShowLegalDots)
                    CtToggleRow("Always promote to a queen", settings.autoQueen, actions.onAutoQueen)
                    CtToggleRow(
                        title = "White always at the bottom",
                        checked = settings.boardOrientation == BoardOrientation.WHITE_ALWAYS_BOTTOM,
                        onCheckedChange = { on -> actions.onOrientation(if (on) BoardOrientation.WHITE_ALWAYS_BOTTOM else BoardOrientation.FOLLOW_SIDE) },
                        divider = false,
                    )
                }
            }
        }
    }
}

/** A white knight in the set's own art, with the set's name; the chosen one is raised and ringed. */
@Composable
private fun PieceSetCard(set: PieceSet, selected: Boolean, onSelect: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .height(92.dp)
            .clip(CtShapes.CardSmall)
            .background(if (selected) CtColors.Raised else CtColors.Surface)
            .then(if (selected) Modifier.border(2.dp, CtColors.Accent, CtShapes.CardSmall) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CtSpacing.xs, Alignment.CenterVertically),
    ) {
        Image(painterResource(PieceArt.drawable(set, Piece(PieceType.KNIGHT, PieceColor.WHITE))), contentDescription = null, modifier = Modifier.size(48.dp))
        Text(
            text = set.displayName,
            style = if (selected) CtType.Label else CtType.Caption,
            maxLines = 1,
        )
    }
}

/** Four squares of the board's colours; the name is for screen readers only. */
@Composable
private fun ColorSwatch(colors: BoardColors, selected: Boolean, onSelect: () -> Unit) {
    val ring = RoundedCornerShape(15.dp)
    Box(
        modifier = Modifier
            .size(44.dp)
            .then(if (selected) Modifier.border(2.dp, CtColors.Accent, ring) else Modifier)
            .clip(ring)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .semantics { contentDescription = colors.displayName }
            .padding(4.dp),
    ) {
        Canvas(Modifier.fillMaxSize().clip(RoundedCornerShape(11.dp))) {
            val half = size.minDimension / 2f
            val light = Color(colors.light)
            val dark = Color(colors.dark)
            drawRect(light, Offset.Zero, Size(half, half))
            drawRect(dark, Offset(half, 0f), Size(half, half))
            drawRect(dark, Offset(0f, half), Size(half, half))
            drawRect(light, Offset(half, half), Size(half, half))
        }
    }
}
