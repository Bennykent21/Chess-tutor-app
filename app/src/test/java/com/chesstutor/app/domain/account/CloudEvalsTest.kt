package com.chesstutor.app.domain.account

import com.chesstutor.app.data.account.InMemoryGameAnalysisRepository
import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.domain.opening.OpeningTree
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Lichess cloud evaluations for the opening of a review (D86). */
class CloudEvalsTest {

    private val afterNf3 = "rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2"

    @Test
    fun lichessAnswersBecomeTheAppsEvalsWithCastlingInItsOwnSpelling() {
        // Lichess's real answer for 1.e4 e5 2.Nf3 (15 Nov 2026).
        val json = """{"fen":"$afterNf3","knodes":9319346,"depth":57,"pvs":[{"moves":"b8c6 f1b5 g8f6 e1h1 f6e4 f1e1 e4d6 f3e5 f8e7 b5f1","cp":13},{"moves":"g8f6 f3e5 d7d6 e5f3 f6e4 d2d4 d6d5 f1d3 f8d6 e1h1","cp":27}]}"""
        val eval = CloudEvals.parseLichess(3, afterNf3, json)!!
        assertEquals(3, eval.ply)
        assertEquals(13, eval.centipawns)
        assertEquals("b8c6", eval.bestMove)
        assertEquals(listOf("b8c6", "f1b5", "g8f6", "e1g1", "f6e4", "f1e1", "e4d6", "f3e5"), eval.line)
        assertEquals(57, eval.depth)
        assertEquals(27, eval.secondCentipawns)
        assertNull(CloudEvals.parseLichess(3, afterNf3, """{"error":"Not found"}"""))
    }

    @Test
    fun theOpeningComesFromTheCloudUntilItRunsOut() = runTest {
        val game = GameLibrary.summarise(
            GameRecord(id = "g", dateMillis = 1, botName = "Pip", botRating = 800, result = "1-0", pgn = "", moveCount = 4, userColor = "white", finalFen = "", uciMoves = "e2e4 e7e5 g1f3 b8c6"),
            OpeningTree.EMPTY,
        )
        val asked = mutableListOf<Int>()
        // The cloud holds the first three positions, the first one too shallow to use.
        val cloud = CloudEvals { ply, _ ->
            asked += ply
            when (ply) {
                0 -> PlyEval(ply, 20, null, "e2e4", depth = 12)
                1, 2 -> PlyEval(ply, 20, null, "e7e5", depth = 40)
                else -> null
            }
        }
        val engine = GameAnalyzerTest.FakeEngine()
        val repo = InMemoryGameAnalysisRepository()
        GameAnalyzer(engine, repo, cloud = cloud).analyse(game)
        assertEquals("a shallow answer counts as not held", listOf(0), asked)

        val again = InMemoryGameAnalysisRepository()
        asked.clear()
        val deep = CloudEvals { ply, _ -> asked += ply; if (ply < 3) PlyEval(ply, 20, null, "e2e4", depth = 40) else null }
        GameAnalyzer(engine, again, cloud = deep).analyse(game)
        assertEquals(listOf(0, 1, 2, 3), asked)
        assertEquals(listOf(40, 40, 40, 18, 18), again.evals("g").sortedBy { it.ply }.map { it.depth })
    }
}
