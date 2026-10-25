package com.chesstutor.app.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chesstutor.app.data.model.ImportDepth
import com.chesstutor.app.data.model.ImportProgress
import com.chesstutor.app.data.model.LinkedChessProfile
import com.chesstutor.app.data.model.RatingPlatform
import com.chesstutor.app.data.model.RatingTimeControl
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtCard
import com.chesstutor.app.design.CtChip
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtConfirmDialog
import com.chesstutor.app.design.CtIconBubble
import com.chesstutor.app.design.CtProgressBar
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTextField
import com.chesstutor.app.design.CtType
import com.chesstutor.app.viewmodel.AppUiState

/** The Account page shows the link card only until an account is linked (or while its first import runs). */
internal fun showsLinkCard(state: AppUiState): Boolean = state.linkedProfile == null || state.isLinkingLoading

/**
 * Chess.com or Lichess on the Account page: the link form, then the first import's live
 * progress. Once linked, the account is managed in Settings and syncs by itself.
 */
@Composable
fun LinkAccountCard(state: AppUiState, actions: AccountActions, modifier: Modifier = Modifier) {
    CtCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
            if (state.isLinkingLoading) ImportRunning(state.importProgress, onCancel = actions.onCancelImport)
            else LinkForm(state.selectedImportDepth, actions)
            LinkStatus(state, actions)
        }
    }
}

/**
 * Settings > Linked account: who is linked and their ratings, which rating the app uses,
 * bringing in more of the history, and unlinking. New games arrive by themselves.
 */
@Composable
fun ManageAccountCard(state: AppUiState, actions: AccountActions, modifier: Modifier = Modifier) {
    val profile = state.linkedProfile
    CtCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
            if (profile == null) {
                Text("No account linked. Link Chess.com or Lichess on the Account page to bring in your games.", style = CtType.BodySmall)
                return@Column
            }
            Linked(profile, state, actions)
            LinkStatus(state, actions)
        }
    }
}

@Composable
private fun LinkStatus(state: AppUiState, actions: AccountActions) {
    val status = state.linkingError ?: state.linkingSuccessMessage
    if (status == null || state.isLinkingLoading) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = status,
            style = CtType.BodySmall.copy(color = if (state.linkingError != null) CtColors.Coral else CtColors.Lime),
            modifier = Modifier.weight(1f),
        )
        CtChip(text = "OK", color = CtColors.Text2, onClick = actions.onDismissLinkStatus)
    }
}

