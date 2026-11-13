package com.chesstutor.app.feature.learn

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chesstutor.app.R
import com.chesstutor.app.board.BoardArrow
import com.chesstutor.app.board.BoardFeedback
import com.chesstutor.app.board.BoardHost
import com.chesstutor.app.board.BoardMoveList
import com.chesstutor.app.board.MarkKind
import com.chesstutor.app.board.SquareRing
import com.chesstutor.app.board.moveListOf
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Square
import com.chesstutor.app.data.settings.AppSettings
import com.chesstutor.app.design.CtArt
import com.chesstutor.app.design.CtBoardScreen
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtControl
import com.chesstutor.app.design.CtIconButton
import com.chesstutor.app.design.CtListRow
import com.chesstutor.app.design.CtMetalIcon
import com.chesstutor.app.design.CtProgressBar
import com.chesstutor.app.design.CtScene
import com.chesstutor.app.design.CtSheet
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTutor
import com.chesstutor.app.design.CtType
import com.chesstutor.app.domain.MascotMood
import com.chesstutor.app.domain.repertoire.MasteryProgress
import com.chesstutor.app.domain.repertoire.RepertoireLevel
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextOverflow
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.repertoire.Variation
import kotlin.math.roundToInt

/** What the opening page can open. Null hides the row. */
data class RepertoireActions(
    val onBack: () -> Unit,
    val onReview: () -> Unit,
    val onLearn: () -> Unit,
    val onTricky: () -> Unit,
    val onSparring: (() -> Unit)? = null,
    val onPlayOut: (() -> Unit)? = null,
    /** Learns the next new moves inside one variation (its moves from the start). */
    val onLearnVariation: (List<String>) -> Unit = {},
    /** Opens the course that teaches the opening's ideas, when there is one. */
    val onIdeas: (() -> Unit)? = null,
)

/**
 * One taught opening (Phase 10, mock-up round 2, direction A with B's cups; D77): its scene, how
 * ready the learner is, the three levels as a rail of cups, the main action (review what is due,
 * else learn new moves), its ideas, tricky lines, sparring and play it out, and its variations:
 * each as long as it is common at the learner's level and filled as far as it is learnt.
 */
@Composable
fun RepertoirePage(ui: RepertoireBookUi, actions: RepertoireActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(CtColors.Bg).verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(236.dp)) {
            CtArt(CtScene.OPENINGS, Modifier.fillMaxSize(), fadeFrom = 0.45f)
            CtIconButton(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", onClick = actions.onBack, modifier = Modifier.padding(CtSpacing.xs))
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = CtSpacing.gutter)) {
                Text(ui.book.title, style = CtType.Display)
                Text(if (ui.book.side == PieceColor.WHITE) "As White" else "As Black", style = CtType.BodySmall.copy(color = CtColors.Text2))
            }
        }
        Column(Modifier.padding(horizontal = CtSpacing.gutter), verticalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
            Spacer(Modifier.height(CtSpacing.xs))
            if (ui.introduced > 0) Readiness(ui.readiness)
            CupRail(ui)
            Spacer(Modifier.height(CtSpacing.xxs))
            if (ui.due > 0) {
                CtButton(text = "Review ${ui.due}", onClick = actions.onReview, modifier = Modifier.fillMaxWidth())
                if (ui.fresh > 0) CtButton(text = "Learn new moves", onClick = actions.onLearn, style = CtButtonStyle.Secondary, modifier = Modifier.fillMaxWidth())
            } else if (ui.fresh > 0) {
                CtButton(text = "Learn new moves", onClick = actions.onLearn, modifier = Modifier.fillMaxWidth())
            }
        }
        Column(Modifier.padding(horizontal = CtSpacing.gutter)) {
            actions.onIdeas?.let { CtListRow(title = "Ideas", icon = null, onClick = it, leading = { CtMetalIcon(Icons.Rounded.AutoStories, size = 36.dp) }) }
            if (ui.tricky > 0) {
                CtListRow(
                    title = "Tricky lines", icon = null, onClick = actions.onTricky,
                    leading = { CtMetalIcon(Icons.Rounded.History, size = 36.dp) },
                    trailing = { Text("${ui.tricky}", style = CtType.BodySmall.copy(color = CtColors.Text2)) },
                )
            }
            actions.onSparring?.let { CtListRow(title = "Sparring", icon = null, onClick = it, leading = { CtMetalIcon(Icons.Rounded.SportsEsports, size = 36.dp) }) }
            actions.onPlayOut?.let { CtListRow(title = "Play it out", icon = null, onClick = it, leading = { CtMetalIcon(Icons.Rounded.Flag, size = 36.dp) }) }
        }
        if (ui.variations.isNotEmpty()) {
            Column(Modifier.padding(start = CtSpacing.gutter, end = CtSpacing.gutter, top = CtSpacing.md, bottom = CtSpacing.xl)) {
                VariationList(ui.variations, actions.onLearnVariation)
            }
        }
    }
}

