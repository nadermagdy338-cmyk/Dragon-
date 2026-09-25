#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""تدقيق الأصل والترخيص — «من أين جاء هذا الملف، وبأي حقّ، وما العمل؟».

لماذا وُجد هذا الملف
--------------------
المشروع نشأ من مصادر متعدّدة التراخيص (وحدة أداء لها سلف، ومدير نواة GPL-3.0 نُقلت منه
شاشات، وثنائيات مبنية من أدوات GPL)، وكان يُشحن تحت رخصة واحدة. والفرق بين «رخصة
مكتوبة في ملف» و«رخصة هذا الملف فعلًا» هو ما يقيسه هذا التدقيق.

الخطر الحقيقي ليس أن يكون في الشجرة كود GPL — بل أن يُشحن مكوّن GPL داخل حزمة تُوزَّع
بترخيص لا يسمح بذلك. لذلك هذه الأداة تُنتج ثلاثة مخرجات من مصدر واحد لا تُكرّره:

    build/license-report.json     جرد آليّ لكل مكوّن (بما يقرؤه CI)
    docs/PROVENANCE.md            جدول بشريّ: FILE | ORIGIN | LICENSE | STATUS | ACTION
    حكم واحد                      `--assert` يخرج بخطأ إن دخل GPL إلى مسار الإصدار

الصيغة
------
    python3 tools/license_audit.py                     # تقرير مختصر على الشاشة
    python3 tools/license_audit.py --json              # اكتب build/license-report.json
    python3 tools/license_audit.py --provenance        # اكتب docs/PROVENANCE.md
    python3 tools/license_audit.py --assert            # رمز خروج 1 عند GPL في مسار الإصدار
    python3 tools/license_audit.py --self-test         # الأداة تقيس نفسها على شجرة مصنوعة

ما تقيسه بالضبط
---------------
1. **أصل كل ملف** من ترويسته الفعلية (أول `HEAD_LINES` سطرًا)، لا من اسمه ولا من مجلّده.
   ومن لا ترويسة له يُصنَّف بعائلة وحدته المُعلنة، ويُكتب «بلا ترويسة» صراحةً.
2. **الرخصة** مشتقّة من مصدر الأصل الموثَّق (رابط المستودع يُطبع في التقرير ليتحقّقه بشر).
3. **التبعيات الخارجية** (Gradle من كتالوج الإصدارات + Cargo من `Cargo.lock`) مقابل جدول
   مُنتقى. وما ليس في الجدول يُكتب `Unknown` **ولا يُخمَّن**.
4. **الثنائيات** (`.so`, `libmagiskboot`, …) بقراءة ترويسة ELF: المعمارية فعلًا، وبصمة
   النصّ داخلها (strings) لتحديد ما بُنيت منه.
5. **ثغرات ABI**: كل ABI مُعلن في `abiFilters` بلا ثنائية مقابلة = عطب يُعلَن لا يُخفى.

حدودها المعلنة
--------------
* جدول التراخيص **مُنتقى ومُوثَّق** لا مُشتقّ من الشبكة وقت التشغيل: تدقيق لا يعمل بلا
  إنترنت أنفع من تدقيق يعمل مرّة. وما دخل جديدًا يظهر `Unknown` حتى يُضاف بسند.
* هي لا تحكم على **التشابه الدلالي** («هل هذا مُشتقّ فعلًا؟») — ذاك حكم بشريّ يُبنى على
  الترويسة والـdiff، والأداة تُثبّت *ما هو مُعلَن* لا ما هو خفيّ.
* وتحذير الـ`Unknown` **لا يُفشل** البوابة افتراضيًّا: «مجهول» يُستدعى للمراجعة، أمّا
  «GPL في مسار الإصدار» فيُفشل. الفرق مقصود: الأول نقص بيان، والثاني خطر توزيع.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import struct
import subprocess
import sys
import tempfile
import shutil

_HERE = os.path.dirname(os.path.abspath(__file__))
_ROOT = os.path.dirname(_HERE)

# ── الأصول المعروفة، وأدلّتها، وتراخيصها ───────────────────────────────────────
#
# كل مدخل: اسم الأصل · صيغة الرخصة · معرّف SPDX · درجة الخطر · المرجع الذي يُتحقَّق منه
# · النمط الذي يدلّ عليه في الترويسة. والمرجع **مكتوب** لأن حكمًا بلا مرجع لا يُراجَع.
#
# ⚠️ الخطر يُرتَّب، والأعلى يفوز في الملف الواحد: ملف يذكر مصدرين أحدهما GPL يُعامَل GPL.

RISK_GPL = 3
RISK_WEAK = 2        # LGPL/MPL وأشباهها: مشروطة لا ممنوعة
RISK_UNKNOWN = 1
RISK_FREE = 0

SOURCES: list[dict] = [
    {
        "id": "HorizonKernelFlasher",
        "license": "GNU GPL v3.0 only",
        "spdx": "GPL-3.0-only",
        "risk": RISK_GPL,
        "reference": "https://github.com/libxzr/HorizonKernelFlasher",
        "evidence": r"libxzr|HorizonKernelFlasher",
        "used_for": "شاشة/عامل تفليش النواة في تطبيق المدير",
    },
    {
        "id": "vtools",
        "license": "GNU GPL v3.0",
        "spdx": "GPL-3.0-only",
        "risk": RISK_GPL,
        "reference": "https://github.com/helloklf/vtools",
        "evidence": r"helloklf|\bvtools\b",
        "used_for": "قارئ إطارات (FpsReader) وجدول OPP لـMediaTek",
    },
    {
        "id": "origami_kernel_manager",
        "license": "GNU GPL v3.0 or later",
        "spdx": "GPL-3.0-or-later",
        "risk": RISK_GPL,
        "reference": "https://github.com/Rem01Gaming/origami_kernel_manager",
        "evidence": r"origami_kernel_manager",
        "used_for": "أدوات MediaTek",
    },
    {
        "id": "SmartPack-Kernel-Manager",
        "license": "GNU GPL v3.0",
        "spdx": "GPL-3.0-only",
        "risk": RISK_GPL,
        "reference": "https://github.com/SmartPack/SmartPack-Kernel-Manager",
        "evidence": r"SmartPack",
        "used_for": "مرجع واجهات (أُدخل كأثر في بنك أطلس)",
    },
    {
        "id": "ZKM (Zuan Kernel Manager)",
        "license": "GNU GPL v3.0",
        "spdx": "GPL-3.0-only",
        "risk": RISK_GPL,
        "reference": "https://github.com/ZUANVFX01/ZKM",
        "evidence": r"\bZKM\b|Zuan Kernel Manager|com\.zuan\.kernelmanager|zuan",
        "used_for": "شاشات وأدوات مدير النواة (طرفية · عمليات · إعدادات · إطارات · تفليش)",
    },
    {
        "id": "Termux (termux-app)",
        "license": "GNU GPL v3.0 only — باستثناء معلن لمكتبتي terminal-view وterminal-emulator",
        "spdx": "GPL-3.0-only",
        "risk": RISK_GPL,
        "reference": "https://github.com/termux/termux-app/blob/master/LICENSE.md",
        # **لا كلمة `termux` وحدها:** هي تظهر في `MY_PATH` عندنا كثابت نظام
        # (`/data/data/com.termux/files/usr/bin`) وفي نثر الأدوات — وهي **حقيقة عن الجهاز**
        # لا **نسبة أصل**. فالإسناد يحتاج اسمًا صريحًا للحزمة أو للمستودع.
        "evidence": r"termux-app|com\.termux|Termux terminal",
        "used_for": "مدقّق طرفية (VT) وواجهة الطرفية وثنائية الـJNI",
    },
    {
        "id": "anykernel3",
        "license": "AnyKernel Scripts License (BSD-معيارية)",
        "spdx": "BSD-3-Clause",
        "risk": RISK_FREE,
        "reference": "https://github.com/osm0sis/AnyKernel3",
        "evidence": r"anykernel",
        "used_for": "صيغة حزمة تفليش النواة (تُنفَّذ وقت التشغيل، لا تُوزَّع)",
    },
    {
        "id": "Magisk (magiskboot)",
        "license": "GNU GPL v3.0",
        "spdx": "GPL-3.0-only",
        "risk": RISK_GPL,
        "reference": "https://github.com/topjohnwu/Magisk",
        "evidence": r"MagiskBoot|magiskboot",
        "used_for": "فكّ وتغليف صور الإقلاع",
    },
    {
        "id": "Encore Tweaks",
        "license": "Apache License 2.0",
        "spdx": "Apache-2.0",
        "risk": RISK_FREE,
        "reference": "https://github.com/Rem01Gaming/encore",
        "evidence": r"\bEncore\b|Rem01Gaming",
        "used_for": "أساس خدمة ArchDaemon ومنطق التهيئة",
    },
    {
        "id": "Rianixia-ThermalCore",
        "license": "Apache License 2.0",
        "spdx": "Apache-2.0",
        "risk": RISK_FREE,
        "reference": "https://github.com/ryanistr/Rianixia-ThermalCore",
        "evidence": r"Rianixia|ryanistr",
        "used_for": "محرّك الإدارة الحرارية",
    },
    {
        "id": "KernelFlasher",
        "license": "Apache License 2.0",
        "spdx": "Apache-2.0",
        "risk": RISK_FREE,
        "reference": "https://github.com/capntrips/KernelFlasher",
        "evidence": r"capntrips|kernelflasher",
        "used_for": "وحدة تفليش النواة (مُورَّدة كوحدة Gradle كاملة)",
    },
    {
        "id": "KTweak",
        "license": "BSD 2-Clause",
        "spdx": "BSD-2-Clause",
        "risk": RISK_FREE,
        "reference": "https://github.com/tytydraco/KTweak",
        "evidence": r"\bKTweak\b|tytydraco",
        "used_for": "منهج ضبط النواة المبني على الدليل",
    },
    {
        "id": "VMTouch",
        "license": "BSD 3-Clause",
        "spdx": "BSD-3-Clause",
        "risk": RISK_FREE,
        "reference": "https://github.com/hoytech/vmtouch",
        "evidence": r"VMTouch|vmtouch|Doug Hoyte",
        "used_for": "تثبيت الصفحات في الذاكرة",
    },
    {
        "id": "Android Open Source Project",
        "license": "Apache License 2.0",
        "spdx": "Apache-2.0",
        "risk": RISK_FREE,
        "reference": "https://source.android.com/license",
        "evidence": r"Android Open Source Project|AOSP",
        "used_for": "بنية مساحة المستخدم وسياسة SELinux",
    },
]

