# PROVENANCE — أصل كل ملف ورخصته وما يجب عمله

> **مُولَّد آليًّا — لا يُحرَّر يدويًّا:**
> `python3 tools/license_audit.py --provenance`
> （و`build/license-report.json` هو نفس القياس بصيغة يقرؤها CI）

**الطريقة:** يُصنَّف كل ملف متعقّب في git من **ترويسته الفعلية** (أول 30 سطرًا) لا من اسمه ولا من مجلّده. ومن لا ترويسة له يُصنَّف بعائلة وحدته ويُكتب ذلك صراحةً في عمود الدليل. والأصول وتراخيصها ومراجعها مقيّدة في جدول `SOURCES` داخل الأداة، فكل حكم هنا قابل لإعادة الاشتقاق بأمر واحد.

## الخلاصة

| المقياس | العدد |
| --- | --- |
| ملفات متعقّبة | 1973 |
| ملفات مشتقّة من GPL | **23** |
| منها داخل مسار الإصدار | **23** |
| ملفات مجهولة الترخيص | 3 |
| ملفات برخصة حُرّة | 1947 |
| تبعيات Gradle | 42 |
| صناديق Cargo | 108 |
| ثغرات ABI | 0 |
| تناقضات ترويسة (GPL + Apache-2.0) | 17 |

## بوابة GPL (PHASE 10)

| السؤال | الجواب |
| --- | --- |
| GPL source in MaxManager-owned code | **YES** |
| GPL dependency | **NO** |
| GPL native binary | **NO** |
| GPL code in APK | **YES** |
| GPL-derived source remaining | **YES** |
| GPL declared in body — pending diff review | **YES** |
| Unknown-license component | **YES** |

**وما دام أيٌّ منها `YES` فالتنظيف غير مكتمل** — والأداة تُفشل CI (`--assert`) عند `gpl_code_in_apk = YES`.

## أ‌) ملفات مشتقّة من GPL — تُعاد كتابتها أو تُحذف

| FILE | ORIGIN | LICENSE | STATUS | ACTION |
| --- | --- | --- | --- | --- |
| `manager/app/src/main/aidl/nd/max/IMtkService.aidl` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | **REWRITE_OR_REMOVE** |
| `manager/app/src/main/java/nd/max/service/FpsOverlayService.kt` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | **REWRITE_OR_REMOVE** |
| `manager/app/src/main/java/nd/max/service/MtkRootService.kt` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | **REWRITE_OR_REMOVE** |
| `manager/app/src/main/java/nd/max/service/ProcessOverlayService.kt` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | **REWRITE_OR_REMOVE** |
| `manager/app/src/main/java/nd/max/ui/component/WeatherEffects.kt` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | **REWRITE_OR_REMOVE** |
| `manager/app/src/main/java/nd/max/ui/settings/SettingsPreference.kt` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | **REWRITE_OR_REMOVE** |
| `manager/app/src/main/java/nd/max/ui/settings/SettingsViewModel.kt` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | **REWRITE_OR_REMOVE** |
| `manager/app/src/main/java/nd/max/ui/subscreens/DebloatFreezeScreen.kt` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | **REWRITE_OR_REMOVE** |
| `manager/app/src/main/java/nd/max/ui/subscreens/ProcessManagerScreen.kt` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | **REWRITE_OR_REMOVE** |
| `manager/app/src/main/java/nd/max/ui/subscreens/SetEditScreen.kt` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | **REWRITE_OR_REMOVE** |
| `manager/app/src/main/java/nd/max/ui/theme/ThemeMode.kt` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | **REWRITE_OR_REMOVE** |
| `manager/app/src/main/java/nd/max/ui/util/DebloatFreezeUtil.kt` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | **REWRITE_OR_REMOVE** |
| `manager/app/src/main/java/nd/max/ui/util/Dex2oatUtil.kt` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | **REWRITE_OR_REMOVE** |
| `manager/app/src/main/java/nd/max/ui/util/FpsMonitorUtil.kt` | vtools | GPL-3.0-only | GPL_DERIVED | **REWRITE_OR_REMOVE** |
| `manager/app/src/main/java/nd/max/ui/util/MtkUtils.kt` | vtools | GPL-3.0-only | GPL_DERIVED | **REWRITE_OR_REMOVE** |
| `manager/app/src/main/java/nd/max/ui/util/ProcessMonitorUtil.kt` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | **REWRITE_OR_REMOVE** |
| `manager/app/src/main/java/nd/max/ui/util/SetEditUtil.kt` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | **REWRITE_OR_REMOVE** |
| `manager/app/src/main/java/nd/max/ui/util/ThermalUtil.kt` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | **REWRITE_OR_REMOVE** |
| `manager/app/src/main/java/nd/max/ui/viewmodel/DebloatFreezeViewModel.kt` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | **REWRITE_OR_REMOVE** |
| `manager/app/src/main/java/nd/max/ui/viewmodel/Dex2oatViewModel.kt` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | **REWRITE_OR_REMOVE** |
| `manager/app/src/main/java/nd/max/ui/viewmodel/ProcessManagerViewModel.kt` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | **REWRITE_OR_REMOVE** |
| `manager/app/src/main/java/nd/max/ui/viewmodel/SetEditViewModel.kt` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | **REWRITE_OR_REMOVE** |
| `manager/app/src/main/java/nd/max/ui/viewmodel/ThermalDevicesViewModel.kt` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | **REWRITE_OR_REMOVE** |

## ب) أصل GPL مُعلَن في المتن لا في الترويسة — يُحسم بمقابلة (diff) لا بثقة

هذه ملفات **يقول تعليقها** إنها مأخوذة/مقتبسة من مشروع GPL، ولا تحمل ترويسة حقوق.
وصفها بالاستقلال يفترضها لا يثبتها — فالحكم فيها: إمّا مقابلة تكشف أنها مكتوبة من جديد فعلًا،
وإمّا إعادة تنفيذ مستقلّة. ولا تُترك كما هي.

| FILE | يُصرَّح بأصله | الدليل | ACTION |
| --- | --- | --- | --- |
| `manager/app/src/main/java/nd/max/ui/subscreens/CpuCoreControlScreen.kt` | ZKM (Zuan Kernel Manager) | ترويسة ملكية بلا ذكر أصل خارجي؛ يُذكر في المتن: ZKM (Zuan Kernel Manager) | **VERIFY_BY_DIFF_OR_REWRITE** |
| `manager/app/src/main/java/nd/max/ui/subscreens/LogsViewerScreen.kt` | ZKM (Zuan Kernel Manager) | ترويسة ملكية بلا ذكر أصل خارجي؛ يُذكر في المتن: ZKM (Zuan Kernel Manager) | **VERIFY_BY_DIFF_OR_REWRITE** |
| `manager/app/src/main/java/nd/max/ui/util/CpuTopologyUtil.kt` | ZKM (Zuan Kernel Manager) | ترويسة ملكية بلا ذكر أصل خارجي؛ يُذكر في المتن: ZKM (Zuan Kernel Manager) | **VERIFY_BY_DIFF_OR_REWRITE** |
| `manager/app/src/main/java/nd/max/ui/viewmodel/LogsViewerViewModel.kt` | ZKM (Zuan Kernel Manager) | ترويسة ملكية بلا ذكر أصل خارجي؛ يُذكر في المتن: ZKM (Zuan Kernel Manager) | **VERIFY_BY_DIFF_OR_REWRITE** |
| `manager/app/src/main/java/nd/max/ui/viewmodel/NetworkSchedulerViewModel.kt` | ZKM (Zuan Kernel Manager) | ترويسة ملكية بلا ذكر أصل خارجي؛ يُذكر في المتن: ZKM (Zuan Kernel Manager) | **VERIFY_BY_DIFF_OR_REWRITE** |

