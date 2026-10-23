package com.chesstutor.app.feature.play

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.ui.draw.clip
import com.chesstutor.app.design.CtChip
import com.chesstutor.app.domain.bot.ChatLevel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.zIndex
import com.chesstutor.app.design.LocalReduceMotion
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
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
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtCard
import com.chesstutor.app.design.CtCoachBubble
import com.chesstutor.app.design.CtCoachStrip
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtGradients
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.domain.opening.BattleMode
import com.chesstutor.app.feature.home.BotAvatar
import com.chesstutor.app.feature.home.battleCoachLine

/**
 * A game against a bot on the shared board (plan 6.1, `BoardConfig.PLAY`): the bot above,
 * you below, the move list, and takeback, hint and resign as the rules allow.
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
) {
    val state by game.state.collectAsState()
    val board by game.session.state.collectAsState()
    val tick by game.clockTicks.collectAsState()
    val setup = game.setup
    val bot = setup.bot
    val watch = setup.watch
    val whiteBottom = whiteAtBottom(settings.boardOrientation, setup.playerColor)

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

    CtScreenBackground {
        Column(Modifier.fillMaxSize()) {
            val title = when {
                watch != null -> "Bot vs bot"
                state.battleTitle != null -> "Opening battle"
                else -> "vs ${bot.name}"
            }
            val subtitle = when {
                state.battleTitle != null -> "vs ${bot.name} · ${if (game.battle?.mode == BattleMode.DRILL) "drill" else game.battle?.mode?.label?.lowercase()}"
                else -> openingLine(state)
            }
            CtTopBar(
                title = title,
                subtitle = subtitle,
                onBack = onBack,
                trailing = if (game.battle?.mode != BattleMode.DRILL) {
                    {
                        // Cycles Off, Some, Lots; the bots' chat setting (review R6).
                        val next = ChatLevel.entries[(settings.chatLevel.ordinal + 1) % ChatLevel.entries.size]
                        CtChip(
                            text = "Chat: ${settings.chatLevel.label}",
                            color = CtColors.Violet,
                            icon = Icons.AutoMirrored.Rounded.Chat,
                            onClick = { onChatLevel(next) },
                        )
                    }
                } else null,
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = CtSpacing.gutter),
                verticalArrangement = Arrangement.spacedBy(CtSpacing.sm),
            ) {
                BarWithChat(chat = state.chat?.takeIf { it.speaker == state.botColor }, below = true, onDismiss = game::dismissChat) {
                    PlayerBar(
                        name = bot.name,
                        detail = if (state.phase == GamePhase.BOT_TURN) "Thinking…" else "${bot.rating} · ${bot.style.label}",
                        clock = clockOf(state.botColor),
                        running = state.phase == GamePhase.BOT_TURN && toMove == state.botColor,
                        avatar = { BotAvatar(bot.face, bot.rating, size = 40.dp) },
                        captured = { takenBy(state.botColor) },
                    )
                }
                state.battleTitle?.let { title ->
                    CtCoachStrip(
                        mascot = settings.mascot,
                        label = title,
                        text = battleCoachLine(title, state.opening, state.inBook, state.drillMessage),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                BoardWithEvalBar(
                    evalBar = if (setup.rules.evalBar) { modifier ->
                        BoardEvalBar(centipawns = state.evalCp, mateIn = state.evalMate, whiteAtBottom = whiteBottom, modifier = modifier)
                    } else null,
                ) { modifier ->
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
                    )
                }
                if (watch != null) {
                    BarWithChat(chat = state.chat?.takeIf { it.speaker == setup.playerColor }, below = false, onDismiss = game::dismissChat) {
                        PlayerBar(
                            name = watch.name,
                            detail = if (state.phase == GamePhase.PLAYER_TURN) "Thinking…" else "${watch.rating} · ${watch.style.label}",
                            clock = clockOf(setup.playerColor),
                            running = state.phase == GamePhase.PLAYER_TURN,
                            avatar = { BotAvatar(watch.face, watch.rating, size = 40.dp) },
                            captured = { takenBy(setup.playerColor) },
                        )
                    }
                } else {
                    PlayerBar(
                        name = "You",
                        detail = if (setup.playerColor == PieceColor.WHITE) "White" else "Black",
                        clock = clockOf(setup.playerColor),
                        running = state.phase == GamePhase.PLAYER_TURN,
                        avatar = null,
                        captured = { takenBy(setup.playerColor) },
                    )
                }
                BoardMoveList(entries = remember(board.version) { moveListOf(board.start, board.moves) }, currentPly = board.moves.size)
                if (state.phase == GamePhase.DRILL_DONE) {
                    DrillDoneCard(state, onDrillAgain = onRematch, onAnalyse = { onAnalyse(setup.startFen, board.moves.map { it.uci }) }, onBack = onBack)
                } else if (state.phase == GamePhase.OVER) {
                    ResultCard(
                        state = state,
                        settings = settings,
                        onRematch = onRematch,
                        onAnalyse = { onAnalyse(setup.startFen, board.moves.map { it.uci }) },
                        onBack = onBack,
                    )
                } else if (watch != null) {
                    WatchActions(game, state, onStop = onBack)
                } else {
                    GameActions(game, state, onLeave = onBack)
                }
                Spacer(Modifier.height(CtSpacing.lg))
            }
        }
    }
}

/**
 * A player's bar with its bot's chat line hanging off it, drawn over the edge of the board.
 * The line takes no room in the layout, so the board never moves when a bot speaks.
 * [below]: the line hangs under the bar (top player) or above it (bottom player).
 */
