package com.chesstutor.app.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/** A search box: a magnifier and a placeholder, no label above (game filters, opening lists). */
@Composable
fun CtSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(CtSpacing.touchTarget)
            .clip(CtShapes.Control)
            .background(CtColors.Surface)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = CtColors.Text2, modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = CtType.Body.copy(color = CtColors.Text2), maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = CtType.Body.copy(color = CtColors.Text),
                cursorBrush = SolidColor(CtColors.Text),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
            )
        }
    }
}

/** A one-line text input with its label above. */
@Composable
fun CtTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = label, style = CtType.Label)
        Spacer(Modifier.height(CtSpacing.xs))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = CtType.Body.copy(color = CtColors.Text),
            cursorBrush = SolidColor(CtColors.Accent),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
                .clip(CtShapes.Control)
                .background(CtColors.Surface)
                .border(1.dp, CtColors.Line, CtShapes.Control)
                .padding(horizontal = 14.dp, vertical = 14.dp)
                .semantics { contentDescription = label },
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty() && placeholder != null) Text(text = placeholder, style = CtType.Body)
                    inner()
                }
            },
        )
    }
}

/** A labelled slider with its current value on the right and optional end labels below. */
@Composable
fun CtSliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    startLabel: String? = null,
    endLabel: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CtShapes.CardSmall)
            .background(CtColors.Surface)
            .padding(horizontal = CtSpacing.lg, vertical = CtSpacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, style = CtType.TitleSmall, modifier = Modifier.weight(1f))
            Text(text = valueLabel, style = CtType.Label.copy(color = CtColors.Accent))
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = CtColors.Primary,
                activeTrackColor = CtColors.Primary,
                inactiveTrackColor = CtColors.Track,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier.semantics { contentDescription = title; stateDescription = valueLabel },
        )
        if (startLabel != null || endLabel != null) {
            Row {
                Text(text = startLabel.orEmpty(), style = CtType.Caption, modifier = Modifier.weight(1f))
                Text(text = endLabel.orEmpty(), style = CtType.Caption)
            }
        }
    }
}
