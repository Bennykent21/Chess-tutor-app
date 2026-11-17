package com.chesstutor.app.feature.learn

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chesstutor.app.design.CtBackBar
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtGroupLabel
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.learn.LessonVideo
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

/**
 * A lesson video in the app's own player (D98): the video on top, what it teaches, then what to
 * watch next from the same topic. A video whose owner keeps it on YouTube offers YouTube instead.
 */
@Composable
fun VideoScreen(
    video: LessonVideo?,
    upNext: List<LessonVideo>,
    onOpen: (LessonVideo) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CtScreenBackground(modifier) {
        Column(Modifier.fillMaxSize()) {
            CtBackBar(onBack = onBack)
            if (video == null) return@Column
            VideoFrame(video, autoplay = true, modifier = Modifier.fillMaxWidth())
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = CtSpacing.gutter, end = CtSpacing.gutter, top = CtSpacing.md, bottom = CtSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(CtSpacing.sm),
            ) {
                item(key = "about") {
                    Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.xs)) {
                        Text(video.title, style = CtType.Title, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text("${video.channel} · ${video.minutes} min", style = CtType.Caption.copy(color = CtColors.Text2))
                        if (video.why.isNotBlank()) Text(video.why, style = CtType.Body.copy(color = CtColors.Text2), modifier = Modifier.padding(top = CtSpacing.xs))
                    }
                }
                if (upNext.isNotEmpty()) {
                    item(key = "label") { CtGroupLabel("Up next", modifier = Modifier.padding(top = CtSpacing.md)) }
                    items(upNext, key = { it.id }) { next -> UpNextRow(next, onClick = { onOpen(next) }) }
                }
            }
        }
    }
}

/** A video to watch next: its picture, title and channel in one row. */
@Composable
private fun UpNextRow(video: LessonVideo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CtShapes.CardSmall)
            .ctPressable(onClickLabel = "Watch", onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "${video.title}, ${video.channel}, ${video.minutes} minutes" },
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VideoThumbnail(video, Modifier.width(136.dp), playMark = false)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(video.title, style = CtType.Label, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(video.channel, style = CtType.Caption.copy(color = CtColors.Text2), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The video in a 16:9 frame (D98): YouTube's own embedded player, so its controls and adverts
 * stay as YouTube shows them. If the owner does not allow playing it inside apps, the frame says
 * so and offers YouTube.
 */
@Composable
fun VideoFrame(video: LessonVideo, autoplay: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var failed by remember(video.id) { mutableStateOf(false) }
    Box(modifier.aspectRatio(16f / 9f).background(Color.Black), contentAlignment = Alignment.Center) {
        if (failed) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                Text("This one only plays on YouTube", style = CtType.BodySmall.copy(color = Color.White), textAlign = TextAlign.Center)
                CtButton(text = "Open in YouTube", style = CtButtonStyle.Secondary, onClick = { openInYouTube(context, video) })
            }
        } else {
            YouTubeFrame(video.id, autoplay, onError = { failed = true }, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun YouTubeFrame(videoId: String, autoplay: Boolean, onError: () -> Unit, modifier: Modifier) {
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
                    IFramePlayerOptions.Builder().controls(1).rel(0).ivLoadPolicy(3).fullscreen(0).build(),
                )
            }
        },
        onRelease = { view ->
            lifecycle.removeObserver(view)
            view.release()
        },
    )
    LaunchedEffect(player, videoId) {
        player?.let { if (autoplay) it.loadVideo(videoId, 0f) else it.cueVideo(videoId, 0f) }
    }
}
