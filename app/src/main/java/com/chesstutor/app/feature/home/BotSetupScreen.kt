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
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtCard
import com.chesstutor.app.design.CtChip
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtToggleRow
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.domain.bot.RosterBot
import com.chesstutor.app.domain.game.GameRules
import com.chesstutor.app.domain.game.SideChoice
import com.chesstutor.app.domain.game.TimeControl

/** A bot's page (plan 6.1): who it is, the openings it plays, and the game's settings. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BotSetupScreen(
    bot: RosterBot,
    onPlay: (SideChoice, GameRules) -> Unit,
    onBack: () -> Unit,
    initialRules: GameRules = GameRules(),
    /** Set for the player's own bots. */
    onEdit: (() -> Unit)? = null,
) {
    var side by rememberSaveable { mutableStateOf(SideChoice.RANDOM) }
    var time by rememberSaveable { mutableStateOf(initialRules.timeControl) }
    var takebacks by rememberSaveable { mutableStateOf(initialRules.takebacks) }
    var hints by rememberSaveable { mutableStateOf(initialRules.hints) }
    var evalBar by rememberSaveable { mutableStateOf(initialRules.evalBar) }

    CtScreenBackground {
        Column(Modifier.fillMaxSize()) {
            CtTopBar(title = bot.name, subtitle = "Rated ${bot.rating}", onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = CtSpacing.gutter),
                verticalArrangement = Arrangement.spacedBy(CtSpacing.md),
            ) {
                CtCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        BotAvatar(bot.profile.face, bot.rating, size = 120.dp)
                        Spacer(Modifier.height(CtSpacing.sm))
                        Text(bot.name, style = CtType.Headline)
                        Spacer(Modifier.height(CtSpacing.xs))
                        Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                            CtChip(text = "${bot.rating}", color = botAccent(bot.rating))
                            CtChip(text = bot.profile.style.label, color = CtColors.Violet)
                        }
                        Spacer(Modifier.height(CtSpacing.sm))
                        Text(bot.tagline, style = CtType.Body, textAlign = TextAlign.Center)
                    }
                }
                Repertoire(bot)
                if (onEdit != null) {
                    CtButton(text = "Edit ${bot.name}", icon = Icons.Rounded.Edit, style = CtButtonStyle.Secondary, onClick = onEdit, modifier = Modifier.fillMaxWidth())
                }

                CtSectionHeader(title = "You play")
                Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                    SideChoice.entries.forEach { choice ->
                        CtChip(text = choice.label, selected = side == choice, color = CtColors.Gold, onClick = { side = choice })
                    }
                }
                CtSectionHeader(title = "Clock")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm), verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                    TimeControl.entries.forEach { control ->
                        CtChip(text = control.label, selected = time == control, color = CtColors.Teal, onClick = { time = control })
                    }
                }
                CtSectionHeader(title = "Rules")
                CtToggleRow(title = "Takebacks", subtitle = "Undo your last move", checked = takebacks, onCheckedChange = { takebacks = it })
                CtToggleRow(title = "Hints", subtitle = "Show the engine's move when you are stuck", checked = hints, onCheckedChange = { hints = it })
                CtToggleRow(title = "Evaluation bar", subtitle = "Who is better, as you play", checked = evalBar, onCheckedChange = { evalBar = it })
                Spacer(Modifier.height(CtSpacing.sm))
                CtButton(
                    text = "Play ${bot.name}",
                    icon = Icons.Rounded.PlayArrow,
                    onClick = { onPlay(side, GameRules(time, takebacks, hints, evalBar)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(CtSpacing.lg))
            }
        }
    }
}

@Composable
private fun Repertoire(bot: RosterBot) {
    val white = bot.profile.whiteOpenings
    val black = bot.profile.blackOpenings
    CtCard(modifier = Modifier.fillMaxWidth()) {
        Text("Openings", style = CtType.TitleSmall)
        Spacer(Modifier.height(CtSpacing.xs))
        if (white.isEmpty() && black.isEmpty()) {
            Text("No book: it works everything out over the board.", style = CtType.BodySmall)
        } else {
            Text("As White: ${white.joinToString()}", style = CtType.BodySmall)
            Text("As Black: ${black.joinToString()}", style = CtType.BodySmall)
        }
    }
}
