"""لقطة المصدر `MaxManager-source.zip` — إنشاء **وتحقّق**، لا إنشاء فقط.

سبب وجود هذه الأداة (مقيس لا مُتخيَّل): الالتزام `MaxManager-source.zip` هو الوسيط الذي يُبنى منه
على GitHub، وكان يُبنى بسكربت عابر في جلسة ثم يُنسى — فبقي **يومًا كاملًا** متأخّرًا عن الشجرة،
وتكرّرت شكوى واحدة خمس مرات لأن التغيير لم يكن يصل أصلًا (HANDOFF تكملة ٨٣). فالأداة لا تكفي
لأن تُنتج؛ يجب أن **تكشف تأخّر لقطة قائمة** قبل أن تُرفع.

الاستعمال:

```sh
python3 tools/pack_source.py                 # يكتب MaxManager-source.zip/ الجديدة (استبدال ذرّي)
python3 tools/pack_source.py --check         # يقارن اللقطة القائمة بالشجرة، ويعطب إن تأخّرت
python3 tools/pack_source.py --assert        # نفس --check لكن بأمر واحد وبحكم صريح (بوابة)
python3 tools/pack_source.py --self-test     # يقيس الأداة على شجرة مؤقتة معروفة
python3 tools/pack_source.py --list          # ما الذي يدخل وما الذي لا يدخل، ولماذا
```

سياسة الاستثناء **مصدر واحد** في هذا الملف (ينطبق الاسم على أي عمق، لا المسار وحده — وهذا هو
الفرق الذي أفلت به `manager/local.properties` في محاولة أولى):

| مُستثنى | لماذا |
| --- | --- |
| `.git` · `.gradle` · `build` · `__pycache__` · `.idea` | حالة بناء أو فهارس، لا مصدر |
| `local.properties` · `*.local.json` · `*.local.yml` · `*.local.yaml` | إعدادات جهاز الباني بطبيعتها (`.claude/settings.local.json` · `.serena/project.local.yml`) — اللقطة تُنقل بين أجيال، وهذا لا يُنقل (§5) |
| `*.jks` · `*.keystore` · `*.jks.bak` | مفاتيح توقيع — خط أحمر مطلق (واللقطة العاملة عند HEAD خالية منها) |
| `*.apk` · `*.aar` · `*.aab` | مخرجات بناء نهائية، تُبنى ولا تُغلَّف |
| `MaxManager-source.zip` | اللقطة لا تحوي نفسها |

**وما لا يُستثنى ولو بدا «ثنائيًّا» — بالقياس لا بالحدس:**
`*.jar` (فيه `gradle/wrapper/gradle-wrapper.jar` = 46 KB، وبه يعمل `./gradlew`)، و`*.so`
(سبعة في `jniLibs/` — `libtermux` و`libmagiskboot` و`liblptools` — وهي **مدخلات** يشحنها البناء،
لا مخرجات). وقد كشف `--check` أن استثناءهما كان يُسقط ٨ ملفات من لقطة عاملة، فسُجّل ذلك هنا
حتى لا يُعاد. والباقي **يدخل كله**.

والباقي **يدخل كله** — بما فيه المجلدات المخفية (`.github`, `.planning`, `.serena` …) لأنها تدخل في
توليد ما يُشحن. وما دخل يُكتب في السحابة بزمن تعديله الحقيقي، ويُقرأ بعد الكتابة للتحقّق.
"""
from __future__ import annotations

import argparse
import os
import pathlib
import sys
import tempfile
import time
import zipfile

_HERE = pathlib.Path(__file__).resolve().parent
REPO = _HERE.parent

SNAPSHOT_NAME = "MaxManager-source.zip"

# مورد واحد للمصير: الاسم (أيّ عمق) لا المسار.
SKIP_DIR_NAMES = {".git", ".gradle", "build", "__pycache__", ".idea"}
SKIP_BASENAMES = {SNAPSHOT_NAME, SNAPSHOT_NAME + ".new", SNAPSHOT_NAME + ".tmp", "local.properties"}
# لا تُضَف `.so` ولا `.jar` هنا: القياس أظهر أنهما مدخلات بناء (jniLibs + gradle-wrapper),
# وإسقاطهما يُخفي عطبًا في اللقطة لا يُظهره. والسرّ وحده والمخرج النهائي يُستثنيان.
SKIP_SUFFIXES = {".apk", ".aar", ".aab", ".jks", ".keystore", ".jks.bak"}

# البايت-كود المُصرَّف قد يوجد في لقطة قديمة؛ لا يُحسب «زيادة» فيُعطب تشخيص لا معنى له.
IGNORED_EXTRA_PREFIXES = ("tools/__pycache__/",)


LOCAL_CONFIG_SUFFIXES = (".local.json", ".local.yml", ".local.yaml")