@Composable
private fun LinkForm(depth: ImportDepth, actions: AccountActions) {
    var platformName by rememberSaveable { mutableStateOf(RatingPlatform.CHESS_COM.name) }
    var timeName by rememberSaveable { mutableStateOf(RatingTimeControl.RAPID.name) }
    var username by rememberSaveable { mutableStateOf("") }
    val platform = RatingPlatform.valueOf(platformName)

    Row(verticalAlignment = Alignment.CenterVertically) {
        CtIconBubble(icon = Icons.Rounded.Link, tint = CtColors.Teal, size = 40.dp)
        Spacer(Modifier.width(CtSpacing.md))
        Column(Modifier.weight(1f)) {
            Text("Link your account", style = CtType.TitleSmall)
            Text("Bring in your games and rating from Chess.com or Lichess. New games then arrive by themselves.", style = CtType.Caption)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
        RatingPlatform.entries.forEach { p ->
            CtChip(text = p.displayName, selected = p == platform, onClick = { platformName = p.name })
        }
    }
    CtTextField(value = username, onValueChange = { username = it.trim() }, label = "Username", placeholder = "Your ${platform.displayName} name")
    RatingChips(RatingTimeControl.valueOf(timeName)) { timeName = it.name }
    DepthChips(depth, actions.onImportDepth)
    CtButton(
        text = "Link and import",
        icon = Icons.Rounded.Link,
        enabled = username.isNotBlank(),
        onClick = { actions.onLink(platform, username, RatingTimeControl.valueOf(timeName)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Which of the account's ratings the app uses (games of every time control are imported). */
@Composable
private fun RatingChips(selected: RatingTimeControl, onSelect: (RatingTimeControl) -> Unit) {
    Text("Rating to use", style = CtType.Label)
    Text("Games of every time control are imported; this picks the rating your bots and level follow.", style = CtType.Caption)
    Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
        RatingTimeControl.entries.forEach { t ->
            CtChip(text = t.displayName, color = CtColors.Gold, selected = t == selected, onClick = { onSelect(t) })
        }
    }
}

@Composable
private fun DepthChips(depth: ImportDepth, onDepth: (ImportDepth) -> Unit) {
    Text("Games to import", style = CtType.Label)
    Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
        ImportDepth.entries.forEach { d ->
            CtChip(text = d.label, color = CtColors.Violet, selected = d == depth, onClick = { onDepth(d) })
        }
    }
}

@Composable
private fun Linked(profile: LinkedChessProfile, state: AppUiState, actions: AccountActions) {
    var confirmUnlink by rememberSaveable { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        CtIconBubble(icon = Icons.Rounded.CloudSync, tint = CtColors.Teal, size = 40.dp)
        Spacer(Modifier.width(CtSpacing.md))
        Column(Modifier.weight(1f)) {
            Text("@${profile.username} on ${profile.platform.displayName}", style = CtType.TitleSmall)
            Text(ratingsLine(profile), style = CtType.Caption)
            Text(syncLine(state), style = CtType.Caption)
        }
    }
    RatingChips(profile.selectedTimeControl, actions.onRatingTimeControl)
    if (state.isLinkingLoading) {
        ImportRunning(state.importProgress, onCancel = actions.onCancelImport)
    } else {
        DepthChips(state.selectedImportDepth, actions.onImportDepth)
        CtButton(
            text = "Import ${state.selectedImportDepth.label.lowercase()} games",
            icon = Icons.Rounded.CloudDownload,
            style = CtButtonStyle.Secondary,
            onClick = actions.onImportMore,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    CtButton(text = "Unlink", icon = Icons.Rounded.LinkOff, style = CtButtonStyle.Ghost, onClick = { confirmUnlink = true }, modifier = Modifier.fillMaxWidth())
    if (confirmUnlink) {
        CtConfirmDialog(
            title = "Unlink @${profile.username}?",
            text = "Your imported games stay in the games list. The app stops using this account's rating.",
            confirmLabel = "Unlink",
            onConfirm = {
                confirmUnlink = false
                actions.onUnlink()
            },
            onDismiss = { confirmUnlink = false },
        )
    }
}

/** "New games sync by themselves · last synced 25 Oct 2026". */
internal fun syncLine(state: AppUiState): String = when {
    state.isAutoSyncing -> "Checking for new games…"
    state.lastSyncedEpochMs != null -> "New games sync by themselves · last synced ${formatDate(state.lastSyncedEpochMs)}"
    else -> "New games sync by themselves"
}

/** "Rapid 1848 · Blitz 1720 · Bullet –". */
internal fun ratingsLine(profile: LinkedChessProfile): String = listOf(
    "Rapid" to profile.rapidRating,
    "Blitz" to profile.blitzRating,
    "Bullet" to profile.bulletRating,
).joinToString(" · ") { (name, rating) -> "$name ${rating ?: "–"}" }

@Composable
private fun ImportRunning(progress: ImportProgress?, onCancel: () -> Unit) {
    val p = progress ?: ImportProgress()
    Text("Importing games", style = CtType.TitleSmall)
    Text(
        text = buildString {
            append(p.phase)
            if (p.currentPeriod.isNotBlank()) append(" · ").append(p.currentPeriod)
        },
        style = CtType.Caption,
    )
    CtProgressBar(progress = if (p.targetCount > 0) p.importedCount.toFloat() / p.targetCount else 0f, height = 8.dp)
    Text("${p.importedCount} of up to ${p.targetCount} games · they appear in Games as they arrive", style = CtType.Caption)
    Spacer(Modifier.height(CtSpacing.xs))
    CtButton(text = "Stop importing", style = CtButtonStyle.Secondary, onClick = onCancel, modifier = Modifier.fillMaxWidth())
}
