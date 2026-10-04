# `README-CREDITS-01` — spec (مُستخرَج من مقابلة المالك، لا كود بعد)

**التاريخ:** 2026-09-28 · **الحالة:** **نُفِّذت** في تكملة ١٦٠ — وهذا الملفّ يبقى سجلَّ القرار وأدلّة القياس.
**مكان هذا الملف:** `docs/ai/` لا جذر المستودع، **وهذا قرار مقيس لا ذوق**: `tools/code_health.py`
يحمل قائمة `ROOT_ALLOWED` لكل ملف جذر، وأي ملف جذر خارجها يُسقط البوابة بـ`stray_root_file`.
فلو كُتبت المواصفة في الجذر لأسقطت `--assert` — وهي مواصفة عمل لا وثيقة منتج.

---

## 0. نصّ أمر المالك (حرفيًّا)

> «اريدك ان تضيف في اقراني.md cradit»

وتكميله أثناء المقابلة (بالنصّ): «قم فقط باضافة credit للمشاريع الخارجية التي عندنا **بعد التحقق حقًّا**،
فمثلًا لا أعتقد أنه لدينا AnyKernel3 لذا لا تضف أنه ساهم» · «قم بإزالته تمامًا» · «حسب ما يشترط
كل واحد — أنا أفضل عدم ذلك [بوابة]».

**القراءة المعتمدة:** قسم شكر في `README.md` (ومرآته `README.ar.md`) لأصحاب الأعمال الخارجية
**الموجودة فعلًا في الشجرة** — يُبنى على قياس لا على ذاكرة، ويُزال كل ادّعاء لم يصمد للقياس.

---

## 1. القرارات المثبّتة في المقابلة

| # | السؤال | قرار المالك |
| --- | --- | --- |
| ١ | مَن يُذكر؟ | **المشاريع الخارجية التي كودها في الشجرة فقط** — لا أفراد، لا أدوات، لا مصادر بحث |
| ٢ | شرط الذكر | **التحقّق الفعلي** («بعد التحقق حقًّا») — اسم لا يقابله ملف في الشجرة لا يُذكر |
| ٣ | `THIRD_PARTY_NOTICES.md` الذي يقول «لا يُعرض في README» | **يُعدَّل نصّ المبدأ** ليوافق بين الأمرين (لا ADR موازٍ يُبقي المبدأ صامتًا) |
| ٤ | `AnyKernel3` | **يُحذف من كل مكان**: قسم الفضل + `THIRD_PARTY_NOTICES.md` + `tools/license_audit.py` |
| ٥ | بوابة جديدة تقيس القائمة؟ | **لا** — «أنا أفضل عدم ذلك»: تحقّق مرّة واحدة في هذه الجولة، بلا أداة جديدة |
| ٦ | `AZenith` (لم يكن في قائمتي، فقِسته وأثبتُّ وجوده) | **يُضاف — هو الأصل الأساسي** |
| ٧ | صيغة العلاقة | «**أضف الفضل فقط، لا تذكر مبني على AZenith**» ⇒ بلا كلمة «مبنيّ على» ولا «الأساس» في النصّ |
| ٨ | أعمدة الجدول | **ثلاثة**: اسم المشروع · صاحب الحقوق · الترخيص (لا عمود «ما نستعمله منه» ولا إصدار) |
| ٩ | اسم القسم | `## Credits` (إنجليزيًّا)، و**في الصفحة العربية أيضًا `## Credits`** كما اخترتَ |
| ١٠ | الرمز | **رمز جديد مولَّد** `ic-credit.svg` (يُضاف إلى `tools/gen_readme_assets.py` — لا رسم بيد) |
| ١١ | المشاريع المُزالة (Termux · ZKM · HorizonKernelFlasher · magiskboot · KTweak) | **تُستبعد** — القسم لما هو قائم فقط |
| ١٢ | المرآة | **يُضاف إلى `README.ar.md`** — ترجمة الوصف، والأسماء لاتينية كما هي |
| ١٣ | موضع القسم | «قرب نهاية README.md» ⇒ **آخر قسم في الصفحة، بعد `Licence`** (وفي §٨ القرارات التي وكلتَها إليّ) |

