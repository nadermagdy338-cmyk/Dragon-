# تعديلات Max Manager UX Architecture Plan

استبدل هذه الملفات بنفس المسارات في مشروعك الأصلي `optmize-main/`.
التفاصيل الكاملة وأسباب كل قرار موجودة في التقرير المرفق (docx).

## المرحلة 1

## ملفات جديدة
- `manager/app/src/main/java/nd/max/ui/mainscreens/DiagnosticsScreen.kt`

## ملفات معدّلة
- `manager/app/src/main/java/nd/max/MainActivity.kt`
  — إعادة ترتيب bottom nav إلى Home|Tweaks|Apps|Settings، تسجيل route "diagnostics".
- `manager/app/src/main/java/nd/max/ui/mainscreens/TweakScreen.kt`
  — نقل 6 أدوات تشخيص (Process Manager/Property Editor/Log Console/Shell/
    Boot Image Tools/Hidden Activities) إلى DiagnosticsScreen، إبقاء 4 أدوات ضبط.
- `manager/app/src/main/java/nd/max/ui/mainscreens/SettingsScreen.kt`
  — قسم جديد "Tools & Diagnostics" يربط لشاشة Diagnostics.
- `manager/app/src/main/java/nd/max/ui/subscreens/AppSettingsScreen.kt`
  — تحويل من scroll واحد (6 أقسام) إلى 4 تابات (Performance/Display/Gaming/
    Power & Connectivity) + عدّاد تخصيص لكل تاب. لا تغيير في منطق القراءة/الكتابة.
- `manager/app/src/main/res/values/strings.xml`
  — +13 مصطلح جديد (عناوين تابات، Diagnostics)، تصحيح مصطلح واحد فقط
    (str_app_specific_settings_will_ove: global → Default). setedit_tab_global
    تُرك بدون تغيير عمدًا (اسم Android API حقيقي: Settings.Global).

## المرحلة 2 — حالة "Customized" في قائمة التطبيقات (القسم 12 من الخطة)

## ملفات معدّلة
- `manager/app/src/main/java/nd/max/ui/util/AppConfigUtil.kt`
  — دالة مشتركة جديدة `AppConfig.customizedFieldCount()`: تحسب عدد الحقول
    غير الافتراضية عبر كل الإعدادات الـ18. مصدر واحد للحقيقة يستخدمه كل من
    قائمة التطبيقات وعدّادات التابات في AppSettingsScreen، بدل تكرار المنطق.
- `manager/app/src/main/java/nd/max/ui/viewmodel/ApplistViewmodel.kt`
  — استبدال قراءة مفاتيح JSON فقط (org.json) بفكّ تشفير كامل لـ
    Map<String, AppConfig> عبر kotlinx.serialization (نفس أسلوب
    AppSettingsViewModel تمامًا)، لحساب customizedCount لكل تطبيق.
    حقل جديد `customizedCount: Int` في AppInfo.
- `manager/app/src/main/java/nd/max/ui/mainscreens/ApplistScreen.kt`
  — صف كل تطبيق يعرض "Customized (N)" بدل "Enabled" العام لو فيه overrides
    فعّالة، وإلا يبقى "Enabled" كما هو.
- `manager/app/src/main/res/values/strings.xml`
  — مصطلح جديد واحد: label_customized_count = "Customized (%1$d)".

## المرحلة 2.3 — وضع Simple/Advanced (القسم 15-16 من الخطة)

## ⚠️ اكتشاف مهم أثناء التنفيذ: تصادم أسماء موجود مسبقًا في الكود

يوجد فعليًا **صنفان مختلفان بنفس الاسم** `SettingsViewModel` في حزمتين مختلفتين:
- `nd.max.ui.settings.SettingsViewModel` (ملف SettingsViewModel.kt) — يغلّف
  SharedPreferences (ثيمات، خلفية، تنقّل...).
- `nd.max.ui.viewmodel.SettingsViewModel` (ملف SettingViewmodel.kt، بدون s) —
  يغلّف PropertyUtils/خصائص النظام الجذرية (disableTweak، autoMode...).

`SettingsScreen.kt` يستورد `nd.max.ui.viewmodel.*` بالنجمة (`*`)، فيحلّ اسم
`SettingsViewModel` إلى **الصنف الثاني** تلقائيًا — وهذا مصدر خطأ صامت خطير:
لو أحد أضاف يومًا `import nd.max.ui.settings.SettingsViewModel` صريحًا في نفس
الملف، كل استخدام لـ`uiState`/`setShowToast` سينكسر فورًا بدون أي تحذير واضح
من المترجم (compile error غامض، مش رسالة تشرح المشكلة الحقيقية).

