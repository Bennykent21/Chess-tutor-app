package com.chesstutor.app.feature.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chesstutor.app.board.BoardTheme
import com.chesstutor.app.board.BoardView
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.design.CtBackBar
import com.chesstutor.app.design.CtChip
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtRing
import com.chesstutor.app.design.CtRingPart
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSegmented
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.learn.Course
import com.chesstutor.app.domain.learn.CourseGate
import com.chesstutor.app.domain.learn.LessonProgress

/**
 * One opening in Learn (Phase 12, design round 4): its ideas course and one course per
 * variation, with the trainer's opening when it teaches it. An opening with a single course
 * (no trainer opening yet) is just that course.
 */
data class OpeningGroup(
    val id: String,
    val title: String,
    val side: PieceColor?,
    val ideas: Course?,
    val variations: List<Course>,
    val book: RepertoireBookUi?,
) {
    val courses: List<Course> get() = listOfNotNull(ideas) + variations

    /** Where the opening's own moves end, for its small board. */
    val picture: String? get() = book?.book?.startPosition?.toFen() ?: courses.firstNotNullOfOrNull { c -> c.lessons.firstOrNull()?.pictureFen }

    companion object {
        /** Opening courses grouped by the trainer's opening they belong to, in category order. */
        fun of(courses: List<Course>, books: List<RepertoireBookUi>): List<OpeningGroup> =
            courses.sortedBy { it.order }.groupBy { it.opening ?: it.id }.map { (id, group) ->
                val book = books.firstOrNull { it.book.id == id }
                val ideas = group.firstOrNull { it.variations.isEmpty() }
                OpeningGroup(
                    id = id,
                    title = book?.book?.title ?: (ideas ?: group.first()).title,
                    side = group.firstNotNullOfOrNull { it.side },
                    ideas = ideas,
                    variations = group.filter { it.variations.isNotEmpty() },
                    book = book,
                )
            }
    }
}

/** How far the learner is in [courses]: lessons finished of all. */
private fun progressOf(courses: List<Course>, progress: Map<String, LessonProgress>): Pair<Int, Int> {
    val lessons = courses.flatMap { it.lessons }
    return lessons.count { progress[it.id]?.completed == true } to lessons.size
}

/**
 * Openings in Learn (design round 4): one side at a time, each opening with a small board of its
 * first moves and a ring for how far you are. General courses that suit both sides show on both.
 */
@Composable
fun OpeningsScreen(
    groups: List<OpeningGroup>,
    progress: Map<String, LessonProgress>,
    theme: BoardTheme,
    onOpen: (OpeningGroup) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sideKey by rememberSaveable { mutableStateOf("white") }
    val side = if (sideKey == "white") PieceColor.WHITE else PieceColor.BLACK
    CtScreenBackground(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            CtBackBar(onBack = onBack)
            Column(Modifier.padding(horizontal = CtSpacing.gutter), verticalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
                Text("Openings", style = CtType.Display)
                CtSegmented(
                    options = listOf(PieceColor.WHITE, PieceColor.BLACK),
                    selected = side,
                    label = { if (it == PieceColor.WHITE) "As White" else "As Black" },
                    onSelect = { sideKey = if (it == PieceColor.WHITE) "white" else "black" },
                )
                Column {
                    groups.filter { it.side == null || it.side == side }.forEach { group ->
                        val (done, total) = progressOf(group.courses, progress)
                        OpeningRow(group, done, total, theme, onClick = { onOpen(group) })
                    }
                }
                Spacer(Modifier.height(CtSpacing.xl))
            }
        }
    }
}

@Composable
private fun OpeningRow(group: OpeningGroup, done: Int, total: Int, theme: BoardTheme, onClick: () -> Unit) {
    val courses = group.courses.size
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 68.dp)
            .ctPressable(onClickLabel = "Open ${group.title}", onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Thumbnail(group.picture, theme, flipped = group.side == PieceColor.BLACK)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(group.title, style = CtType.TitleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (courses == 1) "$total lesson${if (total == 1) "" else "s"}" else "$courses courses", style = CtType.Caption, maxLines = 1)
        }
        ProgressRing(done, total)
    }
}

@Composable
private fun Thumbnail(fen: String?, theme: BoardTheme, flipped: Boolean) {
    val position = remember(fen) { fen?.let { Position.tryFromFen(it).getOrNull() } ?: Position.initial() }
    Box(Modifier.size(52.dp).clip(CtShapes.Chip)) {
        BoardView(position, theme, whiteAtBottom = !flipped, showCoordinates = false, showLegalDots = false)
    }
}

