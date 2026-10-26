plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0"
}

android {
    namespace = "dev.soupslurpr.transcribro"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    ndkVersion = "27.2.12479018"

    // Transcribro Compact: different applicationId so it installs alongside the official app.
    // (namespace above stays the same so the code's package names don't need to change.)
    defaultConfig {
        applicationId = "dev.arhamgwork.transcribro"
        minSdk = 29
        targetSdk = 36
        // The GitHub Actions build passes its run number so every build is a newer version.
        val compactBuildNumber = (findProperty("compactBuildNumber") as String?)?.toIntOrNull() ?: 0
        versionCode = 7000 + compactBuildNumber
        versionName = "7-compact-$compactBuildNumber"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters.addAll(setOf("arm64-v8a", "x86_64"))
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    androidResources {
        generateLocaleConfig = true
        localeFilters += listOf("en")
    }
    // Signing for the GitHub Actions build. The workflow sets COMPACT_KEYSTORE_FILE and
    // COMPACT_KEYSTORE_PASSWORD; without them, release builds fall back to the debug key.
    val compactKeystoreFile = System.getenv("COMPACT_KEYSTORE_FILE")
    val compactKeystorePassword = System.getenv("COMPACT_KEYSTORE_PASSWORD")
    val hasCompactKeystore = !compactKeystoreFile.isNullOrBlank() && !compactKeystorePassword.isNullOrBlank()
    signingConfigs {
        if (hasCompactKeystore) {
            create("compact") {
                storeFile = file(compactKeystoreFile!!)
                storePassword = compactKeystorePassword
                keyAlias = "compact"
                keyPassword = compactKeystorePassword
                storeType = "pkcs12"
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
            signingConfig = if (hasCompactKeystore) {
                signingConfigs.getByName("compact")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("debug")
        }
        create("staging") {
            initWith(getByName("release"))
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }
}

dependencies {

    implementation(project(":lib"))

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.navigation:navigation-compose:2.9.3")
    implementation("androidx.activity:activity-ktx:1.10.1")

    implementation(platform("androidx.compose:compose-bom:2025.07.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("com.google.accompanist:accompanist-permissions:0.37.3")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")
}
