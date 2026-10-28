package com.chesstutor.app.feature.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chesstutor.app.board.BoardTheme
import com.chesstutor.app.board.BoardView
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.design.CtBackBar
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtCard
import com.chesstutor.app.design.CtChip
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtDivider
import com.chesstutor.app.design.CtDropdownChip
import com.chesstutor.app.design.CtEmptyState
import com.chesstutor.app.design.CtListRow
import com.chesstutor.app.design.CtProgressBar
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtScreenTitle
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTextField
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.learn.Course
import com.chesstutor.app.domain.learn.Lesson
import com.chesstutor.app.domain.learn.LessonCategory
import com.chesstutor.app.domain.learn.LessonLevel
import com.chesstutor.app.domain.learn.LessonProgress
import com.chesstutor.app.domain.learn.LessonStep
import com.chesstutor.app.domain.learn.PracticeResult
import com.chesstutor.app.domain.learn.PracticeSets

/**
 * A category page (plan 6.2, Phase 7D, canvas `LearnOpenings.dc.html`), for example Openings:
 * every course as a row with a board picture, who it is for and its level, filtered by level
 * and side with dropdown chips. Search hides behind its icon until wanted.
 */
@Composable
fun LearnCategoryScreen(
    category: LessonCategory,
    learn: LearnState,
    onOpenCourse: (courseId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** The player's board, for the course pictures. */
    boardTheme: BoardTheme = BoardTheme(),
) {
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var levelKey by rememberSaveable { mutableStateOf<String?>(null) }
    var sideKey by rememberSaveable { mutableStateOf<String?>(null) }
    val all = learn.library.coursesIn(category)
    val filter = CourseFilter(
        query = query,
        level = levelKey?.let(LessonLevel::fromKey),
        side = sideKey?.let { if (it == "white") PieceColor.WHITE else PieceColor.BLACK },
    )
    val shown = LearnHubModel.filter(all, filter)
    val (icon, _) = categoryStyle(category)
    val count = LearnHubModel.countOf(all.flatMap { it.lessons }, learn.progress)

    CtScreenBackground(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CtBackBar(onBack = onBack, modifier = Modifier.weight(1f))
                if (all.size > 3) {
                    Box(
                        modifier = Modifier
                            .padding(end = CtSpacing.sm)
                            .size(CtSpacing.touchTarget)
                            .clip(CtShapes.Pill)
                            .ctPressable(onClickLabel = "Search courses") { searching = !searching },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = "Search courses", tint = CtColors.Text)
                    }
                }
            }
            CtScreenTitle(title = category.title)
            Text("${count.completed} of ${count.total} lessons", style = CtType.BodySmall, modifier = Modifier.padding(horizontal = CtSpacing.gutter))
            Column(Modifier.padding(horizontal = CtSpacing.gutter)) {
                if (searching) {
                    Spacer(Modifier.height(CtSpacing.md))
                    CtTextField(value = query, onValueChange = { query = it }, label = "Search", placeholder = "Name or idea")
                }
                Row(
                    modifier = Modifier.padding(top = CtSpacing.md, bottom = CtSpacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm),
                ) {
                    val levels = LessonLevel.entries.filter { level -> all.any { it.level == level } }
                    FilterDropdown(
                        label = levelKey?.let(LessonLevel::fromKey)?.label ?: "Any level",
                        options = listOf(null to "Any level") + levels.map { it.key to it.label },
                        onPick = { levelKey = it },
                    )
                    if (LearnHubModel.offersSideFilter(all)) {
                        FilterDropdown(
                            label = when (sideKey) { "white" -> "White"; "black" -> "Black"; else -> "Both sides" },
                            options = listOf(null to "Both sides", "white" to "White", "black" to "Black"),
                            onPick = { sideKey = it },
                        )
                    }
                }
                if (shown.isEmpty()) {
                    CtEmptyState(
                        icon = icon,
                        title = if (all.isEmpty()) "Lessons on the way" else "Nothing matches",
                        body = if (all.isEmpty()) "Lessons for this category are being written." else "Try another word or clear the filters.",
                    )
                }
                shown.forEachIndexed { i, course ->
                    CourseRow(course, learn.progress, boardTheme, last = i == shown.lastIndex, onClick = { onOpenCourse(course.id) })
                }
                Spacer(Modifier.height(CtSpacing.xl))
            }
        }
    }
}

