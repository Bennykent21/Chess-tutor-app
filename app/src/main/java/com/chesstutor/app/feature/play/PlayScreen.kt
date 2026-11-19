package com.chesstutor.app.feature.play

import androidx.compose.runtime.getValue
import com.chesstutor.app.design.CtDialogBand
import com.chesstutor.app.R
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.Image
import android.content.Intent
import android.content.Context
import androidx.compose.runtime.setValue
import com.chesstutor.app.feature.home.PlayerAvatar
import com.chesstutor.app.design.CtControl
import com.chesstutor.app.board.LookBackBoard
import com.chesstutor.app.board.rememberLookBack
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chesstutor.app.board.BoardClock
import com.chesstutor.app.board.BoardEvalBar
import com.chesstutor.app.board.BoardFeedback
import com.chesstutor.app.board.BoardHost
import com.chesstutor.app.board.BoardMoveList
import com.chesstutor.app.board.BoardWithEvalBar
import com.chesstutor.app.board.CapturedPieces
import com.chesstutor.app.board.MaterialBalance
import com.chesstutor.app.board.moveListOf
import com.chesstutor.app.board.whiteAtBottom
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.data.settings.AppSettings
import com.chesstutor.app.design.CtBoardScreen
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtCoachBubble
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtConfirmDialog
import com.chesstutor.app.design.CtIconButton
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtDialog
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtSpeechBubble
import com.chesstutor.app.design.CtTail
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.LocalReduceMotion
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.MascotMood
import com.chesstutor.app.domain.bot.ChatLevel
import com.chesstutor.app.domain.opening.BattleMode
import com.chesstutor.app.feature.home.BotAvatar
import com.chesstutor.app.feature.home.battleCoachLine

/**
 * A game against a bot (plan 6.1, Phase 7D, canvas `Play.dc.html`), built on the board screen
 * layout so the board never moves (D39): the bot's strip in the fixed row above the board, its
 * chat fading in over that strip (D41), you, the moves and the opening below the board, and
 * Resign, Undo, Hint and Chat at the bottom. The result opens in a centred card.
 */
