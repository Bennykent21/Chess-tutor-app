package com.chesstutor.app.data

import com.chesstutor.app.data.model.LearningProfile
import com.chesstutor.app.data.repository.InMemoryLearningRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LearningRepositoryTest {

    @Test
    fun inMemoryProfileRoundTrips() = runTest {
        val repository = InMemoryLearningRepository()
        assertEquals(LearningProfile(), repository.getProfile())
        val saved = LearningProfile(estimatedRating = 1200, assessmentState = "COMPLETE")
        repository.saveProfile(saved)
        assertEquals(saved, repository.getProfile())
    }
}
