package com.chesstutor.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.chesstutor.app.data.model.LinkedChessProfile
import com.chesstutor.app.data.model.RatingPlatform
import com.chesstutor.app.data.model.RatingTimeControl

@Entity(tableName = "linked_profile")
data class LinkedProfileEntity(
    @PrimaryKey val id: Int = 1,
    val platform: String,
    val username: String,
    val rapidRating: Int?,
    val blitzRating: Int?,
    val bulletRating: Int?,
    val selectedTimeControl: String,
    val lastUpdated: Long
) {
    fun toDomain(): LinkedChessProfile {
        val plat = try { RatingPlatform.valueOf(platform) } catch (e: Exception) { RatingPlatform.CHESS_COM }
        val tc = try { RatingTimeControl.valueOf(selectedTimeControl) } catch (e: Exception) { RatingTimeControl.RAPID }
        return LinkedChessProfile(
            platform = plat,
            username = username,
            rapidRating = rapidRating,
            blitzRating = blitzRating,
            bulletRating = bulletRating,
            selectedTimeControl = tc,
            lastUpdated = lastUpdated
        )
    }

    companion object {
        fun fromDomain(profile: LinkedChessProfile): LinkedProfileEntity {
            return LinkedProfileEntity(
                id = 1,
                platform = profile.platform.name,
                username = profile.username,
                rapidRating = profile.rapidRating,
                blitzRating = profile.blitzRating,
                bulletRating = profile.bulletRating,
                selectedTimeControl = profile.selectedTimeControl.name,
                lastUpdated = profile.lastUpdated
            )
        }
    }
}