/** How much of what the learner meets is solid: one number, no sentence. */
@Composable
private fun Readiness(readiness: Double) {
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "${percent(readiness)} ready" },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.xs),
    ) {
        Text(percent(readiness), style = CtType.BigNumber)
        Text("ready", style = CtType.Body.copy(color = CtColors.Text2), modifier = Modifier.padding(bottom = 6.dp))
    }
}

/**
 * The opponent's choices (D77): each bar is as long as the choice is common, solid moves fill
 * it dark and moves met once fill it pale. A tap learns the next new moves of that variation;
 * one with nothing open yet is faint and locked.
 */
@Composable
private fun VariationList(variations: List<Variation>, onLearn: (List<String>) -> Unit) {
    val longest = variations.maxOf { it.share }.coerceAtLeast(0.01)
    Text("Variations", style = CtType.TitleSmall)
    Spacer(Modifier.height(CtSpacing.xs))
    variations.forEach { v ->
        val open = v.cards > 0
        val fresh = v.cards > v.introduced
        val spoken = buildString {
            append("${v.label}, ${percent(v.share)} of games")
            if (open) append(", ${v.solid} of ${v.cards} solid") else append(", locked")
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = CtSpacing.touchTarget)
                .then(if (fresh) Modifier.ctPressable(onClickLabel = "Learn ${v.label}") { onLearn(v.path) } else Modifier)
                .semantics(mergeDescendants = true) { contentDescription = spoken }
                .padding(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CtSpacing.xs)) {
                // The name takes the room it needs; only a name too long for the row is cut.
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CtSpacing.xs)) {
                    Text(v.label, style = CtType.Label.copy(color = if (open) CtColors.Text else CtColors.Text2), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (v.name != null) Text(v.move, style = CtType.Caption.copy(color = CtColors.Text2), maxLines = 1)
                }
                if (!open) Icon(Icons.Rounded.Lock, contentDescription = null, tint = CtColors.Text2, modifier = Modifier.size(13.dp))
                Text(percent(v.share), style = CtType.Caption.copy(color = CtColors.Text2))
            }
            val track = CtColors.Track
            val solid = CtColors.Text
            val met = CtColors.Text2.copy(alpha = 0.55f)
            Canvas(Modifier.fillMaxWidth((v.share / longest).toFloat().coerceIn(0.04f, 1f)).height(6.dp)) {
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(track, cornerRadius = radius)
                if (v.cards > 0) {
                    val metWidth = size.width * v.introduced / v.cards
                    val solidWidth = size.width * v.solid / v.cards
                    if (metWidth > 0f) drawRoundRect(met, size = Size(metWidth, size.height), cornerRadius = radius)
                    if (solidWidth > 0f) drawRoundRect(solid, size = Size(solidWidth, size.height), cornerRadius = radius)
                }
            }
        }
    }
}

@DrawableRes
private fun cupOf(level: RepertoireLevel): Int = when (level) {
    RepertoireLevel.CORE -> R.drawable.medal_cup_bronze
    RepertoireLevel.STANDARD -> R.drawable.medal_cup_silver
    RepertoireLevel.DEEP -> R.drawable.medal_cup_gold
}

