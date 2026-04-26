package com.navpanchang.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 2,
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

        /**
         * v1 → v2: add `lunarConvention` to `calc_metadata`. Default `PURNIMANTA` matches
         * the entity-level default (set on fresh install; onboarding overwrites it from
         * the picked home city). Existing rows pick up the default automatically.
         *
         * See TECH_DESIGN.md §Amanta vs Purnimanta.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE calc_metadata ADD COLUMN lunarConvention " +
                        "TEXT NOT NULL DEFAULT 'PURNIMANTA'"
                )
            }
        }
    }
}
