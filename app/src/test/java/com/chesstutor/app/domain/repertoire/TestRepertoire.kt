package com.chesstutor.app.domain.repertoire

import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.repetitionKey
import com.chesstutor.app.domain.repertoire.RepertoireLevel.CORE
import com.chesstutor.app.domain.repertoire.RepertoireLevel.DEEP
import com.chesstutor.app.domain.repertoire.RepertoireLevel.STANDARD

/**
 * Small hand-made trees for the trainer tests, where every number is known.
 *
 * The Italian (White): 1.e4 e5 2.Nf3 Nc6 3.Bc4, then 3...Bc5 (50%) 4.c3 Nf6 5.d3;
 * 3...Nf6 (40%) 4.d3, then 4...Be7 (60%, Standard) 5.O-O or 4...Bc5 (40%, Deep) 5.c3;
 * 3...Nd4 (10%, a blunder, Standard) 4.Nxd4 (most players take on e5 instead).
 */
object TestRepertoire {
    const val P = "e2e4 e7e5 g1f3 b8c6 f1c4"

    private val bands = listOf(
        RatingBand("under-1000", 0, 1000), RatingBand("1000-1400", 1000, 1400),
        RatingBand("1400-1800", 1400, 1800), RatingBand("1800-plus", 1800, null),
    )

    fun key(path: String): String {
        var position = Position.initial()
        for (uci in path.split(' ').filter(String::isNotEmpty)) position = RepertoireIndex.play(position, uci)!!
        return position.repetitionKey()
    }

    private fun own(path: String, move: String, level: RepertoireLevel, reach: Double, also: List<String> = emptyList(), pitfall: Pitfall? = null) =
        RepertoireNode(
            key = key(path), ply = path.split(' ').count(String::isNotEmpty), own = true, games = listOf(100, 100, 100, 100),
            dataBacked = true, level = level, reach = reach, evalCp = 30, mate = null, move = move, also = also, pitfall = pitfall,
            replies = emptyList(), note = null, name = null,
        )

    private fun reply(uci: String, share: Double, level: RepertoireLevel, punish: Boolean = false, bandShares: List<Double?>? = null) =
        RepertoireReply(
            uci = uci, games = listOf(25, 25, 25, 25), share = share, bandShares = bandShares ?: List(4) { share },
            dataBacked = true, verdict = if (punish) MoveVerdict.BLUNDER else MoveVerdict.SOUND, loss = if (punish) 30.0 else 1.0,
            punish = punish, level = level,
        )

    private fun opponent(path: String, level: RepertoireLevel, reach: Double, vararg replies: RepertoireReply) =
        RepertoireNode(
            key = key(path), ply = path.split(' ').count(String::isNotEmpty), own = false, games = listOf(100, 100, 100, 100),
            dataBacked = true, level = level, reach = reach, evalCp = 30, mate = null, move = null, also = emptyList(),
            pitfall = null, replies = replies.toList(), note = null, name = null,
        )

    fun italian(): RepertoireBook = RepertoireBook(
        id = "italian", title = "Italian Game", side = PieceColor.WHITE, start = P.split(' '),
        source = RepertoireSource("test", 1000, bands, 1000, 0.03, "test"),
        nodeList = listOf(
            own("", "e2e4", CORE, 1.0, also = listOf("d2d4")),
            opponent("e2e4", CORE, 1.0, reply("e7e5", 1.0, CORE)),
            own("e2e4 e7e5", "g1f3", CORE, 1.0),
            opponent("e2e4 e7e5 g1f3", CORE, 1.0, reply("b8c6", 1.0, CORE)),
            own("e2e4 e7e5 g1f3 b8c6", "f1c4", CORE, 1.0),
            opponent(
                P, CORE, 1.0,
                // Players under 1000 choose 3...Nd4 far more often.
                reply("f8c5", 0.5, CORE, bandShares = listOf(0.3, 0.5, 0.5, 0.6)),
                reply("g8f6", 0.4, CORE, bandShares = listOf(0.3, 0.4, 0.4, 0.4)),
                reply("c6d4", 0.1, STANDARD, punish = true, bandShares = listOf(0.4, 0.1, 0.1, null)),
            ),
            own("$P f8c5", "c2c3", CORE, 0.5, also = listOf("d2d3", "e1g1")),
            opponent("$P f8c5 c2c3", CORE, 0.5, reply("g8f6", 1.0, CORE)),
            own("$P f8c5 c2c3 g8f6", "d2d3", CORE, 0.5),
            own("$P g8f6", "d2d3", CORE, 0.4),
            opponent("$P g8f6 d2d3", CORE, 0.4, reply("f8e7", 0.6, STANDARD), reply("f8c5", 0.4, DEEP)),
            own("$P g8f6 d2d3 f8e7", "e1g1", STANDARD, 0.24),
            own("$P g8f6 d2d3 f8c5", "c2c3", DEEP, 0.16),
            own("$P c6d4", "f3d4", STANDARD, 0.1, pitfall = Pitfall("f3e5", 0.6, MoveVerdict.BLUNDER, 25.0)),
        ),
    )

    /** The Caro-Kann (Black), short: 1.e4 c6 2.d4 d5, then 3.e5 Bf5 or 3.Nc3 dxe4. */
    fun caroKann(): RepertoireBook = RepertoireBook(
        id = "caro-kann", title = "Caro-Kann Defence", side = PieceColor.BLACK, start = listOf("e2e4", "c7c6"),
        source = RepertoireSource("test", 1000, bands, 1000, 0.03, "test"),
        nodeList = listOf(
            opponent("", CORE, 1.0, reply("e2e4", 1.0, CORE)),
            own("e2e4", "c7c6", CORE, 1.0),
            opponent("e2e4 c7c6", CORE, 1.0, reply("d2d4", 1.0, CORE)),
            own("e2e4 c7c6 d2d4", "d7d5", CORE, 1.0),
            opponent("e2e4 c7c6 d2d4 d7d5", CORE, 1.0, reply("e4e5", 0.6, CORE), reply("b1c3", 0.4, CORE)),
            own("e2e4 c7c6 d2d4 d7d5 e4e5", "c8f5", CORE, 0.6),
            own("e2e4 c7c6 d2d4 d7d5 b1c3", "d5e4", CORE, 0.4),
        ),
    )
}
