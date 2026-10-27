package com.chesstutor.app.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtCard
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A licence, with the file holding its full text in `assets/licenses` (null: none bundled). */
enum class Licence(val title: String, val file: String?) {
    GPL3("GNU General Public License v3.0", "GPL-3.0.txt"),
    GPL2_PLUS("GNU GPL v2 or later (used under GPL v3)", "GPL-3.0.txt"),
    APACHE2("Apache License 2.0", "Apache-2.0.txt"),
    MIT("MIT License", "MIT-fantasy-pieces.txt"),
    OFL("SIL Open Font License 1.1", "OFL-1.1.txt"),
    CC0("CC0 1.0 (public domain)", null),
    GPL2_CLASSPATH("GNU GPL v2 with the Classpath Exception", null),
}

/** One part of the app made by someone else (or the app itself), and its licence. */
data class Credit(val name: String, val by: String, val licence: Licence)

/** Everything the app ships, as recorded in `LICENSES.md` (plan section 9). */
val CREDITS: List<Credit> = listOf(
    Credit("Chess Tutor", "Its developers", Licence.GPL3),
    Credit("Stockfish 19 chess engine and its network", "The Stockfish developers", Licence.GPL3),
    Credit("Maia human-like move networks", "CSSLab, University of Toronto", Licence.GPL3),
    Credit("Leela Chess Zero move tables (for Maia)", "The LCZero developers", Licence.GPL3),
    Credit("Puzzles", "The Lichess puzzle database (lichess.org)", Licence.CC0),
    Credit("Opening names and lines", "lichess-org/chess-openings", Licence.CC0),
    Credit("Pieces \"Classic\" (cburnett)", "Colin M.L. Burnett", Licence.GPL2_PLUS),
    Credit("Pieces \"Merida\"", "Armando Hernandez Marroquin", Licence.GPL2_PLUS),
    Credit("Pieces \"Chessnut\"", "Alexis Luengas", Licence.APACHE2),
    Credit("Pieces \"Fantasy\"", "Maurizio Monge", Licence.MIT),
    Credit("Sound effects", "Kenney (kenney.nl)", Licence.CC0),
    Credit("Fonts Nunito, Inter and JetBrains Mono", "Their designers, via Google Fonts and JetBrains", Licence.OFL),
    Credit("Jetpack Compose, Room, DataStore, Navigation and other AndroidX libraries", "Google and the Android Open Source Project", Licence.APACHE2),
    Credit("Kotlin and kotlinx.coroutines", "JetBrains", Licence.APACHE2),
    Credit("OkHttp and Moshi", "Square", Licence.APACHE2),
    Credit("Java library support for older phones (desugar_jdk_libs)", "Google, from OpenJDK", Licence.GPL2_CLASSPATH),
)

/**
 * Open-source licences (plan 6.4 About, section 9): every bundled part with its author and
 * licence, and the full text of each licence the app carries.
 */
@Composable
fun LicencesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf<Licence?>(null) }
    var text by remember { mutableStateOf("") }
    LaunchedEffect(open) {
        val file = open?.file ?: return@LaunchedEffect
        text = withContext(Dispatchers.IO) {
            runCatching { context.assets.open("licenses/$file").bufferedReader().use { it.readText() } }.getOrDefault("The licence text could not be read.")
        }
    }
    CtScreenBackground(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            CtTopBar(title = open?.title ?: "Open-source licences", onBack = { if (open != null) open = null else onBack() })
            Column(Modifier.padding(horizontal = CtSpacing.gutter), verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                val shown = open
                if (shown != null) {
                    Text(text, style = CtType.Caption)
                } else {
                    Text(
                        "Chess Tutor is free software under the GNU GPL v3; its source code is on GitHub. It is built on the work of these people and projects. Tap a licence to read it.",
                        style = CtType.Body,
                    )
                    CtSectionHeader(title = "Thanks to")
                    CREDITS.forEach { credit ->
                        CtCard(modifier = Modifier.fillMaxWidth()) {
                            Text(credit.name, style = CtType.Label)
                            Text(credit.by, style = CtType.Caption)
                            Text(credit.licence.title, style = CtType.Caption)
                        }
                    }
                    CtSectionHeader(title = "Licence texts")
                    Licence.entries.filter { it.file != null }.distinctBy { it.file }.forEach { licence ->
                        CtButton(text = licence.title, style = CtButtonStyle.Secondary, onClick = { open = licence }, modifier = Modifier.fillMaxWidth())
                    }
                    Text(
                        "CC0 works are public domain and need no licence text. The Java library support is under the GNU GPL v2 with the Classpath Exception, published with OpenJDK.",
                        style = CtType.Caption,
                    )
                }
                Spacer(Modifier.height(CtSpacing.lg))
            }
        }
    }
}
