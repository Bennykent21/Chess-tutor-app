package com.chesstutor.app.domain.learn

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.Square
import com.squareup.moshi.Moshi

/** A lesson file that cannot be used; [message] says where and why, for whoever writes lessons. */
class LessonFormatException(message: String) : IllegalArgumentException(message)

/**
 * Reads one course file (plan section 11) and checks it completely: every position is a
 * legal FEN, every move, arrow and square is valid where it is used, and every "play" step
 * can be solved. A course that loads has no broken lesson in it.
 *
 * File shape:
 * ```
 * { "id", "category", "title", "intro", "level", "order",
 *   "puzzleThemes": [..], "battleLine": "Italian Game", "side": "white",
 *   "lessons": [ { "id", "title", "summary", "xp", "orientation": "black",
 *     "steps": [
 *       { "type": "watch", "title", "text", "video" },
 *       { "type": "explain", "text", "fen", "move": "e2e4", "arrows": ["f1c4", "f3g5:bad"], "highlights": ["f7"] },
 *       { "type": "play", "text", "fen", "expect": ["g1f3", "b1c3"], "hint", "ifWrong", "ifRight", "feedback": {"d2d4": ".."} },
 *       { "type": "play", "text", "line": ["d1h5", "g7g6", "h5e5|h5f3"] },
 *       { "type": "quiz", "text", "fen", "goal": "mate" } ] } ] }
 * ```
 * A step without "fen" starts where the previous one ended: after an explain step's move, or
 * after a play step's line (its first accepted move each turn). A step after a "goal" step
 * must give its own "fen", because any of several moves may have been played.
 * In a "line", the player plays the 1st, 3rd, 5th... entries ("a|b" accepts either) and the
 * opponent replies with the others.
 * A play or quiz step may add `"rule": true` when it asks for a particular move to practise a
 * rule ("move the king to d1", "castle"). The app ignores it; tools/check_lessons.py then does
 * not compare the move with Stockfish's choice.
 */
object LessonParser {
    private val adapter = Moshi.Builder().build().adapter(Any::class.java)

    fun parseCourse(json: String, source: String = "lesson file"): Course {
        val root = try {
            adapter.fromJson(json)
        } catch (e: Exception) {
            throw LessonFormatException("$source: not valid JSON (${e.message})")
        }
        return Reader(source).course(root.asObject(source))
    }

    private class Reader(private val source: String) {
        fun course(obj: Map<String, Any?>): Course {
            val at = source
            val id = obj.string("id", at)
            val categoryKey = obj.string("category", at)
            val category = LessonCategory.fromKey(categoryKey)
                ?: fail(at, "unknown category \"$categoryKey\" (use ${LessonCategory.entries.joinToString { it.key }})")
            val levelKey = obj.optString("level", at) ?: LessonLevel.BEGINNER.key
            val level = LessonLevel.fromKey(levelKey) ?: fail(at, "unknown level \"$levelKey\"")
            val lessonsJson = obj.list("lessons", at)
            if (lessonsJson.isEmpty()) fail(at, "a course needs at least one lesson")
            val lessons = lessonsJson.mapIndexed { i, item -> lesson(item.asObject("$at lessons[$i]"), "$at lessons[$i]") }
            lessons.groupBy { it.id }.filter { it.value.size > 1 }.keys.firstOrNull()?.let { fail(at, "lesson id \"$it\" is used twice") }
            return Course(
                id = id,
                category = category,
                title = obj.string("title", at),
                intro = obj.optString("intro", at).orEmpty(),
                level = level,
                order = obj.optInt("order", at) ?: 0,
                lessons = lessons,
                puzzleThemes = obj.optList("puzzleThemes", at).mapIndexed { i, v -> v as? String ?: fail(at, "puzzleThemes[$i] must be text") },
                battleLine = obj.optString("battleLine", at),
                side = obj.optString("side", at)?.let { color(it, "$at side") },
            )
        }

