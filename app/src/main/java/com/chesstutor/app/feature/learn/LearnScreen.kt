package com.chesstutor.app.feature.learn

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.chesstutor.app.board.BoardTheme
import com.chesstutor.app.board.BoardView
import com.chesstutor.app.board.PieceArt
import com.chesstutor.app.core.Piece
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.PieceType
import com.chesstutor.app.core.Position
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtDivider
import com.chesstutor.app.design.CtHeroCard
import com.chesstutor.app.design.CtProgressBar
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtScreenTitle
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.learn.LessonCategory

/** What the Learn hub can open. */
data class LearnHubActions(
    val onOpenCategory: (LessonCategory) -> Unit,
    val onOpenLesson: (lessonId: String) -> Unit,
    /** The opening trainer (plan 6C). */
    val onOpenTrainer: () -> Unit = {},
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
    /** Opening trainer lines due again, for its row. */
    trainerDue: Int = 0,
    /** The player's board and pieces, for the continue card's picture and the topic icons. */
    boardTheme: BoardTheme = BoardTheme(),
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
                    CtHeroCard(
                        kicker = course.title,
                        title = lesson.title,
                        detail = "Lesson $number of ${course.lessons.size}",
                        progress = courseDone.toFloat() / course.lessons.size.coerceAtLeast(1),
                        onClick = { actions.onOpenLesson(lesson.id) },
                        onClickLabel = (if (started) "Continue " else "Start ") + lesson.title,
                    ) {
                        val position = lesson.steps.firstOrNull()?.fen?.let { Position.tryFromFen(it).getOrNull() } ?: Position.initial()
                        BoardView(position = position, theme = boardTheme, showCoordinates = false, showLegalDots = false)
                    }
                }

                CtSectionHeader(title = "Topics", modifier = Modifier.padding(top = CtSpacing.md))
                tiles.forEachIndexed { i, tile ->
                    TopicRow(tile, boardTheme, last = i == tiles.lastIndex, onClick = { actions.onOpenCategory(tile.category) })
                }

                Spacer(Modifier.height(CtSpacing.md))
                TrainerRow(due = trainerDue, onClick = actions.onOpenTrainer)
            }
        }
    }
}

/** The piece that stands for a topic (canvas `Learn.dc.html`), drawn in the player's own set. */
internal fun categoryPiece(category: LessonCategory): PieceType = when (category) {
    LessonCategory.BASICS -> PieceType.PAWN
    LessonCategory.OPENINGS -> PieceType.KNIGHT
    LessonCategory.TACTICS -> PieceType.BISHOP
    LessonCategory.MIDDLEGAME -> PieceType.ROOK
    LessonCategory.ENDGAMES -> PieceType.KING
    LessonCategory.BLUNDERS -> PieceType.QUEEN
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

/** A topic: its piece, its name, "3 of 12" and a thin bar; topics without lessons yet do not open. */
@Composable
private fun TopicRow(tile: LearnHubTile, boardTheme: BoardTheme, last: Boolean, onClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
                .then(if (tile.isAvailable) Modifier.ctPressable(onClickLabel = "Open ${tile.title}", onClick = onClick) else Modifier)
                .padding(vertical = CtSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(40.dp).clip(CtShapes.Pill).background(CtColors.Surface), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(PieceArt.drawable(boardTheme.pieceSet, Piece(categoryPiece(tile.category), PieceColor.WHITE))),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tile.title, style = CtType.TitleSmall, modifier = Modifier.weight(1f))
                    Text(
                        if (tile.isAvailable) "${tile.completedLessons} of ${tile.totalLessons}" else "Lessons on the way",
                        style = CtType.Caption,
                    )
                }
                if (tile.isAvailable) CtProgressBar(progress = tile.progress, height = 3.dp)
            }
            if (tile.isAvailable) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = CtColors.Text2)
        }
        if (!last) CtDivider()
    }
}

/** The opening trainer, with how many lines are due again. */
@Composable
private fun TrainerRow(due: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(CtShapes.CardSmall)
            .background(CtColors.Surface)
            .ctPressable(onClickLabel = "Open the opening trainer", onClick = onClick)
            .padding(horizontal = CtSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(Icons.Rounded.Flag, contentDescription = null, tint = CtColors.Accent, modifier = Modifier.size(22.dp))
        Text("Opening trainer", style = CtType.TitleSmall, modifier = Modifier.weight(1f))
        if (due > 0) {
            Text(
                text = "$due due",
                style = CtType.Label.copy(color = CtColors.OnPrimary),
                modifier = Modifier.clip(CtShapes.Pill).background(CtColors.Primary).padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}
