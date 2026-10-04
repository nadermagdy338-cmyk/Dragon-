#!/usr/bin/env python3
"""عقد مؤثّر MaxFx: الرموز الخمسة وتثبيت اللقطات وإغلاق التضمين — بلا مُصرّف.

لماذا وُجدت
-----------
غلاف AIDL (`maxfx/src/maxfx_aidl.cpp`) لا يُصرَّف في هذه البيئة (لا NDK ولا SDK)، ويُترجم
أوّل مرّة في CI. وانتظارُ CI ليكتشف **عطبًا بنيويًّا** كلفته دقائق لكل دورة — وأسوأ منه عطبٌ
**لا** يُمسكه المُصرّف أصلًا. وأربعة أصناف هنا لا يكشفها شيء آخر:

1. **رمزٌ باسمٍ أو توقيعٍ غير المنصوصّ** — `dlsym` يبحث عن الاسم بالحرف، واسمٌ ينقصه محرفٌ
   واحد يُعطي **نفس** نتيجة الغياب: `not exist in library`، والمكتبة تُرمى. والمُصرّف صامت:
   الدالة تُصرَّف وتموت بلا مُستدعٍ. **ومنه نطاقُ الأسماء:** أصناف AIDL المولَّدة تعيش تحت
   `::aidl::android::…`، وكتابة `::android::…` مكانها عطبُ ترجمةٍ محض — قِيس في `37027017557`
   (‏`error: no member named 'media' in namespace 'android'`).
2. **`AELI` حُذف سهوًا** أثناء إضافة الثلاثة — أثره إسقاطُ كل جهازٍ يعمل اليوم (ADR-66/18).
3. **لقطةٌ مجمَّدة مبتورة أو مُعدّلة** — وهذا **وقع فعلًا**: جلبٌ شبكيّ فاشل ترك ملفّين فارغين
   (`0` و`6` بايت) في الشجرة بلا أن يمسكهما شيء، لأنّ `kt_balance` يقيس التوازن (ملفٌّ فارغ
   متوازن) و`code_health` يقيس النظافة (ولا نصّ فيه). والحكم هنا هو حكم
   `fetch_maxfx_aidl.verdict` نفسه — مصدرٌ واحد لا حكمان ينحرفان.
4. **تضمينٌ لا يُحلّ** — شجرة `third_party/libfmq` مقتطعةٌ عن قصد، ورأسٌ ناقص فيها يُسقط
   البناء برسالة لا تدلّ على سببه. والقياس هنا يُسمّي الرأس الغائب **قبل** أن يبدأ البناء.
5. **رأسٌ منسوخٌ ناقص أو مظلِّل** — رؤوس الـC++ لـ`libbinder_ndk` منسوخة في
   `third_party/libbinder_ndk_cpp` لأنّ الـNDK لم يعد يُشحَنها. وعطبٌ هنا **لا يُشبه** عطبَ
   `libfmq`: رأسٌ فارغ أو ناقص يُسقط الترجمة برسالة في ملفٍّ **مُولَّد** لا يملكه أحد؛ ورأسٌ
   **زائد** يُظلّل رأس الـNDK نفسه (فننسخ طبقةً بأكملها بدل رقعةٍ فقط) — وهو خطرٌ صامت.

وما لا تقيسه هذه البوابة (حدّ مُعلن): لا تتحقّق من صحّة الأنواع ولا من تسلسل الـbinder (يحتاج
مُصرّفًا)، ولا من أنّ الرمز يُصدَّر فعلًا في `.so` (يحتاج بناءً — وهو ما يقيسه
`readelf --dyn-syms` في خطوة البناء نفسها). تُقاس **البنية** لا السلوك.

الصيغة
------
    python3 tools/maxfx_contract.py --assert      # سطر حكم واحد، وتُخرج بخطأ عند عطب
    python3 tools/maxfx_contract.py               # التفصيل كاملًا
    python3 tools/maxfx_contract.py --self-test   # يقيس الأداة على حالات معلومة النتيجة

ولا تكتب الأداة شيئًا ولا تُعدّل كودًا — تقرأ وتُبلّغ فقط (ADR-18).
"""

from __future__ import annotations

import argparse
import os
import re
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
_REPO = os.path.dirname(_HERE)
if _HERE not in sys.path:
    sys.path.insert(0, _HERE)

# الحكم على اللقطة **واحد**: يُستورد من أداة الجلب لا يُعاد كتابته (مصدران ينحرفان = حكمان).
import fetch_maxfx_aidl  # noqa: E402

# ── التوقيعات المنصوصّة، من
#    `hardware/interfaces/audio/aidl/default/include/effect-impl/EffectTypes.h` ──
#
# لكل رمزٍ: عائده، ولواحق أنواع وسائطه (بعد نزع الاسم) — تُقارَن باللاحقة لا بالنصّ الكامل حتى
# لا يهبط الفحص على اختيار `std::` مقابل `::std::`. وترتيبها هو ترتيب نداء
# `EffectFactory.cpp` نفسه: `queryEffectFunc(&uuid, desc)` · `createEffectFunc(&uuid, &sp)` ·
# `destroyEffectFunc(sp)`.
EXPECTED_SYMBOLS = {
    "createEffect": {
        "return": "binder_exception_t",
        "params": ("AudioUuid*", "shared_ptr<", "IEffect>*"),
    },
    "queryEffect": {
        "return": "binder_exception_t",
        "params": ("AudioUuid*", "Descriptor*"),
    },
    "destroyEffect": {
        "return": "binder_exception_t",
        "params": ("shared_ptr<", "IEffect>&"),
    },
}

CI_SYMBOLS = ("AELI", "createEffect", "queryEffect", "destroyEffect")

