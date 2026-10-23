package com.chesstutor.app.feature.play

import com.chesstutor.app.board.BoardArrow
import com.chesstutor.app.board.BoardFeedback
import com.chesstutor.app.board.BoardSound
import com.chesstutor.app.board.HapticPattern
import com.chesstutor.app.board.MarkKind
import com.chesstutor.app.board.moveListOf
import com.chesstutor.app.core.GameStatus
import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.PieceType
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.SanFormatter
import com.chesstutor.app.core.repetitionKey
import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.data.repository.GameRepository
import com.chesstutor.app.domain.PgnFormatter
import com.chesstutor.app.domain.bot.BotBrain
import com.chesstutor.app.domain.bot.BotChat
import com.chesstutor.app.domain.bot.BotProfile
import com.chesstutor.app.domain.bot.ChatEvent
import com.chesstutor.app.domain.bot.ChatLevel
import com.chesstutor.app.domain.bot.BotResign
import com.chesstutor.app.domain.bot.BotStrengthModel
import com.chesstutor.app.domain.game.GameEnd
import com.chesstutor.app.domain.game.GameResult
import com.chesstutor.app.domain.game.GameSetup
import com.chesstutor.app.domain.opening.BattleChoice
import com.chesstutor.app.domain.opening.BattlePlan
import com.chesstutor.app.domain.opening.OpeningBattles
import com.chesstutor.app.domain.opening.OpeningTree
import com.chesstutor.app.domain.opening.Repertoire
import com.chesstutor.app.engine.EngineService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.random.Random

enum class GamePhase { STARTING, PLAYER_TURN, BOT_TURN, OVER, /** Opening Battle drill: the line is finished. */ DRILL_DONE }

/** Something a bot says (review R6); [speaker] is the side of the bot talking. */
data class ChatLine(val id: Int, val text: String, val speaker: PieceColor)

/** A sound for the screen to play once; [id] changes with every new event. */
data class GameSoundEvent(val id: Int, val feedback: BoardFeedback)

data class BotGameState(
    val setup: GameSetup,
    val phase: GamePhase = GamePhase.STARTING,
    val whiteMs: Long? = setup.rules.timeControl.initialMs,
    val blackMs: Long? = setup.rules.timeControl.initialMs,
    val result: GameResult? = null,
    /** The deepest named opening reached, and whether the game is still in the database. */
    val opening: String? = null,
    val inBook: Boolean = true,
    val hint: BoardArrow? = null,
    val hintsUsed: Int = 0,
    val takebacksUsed: Int = 0,
    /** White's view, only while the eval bar rule is on. */
    val evalCp: Int? = null,
    val evalMate: Int? = null,
    val canTakeBack: Boolean = false,
    val sound: GameSoundEvent? = null,
    val saved: Boolean = false,
    /** Opening Battle: the line or family being played, mistakes in a drill and the last drill note. */
    val battleTitle: String? = null,
    val drillMistakes: Int = 0,
    val drillMessage: String? = null,
    val chat: ChatLine? = null,
    /** Watch mode only: the game is held. */
    val paused: Boolean = false,
) {
    val playerWon: Boolean get() = result?.winner == setup.playerColor
    val isWatch: Boolean get() = setup.watch != null
    val botColor: PieceColor get() = setup.playerColor.opposite()
}

/** Pauses and clock ticks (shortened in tests). */
data class GameTiming(val tickMs: Long = 100, val hintMovetimeMs: Int = 800, val evalMovetimeMs: Int = 400, val chatMs: Long = 5_000)

/**
 * One game against a bot (plan 6.1, step 3C): whose turn it is, the bot's moves through
 * [BotBrain], clocks, takebacks, hints, the optional eval bar, resigning, the result, and
 * saving the finished game to the games list.
 */