# الترويسات التي تُثبت ملكية MaxManager نفسها. تُقرأ **بعد** قواعد المصادر الأخرى،
# فملف يحمل ترويسة ZKM وأخرى لـMaxManager يُصنَّف GPL — لأن إضافة ترويسة لا تمحو أصلًا.
# سياق يُثبت أنّ الاسم المذكور **أصلٌ للملف** لا **ذكرًا عابرًا** فيه.
#
# هذا الشرط أُضيف بعد قياس: بلا سياق كانت `"/data/data/com.termux/files/usr/bin"` في ثابت
# `MY_PATH` (في `binprofiles` و`binutils`) تُصنّف الملفين «مشتقّين من Termux» — إيجابية كاذبة
# تُسقط ثقة البوابة كلها. والقاعدة الآن: الاسم يُحتسب أصلًا فقط إذا جاء في سطر يقول
# «مأخوذ/مبني على/حقوقه/رخصته» — لا إذا جاء داخل قيمة أو نصّ.
PROVENANCE_CONTEXT = re.compile(
    r"adapt|port(?:ed|ing)?\b|based on|deriv|original|copy|copyright|licen[cs]|modif|"
    r"integr|credit|origin|taken from|fork",
    re.I,
)

# مراكز لا تُصنَّف من ترويستها: نثر الأدوات والتوثيق يذكر أسماء مشاريع كثيرة **وصفًا**،
# وقراءتها كنسب أصل تُنشئ إيجابيات كاذبة. وتُصنَّف بعائلة الوحدة، ويُعلَن ذلك في عمود الدليل.
PROSE_ONLY_PREFIXES = ("docs/", "tools/")

OWNERS = re.compile(r"Zexshia|KowX|\bRapli\b|MaxManager contributors|MaxManager Project", re.I)
APACHE_HEADER = re.compile(r"Apache License,?\s*Version 2\.0", re.I)
GPL_HEADER = re.compile(r"GNU General Public License|GPL-?3|GPLv3", re.I)
COPYRIGHT = re.compile(r"Copyright\s*\(?[Cc]\)?\s*((?:\d{4}\s*[-–]\s*\d{4})|\d{4})\s*([A-Za-z][\w .'-]*)")

HEAD_LINES = 30

# ── عائلات الوحدات: تُصنَّف بها الملفات بلا ترويسة (موارد، بيانات، أيقونات) ──────
#
# ترتيب القواعد مهمّ: الأولى التي تطابق هي التي تحكم.
MODULE_FAMILIES: list[tuple[str, str, str, int]] = [
    # (نمط المسار, المركز, الرخصة, الخطر)
    (r"^manager/terminal-view/", "Termux (terminal-view)", "GPL-3.0-only", RISK_GPL),
    (r"^manager/terminal-emulator/", "Termux (terminal-emulator)", "GPL-3.0-only", RISK_GPL),
    (r"^manager/app/src/main/jniLibs/", "Termux (libtermux.so)", "GPL-3.0-only", RISK_GPL),
    (r"^manager/kernel-flasher/.*jniLibs/.*libmagiskboot", "Magisk (magiskboot)", "GPL-3.0-only", RISK_GPL),
    (r"^manager/kernel-flasher/src/main/assets/libmagiskboot$", "Magisk (magiskboot)", "GPL-3.0-only", RISK_GPL),
    (r"^manager/kernel-flasher/src/main/jniLibs/.*(httools|lptools)",
     "AOSP avbtool/lptools", "Apache-2.0", RISK_FREE),
    (r"^manager/kernel-flasher/src/main/assets/(libhttools|liblptools)",
     "AOSP avbtool/lptools", "Apache-2.0", RISK_FREE),
    (r"^manager/kernel-flasher/", "KernelFlasher", "Apache-2.0", RISK_FREE),
    (r"^manager/src/main/rust/", "MaxManager (native engine)", "Apache-2.0", RISK_FREE),
    (r"^manager/", "MaxManager app (AZenith base)", "Apache-2.0", RISK_FREE),
    (r"^archdaemon/", "Encore Daemon (via AZenith)", "Apache-2.0", RISK_FREE),
    (r"^mainfiles/", "MaxManager module (AZenith base)", "Apache-2.0", RISK_FREE),
    (r"^android/", "MaxManager platform integration", "Apache-2.0", RISK_FREE),
    (r"^thermalcore/", "Rianixia-ThermalCore", "Apache-2.0", RISK_FREE),
    (r"^binprofiles/", "MaxManager binprofiles", "Apache-2.0", RISK_FREE),
    (r"^binutils/", "MaxManager binutils", "Apache-2.0", RISK_FREE),
    (r"^preloadbin/", "MaxManager preloadbin", "Apache-2.0", RISK_FREE),
    (r"^tools/", "MaxManager tooling", "Apache-2.0", RISK_FREE),
    (r"^docs/", "MaxManager documentation", "Apache-2.0", RISK_FREE),
    (r"^\.github/", "MaxManager CI", "Apache-2.0", RISK_FREE),
    # ما بقي: لا أصل خارجي مُعلَن ولا عائلة وحدة معروفة ⇒ الافتراضي **مُعلَن** لا مسكوت
    # عنه، وهو رخصة المستودع. وهذا افتراض عن نطاق المستودع يُكتب في عمود الدليل،
    # ولا يُقدَّم كقياس. والملفات ذات البيانات العبأة (devices.db, socs.json) مُستثناة أدناه.
    (r"^.*$", "MaxManager (repository default)", "Apache-2.0", RISK_FREE),
]

