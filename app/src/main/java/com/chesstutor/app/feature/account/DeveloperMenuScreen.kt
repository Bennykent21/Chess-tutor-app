package com.chesstutor.app.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtListRow
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.navigation.LegacyTab

/**
 * Temporary list of every pre-revamp screen, so nothing is lost while the new tabs are
 * built (plan Phase 0). Each entry disappears when its replacement ships.
 */
@Composable
fun DeveloperMenuScreen(
    onOpenLegacy: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CtScreenBackground(modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            CtTopBar(title = "Classic screens", subtitle = "The app before the revamp", onBack = onBack)
            Column(
                modifier = Modifier.padding(horizontal = CtSpacing.gutter),
                verticalArrangement = Arrangement.spacedBy(CtSpacing.sm),
            ) {
                LegacyTab.ALL.forEach { index ->
                    CtListRow(
                        title = LegacyTab.title(index),
                        subtitle = replacedIn(index),
                        icon = iconFor(index),
                        iconTint = CtColors.Text2,
                        onClick = { onOpenLegacy(index) },
                    )
                }
                Spacer(Modifier.height(CtSpacing.md))
                Text(
                    text = "Back returns here. These screens keep their old look until each one is rebuilt.",
                    style = CtType.Caption,
                )
            }
        }
    }
}

private fun replacedIn(index: Int): String = when (index) {
    LegacyTab.SESSION, LegacyTab.CURRICULUM -> "Replaced in Phase 4 (Learn)"
    LegacyTab.ARENA -> "Replaced in Phase 3 (Home and bots)"
    LegacyTab.REVIEW -> "Replaced in Phase 5 (Account)"
    else -> ""
}

private fun iconFor(index: Int): ImageVector = when (index) {
    LegacyTab.SESSION -> Icons.Rounded.AutoStories
    LegacyTab.CURRICULUM -> Icons.Rounded.School
    LegacyTab.ARENA -> Icons.Rounded.SportsEsports
    LegacyTab.REVIEW -> Icons.Rounded.History
    else -> Icons.Rounded.Extension
}
