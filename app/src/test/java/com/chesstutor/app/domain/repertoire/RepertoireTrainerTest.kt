package com.chesstutor.app.domain.repertoire

import com.chesstutor.app.core.repetitionKey
import com.chesstutor.app.domain.repertoire.RepertoireLevel.CORE
import com.chesstutor.app.domain.repertoire.RepertoireLevel.DEEP
import com.chesstutor.app.domain.repertoire.RepertoireLevel.STANDARD
import com.chesstutor.app.domain.repertoire.TestRepertoire.P
import com.chesstutor.app.domain.repertoire.TestRepertoire.key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.random.Random

class RepertoireTrainerTest {
    private val book = TestRepertoire.italian()
    private val index = RepertoireIndex(book)
    private val day0: Instant = Instant.parse("2026-11-04T09:00:00Z")
    private fun day(n: Long): Instant = day0.plus(Duration.ofDays(n))
    private val zone = ZoneOffset.UTC

    // ---------------------------------------------------------------- index

    @Test
    fun everyNodeHasAPathAndCardsNameTheMoveThatLedThere() {
        for (node in book.nodes.values) assertTrue(index.pathTo(node.key) != null)
        val card = key("$P g8f6 d2d3 f8c5")
        assertEquals("$P g8f6 d2d3 f8c5".split(' '), index.pathTo(card))
        assertEquals("f8c5", index.lastMove(card))
        assertEquals(key("$P g8f6"), index.child(key(P), "g8f6")?.key)
    }

    @Test
    fun theScriptsSampleCanBeLearntToTheEnd() {
        val sample = javaClass.classLoader!!.getResourceAsStream("repertoire/sample.json.gz")!!.use { RepertoireParser.fromStream(it) }
        val sampleIndex = RepertoireIndex(sample)
        val introduced = HashSet<String>()
        for (level in RepertoireLevel.entries) {
            while (true) {
                val segment = LearnPlanner.next(sampleIndex, level, introduced) ?: break
                assertTrue(segment.newCards.isNotEmpty() && segment.newCards.size <= LearnPlanner.MAX_NEW)
                val run = LearnRun(sampleIndex, segment) { day0 }
                // Play both passes with the repertoire's own moves.
                while (run.pass != LearnRun.Pass.DONE) {
                    if (run.isOwnTurn) run.submit(segment.moves[run.ply]) else assertTrue(run.playOpponent())
                }
                assertEquals(segment.newCards.toSet(), run.outcome.map { it.first.key }.toSet())
                assertTrue(run.outcome.all { it.second.firstTryRight })
                introduced += segment.newCards
            }
            assertEquals(sample.cards(level).map { it.key }.toSet(), sample.cards(level).map { it.key }.filter { it in introduced }.toSet())
        }
    }

    // ---------------------------------------------------------------- judging and scheduling

    @Test
    fun anAlternativeIsNotWrong() {
        val c3 = book.nodes.getValue(key("$P f8c5"))
        assertEquals(CardAnswer.RIGHT, RepertoireCards.judge(c3, "c2c3"))
        assertEquals(CardAnswer.ALSO, RepertoireCards.judge(c3, "e1g1"))
        assertEquals(CardAnswer.WRONG, RepertoireCards.judge(c3, "b2b4"))
    }

    @Test
    fun theFirstRecallSetsTheScheduleOnTheExistingScheduler() {
        val node = book.nodes.getValue(key(""))
        val fresh = RepertoireCards.introduce(book.id, node, day0)
        assertTrue(fresh.learning)
        assertEquals("italian|${node.key}", fresh.id)
        val (right, review) = RepertoireCards.afterRecall(fresh, right = true, hinted = false, now = day0)
        assertEquals(0, right.stage)
        assertEquals(day(1), right.dueAt)
        assertTrue(review.firstTryRight)
        val (second, _) = RepertoireCards.afterRecall(right, right = true, hinted = false, now = day(1))
        assertEquals(1, second.stage)
        assertEquals(day(4), second.dueAt)
        val (missed, missedReview) = RepertoireCards.afterRecall(second, right = false, hinted = false, now = day(4))
        assertEquals(-1, missed.stage)
        assertEquals(day(4), missed.dueAt)
        assertFalse(missedReview.firstTryRight)
        val (helped, helpedReview) = RepertoireCards.afterRecall(second, right = true, hinted = true, now = day(4))
        assertEquals(0, helped.stage)
        assertFalse(helpedReview.firstTryRight)
    }

