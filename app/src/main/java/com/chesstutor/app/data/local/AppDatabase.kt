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
        ModuleProgressEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reviewItemDao(): ReviewItemDao
    abstract fun linkedProfileDao(): LinkedProfileDao
    abstract fun learningDao(): LearningDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS linked_profile (" +
                        "id INTEGER NOT NULL, platform TEXT NOT NULL, username TEXT NOT NULL, " +
                        "rapidRating INTEGER, blitzRating INTEGER, bulletRating INTEGER, " +
                        "selectedTimeControl TEXT NOT NULL, lastUpdated INTEGER NOT NULL, PRIMARY KEY(id))"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
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

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chess_tutor_reviews.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
