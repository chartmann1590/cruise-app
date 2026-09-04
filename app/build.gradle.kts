import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("com.google.firebase.firebase-perf")
}

// ── AdMob IDs — secure injection (never committed) ────────────────────────
// Real IDs live in app/admob.properties (gitignored). If that file is missing
// (fresh clone, CI, public PR) we fall back to Google's official TEST IDs so
// the project still builds and shows test ads without any secret.
// See app/admob.properties.example for the template.
// Also supports: app/secrets.properties, local.properties, gradle.properties,
// and env vars ADMOB_APP_ID / ADMOB_BANNER_ID / ADMOB_INTERSTITIAL_ID.
lateinit var admobAppId: String
lateinit var admobBannerId: String
lateinit var admobInterstitialId: String
run {
    val testAppId = "ca-app-pub-3940256099942544~3347511713"
    val testBanner = "ca-app-pub-3940256099942544/6300978111"
    val testInterstitial = "ca-app-pub-3940256099942544/1033173712"
    var appId = testAppId
    var banner = testBanner
    var inter = testInterstitial

    fun loadProps(file: java.io.File, sink: Properties) {
        if (file.exists()) {
            try {
                file.inputStream().use { sink.load(it) }
            } catch (_: Exception) {}
        }
    }

    // Collect from files — later files override earlier, so project-local wins over root
    val merged = Properties()
    loadProps(rootDir.resolve("admob.properties"), merged)
    loadProps(rootDir.resolve("secrets.properties"), merged)
    loadProps(projectDir.resolve("admob.properties"), merged)
    loadProps(projectDir.resolve("secrets.properties"), merged)
    loadProps(rootDir.resolve("local.properties"), merged)

    fun firstProp(vararg keys: String): String? {
        for (k in keys) {
            val v = merged.getProperty(k)?.trim()
            if (!v.isNullOrBlank()) return v
            // also check Gradle properties (gradle.properties, -P flags)
            val gp = findProperty(k)?.toString()?.trim()
            if (!gp.isNullOrBlank()) return gp
        }
        return null
    }

    firstProp("ADMOB_APP_ID", "admob.appId", "admobAppId")?.let { appId = it }
    firstProp("ADMOB_BANNER_ID", "admob.bannerId", "admobBannerId", "BANNER_AD_UNIT_ID")?.let { banner = it }
    firstProp("ADMOB_INTERSTITIAL_ID", "admob.interstitialId", "admobInterstitialId", "INTERSTITIAL_AD_UNIT_ID")?.let { inter = it }

    // Environment variables (highest priority — for CI)
    System.getenv("ADMOB_APP_ID")?.trim()?.takeIf { it.isNotBlank() }?.let { appId = it }
    System.getenv("ADMOB_BANNER_ID")?.trim()?.takeIf { it.isNotBlank() }?.let { banner = it }
    System.getenv("ADMOB_INTERSTITIAL_ID")?.trim()?.takeIf { it.isNotBlank() }?.let { inter = it }

    admobAppId = appId
    admobBannerId = banner
    admobInterstitialId = inter
}

val releaseKeystoreProperties = Properties()
val releaseKeystorePropertiesFile = projectDir.resolve("keystore.properties")
if (releaseKeystorePropertiesFile.exists()) {
    releaseKeystorePropertiesFile.inputStream().use { releaseKeystoreProperties.load(it) }
}

android {
    namespace = "com.charles.cruiseapp"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.charles.cruiseapp"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // AdMob — injected from admob.properties (or test fallback); never hardcoded
        manifestPlaceholders["admobAppId"] = admobAppId
        buildConfigField("String", "ADMOB_APP_ID", "\"$admobAppId\"")
        buildConfigField("String", "ADMOB_BANNER_ID", "\"$admobBannerId\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$admobInterstitialId\"")
        // Also generate a string resource so @string/admob_app_id resolves to the same value
        resValue("string", "admob_app_id", admobAppId)
    }
    signingConfigs {
        create("release") {
            if (releaseKeystorePropertiesFile.exists()) {
                storeFile = projectDir.resolve(releaseKeystoreProperties.getProperty("storeFile"))
                storePassword = releaseKeystoreProperties.getProperty("storePassword")
                keyAlias = releaseKeystoreProperties.getProperty("keyAlias")
                keyPassword = releaseKeystoreProperties.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

val verifyPlayReleaseConfiguration by tasks.registering {
    doLast {
        check(releaseKeystorePropertiesFile.exists()) {
            "Missing app/keystore.properties. A Play release must be signed with the upload key."
        }
        check(!admobAppId.contains("3940256099942544") &&
            !admobBannerId.contains("3940256099942544") &&
            !admobInterstitialId.contains("3940256099942544")) {
            "Google test ad IDs are configured. Add production IDs before creating a Play release."
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyPlayReleaseConfiguration)
}

dependencies {
    val composeBom = "2024.10.00"
    val roomVersion = "2.6.1"
    val lifecycleVersion = "2.8.6"
    val navVersion = "2.8.4"

    // Firebase BOM + Crashlytics + Performance + Analytics (required for Crashlytics/Perf)
    // Using BOM 32.7.4 to stay compatible with Kotlin 1.9.22 (AGP 8.3.2). Newer 33.x requires Kotlin 2.1.
    implementation(platform("com.google.firebase:firebase-bom:32.7.4"))
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-perf-ktx")

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVersion")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:$composeBom"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:$navVersion")
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-scalars:2.11.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.android.gms:play-services-nearby:19.3.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("org.nanohttpd:nanohttpd-websocket:2.3.1")
    // Maps & deck images - no API key required (OSM + GitHub-hosted)
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("io.coil-kt:coil:2.6.0")

    // AdMob — banner + interstitial (IDs injected via BuildConfig from admob.properties)
    implementation("com.google.android.gms:play-services-ads:23.6.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")

    // ML Kit — on-device translation (free, no API key, offline after model download)
    implementation("com.google.mlkit:translate:17.0.3")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
