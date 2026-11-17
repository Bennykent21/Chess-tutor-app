package com.chesstutor.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chesstutor.app.board.BoardTheme
import com.chesstutor.app.board.BoardView
import com.chesstutor.app.core.Chess960
import com.chesstutor.app.core.Position
import com.chesstutor.app.design.CtActionPage
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtGroupLabel
import com.chesstutor.app.design.CtSegmented
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSliderRow
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtToggleRow
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.bot.BotProfile
import com.chesstutor.app.domain.game.GameRules
import com.chesstutor.app.domain.game.SideChoice
import com.chesstutor.app.domain.game.TimeControl
import com.chesstutor.app.feature.play.TimeControlPicker
import com.chesstutor.app.feature.play.TimeControlSaver
import kotlin.math.roundToInt

/**
 * A custom game (D92): the computer at any strength, either side, any clock, normal chess or a
 * chosen Chess960 start. [startIn960] opens it with Chess960 on (Play > Chess960).
 */
@Composable
fun CustomGameScreen(
    theme: BoardTheme,
    onStart: (rating: Int, side: SideChoice, rules: GameRules, chess960: Int?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    startIn960: Boolean = false,
) {
    var rating by rememberSaveable { mutableIntStateOf(1200) }
    var side by rememberSaveable { mutableStateOf(SideChoice.RANDOM) }
    var time by rememberSaveable(stateSaver = TimeControlSaver) { mutableStateOf(TimeControl.RAPID_10) }
    var takebacks by rememberSaveable { mutableStateOf(true) }
    var hints by rememberSaveable { mutableStateOf(true) }
    var evalBar by rememberSaveable { mutableStateOf(false) }
    var chess960 by rememberSaveable { mutableStateOf(if (startIn960) Chess960.random() else null) }
    CtActionPage(
        title = if (startIn960) "Chess960" else "Custom game",
        onBack = onBack,
        bottom = {
            CtButton(text = "Play", onClick = { onStart(rating, side, GameRules(time, takebacks, hints, evalBar), chess960) }, modifier = Modifier.weight(1f))
        },
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.lg)) {
            CtSliderRow(
                title = "Strength",
                value = rating.toFloat(),
                onValueChange = { rating = (it / 50f).roundToInt() * 50 },
                valueRange = BotProfile.MIN_RATING.toFloat()..BotProfile.MAX_RATING.toFloat(),
                valueLabel = if (rating >= BotProfile.MAX_RATING) "Full strength" else "$rating",
                startLabel = "Beginner",
                endLabel = "Full strength",
            )
            Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                CtGroupLabel("You play")
                CtSegmented(options = SideChoice.entries, selected = side, label = { it.label }, onSelect = { side = it })
            }
            TimeControlPicker(selected = time, onSelect = { time = it })
            Column {
                CtToggleRow(title = "Takebacks", checked = takebacks, onCheckedChange = { takebacks = it })
                CtToggleRow(title = "Hints", checked = hints, onCheckedChange = { hints = it })
                CtToggleRow(title = "Evaluation bar", checked = evalBar, onCheckedChange = { evalBar = it })
                CtToggleRow(title = "Chess960", checked = chess960 != null, onCheckedChange = { on -> chess960 = if (on) Chess960.random() else null })
            }
            chess960?.let { number -> Chess960Picker(number, theme, onChange = { chess960 = it }) }
        }
    }
}

/**
 * Picks one Chess960 start (D92): arrows step through the 960, Random rolls one, and a board
 * shows the back ranks the game will start from. 518 is the normal start.
 */
@Composable
fun Chess960Picker(number: Int, theme: BoardTheme, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val position = remember(number) { Position.fromFen(Chess960.startFen(number)) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(CtSpacing.md), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Step(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous start") { onChange((number + Chess960.COUNT - 1) % Chess960.COUNT) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Position", style = CtType.Label.copy(color = CtColors.Text2))
                Text("$number", style = CtType.Display, modifier = Modifier.semantics { contentDescription = "Chess960 position $number" })
            }
            Step(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next start") { onChange((number + 1) % Chess960.COUNT) }
        }
        Box(Modifier.fillMaxWidth(0.72f).aspectRatio(1f).clip(CtShapes.Chip)) {
            BoardView(position, theme, showCoordinates = false, showLegalDots = false)
        }
        CtButton(text = "Random", icon = Icons.Rounded.Shuffle, style = CtButtonStyle.Secondary, onClick = { onChange(Chess960.random()) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Step(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CtShapes.Control)
            .ctPressable(onClickLabel = label, onClick = onClick)
            .padding(CtSpacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = CtColors.Text)
    }
}