private val LevelNames = mapOf(RepertoireLevel.CORE to "Core", RepertoireLevel.STANDARD to "Standard", RepertoireLevel.DEEP to "Deep")

/** Cups not won yet are grey and faint: one treatment, the same everywhere. */
private val Dull = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** The three levels on one rail: a cup each, bright once its level is mastered (D76). */
@Composable
private fun CupRail(ui: RepertoireBookUi) {
    val open = ui.levels.unlocked
    val current = ui.mastery[open]
    val within = current?.let { if (it.total == 0) 0f else it.settled.toFloat() / it.total } ?: 0f
    // Two stretches of rail: Core to Standard, Standard to Deep.
    val filled = ((open.ordinal + if (open in ui.levels.masteredAt) 1f else within) / 2f).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(48.dp)) {
            val track = CtColors.Track
            val accent = CtColors.Accent
            Canvas(Modifier.fillMaxSize()) {
                // The rail runs only in the gaps between the cups, never through them.
                val y = size.height / 2
                val gap = 28.dp.toPx()
                val centres = listOf(24.dp.toPx(), size.width / 2, size.width - 24.dp.toPx())
                for (i in 0..1) {
                    val start = centres[i] + gap
                    val end = centres[i + 1] - gap
                    if (end <= start) continue
                    val done = (filled * 2 - i).coerceIn(0f, 1f)
                    drawLine(track, Offset(start, y), Offset(end, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    if (done > 0f) drawLine(accent, Offset(start, y), Offset(start + (end - start) * done, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                }
            }
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                RepertoireLevel.entries.forEach { level ->
                    val won = level in ui.levels.masteredAt
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Image(
                            painterResource(cupOf(level)),
                            contentDescription = null,
                            colorFilter = if (won) null else Dull,
                            alpha = when {
                                won -> 1f
                                level == open -> 0.75f
                                else -> 0.4f
                            },
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            RepertoireLevel.entries.forEachIndexed { i, level ->
                LevelLabel(level, ui.mastery[level], won = level in ui.levels.masteredAt, open = ui.levels.isOpen(level), align = i)
            }
        }
    }
}

@Composable
private fun LevelLabel(level: RepertoireLevel, progress: MasteryProgress?, won: Boolean, open: Boolean, align: Int) {
    val name = LevelNames.getValue(level)
    val total = progress?.total ?: 0
    val spoken = when {
        won -> "$name mastered"
        open -> "$name, ${progress?.settled ?: 0} of $total learned"
        else -> "$name, locked, $total positions"
    }
    Column(
        modifier = Modifier.width(104.dp).semantics(mergeDescendants = true) { contentDescription = spoken },
        horizontalAlignment = when (align) {
            0 -> Alignment.Start
            1 -> Alignment.CenterHorizontally
            else -> Alignment.End
        },
    ) {
        Text(name, style = CtType.Label.copy(color = if (open) CtColors.Text else CtColors.Text2))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!open) Icon(Icons.Rounded.Lock, contentDescription = null, tint = CtColors.Text2, modifier = Modifier.size(13.dp))
            Text(if (open && !won) "${progress?.settled ?: 0} / $total" else "$total", style = CtType.Caption.copy(color = CtColors.Text2))
        }
    }
}

private fun percent(share: Double): String = "${(share * 100).roundToInt()}%"

/**
 * A learn, review or tricky-lines run on the fixed board (mock-up round 2, direction A):
 * the coach asks, reveals and explains in the zone above the board; the moves follow in the
 * strip below it; the controls sit in their fixed row. The mastery sheet covers the screen
 * when a level is won.
 */
@Composable
fun RepertoireRunScreen(
    run: RunUi?,
    settings: AppSettings,
    onFeedback: (BoardFeedback) -> Unit,
    onNext: () -> Unit,
    onHint: () -> Unit,
    onShowAnswer: () -> Unit,
    onPickBranch: (uci: String) -> Unit,
    onLearnMore: () -> Unit,
    onDismissMastered: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (run == null) return
    val board by run.board.state.collectAsState()
    val moment = run.moment
    CtBoardScreen(
        title = run.title,
        onBack = onBack,
        modifier = modifier,
        zone = {
            RunProgress(run)
            CtTutor(settings.mascot, mood = moodOf(moment), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                RunWords(run)
            }
        },
        under = {
            BoardMoveList(entries = remember(board.version, board.moves.size) { moveListOf(board.start, board.moves) }, currentPly = board.moves.size)
        },
        board = {
            val arrow = when (moment) {
                is RunMoment.Shown -> moment.uci
                is RunMoment.Also -> moment.uci
                is RunMoment.CardDone -> moment.shown
                else -> null
            }
            BoardHost(
                session = run.board,
                theme = settings.boardTheme,
                whiteAtBottom = run.side == PieceColor.WHITE,
                showCoordinates = settings.showCoordinates,
                showLegalDots = settings.showLegalDots,
                autoQueen = settings.autoQueen,
                arrows = listOfNotNull(arrow?.let { BoardArrow(Square.fromAlgebraic(it.substring(0, 2)), Square.fromAlgebraic(it.substring(2, 4)), MarkKind.HINT) }),
                rings = listOfNotNull(run.hintFrom?.let { SquareRing(Square.fromAlgebraic(it), MarkKind.HINT) }),
                onFeedback = onFeedback,
                soundsOtherMoves = true,
            )
        },
        controls = {
            when (moment) {
                is RunMoment.Reveal, is RunMoment.CardDone -> CtButton(text = "Next", onClick = onNext, modifier = Modifier.weight(1f))
                is RunMoment.Intro -> CtButton(text = "Start", onClick = onNext, modifier = Modifier.weight(1f))
                is RunMoment.Branch -> if (moment.picked == null) {
                    moment.question.choices.forEachIndexed { i, reply ->
                        CtButton(text = moment.sans[i].substringAfterLast('.'), onClick = { onPickBranch(reply.uci) }, style = CtButtonStyle.Secondary, modifier = Modifier.weight(1f))
                    }
                } else {
                    CtButton(text = "Next", onClick = onNext, modifier = Modifier.weight(1f))
                }
                is RunMoment.Finished -> {
                    if (run.kind == RunKind.LEARN && run.moreToLearn) {
                        CtButton(text = "Learn more", onClick = onLearnMore, style = CtButtonStyle.Secondary, modifier = Modifier.weight(1f))
                    }
                    CtButton(text = "Done", onClick = onBack, modifier = Modifier.weight(1f))
                }
                else -> {
                    CtControl(Icons.Rounded.Lightbulb, "Hint", onClick = onHint)
                    CtControl(Icons.Rounded.Visibility, "Show me", onClick = onShowAnswer)
                }
            }
        },
        overlay = { MasterySheet(run, onDismiss = onDismissMastered) },
    )
}

@Composable
private fun RunProgress(run: RunUi) {
    if (run.kind == RunKind.LEARN) {
        // One segment per new move, like a lesson's steps.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(run.steps) { i ->
                CtProgressBar(progress = if (i < run.step) 1f else 0f, height = 3.dp, modifier = Modifier.weight(1f))
            }
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
            CtProgressBar(progress = if (run.steps == 0) 0f else run.step.toFloat() / run.steps, height = 3.dp, modifier = Modifier.weight(1f))
            Text("${(run.step + 1).coerceAtMost(run.steps)} / ${run.steps}", style = CtType.Caption.copy(color = CtColors.Text2))
        }
    }
}

