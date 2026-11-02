package com.chesstutor.app.feature.home

import androidx.compose.runtime.Composable
import com.chesstutor.app.board.BoardTheme
import com.chesstutor.app.core.Piece
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.PieceType
import com.chesstutor.app.domain.game.SetupBoard
import com.chesstutor.app.design.CtGroupLabel
import com.chesstutor.app.design.CtSegmented


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
 * Explore's start page (D32, mock-up round 17): set up any position on the fixed board (or
 * keep the usual start), choose to move for both sides or to play one side against the engine.
 */
@Composable
fun ExploreSetupScreen(
    draft: ExploreDraft,
    theme: BoardTheme,
    onChange: ((ExploreDraft) -> ExploreDraft) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    SetupBoardScreen(
        title = "Explore",
        board = draft.board,
        brush = draft.brush,
        theme = theme,
        onBoard = { next -> onChange { it.copy(board = next) } },
        onBrush = { brush -> onChange { it.copy(brush = brush) } },
        onBack = onBack,
        startLabel = "Explore",
        onStart = onStart,
    ) {
        CtGroupLabel("You play")
        CtSegmented(ExploreSide.entries, draft.side, { it.label }, { side -> onChange { it.copy(side = side) } })
        if (draft.side != ExploreSide.BOTH) {
            CtGroupLabel("Engine strength")
            CtSegmented(EXPLORE_STRENGTHS, draft.strength, { it?.toString() ?: "Full" }, { elo -> onChange { it.copy(strength = elo) } })
        }
    }
}
