package com.chesstutor.app.design

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.unit.Density

/**
 * Basic accessibility for every tappable thing on a screen (plan Phase 6D): a screen reader
 * must have words to read for it, and it must be at least [MIN_TOUCH_DP] across so a finger
 * can hit it. Used by the screen tests.
 */
object A11yAudit {
    const val MIN_TOUCH_DP = 48f

    /** One line per problem: "no label" or "too small", with what the node says or where it is. */
    fun problems(compose: SemanticsNodeInteractionsProvider, density: Density): List<String> =
        compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().mapNotNull { node -> problemOf(node, density) }

    private fun problemOf(node: SemanticsNode, density: Density): String? {
        val label = labelOf(node)
        val bounds = node.touchBoundsInRoot
        val widthDp = bounds.width / density.density
        val heightDp = bounds.height / density.density
        return when {
            label.isBlank() -> "no label: a tappable node at ${bounds.left.toInt()},${bounds.top.toInt()} has no text or description"
            widthDp < MIN_TOUCH_DP - 0.5f || heightDp < MIN_TOUCH_DP - 0.5f ->
                "too small: \"$label\" is ${widthDp.toInt()} x ${heightDp.toInt()} dp"
            else -> null
        }
    }

    private fun labelOf(node: SemanticsNode): String {
        val config = node.config
        val parts = mutableListOf<String>()
        config.getOrNull(SemanticsProperties.ContentDescription)?.let { parts += it }
        config.getOrNull(SemanticsProperties.Text)?.let { texts -> parts += texts.map { it.text } }
        config.getOrNull(SemanticsProperties.EditableText)?.let { parts += it.text }
        config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.OnClick)?.label?.let { parts += it }
        return parts.joinToString(" ").trim()
    }
}
