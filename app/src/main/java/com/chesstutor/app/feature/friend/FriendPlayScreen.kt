package com.chesstutor.app.feature.friend

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chesstutor.app.board.BoardFeedback
import com.chesstutor.app.board.BoardHost
import com.chesstutor.app.board.BoardMoveList
import com.chesstutor.app.board.CapturedPieces
import com.chesstutor.app.board.LookBackBoard
import com.chesstutor.app.board.MaterialBalance
import com.chesstutor.app.board.moveListOf
import com.chesstutor.app.board.rememberLookBack
import com.chesstutor.app.board.whiteAtBottom
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.data.settings.AppSettings
import com.chesstutor.app.data.settings.PlayerPicture
import com.chesstutor.app.design.CtBoardScreen
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtConfirmDialog
import com.chesstutor.app.design.CtControl
import com.chesstutor.app.design.CtIconButton
import com.chesstutor.app.design.CtSheet
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType
import com.chesstutor.app.domain.game.Seating
import com.chesstutor.app.feature.home.PlayerAvatar
import com.chesstutor.app.feature.play.FriendGame
import com.chesstutor.app.feature.play.FriendGameState
import com.chesstutor.app.feature.play.FriendPhase
import com.chesstutor.app.feature.play.PlayerStrip

/** What the friend game can ask the app to do. */
data class FriendPlayActions(
    val onFeedback: (BoardFeedback) -> Unit,
    val onRematch: () -> Unit,
    /** Opens the saved game's Stockfish review (Account). */
    val onReview: (gameId: String) -> Unit,
    val onAnalyse: (startFen: String, moves: List<String>) -> Unit,
    val onBack: () -> Unit,
)

private enum class Ask { RESIGN, DRAW, LEAVE }

/**
 * A game between two people on one phone (Phase 11), on the same layout as a game against a bot:
 * the far player's strip stands on the board with the moves above it, the owner's strip is under
 * the board, and Resign, Draw, Undo and Seats are at the bottom. Face to face, the far player's
 * strip and pieces are turned round for them. The board never moves (D39).
 */
