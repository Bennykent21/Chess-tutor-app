package com.chesstutor.app.ui.components

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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chesstutor.app.ui.theme.ChessTutorColors
import com.chesstutor.app.ui.theme.EmilThinkingIndicator

@Composable
fun ChessPlayerBar(
    name: String,
    rating: String,
    modifier: Modifier = Modifier,
    isBot: Boolean = false,
    icon: ImageVector = if (isBot) Icons.Default.SmartToy else Icons.Default.Person,
    isActiveTurn: Boolean = false,
    isThinking: Boolean = false,
    materialAdvantage: Int? = null,
    capturedPiecesText: String = "",
    statusBadge: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Avatar + Name + Rating
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isBot) Color(0xFF383531) else Color(0xFF45413C))
                    .border(
                        width = if (isActiveTurn) 2.dp else 1.dp,
                        color = if (isActiveTurn) ChessTutorColors.Primary else Color(0xFF4A4640),
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isActiveTurn) ChessTutorColors.Primary else Color(0xFFD4D2CD),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    if (rating.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF383531))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = rating,
                                color = ChessTutorColors.TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                if (capturedPiecesText.isNotBlank() || materialAdvantage != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (capturedPiecesText.isNotBlank()) {
                            Text(
                                text = capturedPiecesText,
                                fontSize = 11.sp,
                                color = Color(0xFFAAAAAA)
                            )
                        }
                        if (materialAdvantage != null && materialAdvantage > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "+$materialAdvantage",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Right: Status Badge, Thinking Indicator, or Active indicator
        if (isThinking) {
            EmilThinkingIndicator(text = "Thinking...")
        } else if (statusBadge != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF383531))
                    .border(1.dp, Color(0xFF4A4640), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = statusBadge,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ChessTutorColors.Primary
                )
            }
        } else if (isActiveTurn) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(ChessTutorColors.Primary)
            )
        }
    }
}