@Composable
fun PlayScreen(
    game: BotGame,
    settings: AppSettings,
    onFeedback: (BoardFeedback) -> Unit,
    onRematch: () -> Unit,
    onAnalyse: (startFen: String, moves: List<String>) -> Unit,
    onBack: () -> Unit,
    onChatLevel: (ChatLevel) -> Unit = {},
    /** Opens the saved game's Stockfish review (Account). */
    onReview: (gameId: String) -> Unit = {},
) {
    val state by game.state.collectAsState()
    val board by game.session.state.collectAsState()
    val tick by game.clockTicks.collectAsState()
    val setup = game.setup
    val bot = setup.bot
    val watch = setup.watch
    var flipped by rememberSaveable(game) { mutableStateOf(false) }
    val whiteBottom = whiteAtBottom(settings.boardOrientation, setup.playerColor) != flipped
    val drill = game.battle?.mode == BattleMode.DRILL
    var optionsOpen by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    // Each sound plays once, even when the screen recomposes.
    var lastSound by remember(game) { mutableIntStateOf(state.sound?.id ?: 0) }
    LaunchedEffect(state.sound?.id) {
        val event = state.sound ?: return@LaunchedEffect
        if (event.id != lastSound) {
            lastSound = event.id
            onFeedback(event.feedback)
        }
    }

    val (whiteLeft, blackLeft) = remember(tick, state.whiteMs, state.blackMs, state.phase) { game.liveClocks }
    fun clockOf(color: PieceColor) = if (color == PieceColor.WHITE) whiteLeft else blackLeft
    val toMove = board.position.sideToMove
    // Captured pieces and the material lead, worked out from the board (chess.com style).
    val material = remember(board.version) { MaterialBalance.of(board.start, board.position) }
    val pieceSet = settings.boardTheme.pieceSet
    @Composable
    fun takenBy(color: PieceColor) = CapturedPieces(material.takenBy(color), color.opposite(), material.leadOf(color), pieceSet)
    val explore = { onAnalyse(setup.startFen, board.moves.map { it.uci }) }
    // What the bot said, and in a bot-vs-bot game what the other one said, newest last (D92).
    val talk = rememberTalk(
        listOfNotNull(
            state.chatOf(state.botColor)?.let { TalkLine(it.id, it.text, speaker = if (watch != null) bot.name else null) },
            watch?.let { other -> state.chatOf(setup.playerColor)?.let { TalkLine(it.id, it.text, speaker = other.name) } },
            // In an opening battle the bot itself says how the line is going (D94: no second coach).
            state.battleTitle?.let { battle ->
                battleCoachLine(battle, state.opening, state.inBook, state.drillMessage).let { TalkLine(-1 - (it.hashCode() and 0x7FFFFFF), it) }
            },
        ),
        key = game,
    )

    val over = state.phase == GamePhase.OVER || state.phase == GamePhase.DRILL_DONE
    // The result opens by itself when the game ends; closing it shows the final position.
    var resultClosed by rememberSaveable(game) { mutableStateOf(false) }
    if (!over && resultClosed) resultClosed = false
    var confirmResign by rememberSaveable { mutableStateOf(false) }
    val look = rememberLookBack(board, key = game)

    val title = when {
        watch != null -> "Bot vs bot"
        state.battleTitle != null -> "Opening battle"
        else -> "vs ${bot.name} · ${bot.rating}"
    }
    CtBoardScreen(
        title = title,
        onBack = onBack,
        zone = {
            // The opening, one quiet line at the top: whether you are still in book.
            openingLine(state)?.let { Text(it, style = CtType.Caption.copy(color = CtColors.Text2), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Talk(avatar = { BotAvatar(bot.face, bot.rating, size = 76.dp) }, lines = talk)
        },
        under = {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                val live = state.phase == GamePhase.PLAYER_TURN || state.phase == GamePhase.BOT_TURN
                ClockRow(
                    whiteMs = whiteLeft,
                    blackMs = blackLeft,
                    running = toMove.takeIf { live && whiteLeft != null },
                    whiteTook = { takenBy(PieceColor.WHITE) },
                    blackTook = { takenBy(PieceColor.BLACK) },
                )
                MoveStrip(
                    entries = remember(board.version) { moveListOf(board.start, board.moves) },
                    currentPly = look.ply,
                    onSelect = look::show,
                    onBack = look::back,
                    onForward = look::forward,
                    canGoBack = look.canGoBack,
                    canGoForward = look.ply < look.latestPly,
                )
            }
        },
        board = {
            BoardWithEvalBar(
                evalBar = if (setup.rules.evalBar) { modifier ->
                    BoardEvalBar(centipawns = state.evalCp, mateIn = state.evalMate, whiteAtBottom = whiteBottom, modifier = modifier)
                } else null,
            ) { modifier ->
                // A tap on a past position comes back to the game, as does its latest move in the strip.
                LookBackBoard(look, settings.boardTheme, whiteBottom, settings.showCoordinates, backLabel = "Back to the game", modifier = modifier) {
                    BoardHost(
                        session = game.session,
                        theme = settings.boardTheme,
                        modifier = modifier,
                        whiteAtBottom = whiteBottom,
                        showCoordinates = settings.showCoordinates,
                        showLegalDots = settings.showLegalDots,
                        autoQueen = settings.autoQueen,
                        arrows = listOfNotNull(state.hint),
                        onFeedback = onFeedback,
                        // Queue a move while the bot thinks (D93).
                        premoveColor = setup.playerColor.takeIf { watch == null && !over },
                    )
                }
            }
        },
        controls = {
            val options = { optionsOpen = true }
            when {
                over -> {
                    // Rematch and review live in the result card; this brings it back.
                    CtIconButton(icon = Icons.Rounded.Menu, contentDescription = "Options", onClick = options)
                    CtButton(text = "See the result", onClick = { resultClosed = false }, modifier = Modifier.weight(1f))
                }
                watch != null -> {
                    CtControl(Icons.Rounded.Menu, "Options", onClick = options)
                    CtControl(if (state.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, if (state.paused) "Resume" else "Pause", onClick = game::togglePause)
                    CtControl(Icons.Rounded.Stop, "Stop", onClick = onBack)
                }
                else -> {
                    CtControl(Icons.Rounded.Menu, "Options", onClick = options)
                    CtControl(
                        icon = Icons.Rounded.Flag,
                        label = if (drill) "Stop" else "Resign",
                        enabled = state.phase != GamePhase.STARTING,
                        onClick = { if (drill) onBack() else confirmResign = true },
                    )
                    if (setup.rules.hints) CtControl(Icons.Rounded.Lightbulb, "Hint", enabled = state.phase == GamePhase.PLAYER_TURN, onClick = game::hint)
                    if (setup.rules.takebacks) CtControl(Icons.AutoMirrored.Rounded.Undo, "Undo", enabled = state.canTakeBack, onClick = game::takeBack)
                }
            }
        },
        overlay = {
            CtDialog(visible = optionsOpen, onDismiss = { optionsOpen = false }) {
                GameOptions(
                    botName = bot.name.takeIf { watch == null && !drill },
                    chatLevel = settings.chatLevel,
                    onFlip = {
                        flipped = !flipped
                        optionsOpen = false
                    },
                    onExplore = {
                        optionsOpen = false
                        explore()
                    },
                    onShare = if (over) { { game.pgn()?.let { sharePgn(context, it) } } } else null,
                    onChatLevel = onChatLevel,
                )
            }
            CtDialog(
                visible = over && !resultClosed,
                onDismiss = { resultClosed = true },
                header = { ResultBand(state) },
            ) {
                if (state.phase == GamePhase.DRILL_DONE) {
                    DrillResult(state, onDrillAgain = onRematch, onExplore = explore, onBack = onBack)
                } else {
                    GameResult(
                        state = state,
                        settings = settings,
                        onRematch = onRematch,
                        onExplore = explore,
                        onReview = state.savedGameId?.let { id -> { onReview(id) } },
                        onBack = onBack,
                    )
                }
            }
        },
    )
    if (confirmResign) {
        CtConfirmDialog(
            title = "Resign this game?",
            text = "${bot.name} wins, and the game is saved for review.",
            confirmLabel = "Resign",
            onConfirm = {
                confirmResign = false
                game.resign()
            },
            onDismiss = { confirmResign = false },
        )
    }
}

/**
 * A player's strip: avatar, name, rating or "Thinking…", captured pieces and the clock. A bot's
 * chat fades in over its name, with a tail pointing at its face, and never changes the strip's
 * height (D41). Tap the line to close it.
 */
@Composable
internal fun PlayerStrip(
    name: String,
    detail: String,
    clock: Long?,
    running: Boolean,
    avatar: (@Composable () -> Unit)?,
    captured: @Composable () -> Unit,
    chat: ChatLine? = null,
    onDismissChat: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.md),
    ) {
        avatar?.invoke()
        // A fixed height: a two-line chat may spill over it, but the strip never grows.
        Box(Modifier.weight(1f).height(48.dp), contentAlignment = Alignment.CenterStart) {
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                    Text(name, style = CtType.TitleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Text(detail, style = CtType.Caption, maxLines = 1)
                }
                captured()
            }
            ChatOverlay(chat, onDismissChat, Modifier.wrapContentHeight(unbounded = true))
        }
        if (clock != null) BoardClock(millis = clock, running = running)
    }
}