**لم أُعِد تسمية أي من الصنفين** — هذا قرار بنيوي أكبر من نطاق هذه المرحلة
ويستحق قرارًا صريحًا منك (مثلًا: PreferenceSettingsViewModel و
RootPropsSettingsViewModel). وثّقت المشكلة بدل حلّها من طرف واحد. الحل
المؤقت الآمن الذي استخدمته: alias صريح عند الاستيراد
(`import ... as PreferenceSettingsViewModel`) في كل مكان احتجت فيه الوصول
لصنف الـ SharedPreferences من شاشة تستورد الآخر بالنجمة.

## ملفات معدّلة
- `manager/app/src/main/java/nd/max/ui/settings/SettingsPreference.kt`
  — تفضيل جديد `isAdvancedMode` (SharedPreferences، افتراضيًا false)، بنفس
    نمط بقية التفضيلات في هذا الملف تمامًا.
- `manager/app/src/main/java/nd/max/ui/settings/SettingsViewModel.kt`
  — تعريض `isAdvancedMode`/`setAdvancedMode` بنفس نمط بقية الدوال هناك.
- `manager/app/src/main/java/nd/max/ui/mainscreens/SettingsScreen.kt`
  — مفتاح "Advanced Mode" جديد في أعلى section_features، عبر alias استيراد
    (انظر الاكتشاف أعلاه) لتفادي تصادم الاسم مع SettingsViewModel الأخرى.
- `manager/app/src/main/java/nd/max/ui/subscreens/AppSettingsScreen.kt`
  — قسم "Thermal & GPU Governor" (أكثر الإعدادات تقنية) أصبح مطويًا افتراضيًا
    ما لم يكن Advanced Mode مفعّلًا عالميًا — لكن صف "Show advanced options"
    يفتحه بضغطة واحدة لأي مستخدم، فمفيش شيء مخفي فعليًا، بس مُبعَد عن الطريق.
    هذا التنفيذ الفعلي للقرار المفتوح 4.3 في التقرير (طيّ افتراضي، مش إخفاء دائم).
- `manager/app/src/main/res/values/strings.xml`
  — 3 مصطلحات جديدة: advanced_mode_title/desc، show_advanced_options.

## المرحلة 3 — مكوّن تحذير موحّد (Safety UX، القسم 17 من الخطة)

## ملفات جديدة
- `manager/app/src/main/java/nd/max/ui/component/WarningBanner.kt`
  — مكوّن `WarningBanner(text, severity)` بمستويين (NOTICE/CAUTION)، مبني
    فوق ExpressiveInfoCard الموجود أصلًا (نفس التخطيط والمسافات، بس بلون
    تحذيري) بدل تصميم جديد منفصل — لضمان اتساق بصري تلقائي وسهولة صيانة:
    أي تعديل مستقبلي على شكل "التحذير" مكانه واحد فقط.

## ملفات معدّلة
- `manager/app/src/main/java/nd/max/ui/subscreens/AppSettingsScreen.kt`
  — أول استخدام فعلي للمكوّن: فوق GPU Governor/Thermal Profile مباشرة (نفس
    القسم اللي بقى مطويًا خلف Advanced Mode في المرحلة 2.3) — تزامن طبيعي:
    اللحظة اللي المستخدم يفتح فيها الإعدادات الخطرة، يشوف ليه هي خطرة.
- `manager/app/src/main/res/values/strings.xml`
  — مصطلح تحذير واحد: warning_thermal_gpu_override.

## المرشّحون التاليون لإعادة استخدام WarningBanner (لم يُلمسوا بعد)
AdrenoGpuScreen.kt، MaliGpuFreqScreen.kt، ZramManagerScreen.kt — كلها شاشات
عندها بالفعل حوار "يحتاج إعادة تشغيل" (RebootManager)، لكن مفيش تحذير نصّي
ثابت قبل التعديل. إضافة سطر WarningBanner واحد لكل شاشة عمل بسيط ومنخفض
المخاطر في جلسة قادمة.

## لم يُختبر

لا تتوفر بيئة Android SDK ولا اتصال شبكة في بيئة التنفيذ هذه، فلم يتم تشغيل
`./gradlew build` فعليًا. تم التحقق فقط من: توازن الأقواس في كل ملف Kotlin
المعدَّل، صحة strings.xml كـ XML، وتطابق كل اسم حقل AppConfig المستخدم مع
التعريف الفعلي في AppConfigUtil.kt. **لازم بناء واختبار يدوي على جهاز حقيقي
قبل الدمج أو الإصدار.** بالذات: تأكد أن ملف maxmanagerApplist.json الفعلي
على الجهاز يفكّ تشفيره بنجاح عبر kotlinx.serialization بنفس الطريقة التي
كان بيها JSONObject يقرأ المفاتيح فقط — لو فيه حقول legacy غير متوقعة في
الملف، `ignoreUnknownKeys = true` يجب أن يتعامل معها، لكن يستحق تحققًا يدويًا.

