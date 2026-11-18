package com.chesstutor.app.feature.learn

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.learn.LessonVideo
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.FullscreenListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

/**
 * The video in a 16:9 frame (D98, D100): YouTube's own embedded player, so its controls and
 * adverts stay as YouTube shows them, with a row under it to go back or forward 10 seconds, play
 * or pause, and fill the screen (turned sideways). If the owner does not allow playing it inside
 * apps, the frame says so and offers YouTube.
 */
@Composable
fun VideoFrame(video: LessonVideo, autoplay: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var failed by remember(video.id) { mutableStateOf(false) }
    val control = remember(video.id) { VideoControl() }
    // Our full-screen button: the same video from the same second, sideways over everything.
    var bigFrom by remember(video.id) { mutableStateOf<Float?>(null) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CtSpacing.xs)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(CtShapes.Card).background(Color.Black), contentAlignment = Alignment.Center) {
            if (failed) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                    Text("This one only plays on YouTube", style = CtType.BodySmall.copy(color = Color.White), textAlign = TextAlign.Center)
                    CtButton(text = "Open in YouTube", style = CtButtonStyle.Secondary, onClick = { openInYouTube(context, video) })
                }
            } else {
                YouTubeFrame(video.id, video.start.toFloat(), autoplay, control, onError = { failed = true }, modifier = Modifier.fillMaxSize())
            }
        }
        if (!failed) {
            VideoControls(control, onFullScreen = {
                control.player?.pause()
                bigFrom = control.second
            })
        }
    }
    bigFrom?.let { from ->
        val big = remember { VideoControl() }
        FullScreen(onLeave = {
            control.player?.seekTo(big.second)
            bigFrom = null
        }) {
            YouTubeFrame(video.id, from, autoplay = true, big, onError = { bigFrom = null }, modifier = Modifier.fillMaxSize(), nativeFullScreen = false)
        }
    }
}

/** What the row under the video drives, once YouTube's player is ready. */
private class VideoControl {
    var player by mutableStateOf<YouTubePlayer?>(null)
    var playing by mutableStateOf(false)
    var second = 0f

    fun seekBy(seconds: Float) {
        player?.seekTo((second + seconds).coerceAtLeast(0f))
    }

    fun playOrPause() {
        player?.let { if (playing) it.pause() else it.play() }
    }
}

@Composable
private fun VideoControls(control: VideoControl, onFullScreen: () -> Unit) {
    val ready = control.player != null
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        VideoButton(Icons.Rounded.Replay10, "Back 10 seconds", ready) { control.seekBy(-10f) }
        VideoButton(if (control.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (control.playing) "Pause" else "Play", ready, control::playOrPause)
        VideoButton(Icons.Rounded.Forward10, "Forward 10 seconds", ready) { control.seekBy(10f) }
        VideoButton(Icons.Rounded.Fullscreen, "Full screen", ready, onFullScreen)
    }
}

@Composable
private fun VideoButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(CtSpacing.touchTarget).clip(CtShapes.Pill).ctPressable(onClickLabel = label, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = if (enabled) CtColors.Text else CtColors.Text2)
    }
}

@Composable
private fun YouTubeFrame(
    videoId: String,
    start: Float,
    autoplay: Boolean,
    control: VideoControl,
    onError: () -> Unit,
    modifier: Modifier,
    nativeFullScreen: Boolean = true,
) {
    // Unit tests have no web view to play in: the frame stays black.
    if (Build.FINGERPRINT == "robolectric") return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val failed by rememberUpdatedState(onError)
    var fullView by remember { mutableStateOf<View?>(null) }
    var leaveFull by remember { mutableStateOf<(() -> Unit)?>(null) }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            YouTubePlayerView(context).apply {
                enableAutomaticInitialization = false
                lifecycle.addObserver(this)
                addFullscreenListener(object : FullscreenListener {
                    override fun onEnterFullscreen(fullscreenView: View, exitFullscreen: () -> Unit) {
                        fullView = fullscreenView
                        leaveFull = exitFullscreen
                    }

                    override fun onExitFullscreen() {
                        // Detach it from the full-screen window before that window closes, as YouTube's sample does.
                        fullView?.let { (it.parent as? ViewGroup)?.removeView(it) }
                        fullView = null
                        leaveFull = null
                    }
                })
                initialize(
                    object : AbstractYouTubePlayerListener() {
                        override fun onReady(youTubePlayer: YouTubePlayer) {
                            control.player = youTubePlayer
                        }

                        override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
                            control.second = second
                        }

                        override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
                            control.playing = state == PlayerConstants.PlayerState.PLAYING || state == PlayerConstants.PlayerState.BUFFERING
                        }

                        override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                            failed()
                        }
                    },
                    // 13.0 sends the app as the player's origin, which YouTube now requires (else a black frame).
                    IFramePlayerOptions.Builder(context).controls(1).rel(0).ivLoadPolicy(3).fullscreen(if (nativeFullScreen) 1 else 0).build(),
                )
            }
        },
        onRelease = { view ->
            lifecycle.removeObserver(view)
            view.release()
        },
    )
    LaunchedEffect(control.player, videoId) {
        control.player?.let { if (autoplay) it.loadVideo(videoId, start) else it.cueVideo(videoId, start) }
    }
    // YouTube's own full-screen button hands over its view; it goes in the same sideways window.
    fullView?.let { view ->
        FullScreen(onLeave = { leaveFull?.invoke() }) {
            AndroidView(
                factory = { (view.parent as? ViewGroup)?.removeView(view); view },
                onRelease = { (it.parent as? ViewGroup)?.removeView(it) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** The playing video over the whole screen, sideways, without the system bars; Back leaves it. */
@Composable
private fun FullScreen(onLeave: () -> Unit, content: @Composable () -> Unit) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        val before = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { activity?.requestedOrientation = before }
    }
    Dialog(onDismissRequest = onLeave, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(window) {
            window?.let { WindowCompat.getInsetsController(it, it.decorView) }?.apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
            onDispose { }
        }
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) { content() }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