## ج) ملفات بلا أصل خارجي مُعلَن

العدد: **1562** ملفًا (موارد، أيقونات، خطوط، بيانات، ومصادر بترويسة ملكية داخلية بلا ذكر أصل خارجي). وتفصيلها الكامل في `build/license-report.json`.

## د) التبعيات الخارجية

### Gradle

| COORDINATE | SCOPE | SHIPPED | LICENSE |
| --- | --- | --- | --- |
| `androidx.activity:activity-compose:1.11.0` | implementation | نعم | Apache-2.0 |
| `androidx.appcompat:appcompat:1.8.0` | implementation | نعم | Apache-2.0 |
| `androidx.compose.animation:animation` | implementation | نعم | Apache-2.0 |
| `androidx.compose.animation:animation-core` | implementation | نعم | Apache-2.0 |
| `androidx.compose.material3:material3:1.4.0` | implementation | نعم | Apache-2.0 |
| `androidx.compose.material:material` | implementation | نعم | Apache-2.0 |
| `androidx.compose.material:material-icons-extended` | implementation | نعم | Apache-2.0 |
| `androidx.compose.ui:ui` | implementation | نعم | Apache-2.0 |
| `androidx.compose.ui:ui-text-google-fonts` | implementation | نعم | Apache-2.0 |
| `androidx.compose.ui:ui-tooling-preview` | implementation | نعم | Apache-2.0 |
| `androidx.documentfile:documentfile:1.1.0` | implementation | نعم | Apache-2.0 |
| `androidx.hilt:hilt-navigation-compose:1.2.0` | implementation | نعم | Apache-2.0 |
| `androidx.lifecycle:lifecycle-runtime-compose:2.9.4` | implementation | نعم | Apache-2.0 |
| `androidx.lifecycle:lifecycle-service:2.9.4` | implementation | نعم | Apache-2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4` | implementation | نعم | Apache-2.0 |
| `androidx.media3:media3-exoplayer:1.3.0` | implementation | نعم | Apache-2.0 |
| `androidx.media3:media3-ui:1.3.0` | implementation | نعم | Apache-2.0 |
| `androidx.navigation:navigation-compose:2.9.4` | implementation | نعم | Apache-2.0 |
| `androidx.transition:transition:1.7.1` | implementation | نعم | Apache-2.0 |
| `com.github.Fox2Code.AndroidANSI:library-ktx:1.2.1` | implementation | نعم | MIT |
| `com.github.Fox2Code.AndroidANSI:library:1.2.1` | implementation | نعم | MIT |
| `com.github.jeziellago:compose-markdown:0.7.2` | implementation | نعم | MIT |
| `com.github.topjohnwu.libsu:core:6.0.0` | implementation | نعم | Apache-2.0 |
| `com.github.topjohnwu.libsu:io:6.0.0` | implementation | نعم | Apache-2.0 |
| `com.github.topjohnwu.libsu:service:6.0.0` | implementation | نعم | Apache-2.0 |
| `com.github.yalantis:ucrop:2.2.11-native` | implementation | نعم | Apache-2.0 |
| `com.google.dagger:hilt-android:2.59.2` | implementation | نعم | Apache-2.0 |
| `com.google.dagger:hilt-compiler:2.59.2` | ksp | نعم | Apache-2.0 |
| `com.materialkolor:material-kolor:5.0.0-alpha07` | implementation | نعم | MIT |
| `dev.chrisbanes.haze:haze-android:2.0.0-alpha02` | implementation | نعم | Apache-2.0 |
| `dev.chrisbanes.haze:haze-blur-materials:2.0.0-alpha02` | implementation | نعم | Apache-2.0 |
| `dev.chrisbanes.haze:haze-blur:2.0.0-alpha02` | implementation | نعم | Apache-2.0 |
| `dev.rikka.shizuku:api:13.1.5` | implementation | نعم | Apache-2.0 |
| `dev.rikka.shizuku:provider:13.1.5` | implementation | نعم | Apache-2.0 |
| `io.coil-kt:coil-compose:2.7.0` | implementation | نعم | Apache-2.0 |
| `io.coil-kt:coil-gif:2.7.0` | implementation | نعم | Apache-2.0 |
| `junit:junit:4.13.2` | testImplementation | لا (اختبار) | EPL-1.0 |
| `me.zhanghai.android.appiconloader:appiconloader-coil:1.5.0` | implementation | نعم | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0` | implementation | نعم | Apache-2.0 |
| `org.json:json:20260814` | testImplementation | لا (اختبار) | Public-Domain |
| `org.lsposed.hiddenapibypass:hiddenapibypass:6.1` | implementation | نعم | Apache-2.0 |
| `platform` | implementation | نعم | Unknown |

### Cargo

