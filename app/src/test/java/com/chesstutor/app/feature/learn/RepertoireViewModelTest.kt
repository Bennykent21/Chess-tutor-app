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
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    private fun TestScope.playThrough(vm: RepertoireViewModel, seen: MutableList<RunMoment> = mutableListOf(), branchPick: Int = 0) {
        repeat(200) {
            advanceUntilIdle()
            val run = vm.run.value ?: return
            seen += run.moment
            when (val moment = run.moment) {
                is RunMoment.Finished -> return
                is RunMoment.Reveal, is RunMoment.CardDone -> vm.next()
                is RunMoment.Branch -> if (moment.picked == null) vm.pickBranch(moment.question.choices[branchPick].uci) else vm.next()
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
        assertEquals(6, ui.fresh)
        assertEquals(0, ui.due)
        assertEquals(6, ui.mastery.getValue(RepertoireLevel.CORE).total)
        val chart = ui.chart!!
        assertEquals("3.Bc4", chart.after)
        assertEquals(listOf("Bc5", "Nf6", "Nd4"), chart.replies.map { it.san })
        assertEquals(listOf(false, false, true), chart.replies.map { it.error })
        assertEquals("no database name in the test tree, so the title", "Italian Game", ui.battleLine)
    }

    @Test
    fun aLearnRunGuessesRevealsAsksTheBranchThenRecallsAndSaves() = runTest(dispatcher) {
        val repo = InMemoryRepertoireProgressRepository()
        val vm = model(repo)
        vm.startLearn("italian")
        val seen = mutableListOf<RunMoment>()
        playThrough(vm, seen)
        val reveals = seen.filterIsInstance<RunMoment.Reveal>().distinctBy { it.san }
        assertEquals(listOf("1.e4", "2.Nf3", "3.Bc4", "4.c3", "5.d3"), reveals.map { it.san })
        assertTrue("the first move was guessed right", reveals.first().guessed)
        assertTrue("the branch after 3.Bc4 was asked", seen.any { it is RunMoment.Branch })
        assertTrue("the recall pass started from the top", seen.any { it == RunMoment.FromTheTop })
        val finished = seen.last() as RunMoment.Finished
        assertEquals(5, finished.total)
        assertEquals(5, finished.right)
        val cards = repo.observeCards().first()
        assertEquals(5, cards.size)
        assertTrue(cards.values.all { it.stage == 0 && it.dueAt == day0.plus(Duration.ofDays(1)) })
        assertEquals(5, repo.reviews("italian").size)
        advanceUntilIdle()
        assertEquals(1, vm.state.value.book("italian")!!.fresh)
        assertTrue(vm.run.value!!.moreToLearn)
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
        advanceUntilIdle()
        val done = vm.run.value!!.moment as RunMoment.CardDone
        assertFalse(done.right)
        vm.next()
        advanceUntilIdle()
        val finished = vm.run.value!!.moment as RunMoment.Finished
        assertEquals(0, finished.right)
        assertTrue(repo.observeCards().first().getValue(card.id).learning)
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
