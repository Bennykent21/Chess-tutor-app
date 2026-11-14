package com.chesstutor.app.data.opening

import android.content.Context
import androidx.core.content.edit
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.domain.opening.MyLine
import com.chesstutor.app.domain.opening.MyLineStore
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The player's own lines as one JSON list in SharedPreferences ("my_lines"). */
class SharedPrefsMyLines(context: Context) : MyLineStore {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    override fun lines(): StateFlow<List<MyLine>> = state.asStateFlow()

    @Synchronized
    override fun save(line: MyLine) {
        write(state.value.filterNot { it.id == line.id } + line)
    }

    @Synchronized
    override fun delete(id: String) {
        write(state.value.filterNot { it.id == id })
    }

    /** Forgets every line ("Erase everything"). */
    fun clear() {
        prefs.edit { clear() }
        state.value = emptyList()
    }

    private fun write(lines: List<MyLine>) {
        val json = adapter.toJson(lines.map { mapOf("id" to it.id, "name" to it.name, "side" to it.side.name, "moves" to it.moves) })
        prefs.edit { putString(KEY, json) }
        state.value = lines
    }

    private fun read(): List<MyLine> {
        val json = prefs.getString(KEY, null) ?: return emptyList()
        val list = runCatching { adapter.fromJson(json) as? List<*> }.getOrNull().orEmpty()
        return list.mapNotNull { entry ->
            val map = entry as? Map<*, *> ?: return@mapNotNull null
            runCatching {
                MyLine(map["id"] as String, map["name"] as String, PieceColor.valueOf(map["side"] as String), (map["moves"] as List<*>).map { it as String })
            }.getOrNull()
        }
    }

    private companion object {
        const val PREFS_NAME = "my_lines"
        const val KEY = "lines"
        val adapter = Moshi.Builder().build().adapter(Any::class.java)
    }
}
