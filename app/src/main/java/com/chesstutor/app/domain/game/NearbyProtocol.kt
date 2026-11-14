package com.chesstutor.app.domain.game

import com.chesstutor.app.core.PieceColor
import com.squareup.moshi.Moshi
import kotlin.random.Random

/**
 * What two phones say to each other in a nearby game (Phase 11C): one JSON object per line over a
 * socket on the same Wi-Fi or hotspot. The host decides the game and sends [Setup]; after that each
 * phone sends its own moves with the time its player has left, which the other phone takes as true.
 */
sealed interface NearbyMessage {
    data class Hello(val name: String, val version: Int = NearbyProtocol.VERSION) : NearbyMessage

    /** The game, from the host's side: [setup]'s owner is the host. */
    data class Setup(val setup: FriendSetup) : NearbyMessage

    data class Moved(val uci: String, val remainingMs: Long?) : NearbyMessage

    data object Resign : NearbyMessage

    data object DrawOffer : NearbyMessage

    data class DrawAnswer(val accept: Boolean) : NearbyMessage

    /** The sender's own clock ran out (each phone judges only its own player's time). */
    data object Flag : NearbyMessage

    /** The joiner would like another game; the host answers with a new [Setup]. */
    data object Rematch : NearbyMessage

    data object Bye : NearbyMessage
}

object NearbyProtocol {
    const val VERSION = 1

    /** Codes leave out I and O, which read like 1 and 0. */
    private const val LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    const val CODE_LENGTH = 4

    fun newCode(random: Random = Random.Default): String = String(CharArray(CODE_LENGTH) { LETTERS[random.nextInt(LETTERS.length)] })

    /** The code as typed: upper case, letters only, at most [CODE_LENGTH]. */
    fun cleanCode(text: String): String = text.uppercase().filter { it in LETTERS }.take(CODE_LENGTH)

    private val adapter = Moshi.Builder().build().adapter(Any::class.java)

    fun encode(message: NearbyMessage): String {
        val map: Map<String, Any?> = when (message) {
            is NearbyMessage.Hello -> mapOf("t" to "hello", "name" to message.name, "v" to message.version)
            is NearbyMessage.Setup -> mapOf("t" to "setup") + setupMap(message.setup)
            is NearbyMessage.Moved -> mapOf("t" to "move", "uci" to message.uci, "ms" to message.remainingMs)
            NearbyMessage.Resign -> mapOf("t" to "resign")
            NearbyMessage.DrawOffer -> mapOf("t" to "draw")
            is NearbyMessage.DrawAnswer -> mapOf("t" to "drawAnswer", "accept" to message.accept)
            NearbyMessage.Flag -> mapOf("t" to "flag")
            NearbyMessage.Rematch -> mapOf("t" to "rematch")
            NearbyMessage.Bye -> mapOf("t" to "bye")
        }
        return adapter.toJson(map.filterValues { it != null })
    }

    /** The message on [line], or null for anything this version does not understand. */
    fun decode(line: String): NearbyMessage? {
        val map = runCatching { adapter.fromJson(line) as? Map<*, *> }.getOrNull() ?: return null
        return runCatching {
            when (map["t"]) {
                "hello" -> NearbyMessage.Hello(map["name"] as String, (map["v"] as Number).toInt())
                "setup" -> NearbyMessage.Setup(setupOf(map))
                "move" -> NearbyMessage.Moved(map["uci"] as String, (map["ms"] as? Number)?.toLong())
                "resign" -> NearbyMessage.Resign
                "draw" -> NearbyMessage.DrawOffer
                "drawAnswer" -> NearbyMessage.DrawAnswer(map["accept"] as Boolean)
                "flag" -> NearbyMessage.Flag
                "rematch" -> NearbyMessage.Rematch
                "bye" -> NearbyMessage.Bye
                else -> null
            }
        }.getOrNull()
    }

    private fun setupMap(setup: FriendSetup): Map<String, Any?> = mapOf(
        "white" to setup.whiteName,
        "black" to setup.blackName,
        "host" to setup.ownerColor.name,
        "wMs" to setup.whiteClock?.initialMs,
        "wInc" to setup.whiteClock?.incrementMs,
        "bMs" to setup.blackClock?.initialMs,
        "bInc" to setup.blackClock?.incrementMs,
        "fen" to setup.startFen,
        "moves" to setup.openingMoves,
    )

    private fun setupOf(map: Map<*, *>): FriendSetup {
        fun clock(ms: String, inc: String) = (map[ms] as? Number)?.let { PlayerClock(it.toLong(), (map[inc] as? Number)?.toLong() ?: 0) }
        return FriendSetup(
            whiteName = map["white"] as String,
            blackName = map["black"] as String,
            ownerColor = PieceColor.valueOf(map["host"] as String),
            whiteClock = clock("wMs", "wInc"),
            blackClock = clock("bMs", "bInc"),
            startFen = map["fen"] as String,
            openingMoves = (map["moves"] as? List<*>).orEmpty().map { it as String },
            takebacks = false,
            seating = Seating.SIDE_BY_SIDE,
        )
    }
}