        fun lesson(obj: Map<String, Any?>, path: String): Lesson {
            val id = obj.string("id", path)
            val at = "$source lesson \"$id\""
            val stepsJson = obj.list("steps", at)
            if (stepsJson.isEmpty()) fail(at, "a lesson needs at least one step")
            var carried: Position? = null
            var goalBefore = false
            val steps = stepsJson.mapIndexed { i, item ->
                val stepAt = "$at step ${i + 1}"
                val stepObj = item.asObject(stepAt)
                val fenText = stepObj.optString("fen", stepAt)
                // A watch step shows no board, so it never needs a position of its own.
                val isWatch = stepObj["type"] == "watch"
                if (fenText == null && goalBefore && !isWatch) fail(stepAt, "needs a \"fen\": the step before it has a goal, so its end position is not fixed")
                val start = when {
                    fenText != null -> Position.tryFromFen(fenText).getOrElse { fail(stepAt, "bad fen \"$fenText\" (${it.message})") }
                    carried != null -> carried!!
                    isWatch -> Position.initial()
                    else -> fail(stepAt, "the first step needs a \"fen\"")
                }
                val (step, end) = step(stepObj, start, stepAt)
                carried = end
                if (!isWatch || fenText != null) goalBefore = step is LessonStep.Play && step.goal != null
                step
            }
            val firstPlay = steps.firstOrNull { it is LessonStep.Play }
            val orientation = obj.optString("orientation", at)?.let { color(it, "$at orientation") }
                ?: firstPlay?.let { Position.fromFen(it.fen).sideToMove }
                ?: PieceColor.WHITE
            val xp = obj.optInt("xp", at) ?: Lesson.DEFAULT_XP
            if (xp !in 0..200) fail(at, "xp must be 0 to 200")
            return Lesson(
                id = id,
                title = obj.string("title", at),
                summary = obj.optString("summary", at).orEmpty(),
                steps = steps,
                xp = xp,
                orientation = orientation,
            )
        }

        /** The step, and the position it ends in. */
        fun step(obj: Map<String, Any?>, start: Position, at: String): Pair<LessonStep, Position> {
            val fen = start.toFen()
            val text = obj.optString("text", at).orEmpty()
            val arrows = obj.optList("arrows", at).mapIndexed { i, v -> arrow(v as? String ?: fail(at, "arrows[$i] must be text"), at) }
            val highlights = obj.optList("highlights", at).mapIndexed { i, v -> highlight(v as? String ?: fail(at, "highlights[$i] must be text"), at) }
            return when (val type = obj.string("type", at)) {
                "watch" -> LessonStep.Watch(
                    title = obj.optString("title", at) ?: "Watch",
                    text = text,
                    fen = fen,
                    video = obj.optString("video", at),
                ) to start
                "explain" -> {
                    if (text.isBlank()) fail(at, "an explain step needs \"text\"")
                    val move = obj.optString("move", at)
                    val end = move?.let { LessonChess.play(start, it) ?: fail(at, "move \"$it\" is not legal here") } ?: start
                    LessonStep.Explain(text, fen, arrows, highlights, move) to end
                }
                "play", "quiz" -> play(obj, start, at, text, arrows, highlights, quiz = type == "quiz")
                else -> fail(at, "unknown step type \"$type\" (use watch, explain, play or quiz)")
            }
        }

        fun play(
            obj: Map<String, Any?>,
            start: Position,
            at: String,
            text: String,
            arrows: List<LessonArrow>,
            highlights: List<LessonHighlight>,
            quiz: Boolean,
        ): Pair<LessonStep, Position> {
            if (text.isBlank()) fail(at, "a ${if (quiz) "quiz" else "play"} step needs \"text\" (what to find)")
            if (LegalMoveGenerator.generateLegalMoves(start).isEmpty()) fail(at, "the side to move has no legal move")
            val expect = obj.optList("expect", at).mapIndexed { i, v -> v as? String ?: fail(at, "expect[$i] must be text") }
            val line = obj.optList("line", at).mapIndexed { i, v -> v as? String ?: fail(at, "line[$i] must be text") }
            val goalKey = obj.optString("goal", at)
            val goal = goalKey?.let { MoveGoal.fromKey(it) ?: fail(at, "unknown goal \"$it\" (use mate or check)") }
            val ways = listOf(expect.isNotEmpty(), line.isNotEmpty(), goal != null).count { it }
            if (ways != 1) fail(at, "give exactly one of \"expect\", \"line\" or \"goal\"")

            val turns = mutableListOf<PlayerTurn>()
            var position = start
            when {
                expect.isNotEmpty() -> {
                    expect.forEach { if (LessonChess.legal(start, it) == null) fail(at, "expected move \"$it\" is not legal here") }
                    turns += PlayerTurn(expect.distinct())
                    position = LessonChess.play(start, expect.first())!!
                }
                line.isNotEmpty() -> {
                    line.chunked(2).forEachIndexed { i, pair ->
                        val accepted = pair[0].split('|').map { it.trim() }.filter { it.isNotEmpty() }
                        if (accepted.isEmpty()) fail(at, "line turn ${i + 1} has no move")
                        accepted.forEach { if (LessonChess.legal(position, it) == null) fail(at, "line move \"$it\" (turn ${i + 1}) is not legal here") }
                        position = LessonChess.play(position, accepted.first())!!
                        val reply = pair.getOrNull(1)
                        if (reply != null) {
                            position = LessonChess.play(position, reply) ?: fail(at, "reply \"$reply\" (turn ${i + 1}) is not legal here")
                        }
                        turns += PlayerTurn(accepted, reply)
                    }
                }
                else -> if (LegalMoveGenerator.generateLegalMoves(start).none { LessonChess.reaches(start, it, goal!!) }) {
                    fail(at, "no move reaches the goal \"${goal!!.key}\" here")
                }
            }

            val feedbackObj = obj["feedback"]?.let { it.asObject("$at feedback") }.orEmpty()
            val feedback = feedbackObj.mapValues { (move, value) ->
                if (LessonChess.legal(start, move) == null) fail(at, "feedback move \"$move\" is not legal here")
                value as? String ?: fail(at, "feedback for \"$move\" must be text")
            }
            return LessonStep.Play(
                text = text,
                fen = start.toFen(),
                arrows = arrows,
                highlights = highlights,
                turns = turns,
                goal = goal,
                hint = obj.optString("hint", at),
                ifWrong = obj.optString("ifWrong", at),
                ifRight = obj.optString("ifRight", at),
                feedback = feedback,
                quiz = quiz,
            ) to position
        }

