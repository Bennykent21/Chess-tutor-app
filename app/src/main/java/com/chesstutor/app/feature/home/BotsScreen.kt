package com.chesstutor.app.feature.home

import com.chesstutor.app.design.ctEnter
import com.chesstutor.app.design.CtFade
import com.chesstutor.app.design.CtPictureTile
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.domain.bot.CustomBot
import com.chesstutor.app.domain.bot.RosterBot

/**
 * Play (D42): a friend at this phone, every bot, your own bots, and buttons to build a new one
 * or watch two play each other. Reached from Home's Play button.
 */
@Composable
fun BotsScreen(
    roster: List<RosterBot>,
    myBots: List<CustomBot>,
    onOpenBot: (String) -> Unit,
    onNewBot: () -> Unit,
    onWatch: () -> Unit,
    onPlayFriend: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CtScreenBackground(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            CtTopBar(title = "Play", onBack = onBack)
            Column(Modifier.padding(horizontal = CtSpacing.gutter)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CtButton(text = "Build a bot", icon = Icons.Rounded.Add, onClick = onNewBot, modifier = Modifier.weight(1f))
                    CtButton(text = "Watch two", icon = Icons.Rounded.Visibility, style = CtButtonStyle.Secondary, onClick = onWatch, modifier = Modifier.weight(1f))
                }
                CtButton(
                    text = "Play a friend",
                    icon = Icons.Rounded.Group,
                    style = CtButtonStyle.Secondary,
                    onClick = onPlayFriend,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
                if (myBots.isNotEmpty()) {
                    CtSectionHeader(title = "My bots")
                    BotGrid(myBots.map { it.asRosterBot() }, onOpenBot)
                }
                CtSectionHeader(title = "All bots")
                BotGrid(roster, onOpenBot)
                Spacer(Modifier.height(CtSpacing.xl))
            }
        }
    }
}

/** Two bots to a row, each face filling its card; a card opens that bot's page. */
@Composable
private fun BotGrid(bots: List<RosterBot>, onOpenBot: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
        bots.chunked(2).forEachIndexed { row, pair ->
            Row(Modifier.ctEnter(row), horizontalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
                pair.forEach { bot ->
                    CtPictureTile(
                        title = bot.name,
                        subtitle = "${bot.rating}, ${bot.profile.style.label}",
                        modifier = Modifier.weight(1f).height(196.dp),
                        fade = CtFade.Soft,
                        onClick = { onOpenBot(bot.id) },
                    ) { BotPicture(bot.profile.face) }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
