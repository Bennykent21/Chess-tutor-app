package com.chesstutor.app.data.network

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** Regression (live check, 1 Nov 2026): odds games were replayed from the normal start and broke off. */
@RunWith(AndroidJUnit4::class)
class SetUpGameImportTest {

    private fun archive(pgn: String): String = JSONObject()
        .put(
            "games",
            JSONArray().put(
                JSONObject()
                    .put("rules", "chess")
                    .put("pgn", pgn)
                    .put("url", "https://www.chess.com/game/live/1")
                    .put("end_time", 1_700_000_000L)
                    .put("time_class", "rapid")
                    .put("white", JSONObject().put("username", "me").put("rating", 1500).put("result", "win"))
                    .put("black", JSONObject().put("username", "you").put("rating", 1500).put("result", "resigned")),
            ),
        )
        .toString()

    @Test
    fun aGameSetUpFromItsOwnPositionIsReadFromThatPosition() {
        // Castling on the first move is only legal from the game's own position.
        val pgn = "[White \"me\"]\n[Black \"you\"]\n[SetUp \"1\"]\n[FEN \"4k3/8/8/8/8/8/4P3/4K2R w K - 0 1\"]\n\n1. O-O Kd7 2. Rd1+ Kc6 1-0"
        val game = ChessComArchive.parse(archive(pgn), "me").single()
        assertFalse(game.incompleteParse)
        assertEquals("e1g1 e8d7 f1d1 d7c6", game.uciMoves)
    }

    @Test
    fun aNormalGameStillStartsFromTheNormalPosition() {
        val game = ChessComArchive.parse(archive("[White \"me\"]\n\n1. e4 e5 1-0"), "me").single()
        assertFalse(game.incompleteParse)
        assertEquals("e2e4 e7e5", game.uciMoves)
    }
}
