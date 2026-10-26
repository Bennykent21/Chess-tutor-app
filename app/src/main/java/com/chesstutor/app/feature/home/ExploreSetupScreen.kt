package com.chesstutor.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.chesstutor.app.board.BoardTheme
import com.chesstutor.app.core.Piece
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.PieceType
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtChip
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.domain.game.SetupBoard

/** Who plays which side in Explore: the player alone, or the player against the engine. */
enum class ExploreSide(val label: String) {
    BOTH("Both sides"),
    WHITE("White"),
    BLACK("Black");

    /** The side the engine plays, or null when the player moves for both. */
    val engineSide: PieceColor? get() = when (this) {
        BOTH -> null
        WHITE -> PieceColor.BLACK
        BLACK -> PieceColor.WHITE
    }
}

/** Explore's start page: the position, the player's side and the engine's strength (D32). */
data class ExploreDraft(
    val board: SetupBoard = SetupBoard.START,
    val side: ExploreSide = ExploreSide.BOTH,
    /** The engine's strength when it plays a side; null is full strength. */
    val strength: Int? = 1200,
    val brush: Piece? = Piece(PieceType.QUEEN, PieceColor.WHITE),
)

/** The strengths offered for Explore's engine; null is full strength. */
val EXPLORE_STRENGTHS: List<Int?> = listOf(800, 1200, 1600, 2000, null)

/**
 * Explore's start page (D32): set up any position (or keep the usual start), choose to move
 * for both sides or to play one side against the engine, then explore it with every move judged.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExploreSetupScreen(
    draft: ExploreDraft,
    theme: BoardTheme,
    onChange: ((ExploreDraft) -> ExploreDraft) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    val problem = remember(draft.board) { draft.board.problem() }
    CtScreenBackground {
        Column(Modifier.fillMaxSize()) {
            CtTopBar(title = "Explore", subtitle = "Any position, every move judged", onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = CtSpacing.gutter),
                verticalArrangement = Arrangement.spacedBy(CtSpacing.md),
            ) {
                Text(
                    "Play moves from any position and the engine tells you how good each one was, like a game review as you go. Take moves back and try other ideas whenever you like.",
                    style = CtType.Body,
                )
                PositionEditor(
                    board = draft.board,
                    brush = draft.brush,
                    theme = theme,
                    onBoard = { next -> onChange { it.copy(board = next) } },
                    onBrush = { brush -> onChange { it.copy(brush = brush) } },
                )
                CtSectionHeader(title = "You play")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm), verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                    ExploreSide.entries.forEach { side ->
                        CtChip(text = side.label, selected = draft.side == side, color = CtColors.Gold, onClick = { onChange { it.copy(side = side) } })
                    }
                }
                if (draft.side != ExploreSide.BOTH) {
                    CtSectionHeader(title = "Engine strength")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm), verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                        EXPLORE_STRENGTHS.forEach { elo ->
                            CtChip(text = elo?.toString() ?: "Full strength", selected = draft.strength == elo, color = CtColors.Violet, onClick = { onChange { it.copy(strength = elo) } })
                        }
                    }
                }
                Spacer(Modifier.height(CtSpacing.sm))
                CtButton(
                    text = "Explore",
                    icon = Icons.Rounded.Search,
                    enabled = problem == null,
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(CtSpacing.lg))
            }
        }
    }
}
