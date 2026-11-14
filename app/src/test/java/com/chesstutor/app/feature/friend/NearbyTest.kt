package com.chesstutor.app.feature.friend

import com.chesstutor.app.core.Chess960
import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.data.nearby.LanConnector
import com.chesstutor.app.data.nearby.NearbyConnector
import com.chesstutor.app.data.nearby.NearbyLink
import com.chesstutor.app.data.nearby.SocketLink
import com.chesstutor.app.data.repository.InMemoryGameRepository
import com.chesstutor.app.domain.game.FriendSetup
import com.chesstutor.app.domain.game.NearbyMessage
import com.chesstutor.app.domain.game.NearbyProtocol
import com.chesstutor.app.domain.game.PlayerClock
import com.chesstutor.app.domain.game.Seating
import com.chesstutor.app.domain.opening.OpeningTree
import com.chesstutor.app.feature.play.FriendPhase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import kotlin.random.Random

/** Nearby play (Phase 11C): the messages, a real socket, and two phones' view models playing each other. */
@OptIn(ExperimentalCoroutinesApi::class)
class NearbyTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = kotlinx.coroutines.Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = kotlinx.coroutines.Dispatchers.resetMain()

    @Test
    fun everyMessageSurvivesTheWire() {
        val setup = FriendSetup(
            whiteName = "Sam", blackName = "Ada", ownerColor = PieceColor.BLACK,
            whiteClock = PlayerClock.of(5, 3), blackClock = PlayerClock.of(10),
            startFen = Chess960.startFen(3), openingMoves = listOf("e2e4"), takebacks = false, seating = Seating.SIDE_BY_SIDE,
        )
        val messages = listOf(
            NearbyMessage.Hello("Ada"), NearbyMessage.Setup(setup), NearbyMessage.Moved("e2e4", 298_000), NearbyMessage.Moved("e7e5", null),
            NearbyMessage.Resign, NearbyMessage.DrawOffer, NearbyMessage.DrawAnswer(true), NearbyMessage.Flag, NearbyMessage.Rematch, NearbyMessage.Bye,
        )
        messages.forEach { message ->
            val line = NearbyProtocol.encode(message)
            assertTrue("one line: $line", '\n' !in line)
            assertEquals(message, NearbyProtocol.decode(line))
        }
        assertNull(NearbyProtocol.decode("not json"))
        assertNull(NearbyProtocol.decode("""{"t":"teleport"}"""))
    }

    @Test
    fun codesAreFourClearLetters() {
        val code = NearbyProtocol.newCode(Random(7))
        assertEquals(4, code.length)
        assertTrue(code.none { it == 'I' || it == 'O' })
        assertEquals("ABCD", NearbyProtocol.cleanCode("ab-c d9e"))
        assertEquals(41234, LanConnector.parseAddress("192.168.1.5:41234")?.port)
        assertNull(LanConnector.parseAddress("ABCD"))
    }

    @Test
    fun linesTravelBothWaysOverARealSocket() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO)
        ServerSocket(0).use { server ->
            val accepted = CompletableDeferred<Socket>()
            Thread { accepted.complete(server.accept()) }.start()
            val joiner = SocketLink(Socket("127.0.0.1", server.localPort), scope)
            val host = SocketLink(accepted.await(), scope)
            assertTrue(joiner.send("hello host"))
            assertEquals("hello host", withTimeout(5_000) { host.lines.first() })
            assertTrue(host.send("hello joiner"))
            assertEquals("hello joiner", withTimeout(5_000) { joiner.lines.first() })
            joiner.close()
            host.close()
        }
    }

    /** Two links joined back to back, as two phones on one network. */
    private class Pair {
        val toHost = Channel<String>(Channel.UNLIMITED)
        val toJoiner = Channel<String>(Channel.UNLIMITED)
        val host = link(toHost, toJoiner)
        val joiner = link(toJoiner, toHost)

        private fun link(inbox: Channel<String>, outbox: Channel<String>) = object : NearbyLink {
            override val lines: Flow<String> = inbox.receiveAsFlow()
            override suspend fun send(line: String): Boolean = outbox.trySend(line).isSuccess
            override fun close() {
                inbox.close()
                outbox.close()
            }
        }
    }

    private fun connector(pair: Pair) = object : NearbyConnector {
        override suspend fun host(code: String, scope: CoroutineScope, onListening: (String?) -> Unit): NearbyLink {
            onListening("10.0.0.2:4000")
            return pair.host
        }

        override suspend fun join(target: String, scope: CoroutineScope): NearbyLink = pair.joiner
    }

    @Test
    fun twoPhonesFindEachOtherAndPlay() = runTest(dispatcher) {
        val pair = Pair()
        val hostGames = InMemoryGameRepository()
        val host = FriendViewModel({ OpeningTree.EMPTY }, hostGames, clock = { 0L }, connector = connector(pair))
        val joiner = FriendViewModel({ OpeningTree.EMPTY }, InMemoryGameRepository(), clock = { 0L }, connector = connector(pair))
        // No clock: a running clock ticks for ever under advanceUntilIdle.
        host.update { it.copy(mode = FriendMode.NEARBY, clock = null, ownerColor = PieceColor.WHITE) }

        host.host("Sam")
        joiner.join("abcd", "Ada")
        advanceUntilIdle()

        val hostGame = host.game.value!!
        val joinerGame = joiner.game.value!!
        assertEquals("the host's draft names the joiner", "Ada", hostGame.setup.blackName)
        assertEquals("the joiner plays the other side", PieceColor.BLACK, joinerGame.setup.ownerColor)
        assertEquals("Sam", joinerGame.setup.whiteName)
        assertNull(joinerGame.setup.whiteClock)

        fun move(vm: FriendViewModel, uci: String) {
            val session = vm.game.value!!.session
            session.onMove(LegalMoveGenerator.generateLegalMoves(session.game.position).first { it.uci == uci })
        }
        move(host, "e2e4")
        advanceUntilIdle()
        assertEquals(listOf("e2e4"), joinerGame.session.moves.map { it.uci })
        move(joiner, "e7e5")
        advanceUntilIdle()
        assertEquals(listOf("e2e4", "e7e5"), hostGame.session.moves.map { it.uci })

        joinerGame.resign()
        advanceUntilIdle()
        assertEquals(FriendPhase.OVER, hostGame.state.value.phase)
        assertEquals(PieceColor.WHITE, hostGame.state.value.result?.winner)

        // A rematch asked from the joiner is dealt by the host, colours swapped, on both phones.
        joiner.rematch()
        advanceUntilIdle()
        assertEquals(PieceColor.BLACK, host.game.value!!.setup.ownerColor)
        assertEquals(PieceColor.WHITE, joiner.game.value!!.setup.ownerColor)

        // Leaving tells the other phone.
        joiner.close()
        advanceUntilIdle()
        assertTrue(host.game.value!!.state.value.peerGone)
    }

    @Test
    fun aChess960DraftStartsFromItsShuffledPosition() {
        val setup = FriendDraft(start = FriendStart.CHESS960, chess960 = 3).toSetup("Sam")
        assertEquals(Chess960.startFen(3), setup.startFen)
        assertTrue(Chess960.isChess960(setup.startFen))
    }
}
