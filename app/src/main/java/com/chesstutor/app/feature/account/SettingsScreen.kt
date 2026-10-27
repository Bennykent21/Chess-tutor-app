package com.chesstutor.app.feature.account

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import com.chesstutor.app.BuildConfig
import com.chesstutor.app.data.settings.ReviewDepth
import com.chesstutor.app.design.CtBadge
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtCard
import com.chesstutor.app.design.CtChip
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtConfirmDialog
import com.chesstutor.app.design.CtListRow
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtToggleRow
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.domain.bot.ChatLevel
import com.chesstutor.app.viewmodel.AppUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the Settings page can do. */
data class SettingsActions(
    val onMascot: (com.chesstutor.app.domain.Mascot) -> Unit = {},
    val onChatLevel: (ChatLevel) -> Unit = {},
    val onOpenBoardSettings: () -> Unit = {},
    val onOpenEngineHealth: () -> Unit = {},
    val onOpenLicences: () -> Unit = {},
    val onReviewDepth: (ReviewDepth) -> Unit = {},
    val onOnlineEngine: (Boolean) -> Unit = {},
    /** Every saved game as one PGN text, for "Export my games". */
    val exportPgn: suspend () -> String = { "" },
    /** The crash log on this phone ("" when there were no crashes), and clearing it. */
    val crashLog: suspend () -> String = { "" },
    val clearCrashLog: suspend () -> Unit = {},
    /** The "another move works" puzzle reports as text ("" when none), and clearing them. */
    val puzzleReports: suspend () -> String = { "" },
    val clearPuzzleReports: suspend () -> Unit = {},
    /** Erases every game, progress, setting and the linked account, then restarts the app. */
    val eraseEverything: suspend () -> Unit = {},
    /** "Support the developer" link; blank hides the row. */
    val supportUrl: String = BuildConfig.SUPPORT_URL,
    val onBack: () -> Unit = {},
)

const val SOURCE_URL = "https://github.com/Bennykent21/Chess-tutor-app"

/**
 * Settings as a normal page (plan 6.4): coach, board and sound, bot chat, the linked
 * account, the engine (review depth, the online engine, health), your data (export, crash
 * log, puzzle reports, erase everything) and About (version, source code, licences).
 */
