package com.chesstutor.app.navigation

import com.chesstutor.app.domain.LearnCategory

/**
 * The four bottom-bar tabs (plan section 3). Each tab has its own back stack.
 */
enum class TopTab(val graphRoute: String, val startRoute: String, val label: String) {
    HOME("home_graph", "home", "Home"),
    LEARN("learn_graph", "learn", "Learn"),
    PUZZLES("puzzles_graph", "puzzles", "Puzzles"),
    ACCOUNT("account_graph", "account", "Account"),
}

/**
 * Indices of the pre-revamp screens, which are still driven by `AppUiState.tab`.
 * They live behind the new shell until each one is replaced (strangler approach).
 */
object LegacyTab {
    /** CoachScreen: the lesson and drill player. Removed in Phase 4. */
    const val SESSION = 0
    /** CurriculumScreen: the old Learn list. Removed in Phase 4. */
    const val CURRICULUM = 1
    /** ArenaScreen: games against bots. Removed in Phase 3 (Home and its games replace it); the index stays reserved. */
    const val ARENA = 2
    /** ReviewScreen: linked accounts, game history and review. Removed in Phase 5. */
    const val REVIEW = 3
    /**
     * PuzzleScreen: the old puzzle trainer, replaced by the Puzzles tab in Phase 2. It is no
     * longer listed: it saves to the old SharedPreferences store, so playing it would split
     * the player's stats from the new history in Room.
     */
    const val PUZZLES = 4

    /** The classic screens still offered (Account > Classic screens). */
    val ALL = listOf(SESSION, CURRICULUM, REVIEW)

    fun title(index: Int): String = when (index) {
        SESSION -> "Lesson and drill player"
        CURRICULUM -> "Lessons (classic)"
        ARENA -> "Play a bot (classic)"
        REVIEW -> "Games and review (classic)"
        PUZZLES -> "Puzzle trainer (classic)"
        else -> "Unknown screen"
    }
}

object AppRoutes {
    const val ENGINE_HEALTH = "account/engine"
    const val DEVELOPER_MENU = "account/developer"
    const val DESIGN_GALLERY = "account/design"
    const val BOARD_SETTINGS = "account/board"
    /** The puzzle player, full screen inside the Puzzles tab. */
    const val PUZZLE_PLAYER = "puzzles/play"
    const val PUZZLE_MODES = "puzzles/modes"
    /** Starts the daily puzzle (from Home's daily challenge card) and opens the player. */
    const val PUZZLE_DAILY = "puzzles/daily"
    /** A bot's page (pick colour, clock and rules), then the game itself, full screen in Home. */
    const val BOT_ID_ARG = "botId"
    const val BOT_PATTERN = "home/bot/{$BOT_ID_ARG}"
    fun bot(id: String): String = "home/bot/$id"
    const val PLAY = "home/play"
    /** Opening Battle's setup page and its opening picker. */
    const val BATTLE = "home/battle"
    /** "From a position": the board editor, then a game from there. */
    const val POSITION = "home/position"
    /** "Watch two bots": pick both, then a bot-vs-bot game. */
    const val WATCH = "home/watch"
    const val BATTLE_PICK = "home/battle/pick"
    /** The bot builder: a new bot, or one of "My bots" to edit. */
    const val BUILDER_PATTERN = "home/builder?$BOT_ID_ARG={$BOT_ID_ARG}"
    /** The full opening list for the bot being built; the side is "white" or "black". */
    const val SIDE_ARG = "side"
    const val BUILDER_OPENINGS_PATTERN = "home/builder/openings/{$SIDE_ARG}"
    fun builderOpenings(side: String): String = "home/builder/openings/$side"
    fun builder(botId: String? = null): String = if (botId == null) "home/builder" else "home/builder?$BOT_ID_ARG=$botId"

    const val ANALYSIS_FEN_ARG = "fen"
    const val ANALYSIS_LINE_ARG = "line"
    const val ANALYSIS_PATTERN = "analysis?$ANALYSIS_FEN_ARG={$ANALYSIS_FEN_ARG}&$ANALYSIS_LINE_ARG={$ANALYSIS_LINE_ARG}"

    /**
     * Route to the analysis board on [fen], with [line] (UCI moves) loaded as the main line,
     * for example a puzzle's solution. Spaces and slashes are encoded for the URL.
     */
    fun analysis(fen: String, line: List<String> = emptyList()): String =
        "analysis?$ANALYSIS_FEN_ARG=${encode(fen)}&$ANALYSIS_LINE_ARG=${encode(line.joinToString(" "))}"

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    /** Full screen (no bottom bar), like every activity that hosts a board. */
    const val BOARD_LAB = "board/lab"

    const val LEGACY_CATEGORY_ARG = "category"

    /** One full-screen destination hosts every legacy screen. */
    const val LEGACY_PATTERN = "legacy?$LEGACY_CATEGORY_ARG={$LEGACY_CATEGORY_ARG}"

    /** Route to the legacy host, optionally opening the old Learn list on [category]. */
    fun legacy(category: LearnCategory? = null): String =
        if (category == null) "legacy" else "legacy?$LEGACY_CATEGORY_ARG=${category.name}"

    fun parseCategory(value: String?): LearnCategory? =
        value?.let { name -> LearnCategory.entries.firstOrNull { it.name == name } }

    private val accountPages = setOf(ENGINE_HEALTH, DEVELOPER_MENU, DESIGN_GALLERY, BOARD_SETTINGS)

    /**
     * The bottom bar shows on tab pages and hides on full-screen activities (any legacy
     * screen, since each one can host a board). Unknown routes hide it, which is the safe
     * choice for a new full-screen activity.
     */
    fun showsBottomBar(route: String?): Boolean {
        if (route == null) return true
        if (TopTab.entries.any { it.startRoute == route }) return true
        return route in accountPages
    }

    /** The tab a destination belongs to, used to highlight the bottom bar. */
    fun tabFor(route: String?): TopTab? = when {
        route == null -> null
        route in accountPages -> TopTab.ACCOUNT
        else -> TopTab.entries.firstOrNull { it.startRoute == route }
    }
}
