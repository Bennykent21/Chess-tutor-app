package com.chesstutor.app.feature.play

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.data.repository.InMemoryGameRepository
import com.chesstutor.app.domain.game.FriendSetup
import com.chesstutor.app.domain.game.GameEnd
import com.chesstutor.app.domain.game.PlayerClock
import com.chesstutor.app.domain.opening.OpeningTree
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A nearby game from one phone's side (Phase 11C): what it tells the other phone and what it takes from it. */
@OptIn(ExperimentalCoroutinesApi::class)
class NearbyGameTest {
    private val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(StandardTestDispatcher(scheduler) + SupervisorJob())
    private val games = InMemoryGameRepository()
    private val told = mutableListOf<String>()

    private val peer = object : FriendPeer {
        override fun moved(uci: String, remainingMs: Long?) { told += "move $uci ${remainingMs ?: "-"}" }
        override fun resigned() { told += "resign" }
        override fun offeredDraw() { told += "draw" }
        override fun answeredDraw(accept: Boolean) { told += "answer $accept" }
        override fun flagged() { told += "flag" }
    }

    @After
    fun tearDown() = scope.cancel()

    private fun step(ms: Long = 0) {
        scheduler.advanceTimeBy(ms)
        scheduler.runCurrent()
    }

    private fun game(setup: FriendSetup = FriendSetup("Sam", "Ada", takebacks = false)) =
        FriendGame(setup, { OpeningTree.EMPTY }, games, scope, clock = { scheduler.currentTime }, localSides = setOf(PieceColor.WHITE), peer = peer)
            .also { it.start(); step() }

    private fun FriendGame.play(uci: String) {
        session.onMove(LegalMoveGenerator.generateLegalMoves(session.game.position).first { it.uci == uci })
        step()
    }

    @Test
    fun aMoveHereIsSentWithTheTimeLeftAndTheFriendsTimeIsTakenFromTheirPhone() {
        val g = game(FriendSetup("Sam", "Ada", whiteClock = PlayerClock.of(5), blackClock = PlayerClock.of(5), takebacks = false))
        step(2_000)
        g.play("e2e4")
        assertEquals(listOf("move e2e4 298000"), told)
        step(10_000)
        // The friend says they have 4:40 left; their phone's count is the one that counts.
        assertTrue(g.playRemote("e7e5", 280_000))
        assertEquals(280_000L, g.remaining(PieceColor.BLACK))
    }

    @Test
    fun theFriendsTimeRunningOutHereWaitsForTheirPhoneToSaySo() {
        val g = game(FriendSetup("Sam", "Ada", whiteClock = PlayerClock(60_000), blackClock = PlayerClock(1_000), takebacks = false))
        g.play("e2e4")
        step(5_000)
        assertEquals(FriendPhase.PLAYING, g.state.value.phase)
        g.remoteFlagged()
        assertEquals(PieceColor.WHITE, g.state.value.result?.winner)
        assertEquals(GameEnd.TIMEOUT, g.state.value.result?.end)
    }

    @Test
    fun thisPhonesPlayerRunningOutOfTimeTellsTheFriend() {
        val g = game(FriendSetup("Sam", "Ada", whiteClock = PlayerClock(1_000), blackClock = PlayerClock(60_000), takebacks = false))
        step(2_000)
        assertEquals(listOf("flag"), told)
        assertEquals(PieceColor.BLACK, g.state.value.result?.winner)
    }

    @Test
    fun aDrawIsOfferedAndAnswered() {
        val g = game()
        g.offerDraw()
        assertEquals(listOf("draw"), told)
        assertEquals(PieceColor.WHITE, g.state.value.drawOffer)
        g.remoteAnsweredDraw(false)
        assertNull(g.state.value.drawOffer)

        g.remoteOfferedDraw()
        assertEquals(PieceColor.BLACK, g.state.value.drawOffer)
        g.answerDraw(true)
        assertEquals("answer true", told.last())
        assertEquals(GameEnd.AGREEMENT, g.state.value.result?.end)
    }

    @Test
    fun resigningHereTellsTheFriendAndTheirResignationEndsItHere() {
        val g = game()
        g.resign()
        assertEquals(listOf("resign"), told)
        assertEquals(PieceColor.BLACK, g.state.value.result?.winner)

        val other = game()
        other.remoteResigned()
        assertEquals(PieceColor.WHITE, other.state.value.result?.winner)
    }

    @Test
    fun theFriendLeavingStopsTheGameWithoutSavingIt() {
        val g = game()
        g.play("e2e4")
        g.peerLeft()
        assertTrue(g.state.value.peerGone)
        assertNull(g.session.state.value.movable)
        g.resign()
        assertEquals("nothing ends a game that has stopped", FriendPhase.PLAYING, g.state.value.phase)
        step()
        assertTrue(runBlocking { games.getRecentGames(10) }.isEmpty())
    }
}
