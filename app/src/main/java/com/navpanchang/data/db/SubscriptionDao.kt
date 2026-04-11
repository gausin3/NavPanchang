package com.navpanchang.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.navpanchang.data.db.entities.SubscriptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {

    @Query("SELECT * FROM subscriptions")
    fun observeAll(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE eventId = :eventId")
    suspend fun getById(eventId: String): SubscriptionEntity?

    @Query("SELECT * FROM subscriptions WHERE enabled = 1")
    suspend fun getEnabled(): List<SubscriptionEntity>

    @Upsert
    suspend fun upsert(subscription: SubscriptionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(subscription: SubscriptionEntity): Long
}
