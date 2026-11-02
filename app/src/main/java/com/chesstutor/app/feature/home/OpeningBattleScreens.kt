package com.chesstutor.app.feature.home

import com.chesstutor.app.design.CtShapes
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import com.chesstutor.app.design.CtSegmented
import com.chesstutor.app.design.CtListRow
import com.chesstutor.app.design.CtActionPage
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import com.chesstutor.app.design.CtDivider
import com.chesstutor.app.design.CtGroupLabel
import com.chesstutor.app.design.CtSearchField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.chesstutor.app.domain.bot.OpeningChoice
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
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

/** Opening Battle's setup page (plan 6.1, mock-up round 17): opening, side, mode, the bot's replies, opponent. */
@Composable
fun BattleSetupScreen(
    draft: BattleDraft,
    onChange: ((BattleDraft) -> BattleDraft) -> Unit,
    onPickOpening: () -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    val line = draft.line
    CtActionPage(
        title = "Opening battle",
        onBack = onBack,
        bottom = { CtButton(text = "Start", enabled = line != null, onClick = onStart, modifier = Modifier.weight(1f)) },
    ) {
        CtListRow(
            title = line?.family ?: "Choose an opening",
            subtitle = line?.let { it.variation.ifEmpty { movesText(it) } },
            icon = null,
            onClick = onPickOpening,
            divider = false,
            leading = {
                Box(Modifier.size(40.dp).clip(CtShapes.Pill).background(CtColors.Surface), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Flag, contentDescription = null, tint = CtColors.Accent, modifier = Modifier.size(22.dp))
                }
            },
        )
        CtGroupLabel("You play")
        CtSegmented(SideChoice.entries, draft.side, { it.label }, { choice -> onChange { it.copy(side = choice) } })
        CtGroupLabel("Mode")
        CtSegmented(BattleMode.entries, draft.mode, { it.label }, { mode -> onChange { it.copy(mode = mode) } })
        if (draft.mode != BattleMode.SURPRISE) {
            CtGroupLabel("The bot replies")
            CtSegmented(
                options = listOf(true, false),
                selected = draft.exactReplies,
                label = { exact -> if (exact) "This line" else "Any ${line?.family ?: "line"}" },
                onSelect = { exact -> onChange { it.copy(exactReplies = exact) } },
            )
        }
        CtGroupLabel("Opponent")
        BotPickerRow(BattleDraft.OPPONENTS.mapNotNull(BotRoster::byId), draft.opponentId) { id -> onChange { it.copy(opponentId = id) } }
    }
}

/**
 * Picks openings for a bot's repertoire (review R5): the whole database, as in the battle
 * picker, but with tick boxes. A ticked family covers all its lines; single lines can be
 * ticked too. [popular] gives one-tap picks above the list.
 */
data class OpeningSelection(
    val selected: Set<String>,
    val onChange: (Set<String>) -> Unit,
    val popular: List<OpeningChoice> = emptyList(),
)

