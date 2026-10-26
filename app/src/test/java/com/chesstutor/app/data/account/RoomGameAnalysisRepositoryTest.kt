package com.chesstutor.app.data.account

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.data.local.AppDatabase
import com.chesstutor.app.domain.account.AnalysisSummary
import com.chesstutor.app.domain.account.PlyEval
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomGameAnalysisRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: RoomGameAnalysisRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = RoomGameAnalysisRepository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun savesEvalsInPlyOrderAndSummariesPerGame() = runBlocking {
        repo.saveEval("g", PlyEval(1, -20, null, "e7e5", listOf("e7e5", "g1f3"), depth = 14))
        repo.saveEval("g", PlyEval(0, 25, null, "e2e4", listOf("e2e4"), depth = 14))
        repo.saveEval("g", PlyEval(2, null, -3, "d8h4", emptyList(), depth = 12, lite = true))
        repo.saveEval("other", PlyEval(0, 0, null, "d2d4"))

        val evals = repo.evals("g")
        assertEquals(listOf(0, 1, 2), evals.map { it.ply })
        assertEquals(listOf("e7e5", "g1f3"), evals[1].line)
        assertEquals(-3, evals[2].mate)
        assertTrue(evals[2].lite)
        assertEquals(3, repo.observeEvals("g").first().size)

        // Saving a ply again replaces it.
        repo.saveEval("g", PlyEval(0, 30, null, "d2d4"))
        assertEquals("d2d4", repo.evals("g").first().bestMove)

        repo.saveSummary(AnalysisSummary("g", plies = 2, whiteAccuracy = 91.5, blackAccuracy = 40.0, lite = true, analysedAt = 7))
        assertEquals(91.5, repo.observeSummaries().first().getValue("g").whiteAccuracy!!, 0.001)

        repo.delete("g")
        assertTrue(repo.evals("g").isEmpty())
        assertTrue(repo.observeSummaries().first().isEmpty())
        assertEquals("other games keep their analysis", 1, repo.evals("other").size)
    }
}
