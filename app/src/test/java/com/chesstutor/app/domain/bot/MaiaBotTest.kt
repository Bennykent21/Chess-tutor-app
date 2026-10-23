package com.chesstutor.app.domain.bot

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Position
import com.chesstutor.app.domain.MoveChoice
import com.chesstutor.app.domain.bot.maia.MaiaNet
import com.chesstutor.app.domain.bot.maia.MaiaPolicy
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.random.Random

/** The human-like mode (D26): Maia suggests, strong bots check with a shallow search. */
class MaiaBotTest {

    private val policy by lazy {
        MaiaPolicy(
            File("src/main/assets/maia/maia-1500.bin").inputStream().use(MaiaNet::read),
            File("src/main/assets/maia/maia_moves.tsv").inputStream().use(MaiaPolicy::readSlots),
        )
    }
    private val source = MaiaSource { policy }

    /** Scores every position 0, except those listed (White's view), as if searched. */
    private class ScriptedEngine(val scores: Map<String, Int> = emptyMap()) : EngineService {
        val searchedDepths = mutableListOf<Int>()
        override val status: StateFlow<EngineStatus> = MutableStateFlow(EngineStatus())
        override suspend fun bestMove(fen: String, movetimeMs: Int): MoveChoice? = null
        override suspend fun evaluate(fen: String, movetimeMs: Int): EngineEvaluation? = null
        override suspend fun analyse(fen: String, lines: Int, movetimeMs: Int): List<EngineLine> = analyseToDepth(fen, lines, 20)
        override suspend fun analyseToDepth(fen: String, lines: Int, depth: Int): List<EngineLine> {
            searchedDepths += depth
            val position = Position.fromFen(fen)
            val first = LegalMoveGenerator.generateLegalMoves(position).firstOrNull() ?: return emptyList()
            return listOf(EngineLine(1, listOf(first.uci), scores[fen.substringBefore(" ")] ?: 0))
        }
        override suspend fun botMove(fen: String, elo: Int): MoveChoice? = null
        override suspend fun healthCheck(): EngineHealthReport? = null
    }

    private val afterE4 = LegalMoveGenerator.makeMove(Position.initial(), LegalMoveGenerator.generateLegalMoves(Position.initial()).first { it.uci == "e2e4" })
    private fun bot(rating: Int) = BotProfile("t", "Test", rating, mistakeLevel = 1f)
    private fun brain(engine: EngineService, seed: Int) = BotBrain(engine, Random(seed), source, EmptyCoroutineContext)

    @Test
    fun ratingPicksTheMaiaNetworkHowFreelyItSamplesAndWhetherItChecks() {
        val at1800 = BotStrengthModel.settingsFor(bot(1800), maiaAvailable = true).maia!!
        assertEquals(MaiaSettings(1900, 0.65, 7, 200), at1800.copy(temperature = Math.round(at1800.temperature * 100) / 100.0))
        val beginner = BotStrengthModel.settingsFor(bot(250), maiaAvailable = true)
        assertEquals(1100, beginner.maia!!.net)
        assertEquals(2.5, beginner.maia!!.temperature, 1e-9)
        assertEquals(0.30, beginner.randomMoveChance, 1e-9)
        assertNull("beginners do not check their moves", beginner.maia!!.checkMargin)
        assertNull("above 2400: engine mode", BotStrengthModel.settingsFor(bot(2600), maiaAvailable = true).maia)
        assertNull("without Maia: engine mode", BotStrengthModel.settingsFor(bot(1800), maiaAvailable = false).maia)
        val careful = BotStrengthModel.settingsFor(bot(1800).copy(mistakeLevel = 0f), maiaAvailable = true).maia!!
        assertTrue("Never makes mistakes: sharper and stricter", careful.temperature < at1800.temperature && careful.checkMargin!! < at1800.checkMargin!!)
    }

    @Test
    fun aMaiaBotAnswers1e4LikeAHumanWouldMostlyWith1e5Or1c5() = runBlocking {
        val engine = ScriptedEngine()
        val replies = (0 until 60).map { brain(engine, it).decide(afterE4, 30, bot(1200), Repertoire.NONE, history = listOf(Position.initial(), afterE4))!!.uci }
        val counts = replies.groupingBy { it }.eachCount()
        assertTrue("got $counts", (counts["e7e5"] ?: 0) + (counts["c7c5"] ?: 0) > 30)
        assertTrue("but not always the same move", counts.size > 2)
        assertTrue("a quick eval only, no deep check", engine.searchedDepths.all { it == BotBrain.EVAL_DEPTH })
    }

    @Test
    fun aStrongMaiaBotRejectsAPickTheSearchFindsLosing() = runBlocking {
        // After 1.e4 e5 the scripted engine says White is winning by a rook: e5 is a "blunder".
        val e5 = LegalMoveGenerator.makeMove(afterE4, LegalMoveGenerator.generateLegalMoves(afterE4).first { it.uci == "e7e5" })
        val engine = ScriptedEngine(mapOf(e5.toFen().substringBefore(" ") to 500))
        val strong = (0 until 30).map { brain(engine, it).decide(afterE4, 30, bot(2000), Repertoire.NONE, history = listOf(Position.initial(), afterE4))!! }
        assertTrue("never e5", strong.none { it.uci == "e7e5" })
        assertTrue(engine.searchedDepths.contains(8))
        val weak = (0 until 30).map { brain(ScriptedEngine(mapOf(e5.toFen().substringBefore(" ") to 500)), it).decide(afterE4, 30, bot(1000), Repertoire.NONE)!!.uci }
        assertTrue("a 1000 bot does not check, so it still plays e5 often", weak.count { it == "e7e5" } > 5)
    }

    @Test
    fun withoutMaiaTheBotStillPlaysInTheEngineMode() = runBlocking {
        val engine = ScriptedEngine()
        val decision = BotBrain(engine, Random(1), maia = MaiaSource { null }, compute = EmptyCoroutineContext)
            .decide(afterE4, 30, bot(1200), Repertoire.NONE)!!
        assertNotEquals("", decision.uci)
        assertTrue("engine mode searches at the rating's depth", engine.searchedDepths.contains(4))
    }
}