| CRATE | VERSION | LOCAL | LICENSE |
| --- | --- | --- | --- |
| `android_system_properties` | 0.1.5 | لا | MIT OR Apache-2.0 |
| `autocfg` | 1.5.0 | لا | MIT OR Apache-2.0 |
| `bincode` | 2.0.1 | لا | MIT OR Apache-2.0 |
| `bincode_derive` | 2.0.1 | لا | MIT OR Apache-2.0 |
| `bitflags` | 2.10.0 | لا | MIT OR Apache-2.0 |
| `bumpalo` | 3.19.0 | لا | MIT OR Apache-2.0 |
| `bytes` | 1.12.1 | لا | MIT OR Apache-2.0 |
| `cc` | 1.2.43 | لا | MIT OR Apache-2.0 |
| `cesu8` | 1.1.0 | لا | MIT OR Apache-2.0 |
| `cfg-if` | 1.0.4 | لا | MIT OR Apache-2.0 |
| `cfg_aliases` | 0.2.1 | لا | MIT OR Apache-2.0 |
| `chrono` | 0.4.42 | لا | MIT OR Apache-2.0 |
| `combine` | 4.6.8 | لا | MIT OR Apache-2.0 |
| `core-foundation-sys` | 0.8.7 | لا | MIT OR Apache-2.0 |
| `crc32fast` | 1.5.2 | لا | MIT OR Apache-2.0 |
| `equivalent` | 1.0.2 | لا | MIT OR Apache-2.0 |
| `find-msvc-tools` | 0.1.4 | لا | MIT OR Apache-2.0 |
| `flate2` | 1.1.10 | لا | MIT OR Apache-2.0 |
| `futures-core` | 0.3.31 | لا | MIT OR Apache-2.0 |
| `glob` | 0.3.3 | لا | MIT OR Apache-2.0 |
| `hashbrown` | 0.17.1 | لا | MIT OR Apache-2.0 |
| `iana-time-zone` | 0.1.64 | لا | MIT OR Apache-2.0 |
| `iana-time-zone-haiku` | 0.1.2 | لا | MIT OR Apache-2.0 |
| `indexmap` | 2.14.2 | لا | MIT OR Apache-2.0 |
| `inotify` | 0.11.0 | لا | MIT OR Apache-2.0 |
| `inotify-sys` | 0.1.5 | لا | MIT OR Apache-2.0 |
| `itoa` | 1.0.15 | لا | MIT OR Apache-2.0 |
| `jni` | 0.21.1 | لا | MIT OR Apache-2.0 |
| `jni-sys` | 0.4.1 | لا | MIT OR Apache-2.0 |
| `jni-sys-macros` | 0.4.1 | لا | MIT OR Apache-2.0 |
| `js-sys` | 0.3.82 | لا | MIT OR Apache-2.0 |
| `lazy_static` | 1.5.0 | لا | MIT OR Apache-2.0 |
| `libc` | 0.2.177 | لا | MIT OR Apache-2.0 |
| `log` | 0.4.28 | لا | MIT OR Apache-2.0 |
| `matrixmultiply` | 0.3.11 | لا | MIT OR Apache-2.0 |
| `maxmanager-profilesettings` | 1.0.0 | نعم | Apache-2.0 |
| `maxmanager-utilityconf` | 1.0.0 | نعم | Apache-2.0 |
| `maxmanager_native` | 0.1.0 | نعم | Apache-2.0 |
| `memchr` | 2.7.6 | لا | MIT OR Apache-2.0 |
| `mio` | 1.1.0 | لا | MIT OR Apache-2.0 |
| `ndarray` | 0.15.6 | لا | MIT OR Apache-2.0 |
| `nix` | 0.30.1 | لا | MIT OR Apache-2.0 |
| `ntapi` | 0.4.1 | لا | MIT OR Apache-2.0 |
| `num-complex` | 0.4.6 | لا | MIT OR Apache-2.0 |
| `num-integer` | 0.1.47 | لا | MIT OR Apache-2.0 |
| `num-traits` | 0.2.19 | لا | MIT OR Apache-2.0 |
| `objc2-core-foundation` | 0.3.2 | لا | MIT OR Apache-2.0 |
| `objc2-io-kit` | 0.3.2 | لا | MIT OR Apache-2.0 |
| `once_cell` | 1.21.3 | لا | MIT OR Apache-2.0 |
| `pin-project-lite` | 0.2.16 | لا | MIT OR Apache-2.0 |
| `proc-macro2` | 1.0.103 | لا | MIT OR Apache-2.0 |
| `quote` | 1.0.41 | لا | MIT OR Apache-2.0 |
| `rawpointer` | 0.2.1 | لا | MIT OR Apache-2.0 |
| `rianixia-thermalcore` | 2.0.0 | نعم | Apache-2.0 |
| `rustversion` | 1.0.22 | لا | MIT OR Apache-2.0 |
| `ryu` | 1.0.20 | لا | MIT OR Apache-2.0 |
| `same-file` | 1.0.6 | لا | MIT OR Apache-2.0 |
| `serde` | 1.0.228 | لا | MIT OR Apache-2.0 |
| `serde_core` | 1.0.228 | لا | MIT OR Apache-2.0 |
| `serde_derive` | 1.0.228 | لا | MIT OR Apache-2.0 |
| `serde_json` | 1.0.145 | لا | MIT OR Apache-2.0 |
| `shlex` | 1.3.0 | لا | MIT OR Apache-2.0 |
| `signal-hook` | 0.3.18 | لا | MIT OR Apache-2.0 |
| `signal-hook-registry` | 1.4.6 | لا | MIT OR Apache-2.0 |
| `socket2` | 0.6.1 | لا | MIT OR Apache-2.0 |
| `syn` | 2.0.108 | لا | MIT OR Apache-2.0 |
| `sysinfo` | 0.37.2 | لا | MIT OR Apache-2.0 |
| `thiserror` | 1.0.69 | لا | MIT OR Apache-2.0 |
| `thiserror-impl` | 1.0.69 | لا | MIT OR Apache-2.0 |
| `tokio` | 1.48.0 | لا | MIT OR Apache-2.0 |
| `typed-path` | 0.12.3 | لا | MIT OR Apache-2.0 |
| `unicode-ident` | 1.0.22 | لا | MIT OR Apache-2.0 |
| `unty` | 0.0.4 | لا | MIT OR Apache-2.0 |
| `virtue` | 0.0.18 | لا | MIT OR Apache-2.0 |
| `walkdir` | 2.5.0 | لا | MIT OR Apache-2.0 |
| `wasi` | 0.11.1+wasi-snapshot-preview1 | لا | MIT OR Apache-2.0 |
| `wasm-bindgen` | 0.2.105 | لا | MIT OR Apache-2.0 |
| `wasm-bindgen-macro` | 0.2.105 | لا | MIT OR Apache-2.0 |
| `wasm-bindgen-macro-support` | 0.2.105 | لا | MIT OR Apache-2.0 |
| `wasm-bindgen-shared` | 0.2.105 | لا | MIT OR Apache-2.0 |
| `winapi` | 0.3.9 | لا | MIT OR Apache-2.0 |
| `winapi-i686-pc-windows-gnu` | 0.4.0 | لا | MIT OR Apache-2.0 |
| `winapi-util` | 0.1.11 | لا | MIT OR Apache-2.0 |
| `winapi-x86_64-pc-windows-gnu` | 0.4.0 | لا | MIT OR Apache-2.0 |
| `windows` | 0.61.3 | لا | MIT OR Apache-2.0 |
| `windows-collections` | 0.2.0 | لا | MIT OR Apache-2.0 |
| `windows-core` | 0.62.2 | لا | MIT OR Apache-2.0 |
| `windows-future` | 0.2.1 | لا | MIT OR Apache-2.0 |
| `windows-implement` | 0.60.2 | لا | MIT OR Apache-2.0 |
| `windows-interface` | 0.59.3 | لا | MIT OR Apache-2.0 |
| `windows-link` | 0.2.1 | لا | MIT OR Apache-2.0 |
| `windows-numerics` | 0.2.0 | لا | MIT OR Apache-2.0 |
| `windows-result` | 0.4.1 | لا | MIT OR Apache-2.0 |
| `windows-strings` | 0.5.1 | لا | MIT OR Apache-2.0 |
| `windows-sys` | 0.61.2 | لا | MIT OR Apache-2.0 |
| `windows-targets` | 0.53.5 | لا | MIT OR Apache-2.0 |
| `windows-threading` | 0.1.0 | لا | MIT OR Apache-2.0 |
| `windows_aarch64_gnullvm` | 0.53.1 | لا | MIT OR Apache-2.0 |
| `windows_aarch64_msvc` | 0.53.1 | لا | MIT OR Apache-2.0 |
| `windows_i686_gnu` | 0.53.1 | لا | MIT OR Apache-2.0 |
| `windows_i686_gnullvm` | 0.53.1 | لا | MIT OR Apache-2.0 |
| `windows_i686_msvc` | 0.53.1 | لا | MIT OR Apache-2.0 |
| `windows_x86_64_gnu` | 0.53.1 | لا | MIT OR Apache-2.0 |
| `windows_x86_64_gnullvm` | 0.53.1 | لا | MIT OR Apache-2.0 |
| `windows_x86_64_msvc` | 0.53.1 | لا | MIT OR Apache-2.0 |
| `zip` | 8.6.0 | لا | MIT OR Apache-2.0 |
| `zlib-rs` | 0.6.8 | لا | Zlib |
| `zmij` | 1.0.23 | لا | MIT OR Apache-2.0 |

## هـ) الثنائيات ومعمارياتها

| FILE | ARCH | ORIGIN | LICENSE | ACTION |
| --- | --- | --- | --- | --- |
| `manager/app/azenith.jks` | — | MaxManager (build output) | Apache-2.0 | **REMOVE_FROM_TRACKING** |
| `manager/gradle/wrapper/gradle-wrapper.jar` | — | MaxManager app (AZenith base) | Apache-2.0 | **ATTRIBUTE** |

## و) حدود هذا التدقيق

