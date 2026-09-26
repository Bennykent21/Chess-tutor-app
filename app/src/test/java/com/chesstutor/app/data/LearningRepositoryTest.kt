package com.chesstutor.app.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LearningRepositoryTest {

    @Test
    fun inMemoryObserverReflectsRecordedAttempt() = runTest {
        val repository = InMemoryLearningRepository()

        assertEquals(emptyList<com.chesstutor.app.data.model.ModuleProgress>(), repository.observeModuleProgress().first())

        repository.recordModuleAttempt("opening_fundamentals", correct = true)

        val updated = repository.observeModuleProgress().first()
        assertEquals(1, updated.size)
        assertEquals(1, updated.single().attempts)
        assertEquals(1, updated.single().correctAttempts)
    }

    @Test
    fun inMemoryObserverReflectsReset() = runTest {
        val repository = InMemoryLearningRepository()
        repository.markPracticed("endgame_opposition")

        assertEquals(1, repository.observeModuleProgress().first().size)

        repository.resetModuleProgress()

        assertEquals(emptyList<com.chesstutor.app.data.model.ModuleProgress>(), repository.observeModuleProgress().first())
    }
}
