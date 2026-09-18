package com.chesstutor.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chesstutor.app.ui.theme.ChessTutorColors
import com.chesstutor.app.viewmodel.PromotionRequest

@Composable
fun PromotionDialog(
    request: PromotionRequest,
    onChoose: (Char) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ChessTutorColors.Surface,
        title = {
            Text(
                text = "Choose promotion",
                color = ChessTutorColors.TextPrimary,
                fontSize = 18.sp
            )
        },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                request.choices.forEach { piece ->
                    TextButton(
                        onClick = { onChoose(piece) },
                        modifier = Modifier
                            .size(58.dp)
                            .background(ChessTutorColors.Surface2, RoundedCornerShape(10.dp))
                            .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(10.dp))
                    ) {
                        Text(
                            text = piece.uppercase(),
                            color = ChessTutorColors.TextPrimary,
                            fontSize = 24.sp
                        )
                    }
                }
            }
        },
        confirmButton = {}
    )
}