@Composable
private fun BarWithChat(chat: ChatLine?, below: Boolean, onDismiss: () -> Unit, bar: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().zIndex(1f)) {
        bar()
        Box(Modifier.align(if (below) Alignment.BottomStart else Alignment.TopStart).hanging(below)) {
            ChatBubble(chat, below, onDismiss)
        }
    }
}

/** Measures the content but reports no height, placing it just below (or above) its slot. */
private fun Modifier.hanging(below: Boolean): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    layout(placeable.width, 0) { placeable.place(0, if (below) 0 else -placeable.height) }
}

/** A bot's line beside its avatar; fades in and out (or just appears with Reduce motion), tap to close. */
@Composable
private fun ChatBubble(chat: ChatLine?, below: Boolean, onDismiss: () -> Unit) {
    // Keep the last line while it fades out.
    var last by remember { mutableStateOf(chat) }
    if (chat != null && chat != last) last = chat
    val reduceMotion = LocalReduceMotion.current
    val shape = if (below) RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
    else RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp)
    AnimatedVisibility(
        visible = chat != null,
        enter = if (reduceMotion) EnterTransition.None else fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.9f, transformOrigin = TransformOrigin(0.1f, if (below) 0f else 1f)),
        exit = if (reduceMotion) ExitTransition.None else fadeOut(tween(220)),
    ) {
        val line = last ?: return@AnimatedVisibility
        Text(
            text = line.text,
            style = CtType.BodySmall.copy(color = CtColors.Text),
            modifier = Modifier
                .padding(start = 44.dp, top = if (below) CtSpacing.xs else 0.dp, bottom = if (below) 0.dp else CtSpacing.xs)
                .widthIn(max = 280.dp)
                .shadow(6.dp, shape)
                .clip(shape)
                .background(CtColors.SurfaceRaised)
                .border(1.dp, CtColors.Line, shape)
                .clickable(onClickLabel = "Close message", onClick = onDismiss)
                .padding(horizontal = CtSpacing.md, vertical = CtSpacing.sm)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun WatchActions(game: BotGame, state: BotGameState, onStop: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
        CtButton(
            text = if (state.paused) "Resume" else "Pause",
            icon = if (state.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
            style = CtButtonStyle.Secondary,
            onClick = game::togglePause,
            modifier = Modifier.weight(1f),
        )
        CtButton(text = "Stop", style = CtButtonStyle.Secondary, onClick = onStop, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DrillDoneCard(state: BotGameState, onDrillAgain: () -> Unit, onAnalyse: () -> Unit, onBack: () -> Unit) {
    val clean = state.drillMistakes == 0
    CtCard(
        background = if (clean) CtGradients.Teal else CtGradients.Card,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(if (clean) "Perfect drill!" else "Drill complete", style = CtType.Headline.copy(color = if (clean) CtColors.Teal else CtColors.Text))
        Text(state.drillMessage.orEmpty(), style = CtType.Body)
        Spacer(Modifier.height(CtSpacing.md))
        CtButton(text = "Drill again", icon = Icons.Rounded.Refresh, onClick = onDrillAgain, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(CtSpacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
            CtButton(text = "Explore", icon = Icons.Rounded.Search, style = CtButtonStyle.Secondary, onClick = onAnalyse, modifier = Modifier.weight(1f))
            CtButton(text = "Done", style = CtButtonStyle.Secondary, onClick = onBack, modifier = Modifier.weight(1f))
        }
    }
}

private fun openingLine(state: BotGameState): String? {
    val name = state.opening ?: return if (state.inBook) null else "Out of the opening book"
    return if (state.inBook) "$name · in book" else "$name · out of book"
}

@Composable
private fun PlayerBar(
    name: String,
    detail: String,
    clock: Long?,
    running: Boolean,
    avatar: (@Composable () -> Unit)?,
    /** The pieces this player has taken and its material lead, under the name. */
    captured: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm),
    ) {
        avatar?.invoke()
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                Text(name, style = CtType.TitleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Text(detail, style = CtType.Caption, maxLines = 1)
            }
            captured()
        }
        if (clock != null) BoardClock(millis = clock, running = running)
    }
}

@Composable
private fun GameActions(game: BotGame, state: BotGameState, onLeave: () -> Unit) {
    var confirmResign by rememberSaveable { mutableStateOf(false) }
    if (confirmResign) {
        CtCard(modifier = Modifier.fillMaxWidth()) {
            Text("Resign this game?", style = CtType.TitleSmall)
            Spacer(Modifier.height(CtSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                CtButton(text = "Resign", icon = Icons.Rounded.Flag, onClick = { confirmResign = false; game.resign() }, modifier = Modifier.weight(1f))
                CtButton(text = "Keep playing", style = CtButtonStyle.Secondary, onClick = { confirmResign = false }, modifier = Modifier.weight(1f))
            }
        }
        return
    }
    // Three buttons share one row, so they carry words only (icons would cut the words off).
    Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
        if (game.setup.rules.takebacks) {
            CtButton(
                text = "Undo",
                style = CtButtonStyle.Secondary,
                enabled = state.canTakeBack,
                onClick = game::takeBack,
                modifier = Modifier.weight(1f),
            )
        }
        if (game.setup.rules.hints) {
            CtButton(
                text = "Hint",
                style = CtButtonStyle.Secondary,
                enabled = state.phase == GamePhase.PLAYER_TURN,
                onClick = game::hint,
                modifier = Modifier.weight(1f),
            )
        }
        CtButton(
            text = if (game.battle?.mode == BattleMode.DRILL) "Stop" else "Resign",
            style = CtButtonStyle.Secondary,
            enabled = state.phase != GamePhase.STARTING,
            onClick = { if (game.battle?.mode == BattleMode.DRILL) onLeave() else confirmResign = true },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ResultCard(
    state: BotGameState,
    settings: AppSettings,
    onRematch: () -> Unit,
    onAnalyse: () -> Unit,
    onBack: () -> Unit,
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
        else -> "Every loss teaches something. Explore it to see where it turned."
    }
    CtCard(
        background = if (state.playerWon) CtGradients.Teal else CtGradients.Card,
        modifier = Modifier.fillMaxWidth().semantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = "$title ${result.end.label}"
        },
    ) {
        Text(title, style = CtType.Headline.copy(color = if (state.playerWon) CtColors.Teal else CtColors.Text))
        Text(result.end.label.replaceFirstChar { it.uppercase() }, style = CtType.Body)
        Spacer(Modifier.height(CtSpacing.md))
        CtCoachBubble(mascot = settings.mascot, text = coach, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(CtSpacing.md))
        CtButton(text = "Rematch", icon = Icons.Rounded.Refresh, onClick = onRematch, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(CtSpacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
            // "Explore" opens the analysis board; full game review comes in Phase 5.
            CtButton(text = "Explore", icon = Icons.Rounded.Search, style = CtButtonStyle.Secondary, onClick = onAnalyse, modifier = Modifier.weight(1f))
            CtButton(text = "Other bots", style = CtButtonStyle.Secondary, onClick = onBack, modifier = Modifier.weight(1f))
        }
    }
}
