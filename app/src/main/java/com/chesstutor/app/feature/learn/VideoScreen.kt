package com.chesstutor.app.feature.learn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.domain.learn.LessonVideo

/**
 * One video from a Watch row (D100): the player, its title, channel and what it teaches. No list
 * of more videos: Back returns to the page it came from.
 */
@Composable
fun VideoScreen(video: LessonVideo?, onBack: () -> Unit, modifier: Modifier = Modifier) {
    CtScreenBackground(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            CtTopBar(title = "Watch", onBack = onBack)
            if (video == null) return@Column
            Column(Modifier.padding(horizontal = CtSpacing.gutter), verticalArrangement = Arrangement.spacedBy(CtSpacing.xs)) {
                VideoFrame(video, autoplay = true, modifier = Modifier.fillMaxWidth())
                Text(video.title, style = CtType.Title, modifier = Modifier.padding(top = CtSpacing.sm))
                Text("${video.channel} · ${video.minutes} min", style = CtType.Caption.copy(color = CtColors.Text2))
                if (video.why.isNotBlank()) Text(video.why, style = CtType.Body.copy(color = CtColors.Text2), modifier = Modifier.padding(top = CtSpacing.xs))
            }
        }
    }
}