# نطاقاتٌ لا تصحّ في الغلاف — والأصناف المولَّدة من AIDL لا تعيش فيها.
#
# وليست القاعدة «احظر `::android::`»: نطاق `android` **صحيح** لِما يأتي من `libfmq`
# (`AidlMessageQueue` · `hardware::EventFlag`) ولِما تُوفّره بُدَلنا (`status_t` · `OK`) —
# فالقاعدة تُسمّي ما لا يصحّ بعينه: `::android::media::…` (حزمة `android.media.audio.common`)
# و`::android::hardware::audio::…` (حزمة `android.hardware.audio.effect`) — وكلتاهما مولَّدة.
#
# ⚠️ والقياس بـ**استحضارٍ سابق** (`(?<!aidl)`) لا ببحثٍ عن النصّ: لأنّ `::aidl::android::media::`
# **يحتوي** `::android::media::` — وهو الخطأ الذي وقع في أوّل كتابة لهذا الفحص، وأمسكه
# الفحص الذاتي قبل أن يصل إلى CI.
FORBIDDEN_NAMESPACES = re.compile(r"(?<!aidl)::android::(?:media::|hardware::audio::)")

# ووجهٌ آخر لنفس الفخّ: تبديلٌ آليّ عشوائيّ يكتب `::aidl::` داخل `::aidl::` فيُنتج
# `::aidl::aidl::…` — وهذا **وقع فعلًا** في أوّل إصلاح لهذا العطب (التشغيل `37027906076`:
# `error: no member named 'aidl' in namespace 'aidl'`) ، فصار يُمتحن.
DOUBLED_AIDL = re.compile(r"::aidl::aidl::")

# مسار الرأس المولَّد — ثلاثة مواضع تشير إليه (`gen.sh` · `Android.mk` · CI)، وانحراف واحد
# بينها يعني بناءً لا يجد رؤوسه.
GENERATED_REL = "gen/aidl/android/hardware/audio/effect/BnEffect.h"

# ومصدر الواجهة المُولَّد — باسم النوع لا باسم الأصناف بداخله (‏`GenerateNdk` يكتب ثلاثة
# رؤوس في `--header_out` ومصدرًا واحدًا في `--out`، و`GetOutputFilePath` تُعطي `.cpp`
# لأنّ `IsCppOutput()` تضمّ `NDK`).
IFACE_SRC_REL = "gen/aidl/android/hardware/audio/effect/IEffect.cpp"

# ── تضمينٌ: الأبواب التي نبني منها، والمصطلحات الموحَّدة ──
# ── رؤوس الـC++ لـ`libbinder_ndk`: منسوخة عندنا لأنّ الـNDK يُشحَن واجهة C فقط ──
#
# القائمة **مقيسة** لا مُختارة: اجتماع ما تُضمِّنه دالّة `GenerateHeaderIncludes` في
# `system/tools/aidl/generate_ndk.cpp` (السطور ٢٩٢–٣٩٨) مع إغلاق تضمينه التعدّي في AOSP —
# ناقصًا ما يُشحَنه الـNDK فعلًا (قِيس بمسح فهرس حزمة r29).
BINDER_CPP_ROOT = "maxfx/third_party/libbinder_ndk_cpp"
BINDER_CPP_HEADERS = (
    "binder_interface_utils.h",
    "binder_auto_utils.h",
    "binder_stability.h",
    "binder_enums.h",
    "binder_internal_logging.h",
    "binder_parcel_utils.h",
    "binder_parcelable_utils.h",
    "binder_to_string.h",
)
# ما يُشحَنه الـNDK نفسه في `sysroot/usr/include/android/` — نسخُه عندنا **ظلٌّ** لا رقعة.
# (و`binder_shell.h` معها وإن لم يُشحَنه الـNDK: هو يُضمَّن بـ`__has_include` عندنا، فنسخُه
#  يُشعِل مسار `dumpsys` لخدمةٍ لسنا هي — فغيابه المقصود يُحرَس هنا أيضًا.)
BINDER_NDK_SHIPPED = frozenset("""
binder_ibinder.h binder_ibinder_jni.h binder_parcel.h binder_parcel_jni.h binder_status.h
persistable_bundle.h binder_shell.h
""".split())

WRAPPER_REL = "maxfx/src/maxfx_aidl.cpp"
FMQ_ROOT = "maxfx/third_party/libfmq"
FMQ_ENTRY_POINTS = ("fmq/AidlMessageQueue.h", "fmq/EventFlag.h")
FMQ_ROOTS = ("include", "base", "compat")

# مكتبة C/C++ القياسيّة — تُوفّرها `libc++_shared`/البينيون، ولا تُنسخ.
STDLIB = frozenset("""
algorithm array assert.h atomic cerrno cmath codecvt condition_variable cstddef cstdint cstdio
cstdlib cstring ctime errno.h functional iterator limits locale map memory mutex new optional
pthread.h set sstream stdarg.h stddef.h stdint.h stdlib.h string string.h string_view
sys/mman.h sys/syscall.h sys/types.h sys/user.h sys/cdefs.h thread time.h type_traits
unistd.h vector linux/futex.h iostream syslog.h
""".split())

# بادئات تُوفّرها **البيئة** (الـNDK أو التوليد) لا الشجرة المنسوخة.
#
# ⚠️ و`android-base/` و`utils/` و`cutils/` **ليست** منها عن قصد: هي بالضبط ما يوفّره
# `compat/` عندنا، فرأسٌ منها لا يُحلّ في الشجرة = **بديلٌ ناقص** — وهو العطب الذي يجب أن
# يُمسك، لا عطبٌ يُعفى عنه. (أوّل كتابة لهذه القائمة ضمّتها، فمرّ عطبٌ مغروس في الفحص الذاتي
# — وهو الفحص الذي كشف الخطأ.)
ALLOWED_PREFIXES = ("android/", "binder/", "ndk/", "aidl/")


