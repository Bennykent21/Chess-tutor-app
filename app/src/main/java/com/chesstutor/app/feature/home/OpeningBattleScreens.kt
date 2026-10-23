package com.chesstutor.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.chesstutor.app.board.moveListOf
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.Position
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtCard
import com.chesstutor.app.design.CtChip
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtEmptyState
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTextField
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.bot.BotRoster
import com.chesstutor.app.domain.game.SideChoice
import com.chesstutor.app.domain.opening.BattleMode
import com.chesstutor.app.domain.opening.Opening
import com.chesstutor.app.domain.opening.OpeningBattles
import com.chesstutor.app.domain.opening.OpeningTree
import com.chesstutor.app.domain.opening.groups

/** "1. e4 c5 2. Nf3 d6" for a line from the starting position. */
internal fun movesText(line: Opening): String =
    moveListOf(Position.initial(), line.moves.map(Move::fromUci)).joinToString(" ") { entry ->
        listOfNotNull(entry.numberLabel, entry.san).joinToString(" ")
    }

/** Opening Battle's setup page (plan 6.1): side, opening, mode, the bot's replies, opponent. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BattleSetupScreen(
    draft: BattleDraft,
    onChange: ((BattleDraft) -> BattleDraft) -> Unit,
    onPickOpening: () -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    CtScreenBackground {
        Column(Modifier.fillMaxSize()) {
            CtTopBar(title = "Opening battle", subtitle = "Play or drill one opening", onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = CtSpacing.gutter),
                verticalArrangement = Arrangement.spacedBy(CtSpacing.md),
            ) {
                CtSectionHeader(title = "You play")
                Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                    SideChoice.entries.forEach { choice ->
                        CtChip(text = choice.label, selected = draft.side == choice, color = CtColors.Gold, onClick = { onChange { it.copy(side = choice) } })
                    }
                }

                CtSectionHeader(title = "Opening")
                val line = draft.line
                CtCard(onClick = onPickOpening, onClickLabel = "Choose an opening", modifier = Modifier.fillMaxWidth()) {
                    if (line == null) {
                        Text("Choose an opening", style = CtType.TitleSmall)
                        Text("Browse every named line, from the Sicilian to the Grob.", style = CtType.BodySmall)
                    } else {
                        Text(line.name, style = CtType.TitleSmall)
                        Text(movesText(line), style = CtType.BodySmall)
                        Spacer(Modifier.height(CtSpacing.xs))
                        Text("Tap to change", style = CtType.Caption.copy(color = CtColors.Teal))
                    }
                }

                CtSectionHeader(title = "Mode")
                Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                    BattleMode.entries.forEach { mode ->
                        CtChip(text = mode.label, selected = draft.mode == mode, color = CtColors.Violet, onClick = { onChange { it.copy(mode = mode) } })
                    }
                }
                Text(draft.mode.description, style = CtType.BodySmall)

                if (draft.mode != BattleMode.SURPRISE) {
                    CtSectionHeader(title = "The bot replies")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm), verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                        CtChip(text = "Exactly this line", selected = draft.exactReplies, color = CtColors.Teal, onClick = { onChange { it.copy(exactReplies = true) } })
                        CtChip(
                            text = "Any ${line?.family ?: "line of the opening"}",
                            selected = !draft.exactReplies,
                            color = CtColors.Teal,
                            onClick = { onChange { it.copy(exactReplies = false) } },
                        )
                    }
                }

                CtSectionHeader(title = "Opponent")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm), verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                    BattleDraft.OPPONENTS.mapNotNull(BotRoster::byId).forEach { bot ->
                        CtChip(
                            text = "${bot.name} ${bot.rating}",
                            selected = draft.opponentId == bot.id,
                            color = botAccent(bot.rating),
                            onClick = { onChange { it.copy(opponentId = bot.id) } },
                        )
                    }
                }
                Spacer(Modifier.height(CtSpacing.sm))
                CtButton(
                    text = "Start",
                    icon = Icons.Rounded.PlayArrow,
                    enabled = line != null,
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (line == null) Text("Choose an opening to start.", style = CtType.Caption)
                Spacer(Modifier.height(CtSpacing.lg))
            }
        }
    }
}

/**
 * The opening tree to browse (plan 6.1: family, then opening, then variation). Families are
 * listed biggest first; the search box matches any line name.
 */
