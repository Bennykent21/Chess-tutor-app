package com.chesstutor.app.feature.learn

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType
import com.chesstutor.app.domain.learn.LessonVideo
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

/**
 * The video in a 16:9 frame (D98): YouTube's own embedded player, so its controls and adverts
 * stay as YouTube shows them. If the owner does not allow playing it inside apps, the frame says
 * so and offers YouTube.
 */
@Composable
fun VideoFrame(video: LessonVideo, autoplay: Boolean, modifier: Modifier = Modifier) {
    // The lesson's own part of the video, when it starts later than the beginning (D99).
    val start = video.start.toFloat()
    val context = LocalContext.current
    var failed by remember(video.id) { mutableStateOf(false) }
    Box(modifier.aspectRatio(16f / 9f).background(Color.Black), contentAlignment = Alignment.Center) {
        if (failed) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                Text("This one only plays on YouTube", style = CtType.BodySmall.copy(color = Color.White), textAlign = TextAlign.Center)
                CtButton(text = "Open in YouTube", style = CtButtonStyle.Secondary, onClick = { openInYouTube(context, video) })
            }
        } else {
            YouTubeFrame(video.id, start, autoplay, onError = { failed = true }, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun YouTubeFrame(videoId: String, start: Float, autoplay: Boolean, onError: () -> Unit, modifier: Modifier) {
    // Unit tests have no web view to play in: the frame stays black.
    if (Build.FINGERPRINT == "robolectric") return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val failed by rememberUpdatedState(onError)
    var player by remember { mutableStateOf<YouTubePlayer?>(null) }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            YouTubePlayerView(context).apply {
                enableAutomaticInitialization = false
                lifecycle.addObserver(this)
                initialize(
                    object : AbstractYouTubePlayerListener() {
                        override fun onReady(youTubePlayer: YouTubePlayer) {
                            player = youTubePlayer
                        }

                        override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                            failed()
                        }
                    },
                    // 13.0 sends the app as the player's origin, which YouTube now requires (else a black frame).
                    IFramePlayerOptions.Builder(context).controls(1).rel(0).ivLoadPolicy(3).fullscreen(0).build(),
                )
            }
        },
        onRelease = { view ->
            lifecycle.removeObserver(view)
            view.release()
        },
    )
    LaunchedEffect(player, videoId) {
        player?.let { if (autoplay) it.loadVideo(videoId, start) else it.cueVideo(videoId, start) }
    }
}
