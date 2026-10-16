package com.chesstutor.app.domain.puzzle

/**
 * One puzzle from the bundled pack (built by tools/build_puzzle_pack.py from the Lichess puzzle
 * database).
 *
 * Lichess convention: [fen] is the position BEFORE the opponent's move. [moves] is a UCI list whose
 * first move is the opponent's move (played automatically to set the puzzle up); the solver then
 * has to find moves[1], moves[3], ... while the opponent answers with moves[2], moves[4], ...
 */
data class Puzzle(
    val id: String,
    val fen: String,
    val moves: List<String>,
    val rating: Int,
    val primaryTheme: String,
    val themes: Set<String>,
    val popularity: Int = 0
) {
    /** How many moves the solver has to find. */
    val solverMoveCount: Int get() = moves.size / 2
}

object PuzzleParser {
    private val UCI_MOVE = Regex("^[a-h][1-8][a-h][1-8][qrbn]?$")

    /**
     * Parses one tab-separated pack line:
     * `id  fen  moves  rating  primaryTheme  themes  popularity`.
     * Returns null for anything malformed so one bad line can never break the whole pack.
     */
    fun parseLine(line: String): Puzzle? {
        if (line.isBlank()) return null
        val cols = line.trimEnd('\r', '\n').split('\t')
        if (cols.size < 6) return null

        val id = cols[0].trim()
        val fen = cols[1].trim()
        val moves = cols[2].trim().split(' ').filter { it.isNotEmpty() }.map { it.lowercase() }
        val rating = cols[3].trim().toIntOrNull() ?: return null
        val primaryTheme = cols[4].trim()
        val themes = cols[5].trim().split(' ').filter { it.isNotEmpty() }.toSet()
        val popularity = cols.getOrNull(6)?.trim()?.toIntOrNull() ?: 0

        if (id.isEmpty() || primaryTheme.isEmpty()) return null
        if (fen.split(' ').size < 4) return null
        if (moves.size < 2 || moves.any { !UCI_MOVE.matches(it) }) return null

        return Puzzle(
            id = id,
            fen = fen,
            moves = moves,
            rating = rating,
            primaryTheme = primaryTheme,
            themes = if (themes.isEmpty()) setOf(primaryTheme) else themes,
            popularity = popularity
        )
    }
}

/** Human-readable names for the Lichess theme tags used in the pack. */
object PuzzleThemes {
    private val NAMES = mapOf(
        "mateIn1" to "Mate in 1",
        "mateIn2" to "Mate in 2",
        "mateIn3" to "Mate in 3",
        "backRankMate" to "Back-rank mate",
        "smotheredMate" to "Smothered mate",
        "anastasiaMate" to "Anastasia's mate",
        "arabianMate" to "Arabian mate",
        "hookMate" to "Hook mate",
        "fork" to "Fork",
        "pin" to "Pin",
        "skewer" to "Skewer",
        "discoveredAttack" to "Discovered attack",
        "doubleCheck" to "Double check",
        "hangingPiece" to "Hanging piece",
        "trappedPiece" to "Trapped piece",
        "capturingDefender" to "Remove the defender",
        "deflection" to "Deflection",
        "attraction" to "Attraction",
        "clearance" to "Clearance",
        "interference" to "Interference",
        "xRayAttack" to "X-ray attack",
        "intermezzo" to "In-between move",
        "quietMove" to "Quiet move",
        "zugzwang" to "Zugzwang",
        "sacrifice" to "Sacrifice",
        "exposedKing" to "Exposed king",
        "defensiveMove" to "Defensive move",
        "advancedPawn" to "Advanced pawn",
        "promotion" to "Promotion",
        "pawnEndgame" to "Pawn endgame",
        "rookEndgame" to "Rook endgame",
        "bishopEndgame" to "Bishop endgame",
        "knightEndgame" to "Knight endgame",
        "queenEndgame" to "Queen endgame"
    )

    private val ORDER: List<String> = NAMES.keys.toList()

    /** Sort key that lists mates first, then tactics, then endgames (unknown tags last). */
    fun order(theme: String): Int = ORDER.indexOf(theme).let { if (it < 0) Int.MAX_VALUE else it }

    /** True for tags that have a curated name (skips noise like "short" or "crushing"). */
    fun isKnown(theme: String): Boolean = theme in NAMES

    /** "mateIn1" -> "Mate in 1"; unknown tags fall back to a spaced-out camelCase name. */
    fun displayName(theme: String): String =
        NAMES[theme] ?: theme
            .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
            .lowercase()
            .replaceFirstChar { it.uppercase() }
}
