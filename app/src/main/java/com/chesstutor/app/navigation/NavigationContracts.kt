package com.chesstutor.app.navigation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class Side(val char: Char) {
    WHITE('w'),
    BLACK('b'),
    RANDOM('w');

    fun resolve(): Side = when (this) {
        WHITE -> WHITE
        BLACK -> BLACK
        RANDOM -> if (kotlin.random.Random.nextBoolean()) WHITE else BLACK
    }

    fun resolveChar(): Char = resolve().char

    companion object {
        fun fromChar(c: Char): Side = if (c == 'b' || c == 'B') BLACK else WHITE
    }
}

data class BotConfig(
    val tierKey: String = "CLUB",
    val botName: String = "Marcus",
    val elo: Int = 1100,
    val userSide: Side = Side.WHITE
)

enum class OpeningMode(val label: String, val subtitle: String) {
    FREE("Free Play", "Standard game from the starting position"),
    LEARN_LINE("Learn the Line", "Play book moves with live deviation feedback"),
    START_FROM_LINE("Start from Line", "Begin from the opening position and play freely"),
    SURPRISE_ME("Surprise Me", "Practise a random opening from the repertoire")
}

sealed interface TrainRequest {
    data class Lesson(val topicId: String) : TrainRequest
    data class Theme(val theme: String, val band: IntRange = 400..2800) : TrainRequest
    data object ReviewDue : TrainRequest
    data object Daily : TrainRequest
    data class FromGame(val gameId: String, val ply: Int) : TrainRequest
    data class Opening(val lineId: String) : TrainRequest
}

sealed interface PlayRequest {
    data object Free : PlayRequest
    data class Opening(
        val lineId: String,
        val userSide: Side = Side.WHITE,
        val mode: OpeningMode = OpeningMode.LEARN_LINE
    ) : PlayRequest
    data class FromPosition(val fen: String, val userSide: Side = Side.WHITE) : PlayRequest
    data class Rematch(val bot: BotConfig) : PlayRequest {
        constructor(botRating: Int, userSide: Side = Side.WHITE) : this(
            BotConfig(elo = botRating, userSide = userSide)
        )
    }
}

/** What the app shows at the top level: a plain background while loading, onboarding, or the tabs. */
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

sealed interface NavCommand {
    data class OpenLearn(val topicId: String? = null) : NavCommand
    data class StartTrain(val request: TrainRequest) : NavCommand
    data class StartPlay(val request: PlayRequest) : NavCommand
    data object OpenReview : NavCommand
}

/**
 * Application navigation contract that converts typed [TrainRequest] and [PlayRequest]
 * actions into tab switches and ViewModel session starts.
 */
class AppNavigator {
    private val _commands = MutableSharedFlow<NavCommand>(extraBufferCapacity = 16)
    val commands: SharedFlow<NavCommand> = _commands.asSharedFlow()

    fun dispatch(command: NavCommand) {
        _commands.tryEmit(command)
    }

    fun navigate(command: NavCommand): Boolean {
        return _commands.tryEmit(command)
    }
}
