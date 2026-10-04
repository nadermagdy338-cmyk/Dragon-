#!/usr/bin/env python3
"""جلبُ النسخ المجمَّدة من AOSP **مُتحقَّقًا منه** — لا نسخٌ أعمى.

لماذا وُجد
----------
النسخ المجمَّدة تُشحن في `maxfx/aidl/` وتُبنى بـ`aidl --lang=ndk`. واللقطة تُنسخ بجلبٍ شبكيّ
لكل ملفّ — وجلبٌ يفشل **لا يُشعر أحدًا**: كتبنا `curl > file` فصار ملفّان فارغين (`0` و`6`
بايت) وبقيا في الشجرة أسبوعًا بلا أن يكشفهما شيء، لأنّ `kt_balance` يقيس التوازن (ملفٌّ فارغ
متوازن!) و`code_health` يقيس النظافة (ولا نصّ فيه). والعطب يظهر بعدها في CI برسالة `aidl`
لا تدلّ على سببه.

فالقاعدة هنا: **لا يُقبل ملفٌّ إلا بثلاث شروط مقيسة** — حجمٌ معقول، و`package android.…`
واحد، وعلامة `IMMUTABLE` للقطاتٍ المجمَّدة — وإلا أُعيد الجلب، ثمّ يُعلن الفشل صريحًا.

⚠️ وحدّها: تلمس الشبكة، فليست بوابة (لا تُشغَّل في CI). وموقعها `tools/` لا `build/`، لأنّ الأخير
متجاهَل في git فلا تُرى وصفةٌ فيه (وهذا درسٌ مقيس من جولة ٧).

الصيغة
------
    python3 tools/fetch_maxfx_aidl.py --check     # يقيس الشجرة القائمة بلا شبكة (بوابة)
    python3 tools/fetch_maxfx_aidl.py             # يجلب ويستبدل، متحقّقًا من كل ملفّ
    python3 tools/fetch_maxfx_aidl.py --self-test # يقيس حكم التحقّق نفسه
"""

from __future__ import annotations

import argparse
import base64
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request

_HERE = os.path.dirname(os.path.abspath(__file__))
_REPO = os.path.dirname(_HERE)

# مسار الـgooglesource ⇄ مكانه المحلّيّ. وكلّها لقطاتٌ **مجمَّدة** بأرقام نسخها لا `current`:
# `audio/aidl/Android.bp` يعلن للإصدار 1 من `android.hardware.audio.effect` استيراد
# `android.media.audio.common.types-V2`، والحزمة تُصدِّر `android.hardware.common-V2` و
# `android.hardware.common.fmq-V1` — وتخطيطُ الـparcelable هو العقد السلكيّ.
PACKAGES = {
    "maxfx/aidl/android/hardware/audio/effect": (
        "platform/hardware/interfaces/+/refs/heads/main/audio/aidl/"
        "aidl_api/android.hardware.audio.effect/1/android/hardware/audio/effect",
        27,
    ),
    "maxfx/aidl/android/hardware/common/fmq": (
        "platform/hardware/interfaces/+/refs/heads/main/common/fmq/aidl/"
        "aidl_api/android.hardware.common.fmq/1/android/hardware/common/fmq",
        4,
    ),
    "maxfx/aidl/android/hardware/common": (
        "platform/hardware/interfaces/+/refs/heads/main/common/aidl/"
        "aidl_api/android.hardware.common/2/android/hardware/common",
        3,
    ),
    "maxfx/aidl/android/media/audio/common": (
        "platform/system/hardware/interfaces/+/refs/heads/main/media/"
        "aidl_api/android.media.audio.common.types/2/android/media/audio/common",
        60,
    ),
}

MIRROR = "https://android.googlesource.com"
MIN_BYTES = 200
IMMUTABLE = "IMMUTABLE"


def verdict(name: str, payload: bytes, require_immutable: bool = True) -> str | None:
    """حكمُ القبول على ملفٍّ منفرد — يُعيد سبب الرفض أو `None`.

    الأنماط مصمَّمة على ما رأيناه فعلًا لا على ما نتوقّعه: لقطةٌ فاشلة تصل إمّا فارغة (`0`
    بايت) أو بصفحة خطأ عُدّت نصًّا (بلا `package`) أو ببقايا base64.
    """
    if len(payload) < MIN_BYTES:
        return f"حجمٌ صغير ({len(payload)} بايت)"
    try:
        text = payload.decode("utf-8")
    except UnicodeDecodeError:
        return "ليس نصًّا (بقايا base64 أو صفحة خطأ)"
    packages = [line for line in text.splitlines() if line.startswith("package ")]
    if len(packages) != 1 or not packages[0].startswith("package android."):
        return f"سطر `package` غير سليم ({packages[:1]})"
    if require_immutable and IMMUTABLE not in text:
        return "بلا علامة IMMUTABLE ⇒ ليست لقطةً مجمَّدة"
    if "}" not in text:
        return "بلا قوس إغلاق (لقطةٌ مبتورة)"
    return None