* الحكم مبنيّ على **ما هو مُعلَن في الترويسة**، لا على تشابه دلالي يُقاس بالـdiff. فملف بلا ترويسة قد يكون مُشتقًّا وهو غير معروف — وهذا احتمال يُدار بالمراجعة البشرية لا يُدَّعى نفيه. ولذلك تُفصل حالة `GPL_REFERENCED` عن `GPL_DERIVED`: الأولى **دعوى استقلال** لم تُختبر بعد، والثانية **إقرار بأصل**.
* الاسم في الترويسة يُحتسب أصلًا **فقط** إذا جاء في سطر يحمل سياق نسبة (مأخوذ · مبني على · حقوق · رخصة). بلا هذا الشرط كانت ثوابت مسارات مثل `/data/data/com.termux/files/usr/bin` تُصنّف ملفاتها «مشتقّة من Termux» — وهي إيجابية كاذبة أُزيلت بقياس لا بتقدير.
* `docs/` و`tools/` تُصنَّفان بعائلة وحدتهما ولا يُستنتج أصلهما من أسماء مشاريع تُذكر فيهما وصفًا؛ فلا يظهر نثر الأدوات «مشتقًّا» من كل من يُسمّى فيه.
* ما لا أصل خارجي له ولا عائلة وحدة معروفة يُصنَّف **افتراضًا مُعلَنًا**: رخصة المستودع، بحالة `REPO_DEFAULT`. وهذا افتراض عن نطاق المشروع لا قياس — والبيانات العبأة (`devices.db` · `socs.json` · `maxmanagerApplist.json`) **مُستثناة** منه وتبقى `Unknown` حتى يُكتب إسنادها.
* لا تصل الأداة إلى الشبكة: التراخيص من جدول مُنتقى بسند، وما ليس فيه يُكتب `Unknown`.
* جدول `DEP_LICENSES` يغطّي التبعيات المستعملة اليوم؛ وإضافة تبعية جديدة بدون سطر له تظهر `Unknown` لا `Apache-2.0`.

## ز) جدول الملفات المصدرية الكامل

