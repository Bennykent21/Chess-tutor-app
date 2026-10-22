package com.chesstutor.app.feature.play

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
import com.chesstutor.app.board.moveListOf
import com.chesstutor.app.board.whiteAtBottom
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.data.settings.AppSettings
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtCard
import com.chesstutor.app.design.CtCoachBubble
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtGradients
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.feature.home.BotAvatar

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
) {
    val state by game.state.collectAsState()
    val board by game.session.state.collectAsState()
    val tick by game.clockTicks.collectAsState()
    val setup = game.setup
    val bot = setup.bot
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

    CtScreenBackground {
        Column(Modifier.fillMaxSize()) {
            CtTopBar(
                title = "vs ${bot.name}",
                subtitle = openingLine(state),
                onBack = onBack,
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = CtSpacing.gutter),
                verticalArrangement = Arrangement.spacedBy(CtSpacing.sm),
            ) {
                PlayerBar(
                    name = bot.name,
                    detail = if (state.phase == GamePhase.BOT_TURN) "Thinking…" else "${bot.rating} · ${bot.style.label}",
                    clock = clockOf(state.botColor),
                    running = state.phase == GamePhase.BOT_TURN && toMove == state.botColor,
                    avatar = { BotAvatar(bot.id, bot.rating, size = 40.dp) },
                )
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
                PlayerBar(
                    name = "You",
                    detail = if (setup.playerColor == PieceColor.WHITE) "White" else "Black",
                    clock = clockOf(setup.playerColor),
                    running = state.phase == GamePhase.PLAYER_TURN,
                    avatar = null,
                )
                BoardMoveList(entries = remember(board.version) { moveListOf(board.start, board.moves) }, currentPly = board.moves.size)
                if (state.phase == GamePhase.OVER) {
                    ResultCard(
                        state = state,
                        settings = settings,
                        onRematch = onRematch,
                        onAnalyse = { onAnalyse(setup.startFen, board.moves.map { it.uci }) },
                        onBack = onBack,
                    )
                } else {
                    GameActions(game, state)
                }
                Spacer(Modifier.height(CtSpacing.lg))
            }
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
) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm),
    ) {
        avatar?.invoke()
        Column(Modifier.weight(1f)) {
            Text(name, style = CtType.TitleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = CtType.Caption, maxLines = 1)
        }
        if (clock != null) BoardClock(millis = clock, running = running)
    }
}

@Composable
private fun GameActions(game: BotGame, state: BotGameState) {
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
            text = "Resign",
            style = CtButtonStyle.Secondary,
            enabled = state.phase != GamePhase.STARTING,
            onClick = { confirmResign = true },
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
    val title = when {
        result.winner == null -> "Draw"
        state.playerWon -> "You won!"
        else -> "${bot.name} won"
    }
    val coach = when {
        result.winner == null -> "A fair fight. Want to go again?"
        state.playerWon && state.hintsUsed == 0 && state.takebacksUsed == 0 -> "Clean win, no help at all. Brilliant!"
        state.playerWon -> "Well played! Try it with fewer hints next time."
        else -> "Every loss teaches something. Analyse it to see where it turned."
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
            CtButton(text = "Analyse", icon = Icons.Rounded.Search, style = CtButtonStyle.Secondary, onClick = onAnalyse, modifier = Modifier.weight(1f))
            CtButton(text = "Other bots", style = CtButtonStyle.Secondary, onClick = onBack, modifier = Modifier.weight(1f))
        }
    }
}
