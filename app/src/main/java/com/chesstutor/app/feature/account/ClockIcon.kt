package com.chesstutor.app.feature.account

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chesstutor.app.R
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.account.TimeClass

/** Each clock's metal icon (D60): a stopwatch, lightning, a bullet, an hourglass, a calendar. */
@DrawableRes
internal fun clockIcon(clock: TimeClass): Int = when (clock) {
    TimeClass.RAPID, TimeClass.UNTIMED -> R.drawable.icon_clock_rapid
    TimeClass.BLITZ -> R.drawable.icon_clock_blitz
    TimeClass.BULLET -> R.drawable.icon_clock_bullet
    TimeClass.CLASSICAL -> R.drawable.icon_clock_classical
    TimeClass.DAILY -> R.drawable.icon_clock_daily
}

/** A clock's icon; screen readers hear its name. */
@Composable
internal fun ClockIcon(clock: TimeClass, size: Dp = 16.dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(clockIcon(clock)),
        contentDescription = clock.label,
        modifier = modifier.size(size),
    )
}

/** One chip for each clock, with [all] in front when the list may also show every clock at once. */
@Composable
internal fun ClockChips(
    clocks: List<TimeClass>,
    selected: TimeClass?,
    onSelect: (TimeClass?) -> Unit,
    modifier: Modifier = Modifier,
    all: Boolean = false,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()).padding(horizontal = CtSpacing.gutter),
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm),
    ) {
        if (all) ClockChip(null, selected == null, onClick = { onSelect(null) })
        clocks.forEach { clock -> ClockChip(clock, clock == selected, onClick = { onSelect(clock) }) }
    }
}

@Composable
private fun ClockChip(clock: TimeClass?, on: Boolean, onClick: () -> Unit) {
    val label = clock?.label ?: "All"
    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(CtShapes.Pill)
            .background(if (on) CtColors.Primary else CtColors.Surface)
            .ctPressable(role = Role.Tab, onClickLabel = "Show $label", onClick = onClick)
            .semantics(mergeDescendants = true) {
                selected = on
                contentDescription = label
            }
            .padding(start = if (clock == null) 16.dp else 10.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (clock != null) ClockIcon(clock, size = 22.dp)
        Text(label, style = CtType.Label.copy(color = if (on) CtColors.OnPrimary else CtColors.Text), maxLines = 1)
    }
}