def is_skipped(rel: str, name: str) -> str | None:
    """يُعيد سبب الاستثناء، أو None إن كان الملف داخلًا."""
    if any(part in SKIP_DIR_NAMES for part in rel.split("/")):
        return "مجرد/حالة بناء"
    if name in SKIP_BASENAMES:
        return "اسم مستثنى"
    if name.endswith(LOCAL_CONFIG_SUFFIXES):
        return "إعداد جهاز محليّ"
    for suffix in SKIP_SUFFIXES:
        if name.endswith(suffix):
            return "مفتاح أو مخرج نهائي"
    return None


def collect(
    root: pathlib.Path, exclude_rel: str | None = None
) -> tuple[list[str], set[str], list[tuple[str, str]]]:
    """`exclude_rel`: مسار اللقطة الناتجة إن كانت داخل الشجرة، فلا تُغلَّف في نفسها."""
    files: list[str] = []
    dirs: set[str] = set()
    skipped: list[tuple[str, str]] = []
    for path in root.rglob("*"):
        rel = path.relative_to(root).as_posix()
        if exclude_rel is not None and rel == exclude_rel:
            continue
        reason = is_skipped(rel, path.name)
        if reason:
            if path.is_file():
                skipped.append((rel, reason))
            continue
        if path.is_dir():
            dirs.add(rel)
        elif path.is_file():
            files.append(rel)
            parent = os.path.dirname(rel)
            while parent:
                dirs.add(parent)
                parent = os.path.dirname(parent)
    files.sort()
    return files, dirs, skipped


def write_snapshot(root: pathlib.Path, out: pathlib.Path) -> int:
    files, dirs, _ = collect(root, _out_rel(root, out))
    tmp = out.with_suffix(out.suffix + ".tmp")
    with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for d in sorted(dirs):
            info = zipfile.ZipInfo(d + "/", date_time=(2026, 1, 1, 0, 0, 0))
            info.external_attr = (0o40755 << 16) | 0x10
            z.writestr(info, b"")
        for rel in files:
            path = root / rel
            info = zipfile.ZipInfo(rel, date_time=_mtime(path))
            info.external_attr = 0o100644 << 16
            info.create_system = 3
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, path.read_bytes())
    os.replace(tmp, out)  # استبدال ذرّي: لا لقطة نصف مكتوبة تُرفع
    return len(files)


def _mtime(path: pathlib.Path) -> tuple[int, int, int, int, int, int]:
    stamp = time.localtime(path.stat().st_mtime)
    year = min(max(stamp.tm_year, 1980), 2107)  # حدّ ZIP
    return (year, stamp.tm_mon, stamp.tm_mday, stamp.tm_hour, stamp.tm_min, stamp.tm_sec)


def _out_rel(root: pathlib.Path, out: pathlib.Path) -> str | None:
    try:
        return out.resolve().relative_to(root.resolve()).as_posix()
    except ValueError:
        return None


def check(root: pathlib.Path, archive: pathlib.Path) -> tuple[int, int, int, list[str]]:
    """المقارنة بالاسم **وبالمحتوى بايتًا بايتًا** — لا بعدد الملفات."""
    if not archive.is_file():
        return 0, 0, 0, [f"اللقطة غير موجودة: {archive}"]
    files, _, _ = collect(root, _out_rel(root, archive))
    with zipfile.ZipFile(archive) as z:
        inside = {n for n in z.namelist() if not n.endswith("/")}
        missing = sorted(set(files) - inside)
        extra = sorted(
            n for n in inside - set(files)
            if not any(n.startswith(p) for p in IGNORED_EXTRA_PREFIXES)
        )
        stale = []
        for rel in sorted(set(files) & inside):
            if z.read(rel) != (root / rel).read_bytes():
                stale.append(rel)
    details = (
        [f"غائب عن اللقطة ({len(missing)}): {p}" for p in missing]
        + [f"قديم في اللقطة — محتواه يخالف الشجرة ({len(stale)}): {p}" for p in stale]
        + [f"زائد في اللقطة ({len(extra)}): {p}" for p in extra]
    )
    return len(missing), len(stale), len(extra), details


