package com.chesstutor.app.feature.update

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chesstutor.app.R
import com.chesstutor.app.data.update.UpdateState
import com.chesstutor.app.design.CtActionPage
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtProgressBar
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.update.AppUpdates
import com.chesstutor.app.domain.update.ReleaseNotes

/**
 * The Home icon while an update waits (D90): the arrow, which fills from the bottom while the
 * download runs. Nothing when there is no update.
 */
@Composable
fun UpdateIcon(state: UpdateState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val release = state.release ?: return
    val filled = (state as? UpdateState.Downloading)?.progress
    Box(
        modifier = modifier
            .size(CtSpacing.touchTarget)
            .clip(CtShapes.Pill)
            .ctPressable(onClickLabel = "See what's new", onClick = onClick)
            .semantics { contentDescription = "Chess Tutor ${release.version} is ready" },
        contentAlignment = Alignment.Center,
    ) {
        if (filled == null) {
            Image(painterResource(R.drawable.ic_update), contentDescription = null, modifier = Modifier.size(24.dp))
        } else {
            Icon(painterResource(R.drawable.ic_update), contentDescription = null, tint = CtColors.Text2, modifier = Modifier.size(24.dp))
            Image(
                painterResource(R.drawable.ic_update),
                contentDescription = null,
                modifier = Modifier.size(24.dp).drawWithContent {
                    clipRect(top = size.height * (1f - filled)) { this@drawWithContent.drawContent() }
                },
            )
        }
    }
}

/**
 * What's new (D90): the notes of every version since the one installed, newest first, with the
 * update itself fixed at the bottom. Reached from the Home icon, the prompt and Settings.
 */
@Composable
fun WhatsNewScreen(
    state: UpdateState,
    onUpdate: () -> Unit,
    onAllow: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val release = state.release
    CtActionPage(
        title = "What's new",
        onBack = onBack,
        bottom = { UpdateAction(state, onUpdate, onAllow) },
        modifier = modifier,
    ) {
        if (release == null) {
            Text("You have the newest version.", style = CtType.Body.copy(color = CtColors.Text2), modifier = Modifier.padding(top = CtSpacing.md))
            return@CtActionPage
        }
        Image(
            painterResource(R.drawable.ic_update),
            contentDescription = null,
            modifier = Modifier.padding(top = CtSpacing.md).size(56.dp),
        )
        Spacer(Modifier.height(CtSpacing.md))
        Text("Chess Tutor ${release.version}", style = CtType.Display, modifier = Modifier.semantics { heading() })
        Text(AppUpdates.megabytes(release.apkBytes), style = CtType.Body.copy(color = CtColors.Text2))
        when (state) {
            is UpdateState.NeedsPermission -> "Android asks once: allow installs from Chess Tutor, come back, then tap Install."
            is UpdateState.Failed -> state.message
            else -> null
        }?.let {
            Spacer(Modifier.height(CtSpacing.md))
            Text(it, style = CtType.Body)
        }
        release.notes.forEachIndexed { index, notes ->
            // Older versions the player also skipped get their own number above their notes.
            if (index > 0) {
                Spacer(Modifier.height(CtSpacing.xl))
                Text(notes.version, style = CtType.Headline, modifier = Modifier.semantics { heading() })
            }
            Notes(notes)
        }
    }
}

@Composable
private fun Notes(notes: ReleaseNotes) {
    notes.intro?.let {
        Spacer(Modifier.height(CtSpacing.lg))
        Text(it, style = CtType.Body)
    }
    for (section in notes.sections) {
        Spacer(Modifier.height(CtSpacing.lg))
        if (section.title.isNotEmpty()) {
            Text(section.title, style = CtType.TitleSmall, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(CtSpacing.sm))
        }
        Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
            for (item in section.items) {
                Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
                    Box(Modifier.padding(top = 9.dp).size(6.dp).clip(CircleShape).background(CtColors.Accent))
                    Text(item, style = CtType.Body, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** The bottom of What's new: Update, then the download, then Install; the slot keeps its height. */
@Composable
private fun RowScope.UpdateAction(state: UpdateState, onUpdate: () -> Unit, onAllow: () -> Unit) {
    val full = Modifier.weight(1f)
    when (state) {
        is UpdateState.Available -> CtButton("Update", onUpdate, full)
        is UpdateState.Downloading -> Column(full.height(52.dp), verticalArrangement = Arrangement.Center) {
            CtProgressBar(progress = state.progress)
            Spacer(Modifier.height(CtSpacing.sm))
            Text("${AppUpdates.megabytes(state.done)} of ${AppUpdates.megabytes(state.total)}", style = CtType.Caption.copy(color = CtColors.Text2))
        }
        is UpdateState.Ready -> CtButton("Install", onUpdate, full)
        is UpdateState.NeedsPermission -> {
            CtButton("Allow", onAllow, full, style = CtButtonStyle.Secondary)
            CtButton("Install", onUpdate, full)
        }
        is UpdateState.Failed -> if (state.release != null) CtButton("Try again", onUpdate, full)
        else -> Unit
    }
}
