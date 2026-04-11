package com.navpanchang.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.navpanchang.data.db.entities.CalcMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MetadataDao {

    @Query("SELECT * FROM calc_metadata WHERE id = 1")
    fun observe(): Flow<CalcMetadataEntity?>

    @Query("SELECT * FROM calc_metadata WHERE id = 1")
    suspend fun get(): CalcMetadataEntity?

    @Upsert
    suspend fun upsert(metadata: CalcMetadataEntity)
}
