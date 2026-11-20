plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ar.luis.canalestv"
    compileSdk = 35

    defaultConfig {
        applicationId = "ar.luis.canalestv"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }
    // Clave de firma fija (uso personal): así cada APK nuevo se instala encima del anterior.
    // Sin esto GitHub firma cada versión con una clave distinta y la tele rechaza la actualización.
    signingConfigs {
        getByName("debug") {
            storeFile = file("canalestv.keystore")
            storePassword = "canalestv"
            keyAlias = "canalestv"
            keyPassword = "canalestv"
        }
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.tv:tv-material:1.0.0")
}