---

## 2. القائمة المُتحقّقة — بالقياس لا بالذاكرة

### ٢.١ ما يصدُق عليه الشرط (وسيُذكر)

| المشروع | الدليل المقيس في هذه الشجرة | الأمر الذي أعاد القياس | الترخيص المُعلَن |
| --- | --- | --- | --- |
| **AZenith** — `github.com/Liliya2727/AZenith` | **١٧٩ ملفًا** في `manager/app` يحمل ترويسة `Copyright (C) 2026-2027 Zexshia` + ملفات C في `archdaemon/` (تُصدّقها صفوف `docs/PROVENANCE.md` مثل `archdaemon/jni/Main.c`) | `grep -rl 'Zexshia' --include='*.kt' manager/app \| wc -l` ⇒ 179 · `grep -rl 'Zexshia' archdaemon` ⇒ ملفات C | Apache-2.0 — وترويسة AZenith وحدها في ٢٥ ملفًا (`MaxManager contributors`) |
| **Encore Tweaks** — `github.com/Rem01Gaming/encore` | **٢٠ ملفًا** باسمه في الترويسة: ١٤ ملف `.c` في `archdaemon/jni/src/**`، و`archdaemon/jni/Android.mk`، و`Application.mk`، و`archdaemon/tests/build/DaemonUtility_mutant.c`، و`mainfiles/verify.sh`، و`manager/README.md`، و`manager/app/src/main/java/nd/max/AppMonitor.kt` | `grep -rl 'Rem01Gaming' archdaemon mainfiles manager \| wc -l` ⇒ 20 | Apache-2.0 — `Copyright (C) 2024-2025 Rem01Gaming` |
| **Rianixia-ThermalCore** — `github.com/ryanistr/Rianixia-ThermalCore` | **١٦ ملف Rust** في `thermalcore/src/*.rs` (و٤١ ملفًا في `thermalcore/` تسمّي Rianixia/ryanistr) | `find thermalcore -name '*.rs' -not -path '*/target/*' \| wc -l` ⇒ 16 | Apache-2.0 — `Copyright (C) 2025-2026 ryanistr` |
| **VMTouch** — `github.com/hoytech/vmtouch` | **كود مُضمَّن**: `preloadbin/jni/main.c` (٢٧٦٤٨ بايت) يحمل `#define VMTOUCH_VERSION "1.4.1"` و`Copyright (c) 2009-2023 Doug Hoyte and contributors` في ترويسته، وهو بحسب `THIRD_PARTY_NOTICES.md` **٩٩٫٧٪ مطابقة (فارق ٣ أسطر)** | `grep -n 'VMTOUCH_VERSION\|Doug Hoyte' preloadbin/jni/main.c` | BSD-3-Clause |

### ٢.٢ ما لا يصدُق عليه الشرط (ولماذا يُستبعد)

| المُستبعَد | القياس | الحكم |
| --- | --- | --- |
| **AnyKernel3** | `grep -rni anykernel` في الشجرة كلها ⇒ **موضعان فقط**: `THIRD_PARTY_NOTICES.md:31` و`tools/license_audit.py:161-167`. لا ملفّ له | **يُحذف من الموضعين** (§٥.٢) — حدس المالك كان صحيحًا |
| **KTweak** | `THIRD_PARTY_NOTICES.md` نفسه يقول: «منهج الضبط المبني على الدليل (**لا كود مُنقول — قيس ولم يُصبْ أي ملف**)» | يبقى إعلانًا في ملفّ الإشعارات، **ولا يُذكر** في الفضل |
| **AOSP** | `grep -rl 'Android Open Source Project'` ⇒ `manager/app/src/main/res/values/font_certs.xml` وحده؛ و`android/aosp/` **٦ ملفات مكتوبة بأسلوب AOSP لا منقولة منه** (لا ترويسة AOSP فيها) | لم يُختر — الأثر رقيق، وذكره بلا ملفّ يقابله يخالف شرط «التحقّق حقًّا» |
| **مصادر البحث** (`SmartPack` · `Kelvin` · `Calibrate-SoC` · `ACC` · `FusionHUD` · `Thrawl` · `AutoSystemBoost` …) | `.planning/phases/01-…/01-SOURCES.md` — «لم يُنقل كودها» | لم تُختر |
| **المُزالة**: Termux · ZKM · HorizonKernelFlasher · KernelFlasher · magiskboot | مُثبتة في `THIRD_PARTY_NOTICES.md` §٣ و`docs/PROVENANCE.md` | لم تُختر — «القسم لما هو قائم فقط» |
| **تبعيات Gradle (٤٢) وصناديق Cargo (١٠٨)** | `build/license-report.json` | مكتبات تُبنى معنا لا مشاريع كودها في الشجرة — تبقى في §٢ من ملفّ الإشعارات |
| **أفراد**: `Rapli` (١ ملف) · `KowX` (١ ملف) | ترويسات Apache-2.0 في `manager/app` | المالك: المشاريع فقط |

