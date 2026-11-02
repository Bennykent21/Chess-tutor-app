package com.chesstutor.app.feature.account

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chesstutor.app.data.settings.PlayerPicture
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.Mascot
import com.chesstutor.app.domain.bot.BotRoster
import com.chesstutor.app.feature.home.PlayerAvatar

private const val FACES_PER_ROW = 5

/** What goes in the sheet: a photo, the Chess.com picture when that account is linked, or a face from the app. */
@Composable
internal fun PlayerPictureChoices(
    current: PlayerPicture,
    name: String,
    /** The linked Chess.com username, or null: Lichess has no pictures to bring over. */
    chessComUser: String?,
    busy: Boolean,
    message: String?,
    onPhone: () -> Unit,
    onChessCom: () -> Unit,
    onChoose: (PlayerPicture) -> Unit,
) {
    Text("Your picture", style = CtType.Headline)
    CtButton(text = "Choose a photo", icon = Icons.Rounded.Image, style = CtButtonStyle.Secondary, enabled = !busy, onClick = onPhone, modifier = Modifier.fillMaxWidth())
    if (chessComUser != null) {
        CtButton(text = "Use my Chess.com picture", icon = Icons.Rounded.Person, style = CtButtonStyle.Secondary, enabled = !busy, onClick = onChessCom, modifier = Modifier.fillMaxWidth())
    }
    if (message != null) Text(message, style = CtType.BodySmall.copy(color = CtColors.Error))
    val faces = BotRoster.bots.map { PlayerPicture.Face(it.id) to it.name } + Mascot.entries.map { PlayerPicture.Coach(it.id) to it.displayName }
    Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
        faces.chunked(FACES_PER_ROW).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                row.forEach { (face, label) -> FaceCell(face, label, chosen = face == current, name = name, onClick = { onChoose(face) }) }
            }
        }
    }
    if (current != PlayerPicture.None) {
        CtButton(text = "Remove picture", style = CtButtonStyle.Ghost, onClick = { onChoose(PlayerPicture.None) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun FaceCell(face: PlayerPicture, label: String, chosen: Boolean, name: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CtShapes.Pill)
            .then(if (chosen) Modifier.border(2.dp, CtColors.Accent, CtShapes.Pill) else Modifier)
            .ctPressable(onClickLabel = "Use $label as your picture", onClick = onClick)
            .semantics {
                role = Role.RadioButton
                selected = chosen
            },
        contentAlignment = Alignment.Center,
    ) {
        PlayerAvatar(face, name, size = 50.dp)
    }
}
