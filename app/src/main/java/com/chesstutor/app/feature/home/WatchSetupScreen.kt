package com.chesstutor.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chesstutor.app.design.CtActionPage
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtGroupLabel
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.bot.RosterBot

/** "Watch two bots" (review R6, mock-up round 17): the two bots face off; pick each side below. */
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
    CtActionPage(
        title = "Watch",
        onBack = onBack,
        bottom = { CtButton(text = "Watch", enabled = white != null && black != null, onClick = onStart, modifier = Modifier.weight(1f)) },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = CtSpacing.sm),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Matchup(white, "White")
            Text("vs", style = CtType.Headline.copy(color = CtColors.Text2))
            Matchup(black, "Black")
        }
        CtGroupLabel("White")
        BotPickerRow(bots, draft.whiteId) { id -> onChange { it.copy(whiteId = id) } }
        CtGroupLabel("Black")
        BotPickerRow(bots, draft.blackId) { id -> onChange { it.copy(blackId = id) } }
    }
}

@Composable
private fun Matchup(bot: RosterBot?, side: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(CtSpacing.xxs)) {
        if (bot != null) {
            BotAvatar(bot.profile.face, bot.rating, size = 96.dp)
            Text(bot.name, style = CtType.TitleSmall, modifier = Modifier.padding(top = CtSpacing.xs))
            Text("${bot.rating} · $side", style = CtType.Caption)
        } else {
            Text("Pick a bot", style = CtType.Caption)
        }
    }
}

/** The bots as a sideways row of faces; the chosen one is ringed (Watch, From a position, Opening battle). */
@Composable
internal fun BotPickerRow(bots: List<RosterBot>, selectedId: String?, onPick: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(CtSpacing.md), contentPadding = PaddingValues(vertical = CtSpacing.xs)) {
        items(bots, key = { it.id }) { bot ->
            val chosen = bot.id == selectedId
            Box(
                modifier = Modifier
                    .ctPressable(onClickLabel = "Choose ${bot.name}", onClick = { onPick(bot.id) })
                    .semantics {
                        role = Role.RadioButton
                        selected = chosen
                        contentDescription = "${bot.name}, ${bot.rating}"
                    },
            ) {
                BotAvatar(bot.profile.face, bot.rating, size = 56.dp, accent = if (chosen) CtColors.Accent else CtColors.Line)
            }
        }
    }
}