### ٢.٣ ما تشترطه كلٌّ منها فعلًا — بحث مُسجَّل

سألت: «حسب ما يشترطون هم — ابحث عنهم». والنتيجة المقروءة من مستودعاتهم:

| المشروع | ما يطلبه صراحةً | الأثر على قسم الفضل |
| --- | --- | --- |
| **AZenith** | `NOTICE.md` في المستودع + صاحب المشروع يكتب علنًا: «All my modules are open sources, **you may use it but give credits!**» | **الوحيد الذي يطلب الفضل صراحةً** — ومادّة Apache-2.0 §4(d) تُوجب نقل إشعارات `NOTICE` في العمل المشتق، فالقسم يُعطي النسخة المقروءة |
| **Encore Tweaks** | Apache-2.0 معلنًا؛ لا شرط ذكر في الـREADME | الذكر فضل لا التزام |
| **Rianixia-ThermalCore** | Apache-2.0 (`LICENSE` وحده، ولا `NOTICE`) | الذكر فضل لا التزام |
| **VMTouch** | BSD-3-Clause: «so you can basically do whatever you want with it» — بشرط بقاء سطر الحقوق في المصدر (وهو محفوظ في `main.c`) | الذكر فضل لا التزام |

**ولا شيء من هذا يُلغي `THIRD_PARTY_NOTICES.md`**: هو المرجع الملزم لكل إشعارات ما قبل الرخصة،
والقسم في الـREADME **إعلان فضل مقروء**، لا نصّ رخصة ولا بديل عن الإشعار.

---

## 3. النصّ المقترح للقسم (جاهز للنسخ)

### ٣.١ في `README.md` — آخر قسم في الصفحة، بعد `Licence`

```markdown
---

<a id="credits"></a>

## <img src="docs/assets/ic-credit.svg?v=2" width="22" height="22" align="absmiddle" alt="Credits"> Credits

MaxManager is not written alone. These are the works it thanks — and every notice their licences
require stays in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md), which remains the binding list.

| Project | Copyright | Licence |
| --- | --- | --- |
| [AZenith](https://github.com/Liliya2727/AZenith) | (C) 2025-2026 Zexshia | Apache-2.0 |
| [Encore Tweaks](https://github.com/Rem01Gaming/encore) | (C) 2024-2025 Rem01Gaming | Apache-2.0 |
| [Rianixia-ThermalCore](https://github.com/ryanistr/Rianixia-ThermalCore) | (C) 2025-2026 ryanistr | Apache-2.0 |
| [VMTouch](https://github.com/hoytech/vmtouch) | (c) 2009-2023 Doug Hoyte and contributors | BSD-3-Clause |

<p align="right"><sub><a href="#top">↑ Back to top</a></sub></p>
```

### ٣.٢ في `README.ar.md` — نفس الموضع، والعنوان إنجليزي كما اخترتَ

