package com.chesstutor.app.design

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp

/** Asks before something that cannot be undone, such as deleting. [confirmLabel] names the action. */
@Composable
fun CtConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancel",
    /** False for a plain choice (accept a draw), whose confirm is not written in the error colour. */
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CtColors.Raised,
        shape = RoundedCornerShape(24.dp),
        title = { Text(text = title, style = CtType.Headline) },
        text = { Text(text = text, style = CtType.Body) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(text = confirmLabel, style = CtType.Button.copy(color = if (destructive) CtColors.Error else CtColors.Text)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = dismissLabel, style = CtType.Button.copy(color = CtColors.Text)) }
        },
    )
}

/** One entry of a [CtOverflowMenu]; [destructive] ones (Delete) are written in the error colour. */
data class CtMenuItem(val label: String, val destructive: Boolean = false, val onClick: () -> Unit)

/** The three-dot button at the top right and its menu, for actions that are not the page's main one. */
@Composable
fun CtOverflowMenu(items: List<CtMenuItem>, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Box(
            modifier = Modifier
                .size(CtSpacing.touchTarget)
                .clip(CtShapes.Pill)
                .ctPressable(onClickLabel = "More options") { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "More options", tint = CtColors.Text)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = CtColors.Raised) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label, style = CtType.Body.copy(color = if (item.destructive) CtColors.Error else CtColors.Text)) },
                    onClick = {
                        open = false
                        item.onClick()
                    },
                )
            }
        }
    }
}
