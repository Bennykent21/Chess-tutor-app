package com.chesstutor.app.data.network

import com.chesstutor.app.domain.account.TimeClass
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A busy clock must not use up the import and leave the others with nothing to graph. */
@RunWith(RobolectricTestRunner::class)
class ClockQuotaTest {

    private fun game(id: String, clock: String, endSeconds: Long) = JSONObject()
        .put("url", "https://chess.com/game/$id")
        .put("pgn", "[Event \"Live\"]\n\n1. e4 e5 2. Nf3 Nc6 1-0")
        .put("rules", "chess")
        .put("time_class", clock)
        .put("end_time", endSeconds)
        .put("white", JSONObject().put("username", "kenny").put("rating", 1500).put("result", "win"))
        .put("black", JSONObject().put("username", "them").put("rating", 1500).put("result", "resigned"))

    private fun month(vararg games: JSONObject) = JSONObject().put("games", JSONArray(games.toList())).toString()

    @Test
    fun eachClockHasItsOwnCount() {
        val quota = ClockQuota(perClock = 2)
        assertTrue(quota.take(TimeClass.BULLET))
        assertTrue(quota.take(TimeClass.BULLET))
        assertFalse(quota.take(TimeClass.BULLET))
        assertTrue(quota.wants(TimeClass.BLITZ))
        assertFalse(quota.full)
    }

    @Test
    fun onlyTheRequiredClocksHoldTheImportOpen() {
        val quota = ClockQuota(perClock = 1, required = setOf(TimeClass.RAPID))
        assertEquals(1, quota.target)
        quota.take(TimeClass.RAPID)
        assertTrue("a clock the account never plays is not searched for", quota.full)
    }

    @Test
    fun aBusyClockLeavesRoomForTheOthers() {
        val json = month(game("b1", "bullet", 100), game("b2", "bullet", 200), game("b3", "bullet", 300), game("z1", "blitz", 50), game("r1", "rapid", 40))
        val games = ChessComArchive.parse(json, "kenny", Int.MAX_VALUE, quota = ClockQuota(perClock = 2))
        assertEquals("the newest two bullet games, and the others", setOf("chesscom_b3", "chesscom_b2", "chesscom_z1", "chesscom_r1"), games.map { it.id }.toSet())
    }

    @Test
    fun eachClockCarriesOnFromItsOwnOldestGame() {
        val json = month(game("old", "bullet", 100), game("seen", "bullet", 200), game("rapidOld", "rapid", 150), game("rapidSeen", "rapid", 300))
        val cutoffs = mapOf(TimeClass.BULLET to 200_000L, TimeClass.RAPID to 300_000L)
        val games = ChessComArchive.parse(json, "kenny", Int.MAX_VALUE, clockCutoffs = cutoffs)
        assertEquals(setOf("chesscom_old", "chesscom_rapidOld"), games.map { it.id }.toSet())
    }

    @Test
    fun aClockWithNothingSavedYetIsFetchedFromTheNewest() {
        val json = month(game("bulletNew", "bullet", 900), game("rapidOld", "rapid", 100))
        val games = ChessComArchive.parse(json, "kenny", Int.MAX_VALUE, clockCutoffs = mapOf(TimeClass.RAPID to 500_000L))
        assertEquals(setOf("chesscom_bulletNew", "chesscom_rapidOld"), games.map { it.id }.toSet())
    }

    @Test
    fun cutoffsSurviveBeingSavedWithABackgroundImport() {
        val cutoffs = mapOf(TimeClass.RAPID to 123L, TimeClass.BULLET to 456L)
        assertEquals(cutoffs, ClockQuota.decode(ClockQuota.encode(cutoffs)))
        assertEquals(emptyMap<TimeClass, Long>(), ClockQuota.decode(""))
    }
}