```markdown
---

<a id="credits"></a>

## <img src="docs/assets/ic-credit.svg?v=2" width="22" height="22" align="absmiddle" alt="الفضل"> Credits

‏MaxManager لم يُكتب وحده. هذه الأعمال نشكره عليها — وكلّ إشعار تفرضه رخصتها يبقى في
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)، وهو القائمة الملزمة.

| المشروع | صاحب الحقوق | الرخصة |
| --- | --- | --- |
| [AZenith](https://github.com/Liliya2727/AZenith) | (C) 2025-2026 Zexshia | Apache-2.0 |
| [Encore Tweaks](https://github.com/Rem01Gaming/encore) | (C) 2024-2025 Rem01Gaming | Apache-2.0 |
| [Rianixia-ThermalCore](https://github.com/ryanistr/Rianixia-ThermalCore) | (C) 2025-2026 ryanistr | Apache-2.0 |
| [VMTouch](https://github.com/hoytech/vmtouch) | (c) 2009-2023 Doug Hoyte and contributors | BSD-3-Clause |

<p align="right"><sub><a href="#top">↑ Back to top</a></sub></p>
```

---

## 4. الملفّات التي ستُلمَس (بالموضع)

| # | الملفّ | التغيير |
| --- | --- | --- |
| ١ | `README.md` | إضافة القسم (§٣.١) آخر الصفحة؛ وفي كتلة `Contents` أعلى الملف (السطر ٤٤): إضافة صفّ ١٧ و`sixteen` ← `seventeen` |
| ٢ | `README.ar.md` | مرآة القسم (§٣.٢)؛ وفي «المحتويات» (السطر ٤٥): صفّ `١٧` و«الستة عشر» ← «السبعة عشر» |
| ٣ | `tools/gen_readme_assets.py` | إدخال الرمز الجديد في قوائم الرموز المرجعية (`icons_reference()`/`all_icons()`) — **بلا رسم بيد** (القاعدة: المولّد هو المصدر، تكملة ١٥٤) |
| ٤ | `docs/assets/ic-credit.svg` | **ملفّ جديد مولَّد** — يُشار إليه من الصفحتين معًا (شرط القاعدة ⑬(ج)) |
| ٥ | `THIRD_PARTY_NOTICES.md` | تعديل نصّ المبدأ (§٥.١) · تعديل جملة §١.١ الخاتمة (§٥.١ب) · حذف صفّ AnyKernel3 (السطر ٣١) |
| ٦ | `tools/license_audit.py` | حذف إدخال `SOURCES` الخاص بـ`anykernel3` (الأسطر ١٦٠-١٦٨) |
| ٧ | `docs/ai/DECISIONS.md` | **ADR-54**: «الفضل يُعلن في الـREADME، والإشعارات تبقى ملزمة في `THIRD_PARTY_NOTICES.md`» — لأن النصّ المعدَّل كان مبدأً مُعلَنًا |
| ٨ | `docs/ai/HANDOFF.md` | **تكملة ١٦٠** — مرّة واحدة في النهاية (§0.3) |
| ٩ | `docs/ai/NEXT_TASK.md` | ترويسة المهمة الجديدة |
| ١٠ | `docs/PROVENANCE.md` | **إعادة توليد**: `python3 tools/license_audit.py --provenance` (حذف إدخال من `SOURCES` يغيّر التصنيف والجداول) |
| ١١ | `docs/ai/source-manifest.txt` | **إعادة توليد** `--write` ثم `--check` (ملفّ جديد + ملفّان معدَّلان + محتوى متغيّر) |
| ١٢ | `docs/ai/KNOWN_ISSUES.md` | سطر: ادّعاء `AnyKernel3` في ملفّ الإشعارات كان **بلا ملفّ يقابله** — أُزيل بالقياس |
| ١٣ | `docs/README.md` و`docs/verification.md` | **لا يُلمسان** ما لم يظهر أثرهما في البوابات — إلّا لو لزم ذكر مصدر الرموز (فالسطر قائم أصلًا في `docs/README.md`) |

---

## 5. تعديلات ملفّ الإشعارات بالحرف

### ٥.١ نصّ المبدأ

**قبل** (سطر ١١-١٣):

> **ومبدأ هذا الملف:** يُحفظ **القدر الذي تفرضه الرخصة** من الإشعار (حقوق النشر والرخصة والنصّ)،
> ولا يُعضَّد بذكرٍ دعائي ولا يُعرض في الواجهة ولا في `README`؛ ...

**بعد** (المقترح):

