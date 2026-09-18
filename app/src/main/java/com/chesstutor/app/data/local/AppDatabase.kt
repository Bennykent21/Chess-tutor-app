package com.chesstutor.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ReviewItemEntity::class, LinkedProfileEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reviewItemDao(): ReviewItemDao
    abstract fun linkedProfileDao(): LinkedProfileDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS linked_profile (id INTEGER NOT NULL, platform TEXT NOT NULL, username TEXT NOT NULL, rapidRating INTEGER, blitzRating INTEGER, bulletRating INTEGER, selectedTimeControl TEXT NOT NULL, lastUpdated INTEGER NOT NULL, PRIMARY KEY(id))")
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
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