/** A bot's line over its own strip; fades in and out (or just appears with Reduce motion). */
@Composable
private fun ChatOverlay(chat: ChatLine?, onDismiss: () -> Unit, modifier: Modifier = Modifier, maxLines: Int = 2) {
    // Keep the last line while it fades out.
    var last by remember { mutableStateOf(chat) }
    if (chat != null && chat != last) last = chat
    val fade = if (LocalReduceMotion.current) 0 else 180
    AnimatedVisibility(visible = chat != null, modifier = modifier, enter = fadeIn(tween(fade)), exit = fadeOut(tween(fade))) {
        val line = last ?: return@AnimatedVisibility
        CtSpeechBubble(
            tail = CtTail.Start,
            color = CtColors.Surface,
            modifier = Modifier
                .fillMaxWidth()
                .clip(CtShapes.CardSmall)
                .ctPressable(onClickLabel = "Close message", onClick = onDismiss)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(line.text, style = CtType.BodySmall.copy(color = CtColors.Text), maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DrillResult(state: BotGameState, onDrillAgain: () -> Unit, onExplore: () -> Unit, onBack: () -> Unit) {
    val clean = state.drillMistakes == 0
    Column(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CtSpacing.xs),
    ) {
        Text(if (clean) "Perfect drill!" else "Drill complete", style = CtType.Headline)
        Text(state.drillMessage.orEmpty(), style = CtType.Body)
    }
    CtButton(text = "Drill again", icon = Icons.Rounded.Refresh, onClick = onDrillAgain, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CtButton(text = "Explore", icon = Icons.Rounded.Search, style = CtButtonStyle.Secondary, onClick = onExplore, modifier = Modifier.weight(1f))
        CtButton(text = "Done", style = CtButtonStyle.Secondary, onClick = onBack, modifier = Modifier.weight(1f))
    }
}

private fun openingLine(state: BotGameState): String? {
    val name = state.opening ?: return if (state.inBook) null else "Out of the opening book"
    return if (state.inBook) "$name, in book" else "$name, out of book"
}

/** The game's result (canvas `Play.dc.html` game-over card): who won, the coach, then what next. */
@Composable
private fun GameResult(
    state: BotGameState,
    settings: AppSettings,
    onRematch: () -> Unit,
    onExplore: () -> Unit,
    onBack: () -> Unit,
    /** Null until the game is saved (and for watched games, which are not saved). */
    onReview: (() -> Unit)? = null,
) {
    val result = state.result ?: return
    val bot = state.setup.bot
    val watch = state.setup.watch
    val title = when {
        result.winner == null -> "Draw"
        watch != null -> "${if (result.winner == state.botColor) bot.name else watch.name} won"
        state.playerWon -> "You won!"
        else -> "${bot.name} won"
    }
    val coach = when {
        watch != null -> "What a game! Want to watch them swap colours?"
        result.winner == null -> "A fair fight. Want to go again?"
        state.playerWon && state.hintsUsed == 0 && state.takebacksUsed == 0 -> "Clean win, no help at all. Brilliant!"
        state.playerWon -> "Well played! Try it with fewer hints next time."
        else -> "Every loss teaches something. Review it to see where it turned."
    }
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
    CtCoachBubble(
        mascot = settings.mascot,
        text = coach,
        mood = if (state.playerWon) MascotMood.PROUD else if (result.winner == null) MascotMood.READY else MascotMood.KIND,
        modifier = Modifier.fillMaxWidth(),
    )
    if (onReview != null) {
        CtButton(text = "Review this game", icon = Icons.Rounded.Insights, onClick = onReview, modifier = Modifier.fillMaxWidth())
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CtButton(
            text = "Rematch",
            style = if (onReview != null) CtButtonStyle.Secondary else CtButtonStyle.Primary,
            onClick = onRematch,
            modifier = Modifier.weight(1f),
        )
        CtButton(text = "Other bots", style = CtButtonStyle.Secondary, onClick = onBack, modifier = Modifier.weight(1f))
    }
    // "Explore" opens the analysis board; "Review this game" the Stockfish review.
    CtButton(text = "Explore the position", icon = Icons.Rounded.Search, style = CtButtonStyle.Ghost, onClick = onExplore, modifier = Modifier.fillMaxWidth())
}

/** Hands the game's PGN to Android's share sheet (another app, a file, a message). */
private fun sharePgn(context: Context, pgn: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, pgn)
    runCatching { context.startActivity(Intent.createChooser(send, "Share the game").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** The lit top of the result card (D92): the winner's crown, the toppled king, or a half for a draw. */
@Composable
private fun ResultBand(state: BotGameState) {
    val result = state.result
    CtDialogBand {
        when {
            result == null -> Unit
            result.winner == null -> Text("½", style = CtType.Display.copy(color = CtColors.Text))
            else -> {
                val crown = state.playerWon || (state.setup.watch != null && result.winner == state.botColor)
                Image(
                    painterResource(if (crown) R.drawable.ic_result_win else R.drawable.ic_result_loss),
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                )
            }
        }
    }
}
