package com.example.chess.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persists user blunders to power the Spaced-Repetition Review Loop.
 */
@Entity(tableName = "mistake_book")
data class MistakeRecord(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val fenBefore: String,
  val playedMoveUci: String,
  val bestMoveUci: String,
  val evalDeltaPawns: Float,
  val pedagogicalExplanation: String,
  val reviewDueTimestampMs: Long,
  val repetitionStage: Int = 0, // 0 = New, 1 = 1-day, 2 = 3-day, 3 = 7-day, 4 = Mastered
  val timesReviewed: Int = 0,
  val timesSolvedSuccessfully: Int = 0
)

/**
 * Tracks lesson completion and user rating estimates.
 */
@Entity(tableName = "user_progress")
data class UserProgress(
  @PrimaryKey val id: String = "default_user",
  val estimatedRating: Int = 1000,
  val tacticsRating: Int = 1100,
  val puzzlesSolved: Int = 0,
  val completedLessonIds: String = "", // Comma-separated
  val gamesPlayed: Int = 0,
  val blundersCorrected: Int = 0,
  val currentDailyStreak: Int = 1
)
