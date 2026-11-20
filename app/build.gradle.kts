plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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
