package com.chesstutor.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chesstutor.app.ui.theme.ChessTutorColors
import com.chesstutor.app.ui.theme.ChessTutorTypography

@Composable
fun AcademyCard(
    modifier: Modifier = Modifier,
    sectionLabel: String? = null,
    backgroundColor: Color = ChessTutorColors.Surface,
    borderColor: Color = ChessTutorColors.Border,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (!sectionLabel.isNullOrBlank()) {
                Text(
                    text = sectionLabel.uppercase(),
                    style = ChessTutorTypography.labelSmall
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            content()
        }
    }
}
