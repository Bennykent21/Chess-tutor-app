package com.chesstutor.app.data.learn

import com.chesstutor.app.data.diagnostics.PerfLog
import android.content.res.AssetManager
import com.chesstutor.app.domain.learn.Course
import com.chesstutor.app.domain.learn.LessonCategory
import com.chesstutor.app.domain.learn.LessonFormatException
import com.chesstutor.app.domain.learn.LessonLibrary
import com.chesstutor.app.domain.learn.LessonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Loads every course file in `assets/lessons/` (one JSON file per course, plan section 11)
 * once per process. A broken file is skipped with a warning rather than crashing Learn; the
 * unit test `LessonAssetsTest` keeps every bundled file valid.
 */
class LessonAssetLoader(private val assets: AssetManager, private val folder: String = FOLDER) {
    private val lock = Mutex()
    @Volatile private var cached: LessonLibrary? = null

    suspend fun load(): LessonLibrary = cached ?: lock.withLock {
        cached ?: withContext(Dispatchers.Default) { PerfLog.timed("Lessons") { read() } }.also { cached = it }
    }

    private fun read(): LessonLibrary {
        val names = try {
            assets.list(folder).orEmpty().filter { it.endsWith(".json") }.sorted()
        } catch (_: IOException) {
            emptyList()
        }
        val courses = names.mapNotNull { name ->
            try {
                val text = assets.open("$folder/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }
                LessonParser.parseCourse(text, name)
            } catch (e: LessonFormatException) {
                android.util.Log.w("LessonAssetLoader", e.message ?: "bad lesson file $name")
                null
            } catch (e: IOException) {
                android.util.Log.w("LessonAssetLoader", "could not read $name", e)
                null
            }
        }
        return library(courses)
    }

    companion object {
        const val FOLDER = "lessons"

        /** Courses in hub order: by category, then each course's own order. Duplicate ids keep the first. */
        fun library(courses: List<Course>): LessonLibrary =
            LessonLibrary(courses.distinctBy { it.id }.sortedWith(compareBy<Course>({ LessonCategory.entries.indexOf(it.category) }, { it.order })))
    }
}
