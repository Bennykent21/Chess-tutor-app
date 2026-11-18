package com.chesstutor.app.domain.account

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Position
import com.chesstutor.app.engine.ChessEngineManager
import com.chesstutor.app.engine.ManagerEngineService
import com.chesstutor.app.engine.StockfishProcessEngineClient
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Explore's grading against the real Stockfish of this PC (tools/.cache/stockfish), the way the
 * screen asks it: three lines for 1.5 s on each side of a move. Skipped where that binary is missing.
 */
class ExploreRealEngineTest {

    private val binary = File("../tools/.cache/stockfish/stockfish/stockfish-windows-x86-64-universal.exe")

    @Test
    fun movesAreGradedLikeAReview() = runBlocking {
        assumeTrue(binary.exists())
        val engine = ManagerEngineService(ChessEngineManager(StockfishProcessEngineClient(binary.absolutePath)))
        val cases = listOf(
            // Black hangs the queen to the knight.
            "rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2" to "d8h4",
            // White hangs the queen on h5 to the knight.
            "r1bqkb1r/pppp1ppp/2n2n2/4p3/4P3/2N5/PPPP1PPP/R1BQKBNR w KQkq - 4 4" to "d1h5",
            // A good move for White and one for Black.
            "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2" to "g1f3",
            "rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2" to "b8c6",
        )
        for ((fen, uci) in cases) {
            val before = Position.fromFen(fen)
            val after = LegalMoveGenerator.makeMove(before, LegalMoveGenerator.generateLegalMoves(before).first { it.uci == uci })
            val beforeEval = ExploreJudge.evalOf(before, engine.analyse(before.toFen(), lines = 3, movetimeMs = 1500))!!
            val afterEval = ExploreJudge.evalOf(after, engine.analyse(after.toFen(), lines = 3, movetimeMs = 1500))!!
            val judged = ExploreJudge.judge(before, uci, beforeEval, afterEval)!!
            println("EXPLORE $uci: ${judged.move.quality} before=${beforeEval.centipawns}/${beforeEval.mate} best=${beforeEval.bestMove} after=${afterEval.centipawns}/${afterEval.mate} win ${judged.move.winBefore}->${judged.move.winAfter}")
        }
    }
}
