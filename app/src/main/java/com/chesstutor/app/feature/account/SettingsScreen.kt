package com.chesstutor.app.feature.account

import com.chesstutor.app.data.settings.AppSettingsCodec
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtTextField
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.chesstutor.app.board.BoardColors
import com.chesstutor.app.board.PieceSet
import com.chesstutor.app.design.CtBackBar
import com.chesstutor.app.design.CtMascotArt
import com.chesstutor.app.design.CtScreenTitle
import com.chesstutor.app.design.CtSheet
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
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
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
import androidx.compose.ui.unit.dp
import com.chesstutor.app.BuildConfig
import com.chesstutor.app.data.settings.ReviewDepth
import com.chesstutor.app.design.AppTheme
import com.chesstutor.app.design.Appearance
import com.chesstutor.app.design.CtBadge
import com.chesstutor.app.design.CtChip
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtConfirmDialog
import com.chesstutor.app.design.CtListRow
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtToggleRow
import com.chesstutor.app.design.CtSegmented
import com.chesstutor.app.design.CtThemeCard
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
    val onSound: (Boolean) -> Unit = {},
    val onHaptics: (Boolean) -> Unit = {},
    val onReduceMotion: (Boolean) -> Unit = {},
    val onAppTheme: (AppTheme) -> Unit = {},
    val onAppearance: (Appearance) -> Unit = {},
    /** The design gallery, shown while the Phase 7 redesign is under way. */
    /** The name the app calls the player (D46). */
    val onName: (String) -> Unit = {},
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
 * Settings (Phase 7C3, canvas `Settings.dc.html`): appearance first, then the board, the coach
 * and the switches people change most, then bot chat, the linked account, the engine, your
 * data (export, crash log, puzzle reports, erase everything) and About.
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
    var confirmErase by rememberSaveable { mutableStateOf(false) }
    var coachSheet by rememberSaveable { mutableStateOf(false) }
    var nameSheet by rememberSaveable { mutableStateOf(false) }
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
            CtBackBar(onBack = actions.onBack)
            CtScreenTitle(title = "Settings")
            Column(Modifier.padding(horizontal = CtSpacing.gutter)) {
                CtSectionHeader(title = "Appearance")
                Spacer(Modifier.height(CtSpacing.sm))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppTheme.entries.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { theme ->
                                CtThemeCard(
                                    theme = theme,
                                    dark = CtColors.IsDark,
                                    selected = state.appSettings.appTheme == theme,
                                    onClick = { actions.onAppTheme(theme) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    CtSegmented(
                        options = Appearance.entries,
                        selected = state.appSettings.appearance,
                        label = { it.label },
                        onSelect = actions.onAppearance,
                    )
                }

                Spacer(Modifier.height(CtSpacing.md))
                val board = BoardColors.fromId(state.appSettings.boardThemeId)
                val pieces = PieceSet.fromId(state.appSettings.pieceSetId)
                CtListRow(
                    title = "Board and pieces",
                    icon = null,
                    subtitle = "${board.displayName}, ${pieces.displayName} pieces",
                    onClick = actions.onOpenBoardSettings,
                    leading = { MiniBoard(board) },
                )
                CtListRow(
                    title = "Name",
                    icon = null,
                    subtitle = state.appSettings.playerName.ifBlank { "Not set" },
                    onClick = { nameSheet = true },
                )
                CtListRow(
                    title = "Coach",
                    icon = null,
                    subtitle = state.appSettings.mascot.displayName,
                    onClick = { coachSheet = true },
                    leading = { CtMascotArt(mascot = state.appSettings.mascot, height = 40.dp, modifier = Modifier.size(40.dp)) },
                )
                CtToggleRow(title = "Sound", checked = state.appSettings.soundEnabled, onCheckedChange = actions.onSound)
                CtToggleRow(title = "Vibration", checked = state.appSettings.hapticsEnabled, onCheckedChange = actions.onHaptics)
                CtToggleRow(title = "Reduce motion", checked = state.appSettings.reduceMotion, onCheckedChange = actions.onReduceMotion, divider = false)

                CtSectionHeader(title = "Bot chat")
                Spacer(Modifier.height(CtSpacing.sm))
                CtSegmented(
                    options = ChatLevel.entries,
                    selected = state.appSettings.chatLevel,
                    label = { it.label },
                    onSelect = actions.onChatLevel,
                )

                CtSectionHeader(title = "Linked account")
                Spacer(Modifier.height(CtSpacing.sm))
                ManageAccountCard(state = state, actions = accountActions)

                CtSectionHeader(title = "Engine")
                Spacer(Modifier.height(CtSpacing.sm))
                Text("Game review depth", style = CtType.Label)
                Spacer(Modifier.height(CtSpacing.sm))
                CtSegmented(
                    options = ReviewDepth.entries,
                    selected = state.appSettings.reviewDepth,
                    label = { it.label },
                    onSelect = actions.onReviewDepth,
                )
                Spacer(Modifier.height(CtSpacing.xs))
                Text(state.appSettings.reviewDepth.detail, style = CtType.Caption)
                CtToggleRow(
                    title = "Online engine as a backup",
                    subtitle = "If Stockfish stops, a free online engine answers",
                    checked = state.appSettings.onlineEngine,
                    onCheckedChange = actions.onOnlineEngine,
                )
                CtListRow(
                    title = "Engine health",
                    subtitle = if (state.engineStatus.isDegraded) "Stockfish is not running: using a weaker engine" else "Check that Stockfish runs on this phone",
                    icon = Icons.Rounded.Memory,
                    iconTint = if (state.engineStatus.isDegraded) CtColors.Error else CtColors.Accent,
                    onClick = actions.onOpenEngineHealth,
                    divider = false,
                    trailing = if (state.engineStatus.isDegraded) { { CtBadge(text = "Lite engine", icon = Icons.Rounded.Warning) } } else null,
                )

                CtSectionHeader(title = "Your data")
                CtListRow(
                    title = "Export my games",
                    subtitle = exportMessage ?: "One PGN file for other chess apps",
                    icon = Icons.Rounded.Download,
                    onClick = { exporter.launch("chess-tutor-games.pgn") },
                )
                ShareRow(
                    title = "Crash log",
                    icon = Icons.Rounded.BugReport,
                    empty = "No crashes recorded on this phone.",
                    filled = "The app crashed ${countLabel(crashCount(crashText), "time")} recently. Tap to share the log.",
                    text = crashText,
                    onShare = { share("Chess Tutor crash log", crashText) },
                    onClear = { scope.launch { actions.clearCrashLog(); dataVersion++ } },
                )
                ShareRow(
                    title = "Puzzle reports",
                    icon = Icons.Rounded.Flag,
                    empty = "Reports of another move that works appear here.",
                    filled = "${countLabel(reportText.lines().count { it.isNotBlank() }, "report")} to share, so the puzzles can be checked.",
                    text = reportText,
                    onShare = { share("Chess Tutor puzzle reports", reportText) },
                    onClear = { scope.launch { actions.clearPuzzleReports(); dataVersion++ } },
                )
                CtListRow(
                    title = "Erase everything",
                    subtitle = "Games, progress, settings and the linked account",
                    icon = Icons.Rounded.DeleteForever,
                    iconTint = CtColors.Error,
                    onClick = { confirmErase = true },
                    divider = false,
                )

                CtSectionHeader(title = "About")
                Column(Modifier.padding(vertical = CtSpacing.sm)) {
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
                    subtitle = "Stockfish, Maia, puzzles, pieces, sounds, fonts and libraries",
                    icon = Icons.Rounded.Description,
                    onClick = actions.onOpenLicences,
                    divider = actions.supportUrl.isNotBlank(),
                )
                // Only in builds that carry a support link (plan section 9).
                if (actions.supportUrl.isNotBlank()) {
                    CtListRow(
                        title = "Support the developer",
                        subtitle = "Chess Tutor is free. If it helps you, you can chip in.",
                        icon = Icons.Rounded.Favorite,
                        onClick = { runCatching { uriHandler.openUri(actions.supportUrl) } },
                        divider = false,
                    )
                }
                Spacer(Modifier.height(CtSpacing.xl))
            }
        }
        CtSheet(visible = coachSheet, onDismiss = { coachSheet = false }) {
            CoachPicker(selected = state.appSettings.mascot, onSelect = { actions.onMascot(it); coachSheet = false })
        }
        CtSheet(visible = nameSheet, onDismiss = { nameSheet = false }) {
            var draft by rememberSaveable(nameSheet) { mutableStateOf(state.appSettings.playerName) }
            CtTextField(value = draft, onValueChange = { draft = it.take(AppSettingsCodec.MAX_PLAYER_NAME) }, label = "Name", placeholder = "What should the coach call you?")
            CtButton(text = "Save", onClick = { actions.onName(draft); nameSheet = false }, modifier = Modifier.fillMaxWidth())
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

/** A text the player may share (crash log, puzzle reports): tap the row to share it; Clear empties it. */
@Composable
private fun ShareRow(
    title: String,
    icon: ImageVector,
    empty: String,
    filled: String,
    text: String,
    onShare: () -> Unit,
    onClear: () -> Unit,
) {
    val has = text.isNotBlank()
    CtListRow(
        title = title,
        subtitle = if (has) filled else empty,
        icon = icon,
        onClick = { if (has) onShare() },
        trailing = if (has) { { CtChip(text = "Clear", onClick = onClear) } } else null,
    )
}

/** The player's board colours in a 4 x 4 corner, for the "Board and pieces" row. */
@Composable
private fun MiniBoard(colors: BoardColors) {
    Column(Modifier.size(40.dp).clip(RoundedCornerShape(6.dp))) {
        repeat(4) { row ->
            Row(Modifier.weight(1f)) {
                repeat(4) { col ->
                    Box(Modifier.weight(1f).fillMaxHeight().background(Color(if ((row + col) % 2 == 0) colors.light else colors.dark)))
                }
            }
        }
    }
}

/** How many crashes a crash log holds (each starts with its own header line). */
internal fun crashCount(log: String): Int = log.lines().count { it.startsWith("=== Crash") }

/** "1 time", "3 times". */
internal fun countLabel(count: Int, noun: String): String = "$count $noun" + if (count == 1) "" else "s"
