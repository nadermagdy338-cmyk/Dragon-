# Third-Party Notices — MaxManager

هذا الملف هو **القائمة الملزمة** لكل ما في هذا المستودع (أو ما يُبنى معه) وليس مملوكًا لصاحبه،
مع إشعاره وترخيصه. و`LICENSE` يحكم ما عدا ذلك وحده.

والقائمة **مقيسة لا مكتوبة من الذاكرة**: مصدرها `python3 tools/license_audit.py --json`
الذي يقرأ ترويسة كل ملف متعقَّب (١٩٧٩ ملفًا)، ويقرأ ترويسة ELF لكل ثنائية، وتتبعيات Gradle
(٤٢) وصناديق Cargo (١٠٨). وتُعاد كتابة الجدول بعد كل تغيير في المكوّنات — ومن قرأ رقمًا هنا
يمكنه إعادة اشتقاقه بالأمر نفسه.

## 1. مكوّنات برمجة مشمولة في الشجرة

| المكوّن | ما استُعمل منه | الترخيص | حقوق النشر |
| --- | --- | --- | --- |
| **Encore Tweaks** `github.com/Rem01Gaming/encore` | أساس خدمة `archdaemon/` (٤٠ ملفًا في الوحدة؛ منها ١٤ ملف C ومعه `mainfiles/verify.sh` و`AppMonitor.kt` **تحمل ترويسته صراحةً** — وهو مصدر الـ`APACHE_DERIVED` الستة عشر في التدقيق) | Apache-2.0 | Copyright (C) 2024-2025 Rem01Gaming |
| **Rianixia-ThermalCore** `github.com/ryanistr/Rianixia-ThermalCore` | محرّك الإدارة الحرارية `thermalcore/` (١٦ ملف Rust؛ منها ٣ تُسمّي أصله: `main.rs` · `monitor.rs` · `android_ffi.rs`) | Apache-2.0 | Copyright (C) 2025-2026 ryanistr |
| **Android Open Source Project** `source.android.com/license` | بنية مساحة المستخدم وسياسة SELinux؛ أدوات الإقلاع/الصور (`binutils/` · `android/aosp/`) | Apache-2.0 | Copyright (C) The Android Open Source Project |
| **KTweak** `github.com/tytydraco/KTweak` | **منهج** الضبط المبني على الدليل (لا كود مُنقول) | BSD-2-Clause | Copyright (C) tytydraco |
| **VMTouch** `github.com/hoytech/vmtouch` | **منهج** تثبيت الصفحات في الذاكرة (لا كود مُنقول) | BSD-3-Clause | Copyright (C) 2009 Doug Hoyte |
| **AnyKernel3** `github.com/osm0sis/AnyKernel3` | صيغة حزمة التثبيت (تُنفَّذ وقت التثبيت، لا تُوزَّع مع التطبيق) | BSD-3-Clause | Copyright (C) osm0sis |

## 2. مكتبات تُبنى معها (لا نسخ مصدري في المستودع)

تُقرأ من ملفات البناء والكتالوج، وتُراجَع عند كل تحديث إصدار:

- **تبعيات Gradle (٤٢)** — التوزيع المقيس: `Apache-2.0` (٣٦) · `MIT` (٤، تُشحن: `AndroidANSI` ·
  `compose-markdown` · `material-kolor`) · واثنتان **لا تُشحنان** فهما `testImplementation` وحدهما:
  `junit:junit` (`EPL-1.0`) و`org.json:json` (`Public-Domain`). وترخيص `EPL-1.0` **مُعلَن لا مسكوت
  عنه**: ضعيف الترابط وبمستوى الملف، والاعتماد عليه في نطاق الاختبار فقط.
  القائمة الكاملة بأسمائها وتراخيصها في `build/license-report.json` ← `gradle_dependencies`.
- **صناديق Cargo (١٠٨)** — التوزيع المقيس: `MIT OR Apache-2.0` (١٠٣)، `Apache-2.0` (٤)، و`Zlib` (١).
  القائمة في `build/license-report.json` ← `cargo_crates`.

## 3. ما أُزيل — وسُجّل ليبقى مُثبتًا

