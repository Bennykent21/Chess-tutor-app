package com.chesstutor.app.data.diagnostics

import android.content.Context
import android.content.Intent
import com.chesstutor.app.data.local.AppDatabase
import com.chesstutor.app.data.puzzle.SharedPrefsPuzzleProgressStore
import com.chesstutor.app.data.settings.AppSettings
import com.chesstutor.app.data.settings.SettingsStore
import com.chesstutor.app.feature.play.SharedPrefsActiveGameStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Erase everything" in Settings > Your data (plan 6.4): every table, every setting, the
 * unfinished game, the old puzzle progress and the crash log. The app then restarts, so
 * nothing erased lingers in memory.
 */
object DataReset {

    suspend fun eraseEverything(context: Context, db: AppDatabase, settings: SettingsStore) = withContext(Dispatchers.IO) {
        db.clearAllTables()
        settings.update { AppSettings() }
        SharedPrefsActiveGameStore(context).clear()
        // Otherwise the puzzle history would copy the pre-Room progress back in on the next start.
        SharedPrefsPuzzleProgressStore(context).clearLegacy()
        CrashLog.of(context).clear()
    }

    /** Opens the app afresh in a new process. */
    fun restart(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}
