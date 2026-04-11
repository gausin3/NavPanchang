package com.navpanchang.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.navpanchang.data.db.entities.CalcMetadataEntity
import com.navpanchang.data.db.entities.EventDefinitionEntity
import com.navpanchang.data.db.entities.OccurrenceEntity
import com.navpanchang.data.db.entities.ScheduledAlarmEntity
import com.navpanchang.data.db.entities.SubscriptionEntity

@Database(
    entities = [
        EventDefinitionEntity::class,
        SubscriptionEntity::class,
        OccurrenceEntity::class,
        ScheduledAlarmEntity::class,
        CalcMetadataEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class NavPanchangDb : RoomDatabase() {
    abstract fun eventDefinitionDao(): EventDefinitionDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun occurrenceDao(): OccurrenceDao
    abstract fun alarmDao(): AlarmDao
    abstract fun metadataDao(): MetadataDao

    companion object {
        const val DB_NAME = "navpanchang.db"
    }
}