@Composable
fun OpeningPickerScreen(
    tree: OpeningTree?,
    onPick: (Opening) -> Unit,
    onBack: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var openFamily by rememberSaveable { mutableStateOf<String?>(null) }
    CtScreenBackground {
        Column(Modifier.fillMaxSize()) {
            CtTopBar(title = "Choose an opening", subtitle = tree?.let { "${it.openings.size} named lines" }, onBack = onBack)
            CtTextField(
                value = query,
                onValueChange = { query = it },
                label = "Search",
                placeholder = "Najdorf, London, Gambit…",
                modifier = Modifier.padding(horizontal = CtSpacing.gutter),
            )
            Spacer(Modifier.height(CtSpacing.sm))
            if (tree == null) {
                CtEmptyState(icon = Icons.Rounded.Flag, title = "Opening the book…", body = "Reading every named opening.")
                return@Column
            }
            val families = remember(tree, query) { OpeningBattles.searchFamilies(tree, query) }
            // While searching, show the matching lines straight away.
            LaunchedEffect(query) { if (query.isNotBlank() && families.size == 1) openFamily = families.first().name }
            if (families.isEmpty()) {
                CtEmptyState(icon = Icons.Rounded.Flag, title = "No opening found", body = "Try another name, such as \"Italian\" or \"Dragon\".")
                return@Column
            }
            LazyColumn(
                contentPadding = PaddingValues(horizontal = CtSpacing.gutter, vertical = CtSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(CtSpacing.sm),
            ) {
                // Flat lazy rows (family, group, line), so a family of 400 lines is built as it scrolls.
                families.forEach { family ->
                    val expanded = openFamily == family.name
                    item(key = "f:${family.name}") {
                        CtCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(CtSpacing.md)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .ctPressable(onClickLabel = if (expanded) "Close ${family.name}" else "Open ${family.name}") {
                                        openFamily = if (expanded) null else family.name
                                    },
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(family.name, style = CtType.TitleSmall)
                                    Text("${family.lines.size} ${if (family.lines.size == 1) "line" else "lines"}", style = CtType.Caption)
                                }
                                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = CtColors.Text2)
                            }
                        }
                    }
                    if (expanded) {
                        val q = query.trim()
                        family.groups().forEach { group ->
                            val lines = if (q.isEmpty() || family.name.contains(q, ignoreCase = true)) group.lines
                            else group.lines.filter { it.name.contains(q, ignoreCase = true) }
                            if (lines.isEmpty()) return@forEach
                            item(key = "g:${family.name}:${group.name}") {
                                Text(group.name, style = CtType.Label.copy(color = CtColors.Gold), modifier = Modifier.padding(start = CtSpacing.md, top = CtSpacing.sm))
                            }
                            items(lines, key = { "l:${it.moves.joinToString("")}" }) { line -> LineRow(line, group.name, onPick) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LineRow(line: Opening, groupName: String, onPick: (Opening) -> Unit) {
    // The part of the name after the group, so rows read "English Attack", not the full name.
    val rest = line.variation.removePrefix(groupName).trimStart(',', ' ')
    val title = rest.ifEmpty { if (groupName == com.chesstutor.app.domain.opening.MAIN_LINE) line.family else groupName }
    val moves = remember(line) { movesText(line) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .ctPressable(onClickLabel = "Play ${line.name}") { onPick(line) }
            .padding(horizontal = CtSpacing.md, vertical = CtSpacing.xs),
    ) {
        Text(title, style = CtType.Body.copy(color = CtColors.Text), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${line.eco} · $moves", style = CtType.Caption, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** The coach's line above the board during a battle. */
internal fun battleCoachLine(title: String, opening: String?, inBook: Boolean, drillMessage: String?): String = when {
    drillMessage != null -> drillMessage
    inBook -> "In book: ${opening ?: title}."
    else -> "Out of book. You're on your own now!"
}