private fun moodOf(moment: RunMoment): MascotMood = when (moment) {
    is RunMoment.Reveal -> if (moment.guessed) MascotMood.PROUD else MascotMood.KIND
    is RunMoment.Shown, is RunMoment.Also -> MascotMood.KIND
    is RunMoment.CardDone -> if (moment.right) MascotMood.PROUD else MascotMood.KIND
    is RunMoment.Branch -> if (moment.picked == null) MascotMood.THINKING else MascotMood.READY
    is RunMoment.Finished -> MascotMood.PROUD
    is RunMoment.Intro -> MascotMood.READY
    else -> MascotMood.THINKING
}

@Composable
private fun RunWords(run: RunUi) {
    when (val moment = run.moment) {
        RunMoment.YourMove -> {
            Text(run.against?.let { "What do you play against $it?" } ?: "Your move.", style = CtType.Chat)
            if (run.tricky) Text("A tricky line, from the start.", style = CtType.Caption.copy(color = CtColors.Text2))
        }
        RunMoment.FromTheTop -> Text("Now from the start, from memory.", style = CtType.Chat)
        is RunMoment.Intro -> {
            Text(moment.name, style = CtType.TitleSmall)
            Text(moment.text ?: "Guess each move first. Then the line again, from memory.", style = CtType.Chat)
        }
        is RunMoment.Reveal -> {
            Text(moment.san, style = CtType.TitleSmall)
            moment.note?.let { Text(it.text, style = CtType.Chat) }
            moment.share?.let { Text("Played in ${percent(it)} of games", style = CtType.Caption.copy(color = CtColors.Text2)) }
            moment.pitfall?.let { Text(it, style = CtType.Caption.copy(color = CtColors.Error)) }
        }
        is RunMoment.Also -> Text(
            moment.san?.let { "Also good, but your move here is $it. Play it to go on." } ?: "Also good, but not the move to remember here. Try again.",
            style = CtType.Chat,
        )
        is RunMoment.Shown -> Text("Your move here is ${moment.san}. Play it to go on.", style = CtType.Chat)
        is RunMoment.Branch -> {
            val answer = moment.question.answer.uci
            Text(
                when (moment.picked) {
                    null -> "What do most players at your level play here?"
                    answer -> "Right, most play ${moment.sans.first()}."
                    else -> "Most play ${moment.sans.first()}."
                },
                style = CtType.Chat,
            )
            if (moment.picked != null) {
                Text(
                    moment.question.choices.mapIndexed { i, r -> "${moment.sans[i]} ${percent(r.share)}" }.joinToString("   "),
                    style = CtType.Caption.copy(color = CtColors.Text2),
                )
            }
        }
        is RunMoment.CardDone -> Text(
            if (moment.right) "Right. " + comesBack(moment.dueAt.toEpochMilli() - System.currentTimeMillis()) else "It comes back today.",
            style = CtType.Chat,
        )
        is RunMoment.Finished -> Text(
            if (run.kind == RunKind.LEARN) "${moment.total} new move${if (moment.total == 1) "" else "s"} learnt. They come back tomorrow."
            else "${moment.right} of ${moment.total} right first time.",
            style = CtType.Chat,
        )
    }
}

