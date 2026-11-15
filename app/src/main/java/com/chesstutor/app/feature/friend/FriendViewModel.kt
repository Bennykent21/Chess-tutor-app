package com.chesstutor.app.feature.friend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chesstutor.app.core.Chess960
import com.chesstutor.app.core.Piece
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.PieceType
import com.chesstutor.app.core.Position
import com.chesstutor.app.data.nearby.NearbyConnector
import com.chesstutor.app.data.nearby.NearbyLink
import com.chesstutor.app.data.repository.GameRepository
import com.chesstutor.app.domain.game.FriendSetup
import com.chesstutor.app.domain.game.NearbyMessage
import com.chesstutor.app.domain.game.NearbyProtocol
import com.chesstutor.app.domain.game.PlayerClock
import com.chesstutor.app.domain.game.Seating
import com.chesstutor.app.domain.game.SetupBoard
import com.chesstutor.app.domain.opening.Opening
import com.chesstutor.app.domain.opening.OpeningTree
import com.chesstutor.app.feature.play.FriendGame
import com.chesstutor.app.feature.play.FriendPeer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Where a friend game starts. */
enum class FriendStart { NORMAL, OPENING, POSITION, CHESS960 }

/** Both players at this phone, or one at each of two phones nearby (Phase 11C). */
enum class FriendMode { THIS_PHONE, NEARBY }

/** A nearby game being opened or looked for. */
sealed interface NearbyStatus {
    data object Idle : NearbyStatus

    /** Waiting for the friend: they type [code], or [address] where the code is not found. */
    data class Hosting(val code: String, val address: String?) : NearbyStatus

    data class Joining(val target: String) : NearbyStatus

    data class Failed(val message: String) : NearbyStatus

    data class Connected(val friendName: String) : NearbyStatus
}

/** The "Play a friend" page as the players have filled it in. */
data class FriendDraft(
    /** The friend's name; the phone owner's comes from their profile. */
    val friendName: String = "",
    val ownerColor: PieceColor = PieceColor.WHITE,
    /** Null plays without a clock. */
    val clock: PlayerClock? = PlayerClock.of(10),
    /** The friend's own clock, for time odds; null gives both players [clock]. */
    val friendClock: PlayerClock? = null,
    val start: FriendStart = FriendStart.NORMAL,
    val opening: Opening? = null,
    val board: SetupBoard = SetupBoard.START,
    /** The piece the next tap places on the position board; null erases. */
    val brush: Piece? = Piece(PieceType.QUEEN, PieceColor.WHITE),
    val takebacks: Boolean = true,
    val seating: Seating = Seating.FACE_TO_FACE,
    val mode: FriendMode = FriendMode.THIS_PHONE,
    /** The Chess960 start, 0 to 959 (518 is the normal one). */
    val chess960: Int = Chess960.random(),
) {
    /** Why the game cannot start yet, or null. */
    val problem: String?
        get() = when {
            start == FriendStart.OPENING && opening == null -> "Choose an opening"
            start == FriendStart.POSITION -> board.problem()
            else -> null
        }

    fun toSetup(ownerName: String): FriendSetup {
        val owner = ownerName.trim().ifEmpty { "You" }
        val friend = friendName.trim().ifEmpty { "Friend" }
        val ownerWhite = ownerColor == PieceColor.WHITE
        val friendsClock = if (clock == null) null else friendClock ?: clock
        return FriendSetup(
            whiteName = if (ownerWhite) owner else friend,
            blackName = if (ownerWhite) friend else owner,
            ownerColor = ownerColor,
            whiteClock = if (ownerWhite) clock else friendsClock,
            blackClock = if (ownerWhite) friendsClock else clock,
            startFen = when (start) {
                FriendStart.POSITION -> board.toFen()
                FriendStart.CHESS960 -> Chess960.startFen(chess960)
                else -> Position.STARTING_FEN
            },
            openingMoves = if (start == FriendStart.OPENING) opening?.moves.orEmpty() else emptyList(),
            takebacks = takebacks,
            seating = seating,
        )
    }
}

