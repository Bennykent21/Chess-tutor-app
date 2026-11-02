package com.chesstutor.app.data.settings

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerPictureTest {

    @Test
    fun everyChoiceSurvivesBeingStored() {
        listOf(PlayerPicture.None, PlayerPicture.Face("juno"), PlayerPicture.Coach("pip"), PlayerPicture.Photo(1234L)).forEach {
            assertEquals(it, PlayerPicture.decode(it.encoded))
        }
    }

    @Test
    fun anythingUnreadableFallsBackToTheInitial() {
        listOf("", "nonsense", "face:", "coach:", "photo:", "photo:soon").forEach {
            assertEquals(it, PlayerPicture.None, PlayerPicture.decode(it))
        }
    }

    @Test
    fun settingsKeepThePicture() {
        val settings = AppSettings(playerPicture = PlayerPicture.Face("kofi").encoded)
        assertEquals(PlayerPicture.Face("kofi"), AppSettingsCodec.decode(AppSettingsCodec.encode(settings)).picture)
        assertEquals("nothing chosen yet", PlayerPicture.None, AppSettings().picture)
    }

    private fun jpeg(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    @Test
    fun aPhotoOfAnyShapeBecomesASmallSquare() {
        listOf(1200 to 800, 800 to 1200, 200 to 200).forEach { (width, height) ->
            val square = PlayerPhoto.squared(jpeg(width, height))
            assertNotNull(square)
            val decoded = BitmapFactory.decodeByteArray(square, 0, square!!.size)
            assertEquals("$width x $height", 256, decoded.width)
            assertEquals("$width x $height", 256, decoded.height)
        }
    }

    @Test
    fun aBigPhotoIsKeptSmall() {
        assertTrue(PlayerPhoto.squared(jpeg(3000, 4000))!!.size < 60_000)
    }

    @Test
    fun aFileThatIsNotAPictureIsRefused() {
        assertNull(PlayerPhoto.squared("not a picture".toByteArray()))
        assertNull(PlayerPhoto.squared(ByteArray(0)))
    }
}