# بيانات عبأة بلا إسناد: لا ترويسة تقول من صنعها ولا عائلة وحدة تحكم. تُطلب مراجعة بشرية
# ولا تُدَّعى Apache-2.0 بالافتراضي — لأن ملف بيانات ٤ ميجابايت قد يحمل جدولًا منسوخًا.
DATA_ASSETS = (
    r"^manager/app/src/main/assets/devices\.db$",
    r"^manager/app/src/main/assets/socs\.json$",
    r"^maxmanagerApplist\.json$",
)

# مخرجات بناء أو مواد توقيع دخلت الشجرة — لا تُشحن كـ«مصدر» ويجب أن تخر́ج من التتبّع.
# النمط مقصود بدقّة: `META-INF` **في الجذر** هو بيانات Kotlin المُصرَّفة التي تسرّبت،
# أمّا `mainfiles/META-INF/com/google/android/update-binary` فهو **ملفّ المنصّب الشرعي**
# للوحدة Magisk — وتسميته حطامًا كانت ستطلب حذف ملفّ لا يعمل الموديول بدونه.
STRAY_ARTIFACTS = (
    (r"^META-INF/", "بيانات Kotlin/Java مُصرَّفة تسرّبت إلى الجذر"),
    (r"\.class$", "صنف مُصرَّف متعقَّب داخل مجلّد مصادر"),
    (r"\.jks$", "مخزن مفاتيح داخل الشجرة — مادة توقيع لا مصدر"),
)

# ── رخص التبعيات: جدول مُنتقى، وما ليس فيه يُكتب Unknown ولا يُخمَّن ───────────
#
# المفتاح إمّا بادئة إحداثية (`group:`) وإمّا إحداثية كاملة.
DEP_LICENSES: dict[str, str] = {
    "androidx.": "Apache-2.0",
    "com.google.android.material": "Apache-2.0",
    "com.google.dagger:": "Apache-2.0",
    "com.google.devtools.ksp": "Apache-2.0",
    "com.squareup.okhttp3:": "Apache-2.0",
    "com.github.topjohnwu.libsu": "Apache-2.0",
    "dev.rikka.shizuku": "Apache-2.0",
    "org.lsposed.hiddenapibypass": "Apache-2.0",
    "org.jetbrains.kotlin": "Apache-2.0",
    "org.jetbrains.kotlinx:": "Apache-2.0",
    "com.android.tools.build:": "Apache-2.0",
    "dev.chrisbanes.haze": "Apache-2.0",
    "com.materialkolor:": "MIT",
    "io.coil-kt:": "Apache-2.0",
    "me.zhanghai.android.appiconloader": "Apache-2.0",
    "com.github.yalantis:ucrop": "Apache-2.0",
    "com.github.megatronking.stringfog": "Apache-2.0",
    "com.github.jeziellago:compose-markdown": "MIT",
    "com.github.Fox2Code.AndroidANSI": "MIT",
    "junit:junit": "EPL-1.0",
    "org.json:json": "Public-Domain",   # JSON.org: «The Software shall be used for Good, not Evil»
}

# صناديق Rust: كلها من عائلة MIT/Apache المزدوجة، عدا ما نُصّ هنا.
CARGO_LICENSES: dict[str, str] = {
    "rianixia-thermalcore": "Apache-2.0",
    "maxmanager_native": "Apache-2.0",
    "maxmanager-profilesettings": "Apache-2.0",
    "maxmanager-utilityconf": "Apache-2.0",
    "zlib-rs": "Zlib",
    "typed-path": "MIT OR Apache-2.0",
    "unty": "MIT OR Apache-2.0",
    "zmij": "MIT OR Apache-2.0",
    "virtue": "MIT OR Apache-2.0",
    "windows": "MIT OR Apache-2.0",
    "windows-sys": "MIT OR Apache-2.0",
    "windows-targets": "MIT OR Apache-2.0",
}
CARGO_DEFAULT = "MIT OR Apache-2.0"   # العُرف الساحق في crates.io، ويُعلَن كافتراض لا كحكم

SOURCE_EXTS = {
    ".kt", ".java", ".kts", ".gradle", ".rs", ".c", ".h", ".cpp", ".hpp",
    ".aidl", ".sh", ".py", ".pro", ".mk", ".te", ".rc", ".bp",
}
BINARY_EXTS = {".so", ".a", ".o", ".class", ".jar", ".jks", ".keystore", ".dex", ".apk"}

# ملفات تحمل اسمًا يجعلها ثنائية بلا امتداد.
BINARY_NAMES = {"libmagiskboot", "libhttools_static", "liblptools_static", "gradle-wrapper.jar"}

RELEASE_PATH_PREFIXES = (
    "manager/app/",
    "manager/kernel-flasher/",
    "manager/terminal-view/",
    "manager/terminal-emulator/",
    "manager/src/main/rust/",
    "mainfiles/",
    "archdaemon/",
    "thermalcore/",
    "binprofiles/",
    "binutils/",
    "preloadbin/",
    "android/",
)


def repo_root(start: str | None = None) -> str:
    here = os.path.abspath(start or _HERE)
    d = here
    for _ in range(8):
        if os.path.isdir(os.path.join(d, "manager")) and os.path.isdir(os.path.join(d, "tools")):
            return d
        parent = os.path.dirname(d)
        if parent == d:
            break
        d = parent
    return here


def tracked_files(root: str) -> list[str]:
    """ملفات git المتعقّبة — لا نُفتّش مخرجات بناء ولا مخازن. وإن لم يوجد git نُسير الشجرة."""
    try:
        out = subprocess.run(
            ["git", "-C", root, "ls-files", "-z"],
            capture_output=True, check=True,
        ).stdout.decode("utf-8", "replace")
        return sorted(p for p in out.split("\0") if p)
    except Exception:
        found: list[str] = []
        skip = {".git", "build", ".gradle", "target", "node_modules"}
        for dirpath, dirnames, filenames in os.walk(root):
            dirnames[:] = [d for d in dirnames if d not in skip]
            for name in filenames:
                found.append(os.path.relpath(os.path.join(dirpath, name), root).replace(os.sep, "/"))
        return sorted(found)


def read_head(path: str, lines: int = HEAD_LINES) -> str:
    try:
        with open(path, encoding="utf-8", errors="replace") as fh:
            return "".join(next(fh, "") for _ in range(lines))
    except OSError:
        return ""


def efl_arch(path: str) -> str:
    """معمارية ثنائية ELF من ترويستها — لا من اسم مجلّدها."""
    try:
        with open(path, "rb") as fh:
            head = fh.read(20)
    except OSError:
        return "unreadable"
    if len(head) < 20 or head[:4] != b"\x7fELF":
        return "not-elf"
    little = head[5] == 1
    endian = "<" if little else ">"
    machine = struct.unpack(endian + "H", head[18:20])[0]
    return {
        0x28: "armeabi-v7a",   # EM_ARM (32-bit)
        0xB7: "arm64-v8a",     # EM_AARCH64
    }.get(machine, f"machine-0x{machine:x}")


def bin_markers(path: str) -> list[str]:
    """بصمات داخل الثنائية: من أيّ مشروع بُنيت؟ تُقرأ من النصّ لا من الاسم."""
    try:
        if os.path.getsize(path) > 40 * 1024 * 1024:
            return []
        with open(path, "rb") as fh:
            data = fh.read()
    except OSError:
        return []
    hits = []
    for needle, label in (
        (b"MagiskBoot", "MagiskBoot"),
        (b"magiskboot", "magiskboot"),
        (b"init.magisk.rc", "init.magisk.rc"),
        (b"com.termux.terminal", "com.termux.terminal"),
        (b"avb", "avb"),
    ):
        if needle in data:
            hits.append(label)
    return hits


