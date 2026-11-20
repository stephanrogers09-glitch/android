plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/** Reads a build setting, rejecting quotes and newlines so the generated BuildConfig stays valid. */
fun backendSetting(property: String, env: String): String {
    val raw = (findProperty(property) as? String) ?: System.getenv(env) ?: ""
    return raw.trim().replace("\"", "").replace("\n", "").replace("\r", "")
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

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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
