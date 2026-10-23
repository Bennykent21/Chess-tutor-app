package com.chesstutor.app.domain.bot.maia

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.PieceType
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.repetitionKey
import java.io.DataInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * One Maia network (CSSLab/maia-chess, GPL-3.0; plan D26): a small residual network trained
 * on human games of one rating band that predicts the move a human of that rating plays.
 * Only the policy part ships (`assets/maia/maia-XXXX.bin`, written by tools/maia_net.py with
 * batch norm folded in). Checked against lc0 to within 0.1 % per move (MaiaNetTest).
 */
class MaiaNet private constructor(
    private val blocks: Int,
    private val channels: Int,
    private val seHidden: Int,
    private val inputW: FloatArray,
    private val inputB: FloatArray,
    private val residual: List<Block>,
    private val policy1W: FloatArray,
    private val policy1B: FloatArray,
    private val policyW: FloatArray,
    private val policyB: FloatArray,
) {
    private class Block(
        val conv1W: FloatArray, val conv1B: FloatArray,
        val conv2W: FloatArray, val conv2B: FloatArray,
        val seW1: FloatArray, val seB1: FloatArray,
        val seW2: FloatArray, val seB2: FloatArray,
    )

    /** The 80 x 64 policy outputs for 112 x 64 input planes (see [MaiaEncoder]). */
    fun policyLogits(planes: FloatArray): FloatArray {
        require(planes.size == INPUT_PLANES * 64)
        var x = FloatArray(channels * 64)
        conv3(planes, INPUT_PLANES, inputW, inputB, channels, x)
        relu(x)
        val y = FloatArray(channels * 64)
        val z = FloatArray(channels * 64)
        for (block in residual) {
            conv3(x, channels, block.conv1W, block.conv1B, channels, y)
            relu(y)
            conv3(y, channels, block.conv2W, block.conv2B, channels, z)
            squeezeExcite(z, block)
            for (i in z.indices) z[i] = max(z[i] + x[i], 0f)
            x = z.copyOf()
        }
        conv3(x, channels, policy1W, policy1B, channels, y)
        relu(y)
        val out = FloatArray(POLICY_PLANES * 64)
        conv3(y, channels, policyW, policyB, POLICY_PLANES, out)
        return out
    }

    private fun squeezeExcite(z: FloatArray, block: Block) {
        val pooled = FloatArray(channels) { c -> var sum = 0f; for (s in 0 until 64) sum += z[c * 64 + s]; sum / 64f }
        val hidden = FloatArray(seHidden) { h ->
            var sum = block.seB1[h]
            for (c in 0 until channels) sum += block.seW1[h * channels + c] * pooled[c]
            max(sum, 0f)
        }
        for (c in 0 until channels) {
            var gamma = block.seB2[c]
            var beta = block.seB2[channels + c]
            for (h in 0 until seHidden) {
                gamma += block.seW2[c * seHidden + h] * hidden[h]
                beta += block.seW2[(channels + c) * seHidden + h] * hidden[h]
            }
            val gate = 1f / (1f + exp(-gamma))
            for (s in 0 until 64) z[c * 64 + s] = gate * z[c * 64 + s] + beta
        }
    }

    companion object {
        const val INPUT_PLANES = 112
        const val POLICY_PLANES = 80

        /** Reads a net written by tools/maia_net.py (see its `export` for the layout). */
        fun read(stream: InputStream): MaiaNet {
            val bytes = stream.readBytes()
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = String(bytes, 0, 4, Charsets.US_ASCII)
            require(magic == "MAIA") { "not a Maia net" }
            buf.position(4)
            val version = buf.short.toInt()
            require(version == 1) { "unknown Maia net version $version" }
            val blocks = buf.short.toInt()
            val channels = buf.short.toInt()
            val seHidden = buf.short.toInt()
            fun tensor(): FloatArray {
                val count = buf.int
                val lo = buf.float
                val hi = buf.float
                val step = (hi - lo) / 65535f
                return FloatArray(count) { lo + step * (buf.short.toInt() and 0xFFFF) }
            }
            val inputW = tensor()
            val inputB = tensor()
            val residual = List(blocks) { Block(tensor(), tensor(), tensor(), tensor(), tensor(), tensor(), tensor(), tensor()) }
            val net = MaiaNet(blocks, channels, seHidden, inputW, inputB, residual, tensor(), tensor(), tensor(), tensor())
            require(buf.remaining() == 0) { "Maia net has ${buf.remaining()} unread bytes" }
            return net
        }

        private fun relu(a: FloatArray) {
            for (i in a.indices) if (a[i] < 0f) a[i] = 0f
        }

        /** 3 x 3 convolution with zero padding on an 8 x 8 board; weights are [out][in][3][3]. */
        private fun conv3(input: FloatArray, cIn: Int, w: FloatArray, b: FloatArray, cOut: Int, out: FloatArray) {
            for (o in 0 until cOut) {
                val ob = o * 64
                val bias = b[o]
                for (s in 0 until 64) out[ob + s] = bias
                for (i in 0 until cIn) {
                    val ib = i * 64
                    val wb = (o * cIn + i) * 9
                    for (ky in 0..2) {
                        val dy = ky - 1
                        val y0 = max(0, -dy)
                        val y1 = min(8, 8 - dy)
                        for (kx in 0..2) {
                            val wv = w[wb + ky * 3 + kx]
                            if (wv == 0f) continue
                            val dx = kx - 1
                            val x0 = max(0, -dx)
                            val x1 = min(8, 8 - dx)
                            for (y in y0 until y1) {
                                val row = ob + y * 8
                                val from = ib + (y + dy) * 8 + dx
                                for (x in x0 until x1) out[row + x] += wv * input[from + x]
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Builds Maia's 112 input planes the way lc0 does for its classical input format. */
object MaiaEncoder {
    private val ORDER = listOf(PieceType.PAWN, PieceType.KNIGHT, PieceType.BISHOP, PieceType.ROOK, PieceType.QUEEN, PieceType.KING)

    /**
     * [history] is the game so far, oldest first, ending with the position to move in. A game
     * that started from a set-up position ([fromStandardStart] false) fills the missing history
     * with its first position, as lc0 does (HistoryFill "fen_only").
     */
    fun planes(history: List<Position>, fromStandardStart: Boolean): FloatArray {
        val current = history.last()
        val us = current.sideToMove
        val planes = FloatArray(MaiaNet.INPUT_PLANES * 64)
        val keys = history.map { it.repetitionKey() }
        for (i in 0 until 8) {
            val index = history.size - 1 - i
            val (position, repeated) = when {
                index >= 0 -> history[index] to (keys.subList(0, index).contains(keys[index]))
                !fromStandardStart -> history.first() to false
                else -> break
            }
            val base = i * 13
            for (sq in 0 until 64) {
                val piece = position.squares[sq] ?: continue
                val s = if (us == PieceColor.WHITE) sq else sq xor 56
                val plane = base + ORDER.indexOf(piece.type) + if (piece.color == us) 0 else 6
                planes[plane * 64 + s] = 1f
            }
            if (repeated) fill(planes, base + 12, 1f)
        }
        val rights = current.castlingRights
        val white = us == PieceColor.WHITE
        fill(planes, 104, if (if (white) rights.whiteQueenside else rights.blackQueenside) 1f else 0f)
        fill(planes, 105, if (if (white) rights.whiteKingside else rights.blackKingside) 1f else 0f)
        fill(planes, 106, if (if (white) rights.blackQueenside else rights.whiteQueenside) 1f else 0f)
        fill(planes, 107, if (if (white) rights.blackKingside else rights.whiteKingside) 1f else 0f)
        fill(planes, 108, if (white) 0f else 1f)
        fill(planes, 109, current.halfmoveClock.toFloat())
        fill(planes, 111, 1f)
        return planes
    }

    private fun fill(planes: FloatArray, plane: Int, value: Float) {
        for (s in 0 until 64) planes[plane * 64 + s] = value
    }
}

/**
 * Maia's move probabilities over the legal moves. [slots] maps each policy move name (from
 * the mover's side, knight promotions without a suffix) to its place in the policy output
 * (`assets/maia/maia_moves.tsv`).
 */
class MaiaPolicy(private val net: MaiaNet, private val slots: Map<String, Int>) {

    /** UCI move to probability (summing to 1) for the last position of [history]. */
    fun probabilities(history: List<Position>, fromStandardStart: Boolean): Map<String, Float> {
        val position = history.last()
        val legal = LegalMoveGenerator.generateLegalMoves(position)
        if (legal.isEmpty()) return emptyMap()
        val logits = net.policyLogits(MaiaEncoder.planes(history, fromStandardStart))
        val values = legal.associate { move -> move.uci to logits[slots.getValue(nameOf(position, move))] }
        val top = values.values.max()
        val exps = values.mapValues { (_, v) -> exp(v - top) }
        val total = exps.values.sum()
        return exps.mapValues { (_, v) -> v / total }
    }

    companion object {
        /** The policy name of [move]: seen from the mover's side; knight promotions have no suffix. */
        fun nameOf(position: Position, move: Move): String {
            val flip = position.sideToMove == PieceColor.BLACK
            fun square(index: Int): String {
                val i = if (flip) index xor 56 else index
                return "${'a' + i % 8}${'1' + i / 8}"
            }
            val promotion = move.promotion?.takeIf { it != PieceType.KNIGHT }?.notation?.lowercaseChar()?.toString().orEmpty()
            return square(move.from.index) + square(move.to.index) + promotion
        }

        /** Reads `maia_moves.tsv`: one "name TAB slot" per line. */
        fun readSlots(stream: InputStream): Map<String, Int> =
            stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.filter { it.isNotBlank() }.associate { line -> line.substringBefore('\t') to line.substringAfter('\t').trim().toInt() }
            }
    }
}
