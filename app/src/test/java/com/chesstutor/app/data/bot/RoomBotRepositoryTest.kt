package com.chesstutor.app.data.bot

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.chesstutor.app.data.local.AppDatabase
import com.chesstutor.app.domain.bot.BotDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomBotRepositoryTest {

    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val repository = RoomBotRepository(db.botProfileDao(), clock = { 1_000 })

    @After
    fun tearDown() = db.close()

    @Test
    fun botsAreSavedEditedListedAndDeleted() = runBlocking {
        val first = BotDraft(name = "Captain Fork").toCustomBot("my_1", now = 10)
        val second = BotDraft(name = "Rowan", rating = 2000).toCustomBot("my_2", now = 20)
        repository.save(first)
        repository.save(second)
        assertEquals(listOf("Captain Fork", "Rowan"), repository.observe().first().map { it.profile.name })

        val renamed = first.copy(profile = first.profile.copy(name = "Major Pin"))
        repository.save(renamed)
        assertEquals(renamed, repository.get("my_1"))
        assertEquals("oldest first, editing keeps the place", listOf("Major Pin", "Rowan"), repository.observe().first().map { it.profile.name })

        repository.delete("my_1")
        assertNull(repository.get("my_1"))
        assertEquals(1, repository.observe().first().size)
    }
}