هذه مكوّنات كانت في الشجرة، و**أُزيلت** في جولات التنقية (`docs/PROVENANCE.md` §الإزالة):

- **Termux / termux-app** (GPL-3.0) — وحدتا `terminal-view` و`terminal-emulator`، والثنائية
  `libtermux.so`، وربطها في التطبيق والـCI. أُزيلت كاملة بأمر المالك.
- **HorizonKernelFlasher**, **KernelFlasher**, **Magisk/magiskboot** — وُحّدت في ميزة تفليش
  النواة التي أُزيلت بأمر المالك أيضًا.
- **ZKM (Zuan Kernel Manager)** (GPL-3.0) — الشاشات وأدوات مدير النواة التي كانت الأساس
  التاريخي لجزء من طبقة الواجهة. **أُعيد تأليفها** في المشروع، وشرط «لم يبقَ نصّ مشترك»
  مُقاس لا مُدَّعى: `python3 tools/upstream_similarity.py --assert` (انظر §٥).

## 4. بيانات المستودع المُعلَنة (كانت «بلا إسناد» — صارت مُقاسة)

ثلاثة ملفات بيانات لا ترويسة لها ولا تحمل اسنادًا خارجيًّا. وكانت في الجدول السابق بحالة
«أصل غير مُثبت». واليوم **قُيست** بدل أن تُدَّعى:

| الملف | يُقرأ من | الإسناد المُعلَن | الدليل المقيس |
| --- | --- | --- | --- |
| `manager/app/src/main/assets/devices.db` | `ui/util/DeviceNameUtil.kt` | MaxManager — رخصة المستودع | لا نظير له في أي أصل خارجي مُدقَّق؛ أُضيف في الالتزام الأول `581fe5d` |
| `manager/app/src/main/assets/socs.json` | `ui/util/HardwareUtil.kt` | MaxManager — رخصة المستودع | لا نظير له في أي أصل خارجي مُدقَّق؛ أُضيف في الالتزام الأول `581fe5d` |
| `maxmanagerApplist.json` | `MaxManagerPaths.APPLIST_JSON` | MaxManager — رخصة المستودع | لا نظير له في أي أصل خارجي مُدقَّق؛ أُضيف في الالتزام الأول `581fe5d` |

**كيف قُيس:** شجرة ZKM المرجعية نُزّلت وعددها ٤٤٣ ملفًا (`git ls-tree -r origin/main`)،
وبحثت فيها (وفي `zkm-raw` و`vtools-raw` و`vtools`) عن `socs` و`devices.db` و`Applist`:
**لم يُصَبْ منها شيء** (الوحيد المُطابق اسمًا: `ui/soc/SoCScreen.kt`، شاشة لا بيانات).
فالنتيجة المُعلَنة: بيانات يملكها المشروع، والرخصة رخصة المستودع.

**وحدّ هذا القياس:** «لا أصل خارجي **مُدقَّق**» ينفي ما فُحص لا كل شيء في العالم. وهو مثبت
في الأداة نفسها كجدول `DECLARED_DATA_ASSETS` بدليل مكتوب بجانب كل سطر (لا استثناء صامت)،
وبوابة `--self-test` تتحقّق أن ملفًا **غير** مُعلَن في الجدول لا يُمنح حالة `DECLARED`.
وإن ظهر يومًا أصل خارجي لهذه الملفات، فالسطر يُعدَّل ويُعاد القياس.

## 5. بوابات الإثبات (تُشغَّل، لا تُوصف)

```sh
python3 tools/license_audit.py --assert          # لا GPL في مسار الإصدار
python3 tools/license_audit.py --self-test       # يقيس الأداة نفسها
python3 tools/upstream_similarity.py --self-test # يقيس أداة المقابلة النصّية
python3 tools/upstream_similarity.py --assert \
    --upstream build/audit/zkm-raw --upstream build/audit/vtools-raw
```

والأخيرة تحتاج نسخة من الأصلين المرجعيين محليًّا (وغيابها **يُعلن «غير مُتحقَّقة» ولا يمرّ
صامتًا)، وطريقة جلبها مكتوبة في مخرجات الأداة نفسها عند غيابها.
