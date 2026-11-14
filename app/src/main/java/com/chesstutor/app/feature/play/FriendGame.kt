package com.chesstutor.app.feature.play

import com.chesstutor.app.board.BoardFeedback
import com.chesstutor.app.board.BoardSound
import com.chesstutor.app.board.HapticPattern
import com.chesstutor.app.board.moveListOf
import com.chesstutor.app.core.GameStatus
import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.PieceType
import com.chesstutor.app.core.Position
import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.data.repository.GameRepository
import com.chesstutor.app.domain.PgnFormatter
import com.chesstutor.app.domain.game.FriendSetup
import com.chesstutor.app.domain.game.GameClock
import com.chesstutor.app.domain.game.GameEnd
import com.chesstutor.app.domain.game.GameResult
import com.chesstutor.app.domain.opening.OpeningTree
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

enum class FriendPhase { STARTING, PLAYING, OVER }

data class FriendGameState(
    val setup: FriendSetup,
    val phase: FriendPhase = FriendPhase.STARTING,
    val toMove: PieceColor = PieceColor.WHITE,
    val result: GameResult? = null,
    /** The deepest named opening reached, for a game from the normal start. */
    val opening: String? = null,
    val canTakeBack: Boolean = false,
    val sound: GameSoundEvent? = null,
    /** The saved game's id in the games list, once saved (for "Review this game"). */
    val savedGameId: String? = null,
    /** Nearby: the side that offered a draw and waits for an answer. */
    val drawOffer: PieceColor? = null,
    /** Nearby: the other phone went away before the game ended. */
    val peerGone: Boolean = false,
)

/** The other phone of a nearby game (Phase 11C): what this phone tells it. */
interface FriendPeer {
    fun moved(uci: String, remainingMs: Long?)
    fun resigned()
    fun offeredDraw()
    fun answeredDraw(accept: Boolean)
    /** This phone's player ran out of time. */
    fun flagged()
}

/**
 * One game between two people (Phase 11): turns, a clock each, takebacks, resigning, a draw by
 * agreement, the result, and saving the finished game to the games list. On one phone both sides
 * are in [localSides]; nearby, this phone holds one side and the friend's moves come in through
 * [playRemote].
 */
