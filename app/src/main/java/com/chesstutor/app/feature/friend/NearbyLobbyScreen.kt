package com.chesstutor.app.feature.friend

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chesstutor.app.design.CtActionPage
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTextField
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctBreathe
import com.chesstutor.app.domain.game.NearbyProtocol

/**
 * Nearby (Phase 11C): the host's code while they wait, or the joiner typing it in. The game
 * opens by itself once the two phones find each other.
 */
@Composable
fun NearbyLobbyScreen(
    status: NearbyStatus,
    joining: Boolean,
    onJoin: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var typed by rememberSaveable { mutableStateOf("") }
    val ready = NearbyProtocol.cleanCode(typed).length == NearbyProtocol.CODE_LENGTH || ':' in typed
    CtActionPage(
        title = "",
        onBack = onCancel,
        bottom = {
            when {
                status is NearbyStatus.Hosting || status is NearbyStatus.Joining ->
                    CtButton(text = "Cancel", style = CtButtonStyle.Secondary, onClick = onCancel, modifier = Modifier.weight(1f))
                joining -> CtButton(text = "Join", enabled = ready, onClick = { onJoin(typed) }, modifier = Modifier.weight(1f))
                else -> CtButton(text = "Back", style = CtButtonStyle.Secondary, onClick = onCancel, modifier = Modifier.weight(1f))
            }
        },
    ) {
        Column(
            Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(CtSpacing.md),
        ) {
            when (status) {
                is NearbyStatus.Hosting -> {
                    Text("Your code", style = CtType.Display)
                    CodeBoxes(status.code)
                    Text(
                        "On the same Wi-Fi or hotspot, your friend opens Play a friend, taps Nearby, then Join, and types this code.",
                        style = CtType.Body.copy(color = CtColors.Text2),
                    )
                    status.address?.let { Text("Code not found? They can type $it instead.", style = CtType.Caption.copy(color = CtColors.Text2)) }
                }
                is NearbyStatus.Joining -> {
                    Text("Looking for ${status.target}", style = CtType.Display)
                    Text("Stay on the same Wi-Fi or hotspot as your friend.", style = CtType.Body.copy(color = CtColors.Text2))
                }
                is NearbyStatus.Connected -> Text("Playing ${status.friendName}", style = CtType.Display)
                is NearbyStatus.Failed, NearbyStatus.Idle -> if (joining) {
                    Text("Join a game", style = CtType.Display)
                    CtTextField(
                        value = typed,
                        onValueChange = { typed = if (':' in it || '.' in it) it.take(MAX_ADDRESS) else NearbyProtocol.cleanCode(it) },
                        label = "Your friend's code",
                        placeholder = "ABCD",
                    )
                    (status as? NearbyStatus.Failed)?.let { Text(it.message, style = CtType.BodySmall.copy(color = CtColors.Error)) }
                } else {
                    Text("Not connected", style = CtType.Display)
                    (status as? NearbyStatus.Failed)?.let { Text(it.message, style = CtType.Body.copy(color = CtColors.Error)) }
                }
            }
        }
    }
}

/** The four letters, each in its own box, breathing gently while the host waits. */
@Composable
private fun CodeBoxes(code: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = CtSpacing.md).semantics { contentDescription = "Code ${code.toList().joinToString(" ")}" },
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm, Alignment.CenterHorizontally),
    ) {
        code.forEach { letter ->
            Box(
                Modifier.size(64.dp).ctBreathe(to = 1.04f, periodMs = 1600).clip(CtShapes.Control).background(CtColors.Surface),
                contentAlignment = Alignment.Center,
            ) {
                Text("$letter", style = CtType.BigNumber)
            }
        }
    }
}

private const val MAX_ADDRESS = 21
