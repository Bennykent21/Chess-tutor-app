package com.chesstutor.app.domain

/**
 * Represents a standard ECO chess opening line with its move sequence, EPD key,
 * family name (before ':'), and curriculum cross-link.
 */
data class OpeningLine(
    val id: String,
    val eco: String,
    val name: String,
    val uciMoves: List<String>,
    val sanMoves: List<String>,
    val epd: String,
    val targetRating: Int = 1000,
    val summary: String = "",
    val relatedTopicId: String = "opening_principles"
) {
    /** Opening family name (the part before the first ':', e.g. "Sicilian Defense"). */
    val family: String
        get() = name.substringBefore(":").trim()

    val variation: String
        get() = if (name.contains(":")) name.substringAfter(":").trim() else "Main Line"

    val recommendedSide: Char
        get() = if (
            name.startsWith("Sicilian") ||
            name.startsWith("French") ||
            name.startsWith("Caro-Kann") ||
            name.startsWith("King's Indian") ||
            name.contains("Slav") ||
            name.contains("Two Knights")
        ) 'b' else 'w'

    val formattedMoveLine: String
        get() = buildString {
            for (i in sanMoves.indices step 2) {
                if (isNotEmpty()) append(" ")
                val moveNum = (i / 2) + 1
                append("$moveNum. ${sanMoves[i]}")
                if (i + 1 < sanMoves.size) {
                    append(" ${sanMoves[i + 1]}")
                }
            }
        }
}

data class OpeningMatchResult(
    val opening: OpeningLine?,
    val lastBookPly: Int,
    val outOfTheoryPly: Int?,
    val nextBookMoveUci: String?,
    val nextBookMoveSan: String?
)

object OpeningBook {

    /**
     * Extracts the 4-field EPD (board, side-to-move, castling, en-passant) from a full FEN.
     */
    fun toEpd(fen: String): String {
        val parts = fen.trim().split(Regex("\\s+"))
        return if (parts.size >= 4) {
            parts.take(4).joinToString(" ")
        } else {
            fen.trim()
        }
    }

    private fun buildLine(
        id: String,
        eco: String,
        name: String,
        uciString: String,
        targetRating: Int,
        summary: String,
        relatedTopicId: String = "opening_principles"
    ): OpeningLine {
        val uciList = uciString.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val pos = ChessPosition()
        val sanList = mutableListOf<String>()
        for (uci in uciList) {
            val san = pos.toSan(uci) ?: uci
            pos.play(uci)
            sanList += san
        }
        return OpeningLine(
            id = id,
            eco = eco,
            name = name,
            uciMoves = uciList,
            sanMoves = sanList,
            epd = toEpd(pos.fen),
            targetRating = targetRating,
            summary = summary,
            relatedTopicId = relatedTopicId
        )
    }