def _read(path: str) -> str:
    try:
        with open(path, "r", encoding="utf-8", errors="replace") as fh:
            return fh.read()
    except OSError:
        return ""


def _strip_params(text: str) -> str:
    """يُزيل أسماء الوسائط ويُوحّد الفراغات — فيبقى **النوع** وحده قابلًا للمقارنة."""
    text = re.sub(r"//.*", "", text)
    out = []
    for chunk in text.split(","):
        chunk = chunk.strip()
        if not chunk:
            continue
        parts = chunk.split()
        while parts and "::" not in parts[-1] and not parts[-1].endswith(("*", "&", ">")):
            parts.pop()
        out.append("".join(parts))
    return ",".join(out)


def check_symbols(text: str) -> list[str]:
    """الرموز الثلاثة: `extern "C"` · العائد · اللواحق بالترتيب."""
    problems = []
    if not text:
        return [f"ملفّ الغلاف غائب أو فارغ ({WRAPPER_REL})"]

    for name, spec in EXPECTED_SYMBOLS.items():
        pattern = re.compile(
            r'extern\s+"C"\s+' + re.escape(spec["return"]) + r"\s+" + name + r"\s*\((.*?)\)\s*\{",
            re.S,
        )
        matches = pattern.findall(text)
        if not matches:
            problems.append(f"الرمز {name}: غائب أو عائده ليس {spec['return']} داخل extern \"C\"")
            continue
        if len(matches) > 1:
            problems.append(f"الرمز {name}: مُعرَّف {len(matches)} مرّات")
        flat = _strip_params(matches[0])
        for suffix in spec["params"]:
            if suffix not in flat:
                problems.append(f"الرمز {name}: «{suffix}» غائبة عن توقيعه ⇒ {flat}")
    return problems


def check_namespaces(text: str) -> list[str]:
    """نطاقات الأصناف المولَّدة في الغلاف: تُقاس بـ`::aidl::` لا تُخمَّن بالتذكُّر."""
    problems = []
    if not text:
        return [f"ملفّ الغلاف غائب أو فارغ ({WRAPPER_REL})"]
    for lineno, line in enumerate(text.splitlines(), 1):
        code = line.split("//", 1)[0]
        for match in FORBIDDEN_NAMESPACES.finditer(code):
            problems.append(
                f"{WRAPPER_REL}:{lineno}: «{match.group(0)}…» بلا `aidl` — "
                "والأصناف المولَّدة تعيش تحت ::aidl::android::")
        if DOUBLED_AIDL.search(code):
            problems.append(f"{WRAPPER_REL}:{lineno}: «::aidl::aidl::» — نطاقٌ مُضاعف")
    return problems


def check_aeli(header_text: str, source_text: str) -> list[str]:
    """`AELI` يبقى: العقد المزدوج **يضيف** ولا يُبدّل (ADR-18/66)."""
    problems = []
    if not re.search(r"extern\s+audio_effect_library_t\s+AELI\s*;", header_text):
        problems.append("`AELI` لم تُعلَن في maxfx/src/maxfx_effect.h")
    if not re.search(r"audio_effect_library_t\s+AELI\s*=", source_text):
        problems.append("`AELI` لم تُعرَّف في maxfx/src/maxfx_effect.c ⇒ أجهزة HIDL تسقط")
    return problems


def check_snapshots(snapshots: dict[str, dict[str, bytes]]) -> list[str]:
    """اللقطات: العدد المثبَّت، وحكمُ القبول لكل ملفّ (حجم · `package` · `IMMUTABLE` · قوس)."""
    problems = []
    for rel, (expected, files) in sorted(snapshots.items()):
        # بنية المدخل: (العدد المرجعيّ، ملفّات اللقطة)
        if len(files) != expected:
            problems.append(
                f"{rel}: {len(files)} ملفًّا والمرجع {expected} — نقصٌ يعني رأسًا سقط من الشحن، "
                "وزيادةٌ نسخةً غير التي كُتب بها الإطار"
            )
        for name, payload in sorted(files.items()):
            reason = fetch_maxfx_aidl.verdict(name, payload)
            if reason:
                problems.append(f"{rel}/{name}: {reason}")
    return problems


def _fmq_keys(files: dict[str, str]) -> dict[str, str]:
    """مفتاح كل رأس منسوخ كما يُطلَب في `#include`: `include/fmq/x.h` ⇒ `fmq/x.h`."""
    keys = {}
    for rel in files:
        tail = rel.split(FMQ_ROOT + "/", 1)[-1]
        for root in FMQ_ROOTS:
            if tail.startswith(root + "/"):
                keys[tail[len(root) + 1:]] = rel
    return keys


def _resolve_closure(keys: dict[str, str], files: dict[str, str], entries: tuple[str, ...]):
    """يمشي إغلاق التضمين من الأبواب المُعلنة — ويُعيد (المزوَّد, المشاكل)."""
    provided: dict[str, str] = {}
    problems: list[str] = []
    seen: set[str] = set()
    queue = list(entries)

    def resolve_key(header: str, from_rel: str) -> str | None:
        """مفتاح الرأس كما يُطلَب: مطلقًا في الشجرة، أو نسبةً إلى مجلّد المُضمِّن."""
        if header in keys:
            return header
        tail = from_rel.split(FMQ_ROOT + "/", 1)[-1]
        for root in FMQ_ROOTS:
            if not tail.startswith(root + "/"):
                continue
            local = tail[len(root) + 1:]
            candidate = os.path.normpath(os.path.join(os.path.dirname(local), header))
            candidate = candidate.replace(os.sep, "/")
            if candidate in keys:
                return candidate
        return None

    while queue:
        key = queue.pop()
        if key in seen:
            continue
        seen.add(key)
        rel = keys.get(key)
        if rel is None:
            problems.append(f"رأسٌ مطلوب ولا يُوجد في الشجرة المنسوخة: {key}")
            continue
        provided[key] = rel
        for header in re.findall(r'#include\s+[<"]([^">]+)[">]', files.get(rel, "")):
            resolved = resolve_key(header, rel)
            if resolved is not None:
                queue.append(resolved)
                continue
            if header in STDLIB or header.startswith(ALLOWED_PREFIXES + ("aidl/",)):
                continue
            problems.append(f"{rel}: التضمين «{header}» لا يُحلّ في الشجرة المنسوخة")
    return provided, problems


