package com.chesstutor.app.domain.opening

import com.chesstutor.app.core.PieceColor
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

/** One of the openings the trainer teaches, from the side the player plays (plan 6C). */
data class TrainerOpening(
    val id: String,
    val family: String,
    val side: PieceColor,
    val variations: List<String>,
    /** Full database names, for an opening whose lines are filed under several families (the London). */
    val fullNames: List<String> = emptyList(),
) {
    val title: String get() = family
}

/** A line to learn by heart: the database line named exactly [name], played as [side]. */
data class TrainerLine(val name: String, val moves: List<String>, val side: PieceColor) {
    /** Unique across sides: the Queen's Gambit Declined is trained as White and as Black. */
    val id: String get() = (if (side == PieceColor.WHITE) "w:" else "b:") + name

    /** "Najdorf Variation" (the part after the family). */
    val variation: String get() = name.substringAfter(": ", name)

    /** How many moves the player makes in this line. */
    val playerMoves: Int get() = moves.indices.count { isPlayerPly(it) }

    /** True for the plies the player makes (White's are the even ones). */
    fun isPlayerPly(ply: Int): Boolean = (ply % 2 == 0) == (side == PieceColor.WHITE)
}

/** How well a line is known: spaced repetition like the mistakes review. */
data class DrillProgress(
    val lineId: String,
    /** 0 when new or just missed; each clean run moves it up one step of [OpeningTrainer.INTERVAL_DAYS]. */
    val stage: Int,
    val dueAt: Long,
    val attempts: Int,
    val cleanRuns: Int,
    val lastPlayedAt: Long,
) {
    /** Learned once it has been played cleanly at least twice in a row. */
    val learned: Boolean get() = stage >= 2
}

/**
 * The opening trainer (plan 6C, D32 fix list): 15 openings, a few main variations each, taken
 * from the CC0 opening database. The player plays their side's moves from memory; the other
 * side's book replies come by themselves. A clean run moves the line further apart in time,
 * a slip brings it back today.
 */
object OpeningTrainer {
    /** Days until a line comes back, by stage. */
    val INTERVAL_DAYS = listOf(0, 1, 3, 7, 14, 30, 60)

    private val W = PieceColor.WHITE
    private val B = PieceColor.BLACK

    val openings: List<TrainerOpening> = listOf(
        TrainerOpening("italian", "Italian Game", W, listOf("Giuoco Piano", "Giuoco Pianissimo", "Two Knights Defense", "Evans Gambit", "Classical Variation")),
        TrainerOpening("ruy-lopez", "Ruy Lopez", W, listOf("Morphy Defense", "Closed", "Open", "Berlin Defense", "Exchange Variation", "Marshall Attack")),
        TrainerOpening("scotch", "Scotch Game", W, listOf("Classical Variation", "Schmidt Variation", "Mieses Variation", "Steinitz Variation", "Scotch Gambit")),
        TrainerOpening("queens-gambit", "Queen's Gambit Declined", W, listOf("Exchange Variation", "Tarrasch Defense", "Semi-Tarrasch Defense", "Cambridge Springs Defense", "Ragozin Defense")),
        TrainerOpening(
            "london", "London System", W, emptyList(),
            fullNames = listOf(
                "London System", "London System, with Bd3", "London System, with Be2", "London System: Poisoned Pawn Variation",
            ),
        ),
        TrainerOpening("english", "English Opening", W, listOf("King's English Variation", "Symmetrical Variation", "Anglo-Indian Defense", "Agincourt Defense", "Mikenas-Carls Variation")),
        TrainerOpening("vienna", "Vienna Game", W, listOf("Vienna Gambit", "Mieses Variation", "Stanley Variation", "Max Lange Defense", "Falkbeer Variation")),
        TrainerOpening("sicilian", "Sicilian Defense", B, listOf("Najdorf Variation", "Dragon Variation", "Taimanov Variation", "Classical Variation", "Scheveningen Variation", "Alapin Variation", "Closed")),
        TrainerOpening("french", "French Defense", B, listOf("Winawer Variation", "Classical Variation", "Tarrasch Variation", "Advance Variation", "Exchange Variation", "Rubinstein Variation")),
        TrainerOpening("caro-kann", "Caro-Kann Defense", B, listOf("Classical Variation", "Advance Variation", "Exchange Variation", "Panov Attack", "Two Knights Attack", "Karpov Variation")),
        TrainerOpening("scandinavian", "Scandinavian Defense", B, listOf("Main Line", "Modern Variation", "Mieses-Kotroc Variation", "Icelandic-Palme Gambit", "Portuguese Gambit")),
        TrainerOpening("kings-indian", "King's Indian Defense", B, listOf("Orthodox Variation", "Sämisch Variation", "Four Pawns Attack", "Fianchetto Variation", "Averbakh Variation")),
        TrainerOpening("slav", "Slav Defense", B, listOf("Modern Line", "Exchange Variation", "Czech Variation", "Chebanenko Variation", "Quiet Variation")),
        TrainerOpening("nimzo-indian", "Nimzo-Indian Defense", B, listOf("Classical Variation", "Rubinstein System", "Normal Variation", "Sämisch Variation", "Leningrad Variation")),
        TrainerOpening("qgd-black", "Queen's Gambit Declined", B, listOf("Exchange Variation", "Tarrasch Defense", "Cambridge Springs Defense", "Ragozin Defense", "Harrwitz Attack")),
    )

    fun byId(id: String): TrainerOpening? = openings.firstOrNull { it.id == id }

    /**
     * The lines of [opening]: for each variation the deepest database line with exactly its
     * name (names repeat for deeper versions of a line), or for each of its [TrainerOpening.fullNames].
     */
    fun linesFor(opening: TrainerOpening, tree: OpeningTree): List<TrainerLine> {
        val names = opening.fullNames.ifEmpty { opening.variations.map { "${opening.family}: $it" } }
        return names.mapNotNull { name ->
            tree.openings.filter { it.name == name }.maxByOrNull { it.moves.size }?.let { TrainerLine(name, it.moves, opening.side) }
        }
    }

    /** After a run of a line: a clean run moves it one stage on, a slip sends it back to today. */
    fun afterRun(previous: DrillProgress?, lineId: String, clean: Boolean, now: Long): DrillProgress {
        val stage = if (clean) ((previous?.stage ?: 0) + 1).coerceAtMost(INTERVAL_DAYS.lastIndex) else 0
        return DrillProgress(
            lineId = lineId,
            stage = stage,
            dueAt = now + TimeUnit.DAYS.toMillis(INTERVAL_DAYS[stage].toLong()),
            attempts = (previous?.attempts ?: 0) + 1,
            cleanRuns = (previous?.cleanRuns ?: 0) + if (clean) 1 else 0,
            lastPlayedAt = now,
        )
    }

    /** Lines already started that are due again. New lines are not "due"; they are offered separately. */
    fun due(lines: List<TrainerLine>, progress: Map<String, DrillProgress>, now: Long): List<TrainerLine> =
        lines.filter { line -> progress[line.id]?.let { it.dueAt <= now } == true }
            .sortedBy { progress.getValue(it.id).dueAt }

    /** The next line to learn: the first one never played. */
    fun nextNew(lines: List<TrainerLine>, progress: Map<String, DrillProgress>): TrainerLine? =
        lines.firstOrNull { it.id !in progress }
}

/** Where opening drill progress is kept. */
interface OpeningDrillRepository {
    fun observeDrills(): Flow<Map<String, DrillProgress>>

    suspend fun saveDrill(progress: DrillProgress)
}
