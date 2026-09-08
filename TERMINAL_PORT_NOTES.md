# نقل ميزة Terminal من ZKM (Zuan Kernel Manager) لمشروع MaxManager

هذا الملف بيوثّق التعديلات اللي اتعملت عشان ننقل ميزة الـ **Terminal** (شاشة تيرمنال
داخل التطبيق + وضع Floating Overlay) من مشروع `ZKM` لمشروع `optmize` (MaxManager).

ملحوظة: ميزة **Logs** (لوجكات لايف) كانت **منقولة بالفعل** في النسخة اللي رفعتها
(`ui/subscreens/LogsViewerScreen.kt` + `ui/viewmodel/LogsViewerViewModel.kt`)،
وبنسخة أفضل من ZKM أصلًا (بتستخدم `libsu` Shell مخصّص بصلاحية روت كاملة بدل
`ProcessBuilder` العادي المحدود اللي بيستخدمه ZKM). مفيش أي تعديل عليها.

## ملفات جديدة (new)

- `manager/terminal-emulator/` — موديول كامل (مكتبة Termux الأصلية، محرك التيرمنال + JNI).
- `manager/terminal-view/` — موديول كامل (الـ View بتاع التيرمنال، بيعتمد على `terminal-emulator`).
- `manager/app/src/main/jniLibs/arm64-v8a/libtermux.so` — المكتبة الأصلية (arm64-v8a بس، زي ما هي في ZKM).
- `manager/app/src/main/java/zx/maxmanager/ui/terminal/TerminalManager.kt`
- `manager/app/src/main/java/zx/maxmanager/ui/terminal/TerminalScreen.kt`
- `manager/app/src/main/java/zx/maxmanager/ui/terminal/TerminalPreferences.kt`
- `manager/app/src/main/java/zx/maxmanager/ui/terminal/FloatingTerminalService.kt`
- `manager/app/src/main/java/zx/maxmanager/ui/terminal/FastFetchView.kt`
- `manager/app/src/main/java/zx/maxmanager/ui/terminal/VideoBackground.kt`

## ملفات معدّلة (modified)

- `manager/settings.gradle.kts` — إضافة `include(":terminal-emulator")` و `include(":terminal-view")`.
- `manager/app/build.gradle.kts` — إضافة `implementation(project(":terminal-view"))` +
  `media3-exoplayer` / `media3-ui` (كانوا موجودين أصلًا في `libs.versions.toml`، بس متستخدمنش).
- `manager/app/src/main/AndroidManifest.xml` — تسجيل `FloatingTerminalService` بنفس نمط
  `FpsOverlayService` الموجود (`foregroundServiceType="specialUse"`)؛ صلاحيات
  `SYSTEM_ALERT_WINDOW` / `FOREGROUND_SERVICE*` كانت موجودة أصلًا فمحتجناش نضيف حاجة جديدة.
- `manager/app/src/main/res/values/strings.xml` — إضافة كل النصوص بتاعة التيرمنال
  (`terminal_*`, `action_close/lock/resize/settings/input_keyboard`).
- `manager/app/src/main/java/zx/maxmanager/MainActivity.kt` — إضافة
  `composable("terminal") { TerminalScreen() }`.
- `manager/app/src/main/java/zx/maxmanager/ui/mainscreens/TweakScreen.kt` — إضافة عنصر
  "Terminal Shell" في نفس القايمة اللي فيها "Logs Viewer".
- `manager/terminal-emulator/build.gradle`, `manager/terminal-view/build.gradle` —
  توحيد `compileSdk`/`targetSdk` (37) و Java version (17) مع باقي المشروع.

## تعديل مهم: توافق مكتبة Haze

مشروع ZKM بيستخدم `haze v1.7.2` مع API قديم (`HazeMaterials`, `style = HazeStyle(...)`,
`rememberHazeState()`)، بينما optmize بيستخدم `haze v2.0.0-alpha02` مع API مختلف
تمامًا (`HazeState()` + `hazeEffect(state) { blurEffect { blurRadius = ... } }`).

اتعمل تحويل لكل الاستخدامات دي في:
- `TerminalScreen.kt` (نافذة التيرمنال الرئيسية)
- `TerminalPreferences.kt` (قايمة الإعدادات)

الميزة الوحيدة اللي اتشالت: `forceInvalidateOnPreDraw` (workaround خاص بـ Android
12/12L في نسخة haze القديمة) — مش موجودة في الإصدار الجديد، فمحتاجة حل بديل لو
ظهرت مشكلة تحديث شاشة على أجهزة Android 12/12L تحديدًا.

## حاجات لازم تتفحص/تتعمل يدويًا

1. **Build فعلي في Android Studio** — كل التعديلات دي اتعملت يدويًا من غير Gradle/Android
   SDK متاح، فمحتاج build كامل (`./gradlew assembleDebug`) للتأكد من عدم وجود أخطاء
   compile، خصوصًا في تحويلات الـ haze.
2. **دعم armeabi-v7a**: `libtermux.so` موجودة بس لـ arm64-v8a (زي ما هي في ZKM نفسه).
   على أي جهاز 32-bit هتفشل الشاشة بـ `UnsatisfiedLinkError` عند فتح التيرمنال.
   لو محتاج دعم armv7 لازم تجيب/تبني نسخة 32-bit من نفس المصدر
   (`terminal-emulator/src/main/jni/termux.c`).
3. **صلاحية Overlay**: وضع الـ Floating Terminal بيحتاج `SYSTEM_ALERT_WINDOW` (Draw
   over other apps) — الكود بيتحقق ويطلبها تلقائيًا (`Settings.canDrawOverlays`)، تأكد
   إنها بتشتغل صح على أول تشغيل.
4. **الأيقونة**: `FloatingTerminalService`'s notification بتستخدم `R.mipmap.ic_launcher`
   و `R.string.app_name` — دول موجودين أصلًا في MaxManager فمفيش مشكلة.
