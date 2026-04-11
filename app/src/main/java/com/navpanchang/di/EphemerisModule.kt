package com.navpanchang.di

import android.content.Context
import com.navpanchang.ephemeris.EphemerisEngine
import com.navpanchang.ephemeris.SunriseCalculator
import com.navpanchang.ephemeris.SwissEphemerisEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for the ephemeris layer.
 *
 * **Phase 1b — current:** [SwissEphemerisEngine] is the default. Moshier built-in
 * approximation (no `.se1` data files required), accurate to ≤ 1" for the Sun and Moon
 * across modern dates. Vendored JAR at `app/libs/swisseph-2.00.00-01.jar`. Licensing:
 * AGPL v3 flows to the whole app — see [com.navpanchang.ui.settings.AboutScreen].
 *
 * **Fallback:** [com.navpanchang.ephemeris.MeeusEphemerisEngine] remains in the codebase
 * as a pure-Kotlin reference implementation. Useful as a cross-check in
 * [com.navpanchang.ephemeris.SwissEphemerisEngineTest] and as a failsafe if the library
 * ever throws at init time. Not wired into Hilt by default.
 */
@Module
@InstallIn(SingletonComponent::class)
object EphemerisModule {

    @Provides
    @Singleton
    fun provideEphemerisEngine(
        @ApplicationContext context: Context
    ): EphemerisEngine = SwissEphemerisEngine(context)

    @Provides
    @Singleton
    fun provideSunriseCalculator(engine: EphemerisEngine): SunriseCalculator =
        SunriseCalculator(engine)
}