    // ---------------------------------------------------------------- learn

    @Test
    fun learningStartsAtTheTopOfTheMostCommonLine() {
        val first = LearnPlanner.next(index, CORE, emptySet())!!
        assertEquals("$P f8c5 c2c3 g8f6 d2d3".split(' '), first.moves)
        assertEquals(listOf(key(""), key("e2e4 e7e5"), key("e2e4 e7e5 g1f3 b8c6"), key("$P f8c5"), key("$P f8c5 c2c3 g8f6")), first.newCards)
        val second = LearnPlanner.next(index, CORE, first.newCards.toSet())!!
        assertEquals("4...Be7 and 4...Bc5 are not open yet", listOf(key("$P g8f6")), second.newCards)
        assertEquals("$P g8f6 d2d3".split(' '), second.moves)
        assertNull(LearnPlanner.next(index, CORE, (first.newCards + second.newCards).toSet()))
        val standard = LearnPlanner.next(index, STANDARD, (first.newCards + second.newCards).toSet())!!
        assertEquals(listOf(key("$P g8f6 d2d3 f8e7")), standard.newCards)
    }

    @Test
    fun aLearnRunGuessesFirstThenRecalls() {
        val segment = LearnSegment("$P g8f6 d2d3".split(' '), listOf(key("$P g8f6")))
        val run = LearnRun(index, segment) { day0 }
        // Known cards on the way are practice: an alternative asks again, the right move goes on.
        assertEquals(LearnMoveResult.ALSO, run.submit("d2d4"))
        assertEquals(LearnMoveResult.RIGHT, run.submit("e2e4"))
        assertTrue(run.playOpponent())
        assertEquals(LearnMoveResult.RIGHT, run.submit("g1f3"))
        run.playOpponent()
        run.submit("f1c4")
        run.playOpponent()
        assertTrue(run.isNewCard)
        // A wrong guess costs nothing: the move is shown and played.
        assertEquals(LearnMoveResult.REVEALED, run.submit("b1c3"))
        assertEquals("the segment ended, so the recall pass starts from the top", LearnRun.Pass.RECALL, run.pass)
        assertTrue(run.outcome.isEmpty())
        for (uci in "e2e4 e7e5 g1f3 b8c6 f1c4 g8f6".split(' ')) {
            if (run.isOwnTurn) run.submit(uci) else run.playOpponent()
        }
        assertTrue(run.isNewCard)
        assertEquals(LearnMoveResult.WRONG, run.submit("b1c3"))
        assertEquals("d2d3", run.shown)
        assertEquals(LearnMoveResult.RIGHT, run.submit("d2d3"))
        assertEquals(LearnRun.Pass.DONE, run.pass)
        val (card, review) = run.outcome.single()
        assertEquals(key("$P g8f6"), card.key)
        assertTrue("missed in the recall pass: back to Learning", card.learning)
        assertFalse(review.firstTryRight)
    }

    @Test
    fun aRightRecallSchedulesTheCardForTomorrow() {
        val segment = LearnSegment(listOf("e2e4"), listOf(key("")))
        val run = LearnRun(index, segment) { day0 }
        assertEquals(LearnMoveResult.GUESSED, run.submit("e2e4"))
        assertEquals(LearnMoveResult.RIGHT, run.submit("e2e4"))
        val (card, review) = run.outcome.single()
        assertEquals(0, card.stage)
        assertEquals(day(1), card.dueAt)
        assertTrue(review.firstTryRight)
    }

