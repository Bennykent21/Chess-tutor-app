package com.chesstutor.app.domain.repertoire

import com.chesstutor.app.domain.ReviewItem
import com.chesstutor.app.domain.ReviewScheduler
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/**
 * One card: a position where it is the learner's turn, in one opening (D71). The same
 * position in two openings is two cards, since each may want its own move there.
 * Scheduling is [ReviewScheduler]'s, unchanged: stage -1 is Learning, then 1, 3, 7, 14, 30 days.
 */
data class CardState(
    val bookId: String,
    val key: String,
    val level: RepertoireLevel,
    val stage: Int = -1,
    val easeFactor: Float = 2.5f,
    val intervalDays: Int = 0,
    val dueAt: Instant,
    val attempts: Int = 0,
    val introducedAt: Instant,
    val lastReviewedAt: Instant? = null,
    /** Misses after the first recall: how often this card has slipped once learned. */
    val lapses: Int = 0,
    /** Right first tries without a hint in a row. */
    val streak: Int = 0,
) {
    val id: String get() = RepertoireCards.cardId(bookId, key)

    /** Missed last time, or not yet recalled once. */
    val learning: Boolean get() = stage < 0

    /**
     * A line the learner finds hard to hold (D76): missed at least [RepertoireCards.TRICKY_LAPSES]
     * times once learned, and not yet right [RepertoireCards.TRICKY_CLEAR] times running since.
     * It comes back sooner and is played from the start of its line.
     */
    val tricky: Boolean get() = lapses >= RepertoireCards.TRICKY_LAPSES && streak < RepertoireCards.TRICKY_CLEAR

    fun isDue(now: Instant): Boolean = !dueAt.isAfter(now)
}

/** One scheduled recall, kept for the mastery numbers (D72). */
data class CardReview(
    val cardId: String,
    val bookId: String,
    val level: RepertoireLevel,
    val at: Instant,
    /** Right on the first try without a hint. */
    val firstTryRight: Boolean,
    val hinted: Boolean,
)

/** What a move on a card means. */
enum class CardAnswer {
    /** The repertoire move. */
    RIGHT,

    /** Just as good, but not the move this repertoire plays: no penalty, the card asks again. */
    ALSO,
    WRONG,
}

object RepertoireCards {
    const val TRICKY_LAPSES = 2
    const val TRICKY_CLEAR = 3

    /** A tricky card never waits longer than this. */
    const val TRICKY_MAX_DAYS = 3L

    fun cardId(bookId: String, key: String): String = "$bookId|$key"

    fun judge(node: RepertoireNode, uci: String): CardAnswer = when (uci) {
        node.move -> CardAnswer.RIGHT
        in node.also -> CardAnswer.ALSO
        else -> CardAnswer.WRONG
    }

    /** A card the learner has just met; it is due at once until its first recall sets the schedule. */
    fun introduce(bookId: String, node: RepertoireNode, now: Instant): CardState =
        CardState(bookId = bookId, key = node.key, level = node.level, dueAt = now, introducedAt = now)

    /**
     * The first try at a card decides its schedule, as in the mistakes review: right moves it
     * further out, right with a hint keeps it at a day, wrong (or the answer shown) brings it
     * back now. A miss after the first recall is a lapse; a tricky card's next review is never
     * more than [TRICKY_MAX_DAYS] away.
     */
    fun afterRecall(card: CardState, right: Boolean, hinted: Boolean, now: Instant): Pair<CardState, CardReview> {
        val item = ReviewScheduler.recordAttempt(
            ReviewItem(
                id = card.id, fen = card.key, dueAt = card.dueAt, stage = card.stage, attempts = card.attempts,
                easeFactor = card.easeFactor, intervalDays = card.intervalDays,
            ),
            correct = right, usedHint = hinted, now = now,
        )
        val updated = card.copy(
            stage = item.stage, easeFactor = item.easeFactor, intervalDays = item.intervalDays,
            dueAt = item.dueAt, attempts = item.attempts, lastReviewedAt = now,
            lapses = card.lapses + if (!right && card.attempts > 0) 1 else 0,
            streak = if (right && !hinted) card.streak + 1 else 0,
        )
        val soonest = now.plus(TRICKY_MAX_DAYS, ChronoUnit.DAYS)
        val next = if (updated.tricky && updated.dueAt.isAfter(soonest)) updated.copy(dueAt = soonest) else updated
        return next to CardReview(card.id, card.bookId, card.level, now, firstTryRight = right && !hinted, hinted = hinted)
    }
}

