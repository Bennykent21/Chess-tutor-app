package com.chesstutor.app.feature.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chesstutor.app.data.update.UpdateCenter
import com.chesstutor.app.data.update.UpdateState
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtProgressBar
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType
import com.chesstutor.app.domain.update.AppUpdates

/** The centred prompt for an update (D88), over whatever screen is open; What's new opens the full notes (D90). */
@Composable
fun UpdatePrompt(center: UpdateCenter, onWhatsNew: () -> Unit) {
    val shown by center.prompt.collectAsStateWithLifecycle()
    val state by center.state.collectAsStateWithLifecycle()
    if (!shown) return
    val context = LocalContext.current
    UpdateDialog(
        state = state,
        onUpdate = { center.update(context) },
        onAllow = { center.openInstallPermission(context) },
        onLater = center::later,
        onHide = center::hide,
        onWhatsNew = {
            center.seen()
            onWhatsNew()
        },
    )
}

/** What the prompt says at each stage; separate from [UpdateCenter] so tests can show every stage. */
@Composable
fun UpdateDialog(state: UpdateState, onUpdate: () -> Unit, onAllow: () -> Unit, onLater: () -> Unit, onHide: () -> Unit, onWhatsNew: () -> Unit) {
    val title = when (state) {
        is UpdateState.Available -> "Chess Tutor ${state.release.version}"
        is UpdateState.Downloading -> "Chess Tutor ${state.release.version}"
        is UpdateState.Ready -> "Chess Tutor ${state.release.version}"
        is UpdateState.NeedsPermission -> "Allow updates"
        is UpdateState.Failed -> "Update"
        UpdateState.UpToDate -> "Up to date"
        else -> return
    }
    AlertDialog(
        onDismissRequest = if (state is UpdateState.Downloading) onHide else onLater,
        containerColor = CtColors.Raised,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title, style = CtType.Headline) },
        text = {
            when (state) {
                is UpdateState.Available -> Text("A new version is ready. ${AppUpdates.megabytes(state.release.apkBytes)}.", style = CtType.Body)
                is UpdateState.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                    CtProgressBar(progress = state.progress)
                    Text("${AppUpdates.megabytes(state.done)} of ${AppUpdates.megabytes(state.total)}", style = CtType.Caption)
                }
                is UpdateState.Ready -> Text("Downloaded. Android will ask you to confirm.", style = CtType.Body)
                is UpdateState.NeedsPermission -> Text("Turn on \"Allow from this source\" for Chess Tutor, come back, then tap Install.", style = CtType.Body)
                is UpdateState.Failed -> Text(state.message, style = CtType.Body)
                UpdateState.UpToDate -> Text("You have the newest version.", style = CtType.Body)
                else -> Unit
            }
        },
        confirmButton = {
            when (state) {
                is UpdateState.Available -> Action("Update", onUpdate)
                is UpdateState.Ready -> Action("Install", onUpdate)
                is UpdateState.NeedsPermission -> Action("Install", onUpdate)
                is UpdateState.Failed -> Action("Try again", onUpdate)
                is UpdateState.Downloading -> Action("Hide", onHide)
                else -> Action("OK", onLater)
            }
        },
        dismissButton = {
            when (state) {
                is UpdateState.NeedsPermission -> Action("Open settings", onAllow)
                // Tapping outside is Later; the notes are one tap away.
                is UpdateState.Available -> Action("What's new", onWhatsNew)
                is UpdateState.Ready, is UpdateState.Failed -> Action("Later", onLater)
                else -> Unit
            }
        },
    )
}

@Composable
private fun Action(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(label, style = CtType.Button.copy(color = CtColors.Text)) }
}