/** A level is won: its cup, what opens next, and the three cups (D76). */
@Composable
private fun MasterySheet(run: RunUi, onDismiss: () -> Unit) {
    val level = run.mastered
    CtSheet(visible = level != null, onDismiss = onDismiss) {
        if (level == null) return@CtSheet
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
            Image(painterResource(cupOf(level)), contentDescription = null, modifier = Modifier.size(132.dp))
            val next = RepertoireLevel.entries.getOrNull(level.ordinal + 1)
            Text(
                if (next == null) "${run.masteredBook ?: run.title} mastered" else "${LevelNames.getValue(level)} mastered",
                style = CtType.Display,
            )
            Text(
                if (next == null) "Every line, every level." else "${LevelNames.getValue(next)} is open: ${run.nextLevelCards} new positions.",
                style = CtType.Body.copy(color = CtColors.Text2),
            )
            Row(Modifier.padding(vertical = CtSpacing.sm), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                RepertoireLevel.entries.forEach { cup ->
                    val won = cup.ordinal <= level.ordinal
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(painterResource(cupOf(cup)), contentDescription = null, colorFilter = if (won) null else Dull, alpha = if (won) 1f else 0.4f, modifier = Modifier.size(40.dp))
                        Text(LevelNames.getValue(cup), style = CtType.Caption.copy(color = if (won) CtColors.Text else CtColors.Text2))
                    }
                }
            }
            CtButton(text = if (next == null) "Done" else "Start ${LevelNames.getValue(next)}", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}