/** How far a level is from mastered; every number the rule (D72) needs. */
data class MasteryProgress(
    val total: Int,
    val introduced: Int,
    /** Cards at stage 1 or more: recalled on the first try on two different days. */
    val settled: Int,
    val learning: Int,
    val recentReviews: Int,
    val recentAccuracy: Double?,
    val recentDays: Int,
) {
    val mastered: Boolean
        get() = total > 0 && introduced == total && settled >= ceil(Mastery.SETTLED_SHARE * total).toInt() &&
            learning == 0 && recentReviews > 0 && (recentAccuracy ?: 0.0) >= Mastery.ACCURACY && recentDays >= Mastery.MIN_DAYS
}

/**
 * D72: a level is mastered when every card in it has been introduced, at least 90% are at
 * stage 1 or more, none is in Learning, and the first try was right in at least 90% of the
 * level's last 50 reviews, spread over 3 or more days. One pass can never master a level:
 * stage 1 needs a second right recall at least a day after the first.
 */
object Mastery {
    const val SETTLED_SHARE = 0.9
    const val ACCURACY = 0.9
    const val WINDOW = 50
    const val MIN_DAYS = 3

    fun of(
        book: RepertoireBook,
        level: RepertoireLevel,
        states: Map<String, CardState>,
        reviews: List<CardReview>,
        zone: ZoneId,
    ): MasteryProgress {
        val ids = book.nodes.values.filter { book.isCard(it) && it.level == level }.map { RepertoireCards.cardId(book.id, it.key) }.toSet()
        val cards = ids.mapNotNull { states[it] }
        val recent = reviews.filter { it.cardId in ids }.sortedBy { it.at }.takeLast(WINDOW)
        return MasteryProgress(
            total = ids.size,
            introduced = cards.size,
            settled = cards.count { it.stage >= 1 },
            learning = cards.count { it.learning },
            recentReviews = recent.size,
            recentAccuracy = if (recent.isEmpty()) null else recent.count { it.firstTryRight }.toDouble() / recent.size,
            recentDays = recent.map { it.at.atZone(zone).toLocalDate() }.distinct().size,
        )
    }
}

/** Which levels of an opening are open; a mastered level stays mastered (D72). */
data class LevelProgress(
    val bookId: String,
    val unlocked: RepertoireLevel = RepertoireLevel.CORE,
    val masteredAt: Map<RepertoireLevel, Instant> = emptyMap(),
) {
    fun isOpen(level: RepertoireLevel): Boolean = level <= unlocked
}

object LevelUnlocks {
    /** Opens the next level once the highest open one is mastered. One level at a time. */
    fun update(
        book: RepertoireBook,
        progress: LevelProgress,
        states: Map<String, CardState>,
        reviews: List<CardReview>,
        now: Instant,
        zone: ZoneId,
    ): LevelProgress {
        val level = progress.unlocked
        if (level in progress.masteredAt || !Mastery.of(book, level, states, reviews, zone).mastered) return progress
        val mastered = progress.masteredAt + (level to now)
        val next = RepertoireLevel.entries.getOrNull(level.ordinal + 1)
        return progress.copy(unlocked = next ?: level, masteredAt = mastered)
    }
}

/** Where the trainer's progress is kept (Room tables `opening_card`, `opening_review`, `opening_level`). */
interface RepertoireProgressRepository {
    fun observeCards(): Flow<Map<String, CardState>>

    fun observeLevels(): Flow<Map<String, LevelProgress>>

    suspend fun reviews(bookId: String): List<CardReview>

    /** Saves the cards and appends the reviews in one go. */
    suspend fun save(cards: List<CardState>, reviews: List<CardReview>)

    suspend fun saveLevel(progress: LevelProgress)
}