def _get(url: str, tries: int = 4) -> bytes:
    """جلبٌ بمحاولاتٍ متباعدة — الـgooglesource يحدّ المعدّل فيسقط بعض الطلبات عشوائيًّا."""
    last = None
    for attempt in range(1, tries + 1):
        try:
            with urllib.request.urlopen(url, timeout=30) as response:
                if response.status == 200:
                    return response.read()
                last = f"HTTP {response.status}"
        except (urllib.error.URLError, TimeoutError, OSError) as exc:
            last = str(exc)
        time.sleep(1.5 * attempt)
    raise RuntimeError(f"فشل الجلب بعد {tries} محاولات: {url} ({last})")


def listing(gs_dir: str) -> list[str]:
    raw = base64.b64decode(_get(f"{MIRROR}/{gs_dir}/?format=TEXT"))
    names = []
    for line in raw.decode("utf-8", "replace").splitlines():
        parts = line.split("\t")
        if len(parts) >= 2 and parts[1].endswith(".aidl"):
            names.append(parts[1])
    return sorted(names)


def fetch_one(gs_dir: str, name: str) -> bytes:
    return base64.b64decode(_get(f"{MIRROR}/{gs_dir}/{name}?format=TEXT"))


def check_tree(repo: str) -> list[str]:
    """قياسٌ بلا شبكة: العدد المثبَّت وحكم القبول لكل ملفّ — وهذا ما يُشغَّل في البوابات."""
    problems = []
    for rel, (_gs, expected) in PACKAGES.items():
        root = os.path.join(repo, rel)
        files = sorted(f for f in os.listdir(root)) if os.path.isdir(root) else []
        aidl = [f for f in files if f.endswith(".aidl")]
        if len(aidl) != expected:
            problems.append(f"{rel}: {len(aidl)} ملفًّا والمرجع {expected}")
        for name in aidl:
            with open(os.path.join(root, name), "rb") as fh:
                payload = fh.read()
            reason = verdict(name, payload)
            if reason:
                problems.append(f"{rel}/{name}: {reason}")
    return problems


def fetch_all(repo: str) -> int:
    failures = []
    for rel, (gs_dir, expected) in PACKAGES.items():
        target = os.path.join(repo, rel)
        os.makedirs(target, exist_ok=True)
        names = listing(gs_dir)
        if len(names) != expected:
            failures.append(f"{rel}: القائمة البعيدة {len(names)} والمرجع {expected}")
            continue
        for name in names:
            payload = fetch_one(gs_dir, name)
            reason = verdict(name, payload)
            if reason:
                # محاولةٌ ثانية قبل الرفض: الانقطاع عابرٌ غالبًا لا دائم.
                time.sleep(2)
                payload = fetch_one(gs_dir, name)
                reason = verdict(name, payload)
            if reason:
                failures.append(f"{rel}/{name}: {reason}")
                continue
            with open(os.path.join(target, name), "wb") as fh:
                fh.write(payload)
        print(f"✅ {rel}: {len(names)} ملفًّا")
    if failures:
        for failure in failures:
            print(f"  ✗ {failure}")
        return 1
    return 0


def self_test() -> int:
    """يقيس الحكم نفسه على الحالات التي رأيناها فعلًا."""
    cases = [
        ("لقطة سليمة", b"// IMMUTABLE\npackage android.a;\nparcelable X {\n}\n" + b" " * 200, True),
        ("ملفٌّ فارغ", b"", False),
        ("بقايا base64", b"DDAiQk", False),
        ("صفحة خطأ", b"<html>Not Found</html>" + b" " * 200, False),
        ("حزمة خاطئة", b"// IMMUTABLE\npackage com.x;\n" + b" " * 200, False),
        ("بلا IMMUTABLE", b"package android.a;\nparcelable X {\n}\n" + b" " * 200, False),
        ("مبتورة بلا قوس", b"// IMMUTABLE\npackage android.a;\nparcelable X" + b" " * 200, False),
    ]
    failures = []
    for name, payload, should_pass in cases:
        reason = verdict(name, payload)
        if should_pass and reason:
            failures.append(f"رُفض ملفٌّ سليم «{name}»: {reason}")
        if not should_pass and not reason:
            failures.append(f"قُبل ملفٌّ فاسد «{name}»")
    for failure in failures:
        print(f"  ✗ {failure}")
    print(f"الفحص الذاتي: {len(failures)} فشل من {len(cases)} حالة")
    return 1 if failures else 0


def main() -> int:
    parser = argparse.ArgumentParser(description="جلب لقطات AIDL المجمَّدة لمؤثّر MaxFx")
    parser.add_argument("--check", action="store_true", help="يقيس الشجرة القائمة بلا شبكة")
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--repo", default=_REPO)
    args = parser.parse_args()

    if args.self_test:
        return self_test()
    if args.check:
        problems = check_tree(args.repo)
        print(f"لقطات AIDL: {'✅ مطابقة' if not problems else f'{len(problems)} عطبًا'}")
        for problem in problems:
            print(f"  ✗ {problem}")
        return 1 if problems else 0
    return fetch_all(args.repo)


if __name__ == "__main__":
    sys.exit(main())