> **ومبدأ هذا الملف:** يُحفظ **القدر الذي تفرضه الرخصة** من الإشعار (حقوق النشر والرخصة والنصّ)،
> ولا يُعضَّد بذكرٍ دعائي ولا يُعرض في الواجهة. **ويُذكر أصحاب المكوّنات القائمة في `README`
> باسم العمل وحقّ النشر ورخصته (قسم `Credits`) — إعلانًا للفضل، لا نقلًا للإشعار:** هذا الملفّ
> يبقى **وحده** المرجع الملزم، ووحده موضع نصّ الرخصة الكامل.

**وأيضًا جملة §١.١ الخاتمة** (سطر ٦٢ تقريبًا) تقول اليوم «ولا تُعرض في الواجهة ولا في `README`»
عن أصحاب ترويسات Apache المحفوظة (وفيها `Zexshia` — وهو مذكور في قسم الفضل): تُعدَّل بنفس المعنى
(«تُعرض أسماؤهم في `README` باسم العمل ورخصته، ولا يُنقل نصّ الإشعار هناك»).

### ٥.٢ حذف AnyKernel3 — موضعاه بالحرف

- `THIRD_PARTY_NOTICES.md` السطر ٣١: صفّ الجدول
  `| **AnyKernel3** \`github.com/osm0sis/AnyKernel3\` | صيغة حزمة التثبيت … | BSD-3-Clause | Copyright (C) osm0sis |`
- `tools/license_audit.py` الأسطر ١٦٠-١٦٨: إدخال `{"id": "anykernel3", … "evidence": r"anykernel", …}`

**وحدّ القياس بعد الحذف:** `mainfiles/customize.sh` يستعمل أسلوب AK3 في دوالّ الإجهاض/الطباعة
(`abort` · رسائل ملوّنة). وهو **أسلوب لا كود منقول**، فلا يُذكر — وهذا نصّ قرارك: «قم بإزالته تمامًا».

---

## 6. البوابات المتأثّرة (تُشغَّل بعد التنفيذ، بلا مُصرّف)

```sh
python3 tools/kt_balance.py --assert                # توازن + XML
python3 tools/code_health.py --assert               # صحة = 0 (وقائمة ملفات الجذر سليمة)
python3 tools/i18n_coverage.py --assert             # لا نصّ تطبيق جديد ⇒ لا تغيير متوقّع
python3 tools/readme_assets.py --assert             # ⑬ الرمز الجديد (ورقة الأمان · غير يتيم) · ⑫ الرابط والنقطة #credits
python3 tools/readme_assets.py --self-test          # 19/19 كما هي
python3 tools/svg_review.py --assert                # 40 أصلًا بدل 39 · صفر عيب
python3 tools/license_audit.py --assert --self-test # بعد حذف إدخال AnyKernel3
python3 tools/source_manifest.py --check            # بعد --write
python3 tools/readme_assets.py --assert             # ثم كامل الـ34 أمرًا كما في CI
```

**المتوقّع بعد التنفيذ (يُقاس لا يُدَّعى):** أصول `readme_assets` 39 ⇒ **40** · `svg_review` 39 ⇒ **40**
· رماز الـREADME 30 ⇒ **31** · البصمة تتغيّر ⇒ تُعاد كتابة `source-manifest.txt` و`docs/PROVENANCE.md` قبل `--check`.

**وبناء لا يُشغَّل** (§0.1-3: تغيير وثائق وأصل SVG — لا حالة من الثلاث). ويُكتب في التسليم
«compilation unverified in this environment» كما تقتضي القاعدة.

---

## 7. تصميم رمز `ic-credit.svg`

- **الفكرة:** وسام (ميدالية) — حلقة نعناعية `ICON_MINT` في الوسط الأعلى، وشريطان مائلان `ICON_STEEL`
  ينزلان إلى قاع البلاطة. تُقرأ في ٢٠px كـ«وسام شكر»، ولا تتشابه مع الرموز الثلاثين (لا نجمة Max AI،
  ولا كتاب الذاكرة، ولا درع الشفافية).
