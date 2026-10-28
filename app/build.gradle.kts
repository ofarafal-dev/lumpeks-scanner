plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

// Podpis release ze zmiennych środowiskowych (w CI ustawia je build.yml z GitHub Secrets).
// Brak zmiennych (build lokalny) → release podpisany kluczem debug.
val keystorePath: String? = System.getenv("KEYSTORE_PATH")
val keystorePassword: String? = System.getenv("KEYSTORE_PASSWORD")
val releaseKeyAlias: String? = System.getenv("KEY_ALIAS")
val releaseKeyPassword: String? = System.getenv("KEY_PASSWORD")
val hasReleaseKey = listOf(keystorePath, keystorePassword, releaseKeyAlias, releaseKeyPassword)
    .all { !it.isNullOrBlank() } && file(keystorePath!!).exists()

android {
    namespace = "dev.ofarafal.lumpeksscanner"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.ofarafal.lumpeksscanner"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
        buildConfigField(
            "String",
            "BRANDS_URL",
            "\"https://raw.githubusercontent.com/ofarafal-dev/lumpeks-scanner/main/data/brands.json\""
        )
        buildConfigField(
            "String",
            "RELEASES_URL",
            "\"https://api.github.com/repos/ofarafal-dev/lumpeks-scanner/releases/latest\""
        )
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKey) {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                logger.warn("Brak KEYSTORE_* — release podpisany kluczem debug")
                signingConfigs.getByName("debug")
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

    // Startowa baza marek: data/brands.json z repo trafia do APK jako assets/brands.json
    // (jedno źródło prawdy — nie trzeba kopiować pliku ręcznie).
    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets", "$rootDir/data")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")

    val camerax = "1.3.4"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    // ML Kit Text Recognition (łaciński) — model wbudowany w APK, działa offline
    implementation("com.google.mlkit:text-recognition:16.0.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
}
