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

/**
 * Persists user opening repertoire sets for White and Black.
 */
@Entity(tableName = "repertoire")
data class RepertoireEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val side: String, // "WHITE" or "BLACK"
  val ecoFamily: String,
  val description: String,
  val totalLines: Int = 0,
  val masteredLines: Int = 0,
  val weakLines: Int = 0,
  val createdAtTimestampMs: Long = System.currentTimeMillis()
)

/**
 * A critical branch, variation move, or annotated position within a Repertoire.
 */
@Entity(tableName = "repertoire_position")
data class RepertoirePositionEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val repertoireId: Long,
  val fen: String,
  val parentFen: String? = null,
  val moveUci: String,
  val moveSan: String,
  val coachNote: String = "",
  val isCritical: Boolean = false,
  val status: String = "LEARNING", // "KNOWN", "LEARNING", "WEAK"
  val reviewCount: Int = 0,
  val lastReviewedAt: Long = 0L
)

