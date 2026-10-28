package com.chesstutor.app.navigation

/**
 * The four bottom-bar tabs (plan section 3). Each tab has its own back stack.
 */
enum class TopTab(val graphRoute: String, val startRoute: String, val label: String) {
    HOME("home_graph", "home", "Home"),
    LEARN("learn_graph", "learn", "Learn"),
    PUZZLES("puzzles_graph", "puzzles", "Puzzles"),
    ACCOUNT("account_graph", "account", "Account"),
}

object AppRoutes {
    const val ENGINE_HEALTH = "account/engine"
    const val DESIGN_GALLERY = "account/design"
    const val BOARD_SETTINGS = "account/board"
    /** The Settings page (plan 6.4), replacing the old settings sheet. */
    const val SETTINGS = "account/settings"
    /** Every bundled part and its licence, with the licence texts (Settings > About). */
    const val LICENCES = "account/licences"
    /** One saved game: result, opening, final position and moves (plan section 6.4). */
    const val GAME_ID_ARG = "gameId"
    const val GAME_PATTERN = "account/game/{$GAME_ID_ARG}"
    fun game(id: String): String = "account/game/${encode(id)}"
    /** A game's review on the board, full screen (no bottom bar); [analyse] starts Stockfish on arrival. */
    const val ANALYSE_ARG = "analyse"
    const val REVIEW_PATTERN = "account/review/{$GAME_ID_ARG}?$ANALYSE_ARG={$ANALYSE_ARG}"
    fun review(id: String, analyse: Boolean = false): String =
        "account/review/${encode(id)}" + if (analyse) "?$ANALYSE_ARG=true" else ""
    /** The puzzle player, full screen inside the Puzzles tab. */
    const val PUZZLE_PLAYER = "puzzles/play"
    const val PUZZLE_MODES = "puzzles/modes"
    /** Every level of the five worlds (D43). */
    const val PUZZLE_LEVELS = "puzzles/levels"
    /** Starts the daily puzzle (from Home's daily challenge card) and opens the player. */
    const val PUZZLE_DAILY = "puzzles/daily"
    /** Mistakes review: due positions from the player's own games, full screen on the board. */
    const val MISTAKE_REVIEW = "puzzles/review"
    /** Starts a course's practice set (plan 6C) and opens the player. */
    const val PUZZLE_PRACTICE_PATTERN = "puzzles/practice/{courseId}"
    fun puzzlePractice(courseId: String): String = "puzzles/practice/${encode(courseId)}"
    /** Starts puzzles with a lesson's themes (comma-separated Lichess names) and opens the player. */
    const val THEMES_ARG = "themes"
    const val PUZZLE_RELATED_PATTERN = "puzzles/related/{$THEMES_ARG}"
    fun puzzleRelated(themes: List<String>): String = "puzzles/related/${encode(themes.joinToString(","))}"
    fun parseThemes(value: String?): List<String> = value.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
    /** The lesson player, full screen inside the Learn tab (the lesson is chosen before opening it). */
    const val LESSON = "learn/lesson"
    /** The opening trainer's list (plan 6C), and one line on the board (full screen). */
    const val TRAINER = "learn/trainer"
    const val TRAINER_DRILL = "learn/trainer/drill"
    /** Opens one lesson from another tab (Home's Continue card), then shows the player. */
    const val LESSON_ID_ARG = "lessonId"
    const val LESSON_OPEN_PATTERN = "learn/open/{$LESSON_ID_ARG}"
    fun openLesson(id: String): String = "learn/open/${encode(id)}"
    /** A Learn category page (courses with search and filters); the argument is a `LessonCategory.key`. */
    const val CATEGORY_ARG = "category"
    const val LEARN_CATEGORY_PATTERN = "learn/category/{$CATEGORY_ARG}"
    fun learnCategory(key: String): String = "learn/category/$key"
    /** A course page: intro, numbered lessons and practice links. */
    const val COURSE_ID_ARG = "courseId"
    const val COURSE_PATTERN = "learn/course/{$COURSE_ID_ARG}"
    fun course(id: String): String = "learn/course/$id"
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
    /** "white" or "black" when the engine plays that side; empty when nobody does. */
    const val ANALYSIS_ENGINE_ARG = "engine"
    /** The engine's strength; 0 for full strength. */
    const val ANALYSIS_ELO_ARG = "elo"
    const val ANALYSIS_PATTERN = "analysis?$ANALYSIS_FEN_ARG={$ANALYSIS_FEN_ARG}&$ANALYSIS_LINE_ARG={$ANALYSIS_LINE_ARG}" +
        "&$ANALYSIS_ENGINE_ARG={$ANALYSIS_ENGINE_ARG}&$ANALYSIS_ELO_ARG={$ANALYSIS_ELO_ARG}"

    /**
     * Route to Explore (the analysis board) on [fen], with [line] (UCI moves) loaded as the
     * main line, for example a puzzle's solution, and optionally the engine playing
     * [engineSide] at [engineElo] (null: full strength). Spaces and slashes are encoded.
     */
    fun analysis(fen: String, line: List<String> = emptyList(), engineSide: String? = null, engineElo: Int? = null): String =
        "analysis?$ANALYSIS_FEN_ARG=${encode(fen)}&$ANALYSIS_LINE_ARG=${encode(line.joinToString(" "))}" +
            (engineSide?.let { "&$ANALYSIS_ENGINE_ARG=$it&$ANALYSIS_ELO_ARG=${engineElo ?: 0}" } ?: "")

    /** Every bot, your own bots, "Build a bot" and "Watch two" (D42). */
    const val BOTS = "home/bots"
    /** Explore's start page on Home: choose the position and who the engine plays. */
    const val EXPLORE = "home/explore"

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    /** Full screen (no bottom bar), like every activity that hosts a board. */
    const val BOARD_LAB = "board/lab"

    private val accountPages = setOf(ENGINE_HEALTH, DESIGN_GALLERY, BOARD_SETTINGS, SETTINGS, LICENCES, GAME_PATTERN)
    /** Learn's browsing pages keep the bottom bar; the lesson player (a board) hides it. */
    private val learnPages = setOf(LEARN_CATEGORY_PATTERN, COURSE_PATTERN, TRAINER)
    /** Home's browsing pages keep the bottom bar too. */
    private val homePages = setOf(BOTS)
    private val puzzlePages = setOf(PUZZLE_LEVELS)

    /**
     * The bottom bar shows on tab pages and hides on full-screen activities (anything that
     * hosts a board). Unknown routes hide it, which is the safe choice for a new full-screen
     * activity.
     */
    fun showsBottomBar(route: String?): Boolean {
        if (route == null) return true
        if (TopTab.entries.any { it.startRoute == route }) return true
        return route in accountPages || route in learnPages || route in homePages || route in puzzlePages
    }

    /** The tab a destination belongs to, used to highlight the bottom bar. */
    fun tabFor(route: String?): TopTab? = when {
        route == null -> null
        route in accountPages -> TopTab.ACCOUNT
        route in learnPages -> TopTab.LEARN
        route in homePages -> TopTab.HOME
        route in puzzlePages -> TopTab.PUZZLES
        else -> TopTab.entries.firstOrNull { it.startRoute == route }
    }
}