/** Play a friend (Phase 11): the setup page's draft and the game being played. */
class FriendViewModel(
    private val openings: suspend () -> OpeningTree,
    private val games: GameRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val connector: NearbyConnector? = null,
) : ViewModel() {
    private val mutableDraft = MutableStateFlow(FriendDraft())
    val draft: StateFlow<FriendDraft> = mutableDraft.asStateFlow()

    private val mutableTree = MutableStateFlow<OpeningTree?>(null)
    /** The opening database for the picker; null until [loadOpenings] has read it. */
    val openingTree: StateFlow<OpeningTree?> = mutableTree.asStateFlow()

    private val mutableGame = MutableStateFlow<FriendGame?>(null)
    /** The game on screen, if any. */
    val game: StateFlow<FriendGame?> = mutableGame.asStateFlow()

    fun update(change: (FriendDraft) -> FriendDraft) {
        mutableDraft.value = change(mutableDraft.value)
    }

    fun loadOpenings() {
        if (mutableTree.value != null) return
        viewModelScope.launch { mutableTree.value = runCatching { tree() }.getOrNull() }
    }

    private suspend fun tree(): OpeningTree = mutableTree.value ?: openings()

    /** Starts the game the draft describes; false if it cannot start yet. */
    fun start(ownerName: String): Boolean {
        val draft = mutableDraft.value
        if (draft.problem != null) return false
        launch(draft.toSetup(ownerName))
        return true
    }

    /** The same game again with the colours swapped, as friends do. Nearby, the host deals it; the joiner asks. */
    fun rematch() {
        val last = mutableGame.value?.setup ?: return
        if (link != null && !hosting) {
            sendLater(NearbyMessage.Rematch)
            return
        }
        val next = swapped(last)
        if (link != null) {
            sendLater(NearbyMessage.Setup(next))
            launchNearby(next)
        } else {
            launch(next)
        }
        mutableDraft.value = mutableDraft.value.copy(ownerColor = next.ownerColor)
    }

    private fun swapped(last: FriendSetup) = last.copy(
        whiteName = last.blackName,
        blackName = last.whiteName,
        ownerColor = last.ownerColor.opposite(),
        whiteClock = last.blackClock,
        blackClock = last.whiteClock,
    )

    /** Leaves the game; one that is not over is dropped, since nothing is saved until the end. Nearby, the friend is told. */
    fun close() {
        mutableGame.value?.pause()
        mutableGame.value = null
        leaveNearby()
    }

    private fun launch(setup: FriendSetup) {
        mutableGame.value?.pause()
        mutableGame.value = FriendGame(setup, ::tree, games, viewModelScope, clock).also { it.start() }
    }

    // ------------------------------------------------------------------ nearby (Phase 11C)

    private val mutableNearby = MutableStateFlow<NearbyStatus>(NearbyStatus.Idle)
    val nearby: StateFlow<NearbyStatus> = mutableNearby.asStateFlow()

    private var link: NearbyLink? = null
    private var hosting = false
    private var ownerName = ""
    private var friendName = ""
    private var connecting: Job? = null
    private var listening: Job? = null

    /** Opens a game for a friend nearby to join with a new code. */
    fun host(ownerName: String) {
        val connector = connector ?: return
        if (mutableDraft.value.problem != null) return
        leaveNearby()
        this.ownerName = ownerName
        hosting = true
        val code = NearbyProtocol.newCode()
        mutableNearby.value = NearbyStatus.Hosting(code, null)
        connecting = viewModelScope.launch {
            val opened = runCatching {
                connector.host(code, viewModelScope) { address -> mutableNearby.value = NearbyStatus.Hosting(code, address) }
            }
            opened.onSuccess { connected(it) }
                .onFailure { if (it !is CancellationException) mutableNearby.value = NearbyStatus.Failed("Could not open a game on this network.") }
        }
    }

    /** Joins a friend's game by its code, or by the address their phone shows. */
    fun join(target: String, ownerName: String) {
        val connector = connector ?: return
        leaveNearby()
        this.ownerName = ownerName
        hosting = false
        val clean = target.trim().let { if (':' in it) it else NearbyProtocol.cleanCode(it) }
        mutableNearby.value = NearbyStatus.Joining(clean)
        connecting = viewModelScope.launch {
            val joined = runCatching { withTimeout(JOIN_TIMEOUT_MS) { connector.join(clean, viewModelScope) } }
            joined.onSuccess { connected(it) }.onFailure {
                if (it is CancellationException && it !is TimeoutCancellationException) return@onFailure
                mutableNearby.value = NearbyStatus.Failed(
                    if (':' in clean) "Nothing answered at $clean." else "No game with the code $clean nearby. Are you both on the same Wi-Fi or hotspot?",
                )
            }
        }
    }

    /** Stops hosting or looking. */
    fun cancelNearby() {
        leaveNearby()
    }

    private suspend fun connected(link: NearbyLink) {
        this.link = link
        listening = viewModelScope.launch {
            link.lines.collect { line -> NearbyProtocol.decode(line)?.let(::receive) }
            // The connection closed.
            if (this@FriendViewModel.link === link) {
                this@FriendViewModel.link = null
                if (mutableGame.value != null) mutableGame.value?.peerLeft() else mutableNearby.value = NearbyStatus.Failed("The connection was lost.")
            }
        }
        link.send(NearbyProtocol.encode(NearbyMessage.Hello(ownerName.trim().ifEmpty { "Friend" })))
    }

    private fun receive(message: NearbyMessage) {
        val game = mutableGame.value
        when (message) {
            is NearbyMessage.Hello -> {
                friendName = message.name
                mutableNearby.value = NearbyStatus.Connected(message.name)
                if (hosting) {
                    val setup = mutableDraft.value.copy(friendName = message.name).toSetup(ownerName).copy(takebacks = false, seating = Seating.SIDE_BY_SIDE)
                    sendLater(NearbyMessage.Setup(setup))
                    launchNearby(setup)
                }
            }
            // The host's setup, seen from the host: this phone plays the other side.
            is NearbyMessage.Setup -> if (!hosting) launchNearby(message.setup.copy(ownerColor = message.setup.ownerColor.opposite()))
            is NearbyMessage.Moved -> game?.playRemote(message.uci, message.remainingMs)
            NearbyMessage.Resign -> game?.remoteResigned()
            NearbyMessage.DrawOffer -> game?.remoteOfferedDraw()
            is NearbyMessage.DrawAnswer -> game?.remoteAnsweredDraw(message.accept)
            NearbyMessage.Flag -> game?.remoteFlagged()
            NearbyMessage.Rematch -> if (hosting) rematch()
            NearbyMessage.Bye -> game?.peerLeft()
        }
    }

    private fun launchNearby(setup: FriendSetup) {
        mutableGame.value?.pause()
        val peer = object : FriendPeer {
            override fun moved(uci: String, remainingMs: Long?) = sendLater(NearbyMessage.Moved(uci, remainingMs))
            override fun resigned() = sendLater(NearbyMessage.Resign)
            override fun offeredDraw() = sendLater(NearbyMessage.DrawOffer)
            override fun answeredDraw(accept: Boolean) = sendLater(NearbyMessage.DrawAnswer(accept))
            override fun flagged() = sendLater(NearbyMessage.Flag)
        }
        mutableGame.value = FriendGame(setup, ::tree, games, viewModelScope, clock, localSides = setOf(setup.ownerColor), peer = peer).also { it.start() }
    }

    private fun sendLater(message: NearbyMessage) {
        val link = link ?: return
        viewModelScope.launch { link.send(NearbyProtocol.encode(message)) }
    }

    private fun leaveNearby() {
        connecting?.cancel()
        listening?.cancel()
        link?.let { gone ->
            viewModelScope.launch {
                gone.send(NearbyProtocol.encode(NearbyMessage.Bye))
                gone.close()
            }
        }
        link = null
        mutableNearby.value = NearbyStatus.Idle
    }

    override fun onCleared() {
        link?.close()
    }

    class Factory(
        private val openings: suspend () -> OpeningTree,
        private val games: GameRepository,
        private val connector: NearbyConnector? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = FriendViewModel(openings, games, connector = connector) as T
    }

    companion object {
        const val JOIN_TIMEOUT_MS = 20_000L
    }
}
