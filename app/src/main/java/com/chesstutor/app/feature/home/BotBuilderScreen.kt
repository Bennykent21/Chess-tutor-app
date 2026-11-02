package com.chesstutor.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.design.CtActionPage
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtChip
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtGroupLabel
import com.chesstutor.app.design.CtListRow
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtSegmented
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSliderRow
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtToggleRow
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.bot.BotDraft
import com.chesstutor.app.domain.bot.BotRoster
import com.chesstutor.app.domain.bot.BotStyle
import com.chesstutor.app.domain.bot.MistakeLevel
import com.chesstutor.app.domain.bot.ResignPolicy
import com.chesstutor.app.domain.bot.ThinkSpeed
import com.chesstutor.app.domain.game.TimeControl
import kotlin.math.roundToInt

/**
 * The custom bot builder (plan 6.1, mock-up round 17): name and face, strength, style,
 * opening repertoire and the rules its games use, with Save fixed at the bottom. Save stays
 * off until the bot has a name.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BotBuilderScreen(
    draft: BotDraft,
    onChange: ((BotDraft) -> BotDraft) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    /** Opens the full opening list for one side. */
    onChooseOpenings: (PieceColor) -> Unit = {},
) {
    CtActionPage(
        title = if (draft.isNew) "New bot" else "Edit ${draft.name}",
        onBack = onBack,
        bottom = {
            CtButton(text = if (draft.isNew) "Save bot" else "Save changes", enabled = draft.canSave, onClick = onSave, modifier = Modifier.weight(1f))
        },
    ) {
        NameField(draft.name) { name -> onChange { it.copy(name = name.take(BotDraft.MAX_NAME)) } }
        CtGroupLabel("Face")
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
        CtGroupLabel("Thinking time")
        CtSegmented(ThinkSpeed.entries, draft.thinkSpeed, { it.name.lowercase().replaceFirstChar(Char::uppercase) }, { s -> onChange { it.copy(thinkSpeed = s) } })
        CtGroupLabel("Mistakes")
        CtSegmented(MistakeLevel.entries, draft.mistakes, { it.label }, { m -> onChange { it.copy(mistakes = m) } })

        CtSectionHeader(title = "Style")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm), verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
            BotStyle.entries.forEach { style ->
                CtChip(text = style.label, selected = draft.style == style, onClick = { onChange { it.withStyle(style) } })
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
            modifier = Modifier.padding(top = CtSpacing.md),
        )
        CtSliderRow(
            title = "Trades",
            value = draft.trades,
            onValueChange = { v -> onChange { it.copy(trades = (v * 10).roundToInt() / 10f) } },
            valueRange = -1f..1f,
            valueLabel = lean(draft.trades, listOf("Hoards pieces", "Keeps pieces", "Balanced", "Trades", "Trades everything")),
            startLabel = "Keeps pieces",
            endLabel = "Trades down",
            modifier = Modifier.padding(top = CtSpacing.sm),
        )

        CtSectionHeader(title = "Openings")
        CtListRow(title = "White", icon = null, subtitle = openingSummary(draft.white), onClick = { onChooseOpenings(PieceColor.WHITE) })
        CtListRow(title = "Black", icon = null, subtitle = openingSummary(draft.black), onClick = { onChooseOpenings(PieceColor.BLACK) }, divider = false)

        CtSectionHeader(title = "Rules")
        CtGroupLabel("Clock")
        CtSegmented(TimeControl.entries, draft.rules.timeControl, ::clockLabel, { t -> onChange { it.copy(rules = it.rules.copy(timeControl = t)) } })
        Column(Modifier.padding(top = CtSpacing.sm)) {
            CtToggleRow(title = "Takebacks", checked = draft.rules.takebacks, onCheckedChange = { v -> onChange { it.copy(rules = it.rules.copy(takebacks = v)) } })
            CtToggleRow(title = "Hints", checked = draft.rules.hints, onCheckedChange = { v -> onChange { it.copy(rules = it.rules.copy(hints = v)) } })
            CtToggleRow(title = "Evaluation bar", checked = draft.rules.evalBar, onCheckedChange = { v -> onChange { it.copy(rules = it.rules.copy(evalBar = v)) } }, divider = false)
        }
        CtGroupLabel("Resigns")
        CtSegmented(ResignPolicy.entries, draft.resign, ::resignLabel, { r -> onChange { it.copy(resign = r) } })
    }
}

/** What a side plays in a few names; empty means it thinks for itself from the first move. */
internal fun openingSummary(picked: Set<String>): String {
    val shown = picked.sorted()
    return when {
        shown.isEmpty() -> "No book"
        shown.size <= 3 -> shown.joinToString()
        else -> shown.take(3).joinToString() + " and ${shown.size - 3} more"
    }
}

/** The bot's name: a plain box with "Name" until something is typed. */
@Composable
private fun NameField(value: String, onValueChange: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(CtShapes.Control)
            .background(CtColors.Surface)
            .padding(horizontal = CtSpacing.lg),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text("Name", style = CtType.Body.copy(color = CtColors.Text2))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = CtType.Body.copy(color = CtColors.Text),
            cursorBrush = SolidColor(CtColors.Text),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Bot name" },
        )
    }
}

/** The roster's faces, to borrow one for the new bot; the chosen one is ringed. */
@Composable
private fun FacePicker(face: String, rating: Int, onPick: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(CtSpacing.md), contentPadding = PaddingValues(vertical = CtSpacing.xs)) {
        items(BotRoster.bots, key = { it.id }) { bot ->
            val chosen = bot.id == face
            Box(
                modifier = Modifier
                    .ctPressable(onClickLabel = "Use the ${bot.animal} face", onClick = { onPick(bot.id) })
                    .semantics {
                        role = Role.RadioButton
                        selected = chosen
                        contentDescription = "${bot.animal} face"
                    },
            ) {
                BotAvatar(bot.id, rating, size = 52.dp, accent = if (chosen) CtColors.Accent else CtColors.Line)
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
