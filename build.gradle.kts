plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
val tmdb = (findProperty("TMDB_KEY") as String?) ?: System.getenv("TMDB_KEY") ?: ""
val repo = (findProperty("GITHUB_REPO") as String?) ?: "GANTI_USERNAME/tv-launcher"
android {
    namespace = "id.kebasen.launcher"
    compileSdk = 34
    defaultConfig {
        applicationId = "id.kebasen.launcher"
        minSdk = 23; targetSdk = 34
        versionCode = (findProperty("VER_CODE") as String?)?.toIntOrNull() ?: 1
        versionName = (findProperty("VER_NAME") as String?) ?: "1.0.0"
        buildConfigField("String", "TMDB_KEY", "\"$tmdb\"")
        buildConfigField("String", "GITHUB_REPO", "\"$repo\"")
    }
    signingConfigs { create("main") { storeFile = file("launcher.p12"); storePassword = "launcher123"; keyAlias = "launcher"; keyPassword = "launcher123"; storeType = "pkcs12" } }
    buildTypes {
        release { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("main") }
        debug { signingConfig = signingConfigs.getByName("main") }
    }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("io.coil-kt:coil-compose:2.6.0")
}