    @Test
    fun blackStartsWithTheOpponentsMove() {
        val caro = RepertoireIndex(TestRepertoire.caroKann())
        val segment = LearnPlanner.next(caro, CORE, emptySet())!!
        assertEquals(listOf("e2e4", "c7c6", "d2d4", "d7d5", "e4e5", "c8f5"), segment.moves)
        val run = LearnRun(caro, segment) { day0 }
        assertFalse(run.isOwnTurn)
        assertTrue(run.playOpponent())
        assertTrue(run.isOwnTurn)
        assertEquals(LearnMoveResult.GUESSED, run.submit("c7c6"))
    }

    // ---------------------------------------------------------------- review

    private fun introducedAll(level: RepertoireLevel, at: Instant): Map<String, CardState> =
        book.cards(level).associate { node -> RepertoireCards.introduce(book.id, node, at).let { it.id to it } }

    @Test
    fun theReviewMixesOpeningsAndOnlyTheFirstTryCounts() {
        val caroBook = TestRepertoire.caroKann()
        val italianCards = book.cards(CORE).take(3).mapIndexed { i, n -> RepertoireCards.introduce(book.id, n, day0.plusSeconds(i.toLong())) }
        val caroCards = caroBook.cards(CORE).take(2).mapIndexed { i, n -> RepertoireCards.introduce(caroBook.id, n, day0.plusSeconds(10L + i)) }
        val queue = ReviewQueue.build(listOf(index, RepertoireIndex(caroBook)), italianCards + caroCards, day(1))
        assertEquals(listOf("italian", "caro-kann", "italian", "caro-kann", "italian"), queue.map { it.book.id })

        val session = ReviewSession(queue) { day(1) }
        // The first card is 1.e4: an alternative asks again without penalty, then the right move.
        assertEquals(CardAnswer.ALSO, session.submit("d2d4"))
        assertEquals(CardAnswer.RIGHT, session.submit("e2e4"))
        assertTrue(session.done)
        session.next()
        // 1...c6: wrong first, then right: counts as missed.
        assertEquals(CardAnswer.WRONG, session.submit("e7e5"))
        assertEquals("c7c6", session.shown)
        assertEquals(CardAnswer.RIGHT, session.submit("c7c6"))
        session.next()
        // A hint keeps a right answer close.
        session.hint()
        session.submit("g1f3")
        session.next()
        session.showAnswer()
        session.next()
        session.submit("f1c4")
        session.next()
        assertTrue(session.finished)
        val stages = session.outcome.map { it.first.stage }
        assertEquals(listOf(0, -1, 0, -1, 0), stages)
        assertEquals(2, session.rightFirstTime)
        assertEquals(listOf(true, false, false, false, true), session.outcome.map { it.second.firstTryRight })
    }

    @Test
    fun aCardNamesTheOpponentsLastMove() {
        val states = mapOf(RepertoireCards.cardId(book.id, key("$P g8f6")) to RepertoireCards.introduce(book.id, book.nodes.getValue(key("$P g8f6")), day0))
        val due = ReviewQueue.build(listOf(index), states.values, day0).single()
        assertEquals("g8f6", due.lastMove)
        assertEquals(key("$P g8f6"), due.position.repetitionKey())
    }

    // ---------------------------------------------------------------- tricky lines (D76)

    @Test
    fun twoSlipsAfterLearningMakeALineTrickyUntilThreeCleanRecalls() {
        val node = book.nodes.getValue(key("$P g8f6"))
        var card = RepertoireCards.introduce(book.id, node, day0)
        // A miss in the very first recall is learning, not a lapse.
        card = RepertoireCards.afterRecall(card, right = false, hinted = false, now = day0).first
        assertEquals(0, card.lapses)
        card = RepertoireCards.afterRecall(card, right = true, hinted = false, now = day0).first
        card = RepertoireCards.afterRecall(card, right = false, hinted = false, now = day(1)).first
        assertEquals(1, card.lapses)
        assertFalse(card.tricky)
        card = RepertoireCards.afterRecall(card, right = true, hinted = false, now = day(1)).first
        card = RepertoireCards.afterRecall(card, right = false, hinted = false, now = day(2)).first
        assertTrue(card.tricky)
        // Right again: still tricky, so it comes back within three days however far the scheduler would push it.
        for ((n, d) in listOf(2L, 4L, 6L).withIndex()) {
            card = RepertoireCards.afterRecall(card.copy(stage = 4, easeFactor = 3f), right = true, hinted = false, now = day(d)).first
            if (n < 2) {
                assertTrue(card.tricky)
                assertFalse(card.dueAt.isAfter(day(d + RepertoireCards.TRICKY_MAX_DAYS)))
            }
        }
        assertFalse("three clean recalls in a row clear it", card.tricky)
        assertTrue(card.dueAt.isAfter(day(6 + RepertoireCards.TRICKY_MAX_DAYS)))
    }

