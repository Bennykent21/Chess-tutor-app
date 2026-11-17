package com.chesstutor.app.feature.play

import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.AnimatedVisibility
import com.chesstutor.app.design.CtMetalIcon
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chesstutor.app.board.BoardMoveList
import com.chesstutor.app.board.MoveListEntry
import com.chesstutor.app.board.SideTones
import com.chesstutor.app.board.formatClock
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.domain.bot.ChatLevel
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtMotion
import com.chesstutor.app.design.CtSegmented
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtSpeechBubble
import com.chesstutor.app.design.CtTail
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.LocalReduceMotion
import com.chesstutor.app.design.ctPressable

/** One thing a bot (or the coach) said, with who said it when two bots talk. */
data class TalkLine(val id: Int, val text: String, val speaker: String? = null)

/**
 * The last [MAX_TALK] lines said, newest last (D92): a new line joins at the bottom and the oldest
 * leaves once there are more. Each line stays the newest for at least [MIN_READ_MS] so it can be
 * read; lines that pile up meanwhile keep only each speaker's latest, and a line that repeats the
 * newest one is not said twice. Starts again when [key] changes (a new game).
 */
@Composable
fun rememberTalk(said: List<TalkLine>, key: Any?): List<TalkLine> {
    val lines = remember(key) { mutableStateListOf<TalkLine>() }
    val heard = remember(key) { HashSet<Int>() }
    val waiting = remember(key) { Channel<TalkLine>(Channel.UNLIMITED) }
    LaunchedEffect(said.map { it.id }, key) {
        said.filter { heard.add(it.id) }.sortedBy { it.id }.forEach { waiting.trySend(it) }
    }
    LaunchedEffect(key) {
        while (true) {
            val batch = mutableListOf(waiting.receive())
            while (true) batch += waiting.tryReceive().getOrNull() ?: break
            val fresh = batch.filter { line -> batch.last { it.speaker == line.speaker } === line }
                .filterNot { line -> lines.lastOrNull()?.let { it.text == line.text && it.speaker == line.speaker } == true }
            if (fresh.isEmpty()) continue
            lines += fresh
            while (lines.size > MAX_TALK) lines.removeAt(0)
            delay(MIN_READ_MS)
        }
    }
    return lines
}

private const val MAX_TALK = 2
internal const val MIN_READ_MS = 1_200L

/**
 * The speaker sits large at the bottom left of the zone above the board and talks to its right
 * (D92): the newest line stands just above the board, the one before it moves up, slightly faded
 * but unchanged, and a third pushes the oldest out, all sliding.
 */
@Composable
fun ColumnScope.Talk(
    avatar: @Composable () -> Unit,
    lines: List<TalkLine>,
    modifier: Modifier = Modifier,
    /** How many lines the newest bubble may take; lessons explain more than games. */
    maxLines: Int = 4,
) {
    val reduceMotion = LocalReduceMotion.current
    val move = if (reduceMotion) 0 else CtMotion.OVERLAY_MS
    // What is on screen: the lines said, plus one still fading out at the top.
    val shown = remember { mutableStateListOf<TalkLine>() }
    LaunchedEffect(lines.map { it.id }) {
        lines.forEach { line -> if (shown.none { it.id == line.id }) shown += line }
    }
    val newestId = lines.lastOrNull()?.id
    Row(
        modifier = modifier.fillMaxWidth().weight(1f),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.md),
    ) {
        avatar()
        // Bubbles stack up from the board; anything taller than the zone is cut off at the top.
        Box(Modifier.weight(1f).fillMaxHeight().clipToBounds(), contentAlignment = Alignment.BottomStart) {
            Column(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Bottom, unbounded = true)) {
                shown.toList().forEach { line ->
                    key(line.id) {
                        val there = lines.any { it.id == line.id }
                        val seen = remember { MutableTransitionState(reduceMotion) }
                        seen.targetState = there
                        if (!there && seen.isIdle && !seen.currentState) LaunchedEffect(Unit) { shown.remove(line) }
                        AnimatedVisibility(
                            visibleState = seen,
                            enter = expandVertically(tween(move, easing = CtMotion.OverlayEase), expandFrom = Alignment.Top) + fadeIn(tween(move)),
                            exit = shrinkVertically(tween(move, easing = CtMotion.OverlayEase), shrinkTowards = Alignment.Bottom) + fadeOut(tween(move)),
                        ) {
                            TalkBubble(line, newest = line.id == newestId, fade = move, maxLines = maxLines)
                        }
                    }
                }
            }
        }
    }
}

