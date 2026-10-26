package com.chesstutor.app.feature.account

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import com.chesstutor.app.BuildConfig
import com.chesstutor.app.design.CtBadge
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtCard
import com.chesstutor.app.design.CtChip
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtListRow
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.domain.bot.ChatLevel
import com.chesstutor.app.viewmodel.AppUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the Settings page can do; the linking actions come from [AccountActions]. */
data class SettingsActions(
    val onMascot: (com.chesstutor.app.domain.Mascot) -> Unit = {},
    val onChatLevel: (ChatLevel) -> Unit = {},
    val onOpenBoardSettings: () -> Unit = {},
    val onOpenEngineHealth: () -> Unit = {},
    val onOpenDesignGallery: () -> Unit = {},
    val onOpenBoardLab: () -> Unit = {},
    /** Every saved game as one PGN text, for "Export my games". */
    val exportPgn: suspend () -> String = { "" },
    /** "Support the developer" link; blank hides the row (always blank in the Play build). */
    val supportUrl: String = BuildConfig.SUPPORT_URL,
    val onBack: () -> Unit = {},
)

/** The main credits for the About section; `LICENSES.md` has the full record. */
internal val CREDITS = listOf(
    "Stockfish 19 chess engine" to "GPL-3.0",
    "Maia human-like move networks (CSSLab)" to "GPL-3.0",
    "Puzzles from the Lichess puzzle database" to "CC0",
    "Opening names from lichess-org/chess-openings" to "CC0",
    "Pieces: cburnett and merida (GPL), chessnut (Apache 2.0), fantasy (MIT)" to "Various",
    "Sounds by Kenney (kenney.nl)" to "CC0",
    "Fonts: Nunito, Inter, JetBrains Mono" to "SIL OFL 1.1",
)

const val SOURCE_URL = "https://github.com/Bennykent21/Chess-tutor-app"

/**
 * Settings as a normal page (plan 6.4), replacing the old settings sheet: coach, board and
 * sound, bot chat, the linked account, engine diagnostics under Advanced, exporting games,
 * and About (version, source code, open-source credits).
 */
@Composable
fun SettingsScreen(
    state: AppUiState,
    accountActions: AccountActions,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var exportMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-chess-pgn")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            exportMessage = runCatching {
                val text = actions.exportPgn()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: error("no stream")
                }
                "Your games were saved."
            }.getOrElse { "The file could not be saved." }
        }
    }

    CtScreenBackground(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            CtTopBar(title = "Settings", onBack = actions.onBack)
            Column(Modifier.padding(horizontal = CtSpacing.gutter), verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                CtCard(modifier = Modifier.fillMaxWidth()) {
                    CoachPicker(selected = state.appSettings.mascot, onSelect = actions.onMascot)
                }

                CtSectionHeader(title = "Board and play")
                CtListRow(
                    title = "Board, pieces and sound",
                    subtitle = "Piece sets, colours, coordinates, sound, haptics, reduce motion",
                    icon = Icons.Rounded.GridView,
                    iconTint = CtColors.Gold,
                    onClick = actions.onOpenBoardSettings,
                )
                CtCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Bot chat", style = CtType.Label)
                    Text("How much the bots talk during a game.", style = CtType.Caption)
                    Spacer(Modifier.height(CtSpacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                        ChatLevel.entries.forEach { level ->
                            CtChip(text = level.label, color = CtColors.Violet, selected = state.appSettings.chatLevel == level, onClick = { actions.onChatLevel(level) })
                        }
                    }
                }

                CtSectionHeader(title = "Linked account")
                ManageAccountCard(state = state, actions = accountActions)

                CtSectionHeader(title = "Your data")
                CtCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Export my games", style = CtType.Label)
                    Text("Saves every game in this app as one PGN file, which other chess apps can open.", style = CtType.Caption)
                    Spacer(Modifier.height(CtSpacing.sm))
                    CtButton(
                        text = "Export as PGN",
                        icon = Icons.Rounded.Download,
                        style = CtButtonStyle.Secondary,
                        onClick = { exporter.launch("chess-tutor-games.pgn") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    exportMessage?.let { Text(it, style = CtType.Caption) }
                }

                CtSectionHeader(title = "Advanced")
                CtListRow(
                    title = "Engine health",
                    subtitle = if (state.engineStatus.isDegraded) "Stockfish is not running: using a weaker engine" else "Check that Stockfish is running on this phone",
                    icon = Icons.Rounded.Memory,
                    iconTint = if (state.engineStatus.isDegraded) CtColors.Coral else CtColors.Lime,
                    onClick = actions.onOpenEngineHealth,
                    trailing = if (state.engineStatus.isDegraded) { { CtBadge(text = "Lite engine", icon = Icons.Rounded.Warning) } } else null,
                )

                CtSectionHeader(title = "About")
                CtCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Chess Tutor ${BuildConfig.VERSION_NAME}", style = CtType.TitleSmall)
                    Text("Free software under the GNU GPL v3. Everything runs on your phone.", style = CtType.Caption)
                }
                CtListRow(
                    title = "Source code",
                    subtitle = "github.com/Bennykent21/Chess-tutor-app",
                    icon = Icons.Rounded.Code,
                    onClick = { runCatching { uriHandler.openUri(SOURCE_URL) } },
                )
                // Only in builds that carry a support link: never in the Play version (plan section 9).
                if (actions.supportUrl.isNotBlank()) {
                    CtListRow(
                        title = "Support the developer",
                        subtitle = "Chess Tutor is free. If it helps you, you can chip in.",
                        icon = Icons.Rounded.Favorite,
                        iconTint = CtColors.Coral,
                        onClick = { runCatching { uriHandler.openUri(actions.supportUrl) } },
                    )
                }
                CtCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Open-source licences", style = CtType.Label)
                    CREDITS.forEach { (name, licence) ->
                        Spacer(Modifier.height(CtSpacing.xs))
                        Row {
                            Text(name, style = CtType.Caption, modifier = Modifier.weight(1f))
                            Text(licence, style = CtType.Caption.copy(color = CtColors.Text2))
                        }
                    }
                }

                CtSectionHeader(title = "During development")
                CtListRow(title = "Design system", subtitle = "Colours, type and components", icon = Icons.Rounded.Palette, iconTint = CtColors.Violet, onClick = actions.onOpenDesignGallery)
                CtListRow(title = "Board lab", subtitle = "Try the new board: moves, sounds and motion", icon = Icons.Rounded.Science, iconTint = CtColors.Gold, onClick = actions.onOpenBoardLab)
                Spacer(Modifier.height(CtSpacing.lg))
            }
        }
    }
}