    @Test
    fun aHintedRightAnswerDoesNotCountTowardsClearing() {
        val card = RepertoireCards.introduce(book.id, book.nodes.getValue(key("")), day0).copy(attempts = 5, lapses = 2, streak = 2)
        val after = RepertoireCards.afterRecall(card, right = true, hinted = true, now = day0).first
        assertEquals(0, after.streak)
        assertTrue(after.tricky)
    }

    @Test
    fun aTrickyCardIsPlayedFromTheStartOfItsLine() {
        val tricky = RepertoireCards.introduce(book.id, book.nodes.getValue(key("$P g8f6")), day0).copy(attempts = 4, lapses = 2)
        val plain = RepertoireCards.introduce(book.id, book.nodes.getValue(key("$P f8c5")), day0.plusSeconds(1))
        assertEquals(listOf(key("$P g8f6")), ReviewQueue.tricky(listOf(index), listOf(plain, tricky)).map { it.node.key })
        val due = ReviewQueue.build(listOf(index), listOf(tricky), day(1)).single()
        assertTrue(due.fromStart)

        val session = ReviewSession(listOf(due)) { day(1) }
        assertFalse(session.atCard)
        // Earlier moves are practice: a slip is shown and costs nothing, an alternative asks again.
        assertEquals(CardAnswer.WRONG, session.submit("a2a3"))
        assertEquals("e2e4", session.shown)
        assertEquals(CardAnswer.ALSO, session.submit("d2d4"))
        assertEquals(CardAnswer.RIGHT, session.submit("e2e4"))
        assertNull("the opponent's move is played for them", session.submit("e7e5"))
        assertTrue(session.playOpponent())
        session.submit("g1f3")
        session.playOpponent()
        session.submit("f1c4")
        session.playOpponent()
        assertTrue(session.atCard)
        assertEquals(CardAnswer.RIGHT, session.submit("d2d3"))
        val (card, review) = session.outcome.single()
        assertTrue("the practice slip did not count", review.firstTryRight)
        assertEquals(1, card.streak)
    }

    // ---------------------------------------------------------------- mastery and levels

    private fun reviewAll(states: Map<String, CardState>, at: Instant, right: (CardState) -> Boolean = { true }): Pair<Map<String, CardState>, List<CardReview>> {
        val out = states.mapValues { (_, card) -> RepertoireCards.afterRecall(card, right(card), hinted = false, now = at) }
        return out.mapValues { it.value.first } to out.values.map { it.second }
    }

    @Test
    fun onePassNeverMastersALevel() {
        var states = introducedAll(CORE, day0)
        val reviews = ArrayList<CardReview>()
        val (afterOne, first) = reviewAll(states, day0)
        states = afterOne
        reviews += first
        val progress = Mastery.of(book, CORE, states, reviews, zone)
        assertEquals(6, progress.total)
        assertEquals(6, progress.introduced)
        assertEquals(0, progress.settled)
        assertFalse(progress.mastered)
        assertEquals(CORE, LevelUnlocks.update(book, LevelProgress(book.id), states, reviews, day0, zone).unlocked)
    }