def check_fmq_includes(files: dict[str, str]) -> list[str]:
    """إغلاق تضمين `libfmq` من بابينا الفعليّين — لا من كل ملفّ منسوخ (فبعضه غير مستعمل)."""
    keys = _fmq_keys(files)
    _provided, problems = _resolve_closure(keys, files, FMQ_ENTRY_POINTS)
    return problems


def _active_includes(text: str, resolvable) -> list[str]:
    """التضمينات الفعّالة: يتخطّى ما داخل `#if __has_include(<X>)` وهو غير موجود.

    وسببها مقيس: `binder_to_string.h` (AOSP) يُضمِّن `utils/StrongPointer.h` و`binder/IBinder.h`
    داخل حرّاس `__has_include` — وهي رؤوس `libutils`/`libbinder` لا تُشحَن في الـNDK، فالكتلة
    **مُطفأة بإرادتها**. وعدّها عطبًا كان يُسقط البوابة على شجرةٍ سليمة (وقَع فعلًا في أوّل
    تشغيل لهذا الفحص)، فتخطّيها هو القراءة الصحيحة لا تسامحًا.
    """
    out: list[str] = []
    skip: list[bool] = []
    for line in text.splitlines():
        s = line.strip()
        m = re.match(r"#\s*if\s+__has_include\s*\(\s*[<\"]([^\">]+)[\">]", s)
        if m:
            skip.append(not resolvable(m.group(1)))
            continue
        if re.match(r"#\s*(if|ifdef|ifndef)\b", s):
            skip.append(skip[-1] if skip else False)
            continue
        if re.match(r"#\s*else\b", s):
            if skip:
                skip[-1] = not skip[-1]
            continue
        if re.match(r"#\s*endif\b", s):
            if skip:
                skip.pop()
            continue
        if skip and skip[-1]:
            continue
        m = re.match(r"#\s*include\s*[<\"]([^\">]+)[\">]", s)
        if m:
            out.append(m.group(1))
    return out


def check_binder_cpp(files: dict[str, str], android_mk: str) -> list[str]:
    """الرؤوس المنسوخة: كاملة غير فارغة · بلا ظلٍّ على الـNDK · وتضمينها يُحلّ."""
    problems = []
    have = {rel.rsplit("/", 1)[-1] for rel in files}

    def resolvable(header: str) -> bool:
        """هل يُوجد هذا الرأس فعلًا عند البناء؟ شجرتنا أو الـNDK أو libc++."""
        if header.startswith("android/"):
            base = header[len("android/"):]
            return base in have or base in BINDER_NDK_SHIPPED
        return header in STDLIB or header.startswith(("c++/", "linux/", "sys/"))


    for name in BINDER_CPP_HEADERS:
        rel = f"{BINDER_CPP_ROOT}/android/{name}"
        text = files.get(rel, "")
        if not text:
            problems.append(f"رأسٌ منسوخ غائب أو فارغ: {rel}")
            continue
        if len(text) < 400:
            problems.append(f"{rel}: {len(text)} بايت — أقصر من أن يكون رأسًا حقيقيًّا")
        if "Apache License" not in text:
            problems.append(f"{rel}: لا يحمل رأس ترخيص Apache-2.0 (نسبة الفضل شرط النسخ)")
        if "#pragma once" not in text:
            problems.append(f"{rel}: بلا `#pragma once` ⇒ تضمينٌ مزدوج يُعيد التعريف")

    for rel in files:
        name = rel.rsplit("/", 1)[-1]
        if name in BINDER_NDK_SHIPPED:
            problems.append(f"{rel}: يُظلّل رأسًا يُشحَنه الـNDK — المنسوخ رقعةٌ لا طبقة")

    # الإغلاق: كلّ تضمين **فعّال** يُحلّ في شجرتنا أو في الـNDK أو في libc++
    for rel, text in sorted(files.items()):
        for header in _active_includes(text, resolvable):
            if header.startswith("android/"):
                base = header[len("android/"):]
                if base not in have and base not in BINDER_NDK_SHIPPED:
                    problems.append(f"{rel}: التضمين «{header}» لا يُحلّ (لا فينا ولا في الـNDK)")
                continue
            if not resolvable(header):
                problems.append(f"{rel}: التضمين «{header}» لا يُحلّ")

    # والبناء يشير إلى الشجرة
    if "MAXFX_BINDER_NDK_CPP" not in android_mk:
        problems.append("Android.mk: لا يُضيف مسار الرؤوس المنسوخة (MAXFX_BINDER_NDK_CPP)")
    return problems


def check_wrapper_includes(text: str, fmq_files: dict[str, str], project: set[str]) -> list[str]:
    """تضمينات غلافنا: المشروع · رؤوس `libfmq` · AIDL المولَّد · النظام — وما عداها عطب."""
    if not text:
        return [f"غلافٌ غائب: {WRAPPER_REL}"]
    fmq_keys = set(_fmq_keys(fmq_files))
    problems = []
    for header in re.findall(r'#include\s+[<"]([^">]+)[">]', text):
        if header.startswith("aidl/"):
            continue  # مولَّد من `maxfx/aidl` بـ`aidl --lang=ndk`
        if header in fmq_keys:
            continue
        if header in project:
            continue
        if header in STDLIB or header.startswith(ALLOWED_PREFIXES):
            continue
        problems.append(f"{WRAPPER_REL}: التضمين «{header}» لا يُحلّ (لا في المشروع ولا libfmq)")
    return problems


