package com.chesstutor.app.domain.learn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Opening lessons show the line first, then ask for it back (Phase 12). */
class TeachFirstTest {

    private fun course(name: String) = File("src/main/assets/lessons/$name.json").let { LessonParser.parseCourse(it.readText(), it.name) }

    @Test
    fun theLineIsShownWithItsReasonsThenPlayedBackInOneGo() {
        val lesson = course("openings-caro-kann-advance").lessons.first()
        val taught = TeachFirst.of(lesson)
        val playBack = taught.steps.indexOfFirst { it is LessonStep.Play }
        val shown = taught.steps.take(playBack)
        assertTrue("nothing is asked before it is shown", shown.all { it is LessonStep.Explain })
        assertEquals(
            "every move of the line is played on the board first",
            listOf("e2e4", "c7c6", "d2d4", "d7d5", "e4e5", "c8f5"),
            shown.mapNotNull { (it as LessonStep.Explain).move },
        )
        assertEquals("the learner's move comes with its reason", "1...c6 prepares ...d5 to challenge White's centre.", shown[1].text)

        val back = taught.steps[playBack] as LessonStep.Play
        assertEquals(TeachFirst.PLAY_BACK, back.text)
        assertEquals("from the learner's first move", lesson.steps[1].fen, back.fen)
        assertEquals(listOf("c7c6", "d7d5", "c8f5"), back.turns.map { it.accepted.first() })
        assertEquals(listOf("d2d4", "e4e5", null), back.turns.map { it.reply })

        val question = taught.steps.last() as LessonStep.Play
        assertTrue("the question in a new position stays a question, after the play-back", question.quiz && question.turns.single().accepted == listOf("c8g4"))
    }

    @Test
    fun everyOpeningLessonStillWorksTaughtFirst() {
        File("src/main/assets/lessons").listFiles { f -> f.name.startsWith("openings-") }.orEmpty().forEach { file ->
            LessonParser.parseCourse(file.readText(), file.name).lessons.forEach { lesson ->
                val taught = TeachFirst.of(lesson)
                // The play-back replays legally from its start.
                taught.steps.filterIsInstance<LessonStep.Play>().filter { it.text == TeachFirst.PLAY_BACK }.forEach { back ->
                    back.turns.fold(com.chesstutor.app.core.Position.fromFen(back.fen)) { at, turn ->
                        val mine = LessonChess.play(at, turn.accepted.first())
                        assertTrue("${lesson.id}: ${turn.accepted.first()}", mine != null)
                        turn.reply?.let { LessonChess.play(mine!!, it).also { after -> assertTrue("${lesson.id}: reply $it", after != null) } } ?: mine!!
                    }
                }
            }
        }
    }

    @Test
    fun aLessonOfQuestionsOnlyIsLeftAlone() {
        val start = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        val lesson = Lesson("q", "q", "", listOf(LessonStep.Explain("Look.", start)))
        assertSame(lesson, TeachFirst.of(lesson))
    }
}
