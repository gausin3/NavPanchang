package com.navpanchang.di

import android.content.Context
import androidx.room.Room
import com.navpanchang.data.db.AlarmDao
import com.navpanchang.data.db.EventDefinitionDao
import com.navpanchang.data.db.MetadataDao
import com.navpanchang.data.db.NavPanchangDb
import com.navpanchang.data.db.OccurrenceDao
import com.navpanchang.data.db.SubscriptionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideNavPanchangDb(@ApplicationContext context: Context): NavPanchangDb =
        Room.databaseBuilder(context, NavPanchangDb::class.java, NavPanchangDb.DB_NAME)
            .addMigrations(NavPanchangDb.MIGRATION_1_2)
            // Safety net: if a future schema bump arrives before its migration is wired
            // up, prefer wiping local data over crashing on launch. Real migrations should
            // always be added above so user data survives.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideEventDefinitionDao(db: NavPanchangDb): EventDefinitionDao = db.eventDefinitionDao()

    @Provides
    fun provideSubscriptionDao(db: NavPanchangDb): SubscriptionDao = db.subscriptionDao()

    @Provides
    fun provideOccurrenceDao(db: NavPanchangDb): OccurrenceDao = db.occurrenceDao()

    @Provides
    fun provideAlarmDao(db: NavPanchangDb): AlarmDao = db.alarmDao()

    @Provides
    fun provideMetadataDao(db: NavPanchangDb): MetadataDao = db.metadataDao()
}
