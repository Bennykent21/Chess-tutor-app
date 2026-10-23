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
        var lastDepth: Int? = null
        override suspend fun analyseToDepth(fen: String, lines: Int, depth: Int): List<EngineLine> {
            lastDepth = depth
            return analyse(fen, lines, 0)
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
        assertEquals("a beginner searches only one ply deep", 1, engine.lastDepth)
    }

    @Test
    fun aMaximumStrengthBotAlwaysPlaysTheBestMove() = runBlocking {
        val engine = FakeEngine(listOf(line(1, "e2e4", 40), line(2, "d2d4", 35), line(3, "c2c4", 30)))
        repeat(50) { seed ->
            val decision = BotBrain(engine, Random(seed)).decide(start, 30, bot(3000), Repertoire.NONE)!!
            assertEquals("e2e4", decision.uci)
            assertEquals(40, decision.evalForBot)
        }
        assertEquals("full strength has no depth cap", null, engine.lastDepth)
        assertEquals(BotBrain.FULL_STRENGTH_MS, engine.lastMovetime)
    }

    @Test
    fun levelTwentyAlwaysPlaysTheBestMove() {
        val candidates = listOf(BotCandidate("e2e4", 50), BotCandidate("d2d4", 45), BotCandidate("c2c4", 40))
        assertTrue((0 until 100).all { SkillPicker.pick(start, candidates, 20, bot(3000), Random(it)).uci == "e2e4" })
    }

    @Test
    fun lowLevelsPickFreelyAmongCloseMoves() {
        val candidates = listOf(BotCandidate("e2e4", 30), BotCandidate("d2d4", 20), BotCandidate("g1f3", 10), BotCandidate("c2c4", 0))
        val picks = (0 until 300).map { SkillPicker.pick(start, candidates, 0, bot(250), Random(it)).uci }.toSet()
        assertEquals(4, picks.size)
    }

    @Test
    fun strongLevelsDoNotHangPiecesButBeginnersSometimesDo() {
        val candidates = listOf(BotCandidate("e2e4", 0), BotCandidate("d2d4", -20), BotCandidate("g1f3", -40), BotCandidate("a2a3", -400))
        fun hangs(level: Int) = (0 until 400).count { SkillPicker.pick(start, candidates, level, bot(1000), Random(it)).uci == "a2a3" }
        assertEquals(0, hangs(18))
        assertEquals(0, hangs(9))
        assertTrue("level 0 drops the piece now and then", hangs(0) in 1..120)
        val mistake = (0 until 400).map { SkillPicker.pick(start, candidates, 0, bot(250), Random(it)) }.first { it.uci == "a2a3" }
        assertEquals(BotMoveKind.MISTAKE, mistake.kind)
    }

    @Test
    fun beginnersSometimesPlayARandomMove() = runBlocking {
        val engine = FakeEngine(listOf(line(1, "e2e4", 30)))
        val kinds = (0 until 200).map { BotBrain(engine, Random(it)).decide(start, 20, bot(250, mistakes = 1f), Repertoire.NONE)!!.kind }
        assertTrue(kinds.count { it == BotMoveKind.MISTAKE } in 20..90)
        val strong = (0 until 100).map { BotBrain(engine, Random(it)).decide(start, 20, bot(1800, mistakes = 1f), Repertoire.NONE)!!.uci }
        assertTrue("1800 never plays a random move", strong.all { it == "e2e4" })
    }

    @Test
    fun anAttackerChoosesTheCheckMoreOftenThanACalmBot() {
        // White queen d1, black king e8: Qa4+ is a check, Qc1 is quiet; the engine rates them the same.
        val position = Position.fromFen("4k3/8/8/8/8/8/8/3QK3 w - - 0 1")
        val candidates = listOf(BotCandidate("d1a4", 0), BotCandidate("d1c1", 0))
        fun checks(profile: BotProfile) = (0 until 400).count {
            SkillPicker.pick(position, candidates, 10, profile, Random(it)).uci == "d1a4"
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
            SkillPicker.pick(position, candidates, 10, profile, Random(it)).uci == "e3d5"
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
    fun ratingSetsSkillLevelDepthAndRandomMoves() {
        assertEquals(StrengthSettings(9, 7, 0.0), BotStrengthModel.settingsFor(bot(1800, mistakes = 1f)))
        assertEquals(StrengthSettings(0, 1, 0.25), BotStrengthModel.settingsFor(bot(250, mistakes = 1f)))
        assertEquals(StrengthSettings(20, null, 0.0), BotStrengthModel.settingsFor(bot(3000, mistakes = 1f)))
        assertEquals("halfway between 1800 and 2000", StrengthSettings(10, 8, 0.0), BotStrengthModel.settingsFor(bot(1900, mistakes = 1f)))
        assertEquals("Never makes mistakes: 4 levels cleaner", 13, BotStrengthModel.settingsFor(bot(1800, mistakes = 0f)).skillLevel)
        assertEquals("Often: 4 levels sloppier", 5, BotStrengthModel.settingsFor(bot(1800, mistakes = 2f)).skillLevel)
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
