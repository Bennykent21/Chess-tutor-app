package com.chesstutor.app.feature.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtCard
import com.chesstutor.app.design.CtChip
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtEmptyState
import com.chesstutor.app.design.CtProgressBar
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTextField
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctEnter
import com.chesstutor.app.domain.learn.Course
import com.chesstutor.app.domain.learn.Lesson
import com.chesstutor.app.domain.learn.LessonCategory
import com.chesstutor.app.domain.learn.LessonLevel
import com.chesstutor.app.domain.learn.LessonProgress
import com.chesstutor.app.domain.learn.LessonStep

/**
 * A category page (plan section 6.2), for example Openings: every course as a card with
 * progress and a level tag, with search and filter chips (level, and side where it matters).
 */
@Composable
fun LearnCategoryScreen(
    category: LessonCategory,
    learn: LearnState,
    onOpenCourse: (courseId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var levelKey by rememberSaveable { mutableStateOf<String?>(null) }
    var sideKey by rememberSaveable { mutableStateOf<String?>(null) }
    val all = learn.library.coursesIn(category)
    val filter = CourseFilter(
        query = query,
        level = levelKey?.let(LessonLevel::fromKey),
        side = sideKey?.let { if (it == "white") PieceColor.WHITE else PieceColor.BLACK },
    )
    val shown = LearnHubModel.filter(all, filter)
    val (icon, accent) = categoryStyle(category)

    CtScreenBackground(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            CtTopBar(title = category.title, subtitle = "${all.size} course${if (all.size == 1) "" else "s"}", onBack = onBack)
            Column(
                modifier = Modifier.padding(horizontal = CtSpacing.gutter),
                verticalArrangement = Arrangement.spacedBy(CtSpacing.md),
            ) {
                if (all.size > 3) {
                    CtTextField(value = query, onValueChange = { query = it }, label = "Search", placeholder = "Name or idea")
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm),
                ) {
                    CtChip(text = "All levels", selected = levelKey == null, onClick = { levelKey = null })
                    LessonLevel.entries.filter { level -> all.any { it.level == level } }.forEach { level ->
                        CtChip(text = level.label, selected = levelKey == level.key, onClick = { levelKey = level.key })
                    }
                    if (LearnHubModel.offersSideFilter(all)) {
                        CtChip(text = "White", color = CtColors.Gold, selected = sideKey == "white", onClick = { sideKey = if (sideKey == "white") null else "white" })
                        CtChip(text = "Black", color = CtColors.Gold, selected = sideKey == "black", onClick = { sideKey = if (sideKey == "black") null else "black" })
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
                    CourseCard(course, learn.progress, accent, onClick = { onOpenCourse(course.id) }, modifier = Modifier.ctEnter(i))
                }
                Spacer(Modifier.height(CtSpacing.lg))
            }
        }
    }
}

