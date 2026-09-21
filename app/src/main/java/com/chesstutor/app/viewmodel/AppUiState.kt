package com.chesstutor.app.viewmodel

import com.chesstutor.app.domain.ChessPosition
import com.chesstutor.app.domain.MoveAssessment
import com.chesstutor.app.domain.ReviewItem
import com.example.chess.engine.BotStrength

data class PromotionRequest(
    val from: String,
    val to: String,
    val choices: List<Char>
)

data class TacticalIssueSummary(
    val tacticalIssue: String,
    val explanation: String,
    val bestAlternativeMove: Pair<String, String>?
)

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
    val pendingPromotion: PromotionRequest? = null,
    val recommendedArrow: Pair<String, String>? = null,
    val lastMove: Pair<String, String>? = null,
    val mistakeDetected: Boolean = false,
    val mistakeFen: String? = null,
    val canRetryMistake: Boolean = false,
    val arenaDifficulty: String = "Casual", // Beginner, Casual, Intermediate, Advanced, Master, Custom
    val arenaBotName: String = "Wayne",
    val customBotElo: Int = 600,
    val analysis: TacticalIssueSummary? = null,
    val arenaPlayerSide: Char = 'w',
    val arenaStatusText: String = "",
    val evaluationCp: Int? = null,
    val mateIn: Int? = null,
    val curriculumLessonId: String? = null,
    val curriculumTab: Int = 0, // 0: Mistake Patterns, 1: Learn (Openings, Middlegame, Endgame)
    val activeCoachTitle: String = "Forced Mate & Consequence Retry",
    val activeCoachSubtitle: String = "Every mistake is backed by a concrete, checkable fact.",
    val activeCoachCategory: String = "TODAY'S FOCUS",
    val activeCoachRecommendedMove: String? = null,
    val isSettingsVisible: Boolean = false,
    val isSoundEnabled: Boolean = true,
    val practicedModules: Set<String> = setOf("lesson_mate_1"),
    val masteredModules: Set<String> = emptySet(),
    val isArenaOpponentSheetVisible: Boolean = false,
    val isEngineDiagnosticsDialogVisible: Boolean = false,
    val engineDiagnostics: com.chesstutor.app.engine.EngineDiagnostics? = null,
    val isRunningDiagnostics: Boolean = false,
    val linkedProfile: com.chesstutor.app.data.model.LinkedChessProfile? = null,
    val useLinkedRatingForBot: Boolean = false,
    val isLinkingLoading: Boolean = false,
    val linkingError: String? = null,
    val linkingSuccessMessage: String? = null,
    val isAutoOpponentEnabled: Boolean = true,
    val opponentThinking: Boolean = false,
    val currentDrillIndex: Int = 0,
    val moveHistory: List<String> = emptyList(),
    val isDrillSheetVisible: Boolean = false
) {
    val effectiveBotElo: Int
        get() {
            if (arenaDifficulty.equals("Custom", ignoreCase = true)) {
                return customBotElo
            }
            if (useLinkedRatingForBot && linkedProfile?.activeRating != null) {
                return linkedProfile.activeRating!!.coerceIn(250, 3200)
            }
            return BotStrength.presetForKey(arenaDifficulty)?.rating
                ?: BotStrength.presets.first().rating
        }

    val botTuningDescription: String
        get() {
            if (useLinkedRatingForBot && linkedProfile?.activeRating != null) {
                val prof = linkedProfile
                return "Calibrated to your ${prof.platform.displayName} ${prof.selectedTimeControl.displayName} rating (${prof.activeRating})"
            }
            return "$arenaBotName ($effectiveBotElo Elo)"
        }
}
