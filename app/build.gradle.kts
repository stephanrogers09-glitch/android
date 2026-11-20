plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/** Reads a build setting, rejecting quotes and newlines so the generated BuildConfig stays valid. */
fun backendSetting(property: String, env: String): String {
    val raw = (findProperty(property) as? String) ?: System.getenv(env) ?: ""
    return raw.trim().replace("\"", "").replace("\n", "").replace("\r", "")
}

/**
 * Release signing comes only from Gradle properties or environment variables. Nothing is stored in
 * this repository, and the debug key is never used for release. Values are not trimmed: a password
 * may legitimately contain spaces.
 */
fun releaseSetting(property: String, env: String): String? =
    ((findProperty(property) as? String) ?: System.getenv(env))?.takeIf { it.isNotEmpty() }

val releaseStoreFile = releaseSetting("lockedin.releaseStoreFile", "LOCKEDIN_RELEASE_STORE_FILE")
val releaseStorePassword = releaseSetting("lockedin.releaseStorePassword", "LOCKEDIN_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = releaseSetting("lockedin.releaseKeyAlias", "LOCKEDIN_RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseSetting("lockedin.releaseKeyPassword", "LOCKEDIN_RELEASE_KEY_PASSWORD")
val missingReleaseSettings: List<String> = listOfNotNull(
    if (releaseStoreFile == null) "LOCKEDIN_RELEASE_STORE_FILE" else null,
    if (releaseStorePassword == null) "LOCKEDIN_RELEASE_STORE_PASSWORD" else null,
    if (releaseKeyAlias == null) "LOCKEDIN_RELEASE_KEY_ALIAS" else null,
    if (releaseKeyPassword == null) "LOCKEDIN_RELEASE_KEY_PASSWORD" else null,
)

// A release build (or a build of everything) without release signing must fail, not produce an
// unsigned or debug-signed APK. Debug builds are unaffected.
gradle.taskGraph.whenReady {
    val requested = gradle.startParameter.taskNames
    val needsRelease = requested.any { it.contains("release", ignoreCase = true) } ||
        requested.any { it == "assemble" || it == "build" || it == "bundle" }
    if (needsRelease && missingReleaseSettings.isNotEmpty()) {
        throw GradleException(
            "Release build needs a release keystore. Missing: " + missingReleaseSettings.joinToString(", ") +
                ". The debug key is never used for release builds.",
        )
    }
}

android {
    namespace = "app.lockedin.phone"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.lockedin.phone"
        minSdk = 26
        targetSdk = 34
        versionCode = 16
        versionName = "1.8.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Backend settings come from Gradle properties or environment variables at build time.
        // Nothing is hardcoded. Empty means the study session reports what to set.
        buildConfigField("String", "SUPABASE_URL", "\"${backendSetting("lockedin.supabaseUrl", "LOCKEDIN_SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${backendSetting("lockedin.supabaseAnonKey", "LOCKEDIN_SUPABASE_ANON_KEY")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            // Only configured when every release setting is present; otherwise the guard above stops the build.
            if (releaseStoreFile != null && releaseStorePassword != null &&
                releaseKeyAlias != null && releaseKeyPassword != null
            ) {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // Platform-independent core (sessions, entitlements, AI wire format). Pure Kotlin, no Android
    // imports, compiled into the app so there is one build and no second module to keep in sync.
    sourceSets {
        getByName("main") {
            java.srcDir("../shared/src/main/kotlin")
        }
    }
}

dependencies {
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}
