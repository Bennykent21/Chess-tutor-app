package com.chesstutor.app.domain

enum class SessionStepKind(val badge: String) {
    EXPLAIN("1 · CONCEPT"),
    GUIDED_TRY("2 · GUIDED TRY"),
    PRACTICE("3 · PRACTICE"),
    SUMMARY("COMPLETE")
}

data class SessionStep(
    val id: String,
    val kind: SessionStepKind,
    val title: String,
    val prompt: String,
    val fen: String,
    val solutionUci: String = "",
    val acceptedMovesUci: List<String> = emptyList(),
    val explanationBullets: List<String> = emptyList(),
    val whyItWorks: String = "",
    val conceptHint: String = "",
    val showDemoArrowOnExplain: Boolean = false
) {
    val allSolutionsUci: List<String>
        get() = (listOf(solutionUci) + acceptedMovesUci)
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .distinct()
}

data class LessonTrainSession(
    val topicId: String,
    val topicTitle: String,
    val category: LearnCategory,
    val steps: List<SessionStep>,
    val currentStepIndex: Int = 0,
    val interactiveAttempts: Int = 0,
    val interactiveFirstTryCorrect: Int = 0,
    val currentStepFailedAttempts: Int = 0,
    val currentStepHintUsed: Boolean = false
) {
    val currentStep: SessionStep
        get() = steps[currentStepIndex.coerceIn(0, steps.lastIndex)]

    val totalSteps: Int
        get() = steps.size

    val isSummary: Boolean
        get() = currentStep.kind == SessionStepKind.SUMMARY

    val progressFraction: Float
        get() = if (steps.isEmpty()) 0f else ((currentStepIndex + 1).toFloat() / steps.size.toFloat()).coerceIn(0f, 1f)

    val accuracyPercent: Int
        get() {
            val interactiveCount = steps.count { it.kind == SessionStepKind.GUIDED_TRY || it.kind == SessionStepKind.PRACTICE }
            if (interactiveCount <= 0) return 100
            return ((interactiveFirstTryCorrect * 100) / interactiveCount).coerceIn(0, 100)
        }
}

object LessonSessionFactory {

    fun buildSessionForTopic(topic: LearnTopic): LessonTrainSession {
        val steps = mutableListOf<SessionStep>()

        // Step 1: Explain (Concept + Principles + Annotated Diagram)
        val demoPos = runCatching { ChessPosition(topic.demoFen) }.getOrNull()
        val demoMoveSan = demoPos?.toSan(topic.recommendedMoveUci) ?: topic.recommendedMoveUci
        steps += SessionStep(
            id = "${topic.id}_explain",
            kind = SessionStepKind.EXPLAIN,
            title = topic.title,
            prompt = "${topic.summary} On the board, notice how $demoMoveSan applies this principle.",
            fen = topic.demoFen,
            solutionUci = topic.recommendedMoveUci,
            explanationBullets = topic.keyPrinciples,
            whyItWorks = topic.keyPrinciples.firstOrNull() ?: topic.subtitle,
            conceptHint = topic.subtitle,
            showDemoArrowOnExplain = true
        )

        // Step 2: Guided Try (User plays the move with NO answer arrow drawn initially)
        steps += SessionStep(
            id = "${topic.id}_guided_try",
            kind = SessionStepKind.GUIDED_TRY,
            title = "${topic.title} · Guided Try",
            prompt = "Your turn: play the move that demonstrates '${topic.title}'. (${topic.subtitle})",
            fen = topic.demoFen,
            solutionUci = topic.recommendedMoveUci,
            explanationBullets = topic.keyPrinciples,
            whyItWorks = "Well played! $demoMoveSan demonstrates ${topic.title}: ${topic.keyPrinciples.firstOrNull() ?: topic.subtitle}",
            conceptHint = topic.keyPrinciples.firstOrNull() ?: topic.subtitle,
            showDemoArrowOnExplain = false
        )

        // Steps 3..5: Graded Practice positions matched to this lesson's category & theme
        val practiceDrills = selectPracticeDrillsForTopic(topic)
        practiceDrills.forEachIndexed { idx, drill ->
            val drillPos = runCatching { ChessPosition(drill.fen) }.getOrNull()
            val drillSan = drillPos?.toSan(drill.solutionUci) ?: drill.solutionUci
            steps += SessionStep(
                id = "${topic.id}_practice_${drill.id}",
                kind = SessionStepKind.PRACTICE,
                title = "Practice ${idx + 1} of ${practiceDrills.size} · ${drill.title}",
                prompt = "${drill.subtitle}. ${drill.objectivePrompt}",
                fen = drill.fen,
                solutionUci = drill.solutionUci,
                explanationBullets = topic.keyPrinciples,
                whyItWorks = "$drillSan is best: ${drill.objectivePrompt}",
                conceptHint = drill.category,
                showDemoArrowOnExplain = false
            )
        }

        // Step 6: Summary & Recap
        steps += SessionStep(
            id = "${topic.id}_summary",
            kind = SessionStepKind.SUMMARY,
            title = "Lesson Complete · ${topic.title}",
            prompt = "You've completed the explanation, guided try, and practice positions for ${topic.title}.",
            fen = topic.demoFen,
            solutionUci = topic.recommendedMoveUci,
            explanationBullets = topic.keyPrinciples,
            whyItWorks = topic.summary,
            conceptHint = topic.subtitle,
            showDemoArrowOnExplain = true
        )

        return LessonTrainSession(
            topicId = topic.id,
            topicTitle = topic.title,
            category = topic.category,
            steps = steps
        )
    }

