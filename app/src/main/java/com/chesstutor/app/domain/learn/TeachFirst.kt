package com.chesstutor.app.domain.learn

import com.chesstutor.app.core.Position

/**
 * Opening lessons teach before they ask (Phase 12, like the drills' watch-then-play): the line's
 * moves are first shown with their reasons, then the learner plays the line back in one go.
 * A question set in a new position (its own position, not the next move of the line) stays a
 * question and keeps its place after the play-back.
 */
object TeachFirst {
    const val PLAY_BACK = "Now play it back."
    const val NOT_THE_LINE = "Not the move from the line. Try again, or tap Hint."

    fun of(lesson: Lesson): Lesson {
        val line = lineLength(lesson.steps)
        val head = lesson.steps.take(line)
        if (head.none { it is LessonStep.Play }) return lesson
        val shown = head.flatMap(::shown)
        val playBack = playBack(head) ?: return lesson.copy(steps = shown + lesson.steps.drop(line))
        return lesson.copy(steps = shown + playBack + lesson.steps.drop(line))
    }

    /** How many steps from the start follow on from each other as one line. */
    private fun lineLength(steps: List<LessonStep>): Int {
        var position: Position? = null
        steps.forEachIndexed { i, step ->
            val at = Position.fromFen(step.fen)
            when (step) {
                is LessonStep.Watch -> return i
                is LessonStep.Explain -> if (position != null && !same(at, position!!)) return i
                is LessonStep.Play -> if (step.goal != null || step.turns.isEmpty() || (position != null && !same(at, position!!))) return i
            }
            position = after(step) ?: return i
        }
        return steps.size
    }

    /** The step as it is shown: a move to find becomes the move, played, with its reason. */
    private fun shown(step: LessonStep): List<LessonStep> {
        if (step !is LessonStep.Play) return listOf(step)
        var position = Position.fromFen(step.fen)
        val out = mutableListOf<LessonStep>()
        step.turns.forEachIndexed { i, turn ->
            val move = turn.accepted.first()
            val words = if (i == 0) step.ifRight ?: step.text else ""
            out += LessonStep.Explain(words, position.toFen(), if (i == 0) step.arrows else emptyList(), if (i == 0) step.highlights else emptyList(), move = move)
            position = LessonChess.play(position, move) ?: return out
            turn.reply?.let { reply ->
                out += LessonStep.Explain("", position.toFen(), move = reply)
                position = LessonChess.play(position, reply) ?: return out
            }
        }
        return out
    }

    /** One move to find per learner move of the line, with the other side's moves as replies. */
    private fun playBack(line: List<LessonStep>): LessonStep.Play? {
        val first = line.indexOfFirst { it is LessonStep.Play }
        val turns = mutableListOf<PlayerTurn>()
        for (step in line.drop(first)) {
            when (step) {
                is LessonStep.Play -> {
                    // Two learner moves in a row: the line has a gap, so the play-back ends before it.
                    if (turns.isNotEmpty() && turns.last().reply == null) break
                    turns += step.turns
                }
                is LessonStep.Explain -> {
                    val move = step.move ?: continue
                    val last = turns.last()
                    if (last.reply != null) break
                    turns[turns.lastIndex] = last.copy(reply = move)
                }
                is LessonStep.Watch -> break
            }
        }
        // A reply after the learner's last move would end the line on the other side's move.
        turns.lastOrNull()?.let { turns[turns.lastIndex] = it.copy(reply = null) }
        if (turns.isEmpty()) return null
        return LessonStep.Play(PLAY_BACK, line[first].fen, turns = turns, ifWrong = NOT_THE_LINE)
    }

    private fun after(step: LessonStep): Position? {
        var position = Position.fromFen(step.fen)
        when (step) {
            is LessonStep.Explain -> step.move?.let { position = LessonChess.play(position, it) ?: return null }
            is LessonStep.Play -> step.turns.forEach { turn ->
                position = LessonChess.play(position, turn.accepted.first()) ?: return null
                turn.reply?.let { position = LessonChess.play(position, it) ?: return null }
            }
            is LessonStep.Watch -> Unit
        }
        return position
    }

    /** Same pieces, side, castling and en passant; the move counters may differ. */
    private fun same(a: Position, b: Position): Boolean = a.toFen().split(' ').take(4) == b.toFen().split(' ').take(4)
}
