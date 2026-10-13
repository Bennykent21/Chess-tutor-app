package com.chesstutor.app.domain

import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.navigation.NavCommand
import com.chesstutor.app.navigation.OpeningMode
import com.chesstutor.app.navigation.PlayRequest
import com.chesstutor.app.navigation.Side
import com.chesstutor.app.navigation.TrainRequest
import com.example.chess.core.PieceType
import com.example.chess.core.Position
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class GamePhase(val label: String) {
    OPENING("Opening"),
    MIDDLEGAME("Middlegame"),
    ENDGAME("Endgame")
}

data class ColorBreakdown(
    val games: Int = 0,
    val wins: Int = 0,
    val draws: Int = 0,
    val losses: Int = 0,
    val winPct: Int = 0,
    val scorePct: Int = 0
)

data class OpeningVariationStat(
    val lineId: String?,
    val eco: String,
    val variationName: String,
    val fullName: String,
    val color: String,
    val games: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val winPct: Int,
    val scorePct: Int,
    val gameIds: List<String>
)

data class OpeningFamilyStat(
    val family: String,
    val eco: String,
    val color: String, // "White" or "Black"
    val games: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val winPct: Int,
    val scorePct: Int,
    val wilsonLowerBoundScore: Double,
    val hasSufficientSample: Boolean,
    val needsWork: Boolean,
    val representativeLineId: String?,
    val relatedTopicId: String?,
    val variations: List<OpeningVariationStat>,
    val gameIds: List<String>,
    val isPlayedByUser: Boolean = true
) {
    val userColor: String get() = color.lowercase()
    val scorePercent: Int get() = scorePct
    val displayLabel: String
        get() = if (isPlayedByUser) {
            "$family (as $color)"
        } else {
            "As $color vs $family"
        }
}

data class ReviewInsight(
    val id: String,
    val title: String,
    val detail: String,
    val actionLabel: String,
    val command: NavCommand
) {
    val sentence: String get() = "$title — $detail"
}

data class ReviewDashboardStats(
    val totalGames: Int = 0,
    val wins: Int = 0,
    val draws: Int = 0,
    val losses: Int = 0,
    val winPct: Int = 0,
    val scorePct: Int = 0,
    val whiteStats: ColorBreakdown = ColorBreakdown(),
    val blackStats: ColorBreakdown = ColorBreakdown(),
    val openings: List<OpeningFamilyStat> = emptyList(),
    val openingsPlayed: List<OpeningFamilyStat> = emptyList(),
    val openingsFaced: List<OpeningFamilyStat> = emptyList(),
    val insights: List<ReviewInsight> = emptyList(),
    val accountGameCount: Int = 0,
    val arenaGameCount: Int = 0
) {
    val scorePercent: Int get() = scorePct
}

object ReviewStatsCalculator {

    const val MIN_OPENING_SAMPLE = 8

    /**
     * Score % = (wins + 0.5 * draws) / games, in [0, 100].
     */
    fun scorePercent(wins: Int, draws: Int, games: Int): Double {
        if (games <= 0) return 0.0
        return ((wins + 0.5 * draws) / games.toDouble()) * 100.0
    }

    /**
     * Win % = wins / games, in [0, 100].
     */
    fun winPercent(wins: Int, games: Int): Double {
        if (games <= 0) return 0.0
        return (wins.toDouble() / games.toDouble()) * 100.0
    }

    /**
     * Wilson score interval lower bound for a score fraction p in [0, 1] over n games.
     * Used to rank openings conservatively so 2-3 game flukes do not dominate.
     */
    fun wilsonLowerBound(wins: Int, draws: Int, games: Int, z: Double = 1.96): Double {
        if (games <= 0) return 0.0
        val n = games.toDouble()
        val p = ((wins + 0.5 * draws) / n).coerceIn(0.0, 1.0)
        val z2 = z * z
        val denominator = 1.0 + z2 / n
        val center = p + z2 / (2.0 * n)
        val spread = z * sqrt((p * (1.0 - p) + z2 / (4.0 * n)) / n)
        return ((center - spread) / denominator).coerceIn(0.0, 1.0)
    }

    /**
     * Determines the phase of a position at [ply] (1-indexed).
     * Opening: first 10 full moves (ply <= 20) while still in/near opening structure.
     * Endgame: combined non-pawn material (N/B=3, R=5, Q=9) <= 26.
     * Middlegame: all other positions.
     */
    fun detectPhase(fen: String, ply: Int, outOfTheoryPly: Int? = null): GamePhase {
        val pos = Position.tryFromFen(fen).getOrNull()
        if (pos != null) {
            var nonPawnPoints = 0
            for (sq in pos.squares) {
                val piece = sq ?: continue
                nonPawnPoints += when (piece.type) {
                    PieceType.KNIGHT, PieceType.BISHOP -> 3
                    PieceType.ROOK -> 5
                    PieceType.QUEEN -> 9
                    else -> 0
                }
            }
            if (nonPawnPoints <= 26) {
                return GamePhase.ENDGAME
            }
        }
        val theoryCutoff = outOfTheoryPly ?: 20
        return if (ply <= 20 && ply <= theoryCutoff) {
            GamePhase.OPENING
        } else {
            GamePhase.MIDDLEGAME
        }
    }

