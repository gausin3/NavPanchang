# Proguard / R8 rules for NavPanchang release builds.

# Swiss Ephemeris Java port (Thomas Mack v2.00.00-01). The library uses extensive
# reflection internally (Moshier table loading, nutation series, eclipse routines). We
# keep the entire swisseph package plus the top-level Transit* classes which ship in
# the same JAR.
-keep class swisseph.** { *; }
-keepclassmembers class swisseph.** { *; }
-keep class Transit* { *; }
-keepclassmembers class Transit* { *; }
-keep class ObjFormatter { *; }
-dontwarn swisseph.**

# Room entities — reflection access in schema migration tests.
-keep class com.navpanchang.data.db.entities.** { *; }

# Hilt generated classes.
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep serializable data classes used for events.json parsing.
-keepclassmembers class com.navpanchang.data.seed.** { *; }
