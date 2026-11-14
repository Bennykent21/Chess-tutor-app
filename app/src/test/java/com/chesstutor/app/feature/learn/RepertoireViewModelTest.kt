package com.chesstutor.app.feature.learn

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.data.repertoire.InMemoryRepertoireProgressRepository
import com.chesstutor.app.domain.repertoire.CardReview
import com.chesstutor.app.domain.repertoire.RepertoireCards
import com.chesstutor.app.domain.repertoire.RepertoireLevel
import com.chesstutor.app.domain.repertoire.TestRepertoire
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class RepertoireViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val book = TestRepertoire.italian()
    private val day0: Instant = Instant.parse("2026-11-04T09:00:00Z")
    private var now = day0

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.model(repo: InMemoryRepertoireProgressRepository): RepertoireViewModel {
        val vm = RepertoireViewModel(repo, { listOf(book) }, rating = { 1200 }, clock = { now }, zone = ZoneOffset.UTC)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        return vm
    }

    /** Plays the run to its end with the repertoire's own moves, answering every question with [branchPick]. */
    /** Plays the repertoire's own moves until the run is over; nothing waits for a tap any more. */
    private fun TestScope.playThrough(vm: RepertoireViewModel, seen: MutableList<RunMoment> = mutableListOf()) {
        repeat(200) {
            advanceUntilIdle()
            val run = vm.run.value ?: return
            seen += run.moment
            when (run.moment) {
                is RunMoment.Finished -> return
                is RunMoment.CardDone -> Unit
                else -> {
                    val position = run.board.state.value.position
                    val uci = book.at(position)?.move ?: error("no repertoire move here")
                    run.board.onMove(LegalMoveGenerator.generateLegalMoves(position).first { it.uci == uci })
                }
            }
        }
        error("the run did not finish")
    }

    @Test
    fun thePageShowsLevelsNewCardsAndTheRepliesAtTheFirstBranch() = runTest(dispatcher) {
        val vm = model(InMemoryRepertoireProgressRepository())
        val ui = vm.state.value.book("italian")!!
        assertEquals(RepertoireLevel.CORE, ui.levels.unlocked)
        assertEquals("1.e4, 2.Nf3 and 3.Bc4 are the opening itself, not cards", 3, ui.fresh)
        assertEquals(0, ui.due)
        assertEquals(3, ui.mastery.getValue(RepertoireLevel.CORE).total)
        val chart = ui.chart!!
        assertEquals("3.Bc4", chart.after)
        assertEquals(listOf("Bc5", "Nf6", "Nd4"), chart.replies.map { it.san })
        assertEquals(listOf(false, false, true), chart.replies.map { it.error })
        assertEquals("no database name in the test tree, so the title", "Italian Game", ui.battleLine)
    }

    @Test
    fun aLearnRunFlowsThroughBothPassesWithoutATapAndSaves() = runTest(dispatcher) {
        val repo = InMemoryRepertoireProgressRepository()
        val vm = model(repo)
        vm.startLearn("italian")
        advanceUntilIdle()
        assertEquals("the line was watched to its end, then it is the learner's turn", RunMoment.FromTheTop, vm.run.value!!.moment)
        assertEquals("the defining moves and 3...Bc5 are on the board again", 6, vm.run.value!!.board.state.value.moves.size)
        val seen = mutableListOf<RunMoment>()
        playThrough(vm, seen)
        val finishedEarly = seen.first()
        assertEquals(RunMoment.FromTheTop, finishedEarly)
        val finished = seen.last() as RunMoment.Finished
        assertEquals(2, finished.total)
        assertEquals(2, finished.right)
        val cards = repo.observeCards().first()
        assertEquals(2, cards.size)
        assertTrue(cards.values.all { it.stage == 0 && it.dueAt == day0.plus(Duration.ofDays(1)) })
        assertEquals(2, repo.reviews("italian").size)
        advanceUntilIdle()
        assertEquals(1, vm.state.value.book("italian")!!.fresh)
        assertTrue(vm.run.value!!.moreToLearn)
    }

    @Test
    fun theLineIsWatchedFirstThenAWrongMoveIsNotPlayed() = runTest(dispatcher) {
        val vm = model(InMemoryRepertoireProgressRepository())
        vm.startLearn("italian")
        runCurrent()
        assertEquals("the line starts by playing itself", RunMoment.Watch(null, null), vm.run.value!!.moment)
        advanceTimeBy(RepertoireViewModel.REPLY_DELAY_MS + RepertoireViewModel.WATCH_MOVE_MS + 1)
        val arrow = vm.run.value!!.moment as RunMoment.Watch
        assertEquals("the learner's move shows as an arrow before it is played", "c2c3", arrow.arrow)
        advanceUntilIdle()
        val board = vm.run.value!!.board
        val start = board.state.value.position
        assertEquals(6, board.state.value.moves.size)
        // 4.d3 is as good as 4.c3, but not this repertoire's move: after watching, it is not named.
        board.onMove(LegalMoveGenerator.generateLegalMoves(start).first { it.uci == "d2d3" })
        advanceUntilIdle()
        assertEquals(RunMoment.Also(null, null), vm.run.value!!.moment)
        assertEquals(6, board.state.value.moves.size)
        // A wrong move slides back too, and the right one is drawn.
        board.onMove(LegalMoveGenerator.generateLegalMoves(start).first { it.uci == "a2a3" })
        advanceUntilIdle()
        assertEquals(RunMoment.Shown("4.c3", "c2c3"), vm.run.value!!.moment)
        assertEquals(6, board.state.value.moves.size)
        board.onMove(LegalMoveGenerator.generateLegalMoves(start).first { it.uci == "c2c3" })
        advanceUntilIdle()
        assertEquals("the learner's move, then the reply by itself", listOf("c2c3", "g8f6"), board.state.value.moves.drop(6).map { it.uci })
    }

    @Test
    fun aWrongMoveInReviewCountsAsMissedAndShowsTheAnswer() = runTest(dispatcher) {
        val repo = InMemoryRepertoireProgressRepository()
        val card = RepertoireCards.introduce(book.id, book.nodes.getValue(TestRepertoire.key("${TestRepertoire.P} g8f6")), day0.minus(Duration.ofDays(1)))
        repo.save(listOf(card), emptyList())
        val vm = model(repo)
        assertEquals(1, vm.state.value.book("italian")!!.due)
        vm.startReview("italian")
        advanceUntilIdle()
        val run = vm.run.value!!
        assertEquals("3...Nf6", run.against)
        val position = run.board.state.value.position
        run.board.onMove(LegalMoveGenerator.generateLegalMoves(position).first { it.uci == "b1c3" })
        advanceUntilIdle()
        val shown = vm.run.value!!.moment as RunMoment.Shown
        assertEquals("d2d3", shown.uci)
        assertEquals("4.d3", shown.san)
        run.board.onMove(LegalMoveGenerator.generateLegalMoves(position).first { it.uci == "d2d3" })
        val done = vm.run.value!!.moment as RunMoment.CardDone
        assertFalse(done.right)
        assertEquals("a green ring on the right move", "d3", vm.run.value!!.ringSquare)
        // No Next: the run moves on by itself after a moment.
        advanceUntilIdle()
        val finished = vm.run.value!!.moment as RunMoment.Finished
        assertEquals(0, finished.right)
        assertTrue(repo.observeCards().first().getValue(card.id).learning)
    }

    @Test
    fun aSecondHintShowsTheMoveAndASlipGetsARedRing() = runTest(dispatcher) {
        val vm = model(InMemoryRepertoireProgressRepository())
        vm.startLearn("italian")
        advanceUntilIdle()
        assertEquals("the watch is over", RunMoment.FromTheTop, vm.run.value!!.moment)
        vm.hint()
        assertEquals("first the piece to move", "c2", vm.run.value!!.hintFrom)
        vm.hint()
        assertEquals(RunMoment.Shown("4.c3", "c2c3"), vm.run.value!!.moment)
        assertNull(vm.run.value!!.hintFrom)
        val board = vm.run.value!!.board
        board.onMove(LegalMoveGenerator.generateLegalMoves(board.state.value.position).first { it.uci == "a2a3" })
        assertEquals("a3", vm.run.value!!.ringSquare)
        assertFalse(vm.run.value!!.ringRight)
        assertFalse("no note in the test tree, so nothing to explain", vm.run.value!!.canWhy)
    }

    @Test
    fun masteringCoreShowsTheBronzeCupAndOpensStandard() = runTest(dispatcher) {
        val repo = InMemoryRepertoireProgressRepository()
        val cards = book.cards(RepertoireLevel.CORE).map {
            RepertoireCards.introduce(book.id, it, day0.minus(Duration.ofDays(9))).copy(stage = 1, attempts = 3, dueAt = day0)
        }
        val reviews = cards.flatMap { c -> (1..3).map { d -> CardReview(c.id, book.id, RepertoireLevel.CORE, day0.minus(Duration.ofDays(d.toLong())), firstTryRight = true, hinted = false) } }
        repo.save(cards, reviews)
        val vm = model(repo)
        vm.startReview("italian")
        playThrough(vm)
        advanceUntilIdle()
        val run = vm.run.value!!
        assertEquals(RepertoireLevel.CORE, run.mastered)
        assertEquals("Italian Game", run.masteredBook)
        assertEquals(2, run.nextLevelCards)
        assertEquals(RepertoireLevel.STANDARD, vm.state.value.book("italian")!!.levels.unlocked)
        vm.dismissMastered()
        assertEquals(null, vm.run.value!!.mastered)
        assertNotNull(repo.observeLevels().first()["italian"]!!.masteredAt[RepertoireLevel.CORE])
    }
}
