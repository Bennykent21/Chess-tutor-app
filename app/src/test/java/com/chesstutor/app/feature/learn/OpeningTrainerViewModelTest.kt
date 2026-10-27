package com.chesstutor.app.feature.learn

import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.data.learn.InMemoryTrainingRepository
import com.chesstutor.app.domain.opening.OpeningTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class OpeningTrainerViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val tree by lazy { File("src/main/assets/openings.tsv.gz").inputStream().use(OpeningTree::fromStream) }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun aDrilledLineIsSavedAndCountedOnTheList() = runTest(dispatcher) {
        val repo = InMemoryTrainingRepository()
        val vm = OpeningTrainerViewModel(repo, { tree }, clock = { 1_000L })
        backgroundScope.launch { vm.trainer.collect {} }
        advanceUntilIdle()
        val italian = vm.trainer.value.openings.first { it.opening.id == "italian" }
        assertEquals(5, italian.lines.size)
        assertEquals(0, italian.started)

        vm.start("italian")
        advanceUntilIdle()
        val drill = vm.drill.value!!
        assertEquals("the first new line", italian.lines.first().id, drill.line.id)
        // Play every move of the line; the book replies come after the pause.
        while (!drill.session.isFinished) {
            val ply = drill.session.state.value.moves.size
            if (drill.line.isPlayerPly(ply)) {
                val move = LegalMoveGenerator.generateLegalMoves(drill.session.state.value.position).first { it.uci == drill.line.moves[ply] }
                drill.session.onMove(move)
            }
            advanceUntilIdle()
        }
        advanceUntilIdle()
        val saved = vm.drill.value!!.result!!
        assertEquals(1, saved.stage)
        assertEquals(saved, repo.observeDrills().first()[drill.line.id])
        assertEquals(1, vm.trainer.value.openings.first { it.opening.id == "italian" }.started)

        vm.next()
        advanceUntilIdle()
        assertTrue("the next new line", vm.drill.value!!.line.id != drill.line.id)
    }
}
