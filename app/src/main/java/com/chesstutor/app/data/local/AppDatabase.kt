package com.chesstutor.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ReviewItemEntity::class,
        LinkedProfileEntity::class,
        LearningProfileEntity::class,
        ModuleProgressEntity::class,
        GameRecordEntity::class,
        PuzzleAttemptEntity::class,
        XpEventEntity::class,
        PuzzleProfileEntity::class,
        LevelProgressEntity::class,
        ReportIssueEntity::class,
        BotProfileEntity::class,
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reviewItemDao(): ReviewItemDao
    abstract fun linkedProfileDao(): LinkedProfileDao
    abstract fun learningDao(): LearningDao
    abstract fun gameRecordDao(): GameRecordDao
    abstract fun puzzleDao(): PuzzleDao
    abstract fun botProfileDao(): BotProfileDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS linked_profile (" +
                        "id INTEGER NOT NULL, platform TEXT NOT NULL, username TEXT NOT NULL, " +
                        "rapidRating INTEGER, blitzRating INTEGER, bulletRating INTEGER, " +
                        "selectedTimeControl TEXT NOT NULL, lastUpdated INTEGER NOT NULL, PRIMARY KEY(id))"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS learning_profile (" +
                        "id INTEGER NOT NULL, estimatedRating INTEGER NOT NULL, " +
                        "assessmentState TEXT NOT NULL, assessmentPositionIndex INTEGER NOT NULL, " +
                        "assessmentCorrect INTEGER NOT NULL, assessmentTotal INTEGER NOT NULL, " +
                        "learningGoal TEXT NOT NULL, totalTacticalAttempts INTEGER NOT NULL, " +
                        "totalTacticalCorrect INTEGER NOT NULL, soundEnabled INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL, PRIMARY KEY(id))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS module_progress (" +
                        "moduleId TEXT NOT NULL, practiced INTEGER NOT NULL, mastered INTEGER NOT NULL, " +
                        "attempts INTEGER NOT NULL, correctAttempts INTEGER NOT NULL, " +
                        "lastPracticedAt INTEGER, PRIMARY KEY(moduleId))"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE learning_profile ADD COLUMN assessmentCompletedAt INTEGER")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS game_records (" +
                        "id TEXT NOT NULL, dateMillis INTEGER NOT NULL, botName TEXT NOT NULL, " +
                        "botRating INTEGER NOT NULL, result TEXT NOT NULL, pgn TEXT NOT NULL, " +
                        "moveCount INTEGER NOT NULL, userColor TEXT NOT NULL, finalFen TEXT NOT NULL, " +
                        "PRIMARY KEY(id))"
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE game_records ADD COLUMN uciMoves TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE game_records ADD COLUMN source TEXT NOT NULL DEFAULT 'ARENA'")
                db.execSQL("ALTER TABLE game_records ADD COLUMN termination TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE game_records ADD COLUMN timeClass TEXT NOT NULL DEFAULT 'rapid'")
                db.execSQL("ALTER TABLE game_records ADD COLUMN incompleteParse INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE game_records SET source = 'CHESS_COM' WHERE id LIKE 'chesscom_%'")
                db.execSQL("UPDATE game_records SET source = 'LICHESS' WHERE id LIKE 'lichess_%'")
            }
        }

        /** Puzzles move into Room (plan Phase 2B): attempts, XP, profile, level path, reports. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS puzzle_attempt (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, puzzleId TEXT NOT NULL, " +
                        "puzzleRating INTEGER NOT NULL, mode TEXT NOT NULL, levelNumber INTEGER, " +
                        "solved INTEGER NOT NULL, clean INTEGER NOT NULL, viaAlternative INTEGER NOT NULL, " +
                        "revealed INTEGER NOT NULL, mistakes INTEGER NOT NULL, hintsUsed INTEGER NOT NULL, " +
                        "elapsedMs INTEGER NOT NULL, ratingBefore INTEGER NOT NULL, ratingAfter INTEGER NOT NULL, " +
                        "xpEarned INTEGER NOT NULL, finishedAt INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_puzzle_attempt_puzzleId ON puzzle_attempt (puzzleId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_puzzle_attempt_finishedAt ON puzzle_attempt (finishedAt)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS xp_event (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, amount INTEGER NOT NULL, " +
                        "reason TEXT NOT NULL, attemptId INTEGER, createdAt INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_xp_event_createdAt ON xp_event (createdAt)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS puzzle_profile (" +
                        "id INTEGER NOT NULL, rating INTEGER NOT NULL, attempts INTEGER NOT NULL, " +
                        "solved INTEGER NOT NULL, cleanSolves INTEGER NOT NULL, streak INTEGER NOT NULL, " +
                        "bestStreak INTEGER NOT NULL, legacySeenIds TEXT NOT NULL, updatedAt INTEGER NOT NULL, " +
                        "PRIMARY KEY(id))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS puzzle_level_progress (" +
                        "levelNumber INTEGER NOT NULL, bestScore INTEGER NOT NULL, passed INTEGER NOT NULL, " +
                        "plays INTEGER NOT NULL, lastPlayedAt INTEGER NOT NULL, PRIMARY KEY(levelNumber))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS report_issue (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, puzzleId TEXT NOT NULL, " +
                        "fen TEXT NOT NULL, moveUci TEXT NOT NULL, note TEXT NOT NULL, createdAt INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_report_issue_puzzleId ON report_issue (puzzleId)")
            }
        }

        /** Phase 3D: the player's own bots ("My bots"). */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS bot_profile (" +
                        "id TEXT NOT NULL, name TEXT NOT NULL, rating INTEGER NOT NULL, style TEXT NOT NULL, " +
                        "aggression REAL NOT NULL, trades REAL NOT NULL, mistakeLevel REAL NOT NULL, " +
                        "thinkSpeed TEXT NOT NULL, resign TEXT NOT NULL, whiteOpenings TEXT NOT NULL, " +
                        "blackOpenings TEXT NOT NULL, face TEXT NOT NULL, timeControl TEXT NOT NULL, " +
                        "takebacks INTEGER NOT NULL, hints INTEGER NOT NULL, evalBar INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))"
                )
            }
        }

        val ALL_MIGRATIONS = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
        )

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chess_tutor_reviews.db"
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
