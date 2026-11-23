plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.jamal2367.urlradio"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.jamal2367.urlradio"
        minSdk = 23
        targetSdk = 37
        versionCode = 141
        versionName = "14.1"
    }

    // Release signing is configured from environment variables (used by the CI release
    // workflow). Without them, the release build stays unsigned, exactly as before.
    val releaseKeystore = providers.environmentVariable("URLRADIO_KEYSTORE_FILE").orNull
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("URLRADIO_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("URLRADIO_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("URLRADIO_KEY_PASSWORD").orNull
            }
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    // Leaves out the dependency metadata block that AGP otherwise adds to the signing block.
    // It is encrypted with a key only Google holds, so nobody else can check what it contains;
    // F-Droid and IzzyOnDroid flag it (see issue #96).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            isCrunchPngs = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            applicationIdSuffix = ".debug"
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isCrunchPngs = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
}

dependencies {
    // Compose
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.windowSizeClass)
    debugImplementation(libs.compose.ui.tooling)

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.palette.ktx)

    // Media3
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource.okhttp)

    // Misc
    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    // lets Coil load station logos straight from the web - used for the search results
    implementation(libs.coil.network.okhttp)
}