def check_build(android_mk: str, gen_sh: str, app_mk: str, workflow: str,
                gitignore: str) -> list[str]:
    """اتّساق البناء: الرأس المولَّد، والرموز المقيسة، والأعمدة، والتجاهل."""
    problems = []

    # `Android.mk` يبنيه من `$(MAXFX_AIDL_GEN)` ⇒ يُقاس **اللاحقة المشتركة** لا السلسلة الكاملة.
    binder_suffix = "android/hardware/audio/effect/BnEffect.h"
    if binder_suffix not in android_mk or "MAXFX_AIDL_GEN" not in android_mk:
        problems.append(f"Android.mk: لا يُشير إلى الرأس المولَّد ({binder_suffix}) عبر MAXFX_AIDL_GEN")
    if GENERATED_REL not in workflow:
        problems.append("workflow: لا يقيس وجود الرأس المولَّد ⇒ قد يُبنى بلا الغلاف بلا إنذار")
    # ومصادر الحزمة المُولَّدة: الرؤوس تُكتَب ببادئة `aidl/` (‏`--header_out`، والمولِّد يضع
    # البادئة)، والمصادر من `GetOutputFilePath` التي تبني من اسم الحزمة وحده (‏`--out`، بلا
    # بادئة) — **مجلّدان مختلفان**. وتركها عطبُ **ربطٍ** لا ترجمة: قِيس في `37031374179`
    # `undefined symbol: BnEffect::createBinder()`، وقِيس في `37034110669` أنّ موضعًا واحدًا
    # مكتوبًا بالاسم **ليس كافيًا** (بحثنا في موضعٍ ووجدنا الآخر). فالمطلوب في `Android.mk`
    # بحثٌ بـ`wildcard` على الموضعين، لا اسمٌ واحد.
    if "MAXFX_AIDL_PKG_DIRS" not in android_mk or "wildcard" not in android_mk:
        problems.append(
            "Android.mk: لا يُصرّف مصادر الرزم المُولَّدة بحثًا (wildcard) ⇒ رابطٌ بلا تعريف")
    # والرزم **أربع** لا واحدة: قِيس في `37034950650` أنّ حزمة `effect` وحدها تترك ربطًا ناقصًا
    # (‏`NativeHandle` و`GrantorDescriptor` و`AudioUuid` تُعرَّف في رزمها).
    for pkg in ("hardware/audio/effect", "hardware/common/fmq", "hardware/common",
                "media/audio/common"):
        if pkg not in android_mk:
            problems.append(f"Android.mk: رزمة مُولَّدة غير مُصرَّفة: {pkg}")
    if "--lang=ndk" not in gen_sh:
        problems.append("gen.sh: لا يستعمل `--lang=ndk` ⇒ رؤوس C++ لا تصلح لمكتبة NDK")

    block = ""
    idx = workflow.find("symbols=")
    if idx != -1:
        block = workflow[idx:idx + 1500]
    for sym in CI_SYMBOLS:
        if sym not in block:
            problems.append(f"workflow: بوابة الرموز لا تذكر {sym}")

    for abi in ("arm64-v8a", "armeabi-v7a"):
        if abi not in app_mk:
            problems.append(f"Application.mk: العمود {abi} غائب")
    if not re.search(r"APP_STL\s*:=\s*c\+\+_shared", app_mk):
        problems.append("Application.mk: APP_STL ليست c++_shared (libbinder_ndk مبنيّة عليها)")

    if "maxfx/gen/" not in gitignore:
        problems.append(".gitignore: لا يُتجاهل maxfx/gen/ (مخرجات توليد)")
    if re.search(r"^maxfx/aidl/", gitignore, re.M):
        problems.append(".gitignore: يُتجاهل maxfx/aidl/ وهي **مصدر مشحون** لا مخرَج")

    return problems


def collect(repo: str) -> dict:
    aidl_root = os.path.join(repo, "maxfx", "aidl")
    fmq_root = os.path.join(repo, FMQ_ROOT)

    snapshots: dict[str, dict[str, bytes]] = {}
    for rel, (_gs, expected) in fetch_maxfx_aidl.PACKAGES.items():
        root = os.path.join(repo, rel)
        files: dict[str, bytes] = {}
        if os.path.isdir(root):
            for name in sorted(os.listdir(root)):
                if name.endswith(".aidl"):
                    with open(os.path.join(root, name), "rb") as fh:
                        files[name] = fh.read()
        snapshots[rel] = (expected, files)

    fmq: dict[str, str] = {}
    for dirpath, _dirs, names in os.walk(fmq_root):
        for name in names:
            if name.endswith((".h", ".hpp", ".cpp")):
                full = os.path.join(dirpath, name)
                fmq[os.path.relpath(full, repo).replace(os.sep, "/")] = _read(full)

    binder_cpp: dict[str, str] = {}
    cpp_root = os.path.join(repo, BINDER_CPP_ROOT, "android")
    if os.path.isdir(cpp_root):
        for name in sorted(os.listdir(cpp_root)):
            if name.endswith((".h", ".hpp")):
                full = os.path.join(cpp_root, name)
                binder_cpp[os.path.relpath(full, repo).replace(os.sep, "/")] = _read(full)

    project = {"maxfx_dsp.h", "maxfx_effect.h", "maxfx_props.h", "maxfx_effect_abi.h"}

    return {
        "symbols_text": _read(os.path.join(repo, WRAPPER_REL)),
        "aeli_header": _read(os.path.join(repo, "maxfx", "src", "maxfx_effect.h")),
        "aeli_source": _read(os.path.join(repo, "maxfx", "src", "maxfx_effect.c")),
        "snapshots": snapshots,
        "fmq": fmq,
        "binder_cpp": binder_cpp,
        "project": project,
        "android_mk": _read(os.path.join(repo, "maxfx", "jni", "Android.mk")),
        "gen_sh": _read(os.path.join(repo, "maxfx", "aidl", "gen.sh")),
        "app_mk": _read(os.path.join(repo, "maxfx", "jni", "Application.mk")),
        "workflow": _read(os.path.join(repo, ".github", "workflows", "build.yml")),
        "gitignore": _read(os.path.join(repo, ".gitignore")),
        "_aidl_root": aidl_root,
    }


