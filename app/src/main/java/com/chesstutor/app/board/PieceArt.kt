package com.chesstutor.app.board

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.chesstutor.app.R
import com.chesstutor.app.core.Piece
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.PieceType

/**
 * Drawables for every piece of every set, generated from `design/pieces/` by
 * `tools/PieceSvgToVector.java`. Listed explicitly (not looked up by name) so the release
 * build's resource shrinker keeps them.
 */
object PieceArt {
    /** Per set: white K, Q, R, B, N, P, then black K, Q, R, B, N, P. */
    private val drawables: Map<PieceSet, IntArray> = mapOf(
        PieceSet.CBURNETT to intArrayOf(
            R.drawable.piece_cburnett_wk, R.drawable.piece_cburnett_wq, R.drawable.piece_cburnett_wr,
            R.drawable.piece_cburnett_wb, R.drawable.piece_cburnett_wn, R.drawable.piece_cburnett_wp,
            R.drawable.piece_cburnett_bk, R.drawable.piece_cburnett_bq, R.drawable.piece_cburnett_br,
            R.drawable.piece_cburnett_bb, R.drawable.piece_cburnett_bn, R.drawable.piece_cburnett_bp,
        ),
        PieceSet.MERIDA to intArrayOf(
            R.drawable.piece_merida_wk, R.drawable.piece_merida_wq, R.drawable.piece_merida_wr,
            R.drawable.piece_merida_wb, R.drawable.piece_merida_wn, R.drawable.piece_merida_wp,
            R.drawable.piece_merida_bk, R.drawable.piece_merida_bq, R.drawable.piece_merida_br,
            R.drawable.piece_merida_bb, R.drawable.piece_merida_bn, R.drawable.piece_merida_bp,
        ),
        PieceSet.CHESSNUT to intArrayOf(
            R.drawable.piece_chessnut_wk, R.drawable.piece_chessnut_wq, R.drawable.piece_chessnut_wr,
            R.drawable.piece_chessnut_wb, R.drawable.piece_chessnut_wn, R.drawable.piece_chessnut_wp,
            R.drawable.piece_chessnut_bk, R.drawable.piece_chessnut_bq, R.drawable.piece_chessnut_br,
            R.drawable.piece_chessnut_bb, R.drawable.piece_chessnut_bn, R.drawable.piece_chessnut_bp,
        ),
        PieceSet.FANTASY to intArrayOf(
            R.drawable.piece_fantasy_wk, R.drawable.piece_fantasy_wq, R.drawable.piece_fantasy_wr,
            R.drawable.piece_fantasy_wb, R.drawable.piece_fantasy_wn, R.drawable.piece_fantasy_wp,
            R.drawable.piece_fantasy_bk, R.drawable.piece_fantasy_bq, R.drawable.piece_fantasy_br,
            R.drawable.piece_fantasy_bb, R.drawable.piece_fantasy_bn, R.drawable.piece_fantasy_bp,
        ),
    )

    private val typeOrder = listOf(PieceType.KING, PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT, PieceType.PAWN)

    /** Every piece, in the order the drawable tables use. */
    val allPieces: List<Piece> = listOf(PieceColor.WHITE, PieceColor.BLACK).flatMap { color ->
        typeOrder.map { Piece(it, color) }
    }

    @DrawableRes
    fun drawable(set: PieceSet, piece: Piece): Int {
        val colorOffset = if (piece.color == PieceColor.WHITE) 0 else typeOrder.size
        return drawables.getValue(set)[colorOffset + typeOrder.indexOf(piece.type)]
    }
}

/** The twelve piece painters of [set], for drawing a whole board in one Canvas. */
@Composable
fun rememberPiecePainters(set: PieceSet): Map<Piece, Painter> {
    // painterResource caches each vector, so this is cheap on recomposition.
    return PieceArt.allPieces.associateWith { painterResource(PieceArt.drawable(set, it)) }
}
