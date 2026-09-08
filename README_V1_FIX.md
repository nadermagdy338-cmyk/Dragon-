# Max Manager V1 — إصلاح فحص الحماية (is_kanged / version mismatch)

## اللي اتعمل
عدّلت 3 ملفات بس عشان الـ daemon (`sys.maxmanager-service`) يبطّل يقفل نفسه فورًا بعد التشغيل:

1. `mainfiles/module.prop`
   - `version=` → `V1`
   - `versionCode=` → `1`

2. `archdaemon/jni/include/AZenith.h`
   - `#define MODULE_VERSION ".placeholder"` → `#define MODULE_VERSION "V1"`

3. `archdaemon/jni/src/MaxManagerUtility/ModuleIntegrity.c`
   - فحص `is_kanged()` بقى بيطابق `name=Max Manager` و `author=Nader` (بدل القيم الأصلية `MaxManager火` / `ArchHaven Developers`)

دلوقتي الثلاثة مصادر (`module.prop`, `MODULE_VERSION`, وفحص `is_kanged`) متطابقين، فالـ daemon مفروض يعدّي الفحصين ويكمل شغله عادي.

## ⚠️ ملاحظة مهمة
**معنديش Android NDK في البيئة اللي بشتغل فيها**، فمقدرش أبني (compile) ملف الـ `sys.maxmanager-service` binary الجديد من هنا مباشرة. لازم تبنيه إنت على جهازك/الـ CI بتاعك. خطوات البناء تحت.

## خطوات البناء (على جهازك اللي فيه NDK)

استبدل الملفات التلاتة دي في مشروعك الأصلي بنفس المسارات، وبعدين:

### الطريقة 1: عن طريق الـ Makefile (clang standalone)
```bash
cd archdaemon/jni
# لازم يكون معاك NDK وتضيف الـ standalone toolchain لـ PATH، مثال:
export PATH=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH
make clean
make
# الناتج: archdaemon/jni/sys.maxmanager-service
```

### الطريقة 2: عن طريق ndk-build
```bash
cd archdaemon
$ANDROID_NDK_HOME/ndk-build
# الناتج جوه: archdaemon/libs/arm64-v8a/sys.maxmanager-service
```

## إعادة التغليف والفلاش

1. حط الـ binary الجديد مكان القديم داخل `libs/arm64-v8a/sys.maxmanager-service` (أو `armeabi-v7a` لو محتاج)، جوه الزيب اللي بتفلشه (مش زيب المشروع اللي بعتهولي — ده زيب فلاشابل منفصل فيه `META-INF/`, `libs/`, و `.sha256` لكل binary زي ما موضّح في `verify.sh`).
2. **مهم:** لازم تعمل تحديث لملف `.sha256` بتاع الـ binary الجديد، لأن `verify.sh` بيرفض التثبيت لو الـ checksum مش مطابق:
   ```bash
   sha256sum libs/arm64-v8a/sys.maxmanager-service | awk '{print $1}' > libs/arm64-v8a/sys.maxmanager-service.sha256
   ```
3. حدّث `mainfiles/module.prop` (اللي جوه زيب الفلاش) بنفس النسخة المعدّلة هنا (`version=V1`).
4. اعمل زيب للفلاشابل تاني وفلاشه من جديد.
5. بعد الريبووت، تأكد:
   ```
   pidof sys.maxmanager-service
   cat /data/adb/.config/MaxManager/debug/MaxManager.log
   ```
   لو ظهر PID ومفيش رسالة `Module modified by 3rd party` أو `version mismatch` في اللوج → المشكلة اتحلت.

## لو عايز ترفع الإصدار بعدين
أي إصدار جديد لازم تحافظ على تطابق الثلاثة أماكن دي مع بعض:
- `mainfiles/module.prop` → `version=X`
- `archdaemon/jni/include/AZenith.h` → `MODULE_VERSION "X"`
- إعادة بناء الـ binary بعد أي تعديل في `AZenith.h`

أفضل حل دائم: خلي خطوة بناء تلقائية (Makefile/CI) تولّد `AZenith.h`'s `MODULE_VERSION` من ملف `version` نفسه بدل ما تكتبها يدوي مرتين، عشان منستحملش ننسى نطابقهم تاني في المستقبل.
