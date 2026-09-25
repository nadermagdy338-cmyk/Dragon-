/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
@file:Suppress("UnstableApiUsage")

import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import com.android.build.gradle.tasks.PackageAndroidArtifact
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.ByteArrayOutputStream
import java.io.File

plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.agp.app)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    id("kotlin-parcelize")
    id("dagger.hilt.android.plugin")
}

/**
 * زمن البناء الذي يُكتب في `BuildConfig.BUILD_TIME` (يُعرض كتاريخ بناء في ترويسة الإعدادات).
 *
 * **لماذا ليس `System.currentTimeMillis()` مباشرةً:** ذاك يتغيّر في كل تشغيل، فيصير
 * `generateReleaseBuildConfig` **غير up-to-date أبدًا** ⇒ كل من يقرأ `BuildConfig` يُعاد
 * تصريفه (٤ ملفات) ⇒ ثم dex ثم R8 ثم التغليف — سلسلة كاملة تُعاد من أجل رقم واحد،
 * و`--build-cache` لا يُصيب منها شيئًا لأن مخرَج المُدخَل يتغيّر في كل بناء.
 *
 * **البديل:** زمن الالتزام — ثابتٌ للنُسخة الواحدة (نفس الالتزام ⇒ نفس الرقم)، فيصير البناء
 * الثاني للنُسخة نفسها شبه مجّاني على الجهاز. وهو أصدق معنًى أيضًا: تاريخ إنتاج النسخة.
 *
 * **ولماذا `ValueSource` لا `ProcessBuilder` في جسم السكربت** — عطب قِيس في CI، لا تخمين.
 * تشغيل عملية خارجية في زمن التهيئة مباشرةً يجعل Gradle **يتخلّى عن مخزن التهيئة كلّه**:
 *
 * ```
 * Configuration cache problems found in this build.
 * 1 problem was found storing the configuration cache.
 * - Build file 'app/build.gradle.kts': external process started 'git log -1 --format=%ct'
 * ```
 *
 * أي أن كل تشغيل كان يُهدِر المخزن من أجل رقم واحد. والطريق الذي توثّقه Gradle نفسها
 * (Configuration Cache Requirements §Running External Processes) هو تشغيل العملية **داخل
 * `ValueSource`** بمُشغِّل محقون، وحينها: «تُشغَّل العملية في كل بناء لتقرير هل المخزن ما زال
 * صالحًا، وإن تغيّرت القيمة فُسد المخزن» — وهو المطلوب بالضبط: نفس الالتزام ⇒ نفس الرقم ⇒
 * المخزن صالح، والتزام جديد ⇒ الرقم يتغيّر ⇒ تُعاد التهيئة مرّة واحدة.
 *
 * **والسقوط:** بلا `git` أو بلا التزام (نسخة مفكوكة من zip، أو بناء في AndroidIDE) يُكتب يوم
 * البناء الحالي بدل أن يُرمى خطأ أو يُكتب صفرًا. وتدويرُه إلى اليوم **مقصود** لا تهاون: القارئ
 * الوحيد لهذا الرقم يرسمه `yyyy-MM-dd` (SettingsHeaderComponent)، فالتدوير لا يكذب في شيء
 * ويُبقي المخزن صالحًا داخل اليوم نفسه على جهاز بلا `git`.
 */
abstract class GitCommitTimeValueSource : ValueSource<Long, GitCommitTimeValueSource.Parameters> {

    interface Parameters : ValueSourceParameters {
        /**
         * مسار المستودع. `String` لا `DirectoryProperty` عن قصد: بصمة شجرة كاملة تُفسد المخزن
         * عند كل ملف يتغيّر، بينما مسارٌ ثابت لا يتغيّر أبدًا.
         */
        val repositoryPath: Property<String>
    }

    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): Long {
        val output = ByteArrayOutputStream()
        val ran = runCatching {
            execOperations.exec {
                workingDir = File(parameters.repositoryPath.get())
                commandLine("git", "log", "-1", "--format=%ct")
                standardOutput = output
                // بلا مستودع يعود `git` بغير صفر: لا نُفشل البناء، بل نسقط إلى يوم البناء.
                isIgnoreExitValue = true
            }
        }.isSuccess
        val epochSeconds = output.toByteArray().toString(Charsets.UTF_8).trim().toLongOrNull()
        return if (ran && epochSeconds != null) epochSeconds * 1000L else todayEpochMs()
    }

    /** يوم البناء الحالي — سقوطٌ صادق (لا صفر = ١٩٧٠) وثابتٌ داخل اليوم. */
    private fun todayEpochMs(): Long {
        val millisPerDay = 24L * 60L * 60L * 1000L
        return System.currentTimeMillis().let { now -> now - now % millisPerDay }
    }
}

