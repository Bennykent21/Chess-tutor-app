package com.chesstutor.app.data.repository

import com.chesstutor.app.data.local.ReviewItemDao
import com.chesstutor.app.data.local.toEntity
import com.chesstutor.app.domain.ReviewItem

interface ReviewRepository {
    suspend fun loadAll(): List<ReviewItem>
    suspend fun getById(id: String): ReviewItem?
    suspend fun upsert(item: ReviewItem)
    suspend fun delete(id: String)
}

class RoomReviewRepository(private val dao: ReviewItemDao) : ReviewRepository {
    override suspend fun loadAll(): List<ReviewItem> = dao.getAll().map { it.toDomain() }
    override suspend fun getById(id: String): ReviewItem? = dao.getById(id)?.toDomain()
    override suspend fun upsert(item: ReviewItem) = dao.upsert(item.toEntity())
    override suspend fun delete(id: String) = dao.deleteById(id)
}

/**
 * Test double — no Room, no instrumentation required.
 */
class InMemoryReviewRepository : ReviewRepository {
    private val items = mutableMapOf<String, ReviewItem>()

    override suspend fun loadAll(): List<ReviewItem> = items.values.toList()
    override suspend fun getById(id: String): ReviewItem? = items[id]
    override suspend fun upsert(item: ReviewItem) {
        items[item.id] = item
    }
    override suspend fun delete(id: String) {
        items.remove(id)
    }

    fun clear() {
        items.clear()
    }
}