    val lines: List<OpeningLine> by lazy {
        listOf(
            buildLine(
                id = "italian_giuoco_piano",
                eco = "C50",
                name = "Italian Game: Giuoco Piano",
                uciString = "e2e4 e7e5 g1f3 b8c6 f1c4 f8c5",
                targetRating = 800,
                summary = "Rapid minor-piece development targeting the vulnerable f7 square while preparing central d3/c3 or d4 breaks.",
                relatedTopicId = "opening_italian_game"
            ),
            buildLine(
                id = "italian_two_knights",
                eco = "C55",
                name = "Italian Game: Two Knights Defense",
                uciString = "e2e4 e7e5 g1f3 b8c6 f1c4 g8f6",
                targetRating = 1000,
                summary = "Black counter-attacks the e4 pawn immediately with 3...Nf6, inviting sharp tactical play.",
                relatedTopicId = "opening_italian_game"
            ),
            buildLine(
                id = "ruy_lopez_morphy",
                eco = "C78",
                name = "Ruy Lopez: Morphy Defense",
                uciString = "e2e4 e7e5 g1f3 b8c6 f1b5 a7a6 b5a4 g8f6",
                targetRating = 1300,
                summary = "Classical pressure against the defender of the e5 pawn, followed by kingside castling and central buildup.",
                relatedTopicId = "opening_principles"
            ),
            buildLine(
                id = "scotch_game",
                eco = "C45",
                name = "Scotch Game: Classical Variation",
                uciString = "e2e4 e7e5 g1f3 b8c6 d2d4 e5d4 f3d4",
                targetRating = 1000,
                summary = "White immediately opens the center with 3.d4, seizing space and active piece play.",
                relatedTopicId = "opening_principles"
            ),
            buildLine(
                id = "sicilian_najdorf",
                eco = "B90",
                name = "Sicilian Defense: Open Najdorf",
                uciString = "e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4 g8f6 b1c3 a7a6",
                targetRating = 1450,
                summary = "Asymmetric counterattacking fight where Black trades the c-pawn for White's central d-pawn and controls b5.",
                relatedTopicId = "opening_sicilian_defense"
            ),
            buildLine(
                id = "sicilian_dragon",
                eco = "B70",
                name = "Sicilian Defense: Dragon Variation",
                uciString = "e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4 g8f6 b1c3 g7g6",
                targetRating = 1350,
                summary = "Black fianchettoes the dark-squared bishop on g7 to slice across the long diagonal.",
                relatedTopicId = "opening_sicilian_defense"
            ),
            buildLine(
                id = "french_advance",
                eco = "C02",
                name = "French Defense: Advance Variation",
                uciString = "e2e4 e7e6 d2d4 d7d5 e4e5 c7c5",
                targetRating = 1150,
                summary = "A classical pawn-chain battle where Black undermines the base of White's chain at d4 with ...c5.",
                relatedTopicId = "middlegame_pawn_breaks"
            ),
            buildLine(
                id = "caro_kann_classical",
                eco = "B18",
                name = "Caro-Kann Defense: Classical Variation",
                uciString = "e2e4 c7c6 d2d4 d7d5 b1c3 d5e4 c3e4 c8f5",
                targetRating = 1200,
                summary = "Solid central structure where Black develops the light-squared bishop outside the pawn chain before playing ...e6.",
                relatedTopicId = "opening_principles"
            ),
            buildLine(
                id = "queens_gambit_declined",
                eco = "D37",
                name = "Queen's Gambit: Declined Orthodox",
                uciString = "d2d4 d7d5 c2c4 e7e6 b1c3 g8f6",
                targetRating = 1200,
                summary = "White challenges Black's d5 anchor with c4 while Black maintains a fortified central foothold.",
                relatedTopicId = "opening_queens_gambit"
            ),
            buildLine(
                id = "queens_gambit_slav",
                eco = "D10",
                name = "Queen's Gambit: Slav Defense",
                uciString = "d2d4 d7d5 c2c4 c7c6 g1f3 g8f6",
                targetRating = 1150,
                summary = "Black supports d5 with ...c6, keeping the c8-bishop free to develop actively.",
                relatedTopicId = "opening_queens_gambit"
            ),
            buildLine(
                id = "london_system",
                eco = "D02",
                name = "London System: Main Line",
                uciString = "d2d4 d7d5 g1f3 g8f6 c1f4 e7e6 e2e3",
                targetRating = 900,
                summary = "A resilient pyramid structure (c3-d4-e3) where White develops the dark-squared bishop to f4 early.",
                relatedTopicId = "opening_principles"
            ),
            buildLine(
                id = "kings_indian_defense",
                eco = "E61",
                name = "King's Indian Defense: Normal Variation",
                uciString = "d2d4 g8f6 c2c4 g7g6 b1c3 f8g7 e2e4 d7d6",
                targetRating = 1400,
                summary = "Hypermodern counter-attack where Black allows White a pawn center to strike back with ...e5 or ...c5.",
                relatedTopicId = "middlegame_pawn_breaks"
            )
        )
    }

