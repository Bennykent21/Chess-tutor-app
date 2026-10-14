package com.chesstutor.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigrationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun allMigrationsArrayContainsAllVersionsFrom1To7() {
        val migrations = AppDatabase.ALL_MIGRATIONS
        assertEquals(6, migrations.size)
        assertEquals(1, migrations[0].startVersion)
        assertEquals(2, migrations[0].endVersion)
        assertEquals(2, migrations[1].startVersion)
        assertEquals(3, migrations[1].endVersion)
        assertEquals(3, migrations[2].startVersion)
        assertEquals(4, migrations[2].endVersion)
        assertEquals(4, migrations[3].startVersion)
        assertEquals(5, migrations[3].endVersion)
        assertEquals(5, migrations[4].startVersion)
        assertEquals(6, migrations[4].endVersion)
        assertEquals(6, migrations[5].startVersion)
        assertEquals(7, migrations[5].endVersion)
    }

    @Test
    fun migrations1Through7ExecuteSequentiallyAndPreserveData() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Initial Schema Version 1: review_items
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS review_items (" +
                            "id TEXT NOT NULL, fen TEXT NOT NULL, dueAt INTEGER NOT NULL, " +
                            "stage INTEGER NOT NULL DEFAULT -1, attempts INTEGER NOT NULL DEFAULT 0, " +
                            "mistakeUci TEXT NOT NULL DEFAULT '', bestMoveUci TEXT NOT NULL DEFAULT '', " +
                            "explanation TEXT NOT NULL DEFAULT '', PRIMARY KEY(id))"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase

        // 1. Insert row into v1 table
        db.execSQL("INSERT INTO review_items (id, fen, dueAt) VALUES ('item1', 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1', 1000)")

        // 2. Run Migration 1 -> 2 (linked_profile)
        AppDatabase.MIGRATION_1_2.migrate(db)
        db.execSQL(
            "INSERT INTO linked_profile (id, platform, username, rapidRating, blitzRating, bulletRating, selectedTimeControl, lastUpdated) " +
                "VALUES (1, 'CHESS_COM', 'testuser', 1500, 1400, 1300, 'RAPID', 2000)"
        )

        // 3. Run Migration 2 -> 3 (learning_profile and module_progress)
        AppDatabase.MIGRATION_2_3.migrate(db)
        db.execSQL(
            "INSERT INTO learning_profile (id, estimatedRating, assessmentState, assessmentPositionIndex, assessmentCorrect, assessmentTotal, learningGoal, totalTacticalAttempts, totalTacticalCorrect, soundEnabled, updatedAt) " +
                "VALUES (1, 1200, 'COMPLETED', 5, 4, 5, 'TACTICS', 10, 8, 1, 3000)"
        )
        db.execSQL(
            "INSERT INTO module_progress (moduleId, practiced, mastered, attempts, correctAttempts, lastPracticedAt) " +
                "VALUES ('forks', 1, 0, 5, 4, 3000)"
        )

        // 4. Run Migration 3 -> 4 (add assessmentCompletedAt to learning_profile)
        AppDatabase.MIGRATION_3_4.migrate(db)
        db.execSQL("UPDATE learning_profile SET assessmentCompletedAt = 3500 WHERE id = 1")

        // 5. Run Migration 4 -> 5 (game_records)
        AppDatabase.MIGRATION_4_5.migrate(db)
        db.execSQL(
            "INSERT INTO game_records (id, dateMillis, botName, botRating, result, pgn, moveCount, userColor, finalFen) " +
                "VALUES ('chesscom_123', 4000, 'Bot', 1500, '1-0', '1. e4', 1, 'w', 'final_fen')"
        )
        db.execSQL(
            "INSERT INTO game_records (id, dateMillis, botName, botRating, result, pgn, moveCount, userColor, finalFen) " +
                "VALUES ('arena_456', 4100, 'Magnus', 2800, '0-1', '1. d4', 1, 'b', 'final_fen_2')"
        )

        // 6. Run Migration 5 -> 6 (uciMoves on game_records)
        AppDatabase.MIGRATION_5_6.migrate(db)
        db.execSQL("UPDATE game_records SET uciMoves = 'e2e4' WHERE id = 'chesscom_123'")

        // 7. Run Migration 6 -> 7 (source, termination, timeClass, incompleteParse on game_records)
        AppDatabase.MIGRATION_6_7.migrate(db)

        // Verify data integrity in review_items
        val cursorReview = db.query("SELECT * FROM review_items WHERE id = 'item1'")
        assertTrue(cursorReview.moveToFirst())
        assertEquals("item1", cursorReview.getString(cursorReview.getColumnIndexOrThrow("id")))
        cursorReview.close()

        // Verify data integrity in linked_profile
        val cursorProfile = db.query("SELECT * FROM linked_profile WHERE id = 1")
        assertTrue(cursorProfile.moveToFirst())
        assertEquals("testuser", cursorProfile.getString(cursorProfile.getColumnIndexOrThrow("username")))
        assertEquals(1500, cursorProfile.getInt(cursorProfile.getColumnIndexOrThrow("rapidRating")))
        cursorProfile.close()

        // Verify data integrity in learning_profile
        val cursorLearning = db.query("SELECT * FROM learning_profile WHERE id = 1")
        assertTrue(cursorLearning.moveToFirst())
        assertEquals(3500L, cursorLearning.getLong(cursorLearning.getColumnIndexOrThrow("assessmentCompletedAt")))
        cursorLearning.close()

        // Verify data integrity in game_records and migration 6->7 source classification
        val cursorChessCom = db.query("SELECT * FROM game_records WHERE id = 'chesscom_123'")
        assertTrue(cursorChessCom.moveToFirst())
        assertEquals("CHESS_COM", cursorChessCom.getString(cursorChessCom.getColumnIndexOrThrow("source")))
        assertEquals("e2e4", cursorChessCom.getString(cursorChessCom.getColumnIndexOrThrow("uciMoves")))
        assertEquals("rapid", cursorChessCom.getString(cursorChessCom.getColumnIndexOrThrow("timeClass")))
        assertEquals(0, cursorChessCom.getInt(cursorChessCom.getColumnIndexOrThrow("incompleteParse")))
        cursorChessCom.close()

        val cursorArena = db.query("SELECT * FROM game_records WHERE id = 'arena_456'")
        assertTrue(cursorArena.moveToFirst())
        assertEquals("ARENA", cursorArena.getString(cursorArena.getColumnIndexOrThrow("source")))
        cursorArena.close()

        db.close()
    }

    @Test
    fun roomDatabaseBuildsAndOpensWithAllMigrations() {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .build()

        assertNotNull(db.reviewItemDao())
        assertNotNull(db.linkedProfileDao())
        assertNotNull(db.learningDao())
        assertNotNull(db.gameRecordDao())

        db.close()
    }
}