def _provenance_scope(head: str) -> str:
    """أسطر الترويسة التي تحمل سياق نسبة — وسواها لا يُعدّ إسنادًا."""
    return "\n".join(l for l in head.splitlines() if PROVENANCE_CONTEXT.search(l))


def classify_head(head: str) -> tuple[dict | None, list[dict]]:
    """يعيد (الفائز بالخطر الأعلى, كل المطابقات). و«بلا ترويسة» ليست نتيجة هنا."""
    scoped = _provenance_scope(head)
    if not scoped:
        return None, []
    matched = [s for s in SOURCES if re.search(s["evidence"], scoped, re.I)]
    if not matched:
        return None, []
    matched.sort(key=lambda s: -s["risk"])
    return matched[0], matched


def body_declared_origins(body: str) -> list[str]:
    """أصول GPL يُصرَّح بها في **متن** الملف لا في ترويسته.

    ما يُكتشف هنا لا يُصنَّف مشتقًّا — فقد يقول التعليق «أُعيد تنفيذه لا نُسخ». لكنه **يُسمّى**
    بحالة `GPL_REFERENCED` تُطالب بمقابلة بالـdiff لا بالثقة: إمّا يُثبت الاستقلال، وإمّا يُعاد
    التنفيذ. وهذا الصنف كان سيضيع لو قُرئت الترويسة وحدها، وهو موجود فعلًا في هذه الشجرة.
    """
    hits: list[str] = []
    for src in SOURCES:
        if src["risk"] != RISK_GPL:
            continue
        for m in re.finditer(src["evidence"], body, re.I):
            window = body[max(0, m.start() - 120): m.end() + 120]
            if PROVENANCE_CONTEXT.search(window):
                hits.append(src["id"])
                break
    return hits


def module_family(rel: str) -> tuple[str, str, int]:
    for pattern, origin, lic, risk in MODULE_FAMILIES:
        if re.match(pattern, rel):
            return origin, lic, risk
    return "UNKNOWN", "Unknown", RISK_UNKNOWN


def holders(head: str) -> list[str]:
    out = []
    for year_range, name in COPYRIGHT.findall(head):
        cleaned = name.strip().rstrip(".")
        if cleaned and cleaned not in out:
            out.append(f"{cleaned} ({year_range.strip()})")
    return out[:4]


def classify_file(root: str, rel: str) -> dict:
    path = os.path.join(root, rel)
    ext = os.path.splitext(rel)[1].lower()
    fam_origin, fam_lic, fam_risk = module_family(rel)
    is_binary = ext in BINARY_EXTS or os.path.basename(rel) in BINARY_NAMES

    record: dict = {"file": rel, "binary": is_binary}

    # ٠) حطام بناء أو مادة توقيع — يُقاس قبل أي قراءة أخرى.
    for pattern, why in STRAY_ARTIFACTS:
        if re.search(pattern, rel):
            record.update(
                origin="MaxManager (build output)", license="Apache-2.0",
                spdx="Apache-2.0", risk=RISK_FREE, status="STRAY_ARTIFACT",
                evidence=why, arch=efl_arch(path) if ext == ".so" else "", holders=[],
            )
            return record

    # ١) الثنائيات: تُقرأ ترويستها الفعلية وبصمتها الداخلية لا اسمها.
    if is_binary:
        hits = bin_markers(path) if os.path.exists(path) else []
        record.update(
            origin=fam_origin,
            license=fam_lic,
            spdx=fam_lic,
            risk=fam_risk,
            status="THIRD_PARTY_BINARY" if fam_risk else "BINARY",
            evidence=f"ELF/بصمة داخلية: {', '.join(hits) if hits else '—'}",
            arch=efl_arch(path) if ext == ".so" or rel.endswith("libmagiskboot") else "",
            holders=[],
        )
        return record

    # ٢) ما ليس مصدرًا: بيانات عبأة تُطلب مراجعتها، وسواها بعائلة الوحدة.
    if ext not in SOURCE_EXTS:
        if any(re.match(p, rel) for p in DATA_ASSETS):
            record.update(
                origin="UNATTRIBUTED DATA ASSET", license="Unknown", spdx="Unknown",
                risk=RISK_UNKNOWN, status="DATA_ASSET_UNVERIFIED",
                evidence="بيانات عبأة بلا إسناد — لا تُدَّعى Apache-2.0 بالافتراضي",
                arch="", holders=[],
            )
            return record
        record.update(
            origin=fam_origin, license=fam_lic, spdx=fam_lic, risk=fam_risk,
            status="RESOURCE" if fam_risk == RISK_FREE else "RESOURCE_AT_RISK",
            evidence="مورد بلا ترويسة — يُصنَّف بعائلة وحدته",
            arch="", holders=[],
        )
        return record

    head = read_head(path)

    # ٣) نثر الأدوات والتوثيق لا يُصنَّف من ترويسته: فيه أسماء مشاريع كثيرة **وصفًا**.
    if rel.startswith(PROSE_ONLY_PREFIXES):
        record.update(
            origin=fam_origin, license=fam_lic, spdx=fam_lic, risk=fam_risk,
            status="PROSE_SCOPE",
            evidence="نثر: تُقرأ عائلة الوحدة دون استنتاج أصل من أسماء مذكورة",
            arch="", holders=holders(head),
        )
        return record

    winner, all_hits = classify_head(head)

    # ٤) لا أصل خارجي في الترويسة.
    if winner is None:
        # ٤-أ) لكن عائلة الوحدة تشهد لأصل GPL (مجلّد وارد بكامله من مشروع GPL: الطرفية).
        #      فهذه مشتقّة بمكانها لا بترويستها، والحالة تُسمّى `GPL_DERIVED` صراحةً —
        #      وتركها `REPO_DEFAULT` كان سيقول «رخصة المستودع» عن كود GPL ويسيء التسمية.
        if fam_risk == RISK_GPL:
            record.update(
                origin=fam_origin, license=fam_lic, spdx=fam_lic, risk=fam_risk,
                status="GPL_DERIVED",
                evidence="عائلة الوحدة تشهد للأصل: المجلّد وارد من مشروع GPL",
                arch="", holders=holders(head),
            )
            return record

        # ٤-ب) ترويسة ملكية، أو لا ترويسة إطلاقًا: تُصنَّف بعائلة الوحدة.
        record.update(
            origin=fam_origin, license=fam_lic, spdx=fam_lic, risk=fam_risk,
            status="NO_HEADER" if OWNERS.search(head) else "REPO_DEFAULT",
            evidence=(
                "ترويسة ملكية بلا ذكر أصل خارجي" if OWNERS.search(head)
                else "بلا ترويسة — يُصنَّف بعائلة الوحدة (افتراض مُعلَن)"
            ),
            arch="", holders=holders(head),
        )
        # ٥) ومع ذلك: قد يُصرَّح بالأصل في المتن لا في الترويسة ⇒ يُسمّى للمراجعة.
        #    ولا يُطالب بهذا ما كان مشتقًّا أصلًا (لا تُضاعف المطالبة على الملف نفسه).
        if fam_risk != RISK_GPL:
            try:
                body = open(path, encoding="utf-8", errors="replace").read()
            except OSError:
                body = head
            declared = body_declared_origins(body)
            if declared:
                record["status"] = "GPL_REFERENCED"
                record["declared_origin_in_body"] = declared
                record["evidence"] += f"؛ يُذكر في المتن: {'، '.join(declared)}"
        return record

    record.update(
        origin=winner["id"],
        license=winner["license"],
        spdx=winner["spdx"],
        risk=winner["risk"],
        reference=winner["reference"],
        status="GPL_DERIVED" if winner["risk"] == RISK_GPL else "APACHE_DERIVED",
        evidence="؛ ".join(h["id"] for h in all_hits),
        arch="",
        holders=holders(head),
        all_sources=[h["id"] for h in all_hits],
    )
    # ملف GPL يحمل ترويسة Apache-2.0 صريحة = التناقض الذي وُجد التدقيق لأجله.
    if winner["risk"] == RISK_GPL and APACHE_HEADER.search(head):
        record["contradiction"] = "ترويسة Apache-2.0 على مصدر مشتقّ من GPL"
    return record


