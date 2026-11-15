package com.chesstutor.app.feature.play

import com.chesstutor.app.board.BoardSound
import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.data.repository.InMemoryGameRepository
import com.chesstutor.app.domain.game.FriendSetup
import com.chesstutor.app.domain.game.GameClock
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class FriendGameTest {
    private val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(StandardTestDispatcher(scheduler) + SupervisorJob())
    private val games = InMemoryGameRepository()

    @After
    fun tearDown() = scope.cancel()

    private fun step(ms: Long = 0) {
        scheduler.advanceTimeBy(ms)
        scheduler.runCurrent()
    }

    private fun game(
        setup: FriendSetup = FriendSetup("Sam", "Ada"),
        local: Set<PieceColor> = PieceColor.entries.toSet(),
    ) = FriendGame(setup, { tree }, games, scope, clock = { scheduler.currentTime }, localSides = local).also { it.start(); step() }

    private fun FriendGame.play(vararg ucis: String) {
        for (uci in ucis) {
            val move = LegalMoveGenerator.generateLegalMoves(session.game.position).first { it.uci == uci }
            session.onMove(move)
            step()
        }
    }

    private fun timed(white: PlayerClock = PlayerClock.of(5), black: PlayerClock = white) =
        FriendSetup("Sam", "Ada", whiteClock = white, blackClock = black)

    @Test
    fun bothPlayersMoveOnTheOnePhone() {
        val g = game()
        assertEquals(FriendPhase.PLAYING, g.state.value.phase)
        assertEquals(PieceColor.WHITE, g.session.state.value.movable)

        g.play("e2e4")
        assertEquals("Black may move now, on the same phone", PieceColor.BLACK, g.session.state.value.movable)
        assertEquals(PieceColor.BLACK, g.state.value.toMove)

        g.play("e7e5", "g1f3", "b8c6", "f1c4")
        assertEquals("Italian Game", g.state.value.opening)
    }

    @Test
    fun checkmateEndsTheGameAndSavesItForTheOwner() {
        val g = game(FriendSetup("Sam", "Ada", ownerColor = PieceColor.BLACK))
        g.play("f2f3", "e7e5", "g2g4", "d8h4")

        val state = g.state.value
        assertEquals(FriendPhase.OVER, state.phase)
        assertEquals(PieceColor.BLACK, state.result?.winner)
        assertEquals(GameEnd.CHECKMATE, state.result?.end)
        assertNull("nobody moves after the end", g.session.state.value.movable)
        assertEquals("someone at the table won", BoardSound.WIN, state.sound?.feedback?.sound)

        val saved = runBlocking { games.getRecentGames() }.single()
        assertEquals(state.savedGameId, saved.id)
        assertTrue(saved.id.startsWith(FriendGame.ID_PREFIX))
        assertEquals("the friend is the opponent", "Sam", saved.botName)
        assertEquals("black", saved.userColor)
        assertEquals("Win", saved.userOutcomeLabel)
        assertEquals("0-1", saved.result)
        assertEquals("untimed", saved.timeClass)
        assertTrue(saved.pgn.contains("[White \"Sam\"]") && saved.pgn.contains("[Black \"Ada\"]"))
        assertEquals("f2f3 e7e5 g2g4 d8h4", saved.uciMoves)
    }

    @Test
    fun eachPlayerHasTheirOwnClockAndIncrement() {
        val g = game(timed(white = PlayerClock.of(5, 2), black = PlayerClock.of(1)))
        step(10_000)
        assertEquals(290_000L, g.remaining(PieceColor.WHITE))
        assertEquals("Black's clock waits", 60_000L, g.remaining(PieceColor.BLACK))

        g.play("e2e4")
        assertEquals("White spent 10 s and gained 2", 292_000L, g.remaining(PieceColor.WHITE))
        step(4_000)
        assertEquals(56_000L, g.remaining(PieceColor.BLACK))
        g.play("e7e5")
        assertEquals("Black has no increment", 56_000L, g.remaining(PieceColor.BLACK))
    }

    @Test
    fun aPlayerWhoRunsOutOfTimeLoses() {
        val g = game(timed(black = PlayerClock.of(1)))
        g.play("e2e4")
        step(61_000)

        assertEquals(FriendPhase.OVER, g.state.value.phase)
        assertEquals(PieceColor.WHITE, g.state.value.result?.winner)
        assertEquals(GameEnd.TIMEOUT, g.state.value.result?.end)
        assertEquals(0L, g.remaining(PieceColor.BLACK))
        assertEquals("the slower clock is 5 minutes", "blitz", runBlocking { games.getRecentGames() }.single().timeClass)
    }

    @Test
    fun runningOutOfTimeAgainstALoneKingIsADraw() {
        val g = game(timed().copy(startFen = "8/8/8/8/8/k7/8/K6Q w - - 0 1"))
        step(5 * 60_000L + 1_000)
        assertEquals(GameEnd.TIMEOUT, g.state.value.result?.end)
        assertNull("Black's king alone cannot mate", g.state.value.result?.winner)
    }

    @Test
    fun aTakebackGivesTheMoveBackToItsMover() {
        val g = game()
        assertFalse(g.state.value.canTakeBack)
        g.play("e2e4", "e7e5")
        assertTrue(g.state.value.canTakeBack)

        g.takeBack()
        assertEquals(listOf("e2e4"), g.session.moves.map { it.uci })
        assertEquals(PieceColor.BLACK, g.state.value.toMove)
        assertEquals(PieceColor.BLACK, g.session.state.value.movable)

        val strict = game(FriendSetup("Sam", "Ada", takebacks = false))
        strict.play("e2e4")
        assertFalse(strict.state.value.canTakeBack)
        strict.takeBack()
        assertEquals(1, strict.session.moves.size)
    }

    @Test
    fun theGameCanStartFromAnOpeningAndItsMovesCannotBeTakenBack() {
        val g = game(FriendSetup("Sam", "Ada", openingMoves = listOf("e2e4", "c7c5")))
        assertEquals(2, g.session.moves.size)
        assertEquals("Sicilian Defense", g.state.value.opening)
        assertEquals(PieceColor.WHITE, g.state.value.toMove)
        assertFalse("the opening's own moves stay", g.state.value.canTakeBack)

        g.play("g1f3")
        g.takeBack()
        g.takeBack()
        assertEquals(2, g.session.moves.size)
    }

    @Test
    fun theSideToMoveResignsAndADrawCanBeAgreed() {
        val g = game()
        g.play("e2e4")
        g.resign()
        assertEquals("Black was to move, so Black resigned", PieceColor.WHITE, g.state.value.result?.winner)
        assertEquals(GameEnd.RESIGNATION, g.state.value.result?.end)
        g.agreeDraw()
        assertEquals("a finished game stays finished", GameEnd.RESIGNATION, g.state.value.result?.end)

        val drawn = game()
        drawn.play("e2e4")
        drawn.agreeDraw()
        step()
        assertNull(drawn.state.value.result?.winner)
        assertEquals(GameEnd.AGREEMENT, drawn.state.value.result?.end)
        assertEquals("Draw", runBlocking { games.getRecentGames() }.first().userOutcomeLabel)
    }

    @Test
    fun nearbyThisPhoneMovesOneSideAndTheFriendsMovesComeIn() {
        val g = game(local = setOf(PieceColor.WHITE))
        assertFalse("it is this phone's turn", g.playRemote("e7e5"))
        g.play("e2e4")
        assertNull("the friend's pieces cannot be moved here", g.session.state.value.movable)

        assertFalse("an illegal move changes nothing", g.playRemote("e7e4"))
        assertTrue(g.playRemote("e7e5"))
        assertEquals(listOf("e2e4", "e7e5"), g.session.moves.map { it.uci })
        assertEquals("the friend's move is heard", BoardSound.MOVE, g.state.value.sound?.feedback?.sound)
        assertEquals(PieceColor.WHITE, g.session.state.value.movable)
    }

    @Test
    fun clocksAreLabelledAndSortedLikeTheSites() {
        assertEquals("10 min", PlayerClock.of(10).label)
        assertEquals("3 | 2", PlayerClock.of(3, 2).label)
        assertEquals("0:30", PlayerClock(30_000).label)
        assertEquals("bullet", timed(PlayerClock.of(1)).timeClass)
        assertEquals("blitz", timed(PlayerClock.of(3, 2)).timeClass)
        assertEquals("rapid", timed(PlayerClock.of(15, 10)).timeClass)
        assertEquals("classical", timed(PlayerClock.of(30)).timeClass)
        assertEquals("the slower clock decides", "rapid", timed(white = PlayerClock.of(1), black = PlayerClock.of(10)).timeClass)
        assertThrows(IllegalArgumentException::class.java) { FriendSetup("Sam", "Ada", whiteClock = PlayerClock.of(5)) }
    }

    @Test
    fun aStoppedClockKeepsItsTimeAndNeverGoesBelowZero() {
        var now = 0L
        val clock = GameClock(PlayerClock.of(1), PlayerClock.of(1), now = { now })
        clock.start(PieceColor.WHITE)
        now = 20_000
        clock.stop()
        now = 500_000
        assertEquals(40_000L, clock.remaining(PieceColor.WHITE))
        assertNull(clock.flagged())

        clock.start(PieceColor.BLACK)
        now = 900_000
        assertEquals(0L, clock.remaining(PieceColor.BLACK))
        assertEquals(PieceColor.BLACK, clock.flagged())
        assertNull(GameClock(null, null) { now }.remaining(PieceColor.WHITE))
    }

    companion object {
        private val tree: OpeningTree by lazy { File("src/main/assets/openings.tsv.gz").inputStream().use(OpeningTree::fromStream) }
    }
}