class FriendGame(
    val setup: FriendSetup,
    private val openings: suspend () -> OpeningTree,
    private val games: GameRepository,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val timing: GameTiming = GameTiming(),
    private val localSides: Set<PieceColor> = PieceColor.entries.toSet(),
    /** Nearby: the other phone, told of this phone's moves; null on one phone. */
    private val peer: FriendPeer? = null,
) {
    val session = PlaySession(Position.fromFen(setup.startFen), localSides) { move -> localMove(move) }

    /** True for a nearby game, where this phone holds one side. */
    val nearby: Boolean get() = peer != null

    private val mutableState = MutableStateFlow(FriendGameState(setup, toMove = session.game.position.sideToMove))
    val state: StateFlow<FriendGameState> = mutableState.asStateFlow()

    private val clocks = GameClock(setup.whiteClock, setup.blackClock, clock)
    private val standardStart = setup.startFen == Position.STARTING_FEN
    private var tree: OpeningTree = OpeningTree.EMPTY
    private var ticker: Job? = null
    private var soundId = 0
    private val startedAt = clock()

    private val clockTick = MutableStateFlow(0L)
    /** Ticks every [GameTiming.tickMs] while a clock runs, so the screen redraws the clocks. */
    val clockTicks: StateFlow<Long> = clockTick.asStateFlow()

    /** Time left for [side] right now; null in a game without clocks. */
    fun remaining(side: PieceColor): Long? = clocks.remaining(side)

    fun start() {
        scope.launch {
            if (standardStart) tree = runCatching { openings() }.getOrDefault(OpeningTree.EMPTY)
            for (uci in setup.openingMoves) {
                session.play(legal(uci) ?: break)
            }
            mutableState.update { it.copy(phase = FriendPhase.PLAYING) }
            if (checkGameOver()) return@launch
            clocks.start(session.game.position.sideToMove)
            if (clocks.timed) startTicker()
            publishTurn()
        }
    }

    // ------------------------------------------------------------------ moves

    /**
     * The friend's move from the other phone, with the time they have left by their own clock.
     * False if it is not their turn or not legal, and then nothing changes.
     */
    fun playRemote(uci: String, remainingMs: Long? = null): Boolean {
        val mover = session.game.position.sideToMove
        if (state.value.phase != FriendPhase.PLAYING || mover in localSides) return false
        val move = legal(uci) ?: return false
        sound(session.play(move))
        clocks.moved(mover)
        remainingMs?.let { clocks.set(mover, it) }
        mutableState.update { it.copy(drawOffer = null) }
        if (!checkGameOver()) publishTurn()
        return true
    }

    private fun localMove(move: Move) {
        val mover = session.game.position.sideToMove.opposite()
        clocks.moved(mover)
        peer?.moved(move.uci, clocks.remaining(mover))
        mutableState.update { it.copy(drawOffer = null) }
        if (!checkGameOver()) publishTurn()
    }

    private fun publishTurn() {
        session.open = true
        val opening = if (standardStart && !tree.isEmpty) tree.match(session.moves.map { it.uci }).opening?.name else null
        mutableState.update {
            it.copy(
                toMove = session.game.position.sideToMove,
                opening = opening,
                canTakeBack = setup.takebacks && session.moves.size > setup.openingMoves.size,
            )
        }
    }

    private fun legal(uci: String): Move? =
        LegalMoveGenerator.generateLegalMoves(session.game.position).firstOrNull { it.uci == uci }

    // ------------------------------------------------------------------ players' actions

    /** Takes the last move back; its mover is to move again, and the time it used stays used. */
    fun takeBack() {
        if (!state.value.canTakeBack || state.value.phase != FriendPhase.PLAYING) return
        session.undo(1)
        clocks.stop()
        clocks.start(session.game.position.sideToMove)
        publishTurn()
    }

    /** [side] gives up; on one phone that is the player whose turn it is, nearby it is this phone's player. */
    fun resign(side: PieceColor = localSide ?: session.game.position.sideToMove) {
        if (state.value.phase != FriendPhase.PLAYING) return
        if (side in localSides) peer?.resigned()
        finish(GameResult(side.opposite(), GameEnd.RESIGNATION))
    }

    /** Both players agreed a draw (on one phone). */
    fun agreeDraw() {
        finish(GameResult(null, GameEnd.AGREEMENT))
    }

    /** This phone's player offers a draw; on one phone both are here, so it is agreed. */
    fun offerDraw() {
        val side = localSide ?: return agreeDraw()
        if (state.value.phase != FriendPhase.PLAYING || state.value.drawOffer != null) return
        mutableState.update { it.copy(drawOffer = side) }
        peer?.offeredDraw()
    }

    /** This phone's answer to the friend's offer. */
    fun answerDraw(accept: Boolean) {
        val offer = state.value.drawOffer ?: return
        if (offer in localSides) return
        peer?.answeredDraw(accept)
        if (accept) agreeDraw() else mutableState.update { it.copy(drawOffer = null) }
    }

    /** The other phone: their player resigned. */
    fun remoteResigned() {
        val side = remoteSide ?: return
        finish(GameResult(side.opposite(), GameEnd.RESIGNATION))
    }

    /** The other phone: their player offers a draw. */
    fun remoteOfferedDraw() {
        val side = remoteSide ?: return
        if (state.value.phase == FriendPhase.PLAYING) mutableState.update { it.copy(drawOffer = side) }
    }

    /** The other phone: their answer to this phone's offer. */
    fun remoteAnsweredDraw(accept: Boolean) {
        if (state.value.drawOffer !in localSides) return
        if (accept) agreeDraw() else mutableState.update { it.copy(drawOffer = null) }
    }

    /** The other phone: their player ran out of time. */
    fun remoteFlagged() {
        remoteSide?.let(::timeout)
    }

    /** The other phone went away: the clocks stop and the game cannot go on (it is not saved). */
    fun peerLeft() {
        if (state.value.phase != FriendPhase.PLAYING) return
        ticker?.cancel()
        clocks.stop()
        session.open = false
        mutableState.update { it.copy(peerGone = true) }
    }

    /** Nearby: the one side this phone plays. */
    private val localSide: PieceColor? get() = localSides.singleOrNull()
    private val remoteSide: PieceColor? get() = localSide?.opposite()

    /** Stops the clock's redraws, for leaving the screen; the clocks themselves keep their time. */
    fun pause() {
        ticker?.cancel()
    }

    // ------------------------------------------------------------------ the end

    private fun checkGameOver(): Boolean {
        val game = session.game
        val mover = game.position.sideToMove.opposite()
        val result = when (game.status) {
            GameStatus.CHECKMATE -> GameResult(mover, GameEnd.CHECKMATE)
            GameStatus.STALEMATE -> GameResult(null, GameEnd.STALEMATE)
            GameStatus.DRAW_INSUFFICIENT_MATERIAL -> GameResult(null, GameEnd.INSUFFICIENT_MATERIAL)
            // As in a game against a bot, a claimable draw ends the game.
            GameStatus.DRAW_FIVEFOLD_REPETITION, GameStatus.DRAW_THREEFOLD_REPETITION -> GameResult(null, GameEnd.REPETITION)
            GameStatus.DRAW_75_MOVES, GameStatus.DRAW_50_MOVES -> GameResult(null, GameEnd.FIFTY_MOVES)
            else -> null
        } ?: return false
        finish(result)
        return true
    }

    private fun finish(result: GameResult) {
        // A game the friend left has stopped for good: nothing ends or saves it.
        if (state.value.phase != FriendPhase.PLAYING || state.value.peerGone) return
        ticker?.cancel()
        clocks.stop()
        session.open = false
        mutableState.update { it.copy(phase = FriendPhase.OVER, result = result, canTakeBack = false, toMove = session.game.position.sideToMove) }
        // Someone at the table won unless it is a draw, so the ending is never the losing sound.
        sound(if (result.winner == null) BoardFeedback(BoardSound.MOVE, HapticPattern.TICK) else BoardFeedback(BoardSound.WIN, HapticPattern.LONG))
        scope.launch {
            val record = record(result)
            val ok = runCatching { games.saveGame(record) }.isSuccess
            mutableState.update { it.copy(savedGameId = record.id.takeIf { ok }) }
        }
    }

    private fun record(result: GameResult): GameRecord {
        val sans = moveListOf(session.start, session.moves).map { it.san }
        val ownerWhite = setup.ownerColor == PieceColor.WHITE
        return GameRecord(
            id = "$ID_PREFIX$startedAt",
            dateMillis = startedAt,
            botName = setup.nameOf(setup.ownerColor.opposite()),
            botRating = 0,
            result = result.pgn,
            pgn = PgnFormatter.formatPgn(
                moves = sans,
                whitePlayer = setup.whiteName,
                blackPlayer = setup.blackName,
                result = result.pgn,
                date = Date(startedAt),
                event = "Chess Tutor: friend game",
                startFen = setup.startFen,
            ),
            moveCount = session.moves.size,
            userColor = if (ownerWhite) "white" else "black",
            finalFen = session.game.position.toFen(),
            uciMoves = session.moves.joinToString(" ") { it.uci },
            termination = result.end.label,
            timeClass = setup.timeClass,
        )
    }

    // ------------------------------------------------------------------ clocks

    private fun startTicker() {
        ticker = scope.launch {
            while (isActive) {
                delay(timing.tickMs)
                val side = clocks.flagged()
                // Nearby, each phone judges only its own player's clock; the friend's flag comes as a message.
                if (side != null && (peer == null || side in localSides)) {
                    peer?.flagged()
                    timeout(side)
                    return@launch
                }
                clockTick.value = clock()
            }
        }
    }

    private fun timeout(side: PieceColor) {
        // A lone king cannot win on time.
        val opponentCanMate = session.game.position.squares.any { it != null && it.color != side && it.type != PieceType.KING }
        finish(GameResult(if (opponentCanMate) side.opposite() else null, GameEnd.TIMEOUT))
    }

    private fun sound(feedback: BoardFeedback) {
        mutableState.update { it.copy(sound = GameSoundEvent(++soundId, feedback)) }
    }

    companion object {
        /** Saved friend games start with this, so the games list can tell them from bot games. */
        const val ID_PREFIX = "friend_"
    }
}
