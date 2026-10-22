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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtChip
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtSliderRow
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTextField
import com.chesstutor.app.design.CtToggleRow
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.bot.BotDraft
import com.chesstutor.app.domain.bot.BotOpeningChoices
import com.chesstutor.app.domain.bot.BotRoster
import com.chesstutor.app.domain.bot.BotStyle
import com.chesstutor.app.domain.bot.MistakeLevel
import com.chesstutor.app.domain.bot.OpeningChoice
import com.chesstutor.app.domain.bot.ResignPolicy
import com.chesstutor.app.domain.bot.ThinkSpeed
import com.chesstutor.app.domain.game.TimeControl
import kotlin.math.roundToInt

/**
 * The custom bot builder (plan 6.1): one page of grouped controls, no dropdowns. Strength,
 * style, opening repertoire, then the rules its games use.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BotBuilderScreen(
    draft: BotDraft,
    onChange: ((BotDraft) -> BotDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    CtScreenBackground {
        Column(Modifier.fillMaxSize()) {
            CtTopBar(title = if (draft.isNew) "New bot" else "Edit ${draft.name}", subtitle = "Build your own opponent", onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = CtSpacing.gutter),
                verticalArrangement = Arrangement.spacedBy(CtSpacing.md),
            ) {
                CtTextField(
                    value = draft.name,
                    onValueChange = { name -> onChange { it.copy(name = name.take(BotDraft.MAX_NAME)) } },
                    label = "Name",
                    placeholder = "For example, Captain Fork",
                )
                FacePicker(draft.face, draft.rating) { face -> onChange { it.copy(face = face) } }

                CtSectionHeader(title = "Strength")
                CtSliderRow(
                    title = "Rating",
                    value = draft.rating.toFloat(),
                    onValueChange = { v -> onChange { it.copy(rating = (v / 50).roundToInt() * 50) } },
                    valueRange = 250f..3000f,
                    steps = 54,
                    valueLabel = "${draft.rating}",
                    startLabel = "Beginner",
                    endLabel = "Full strength",
                )
                Text("Thinking time", style = CtType.Label)
                ChipRow(ThinkSpeed.entries, draft.thinkSpeed, { it.name.lowercase().replaceFirstChar(Char::uppercase) }) { s -> onChange { it.copy(thinkSpeed = s) } }
                Text("Mistakes", style = CtType.Label)
                ChipRow(MistakeLevel.entries, draft.mistakes, { it.label }) { m -> onChange { it.copy(mistakes = m) } }

                CtSectionHeader(title = "Style")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm), verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                    BotStyle.entries.forEach { style ->
                        CtChip(text = style.label, selected = draft.style == style, color = CtColors.Violet, onClick = { onChange { it.withStyle(style) } })
                    }
                }
                CtSliderRow(
                    title = "Aggression",
                    value = draft.aggression,
                    onValueChange = { v -> onChange { it.copy(aggression = (v * 10).roundToInt() / 10f) } },
                    valueRange = -1f..1f,
                    valueLabel = lean(draft.aggression, listOf("Very calm", "Calm", "Balanced", "Attacking", "All-out attack")),
                    startLabel = "Calm",
                    endLabel = "Attacking",
                )
                CtSliderRow(
                    title = "Trades",
                    value = draft.trades,
                    onValueChange = { v -> onChange { it.copy(trades = (v * 10).roundToInt() / 10f) } },
                    valueRange = -1f..1f,
                    valueLabel = lean(draft.trades, listOf("Hoards pieces", "Keeps pieces", "Balanced", "Trades", "Trades everything")),
                    startLabel = "Keeps pieces",
                    endLabel = "Trades down",
                )

                CtSectionHeader(title = "Openings")
                OpeningChips("As White", BotOpeningChoices.white, draft.white) { label -> onChange { it.toggleWhite(label) } }
                OpeningChips("As Black against 1.e4", BotOpeningChoices.blackVsE4, draft.black) { label -> onChange { it.toggleBlack(label) } }
                OpeningChips("As Black against 1.d4", BotOpeningChoices.blackVsD4, draft.black) { label -> onChange { it.toggleBlack(label) } }

                CtSectionHeader(title = "Rules")
                Text("Clock", style = CtType.Label)
                ChipRow(TimeControl.entries, draft.rules.timeControl, { it.label }) { t -> onChange { it.copy(rules = it.rules.copy(timeControl = t)) } }
                CtToggleRow(title = "Takebacks", checked = draft.rules.takebacks, onCheckedChange = { v -> onChange { it.copy(rules = it.rules.copy(takebacks = v)) } })
                CtToggleRow(title = "Hints", checked = draft.rules.hints, onCheckedChange = { v -> onChange { it.copy(rules = it.rules.copy(hints = v)) } })
                CtToggleRow(title = "Evaluation bar", checked = draft.rules.evalBar, onCheckedChange = { v -> onChange { it.copy(rules = it.rules.copy(evalBar = v)) } })
                Text("Resigns", style = CtType.Label)
                ChipRow(ResignPolicy.entries, draft.resign, ::resignLabel) { r -> onChange { it.copy(resign = r) } }

                Spacer(Modifier.height(CtSpacing.sm))
                CtButton(
                    text = if (draft.isNew) "Save bot" else "Save changes",
                    icon = Icons.Rounded.Check,
                    enabled = draft.canSave,
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!draft.canSave) Text("Give your bot a name to save it.", style = CtType.Caption)
                if (!draft.isNew) {
                    CtButton(text = "Delete bot", icon = Icons.Rounded.Delete, style = CtButtonStyle.Secondary, onClick = onDelete, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(CtSpacing.lg))
            }
        }
    }
}

/** Words for a -1..1 slider: [words] holds five, from the strongest low to the strongest high. */
internal fun lean(value: Float, words: List<String>): String = when {
    value <= -0.6f -> words[0]
    value < -0.15f -> words[1]
    value < 0.15f -> words[2]
    value < 0.6f -> words[3]
    else -> words[4]
}

internal fun resignLabel(policy: ResignPolicy): String = when (policy) {
    ResignPolicy.NEVER -> "Never"
    ResignPolicy.WHEN_LOST -> "When lost"
    ResignPolicy.EARLY -> "Early"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm), verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
        options.forEach { option ->
            CtChip(text = label(option), selected = option == selected, color = CtColors.Teal, onClick = { onSelect(option) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OpeningChips(title: String, choices: List<OpeningChoice>, picked: Set<String>, onToggle: (String) -> Unit) {
    Text(title, style = CtType.Label)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm), verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
        choices.forEach { choice ->
            CtChip(text = choice.label, selected = choice.label in picked, color = CtColors.Gold, onClick = { onToggle(choice.label) })
        }
    }
}

/** The roster's faces, to borrow one for the new bot. */
@Composable
private fun FacePicker(face: String, rating: Int, onPick: (String) -> Unit) {
    Text("Face", style = CtType.Label)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm), contentPadding = PaddingValues(vertical = CtSpacing.xs)) {
        items(BotRoster.bots, key = { it.id }) { bot ->
            val chosen = bot.id == face
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .ctPressable(onClickLabel = "Use the ${bot.animal} face", onClick = { onPick(bot.id) })
                    .semantics { role = Role.RadioButton; selected = chosen },
            ) {
                BotAvatar(bot.id, rating, size = if (chosen) 64.dp else 52.dp, accent = if (chosen) CtColors.Gold else CtColors.Line)
                Text(bot.animal, style = if (chosen) CtType.Label.copy(color = CtColors.Gold) else CtType.Caption)
            }
        }
    }
}