class BotGame(
    val setup: GameSetup,
    private val brain: BotBrain,
    private val engine: EngineService,
    private val openings: suspend () -> OpeningTree,
    private val games: GameRepository,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val timing: GameTiming = GameTiming(),
    /** Moves to replay first, and the clocks as they were, for a resumed game. */
    private val resumeMoves: List<String> = emptyList(),
    resumeClocks: Pair<Long?, Long?>? = null,
    /** Set for an Opening Battle (step 3E); its book replaces the bot's own repertoire. */
    val battle: BattleChoice? = null,
    private val random: Random = Random.Default,
    /** How chatty the bots are; read at every line, so the setting applies at once. */
    private val chatLevel: () -> ChatLevel = { ChatLevel.SOME },
) {
    val session = PlaySession(Position.fromFen(setup.startFen), setup.playerColor, ::onPlayerMoved)

    private val mutableState = MutableStateFlow(
        BotGameState(setup).let { s -> resumeClocks?.let { (white, black) -> s.copy(whiteMs = white, blackMs = black) } ?: s }
    )
    val state: StateFlow<BotGameState> = mutableState.asStateFlow()

    private val standardStart = setup.startFen == Position.STARTING_FEN
    private var tree: OpeningTree = OpeningTree.EMPTY
    private var repertoire: Repertoire = Repertoire.NONE
    private var plan: BattlePlan? = null
    private val isDrill: Boolean get() = plan?.isDrill == true
    /** Where the player's current move starts, to judge it in a drill. */
    private var playerTurnStart: Position? = null
    private val botEvals = mutableListOf<Int>()
    /** Watch mode: the second bot's book and evaluations. */
    private var watchRepertoire: Repertoire = Repertoire.NONE
    private val watchEvals = mutableListOf<Int>()
    /** The bot's view after the player's previous move, to notice a blunder. */
    private var lastBotEval: Int? = null
    private var lastChatPly = -10
    private var chatId = 0
    private var chatJob: Job? = null
    private var paused = false
    private var botJob: Job? = null
    private var evalJob: Job? = null
    private var ticker: Job? = null
    private var turnStartedAt = 0L
    private var soundId = 0
    private val startedAt = clock()

    fun start() {
        scope.launch {
            if (standardStart) {
                tree = runCatching { openings() }.getOrDefault(OpeningTree.EMPTY)
                repertoire = Repertoire.of(tree, setup.bot.whiteOpenings, setup.bot.blackOpenings)
                setup.watch?.let { watchRepertoire = Repertoire.of(tree, it.whiteOpenings, it.blackOpenings) }
                battle?.let { choice ->
                    val battlePlan = OpeningBattles.plan(tree, choice, random)
                    plan = battlePlan
                    repertoire = battlePlan.repertoire
                    mutableState.update { it.copy(battleTitle = battlePlan.title) }
                }
            }
            for (uci in resumeMoves) {
                val legal = LegalMoveGenerator.generateLegalMoves(session.game.position).firstOrNull { it.uci == uci } ?: break
                session.play(legal)
            }
            updateOpening()
            turnStartedAt = clock()
            if (setup.rules.timeControl.initialMs != null) startTicker()
            if (!checkGameOver()) {
                if (resumeMoves.isEmpty()) say(ChatEvent.GREETING, setup.bot, state.value.botColor)
                nextTurn()
            }
        }
    }

    // ------------------------------------------------------------------ turns

    private fun nextTurn() {
        val playerToMove = session.game.position.sideToMove == setup.playerColor
        val watch = setup.watch
        session.open = playerToMove && watch == null
        if (playerToMove) playerTurnStart = session.game.position
        mutableState.update { it.copy(phase = if (playerToMove) GamePhase.PLAYER_TURN else GamePhase.BOT_TURN, canTakeBack = canTakeBack(playerToMove)) }
        if (paused) return
        // The engine does one thing at a time: the eval bar updates while the player thinks.
        botJob = when {
            playerToMove && watch != null -> scope.launch { moveFor(watch, setup.playerColor, watchRepertoire, watchEvals) }
            playerToMove -> null.also { refreshEval() }
            else -> scope.launch { moveFor(setup.bot, state.value.botColor, repertoire, botEvals) }
        }
    }

    private fun onPlayerMoved(move: Move) {
        mutableState.update { it.copy(hint = null) }
        val start = playerTurnStart
        if (isDrill && start != null) {
            val book = repertoire.movesFor(setup.playerColor, start.repetitionKey())
            if (book.none { it.uci == move.uci }) {
                rejectInDrill(start, book.firstOrNull()?.uci)
                return
            }
            mutableState.update { it.copy(drillMessage = null) }
        }
        val (capture, check) = lastMoveFacts()
        when {
            check -> say(ChatEvent.PLAYER_CHECKS, setup.bot, state.value.botColor)
            capture -> say(ChatEvent.PLAYER_CAPTURES, setup.bot, state.value.botColor)
        }
        afterMove(setup.playerColor)
    }

    /** Whether the last move took a piece, and whether it gives check. */
    private fun lastMoveFacts(): Pair<Boolean, Boolean> {
        val board = session.state.value
        val before = board.previous?.squares?.count { it != null } ?: return false to false
        val after = board.position.squares.count { it != null }
        return (after < before) to LegalMoveGenerator.isKingInCheck(board.position, board.position.sideToMove)
    }

    /** A drill move off the line is taken back, and the book move is shown. */
    private fun rejectInDrill(start: Position, expected: String?) {
        session.undo(1)
        val legal = expected?.let { uci -> LegalMoveGenerator.generateLegalMoves(start).firstOrNull { it.uci == uci } }
        val san = legal?.let { SanFormatter.format(start, it) }
        sound(BoardFeedback(BoardSound.WRONG, HapticPattern.DOUBLE))
        mutableState.update {
            it.copy(
                drillMistakes = it.drillMistakes + 1,
                drillMessage = if (san != null) "Not the book move. The line goes on with $san. Try again." else "Not the book move. Try again.",
                hint = legal?.let { m -> BoardArrow(m.from, m.to, MarkKind.HINT) },
            )
        }
    }

    /** One move for the bot [profile] playing [color] (the opponent, or either side when watching). */
    private suspend fun moveFor(profile: BotProfile, color: PieceColor, book: Repertoire, evals: MutableList<Int>) {
        val position = session.game.position
        val began = clock()
        val clockMs = remainingFor(color)
        val bookDepth = if (plan != null) Int.MAX_VALUE else BotStrengthModel.bookDepth(profile.rating)
        val decision = brain.decide(position, session.moves.size, profile, book, clockMs, bookDepth) ?: return
        var playerBlundered = false
        decision.evalForBot?.let { eval ->
            evals += eval
            if (setup.watch == null) {
                // A big jump in the bot's favour right after the player's move: the player slipped.
                val before = lastBotEval
                playerBlundered = before != null && eval - before >= BLUNDER_JUMP && eval >= BLUNDER_FLOOR
                lastBotEval = eval
            }
        }
        if (BotResign.shouldResign(profile, evals)) {
            finish(GameResult(color.opposite(), GameEnd.RESIGNATION))
            return
        }
        val wait = decision.thinkMs - (clock() - began)
        if (wait > 0) delay(wait)
        val legal = LegalMoveGenerator.generateLegalMoves(position).firstOrNull { it.uci == decision.uci } ?: return
        sound(session.play(legal))
        val (capture, check) = lastMoveFacts()
        when {
            playerBlundered -> say(ChatEvent.PLAYER_BLUNDER, profile, color)
            check -> say(ChatEvent.BOT_CHECKS, profile, color)
            capture -> say(ChatEvent.BOT_CAPTURES, profile, color)
        }
        afterMove(color)
    }

    private fun afterMove(mover: PieceColor) {
        chargeClock(mover)
        updateOpening()
        if (checkGameOver()) return
        if (isDrill && repertoire.movesFor(session.game.position.sideToMove, session.game.position.repetitionKey()).isEmpty()) {
            finishDrill()
            return
        }
        nextTurn()
    }

    private fun finishDrill() {
        ticker?.cancel()
        session.open = false
        val mistakes = state.value.drillMistakes
        mutableState.update {
            it.copy(
                phase = GamePhase.DRILL_DONE,
                canTakeBack = false,
                hint = null,
                drillMessage = if (mistakes == 0) "Line complete, without a single slip!" else "Line complete, with $mistakes ${if (mistakes == 1) "slip" else "slips"}.",
            )
        }
        sound(BoardFeedback(BoardSound.WIN, HapticPattern.LONG))
    }

    // ------------------------------------------------------------------ player actions

    fun takeBack() {
        val current = state.value
        if (!current.canTakeBack) return
        val plies = if (current.phase == GamePhase.BOT_TURN) 1 else 2
        botJob?.cancel()
        session.undo(plies)
        botEvals.clear()
        lastBotEval = null
        mutableState.update { it.copy(takebacksUsed = it.takebacksUsed + 1, hint = null) }
        say(ChatEvent.TAKEBACK, setup.bot, state.value.botColor)
        updateOpening()
        turnStartedAt = clock()
        nextTurn()
    }

    fun hint() {
        val current = state.value
        if (!setup.rules.hints || current.phase != GamePhase.PLAYER_TURN) return
        if (isDrill) {
            // In a drill the hint is the book move, not the engine's.
            val position = session.game.position
            val uci = repertoire.movesFor(setup.playerColor, position.repetitionKey()).firstOrNull()?.uci ?: return
            val move = LegalMoveGenerator.generateLegalMoves(position).firstOrNull { it.uci == uci } ?: return
            mutableState.update { it.copy(hint = BoardArrow(move.from, move.to, MarkKind.HINT), hintsUsed = it.hintsUsed + 1) }
            return
        }
        val fen = session.game.position.toFen()
        scope.launch {
            val best = runCatching { engine.bestMove(fen, timing.hintMovetimeMs) }.getOrNull() ?: return@launch
            if (session.game.position.toFen() != fen) return@launch
            val move = best.toCoreMove()
            mutableState.update { it.copy(hint = BoardArrow(move.from, move.to, MarkKind.HINT), hintsUsed = it.hintsUsed + 1) }
        }
    }

    fun resign() {
        if (state.value.phase == GamePhase.OVER || state.value.phase == GamePhase.DRILL_DONE) return
        finish(GameResult(state.value.botColor, GameEnd.RESIGNATION))
    }

    /** Watch mode: holds the game, or lets it go on. */
    fun togglePause() {
        if (setup.watch == null || state.value.phase == GamePhase.OVER) return
        paused = !paused
        mutableState.update { it.copy(paused = paused) }
        if (paused) botJob?.cancel() else nextTurn()
    }

    /** Stops the bot and the clock, for leaving the screen without ending the game. */
    fun pause() {
        botJob?.cancel()
        ticker?.cancel()
        evalJob?.cancel()
    }

    // ------------------------------------------------------------------ rules

    private fun canTakeBack(playerToMove: Boolean): Boolean {
        if (!setup.rules.takebacks || setup.watch != null) return false
        val playerMovesFirst = Position.fromFen(setup.startFen).sideToMove == setup.playerColor
        val plies = session.moves.size
        // On the player's turn two plies go (the bot's reply and the player's move); the bot's
        // own first move, when it starts, is never taken back.
        return if (playerToMove) plies >= (if (playerMovesFirst) 2 else 3) else plies >= 1
    }

    private fun checkGameOver(): Boolean {
        val game = session.game
        val mover = game.position.sideToMove.opposite()
        val result = when (game.status) {
            GameStatus.CHECKMATE -> GameResult(mover, GameEnd.CHECKMATE)
            GameStatus.STALEMATE -> GameResult(null, GameEnd.STALEMATE)
            GameStatus.DRAW_INSUFFICIENT_MATERIAL -> GameResult(null, GameEnd.INSUFFICIENT_MATERIAL)
            // Claimable draws end the game: the bot always claims them.
            GameStatus.DRAW_FIVEFOLD_REPETITION, GameStatus.DRAW_THREEFOLD_REPETITION -> GameResult(null, GameEnd.REPETITION)
            GameStatus.DRAW_75_MOVES, GameStatus.DRAW_50_MOVES -> GameResult(null, GameEnd.FIFTY_MOVES)
            else -> null
        } ?: return false
        finish(result)
        return true
    }

    private fun finish(result: GameResult) {
        if (state.value.phase == GamePhase.OVER) return
        botJob?.cancel()
        ticker?.cancel()
        evalJob?.cancel()
        session.open = false
        // The turn in progress still costs time, so the final clocks are what was left.
        if (result.end != GameEnd.TIMEOUT) chargeClock(session.game.position.sideToMove, withIncrement = false)
        val win = result.winner == setup.playerColor
        val feedback = when {
            result.winner == null -> BoardFeedback(BoardSound.MOVE, HapticPattern.TICK)
            win -> BoardFeedback(BoardSound.WIN, HapticPattern.LONG)
            else -> BoardFeedback(BoardSound.LOSE, HapticPattern.DOUBLE)
        }
        mutableState.update { it.copy(phase = GamePhase.OVER, result = result, hint = null, canTakeBack = false) }
        sound(feedback)
        val botColor = state.value.botColor
        val watch = setup.watch
        when {
            result.winner == null -> say(ChatEvent.DRAW, setup.bot, botColor)
            watch != null -> {
                val winner = if (result.winner == botColor) setup.bot else watch
                say(ChatEvent.BOT_WINS, winner, result.winner)
            }
            result.winner == botColor -> say(ChatEvent.BOT_WINS, setup.bot, botColor)
            else -> say(ChatEvent.PLAYER_WINS, setup.bot, botColor)
        }
        // Drills are practice and watched games are not the player's: neither goes in the games list.
        if (isDrill || setup.watch != null) return
        scope.launch {
            runCatching { games.saveGame(record(result)) }
            mutableState.update { it.copy(saved = true) }
        }
    }

    private fun record(result: GameResult): GameRecord {
        val sans = moveListOf(session.start, session.moves).map { it.san }
        val playerWhite = setup.playerColor == PieceColor.WHITE
        val bot = setup.bot
        return GameRecord(
            id = "bot_${bot.id}_$startedAt",
            dateMillis = startedAt,
            botName = bot.name,
            botRating = bot.rating,
            result = result.pgn,
            pgn = PgnFormatter.formatPgn(
                moves = sans,
                whitePlayer = if (playerWhite) "You" else bot.name,
                blackPlayer = if (playerWhite) bot.name else "You",
                whiteElo = if (playerWhite) null else bot.rating,
                blackElo = if (playerWhite) bot.rating else null,
                result = result.pgn,
                date = Date(startedAt),
                event = "Chess Tutor: vs ${bot.name}",
                startFen = setup.startFen,
            ),
            moveCount = session.moves.size,
            userColor = if (playerWhite) "white" else "black",
            finalFen = session.game.position.toFen(),
            uciMoves = session.moves.joinToString(" ") { it.uci },
            termination = result.end.label,
            timeClass = setup.rules.timeControl.timeClass,
        )
    }

    // ------------------------------------------------------------------ clocks

    private fun startTicker() {
        ticker = scope.launch {
            while (isActive) {
                delay(timing.tickMs)
                val side = session.game.position.sideToMove
                val left = remainingFor(side) ?: return@launch
                if (left <= 0) {
                    flag(side)
                    return@launch
                }
                publishClocks()
            }
        }
    }

    /** Time left for [side] right now, counting the turn in progress. */
    private fun remainingFor(side: PieceColor): Long? {
        val stored = (if (side == PieceColor.WHITE) state.value.whiteMs else state.value.blackMs) ?: return null
        val running = state.value.phase != GamePhase.OVER && session.game.position.sideToMove == side
        return if (running) stored - (clock() - turnStartedAt) else stored
    }

    /** Live clocks for the screen; the stored values change only when a move is made. */
    val liveClocks: Pair<Long?, Long?> get() = remainingFor(PieceColor.WHITE) to remainingFor(PieceColor.BLACK)

    private val clockTick = MutableStateFlow(0L)
    /** Ticks every [GameTiming.tickMs] while a clock runs, so the screen redraws the clocks. */
    val clockTicks: StateFlow<Long> = clockTick.asStateFlow()

    private fun publishClocks() {
        clockTick.value = clock()
    }

    private fun chargeClock(mover: PieceColor, withIncrement: Boolean = true) {
        val increment = if (withIncrement) setup.rules.timeControl.incrementMs else 0L
        val spent = clock() - turnStartedAt
        mutableState.update { s ->
            when (mover) {
                PieceColor.WHITE -> s.copy(whiteMs = s.whiteMs?.let { it - spent + increment })
                PieceColor.BLACK -> s.copy(blackMs = s.blackMs?.let { it - spent + increment })
            }
        }
        turnStartedAt = clock()
    }

    private fun flag(side: PieceColor) {
        mutableState.update { s -> if (side == PieceColor.WHITE) s.copy(whiteMs = 0) else s.copy(blackMs = 0) }
        // A lone king cannot win on time.
        val opponentCanMate = session.game.position.squares.any { it != null && it.color != side && it.type != PieceType.KING }
        finish(GameResult(if (opponentCanMate) side.opposite() else null, GameEnd.TIMEOUT))
    }

    // ------------------------------------------------------------------ book and eval

    private fun updateOpening() {
        if (!standardStart || tree.isEmpty) {
            mutableState.update { it.copy(inBook = false) }
            return
        }
        val match = tree.match(session.moves.map { it.uci })
        mutableState.update { it.copy(opening = match.opening?.name, inBook = match.leftBookAt == null) }
    }

    private fun refreshEval() {
        if (!setup.rules.evalBar) return
        evalJob?.cancel()
        val fen = session.game.position.toFen()
        evalJob = scope.launch {
            val eval = runCatching { engine.evaluate(fen, timing.evalMovetimeMs) }.getOrNull() ?: return@launch
            mutableState.update { it.copy(evalCp = eval.centipawns, evalMate = eval.mateInMoves) }
        }
    }

    /**
     * A line from [profile] about [event], if the chat setting and a little chance allow. Lines
     * during play are spaced at least two plies apart and fade after a few seconds.
     */
    private fun say(event: ChatEvent, profile: BotProfile, speaker: PieceColor) {
        if (isDrill || !BotChat.shouldSpeak(event, chatLevel(), random)) return
        val plies = session.moves.size
        val ending = event == ChatEvent.BOT_WINS || event == ChatEvent.PLAYER_WINS || event == ChatEvent.DRAW
        if (!ending && event != ChatEvent.GREETING && event != ChatEvent.PLAYER_BLUNDER && plies - lastChatPly < 2) return
        val text = BotChat.lineFor(profile.id, profile.style, event, random) ?: return
        lastChatPly = plies
        val id = ++chatId
        mutableState.update { it.copy(chat = ChatLine(id, text, speaker)) }
        chatJob?.cancel()
        if (ending) return
        chatJob = scope.launch {
            delay(timing.chatMs)
            mutableState.update { if (it.chat?.id == id) it.copy(chat = null) else it }
        }
    }

    private fun sound(feedback: BoardFeedback) {
        mutableState.update { it.copy(sound = GameSoundEvent(++soundId, feedback)) }
    }

    private companion object {
        /** Centipawns the bot's view must jump, and reach, to call the player's move a blunder. */
        const val BLUNDER_JUMP = 250
        const val BLUNDER_FLOOR = 150
    }
}
