package com.chesstutor.app.data.settings

import com.chesstutor.app.board.BoardColors
import com.chesstutor.app.board.BoardTheme
import com.chesstutor.app.board.PieceSet
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
