package com.navpanchang.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Singleton row holding calculation metadata: home city, last-calculated location,
 * ayanamsha / tradition config, and the bundled event catalog version.
 *
 * Always keyed on `id = 1` so upserts are a no-brainer.
 */
@Entity(tableName = "calc_metadata")
data class CalcMetadataEntity(
    @PrimaryKey val id: Int = 1,
    val homeCityName: String? = null,
    val homeLat: Double? = null,
    val homeLon: Double? = null,
    val homeTz: String? = null,
    val lastCalcLat: Double? = null,
    val lastCalcLon: Double? = null,
    val lastHomeCalcAt: Long? = null,
    val lastCurrentCalcAt: Long? = null,
    val ayanamshaType: String = "LAHIRI",
    val calculationTradition: String = "DRIK",
    val eventCatalogVersion: Int = 0,
    val sunriseOffsetMinutes: Int = 0
)
