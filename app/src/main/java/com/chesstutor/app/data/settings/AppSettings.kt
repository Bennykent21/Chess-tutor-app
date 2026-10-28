package com.chesstutor.app.data.settings

import com.chesstutor.app.board.BoardColors
import com.chesstutor.app.board.BoardTheme
import com.chesstutor.app.board.PieceSet
import com.chesstutor.app.data.model.ImportDepth
import com.chesstutor.app.design.AppTheme
import com.chesstutor.app.design.Appearance
import com.chesstutor.app.domain.Mascot
import com.chesstutor.app.domain.bot.ChatLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Which side of the board is at the bottom. */
enum class BoardOrientation {
    /** The side you are playing is at the bottom (the default, decision D2). */
    FOLLOW_SIDE,
    /** White is always at the bottom. */
    WHITE_ALWAYS_BOTTOM,
}

/** How deeply Stockfish looks at each position of a game review (Settings > Engine). */
enum class ReviewDepth(val label: String, val detail: String, val depth: Int) {
    FAST("Fast", "Quicker reviews, may miss deep tactics", 12),
    NORMAL("Normal", "A good balance on most phones", 14),
    DEEP("Deep", "Slower, catches more", 18),
}

/**
 * Everything the player can set once and have applied everywhere (plan section 4).
 */
data class AppSettings(
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val showCoordinates: Boolean = true,
    val showLegalDots: Boolean = true,
    val reduceMotion: Boolean = false,
    val autoQueen: Boolean = false,
    val boardOrientation: BoardOrientation = BoardOrientation.FOLLOW_SIDE,
    /** Always a known [BoardColors.id] once decoded. */
    val boardThemeId: String = DEFAULT_THEME_ID,
    /** Always a known [PieceSet.id] once decoded. */
    val pieceSetId: String = DEFAULT_PIECE_SET_ID,
    /** The chosen coach (decision D20). Always a known [Mascot.id] once decoded. */
    val mascotId: String = Mascot.DEFAULT.id,
    /** How much bots chat during games (review R6). */
    val chatLevel: ChatLevel = ChatLevel.SOME,
    /**
     * How many games from the linked site the player wants saved in total, until the import has
     * finished (an import cut short carries on later). "Import 500 more" with 257 saved is 757.
     */
    val importTarget: Int? = null,
    /** Game review depth (Settings > Engine). */
    val reviewDepth: ReviewDepth = ReviewDepth.NORMAL,
    /** May the free online engine stand in when Stockfish on the phone fails? (plan 4.5, D18) */
    val onlineEngine: Boolean = true,
    /** The app's colour theme (D37). */
    val appTheme: AppTheme = AppTheme.DEFAULT,
    /** Light, dark, or the phone's setting (D36). */
    val appearance: Appearance = Appearance.DEFAULT,
) {
    val mascot: Mascot get() = Mascot.fromId(mascotId)

    val boardTheme: BoardTheme get() = BoardTheme(BoardColors.fromId(boardThemeId), PieceSet.fromId(pieceSetId))

    companion object {
        val DEFAULT_THEME_ID = BoardColors.DEFAULT.id
        val DEFAULT_PIECE_SET_ID = PieceSet.DEFAULT.id
    }
}

/**
 * Converts [AppSettings] to and from a flat key/value map (the shape DataStore stores).
 * Decoding never throws: a missing, mistyped or unknown value falls back to its default,
 * so a corrupted preference can never crash the app.
 */
object AppSettingsCodec {
    const val SOUND = "sound_enabled"
    const val HAPTICS = "haptics_enabled"
    const val COORDINATES = "show_coordinates"
    const val LEGAL_DOTS = "show_legal_dots"
    const val REDUCE_MOTION = "reduce_motion"
    const val AUTO_QUEEN = "auto_queen"
    const val ORIENTATION = "board_orientation"
    const val BOARD_THEME = "board_theme_id"
    const val PIECE_SET = "piece_set_id"
    const val MASCOT = "mascot_id"
    const val BOT_CHAT = "bot_chat"
    const val IMPORT_TARGET = "import_target"
    const val REVIEW_DEPTH = "review_depth"
    const val ONLINE_ENGINE = "online_engine"
    const val APP_THEME = "app_theme"
    const val APPEARANCE = "appearance"