- **الالتزامات** (تُقاس ببوابتين مستقلّتين): بلاطة `24×24` واحدة بحوافّ `rx` كبقية الرموز · لا `<text>`
  · لا قوس `A` · كل الإحداثيات داخل **[4.4, 19.6]** ليبقى الرسم داخل ورقة الأمان `[4, 20]` حتى مع
  عرض الخطّ (القاعدة ⑬ تقيس الإحداثيات، و`svg_review` يقيس القصّ) · `<title>`/`<desc>` بنمط
  `{name}-t`/`{name}-d` · وسماعة `stroke-linecap/linejoin="round"` عبر `_ink()`.
- **القناة:** يُكتب في المولّد ← يُصدَّر بـ`python3 tools/gen_readme_assets.py` ← يُقرأ نصًّا (لا مُصيِّر
  في هذه البيئة) ← تُشغَّل البوابتان.

---

## 8. القرارات التي وكلتَها إليّ (قابلة للعكس بسطر واحد أنت)

| القرار | ما اخترته | لماذا | العكس |
| --- | --- | --- | --- |
| **جدول المحتويات** | **يُضاف صفّ ١٧** وتُحدَّث «الستة عشر» | الجدول هو فهرس الصفحة؛ قسم موجود خارجه ثقب في الفهرس لا حرية | حذف الصفّ وإعادة العبارة |
| **الترتيب** | آخر قسم تمامًا بعد `Licence` | «قرب نهاية README.md»؛ ويبقى ترقيم الأقسام الستة عشر كما هو بالإلحاق لا بالإزاحة | نقل القسم قبل `Licence` |
| **الرابط على اسم المشروع** | نعم — المستودع الأصلي على الاسم | لا يشترطه أحد منهم، لكنه **ما يجعل الفضل قابلًا للتحقق** بضغطة (وهو نمط AZenith نفسه في قسمها) | إزالة الرابط من الخلايا الأربع |
| **البوابة** | لا أداة جديدة | أمرك: «أنا أفضل عدم ذلك» | تُضاف لاحقًا عند طلبك |

**وما حُسم بلا سؤال:** بلا عمود «ما نستعمله منه»، وبلا ذكر «مبنيّ على AZenith»، وبلا ذكر
المشاريع المُزالة، وبلا ترجمة الأسماء.

---

## 9. حدود الصدق في هذه المواصفة

- **الرموز لا تُرى بالعين هنا:** لا متصفّح ولا مُصيِّر في هذه البيئة — الرمز الجديد يُقاس (هندسة)
  ويُقرأ نصًّا (ASCII)، و«هل يبدو جيدًا على GitHub» حكمك.
- **العدد ١٧ في جدول المحتويات** نصّ يُكتب بيد، ولا بوّابة تقيس عدد الأقسام — فلا شيء يمنعه من
  التقادم لاحقًا إلّا المراجعة.
- **بحث شروط المشاريع الأربعة** قُرئ من صفحاتها العامة (README/NOTICE/LICENSE) لا من نصّ قانوني،
  وهو **ملاحظة فضل لا رأي قانوني**؛ والمرجع القانوني يبقى `LICENSE` + `THIRD_PARTY_NOTICES.md`.

---

## 10. معيار القبول

1. `## Credits` في الصفحتين بنفس الجدول (٤ صفوف، ٣ أعمدة) وبلا كلمة «مبنيّ على».
2. صفّ ١٧ في جدولَي المحتويات، والعبارتان محدَّثتان.
3. `ic-credit.svg` مولَّد ومُشار إليه من **الصفحتين** (لا رمز يتيم).
4. `AnyKernel3` غير موجود في أي ملفّ من ملفّات المشروع.
5. المبدأ في `THIRD_PARTY_NOTICES.md` موفَّق، وADR-54 يسجّل القرار.
6. ٣٤ أمرًا في CI ⇒ `exit 0` · `readme_assets --self-test` 19/19 · `source_manifest --check` مطابق.
7. لا بناء (§0.1)، ويُقال ذلك في التسليم.

## 11. خارج النطاق

- أي واجهة داخل التطبيق (شاشة About/Credits) — لم تُطلب.
- لقطات الشاشة ومعرضها (٣٠ إطارًا ما زالت بانتظار جهاز).
- أي لغة ثالثة (الإنجليزية والعربية فقط — §0.2).
- أي تغيير في `core/**` أو العتاد أو الإقلاع.
