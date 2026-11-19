package com.chesstutor.app.feature.learn

import com.chesstutor.app.domain.learn.LessonVideos
import com.chesstutor.app.domain.learn.LessonVideo
import com.chesstutor.app.domain.learn.PlainMoves
import com.chesstutor.app.design.CtGroupLabel
import com.chesstutor.app.design.CtSegmented
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
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.design.CtArt
import com.chesstutor.app.design.CtBackBar
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtCard
import com.chesstutor.app.design.CtChip
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtDropdownChip
import com.chesstutor.app.design.CtEmptyState
import com.chesstutor.app.design.CtListRow
import com.chesstutor.app.design.CtProgressBar
import com.chesstutor.app.design.CtRing
import com.chesstutor.app.design.CtRingPart
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
 * A topic page (plan 6.2, D96), for example Endgames: the topic's scene, then Beginner,
 * Intermediate and Advanced along the top, each course of the chosen tier as a ring that fills
 * (a tick once done) with its count under its name, and the tier's videos. Openings can also be
 * filtered by side; search hides behind its icon until wanted.
 */
@Composable
fun LearnCategoryScreen(
    category: LessonCategory,
    learn: LearnState,
    onOpenCourse: (courseId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    videos: LessonVideos = LessonVideos.EMPTY,
    onOpenVideo: (LessonVideo) -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    val all = learn.library.coursesIn(category)
    // Opens on the first tier with something left to do.
    val firstOpen = LessonLevel.entries.firstOrNull { level ->
        all.any { course -> course.level == level && LearnHubModel.countOf(course.lessons, learn.progress).let { it.completed < it.total } }
    } ?: LessonLevel.BEGINNER
    var levelKey by rememberSaveable(firstOpen) { mutableStateOf(firstOpen.key) }
    var sideKey by rememberSaveable { mutableStateOf<String?>(null) }
    val tier = LessonLevel.fromKey(levelKey) ?: LessonLevel.BEGINNER
    val filter = CourseFilter(
        query = query,
        // A search looks through every tier.
        level = tier.takeIf { query.isBlank() },
        side = sideKey?.let { if (it == "white") PieceColor.WHITE else PieceColor.BLACK },
        against = when (sideKey) { "black-e4" -> "e2e4"; "black-d4" -> "d2d4"; else -> null },
    )
    val shown = LearnHubModel.filter(all, filter)
    val (icon, _) = categoryStyle(category)
    val count = LearnHubModel.countOf(all.flatMap { it.lessons }, learn.progress)

    CtScreenBackground(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(HERO_HEIGHT)) {
                CtArt(categoryScene(category), Modifier.matchParentSize(), lift = 0.14f, alive = true)
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
                CtScreenTitle(title = category.title, modifier = Modifier.align(Alignment.BottomStart))
            }
            Text("${count.completed} of ${count.total} lessons", style = CtType.BodySmall, modifier = Modifier.padding(horizontal = CtSpacing.gutter))
            Column(Modifier.padding(horizontal = CtSpacing.gutter)) {
                if (searching) {
                    Spacer(Modifier.height(CtSpacing.md))
                    CtTextField(value = query, onValueChange = { query = it }, label = "Search", placeholder = "Name or idea")
                }
                CtSegmented(
                    options = LessonLevel.entries,
                    selected = tier,
                    label = { it.label },
                    onSelect = { levelKey = it.key },
                    modifier = Modifier.padding(top = CtSpacing.md),
                )
                Row(
                    modifier = Modifier.padding(top = CtSpacing.sm, bottom = CtSpacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm),
                ) {
                    if (LearnHubModel.offersSideFilter(all)) {
                        FilterDropdown(
                            label = SIDE_CHOICES.firstOrNull { it.first == sideKey }?.second ?: "Both sides",
                            options = SIDE_CHOICES,
                            onPick = { sideKey = it },
                        )
                    }
                }
                if (shown.isEmpty()) {
                    CtEmptyState(
                        icon = icon,
                        title = if (query.isBlank()) "${tier.label} courses on the way" else "Nothing matches",
                        body = if (query.isBlank()) "They are being written." else "Try another word or clear the filters.",
                    )
                }
                shown.forEach { course ->
                    CourseRow(course, learn.progress, onClick = { onOpenCourse(course.id) })
                }
                VideoRow(
                    videos = videos.forTopic(category, tier, all.map { it.id }.toSet()),
                    onOpen = onOpenVideo,
                    modifier = Modifier.padding(top = CtSpacing.lg),
                )
                Spacer(Modifier.height(CtSpacing.xl))
            }
        }
    }
}

private val HERO_HEIGHT = 240.dp

/** The side filter: either side, one side, or Black's answers to 1.e4 or 1.d4 (D111). */
private val SIDE_CHOICES = listOf(null to "Both sides", "white" to "White", "black" to "Black", "black-e4" to "Black vs 1.e4", "black-d4" to "Black vs 1.d4")

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

/** A course: how far you are round a ring (a tick once done), its name, who it is for and how many lessons are done. */
@Composable
private fun CourseRow(course: Course, progress: Map<String, LessonProgress>, onClick: () -> Unit) {
    val count = LearnHubModel.countOf(course.lessons, progress)
    val who = course.side?.let { if (it == PieceColor.WHITE) "For White" else "For Black" }
    val lessons = when {
        count.completed == 0 -> "${count.total} lesson${if (count.total == 1) "" else "s"}"
        count.completed == count.total -> "${count.total} of ${count.total} lessons, done"
        else -> "${count.completed} of ${count.total} lessons"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 68.dp)
            .ctPressable(onClickLabel = "Open ${course.title}", onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ProgressRing(count.completed, count.total)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(course.title, style = CtType.TitleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(who, lessons).joinToString(", "), style = CtType.Caption, maxLines = 1)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = CtColors.Text2)
    }
}

