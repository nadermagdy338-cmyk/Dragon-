#!/usr/bin/env python3
"""بوابة عقد التحزيم والتكامل — تقيس ما كان يُقاس بالعين.

لماذا وُجدت: جولة `BUNDLE-01` كشفت أنّ **التحقّق من الحزمة كان يدويًّا في أغلبه**، وأن
ثلاثة أعطاب مرّت لأنّ ما يحرسها لم يكن مكتوبًا:

* **سلسلة الإصدار** كانت تُشتقّ في الـworkflow (`5.2 (…-Dazzling)`) وتُطالَب بها
  `MaxManager.h` وهي تقول `V1` ⇒ شرط مستحيل يُحمرّ كل تشغيل. والحرس الحقيقيّ أنّ
  `check_module_version()` في الخادم يُشغّل `grep -q '^version=%s$' module.prop`
  واختلاف بايت **يُخرج الخادم عند الإقلاع**. فالاتّفاق ليس ترفًا: هو شرط إقلاع.
* **مسار الديمون** مكتوب في أربعة ملفّات (`Android.bp` · `file_contexts` ·
  `maxmanager.rc` · `product-inclusion.mk`)، وقد **انزاح فعلًا**: الـ`.rc` كان يُنسخ إلى
  `vendor/etc/init` والثنائيّ في `/system/bin`، و`maxmanager.te` يوصفه بـ`vendor_file_type`
  ومساره `/system/bin`. ولا شيء كان يقارن الأربعة.
* **`customize.sh` يستخرج ملفّات من الحزمة**، فحذف `module.banner.avif` من الشجرة
  ونسيانه هنا يعني **حزمة لا تُفلّش** (`extract` تُوقف التركيب عند ملفّ غائب). ولا شيء
  كان يقابل ما يُستخرج بما يُحزَّم.

فالبوابة تجمع هذه العقود في مكان واحد، وتعمل **محليًّا في ثوانٍ** (لا مُصرّف ولا جهاز)،
وهو الشرط الذي جعل البوابات الخفيفة هي البديل المعتمد عن البناء في هذا المستودع.

الأمثلة:
    python3 tools/bundle_contract.py --assert     # يفشل عند أي انزياح عن العقد
    python3 tools/bundle_contract.py --json       # الفحوص ونتائجها
    python3 tools/bundle_contract.py --self-test  # يقيس الأداة نفسها: يكسر ويطالب بالفشل
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)

# الملفّات التي يقوم عليها العقد. تُجمع في مكان واحد ليستطيع `--self-test` نسخها إلى
# شجرة مؤقّتة ثم كسرها — فتُقاس الأداة على عطب معروف لا على ثقة.
CONTRACT_FILES = [
    "version",
    "mainfiles/module.prop",
    "mainfiles/customize.sh",
    "mainfiles/verify.sh",
    "archdaemon/jni/include/MaxManager.h",
    "archdaemon/jni/src/MaxManagerUtility/ModuleIntegrity.c",
    "android/aosp/Android.bp",
    "android/aosp/BoardConfig.mk",
    "android/aosp/product-inclusion.mk",
    "android/aosp/maxmanager.rc",
    "android/aosp/sepolicy/maxmanager.te",
    "android/aosp/sepolicy/file_contexts",
    "android/overlay/product/etc/permissions/privapp-permissions-nd.max.xml",
    ".github/scripts/compile_zip.sh",
    ".github/scripts/build_developer_bundle.sh",
    ".github/scripts/generatesha256.sh",
    ".github/scripts/verify.sh",
    ".github/workflows/build.yml",
]

# ما يُنتجه المُحزِّم داخل `mainfiles/` قبل الضغط، فلا وجود له في الشجرة.
PACKER_PRODUCED_PREFIXES = ("libs/", "system/bin/", "system/product/")

CHECKSUM_MANIFESTS = ("checksums-module.sha256", "checksums-developer-bundle.sha256")

WORKFLOW = ".github/workflows/build.yml"

# العلَم كما يُسجَّل فعلًا في argparse — لا كما يُذكر في وصف أو تعليق. وهذا التمييز
# مقيس: من ١٣ أداة تسجّل العلَم لم يكن في الشجرة **واحدة** تذكره في نصّها فقط، فالقاعدة
# تصف الواقع ولا تُنتج بلاغًا كاذبًا.
SELFTEST_FLAG = re.compile(r"add_argument\([^)]*--self-test", re.S)

# والاستثناء الوحيد **مُعلَن بسبب مقيس**: تشغيله في الـrunner يعني بوابة حمراء دائمًا
# لا بوابة تقيس (المحرّك والاوزان تُثبّت خارج المستودع عمدًا).
SELFTEST_NOT_IN_CI = {
    "read_image_text.py": "يحتاج محرّك OCR خارجيًّا (tesseract/easyocr) لا يوجد في الـrunner",
}


def read(root: str, rel: str) -> str:
    path = os.path.join(root, rel)
    with open(path, encoding="utf-8", errors="replace") as handle:
        return handle.read()


def clean(value: str) -> str:
    """يُزيل CR/LF والمسافات الطرفية — المقارنة بايت ببايت على السلسلة وحدها."""
    return value.strip("\n\r \t")


def code_lines(text: str) -> list[str]:
    """سطور الكود وحدها: التعليق يشرح العقد ولا يُنفّذه.

    **وقد قيس هذا مرّتين في الجولة نفسها.** الفحص كان يقرأ **كل** سطر فيه
    `privapp-permissions` ونقطتان، وكل سطر فيه `maxmanager.rc:` — فيلتقط شرحًا في
    تعليق كما لو أنه قاعدة نسخ. والفرق ليس تجميليًّا: لو أشار التعليق إلى مسار
    صحيح وقاعدة النسخ إلى مسار خاطئ، لأبلغت البوابة «✓» عن عقد مكسور. وقِيس العطب
    مرّة أخرى في الاتجاه المعاكس: طفرة أصابت سطرًا في تعليق فبدت الأداة غير قائسة.
    """
    return [line for line in text.splitlines() if not line.lstrip().startswith(("#", "//"))]


def version_strings(root: str) -> dict[str, str]:
    header = re.search(
        r'^#define\s+MODULE_VERSION\s+"([^"]*)"', read(root, "archdaemon/jni/include/MaxManager.h"), re.M
    )
    prop = re.search(r"^version=(.*)$", read(root, "mainfiles/module.prop"), re.M)
    return {
        "version": clean(read(root, "version")),
        "mainfiles/module.prop": clean(prop.group(1)) if prop else "",
        "archdaemon/jni/include/MaxManager.h": clean(header.group(1)) if header else "",
    }


def check_version_triangle(root: str) -> str | None:
    """المصادر الثلاثة تقول الشيء نفسه — وإلا يُخرج الخادم عند الإقلاع."""
    homes = version_strings(root)
    values = set(homes.values())
    if "" in values:
        missing = [where for where, value in homes.items() if not value]
        return f"سلسلة الإصدار غائبة في: {', '.join(missing)}"
    if len(values) != 1:
        detail = " · ".join(f"{where}='{value}'" for where, value in homes.items())
        return f"سلسلة الإصدار متفرّعة (وهذا شرط إقلاع الخادم): {detail}"
    return None


def module_identity(root: str) -> dict[str, tuple[str, str]]:
    """هوية الوحدة كما تعلنها جهتان: `module.prop` المشحون، والحارس الذي يقرؤه."""
    prop = read(root, "mainfiles/module.prop")
    daemon = read(root, "archdaemon/jni/src/MaxManagerUtility/ModuleIntegrity.c")

    def prop_field(key: str) -> str:
        match = re.search(rf"^{key}=(.*)$", prop, re.M)
        return clean(match.group(1)) if match else ""

    def daemon_field(name: str) -> str:
        match = re.search(rf'^#define\s+{name}\s+"([^"]*)"', daemon, re.M)
        return match.group(1) if match else ""

    return {
        "mainfiles/module.prop": (prop_field("name"), prop_field("author")),
        "ModuleIntegrity.c": (
            daemon_field("MODULE_IDENTITY_NAME"),
            daemon_field("MODULE_IDENTITY_AUTHOR"),
        ),
    }


def check_module_identity(root: str) -> str | None:
    """الاسم والمؤلف: `module.prop` المشحون **و** الحارس الذي يقرؤهما — نصًّا واحدًا.

    وهذا عطب حقيقي مقيس لا فحص نظري: حارس `is_kanged()` كان ينفّذ
    `grep -q '^name=Max Manager$'`، وقد صار `module.prop` يقول `name=MaxManager` (تسمية
    مقصودة لاحقة). فصار الحارس يرفض الوحدة **نفسها** على كل تنصيب نظيف:

        F MaxManager: EVENT=MODULE_INTEGRITY_FAILED reason=modified_by_third_party

    ثم `exit(EXIT_FAILURE)` وتصفير `persist.sys.maxmanager.service` — فيصمت كل ما يملكه
    الخادم (الملفّ العام، الحاكم لكل تطبيق، `--checkbypasschg`)، وهذه هي رسالة المستخدم
    «MaxManager daemon is not running». فهذه البوابة تجعل تغيير الاسم يُسقط بناءً،
    لا يُخرج خادمًا على جهاز. وتُقاس من الطرفين: النصّ المشحون ونصّ الحارس، لا أحدهما.
    """
    homes = module_identity(root)
    shipped = homes["mainfiles/module.prop"]
    daemon = homes["ModuleIntegrity.c"]
    if "" in shipped or "" in daemon:
        return f"هوية الوحدة ناقصة: module.prop name/author={shipped} · الحارس={daemon}"
    if shipped != daemon:
        return (
            "هوية الوحدة متفرّعة (وهذا يُخرج الخادم عند الإقلاع): "
            f"module.prop name/author={shipped} · الحارس يطلب={daemon}"
        )
    return None


def service_line(root: str) -> tuple[str, str] | None:
    match = re.search(r"^service\s+(\S+)\s+(\S+)\s*$", read(root, "android/aosp/maxmanager.rc"), re.M)
    return (match.group(1), match.group(2)) if match else None


def check_daemon_path(root: str) -> str | None:
    """الاسم والمسار: أربعة ملفّات، واتّفاق واحد."""
    parsed = service_line(root)
    if not parsed:
        return "maxmanager.rc: لا سطر `service <name> <path>`"
    name, path = parsed
    problems: list[str] = []

    if not re.search(rf'^\s*name:\s*"{re.escape(name)}"', read(root, "android/aosp/Android.bp"), re.M):
        problems.append(f"Android.bp بلا `name: \"{name}\"`")

    if name not in read(root, "android/aosp/product-inclusion.mk"):
        problems.append(f"product-inclusion.mk لا يدرج {name} في PRODUCT_PACKAGES")

    labelled = [
        line.split()[0]
        for line in read(root, "android/aosp/sepolicy/file_contexts").splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    ]
    if path not in labelled:
        problems.append(f"file_contexts لا يوسم {path} (الموجود: {', '.join(labelled) or 'لا شيء'})")

    return " · ".join(problems) if problems else None


def check_rc_partition(root: str) -> str | None:
    """الـ`.rc` يُنسخ إلى قسم الثنائيّ نفسه: `/system/bin` ⇒ `/system/etc/init`."""
    parsed = service_line(root)
    if not parsed:
        return None
    _, path = parsed
    package = "/".join(path.split("/")[:-1])  # /system/bin
    partition = path.split("/")[1] if path.startswith("/") else ""
    want = f"$(TARGET_COPY_OUT_{partition.upper()})" if partition else ""
    lines = [
        line for line in code_lines(read(root, "android/aosp/product-inclusion.mk"))
        if "maxmanager.rc:" in line
    ]
    if not lines:
        return "product-inclusion.mk لا ينسخ maxmanager.rc (فلا خدمة init في الروم)"
    if want and want not in lines[0]:
        return f"الـ.rc يُنسخ إلى قسم مخالف للثنائيّ {package}: المتوقّع {want} · السطر: {lines[0].strip()}"
    return None


def check_sepolicy_partition(root: str) -> str | None:
    """نوع التنفيذ لا يُوصف بقسم يخالف مساره الموسوم."""
    parsed = service_line(root)
    if not parsed:
        return None
    _, path = parsed
    text = read(root, "android/aosp/sepolicy/maxmanager.te")
    type_line = next(
        (line for line in code_lines(text) if re.match(r"^\s*type\s+maxmanager_exec\b", line)), None
    )
    if not type_line:
        return "maxmanager.te لا يُعلن `type maxmanager_exec`"
    attributes = {token.strip() for token in type_line.split("=", 1)[-1].split(";")[0].split(",")}
    if path.startswith("/system/") and "vendor_file_type" in attributes:
        return "maxmanager_exec يوصف بـ`vendor_file_type` ومساره في /system (نقيض نفسه)"
    if path.startswith("/vendor/") and "vendor_file_type" not in attributes:
        return "maxmanager_exec في /vendor بلا `vendor_file_type`"
    return None


def check_board_has_no_product(root: str) -> str | None:
    """`PRODUCT_*` لا تُقرأ من BoardConfig — ووجودها هناك عطب صامت."""
    offenders = [
        f"{index}: {line.strip()}"
        for index, line in enumerate(read(root, "android/aosp/BoardConfig.mk").splitlines(), 1)
        if re.search(r"^\s*(PRODUCT_[A-Z_]+)", line) and not line.lstrip().startswith("#")
    ]
    if offenders:
        return "BoardConfig.mk يحمل متغيّرات لا تُقرأ منه: " + " · ".join(offenders)
    return None


def check_permission_xml(root: str) -> str | None:
    """الصلاحيات المميّزة: اسم موجود، وعلى قسم التطبيق نفسه."""
    text = read(root, "android/aosp/product-inclusion.mk")
    copies = [
        line.strip() for line in code_lines(text)
        if "privapp-permissions" in line and ":" in line
    ]
    if not copies:
        return "product-inclusion.mk لا ينسخ XML الصلاحيات"
    source, destination = copies[0].split(":", 1)
    basename = os.path.basename(source.strip())
    if basename != "privapp-permissions-nd.max.xml":
        return f"اسم XML الصلاحيات مخالف: {basename}"
    if not os.path.exists(os.path.join(root, "android/overlay/product/etc/permissions", basename)):
        return f"XML المُشار إليه غير موجود في الشجرة: {basename}"
    if "$(TARGET_COPY_OUT_PRODUCT)" not in destination:
        return f"XML الصلاحيات يهبط على قسم مخالف للـAPK (‏product_specific): {destination.strip()}"
    return None


def check_no_banner(root: str) -> str | None:
    """الـbanner: أُزيل من الطلب والطلبُ نفسه يفرض غيابه من ثلاثة مواضع."""
    problems: list[str] = []
    if re.search(r"^banner=", read(root, "mainfiles/module.prop"), re.M):
        problems.append("module.prop لا يزال يعلن `banner=`")
    if os.path.exists(os.path.join(root, "mainfiles/module.banner.avif")):
        problems.append("module.banner.avif لا يزال في mainfiles/")
    if "module.banner.avif" in read(root, ".github/scripts/compile_zip.sh"):
        problems.append("compile_zip.sh لا يزال يطالب به في need_integrity")
    return " · ".join(problems) if problems else None


def packer_provided_paths(root: str) -> set[str]:
    """ما يضعه المُحزِّم في `mainfiles/` قبل الضغط — يُقرأ من الـscript لا يُفترض.

    **وسبب هذا الدالّة مقيس:** أوّل نسخة من الفحص عرفت المجلّدات المشتقّة
    (`libs/` · `system/bin/` · `system/product/`) وأغفلت ما يُنسَخ بيد
    (`cp maxmanagerApplist.json mainfiles/`) ⇒ **بلّغت عن عطب وهميّ في ملفّ سليم**.
    وبوابة تُبلّغ كذبًا تُقرأ ضجيجًا، وفي الضجيج يضيع العطب الحقيقيّ — وهو ما حذّرت
    منه `REVIEW.md` §4. فالقائمة تُستنبَط من سطور `cp` نفسها.
    """
    provided: set[str] = set()
    for line in read(root, ".github/scripts/compile_zip.sh").splitlines():
        match = re.match(r"\s*cp\s+(\S+)\s+(\"?)(mainfiles\S*)\2\s*$", line)
        if not match:
            continue
        source, destination = match.group(1), match.group(3)
        if "$" in source:
            continue
        if destination.endswith("/"):
            provided.add(os.path.basename(source))
        else:
            provided.add(os.path.relpath(destination, "mainfiles"))
    return provided


def check_extract_targets(root: str) -> str | None:
    """كل ما يستخرجه المنصّب يجب أن يكون في الحزمة أو يُنتجه المُحزِّم.

    هذا الفحص هو الذي كان يمسك حذف `module.banner.avif`: السطر باقٍ في
    `customize.sh`، والملفّ غائب ⇒ `extract` تُوقف التركيب.
    """
    text = read(root, "mainfiles/customize.sh")
    targets = re.findall(r'extract\s+"\$ZIPFILE"\s+"?([^"\s]+)"?', text)
    provided = packer_provided_paths(root)
    problems: list[str] = []
    for target in sorted(set(targets)):
        if target.startswith("libs/"):  # `$ARCH_TMP` داخل الاسم، والمُحزِّم يملأ libs/
            continue
        if target.startswith(PACKER_PRODUCED_PREFIXES):
            continue
        if target in provided:
            continue
        if not os.path.exists(os.path.join(root, "mainfiles", target)):
            problems.append(target)
    if problems:
        return "customize.sh يستخرج ملفّات غير موجودة في الحزمة: " + " · ".join(problems)
    return None


def check_no_sha256_shipped(root: str) -> str | None:
    """لا `.sha256` داخل أي حزمة؛ والتحقّق في مانيفست خارجها."""
    problems: list[str] = []
    stale = [
        os.path.join(dirpath, name)
        for dirpath, _, names in os.walk(os.path.join(root, "mainfiles"))
        for name in names
        if name.endswith(".sha256")
    ]
    if stale:
        problems.append("ملفّات `.sha256` داخل الشجرة المحزَّمة: " + ", ".join(sorted(stale)))

    for script in (".github/scripts/compile_zip.sh", ".github/scripts/build_developer_bundle.sh"):
        text = read(root, script)
        # **الفرق بين `zip -r` و`zip -z` مقصود:** الثاني يكتب *تعليق* الأرشيف لا
        # مدخلات، وليس حزمة تُشحن — ومطالبته باستثناء `*.sha256` كانت **بلاغًا كَاذبًا**
        # (وهو ما أظهره التشغيل الأوّل لهذه البوابة). فتُفحص أسطر الإنشاء وحدها.
        zips = [
            line for line in text.splitlines()
            if (match := re.match(r"\s*zip\s+(-\w+)", line))
            and "r" in match.group(1) and "z" not in match.group(1)
        ]
        if not zips:
            problems.append(f"{os.path.basename(script)}: لا سطر ضغط يُنشئ مدخلات")
            continue
        if not all("*.sha256" in line for line in zips):
            problems.append(f"{os.path.basename(script)}: سطر ضغط بلا استثناء `*.sha256`")

    compile_zip = read(root, ".github/scripts/compile_zip.sh")
    if not any(name in compile_zip for name in CHECKSUM_MANIFESTS):
        problems.append("compile_zip.sh لا يكتب مانيفست تحقّق خارج الحزمة")

    return " · ".join(problems) if problems else None


def check_verify_checksum_optional(root: str) -> str | None:
    """التوقيع الغائب لا يُوقف التركيب — وإلا كانت الحزمة بلا توقيع غير قابلة للتفليش."""
    text = read(root, "mainfiles/verify.sh")
    if re.search(r'abort_verify\s+"Missing checksum', text):
        return 'verify.sh يُوقف التركيب عند توقيع غائب — والحزم لا تشحن `.sha256`'
    if "sha256sum -c" not in text:
        return "verify.sh لم يعد يتحقّق من التوقيع حين يوجد"
    return None


def check_single_version_writer(root: str) -> str | None:
    """سلسلة الإصدار لها مصدر واحد؛ المُحزِّم لا يكتبها."""
    text = read(root, ".github/scripts/compile_zip.sh")
    if re.search(r"s/version=", text):
        return "compile_zip.sh يكتب `version=` — مصدر ثانٍ للحقيقة في الملفّ نفسه المطالَب به"
    return None


def check_no_second_copy(root: str) -> str | None:
    """التكامل يُنسَخ من مصدره، لا يُعاد كتابته في الـscript."""
    text = read(root, ".github/scripts/build_developer_bundle.sh")
    problems: list[str] = []
    for source in ("android/aosp/maxmanager.rc", "android/aosp/sepolicy/maxmanager.te",
                   "android/aosp/sepolicy/file_contexts", "android/aosp/BoardConfig.mk"):
        if f"cp {source}" not in text:
            problems.append(f"لا ينسخ {source}")
    if re.search(r"^\s*service\s+\S+\s+/", text, re.M):
        return "الـscript يُعلن خدمة init بنفسه بدل نسخ maxmanager.rc (نسخة ثانية تنزاح)"
    return " · ".join(problems) if problems else None


def check_script_syntax(root: str) -> str | None:
    """`bash -n` على كل سكربت — كلفته ميلي ثانية ويمنع حزمة لا تعمل."""
    if shutil.which("bash") is None:
        return None
    candidates: list[str] = []
    for directory in (".github/scripts", "mainfiles"):
        base = os.path.join(root, directory)
        if os.path.isdir(base):
            candidates += [
                os.path.join(base, name)
                for name in sorted(os.listdir(base))
                if name.endswith(".sh")
            ]
    problems: list[str] = []
    for path in candidates:
        result = subprocess.run(["bash", "-n", path], capture_output=True, text=True)
        if result.returncode != 0:
            problems.append(f"{os.path.relpath(path, root)}: {result.stderr.strip()[:90]}")
    return " · ".join(problems) if problems else None


def check_selftests_wired(root: str) -> str | None:
    """كل أداة تسجّل `--self-test` يُشغّلها CI فعليًّا — أو تُدرَج باستثناء معلَن.

    **وسبب العقد مقيس في هذه الجولة:** ثلاث بوابات (`kt_balance` · `code_health` ·
    `source_manifest`) كانت تسجّل `--self-test` **ولا يشغّلها أيّ تشغيل**، فقد قِيست
    الأداة **بيدٍ** ثم صارت سطرًا في الوثائق لا يقيس شيئًا. وهو نفس ما دفع سابقًا
    إلى `--self-test` لكل البوابات: أداة تُشغَّل عند توفّر حاجة تصدأ بصمت.

    والاستثناء **لا ينمو بصمت** في اتّجاهين: اسم في القائمة وليس أداةً ⇒ بلاغ،
    وأداة مُدرَجة استثناءً وقد صارت مُشغَّلة ⇒ بلاغ (استثناء قديم يُبرّئ ما لا يحتاج).
    """
    tools_dir = os.path.join(root, "tools")
    if not os.path.isdir(tools_dir):
        # شجرة بلا أدوات ليست شجرة تكذب — تُعلَن «غير مُتحقَّقة» لا «عطب».
        return None
    try:
        workflow = read(root, WORKFLOW)
    except FileNotFoundError:
        return f"{WORKFLOW} مفقود — لا سبيل إلى قياس اقتران البوابات"

    problems: list[str] = []
    registered: set[str] = set()
    for name in sorted(os.listdir(tools_dir)):
        if not name.endswith(".py"):
            continue
        with open(os.path.join(tools_dir, name), encoding="utf-8", errors="replace") as handle:
            if not SELFTEST_FLAG.search(handle.read()):
                continue
        registered.add(name)
        wired = f"tools/{name} --self-test" in workflow
        if wired and name in SELFTEST_NOT_IN_CI:
            problems.append(
                f"{name}: في قائمة الاستثناء وقد صار مُشغَّلًا — استثناء قديم يُبرّئ ما لا يحتاج"
            )
        elif not wired and name not in SELFTEST_NOT_IN_CI:
            problems.append(
                f"{name}: تسجّل `--self-test` ولا يُشغّلها أيّ تشغيل ⇒ بوابة تصدأ بلا أن يسقط شيء"
            )
    for name in SELFTEST_NOT_IN_CI:
        if name not in registered:
            problems.append(f"{name}: في قائمة الاستثناء وليس أداة تسجّل `--self-test`")
    return " · ".join(problems) if problems else None


CHECKS = [
    ("version_triangle", "المصادر الثلاثة لسلسلة الإصدار تقول الشيء نفسه", check_version_triangle),
    ("module_identity", "الاسم والمؤلف في module.prop يطابقان ما يحرسه الخادم", check_module_identity),
    ("daemon_path", "اسم الديمون ومساره متّفقان في Android.bp و file_contexts و product-inclusion.mk", check_daemon_path),
    ("rc_partition", "الـ.rc يهبط على قسم الثنائيّ نفسه", check_rc_partition),
    ("sepolicy_partition", "نوع التنفيذ لا يوصف بقسم يخالف مساره", check_sepolicy_partition),
    ("board_no_product", "BoardConfig.mk بلا متغيّرات PRODUCT_*", check_board_has_no_product),
    ("permission_xml", "XML الصلاحيات باسم صحيح وعلى قسم التطبيق", check_permission_xml),
    ("no_banner", "الـbanner مُزال من module.prop والحزمة والمُحزِّم", check_no_banner),
    ("extract_targets", "كل ما يستخرجه المنصّب موجود في الحزمة", check_extract_targets),
    ("no_sha256_shipped", "لا `.sha256` داخل الحزمة، والمانيفست خارجها", check_no_sha256_shipped),
    ("verify_optional_hash", "التوقيع الغائب لا يُوقف التركيب", check_verify_checksum_optional),
    ("single_version_writer", "المُحزِّم لا يكتب `version=`", check_single_version_writer),
    ("no_second_copy", "ملفّات التكامل تُنسَخ من مصدرها لا تُعاد كتابتها", check_no_second_copy),
    ("script_syntax", "كل سكربتات التحزيم والتثبيت تمرّ `bash -n`", check_script_syntax),
    ("selftests_wired", "كل أداة تسجّل `--self-test` يُشغّلها CI أو تُدرَج باستثناء معلَن", check_selftests_wired),
]


def run_checks(root: str) -> list[tuple[str, str, str | None]]:
    results: list[tuple[str, str, str | None]] = []
    for key, description, function in CHECKS:
        try:
            results.append((key, description, function(root)))
        except FileNotFoundError as exc:
            results.append((key, description, f"ملفّ مفقود: {os.path.basename(exc.filename or '')}"))
    return results


# ─────────────────────────── قياس الأداة نفسها ───────────────────────────

def build_fixture() -> str:
    """نسخة مصغّرة من الملفّات التعاقدية — تكفي للفحوص بلا شجرة كاملة.

    و**`mainfiles/` تُنسَخ كاملة**: أوّل نسخة اقتصرت على الملفّات التعاقدية، فسقط فحص
    `extract_targets` على النسخة السليمة (‏`customize.sh` يستخرج `service.sh`
    و`props.sh`…) ⇒ «عطب» في الشجرة المصنوعة لا في الشجرة الحقيقية. والدرس نفسه
    المُكرَّر في هذا الملفّ: التجهيز الناقص يُنتج بلاغًا كاذبًا لا فحصًا أقوى.
    """
    fixture = tempfile.mkdtemp(prefix="bundle-contract-")
    for rel in CONTRACT_FILES:
        source = os.path.join(ROOT, rel)
        destination = os.path.join(fixture, rel)
        os.makedirs(os.path.dirname(destination), exist_ok=True)
        shutil.copy2(source, destination)
    shutil.copytree(os.path.join(ROOT, "mainfiles"), os.path.join(fixture, "mainfiles"),
                    dirs_exist_ok=True)
    # و`tools/*.py` وحدها: عقد `selftests_wired` يقرأ ما تسجّله كلّ أداة وما يُشغّله CI،
    # وبلا نسخة منها في الشجرة المصنوعة لا يُقاس العقد أصلًا. والبقيّة (‏`.csv` · `.json`)
    # لا تدخل في العقد فلا تُنسخ.
    os.makedirs(os.path.join(fixture, "tools"), exist_ok=True)
    for name in sorted(os.listdir(os.path.join(ROOT, "tools"))):
        if name.endswith(".py"):
            shutil.copy2(os.path.join(ROOT, "tools", name), os.path.join(fixture, "tools", name))
    return fixture


def mutate(path: str, replacements: list[tuple[str, str]]) -> None:
    with open(path, encoding="utf-8") as handle:
        text = handle.read()
    for old, new in replacements:
        if old not in text:
            raise SystemExit(f"الطَفرة لم تجد نصّها في {path}: {old!r}")
        text = text.replace(old, new, 1)
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(text)


def self_test() -> int:
    """يكسر العقد عمدًا — فإن مرّ الفحص، فالأداة لا تقيس شيئًا.

    وكان في المستودع سابقة مقيسة: بوابة «نجحت» على عطبٍ لأنّ `grep` هو من أعطى
    صفر الخروج لا الأمر المقصود. فالفحص الذاتيّ هنا يكسر **كل** عقد ويطالب باسم
    الفحص المخالف بعينه.
    """
    print("═══ الفحص الذاتي: كل عقد يُكسر، والبوابة يجب أن تمسكه ═══")
    failures = 0
    mutations: list[tuple[str, list[tuple[str, str]], str]] = [
        ("version_triangle", [("version", [("v1.0", "v2.0")])], "version_triangle"),
        ("module_identity", [("mainfiles/module.prop", [("name=MaxManager", "name=Max Manager")])], "module_identity"),
        ("daemon_path", [("android/aosp/maxmanager.rc", [("/system/bin/sys.maxmanager-service", "/vendor/bin/sys.maxmanager-service")])], "daemon_path"),
        ("rc_partition", [("android/aosp/product-inclusion.mk", [("TARGET_COPY_OUT_SYSTEM", "TARGET_COPY_OUT_VENDOR")])], "rc_partition"),
        # السطر الكامل لا جزءه: الشِّقّ موجود أيضًا في تعليق فوقه، واستبدال أوّل
        # تطابق كان يُصيب التعليق ويبدو كأنّ البوابة لا تقيس (وهو ما حدث فعلًا).
        ("sepolicy_partition", [("android/aosp/sepolicy/maxmanager.te", [("type maxmanager_exec, exec_type, file_type;", "type maxmanager_exec, exec_type, file_type, vendor_file_type;")])], "sepolicy_partition"),
        ("board_no_product", [("android/aosp/BoardConfig.mk", [("BOARD_SEPOLICY_DIRS +=", "PRODUCT_PACKAGES +=\nBOARD_SEPOLICY_DIRS +=")])], "board_no_product"),
        ("permission_xml", [("android/aosp/product-inclusion.mk", [("device/maxmanager/product/etc/permissions/privapp-permissions-nd.max.xml:$(TARGET_COPY_OUT_PRODUCT)", "device/maxmanager/product/etc/permissions/nd.max.xml:$(TARGET_COPY_OUT_PRODUCT)")])], "permission_xml"),
        ("no_banner", [("mainfiles/module.prop", [("versionCode=1", "versionCode=1\nbanner=module.banner.avif")])], "no_banner"),
        ("extract_targets", [("mainfiles/customize.sh", [("# Skip mountify", 'extract "$ZIPFILE" module.banner.avif "$MODPATH"\n# Skip mountify')])], "extract_targets"),
        ("no_sha256_shipped", [(".github/scripts/compile_zip.sh", [("-x *placeholder* *.map .shellcheckrc '*.sha256'", "-x *placeholder* *.map .shellcheckrc")])], "no_sha256_shipped"),
        ("verify_optional_hash", [("mainfiles/verify.sh", [('if [ -f "$hash_path" ]; then', 'abort_verify "Missing checksum for $file"\n\tif [ -f "$hash_path" ]; then')])], "verify_optional_hash"),
        ("single_version_writer", [(".github/scripts/compile_zip.sh", [("version_code=\"$(git", "sed -i \"s/version=.*/version=x/\" mainfiles/module.prop\nversion_code=\"$(git")])], "single_version_writer"),
        ("no_second_copy", [(".github/scripts/build_developer_bundle.sh", [("cp android/aosp/maxmanager.rc", "cp /dev/null")])], "no_second_copy"),
        ("script_syntax", [(".github/scripts/generatesha256.sh", [("#!/bin/env bash", "#!/bin/env bash\nif [ ; then")])], "script_syntax"),
        # والطَفرة تُبقي السطر وتُزيل العلَم: فهو ما يقيسه العقد، ولا يُقاس بحذف السطر.
        ("selftests_wired", [(".github/workflows/build.yml", [("tools/rtl_guard.py --self-test", "tools/rtl_guard.py")])], "selftests_wired"),
    ]

    for label, edits, expected in mutations:
        fixture = build_fixture()
        try:
            for rel, replacements in edits:
                mutate(os.path.join(fixture, rel), replacements)
            results = {key: error for key, _, error in run_checks(fixture)}
            if results.get(expected) is None:
                print(f"  ✗ الطَفرة «{label}» مرّت بلا التقاط — الأداة لا تقيس هذا العقد")
                failures += 1
            else:
                print(f"  ✓ الطَفرة «{label}» التُقطت: {results[expected][:72]}")
        finally:
            shutil.rmtree(fixture, ignore_errors=True)

    # والعقد السليم يجب أن يمرّ: أداة تفشل دائمًا لا تفرّق بين عطب وسلامة.
    fixture = build_fixture()
    try:
        clean_errors = [f"{key}: {error}" for key, _, error in run_checks(fixture) if error]
        if clean_errors:
            print("  ✗ النسخة السليمة فشلت (الأداة تبلّغ عن نفسها):")
            for line in clean_errors:
                print(f"      {line}")
            failures += 1
        else:
            print("  ✓ النسخة السليمة تمرّ بلا بلاغ")
    finally:
        shutil.rmtree(fixture, ignore_errors=True)

    total = len(mutations) + 1
    print(f"\nالنتيجة: {total - failures}/{total}")
    return 1 if failures else 0


def main() -> int:
    parser = argparse.ArgumentParser(description="بوابة عقد التحزيم والتكامل")
    parser.add_argument("--assert", dest="assert_gate", action="store_true",
                        help="يفشل عند أي انزياح عن العقد")
    parser.add_argument("--self-test", action="store_true", help="يقيس الأداة نفسها")
    parser.add_argument("--json", action="store_true", help="الفحوص ونتائجها")
    args = parser.parse_args()

    if args.self_test:
        return self_test()

    results = run_checks(ROOT)
    failures = [(key, error) for key, _, error in results if error]

    if args.json:
        print(json.dumps(
            [{"check": key, "description": description, "error": error}
             for key, description, error in results],
            ensure_ascii=False, indent=2,
        ))
        return 1 if failures and args.assert_gate else 0

    print("═" * 72)
    print("عقد التحزيم والتكامل")
    print("═" * 72)
    for key, description, error in results:
        mark = "✓" if error is None else "✗"
        print(f"  {mark} {key}: {description}")
        if error:
            print(f"      {error}")

    if failures and args.assert_gate:
        print()
        print(f"فشل العقد في {len(failures)} موضعًا — الإصلاح قبل التسليم.", file=sys.stderr)
        return 1
    print()
    print("بوابة عقد التحزيم: exit 0")
    return 0


if __name__ == "__main__":
    sys.exit(main())