/** One line in its bubble; the older line keeps its look and only fades a little. */
@Composable
private fun TalkBubble(line: TalkLine, newest: Boolean, fade: Int, maxLines: Int) {
    val alpha by animateFloatAsState(if (newest) 1f else 0.72f, tween(fade), label = "talk")
    Column(
        modifier = Modifier
            .padding(top = CtSpacing.sm)
            .alpha(alpha)
            .semantics { if (newest) liveRegion = LiveRegionMode.Polite },
    ) {
        line.speaker?.let { Text(it, style = CtType.Caption.copy(color = CtColors.Text2), modifier = Modifier.padding(start = CtSpacing.md, bottom = 2.dp)) }
        CtSpeechBubble(tail = if (newest) CtTail.Start else CtTail.None, color = CtColors.Surface, modifier = Modifier.fillMaxWidth()) {
            Text(line.text, style = CtType.BodySmall.copy(color = CtColors.Text), maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The row under the board in a game (D92): White's clock in a white box at the far left, Black's
 * in a black box at the far right, the running one ringed, and what each side has taken beside
 * its clock. Without a clock only the taken pieces show.
 */
@Composable
fun ClockRow(
    whiteMs: Long?,
    blackMs: Long?,
    running: PieceColor?,
    whiteTook: @Composable () -> Unit,
    blackTook: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
        if (whiteMs != null) ClockBox(whiteMs, white = true, running = running == PieceColor.WHITE)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { whiteTook() }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { blackTook() }
        if (blackMs != null) ClockBox(blackMs, white = false, running = running == PieceColor.BLACK)
    }
}

/** One side's clock in that side's colour; under 20 seconds while running it turns red. */
@Composable
fun ClockBox(millis: Long, white: Boolean, running: Boolean, modifier: Modifier = Modifier) {
    val low = running && millis < 20_000
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .width(84.dp)
            .fillMaxHeight()
            .clip(shape)
            .background(if (low) CtColors.Error else if (white) SideTones.White else SideTones.Black)
            .then(if (running && !low) Modifier.border(2.dp, CtColors.Accent, shape) else if (white) Modifier.border(1.dp, CtColors.Line, shape) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = "${if (white) "White" else "Black"}'s clock, ${formatClock(millis)}${if (running) ", running" else ""}" },
        contentAlignment = Alignment.Center,
    ) {
        Text(formatClock(millis), style = CtType.Number.copy(color = if (low) CtColors.OnError else if (white) SideTones.OnWhite else SideTones.OnBlack))
    }
}

/** The moves in a strip that follows the current one, with a step back and a step forward at its ends. */
@Composable
fun MoveStrip(
    entries: List<MoveListEntry>,
    currentPly: Int,
    onSelect: (Int) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    canGoBack: Boolean,
    canGoForward: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        StepButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous move", canGoBack, onBack)
        BoardMoveList(entries = entries, currentPly = currentPly, onSelect = onSelect, modifier = Modifier.weight(1f))
        StepButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next move", canGoForward, onForward)
    }
}

@Composable
private fun StepButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(CtSpacing.touchTarget)
            .clip(CtShapes.Pill)
            .ctPressable(enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = if (enabled) CtColors.Text else CtColors.Text2.copy(alpha = 0.4f))
    }
}

/**
 * What the Options button opens during a game (D92): flip the board, explore the position, save
 * and share once the game is over, and how much the bot talks.
 */
@Composable
fun ColumnScope.GameOptions(
    botName: String?,
    chatLevel: ChatLevel?,
    onFlip: () -> Unit,
    onExplore: () -> Unit,
    onShare: (() -> Unit)?,
    onChatLevel: (ChatLevel) -> Unit,
) {
    Text("Options", style = CtType.Headline)
    Column {
        OptionRow(Icons.Rounded.SwapVert, "Flip board", onFlip)
        OptionRow(Icons.Rounded.Search, "Explore this position", onExplore)
        if (onShare != null) OptionRow(Icons.Rounded.IosShare, "Save and share", onShare)
    }
    if (botName != null && chatLevel != null) {
        Text("$botName talks", style = CtType.Label.copy(color = CtColors.Text2))
        CtSegmented(options = ChatLevel.entries, selected = chatLevel, label = { it.label }, onSelect = onChatLevel)
    }
}

@Composable
private fun OptionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(CtShapes.Control)
            .ctPressable(onClick = onClick)
            .padding(horizontal = CtSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.lg),
    ) {
        CtMetalIcon(icon, size = 30.dp)
        Text(label, style = CtType.TitleSmall)
    }
}