# ── التبعيات ──────────────────────────────────────────────────────────────────

def parse_version_catalog(root: str) -> dict[str, str]:
    """كتالوج الإصدارات ⇒ {alias: «group:name:version»} — فلا نُكرّر جدولًا يدويًّا."""
    path = os.path.join(root, "manager", "gradle", "libs.versions.toml")
    if not os.path.isfile(path):
        return {}
    text = open(path, encoding="utf-8").read()
    versions: dict[str, str] = {}
    for block in ("versions",):
        m = re.search(rf"\[{block}\]\n(.*?)(?=\n\[|\Z)", text, re.S)
        if m:
            for name, ver in re.findall(r'^([\w.-]+)\s*=\s*"([^"]*)"', m.group(1), re.M):
                versions[name] = ver
    out: dict[str, str] = {}
    m = re.search(r"\[libraries\]\n(.*?)(?=\n\[|\Z)", text, re.S)
    if not m:
        return {}
    for line in m.group(1).splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        alias, rhs = line.split("=", 1)
        alias = alias.strip()
        group = re.search(r'group\s*=\s*"([^"]+)"', rhs)
        name = re.search(r'name\s*=\s*"([^"]+)"', rhs)
        module = re.search(r'module\s*=\s*"([^"]+)"', rhs)
        ver = re.search(r'version\s*=\s*"([^"]+)"', rhs)
        verref = re.search(r'version\.ref\s*=\s*"([^"]+)"', rhs)
        if module:
            coordinate = module.group(1)
        elif group and name:
            coordinate = f"{group.group(1)}:{name.group(1)}"
        else:
            continue
        if ver:
            coordinate += f":{ver.group(1)}"
        elif verref:
            coordinate += f":{versions.get(verref.group(1), '?')}"
        out[alias] = coordinate
    return out


def gradle_dependencies(root: str) -> list[dict]:
    catalog = parse_version_catalog(root)
    found: dict[str, str] = {}

    def resolve(token: str) -> str | None:
        key = token[len("libs."):] if token.startswith("libs.") else token
        for candidate in (key, key.replace(".", "-"), key.replace("-", ".")):
            if candidate in catalog:
                return catalog[candidate]
        # `libs.haze.blur` قد يُعرَّف `haze-blur` أو `haze.blur` أو `haze.blur.materials`
        parts = key.split(".")
        for cut in range(len(parts), 0, -1):
            for candidate in ("-".join(parts[:cut]), ".".join(parts[:cut])):
                if candidate in catalog:
                    return catalog[candidate]
        return None

    for dirpath, dirnames, filenames in os.walk(os.path.join(root, "manager")):
        dirnames[:] = [d for d in dirnames if d not in {"build", ".gradle"}]
        for name in filenames:
            if not (name.endswith(".gradle.kts") or name.endswith(".gradle")):
                continue
            path = os.path.join(dirpath, name)
            text = open(path, encoding="utf-8", errors="replace").read()
            for config, token in re.findall(
                r"\b(implementation|api|ksp|kapt|debugImplementation|releaseImplementation|"
                r"testImplementation|androidTestImplementation|compileOnly)\s*\(\s*"
                r"(?:project\s*\(\s*)?\"?([\w.:-]+)\"?",
                text,
            ):
                if token.startswith(":"):
                    continue
                if re.fullmatch(r"[\w.]+", token) and token.startswith("libs."):
                    coordinate = resolve(token)
                else:
                    coordinate = token
                if coordinate:
                    found[coordinate] = config
    out = []
    for coordinate, config in sorted(found.items()):
        out.append({
            "coordinate": coordinate,
            "scope": config,
            "license": dep_license(coordinate),
            "shipped": config not in {"testImplementation", "androidTestImplementation"},
        })
    return out


def dep_license(coordinate: str) -> str:
    for key, lic in DEP_LICENSES.items():
        if coordinate.startswith(key):
            return lic
    group = coordinate.split(":")[0]
    for key, lic in DEP_LICENSES.items():
        if key.rstrip(":") == group:
            return lic
    return "Unknown"


def cargo_dependencies(root: str) -> list[dict]:
    crates: dict[str, str] = {}
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in {"build", ".git", "target"}]
        if "Cargo.lock" not in filenames:
            continue
        path = os.path.join(dirpath, "Cargo.lock")
        text = open(path, encoding="utf-8", errors="replace").read()
        for block in text.split("[[package]]")[1:]:
            name = re.search(r'name\s*=\s*"([^"]+)"', block)
            ver = re.search(r'version\s*=\s*"([^"]+)"', block)
            if name:
                crates[name.group(1)] = ver.group(1) if ver else "?"
    local = {"rianixia-thermalcore", "maxmanager_native", "maxmanager-profilesettings", "maxmanager-utilityconf"}
    return [
        {
            "crate": name,
            "version": ver,
            "license": CARGO_LICENSES.get(name, "Apache-2.0" if name in local else CARGO_DEFAULT),
            "local": name in local,
        }
        for name, ver in sorted(crates.items())
    ]


# ── ثغرات ABI ─────────────────────────────────────────────────────────────────

def declared_abis(root: str) -> dict[str, list[str]]:
    out: dict[str, list[str]] = {}
    for module in ("app", "kernel-flasher"):
        path = os.path.join(root, "manager", module, "build.gradle.kts")
        if not os.path.isfile(path):
            continue
        text = open(path, encoding="utf-8", errors="replace").read()
        # تُكتب بثلاث صور في هذه الشجرة: `abiFilters.addAll(listOf(…))`، و`abiFilters += listOf(…)`،
        # و`abiFilters = listOf(…)`. والقراءة من الصورة **الثلاث** لا من واحدة منها.
        block = re.search(r"abiFilters[^\n]*?listOf\(([^)]*)\)", text)
        if block:
            out[module] = re.findall(r'"([\w-]+)"', block.group(1))
    return out


def abi_gaps(root: str, records: list[dict]) -> list[dict]:
    declared = declared_abis(root)
    gaps = []
    for module, abis in declared.items():
        jni = os.path.join(root, "manager", module, "src", "main", "jniLibs")
        if not os.path.isdir(jni):
            continue
        for abi in abis:
            d = os.path.join(jni, abi)
            # كل ABI مُعلن يجب أن يحمل ثنائياته؛ ومكتبة موجودة في ABI وآخر غائبة = عطب.
            if not os.path.isdir(d) or not os.listdir(d):
                gaps.append({"module": module, "abi": abi, "issue": "ABI declared but no libraries present"})
                continue
            present = {f for f in os.listdir(d)}
            for other in abis:
                if other == abi:
                    continue
                other_d = os.path.join(jni, other)
                if not os.path.isdir(other_d):
                    continue
                other_present = set(os.listdir(other_d))
                only_here = present - other_present
                for lib in sorted(only_here):
                    gaps.append({
                        "module": module, "abi": abi, "issue": "library missing for other ABI",
                        "library": lib, "missing_in": other,
                    })
    # إزالة التكرار (تُكتشف من الاتجاهين)
    seen = set()
    unique = []
    for g in gaps:
        key = (g["module"], g.get("library", ""), g["abi"], g["issue"], g.get("missing_in", ""))
        if key not in seen:
            seen.add(key)
            unique.append(g)
    return unique


# ── التجميع ───────────────────────────────────────────────────────────────────

ACTION_BY_STATUS = {
    "GPL_DERIVED": "REWRITE_OR_REMOVE",
    "GPL_REFERENCED": "VERIFY_BY_DIFF_OR_REWRITE",
    "APACHE_DERIVED": "REWRITE_FOR_IDENTITY",
    "NO_HEADER": "ADD_COPYRIGHT_HEADER",
    "REPO_DEFAULT": "ADD_COPYRIGHT_HEADER",
    "PROSE_SCOPE": "KEEP",
    "RESOURCE": "KEEP",
    "RESOURCE_AT_RISK": "REVIEW_ORIGIN",
    "DATA_ASSET_UNVERIFIED": "VERIFY_OR_REPLACE",
    "STRAY_ARTIFACT": "REMOVE_FROM_TRACKING",
    "THIRD_PARTY_BINARY": "REPLACE_OR_ATTRIBUTE",
    "BINARY": "ATTRIBUTE",
}