@Composable
fun FriendPlayScreen(game: FriendGame, settings: AppSettings, actions: FriendPlayActions) {
    val state by game.state.collectAsState()
    val board by game.session.state.collectAsState()
    val tick by game.clockTicks.collectAsState()
    val setup = game.setup
    val whiteBottom = whiteAtBottom(settings.boardOrientation, setup.ownerColor)
    val near = if (whiteBottom) PieceColor.WHITE else PieceColor.BLACK
    val far = near.opposite()
    var seating by rememberSaveable(game) { mutableStateOf(setup.seating) }
    val faceToFace = seating == Seating.FACE_TO_FACE

    // Each sound plays once, even when the screen recomposes.
    var lastSound by remember(game) { mutableIntStateOf(state.sound?.id ?: 0) }
    LaunchedEffect(state.sound?.id) {
        val event = state.sound ?: return@LaunchedEffect
        if (event.id != lastSound) {
            lastSound = event.id
            actions.onFeedback(event.feedback)
        }
    }

    val over = state.phase == FriendPhase.OVER
    val playing = state.phase == FriendPhase.PLAYING
    // The result opens by itself when the game ends; closing it shows the final position.
    var resultClosed by rememberSaveable(game) { mutableStateOf(false) }
    var ask by rememberSaveable { mutableStateOf<Ask?>(null) }
    val look = rememberLookBack(board, key = game)
    val material = remember(board.version) { MaterialBalance.of(board.start, board.position) }
    val explore = { actions.onAnalyse(setup.startFen, board.moves.map { it.uci }) }
    // A game in progress is not saved, so leaving it asks first.
    val leave = { if (playing && board.moves.size > setup.openingMoves.size) ask = Ask.LEAVE else actions.onBack() }
    BackHandler(onBack = leave)

    @Composable
    fun strip(color: PieceColor, modifier: Modifier = Modifier) {
        val name = setup.nameOf(color)
        PlayerStrip(
            name = name,
            detail = if (color == PieceColor.WHITE) "White" else "Black",
            clock = remember(tick, state.phase, board.version) { game.remaining(color) },
            running = playing && state.toMove == color,
            avatar = { PlayerAvatar(if (color == setup.ownerColor) settings.picture else PlayerPicture.None, name) },
            captured = { CapturedPieces(material.takenBy(color), color.opposite(), material.leadOf(color), settings.boardTheme.pieceSet) },
            modifier = modifier,
        )
    }

    CtBoardScreen(
        title = "Friend",
        onBack = leave,
        zone = {
            BoardMoveList(
                entries = remember(board.version) { moveListOf(board.start, board.moves) },
                currentPly = look.ply,
                onSelect = look::show,
            )
            state.opening?.let { Text(it, style = CtType.Caption, maxLines = 1) }
            strip(far, if (faceToFace) Modifier.rotate(180f) else Modifier)
        },
        under = { strip(near) },
        board = {
            LookBackBoard(look, settings.boardTheme, whiteBottom, settings.showCoordinates, backLabel = "Back to the game") {
                BoardHost(
                    session = game.session,
                    theme = settings.boardTheme,
                    whiteAtBottom = whiteBottom,
                    showCoordinates = settings.showCoordinates,
                    showLegalDots = settings.showLegalDots,
                    autoQueen = settings.autoQueen,
                    onFeedback = actions.onFeedback,
                    turned = far.takeIf { faceToFace },
                )
            }
        },
        controls = {
            if (over) {
                // Rematch and review live in the result sheet; this brings it back.
                CtIconButton(icon = Icons.Rounded.Search, contentDescription = "Explore", onClick = explore)
                CtButton(text = "See the result", onClick = { resultClosed = false }, modifier = Modifier.weight(1f))
            } else {
                CtControl(Icons.Rounded.Flag, "Resign", enabled = playing, onClick = { ask = Ask.RESIGN })
                CtControl(Icons.Rounded.Handshake, "Draw", enabled = playing, onClick = { ask = Ask.DRAW })
                if (setup.takebacks) CtControl(Icons.AutoMirrored.Rounded.Undo, "Undo", enabled = state.canTakeBack, onClick = game::takeBack)
                CtControl(
                    icon = Icons.Rounded.SwapVert,
                    label = "Seats",
                    description = if (faceToFace) "Sit side by side" else "Sit face to face",
                    onClick = { seating = if (faceToFace) Seating.SIDE_BY_SIDE else Seating.FACE_TO_FACE },
                )
            }
        },
        overlay = {
            CtSheet(visible = over && !resultClosed, onDismiss = { resultClosed = true }) {
                FriendResult(
                    state = state,
                    onRematch = actions.onRematch,
                    onReview = state.savedGameId?.let { id -> { actions.onReview(id) } },
                    onBack = actions.onBack,
                )
            }
        },
    )

    when (ask) {
        Ask.RESIGN -> {
            // On one phone it is the player to move who gives up.
            val name = setup.nameOf(state.toMove)
            CtConfirmDialog(
                title = "$name resigns?",
                text = "${setup.nameOf(state.toMove.opposite())} wins, and the game is saved for review.",
                confirmLabel = "Resign",
                onConfirm = {
                    ask = null
                    game.resign()
                },
                onDismiss = { ask = null },
            )
        }
        Ask.DRAW -> CtConfirmDialog(
            title = "Agree a draw?",
            text = "Both players must agree. The game is saved for review.",
            confirmLabel = "Draw",
            onConfirm = {
                ask = null
                game.agreeDraw()
            },
            onDismiss = { ask = null },
        )
        Ask.LEAVE -> CtConfirmDialog(
            title = "Leave this game?",
            text = "A game that is not finished is not saved.",
            confirmLabel = "Leave",
            onConfirm = {
                ask = null
                actions.onBack()
            },
            onDismiss = { ask = null },
        )
        null -> Unit
    }
}

/** The result of a friend game: who won and how, then what next. */
@Composable
private fun FriendResult(state: FriendGameState, onRematch: () -> Unit, onReview: (() -> Unit)?, onBack: () -> Unit) {
    val result = state.result ?: return
    val title = result.winner?.let { "${state.setup.nameOf(it)} won" } ?: "Draw"
    Column(
        modifier = Modifier.fillMaxWidth().semantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = "$title ${result.end.label}"
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CtSpacing.xs),
    ) {
        Text(title, style = CtType.Headline)
        Text(result.end.label.replaceFirstChar { it.uppercase() }, style = CtType.Body)
    }
    if (onReview != null) {
        CtButton(text = "Review this game", icon = Icons.Rounded.Insights, onClick = onReview, modifier = Modifier.fillMaxWidth())
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CtButton(
            text = "Rematch",
            icon = Icons.Rounded.Refresh,
            style = if (onReview != null) CtButtonStyle.Secondary else CtButtonStyle.Primary,
            onClick = onRematch,
            modifier = Modifier.weight(1f),
        )
        CtButton(text = "Done", style = CtButtonStyle.Secondary, onClick = onBack, modifier = Modifier.weight(1f))
    }
}