    fun isUserWin(game: GameRecord): Boolean {
        val white = game.userColor.equals("white", ignoreCase = true)
        return (white && game.result == "1-0") || (!white && game.result == "0-1")
    }

    fun isUserLoss(game: GameRecord): Boolean {
        val white = game.userColor.equals("white", ignoreCase = true)
        return (white && game.result == "0-1") || (!white && game.result == "1-0")
    }

    fun isDraw(game: GameRecord): Boolean {
        return game.result == "1/2-1/2"
    }

    private fun computeBreakdown(games: List<GameRecord>): ColorBreakdown {
        if (games.isEmpty()) return ColorBreakdown()
        val wins = games.count { isUserWin(it) }
        val losses = games.count { isUserLoss(it) }
        val draws = games.count { isDraw(it) }
        val total = games.size
        return ColorBreakdown(
            games = total,
            wins = wins,
            draws = draws,
            losses = losses,
            winPct = winPercent(wins, total).roundToInt(),
            scorePct = scorePercent(wins, draws, total).roundToInt()
        )
    }

    /**
     * Resolves the opening line or ECO/name for a [GameRecord] by checking
     * PGN headers first, then matching position EPD along the game's UCI moves.
     */
    fun resolveOpeningForGame(game: GameRecord): OpeningLine {
        val moves = game.uciMoves.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val matched = OpeningBook.matchAlongMoves(moves).opening
        if (matched != null) return matched

        // Check PGN headers if imported from Lichess / Chess.com
        val pgnOpening = Regex("""\[Opening\s+"([^"]+)"\]""")
            .find(game.pgn)?.groupValues?.getOrNull(1)
        val pgnEco = Regex("""\[ECO\s+"([^"]+)"\]""")
            .find(game.pgn)?.groupValues?.getOrNull(1)

        if (!pgnOpening.isNullOrBlank()) {
            val family = pgnOpening.substringBefore(":").trim()
            val bookByFamily = OpeningBook.lines.firstOrNull {
                it.family.equals(family, ignoreCase = true)
            }
            return OpeningLine(
                id = bookByFamily?.id ?: "imported_${family.lowercase().replace(Regex("[^a-z0-9]+"), "_")}",
                eco = pgnEco ?: bookByFamily?.eco ?: "A00",
                name = pgnOpening,
                uciMoves = moves.take(8),
                sanMoves = emptyList(),
                epd = "",
                relatedTopicId = bookByFamily?.relatedTopicId ?: "opening_principles"
            )
        }

        // Fallback by first move
        val firstMove = moves.firstOrNull()
        return when (firstMove) {
            "e2e4" -> OpeningLine(
                id = "italian_giuoco_piano",
                eco = "C20",
                name = "King's Pawn Game: Open Game",
                uciMoves = listOf("e2e4"),
                sanMoves = listOf("e4"),
                epd = "",
                relatedTopicId = "opening_italian_game"
            )
            "d2d4" -> OpeningLine(
                id = "queens_gambit_declined",
                eco = "D00",
                name = "Queen's Pawn Game: Closed Game",
                uciMoves = listOf("d2d4"),
                sanMoves = listOf("d4"),
                epd = "",
                relatedTopicId = "opening_queens_gambit"
            )
            else -> OpeningLine(
                id = "italian_giuoco_piano",
                eco = "A00",
                name = "Unclassified Opening: General Play",
                uciMoves = emptyList(),
                sanMoves = emptyList(),
                epd = "",
                relatedTopicId = "opening_principles"
            )
        }
    }

    private fun isBlackChoiceOpening(family: String, line: OpeningLine): Boolean {
        val lower = family.lowercase()
        return lower.contains("defense") ||
            lower.contains("defence") ||
            lower.contains("sicilian") ||
            lower.contains("french") ||
            lower.contains("caro-kann") ||
            lower.contains("king's indian") ||
            lower.contains("slav") ||
            lower.contains("scandinavian") ||
            lower.contains("pirc") ||
            lower.contains("alekhine") ||
            lower.contains("nimzo") ||
            lower.contains("dutch") ||
            line.recommendedSide == 'b'
    }