def build_report(root: str) -> dict:
    records = [classify_file(root, rel) for rel in tracked_files(root)]

    for rec in records:
        rec["action"] = ACTION_BY_STATUS.get(rec["status"], "REVIEW")
        if rec["status"] == "GPL_DERIVED" or rec.get("risk") == RISK_GPL:
            rec["action"] = "REWRITE_OR_REMOVE"
        rec["in_release_path"] = rec["file"].startswith(RELEASE_PATH_PREFIXES)
        if rec["file"].startswith("docs/") or rec["file"].startswith("tools/"):
            # التوثيق والأدوات لا تُشحن في الحزمة: الأصل يُسجَّل، والخطر لا يُحتسب توزيعًا.
            rec["in_release_path"] = False

    def count_risk(risk: int) -> list[dict]:
        return [r for r in records if r.get("risk") == risk]

    gpl_release = [r for r in count_risk(RISK_GPL) if r["in_release_path"]]
    referenced = [r for r in records if r["status"] == "GPL_REFERENCED"]
    deps = gradle_dependencies(root)
    crates = cargo_dependencies(root)

    gpl_deps = [d for d in deps if "GPL" in d["license"]]
    gpl_crates = [c for c in crates if "GPL" in c["license"]]
    # «مجهول» ليس GPL: الأول نقص بيان يُستدعى للمراجعة، والثاني خطر توزيع يُفشل البوابة.
    unknown_shipped = [d for d in deps if d["license"] == "Unknown" and d["shipped"]]

    gaps = abi_gaps(root, records)

    report = {
        "tool": "tools/license_audit.py",
        "root": os.path.basename(root),
        "summary": {
            "tracked_files": len(records),
            "gpl_derived_files": len(count_risk(RISK_GPL)),
            "gpl_referenced_files": len(referenced),
            "gpl_in_release_path": len(gpl_release),
            "unknown_license_files": len(count_risk(RISK_UNKNOWN)),
            "permissive_files": len(count_risk(RISK_FREE)),
            "gradle_dependencies": len(deps),
            "cargo_crates": len(crates),
            "gpl_dependencies": len(gpl_deps) + len(gpl_crates),
            "unknown_shipped_dependencies": len(unknown_shipped),
            "abi_gaps": len(gaps),
            "contradictions": len([r for r in records if r.get("contradiction")]),
        },
        "gpl_gate": {
            "gpl_source_in_owned_code": "YES" if gpl_release else "NO",
            "gpl_dependency": "YES" if (gpl_deps or gpl_crates) else "NO",
            "gpl_native_binary": "YES" if any(
                r["binary"] and r.get("risk") == RISK_GPL and r["in_release_path"] for r in records
            ) else "NO",
            "gpl_code_in_apk": "YES" if gpl_release else "NO",
            "gpl_derived_source_remaining": "YES" if count_risk(RISK_GPL) else "NO",
            "gpl_referenced_pending_diff_review": "YES" if referenced else "NO",
            "unknown_license_component": "YES" if count_risk(RISK_UNKNOWN) else "NO",
        },
        "files": records,
        "gradle_dependencies": deps,
        "cargo_crates": crates,
        "abi_gaps": gaps,
    }
    return report


# ── المخرجات ──────────────────────────────────────────────────────────────────

def write_json(root: str, report: dict) -> str:
    dest = os.path.join(root, "build", "license-report.json")
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    with open(dest, "w", encoding="utf-8") as fh:
        json.dump(report, fh, ensure_ascii=False, indent=2, sort_keys=False)
        fh.write("\n")
    return dest


