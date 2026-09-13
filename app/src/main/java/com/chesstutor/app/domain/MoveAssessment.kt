package com.chesstutor.app.domain

data class MoveAssessment(
    val evaluationBeforeCp: Int?,
    val evaluationAfterCp: Int?,
    val mateInMovesBefore: Int?,
    val mateInMovesAfter: Int?,
    val verifiedConsequences: List<VerifiedConsequence>,
    val confidence: SearchConfidence,
    val coachingLabel: String,   // generated LAST, from the fields above — never authored directly
)

enum class VerifiedConsequence {
    MISSED_FORCED_MATE,
    WALKED_INTO_FORCED_MATE,
    MATERIAL_LOST_BY_FORCE,
    HANGING_PIECE,
    FORK_OPPORTUNITY_MISSED,
    ALLOWED_ENEMY_FORK
}

data class SearchConfidence(val depth: Int?, val nodes: Long?)
