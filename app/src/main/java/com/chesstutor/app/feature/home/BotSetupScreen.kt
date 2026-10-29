package com.chesstutor.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.chesstutor.app.design.CtActionPage
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtConfirmDialog
import com.chesstutor.app.design.CtGroupLabel
import com.chesstutor.app.design.CtMenuItem
import com.chesstutor.app.design.CtOverflowMenu
import com.chesstutor.app.design.CtSegmented
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtSpeechBubble
import com.chesstutor.app.design.CtTail
import com.chesstutor.app.design.CtToggleRow
import com.chesstutor.app.design.CtType
import com.chesstutor.app.domain.bot.RosterBot
import com.chesstutor.app.domain.game.GameRules
import com.chesstutor.app.domain.game.SideChoice
import com.chesstutor.app.domain.game.TimeControl

/**
 * A bot's page (plan 6.1, mock-up round 16): the bot, its line in its own voice, the game's
 * settings and the openings it plays, with Play fixed at the bottom. The player's own bots
 * keep Edit and Delete in the menu.
 */
@Composable
fun BotSetupScreen(
    bot: RosterBot,
    onPlay: (SideChoice, GameRules) -> Unit,
    onBack: () -> Unit,
    initialRules: GameRules = GameRules(),
    /** Set for the player's own bots. */
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var side by rememberSaveable { mutableStateOf(SideChoice.RANDOM) }
    var time by rememberSaveable { mutableStateOf(initialRules.timeControl) }
    var takebacks by rememberSaveable { mutableStateOf(initialRules.takebacks) }
    var hints by rememberSaveable { mutableStateOf(initialRules.hints) }
    var evalBar by rememberSaveable { mutableStateOf(initialRules.evalBar) }
    val menu = listOfNotNull(
        onEdit?.let { CtMenuItem("Edit bot", onClick = it) },
        onDelete?.let { CtMenuItem("Delete bot", destructive = true) { confirmDelete = true } },
    )

    CtActionPage(
        title = "",
        onBack = onBack,
        actions = if (menu.isEmpty()) null else { { CtOverflowMenu(menu) } },
        bottom = {
            CtButton(text = "Play", onClick = { onPlay(side, GameRules(time, takebacks, hints, evalBar)) }, modifier = Modifier.weight(1f))
        },
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            BotAvatar(bot.profile.face, bot.rating, size = 104.dp)
            Text(bot.name, style = CtType.Display, modifier = Modifier.padding(top = CtSpacing.sm))
            Text("${bot.rating} · ${bot.profile.style.label}", style = CtType.BodySmall)
            CtSpeechBubble(tail = CtTail.Top, modifier = Modifier.padding(top = CtSpacing.md)) {
                Text(bot.tagline, style = CtType.Chat.copy(color = CtColors.Text), textAlign = TextAlign.Center)
            }
        }
        CtGroupLabel("You play")
        CtSegmented(options = SideChoice.entries, selected = side, label = { it.label }, onSelect = { side = it })
        CtGroupLabel("Clock")
        CtSegmented(options = TimeControl.entries, selected = time, label = ::clockLabel, onSelect = { time = it })
        Column(Modifier.padding(top = CtSpacing.sm)) {
            CtToggleRow(title = "Takebacks", checked = takebacks, onCheckedChange = { takebacks = it })
            CtToggleRow(title = "Hints", checked = hints, onCheckedChange = { hints = it })
            CtToggleRow(title = "Evaluation bar", checked = evalBar, onCheckedChange = { evalBar = it }, divider = false)
        }
        Repertoire(bot)
    }

    if (confirmDelete && onDelete != null) {
        CtConfirmDialog(
            title = "Delete ${bot.name}?",
            text = "Games you played against it stay in your games list.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** "None" fits a four-way control better than "No clock". */
internal fun clockLabel(time: TimeControl): String = if (time == TimeControl.NONE) "None" else time.label

/** The openings the bot plays, one short line a side; nothing when it plays without a book. */
@Composable
private fun Repertoire(bot: RosterBot) {
    val white = bot.profile.whiteOpenings
    val black = bot.profile.blackOpenings
    if (white.isEmpty() && black.isEmpty()) return
    CtGroupLabel("Openings")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("White" to white, "Black" to black).filter { it.second.isNotEmpty() }.forEach { (side, names) ->
            Row {
                Text(side, style = CtType.BodySmall, modifier = Modifier.width(56.dp))
                Text(names.joinToString(), style = CtType.BodySmall.copy(color = CtColors.Text), modifier = Modifier.weight(1f))
            }
        }
    }
}
