package com.chesstutor.app.data.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/** The picture shown for the player (D52): their initial until they choose one. */
sealed interface PlayerPicture {
    data object None : PlayerPicture

    /** One of the bots' faces. */
    data class Face(val botId: String) : PlayerPicture

    /** One of the coaches. */
    data class Coach(val mascotId: String) : PlayerPicture

    /** A photo saved by [PlayerPhoto]; [version] changes with each one, so the screens reload it. */
    data class Photo(val version: Long) : PlayerPicture

    /** How the choice is stored in settings. */
    val encoded: String
        get() = when (this) {
            None -> ""
            is Face -> "$FACE$botId"
            is Coach -> "$COACH$mascotId"
            is Photo -> "$PHOTO$version"
        }

    companion object {
        private const val FACE = "face:"
        private const val COACH = "coach:"
        private const val PHOTO = "photo:"

        /** Anything unreadable falls back to the initial. */
        fun decode(value: String): PlayerPicture = when {
            value.startsWith(FACE) -> value.removePrefix(FACE).takeIf(String::isNotBlank)?.let(::Face) ?: None
            value.startsWith(COACH) -> value.removePrefix(COACH).takeIf(String::isNotBlank)?.let(::Coach) ?: None
            value.startsWith(PHOTO) -> value.removePrefix(PHOTO).toLongOrNull()?.let(::Photo) ?: None
            else -> None
        }
    }
}

/**
 * The player's photo, kept only on this phone as one small square JPEG in the app's own files.
 * Used for a picture chosen from the phone and for one fetched from Chess.com.
 */
object PlayerPhoto {
    private const val FILE = "player_picture.jpg"
    private const val SIDE = 256
    private const val QUALITY = 88

    private fun file(context: Context) = File(context.filesDir, FILE)

    /** Saves [image] as the player's photo. False when it is not a picture that can be read. */
    suspend fun save(context: Context, image: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val square = squared(image) ?: return@withContext false
        runCatching { file(context).writeBytes(square) }.isSuccess
    }

    suspend fun load(context: Context): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { BitmapFactory.decodeFile(file(context).path) }.getOrNull()
    }

    fun delete(context: Context) {
        file(context).delete()
    }

    /** [image] turned the right way up, cut to its middle square and scaled to [SIDE] as a JPEG. */
    internal fun squared(image: ByteArray): ByteArray? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(image, 0, image.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        // A phone photo is huge: read it at no more than twice the size it is kept at.
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIDE * 2) sample *= 2
        val full = BitmapFactory.decodeByteArray(image, 0, image.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val side = minOf(full.width, full.height)
        val matrix = Matrix().apply {
            postScale(SIDE.toFloat() / side, SIDE.toFloat() / side)
            postRotate(rotationOf(image).toFloat())
        }
        val square = Bitmap.createBitmap(full, (full.width - side) / 2, (full.height - side) / 2, side, side, matrix, true)
        ByteArrayOutputStream().also { square.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }.toByteArray()
    }.getOrNull()

    private fun rotationOf(image: ByteArray): Int = runCatching {
        when (ExifInterface(ByteArrayInputStream(image)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }.getOrDefault(0)
}
