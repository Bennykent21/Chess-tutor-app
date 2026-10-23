package com.chesstutor.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtCard
import com.chesstutor.app.design.CtChip
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.domain.bot.RosterBot

/** "Watch two bots" (review R6): pick who plays White and who plays Black, then watch. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WatchSetupScreen(
    draft: WatchDraft,
    bots: List<RosterBot>,
    onChange: ((WatchDraft) -> WatchDraft) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    val white = bots.firstOrNull { it.id == draft.whiteId }
    val black = bots.firstOrNull { it.id == draft.blackId }
    CtScreenBackground {
        Column(Modifier.fillMaxSize()) {
            CtTopBar(title = "Watch two bots", subtitle = "Sit back and see how they play", onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = CtSpacing.gutter),
                verticalArrangement = Arrangement.spacedBy(CtSpacing.md),
            ) {
                CtCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                        Matchup(white, "White")
                        Text("vs", style = CtType.Title)
                        Matchup(black, "Black")
                    }
                }
                CtSectionHeader(title = "White")
                BotChips(bots, draft.whiteId) { id -> onChange { it.copy(whiteId = id) } }
                CtSectionHeader(title = "Black")
                BotChips(bots, draft.blackId) { id -> onChange { it.copy(blackId = id) } }
                Spacer(Modifier.height(CtSpacing.sm))
                CtButton(text = "Watch", icon = Icons.Rounded.PlayArrow, enabled = white != null && black != null, onClick = onStart, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(CtSpacing.lg))
            }
        }
    }
}

@Composable
private fun Matchup(bot: RosterBot?, side: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (bot != null) {
            BotAvatar(bot.profile.face, bot.rating, size = 72.dp)
            Text(bot.name, style = CtType.TitleSmall)
            Text("${bot.rating} · $side", style = CtType.Caption)
        } else {
            Text("Pick a bot", style = CtType.Caption)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BotChips(bots: List<RosterBot>, selectedId: String, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm), verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
        bots.forEach { bot ->
            CtChip(text = "${bot.name} ${bot.rating}", selected = bot.id == selectedId, color = botAccent(bot.rating), onClick = { onPick(bot.id) })
        }
    }
}
