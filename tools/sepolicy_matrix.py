#!/usr/bin/env python3
"""مصفوفة SELinux — «هل يسمح النظام بهذا المسار؟» قبل أن يُجرَّب على جهاز.

لماذا وُجدت هذه الأداة
----------------------
السياسة في هذا المستودع **مكتوبة مرّتين، ولا واحدة منهما تُقاس**: `android/aosp/sepolicy/`
مسارات AOSP، و`mainfiles/` مسار الموديول. والفرق بينهما لم يكن في جدول واحد، فسؤال
«هل المسار الذي نكتبه مُعلَّم؟» كان يُجاب بالقراءة اليدوية لكل ملف في كل مراجعة.

والأعطاب التي كشفتها المراجعة الأولى والعميقة — وتُقاس هنا لا تُروى:

* `mainfiles/META-INF/com/google/android/update-binary:138` ينسخ `$MODPATH/sepolicy.rule`
  **ولا ملف بهذا الاسم في الشجرة كلها** — والفحص `[ -f ]` صامت، فيمرّ غيابه بلا رسالة. وهذا
  **مصدر السياسة الوحيد المتاح للموديول**: وثيقة Magisk وKernelSU تذكران `sepolicy.rule` ملفًّا
  اختياريًّا في جذر الموديول تُطبَّق أسطره عند الإقلاع؛ فغيابه يعني أن الموديول لا يُضيف قاعدة
  سياسة واحدة على مساره (وذاك لا يعني خللًا: مساره يعمل بصدفة جذر).
* و`android/aosp/Android.bp` يُعلن مصادر **غير موجودة**: `runtime/daemon-rust/src/**/*.rs`
  و`runtime/daemon-rust/src/lib.rs` ولا مجلد `runtime/` في الشجرة أصلًا.
* و`android/aosp/sepolicy/file_contexts` يُعلّم `/data/misc/maxmanager` وخادمنا يكتب
  `/data/adb/.config/MaxManager/**` (`archdaemon/jni/include/AZenith.h`) — أي أن قواعد `allow`
  في `maxmanager.te` تحرس مسارًا لا يُستخدم، والمسار المُستخدم (`adb_data_file` من المنصّة)
  بلا قاعدة.

فالأداة تجمع **من الشجرة** ثلاث حقائق وتقارنها: ما يُثبَّت، وما هو مُعلَّم، وما يُشار إليه ولا وجود له.

البوابات
--------
| البوابة | ما تحكم عليه |
| --- | --- |
| `install-labeled` | كل **ملف** يُشغَّل **من init** (`service … /system/bin/<name>` في `android/aosp/*.rc`) لازمه وسم `exec_type` في `file_contexts` (ولا يُحاسب مجلدٌ: المجلد لا يُوسَم) |
| `label-not-stale` | كل سطر وسم يشير إلى مسار **يُنتَج فعلًا** (من الموديول أو من مسار AOSP أو من تعريف Soong) وإلا فهو وسم ميت |
| `no-dead-reference` | كل ملف يُشار إليه بـ`$MODPATH/<اسم>` موجودٌ في الشجرة أو يُنتجه البناء |
| `no-dead-source` | كل مصدر يُعلنه `Android.bp` (`srcs:`/`manifest:`) له جذرٌ موجود في الشجرة |
| `path-conflict` | تعريف Soong لا يجمع قسمين (`vendor: true` + `product_specific: true`)، ومسار الخدمة في `.rc` يطابق ما يُنشره Soong |
| `policy-gap` | كل مسار حالة **يكتبه خادمنا** إمّا موسوم منّا مع `allow`، وإمّا يُعلَن نوعه من المنصّة صراحةً |

والمصادر المُعلنة للمسارات المُنتَجة: أوامر التثبيت في `mainfiles/`، وأسطر `service` في
`android/aosp/*.rc`، وتعريفات `Android.bp` (Soong)، ومسارات الحالة التي تُكتب من الشيفرة. والاتحاد هو
ما يُقارَن به الوسم — فلا يُسمّى وسمًا ميتًا وهو صحيح في مسار آخر.

⚠️ **تصحيح مقيس (جولة المراجعة العميقة):** كانت بوابة `install-labeled` في نسختها الأولى
تُحاسب **خمسة ثنائيّات** (`sys.maxmanager-*`) يُثبّتها الموديول في `system/bin/`، حكمًا واحدًا مع
ثنائيّات مسار AOSP. وهي **مقارنة نطاقين مختلفين**، ووسمها في `file_contexts` **لا يُقرأ أصلًا** على
مسار الموديول: وثيقة Magisk تقول إن `set_perm`/`set_perm_recursive` تُطبّق افتراضًا
`u:object_r:system_file:s0`، وإن نطاق `magisk` «permissive فعليًّا» (تفاصيل: `topjohnwu.github.io/Magisk/`).
ونطاقنا نحن (`maxmanager`) لا يُشغّل هذه الثنائيّات: `mainfiles/service.sh` يُشغّلها من مجلد الموديول
بصدفة جذر، لا من `init`. فالحكم الصحيح لها: **معلومة نطاق** (`ℹ️`) لا عطبًا — و`exec_type` يلزم
للذي يُشغّله `init` وحده. وهذا الفرق هو سبب وجود البوابة الأولى بشرط «من init».

وحدودها مُعلَنة
--------------
* مسارات المنصّة (`/sys/**`, `/proc/**`, `/dev/**`) **ليست ملكنا**، ووسمها يأتي من نطاق النظام
  (`sysfs`, `proc`, `dev_type`). الأداة تُدرجها كـ`platform` بسببها ولا تُحاسبها — ولا تدّعي أنها
  «آمنة»: ما يدخلها من الكتابة يبقى محكومًا بـarbiter ونطاق الجذر.
* وبوابة `install-labeled` تُحاسب ما يُشغّله `init` وحده (وهو ما يحتاج `exec_type`): ثنائيّات `bin/`
  في مسار AOSP. والمحتوى المركّب (`product/priv-app`, `product/etc`) يُوسَم بوسوم المنصّة
  (`system_file`, `priv_app`) فلا يُحاسب.
* والأداة لا تُصرّف سياسة ولا تُصرّف `Android.bp`: تقرأ النصوص وتربطها، ولا تُشغّل `checkpolicy`
  ولا `soong_build`. وبوابة `path-conflict` تُعلن ما تقرأه من جمع قسمين لا حكم Soong نفسه.

الاستعمال
--------
```sh
python3 tools/sepolicy_matrix.py            # تقرير مقروء
python3 tools/sepolicy_matrix.py --json     # للقراءة الآلية
python3 tools/sepolicy_matrix.py --assert   # exit 1 عند أي عطب
python3 tools/sepolicy_matrix.py --self-test
```

و`--self-test` يقيس الأداة على شجرة مصغّرة **معلومة الحكم**، وفيه حالة لكل قاعدة جديدة: ثنائيّ
يُشغّله سكربت جذر (لا init) لا يُحاسب، وثنائيّ يُشغّله init بلا وسم يُحاسب، وتعريف Soong بـ`vendor: true`
يُبرّئ وسم `/vendor/bin`، ومصدر غائب في `srcs:` يُسمّى، وجمع قسمين في وحدة يُعلَن، ومسار حالة
يكتبه الخادم بلا وسم ولا قاعدة يُعلَن.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

POLICY_DIR = "android/aosp/sepolicy"
FILE_CONTEXTS = f"{POLICY_DIR}/file_contexts"
INSTALLER_SCRIPTS = ("mainfiles/customize.sh", "mainfiles/META-INF/com/google/android/update-binary")
POLICY_TYPE_FILES = (f"{POLICY_DIR}/maxmanager.te",)

# ملفات تعريف Soong: المصدر الثاني للحقيقة عن «ما يُنشر وأين». وهذا ما جعل حكمًا سابقًا خاطئًا:
# وسم `/vendor/bin/maxmanager_daemon` كان يُقرأ «ميتًا» لأن مسار AOSP المسحي كان `.rc` وحده،
# بينما `Android.bp` يُنشر الوحدة بـ`vendor: true` (أي إلى `/vendor/bin` لا `/system/bin`).
SOONG_FILES = ("android/aosp/Android.bp",)

# مسار التغليف الثاني (KernelSU/APatch) — يُقرأ ليُعلَن ما يطلبه ولا يُنتجه شيء في الشجرة.
PARALLEL_PACKAGER = "android/kernelsu/customize.sh"

# مسارات حالة يكتبها الخادم/السكربتات، ونوع وسمها من المنصّة حين لا يكون لنا وسم عليها.
# والأسباب مكتوبة: `adb_data_file` من وثيقة Magisk نفسها (`/data/adb` موسوم بها).
PLATFORM_DATA_LABELS = (
    ("/data/adb/", "adb_data_file", "وثيقة Magisk: `/data/adb` موسوم `u:object_r:adb_data_file:s0`"),
    ("/data/data/", "app_data_file", "بيانات التطبيقات: وسم المنصّة `app_data_file`"),
    ("/data/system/", "system_data_file", "بيانات النظام: وسم المنصّة `system_data_file`"),
    ("/data/misc/", "misc_user_data_file", "`/data/misc` وسمه من المنصّة إلا ما وُسِم منه"),
    ("/data/local/tmp/", "shell_data_file", "`/data/local/tmp` وسمه `shell_data_file` من المنصّة"),
)

# ما يُعدّ «خادمنا» في بوابة `policy-gap`: الخادم الأصلي وسكربتات التثبيت/الإقلاع وحدها.
DAEMON_SOURCE_ROOTS = (
    "archdaemon/",
    "preloadbin/",
    "mainfiles/",
    "android/",
    "thermalcore/",
    "binprofiles/",
    "binutils/",
)

# جذور الحالة التي تُقرأ من الشيفرة: ما يكتبه **خادمنا** فعلًا (لا ما نتمنى أنه يكتبه).
STATE_ROOT_PREFIXES = (
    "/data/misc/maxmanager",
    "/data/adb/.config/MaxManager",
    "/data/adb/modules/MaxManager",
    "/data/system/maxmanager",
    "/data/data/nd.max",
)

# ── ما هو **ملكنا** فعلًا ────────────────────────────────────────────────────────────────
# مسار الموديول على الجهاز يبدأ بـ`/data/adb/modules/...`، وما يُركَّب منه يُقرأ من `/system`
# و`/product`. وهذه هي البادئات التي تُحاسَب على الوسم؛ وما عداها من مسارات المنصّة يُدرج
# بسببٍ مكتوب ولا يُحاسب (فليس لنا أن نُعلّم `/sys`).
MODULE_OWNED_ROOTS = (
    "/data/adb/modules",
    "/data/misc/maxmanager",
    "/system/bin",
    "/system/app",
    "/system/etc",
    "/system/product",
    "/product",
    "/vendor",
)

# وما يحتاج وسمًا **منّا** (نطاقنا و`exec_type`نا) هو ما نُشغّله من `bin/`؛ وما عداه من محتوى
# مركّب يُوسَم بوسوم المنصّة كما هي.
NEEDS_OUR_LABEL_PREFIXES = ("/system/bin/", "/vendor/bin/")
PLATFORM_ROOT_PREFIXES = ("/sys/", "/proc/", "/dev/", "/sys", "/proc", "/dev")

# ── استثناءات مُعلَنة ──────────────────────────────────────────────────────────────────
# كل استثناء يحمل سببه. والغرض: أن يكون **قرارًا مكتوبًا** لا صمتًا يقرأه المراجع القادم عطبًا.
DOCUMENTED_EXCEPTIONS: dict[str, str] = {
    "/data/local/tmp/MaxManager.apk": (
        "المسار مؤقّت ملك system (`/data/local/tmp` وسمه `shell_data_file` من المنصّة)، "
        "ويُستخدم لتمرير الـAPK إلى `pm install` ثم لا يُمسّ"
    ),
}

# مسارات تُبنى في شجرة الالتزام ولا يُنتجها المثبِّت وحده.
BUILD_PRODUCED_PREFIXES = ("manager/app/build/", "archdaemon/libs/", "preloadbin/libs/")


class Finding:
    def __init__(self, gate: str, path: str, detail: str, sources: list[str] | None = None):
        self.gate = gate
        self.path = path
        self.detail = detail
        self.sources = sources or []

    def to_dict(self) -> dict:
        return {"gate": self.gate, "path": self.path, "detail": self.detail, "sources": self.sources}


def read(root: Path, rel: str) -> str:
    path = root / rel
    return path.read_text(encoding="utf-8", errors="replace") if path.is_file() else ""


def install_targets(root: Path) -> dict[str, list[str]]:
    """ملفات التثبيت: `$MODPATH/system/...` ⇒ `/system/...`، مع السطر الذي يُنتجه.

    والمجلدات تُسقَط عن قصد: `$MODPATH/system/bin/` هدفٌ **مجلّدي**، والمجلد لا يحمل وسمًا في
    `file_contexts` (الوسم للملفات). وإسقاطها هو ما يفرّق «عطبًا» عن «ضجيج». ولذلك تُقرأ الأسماء
    من **الطرف الآخر** للسطر أيضًا: `cp "$MODPATH/system/bin/sys.maxmanager-service" "$MODPATH/system/bin/"`
    يُنتج ملفًّا في المجلد، واسمه من المصدر لا من الوجهة.
    """
    targets: dict[str, list[str]] = {}

    def add(device_path: str, source: str) -> None:
        # المجلد يُسقَط (لا وسم له)، وملفٌ يحمل مسارًا فرعيًّا على الأقل يُبقى.
        #
        # ⚠️ ولا يُشترط «امتداد»: أسماء ثنائيّاتنا `sys.maxmanager-service` تنتهي بـ`-service`
        # لا بنقطة‌وُحرف، فشرط الامتداد كان يُسقطها كلها ويكتم عطبًا حقيقيًّا (خمسة ثنائيّات
        # تُثبَّت في `/system/bin` بلا وسم). وهذا العطب اكتشفه هذا القياس لا القراءة.
        if device_path.endswith("/") or device_path.count("/") < 3:
            return
        targets.setdefault(device_path, []).append(source)

    for script in INSTALLER_SCRIPTS:
        for raw in read(root, script).splitlines():
            line = raw.strip()
            if line.startswith("#"):
                continue
            # `$MODPATH/<partition>/<tail>` → `/<partition>/<tail>`
            #
            # وشرط وجود `/` ضروري: بلا فحصه يُطابق `$MODPATH/system.prop` القسم `system` +
            # الامتداد `.prop` فينشأ مسار وهمي `/system.prop` يُحاسب على وسم لا وجود له.
            for match in re.finditer(r"\$MODPATH/(system|vendor|product|my_product)/?([A-Za-z0-9_./-]*)", line):
                tail = match.group(2)
                if not tail:
                    continue
                add("/" + match.group(1) + "/" + tail.lstrip("/"), f"{script}:{line[:70]}")
    return targets


# أسماء تُنشئها السكربتات نفسها (علامات `touch`، أو نُسخ `cp` إليها، أو توجيه `>`):
# ذكرها كمرجع ليس ادّعاءً بوجود سابق، فلا تُحسب مرجعًا ميتًا.
_CREATED_PATTERNS = (
    r"touch\s+\"?\$MODPATH/([A-Za-z0-9_][A-Za-z0-9_.-]*)",
    r">\s*\"?\$MODPATH/([A-Za-z0-9_][A-Za-z0-9_.-]*)",
    # وجهة النقل: `$MODPATH/<اسم>` في **آخر** السطر = يُنشأ، لا يُقرأ.
    r"\bcp\s+[^\n]*?\s+\"?\$MODPATH/([A-Za-z0-9_][A-Za-z0-9_.-]*)\"?\s*$",
    # مجلد يُدار من السكربت (`set_perm_recursive`) أو يُنشأ (`mkdir -p`): ليس ملفًا مطلوب الوجود
    # في الحزمة، فعدّه مرجعًا ميتًا كان يُنتج ضجيجًا فوق الاكتشاف الحقيقي (٣ أعطاب وهمية مقيسة).
    r"set_perm_recursive\s+\"?\$MODPATH/([A-Za-z0-9_][A-Za-z0-9_.-]*)",
    r"mkdir\s+-p\s+\"?\$MODPATH/([A-Za-z0-9_][A-Za-z0-9_.-]*)",
)


def binaries_installed_by_glob(root: Path) -> dict[str, list[str]]:
    """ثنائيّات تُثبَّت في `bin/` **بنسخ جماعي** (`cp "$TMPDIR/libs/..."* "$MODPATH/system/bin/"`).

    وسببُ الفحص الخاص بها: بلا استخراج الأسماء من سطور `extract "$ZIPFILE" "libs/<arch>/<name>"`
    لا يُرى في الشجرة **أي** هدف تثبيت في `bin/`، فيخرج حكم «الوسوم سليمة» وهو كذب — لأن الخمسة
    تُنسخ بسلوب واحد بلا أسماء في السطر. والشرط مقيس: لا تُعلن الأسماء إلّا إذا وُجد سطر نسخ فعلي
    إلى مجلد `system/bin`، فتضيق التوسعة عن التفخيم.
    """
    installed: dict[str, list[str]] = {}
    for script in INSTALLER_SCRIPTS:
        text = read(root, script)
        copies_into_bin = re.search(r"cp\s+[^\n]*\$MODPATH/system/bin/[\"']?\s*$", text, re.M) is not None
        if not copies_into_bin:
            continue
        # `libs/<arch>/<name>`: والقوس يبقى في الصيغة لأن اسم المعمارية متغيّر (`$ARCH_TMP`).
        for match in re.finditer(r"\"?libs/[A-Za-z0-9_$.{}-]+/([A-Za-z0-9_][A-Za-z0-9_.-]*)\"?", text):
            installed.setdefault("/system/bin/" + match.group(1), []).append(f"{script}: نسخ جماعي إلى system/bin")
    return installed


def created_by_scripts(root: Path) -> set[str]:
    created: set[str] = set()
    for script in (*INSTALLER_SCRIPTS, PARALLEL_PACKAGER):
        text = read(root, script)
        for pattern in _CREATED_PATTERNS:
            # `re.M`: بلاها لا يُطابق `$` إلا نهاية الملف كله، فيسقط كل «وجهة نقل في آخر السطر».
            created.update(re.findall(pattern, text, re.M))
    # وملفات تُنتجها أدوات الحزمة (`.github/scripts/compile_zip.sh`): تُنسخ إلى `mainfiles/` قبل الضغط.
    for line in read(root, ".github/scripts/compile_zip.sh").splitlines():
        match = re.match(r"\s*cp\s+\S+\s+mainfiles/([A-Za-z0-9_][A-Za-z0-9_.-]*)", line)
        if match:
            created.add(match.group(1))
    return created


def aosp_produced_paths(root: Path) -> dict[str, list[str]]:
    """مسار AOSP يُنشر من `android/aosp/*.rc`: سطر `service <name> <path>` هو البيان الحقيقي."""
    produced: dict[str, list[str]] = {}
    for rc in (root / "android/aosp").glob("*.rc"):
        for raw in rc.read_text(encoding="utf-8", errors="replace").splitlines():
            line = raw.split("#", 1)[0].strip()
            match = re.match(r"service\s+\S+\s+(/[A-Za-z0-9_./-]+)", line)
            if match:
                produced.setdefault(match.group(1), []).append(str(rc.relative_to(root)))
    return produced


def aosp_init_started(root: Path) -> dict[str, list[str]]:
    """ما يُشغّله `init` (اسم الملف ⇒ مصدره) — وهو **وحده** ما يحتاج `exec_type` منّا.

    والمصدر هو سطر `service <name> <path>` في `android/aosp/*.rc`. وما يُشغّله سكربت جذر
    (`mainfiles/service.sh`) لا يحتاج وسمًا: يشتغل بصدفة `root`/`magisk` ولا يعبر بوابة `init`.
    """
    started: dict[str, list[str]] = {}
    for rc in (root / "android/aosp").glob("*.rc"):
        for raw in rc.read_text(encoding="utf-8", errors="replace").splitlines():
            line = raw.split("#", 1)[0].strip()
            match = re.match(r"service\s+\S+\s+(/[A-Za-z0-9_./-]+)", line)
            if match:
                started.setdefault(match.group(1).rsplit("/", 1)[-1], []).append(str(rc.relative_to(root)))
    return started


def _soong_blocks(text: str) -> list[tuple[str, str, list[str]]]:
    """يقرأ كتل `Android.bp` الثلاثية (`<kind> { name: … }`) بلا مُصرّف Soong.

    وحدّه المُعلَن: قارئ أسطر لا محلّل أسماء؛ يكفي لـ`name:` و`srcs:` و`vendor:`/`product_specific:`،
    وهو كل ما تُبنى عليه هنا. وليس بديلًا عن `soong_build` ولا يُدَّعى أنه كذلك.
    """
    blocks: list[tuple[str, str, list[str]]] = []
    kind: str | None = None
    name = ""
    body: list[str] = []
    depth = 0
    for raw in text.splitlines():
        line = raw.split("//", 1)[0]
        if kind is None:
            match = re.match(r"\s*([A-Za-z_][A-Za-z0-9_]*)\s*\{", line)
            if match:
                kind, name, body, depth = match.group(1), "", [], 1
            continue
        depth += line.count("{") - line.count("}")
        if name == "":
            found = re.search(r"\bname:\s*\"([^\"]+)\"", line)
            if found:
                name = found.group(1)
        body.append(line)
        if depth <= 0:
            blocks.append((kind, name, body))
            kind = None
    return blocks


def _soong_srcs(body: list[str]) -> list[str]:
    joined = "\n".join(body)
    values: list[str] = []
    for match in re.finditer(r"(?:srcs|static_libs|shared_libs):\s*\[(.*?)\]", joined, re.S):
        values += re.findall(r"\"([^\"]+)\"", match.group(1))
    for match in re.finditer(r"\bmanifest:\s*\"([^\"]+)\"", joined):
        values.append(match.group(1))
    return values


def soong_installed_paths(root: Path) -> dict[str, list[str]]:
    """ما يُنشره Soong وأين — `vendor: true` ⇒ `/vendor/bin`، و`product_specific` ⇒ `/product/priv-app`."""
    produced: dict[str, list[str]] = {}
    for file in SOONG_FILES:
        for kind, name, body in _soong_blocks(read(root, file)):
            if not name:
                continue
            flags = "\n".join(body)
            vendor = re.search(r"\bvendor:\s*true", flags) is not None
            product = re.search(r"\bproduct_specific:\s*true", flags) is not None
            if kind == "cc_binary":
                partition = "/vendor/bin" if vendor else "/system/bin"
                produced.setdefault(f"{partition}/{name}", []).append(f"{file}: {kind} {name}")
            elif kind in ("android_app", "android_app_import"):
                base = "/product/priv-app" if product else "/system/app"
                produced.setdefault(f"{base}/{name}/{name}.apk", []).append(f"{file}: {kind} {name}")
    return produced


def declared_sources(root: Path) -> dict[str, list[str]]:
    """مصادر تُعلنها تعريفات Soong — الفحص: جذرها موجود في الشجرة."""
    declared: dict[str, list[str]] = {}
    for file in SOONG_FILES:
        for kind, name, body in _soong_blocks(read(root, file)):
            for source in _soong_srcs(body):
                declared.setdefault(source, []).append(f"{file}: {kind} {name}".strip())
    return declared


def runtime_state_paths(root: Path) -> dict[str, list[str]]:
    """مسارات الحالة التي تُكتب من الشيفرة — وجودها يُبرّر وسمًا لا يُنتجه المثبِّت."""
    return {
        path: sources
        for path, sources in _device_paths(root, STATE_ROOT_PREFIXES).items()
        if not path.endswith("/")
    }


def policy_type_tokens(root: Path) -> str:
    """نصّ ملفات السياسة (`*.te`) — يُقرأ لسؤال واحد: هل النوع مذكور بقاعدة؟"""
    return "\n".join(read(root, name) for name in POLICY_TYPE_FILES)


def platform_label_for(device_path: str) -> tuple[str, str] | None:
    """نوع وسم المنصّة لأول بادئة تُطابق المسار (والسبب مكتوب معه)."""
    for prefix, label, reason in PLATFORM_DATA_LABELS:
        if device_path == prefix.rstrip("/") or device_path.startswith(prefix):
            return label, reason
    return None


def label_type_of(label_line: str) -> str | None:
    """`u:object_r:maxmanager_data_file:s0` ⇒ `maxmanager_data_file`."""
    match = re.search(r"u:object_r:([A-Za-z0-9_]+)", label_line)
    return match.group(1) if match else None


def declared_labels(root: Path) -> dict[str, list[str]]:
    """وسوم `file_contexts`: المسار (منظَّمًا) ⇒ نصوص المصدر."""
    labels: dict[str, list[str]] = {}
    for raw in read(root, FILE_CONTEXTS).splitlines():
        line = raw.split("#", 1)[0].strip()
        if not line:
            continue
        parts = line.split()
        if len(parts) < 2:
            continue
        labels.setdefault(normalize_label_path(parts[0]), []).append(line)
    return labels


def normalize_label_path(pattern: str) -> str:
    """`/data/misc/maxmanager(/.*)?` ⇒ `/data/misc/maxmanager` (البادئة الثابتة)."""
    pattern = pattern.strip()
    for marker in ("(/.*)?", "(/.*)", ".*", "/?"):
        if pattern.endswith(marker):
            pattern = pattern[: -len(marker)]
    return pattern.rstrip("/") or "/"


def label_covers(labels: dict[str, list[str]], device_path: str) -> str | None:
    """أي وسم يغطّي هذا المسار؟ (الأطول بادئةً يفوز، ولا تخمين على المجهول)."""
    candidates = [pattern for pattern in labels if device_path == pattern or device_path.startswith(pattern + "/")]
    if not candidates:
        return None
    return max(candidates, key=len)


def script_references(root: Path) -> dict[str, list[str]]:
    """ملفات يُشار إليها من سكربتات التثبيت بـ`$MODPATH/<اسم>` **بلا مسار فرعي**.

    والاقتصار على اسم واحد عن قصد: `$MODPATH/system` مسار مجلد ولا يُنتج ملفًّا، وإدخاله يُنتج
    ضجيجًا يُغرق الاكتشاف الحقيقي (ملف **يطلب نسخه** ولا وجود له).

    ويُضاف إليها سكربت التغليف الموازي (`android/kernelsu/customize.sh`): هو أيضًا يطلب ملفات
    بـ`$MODPATH/<اسم>` من الحزمة، ولو لم يُقرأ لمرّ مطلبه الذي لا يُنتجه شيء في الشجرة صامتًا.
    """
    references: dict[str, list[str]] = {}
    for script in (*INSTALLER_SCRIPTS, "mainfiles/META-INF/com/google/android/updater-script", PARALLEL_PACKAGER):
        for raw in read(root, script).splitlines():
            line = raw.split("#", 1)[0]
            for match in re.finditer(r"\$MODPATH/([A-Za-z0-9_][A-Za-z0-9_.-]*(?:/[A-Za-z0-9_.-]+)?)(?![A-Za-z0-9_./-])", line):
                name = match.group(1)
                # ومجلداتنا المُركّبة (`system/**`, `product/**`) تُسقَط: تُنشأ من المثبِّت لا من الحزمة.
                # وسببُ ذلك مقيس: ذكرها كان يُنتج مراجع «ميتة» لأشياء ليست ملفات في الحزمة أصلًا.
                if name.startswith(("system/", "product/")):
                    continue
                references.setdefault(name, []).append(f"{script}:{line.strip()[:70]}")
    return references


def is_produced(root: Path, name: str) -> bool:
    """هل يُنتج البناء هذا الاسم (في الشجرة أو في مخرَج مُعلَن)؟"""
    if (root / name).exists() or (root / "mainfiles" / name).exists():
        return True
    # ومسار تغليف موازٍ يُنتج الحزمة في مكان آخر (`android/kernelsu/`) يُحسَب أيضًا: المطلوب
    # أنه **يُنتَج من شيء في الشجرة**، لا أن يكون في مكان بعينه.
    if (root / "android" / "kernelsu" / name).exists():
        return True
    produced_names = {
        "module.prop",
        "customize.sh",
        "service.sh",
        "post-fs-data.sh",
    }
    if name in produced_names:
        return True
    # أسماء تُبنى في مجلدات المخرجات المعلَنة (تُنسخ إلى الـzip عند التجميع).
    for prefix in BUILD_PRODUCED_PREFIXES:
        directory = root / prefix
        if directory.is_dir() and any(entry.name == name for entry in directory.iterdir()):
            return True
    return False


def scan(root: Path) -> tuple[list[Finding], list[dict[str, str]]]:
    findings: list[Finding] = []
    platform_notes: list[dict[str, str]] = []

    targets = install_targets(root)
    labels = declared_labels(root)
    aosp = aosp_produced_paths(root)
    soong = soong_installed_paths(root)
    state = runtime_state_paths(root)
    produced = {**targets, **aosp, **soong, **state}
    type_text = policy_type_tokens(root)
    # ثنائيّات bin/ المنسوخة جماعيًّا تدخل فحص الوسم أيضًا، وإن لم تُنتج هدفًا صريحًا في السطر.
    glob_binaries = binaries_installed_by_glob(root)
    for device_path, sources in glob_binaries.items():
        targets.setdefault(device_path, sources)

    # ١) كل **ثنائيّ يُشغّله init** نضعه في `bin/` يجب أن يحمل وسم `exec_type` (أو استثناءً مكتوبًا).
    #
    #    ⚠️ والشرط «من init» هو **التصحيح المقيس**: كان كل ملف في `bin/` يُحاسب، فخرجت الخمسة
    #    `sys.maxmanager-*` «أعطابًا» وهي لا تمرّ ببوابة `init` أصلًا: يشتغلها `mainfiles/service.sh`
    #    بصدفة جذر، ووسمها يأتي من مثبِّت الموديول (وثيقة Magisk: افتراض `set_perm` هو
    #    `u:object_r:system_file:s0`، ونطاق `magisk` permissive فعليًّا). فالحكم عليها = معلومة نطاق.
    #    والاقتصار على `bin/` مقصود ومُعلَن: المحتوى المركّب (`product/priv-app`, `product/etc`)
    #    يُوسَم بوسوم المنصّة (`system_file`, `priv_app`, …) ولا يحتاج وسمًا من سياستنا.
    init_started = aosp_init_started(root)
    for device_path in sorted(targets):
        if not device_path.startswith(NEEDS_OUR_LABEL_PREFIXES):
            continue
        if device_path in DOCUMENTED_EXCEPTIONS:
            continue
        if label_covers(labels, device_path) is not None:
            continue
        basename = device_path.rsplit("/", 1)[-1]
        if basename not in init_started:
            platform_notes.append(
                {
                    "path": device_path,
                    "reason": (
                        "يُثبَّت في bin/ ولا يُشغّله init (يشتغله سكربت جذر) ⇒ لا يحتاج exec_type منّا، "
                        "ووسمه من مثبِّت الموديول (افتراض set_perm في Magisk = system_file)"
                    ),
                    "sources": sorted(set(targets[device_path]))[:2],
                }
            )
            continue
        findings.append(
            Finding(
                gate="install-labeled",
                path=device_path,
                detail="يُشغّله init بلا وسم في file_contexts (لا exec_type لنا ⇒ منع تحت enforcing)",
                sources=sorted(set(targets[device_path])) + init_started[basename][:1],
            )
        )

    # ٢) وسم لا يطابق أي مسار مُنتَج (من الموديول أو من مسار AOSP) = وسم ميت.
    for pattern in sorted(labels):
        if not pattern.startswith(MODULE_OWNED_ROOTS):
            continue
        if pattern in produced:
            continue
        if any(path == pattern or path.startswith(pattern + "/") for path in produced):
            continue
        findings.append(
            Finding(
                gate="label-not-stale",
                path=pattern,
                detail="وسم يشير إلى مسار لا يُنتجه الموديول ولا مسار AOSP (اسم قديم أو مسار متروك)",
                sources=labels[pattern],
            )
        )

    # ٣) إشارة إلى ملف غير موجود (الفحص `[ -f ]` الصامت) — العطب المقيس في تكملة ٩٩.
    created = created_by_scripts(root)
    for name, sources in sorted(script_references(root).items()):
        if is_produced(root, name) or name in created:
            continue
        findings.append(
            Finding(
                gate="no-dead-reference",
                path=f"$MODPATH/{name}",
                detail="يُشار إليه من سكربت التثبيت ولا وجود له في الشجرة (الفحص `[ -f ]` يمرّ صامتًا)",
                sources=sources,
            )
        )

    # ٤) مصدر يُعلنه Soong وجذره غير موجود في الشجرة (تعريف لا يُصرَّف).
    #
    #    والشرط `"/" in source` مقصود: `static_libs` تحمل **أسماء وحدات** (`liblog`, `libc++`) لا
    #    مسارات، ففحصها كمسارات يُنتج أربعة أعطاب وهمية ويُدفن العطب الحقيقي.
    for source, sources in sorted(declared_sources(root).items()):
        if "/" not in source:
            continue
        prefix = source.split("*", 1)[0].rstrip("/")
        if not prefix or (root / prefix).exists():
            continue
        findings.append(
            Finding(
                gate="no-dead-source",
                path=source,
                detail="مصدر يُعلنه Android.bp ولا جذر له في الشجرة (مسار قديم أو شجرة أخرى)",
                sources=sources,
            )
        )

    # ٥) تعارض تنصيب: وحدة تُعلن قسمين، أو `init` يشير إلى مسار وSoong يُنشر آخر.
    for file in SOONG_FILES:
        for kind, name, body in _soong_blocks(read(root, file)):
            flags = "\n".join(body)
            if re.search(r"\bvendor:\s*true", flags) and re.search(r"\bproduct_specific:\s*true", flags):
                findings.append(
                    Finding(
                        gate="path-conflict",
                        path=f"{file}: {kind} {name}",
                        detail="وحدة تُعلن قسمين معًا (`vendor: true` + `product_specific: true`) — قسم واحد لكل وحدة",
                        sources=[file],
                    )
                )
    soong_by_basename = {path.rsplit("/", 1)[-1]: path for path in soong}
    for path, sources in sorted(aosp.items()):
        other = soong_by_basename.get(path.rsplit("/", 1)[-1])
        if other and other != path:
            findings.append(
                Finding(
                    gate="path-conflict",
                    path=path,
                    detail=f"خدمة init تشير إلى `{path}` بينما Soong يُنشر الوحدة إلى `{other}`",
                    sources=sources + soong[other][:1],
                )
            )

    # ٦) مسار حالة يكتبه خادمنا: إمّا موسوم منّا **مع قاعدة**، وإمّا نوعه من المنصّة مُعلَن ومذكور.
    #
    #    وهذا هو الفرق بين «الملف موجود» و«النطاق مسموح»: سياسة مكتوبة تحرس مسارًا لا يُستخدم
    #    (قاعدة `maxmanager_data_file` على `/data/misc/maxmanager`) تترك المسار المُستخدم فعلًا
    #    (`/data/adb/.config/MaxManager/**`) بلا قاعدة — وهو منع مؤكّد تحت enforcing.
    #    والتجميع على **جذر المسار** لا على كل ملف: العطب واحد والمواضع كثيرة.
    grouped_state: dict[str, list[str]] = {}
    for device_path, sources in sorted(state.items()):
        # ومصادر الشيفرة **الخادمية/السكربتية** وحدها تُحسَب هنا: مسار يذكره كود التطبيق
        # (`manager/app/**`) يخصّ نطاق `system_app` لا نطاقنا، فإدخاله في بوابة «خادمنا» كان
        # سيخلط نطاقين ويُنتج عطبًا لا يخصّ السياسة. والحدّ مُعلَن لا صمت.
        sources = [source for source in sources if source.startswith(DAEMON_SOURCE_ROOTS)]
        if not sources:
            continue
        root_prefix = max(
            (prefix for prefix in STATE_ROOT_PREFIXES if device_path == prefix or device_path.startswith(prefix + "/")),
            key=len,
            default=device_path,
        )
        bucket = grouped_state.setdefault(root_prefix, [])
        for source in sources:
            if source not in bucket and len(bucket) < 3:
                bucket.append(source)
    for root_prefix, sources in sorted(grouped_state.items()):
        cover = label_covers(labels, root_prefix)
        if cover is not None:
            token = next((label_type_of(line) for line in labels[cover] if label_type_of(line)), None)
            if token and token not in type_text:
                findings.append(
                    Finding(
                        gate="policy-gap",
                        path=root_prefix,
                        detail=f"موسوم لدينا بـ`{token}` ولا قاعدة له في السياسة — نطاقنا يُمنع عن مساره",
                        sources=labels[cover][:1] + sources[:1],
                    )
                )
            continue
        platform = platform_label_for(root_prefix)
        if platform is None:
            continue
        label, reason = platform
        if label in type_text:
            continue
        findings.append(
            Finding(
                gate="policy-gap",
                path=root_prefix,
                detail=f"يكتبه خادمنا ولا وسم لنا عليه: نوعه `{label}` ({reason}) ولا قاعدة في `.te` تشمله",
                sources=sources[:2],
            )
        )

    # ٧) مسارات المنصّة: تُدرج بسببها ولا تُحاسب — ولا تُسمّى «آمنة».
    for path, sources in sorted(platform_paths(root).items())[:400]:
        platform_notes.append(
            {
                "path": path,
                "reason": "مسار منصّة: وسمه من نطاق النظام لا من سياستنا",
                "sources": sources[:2],
            }
        )

    # وما نُثبّته من محتوى مركّب (`product/**`) يُدرج بوسم المنصّة: لا يُحاسب، ولا يُسمّى «مُعلَّمًا بأمان».
    for device_path in sorted(targets):
        if not device_path.startswith(MODULE_OWNED_ROOTS):
            continue
        if device_path.startswith(NEEDS_OUR_LABEL_PREFIXES):
            continue
        platform_notes.append(
            {
                "path": device_path,
                "reason": "محتوى مركّب يُوسَم بوسوم المنصّة (`system_file`/`priv_app`)، لا بسياسة لنا",
                "sources": sorted(set(targets[device_path]))[:2],
            }
        )

    return findings, platform_notes


def _iter_source_files(root: Path) -> list[Path]:
    extensions = {".kt", ".rs", ".c", ".h", ".cpp", ".sh", ".java", ".mk", ".bp", ".te"}
    skip = {".git", "build", "target", "node_modules", ".gradle", "libs"}
    files: list[Path] = []
    for path in root.rglob("*"):
        if not path.is_file() or path.suffix not in extensions:
            continue
        if any(part in skip for part in path.relative_to(root).parts):
            continue
        files.append(path)
    return files


def _starts_with_prefix(path: str, prefixes: tuple[str, ...]) -> bool:
    """/dev يجب أن لا تُطابق `/devfreq` (وإلّا اختلطت مسارات المنصّة بغيرها)."""
    return any(path == prefix or path.startswith(prefix.rstrip("/") + "/") or path.startswith(prefix) and prefix.endswith("/")
               for prefix in prefixes)


def _device_paths(root: Path, prefixes: tuple[str, ...], limit_per_path: int = 3) -> dict[str, list[str]]:
    found: dict[str, list[str]] = {}
    pattern = re.compile(r'"(/(?:sys|proc|dev|data|system|product|vendor)[A-Za-z0-9_./$*-]*)"')
    for file in _iter_source_files(root):
        text = file.read_text(encoding="utf-8", errors="replace")
        for match in pattern.finditer(text):
            value = match.group(1).rstrip("/")
            if not _starts_with_prefix(value, prefixes):
                continue
            if any(token in value for token in ("$", "*")) and not value.endswith("/"):
                continue
            rel = str(file.relative_to(root))
            bucket = found.setdefault(value, [])
            if rel not in bucket and len(bucket) < limit_per_path:
                bucket.append(rel)
    return found


def platform_paths(root: Path) -> dict[str, list[str]]:
    return _device_paths(root, PLATFORM_ROOT_PREFIXES)


def render(findings: list[Finding], platform_notes: list[dict], out) -> None:
    gates = [
        "install-labeled",
        "label-not-stale",
        "no-dead-reference",
        "no-dead-source",
        "path-conflict",
        "policy-gap",
    ]
    print("مصفوفة SELinux — ما يُثبَّت · ما هو مُعلَّم · ما يُشار إليه ولا وجود له", file=out)

    for gate in gates:
        rows = [finding for finding in findings if finding.gate == gate]
        status = "✅" if not rows else "❌"
        print(f"\n{status} {gate}: {len(rows)} عطبًا", file=out)
        for row in rows:
            print(f"   • {row.path}", file=out)
            print(f"     {row.detail}", file=out)
            for source in row.sources[:3]:
                print(f"     ↳ {source}", file=out)

    print(f"\nℹ️ مسارات مُدرجة بسببها ولا تُحاسَب (منصّة + محتوى مركّب): {len(platform_notes)}", file=out)
    for note in platform_notes[:10]:
        print(f"   • {note['path']} — {note['reason']}; مثال: {note['sources']}", file=out)
    if len(platform_notes) > 10:
        print(f"   … و{len(platform_notes) - 10} غيرها (--json للقائمة كاملة)", file=out)

    total = len(findings)
    print(f"\nالنتيجة: {total} عطبًا", file=out)


# ── قياس الأداة نفسها ──────────────────────────────────────────────────────────────────
def self_test() -> int:
    """يبني شجرة مصغّرة فيها عطبٌ معلوم ثم يتحقّق أن الأداة **تسمّيه**، وأن الشجرة النظيفة تمرّ.

    وأداة تمرّ على كل شيء لا تُثبت شيئًا: هذا الاختبار هو ما يفصل «تعمل» عن «تُصادق».
    """
    failures: list[str] = []

    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        (root / POLICY_DIR).mkdir(parents=True)
        (root / "mainfiles/META-INF/com/google/android").mkdir(parents=True)

        (root / FILE_CONTEXTS).write_text(
            "# مثال\n"
            "/system/bin/sys.maxmanager-service   u:object_r:maxmanager_exec:s0\n"
            "/system/bin/ghost-daemon             u:object_r:maxmanager_exec:s0\n",
            encoding="utf-8",
        )
        (root / "mainfiles/customize.sh").write_text(
            "cp \"$MODPATH/system/bin/sys.maxmanager-service\" \"$MODPATH/system/bin/\"\n"
            "cp \"$MODPATH/system/bin/sys.maxmanager-service\" \"$MODPATH/system/bin/sys.maxmanager-utilityconf\"\n"
            "touch \"$MODPATH/marker\"\n",
            encoding="utf-8",
        )
        # و`init` هو من يُشغّل، وإلا لصار التسمية معلومة نطاق لا عطبًا (وهو التصحيح المقيس).
        (root / "android/aosp").mkdir(parents=True, exist_ok=True)
        (root / "android/aosp/fixture.rc").write_text(
            "service fixture_a /system/bin/sys.maxmanager-service\n"
            "service fixture_b /system/bin/sys.maxmanager-utilityconf\n",
            encoding="utf-8",
        )
        (root / "mainfiles/META-INF/com/google/android/update-binary").write_text(
            "if [ -f $MODPATH/sepolicy.rule ]; then cp -af $MODPATH/sepolicy.rule /x/; fi\n",
            encoding="utf-8",
        )

        findings, _ = scan(root)
        gates: dict[str, list[str]] = {}
        for finding in findings:
            # تجميع حقيقي بالبوابة: الكتابة السابقة كانت تُسند إلى كل بوابة **كل** النتائج،
            # فيمرّ الاختبار على عطب في بوابة بالعطب الذي في غيرها (أي لا يُثبت شيئًا).
            gates.setdefault(finding.gate, []).append(finding.path)

        if gates.get("install-labeled") != ["/system/bin/sys.maxmanager-utilityconf"]:
            failures.append(f"install-labeled: متوقَّع مسار واحد بلا وسم، والمُحصى {gates.get('install-labeled')}")
        if gates.get("label-not-stale") != ["/system/bin/ghost-daemon"]:
            failures.append(f"label-not-stale: متوقَّع الوسم الميت وحده، والمُحصى {gates.get('label-not-stale')}")
        if gates.get("no-dead-reference") != ["$MODPATH/sepolicy.rule"]:
            failures.append(
                f"no-dead-reference: متوقَّع sepolicy.rule وحده، والمُحصى {gates.get('no-dead-reference')}"
            )

        # والشجرة النظيفة: وسمان يطابقان ما يُثبَّت، ولا إشارة إلى ملف غائب ⇒ صفر نتائج.
        (root / FILE_CONTEXTS).write_text(
            "/system/bin/sys.maxmanager-service   u:object_r:maxmanager_exec:s0\n"
            "/system/bin/sys.maxmanager-utilityconf u:object_r:maxmanager_exec:s0\n",
            encoding="utf-8",
        )
        (root / "mainfiles/META-INF/com/google/android/update-binary").write_text("echo ok\n", encoding="utf-8")
        clean, _ = scan(root)
        if clean:
            failures.append(f"الشجرة النظيفة أخرجت نتائج: {[finding.to_dict() for finding in clean]}")

        # وعطب ثالث يُقاس: مسار الحالة (`/data/misc/...`) الذي لا يُنتجه الموديول ولا تُكتبه الشيفرة.
        (root / FILE_CONTEXTS).write_text(
            "/system/bin/sys.maxmanager-service   u:object_r:maxmanager_exec:s0\n"
            "/system/bin/sys.maxmanager-utilityconf u:object_r:maxmanager_exec:s0\n"
            "/data/misc/maxmanager(/.*)?          u:object_r:maxmanager_data_file:s0\n",
            encoding="utf-8",
        )
        stale, _ = scan(root)
        if [finding.path for finding in stale if finding.gate == "label-not-stale"] != ["/data/misc/maxmanager"]:
            failures.append(f"label-not-stale: متوقَّع مسار الحالة المتروك وحده، والمُحصى {[f.path for f in stale]}")

        # ── قواعد المراجعة العميقة: نطاق التسمية، وتعارض المنصّة، ومصادر Soong، ومسارات الحالة ──
        #
        #  وهي أربع حالات لا تُقاس بغيرها: ثنائيّ يُشغّله سكربت جذر (لا init) يجب أن يمرّ،
        #  وثنائيّ يُشغّله init بلا وسم يجب أن يُسمّى، و`vendor: true` يُبرّئ وسم `/vendor/bin`،
        #  ومصدر غائب في `srcs:` يجب أن يُسمّى، ومسار يكتبه الخادم ولا قاعدة له يجب أن يُعلَن.
        deep = Path(tmp) / "deep"
        for directory in (POLICY_DIR, "mainfiles/META-INF/com/google/android", "android/aosp", "archdaemon/jni/include"):
            (deep / directory).mkdir(parents=True, exist_ok=True)
        (deep / FILE_CONTEXTS).write_text(
            "/vendor/bin/maxmanager_daemon   u:object_r:maxmanager_exec:s0\n"
            "/data/misc/maxmanager(/.*)?     u:object_r:maxmanager_data_file:s0\n",
            encoding="utf-8",
        )
        (deep / f"{POLICY_DIR}/maxmanager.te").write_text(
            "type maxmanager, domain;\n"
            "type maxmanager_exec, exec_type, file_type, vendor_file_type;\n"
            "type maxmanager_data_file, file_type, data_file_type;\n"
            "allow maxmanager maxmanager_data_file:file { read write };\n",
            encoding="utf-8",
        )
        (deep / "android/aosp/maxmanager.rc").write_text(
            "service maxmanager_daemon /system/bin/maxmanager_daemon\n",
            encoding="utf-8",
        )
        (deep / SOONG_FILES[0]).write_text(
            "cc_binary {\n    name: \"maxmanager_daemon\",\n"
            "    srcs: [\"runtime/daemon-rust/src/**/*.rs\"],\n"
            "    vendor: true,\n    product_specific: true,\n}\n",
            encoding="utf-8",
        )
        (deep / "mainfiles/customize.sh").write_text(
            "cp \"$TMPDIR/libs/arm64-v8a/sys.maxmanager-service\" \"$MODPATH/system/bin/\"\n"
            "cp \"$MODPATH/bin/maxmanager_daemon\" \"$MODPATH/system/bin/maxmanager_daemon\"\n",
            encoding="utf-8",
        )
        (deep / "mainfiles/service.sh").write_text(
            "$MODPATH/system/bin/sys.maxmanager-service --clearlogs\n", encoding="utf-8"
        )
        (deep / "archdaemon/jni/include/AZenith.h").write_text(
            '#define LOG_FILE "/data/adb/.config/MaxManager/debug/MaxManager.log"\n', encoding="utf-8"
        )
        deep_findings, deep_notes = scan(deep)
        deep_gates: dict[str, list[str]] = {}
        for finding in deep_findings:
            deep_gates.setdefault(finding.gate, []).append(finding.path)
        noted = [note["path"] for note in deep_notes]

        if "/system/bin/sys.maxmanager-service" in deep_gates.get("install-labeled", []):
            failures.append("install-labeled: حساب ثنائيّ يشتغله سكربت جذر (لا init) — وهو ما صُحّح")
        if "/system/bin/sys.maxmanager-service" not in noted:
            failures.append("install-labeled: الثنائيّ الذي لا يُشغّله init لم يُدرَج كمعلومة نطاق")
        if "/system/bin/maxmanager_daemon" not in deep_gates.get("install-labeled", []):
            failures.append(
                f"install-labeled: متوقَّع الثنائيّ الذي يُشغّله init بلا وسم، والمُحصى {deep_gates.get('install-labeled')}"
            )
        if "/vendor/bin/maxmanager_daemon" in deep_gates.get("label-not-stale", []):
            failures.append("label-not-stale: `vendor: true` في Soong لم يُبرّئ `/vendor/bin/...`")
        if "runtime/daemon-rust/src/**/*.rs" not in deep_gates.get("no-dead-source", []):
            failures.append(f"no-dead-source: المصدر الغائب لم يُسمَّ، والمُحصى {deep_gates.get('no-dead-source')}")
        expected_conflicts = {
            f"{SOONG_FILES[0]}: cc_binary maxmanager_daemon",
            "/system/bin/maxmanager_daemon",
        }
        if not expected_conflicts <= set(deep_gates.get("path-conflict", [])):
            failures.append(
                "path-conflict: متوقَّع جمع قسمين في الوحدة وتعاشر المسار مع init، "
                f"والمُحصى {deep_gates.get('path-conflict')}"
            )
        if "/data/adb/.config/MaxManager" not in deep_gates.get("policy-gap", []):
            failures.append(f"policy-gap: مسار الخادم غير المحروس لم يُعلَن، والمُحصى {deep_gates.get('policy-gap')}")

    if failures:
        print("❌ فشل قياس الأداة:")
        for failure in failures:
            print(f"   • {failure}")
        return 1
    print("✅ قياس الأداة: تسمّي الوسم الناقص والميت والإشارة الميتة، وتمرّ على الشجرة النظيفة.")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="مصفوفة SELinux: تثبيت · وسم · مراجع")
    parser.add_argument("--root", default=str(ROOT), help="جذر المستودع (للاختبار)")
    parser.add_argument("--json", action="store_true", help="إخراج آلي")
    parser.add_argument("--assert", dest="assert_gate", action="store_true", help="exit 1 عند أي عطب")
    parser.add_argument("--self-test", action="store_true", help="يقيس الأداة نفسها")
    args = parser.parse_args()

    if args.self_test:
        return self_test()

    root = Path(args.root)
    findings, platform_notes = scan(root)

    if args.json:
        print(
            json.dumps(
                {
                    "findings": [finding.to_dict() for finding in findings],
                    "platform_paths": platform_notes,
                },
                ensure_ascii=False,
                indent=2,
            )
        )
    else:
        render(findings, platform_notes, sys.stdout)

    if args.assert_gate and findings:
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
