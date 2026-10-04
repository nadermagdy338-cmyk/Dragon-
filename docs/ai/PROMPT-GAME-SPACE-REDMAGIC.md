# برومبت جاهز للنسخ — Game Space بالعرض بمستوى REDMAGIC

> **ما هذا الملفّ:** نصّ واحد يُنسخ كما هو إلى جلسة جديدة (المُنتقي/Codebuff) لتنفيذ الطلب من أوّله.
> وليس خطةً جديدة تُضاف إلى الخطط: هو **إعادة صياغة الطلب نفسه** بالنصوص والحدود التي تُلزم المنفّذ،
> ومعها ما قِيس فعلًا في الجلسة السابقة حتى لا يُعاد من الصفر.

---

## البرومبت (انسخ من هنا إلى النهاية)

```
المطلوب: Game Space بواجهة عرضية (landscape) احترافية بمستوى REDMAGIC — بنفس الفكرة والتصميم،
مصقولة وحقيقية لا شكلًا ضعيفًا/واهنًا. وGame Space Overlay **لا يغطّي اللعبة**: يبقى قائمة
جانبية (side panel) مثل REDMAGIC، تُفتح وتُطوى.

مراجع المالك (بصريّة فقط، لا نسخ أصول/تخطيط/ألوان/حركة):
- https://www.google.com/search?...&q=game+space+red+magic (نتائج صور)
- مقال REDMAGIC الرسمي عن Game Space (ملكي — مرجع وظيفي/UX فقط)

مشروع مرجعي مطلوب استخدامه: https://github.com/Dreamucxe/GameCore (MIT — يُقرأ ويُستعار منهجًا؛
أي نقل كود يتسامح مع MIT **بشرط credit** في README/THIRD_PARTY_NOTICES). وابحث عن مشاريع أخرى
تحقّق الهدف وصنّف ترخيص كل واحد قبل أي اقتباس (GPL/AGPL ⇒ صفر كود).

سلوك يجب أن يتحقق حرفيًّا (هذا جوهر الطلب):
1. شاشة فرعية داخل التطبيق تكون **لوبي عرضيًّا** (لا قائمة عمودية): بطاقات ألعاب، مقصورة اللعبة،
   تبويبات، وأزرار قيادة — تُقرأ عرضيًّا لا رأسيًّا.
2. خيار overlay **لكل لعبة** في اللوبي. عند تفعيله: **تظهر اللوحة الجانبية تلقائيًّا** حين تصبح
   اللعبة أمامية — حتى لو فُتحت اللعبة **من خارج التطبيق** (مشغّل/مفتاح) — لا عند فتحها من اللوبي وحده.
3. اللوحة مطويّة = **مقبض رقيق على حافة الشاشة لا يشغل من مساحة اللعبة**؛ مفتوحة = لوحة جانبية
   تأخذ جزءًا من العرض حتى تُطوى. ولا يُوعَد بأنّ التراكب «لا يغطّي أبدًا» بلا هذا التفصيل.
4. اللوحة تحمل **الاثنين معًا**: أرقام القياس (FPS/حمل/حرارة/زمن الإطار) **و** أدوات سريعة
   (معدّل التحديث، تسجيل الجلسة، عدم الإزعاج، لقطة الشاشة…) — والأداة التي لا تُنفَّذ بعد تُعلن
   «غير متاحة بعد» ولا تُعرض كزرّ صامت.

قيود ملزمة من المستودع (اقرأها قبل أي سطر):
- `AGENTS.md` §0.1: لا Gradle/APK كعادة. ثلاث حالات للبناء فقط: طلب المالك · سؤال أنواع/API لا
  يجيبه غير مُصرّف · تغيير يمسّ `core/**` أو العتاد/الإقلاع في مهمة large. والبديل الإلزامي:
  البوابات الخفيفة (`kt_balance` · `code_health` · `i18n_coverage` · `prune` · `jni_symbols` ·
  `resource_compile` · `rtl_guard` · `repo_audit` · `license_audit`) + حاضنتا القياس
  (`build/kverify-audio/run-all.sh` النقيّة، و`build/kverify-android/run-all.sh` للأنواع).
  **والأفضل اليوم متاح:** ثُبّت Android SDK في `~/android-sdk` و`manager/local.properties` يشير
  إليه، فأصبح `./gradlew :app:compileReleaseKotlin` و`:app:testReleaseUnitTest` و`assembleDebug`
  تعمل فعليًّا (JDK 21 في `/home/codespace/java/21.0.12+1-ms`، و`--offline` يمرّ). فإذا كان السؤال
  «هل يترجم/هل تمرّ الاختبارات» فهو الآن مُصرّف لا تخمين — استعمله عند الحاجة الحقيقية فقط.
- `AGENTS.md` §0.2: النصوص الجديدة في `values/` (إنجليزي) و`values-ar/` (عربي) **فقط**؛ لا لغة
  ثالثة إلا بأمر «زامن». ADR-14.
- `AGENTS.md` §0.4 + ADR-54: **لا نقلَ ولا تقليدَ شكل** من مشروع مغلق/ملكي (REDMAGIC). الاستعارة
  «معلومة ومنهج»، والرسم من `ui/design/` عندنا لا من شكل تطبيق مرجعيّ. والترخيص المتسامح
  (MIT/Apache-2.0/BSD-3) يسمح بالنقل **مع credit**؛ وGPL/AGPL ⇒ صفر كود. والترخيص غير المُثبت
  ⇒ صفر نقل حتى يُقرأ نصّه.
- ADR-11: **لا كتابة sysfs/root من `ui/**`**. كل كتابة عبر `AppMonitor` + `PerAppControlRegistry`
  + `HardwareControlArbiter`. وكل أداة في اللوحة تذهب إلى **مالكها القائم** (مثال: معدّل التحديث
  عبر `RefreshRateReceiver` وحده). والبوابة `code_health.py` ترصد `presentation_hw_writes` وحدّها
  الحالي **20** — لا يزيد.
- ADR-07: المجهول `status_unknown` ولا صفر ولا رقم مُصنَّع. و`MaxDataTrust`/`MaxMetric` تحكم العرض.
- ADR-18: لا إعادة كتابة عمل مُنجز لأسباب جمالية.

ما هو موجود فعلًا ويُعاد استعماله (لا تبنِ محرّكًا ثانيًا):
- `ui/overlay/OverlayWindow.kt` — نافذة Compose بلا Activity (mount يعيد Boolean بعد نجاح
  `addView`، `unmount` idempotent، `place/screenBounds/contentSize`)، وخدمات `FpsOverlayService` ·
  `ProcessOverlayService` · `OverlayForeground` (إشعار أمامي مشترك).
- `core/platform/HudSession.kt` — `HudSampler` (قارئ واحد بمالك واحد `HUD_OWNER_OVERLAY`) ·
  `HudLive` · `HudRecorder`/`HudJournal` · `HudForm/HudField/HudArrangement`.
- `ui/component/HudSurface.kt` — مصيِّر الأرقام الواحد (يُستعمل في التراكب والمعاينة معًا).
- `core/gamespace/` — `GameProfileRepository` (على `APPLIST_JSON` نفسه) · `GameLibrary` ·
  `GameLibraryAccess` · `GameSpaceRepository` (مكتبة/مفضلة/manual/excluded في تفضيلات `settings`).
- `core/gamespace/GameSessionPanel.kt` — **نموذج اللوحة النقيّ المُختبَر** (من الجلسة السابقة):
  `panelSubject` (غير المقروء ليس «غير لعبة») · `reconcileGamePanel` (مفعَّل + لعبة ⟹ مقبض على
  الأقلّ) · `panelPlacement` (شرائح النظام والحدود) · `panelServiceNeeded` · `panelServiceLifetime`
  (انتظار ثم إيقاف) · `nextRefreshRate` (60⟶90⟶120⟶بلا فرض) · `gamePanelTileState`.
- `service/GamePanelService.kt` + `ui/component/GamePanelSurface.kt` + `ui/util/GamePanelPrefs.kt`
  + `ui/subscreens/GameLobbyScreen.kt` — البناء الحالي (راجع ما ينقص منه بدل إعادته).

فجوات **معلنة** ما زالت مفتوحة (اختر ما ينفّذه الطلب، وأعلن ما لا يُنفّذ):
- قياس FPS **بمستوى الحزمة/الطبقة** مع حالات stale/ambiguous — العدّاد العام في `FpsMonitorUtil`
  ليس FPS للعبة، ولا يُسمّى كذلك. (المرجع: GameCore `GameWatch`/`SessionRecorder`، وFPS-Meter
  `SurfaceFlingerFpsMonitor` — وكلتاهما بلا نقل كود حتى الآن.)
- سجلّ جلسات **دائم** (دوام + استعادة) بدل العدّ في الذاكرة.
- معاينة حيّة للوحة **داخل الشاشة** (كما في `FpsOverlayScreen`).
- `HudSampler` مالك واحد ⇒ لوحة الألعاب وتراكب الإطارات لا يعملان معًا: إمّا تعايش مُقاس أو
  استعمال متبادل معلن.
- أدوات اللوحة الناقصة: لقطة شاشة (MediaProjection)، وعدم الإزعاج (أثره عالميّ ومالكه `AppMonitor`).
- على **جهاز** (لا يُدّعى بدونه): منح/سحب إذن `SYSTEM_ALERT_WINDOW`، الدوران، انعكاس RTL للمقبض،
  تمرير اللمس، إدخال يد التحكّم، الشرائح فوق لعبة حقيقية، وموت العملية.

طريقة التسليم المطلوبة:
1. نفّذ على دفعات صغيرة: تصميم → قياس (حاضنتا القياس + البوابات الخفيفة) → إصلاح.
2. لا commit/push بلا إذن صريح. ولا `git push`/`reset --hard` أبدًا بلا طلب.
3. حدّث `docs/ai/HANDOFF.md` (قسم `Executor log`) و`docs/ai/NEXT_TASK.md` **مرّة واحدة في نهاية
   الدفعة**، لا بعد كل خطوة.
4. اكتب التسليم بقالب `docs/ai/VALIDATION.md` §8 حرفيًّا، وأعلن صراحةً كل ما لم يُقَس:
   «compilation unverified» إن لم تُشغّل مُصرّفًا، و«يحتاج جهازًا» لكل ما يمسّ العتاد/الإذن/الإقلاع.
5. **لا تدّعِ رقمًا لم تقسه**: إن قلت «يمرّ» فيجب أن يكون هناك أمر شُغّل ونتيجته. وإن سقطت بوابة
   فاذكر رسالتها لا «فشل عامّ».

الحدّ الذي لا يُخترق: أقصى جهد = **أكثر تحقّقًا** لا أقل؛ ولا يُقايض التحقّق بالإنجاز.
```

## ما قِيس في الجلسة السابقة (حتى لا يُعاد من الصفر)

| القياس | الأمر | النتيجة |
| --- | --- | --- |
| تصريف release | `./gradlew :app:compileReleaseKotlin` (JDK 21 · SDK في `~/android-sdk`) | **BUILD SUCCESSFUL** |
| اختبارات release | `./gradlew :app:testReleaseUnitTest` | **2466 اختبارًا / 0 فشل** (233 صنفًا) |
| موارد بـaapt2 حقيقي | `:app:processReleaseResources` + `tools/resource_compile.py --assert` | نجح — لم تبقَ «غير متحقَّقة» |
| APK | `./gradlew :app:assembleDebug` (`-Xmx3g --no-parallel`) | **BUILD SUCCESSFUL** (الذاكرة 8GB: `-Xmx6g` كان يقتل الـdaemon) |
| حاضنة النقيّة | `bash build/kverify-audio/run-all.sh` | **437 / 0** (75 مدخلًا / 266 صنفًا) |
| حاضنة الأنواع | `bash build/kverify-android/run-all.sh` | **557 مدخلًا / 3297 صنفًا / 0 خطأ** |
| بوابات العقد | 16 بوابة `--assert` (kt_balance · code_health · i18n · prune · jni · resource · rtl · license · manifest · design_tokens · bundle · design_doc · readme_assets · svg_review · screenshot_gallery · repo_audit) | كلها نجحت |
| CI على آخر دفع | `gh run view --log-failed` | كان **فاشلًا** بسبب رقم شاشات متقادم في README (60 مقابل 61) — **أُصلح** في `7c777e3` |

### عطب بناء حقيقي وقع هنا — ودرسُه

إضافة وجهة `GameLobby` رفعت عدد الشاشات المقيس من 60 إلى 61، و`tools/readme_assets.py --assert`
يقيس الرقم من `MaxDestinations.kt` ولا يصدّق الـREADME — فسقط **CI** بعد أن نجح كل شيء محليًّا.
**والدرس:** بعد أي وجهة جديدة، شغّل `python3 tools/readme_assets.py --assert` قبل الدفع؛ فبوّابة
الرقم المقيس هي التي تكشف «ادّعاء متقادم» في الصفحات، ولا تكشفه حاضنة الأنواع ولا البناء.
