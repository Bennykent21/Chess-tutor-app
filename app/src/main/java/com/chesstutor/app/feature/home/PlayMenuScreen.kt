package com.chesstutor.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.chesstutor.app.design.CtGroupLabel
import com.chesstutor.app.design.CtListRow
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.ctEnter

/** Where the Play page leads (D92). */
data class PlayMenuActions(
    val onBots: () -> Unit = {},
    val onCustomGame: () -> Unit = {},
    val onOpeningBattle: () -> Unit = {},
    val onFromPosition: () -> Unit = {},
    val onChess960: () -> Unit = {},
    val onThisPhone: () -> Unit = {},
    val onNearby: () -> Unit = {},
    val onWatch: () -> Unit = {},
    val onExplore: () -> Unit = {},
)

/**
 * Play (D92): every way to play as a plain list in three groups, each opening its own set-up page.
 * Home's Play button leads here; the bots' faces live one step further, on the Bots page.
 */
@Composable
fun PlayMenuScreen(actions: PlayMenuActions, onBack: () -> Unit, modifier: Modifier = Modifier) {
    CtScreenBackground(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            CtTopBar(title = "Play", onBack = onBack)
            Column(Modifier.padding(horizontal = CtSpacing.gutter), verticalArrangement = Arrangement.spacedBy(CtSpacing.xs)) {
                CtGroupLabel("Against the computer")
                CtListRow(title = "Bots", subtitle = "12 characters, 250 to 3000", icon = Icons.Rounded.SmartToy, onClick = actions.onBots, modifier = Modifier.ctEnter(0))
                CtListRow(title = "Custom game", subtitle = "Any strength, side and clock", icon = Icons.Rounded.Tune, onClick = actions.onCustomGame, modifier = Modifier.ctEnter(0))
                CtListRow(title = "Opening battle", subtitle = "Play from an opening you choose", icon = Icons.Rounded.MenuBook, onClick = actions.onOpeningBattle, modifier = Modifier.ctEnter(1))
                CtListRow(title = "From a position", subtitle = "Set up the board, then play", icon = Icons.Rounded.GridOn, onClick = actions.onFromPosition, modifier = Modifier.ctEnter(1))
                CtListRow(title = "Chess960", subtitle = "Pick any of the 960 starts", icon = Icons.Rounded.Shuffle, onClick = actions.onChess960, modifier = Modifier.ctEnter(1))
                CtGroupLabel("Against a friend")
                CtListRow(title = "On this phone", subtitle = "Pass and play, or face to face", icon = Icons.Rounded.PhoneAndroid, onClick = actions.onThisPhone, modifier = Modifier.ctEnter(2))
                CtListRow(title = "Nearby", subtitle = "Two phones on the same Wi-Fi", icon = Icons.Rounded.Wifi, onClick = actions.onNearby, modifier = Modifier.ctEnter(2))
                CtGroupLabel("Watch and study")
                CtListRow(title = "Watch two bots", subtitle = "Pick any pair", icon = Icons.Rounded.Visibility, onClick = actions.onWatch, modifier = Modifier.ctEnter(3))
                CtListRow(title = "Explore", subtitle = "Any position with Stockfish", icon = Icons.Rounded.Search, onClick = actions.onExplore, modifier = Modifier.ctEnter(3))
                Spacer(Modifier.height(CtSpacing.xl))
            }
        }
    }
}