def self_test() -> int:
    """أداة تُمرّ على كل شيء لا تُثبت شيئًا: تُقاس على شجرة معروفة، ويُطلب منها أن تكشف تأخّرًا."""
    failures = []

    def case(name: str, got, want):
        if got != want:
            failures.append(f"{name}: توقّع {want} · حصل {got}")

    with tempfile.TemporaryDirectory() as tmpdir:
        root = pathlib.Path(tmpdir)
        (root / "src").mkdir()
        (root / "src" / "a.kt").write_text("val a = 1\n", encoding="utf-8")
        (root / "build").mkdir()
        (root / "build" / "junk.kt").write_text("x\n", encoding="utf-8")
        (root / ".git").mkdir()
        (root / ".git" / "HEAD").write_text("ref\n", encoding="utf-8")
        (root / "manager").mkdir()
        (root / "manager" / "local.properties").write_text("sdk.dir=/home/me\n", encoding="utf-8")
        (root / ".claude").mkdir()
        (root / ".claude" / "settings.local.json").write_text("{}\n", encoding="utf-8")
        (root / "app.jks").write_text("key\n", encoding="utf-8")
        (root / "tools").mkdir()
        (root / "tools" / "__pycache__").mkdir()
        (root / "tools" / "__pycache__" / "x.pyc").write_bytes(b"\x00")

        files, _, _ = collect(root)
        case("الاستثناءات بالاسم في أي عمق", files, ["src/a.kt"])
        skipped_names = {rel for rel, _ in collect(root)[2]}
        case(
            "إعداد الجهاز المحليّ لا يدخل",
            {"manager/local.properties", ".claude/settings.local.json"} <= skipped_names,
            True,
        )

        archive = root / SNAPSHOT_NAME
        n = write_snapshot(root, archive)
        case("عدد المُغلَّف", n, 1)
        case("لقطة طازجة تُقبل", check(root, archive)[:3], (0, 0, 0))

        # تأخّر بايت واحد في ملف موجود ⇒ يُكشف (وهذا هو العطب الذي دفع لكتابة الأداة)
        (root / "src" / "a.kt").write_text("val a = 2\n", encoding="utf-8")
        missing, stale, extra, _ = check(root, archive)
        case("محتوى متغيّر يُكشف", (missing, stale, extra), (0, 1, 0))

        # ملف جديد لم يدخل اللقطة ⇒ «غائب»
        write_snapshot(root, archive)
        (root / "src" / "b.kt").write_text("val b = 1\n", encoding="utf-8")
        case("ملف جديد يُكشف", check(root, archive)[:3], (1, 0, 0))

        # ملف أُزيل من الشجرة وبقي في اللقطة ⇒ «زائد»
        write_snapshot(root, archive)
        (root / "src" / "b.kt").unlink()
        case("ملف مُزال يُكشف", check(root, archive)[:3], (0, 0, 1))

        # البايت-كود المُصرَّف في لقطة قديمة لا يُعطب الحكم
        write_snapshot(root, archive)
        (root / "tools" / "__pycache__" / "x.pyc").write_bytes(b"\x01")
        with zipfile.ZipFile(archive, "a") as z:
            z.writestr("tools/__pycache__/x.pyc", b"\x00")
        case("البايت-كود المُتجاهَل لا يعطب", check(root, archive)[:3], (0, 0, 0))

    print("اختبار الأداة على شجرة معروفة: 6 حالات")
    if failures:
        for f in failures:
            print(f"  ✗ {f}")
        print(f"  النتيجة: {len(failures)} إخفاق")
        return 1
    print("  ✓ الاستثناءات · الطزاجة · التغيّر · الجديد · المُزال · المُتجاهَل — كلها كما يجب")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="لقطة المصدر: بناء وتحقّق")
    parser.add_argument("--out", default=str(REPO / SNAPSHOT_NAME), help="مسار اللقطة")
    parser.add_argument("--check", action="store_true", help="قارن اللقطة القائمة بالشجرة")
    parser.add_argument("--assert", dest="gate", action="store_true", help="بوابة: حكم واحد وخرج بخطأ عند تأخّر")
    parser.add_argument("--self-test", action="store_true", help="اختبر الأداة نفسها")
    parser.add_argument("--list", dest="list_skipped", action="store_true", help="اعرض المُستثنى وأسبابه")
    args = parser.parse_args()

    if args.self_test:
        return self_test()

    out = pathlib.Path(args.out)
    if args.list_skipped:
        _, _, skipped = collect(REPO)
        buckets: dict[str, list[str]] = {}
        for rel, reason in skipped:
            buckets.setdefault(reason, []).append(rel)
        for reason in sorted(buckets):
            print(f"— {reason} ({len(buckets[reason])})")
            for rel in sorted(buckets[reason])[:8]:
                print(f"    {rel}")
            if len(buckets[reason]) > 8:
                print(f"    … و {len(buckets[reason]) - 8} أخرى")
        return 0

    if args.check or args.gate:
        missing, stale, extra, details = check(REPO, out)
        print(f"لقطة المصدر: {out.name}")
        print(f"  غائب {missing} · قديم {stale} · زائد {extra}")
        if missing or stale or extra:
            for line in details[:40]:
                print(f"  ✗ {line}")
            if len(details) > 40:
                print(f"  … و {len(details) - 40} أخرى")
            print("  الحصيلة: اللقطة متأخّرة عن الشجرة — أعد البناء بلا --check")
            return 1
        print("  الحصيلة: اللقطة مطابقة للشجرة بايتًا بايتًا ✅")
        return 0

    count = write_snapshot(REPO, out)
    missing, stale, extra, details = check(REPO, out)
    size_mb = out.stat().st_size / 1048576
    print(f"كُتبت {out.name}: {count} ملفًا · {size_mb:.1f} ميجابايت")
    print(f"  تحقّق فوري: غائب {missing} · قديم {stale} · زائد {extra}")
    if missing or stale or extra:
        for line in details[:20]:
            print(f"  ✗ {line}")
        return 1
    print("  الحصيلة: لقطة مطابقة للشجرة ✅")
    return 0


if __name__ == "__main__":
    sys.exit(main())
