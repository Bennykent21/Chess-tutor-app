package com.chesstutor.app.domain.bot

import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.domain.MoveChoice
import com.chesstutor.app.domain.opening.Repertoire
import com.chesstutor.app.engine.EngineEvaluation
import com.chesstutor.app.engine.EngineHealthReport
import com.chesstutor.app.engine.EngineLine
import com.chesstutor.app.engine.EngineService
import com.chesstutor.app.engine.EngineStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BotBrainTest {

    /** Answers with fixed lines (White's perspective, as the real service does) and counts calls. */
    private class FakeEngine(var lines: List<EngineLine> = emptyList(), var botMove: String? = null) : EngineService {
        var analyseCalls = 0
        var lastMovetime = 0
        override val status: StateFlow<EngineStatus> = MutableStateFlow(EngineStatus())
        override suspend fun bestMove(fen: String, movetimeMs: Int): MoveChoice? = null
        override suspend fun evaluate(fen: String, movetimeMs: Int): EngineEvaluation? = null
        override suspend fun analyse(fen: String, lines: Int, movetimeMs: Int): List<EngineLine> {
            analyseCalls++
            lastMovetime = movetimeMs
            return this.lines.take(lines)
        }
        override suspend fun botMove(fen: String, elo: Int): MoveChoice? = botMove?.let { MoveChoice.fromUci(it) }
        override suspend fun healthCheck(): EngineHealthReport? = null
    }

    private fun line(rank: Int, uci: String, cp: Int? = null, mate: Int? = null) = EngineLine(rank, listOf(uci), cp, mate)
    private fun bot(rating: Int, mistakes: Float = 0f, aggression: Float = 0f, trades: Float = 0f) =
        BotProfile("t", "Test", rating, aggression = aggression, trades = trades, mistakeLevel = mistakes)

    private val start = Position.initial()

    @Test
    fun inBookTheBotPlaysItsRepertoireWithoutTheEngine() = runBlocking {
        val engine = FakeEngine(listOf(line(1, "d2d4", 30)))
        val decision = BotBrain(engine, Random(1)).decide(start, 0, bot(1500), Repertoire.line(listOf("e2e4", "e7e5")))!!
        assertEquals("e2e4", decision.uci)
        assertEquals(BotMoveKind.BOOK, decision.kind)
        assertEquals(0, engine.analyseCalls)
        assertNull(decision.evalForBot)
    }

    @Test
    fun beginnersLeaveTheirBookEarly() = runBlocking {
        val engine = FakeEngine(listOf(line(1, "d2d4", 30)))
        val decision = BotBrain(engine, Random(1)).decide(start, 4, bot(400), Repertoire.line(listOf("e2e4")))!!
        assertEquals(BotMoveKind.ENGINE, decision.kind)
        assertEquals(1, engine.analyseCalls)
        assertEquals(BotStrengthModel.searchMs(400), engine.lastMovetime)
    }

    @Test
    fun aMaximumStrengthBotAlwaysPlaysTheBestMove() = runBlocking {
        val engine = FakeEngine(listOf(line(1, "e2e4", 40), line(2, "d2d4", 35), line(3, "c2c4", 30)))
        repeat(50) { seed ->
            val decision = BotBrain(engine, Random(seed)).decide(start, 30, bot(3000), Repertoire.NONE)!!
            assertEquals("e2e4", decision.uci)
            assertEquals(40, decision.evalForBot)
        }
    }

    @Test
    fun withoutSlipsABotOnlyPlaysMovesWithinItsTolerance() {
        val candidates = listOf(BotCandidate("e2e4", 50), BotCandidate("d2d4", 30), BotCandidate("a2a3", -100))
        val legal = candidates.map { it.uci }
        val picks = (0 until 300).map { BotMoveChooser.choose(start, candidates, legal, bot(1200), Random(it)).uci }.toSet()
        assertEquals("a 20 cp gap is fine at 1200, 150 cp is not", setOf("e2e4", "d2d4"), picks)
    }

    @Test
    fun slipsCostNoMoreThanTheRatingAllows() {
        val candidates = listOf(
            BotCandidate("e2e4", 50), BotCandidate("d2d4", -40), BotCandidate("c2c4", -120), BotCandidate("a2a3", -400),
        )
        val legal = candidates.map { it.uci }
        val choices = (0 until 400).map { BotMoveChooser.choose(start, candidates, legal, bot(1600, mistakes = 6f), Random(it)) }
        val slips = choices.filter { it.kind == BotMoveKind.MISTAKE }.map { it.uci }.toSet()
        // At 1600 a slip loses 60 to 200 cp: d2d4 (-90) or c2c4 (-170), never a2a3 (-450).
        assertEquals(setOf("d2d4", "c2c4"), slips)
    }

    @Test
    fun beginnersSometimesBlunderWithAMoveTheEngineDidNotSuggest() {
        val candidates = listOf(BotCandidate("e2e4", 30))
        val legal = listOf("e2e4", "d2d4", "g1f3", "a2a3")
        val choices = (0 until 200).map { BotMoveChooser.choose(start, candidates, legal, bot(400, mistakes = 2f), Random(it)) }
        assertTrue(choices.any { it.kind == BotMoveKind.MISTAKE && it.uci != "e2e4" })
        assertTrue("most moves are still the engine's", choices.count { it.uci == "e2e4" } > 80)
        assertFalse("strong bots never make random moves", (0 until 200).any {
            BotMoveChooser.choose(start, candidates, legal, bot(2000, mistakes = 2f), Random(it)).uci != "e2e4"
        })
    }

    @Test
    fun aBotThatCanMateDoesNotSettleForLess() {
        val candidates = listOf(BotCandidate("e2e4", BotBrain.MATE - 200), BotCandidate("d2d4", BotBrain.MATE - 300), BotCandidate("c2c4", 900))
        val picks = (0 until 100).map { BotMoveChooser.choose(start, candidates, candidates.map { it.uci }, bot(1500), Random(it)).uci }.toSet()
        assertFalse(picks.contains("c2c4"))
    }

    @Test
    fun anAttackerChoosesTheCheckMoreOftenThanACalmBot() {
        // White queen d1, black king e8: Qa4+ is a check, Qc1 is quiet; the engine rates them the same.
        val position = Position.fromFen("4k3/8/8/8/8/8/8/3QK3 w - - 0 1")
        val candidates = listOf(BotCandidate("d1a4", 0), BotCandidate("d1c1", 0))
        fun checks(profile: BotProfile) = (0 until 400).count {
            BotMoveChooser.choose(position, candidates, candidates.map { c -> c.uci }, profile, Random(it)).uci == "d1a4"
        }
        val attacker = checks(bot(2400, aggression = 1f))
        val calm = checks(bot(2400, aggression = -1f))
        assertTrue("attacker $attacker, calm $calm", attacker > calm + 100)
    }

    @Test
    fun aTraderTakesEvenTradesMoreOftenThanAHoarder() {
        // Nxd5 trades knights; Kb1 is a quiet king move.
        val position = Position.fromFen("k7/8/8/3n4/8/4N3/8/K7 w - - 0 1")
        val candidates = listOf(BotCandidate("e3d5", 0), BotCandidate("a1b1", 0))
        fun trades(profile: BotProfile) = (0 until 400).count {
            BotMoveChooser.choose(position, candidates, candidates.map { c -> c.uci }, profile, Random(it)).uci == "e3d5"
        }
        val trader = trades(bot(2400, trades = 1f))
        val hoarder = trades(bot(2400, trades = -1f))
        assertTrue("trader $trader, hoarder $hoarder", trader > hoarder + 100)
    }

    @Test
    fun engineScoresBecomeTheBotsOwnPointOfView() {
        val lines = listOf(line(1, "e7e5", cp = 30), line(2, "d7d5", mate = -3), line(3, "c7c5", mate = 2))
        val asBlack = BotBrain.candidatesOf(lines, PieceColor.BLACK)
        assertEquals(listOf("d7d5", "e7e5", "c7c5"), asBlack.map { it.uci })
        assertEquals(BotBrain.MATE - 300, asBlack[0].score)
        assertEquals(-30, asBlack[1].score)
        assertTrue("being mated in 2 is the worst", asBlack[2].score < -BotBrain.MATE / 2)
    }

    @Test
    fun withoutEngineLinesTheBotFallsBackToItsSimpleMoveThenToAnyLegalMove() = runBlocking {
        val engine = FakeEngine(lines = emptyList(), botMove = "g1f3")
        val simple = BotBrain(engine, Random(3)).decide(start, 20, bot(1200), Repertoire.NONE)!!
        assertEquals("g1f3" to BotMoveKind.FALLBACK, simple.uci to simple.kind)
        engine.botMove = null
        val any = BotBrain(engine, Random(3)).decide(start, 20, bot(1200), Repertoire.NONE)!!
        assertEquals(BotMoveKind.FALLBACK, any.kind)
        assertEquals(4, any.uci.length)
    }

    @Test
    fun aMatedSideHasNoMove() = runBlocking {
        val mated = Position.fromFen("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3")
        assertNull(BotBrain(FakeEngine(), Random(0)).decide(mated, 4, bot(1200), Repertoire.NONE))
    }

    @Test
    fun theClockLimitsThinkingTime() = runBlocking {
        val engine = FakeEngine(listOf(line(1, "e2e4", 40)))
        val decision = BotBrain(engine, Random(0)).decide(start, 30, bot(1500).copy(thinkSpeed = ThinkSpeed.SLOW), Repertoire.NONE, clockMs = 2_000)!!
        assertTrue(decision.thinkMs <= 100)
    }

    @Test
    fun strengthTablesInterpolateBetweenRows() {
        assertEquals(85, BotStrengthModel.tolerance(1000))
        assertEquals(150, BotStrengthModel.tolerance(250))
        assertEquals(0.0, BotStrengthModel.mistakeChance(bot(1200, mistakes = 0f)), 0.0)
        assertEquals(0.10, BotStrengthModel.mistakeChance(bot(1200, mistakes = 1f)), 1e-9)
        assertTrue(BotStrengthModel.bookDepth(2000) > 100)
        assertThrows(IllegalArgumentException::class.java) { bot(100) }
    }

    @Test
    fun botsResignOnlyWhenTheirRulesSaySo() {
        val lost = listOf(-950, -1200, -1500)
        assertTrue(BotResign.shouldResign(bot(1500), lost))
        assertFalse("two bad moves are not enough", BotResign.shouldResign(bot(1500), lost.drop(1)))
        assertFalse(BotResign.shouldResign(bot(1500).copy(resign = ResignPolicy.NEVER), lost))
        assertTrue(BotResign.shouldResign(bot(1500).copy(resign = ResignPolicy.EARLY), listOf(-600, -700)))
        assertFalse("beginners play on", BotResign.shouldResign(bot(400), lost))
    }
}
