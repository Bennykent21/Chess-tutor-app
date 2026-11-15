package com.chesstutor.app.feature.learn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.chesstutor.app.design.CtArtPlayMark
import com.chesstutor.app.design.CtArtTile
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtScene
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtScreenTitle
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType
import com.chesstutor.app.domain.learn.LessonCategory

/** What the Learn hub can open. */
data class LearnHubActions(
    val onOpenCategory: (LessonCategory) -> Unit,
    val onOpenLesson: (lessonId: String) -> Unit,
)

/**
 * The Learn hub (plan 6.2, Phase 7D, canvas `Learn.dc.html`): how far you are, the lesson to
 * continue with a picture of its board, the topics with their progress, and the opening trainer.
 */
@Composable
fun LearnScreen(
    learn: LearnState,
    actions: LearnHubActions,
    modifier: Modifier = Modifier,
) {
    val tiles = remember(learn) { LearnHubModel.tiles(learn.library, learn.progress) }
    val next = remember(learn) { LearnHubModel.continueLesson(learn.library, learn.progress) }
    val done = tiles.sumOf { it.completedLessons }
    val total = tiles.sumOf { it.totalLessons }

    CtScreenBackground(modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = CtSpacing.xl),
        ) {
            CtScreenTitle(title = "Learn")
            Text(
                text = "$done of $total lessons",
                style = CtType.BodySmall,
                modifier = Modifier.padding(horizontal = CtSpacing.gutter),
            )
            Column(Modifier.padding(horizontal = CtSpacing.gutter)) {
                next?.let { (course, lesson) ->
                    Spacer(Modifier.height(CtSpacing.lg))
                    val number = course.lessons.indexOf(lesson) + 1
                    val courseDone = course.lessons.count { LearnHubModel.status(it, learn.progress) == LessonStatus.DONE }
                    val started = LearnHubModel.status(lesson, learn.progress) == LessonStatus.STARTED
                    CtArtTile(
                        title = lesson.title,
                        scene = categoryScene(course.category),
                        modifier = Modifier.fillMaxWidth().height(196.dp),
                        kicker = course.title,
                        subtitle = "Lesson $number of ${course.lessons.size}",
                        progress = courseDone.toFloat() / course.lessons.size.coerceAtLeast(1),
                        titleStyle = CtType.Headline,
                        onClick = { actions.onOpenLesson(lesson.id) },
                        onClickLabel = (if (started) "Continue " else "Start ") + lesson.title,
                        lift = 0.3f,
                        trailing = { CtArtPlayMark() },
                    )
                }

                CtSectionHeader(title = "Topics", modifier = Modifier.padding(top = CtSpacing.md))
                Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
                    tiles.chunked(2).forEach { pair ->
                        Row(Modifier.height(124.dp), horizontalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
                            pair.forEach { tile ->
                                TopicTile(tile, Modifier.weight(1f).fillMaxHeight(), onClick = { actions.onOpenCategory(tile.category) })
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/** Icon and accent colour of a category, for the category page. */
@Composable
@ReadOnlyComposable
internal fun categoryStyle(category: LessonCategory): Pair<ImageVector, Color> = when (category) {
    LessonCategory.BASICS -> Icons.Rounded.School to CtColors.Accent
    LessonCategory.OPENINGS -> Icons.Rounded.Flag to CtColors.Accent
    LessonCategory.TACTICS -> Icons.Rounded.Bolt to CtColors.Accent
    LessonCategory.MIDDLEGAME -> Icons.Rounded.Psychology to CtColors.Accent
    LessonCategory.ENDGAMES -> Icons.Rounded.AutoStories to CtColors.Accent
    LessonCategory.BLUNDERS -> Icons.Rounded.Shield to CtColors.Accent
}

/** The scene that tells a topic's idea (D56). */
internal fun categoryScene(category: LessonCategory): CtScene = when (category) {
    LessonCategory.BASICS -> CtScene.BASICS
    LessonCategory.OPENINGS -> CtScene.OPENINGS
    LessonCategory.TACTICS -> CtScene.TACTICS
    LessonCategory.MIDDLEGAME -> CtScene.MIDDLEGAME
    LessonCategory.ENDGAMES -> CtScene.ENDGAMES
    LessonCategory.BLUNDERS -> CtScene.BLUNDERS
}

/** A topic on its scene, with "3 of 12" and a thin bar; topics without lessons yet do not open. */
@Composable
private fun TopicTile(tile: LearnHubTile, modifier: Modifier, onClick: () -> Unit) {
    CtArtTile(
        title = tile.title,
        scene = categoryScene(tile.category),
        modifier = modifier,
        subtitle = if (tile.isAvailable) "${tile.completedLessons} of ${tile.totalLessons}" else "Lessons on the way",
        progress = tile.progress.takeIf { tile.isAvailable },
        onClick = onClick.takeIf { tile.isAvailable },
        onClickLabel = "Open ${tile.title}",
    )
}
