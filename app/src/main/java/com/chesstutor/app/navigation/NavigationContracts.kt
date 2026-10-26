package com.chesstutor.app.navigation

/** What the app shows at the top level: a plain background while loading, or the tabs. */
enum class StartScreen {
    LOADING,
    APP;

    companion object {
        /**
         * A blank screen until the saved profile is read, then the app. Onboarding was dropped
         * (D30): new players get a welcome card on Home instead.
         */
        fun of(profileLoaded: Boolean): StartScreen = if (profileLoaded) APP else LOADING
    }
}

/** The welcome card's answers (D30): how strong the player says they are, as a starting rating. */
enum class StartingLevel(val label: String, val detail: String, val rating: Int) {
    NEW("I'm new to chess", "Start with the basics in Learn", 400),
    RULES("I know the rules", "Play bots and puzzles at an easy level", 800),
    PLAYER("I play online", "Link Chess.com or Lichess for your real rating", 1200),
}

/** True while a new player has not answered Home's welcome card. */
fun needsWelcome(assessmentState: String): Boolean = assessmentState != "COMPLETE"