    /** Marks that settings formerly kept elsewhere were copied in (see [SettingsStore]). */
    const val LEGACY_IMPORTED = "legacy_imported_v1"

    fun decode(values: Map<String, Any?>): AppSettings {
        val defaults = AppSettings()
        fun bool(key: String, fallback: Boolean) = values[key] as? Boolean ?: fallback
        val orientation = (values[ORIENTATION] as? String)
            ?.let { name -> BoardOrientation.entries.firstOrNull { it.name == name } }
            ?: defaults.boardOrientation
        return AppSettings(
            soundEnabled = bool(SOUND, defaults.soundEnabled),
            hapticsEnabled = bool(HAPTICS, defaults.hapticsEnabled),
            showCoordinates = bool(COORDINATES, defaults.showCoordinates),
            showLegalDots = bool(LEGAL_DOTS, defaults.showLegalDots),
            reduceMotion = bool(REDUCE_MOTION, defaults.reduceMotion),
            autoQueen = bool(AUTO_QUEEN, defaults.autoQueen),
            boardOrientation = orientation,
            boardThemeId = BoardColors.fromId(values[BOARD_THEME] as? String).id,
            pieceSetId = PieceSet.fromId(values[PIECE_SET] as? String).id,
            mascotId = Mascot.fromId(values[MASCOT] as? String).id,
            chatLevel = (values[BOT_CHAT] as? String)?.let { name -> ChatLevel.entries.firstOrNull { it.name == name } } ?: defaults.chatLevel,
            // Before Phase 7 the target was an ImportDepth name ("LAST_500").
            importTarget = (values[IMPORT_TARGET] as? String)?.let { text ->
                text.toIntOrNull()?.takeIf { it > 0 } ?: ImportDepth.entries.firstOrNull { it.name == text }?.maxGames
            },
            reviewDepth = (values[REVIEW_DEPTH] as? String)?.let { name -> ReviewDepth.entries.firstOrNull { it.name == name } } ?: defaults.reviewDepth,
            onlineEngine = bool(ONLINE_ENGINE, defaults.onlineEngine),
            appTheme = AppTheme.fromName(values[APP_THEME] as? String),
            appearance = Appearance.fromName(values[APPEARANCE] as? String),
        )
    }

    /** Booleans and strings only, so every value maps onto a DataStore key type. */
    fun encode(settings: AppSettings): Map<String, Any> = linkedMapOf(
        SOUND to settings.soundEnabled,
        HAPTICS to settings.hapticsEnabled,
        COORDINATES to settings.showCoordinates,
        LEGAL_DOTS to settings.showLegalDots,
        REDUCE_MOTION to settings.reduceMotion,
        AUTO_QUEEN to settings.autoQueen,
        ORIENTATION to settings.boardOrientation.name,
        BOARD_THEME to settings.boardThemeId,
        PIECE_SET to settings.pieceSetId,
        MASCOT to settings.mascotId,
        BOT_CHAT to settings.chatLevel.name,
        IMPORT_TARGET to (settings.importTarget?.toString() ?: ""),
        REVIEW_DEPTH to settings.reviewDepth.name,
        ONLINE_ENGINE to settings.onlineEngine,
        APP_THEME to settings.appTheme.name,
        APPEARANCE to settings.appearance.name,
    )
}

/** Where [AppSettings] live. The app uses DataStore; tests and previews use memory. */
interface SettingsStore {
    val settings: Flow<AppSettings>

    suspend fun update(transform: (AppSettings) -> AppSettings)

    /**
     * Copies the sound setting from the old learning profile, once. After that the profile
     * value is ignored, so a later change made here is never overwritten.
     */
    suspend fun importLegacyOnce(soundEnabled: Boolean)
}

class InMemorySettingsStore(initial: AppSettings = AppSettings()) : SettingsStore {
    private val state = MutableStateFlow(initial)
    private var legacyImported = false

    override val settings: StateFlow<AppSettings> = state.asStateFlow()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.update(transform)
    }

    override suspend fun importLegacyOnce(soundEnabled: Boolean) {
        if (legacyImported) return
        legacyImported = true
        state.update { it.copy(soundEnabled = soundEnabled) }
    }
}