/**
 * ويُقاس **في زمن التهيئة** عن قصد: القيمة الفعلية هي ما يُخزَّن في مدخلات التهيئة، فالنداء
 * يُعاد في كل بناء ليعرف Gradle هل المخزن ما زال صالحًا. والكلفة مقيسة لا مُقدَّرة: تشغيلان
 * كاملان في تجربة هذا الإصلاح استغرقا ٨٥٦ و٧٦١ ملّي ثانية، وفي كلٍّ منهما نداء `git` واحد.
 */
val buildTimeEpochMs: Long = providers.of(GitCommitTimeValueSource::class) {
    parameters {
        repositoryPath.set(rootDir.absolutePath)
    }
}.get()

android {
    namespace = "nd.max"
    // **API 37 = Android 17 — منشور ومُتحقَّق (تكملة ١١٠):** التقييد السابق («API 37 غير
    // منشور في مستودع SDK») كان صحيحًا وقته، وقد انتهى: `platforms;android-37.0`
    // و`build-tools;37.0.0` مستقرّان — مُثبَّتان محليًّا ومُشترَطان في CI. وطلب المالك
    // «يعمل على كل أندرويد دون مشاكل» يبدأ بأن يُصرَّف ضد أحدث واجهة ويُعلَن عليها.
    compileSdk = 37

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
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        vectorDrawables.useSupportLibrary = true
        buildConfigField("long", "BUILD_TIME", "${buildTimeEpochMs}L")
        ndk {
            // **العمودان معًا (قرار المالك، تكملة ١١٠ — عكس تكملة ٨٢):** هواتف 32-بت
            // تعود مدعومة كاملة: `armeabi-v7a` يُبنى في التطبيق (مكتبات JNI) وفي الموديول
            // (الثنائيات الخمسة) وفي المنصّب (يختار بحسب `ARCH`)، و`arm64-v8a` كما كان.
            // وترتيب القائمة (64 ثم 32) هو عُرف السلف المُستعاد من المشروع القديم.
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
    
    // ── ملاحظة عن `lintVitalRelease` (قِيست، ولا تُغيَّر هنا) ──
    //
    // `assembleRelease` يستدعي `lintVitalRelease` تلقائيًّا، وقِسناها في هذه البيئة:
    // **٣ دقائق و٢٩ ثانية** من أصل ~١٥ دقيقة (٤ أنوية · ١٥ جيجابايت، نفس مقاس runner).
    //
    // **ولا يُعطَّل هنا:** `checkReleaseBuilds = false` **يمحو المهمة نفسها** (تحقّقنا:
    // اختفت من `:app:tasks --all`)، فيصير الحاجز غائبًا لا منقولًا — وهذا إسقاط لجودة لا
    // تسريع لبناء. والفصل جرى بـ`-x :app:lintVitalRelease` في أمر البناء داخل
    // `.github/workflows/build.yml` (والمهمة الموازية التي كانت تشغّله أُزيلت في تكملة ٨٠)
    // ⇒ **لا حاجز آليّ اليوم لِما تكشفه هذه المهمة وحدها**، ومسار الفحص اليدوي قبل الإصدار:
    // `./gradlew :app:lintVitalRelease`.

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
        // **حرس 32-بت أُزيل (تكملة ١١٠):** كان يمنع دخول `lib/armeabi-v7a/**` في زمن
        // «64-بت وحده». اليوم الـABIان يُشحنان معًا، فالحرس انقلب إلى الاتجاه الصحيح
        // وموضعه CI: البناء يفشل إن **غاب** `lib/armeabi-v7a/` أو `lib/arm64-v8a/`.
        // (وقيمة إيجابية بالمناسبة: `kernel-flasher` حمل ثنائيات v7a جاهزة في المستودع،
        // وكان الحرس السابق يمنعها من الوصول إلى جهازها. **وقد حُذفت الوحدة** في جولة
        // تدقيق الأصل `PROVENANCE-01` مع ميزة التفليش.)
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

    // Shizuku: طبقة امتياز ثانية بمستوى ADB بلا جذر (AR-20).
    // provider ليس اختياريًا: يحمل ShizukuProvider الذي يُصرَّح به في البيان.
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

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
