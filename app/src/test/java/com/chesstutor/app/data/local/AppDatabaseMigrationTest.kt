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

    private companion object {
        const val LATEST = 13
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun allMigrationsArrayContainsAllVersionsFrom1ToLatest() {
        val migrations = AppDatabase.ALL_MIGRATIONS
        assertEquals(LATEST - 1, migrations.size)
        migrations.forEachIndexed { i, m ->
            assertEquals(i + 1, m.startVersion)
            assertEquals(i + 2, m.endVersion)
        }
        assertEquals(LATEST, migrations.last().endVersion)
        assertEquals(10, migrations[9].startVersion)
        assertEquals(11, migrations[9].endVersion)
        assertEquals(9, migrations[8].startVersion)
        assertEquals(10, migrations[8].endVersion)
        assertEquals(8, migrations[7].startVersion)
        assertEquals(9, migrations[7].endVersion)
        assertEquals(7, migrations[6].startVersion)
        assertEquals(8, migrations[6].endVersion)
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

    /**
     * Builds a real version-7 database file, then lets Room open it at version 8. Room runs
     * MIGRATION_7_8 and refuses to open if the new tables do not match the entities exactly,
     * so this proves the hand-written SQL. Existing data must survive.
     */
    @Test
    fun migration7To8IsAcceptedByRoomAndKeepsData() {
        val name = "migration-7-8-test.db"
        context.deleteDatabase(name)
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS review_items (" +
                            "id TEXT NOT NULL, fen TEXT NOT NULL, dueAt INTEGER NOT NULL, " +
                            "stage INTEGER NOT NULL DEFAULT -1, attempts INTEGER NOT NULL DEFAULT 0, " +
                            "mistakeUci TEXT NOT NULL DEFAULT '', bestMoveUci TEXT NOT NULL DEFAULT '', " +
                            "explanation TEXT NOT NULL DEFAULT '', PRIMARY KEY(id))"
                    )
                    AppDatabase.ALL_MIGRATIONS.filter { it.endVersion <= 7 }.forEach { it.migrate(db) }
                    db.execSQL("INSERT INTO review_items (id, fen, dueAt) VALUES ('kept', 'fen', 1)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(config).also { it.writableDatabase.close() }

        val room = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        val db = room.openHelper.writableDatabase
        assertEquals(LATEST, db.version)
        db.query("SELECT id FROM review_items").use { assertTrue(it.moveToFirst()); assertEquals("kept", it.getString(0)) }
        listOf("puzzle_attempt", "xp_event", "puzzle_profile", "puzzle_level_progress", "report_issue").forEach { table ->
            db.query("SELECT COUNT(*) FROM $table").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
        }
        room.close()
        context.deleteDatabase(name)
    }

    /**
     * Builds a real version-8 database with a saved game and puzzle history, then lets Room
     * open it at version 9: MIGRATION_8_9 must create bot_profile exactly as the entity
     * describes, and keep the old data.
     */
    @Test
    fun migration8To9AddsMyBotsAndKeepsData() {
        val name = "migration-8-9-test.db"
        context.deleteDatabase(name)
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(8) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS review_items (" +
                            "id TEXT NOT NULL, fen TEXT NOT NULL, dueAt INTEGER NOT NULL, " +
                            "stage INTEGER NOT NULL DEFAULT -1, attempts INTEGER NOT NULL DEFAULT 0, " +
                            "mistakeUci TEXT NOT NULL DEFAULT '', bestMoveUci TEXT NOT NULL DEFAULT '', " +
                            "explanation TEXT NOT NULL DEFAULT '', PRIMARY KEY(id))"
                    )
                    AppDatabase.ALL_MIGRATIONS.filter { it.endVersion <= 8 }.forEach { it.migrate(db) }
                    db.execSQL("INSERT INTO review_items (id, fen, dueAt) VALUES ('kept', 'fen', 1)")
                    db.execSQL("INSERT INTO xp_event (amount, reason, createdAt) VALUES (10, 'solve', 5)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(config).also { it.writableDatabase.close() }

        val room = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        val db = room.openHelper.writableDatabase
        assertEquals(LATEST, db.version)
        db.query("SELECT id FROM review_items").use { assertTrue(it.moveToFirst()); assertEquals("kept", it.getString(0)) }
        db.query("SELECT amount FROM xp_event").use { assertTrue(it.moveToFirst()); assertEquals(10, it.getInt(0)) }
        db.query("SELECT COUNT(*) FROM bot_profile").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
        room.close()
        context.deleteDatabase(name)
    }

    /**
     * Builds a real version-9 database with a bot and some XP, then lets Room open it at
     * version 10: MIGRATION_9_10 must create lesson_progress exactly as the entity describes.
     */
    @Test
    fun migration9To10AddsLessonProgressAndKeepsData() {
        val name = "migration-9-10-test.db"
        context.deleteDatabase(name)
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(9) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS review_items (" +
                            "id TEXT NOT NULL, fen TEXT NOT NULL, dueAt INTEGER NOT NULL, " +
                            "stage INTEGER NOT NULL DEFAULT -1, attempts INTEGER NOT NULL DEFAULT 0, " +
                            "mistakeUci TEXT NOT NULL DEFAULT '', bestMoveUci TEXT NOT NULL DEFAULT '', " +
                            "explanation TEXT NOT NULL DEFAULT '', PRIMARY KEY(id))"
                    )
                    AppDatabase.ALL_MIGRATIONS.filter { it.endVersion <= 9 }.forEach { it.migrate(db) }
                    db.execSQL("INSERT INTO xp_event (amount, reason, createdAt) VALUES (10, 'solve', 5)")
                    db.execSQL(
                        "INSERT INTO bot_profile VALUES ('my_1', 'Rowan', 1200, 'BALANCED', 0.5, 0.5, 0.2, 'NORMAL', " +
                            "'NEVER', '', '', 'fox', 'RAPID_10', 1, 1, 0, 1, 2)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(config).also { it.writableDatabase.close() }

        val room = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        val db = room.openHelper.writableDatabase
        assertEquals(LATEST, db.version)
        db.query("SELECT amount FROM xp_event").use { assertTrue(it.moveToFirst()); assertEquals(10, it.getInt(0)) }
        db.query("SELECT name FROM bot_profile").use { assertTrue(it.moveToFirst()); assertEquals("Rowan", it.getString(0)) }
        db.query("SELECT COUNT(*) FROM lesson_progress").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
        room.close()
        context.deleteDatabase(name)
    }

    /**
     * Builds a real version-10 database with a saved game and lesson progress, then lets Room
     * open it at version 11: MIGRATION_10_11 must create analysis_result and game_analysis
     * exactly as the entities describe.
     */
    @Test
    fun migration10To11AddsGameAnalysisAndKeepsData() {
        val name = "migration-10-11-test.db"
        context.deleteDatabase(name)
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(10) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS review_items (" +
                            "id TEXT NOT NULL, fen TEXT NOT NULL, dueAt INTEGER NOT NULL, " +
                            "stage INTEGER NOT NULL DEFAULT -1, attempts INTEGER NOT NULL DEFAULT 0, " +
                            "mistakeUci TEXT NOT NULL DEFAULT '', bestMoveUci TEXT NOT NULL DEFAULT '', " +
                            "explanation TEXT NOT NULL DEFAULT '', PRIMARY KEY(id))"
                    )
                    AppDatabase.ALL_MIGRATIONS.filter { it.endVersion <= 10 }.forEach { it.migrate(db) }
                    db.execSQL(
                        "INSERT INTO game_records (id, dateMillis, botName, botRating, result, pgn, moveCount, userColor, finalFen, uciMoves) " +
                            "VALUES ('bot_1', 5, 'Pip', 800, '1-0', '', 2, 'white', '', 'e2e4 e7e5')"
                    )
                    db.execSQL("INSERT INTO lesson_progress VALUES ('basics-check', 2, 6, 0, 1, 0, 3, 0, 0, NULL, 9)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(config).also { it.writableDatabase.close() }

        val room = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        val db = room.openHelper.writableDatabase
        assertEquals(LATEST, db.version)
        db.query("SELECT botName FROM game_records").use { assertTrue(it.moveToFirst()); assertEquals("Pip", it.getString(0)) }
        db.query("SELECT stepIndex FROM lesson_progress").use { assertTrue(it.moveToFirst()); assertEquals(2, it.getInt(0)) }
        db.query("SELECT COUNT(*) FROM analysis_result").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
        db.query("SELECT COUNT(*) FROM game_analysis").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
        room.close()
        context.deleteDatabase(name)
    }

    /**
     * A real version-11 database with one analysed position: MIGRATION_11_12 adds the
     * second-line columns (empty for old rows) and keeps the analysis.
     */
    @Test
    fun migration11To12AddsSecondLineAndKeepsAnalysis() {
        val name = "migration-11-12-test.db"
        context.deleteDatabase(name)
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(11) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS review_items (" +
                            "id TEXT NOT NULL, fen TEXT NOT NULL, dueAt INTEGER NOT NULL, " +
                            "stage INTEGER NOT NULL DEFAULT -1, attempts INTEGER NOT NULL DEFAULT 0, " +
                            "mistakeUci TEXT NOT NULL DEFAULT '', bestMoveUci TEXT NOT NULL DEFAULT '', " +
                            "explanation TEXT NOT NULL DEFAULT '', PRIMARY KEY(id))"
                    )
                    AppDatabase.ALL_MIGRATIONS.filter { it.endVersion <= 11 }.forEach { it.migrate(db) }
                    db.execSQL("INSERT INTO analysis_result VALUES ('bot_1', 0, 20, NULL, 'e2e4', 'e2e4 e7e5', 14, 0)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(config).also { it.writableDatabase.close() }

        val room = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        val db = room.openHelper.writableDatabase
        assertEquals(LATEST, db.version)
        db.query("SELECT bestMove, secondCentipawns, secondMate FROM analysis_result").use {
            assertTrue(it.moveToFirst())
            assertEquals("e2e4", it.getString(0))
            assertTrue(it.isNull(1))
            assertTrue(it.isNull(2))
        }
        room.close()
        context.deleteDatabase(name)
    }

    /** A real version-12 database: MIGRATION_12_13 adds the practice and opening drill tables and keeps the games. */
    @Test
    fun migration12To13AddsTrainingTablesAndKeepsData() {
        val name = "migration-12-13-test.db"
        context.deleteDatabase(name)
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(12) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS review_items (" +
                            "id TEXT NOT NULL, fen TEXT NOT NULL, dueAt INTEGER NOT NULL, " +
                            "stage INTEGER NOT NULL DEFAULT -1, attempts INTEGER NOT NULL DEFAULT 0, " +
                            "mistakeUci TEXT NOT NULL DEFAULT '', bestMoveUci TEXT NOT NULL DEFAULT '', " +
                            "explanation TEXT NOT NULL DEFAULT '', PRIMARY KEY(id))"
                    )
                    AppDatabase.ALL_MIGRATIONS.filter { it.endVersion <= 12 }.forEach { it.migrate(db) }
                    db.execSQL(
                        "INSERT INTO game_records (id, dateMillis, botName, botRating, result, pgn, moveCount, userColor, finalFen, uciMoves) " +
                            "VALUES ('bot_1', 5, 'Pip', 800, '1-0', '', 2, 'white', '', 'e2e4 e7e5')"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(config).also { it.writableDatabase.close() }

        val room = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        val db = room.openHelper.writableDatabase
        assertEquals(LATEST, db.version)
        db.query("SELECT botName FROM game_records").use { assertTrue(it.moveToFirst()); assertEquals("Pip", it.getString(0)) }
        db.query("SELECT COUNT(*) FROM course_practice").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
        db.query("SELECT COUNT(*) FROM opening_drill").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
        room.close()
        context.deleteDatabase(name)
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
        assertNotNull(db.puzzleDao())
        assertNotNull(db.botProfileDao())
        assertNotNull(db.lessonProgressDao())
        assertNotNull(db.analysisDao())

        db.close()
    }
}
