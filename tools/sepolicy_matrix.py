#!/usr/bin/env python3
"""مصفوفة SELinux — «هل يسمح النظام بهذا المسار؟» قبل أن يُجرَّب على جهاز.

لماذا وُجدت هذه الأداة
----------------------
السياسة في هذا المستودع **مكتوبة مرّتين، ولا واحدة منهما تُقاس**: `android/aosp/sepolicy/`
مسارات AOSP، و`mainfiles/` مسار الموديول. والفرق بينهما لم يكن في جدول واحد، فسؤال
«هل المسار الذي نكتبه مُعلَّم؟» كان يُجاب بالقراءة اليدوية لكل ملف في كل مراجعة.

والعطب الذي كشفته المراجعة الأولى — ويُقاس هنا لا يُروى:

* `mainfiles/META-INF/com/google/android/update-binary:138` ينسخ `$MODPATH/sepolicy.rule`
  **ولا ملف بهذا الاسم في الشجرة كلها** — والفحص `[ -f ]` صامت، فيمرّ غيابه بلا رسالة.
* و`android/aosp/sepolicy/file_contexts` يُعلّم `/system/bin/maxmanager_daemon`، بينما الموديول
  يُثبّت `sys.maxmanager-service` في `system/bin/` — أي أن الوسم يشير إلى اسم لا يُنتَج.

فالأداة تجمع **من الشجرة** ثلاث حقائق وتقارنها: ما يُثبَّت، وما هو مُعلَّم، وما يُشار إليه ولا وجود له.

البوابات
--------
| البوابة | ما تحكم عليه |
| --- | --- |
| `install-labeled` | كل **ملف** يُثبَّت عندنا لازمٌ وسمٌ في `file_contexts` (ولا يُحاسب مجلدٌ: المجلد لا يُوسَم) |
| `label-not-stale` | كل سطر وسم يشير إلى مسار **يُنتَج فعلًا** (من الموديول أو من مسار AOSP) وإلا فهو وسم ميت |
| `no-dead-reference` | كل ملف يُشار إليه بـ`$MODPATH/<اسم>` موجودٌ في الشجرة أو يُنتجه البناء |

والمصادر الثلاثة للمسارات المُنتَجة معلنة في الرمز: أوامر التثبيت في `mainfiles/`، وأسطر `service`
في `android/aosp/*.rc` (وهي التي تُنشر الثنائيات في مسار AOSP)، ومسارات الحالة التي تُكتب من الشيفرة.
والاتحاد هو ما يُقارَن به الوسم — فلا يُسمّى وسمًا ميتًا وهو صحيح في مسار آخر.

وحدودها مُعلَنة
--------------
* مسارات المنصّة (`/sys/**`, `/proc/**`, `/dev/**`) **ليست ملكنا**، ووسمها يأتي من نطاق النظام
  (`sysfs`, `proc`, `dev_type`). الأداة تُدرجها كـ`platform` بسببها ولا تُحاسبها — ولا تدّعي أنها
  «آمنة»: ما يدخلها من الكتابة يبقى محكومًا بـarbiter ونطاق الجذر.
* وبوابة `install-labeled` تُحاسب ثنائيّات `bin/` وحدها، وهذا **تصنيف مُعلَن لا تجاهل**: المحتوى
  المركّب (`product/priv-app`, `product/etc`) يُوسَم بوسوم المنصّة (`system_file`, `priv_app`).
  ⚠️ وأما `/system/bin/**` في مسار الموديول فهي أسماء **تُرى عبر OverlayFS/magic-mount** لا ملفات
  حقيقية في القسم، ووسمها يأتي من طبقة التركيب — فوِجدانُ الأداة هنا يخصّ التنصيب المباشر في
  القسم (مسار AOSP/الصورة) وما يُشغَّل بنطاق خاص بنا. وهذا الحدّ مكتوب لأنه يُغيّر تفسير العطب.
* الأداة لا تُصرّف سياسة ولا تتحقّق من صحّة `.te` نحويًّا: تقرأ الوسوم والمسارات وتربطها.
* ولا تكشف `allow` الناقص داخل نطاقنا: ذلك يحتاج `checkpolicy`/جهازًا.

الاستعمال
--------
```sh
python3 tools/sepolicy_matrix.py            # تقرير مقروء
python3 tools/sepolicy_matrix.py --json     # للقراءة الآلية
python3 tools/sepolicy_matrix.py --assert   # exit 1 عند أي عطب
python3 tools/sepolicy_matrix.py --self-test
```
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
    for script in INSTALLER_SCRIPTS:
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


def runtime_state_paths(root: Path) -> dict[str, list[str]]:
    """مسارات الحالة التي تُكتب من الشيفرة — وجودها يُبرّر وسمًا لا يُنتجه المثبِّت."""
    return {
        path: sources
        for path, sources in _device_paths(root, ("/data/misc/maxmanager",)).items()
        if not path.endswith("/")
    }


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
    """
    references: dict[str, list[str]] = {}
    for script in (*INSTALLER_SCRIPTS, "mainfiles/META-INF/com/google/android/updater-script"):
        for raw in read(root, script).splitlines():
            line = raw.split("#", 1)[0]
            for match in re.finditer(r"\$MODPATH/([A-Za-z0-9_][A-Za-z0-9_.-]*)(?![A-Za-z0-9_./-])", line):
                references.setdefault(match.group(1), []).append(f"{script}:{line.strip()[:70]}")
    return references


def is_produced(root: Path, name: str) -> bool:
    """هل يُنتج البناء هذا الاسم (في الشجرة أو في مخرَج مُعلَن)؟"""
    if (root / name).exists() or (root / "mainfiles" / name).exists():
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
    state = runtime_state_paths(root)
    produced = {**targets, **aosp, **state}
    # ثنائيّات bin/ المنسوخة جماعيًّا تدخل فحص الوسم أيضًا، وإن لم تُنتج هدفًا صريحًا في السطر.
    glob_binaries = binaries_installed_by_glob(root)
    for device_path, sources in glob_binaries.items():
        targets.setdefault(device_path, sources)

    # ١) كل **ثنائيّ** نضعه في `bin/` يجب أن يحمل وسمًا معلنًا (أو استثناءً مكتوبًا).
    #
    #    والاقتصار على `bin/` مقصود ومُعلَن: المحتوى المركّب (`product/etc`, `product/priv-app`)
    #    يُقرأ بوسوم المنصّة (`system_file`, `priv_app`, …) ولا يحتاج وسمًا من سياستنا؛ والفشل هنا
    #    لو اقتضيناه له لكان **تصنيفًا خاطئًا** يُدفن فيه الاكتشاف الحقيقي.
    for device_path in sorted(targets):
        if not device_path.startswith(NEEDS_OUR_LABEL_PREFIXES):
            continue
        if device_path in DOCUMENTED_EXCEPTIONS:
            continue
        if label_covers(labels, device_path) is None:
            findings.append(
                Finding(
                    gate="install-labeled",
                    path=device_path,
                    detail="ثنائيّ يُثبَّت في bin/ بلا وسم في file_contexts (لا نطاق ولا exec_type لنا)",
                    sources=sorted(set(targets[device_path])),
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

    # ٥) مسارات المنصّة: تُدرج بسببها ولا تُحاسب — ولا تُسمّى «آمنة».
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
    gates = ["install-labeled", "label-not-stale", "no-dead-reference"]
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
