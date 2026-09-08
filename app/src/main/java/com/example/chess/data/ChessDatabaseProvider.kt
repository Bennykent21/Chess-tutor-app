package com.example.chess.data

import android.content.Context
import androidx.room.Room

object ChessDatabaseProvider {
  @Volatile
  private var INSTANCE: ChessDatabase? = null

  fun getDatabase(context: Context): ChessDatabase {
    return INSTANCE ?: synchronized(this) {
      val instance = Room.databaseBuilder(
        context.applicationContext,
        ChessDatabase::class.java,
        "chess_tutor_database"
      ).fallbackToDestructiveMigration().build()
      INSTANCE = instance
      instance
    }
  }
}