@Composable
private fun CourseCard(
    course: Course,
    progress: Map<String, LessonProgress>,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = LearnHubModel.countOf(course.lessons, progress)
    CtCard(onClick = onClick, onClickLabel = "Open ${course.title}", modifier = modifier.fillMaxWidth()) {
        Text(course.title, style = CtType.Title, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (course.intro.isNotBlank()) {
            Text(course.intro, style = CtType.BodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(CtSpacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            CtChip(text = course.level.label, color = accent)
            course.side?.let { CtChip(text = if (it == PieceColor.WHITE) "For White" else "For Black", color = CtColors.Gold) }
            Spacer(Modifier.weight(1f))
            Text("${count.completed}/${count.total} lessons", style = CtType.Caption)
        }
        Spacer(Modifier.height(CtSpacing.sm))
        CtProgressBar(progress = count.progress, color = accent, height = 8.dp)
    }
}

/**
 * A course page (plan section 6.2), for example "Italian Game": the intro, its lessons as
 * numbered steps, and buttons for Opening Battle and related puzzles where they apply.
 */
@Composable
fun CourseScreen(
    course: Course?,
    progress: Map<String, LessonProgress>,
    onOpenLesson: (lessonId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Opens Opening Battle on the course's line; null hides the button. */
    onPractise: (() -> Unit)? = null,
    /** Opens puzzles on the course's themes; null hides the button. */
    onRelatedPuzzles: (() -> Unit)? = null,
) {
    CtScreenBackground(modifier) {
        if (course == null) {
            Column(Modifier.fillMaxSize()) { CtTopBar(title = "Lessons", onBack = onBack) }
            return@CtScreenBackground
        }
        val count = LearnHubModel.countOf(course.lessons, progress)
        val (_, accent) = categoryStyle(course.category)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            CtTopBar(title = course.title, subtitle = course.category.title, onBack = onBack)
            Column(
                modifier = Modifier.padding(horizontal = CtSpacing.gutter),
                verticalArrangement = Arrangement.spacedBy(CtSpacing.md),
            ) {
                CtCard(modifier = Modifier.fillMaxWidth()) {
                    if (course.intro.isNotBlank()) Text(course.intro, style = CtType.Body)
                    Spacer(Modifier.height(CtSpacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        CtChip(text = course.level.label, color = accent)
                        course.side?.let { CtChip(text = if (it == PieceColor.WHITE) "For White" else "For Black", color = CtColors.Gold) }
                        Spacer(Modifier.weight(1f))
                        Text("${count.completed}/${count.total} done", style = CtType.Caption)
                    }
                    Spacer(Modifier.height(CtSpacing.sm))
                    CtProgressBar(progress = count.progress, color = accent, height = 8.dp)
                }

                CtSectionHeader(title = "Lessons")
                course.lessons.forEachIndexed { i, lesson ->
                    LessonRow(number = i + 1, lesson = lesson, progress = progress[lesson.id], onClick = { onOpenLesson(lesson.id) }, modifier = Modifier.ctEnter(i))
                }

                if (onPractise != null || onRelatedPuzzles != null) {
                    CtSectionHeader(title = "Practise")
                }
                onPractise?.let {
                    CtButton(text = "Practise in Opening Battle", icon = Icons.Rounded.Flag, style = CtButtonStyle.Secondary, onClick = it, modifier = Modifier.fillMaxWidth())
                }
                onRelatedPuzzles?.let {
                    CtButton(text = "Related puzzles", icon = Icons.Rounded.Extension, style = CtButtonStyle.Secondary, onClick = it, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(CtSpacing.lg))
            }
        }
    }
}

/** One numbered lesson: a tick when finished, the step to resume at when started. */
@Composable
private fun LessonRow(number: Int, lesson: Lesson, progress: LessonProgress?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val status = when {
        progress?.completed == true -> LessonStatus.DONE
        progress?.started == true -> LessonStatus.STARTED
        else -> LessonStatus.NEW
    }
    val challenges = lesson.quizCount
    val detail = when (status) {
        LessonStatus.DONE -> "Done · tap to play again"
        LessonStatus.STARTED -> "Resume at step ${progress!!.stepIndex + 1} of ${lesson.steps.size}"
        LessonStatus.NEW -> "${lesson.steps.count { it !is LessonStep.Watch }} steps" + if (challenges > 0) " · $challenges challenge${if (challenges == 1) "" else "s"}" else ""
    }
    CtCard(
        onClick = onClick,
        onClickLabel = "Open lesson $number, ${lesson.title}",
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
            NumberBubble(number, status)
            Column(Modifier.weight(1f)) {
                Text(lesson.title, style = CtType.TitleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (lesson.summary.isNotBlank()) Text(lesson.summary, style = CtType.BodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(detail, style = CtType.Caption)
            }
        }
    }
}

@Composable
private fun NumberBubble(number: Int, status: LessonStatus) {
    val background = when (status) {
        LessonStatus.DONE -> CtColors.Lime
        LessonStatus.STARTED -> CtColors.Gold
        LessonStatus.NEW -> CtColors.SurfaceHigh
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CtShapes.Pill)
            .background(background)
            .semantics { contentDescription = if (status == LessonStatus.DONE) "Finished" else "Lesson $number" },
        contentAlignment = Alignment.Center,
    ) {
        when (status) {
            LessonStatus.DONE -> Icon(Icons.Rounded.Check, contentDescription = null, tint = CtColors.OnLime, modifier = Modifier.size(22.dp))
            LessonStatus.STARTED -> Text("$number", style = CtType.Label.copy(color = CtColors.OnGold))
            LessonStatus.NEW -> Text("$number", style = CtType.Label)
        }
    }
}
