package com.chesstutor.app.domain.learn

/**
 * Chess notation said in plain words, for beginner courses (D99): "3...Bf5" becomes "bishop to
 * f5", "exd5" "pawn takes on d5", "O-O" "castling", "1.e4 e5" "pawn to e4, pawn to e5". A square
 * on its own ("attacks e4") stays as it is; the course "Reading chess moves" teaches the letters.
 */
object PlainMoves {

    /** The course that teaches the letters keeps them. */
    const val TEACHING_COURSE = "basics-notation"

    /** Beginner courses say moves in words, except the one teaching notation. */
    fun appliesTo(course: Course): Boolean = course.level == LessonLevel.BEGINNER && course.id != TEACHING_COURSE

    private val PIECES = mapOf('K' to "king", 'Q' to "queen", 'R' to "rook", 'B' to "bishop", 'N' to "knight")

    // An optional move number ("3." or "3...") or Black's dots ("..."), then the move and a check sign.
    private val MOVE = Regex(
        """(?<![\w.])(\d+\.(?:\.\.)?\s?|\.\.\.)?(O-O-O|O-O|[KQRBN][a-h]?[1-8]?x?[a-h][1-8]|[a-h]x[a-h][1-8](?:=[QRBN])?|[a-h][1-8](?:=[QRBN])?)([+#]?)(?![\w=])""",
    )
    private val PAWN_PUSH = Regex("""[a-h][1-8]""")

    fun soften(text: String): String {
        val out = StringBuilder()
        var at = 0
        // Where the last move said in words ended, so the next one in a list ("1.e4 e5") is a move too.
        var lastMoveEnd = -2
        for (match in MOVE.findAll(text)) {
            val (number, move, sign) = match.destructured
            val gap = text.substring(at, match.range.first)
            val inList = lastMoveEnd >= 0 && at == lastMoveEnd + 1 && gap == " "
            // "e4" alone is a square unless it is numbered, dotted or follows another move.
            if (number.isEmpty() && !inList && PAWN_PUSH.matches(move)) continue
            val words = words(move) + when (sign) {
                "+" -> " with check"
                "#" -> ", checkmate"
                else -> ""
            }
            out.append(if (inList) ", " else gap)
            out.append(if (startsSentence(text, match.range.first)) words.replaceFirstChar { it.uppercase() } else words)
            at = match.range.last + 1
            lastMoveEnd = match.range.last
        }
        out.append(text.substring(at))
        return out.toString()
    }

    private fun words(move: String): String = when {
        move == "O-O" -> "castling"
        move == "O-O-O" -> "long castling"
        move[0] in PIECES -> {
            val piece = PIECES.getValue(move[0])
            val square = move.takeLast(2)
            if ('x' in move) "$piece takes on $square" else "$piece to $square"
        }
        else -> {
            val promotion = move.substringAfter('=', "").firstOrNull()?.let { ", becoming a ${PIECES.getValue(it)}" }.orEmpty()
            val body = move.substringBefore('=')
            if ('x' in body) "pawn takes on ${body.takeLast(2)}$promotion" else "pawn to ${body.takeLast(2)}$promotion"
        }
    }

    private fun startsSentence(text: String, index: Int): Boolean {
        val before = text.substring(0, index).trimEnd()
        return before.isEmpty() || before.last() in ".!?"
    }
}
