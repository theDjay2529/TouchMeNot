plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.djay.touchmenot"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.djay.touchmenot"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "1.1.2"
    }

    buildFeatures {
        compose = true
    }

    // When using Kotlin 2.x + Compose, the Compose Compiler Gradle plugin is applied (we applied it above),
    // so composeOptions.kotlinCompilerExtensionVersion is not strictly required. If you prefer to set it explicitly,
    // pick a version compatible with Kotlin 2.0.21 — otherwise leave blank.
    composeOptions {
        // intentionally left blank when using the compose gradle plugin
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // Compose BOM (keeps versions aligned). Your versions file listed composeBom = "2024.06.00"
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))

    // Compose UI & Material 3 and tooling (use BOM to pin actual versions)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Activity Compose (version from your file: 1.9.3)
    implementation("androidx.activity:activity-compose:1.9.3")

    // core-ktx from your file: 1.15.0
    implementation("androidx.core:core-ktx:1.15.0")

    // Biometric authentication
    implementation("androidx.biometric:biometric:1.1.0")

    // Xposed compile-only API
    compileOnly(files("libs/xposed-api-82.jar"))

    // Testing dependencies
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test:runner:1.5.2")
}

configurations.all {
    resolutionStrategy {
        force("androidx.core:core:1.15.0")
        force("androidx.core:core-ktx:1.15.0")
    }
}