def audit(data: dict) -> list[tuple[str, list[str]]]:
    return [
        ("الرموز الثلاثة وتوقيعاتها", check_symbols(data["symbols_text"])),
        ("نطاقات الأسماء المولَّدة (::aidl::)", check_namespaces(data["symbols_text"])),
        ("بقاء AELI (العقد المزدوج)", check_aeli(data["aeli_header"], data["aeli_source"])),
        ("اللقطات المجمَّدة (حجم · package · IMMUTABLE)",
         check_snapshots(data["snapshots"])),
        ("إغلاق تضمين libfmq", check_fmq_includes(data["fmq"])),
        ("رؤوس libbinder_ndk المنسوخة",
         check_binder_cpp(data["binder_cpp"], data["android_mk"])),
        ("تضمينات غلافنا",
         check_wrapper_includes(data["symbols_text"], data["fmq"], data["project"])),
        ("اتّساق البناء والأعمدة",
         check_build(data["android_mk"], data["gen_sh"], data["app_mk"], data["workflow"],
                     data["gitignore"])),
    ]


# ── الفحص الذاتي: كل عطبٍ مغروس يجب أن **يُمسك**، وكل نصٍّ سليم يجب أن **يمرّ** ──

def _snapshot_fixture() -> dict[str, dict[str, bytes]]:
    """لقطات مصغّرة سليمة بأعدادها المثبَّتة (المحتوى مُصنَّع، الحكم هو المقيس)."""
    good = b"// IMMUTABLE\npackage android.x;\nparcelable X {\n}\n" + b" "*220
    out = {}
    for rel, (_gs, expected) in fetch_maxfx_aidl.PACKAGES.items():
        out[rel] = (expected, {f"{i}.aidl": good for i in range(expected)})
    return out


def _fmq_fixture() -> dict[str, str]:
    return {
        f"{FMQ_ROOT}/include/fmq/AidlMessageQueue.h":
            '#include <aidl/a/b/C.h>\n#include "AidlMessageQueueBase.h"\n',
        f"{FMQ_ROOT}/include/fmq/AidlMessageQueueBase.h":
            '#include <cutils/native_handle.h>\n#include <fmq/MessageQueueBase.h>\n',
        f"{FMQ_ROOT}/include/fmq/MessageQueueBase.h":
            "#include <android-base/unique_fd.h>\n#include <cutils/ashmem.h>\n"
            "#include <utils/Log.h>\n#include <functional>\n",
        f"{FMQ_ROOT}/include/fmq/EventFlag.h": "#include <utils/Errors.h>\n#include <atomic>\n",
        f"{FMQ_ROOT}/compat/cutils/native_handle.h": "#include <sys/cdefs.h>\n",
        f"{FMQ_ROOT}/compat/cutils/ashmem.h": "#include <android/sharedmem.h>\n",
        f"{FMQ_ROOT}/compat/android-base/unique_fd.h": "#include <unistd.h>\n",
        f"{FMQ_ROOT}/compat/utils/Log.h": "#include <android/log.h>\n",
        f"{FMQ_ROOT}/compat/utils/Errors.h": "#include <errno.h>\n",
    }


def _binder_cpp_fixture() -> dict[str, str]:
    """رؤوس منسوخة مصغّرة سليمة (المحتوى مُصنَّع، لكن شروط الحكم هي المقيسة)."""
    out = {}
    for name in BINDER_CPP_HEADERS:
        out[f"{BINDER_CPP_ROOT}/android/{name}"] = (
            "/*\n * Copyright (C) 2015 The Android Open Source Project\n"
            " * Licensed under the Apache License, Version 2.0\n */\n"
            "#pragma once\n#include <android/binder_ibinder.h>\n" + "// padding\n" * 50
        )
    return out


