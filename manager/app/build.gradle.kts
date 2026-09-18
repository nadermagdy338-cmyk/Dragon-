@file:Suppress("UnstableApiUsage")

import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import com.android.build.gradle.tasks.PackageAndroidArtifact
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.agp.app)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    id("kotlin-parcelize")
    id("dagger.hilt.android.plugin")
}

android {
    namespace = "nd.max"
    // API 37 is not published on the GitHub-hosted SDK repository; API 36
    // is the newest stable level available there and satisfies the AAR
    // metadata floor of the current androidx alphas.
    compileSdk = 36

    // كلمة مرور مخزن المفاتيح من سر CI (KS_PWD / KEYSTORE_PASSWORD).
    // بناء release غير موقّع غير صالح كـ priv-app، لذلك نفشل مبكرًا عند طلبه.
    val ksPwd: String? = System.getenv("KS_PWD")
    val releaseArtifactRequested = gradle.startParameter.taskNames.any { requestedTask ->
        requestedTask.substringAfterLast(':') in setOf("assembleRelease", "bundleRelease")
    }
    if (releaseArtifactRequested && ksPwd.isNullOrEmpty()) {
        throw GradleException("KS_PWD must be set to produce a signed release artifact")
    }

    defaultConfig {
        applicationId = "nd.max"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        vectorDrawables.useSupportLibrary = true
        buildConfigField("long", "BUILD_TIME", "${System.currentTimeMillis()}L")
        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a"))
        }
    }

    tasks.matching { task ->
        task.name in setOf("packageRelease", "assembleRelease", "bundleRelease")
    }.configureEach {
        doFirst {
            check(!ksPwd.isNullOrEmpty()) {
                "KS_PWD must be set to produce a signed release artifact"
            }
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("azenith.jks")
            if (!ksPwd.isNullOrEmpty()) {
                storePassword = ksPwd
                keyAlias = "azenith_key"
                keyPassword = ksPwd
            }
        }
    }
    
    androidResources {
        // [FIX] AGP يرفض الجمع بين توليده الآلي و`android:localeConfig` الصريح في
        // البيان («Locale config generation was requested but user locale config is
        // present in manifest») فيفشل :app:processDebugMainManifest ويسقط البناء كله.
        // نُبقي قائمتنا الصريحة في res/xml/locales_config.xml لأنها المصدر الوحيد
        // الذي يضمن ظهور اللغات الـ85 كاملةً في منتقي النظام — ومنها ما لا يستنتجه
        // التوليد الآلي من أسماء المجلدات (b+sr+Latn، zh-rHK، pt-rPT…) — ولأن
        // tools/i18n_coverage.py --check-codes يتحقق من مطابقتها للمجلدات والمنتقي.
        generateLocaleConfig = false
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            vcsInfo.include = false
            signingConfig =
                if (ksPwd.isNullOrEmpty()) null
                else signingConfigs.getByName("release")
            
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), 
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/*.version"
            excludes += "DebugProbesKt.bin"
            excludes += "kotlin-tooling-metadata.json"
        }
    }

    tasks.withType<PackageAndroidArtifact> {
        doFirst { appMetadata.asFile.orNull?.writeText("") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.animation.core)

    implementation(libs.androidx.navigation.compose)
    
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.material.kolor)
    implementation(libs.haze)
    implementation(libs.haze.blur)
    implementation(libs.haze.materials)
    implementation(libs.me.zhanghai.android.appiconloader.coil)
    implementation(libs.io.coil.kt.coil.compose)
    implementation(libs.androidx.documentfile)

    implementation(libs.com.github.topjohnwu.libsu.core)
    implementation(libs.com.github.topjohnwu.libsu.service)
    implementation(libs.com.github.topjohnwu.libsu.io)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.yalantis.ucrop)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.transition)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.ansi.library)
    implementation(libs.ansi.library.ktx)
    implementation(libs.hiddenapibypass)
    implementation(libs.coil.gif)
    implementation(libs.compose.markdown)

    // Hilt
    implementation("com.google.dagger:hilt-android:2.59.2")
    ksp("com.google.dagger:hilt-compiler:2.59.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Terminal (ported from ZKM)
    implementation(project(":terminal-view"))
    implementation(project(":kernel-flasher"))
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    // ── الاختبارات الوحدوية ──
    // عقد نصوص التوصيات↔الأفعال (RecommendationTextClassifierTest) —
    // أول خط دفاع ضد انزياح الصياغة بين المصادر والمصنِّف
    testImplementation("junit:junit:4.13.2")
    // Local JVM tests execute against the host JDK, so Android's framework
    // org.json stubs must be replaced by the real JSON implementation.
    testImplementation("org.json:json:20260814")
}