/** A chip that opens a short menu of choices; null is "any". */
@Composable
private fun FilterDropdown(label: String, options: List<Pair<String?, String>>, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        CtDropdownChip(text = label, onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = CtColors.Raised) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text, style = CtType.Body.copy(color = CtColors.Text)) },
                    onClick = {
                        open = false
                        onPick(value)
                    },
                )
            }
        }
    }
}

/** A course: a picture of its first board, its name, who it is for and its level. */
@Composable
private fun CourseRow(course: Course, progress: Map<String, LessonProgress>, boardTheme: BoardTheme, last: Boolean, onClick: () -> Unit) {
    val count = LearnHubModel.countOf(course.lessons, progress)
    val who = course.side?.let { if (it == PieceColor.WHITE) "For White" else "For Black" }
    val lessons = if (count.completed > 0) "${count.completed} of ${count.total} done" else "${count.total} lesson${if (count.total == 1) "" else "s"}"
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 76.dp)
                .ctPressable(onClickLabel = "Open ${course.title}", onClick = onClick)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(56.dp).clip(CtShapes.Chip)) {
                val position = course.lessons.firstOrNull()?.steps?.firstOrNull()?.fen?.let { Position.tryFromFen(it).getOrNull() } ?: Position.initial()
                BoardView(position = position, theme = boardTheme, whiteAtBottom = course.side != PieceColor.BLACK, showCoordinates = false, showLegalDots = false)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(course.title, style = CtType.TitleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(who, lessons).joinToString(", "), style = CtType.Caption, maxLines = 1)
            }
            LevelBars(course.level)
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = CtColors.Text2)
        }
        if (!last) CtDivider()
    }
}

/** One, two or three rising bars for beginner, intermediate and advanced. */
@Composable
private fun LevelBars(level: LessonLevel) {
    val filled = LessonLevel.entries.indexOf(level) + 1
    Row(
        modifier = Modifier.height(14.dp).semantics { contentDescription = level.label },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        listOf(6, 10, 14).forEachIndexed { i, h ->
            Box(Modifier.width(3.dp).height(h.dp).clip(CtShapes.Pill).background(if (i < filled) CtColors.Accent else CtColors.Track))
        }
    }
}

/**
 * A course page (plan 6.2, Phase 7D, canvas `Course.dc.html`): its topic and title, how far
 * you are, the lesson to do next as a card, the other lessons as quiet rows, and ways to
 * practise.
 */