        fun arrow(text: String, at: String): LessonArrow {
            val (body, tone) = splitTone(text, MarkTone.GOOD, at)
            if (body.length != 4) fail(at, "arrow \"$text\" must look like \"e2e4\"")
            return LessonArrow(square(body.substring(0, 2), at), square(body.substring(2, 4), at), tone)
        }

        fun highlight(text: String, at: String): LessonHighlight {
            val (body, tone) = splitTone(text, MarkTone.HINT, at)
            return LessonHighlight(square(body, at), tone)
        }

        fun splitTone(text: String, default: MarkTone, at: String): Pair<String, MarkTone> {
            val parts = text.trim().split(':')
            if (parts.size > 2) fail(at, "mark \"$text\" has too many ':'")
            val tone = parts.getOrNull(1)?.let { MarkTone.fromKey(it) ?: fail(at, "unknown mark colour \"$it\" (use good, bad, hint or idea)") } ?: default
            return parts[0] to tone
        }

        fun square(text: String, at: String): String {
            runCatching { Square.fromAlgebraic(text) }.getOrElse { fail(at, "\"$text\" is not a square") }
            return text
        }

        fun color(text: String, at: String): PieceColor = when (text) {
            "white" -> PieceColor.WHITE
            "black" -> PieceColor.BLACK
            else -> fail(at, "use \"white\" or \"black\", not \"$text\"")
        }

        private fun Map<String, Any?>.string(key: String, at: String): String =
            optString(key, at)?.takeIf { it.isNotBlank() } ?: fail(at, "missing \"$key\"")

        private fun Map<String, Any?>.optString(key: String, at: String): String? = when (val v = this[key]) {
            null -> null
            is String -> v
            else -> fail(at, "\"$key\" must be text")
        }

        private fun Map<String, Any?>.optInt(key: String, at: String): Int? = when (val v = this[key]) {
            null -> null
            is Number -> v.toDouble().takeIf { it == Math.floor(it) }?.toInt() ?: fail(at, "\"$key\" must be a whole number")
            else -> fail(at, "\"$key\" must be a number")
        }

        private fun Map<String, Any?>.list(key: String, at: String): List<Any?> =
            this[key]?.let { it as? List<*> ?: fail(at, "\"$key\" must be a list") } ?: fail(at, "missing \"$key\"")

        private fun Map<String, Any?>.optList(key: String, at: String): List<Any?> =
            this[key]?.let { it as? List<*> ?: fail(at, "\"$key\" must be a list") }.orEmpty()
    }

    private fun Any?.asObject(at: String): Map<String, Any?> {
        val map = this as? Map<*, *> ?: fail(at, "must be an object { ... }")
        return map.entries.associate { (k, v) -> k.toString() to v }
    }

    private fun fail(at: String, why: String): Nothing = throw LessonFormatException("$at: $why")
}

/** The few chess questions lessons ask, answered with the app's move generator. */
object LessonChess {
    /** The legal move for [uci] here, with its castling and en passant flags; null if illegal. */
    fun legal(position: Position, uci: String): Move? =
        LegalMoveGenerator.generateLegalMoves(position).firstOrNull { it.uci == uci }

    fun play(position: Position, uci: String): Position? = legal(position, uci)?.let { LegalMoveGenerator.makeMove(position, it) }

    fun reaches(position: Position, move: Move, goal: MoveGoal): Boolean {
        val after = LegalMoveGenerator.makeMove(position, move)
        return when (goal) {
            MoveGoal.MATE -> LegalMoveGenerator.isCheckmate(after)
            MoveGoal.CHECK -> LegalMoveGenerator.isKingInCheck(after)
        }
    }
}
