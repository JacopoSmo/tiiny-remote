plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// GitHub Actions sets GITHUB_RUN_NUMBER, so every build gets a higher version and installs as an update.
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
// The experimental channel (branch "experimental") installs as its own app beside the normal one:
// another id, its own name, its own settings. Normal builds are unchanged.
val experimental = System.getenv("APP_CHANNEL") == "experimental"

android {
    namespace = "app.tiinyremote"
    compileSdk = 35

    defaultConfig {
        applicationId = if (experimental) "app.tiinyremote.exp" else "app.tiinyremote"
        manifestPlaceholders["appLabel"] = if (experimental) "Tiiny Remote Exp" else "Tiiny Remote"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        // 3.0.x: the workflow sets APP_VERSION (3.0.0 was build 37, each later build counts up).
        versionName = System.getenv("APP_VERSION") ?: "3.0.$buildNumber-local"
    }

    // It only identifies this personal app; it protects nothing else.
    // The signing key lives only in the repo's Actions secrets (SIGNING_KEYSTORE_B64 and SIGNING_PASSWORD);
    // nothing secret is kept in the code. Without them the build stops instead of signing with another key.
    signingConfigs {
        create("release") {
            val store = System.getenv("SIGNING_STORE_FILE")
            if (!store.isNullOrBlank()) {
                storeFile = file(store)
                storePassword = System.getenv("SIGNING_PASSWORD")
                keyAlias = System.getenv("SIGNING_ALIAS") ?: "tiinyremote"
                keyPassword = System.getenv("SIGNING_PASSWORD")
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

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // The optional daily check for a newer Tiiny Remote.
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // SSH client used to run "tiiny login" on the NAS.
    implementation("com.github.mwiede:jsch:0.2.20")
}
