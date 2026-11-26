import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val props = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {    packaging {
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += "**/libstockfish.so"
        }
    }
    namespace = "com.screenmentor"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.screenmentor"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.2"
        buildConfigField("String", "GEMINI_API_KEY", "\"${props.getProperty("GEMINI_API_KEY", "")}\"")
        buildConfigField("String", "LIVE_MODEL", "\"${props.getProperty("LIVE_MODEL", "gemini-3.8-live")}\"")
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