def write_provenance(root: str, report: dict) -> str:
    dest = os.path.join(root, "docs", "PROVENANCE.md")
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    s = report["summary"]
    gate = report["gpl_gate"]
    out: list[str] = []
    a = out.append

    a("# PROVENANCE — أصل كل ملف ورخصته وما يجب عمله")
    a("")
    a("> **مُولَّد آليًّا — لا يُحرَّر يدويًّا:**")
    a("> `python3 tools/license_audit.py --provenance`")
    a("> （و`build/license-report.json` هو نفس القياس بصيغة يقرؤها CI）")
    a("")
    a("**الطريقة:** يُصنَّف كل ملف متعقّب في git من **ترويسته الفعلية** (أول "
      f"{HEAD_LINES} سطرًا) لا من اسمه ولا من مجلّده. ومن لا ترويسة له يُصنَّف بعائلة وحدته "
      "ويُكتب ذلك صراحةً في عمود الدليل. والأصول وتراخيصها ومراجعها مقيّدة في جدول "
      "`SOURCES` داخل الأداة، فكل حكم هنا قابل لإعادة الاشتقاق بأمر واحد.")
    a("")
    a("## الخلاصة")
    a("")
    a("| المقياس | العدد |")
    a("| --- | --- |")
    a(f"| ملفات متعقّبة | {s['tracked_files']} |")
    a(f"| ملفات مشتقّة من GPL | **{s['gpl_derived_files']}** |")
    a(f"| منها داخل مسار الإصدار | **{s['gpl_in_release_path']}** |")
    a(f"| ملفات مجهولة الترخيص | {s['unknown_license_files']} |")
    a(f"| ملفات برخصة حُرّة | {s['permissive_files']} |")
    a(f"| تبعيات Gradle | {s['gradle_dependencies']} |")
    a(f"| صناديق Cargo | {s['cargo_crates']} |")
    a(f"| ثغرات ABI | {s['abi_gaps']} |")
    a(f"| تناقضات ترويسة (GPL + Apache-2.0) | {s['contradictions']} |")
    a("")
    a("## بوابة GPL (PHASE 10)")
    a("")
    a("| السؤال | الجواب |")
    a("| --- | --- |")
    for key, label in (
        ("gpl_source_in_owned_code", "GPL source in MaxManager-owned code"),
        ("gpl_dependency", "GPL dependency"),
        ("gpl_native_binary", "GPL native binary"),
        ("gpl_code_in_apk", "GPL code in APK"),
        ("gpl_derived_source_remaining", "GPL-derived source remaining"),
        ("gpl_referenced_pending_diff_review", "GPL declared in body — pending diff review"),
        ("unknown_license_component", "Unknown-license component"),
    ):
        a(f"| {label} | **{gate[key]}** |")
    a("")
    a("**وما دام أيٌّ منها `YES` فالتنظيف غير مكتمل** — والأداة تُفشل CI (`--assert`) عند "
      "`gpl_code_in_apk = YES`.")
    a("")

    a("## أ‌) ملفات مشتقّة من GPL — تُعاد كتابتها أو تُحذف")
    a("")
    gpl = [r for r in report["files"] if r.get("risk") == RISK_GPL]
    if not gpl:
        a("لا شيء. ✅")
    else:
        a("| FILE | ORIGIN | LICENSE | STATUS | ACTION |")
        a("| --- | --- | --- | --- | --- |")
        for r in sorted(gpl, key=lambda x: x["file"]):
            a(f"| `{r['file']}` | {r['origin']} | {r['spdx']} | {r['status']} | **{r['action']}** |")
    a("")
    refd = [r for r in report["files"] if r["status"] == "GPL_REFERENCED"]
    if refd:
        a("## ب) أصل GPL مُعلَن في المتن لا في الترويسة — يُحسم بمقابلة (diff) لا بثقة")
        a("")
        a("هذه ملفات **يقول تعليقها** إنها مأخوذة/مقتبسة من مشروع GPL، ولا تحمل ترويسة حقوق.")
        a("وصفها بالاستقلال يفترضها لا يثبتها — فالحكم فيها: إمّا مقابلة تكشف أنها مكتوبة من جديد فعلًا،")
        a("وإمّا إعادة تنفيذ مستقلّة. ولا تُترك كما هي.")
        a("")
        a("| FILE | يُصرَّح بأصله | الدليل | ACTION |")
        a("| --- | --- | --- | --- |")
        for r in sorted(refd, key=lambda x: x["file"]):
            a(f"| `{r['file']}` | {'، '.join(r.get('declared_origin_in_body', []))} | "
              f"{r['evidence']} | **{r['action']}** |")
        a("")

    a("## ج) ملفات بلا أصل خارجي مُعلَن")
    a("")
    noh = [r for r in report["files"] if r["status"] in {"NO_HEADER", "RESOURCE"}]
    a(f"العدد: **{len(noh)}** ملفًا (موارد، أيقونات، خطوط، بيانات، ومصادر بترويسة ملكية "
      "داخلية بلا ذكر أصل خارجي). وتفصيلها الكامل في `build/license-report.json`.")
    a("")

    a("## د) التبعيات الخارجية")
    a("")
    a("### Gradle")
    a("")
    a("| COORDINATE | SCOPE | SHIPPED | LICENSE |")
    a("| --- | --- | --- | --- |")
    for d in report["gradle_dependencies"]:
        a(f"| `{d['coordinate']}` | {d['scope']} | {'نعم' if d['shipped'] else 'لا (اختبار)'} | {d['license']} |")
    a("")
    a("### Cargo")
    a("")
    a("| CRATE | VERSION | LOCAL | LICENSE |")
    a("| --- | --- | --- | --- |")
    for c in report["cargo_crates"]:
        a(f"| `{c['crate']}` | {c['version']} | {'نعم' if c['local'] else 'لا'} | {c['license']} |")
    a("")

    a("## هـ) الثنائيات ومعمارياتها")
    a("")
    a("| FILE | ARCH | ORIGIN | LICENSE | ACTION |")
    a("| --- | --- | --- | --- | --- |")
    for r in report["files"]:
        if r["binary"]:
            a(f"| `{r['file']}` | {r.get('arch') or '—'} | {r['origin']} | {r['spdx']} | "
              f"**{r['action']}** |")
    a("")

    if report["abi_gaps"]:
        a("### ثغرات ABI")
        a("")
        a("| MODULE | ABI | المكتبة | العطب |")
        a("| --- | --- | --- | --- |")
        for g in report["abi_gaps"]:
            a(f"| {g['module']} | {g['abi']} | `{g.get('library', '—')}` | "
              f"{g['issue']}{' ← غائبة في ' + g['missing_in'] if g.get('missing_in') else ''} |")
        a("")

    a("## و) حدود هذا التدقيق")
    a("")
    a("* الحكم مبنيّ على **ما هو مُعلَن في الترويسة**، لا على تشابه دلالي يُقاس بالـdiff. "
      "فملف بلا ترويسة قد يكون مُشتقًّا وهو غير معروف — وهذا احتمال يُدار بالمراجعة البشرية "
      "لا يُدَّعى نفيه. ولذلك تُفصل حالة `GPL_REFERENCED` عن `GPL_DERIVED`: الأولى **دعوى "
      "استقلال** لم تُختبر بعد، والثانية **إقرار بأصل**.")
    a("* الاسم في الترويسة يُحتسب أصلًا **فقط** إذا جاء في سطر يحمل سياق نسبة "
      "(مأخوذ · مبني على · حقوق · رخصة). بلا هذا الشرط كانت ثوابت مسارات مثل "
      "`/data/data/com.termux/files/usr/bin` تُصنّف ملفاتها «مشتقّة من Termux» — وهي "
      "إيجابية كاذبة أُزيلت بقياس لا بتقدير.")
    a("* `docs/` و`tools/` تُصنَّفان بعائلة وحدتهما ولا يُستنتج أصلهما من أسماء مشاريع "
      "تُذكر فيهما وصفًا؛ فلا يظهر نثر الأدوات «مشتقًّا» من كل من يُسمّى فيه.")
    a("* ما لا أصل خارجي له ولا عائلة وحدة معروفة يُصنَّف **افتراضًا مُعلَنًا**: رخصة المستودع، "
      "بحالة `REPO_DEFAULT`. وهذا افتراض عن نطاق المشروع لا قياس — والبيانات العبأة "
      "(`devices.db` · `socs.json` · `maxmanagerApplist.json`) **مُستثناة** منه وتبقى "
      "`Unknown` حتى يُكتب إسنادها.")
    a("* لا تصل الأداة إلى الشبكة: التراخيص من جدول مُنتقى بسند، وما ليس فيه يُكتب `Unknown`.")
    a("* جدول `DEP_LICENSES` يغطّي التبعيات المستعملة اليوم؛ وإضافة تبعية جديدة بدون سطر "
      "له تظهر `Unknown` لا `Apache-2.0`.")
    a("")

    # جدول كامل مضغوط — للمراجعة البشرية على الملفات المصدرية فقط.
    a("## ز) جدول الملفات المصدرية الكامل")
    a("")
    a("| FILE | ORIGIN | LICENSE | STATUS | ACTION |")
    a("| --- | --- | --- | --- | --- |")
    for r in sorted(report["files"], key=lambda x: x["file"]):
        if r["binary"] or r["status"] == "RESOURCE":
            continue
        a(f"| `{r['file']}` | {r['origin']} | {r['spdx']} | {r['status']} | {r['action']} |")
    a("")

    with open(dest, "w", encoding="utf-8") as fh:
        fh.write("\n".join(out))
    return dest


def print_summary(report: dict) -> None:
    s = report["summary"]
    print("LICENSE-AUDIT")
    print(f"  ملفات متعقّبة        : {s['tracked_files']}")
    print(f"  مشتقّ من GPL          : {s['gpl_derived_files']}")
    print(f"  يُذكر فيه أصل GPL      : {s['gpl_referenced_files']}  (يلزم مقابلة بالـdiff)")
    print(f"  GPL داخل مسار الإصدار : {s['gpl_in_release_path']}")
    print(f"  مجهول الترخيص        : {s['unknown_license_files']}")
    print(f"  تبعيات Gradle        : {s['gradle_dependencies']}  · صناديق Cargo: {s['cargo_crates']}")
    print(f"  ثغرات ABI            : {s['abi_gaps']}")
    print("  ── بوابة GPL ──")
    for key, value in report["gpl_gate"].items():
        print(f"     {key:34s}: {value}")


def mode_assert(root: str, report: dict) -> int:
    gate = report["gpl_gate"]
    print_summary(report)
    blocking = []
    if gate["gpl_code_in_apk"] == "YES":
        blocking.append("كود مشتقّ من GPL داخل مسار الإصدار (سيدخل الحزمة)")
    if gate["gpl_native_binary"] == "YES":
        blocking.append("ثنائية GPL داخل مسار الإصدار")
    if gate["gpl_dependency"] == "YES":
        blocking.append("تبعية GPL")
    if blocking:
        print("\n❌ بوابة الترخيص: فشل")
        for item in blocking:
            print(f"   · {item}")
        print("   الحلّ: أعد التنفيذ مستقلًّا، أو احذف المكوّن، أو اكتب قرارًا موثَّقًا.")
        return 1
    print("\n✅ بوابة الترخيص: لا مكوّن GPL في مسار الإصدار.")
    return 0


# ── الاختبار الذاتي ───────────────────────────────────────────────────────────