    fun compute(
        games: List<GameRecord>,
        reviews: List<ReviewItem>,
        weakestTopicId: String? = null,
        accountOnly: Boolean = true
    ): ReviewDashboardStats {
        val now = java.time.Instant.now()
        val dueCount = reviews.count { ReviewScheduler.isDue(it, now) }
        val accountGames = games.filter { it.isAccountGame }
        val arenaGames = games.filter { !it.isAccountGame }
        val targetGames = if (accountOnly) accountGames else arenaGames
        return calculate(
            games = targetGames,
            dueReviewCount = dueCount,
            weakestTopicId = weakestTopicId,
            accountGameCount = accountGames.size,
            arenaGameCount = arenaGames.size
        )
    }

    fun calculate(
        games: List<GameRecord>,
        dueReviewCount: Int = 0,
        weakestTopicId: String? = null,
        accountGameCount: Int = games.count { it.isAccountGame },
        arenaGameCount: Int = games.count { !it.isAccountGame }
    ): ReviewDashboardStats {
        val finishedGames = games.filter { it.result in setOf("1-0", "0-1", "1/2-1/2") }
        if (finishedGames.isEmpty()) {
            val insights = buildInsights(
                finishedGames = emptyList(),
                openings = emptyList(),
                dueReviewCount = dueReviewCount,
                weakestTopicId = weakestTopicId
            )
            return ReviewDashboardStats(
                insights = insights,
                accountGameCount = accountGameCount,
                arenaGameCount = arenaGameCount
            )
        }

        val overall = computeBreakdown(finishedGames)
        val whiteGames = finishedGames.filter { it.userColor.equals("white", ignoreCase = true) }
        val blackGames = finishedGames.filter { it.userColor.equals("black", ignoreCase = true) }
        val whiteStats = computeBreakdown(whiteGames)
        val blackStats = computeBreakdown(blackGames)

        // Group by (Opening family, user color)
        val resolved = finishedGames.map { game ->
            val line = resolveOpeningForGame(game)
            val colorLabel = if (game.userColor.equals("black", ignoreCase = true)) "Black" else "White"
            Triple(game, line, colorLabel)
        }

        val familyGroups = resolved.groupBy { (_, line, color) -> line.family to color }
        val openingStats = familyGroups.map { (key, entries) ->
            val (family, color) = key
            val groupGames = entries.map { it.first }
            val breakdown = computeBreakdown(groupGames)
            val wilson = wilsonLowerBound(breakdown.wins, breakdown.draws, breakdown.games)
            val sufficient = breakdown.games >= MIN_OPENING_SAMPLE
            val needsWork = sufficient && breakdown.scorePct < 45
            val primaryLine = entries.first().second

            val variations = entries.groupBy { it.second.name }.map { (fullName, varEntries) ->
                val varGames = varEntries.map { it.first }
                val varBreakdown = computeBreakdown(varGames)
                val repLine = varEntries.first().second
                OpeningVariationStat(
                    lineId = repLine.id,
                    eco = repLine.eco,
                    variationName = repLine.variation,
                    fullName = fullName,
                    color = color,
                    games = varBreakdown.games,
                    wins = varBreakdown.wins,
                    draws = varBreakdown.draws,
                    losses = varBreakdown.losses,
                    winPct = varBreakdown.winPct,
                    scorePct = varBreakdown.scorePct,
                    gameIds = varGames.map { it.id }
                )
            }.sortedByDescending { it.games }

            val isBlackDefense = isBlackChoiceOpening(family, primaryLine)
            val isUserBlack = color.equals("Black", ignoreCase = true)
            val isPlayedByUser = (isBlackDefense && isUserBlack) || (!isBlackDefense && !isUserBlack)

            OpeningFamilyStat(
                family = family,
                eco = primaryLine.eco,
                color = color,
                games = breakdown.games,
                wins = breakdown.wins,
                draws = breakdown.draws,
                losses = breakdown.losses,
                winPct = breakdown.winPct,
                scorePct = breakdown.scorePct,
                wilsonLowerBoundScore = wilson,
                hasSufficientSample = sufficient,
                needsWork = needsWork,
                representativeLineId = primaryLine.id,
                relatedTopicId = primaryLine.relatedTopicId,
                variations = variations,
                gameIds = groupGames.map { it.id },
                isPlayedByUser = isPlayedByUser
            )
        }.sortedWith(
            compareByDescending<OpeningFamilyStat> { it.games }
                .thenBy { it.wilsonLowerBoundScore }
        )

        val openingsPlayed = openingStats.filter { it.isPlayedByUser }
        val openingsFaced = openingStats.filter { !it.isPlayedByUser }

        val insights = buildInsights(
            finishedGames = finishedGames,
            openings = openingStats,
            dueReviewCount = dueReviewCount,
            weakestTopicId = weakestTopicId
        )

        return ReviewDashboardStats(
            totalGames = overall.games,
            wins = overall.wins,
            draws = overall.draws,
            losses = overall.losses,
            winPct = overall.winPct,
            scorePct = overall.scorePct,
            whiteStats = whiteStats,
            blackStats = blackStats,
            openings = openingStats,
            openingsPlayed = openingsPlayed,
            openingsFaced = openingsFaced,
            insights = insights,
            accountGameCount = accountGameCount,
            arenaGameCount = arenaGameCount
        )
    }