@Composable
fun CourseScreen(
    course: Course?,
    progress: Map<String, LessonProgress>,
    onOpenLesson: (lessonId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Opens Opening Battle on the course's line; null hides the row. */
    onPractise: (() -> Unit)? = null,
    /** Opens puzzles on the course's themes; null hides the row. */
    onRelatedPuzzles: (() -> Unit)? = null,
    /** Plays the course's practice set (plan 6C); null hides the row. */
    onPracticeSet: (() -> Unit)? = null,
    /** The best go at the practice set so far. */
    practice: PracticeResult? = null,
) {
    CtScreenBackground(modifier) {
        if (course == null) {
            Column(Modifier.fillMaxSize()) { CtTopBar(title = "Lessons", onBack = onBack) }
            return@CtScreenBackground
        }
        val count = LearnHubModel.countOf(course.lessons, progress)
        // The lesson to do next: the first one not finished.
        val current = course.lessons.firstOrNull { progress[it.id]?.completed != true }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            CtBackBar(onBack = onBack)
            Column(Modifier.padding(horizontal = CtSpacing.gutter), verticalArrangement = Arrangement.spacedBy(CtSpacing.xs)) {
                Text(course.category.title, style = CtType.BodySmall)
                Text(course.title, style = CtType.Display)
                if (course.intro.isNotBlank()) Text(course.intro, style = CtType.Caption, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = CtSpacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
                    CtProgressBar(progress = count.progress, height = 3.dp, modifier = Modifier.weight(1f))
                    Text("${count.completed} of ${count.total}", style = CtType.Caption)
                }
            }
            Column(Modifier.padding(start = CtSpacing.gutter, end = CtSpacing.gutter, top = CtSpacing.lg)) {
                course.lessons.forEachIndexed { i, lesson ->
                    if (lesson == current) {
                        CurrentLesson(number = i + 1, lesson = lesson, progress = progress[lesson.id], onOpen = { onOpenLesson(lesson.id) })
                    } else {
                        LessonRow(number = i + 1, lesson = lesson, done = progress[lesson.id]?.completed == true, onClick = { onOpenLesson(lesson.id) })
                    }
                }
                if (onPractise != null || onRelatedPuzzles != null || onPracticeSet != null) {
                    CtSectionHeader(title = "Practise")
                }
                onPracticeSet?.let {
                    CtListRow(
                        title = "Practice set",
                        subtitle = when {
                            practice == null -> "${PracticeSets.SIZE} puzzles on this course's ideas"
                            practice.mastered -> "Mastered, best ${practice.best}/${practice.total}"
                            else -> "Best ${practice.best}/${practice.total}, solve ${PracticeSets.passMark(practice.total)} to master it"
                        },
                        icon = Icons.Rounded.Star,
                        onClick = it,
                        trailing = if (practice?.mastered == true) { { CtChip(text = "Mastered", color = CtColors.Success, icon = Icons.Rounded.Check) } } else null,
                    )
                }
                onPractise?.let { CtListRow(title = "Practise in Opening Battle", icon = Icons.Rounded.Flag, onClick = it) }
                onRelatedPuzzles?.let { CtListRow(title = "Related puzzles", icon = Icons.Rounded.Extension, onClick = it, divider = false) }
                Spacer(Modifier.height(CtSpacing.xl))
            }
        }
    }
}

/** The lesson to do next, raised: its number, title, summary, where you are, and Start or Continue. */
@Composable
private fun CurrentLesson(number: Int, lesson: Lesson, progress: LessonProgress?, onOpen: () -> Unit) {
    val started = progress?.started == true
    val challenges = lesson.quizCount
    val detail = if (started) {
        "Resume at step ${progress!!.stepIndex + 1} of ${lesson.steps.size}"
    } else {
        "${lesson.steps.count { it !is LessonStep.Watch }} steps" + if (challenges > 0) ", $challenges challenge${if (challenges == 1) "" else "s"}" else ""
    }
    CtCard(onClick = onOpen, onClickLabel = "Open lesson $number, ${lesson.title}", modifier = Modifier.fillMaxWidth().padding(vertical = CtSpacing.xs)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier.size(32.dp).clip(CtShapes.Pill).border(2.dp, CtColors.Accent, CtShapes.Pill),
                contentAlignment = Alignment.Center,
            ) {
                Text("$number", style = CtType.Label.copy(color = CtColors.Accent))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(lesson.title, style = CtType.TopBar, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (lesson.summary.isNotBlank()) Text(lesson.summary, style = CtType.BodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(detail, style = CtType.Caption)
            }
        }
        Spacer(Modifier.height(14.dp))
        CtButton(text = if (started) "Continue" else "Start", icon = Icons.AutoMirrored.Rounded.ArrowForward, onClick = onOpen, modifier = Modifier.fillMaxWidth())
    }
}

/** Another lesson: a tick when finished, its number otherwise. */
@Composable
private fun LessonRow(number: Int, lesson: Lesson, done: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 60.dp)
            .ctPressable(onClickLabel = "Open lesson $number, ${lesson.title}", onClick = onClick)
            .padding(horizontal = CtSpacing.lg, vertical = CtSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (done) {
            Box(
                modifier = Modifier.size(32.dp).clip(CtShapes.Pill).background(CtColors.Accent).semantics { contentDescription = "Finished" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = CtColors.OnAccent, modifier = Modifier.size(18.dp))
            }
        } else {
            Box(
                modifier = Modifier.size(32.dp).clip(CtShapes.Pill).border(1.5.dp, CtColors.Line, CtShapes.Pill),
                contentAlignment = Alignment.Center,
            ) {
                Text("$number", style = CtType.Label.copy(color = CtColors.Text2))
            }
        }
        Text(lesson.title, style = CtType.Body.copy(color = if (done) CtColors.Text2 else CtColors.Text), modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
