plugins {
    // Bare id, no version: this build's AGP plugin version is already pinned
    // via `apply false` in the root build.gradle.kts (same convention already
    // used successfully by :terminal-view and :terminal-emulator).
    //
    // NOTE: org.jetbrains.kotlin.android is intentionally NOT applied here.
    // AGP 9's built-in Kotlin support means the standalone Kotlin Android
    // plugin is no longer needed (and is a hard build error if applied) -
    // :app, :terminal-view and :terminal-emulator all already rely on this
    // instead of applying the plugin explicitly.
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    // Do not change this namespace - the app module's KernelFlasherScreen.kt /
    // MtkScreen.kt import classes from com.github.capntrips.kernelflasher.*
    namespace = "com.github.capntrips.kernelflasher"
    compileSdk = 37

    defaultConfig {
        minSdk = 29

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            //noinspection ChromeOsAbiSupport
            // العمودان — كما في `:app` (قرار المالك، تكملة ١١٠). وهذا الموديول تحديدًا
            // يحمل أصلًا ثنائيات v7a مُلتزمة (`jniLibs/armeabi-v7a`): كان الحرس القديم
            // يستثنيها من الحزمة، واليوم تُشحن إلى جهازها بدل أن تُهمل.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // Room schema location (used by AppDatabase under ./schemas)
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            consumerProguardFiles("consumer-rules.pro")
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.directories.add("src/main/jniLibs")
            assets.directories.add("src/main/assets")
        }
    }

    buildFeatures {
        aidl = true    // required for IFilesystemService
        compose = true
    }

    // Matches the rest of the project (and the JDK 17 the CI runner provides) -
    // the original ZKM copy of this module targeted Java 21, which the CI
    // toolchain here does not have installed.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    // Compose / AndroidX - reuse the app's existing shared versions so there's
    // a single resolved version of each across the whole build
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.material)
    implementation(libs.androidx.core.splashscreen)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Glass/frosted-blur cards - matches the effect used by the app's own
    // flasher wrapper (nd.max.ui.flasher.StyledCard) so the ported capntrips
    // screens can share the same theming instead of stock Material3 cards
    implementation(libs.haze)
    implementation(libs.haze.blur)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)

    // Kotlin
    implementation(libs.kotlinx.serialization.json)

    // Root shell (Shell, RootService, IFilesystemService transport)
    implementation(libs.com.github.topjohnwu.libsu.core)
    implementation(libs.com.github.topjohnwu.libsu.service)
    implementation(libs.com.github.topjohnwu.libsu.io)

    // Room - used by AppDatabase for backup/flash history persistence
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // Update checking (UpdatesViewModel)
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
}
