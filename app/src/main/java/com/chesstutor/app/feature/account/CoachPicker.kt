package com.chesstutor.app.feature.account

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtMascotArt
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.LocalReduceMotion
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.Mascot

/** "Your coach" (decision D20): pick one of the three mascots. Lives in Settings. */
@Composable
fun CoachPicker(
    selected: Mascot,
    onSelect: (Mascot) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
        CtSectionHeader(title = "Your coach")
        Text(
            text = "Your coach cheers you on in puzzles and lessons.",
            style = CtType.BodySmall,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(CtSpacing.sm),
        ) {
            Mascot.entries.forEach { mascot ->
                MascotOption(
                    mascot = mascot,
                    isSelected = mascot == selected,
                    onClick = { onSelect(mascot) },
                )
            }
        }
    }
}

@Composable
private fun MascotOption(mascot: Mascot, isSelected: Boolean, onClick: () -> Unit) {
    val duration = if (LocalReduceMotion.current) 0 else 200
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) CtColors.Gold else CtColors.Line,
        animationSpec = tween(duration),
        label = "coachBorder",
    )
    val background by animateColorAsState(
        targetValue = if (isSelected) CtColors.SurfaceHigh else CtColors.Surface,
        animationSpec = tween(duration),
        label = "coachBackground",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CtShapes.CardSmall)
            .background(background)
            .border(if (isSelected) 2.dp else 1.dp, borderColor, CtShapes.CardSmall)
            .semantics { selected = isSelected }
            .ctPressable(role = Role.RadioButton, onClickLabel = "Choose ${mascot.displayName}", onClick = onClick)
            .padding(horizontal = CtSpacing.md, vertical = CtSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Fixed width: Sage is wider than Pip and Rookie, and the names should line up.
        CtMascotArt(mascot = mascot, height = 64.dp, modifier = Modifier.width(60.dp))
        Spacer(Modifier.width(CtSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = mascot.displayName, style = CtType.TitleSmall)
            // Text2, not Text3: Text3 is not allowed on SurfaceHigh (Palette.TEXT_PAIRS).
            Text(text = mascot.tagline, style = CtType.BodySmall)
        }
        Spacer(Modifier.width(CtSpacing.sm))
        Icon(
            imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isSelected) CtColors.Gold else CtColors.Text2,
            modifier = Modifier.size(26.dp),
        )
    }
}
