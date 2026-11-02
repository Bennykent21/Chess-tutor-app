package com.chesstutor.app.data.repository

import com.chesstutor.app.data.local.GameRecordDao
import com.chesstutor.app.data.local.GameRecordEntity
import com.chesstutor.app.data.model.GameRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

interface GameRepository {
    fun observeGames(): Flow<List<GameRecord>>
    suspend fun getRecentGames(limit: Int = 20): List<GameRecord>
    suspend fun saveGame(game: GameRecord)
    suspend fun saveGames(games: List<GameRecord>) {
        for (game in games) {
            saveGame(game)
        }
    }
    suspend fun deleteGame(id: String)

    /** Which of [ids] are saved already, so an import can count the games it really added. */
    suspend fun existingIds(ids: Collection<String>): Set<String> {
        val saved = observeGames().first().mapTo(HashSet()) { it.id }
        return ids.filterTo(HashSet()) { it in saved }
    }
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

    override suspend fun saveGames(games: List<GameRecord>) {
        if (games.isEmpty()) return
        dao.insertGames(games.map { GameRecordEntity.fromDomain(it) })
    }

    override suspend fun deleteGame(id: String) {
        dao.deleteGame(id)
    }

    override suspend fun existingIds(ids: Collection<String>): Set<String> =
        ids.distinct().chunked(500).flatMapTo(HashSet()) { dao.existingIds(it) }
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

    override suspend fun saveGames(games: List<GameRecord>) {
        for (game in games) {
            this.games.removeAll { it.id == game.id }
            this.games.add(0, game)
        }
        flow.value = this.games.toList()
    }

    override suspend fun deleteGame(id: String) {
        games.removeAll { it.id == id }
        flow.value = games.toList()
    }
}
