# NEXT_TASK — للـExecutor (DeepSeek Harness)

## NT-03 — التحقق من بناء Max AI (أولوية قصوى)

السياق: NT-02 أُنجز كاملًا (منطق + واجهة + نصوص). لم يُبنَ المشروع محليًا لأن `gradle` في PATH إصدار 4.4.1 بينما الـwrapper يطلب 9.5.1 والشبكة مغلقة.

المطلوب:
1. `./gradlew :app:assembleDebug` ثم `./gradlew :app:testDebugUnitTest`.
2. إصلاح أي خطأ تجميع في الملفات التالية فقط، بلا إعادة تصميم:
   - `core/maxai/MaxAiJournal.kt`
   - `core/maxai/MaxAiInsights.kt`
   - `core/maxai/MinimalPlanner.kt`
   - `core/maxai/MaxAiEngine.kt`
   - `core/maxai/MaxAiModels.kt`
   - `ui/viewmodel/MaxAiViewModel.kt`
   - `ui/design/MaxAiCinematics.kt`
   - `ui/mainscreens/MaxAiScreen.kt`
3. نقاط الخطر المتوقعة:
   - حقن Hilt: `MaxAiEngine` صار يأخذ `MaxAiJournal` كوسيط أخير (`@Singleton` + `@ApplicationContext`) — لا يلزم تعديل موديول، تأكد فقط.
   - رؤية `MinimalPlanner.SATISFIED_SCORE` من المحرك.
   - توافق أسماء أيقونات Material المستخدمة في الشاشة.
4. ممنوع: حذف أي مرحلة من مراحل الخط الزمني الثماني، أو استبدال قيمة غير مقيسة بصفر، أو إضافة بيانات توضيحية.

## NT-04 — اختبار JVM لاستخلاص المعرفة — مكتمل في هذه النسخة
أُنشئ `app/src/test/java/nd/max/core/maxai/MaxAiInsightsTest.kt`:
- عيّنة واحدة ⇒ `LEARNING`.
- مكسب ≥ `HELPFUL_GAIN` مع ≥ `MIN_SAMPLES_FOR_VERDICT` ⇒ `PROVEN_HELPFUL`.
- ارتفاع حراري > `COSTLY_THERMAL_C` بلا مكسب موازٍ ⇒ `PROVEN_COSTLY`.
- `successRate == null` حين لا حلقات مُنفَّذة.
- تجاهل مفاتيح الأثر غير `GLOBAL_CONTEXT`.
- يغطي أيضًا فصل decision/probe/safety/drift، وعدادات التنبؤ والسياق.

## NT-05 — إتمام إصلاحات NT-01b
F-01 مسار `app_detail/`، F-01b `openApp`، F-02 اعتماد `MaxDomainCard` على التنقل، F-03 تكرار `titleRes`، F-04 شاشتان غير قابلتين للوصول، F-05 `LaunchedEffect(currentRoute)` العام، F-06 بطاقات الـhub الساكنة، F-07 الأكواد الميتة.

## NT-06 — تحسينات Max AI اللاحقة (بعد نجاح البناء)
- زر مسح الدفتر في قسم التحكم (`MaxAiJournal.clear()`).
- فلترة الخط الزمني بالحكم (تحسن/استرجاع/حجب/بلا تدخل).
- أحكام المعرفة لكل سياق تطبيق لا للسياق العام فقط.
