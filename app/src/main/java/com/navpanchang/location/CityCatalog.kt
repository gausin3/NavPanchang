package com.navpanchang.location

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

/**
 * Bundled city catalog for the Home City picker. Loads `assets/cities.json` lazily on
 * first access and caches the parsed list for the lifetime of the process.
 *
 * Cities are exposed as [City] records with lat/lon/tz + English and Hindi names. The
 * catalog is intentionally small (~45 cities) — covers the majority of users without
 * bloating the APK. Users whose city isn't listed fall back to GPS via
 * [LocationProvider] or can manually enter coordinates in a future release.
 */
@Singleton
class CityCatalog @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class City(
        val id: String,
        val nameEn: String,
        val nameHi: String,
        val country: String,
        val lat: Double,
        val lon: Double,
        val tz: String
    )

    private val cachedCities: List<City> by lazy { loadFromAssets() }

    fun all(): List<City> = cachedCities

    /**
     * Case-insensitive substring match over English or Hindi names. Returns all cities
     * if [query] is blank.
     */
    fun search(query: String): List<City> {
        if (query.isBlank()) return cachedCities
        val q = query.trim().lowercase()
        return cachedCities.filter { city ->
            city.nameEn.lowercase().contains(q) ||
                city.nameHi.contains(q) ||
                city.country.lowercase().contains(q)
        }
    }

    fun findById(id: String): City? = cachedCities.firstOrNull { it.id == id }

    private fun loadFromAssets(): List<City> {
        val json = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        val root = JSONObject(json)
        val citiesArr = root.getJSONArray("cities")
        return buildList(citiesArr.length()) {
            for (i in 0 until citiesArr.length()) {
                val obj = citiesArr.getJSONObject(i)
                add(
                    City(
                        id = obj.getString("id"),
                        nameEn = obj.getString("nameEn"),
                        nameHi = obj.getString("nameHi"),
                        country = obj.getString("country"),
                        lat = obj.getDouble("lat"),
                        lon = obj.getDouble("lon"),
                        tz = obj.getString("tz")
                    )
                )
            }
        }
    }

    private companion object {
        private const val ASSET_PATH = "cities.json"
    }
}
