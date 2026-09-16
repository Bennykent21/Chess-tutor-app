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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.chesstutor.app.ui.theme.ChessTutorTypography
import com.chesstutor.app.viewmodel.AppViewModel

sealed class NavTab(val index: Int, val title: String, val icon: ImageVector) {
    object Coach : NavTab(0, "Coach", Icons.Default.Psychology)
    object Lessons : NavTab(1, "Lessons", Icons.Default.AutoStories)
    object PlayBots : NavTab(2, "Play Bots", Icons.Default.SmartToy)
    object Review : NavTab(3, "Review", Icons.Default.CheckCircle)

    companion object {
        val ALL = listOf(Coach, Lessons, PlayBots, Review)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavHost(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ChessTutorColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ChessTutorColors.Primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Chess.com Tutor",
                            style = ChessTutorTypography.titleLarge,
                            color = Color.White
                        )
                    }
                },
                actions = {
                    // Rating Pill
                    val ratingStr = state.linkedProfile?.activeRating?.toString() ?: "1500"
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF312E2B))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = ChessTutorColors.Primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = ratingStr,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(onClick = { viewModel.setSettingsVisible(true) }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF21201D)
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF21201D),
                contentColor = Color.White
            ) {
                NavTab.ALL.forEach { tab ->
                    val selected = state.tab == tab.index
                    NavigationBarItem(
                        selected = selected,
                        onClick = { viewModel.selectTab(tab.index) },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
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

        if (state.isSettingsVisible) {
            SettingsSheet(
                state = state,
                viewModel = viewModel,
                onDismiss = { viewModel.setSettingsVisible(false) }
            )
        }
    }
}
