# Proguard / R8 rules for NavPanchang release builds.

# Swiss Ephemeris Java port (Thomas Mack v2.00.00-01). The library uses extensive
# reflection internally (Moshier table loading, nutation series, eclipse routines). We
# keep the entire swisseph package plus the top-level helper classes which ship in
# the same JAR (Transits, TransitCalculator, ObjFormatter, CFmt, DblObj, …).
#
# These top-level classes have NO package — they're at the root of the JAR. R8
# can't see them via wildcards under `swisseph.**`, so each must be kept by name
# OR every reference from `swisseph.**` to them must be -dontwarn'd.
#
# We do both: keep the names we know about, and -dontwarn anything else the
# library references conditionally so R8 doesn't fail the build on an
# uncalled-from-Android code path (e.g. CFmt is used by Transits.format()
# which Android callers never invoke).
-keep class swisseph.** { *; }
-keepclassmembers class swisseph.** { *; }
-keep class Transits { *; }
-keep class TransitCalculator { *; }
-keep class TCPlanet { *; }
-keep class TCPlanetPlanet { *; }
-keep class ObjFormatter { *; }
-keep class CFmt { *; }
-keep class DblObj { *; }
-keep class IntObj { *; }
-dontwarn swisseph.**
# Belt-and-suspenders: any other top-level class from the swisseph JAR that we
# haven't named explicitly. R8 silently warns instead of erroring.
-dontwarn CFmt
-dontwarn DblObj
-dontwarn IntObj
-dontwarn ObjFormatter
-dontwarn Transits
-dontwarn TransitCalculator
-dontwarn TCPlanet
-dontwarn TCPlanetPlanet

# Room entities — reflection access in schema migration tests.
-keep class com.navpanchang.data.db.entities.** { *; }

# Hilt generated classes.
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep serializable data classes used for events.json parsing.
-keepclassmembers class com.navpanchang.data.seed.** { *; }
