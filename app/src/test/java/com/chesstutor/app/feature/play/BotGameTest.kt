package com.chesstutor.app.feature.play

import com.chesstutor.app.board.BoardSound
import com.chesstutor.app.board.MarkKind
import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.data.repository.InMemoryGameRepository
import com.chesstutor.app.domain.MoveChoice
import com.chesstutor.app.domain.bot.BotBrain
import com.chesstutor.app.domain.bot.BotProfile
import com.chesstutor.app.domain.bot.BotRoster
import com.chesstutor.app.domain.bot.ChatLevel
import com.chesstutor.app.domain.bot.ResignPolicy
import com.chesstutor.app.domain.game.GameEnd
import com.chesstutor.app.domain.game.GameRules
import com.chesstutor.app.domain.game.GameSetup
import com.chesstutor.app.domain.game.TimeControl
import com.chesstutor.app.domain.opening.BattleChoice
import com.chesstutor.app.domain.opening.BattleMode
import com.chesstutor.app.domain.opening.Opening
import com.chesstutor.app.domain.opening.OpeningTree
import com.chesstutor.app.engine.EngineEvaluation
import com.chesstutor.app.engine.EngineHealthReport
import com.chesstutor.app.engine.EngineLine
import com.chesstutor.app.engine.EngineService
import com.chesstutor.app.engine.EngineStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class BotGameTest {

    /** Plays the first legal move with a fixed score (White's view); suggests e2e4 as a hint. */
    private class FakeEngine(var whiteScore: Int = 0) : EngineService {
        override val status: StateFlow<EngineStatus> = MutableStateFlow(EngineStatus())
        override suspend fun bestMove(fen: String, movetimeMs: Int): MoveChoice = MoveChoice.fromUci("e2e4")
        override suspend fun evaluate(fen: String, movetimeMs: Int): EngineEvaluation = EngineEvaluation(centipawns = whiteScore)
        override suspend fun analyse(fen: String, lines: Int, movetimeMs: Int): List<EngineLine> {
            val first = LegalMoveGenerator.generateLegalMoves(Position.fromFen(fen)).firstOrNull() ?: return emptyList()
            return listOf(EngineLine(1, listOf(first.uci), whiteScore))
        }
        override suspend fun botMove(fen: String, elo: Int): MoveChoice? = null
        override suspend fun healthCheck(): EngineHealthReport? = null
    }

    private val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(StandardTestDispatcher(scheduler) + SupervisorJob())
    private val games = InMemoryGameRepository()
    private val engine = FakeEngine()

    @After
    fun tearDown() = scope.cancel()

    private fun step(ms: Long = 0) {
        scheduler.advanceTimeBy(ms)
        scheduler.runCurrent()
    }

    private fun game(
        bot: BotProfile = BotRoster.byId("wayne")!!.profile.copy(mistakeLevel = 0f),
        color: PieceColor = PieceColor.WHITE,
        rules: GameRules = GameRules(),
        fen: String = Position.STARTING_FEN,
        resume: List<String> = emptyList(),
        battle: BattleChoice? = null,
        watch: BotProfile? = null,
        chat: ChatLevel = ChatLevel.SOME,
    ) = BotGame(
        GameSetup(bot, color, rules, fen, watch), BotBrain(engine, Random(7)), engine, { tree }, games, scope,
        clock = { scheduler.currentTime }, resumeMoves = resume, battle = battle, random = Random(11), chatLevel = { chat },
    ).also { it.start(); step() }

    private fun BotGame.play(uci: String) {
        val move = LegalMoveGenerator.generateLegalMoves(session.game.position).first { it.uci == uci }
        session.onMove(move)
        step()
    }

    @Test
    fun thePlayerMovesThenTheBotAnswersFromItsBook() {
        val g = game()
        assertEquals(GamePhase.PLAYER_TURN, g.state.value.phase)
        assertEquals(PieceColor.WHITE, g.session.state.value.movable)

        g.play("e2e4")
        assertEquals(GamePhase.BOT_TURN, g.state.value.phase)
        assertNull("the player waits while the bot thinks", g.session.state.value.movable)

        step(5_000)
        assertEquals("Wayne answers 1.e4 with his Scandinavian", "d7d5", g.session.moves.last().uci)
        assertEquals(GamePhase.PLAYER_TURN, g.state.value.phase)
        assertEquals("Scandinavian Defense", g.state.value.opening)
        assertTrue(g.state.value.inBook)
        assertEquals("the bot's move is heard", BoardSound.MOVE, g.state.value.sound?.feedback?.sound)
    }

    @Test
    fun asBlackTheBotOpensAndItsFirstMoveCannotBeTakenBack() {
        val g = game(color = PieceColor.BLACK)
        assertEquals(GamePhase.BOT_TURN, g.state.value.phase)
        step(5_000)
        assertEquals("Wayne plays his Italian", "e2e4", g.session.moves.single().uci)
        assertFalse(g.state.value.canTakeBack)
        g.play("e7e5")
        step(5_000)
        assertTrue(g.state.value.canTakeBack)
        g.takeBack()
        assertEquals(listOf("e2e4"), g.session.moves.map { it.uci })
        assertEquals(GamePhase.PLAYER_TURN, g.state.value.phase)
    }

    @Test
    fun aTakebackUndoesTheBotsReplyAndThePlayersMove() {
        val g = game()
        g.play("e2e4")
        step(5_000)
        assertTrue(g.state.value.canTakeBack)
        g.takeBack()
        assertTrue(g.session.moves.isEmpty())
        assertEquals(1, g.state.value.takebacksUsed)
        assertEquals(PieceColor.WHITE, g.session.state.value.movable)
        assertFalse("nothing left to take back", g.state.value.canTakeBack)
    }

    @Test
    fun takebacksCanBeSwitchedOff() {
        val g = game(rules = GameRules(takebacks = false))
        g.play("e2e4")
        step(5_000)
        assertFalse(g.state.value.canTakeBack)
        g.takeBack()
        assertEquals(2, g.session.moves.size)
    }

    @Test
    fun checkmateEndsAndSavesTheGame() = runBlocking {
        val g = game(fen = "6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1")
        g.play("a1a8")
        val state = g.state.value
        assertEquals(GamePhase.OVER, state.phase)
        assertEquals(PieceColor.WHITE, state.result?.winner)
        assertEquals(GameEnd.CHECKMATE, state.result?.end)
        assertTrue(state.playerWon)
        assertEquals(BoardSound.WIN, state.sound?.feedback?.sound)
        step()
        assertTrue(g.state.value.saved)
        val saved = games.getRecentGames().single()
        assertEquals("1-0", saved.result)
        assertEquals("white", saved.userColor)
        assertEquals("Wayne", saved.botName)
        assertEquals("a1a8", saved.uciMoves)
        assertTrue(saved.pgn.contains("Ra8#"))
        assertEquals("Win", saved.userOutcomeLabel)
    }

    @Test
    fun aLostBotResignsWhenItsRulesSaySo() {
        engine.whiteScore = 2_000
        val bot = BotProfile("t", "Test", 1500, resign = ResignPolicy.EARLY)
        val g = game(bot = bot, fen = "4k3/8/8/8/8/8/QQ6/4K3 w - - 0 1")
        g.play("e1f1")
        step(5_000)
        assertEquals(GamePhase.PLAYER_TURN, g.state.value.phase)
        g.play(LegalMoveGenerator.generateLegalMoves(g.session.game.position).first { it.uci.startsWith("f1") }.uci)
        step(5_000)
        assertEquals(GameEnd.RESIGNATION, g.state.value.result?.end)
        assertEquals(PieceColor.WHITE, g.state.value.result?.winner)
    }

    @Test
    fun runningOutOfTimeLoses() {
        val g = game(rules = GameRules(timeControl = TimeControl.BLITZ_5))
        step(5 * 60_000L - 1_000)
        assertEquals(GamePhase.PLAYER_TURN, g.state.value.phase)
        assertTrue(g.liveClocks.first!! in 900L..1_000L)
        step(1_200)
        assertEquals(GameEnd.TIMEOUT, g.state.value.result?.end)
        assertEquals(PieceColor.BLACK, g.state.value.result?.winner)
        assertEquals(0L, g.state.value.whiteMs)
    }

    @Test
    fun incrementIsAddedAfterEachMove() {
        val g = game(rules = GameRules(timeControl = TimeControl.RAPID_15_10))
        step(4_000)
        g.play("e2e4")
        assertEquals(15 * 60_000L - 4_000 + 10_000, g.state.value.whiteMs)
    }

    @Test
    fun theFinalClocksCountTheUnfinishedTurn() {
        val g = game(rules = GameRules(timeControl = TimeControl.RAPID_10))
        step(4_000)
        g.resign()
        assertEquals(10 * 60_000L - 4_000, g.state.value.whiteMs)
        assertEquals(10 * 60_000L - 4_000, g.liveClocks.first)
    }

    @Test
    fun hintsShowTheEnginesMoveAndAreCounted() {
        val g = game()
        g.hint()
        step()
        val hint = g.state.value.hint!!
        assertEquals("e2" to "e4", hint.from.algebraic to hint.to.algebraic)
        assertEquals(MarkKind.HINT, hint.kind)
        assertEquals(1, g.state.value.hintsUsed)
        g.play("d2d4")
        assertNull("a move clears the hint", g.state.value.hint)
    }

    @Test
    fun theEvalBarFollowsThePositionWhenSwitchedOn() {
        engine.whiteScore = 35
        assertNull(game().state.value.evalCp)
        assertEquals(35, game(rules = GameRules(evalBar = true)).state.value.evalCp)
    }

    @Test
    fun resigningEndsTheGameForTheBot() {
        val g = game(color = PieceColor.BLACK)
        g.resign()
        assertEquals(PieceColor.WHITE, g.state.value.result?.winner)
        assertFalse(g.state.value.playerWon)
        assertEquals(BoardSound.LOSE, g.state.value.sound?.feedback?.sound)
    }

    @Test
    fun aResumedGameReplaysItsMovesFirst() {
        val g = game(resume = listOf("e2e4", "d7d5"))
        assertEquals(listOf("e2e4", "d7d5"), g.session.moves.map(Move::uci))
        assertEquals(GamePhase.PLAYER_TURN, g.state.value.phase)
        assertEquals("Scandinavian Defense", g.state.value.opening)
    }

    @Test
    fun aDrillTakesBackMovesOffTheLineAndEndsWithTheLine() = runBlocking {
        val italian = Opening("C50", "Italian Game", listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1c4"))
        val g = game(rules = GameRules(takebacks = false), battle = BattleChoice(italian, BattleMode.DRILL))
        assertEquals("Italian Game", g.state.value.battleTitle)
        g.play("e2e4")
        step(5_000)
        assertEquals("the bot plays the book reply", "e7e5", g.session.moves.last().uci)

        g.play("d2d4")
        assertEquals("the wrong move is taken back", listOf("e2e4", "e7e5"), g.session.moves.map(Move::uci))
        assertEquals(1, g.state.value.drillMistakes)
        assertTrue(g.state.value.drillMessage!!.contains("Nf3"))
        assertEquals("g1" to "f3", g.state.value.hint!!.from.algebraic to g.state.value.hint!!.to.algebraic)
        assertEquals(GamePhase.PLAYER_TURN, g.state.value.phase)
        assertEquals(BoardSound.WRONG, g.state.value.sound?.feedback?.sound)

        g.play("g1f3")
        step(5_000)
        g.play("f1c4")
        assertEquals(GamePhase.DRILL_DONE, g.state.value.phase)
        assertTrue(g.state.value.drillMessage!!.contains("1 slip"))
        assertNull(g.session.state.value.movable)
        step()
        assertTrue("drills stay out of the games list", games.getRecentGames().isEmpty())
    }

    @Test
    fun inABattleTheBotFollowsTheWholeLineWhateverItsStrength() {
        val najdorf = Opening("B90", "Sicilian Defense: Najdorf Variation", "e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4 g8f6 b1c3 a7a6".split(' '))
        // Wayne normally leaves his book after 6 plies.
        val g = game(battle = BattleChoice(najdorf, BattleMode.PLAY))
        listOf("e2e4", "g1f3", "d2d4", "f3d4", "b1c3").forEach { move ->
            g.play(move)
            step(5_000)
        }
        assertEquals(najdorf.moves, g.session.moves.map(Move::uci))
        assertEquals("Sicilian Defense: Najdorf Variation", g.state.value.opening)
    }

    @Test
    fun theBotGreetsAndChatsUnlessChatIsOff() {
        val chatty = game(chat = ChatLevel.LOTS)
        assertEquals("Wayne greets in his own words", PieceColor.BLACK, chatty.state.value.chat?.speaker)
        assertTrue(chatty.state.value.chat!!.text.isNotBlank())
        step(6_000)
        assertNull("lines fade after a few seconds", chatty.state.value.chat)
        assertNull(game(chat = ChatLevel.OFF).state.value.chat)
    }

    @Test
    fun theBotNoticesAPlayerBlunder() {
        val g = game(fen = "4k3/8/8/8/8/8/3QQ3/4K3 b - - 0 1", color = PieceColor.WHITE, chat = ChatLevel.LOTS)
        // The bot (Black) moves first; the engine then sees White far better, then far worse.
        engine.whiteScore = 0
        step(5_000)
        // The bot judges the position as soon as the player has moved.
        engine.whiteScore = -900
        g.play(LegalMoveGenerator.generateLegalMoves(g.session.game.position).first().uci)
        step(5_000)
        val said = g.state.value.chat?.text.orEmpty()
        assertTrue("got: $said", said.contains("gift") || said.contains("second"))
    }

    @Test
    fun twoBotsPlayEachOtherWhileThePlayerWatches() = runBlocking {
        val g = game(color = PieceColor.WHITE, watch = BotRoster.byId("juno")!!.profile.copy(mistakeLevel = 0f))
        repeat(6) { step(5_000) }
        assertTrue("both sides moved: ${g.session.moves.size}", g.session.moves.size >= 6)
        assertNull("the player cannot move", g.session.state.value.movable)
        assertFalse(g.state.value.canTakeBack)

        g.togglePause()
        val held = g.session.moves.size
        repeat(3) { step(5_000) }
        assertEquals("paused", held, g.session.moves.size)
        g.togglePause()
        step(5_000)
        assertTrue(g.session.moves.size > held)

        g.resign()
        step()
        assertTrue("watched games stay out of the games list", games.getRecentGames().isEmpty())
    }

    companion object {
        private val tree: OpeningTree by lazy { File("src/main/assets/openings.tsv.gz").inputStream().use(OpeningTree::fromStream) }
    }
}
