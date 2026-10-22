package com.chesstutor.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtTheme
import com.chesstutor.app.design.CtType
import com.chesstutor.app.domain.bot.BotRoster
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Every roster bot has a face; the sheet is saved for review (build/outputs/roborazzi). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w420dp-h640dp-xxhdpi")
class BotAvatarTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun everyRosterBotHasAFace() {
        BotRoster.bots.forEach { assertNotNull("${it.name} has no art", botArt(it.id)) }
        compose.setContent {
            CtTheme(reduceMotion = true) {
                CtScreenBackground {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        BotRoster.bots.chunked(4).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { bot ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        BotAvatar(bot.id, bot.rating, size = 84.dp)
                                        Text("${bot.name} ${bot.rating}", style = CtType.Caption)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/BotFaces.png")
    }
}