@OptIn(ExperimentalLayoutApi::class)
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
    var confirmErase by rememberSaveable { mutableStateOf(false) }
    // Re-read after a share or clear, so the counts stay true.
    var dataVersion by remember { mutableIntStateOf(0) }
    var crashText by remember { mutableStateOf("") }
    var reportText by remember { mutableStateOf("") }
    LaunchedEffect(dataVersion) {
        crashText = runCatching { actions.crashLog() }.getOrDefault("")
        reportText = runCatching { actions.puzzleReports() }.getOrDefault("")
    }
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
    fun share(subject: String, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching { context.startActivity(Intent.createChooser(send, subject)) }
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

                CtSectionHeader(title = "Engine")
                CtCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Game review depth", style = CtType.Label)
                    Text(
                        "How deeply Stockfish checks each move of a game. ${state.appSettings.reviewDepth.detail}. Games already reviewed keep their review until you analyse them again.",
                        style = CtType.Caption,
                    )
                    Spacer(Modifier.height(CtSpacing.sm))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm), verticalArrangement = Arrangement.spacedBy(CtSpacing.xs)) {
                        ReviewDepth.entries.forEach { depth ->
                            CtChip(text = depth.label, color = CtColors.Teal, selected = state.appSettings.reviewDepth == depth, onClick = { actions.onReviewDepth(depth) })
                        }
                    }
                }
                CtToggleRow(
                    title = "Online engine as a backup",
                    subtitle = "If Stockfish on this phone stops, ask a free online engine (needs the internet). Off: the built-in Lite engine answers instead.",
                    checked = state.appSettings.onlineEngine,
                    onCheckedChange = actions.onOnlineEngine,
                )
                CtListRow(
                    title = "Engine health",
                    subtitle = if (state.engineStatus.isDegraded) "Stockfish is not running: using a weaker engine" else "Check that Stockfish is running on this phone",
                    icon = Icons.Rounded.Memory,
                    iconTint = if (state.engineStatus.isDegraded) CtColors.Coral else CtColors.Lime,
                    onClick = actions.onOpenEngineHealth,
                    trailing = if (state.engineStatus.isDegraded) { { CtBadge(text = "Lite engine", icon = Icons.Rounded.Warning) } } else null,
                )

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
                ShareCard(
                    title = "Crash log",
                    empty = "No crashes recorded on this phone.",
                    filled = "The app crashed ${countLabel(crashCount(crashText), "time")} recently. Sharing the log helps fix it; it holds the error, the app version and your phone model, nothing else.",
                    text = crashText,
                    onShare = { share("Chess Tutor crash log", crashText) },
                    onClear = { scope.launch { actions.clearCrashLog(); dataVersion++ } },
                )
                ShareCard(
                    title = "Puzzle reports",
                    empty = "When you report that another move works in a puzzle, it is kept here to share.",
                    filled = "${countLabel(reportText.lines().count { it.isNotBlank() }, "report")} of another move that works. Share them so the puzzles can be checked.",
                    text = reportText,
                    onShare = { share("Chess Tutor puzzle reports", reportText) },
                    onClear = { scope.launch { actions.clearPuzzleReports(); dataVersion++ } },
                )
                CtCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Erase everything", style = CtType.Label)
                    Text("Deletes your games, reviews, puzzle and lesson progress, XP, bots, settings and linked account from this phone, then restarts the app.", style = CtType.Caption)
                    Spacer(Modifier.height(CtSpacing.sm))
                    CtButton(
                        text = "Erase all my data",
                        icon = Icons.Rounded.DeleteForever,
                        style = CtButtonStyle.Ghost,
                        onClick = { confirmErase = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

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
                CtListRow(
                    title = "Open-source licences",
                    subtitle = "Stockfish, Maia, the puzzles, pieces, sounds, fonts and libraries",
                    icon = Icons.Rounded.Description,
                    onClick = actions.onOpenLicences,
                )
                // Only in builds that carry a support link (plan section 9).
                if (actions.supportUrl.isNotBlank()) {
                    CtListRow(
                        title = "Support the developer",
                        subtitle = "Chess Tutor is free. If it helps you, you can chip in.",
                        icon = Icons.Rounded.Favorite,
                        iconTint = CtColors.Coral,
                        onClick = { runCatching { uriHandler.openUri(actions.supportUrl) } },
                    )
                }
                Spacer(Modifier.height(CtSpacing.lg))
            }
        }
    }
    if (confirmErase) {
        CtConfirmDialog(
            title = "Erase everything?",
            text = "Your games, progress, settings and linked account will be deleted from this phone. This cannot be undone.",
            confirmLabel = "Erase",
            onConfirm = {
                confirmErase = false
                scope.launch { actions.eraseEverything() }
            },
            onDismiss = { confirmErase = false },
        )
    }
}

/** A text the player may share (crash log, puzzle reports): what it is, then Share and Clear. */
@Composable
private fun ShareCard(
    title: String,
    empty: String,
    filled: String,
    text: String,
    onShare: () -> Unit,
    onClear: () -> Unit,
) {
    CtCard(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = CtType.Label)
        Text(if (text.isBlank()) empty else filled, style = CtType.Caption)
        if (text.isNotBlank()) {
            Spacer(Modifier.height(CtSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                CtButton(text = "Share", icon = Icons.Rounded.Share, style = CtButtonStyle.Secondary, onClick = onShare, modifier = Modifier.weight(1f))
                CtButton(text = "Clear", icon = Icons.Rounded.Delete, style = CtButtonStyle.Ghost, onClick = onClear, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** How many crashes a crash log holds (each starts with its own header line). */
internal fun crashCount(log: String): Int = log.lines().count { it.startsWith("=== Crash") }

/** "1 time", "3 times". */
internal fun countLabel(count: Int, noun: String): String = "$count $noun" + if (count == 1) "" else "s"
