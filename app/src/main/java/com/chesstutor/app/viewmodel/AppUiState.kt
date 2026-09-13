package com.chesstutor.app.viewmodel

import com.chesstutor.app.domain.ChessPosition
import com.chesstutor.app.domain.MoveAssessment
import com.chesstutor.app.domain.ReviewItem

data class AppUiState(
    val loading: Boolean = false,
    val tab: Int = 0, // 0: Coach, 1: Curriculum, 2: Arena, 3: Review
    val fen: String = ChessPosition.STARTING_FEN,
    val message: String = "",
    val hintLevel: Int = 0, // 0 = none, 1 = concept, 2 = piece, 3 = target, 4 = direct move
    val hintText: String = "",
    val busy: Boolean = false,
    val reviewSolved: Boolean = false,
    val reviews: List<ReviewItem> = emptyList(),
    val activeReviewIndex: Int = 0,
    val activeReviewItem: ReviewItem? = null,
    val storageWarning: String? = null,
    val assessment: MoveAssessment? = null,
    val selectedSquare: String? = null,
    val legalTargets: Set<String> = emptySet(),
    val recommendedArrow: Pair<String, String>? = null,
    val lastMove: Pair<String, String>? = null,
    val mistakeDetected: Boolean = false,
    val mistakeFen: String? = null,
    val canRetryMistake: Boolean = false,
    val arenaDifficulty: String = "Casual", // Beginner, Casual, Intermediate, Advanced
    val arenaPlayerSide: Char = 'w',
    val arenaStatusText: String = "",
    val evaluationCp: Int? = null,
    val mateIn: Int? = null,
    val curriculumLessonId: String? = null,
    val engineDiagnostics: com.chesstutor.app.engine.EngineDiagnostics? = null,
    val isRunningDiagnostics: Boolean = false
)