    private fun buildInsights(
        finishedGames: List<GameRecord>,
        openings: List<OpeningFamilyStat>,
        dueReviewCount: Int,
        weakestTopicId: String?
    ): List<ReviewInsight> {
        val list = mutableListOf<ReviewInsight>()

        // 1. Due spaced-repetition items from real blunders
        if (dueReviewCount > 0) {
            list += ReviewInsight(
                id = "insight_due_reviews",
                title = "You have $dueReviewCount blunder position${if (dueReviewCount == 1) "" else "s"} due for review",
                detail = "Drilling your own missed tactics locks in pattern recognition before your next game.",
                actionLabel = "Drill Mistakes",
                command = NavCommand.StartTrain(TrainRequest.ReviewDue)
            )
        }

        // 2. Weakest opening with sufficient sample (n >= MIN_OPENING_SAMPLE)
        val weakestQualifiedOpening = openings
            .filter { it.hasSufficientSample && it.needsWork }
            .minByOrNull { it.wilsonLowerBoundScore }
            ?: openings.filter { it.games >= 3 && it.scorePct < 45 }.minByOrNull { it.scorePct }

        if (weakestQualifiedOpening != null) {
            val side = if (weakestQualifiedOpening.color.equals("Black", ignoreCase = true)) Side.BLACK else Side.WHITE
            val lineId = weakestQualifiedOpening.representativeLineId ?: "sicilian_najdorf"
            list += ReviewInsight(
                id = "insight_weak_opening_${weakestQualifiedOpening.family}_${weakestQualifiedOpening.color}",
                title = "As ${weakestQualifiedOpening.color} in ${weakestQualifiedOpening.family} you score ${weakestQualifiedOpening.scorePct}% over ${weakestQualifiedOpening.games} games",
                detail = "Record: ${weakestQualifiedOpening.wins}W–${weakestQualifiedOpening.draws}D–${weakestQualifiedOpening.losses}L. Practise the book moves against the engine.",
                actionLabel = "Practise ${weakestQualifiedOpening.family}",
                command = NavCommand.StartPlay(
                    PlayRequest.Opening(
                        lineId = lineId,
                        userSide = side,
                        mode = OpeningMode.LEARN_LINE
                    )
                )
            )
        }

        // 3. Color imbalance insight
        val whiteGames = finishedGames.filter { it.userColor.equals("white", ignoreCase = true) }
        val blackGames = finishedGames.filter { it.userColor.equals("black", ignoreCase = true) }
        if (whiteGames.size >= 4 && blackGames.size >= 4) {
            val wScore = scorePercent(
                whiteGames.count { isUserWin(it) },
                whiteGames.count { isDraw(it) },
                whiteGames.size
            ).roundToInt()
            val bScore = scorePercent(
                blackGames.count { isUserWin(it) },
                blackGames.count { isDraw(it) },
                blackGames.size
            ).roundToInt()
            if (bScore + 15 < wScore) {
                list += ReviewInsight(
                    id = "insight_black_repertoire",
                    title = "Your score as Black ($bScore%) trails your score as White ($wScore%)",
                    detail = "Build a reliable counter-attacking setup against 1.e4 with the Sicilian Defense.",
                    actionLabel = "Learn Sicilian Defense",
                    command = NavCommand.OpenLearn("opening_sicilian_defense")
                )
            } else if (wScore + 15 < bScore) {
                list += ReviewInsight(
                    id = "insight_white_repertoire",
                    title = "Your score as White ($wScore%) trails your score as Black ($bScore%)",
                    detail = "Sharpen your central control and rapid development in the Italian Game.",
                    actionLabel = "Learn Italian Game",
                    command = NavCommand.OpenLearn("opening_italian_game")
                )
            }
        }

        // 4. Weakest curriculum topic recommendation
        if (weakestTopicId != null) {
            val topic = LearnCurriculumRepository.topics.firstOrNull { it.id == weakestTopicId }
            if (topic != null) {
                list += ReviewInsight(
                    id = "insight_weak_topic_${topic.id}",
                    title = "Recommended study: ${topic.title}",
                    detail = topic.subtitle,
                    actionLabel = "Practise ${topic.title}",
                    command = NavCommand.StartTrain(TrainRequest.Lesson(topic.id))
                )
            }
        }

        return list.take(5)
    }
}
