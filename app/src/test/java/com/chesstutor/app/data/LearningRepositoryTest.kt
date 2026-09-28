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
    fun inMemoryObserverTracksAttemptAccuracy() {
        val repository = InMemoryLearningRepository()

        kotlinx.coroutines.runBlocking {
            repository.recordModuleAttempt("forks", correct = true)
            repository.recordModuleAttempt("forks", correct = false)
        }

        val progress = kotlinx.coroutines.runBlocking {
            repository.getModuleProgress().single()
        }

        org.junit.Assert.assertEquals(2, progress.attempts)
        org.junit.Assert.assertEquals(1, progress.correctAttempts)
        org.junit.Assert.assertEquals(0.5f, progress.accuracy)
    }

    @Test
    fun inMemoryObserverReflectsMastery() = runTest {
        val repository = InMemoryLearningRepository()

        repository.markMastered("forks")

        val progress = repository.observeModuleProgress().first().single()
        assertEquals(true, progress.practiced)
        assertEquals(true, progress.mastered)
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
