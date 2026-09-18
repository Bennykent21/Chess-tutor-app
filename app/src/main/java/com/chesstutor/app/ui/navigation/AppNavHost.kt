package com.chesstutor.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chesstutor.app.ui.arena.ArenaScreen
import com.chesstutor.app.ui.components.PromotionDialog
import com.chesstutor.app.ui.coach.CoachScreen
import com.chesstutor.app.ui.curriculum.CurriculumScreen
import com.chesstutor.app.ui.review.ReviewScreen
import com.chesstutor.app.ui.settings.SettingsSheet
import com.chesstutor.app.ui.theme.ChessTutorColors
import com.chesstutor.app.ui.theme.bouncyClickable
import com.chesstutor.app.viewmodel.AppViewModel

sealed class NavTab(val index: Int, val title: String, val icon: ImageVector) {
    object Train : NavTab(0, "Train", Icons.Default.TrackChanges)
    object Learn : NavTab(1, "Learn", Icons.Default.AutoStories)
    object Play : NavTab(2, "Play", Icons.Default.SportsEsports)
    object Review : NavTab(3, "Review", Icons.Default.History)

    companion object {
        val ALL = listOf(Train, Learn, Play, Review)
    }
}

@Composable
fun AppNavHost(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ChessTutorColors.Background,
        bottomBar = {
            // Mockup Navigation Bar (.nav)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ChessTutorColors.Surface)
                    .border(
                        width = 1.dp,
                        color = ChessTutorColors.Line,
                        shape = androidx.compose.ui.graphics.RectangleShape
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NavTab.ALL.forEach { tab ->
                        val isSelected = state.tab == tab.index

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .bouncyClickable { viewModel.selectTab(tab.index) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                tint = if (isSelected) ChessTutorColors.Brass else ChessTutorColors.TextTertiary,
                                modifier = Modifier.size(21.dp)
                            )
                            Text(
                                text = tab.title,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = (-0.005).sp,
                                color = if (isSelected) ChessTutorColors.Brass else ChessTutorColors.TextTertiary,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(ChessTutorColors.Background)
        ) {
            when (state.tab) {
                0 -> CoachScreen(state = state, viewModel = viewModel)
                1 -> CurriculumScreen(state = state, viewModel = viewModel)
                2 -> ArenaScreen(state = state, viewModel = viewModel)
                3 -> ReviewScreen(state = state, viewModel = viewModel)
            }
        }

        if (state.isSettingsVisible) {
            SettingsSheet(
                state = state,
                viewModel = viewModel,
                onDismiss = { viewModel.setSettingsVisible(false) }
            )
        }

        state.pendingPromotion?.let { request ->
            PromotionDialog(
                request = request,
                onChoose = viewModel::choosePromotion,
                onDismiss = viewModel::cancelPromotion
            )
        }
    }
}