    private fun selectPracticeDrillsForTopic(topic: LearnTopic): List<TacticalDrill> {
        val allDrills = TrainDrillsRepository.drills
        val siblingTopics = LearnCurriculumRepository.topics
            .filter { it.category == topic.category && it.id != topic.id }

        val categoryMatchedDrills = allDrills.filter { drill ->
            when (topic.category) {
                LearnCategory.OPENING -> drill.category.contains("OPENING", ignoreCase = true) ||
                    drill.category.contains("DEFENSE", ignoreCase = true) ||
                    drill.category.contains("PIN", ignoreCase = true)
                LearnCategory.TACTICS -> drill.category.contains("FORK", ignoreCase = true) ||
                    drill.category.contains("PIN", ignoreCase = true) ||
                    drill.category.contains("SKEWER", ignoreCase = true) ||
                    drill.category.contains("DISCOVERED", ignoreCase = true) ||
                    drill.category.contains("MATE", ignoreCase = true) ||
                    drill.category.contains("TACTIC", ignoreCase = true)
                LearnCategory.MIDDLEGAME -> drill.category.contains("DEFLECTION", ignoreCase = true) ||
                    drill.category.contains("OVERWORKED", ignoreCase = true) ||
                    drill.category.contains("INTERPOSITION", ignoreCase = true) ||
                    drill.category.contains("PIN", ignoreCase = true)
                LearnCategory.ENDGAME -> drill.category.contains("ENDGAME", ignoreCase = true) ||
                    drill.category.contains("PROMOTION", ignoreCase = true)
                LearnCategory.BLUNDER_PATTERNS -> drill.category.contains("MATE", ignoreCase = true) ||
                    drill.category.contains("DEFENSE", ignoreCase = true) ||
                    drill.category.contains("HANGING", ignoreCase = true) ||
                    drill.category.contains("FORK", ignoreCase = true)
            } && drill.fen != topic.demoFen
        }

        // Convert sibling topics in the same category into practice drills if we need more variety
        val siblingDrills = siblingTopics.map { sib ->
            TacticalDrill(
                id = "topic_drill_${sib.id}",
                title = sib.title,
                category = sib.category.name.replace('_', ' '),
                fen = sib.demoFen,
                solutionUci = sib.recommendedMoveUci,
                prompt = sib.keyPrinciples.firstOrNull() ?: sib.summary,
                subtitle = sib.subtitle
            )
        }

        return (categoryMatchedDrills + siblingDrills)
            .distinctBy { it.fen }
            .take(3)
    }

    /**
     * Generates specific, educational feedback explaining why a wrong move [playedUci]
     * fails in [fen] compared to [solutionUci] (Review 2 §4.3 & §4.4, Review 3 §2.7).
     */
    fun buildSpecificWrongMoveFeedback(
        fen: String,
        playedUci: String,
        solutionUci: String,
        conceptHint: String
    ): String {
        val beforePos = runCatching { ChessPosition(fen) }.getOrNull() ?: return "Not quite. Look for a stronger move and try again."
        val playedSan = beforePos.toSan(playedUci) ?: playedUci
        val solutionMove = beforePos.legalMoves.firstOrNull { it.uci.equals(solutionUci, ignoreCase = true) }
        val playedMove = beforePos.legalMoves.firstOrNull { it.uci.equals(playedUci, ignoreCase = true) }

        val afterPos = ChessPosition(fen).apply { play(playedUci) }
        val solutionPos = ChessPosition(fen).apply { play(solutionUci) }

        val solutionGivesCheck = solutionPos.isCheck
        val solutionIsMate = solutionPos.isCheckmate
        val playedGivesCheck = afterPos.isCheck
        val solutionTargetSq = if (solutionUci.length >= 4) solutionUci.substring(2, 4) else ""
        val solutionCaptures = solutionTargetSq.isNotEmpty() && beforePos.pieceAt(solutionTargetSq) != null
        val playedTargetSq = if (playedUci.length >= 4) playedUci.substring(2, 4) else ""
        val playedCaptures = playedTargetSq.isNotEmpty() && beforePos.pieceAt(playedTargetSq) != null

        return when {
            solutionIsMate && !afterPos.isCheckmate ->
                "$playedSan does not finish the game. Look for an immediate checkmate in one move."
            solutionGivesCheck && !playedGivesCheck ->
                "$playedSan is too slow — the opponent gets time to consolidate. Look for a forcing check that seizes the initiative."
            solutionCaptures && !playedCaptures ->
                "$playedSan misses a direct tactical capture or decisive material gain. Check which enemy piece or key square is undefended."
            playedMove != null && solutionMove != null && playedUci.take(2) == solutionUci.take(2) ->
                "Right piece, wrong destination! Moving from ${playedUci.take(2)} is correct, but look for a stronger target square than $playedTargetSq."
            conceptHint.isNotBlank() ->
                "$playedSan doesn't solve the problem here. Focus on the theme ($conceptHint) and try again."
            else ->
                "$playedSan lets the opponent off the hook. Look for the most forcing move (checks, captures, and double attacks)."
        }
    }
}