def mode_self_test() -> int:
    """أداة تُصنّف كل شيء «سليمًا» لا تُثبت شيئًا — فهذه الحالات المعروفة تُقاس أولًا."""
    checks: list[tuple[str, bool, str]] = []
    tmp = tempfile.mkdtemp(prefix="licaudit-")
    try:
        root = os.path.join(tmp, "repo")
        os.makedirs(os.path.join(root, "tools"))
        os.makedirs(os.path.join(root, "manager", "app", "src", "main", "java"))

        def put(rel: str, body: str) -> None:
            path = os.path.join(root, rel)
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, "w", encoding="utf-8") as fh:
                fh.write(body)

        put("manager/app/src/main/java/A.kt",
            "/*\n * Adapted from ZKM (Zuan Kernel Manager)\n * Copyright (c) 2025 ZKM\n */\n")
        put("manager/app/src/main/java/B.kt",
            "/*\n * Copyright (C) 2026 Zexshia\n * Licensed under the Apache License, Version 2.0\n */\n")
        put("manager/app/src/main/java/C.kt",
            "/*\n * Copyright (C) 2026 Zexshia\n * Adapted from ZKM's Frosting\n * Licensed under the Apache License, Version 2.0\n */\n")

        # ١) ZKM ⇒ GPL، و٢) ترويسة ملكية ⇒ بلا أصل خارجي
        a = classify_file(root, "manager/app/src/main/java/A.kt")
        checks.append(("ترويسة ZKM تُصنَّف GPL-3.0", a["spdx"] == "GPL-3.0-only", a["spdx"]))
        b = classify_file(root, "manager/app/src/main/java/B.kt")
        checks.append(("ترويسة ملكية بلا أصل خارجي ⇒ NO_HEADER مع عائلة الوحدة",
                       b["status"] == "NO_HEADER" and b["origin"].startswith("MaxManager"), b["status"]))
        # ٣) GPL + ترويسة Apache = تناقض يُعلَن
        c = classify_file(root, "manager/app/src/main/java/C.kt")
        checks.append(("تناقض GPL مع ترويسة Apache يُعلَن",
                       c["spdx"] == "GPL-3.0-only" and "contradiction" in c,
                       str(c.get("contradiction"))))

        # ٤) الثنائيات تُصنَّف بعائلتها لا بامتدادها فقط
        os.makedirs(os.path.join(root, "manager", "app", "src", "main", "jniLibs", "arm64-v8a"), exist_ok=True)
        so = os.path.join(root, "manager/app/src/main/jniLibs/arm64-v8a/libtermux.so")
        with open(so, "wb") as fh:
            # ترويسة ELF صحيحة المواضع: e_ident[16] ثم e_type[2] ثم e_machine[2] عند 18.
            fh.write(b"\x7fELF")
            fh.write(bytes([2, 1, 1, 0]) + bytes(8))
            fh.write(b"\x02\x00")                 # e_type = ET_EXEC
            fh.write(struct.pack("<H", 0xB7))      # e_machine = EM_AARCH64
            fh.write(bytes(400))
            fh.write(b"com.termux.terminal")
        r = classify_file(root, "manager/app/src/main/jniLibs/arm64-v8a/libtermux.so")
        checks.append(("معمارية ELF تُقرأ من الترويسة", r["arch"] == "arm64-v8a", r["arch"]))
        checks.append(("libtermux.so ⇒ Termux/GPL", r["spdx"] == "GPL-3.0-only", r["spdx"]))
        checks.append(("بصمة داخل الثنائية تُسجَّل", "com.termux.terminal" in r["evidence"], r["evidence"]))

        # ٥) جدول رخص التبعيات يميّز المعروف من المجهول
        checks.append(("تبعية androidx معروفة", dep_license("androidx.core:core-ktx:1.2.0") == "Apache-2.0", ""))
        checks.append(("تبعية غريبة تبقى Unknown", dep_license("com.nobody:thing:1.0") == "Unknown", ""))

        # ٦) ثغرة ABI تُكتشف: ABI مُعلن بلا مكتبات، ومكتبة في ABI وغائبة في آخر
        put("manager/app/build.gradle.kts",
            'ndk { abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a")) }\n')
        put("manager/kernel-flasher/build.gradle.kts",
            'ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }\n')
        os.makedirs(os.path.join(root, "manager/app/src/main/jniLibs/armeabi-v7a"), exist_ok=True)
        # libtermux.so في arm64 وحدها ⇒ ثغرة معلَنة
        gaps = abi_gaps(root, [])
        kinds = {(g["module"], g["issue"], g.get("library")) for g in gaps}
        checks.append((
            "مكتبة في ABI وغائبة في آخر تُكتشف",
            ("app", "library missing for other ABI", "libtermux.so") in kinds,
            str(sorted(kinds)),
        ))

        # ٧) بوابة GPL تُفشل عند GPL في مسار الإصدار وتَنجح عند غيابه
        report = build_report(root)
        checks.append(("بوابة GPL تفشل مع ثنائية GPL في الإصدار",
                       mode_assert_deep(root, report) == 1, ""))
        # ٨) الحساب على مستوى الملف لا على الثنائية وحدها: بعد حذف الثنائية يبقى الملفان
        #    المشتقّان من GPL محسوبين (A.kt وC.kt) — والعدد متوقّع مسبقًا لا «>= 1».
        os.remove(so)
        after_so = build_report(root)
        checks.append(("حذف الثنائية لا يُخفي مصدرًا مشتقًّا من GPL",
                       after_so["summary"]["gpl_in_release_path"] == 2,
                       str(after_so["summary"]["gpl_in_release_path"])))

        # ٩) شجرة بلا GPL تمرّ فعلًا — وإلا فالبوابة تفشل دائمًا فلا تقيس شيئًا
        for victim in ("manager/app/src/main/java/A.kt", "manager/app/src/main/java/C.kt"):
            os.remove(os.path.join(root, victim))
        clean = build_report(root)
        checks.append(("شجرة بلا GPL تمرّ من البوابة",
                       mode_assert_deep(root, clean) == 0
                       and clean["summary"]["gpl_in_release_path"] == 0,
                       f"gate={clean['gpl_gate']['gpl_code_in_apk']} "
                       f"n={clean['summary']['gpl_in_release_path']}"))

        ok = True
        for name, passed, detail in checks:
            print(("✅ " if passed else "❌ ") + name + (f"  ← {detail}" if detail and not passed else ""))
            ok = ok and passed
        print(f"\nنتيجة الاختبار الذاتي: {sum(1 for _, p, _ in checks if p)}/{len(checks)}")
        return 0 if ok else 1
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def mode_assert_deep(root: str, report: dict) -> int:
    """نسخة صامتة من الحكم — تُستعمل في الاختبار الذاتي بلا طبع."""
    gate = report["gpl_gate"]
    bad = gate["gpl_code_in_apk"] == "YES" or gate["gpl_native_binary"] == "YES" or gate["gpl_dependency"] == "YES"
    return 1 if bad else 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="تدقيق الأصل والترخيص: من أين جاء كل ملف، وبأي حقّ، وما العمل."
    )
    parser.add_argument("--json", action="store_true", help="اكتب build/license-report.json")
    parser.add_argument("--provenance", action="store_true", help="اكتب docs/PROVENANCE.md")
    parser.add_argument("--assert", dest="do_assert", action="store_true",
                        help="اخرج بخطأ إن دخل مكوّن GPL إلى مسار الإصدار")
    parser.add_argument("--self-test", action="store_true", help="الأداة تقيس نفسها")
    parser.add_argument("--shown", type=int, default=0, help="اطبع أول N ملفًا خطرها GPL")
    parser.add_argument("--root", default=None, help="جذر الشجرة (يُكتشف افتراضيًّا)")
    args = parser.parse_args(argv)

    if args.self_test:
        return mode_self_test()

    root = repo_root(args.root)
    report = build_report(root)

    if args.json:
        print(f"كُتب: {os.path.relpath(write_json(root, report), root)}")
    if args.provenance:
        print(f"كُتب: {os.path.relpath(write_provenance(root, report), root)}")
    print_summary(report)

    if args.shown:
        print("\nملفات GPL:")
        for r in [x for x in report["files"] if x.get("risk") == RISK_GPL][: args.shown]:
            print(f"  {r['spdx']}  {r['file']}   [{r['origin']}] {r['action']}")

    if args.do_assert:
        return mode_assert(root, report)
    return 0


if __name__ == "__main__":
    sys.exit(main())
