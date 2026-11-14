package com.chesstutor.app.core

import kotlin.random.Random

/**
 * Chess960 (Fischer Random) start positions, numbered 0 to 959 as Scharnagl did; 518 is the
 * normal start. The FEN names the rooks' files, so the position castles the Chess960 way.
 */
object Chess960 {
    const val COUNT = 960
    const val STANDARD = 518

    /** Where the two knights stand among the five squares left after the bishops and the queen. */
    private val KNIGHTS = listOf(0 to 1, 0 to 2, 0 to 3, 0 to 4, 1 to 2, 1 to 3, 1 to 4, 2 to 3, 2 to 4, 3 to 4)

    /** White's back rank for [number], as piece letters from a to h ("RNBQKBNR" for 518). */
    fun backRank(number: Int): String {
        require(number in 0 until COUNT) { "Chess960 positions are numbered 0 to 959" }
        val rank = CharArray(8) { ' ' }
        var n = number
        rank[2 * (n % 4) + 1] = 'B'
        n /= 4
        rank[2 * (n % 4)] = 'B'
        n /= 4
        val queen = n % 6
        n /= 6
        emptySquares(rank)[queen].let { rank[it] = 'Q' }
        val (a, b) = KNIGHTS[n]
        val free = emptySquares(rank)
        rank[free[a]] = 'N'
        rank[free[b]] = 'N'
        // The last three: rook, king, rook, so the king always stands between its rooks.
        emptySquares(rank).zip("RKR".toList()).forEach { (i, piece) -> rank[i] = piece }
        return String(rank)
    }

    /** The start position for [number], with castling written by the rooks' files ("HAha"). */
    fun startFen(number: Int): String {
        val white = backRank(number)
        val rooks = white.indices.filter { white[it] == 'R' }
        val files = "${'A' + rooks[1]}${'A' + rooks[0]}${'a' + rooks[1]}${'a' + rooks[0]}"
        return "${white.lowercase()}/pppppppp/8/8/8/8/PPPPPPPP/$white w $files - 0 1"
    }

    fun random(random: Random = Random.Default): Int = random.nextInt(COUNT)

    /** True when [fen] is a Chess960 position (its castling names the rooks' files). */
    fun isChess960(fen: String): Boolean = fen.split(' ').getOrNull(2)?.any { it.lowercaseChar() in 'a'..'h' } == true

    private fun emptySquares(rank: CharArray): List<Int> = rank.indices.filter { rank[it] == ' ' }
}
