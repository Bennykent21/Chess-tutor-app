package com.chesstutor.app.feature.learn

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chesstutor.app.data.network.FallbackDns
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtGroupLabel
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.learn.LessonVideo
import com.chesstutor.app.domain.learn.LessonVideos
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Opens [video] in the YouTube app, or the browser without it. */
fun openInYouTube(context: Context, video: LessonVideo) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(video.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** YouTube's picture of a video, with the play mark and its length in the corner. */
@Composable
fun VideoThumbnail(video: LessonVideo, modifier: Modifier = Modifier, playMark: Boolean = true) {
    val picture by produceState<ImageBitmap?>(Thumbnails.cached(video.thumbnail), video.thumbnail) { value = value ?: Thumbnails.load(video.thumbnail) }
    Box(modifier.aspectRatio(16f / 9f).clip(CtShapes.CardSmall).background(CtColors.Surface), contentAlignment = Alignment.Center) {
        picture?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        if (playMark) {
            Box(Modifier.size(40.dp).clip(CtShapes.Pill).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White)
            }
        }
        Text(
            "${video.minutes} min",
            style = CtType.Caption.copy(color = Color.White),
            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.65f)).padding(horizontal = 5.dp),
        )
    }
}

/** Video pictures from YouTube, kept in memory while the app runs. */
private object Thumbnails {
    private val cache = LruCache<String, ImageBitmap>(48)
    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).dns(FallbackDns()).build()
    }

    fun cached(url: String): ImageBitmap? = cache.get(url)

    suspend fun load(url: String): ImageBitmap? {
        // Unit tests run without a network: their cards keep the plain picture.
        if (Build.FINGERPRINT == "robolectric") return null
        return withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) null else response.body?.byteStream()?.let(BitmapFactory::decodeStream)?.asImageBitmap()
                }
            }.getOrNull()
        }?.also { cache.put(url, it) }
    }
}

/** The bundled video list (D96), read once on a background thread; empty until it is. */
@Composable
fun rememberLessonVideos(): LessonVideos {
    val context = LocalContext.current.applicationContext
    val videos by produceState(VideoAssets.loaded ?: LessonVideos.EMPTY) {
        if (VideoAssets.loaded == null) value = VideoAssets.load(context)
    }
    return videos
}

private object VideoAssets {
    @Volatile var loaded: LessonVideos? = null

    suspend fun load(context: android.content.Context): LessonVideos = withContext(Dispatchers.IO) {
        loaded ?: LessonVideos.parse(runCatching { context.assets.open("videos.json").bufferedReader().use { it.readText() } }.getOrDefault("[]")).also { loaded = it }
    }
}