| FILE | ORIGIN | LICENSE | STATUS | ACTION |
| --- | --- | --- | --- | --- |
| `.github/scripts/changelog.sh` | MaxManager CI | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `.github/scripts/compile_zip.sh` | MaxManager CI | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `.github/scripts/generatesha256.sh` | MaxManager CI | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `.github/scripts/telebot.sh` | MaxManager CI | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `.github/scripts/verify.sh` | MaxManager CI | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `android/aosp/Android.bp` | MaxManager platform integration | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `android/aosp/BoardConfig.mk` | MaxManager platform integration | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `android/aosp/maxmanager.rc` | MaxManager platform integration | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `android/aosp/sepolicy/maxmanager.te` | MaxManager platform integration | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `android/kernelsu/action.sh` | MaxManager platform integration | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `android/kernelsu/customize.sh` | MaxManager platform integration | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `android/kernelsu/service.sh` | MaxManager platform integration | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `android/kernelsu/uninstall.sh` | MaxManager platform integration | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `archdaemon/jni/Android.mk` | Encore Daemon (via AZenith) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `archdaemon/jni/Application.mk` | Encore Daemon (via AZenith) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `archdaemon/jni/Main.c` | Encore Daemon (via AZenith) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `archdaemon/jni/include/AZenith.h` | Encore Daemon (via AZenith) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `archdaemon/jni/src/AppLoader/AppLoader.c` | Encore Daemon (via AZenith) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `archdaemon/jni/src/AppLoader/StatusMonitor.c` | Encore Daemon (via AZenith) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `archdaemon/jni/src/AppLoader/VisibleApps.c` | Encore Daemon (via AZenith) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `archdaemon/jni/src/BinaryCLI/BinaryCLI.c` | Encore Daemon (via AZenith) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `archdaemon/jni/src/BinaryCLI/BypassCompatibility.c` | Encore Daemon (via AZenith) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `archdaemon/jni/src/BinaryCLI/CLIUtility.c` | Encore Daemon (via AZenith) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `archdaemon/jni/src/BypassCharge/ChargingNodes.c` | Encore Daemon (via AZenith) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `archdaemon/jni/src/BypassCharge/ChargingUtility.c` | Encore Daemon (via AZenith) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `archdaemon/jni/src/ConfigHandler/RefreshRateHandler.c` | Encore Tweaks | Apache-2.0 | APACHE_DERIVED | REWRITE_FOR_IDENTITY |
| `archdaemon/jni/src/ConfigHandler/RenderingHandler.c` | Encore Tweaks | Apache-2.0 | APACHE_DERIVED | REWRITE_FOR_IDENTITY |
| `archdaemon/jni/src/ConfigHandler/ResolutionChanger.c` | Encore Daemon (via AZenith) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `archdaemon/jni/src/ConfigHandler/StateHandler.c` | Encore Daemon (via AZenith) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `archdaemon/jni/src/FileUtility/FileHandler.c` | Encore Tweaks | Apache-2.0 | APACHE_DERIVED | REWRITE_FOR_IDENTITY |
| `archdaemon/jni/src/FileUtility/LockFile.c` | Encore Tweaks | Apache-2.0 | APACHE_DERIVED | REWRITE_FOR_IDENTITY |
| `archdaemon/jni/src/GamePreload/GamePreload.c` | Encore Daemon (via AZenith) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `archdaemon/jni/src/InotifyHandler/InotifyWatcher.c` | Encore Daemon (via AZenith) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `archdaemon/jni/src/MaxManagerUtility/AppPriority.c` | Encore Tweaks | Apache-2.0 | APACHE_DERIVED | REWRITE_FOR_IDENTITY |
| `archdaemon/jni/src/MaxManagerUtility/DaemonUtility.c` | Encore Tweaks | Apache-2.0 | APACHE_DERIVED | REWRITE_FOR_IDENTITY |
| `archdaemon/jni/src/MaxManagerUtility/ModuleIntegrity.c` | Encore Tweaks | Apache-2.0 | APACHE_DERIVED | REWRITE_FOR_IDENTITY |
| `archdaemon/jni/src/PidTracker/PidTracker.c` | Encore Tweaks | Apache-2.0 | APACHE_DERIVED | REWRITE_FOR_IDENTITY |
| `archdaemon/jni/src/ShellUtility/ExecuteCommand.c` | Encore Tweaks | Apache-2.0 | APACHE_DERIVED | REWRITE_FOR_IDENTITY |
| `archdaemon/jni/src/ShellUtility/ExecuteDirect.c` | Encore Tweaks | Apache-2.0 | APACHE_DERIVED | REWRITE_FOR_IDENTITY |
| `archdaemon/jni/src/ShellUtility/SystemvUtility.c` | Encore Tweaks | Apache-2.0 | APACHE_DERIVED | REWRITE_FOR_IDENTITY |
| `archdaemon/jni/src/StartupInit/ConfigLoader.c` | Encore Tweaks | Apache-2.0 | APACHE_DERIVED | REWRITE_FOR_IDENTITY |
| `archdaemon/jni/src/StartupInit/DaemonContext.c` | Encore Tweaks | Apache-2.0 | APACHE_DERIVED | REWRITE_FOR_IDENTITY |
| `archdaemon/jni/src/StartupInit/DaemonStartup.c` | Encore Daemon (via AZenith) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `archdaemon/jni/src/StartupInit/PropValidator.c` | Encore Daemon (via AZenith) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `archdaemon/jni/src/System/System.c` | Encore Daemon (via AZenith) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `archdaemon/jni/src/SystemLogger/SystemLogger.c` | Encore Tweaks | Apache-2.0 | APACHE_DERIVED | REWRITE_FOR_IDENTITY |
| `archdaemon/jni/src/SystemProfile/PerAppKernel.c` | Encore Daemon (via AZenith) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `archdaemon/jni/src/SystemProfile/PerAppThermal.c` | Encore Daemon (via AZenith) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `archdaemon/jni/src/SystemProfile/ProfileUtility.c` | Encore Daemon (via AZenith) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `archdaemon/jni/src/SystemProfile/SystemProfiles.c` | Encore Daemon (via AZenith) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `binprofiles/src/chipsets/exynos.rs` | MaxManager binprofiles | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `binprofiles/src/chipsets/mediatek.rs` | MaxManager binprofiles | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `binprofiles/src/chipsets/mod.rs` | MaxManager binprofiles | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `binprofiles/src/chipsets/snapdragon.rs` | MaxManager binprofiles | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `binprofiles/src/chipsets/tensor.rs` | MaxManager binprofiles | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `binprofiles/src/chipsets/unisoc.rs` | MaxManager binprofiles | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `binprofiles/src/main.rs` | MaxManager binprofiles | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `binprofiles/src/profiles/mod.rs` | MaxManager binprofiles | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `binprofiles/src/props.rs` | MaxManager binprofiles | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `binprofiles/src/utils/mod.rs` | MaxManager binprofiles | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `binutils/src/main.rs` | MaxManager binutils | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `binutils/src/utils/logger.rs` | MaxManager binutils | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `binutils/src/utils/mod.rs` | MaxManager binutils | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `mainfiles/action.sh` | MaxManager module (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `mainfiles/customize.sh` | MaxManager module (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `mainfiles/post-fs-data.sh` | MaxManager module (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `mainfiles/preferenced-tweaks.sh` | MaxManager module (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `mainfiles/props.sh` | MaxManager module (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `mainfiles/service.sh` | MaxManager module (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `mainfiles/uninstall.sh` | MaxManager module (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `mainfiles/verify.sh` | Encore Tweaks | Apache-2.0 | APACHE_DERIVED | REWRITE_FOR_IDENTITY |
| `manager/app/build.gradle.kts` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/proguard-rules.pro` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/debug/java/nd/max/ui/component/StudioPreviews.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/aidl/nd/max/IMtkService.aidl` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | REWRITE_OR_REMOVE |
| `manager/app/src/main/assets/devices.db` | UNATTRIBUTED DATA ASSET | Unknown | DATA_ASSET_UNVERIFIED | VERIFY_OR_REPLACE |
| `manager/app/src/main/assets/socs.json` | UNATTRIBUTED DATA ASSET | Unknown | DATA_ASSET_UNVERIFIED | VERIFY_OR_REPLACE |
| `manager/app/src/main/java/nd/max/AppMonitor.kt` | Encore Tweaks | Apache-2.0 | APACHE_DERIVED | REWRITE_FOR_IDENTITY |
| `manager/app/src/main/java/nd/max/AppMonitorLogger.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/MainActivity.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/MaxManagerApplication.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/MaxManagerPaths.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/MaxManagerProps.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/PerAppRefreshRateController.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/RefreshRate.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/TileService/BypassChgTileService.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/TileService/ProfileTileService.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/XiaomiVendorFeatures.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/atlas/AtlasBackendProvider.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/atlas/AtlasCapabilityMap.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/atlas/AtlasCatalog.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/atlas/AtlasCommunityBank.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/atlas/AtlasControlIntent.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/atlas/AtlasDeviceIdentity.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/atlas/AtlasDeviceProfile.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/atlas/AtlasDiscovery.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/atlas/AtlasEvidenceStore.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/atlas/AtlasFailureLedger.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/atlas/AtlasFileReadTransport.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/atlas/AtlasFileStoreIo.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/atlas/AtlasFixture.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/atlas/AtlasFixtureRecorder.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/atlas/AtlasFreshness.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/atlas/AtlasModels.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/atlas/AtlasPlatformProvider.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/atlas/AtlasProbeSchedule.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/atlas/AtlasRepository.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/atlas/AtlasResolver.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/atlas/AtlasRoutePlanner.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/atlas/AtlasSafetyPolicy.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/atlas/MaxAtlas.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/di/AppModule.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/di/DataModule.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/diagnostics/AtlasDoctor.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/diagnostics/AtlasReportExporter.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/diagnostics/AtlasSupportReport.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/diagnostics/DeviceBlueprint.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/diagnostics/DiagnosticCenter.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/diagnostics/HardwareRouteHealth.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/diagnostics/LogCodeGlossary.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/diagnostics/LogDiagnosticReport.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/diagnostics/LogEventLine.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/diagnostics/LogHeader.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/diagnostics/LogSettingsDigest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/AdaptiveProfileEngine.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/AtlasAdapters.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/AtlasAdaptiveExecutor.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/AtlasDiscoveredControl.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/AtlasGpuAdapters.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/AtlasPrivilegedReadTransport.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/AtlasRouteMemory.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/AtlasRouteMemoryFactory.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/ChargingHardwareBackend.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/ContextData.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/ControlOwnership.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/CpuHardwareBackend.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/DeviceStateCollector.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/DeviceWriteRecording.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/DriftGuard.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/GpuCeilingPolicy.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/GpuHardwareBackend.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/GpuTweakPersistence.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/HardwareCapability.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/HardwareCapabilityResolver.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/HardwareControlArbiter.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/HardwareControlKey.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/HardwareDataSource.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/HardwareRepairExecutor.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/HardwareRepairModels.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/HardwareRuntime.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/HardwareVerification.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/ManualControlLocks.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/MemoryPressureReader.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/MtkGpuFixedIndex.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/MtkGpuOppTable.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/PerAppControlRegistry.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/PerAppFrequencyController.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/PerAppHardwareStatus.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/PerAppRecoveryStore.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/PlatformCeilingAuthority.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/PredictiveSafety.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/ProfileApplier.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/ReadOnlyProbeAccess.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/RootFileAccess.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/RouteEvidenceFacts.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/SharedHardwareOwnershipStore.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/ThermalCeilingRouter.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/ThermalCurve.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/ThermalGuard.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/VerifiedControl.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/WriteVerification.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/hardware/ZramHardwareBackend.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/jni/ArchiveBridge.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/jni/ArchivePacket.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/jni/ContextBridge.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/jni/PredictorBridge.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/jni/ProbeBridge.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/jni/ProbePacket.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/jni/PropBridge.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/jni/ScanBridge.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/jni/ScanPacket.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/maxai/CoalescingCycleRunner.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/maxai/ControlOutcomeModel.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/maxai/ControlPlane.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/maxai/ControlRegistry.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/maxai/CpuCeilingKnobs.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/maxai/CredibilityStore.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/maxai/DynamicIntentLearner.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/maxai/MaxAiCadence.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/maxai/MaxAiEngine.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/maxai/MaxAiEpisode.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/maxai/MaxAiInsights.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/maxai/MaxAiJournal.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/maxai/MaxAiJournalCodec.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/maxai/MaxAiModels.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/maxai/MaxAiThermalBudget.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/maxai/MaxAiThermalCurve.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/maxai/MemoryStall.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/maxai/MinimalPlanner.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/maxai/Objective.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/maxai/ResponseModel.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/maxai/SafetyEngine.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/maxai/SafetyGovernor.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/maxai/TrustModel.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/privilege/PrivilegeLevel.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/privilege/PrivilegeManager.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/privilege/ShizukuGateway.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/recommendation/RecommendationModels.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/recommendation/RecommendationTextClassifier.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/core/threading/DispatcherProvider.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/data/datasources/HardwareDataSourceImpl.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/receiver/ZenithReceiver.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/service/FpsOverlayService.kt` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | REWRITE_OR_REMOVE |
| `manager/app/src/main/java/nd/max/service/MtkRootService.kt` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | REWRITE_OR_REMOVE |
| `manager/app/src/main/java/nd/max/service/ProcessOverlayService.kt` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | REWRITE_OR_REMOVE |
| `manager/app/src/main/java/nd/max/ui/activitylauncher/ActivityLauncherViewModel.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/AboutAppsComponent.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/AccessibilitySemantics.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/AdaptiveLayout.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/AmbientGlowCycle.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/AmbientMotifOverlay.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/AppIconComponent.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/BottomSheetComponent.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/CapabilityMatrixCard.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/ConfigBackupFlow.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/ContentStateComponents.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/DecisionHierarchy.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/DialogComponent.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/ExpressiveListComponent.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/FileBottomBars.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/FileDrawerContent.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/FileEditorDialog.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/FileEntryList.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/FilePropertiesDialog.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/FileSearchDialog.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/FileWindowChrome.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/GaugeComponent.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/HomeComponents.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/LiveGraphComponent.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/MaxAiActiveBanner.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/MaxControls.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/MaxDesignSystem.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/MaxFeedback.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/MaxInteractionComponents.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/MaxMotion.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/ModuleBackupComponent.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/NeuralClockWave.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/NeuralDashboardKit.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/PowerCoreCard.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/PrivilegePanel.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/ProfileDialogComponent.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/PulseArt.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/PulseFieldEngine.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/RefreshRateComponent.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/RendererComponent.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/RouteVerdictLabel.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/ScreenChrome.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/SensorInventoryCard.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/SettingsHeaderComponent.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/StudioButtons.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/StudioComponents.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/StudioSectionHeader.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/ThermalPulseCard.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/WarningBanner.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/component/WeatherEffects.kt` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | REWRITE_OR_REMOVE |
| `manager/app/src/main/java/nd/max/ui/design/MaxAiCinematics.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/design/MaxBar.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/design/MaxCommandMenu.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/design/MaxCondition.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/design/MaxContextMenu.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/design/MaxControlRows.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/design/MaxDialogs.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/design/MaxDomainCard.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/design/MaxDrawer.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/design/MaxHelp.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/design/MaxMetric.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/design/MaxProgressStrip.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/design/MaxScreenScaffold.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/design/MaxSearchField.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/design/MaxStructure.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/design/MaxTabbedDialog.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/design/MaxTokens.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/design/MaxViewMenu.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/mainscreens/ActivityCardSettings.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/mainscreens/ApplistScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/mainscreens/AtlasDiagnosticsSection.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/mainscreens/ControlLayoutModel.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/mainscreens/ControlScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/mainscreens/DashboardDetailScreens.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/mainscreens/DiagnosticsScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/mainscreens/GetStartedScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/mainscreens/HomeDashboardComponents.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/mainscreens/HomeFormat.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/mainscreens/HomeScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/mainscreens/LegacyTweakComponents.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/mainscreens/LegendaryHomeDashboard.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/mainscreens/MaxAiPresentation.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/mainscreens/MaxAiRuntimeStatus.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/mainscreens/MaxAiScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/mainscreens/MaxLiveScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/mainscreens/SettingsScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/mainscreens/StoryboardHome.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/mainscreens/StoryboardModel.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/mainscreens/UnifiedActivityModel.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/navigation/LaunchRoutes.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/navigation/MaxDestinationCatalog.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/navigation/MaxDestinations.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/navigation/MaxNavActions.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/navigation/MaxNavBar.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/navigation/MaxNavGraph.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/process/MyLifecycleOwner.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/settings/AppLanguage.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/settings/AppLanguageSheet.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/settings/SettingsPreference.kt` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | REWRITE_OR_REMOVE |
| `manager/app/src/main/java/nd/max/ui/settings/SettingsViewModel.kt` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | REWRITE_OR_REMOVE |
| `manager/app/src/main/java/nd/max/ui/subscreens/AboutScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/subscreens/AppSettingsScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/subscreens/BypassChargeScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/subscreens/BypassCheckScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/subscreens/ChargingScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/subscreens/ColorSchemeScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/subscreens/ConfigBackupScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/subscreens/CpuCoreControlScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | GPL_REFERENCED | VERIFY_BY_DIFF_OR_REWRITE |
| `manager/app/src/main/java/nd/max/ui/subscreens/CustomThemeScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/subscreens/DebloatFreezeScreen.kt` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | REWRITE_OR_REMOVE |
| `manager/app/src/main/java/nd/max/ui/subscreens/Dex2oatScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/subscreens/DisplayStudioScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/subscreens/DozeModeScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/subscreens/FasSettingsScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/subscreens/FileManagerActions.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/subscreens/FileManagerCommands.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/subscreens/FileManagerDialogs.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/subscreens/FileManagerPanes.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/subscreens/FileManagerPersistence.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/subscreens/FileManagerScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/subscreens/FileManagerState.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/subscreens/FpsGoSettingsScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/subscreens/FpsOverlayScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/subscreens/GovSettingsScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/subscreens/GpuStudioScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/subscreens/LogsViewerScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | GPL_REFERENCED | VERIFY_BY_DIFF_OR_REWRITE |
| `manager/app/src/main/java/nd/max/ui/subscreens/LogsViewerSections.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/subscreens/MaxBackupHubScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/subscreens/MaxBackupPickerScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/subscreens/MaxBackupRecentItem.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/subscreens/MaxBackupScheduleSection.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/subscreens/MaxBackupScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/subscreens/MaxBackupSystemSection.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/subscreens/ModuleHealthScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/subscreens/NetworkSchedulerScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/subscreens/PermissionsScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/subscreens/PluginsScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/subscreens/PreferencedTweakScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/subscreens/PrivilegeScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/subscreens/ProcessManagerScreen.kt` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | REWRITE_OR_REMOVE |
| `manager/app/src/main/java/nd/max/ui/subscreens/ResolutionScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/subscreens/SetEditScreen.kt` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | REWRITE_OR_REMOVE |
| `manager/app/src/main/java/nd/max/ui/subscreens/StorageDetailScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/subscreens/ThermalDetailScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/subscreens/TouchBoostScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/subscreens/ZramManagerScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/subscreens/hubs/MaxDomainHubScreen.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/theme/Fonts.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/theme/MaxTypography.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/theme/Shape.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/theme/Theme.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/theme/ThemeMode.kt` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | REWRITE_OR_REMOVE |
| `manager/app/src/main/java/nd/max/ui/theme/Type.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/ActivityCardPreferences.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/ApkInspector.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/AppConfigUtil.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/AppOpsBatch.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/AppOpsUtil.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/AppVersionUtil.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/BackgroundGovernanceUtil.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/BackupManagerUtil.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/BannerImageUtil.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/BatteryHealthUtil.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/BootHistoryUtil.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/ChargeLedger.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/ClockMeterModel.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/ClockWaveModel.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/ConfigBackupInventory.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/CpuTopologyUtil.kt` | MaxManager app (AZenith base) | Apache-2.0 | GPL_REFERENCED | VERIFY_BY_DIFF_OR_REWRITE |
| `manager/app/src/main/java/nd/max/ui/util/CrashLogUtil.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/DebloatFreezeUtil.kt` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | REWRITE_OR_REMOVE |
| `manager/app/src/main/java/nd/max/ui/util/DeviceNameUtil.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/Dex2oatUtil.kt` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | REWRITE_OR_REMOVE |
| `manager/app/src/main/java/nd/max/ui/util/DisplayUtil.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/DozeModeUtil.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/EventLog.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/FileActionModel.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/FileArchiveEngine.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/FileBookmarkModel.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/FileClipboardModel.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/FileConflictModel.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/FileOpenModel.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/FileOperationRunner.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/FilePermissionModel.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/FilePermissionOps.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/FileSearchEngine.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/FileSearchFilters.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/FileSearchPlan.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/FileStore.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/FileSystemEngine.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/FileSystemModel.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/FileTaskModel.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/FileWindowModel.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/FpsMonitorUtil.kt` | vtools | GPL-3.0-only | GPL_DERIVED | REWRITE_OR_REMOVE |
| `manager/app/src/main/java/nd/max/ui/util/FpsOverlayPrefs.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/HardwareUtil.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/HomeActivityModel.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/InstallSourceUtil.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/LoadHistory.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/LogUtil.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/MainThreadStallDetector.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/MaxBackupCounts.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/MaxBackupEngine.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/MaxBackupFavorites.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/MaxBackupFolders.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/MaxBackupModel.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/MaxBackupRetention.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/MaxBackupSchedule.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/MaxBackupScheduler.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/MaxBackupStorage.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/MaxBackupSystemEngine.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/MaxBackupSystemModel.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/MaxPrefsBundle.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/MemoryLedger.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/ModuleHealthUtil.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/MtkUtils.kt` | vtools | GPL-3.0-only | GPL_DERIVED | REWRITE_OR_REMOVE |
| `manager/app/src/main/java/nd/max/ui/util/PerAppKernelUtil.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/PermissionPolicy.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/PluginContract.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/PluginDirectory.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/PrivilegedShell.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/ProcessMonitorUtil.kt` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | REWRITE_OR_REMOVE |
| `manager/app/src/main/java/nd/max/ui/util/ProfilePresetStore.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/ProfileSharing.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/PropertyUtil.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/RebootUtil.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/RefreshRatesUtil.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/RootIpcManager.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/RootMount.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/RootUtil.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/SELinuxCheckerUtil.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/SensorInventory.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/SensorMonitorUtil.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/SetEditUtil.kt` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | REWRITE_OR_REMOVE |
| `manager/app/src/main/java/nd/max/ui/util/SettingsVisibilityUtil.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/SpectrumModel.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/StorageHealthUtil.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/StorageScanModel.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/StorageUtil.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/StoryboardSources.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/ThermalModel.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/ThermalUtil.kt` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | REWRITE_OR_REMOVE |
| `manager/app/src/main/java/nd/max/ui/util/VersionIdentity.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/WallpaperUtil.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/XiaomiVendorHalUtil.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/util/ZramPlatformUtil.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/viewmodel/AppSettingsViewmodel.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/viewmodel/ApplistViewmodel.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/viewmodel/AtlasViewModel.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/viewmodel/ChargingViewModel.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/viewmodel/CpuCoreControlViewModel.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/viewmodel/DebloatFreezeViewModel.kt` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | REWRITE_OR_REMOVE |
| `manager/app/src/main/java/nd/max/ui/viewmodel/Dex2oatViewModel.kt` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | REWRITE_OR_REMOVE |
| `manager/app/src/main/java/nd/max/ui/viewmodel/DisplayStudioViewModel.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/viewmodel/DozeModeViewModel.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/viewmodel/GpuStudioViewModel.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/viewmodel/HomeActivityViewModel.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/viewmodel/HomeDashboardViewModel.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/viewmodel/HomeViewmodel.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/viewmodel/LogsLineParser.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/viewmodel/LogsViewerViewModel.kt` | MaxManager app (AZenith base) | Apache-2.0 | GPL_REFERENCED | VERIFY_BY_DIFF_OR_REWRITE |
| `manager/app/src/main/java/nd/max/ui/viewmodel/MaxAiViewModel.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/viewmodel/NetworkSchedulerViewModel.kt` | MaxManager app (AZenith base) | Apache-2.0 | GPL_REFERENCED | VERIFY_BY_DIFF_OR_REWRITE |
| `manager/app/src/main/java/nd/max/ui/viewmodel/ProcessManagerViewModel.kt` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | REWRITE_OR_REMOVE |
| `manager/app/src/main/java/nd/max/ui/viewmodel/ResolutionViewModel.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/viewmodel/SetEditViewModel.kt` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | REWRITE_OR_REMOVE |
| `manager/app/src/main/java/nd/max/ui/viewmodel/SettingViewmodel.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/viewmodel/ThermalDevicesViewModel.kt` | ZKM (Zuan Kernel Manager) | GPL-3.0-only | GPL_DERIVED | REWRITE_OR_REMOVE |
| `manager/app/src/main/java/nd/max/ui/viewmodel/TouchBoostViewModel.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/viewmodel/TweakViewmodel.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/main/java/nd/max/ui/viewmodel/ZramViewModel.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/atlas/AtlasArchitectureTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/atlas/AtlasBackendProviderTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/atlas/AtlasCapabilityMapTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/atlas/AtlasCatalogTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/atlas/AtlasChildScopeTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/atlas/AtlasCommunityBankTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/atlas/AtlasCompletionTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/atlas/AtlasDeviceIdentityTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/atlas/AtlasDeviceProfileTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/atlas/AtlasFailureLedgerTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/atlas/AtlasFileReadTransportTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/atlas/AtlasFixtureRecorderTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/atlas/AtlasFixtureTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/atlas/AtlasFreshnessTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/atlas/AtlasHarnessTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/atlas/AtlasPlatformProviderTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/atlas/AtlasRealReadIntegrationTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/atlas/AtlasResolverTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/atlas/AtlasRoutePlannerTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/atlas/AtlasSafetyPolicyTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/atlas/MaxAtlasTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/atlas/support/AtlasBudgets.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/atlas/support/AtlasCanaries.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/atlas/support/AtlasClock.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/atlas/support/AtlasFakeTransport.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/atlas/support/AtlasSourceGuard.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/diagnostics/AtlasDoctorTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/diagnostics/AtlasSupportReportTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/diagnostics/DiagnosticCenterTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/diagnostics/HardwareRouteHealthTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/diagnostics/LogCodeGlossaryTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/diagnostics/LogDiagnosticReportTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/diagnostics/LogEventLineTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/diagnostics/LogHeaderTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/hardware/AtlasAdapterRegistryTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/hardware/AtlasAdaptiveExecutorTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/hardware/AtlasAdaptiveReadTransportTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/hardware/AtlasDiscoveredControlTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/hardware/AtlasGpuAdaptersTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/hardware/AtlasRouteMemoryTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/hardware/AtlasRouteMemoryWiringTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/hardware/CeilingRaiseTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/hardware/ChargingHardwareBackendTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/hardware/ControlPlaneArchitectureTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/hardware/CpuAvailableFrequencySnapTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/hardware/CpuProvenRangeTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/hardware/GpuCeilingPolicyTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/hardware/GpuControlModelTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/hardware/HardwareControlArbiterTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/hardware/HardwareControlArbiterVerificationTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/hardware/HardwareRepairExecutorTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/hardware/HardwareVerificationTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/hardware/ManualControlLocksTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/hardware/MemoryPressureReaderTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/hardware/PerAppControlRegistryRetargetTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/hardware/PerAppControlRegistryTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/hardware/PerAppHardwareStatusTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/hardware/PlatformCeilingAuthorityTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/hardware/ReadOnlyProbeAccessTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/hardware/RecordedDeviceWriteTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/hardware/RootFileAccessDiscoveryTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/hardware/RootFileAccessNativeMergeTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/hardware/ThermalCeilingRouterTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/hardware/ThermalCurveTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/hardware/ThermalGuardTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/hardware/WriteVerificationTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/jni/ArchivePacketTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/jni/LogPacketTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/jni/ProbePacketTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/jni/PropBridgeTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/jni/ScanPacketTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/maxai/CoalescingCycleRunnerTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/maxai/ControlOutcomeModelTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/maxai/ControlRegistryTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/maxai/CpuCeilingKnobsAtlasTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/maxai/MaxAiCadenceTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/maxai/MaxAiInsightsTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/maxai/MaxAiInterruptSafetyTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/maxai/MaxAiJournalCodecTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/maxai/MaxAiMasterSwitchTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/maxai/MaxAiThermalBudgetTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/maxai/MaxAiThermalCurveTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/maxai/MemoryStallTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/maxai/MinimalPlannerTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/maxai/ObjectiveTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/maxai/ResponseModelTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/core/recommendation/RecommendationTextClassifierTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/design/MaxViewMenuContractTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/design/OutcomeFromCodeNotWordingTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/mainscreens/ApplistPresentationArchitectureTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/mainscreens/ControlLayoutModelTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/mainscreens/MaxAiPresentationTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/mainscreens/MaxAiTimelineFilterTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/mainscreens/MaxLivePresentationArchitectureTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/mainscreens/StoryboardModelTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/mainscreens/UnifiedActivityModelTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/navigation/AtlasReadOnlyEntryTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/navigation/LaunchRouteTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/navigation/MergedDestinationCleanupTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/navigation/SettingsDestinationReachabilityTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/subscreens/DetailScreensLanguageContractTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/ApkDigestsTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/BackgroundGovernanceUtilTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/BootHistoryUtilTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/ChargeLedgerTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/ClockMeterModelTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/ClockWaveTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/ConfigBackupInventoryTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/CrashLogUtilTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/EventLogResultTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/FileActionModelTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/FileArchiveEngineTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/FileBookmarkModelTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/FileClipboardModelTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/FileConflictModelTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/FileOpenPlanTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/FilePermissionModelTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/FilePermissionOpsTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/FileSearchEngineTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/FileSearchFiltersTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/FileSearchPlanTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/FileStoreTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/FileSystemModelTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/FileTaskModelTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/FileWindowModelTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/FpsMonitorParseTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/GpuCeilingChoiceTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/HealthParsersTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/HomeActivityModelTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/InstallSourceUtilTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/LoadHistoryTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/MainThreadStallDetectorTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/MaxBackupCountsTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/MaxBackupFavoritesTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/MaxBackupModelTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/MaxBackupRetentionTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/MaxBackupSchedulePlanTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/MaxBackupStorageTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/MaxBackupSystemPermissionsTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/MaxBackupSystemTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/MemoryLedgerTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/ModuleHealthUtilTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/PermissionPolicyTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/PluginContractTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/ProfileSharingTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/RootMountTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/SensorInventoryTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/SetEditUtilTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/SpectrumModelTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/StorageScanModelTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/StorageScanNativeMappingTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/ThermalModelTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/ThermalZoneBatchTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/VersionIdentityTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/util/ZramPlatformUtilTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/viewmodel/AtlasPresentationTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/viewmodel/HomeTemperaturePolicyTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/viewmodel/LogsLineParserTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/viewmodel/MaxAiPresentationArchitectureTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/app/src/test/java/nd/max/ui/viewmodel/ViewModelInstantiationTest.kt` | MaxManager app (AZenith base) | Apache-2.0 | NO_HEADER | ADD_COPYRIGHT_HEADER |
| `manager/build.gradle.kts` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/settings.gradle.kts` | MaxManager app (AZenith base) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/src/main/rust/src/archive.rs` | MaxManager (native engine) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/src/main/rust/src/contextual_engine.rs` | MaxManager (native engine) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/src/main/rust/src/digital_twin.rs` | MaxManager (native engine) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/src/main/rust/src/lib.rs` | MaxManager (native engine) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/src/main/rust/src/logparse.rs` | MaxManager (native engine) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/src/main/rust/src/power_predictor.rs` | MaxManager (native engine) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/src/main/rust/src/probe.rs` | MaxManager (native engine) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/src/main/rust/src/rl_agent.rs` | MaxManager (native engine) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/src/main/rust/src/scan.rs` | MaxManager (native engine) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `manager/src/main/rust/src/sysprop.rs` | MaxManager (native engine) | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `maxmanagerApplist.json` | UNATTRIBUTED DATA ASSET | Unknown | DATA_ASSET_UNVERIFIED | VERIFY_OR_REPLACE |
| `preloadbin/jni/Android.mk` | MaxManager preloadbin | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `preloadbin/jni/Application.mk` | MaxManager preloadbin | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `preloadbin/jni/main.c` | MaxManager preloadbin | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `thermalcore/src/android_ffi.rs` | Rianixia-ThermalCore | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `thermalcore/src/constants.rs` | Rianixia-ThermalCore | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `thermalcore/src/context.rs` | Rianixia-ThermalCore | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `thermalcore/src/cooling.rs` | Rianixia-ThermalCore | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `thermalcore/src/cpu.rs` | Rianixia-ThermalCore | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `thermalcore/src/effectiveness.rs` | Rianixia-ThermalCore | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `thermalcore/src/learning.rs` | Rianixia-ThermalCore | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `thermalcore/src/lib.rs` | Rianixia-ThermalCore | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `thermalcore/src/main.rs` | Rianixia-ThermalCore | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `thermalcore/src/monitor.rs` | Rianixia-ThermalCore | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `thermalcore/src/policy_manager.rs` | Rianixia-ThermalCore | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `thermalcore/src/prediction.rs` | Rianixia-ThermalCore | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `thermalcore/src/simulator.rs` | Rianixia-ThermalCore | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `thermalcore/src/state.rs` | Rianixia-ThermalCore | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `thermalcore/src/thermal_zones.rs` | Rianixia-ThermalCore | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `thermalcore/src/utils.rs` | Rianixia-ThermalCore | Apache-2.0 | REPO_DEFAULT | ADD_COPYRIGHT_HEADER |
| `tools/code_health.py` | MaxManager tooling | Apache-2.0 | PROSE_SCOPE | KEEP |
| `tools/i18n_coverage.py` | MaxManager tooling | Apache-2.0 | PROSE_SCOPE | KEEP |
| `tools/i18n_translate.py` | MaxManager tooling | Apache-2.0 | PROSE_SCOPE | KEEP |
| `tools/jni_symbols.py` | MaxManager tooling | Apache-2.0 | PROSE_SCOPE | KEEP |
| `tools/kt_balance.py` | MaxManager tooling | Apache-2.0 | PROSE_SCOPE | KEEP |
| `tools/log_gate.py` | MaxManager tooling | Apache-2.0 | PROSE_SCOPE | KEEP |
| `tools/read_image_text.py` | MaxManager tooling | Apache-2.0 | PROSE_SCOPE | KEEP |
| `tools/repo_audit.py` | MaxManager tooling | Apache-2.0 | PROSE_SCOPE | KEEP |
| `tools/sepolicy_matrix.py` | MaxManager tooling | Apache-2.0 | PROSE_SCOPE | KEEP |
| `tools/source_manifest.py` | MaxManager tooling | Apache-2.0 | PROSE_SCOPE | KEEP |
| `tools/test_atlas_jvm.py` | MaxManager tooling | Apache-2.0 | PROSE_SCOPE | KEEP |
| `tools/test_maxai_jvm.py` | MaxManager tooling | Apache-2.0 | PROSE_SCOPE | KEEP |