/**
 * The opening tree to browse (plan 6.1: family, then opening, then variation). Families are
 * listed biggest first; the search box matches any line name. With [selection] it picks many
 * openings instead of one line.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OpeningPickerScreen(
    tree: OpeningTree?,
    onPick: (Opening) -> Unit,
    onBack: () -> Unit,
    title: String = "Choose an opening",
    selection: OpeningSelection? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var openFamily by rememberSaveable { mutableStateOf<String?>(null) }
    CtScreenBackground {
        Column(Modifier.fillMaxSize()) {
            CtTopBar(
                title = title,
                onBack = onBack,
                trailing = if (selection != null && selection.selected.isNotEmpty()) {
                    { Text("Clear", style = CtType.Label.copy(color = CtColors.Accent), modifier = Modifier.ctPressable(onClickLabel = "Clear the openings") { selection.onChange(emptySet()) }.padding(CtSpacing.md)) }
                } else null,
            )
            CtSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search openings",
                modifier = Modifier.padding(horizontal = CtSpacing.gutter),
            )
            Spacer(Modifier.height(CtSpacing.sm))
            if (tree == null) {
                CtEmptyState(icon = Icons.Rounded.Flag, title = "Opening the book…", body = "Reading every named opening.")
                return@Column
            }
            val families = remember(tree, query) { OpeningBattles.searchFamilies(tree, query, limit = if (selection != null) Int.MAX_VALUE else 60) }
            // While searching, show the matching lines straight away.
            LaunchedEffect(query) { if (query.isNotBlank() && families.size == 1) openFamily = families.first().name }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = CtSpacing.gutter, vertical = CtSpacing.sm),
            ) {
                if (selection != null && query.isBlank()) {
                    item(key = "tools") {
                        Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                            if (selection.popular.isNotEmpty()) {
                                CtGroupLabel("Popular")
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm), verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                                    CtChip(text = "Select all", onClick = { selection.onChange(tree.families.map { it.name }.toSet()) })
                                    selection.popular.forEach { choice ->
                                        val on = selection.selected.containsAll(choice.names)
                                        CtChip(text = choice.label, selected = on, onClick = {
                                            selection.onChange(if (on) selection.selected - choice.names.toSet() else selection.selected + choice.names)
                                        })
                                    }
                                }
                            }
                            CtGroupLabel("All openings")
                        }
                    }
                }
                if (families.isEmpty()) {
                    item(key = "none") {
                        CtEmptyState(icon = Icons.Rounded.Flag, title = "No opening found", body = "Try another name, such as \"Italian\" or \"Dragon\".")
                    }
                }
                // Flat lazy rows (family, group, line), so a family of 400 lines is built as it scrolls.
                families.forEach { family ->
                    val expanded = openFamily == family.name
                    val familyTicked = selection?.selected?.contains(family.name) == true
                    item(key = "f:${family.name}") {
                        Column(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
                                if (selection != null) {
                                    TickBox(ticked = familyTicked, label = "All of ${family.name}") {
                                        selection.onChange(if (familyTicked) selection.selected - family.name else selection.selected + family.name)
                                    }
                                    Spacer(Modifier.width(CtSpacing.sm))
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .weight(1f)
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
                            CtDivider()
                        }
                    }
                    if (expanded) {
                        val q = query.trim()
                        family.groups().forEach { group ->
                            val lines = if (q.isEmpty() || family.name.contains(q, ignoreCase = true)) group.lines
                            else group.lines.filter { it.name.contains(q, ignoreCase = true) }
                            if (lines.isEmpty()) return@forEach
                            item(key = "g:${family.name}:${group.name}") {
                                Text(group.name, style = CtType.Label.copy(color = CtColors.Accent), modifier = Modifier.padding(start = CtSpacing.md, top = CtSpacing.sm))
                            }
                            items(lines, key = { "l:${it.moves.joinToString("")}" }) { line ->
                                if (selection == null) {
                                    LineRow(line, group.name, onClickLabel = "Play ${line.name}") { onPick(line) }
                                } else {
                                    // Covered by its ticked family: shown ticked, untick the family to pick lines.
                                    val covered = familyTicked
                                    val ticked = covered || line.name in selection.selected
                                    LineRow(line, group.name, onClickLabel = if (ticked) "Remove ${line.name}" else "Add ${line.name}", ticked = ticked) {
                                        if (!covered) selection.onChange(if (ticked) selection.selected - line.name else selection.selected + line.name)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (selection != null) {
                CtButton(
                    text = if (selection.selected.isEmpty()) "Done" else "Done, ${selection.selected.size} chosen",
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(start = CtSpacing.gutter, end = CtSpacing.gutter, top = CtSpacing.sm, bottom = CtSpacing.lg),
                )
            }
        }
    }
}


@Composable
private fun TickBox(ticked: Boolean, label: String, onToggle: () -> Unit) {
    Icon(
        imageVector = if (ticked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
        contentDescription = null,
        tint = if (ticked) CtColors.Gold else CtColors.Text2,
        modifier = Modifier
            .size(CtSpacing.touchTarget)
            .ctPressable(onClickLabel = if (ticked) "Remove $label" else "Add $label", onClick = onToggle)
            .semantics {
                role = Role.Checkbox
                stateDescription = if (ticked) "Picked" else "Not picked"
            }
            .padding(CtSpacing.sm),
    )
}

@Composable
private fun LineRow(line: Opening, groupName: String, onClickLabel: String, ticked: Boolean? = null, onClick: () -> Unit) {
    // The part of the name after the group, so rows read "English Attack", not the full name.
    val rest = line.variation.removePrefix(groupName).trimStart(',', ' ')
    val title = rest.ifEmpty { if (groupName == com.chesstutor.app.domain.opening.MAIN_LINE) line.family else groupName }
    val moves = remember(line) { movesText(line) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .ctPressable(onClickLabel = onClickLabel, onClick = onClick)
            .padding(horizontal = CtSpacing.md, vertical = CtSpacing.xs),
    ) {
        if (ticked != null) {
            Icon(
                imageVector = if (ticked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                contentDescription = if (ticked) "Picked" else "Not picked",
                tint = if (ticked) CtColors.Gold else CtColors.Text2,
            )
            Spacer(Modifier.width(CtSpacing.sm))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = CtType.Body.copy(color = CtColors.Text), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${line.eco} · $moves", style = CtType.Caption, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The coach's line above the board during a battle. */
internal fun battleCoachLine(title: String, opening: String?, inBook: Boolean, drillMessage: String?): String = when {
    drillMessage != null -> drillMessage
    inBook -> "In book: ${opening ?: title}."
    else -> "Out of book. You're on your own now!"
}
