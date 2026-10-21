package com.chesstutor.app.feature.account

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.text.style.TextAlign
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
    val onSound: (Boolean) -> Unit,
    val onHaptics: (Boolean) -> Unit,
    val onReduceMotion: (Boolean) -> Unit,
)

private val PREVIEW_POSITION = Position.fromFen("r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4")
private val PREVIEW_SELECTED = Square.fromAlgebraic("f3")
private val PREVIEW_MARKS = BoardMarks(
    lastMove = Square.fromAlgebraic("f8") to Square.fromAlgebraic("c5"),
    selected = PREVIEW_SELECTED,
    legalTargets = BoardInput.targetsFrom(PREVIEW_SELECTED, LegalMoveGenerator.generateLegalMoves(PREVIEW_POSITION)),
)

/**
 * Board and pieces (plan Phase 1, step 1D): every board setting with a live preview at the
 * top. Changes apply to every new board in the app at once.
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            CtTopBar(title = "Board and pieces", subtitle = "Changes show here and on every board", onBack = onBack)
            Column(
                modifier = Modifier.padding(horizontal = CtSpacing.gutter),
                verticalArrangement = Arrangement.spacedBy(CtSpacing.sm),
            ) {
                BoardView(
                    position = PREVIEW_POSITION,
                    theme = theme,
                    showCoordinates = settings.showCoordinates,
                    showLegalDots = settings.showLegalDots,
                    marks = PREVIEW_MARKS,
                )
                Text("Pieces: ${theme.pieceSet.credit}", style = CtType.Caption)

                CtSectionHeader(title = "Pieces")
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                    PieceSet.entries.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                            row.forEach { set ->
                                PieceSetCard(set, selected = set == theme.pieceSet, onSelect = { actions.onPieceSet(set) }, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                CtSectionHeader(title = "Board colours")
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                    BoardColors.entries.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                            row.forEach { colors ->
                                ColorSwatch(colors, selected = colors == theme.colors, onSelect = { actions.onBoardColors(colors) }, modifier = Modifier.weight(1f))
                            }
                            // Keep the last row's swatches the same size as the others.
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }

                CtSectionHeader(title = "On the board")
                CtToggleRow("Coordinates", settings.showCoordinates, actions.onShowCoordinates, subtitle = "Letters and numbers along the edges")
                CtToggleRow("Legal-move dots", settings.showLegalDots, actions.onShowLegalDots, subtitle = "Show where a selected piece can go")
                CtToggleRow("Always promote to a queen", settings.autoQueen, actions.onAutoQueen, subtitle = "Skip the choice of piece")
                CtToggleRow(
                    title = "White always at the bottom",
                    checked = settings.boardOrientation == BoardOrientation.WHITE_ALWAYS_BOTTOM,
                    onCheckedChange = { on -> actions.onOrientation(if (on) BoardOrientation.WHITE_ALWAYS_BOTTOM else BoardOrientation.FOLLOW_SIDE) },
                    subtitle = "Otherwise the board faces the side you play",
                )

                CtSectionHeader(title = "Feel")
                CtToggleRow("Sounds", settings.soundEnabled, actions.onSound, subtitle = "Moves, captures, checks and results")
                CtToggleRow("Vibration", settings.hapticsEnabled, actions.onHaptics, subtitle = "A light tick on moves, a buzz when wrong")
                CtToggleRow("Reduce motion", settings.reduceMotion, actions.onReduceMotion, subtitle = "Pieces jump instead of sliding")
                Spacer(Modifier.height(CtSpacing.lg))
            }
        }
    }
}

@Composable
private fun SelectableTile(selected: Boolean, onSelect: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .clip(CtShapes.CardSmall)
            .background(if (selected) CtColors.SurfaceRaised else CtColors.Surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) CtColors.Gold else CtColors.Line, CtShapes.CardSmall)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(CtSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun PieceSetCard(set: PieceSet, selected: Boolean, onSelect: () -> Unit, modifier: Modifier) {
    SelectableTile(selected, onSelect, modifier) {
        Row {
            listOf(Piece(PieceType.KING, PieceColor.WHITE), Piece(PieceType.KNIGHT, PieceColor.BLACK), Piece(PieceType.PAWN, PieceColor.WHITE)).forEach {
                Image(painterResource(PieceArt.drawable(set, it)), contentDescription = null, modifier = Modifier.size(40.dp))
            }
        }
        Text(set.displayName, style = CtType.Label, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ColorSwatch(colors: BoardColors, selected: Boolean, onSelect: () -> Unit, modifier: Modifier) {
    SelectableTile(selected, onSelect, modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CtShapes.Chip),
        ) {
            val half = size.minDimension / 2f
            val light = Color(colors.light)
            val dark = Color(colors.dark)
            drawRect(light, Offset.Zero, Size(half, half))
            drawRect(dark, Offset(half, 0f), Size(half, half))
            drawRect(dark, Offset(0f, half), Size(half, half))
            drawRect(light, Offset(half, half), Size(half, half))
        }
        Text(colors.displayName, style = CtType.Caption.copy(color = CtColors.Text), textAlign = TextAlign.Center, maxLines = 2)
    }
}
