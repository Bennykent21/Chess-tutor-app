package com.chesstutor.app.feature.friend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chesstutor.app.core.Piece
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.PieceType
import com.chesstutor.app.core.Position
import com.chesstutor.app.data.repository.GameRepository
import com.chesstutor.app.domain.game.FriendSetup
import com.chesstutor.app.domain.game.PlayerClock
import com.chesstutor.app.domain.game.Seating
import com.chesstutor.app.domain.game.SetupBoard
import com.chesstutor.app.domain.opening.Opening
import com.chesstutor.app.domain.opening.OpeningTree
import com.chesstutor.app.feature.play.FriendGame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where a friend game starts. */
enum class FriendStart { NORMAL, OPENING, POSITION }

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
            startFen = if (start == FriendStart.POSITION) board.toFen() else Position.STARTING_FEN,
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

    /** The same game again with the colours swapped, as friends do. */
    fun rematch() {
        val last = mutableGame.value?.setup ?: return
        launch(
            last.copy(
                whiteName = last.blackName,
                blackName = last.whiteName,
                ownerColor = last.ownerColor.opposite(),
                whiteClock = last.blackClock,
                blackClock = last.whiteClock,
            ),
        )
        mutableDraft.value = mutableDraft.value.copy(ownerColor = last.ownerColor.opposite())
    }

    /** Leaves the game; one that is not over is dropped, since nothing is saved until the end. */
    fun close() {
        mutableGame.value?.pause()
        mutableGame.value = null
    }

    private fun launch(setup: FriendSetup) {
        mutableGame.value?.pause()
        mutableGame.value = FriendGame(setup, ::tree, games, viewModelScope, clock).also { it.start() }
    }

    class Factory(
        private val openings: suspend () -> OpeningTree,
        private val games: GameRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = FriendViewModel(openings, games) as T
    }
}
