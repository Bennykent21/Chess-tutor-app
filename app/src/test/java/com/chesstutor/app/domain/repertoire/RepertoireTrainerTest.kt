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
    fun theDefiningMovesAreNotCards() {
        assertTrue(book.cards().none { it.ply < P.split(' ').size })
        assertEquals(listOf(key("$P f8c5"), key("$P g8f6"), key("$P f8c5 c2c3 g8f6")), book.cards(CORE).map { it.key })
        assertEquals(key(P), book.startPosition.repetitionKey())
        assertEquals(5, book.startPlies("$P g8f6".split(' ')))
        assertEquals("a path that does not start with the opening", 0, book.startPlies(listOf("d2d4")))
    }

    @Test
    fun learningStartsAtTheTopOfTheMostCommonLine() {
        val first = LearnPlanner.next(index, CORE, emptySet())!!
        assertEquals("$P f8c5 c2c3 g8f6 d2d3".split(' '), first.moves)
        assertEquals(listOf(key("$P f8c5"), key("$P f8c5 c2c3 g8f6")), first.newCards)
        val second = LearnPlanner.next(index, CORE, first.newCards.toSet())!!
        assertEquals("4...Be7 and 4...Bc5 are not open yet", listOf(key("$P g8f6")), second.newCards)
        assertEquals("$P g8f6 d2d3".split(' '), second.moves)
        assertNull(LearnPlanner.next(index, CORE, (first.newCards + second.newCards).toSet()))
        val standard = LearnPlanner.next(index, STANDARD, (first.newCards + second.newCards).toSet())!!
        assertEquals(listOf(key("$P g8f6 d2d3 f8e7")), standard.newCards)
    }

    @Test
    fun aVariationCanBeChosen() {
        val twoKnights = LearnPlanner.next(index, CORE, emptySet(), within = "$P g8f6".split(' '))!!
        assertEquals("$P g8f6 d2d3".split(' '), twoKnights.moves)
        assertEquals(listOf(key("$P g8f6")), twoKnights.newCards)
        assertNull("nothing new left inside it", LearnPlanner.next(index, CORE, twoKnights.newCards.toSet(), within = "$P g8f6".split(' ')))
        assertEquals("Giuoco Pianissimo", LearnSegment(emptyList(), emptyList(), "Italian Game: Giuoco Pianissimo").variation)
        assertEquals("Italian Game", LearnSegment(emptyList(), emptyList(), "Italian Game").variation)
    }

    @Test
    fun aLearnRunGuessesFirstThenRecalls() {
        val segment = LearnSegment("$P g8f6 d2d3".split(' '), listOf(key("$P g8f6")))
        val run = LearnRun(index, segment) { day0 }
        assertEquals("the defining moves are already on the board", 5, run.ply)
        assertEquals(key(P), run.position.repetitionKey())
        assertFalse(run.isOwnTurn)
        assertTrue(run.playOpponent())
        assertTrue(run.isNewCard)
        // A wrong guess costs nothing: it is not played, the right move is shown, and the learner plays it.
        assertEquals(LearnMoveResult.REVEALED, run.submit("b1c3"))
        assertEquals(LearnRun.Pass.GUESS, run.pass)
        assertEquals("d2d3", run.shown)
        assertTrue(run.isNewCard)
        assertEquals("after a reveal, playing it is RIGHT, not GUESSED", LearnMoveResult.RIGHT, run.submit("d2d3"))
        assertEquals("the segment ended, so the recall pass starts from the top", LearnRun.Pass.RECALL, run.pass)
        assertEquals("the top is where the defining moves end", 5, run.ply)
        assertTrue(run.outcome.isEmpty())
        assertTrue(run.playOpponent())
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
        assertEquals("after 1.e4 c6, White moves first", 2, run.ply)
        assertFalse(run.isOwnTurn)
        assertTrue(run.playOpponent())
        assertTrue(run.isOwnTurn)
        assertEquals(LearnMoveResult.GUESSED, run.submit("d7d5"))
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
        // The first card is 4.c3: an alternative asks again without penalty, then the right move.
        assertEquals(CardAnswer.ALSO, session.submit("d2d3"))
        assertEquals(CardAnswer.RIGHT, session.submit("c2c3"))
        assertTrue(session.done)
        session.next()
        // 2...d5: wrong first, then right: counts as missed.
        assertEquals(CardAnswer.WRONG, session.submit("e7e5"))
        assertEquals("d7d5", session.shown)
        assertEquals(CardAnswer.RIGHT, session.submit("d7d5"))
        session.next()
        // A hint keeps a right answer close.
        session.hint()
        session.submit("d2d3")
        session.next()
        session.showAnswer()
        session.next()
        session.submit("d2d3")
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
        val deep = key("$P f8c5 c2c3 g8f6")
        val tricky = RepertoireCards.introduce(book.id, book.nodes.getValue(deep), day0).copy(attempts = 4, lapses = 2)
        val plain = RepertoireCards.introduce(book.id, book.nodes.getValue(key("$P f8c5")), day0.plusSeconds(1))
        assertEquals(listOf(deep), ReviewQueue.tricky(listOf(index), listOf(plain, tricky)).map { it.node.key })
        val due = ReviewQueue.build(listOf(index), listOf(tricky), day(1)).single()
        assertTrue(due.fromStart)

        val session = ReviewSession(listOf(due)) { day(1) }
        assertFalse(session.atCard)
        assertEquals("the line starts where the defining moves end", 5, session.ply)
        assertNull("the opponent's move is played for them", session.submit("f8c5"))
        assertTrue(session.playOpponent())
        // Earlier moves are practice: a slip is shown and costs nothing, an alternative asks again.
        assertEquals(CardAnswer.WRONG, session.submit("a2a3"))
        assertEquals("c2c3", session.shown)
        assertEquals(CardAnswer.ALSO, session.submit("e1g1"))
        assertEquals(CardAnswer.RIGHT, session.submit("c2c3"))
        assertTrue(session.playOpponent())
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
        assertEquals(3, progress.total)
        assertEquals(3, progress.introduced)
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
        assertEquals(3, twoDays.settled)
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
