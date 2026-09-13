package com.chesstutor.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.ui.graphics.vector.ImageVector
import com.chesstutor.app.ui.arena.ArenaScreen
import com.chesstutor.app.ui.coach.CoachScreen
import com.chesstutor.app.ui.curriculum.CurriculumScreen
import com.chesstutor.app.ui.review.ReviewScreen
import com.chesstutor.app.ui.theme.ChessTutorColors
import com.chesstutor.app.viewmodel.AppViewModel

sealed class NavTab(val index: Int, val title: String, val icon: ImageVector) {
    object Coach : NavTab(0, "Coach", Icons.Default.Psychology)
    object Curriculum : NavTab(1, "Curriculum", Icons.Default.AutoStories)
    object Arena : NavTab(2, "Arena", Icons.Default.Shield)
    object Review : NavTab(3, "Review", Icons.Default.History)

    companion object {
        val ALL = listOf(Coach, Curriculum, Arena, Review)
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
                contentColor = ChessTutorColors.TextPrimary
            ) {
                NavTab.ALL.forEach { tab ->
                    val selected = state.tab == tab.index
                    NavigationBarItem(
                        selected = selected,
                        onClick = { viewModel.selectTab(tab.index) },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ChessTutorColors.Background,
                            selectedTextColor = ChessTutorColors.Primary,
                            indicatorColor = ChessTutorColors.Primary,
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
    }
}
