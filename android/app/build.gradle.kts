import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp") version "2.0.0-1.0.21"
}

// Release signing credentials live in android/keystore.properties (gitignored).
// Absent on a fresh checkout/CI — the release build then stays unsigned instead
// of failing, so a clone without the secret still compiles.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) FileInputStream(keystorePropsFile).use { load(it) }
}

// Public base URL the release build talks to. Defaults to the DDNS host; override
// without editing source via -PapiBaseUrl=... (e.g. a Cloudflare Tunnel URL).
// The debug build ignores this and uses the emulator host alias (see below).
val apiBaseUrl = (project.findProperty("apiBaseUrl") as String?) ?: "https://danovich.ddns.net"

// Stable anchor the app re-reads to discover the current (ephemeral) tunnel URL
// when a request can't reach the backend. The tunnel host publishes the live URL
// here (see deploy/tunnel.sh). Override with -PtunnelDiscoveryUrl=...
val tunnelDiscoveryUrl = (project.findProperty("tunnelDiscoveryUrl") as String?)
    ?: "https://raw.githubusercontent.com/sdanovich/nearme/tunnel-url/url.txt"

android {
    namespace = "com.example.nearme"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.nearme"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Backend base URL (host root). All NearMeApi endpoint paths start with
        // "/api/...", so the effective URL is "<base>/api/...". Release talks to
        // the public HTTPS host (reverse-proxied to the backend); debug overrides
        // to the emulator's host alias over plain HTTP. No trailing slash.
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("String", "TUNNEL_DISCOVERY_URL", "\"$tunnelDiscoveryUrl\"")
        // Shared secret exchanged at POST /api/auth/token for a JWT. MUST match
        // the backend's nearme.auth.client-secret. CHANGE for production.
        buildConfigField("String", "AUTH_CLIENT_SECRET", "\"0c9409b5007fae96b09d257d2216762f199e2efd2272ac99\"")
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = rootProject.file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            // Emulator testing: 10.0.2.2 is the host machine as seen from the AVD.
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:28585\"")
        }
        release {
            isMinifyEnabled = false
            // Sign with the release key when credentials are present (see above).
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
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

    // Shared client-credentials auth interceptors (bearer + 401 refresh + token
    // store) from platform-stack, consumed as a published artifact; this app
    // supplies the TokenProvider. SNAPSHOT tracks the latest published fix.
    implementation("com.danovich.platform:android-auth:0.1.0-SNAPSHOT")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Unit tests (JVM)
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
