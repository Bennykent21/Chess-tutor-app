package com.chesstutor.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chesstutor.app.ui.theme.ChessTutorColors

enum class MoveClassification(
    val label: String,
    val color: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    BRILLIANT("BRILLIANT", ChessTutorColors.Brilliant, Icons.Default.AutoAwesome),
    GREAT("GREAT MOVE", ChessTutorColors.Great, Icons.Default.Star),
    BEST("BEST MOVE", ChessTutorColors.Best, Icons.Default.Done),
    EXCELLENT("EXCELLENT", ChessTutorColors.Success, Icons.Default.Done),
    INACCURACY("INACCURACY", ChessTutorColors.Inaccuracy, Icons.Default.PriorityHigh),
    MISTAKE("MISTAKE", ChessTutorColors.Mistake, Icons.Default.PriorityHigh),
    BLUNDER("BLUNDER", ChessTutorColors.Blunder, Icons.Default.Close),
    COACH("COACH", ChessTutorColors.Primary, Icons.Default.Psychology)
}

@Composable
fun CoachSpeechBubble(
    message: String,
    modifier: Modifier = Modifier,
    classification: MoveClassification = MoveClassification.COACH,
    title: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Coach Avatar Circle
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color(0xFF312E2B))
                .border(2.dp, classification.color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = classification.icon,
                contentDescription = null,
                tint = classification.color,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Speech Bubble Container
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 12.dp))
                .background(Color(0xFF312E2B))
                .border(1.dp, Color(0xFF3D3A34), RoundedCornerShape(topStart = 2.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(classification.color)
                    )
                    Text(
                        text = title ?: classification.label,
                        color = classification.color,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = message,
                    color = Color(0xFFECEFF1),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}