/** A ring with a tick when everything is done, else "done/total". */
@Composable
private fun ProgressRing(done: Int, total: Int) {
    val share = if (total == 0) 0f else done.toFloat() / total
    CtRing(listOf(CtRingPart(share, CtColors.Text)), diameter = 44.dp, stroke = 3.dp, modifier = Modifier.semantics { contentDescription = "$done of $total lessons" }) {
        if (total > 0 && done == total) Icon(Icons.Rounded.Check, contentDescription = null, tint = CtColors.Text, modifier = Modifier.size(20.dp).align(Alignment.Center))
        else Text("$done/$total", style = CtType.NavLabel.copy(color = CtColors.Text), maxLines = 1, modifier = Modifier.align(Alignment.Center))
    }
}

/**
 * An opening in Learn (design round 4): the ideas course first, then a course per variation,
 * most common first. A lock means that variation's drills open once its course is done.
 * "Know it already? Take the test" opens every drill for players who pass it.
 */
@Composable
fun LearnOpeningScreen(
    group: OpeningGroup,
    progress: Map<String, LessonProgress>,
    onOpenCourse: (Course) -> Unit,
    onTest: (() -> Unit)?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val side = when (group.side) {
        PieceColor.WHITE -> "As White"
        PieceColor.BLACK -> "As Black"
        null -> null
    }
    val lessons = group.courses.sumOf { it.lessons.size }
    val shares = group.book?.variations.orEmpty()
    val ordered = group.variations.sortedByDescending { course -> course.variations.sumOf { path -> shares.filter { it.path.take(path.size) == path }.sumOf { it.share } } }
    CtScreenBackground(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            CtBackBar(onBack = onBack)
            Column(Modifier.padding(horizontal = CtSpacing.gutter)) {
                Text(group.title, style = CtType.Display)
                Text(listOfNotNull(side, "${group.courses.size} courses", "$lessons lessons").joinToString(" · "), style = CtType.BodySmall.copy(color = CtColors.Text2))
                Spacer(Modifier.height(CtSpacing.md))
                group.ideas?.let { course -> CourseLine(course, "The ideas", null, progress, drills = null, onClick = { onOpenCourse(course) }) }
                if (ordered.isNotEmpty()) {
                    Text("Variations", style = CtType.Label.copy(color = CtColors.Text2), modifier = Modifier.padding(top = CtSpacing.lg, bottom = CtSpacing.xs))
                    ordered.forEach { course ->
                        val share = course.variations.sumOf { path -> shares.filter { it.path.take(path.size) == path }.sumOf { it.share } }
                        val first = course.variations.mapNotNull { path -> shares.firstOrNull { it.path.take(path.size) == path || path.take(it.path.size) == it.path }?.move }.distinct()
                        val detail = listOfNotNull(first.take(3).joinToString(", ").ifEmpty { null }, share.takeIf { it > 0 }?.let { "${(it * 100).toInt()}% of games" }).joinToString(" · ")
                        val open = group.book?.let { book -> course.variations.all { CourseGate.isOpen(it, book.open) } } ?: false
                        CourseLine(course, course.title, detail.ifEmpty { null }, progress, drills = open, onClick = { onOpenCourse(course) })
                    }
                }
                if (onTest != null && group.book?.locked != false) {
                    Text(
                        "Know it already? Take the test",
                        style = CtType.Label.copy(color = CtColors.Accent),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = CtSpacing.lg).ctPressable(onClickLabel = "Take the test", onClick = onTest).padding(vertical = CtSpacing.sm),
                    )
                }
                Spacer(Modifier.height(CtSpacing.xl))
            }
        }
    }
}

/** A course of an opening: its ring, its name and what it covers, and whether its drills are open. */
@Composable
private fun CourseLine(course: Course, title: String, detail: String?, progress: Map<String, LessonProgress>, drills: Boolean?, onClick: () -> Unit) {
    val (done, total) = progressOf(listOf(course), progress)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 60.dp)
            .ctPressable(onClickLabel = "Open $title", onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ProgressRing(done, total)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = CtType.TitleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(detail ?: "$total lesson${if (total == 1) "" else "s"}", style = CtType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when (drills) {
            true -> CtChip(text = "Drills open", color = CtColors.Success)
            false -> Icon(Icons.Rounded.Lock, contentDescription = "Drills locked", tint = CtColors.Text2, modifier = Modifier.size(16.dp))
            null -> Unit
        }
    }
}

/** The drills row of a course page (Phase 12): open, or locked until the course is done. */
data class CourseDrills(val open: Boolean, val onOpen: () -> Unit)

@Composable
internal fun DrillsRow(drills: CourseDrills, lessonsLeft: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 60.dp)
            .then(if (drills.open) Modifier.ctPressable(onClickLabel = "Train it", onClick = drills.onOpen) else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CtShapes.Pill).background(CtColors.Surface), contentAlignment = Alignment.Center) {
            Icon(if (drills.open) Icons.Rounded.Check else Icons.Rounded.Lock, contentDescription = null, tint = CtColors.Text, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("Drills", style = CtType.TitleSmall)
            Text(
                if (drills.open) "Open in Train" else "Open after ${lessonsLeft} more lesson${if (lessonsLeft == 1) "" else "s"}",
                style = CtType.Caption,
            )
        }
    }
}
