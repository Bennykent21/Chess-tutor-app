package com.chesstutor.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtListRow
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.domain.bot.CustomBot
import com.chesstutor.app.domain.bot.RosterBot

/**
 * Bots (D42): every bot to play, your own bots, and buttons to build a new one or watch two
 * play each other. Reached from Home's "More ways to play".
 */
@Composable
fun BotsScreen(
    roster: List<RosterBot>,
    myBots: List<CustomBot>,
    onOpenBot: (String) -> Unit,
    onNewBot: () -> Unit,
    onWatch: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CtScreenBackground(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            CtTopBar(title = "Bots", onBack = onBack)
            Column(Modifier.padding(horizontal = CtSpacing.gutter)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CtButton(text = "Build a bot", icon = Icons.Rounded.Add, onClick = onNewBot, modifier = Modifier.weight(1f))
                    CtButton(text = "Watch two", icon = Icons.Rounded.Visibility, style = CtButtonStyle.Secondary, onClick = onWatch, modifier = Modifier.weight(1f))
                }
                if (myBots.isNotEmpty()) {
                    CtSectionHeader(title = "My bots")
                    myBots.map { it.asRosterBot() }.forEachIndexed { i, bot -> BotRow(bot, last = i == myBots.lastIndex, onOpenBot) }
                }
                CtSectionHeader(title = "All bots")
                roster.forEachIndexed { i, bot -> BotRow(bot, last = i == roster.lastIndex, onOpenBot) }
                Spacer(Modifier.height(CtSpacing.xl))
            }
        }
    }
}

/** A bot: its face, name, rating and style; opens its page (pick colour and clock, then play). */
@Composable
private fun BotRow(bot: RosterBot, last: Boolean, onOpenBot: (String) -> Unit) {
    CtListRow(
        title = bot.name,
        subtitle = "${bot.rating}, ${bot.profile.style.label}",
        icon = null,
        onClick = { onOpenBot(bot.id) },
        divider = !last,
        leading = { BotAvatar(bot.profile.face, bot.rating, size = 40.dp) },
    )
}