    /**
     * Map of every prefix EPD along our book lines to the deepest matching [OpeningLine].
     */
    private val epdPrefixIndex: Map<String, Pair<OpeningLine, Int>> by lazy {
        val map = mutableMapOf<String, Pair<OpeningLine, Int>>()
        for (line in lines) {
            val pos = ChessPosition()
            for ((idx, uci) in line.uciMoves.withIndex()) {
                if (!pos.play(uci)) break
                val epd = toEpd(pos.fen)
                val ply = idx + 1
                val existing = map[epd]
                // Prefer exact line terminal match or deeper ply match
                if (existing == null || ply >= existing.second) {
                    map[epd] = line to ply
                }
            }
        }
        map
    }

    fun byId(id: String): OpeningLine? = lines.firstOrNull { it.id == id }

    fun matchByFen(fen: String): OpeningLine? {
        val epd = toEpd(fen)
        return lines.firstOrNull { it.epd == epd } ?: epdPrefixIndex[epd]?.first
    }

    /**
     * Matches a sequence of UCI moves by position EPD along the game's moves,
     * returning the deepest matched [OpeningLine], the last book ply, and the first
     * ply where play left named theory.
     */
    fun matchAlongMoves(
        uciMoves: List<String>,
        targetLine: OpeningLine? = null
    ): OpeningMatchResult {
        if (uciMoves.isEmpty()) {
            val firstMove = targetLine?.uciMoves?.firstOrNull()
            val firstSan = targetLine?.sanMoves?.firstOrNull()
            return OpeningMatchResult(
                opening = targetLine,
                lastBookPly = 0,
                outOfTheoryPly = null,
                nextBookMoveUci = firstMove,
                nextBookMoveSan = firstSan
            )
        }

        val pos = ChessPosition()
        var matchedOpening: OpeningLine? = targetLine
        var lastBookPly = 0
        var outOfTheoryPly: Int? = null
        var nextBookUci: String? = null
        var nextBookSan: String? = null

        if (targetLine != null) {
            for ((idx, uci) in uciMoves.withIndex()) {
                val expectedUci = targetLine.uciMoves.getOrNull(idx)
                if (expectedUci != null && uci == expectedUci) {
                    lastBookPly = idx + 1
                } else if (outOfTheoryPly == null && idx < targetLine.uciMoves.size) {
                    outOfTheoryPly = idx + 1
                    nextBookUci = expectedUci
                    nextBookSan = targetLine.sanMoves.getOrNull(idx)
                    break
                }
            }
            if (outOfTheoryPly == null && uciMoves.size < targetLine.uciMoves.size) {
                nextBookUci = targetLine.uciMoves[uciMoves.size]
                nextBookSan = targetLine.sanMoves[uciMoves.size]
            } else if (outOfTheoryPly == null && uciMoves.size > targetLine.uciMoves.size) {
                outOfTheoryPly = targetLine.uciMoves.size + 1
            }
            return OpeningMatchResult(
                opening = targetLine,
                lastBookPly = lastBookPly,
                outOfTheoryPly = outOfTheoryPly,
                nextBookMoveUci = nextBookUci,
                nextBookMoveSan = nextBookSan
            )
        }

        for ((idx, uci) in uciMoves.withIndex()) {
            if (!pos.play(uci)) break
            val epd = toEpd(pos.fen)
            val hit = epdPrefixIndex[epd]
            if (hit != null) {
                matchedOpening = hit.first
                lastBookPly = idx + 1
            } else if (matchedOpening != null && outOfTheoryPly == null) {
                outOfTheoryPly = idx + 1
                nextBookUci = matchedOpening.uciMoves.getOrNull(idx)
                nextBookSan = matchedOpening.sanMoves.getOrNull(idx)
            }
        }

        if (matchedOpening != null && outOfTheoryPly == null && uciMoves.size < matchedOpening.uciMoves.size) {
            nextBookUci = matchedOpening.uciMoves[uciMoves.size]
            nextBookSan = matchedOpening.sanMoves[uciMoves.size]
        }

        return OpeningMatchResult(
            opening = matchedOpening,
            lastBookPly = lastBookPly,
            outOfTheoryPly = outOfTheoryPly,
            nextBookMoveUci = nextBookUci,
            nextBookMoveSan = nextBookSan
        )
    }
}