/**
 * A course page (plan 6.2, Phase 7D, canvas `Course.dc.html`): its topic and title, how far
 * you are on the topic's scene, the lesson to do next as a card, the other lessons as quiet rows
 * and ways to practise. A finished course offers its first lesson again.
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
    /** The course's drills in Train (Phase 12): open, or locked until the course is done; null hides the row. */
    drills: CourseDrills? = null,
    /** The course's general video, shown at the top in place of the art (D100). */
    video: LessonVideo? = null,
) {
    CtScreenBackground(modifier) {
        if (course == null) {
            Column(Modifier.fillMaxSize()) { CtTopBar(title = "Lessons", onBack = onBack) }
            return@CtScreenBackground
        }
        val count = LearnHubModel.countOf(course.lessons, progress)
        // The lesson to do next: the first one not finished.
        val current = course.lessons.firstOrNull { progress[it.id]?.completed != true } ?: course.lessons.firstOrNull()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if (video != null) {
                CtBackBar(onBack = onBack)
                Column(Modifier.padding(horizontal = CtSpacing.gutter)) {
                    VideoFrame(video, autoplay = false, modifier = Modifier.fillMaxWidth())
                    Text(course.category.title, style = CtType.BodySmall, modifier = Modifier.padding(top = CtSpacing.sm))
                    Text(course.title, style = CtType.Display, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Box(Modifier.fillMaxWidth().height(HERO_HEIGHT)) {
                    CtArt(categoryScene(course.category), Modifier.matchParentSize(), lift = 0.14f, alive = true)
                    CtBackBar(onBack = onBack)
                    Column(Modifier.align(Alignment.BottomStart).padding(horizontal = CtSpacing.gutter)) {
                        Text(course.category.title, style = CtType.BodySmall)
                        Text(course.title, style = CtType.Display, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Column(Modifier.padding(start = CtSpacing.gutter, end = CtSpacing.gutter, top = CtSpacing.xs), verticalArrangement = Arrangement.spacedBy(CtSpacing.xs)) {
                if (course.intro.isNotBlank()) Text(course.intro, style = CtType.Caption, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = CtSpacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
                    CtProgressBar(progress = count.progress, height = 3.dp, modifier = Modifier.weight(1f))
                    Text("${count.completed} of ${count.total}", style = CtType.Caption)
                }
            }
            Column(Modifier.padding(start = CtSpacing.gutter, end = CtSpacing.gutter, top = CtSpacing.lg)) {
                course.lessons.forEachIndexed { i, original ->
                    // Beginner courses name their lessons in plain words (D99).
                    val lesson = if (PlainMoves.appliesTo(course)) original.copy(title = PlainMoves.soften(original.title)) else original
                    if (original == current) {
                        CurrentLesson(number = i + 1, lesson = lesson, progress = progress[lesson.id], onOpen = { onOpenLesson(lesson.id) })
                    } else {
                        LessonRow(number = i + 1, lesson = lesson, done = progress[lesson.id]?.completed == true, onClick = { onOpenLesson(lesson.id) })
                    }
                }
                drills?.let { DrillsRow(it, lessonsLeft = course.lessons.count { lesson -> progress[lesson.id]?.completed != true }) }
                if (onPractise != null || onRelatedPuzzles != null || onPracticeSet != null) {
                    CtGroupLabel("Practise", modifier = Modifier.padding(top = CtSpacing.lg))
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
                onRelatedPuzzles?.let { CtListRow(title = "Related puzzles", icon = Icons.Rounded.Extension, onClick = it) }
                Spacer(Modifier.height(CtSpacing.xl))
            }
        }
    }
}

/** The lesson to do next, raised: its title, summary, where you are, and the button. */
@Composable
private fun CurrentLesson(number: Int, lesson: Lesson, progress: LessonProgress?, onOpen: () -> Unit) {
    val finished = progress?.completed == true
    val resuming = progress?.started == true && !finished
    val challenges = lesson.quizCount
    val detail = if (resuming) {
        "Resume at step ${progress!!.stepIndex + 1} of ${lesson.steps.size}"
    } else {
        "${lesson.steps.count { it !is LessonStep.Watch }} steps" + if (challenges > 0) ", $challenges challenge${if (challenges == 1) "" else "s"}" else ""
    }
    CtCard(onClick = onOpen, onClickLabel = "Open lesson $number, ${lesson.title}", modifier = Modifier.fillMaxWidth().padding(vertical = CtSpacing.xs)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(lesson.title, style = CtType.TopBar, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (lesson.summary.isNotBlank()) Text(lesson.summary, style = CtType.BodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(detail, style = CtType.Caption)
            Spacer(Modifier.height(CtSpacing.md))
            CtButton(
                text = when {
                    finished -> "Play again"
                    resuming -> "Continue"
                    else -> "Start"
                },
                icon = Icons.AutoMirrored.Rounded.ArrowForward,
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Another lesson: a tick when finished, its number otherwise. */
@Composable
private fun LessonRow(number: Int, lesson: Lesson, done: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .ctPressable(onClickLabel = "Open lesson $number, ${lesson.title}", onClick = onClick)
            .padding(vertical = CtSpacing.sm),
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