    @Test
    fun coreIsMasteredAfterRightRecallsOnThreeDaysAndThenStandardOpens() {
        var states = introducedAll(CORE, day0)
        val reviews = ArrayList<CardReview>()
        for (d in listOf(0L, 1L)) {
            val (next, logged) = reviewAll(states, day(d))
            states = next
            reviews += logged
        }
        val twoDays = Mastery.of(book, CORE, states, reviews, zone)
        assertEquals(6, twoDays.settled)
        assertEquals(2, twoDays.recentDays)
        assertFalse("two days are not enough", twoDays.mastered)
        val (next, logged) = reviewAll(states, day(4))
        states = next
        reviews += logged
        assertTrue(Mastery.of(book, CORE, states, reviews, zone).mastered)
        val opened = LevelUnlocks.update(book, LevelProgress(book.id), states, reviews, day(4), zone)
        assertEquals(STANDARD, opened.unlocked)
        assertEquals(day(4), opened.masteredAt[CORE])
        // Standard has cards nobody has met, so it is not mastered and Deep stays shut.
        assertEquals(STANDARD, LevelUnlocks.update(book, opened, states, reviews, day(5), zone).unlocked)
        assertTrue(opened.isOpen(STANDARD))
        assertFalse(opened.isOpen(DEEP))
    }

    @Test
    fun aMissedCardOrLowAccuracyBlocksMastery() {
        var states = introducedAll(CORE, day0)
        val reviews = ArrayList<CardReview>()
        val missedOne = key("$P g8f6")
        for ((n, d) in listOf(0L, 1L, 4L).withIndex()) {
            val (next, logged) = reviewAll(states, day(d)) { !(n == 2 && it.key == missedOne) }
            states = next
            reviews += logged
        }
        val progress = Mastery.of(book, CORE, states, reviews, zone)
        assertEquals(1, progress.learning)
        assertFalse(progress.mastered)
    }

    @Test
    fun masteryStaysOnceReached() {
        val mastered = LevelProgress(book.id, unlocked = STANDARD, masteredAt = mapOf(CORE to day0))
        assertEquals(mastered, LevelUnlocks.update(book, mastered, emptyMap(), emptyList(), day(9), zone))
    }

    // ---------------------------------------------------------------- the other side's moves

    @Test
    fun theBranchQuestionUsesTheLearnersBand() {
        val node = book.nodes.getValue(key(P))
        val overall = BranchQuestion.at(node, band = null)!!
        assertEquals(listOf("f8c5", "g8f6", "c6d4"), overall.choices.map { it.uci })
        assertEquals("under 1000, 3...Nd4 is the most common", "c6d4", BranchQuestion.at(node, band = 0)!!.answer.uci)
        assertNull("own positions ask no question", BranchQuestion.at(book.nodes.getValue(key("$P g8f6")), null))
        assertNull("one reply is no branch", BranchQuestion.at(book.nodes.getValue(key("e2e4")), null))
    }

    @Test
    fun theGuideSaysWhenTheOpponentLeavesTheTree() {
        val before = index.positionOf(key(P))
        val inTree = RepertoireGuide.afterOpponentMove(book, before, "f8c5") as TreeStatus.InTree
        assertEquals("c2c3", inTree.card?.move)
        assertEquals(TreeStatus.LeftTree, RepertoireGuide.afterOpponentMove(book, before, "a7a6"))
        val end = RepertoireIndex.play(index.positionOf(key("$P f8c5 c2c3 g8f6")), "d2d3")!!
        assertEquals(TreeStatus.EndOfLine, RepertoireGuide.afterOpponentMove(book, end, "a7a6"))
    }

    @Test
    fun sparringPlaysRepliesAsOftenAsRealPlayers() {
        val node = book.nodes.getValue(key(P))
        val random = Random(7)
        val picks = List(10_000) { RepertoireGuide.sparringPick(node, band = 0, open = STANDARD, random = random) }
        val nd4 = picks.count { (it as? SparringPick.Reply)?.reply?.uci == "c6d4" } / 10_000.0
        assertEquals(0.4, nd4, 0.03)
        // With Standard shut, 3...Nd4 is outside what is taught, so those games leave the tree.
        val shut = List(10_000) { RepertoireGuide.sparringPick(node, band = 1, open = CORE, random = random) }
        assertEquals(0.1, shut.count { it == SparringPick.Leave } / 10_000.0, 0.02)
        assertNull(RepertoireGuide.sparringPick(book.nodes.getValue(key("$P g8f6")), null, DEEP))
    }
}