def self_test() -> int:
    failures: list[str] = []

    def expect(name: str, problems: list[str], should_fail: bool) -> None:
        if should_fail and not problems:
            failures.append(f"لم يُمسك العطب المغروس: {name}")
        if not should_fail and problems:
            failures.append(f"سقط على نصٍّ سليم: {name} ⇒ {problems[0]}")

    symbols = (
        'extern "C" binder_exception_t createEffect(\n'
        '        const ::aidl::android::media::audio::common::AudioUuid* uuid,\n'
        '        std::shared_ptr<::aidl::android::hardware::audio::effect::IEffect>* out) {\n'
        '    return EX_NONE;\n'
        '}\n'
        'extern "C" binder_exception_t queryEffect(\n'
        '        const ::aidl::android::media::audio::common::AudioUuid* uuid,\n'
        '        ::aidl::android::hardware::audio::effect::Descriptor* desc) {\n'
        '    return EX_NONE;\n'
        '}\n'
        'extern "C" binder_exception_t destroyEffect(\n'
        '        const std::shared_ptr<::aidl::android::hardware::audio::effect::IEffect>& sp) {\n'
        '    return EX_NONE;\n'
        '}\n'
    )

    # (١) الرموز
    expect("رموز سليمة", check_symbols(symbols), False)
    expect("اسم منقوص", check_symbols(symbols.replace("queryEffect", "queryEffects")), True)
    expect("عائد خاطئ",
           check_symbols(symbols.replace('extern "C" binder_exception_t queryEffect',
                                         'extern "C" int queryEffect')), True)
    expect("وسيطٌ ناقص",
           check_symbols(symbols.replace(
               "        ::aidl::android::hardware::audio::effect::Descriptor* desc) {",
               "        int desc) {")), True)
    expect("تعريفٌ مكرّر", check_symbols(symbols * 2), True)

    # (١-ب) نطاقات الأسماء
    ns_good = ("size_t f(const ::aidl::android::media::audio::common::AudioUuid& u) {\n"
               "    auto q = ::android::AidlMessageQueue<int>{};  // libfmq — نطاقه android\n"
               "    ::android::hardware::EventFlag* e = nullptr;\n"
               "    return 0;\n}\n")
    expect("نطاقات سليمة (aidl مع libfmq)", check_namespaces(ns_good), False)
    expect("نطاق media بلا aidl",
           check_namespaces(ns_good.replace("::aidl::android::media", "::android::media")), True)
    expect("نطاق hardware::audio بلا aidl",
           check_namespaces("void f(::android::hardware::audio::effect::IEffect& i);\n"), True)
    expect("نطاقٌ خاطئ في تعليق لا يُبلَّغ عنه",
           check_namespaces("// ::android::media::audio::common::AudioUuid (توثيق)\nvoid f();\n"),
           False)
    expect("نطاقٌ مُضاعف ::aidl::aidl::",
           check_namespaces("void f(const ::aidl::aidl::android::media::x& y);\n"), True)

    # (٢) AELI
    good_aeli = ("extern audio_effect_library_t AELI;\n",
                 "audio_effect_library_t AELI = {\n    .tag = 0,\n};\n")
    expect("AELI سليمة", check_aeli(*good_aeli), False)
    expect("AELI محذوفة من المصدر", check_aeli(good_aeli[0], "int x;\n"), True)
    expect("AELI محذوفة من الرأس", check_aeli("int x;\n", good_aeli[1]), True)

    # (٣) اللقطات
    snapshots = _snapshot_fixture()
    expect("لقطات سليمة", check_snapshots(snapshots), False)
    shortened = {rel: (expected, dict(files)) for rel, (expected, files) in snapshots.items()}
    first = sorted(shortened)[0]
    shortened[first][1].pop(sorted(shortened[first][1])[0])
    expect("رأسٌ سقط من الشحن", check_snapshots(shortened), True)
    edited = {rel: (expected, dict(files)) for rel, (expected, files) in snapshots.items()}
    edited[first][1]["0.aidl"] = b"package android.x;\nparcelable X {\n}\n" + b" "*220
    expect("لقطةٌ عُدّلت (بلا IMMUTABLE)", check_snapshots(edited), True)
    truncated = {rel: (expected, dict(files)) for rel, (expected, files) in snapshots.items()}
    truncated[first][1]["0.aidl"] = b""
    expect("لقطةٌ مبتورة (الحجم الذي وقع فعلًا)", check_snapshots(truncated), True)

    # (٤) تضمين libfmq
    fmq = _fmq_fixture()
    expect("إغلاق تضمين سليم", check_fmq_includes(fmq), False)
    broken = dict(fmq)
    broken.pop(f"{FMQ_ROOT}/compat/utils/Log.h")
    expect("رأس compat ناقص", check_fmq_includes(broken), True)
    extra = dict(fmq)
    extra[f"{FMQ_ROOT}/include/fmq/MessageQueueBase.h"] += "#include <hidl/MQDescriptor.h>\n"
    expect("رأسٌ غير مشحون في الإغلاق", check_fmq_includes(extra), True)

    # (٤-ب) رؤوس libbinder_ndk المنسوخة
    cpp = _binder_cpp_fixture()
    cpp_mk = "MAXFX_BINDER_NDK_CPP := $(LOCAL_PATH)/third_party/libbinder_ndk_cpp\n"
    expect("رؤوس منسوخة سليمة", check_binder_cpp(cpp, cpp_mk), False)
    thinned = dict(cpp)
    thinned.pop(f"{BINDER_CPP_ROOT}/android/binder_stability.h")
    expect("رأسٌ منسوخ سقط", check_binder_cpp(thinned, cpp_mk), True)
    emptied = dict(cpp)
    emptied[f"{BINDER_CPP_ROOT}/android/binder_enums.h"] = ""
    expect("رأسٌ منسوخ فارغ (العطب الذي وقع فعلًا في اللقطات)",
           check_binder_cpp(emptied, cpp_mk), True)
    nolicense = dict(cpp)
    nolicense[f"{BINDER_CPP_ROOT}/android/binder_to_string.h"] = (
        "#pragma once\n" + "// no license\n" * 60)
    expect("نسخةٌ بلا نسبة فضل (Apache)", check_binder_cpp(nolicense, cpp_mk), True)
    shadow = dict(cpp)
    shadow[f"{BINDER_CPP_ROOT}/android/binder_ibinder.h"] = "/* Apache License */\n#pragma once\n" + "x" * 500
    expect("ظلٌّ على رأسٍ يُشحَنه الـNDK", check_binder_cpp(shadow, cpp_mk), True)
    dangling = dict(cpp)
    dangling[f"{BINDER_CPP_ROOT}/android/binder_auto_utils.h"] += "#include <android/binder_missing.h>\n"
    expect("تضمينٌ لا يُحلّ في المنسوخ", check_binder_cpp(dangling, cpp_mk), True)
    expect("بناءٌ لا يشير إلى الشجرة المنسوخة", check_binder_cpp(cpp, ""), True)

    # الحرّاس: تضمينٌ داخل `__has_include(<غير موجود>)` **مُطفأ** لا عطب — وهو ما يفعل
    # `binder_to_string.h` فعلًا مع رؤوس libbinder/libutils. والحالتان المقابلتان تُقاسان.
    guarded = dict(cpp)
    guarded[f"{BINDER_CPP_ROOT}/android/binder_to_string.h"] += (
        "#if __has_include(<binder/RpcSession.h>)\n"
        "#include <binder/IBinder.h>\n"
        "#endif\n")
    expect("تضمينٌ داخل __has_include مُطفأ", check_binder_cpp(guarded, cpp_mk), False)
    unguarded = dict(cpp)
    unguarded[f"{BINDER_CPP_ROOT}/android/binder_to_string.h"] += "#include <binder/IBinder.h>\n"
    expect("نفس التضمين بلا حارس", check_binder_cpp(unguarded, cpp_mk), True)
    active_guard = dict(cpp)
    active_guard[f"{BINDER_CPP_ROOT}/android/binder_to_string.h"] += (
        "#if __has_include(<android/binder_ibinder.h>)\n"
        "#include <android/binder_missing.h>\n"
        "#endif\n")
    expect("حارسٌ فعّال يحرس تضمينًا غائبًا", check_binder_cpp(active_guard, cpp_mk), True)

    # (٥) تضمينات الغلاف
    project = {"maxfx_dsp.h", "maxfx_effect.h"}
    expect("تضمينات غلاف سليمة",
           check_wrapper_includes(
               "#include <fmq/AidlMessageQueue.h>\n#include <aidl/x/Y.h>\n"
               "#include \"maxfx_dsp.h\"\n#include <memory>\n#include <optional>\n",
               fmq, project), False)
    expect("مسار رأسٍ منقوص في الغلاف",
           check_wrapper_includes("#include <fmq/AidlMessageQueu.h>\n", fmq, project), True)

    # (٦) البناء
    build = {
        "android_mk": "MAXFX_AIDL_GEN := $(LOCAL_PATH)/gen\n"
                      "MAXFX_BINDER_HEADER := $(MAXFX_AIDL_GEN)/"
                      "aidl/android/hardware/audio/effect/BnEffect.h\n"
                      "MAXFX_AIDL_PKG_DIRS := gen/android/hardware/audio/effect \\\n"
                      "    gen/android/hardware/common/fmq gen/android/hardware/common \\\n"
                      "    gen/android/media/audio/common\n"
                      "MAXFX_AIDL_GEN_SRCS := $(foreach d,$(MAXFX_AIDL_PKG_DIRS),"
                      "$(wildcard $(d)/*.cpp))\n"
                      "MAXFX_BINDER_NDK_CPP := $(LOCAL_PATH)/third_party/libbinder_ndk_cpp\n",
        "gen_sh": "#!/bin/sh\naidl --lang=ndk -o$out\n",
        "app_mk": "APP_ABI := arm64-v8a armeabi-v7a\nAPP_STL := c++_shared\n",
        "workflow": ("symbols=$(readelf)\n"
                     "for sym in AELI createEffect queryEffect destroyEffect; do :; done\n"
                     f"{GENERATED_REL}\n"),
        "gitignore": "maxfx/gen/\n",
    }
    expect("بناء متّسق", check_build(**build), False)
    bad = dict(build, app_mk="APP_ABI := arm64-v8a\nAPP_STL := c++_shared\n")
    expect("عمودٌ غائب", check_build(**bad), True)
    bad = dict(build, app_mk="APP_ABI := arm64-v8a armeabi-v7a\nAPP_STL := c++_static\n")
    expect("STL خاطئة", check_build(**bad), True)
    bad = dict(build, workflow="symbols=$(x)\nfor sym in AELI createEffect; do :; done\n"
                               f"{GENERATED_REL}\n")
    expect("رمزٌ غائب عن بوابة CI", check_build(**bad), True)
    bad = dict(build, gitignore="")
    expect("مخرجات التوليد غير متجاهَلة", check_build(**bad), True)
    bad = dict(build, gen_sh="aidl -o$out\n")
    expect("توليدٌ بلا --lang=ndk", check_build(**bad), True)
    bad = dict(build, android_mk=build["android_mk"].replace("MAXFX_AIDL_PKG_DIRS", "X"))
    expect("مصادر الرزم المُولَّدة غير مُصرَّفة (عطب ربط)", check_build(**bad), True)
    bad = dict(build, android_mk=build["android_mk"].replace(
        "gen/android/hardware/common/fmq ", ""))
    expect("رزمةٌ مُولَّدة ساقطة من التصريف", check_build(**bad), True)

    for failure in failures:
        print(f"  ✗ {failure}")
    print(f"الفحص الذاتي: {len(failures)} فشل")
    return 1 if failures else 0


def main() -> int:
    parser = argparse.ArgumentParser(description="عقد مؤثّر MaxFx — بوابة بنيويّة")
    parser.add_argument("--assert", action="store_true", help="سطر حكم واحد، وتُخرج بخطأ")
    parser.add_argument("--self-test", action="store_true", help="يقيس الأداة نفسها")
    parser.add_argument("--repo", default=_REPO, help="جذر المستودع")
    args = parser.parse_args()

    if args.self_test:
        return self_test()

    results = audit(collect(args.repo))
    total = sum(len(problems) for _name, problems in results)

    # `assert` كلمةٌ محجوزة في بايثون، فالوسيط يُقرأ بالاسم لا بالنقطة.
    if getattr(args, "assert"):
        print(f"عقد MaxFx: {len(results)} فحوص · {total} عائقًا")
        for name, problems in results:
            for problem in problems:
                print(f"  ✗ [{name}] {problem}")
        return 1 if total else 0

    for name, problems in results:
        print(f"{'✅' if not problems else '✗'} {name}")
        for problem in problems:
            print(f"     {problem}")
    print(f"\nالحصيلة: {total} عائقًا من {len(results)} فحوص")
    return 1 if total else 0


if __name__ == "__main__":
    sys.exit(main())
