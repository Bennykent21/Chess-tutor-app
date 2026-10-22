package com.chesstutor.app.data.bot

import com.chesstutor.app.data.local.BotProfileDao
import com.chesstutor.app.data.local.BotProfileEntity
import com.chesstutor.app.domain.bot.BotProfile
import com.chesstutor.app.domain.bot.BotRepository
import com.chesstutor.app.domain.bot.BotStyle
import com.chesstutor.app.domain.bot.CustomBot
import com.chesstutor.app.domain.bot.ResignPolicy
import com.chesstutor.app.domain.bot.ThinkSpeed
import com.chesstutor.app.domain.game.GameRules
import com.chesstutor.app.domain.game.TimeControl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** "My bots" in Room (`bot_profile`, database version 9). */
class RoomBotRepository(private val dao: BotProfileDao, private val clock: () -> Long = System::currentTimeMillis) : BotRepository {
    override fun observe(): Flow<List<CustomBot>> = dao.observeAll().map { rows -> rows.mapNotNull(::toDomain) }
    override suspend fun get(id: String): CustomBot? = dao.get(id)?.let(::toDomain)
    override suspend fun save(bot: CustomBot) = dao.upsert(toEntity(bot, clock()))
    override suspend fun delete(id: String) = dao.delete(id)

    companion object {
        private const val SEPARATOR = "|"

        fun toEntity(bot: CustomBot, now: Long): BotProfileEntity {
            val p = bot.profile
            return BotProfileEntity(
                id = p.id, name = p.name, rating = p.rating, style = p.style.name,
                aggression = p.aggression, trades = p.trades, mistakeLevel = p.mistakeLevel,
                thinkSpeed = p.thinkSpeed.name, resign = p.resign.name,
                whiteOpenings = p.whiteOpenings.joinToString(SEPARATOR), blackOpenings = p.blackOpenings.joinToString(SEPARATOR),
                face = p.face, timeControl = bot.rules.timeControl.name,
                takebacks = bot.rules.takebacks, hints = bot.rules.hints, evalBar = bot.rules.evalBar,
                createdAt = bot.createdAt, updatedAt = now,
            )
        }

        /** Null for a row this version cannot read (for example an unknown style name). */
        fun toDomain(row: BotProfileEntity): CustomBot? = runCatching {
            CustomBot(
                profile = BotProfile(
                    id = row.id, name = row.name, rating = row.rating.coerceIn(BotProfile.MIN_RATING, BotProfile.MAX_RATING),
                    style = BotStyle.valueOf(row.style), aggression = row.aggression, trades = row.trades,
                    mistakeLevel = row.mistakeLevel, thinkSpeed = ThinkSpeed.valueOf(row.thinkSpeed),
                    resign = ResignPolicy.valueOf(row.resign),
                    whiteOpenings = row.whiteOpenings.split(SEPARATOR).filter { it.isNotBlank() },
                    blackOpenings = row.blackOpenings.split(SEPARATOR).filter { it.isNotBlank() },
                    face = row.face,
                ),
                rules = GameRules(TimeControl.valueOf(row.timeControl), row.takebacks, row.hints, row.evalBar),
                createdAt = row.createdAt,
            )
        }.getOrNull()
    }
}

class InMemoryBotRepository : BotRepository {
    private val bots = MutableStateFlow<List<CustomBot>>(emptyList())
    override fun observe(): Flow<List<CustomBot>> = bots
    override suspend fun get(id: String): CustomBot? = bots.value.firstOrNull { it.id == id }
    override suspend fun save(bot: CustomBot) {
        bots.value = bots.value.filterNot { it.id == bot.id } + bot
    }
    override suspend fun delete(id: String) {
        bots.value = bots.value.filterNot { it.id == id }
    }
}
