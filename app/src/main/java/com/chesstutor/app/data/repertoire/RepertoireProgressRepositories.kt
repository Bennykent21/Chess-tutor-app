package com.chesstutor.app.data.repertoire

import com.chesstutor.app.data.local.AppDatabase
import com.chesstutor.app.data.local.OpeningCardEntity
import com.chesstutor.app.data.local.OpeningLevelEntity
import com.chesstutor.app.data.local.OpeningReviewEntity
import com.chesstutor.app.domain.repertoire.CardReview
import com.chesstutor.app.domain.repertoire.CardState
import com.chesstutor.app.domain.repertoire.LevelProgress
import com.chesstutor.app.domain.repertoire.RepertoireLevel
import com.chesstutor.app.domain.repertoire.RepertoireProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

/** Opening trainer progress in Room (`opening_card`, `opening_review`, `opening_level`). */
class RoomRepertoireProgressRepository(private val db: AppDatabase) : RepertoireProgressRepository {
    private val dao get() = db.repertoireDao()

    override fun observeCards(): Flow<Map<String, CardState>> =
        dao.observeCards().map { rows -> rows.associate { it.cardId to it.toDomain() } }

    override fun observeLevels(): Flow<Map<String, LevelProgress>> =
        dao.observeLevels().map { rows -> rows.associate { it.bookId to it.toDomain() } }

    override suspend fun reviews(bookId: String): List<CardReview> = dao.reviews(bookId).map { it.toDomain() }

    override suspend fun save(cards: List<CardState>, reviews: List<CardReview>) =
        dao.save(cards.map { it.toEntity() }, reviews.map { it.toEntity() })

    override suspend fun saveLevel(progress: LevelProgress) = dao.upsertLevel(progress.toEntity())
}

/** Test double. */
class InMemoryRepertoireProgressRepository : RepertoireProgressRepository {
    private val cards = MutableStateFlow<Map<String, CardState>>(emptyMap())
    private val levels = MutableStateFlow<Map<String, LevelProgress>>(emptyMap())
    private val log = ArrayList<CardReview>()

    override fun observeCards(): Flow<Map<String, CardState>> = cards

    override fun observeLevels(): Flow<Map<String, LevelProgress>> = levels

    override suspend fun reviews(bookId: String): List<CardReview> = log.filter { it.bookId == bookId }.sortedBy { it.at }

    override suspend fun save(cards: List<CardState>, reviews: List<CardReview>) {
        this.cards.value = this.cards.value + cards.associateBy { it.id }
        log += reviews
    }

    override suspend fun saveLevel(progress: LevelProgress) {
        levels.value = levels.value + (progress.bookId to progress)
    }
}

private fun level(ordinal: Int): RepertoireLevel = RepertoireLevel.entries.getOrElse(ordinal) { RepertoireLevel.DEEP }

private fun OpeningCardEntity.toDomain() = CardState(
    bookId = bookId, key = positionKey, level = level(level), stage = stage, easeFactor = easeFactor,
    intervalDays = intervalDays, dueAt = Instant.ofEpochMilli(dueAt), attempts = attempts,
    introducedAt = Instant.ofEpochMilli(introducedAt), lastReviewedAt = lastReviewedAt?.let(Instant::ofEpochMilli),
    lapses = lapses, streak = streak,
)

private fun CardState.toEntity() = OpeningCardEntity(
    cardId = id, bookId = bookId, positionKey = key, level = level.ordinal, stage = stage, easeFactor = easeFactor,
    intervalDays = intervalDays, dueAt = dueAt.toEpochMilli(), attempts = attempts, introducedAt = introducedAt.toEpochMilli(),
    lastReviewedAt = lastReviewedAt?.toEpochMilli(), lapses = lapses, streak = streak,
)

private fun OpeningReviewEntity.toDomain() = CardReview(cardId, bookId, level(level), Instant.ofEpochMilli(at), firstTryRight, hinted)

private fun CardReview.toEntity() = OpeningReviewEntity(
    cardId = cardId, bookId = bookId, level = level.ordinal, at = at.toEpochMilli(), firstTryRight = firstTryRight, hinted = hinted,
)

private fun OpeningLevelEntity.toDomain() = LevelProgress(
    bookId = bookId,
    unlocked = level(unlocked),
    masteredAt = listOfNotNull(
        coreMasteredAt?.let { RepertoireLevel.CORE to Instant.ofEpochMilli(it) },
        standardMasteredAt?.let { RepertoireLevel.STANDARD to Instant.ofEpochMilli(it) },
        deepMasteredAt?.let { RepertoireLevel.DEEP to Instant.ofEpochMilli(it) },
    ).toMap(),
)

private fun LevelProgress.toEntity() = OpeningLevelEntity(
    bookId = bookId,
    unlocked = unlocked.ordinal,
    coreMasteredAt = masteredAt[RepertoireLevel.CORE]?.toEpochMilli(),
    standardMasteredAt = masteredAt[RepertoireLevel.STANDARD]?.toEpochMilli(),
    deepMasteredAt = masteredAt[RepertoireLevel.DEEP]?.toEpochMilli(),
)
