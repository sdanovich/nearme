plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp") version "2.0.0-1.0.21"
}

android {
    namespace = "com.example.nearme"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.nearme"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Backend base URL. The backend runs at home and is reached over DDNS so
        // a real phone can connect from anywhere. For local emulator testing,
        // override with "http://10.0.2.2:28085" (10.0.2.2 = host from the emulator).
        // No trailing slash — all endpoint paths in NearMeApi start with "/".
        buildConfigField("String", "API_BASE_URL", "\"http://danovich.ddns.net:28085\"")
    }

    buildTypes {
        debug {
            // Emulator testing: 10.0.2.2 is the host machine as seen from the AVD.
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:28085\"")
        }
        release {
            isMinifyEnabled = false
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
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Location
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Room local cache (station locations + price history, survives restart)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Networking: Retrofit + Moshi for JSON
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
