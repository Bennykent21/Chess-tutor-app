package com.chesstutor.app.data.repository

import com.chesstutor.app.data.local.GameRecordDao
import com.chesstutor.app.data.local.GameRecordEntity
import com.chesstutor.app.data.model.GameRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface GameRepository {
    fun observeGames(): Flow<List<GameRecord>>
    suspend fun getRecentGames(limit: Int = 20): List<GameRecord>
    suspend fun saveGame(game: GameRecord)
    suspend fun deleteGame(id: String)
}

class RoomGameRepository(
    private val dao: GameRecordDao
) : GameRepository {
    override fun observeGames(): Flow<List<GameRecord>> {
        return dao.observeAllGames().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getRecentGames(limit: Int): List<GameRecord> {
        return dao.getRecentGames(limit).map { it.toDomain() }
    }

    override suspend fun saveGame(game: GameRecord) {
        dao.insertGame(GameRecordEntity.fromDomain(game))
    }

    override suspend fun deleteGame(id: String) {
        dao.deleteGame(id)
    }
}

class InMemoryGameRepository : GameRepository {
    private val games = mutableListOf<GameRecord>()
    private val flow = kotlinx.coroutines.flow.MutableStateFlow<List<GameRecord>>(emptyList())

    override fun observeGames(): Flow<List<GameRecord>> = flow

    override suspend fun getRecentGames(limit: Int): List<GameRecord> {
        return games.take(limit)
    }

    override suspend fun saveGame(game: GameRecord) {
        games.removeAll { it.id == game.id }
        games.add(0, game)
        flow.value = games.toList()
    }

    override suspend fun deleteGame(id: String) {
        games.removeAll { it.id == id }
        flow.value = games.toList()
    }
}
