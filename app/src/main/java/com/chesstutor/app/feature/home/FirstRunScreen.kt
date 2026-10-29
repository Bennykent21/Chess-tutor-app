package com.chesstutor.app.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chesstutor.app.board.PieceArt
import com.chesstutor.app.board.PieceSet
import com.chesstutor.app.core.Piece
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.PieceType
import com.chesstutor.app.data.settings.AppSettings
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtMascotArt
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.Mascot
import com.chesstutor.app.domain.MascotMood
import com.chesstutor.app.navigation.StartingLevel

/**
 * The first run (D30, D46, mock-up round 17): a full screen shown once. The coach asks the
 * player's name (it can be skipped), then where to start, with three answers.
 */
@Composable
fun FirstRunScreen(
    mascot: Mascot,
    pieceSet: PieceSet,
    onName: (String) -> Unit,
    onStartingLevel: (StartingLevel) -> Unit,
    modifier: Modifier = Modifier,
    initialName: String = "",
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var asked by rememberSaveable { mutableStateOf(false) }
    CtScreenBackground(modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CtSpacing.gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            CtMascotArt(mascot = mascot, mood = if (asked) MascotMood.PROUD else MascotMood.READY, height = 150.dp, modifier = Modifier.size(150.dp))
            Spacer(Modifier.height(CtSpacing.lg))
            if (!asked) {
                Text("Hi, I'm ${mascot.displayName}", style = CtType.Display, textAlign = TextAlign.Center)
                Text("What should I call you?", style = CtType.Body.copy(color = CtColors.Text2), textAlign = TextAlign.Center, modifier = Modifier.padding(top = CtSpacing.xs))
                Spacer(Modifier.height(CtSpacing.xl))
                NameBox(name, onChange = { name = it.take(com.chesstutor.app.data.settings.AppSettingsCodec.MAX_PLAYER_NAME) }, onDone = { if (name.isNotBlank()) { onName(name.trim()); asked = true } })
                Spacer(Modifier.height(CtSpacing.lg))
                CtButton(text = "Continue", enabled = name.isNotBlank(), onClick = { onName(name.trim()); asked = true }, modifier = Modifier.fillMaxWidth())
                CtButton(text = "Skip", style = CtButtonStyle.Ghost, onClick = { asked = true }, modifier = Modifier.fillMaxWidth())
            } else {
                val greeting = name.trim().takeIf { it.isNotEmpty() }?.let { "Nice to meet you, $it" } ?: "Nice to meet you"
                Text(greeting, style = CtType.Display, textAlign = TextAlign.Center)
                Text("Where should we start?", style = CtType.Body.copy(color = CtColors.Text2), textAlign = TextAlign.Center, modifier = Modifier.padding(top = CtSpacing.xs))
                Spacer(Modifier.height(CtSpacing.xl))
                StartingLevel.entries.forEach { level ->
                    LevelCard(level, pieceSet, onClick = { onStartingLevel(level) })
                    Spacer(Modifier.height(10.dp))
                }
            }
            Spacer(Modifier.height(CtSpacing.xl))
        }
    }
}

@Composable
private fun NameBox(value: String, onChange: (String) -> Unit, onDone: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(CtShapes.Control)
            .background(CtColors.Surface)
            .padding(horizontal = CtSpacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        if (value.isEmpty()) Text("Your name", style = CtType.Body.copy(color = CtColors.Text2))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = CtType.Body.copy(color = CtColors.Text, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(CtColors.Text),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Your name" },
        )
    }
}

/** One answer: a piece for the level, what it says and where it leads. */
@Composable
private fun LevelCard(level: StartingLevel, pieceSet: PieceSet, onClick: () -> Unit) {
    val piece = when (level) {
        StartingLevel.NEW -> PieceType.PAWN
        StartingLevel.RULES -> PieceType.KNIGHT
        StartingLevel.PLAYER -> PieceType.QUEEN
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 72.dp)
            .clip(CtShapes.Card)
            .background(CtColors.Surface)
            .ctPressable(onClickLabel = level.label, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = CtSpacing.lg, vertical = CtSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(44.dp).clip(CtShapes.Pill).background(CtColors.Raised), contentAlignment = Alignment.Center) {
            Image(painterResource(PieceArt.drawable(pieceSet, Piece(piece, PieceColor.WHITE))), contentDescription = null, modifier = Modifier.size(32.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CtSpacing.xxs)) {
            Text(level.label, style = CtType.TitleSmall)
            Text(level.detail, style = CtType.Caption)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = CtColors.Text2, modifier = Modifier.size(20.dp))
    }
}

/** The name the app greets the player by: the one they gave, else their linked username. */
fun greetingName(settings: AppSettings, linkedUsername: String?): String? =
    settings.playerName.trim().ifEmpty { null } ?: linkedUsername
