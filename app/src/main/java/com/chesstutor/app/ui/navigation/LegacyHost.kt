package com.chesstutor.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.LearnCategory
import com.chesstutor.app.navigation.LegacyTab
import com.chesstutor.app.navigation.NavCommand
import com.chesstutor.app.ui.arena.ArenaScreen
import com.chesstutor.app.ui.coach.CoachScreen
import com.chesstutor.app.ui.curriculum.CurriculumScreen
import com.chesstutor.app.ui.puzzles.PuzzleScreen
import com.chesstutor.app.ui.review.ReviewScreen
import com.chesstutor.app.ui.theme.ChessTutorColors
import com.chesstutor.app.viewmodel.AppUiState
import com.chesstutor.app.viewmodel.AppViewModel

/**
 * Hosts the pre-revamp screens inside the new shell, full screen with the bottom bar
 * hidden. They still switch between each other through `AppUiState.tab`, exactly as
 * before, so their behaviour is unchanged. Back leaves the lesson player for the lesson
 * list (as before), and otherwise returns to the new screen you came from.
 */
@Composable
fun LegacyHost(
    state: AppUiState,
    viewModel: AppViewModel,
    initialCategory: LearnCategory?,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val goBack: () -> Unit = {
        val lessonTopicId = state.activeLessonSession?.topicId
        if (state.tab == LegacyTab.SESSION && lessonTopicId != null) {
            viewModel.navigate(NavCommand.OpenLearn(lessonTopicId))
        } else {
            onExit()
        }
    }
    BackHandler(onBack = goBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChessTutorColors.Background)
    ) {
        // The lesson player has its own back button; every other classic screen gets one here.
        if (state.tab != LegacyTab.SESSION) {
            LegacyBackStrip(title = LegacyTab.title(state.tab), onBack = goBack)
        }
        Box(modifier = Modifier.weight(1f)) {
            Crossfade(targetState = state.tab, label = "legacy_crossfade") { currentTab ->
                when (currentTab) {
                    LegacyTab.SESSION -> CoachScreen(state = state, viewModel = viewModel)
                    LegacyTab.CURRICULUM -> CurriculumScreen(
                        state = state,
                        viewModel = viewModel,
                        initialOpenCategory = initialCategory
                    )
                    LegacyTab.ARENA -> ArenaScreen(state = state, viewModel = viewModel)
                    LegacyTab.REVIEW -> ReviewScreen(state = state, viewModel = viewModel)
                    LegacyTab.PUZZLES -> PuzzleScreen(
                        seedRating = state.effectiveUserRating,
                        showCoordinates = state.showCoordinates,
                        showLegalDots = state.showLegalDots
                    )
                }
            }
        }
    }
}

@Composable
private fun LegacyBackStrip(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .ctPressable(onClickLabel = "Go back", onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = ChessTutorColors.TextPrimary
            )
        }
        Text(
            text = title,
            style = CtType.Label.copy(color = ChessTutorColors.TextSecondary),
            maxLines = 1
        )
    }
}
