package com.chesstutor.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chesstutor.app.ui.arena.ArenaScreen
import com.chesstutor.app.ui.coach.CoachScreen
import com.chesstutor.app.ui.curriculum.CurriculumScreen
import com.chesstutor.app.ui.review.ReviewScreen
import com.chesstutor.app.ui.settings.SettingsSheet
import com.chesstutor.app.ui.theme.ChessTutorColors
import com.chesstutor.app.viewmodel.AppViewModel

sealed class NavTab(val index: Int, val title: String, val icon: ImageVector) {
    object Train : NavTab(0, "Train", Icons.Default.FitnessCenter)
    object Learn : NavTab(1, "Learn", Icons.Default.MenuBook)
    object Play : NavTab(2, "Play", Icons.Default.SmartToy)
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
            NavigationBar(
                containerColor = ChessTutorColors.Surface,
                contentColor = ChessTutorColors.TextPrimary,
                tonalElevation = 0.dp
            ) {
                NavTab.ALL.forEach { tab ->
                    val selected = state.tab == tab.index
                    NavigationBarItem(
                        selected = selected,
                        onClick = { viewModel.selectTab(tab.index) },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = {
                            Text(
                                text = tab.title,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ChessTutorColors.Background,
                            selectedTextColor = ChessTutorColors.Accent,
                            indicatorColor = ChessTutorColors.Accent,
                            unselectedIconColor = ChessTutorColors.TextSecondary,
                            unselectedTextColor = ChessTutorColors.TextSecondary
                        )
                    )
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
    }
}
