plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.navpanchang"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.navpanchang"
        minSdk = 26
        targetSdk = 34
        // versionCode is overridden by CI (github.run_number); keep a sane default for local builds.
        versionCode = (System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1)
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Contact info baked into BuildConfig so the UI doesn't carry it inline.
        // Update both values in one place when contact channels change. The
        // phone is used for the wa.me deep link (digits only, no `+`).
        buildConfigField("String", "CONTACT_PHONE_E164", "\"+919028155500\"")
        buildConfigField("String", "CONTACT_EMAIL", "\"gaurav@navtakniq.com\"")
    }

    // Signing config consumed by the release build type. Values come from env vars in
    // CI; locally-set `gradle.properties` values are also honored. If none are set the
    // release build will still compile but not be signed — `./gradlew assembleDebug`
    // remains the local path.
    signingConfigs {
        create("release") {
            val ksFile = System.getenv("KEYSTORE_FILE")
            if (!ksFile.isNullOrBlank()) {
                storeFile = file(ksFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Only attach the signing config if a keystore was actually configured —
            // otherwise CI would fail with a misleading "missing keystore" error on
            // validation-only runs.
            if (!System.getenv("KEYSTORE_FILE").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // Room schema export — feeds AutoMigration test fixtures (Phase 3+).
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Robolectric needs Android resources merged into the unit-test classpath so it
    // can spin up an Android-like runtime (Context, Room, etc.) on the host JVM.
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

// Pin Gradle's compile toolchain to JDK 17 across both Java and Kotlin.
//
// AGP 8.5+ and Robolectric 4.13 don't support Java 25 bytecode (class file major
// version 69). Letting Gradle pick up whatever JDK happens to be on the system
// (including Java 25 from JetBrains-bundled IDEs) breaks the build with
// "Unsupported class file major version 69" errors at runtime.
//
// `jvmToolchain(17)` does two things:
//  1. Tells Kotlin and Java compile tasks they need a JDK 17 toolchain.
//  2. With the foojay-resolver-convention plugin (configured in
//     `settings.gradle.kts`), Gradle will auto-download a Temurin 17 JDK on
//     first run if no local install matches — so contributors don't have to
//     install JDK 17 manually before `./gradlew assembleDebug` works.
//
// This replaces the previous `org.gradle.java.home=...` line in
// `gradle.properties`, which hard-coded a Mac-specific Temurin path that broke
// for any clone outside the original developer's machine.
kotlin {
    jvmToolchain(17)
}

dependencies {
    // Swiss Ephemeris — Thomas Mack Java port (v2.00.00-01), vendored under app/libs/.
    // AGPL v3 license flows to the whole app; see LICENSE + About screen attribution.
    implementation(files("libs/swisseph-2.00.00-01.jar"))

    // AndroidX core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    // XML Material3 themes for res/values/themes.xml (launch theme before Compose takes over).
    implementation(libs.material)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Location (for GPS + geofencing; see plan §Two-tier lookahead)
    implementation(libs.play.services.location)

    // Debug-only: LeakCanary monitors the 24-month lookahead (see plan §Ephemeris)
    debugImplementation(libs.leakcanary.android)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    // Android's android.jar stubs org.json — pull in the real JVM implementation so
    // EventRuleParserTest and friends can run as local unit tests.
    testImplementation(libs.json.jvm)

    // Room-integration tests via Robolectric (in-memory DB on the host JVM).
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
