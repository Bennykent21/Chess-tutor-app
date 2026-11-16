package com.chesstutor.app.feature.play

import com.chesstutor.app.core.Chess960
import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.data.repository.InMemoryGameRepository
import com.chesstutor.app.domain.game.FriendSetup
import com.chesstutor.app.domain.game.PlayerClock
import com.chesstutor.app.domain.game.Seating
import com.chesstutor.app.domain.opening.OpeningTree
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A friend game on one phone is kept after every move and carried on later (Phase 13). */
@OptIn(ExperimentalCoroutinesApi::class)
class FriendResumeTest {
    private val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(StandardTestDispatcher(scheduler) + SupervisorJob())
    private val kept = mutableListOf<FriendSnapshot?>()

    @After
    fun tearDown() = scope.cancel()

    private fun step(ms: Long = 0) {
        scheduler.advanceTimeBy(ms)
        scheduler.runCurrent()
    }

    private fun game(setup: FriendSetup, resume: FriendSnapshot? = null) =
        FriendGame(setup, { OpeningTree.EMPTY }, InMemoryGameRepository(), scope, clock = { scheduler.currentTime }, resume = resume, onSnapshot = { kept += it })
            .also { it.start(); step() }

    private fun FriendGame.play(vararg ucis: String) {
        for (uci in ucis) {
            session.onMove(LegalMoveGenerator.generateLegalMoves(session.game.position).first { it.uci == uci })
            step(1_000)
        }
    }

    private val timed = FriendSetup("Sam", "Ada", whiteClock = PlayerClock.of(5), blackClock = PlayerClock.of(5))

    @Test
    fun everyMoveIsKeptAndTheEndClearsIt() {
        val g = game(timed)
        assertEquals("nothing is kept before a move", emptyList<FriendSnapshot?>(), kept)
        g.play("e2e4", "e7e5")
        val last = kept.last()!!
        assertEquals(listOf("e2e4", "e7e5"), last.moves)
        assertEquals("Black thought for a second", 299_000L, last.blackMs)
        g.resign()
        assertNull("a finished game is not kept", kept.last())
    }

    @Test
    fun aKeptGameCarriesOnWithItsMovesAndClocks() {
        val saved = FriendSnapshot(timed, listOf("e2e4", "e7e5", "g1f3"), whiteMs = 250_000, blackMs = 200_000, savedAt = 0)
        val g = game(timed, resume = saved)
        assertEquals(listOf("e2e4", "e7e5", "g1f3"), g.session.moves.map { it.uci })
        assertEquals(PieceColor.BLACK, g.state.value.toMove)
        assertEquals(250_000L, g.remaining(PieceColor.WHITE))
        step(3_000)
        assertEquals("Black's clock runs from where it stopped", 197_000L, g.remaining(PieceColor.BLACK))
        assertEquals("the moves can be taken back as before", true, g.state.value.canTakeBack)
    }

    @Test
    fun theStoreReadsBackWhatItWrote() {
        val setup = FriendSetup(
            "Sam", "Ada", ownerColor = PieceColor.BLACK, whiteClock = PlayerClock.of(3, 2), blackClock = PlayerClock.of(10),
            startFen = Chess960.startFen(7), openingMoves = listOf("e2e4"), takebacks = false, seating = Seating.SIDE_BY_SIDE,
        )
        val snapshot = FriendSnapshot(setup, listOf("e2e4", "e7e5"), 170_000, null, 42)
        assertEquals(snapshot, SharedPrefsFriendGameStore.decode(SharedPrefsFriendGameStore.encode(snapshot)))
        assertNull(SharedPrefsFriendGameStore.decode("{broken"))
    }
}
