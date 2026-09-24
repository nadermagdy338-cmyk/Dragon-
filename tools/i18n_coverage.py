#!/usr/bin/env python3
"""أداة تغطية الترجمة — تجمع مفاتيح النصوص لكل لغة وتقيس الناقص.

لماذا هذه الأداة: التطبيق يشحن ١٦٢٩ مفتاحًا بالإنجليزية و٨٤ مجلد لغات، ولم يكن هناك أي وسيلة
لمعرفة ما ينقص كل لغة بلا عدّ يدوي. هي لا تُترجم شيئًا ولا تُخترع نصًّا: تُخرج الحقيقة (ما ينقص)
وتبعثها إلى خط الترجمة (Crowdin) أو إلى مترجم بشري بصيغة جاهزة.

الاستخدام (من جذر المستودع):
    python3 tools/i18n_coverage.py                  # جدول التغطية لكل لغة
    python3 tools/i18n_coverage.py --locale de      # تفصيل لغة واحدة
    python3 tools/i18n_coverage.py --manifest de    # كتابة manifest CSV لترجمتها
    python3 tools/i18n_coverage.py --check-codes    # تطابق قائمة AppLanguage مع مجلدات values-*
    python3 tools/i18n_coverage.py --assert         # يخرج بخطأ فقط عند عيب حقيقي (ليس عند نقص تغطية)
    python3 tools/i18n_coverage.py --prune all --dry-run   # المفاتيح اليتيمة في كل لغة (بلا كتابة)

مخرجات --manifest تُكتب في `build/i18n/` ولا تُودع في git (مجلد بناء).
"""
from __future__ import annotations

import argparse
import csv
import signal
import os
import re
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field

# الجدر يُستنتج من موقع هذا الملف (tools/ ← جدر المستودع)، فلا يعتمد الناتج على مجلد العمل.
# كان مسارًا نسبيًّا، فكان التشغيل من `tools/` يعطي FileNotFoundError وينتج صفرًا — وهذا هو نفس
# العيب الذي أُصلح في `tools/repo_audit.py`، وكان لا بدّ من إصلاحه هنا أيضًا حتى تصحّ عبارة
# «كل أداة تستنتج الجدر بنفسها» بدلًا من أن تكون وصفًا غير صحيح.
_HERE = os.path.dirname(os.path.abspath(__file__))
_REPO = os.path.dirname(_HERE)

RES = os.path.join(_REPO, "manager", "app", "src", "main", "res")
APP_LANGUAGE_KT = os.path.join(
    _REPO, "manager", "app", "src", "main", "java", "nd", "max", "ui", "settings", "AppLanguage.kt"
)
MANIFEST_DIR = os.path.join(_REPO, "build", "i18n")

if not os.path.isdir(RES):
    sys.exit(f"لم يُعثر على شجرة الموارد: {RES}\nشغّل الملف من داخل شجرة المستودع.")
# أشكال الوسائط الفعلية في المصدر: `%1$s`, `%2$d`, و**الأعداد العشرية** `%4$.2f` / `%2$.1f` — وهي التي
# كشفت فجوة النمط الأول: كان `%\d+\$[sd]` لا يعرف `f`، فمرّت `%4$.2f` بلا مطابقة، ورفضت البوابة نصًّا
# عربيًّا صحيحًا يحتويها، وبقيت غير قادرة على كشف تغيير نوعي فيها. النمط يرفض أن يبدأ بمسافة كي لا
# يقرأ نسبة مئوية عادية («‎100% من»‎) وسيطًا.
SPECIFIER = re.compile(r"%(\d+\$)?[-#+0,(]*\d*(\.\d+)?[sdfoxegX]")


@dataclass
class LocaleStrings:
    locale: str
    files: dict[str, dict[str, str]] = field(default_factory=dict)

    def keys(self, file_name: str) -> dict[str, str]:
        return self.files.get(file_name, {})


def string_files() -> list[str]:
    """كل ملفات النصوص الإنجليزية التي تحمل مفاتيح قابلة للترجمة."""
    values = os.path.join(RES, "values")
    result = []
    for name in sorted(os.listdir(values)):
        if not name.endswith(".xml"):
            continue
        with open(os.path.join(values, name), encoding="utf-8") as fh:
            if "<string " in fh.read():
                result.append(name)
    return result


def load(locale_dir: str, file_name: str) -> dict[str, str]:
    """مفاتيح ملف واحد — و**يُستثنى** ما وُسم `translatable="false"`: لا يُترجم، فليس «ناقصًا».

    القياس الذي أنشأ هذا الشرط (2026-09-24): `home_memory_swap_label` («ZRAM» — اسم وحدة لا كلمة)
    أُضيف بـ`translatable="false"` في `values/` وحده بقرار معلن في `ADR-40`، فكانت هذه الأداة
    تعرضه «مفقودًا في ٨٥ لغة» إلى الأبد — أي رقمًا لا يُطارد، وسببًا دائمًا لتعبئة آلية تُضيف
    ترجمةً لمفتاح طُلب ألّا يُترجم (وهو ما يراه lint `Translatable`: مترجم في لغة وغير قابل للترجمة
    في الأساس). فالمفتاح لم يبقَ في عدّاد، والخارج من العدّاد مكتوب هنا لا مخفيّ.
    """
    path = os.path.join(locale_dir, file_name)
    if not os.path.exists(path):
        return {}
    root = ET.parse(path).getroot()
    return {
        e.get("name"): "".join(e.itertext())
        for e in root
        if e.get("name") and e.get("translatable") != "false"
    }


def load_locale(locale: str) -> LocaleStrings:
    folder = os.path.join(RES, f"values-{locale}")
    return LocaleStrings(locale=locale, files={f: load(folder, f) for f in string_files()})


def english() -> LocaleStrings:
    return LocaleStrings(locale="en", files={f: load(os.path.join(RES, "values"), f) for f in string_files()})


def locale_folders() -> list[str]:
    return sorted(
        d[len("values-") :] for d in os.listdir(RES) if d.startswith("values-") and os.path.isdir(os.path.join(RES, d))
    )


# أكواد المجلدات القديمة التي يوفّق أندرويد بينها وبين صيغتها الحديثة عند قراءة الموارد.
LEGACY = {"in": "id", "iw": "he", "tl": "fil", "ji": "yi"}


def folder_to_tag(folder: str) -> str:
    """يحوّل مقطع الأندرويد (`en-rAU`, `b+sr+Latn`, `in`) إلى وسم BCP-47 (`en-AU`, `sr-Latn`, `id`)."""
    if folder.startswith("b+"):
        return "-".join(folder[2:].split("+"))
    if "-r" in folder:
        lang, region = folder.split("-r", 1)
        return f"{lang}-{region}"
    return LEGACY.get(folder, folder)


def app_language_codes() -> list[str]:
    if not os.path.exists(APP_LANGUAGE_KT):
        return []
    text = open(APP_LANGUAGE_KT, encoding="utf-8").read()
    block = text.split("private val CODES: List<String> = listOf(", 1)[-1].split(")", 1)[0]
    return re.findall(r'"([^"]+)"', block)


def real_defects(en: LocaleStrings, target: LocaleStrings) -> list[str]:
    """عيب حقيقي = ما يمكن أن يُسقط التطبيق أو يُسقط البناء، لا نقص ترجمة.

    العيب الثاني (مفتاح بلا نظير في `values/`) أُضيف بعد قياس، لا تقديرًا: سقط
    `:app:lintVitalRelease` بـ**٦ أخطاء قاتلة** `ExtraTranslation` في `values-es` و`values-fr`
    (ثلاثة مفاتيح حُذفت من `values/` و`values-ar/` وبقيت فيهما)، وهذه البوابة كانت **خضراء في
    اللحظة نفسها** (exit 0 · عوائق 0) — أي أنها لم تكن ترى الصنف الذي يراه lint، بينما lint هو
    ما يُسقط `assembleRelease` فعلًا. فالبوابة الآن ترى ما يراه الحاجز الذي يُسقط البناء.
    """
    problems = []
    for file_name, en_keys in en.files.items():
        target_keys = target.keys(file_name)
        for key, value in target_keys.items():
            if key not in en_keys:
                problems.append(
                    f"{file_name}:{key} موجود في values-{target.locale} ولا نظير له في values/ "
                    f"(lint: ExtraTranslation — خطأ قاتل يُسقط assembleRelease)"
                )
                continue
            extra = set(SPECIFIER.findall(value)) - set(SPECIFIER.findall(en_keys[key]))
            if extra:
                problems.append(f"{file_name}:{key} يطلب {sorted(extra)} ولا يمرّره الكود")
        path = os.path.join(RES, f"values-{target.locale}", file_name)
        if os.path.exists(path):
            names = [e.get("name") for e in ET.parse(path).getroot() if e.get("name")]
            dup = sorted({n for n in names if names.count(n) > 1})
            if dup:
                problems.append(f"{file_name}: مفاتيح مكرّرة {dup}")
    return problems


def coverage_row(en: LocaleStrings, target: LocaleStrings) -> dict[str, object]:
    total = sum(len(v) for v in en.files.values())
    translated = 0
    missing_files = []
    for file_name, en_keys in en.files.items():
        got = target.keys(file_name)
        translated += sum(1 for k in en_keys if k in got)
        if not got:
            missing_files.append(file_name)
    return {
        "locale": target.locale,
        "translated": translated,
        "total": total,
        "percent": round(100.0 * translated / total, 1) if total else 0.0,
        "missing_files": len(missing_files),
        "files": len(en.files),
    }


def cmd_report(args: argparse.Namespace) -> int:
    en = english()
    print(f"الملفات المصدر: {len(en.files)} — إجمالي المفاتيح الإنجليزية: {sum(len(v) for v in en.files.values())}")
    print(f"{'locale':<10}{'keys':>7}{'/':>2}{'total':>7}{'%':>8}{'missing files':>15}")
    rows = []
    for folder in locale_folders():
        row = coverage_row(en, load_locale(folder))
        row["locale"] = folder
        rows.append(row)
    for row in sorted(rows, key=lambda r: (-r["percent"], r["locale"])):  # type: ignore[operator]
        print(
            f"{row['locale']:<10}{row['translated']:>7}{'/':>2}{row['total']:>7}"
            f"{row['percent']:>8}{row['missing_files']:>15}"
        )
    if rows:
        avg = sum(r["percent"] for r in rows) / len(rows)  # type: ignore[misc]
        print(f"\nمتوسط التغطية: {avg:.1f}%  ·  أدنى لغة: {min(r['percent'] for r in rows)}%")  # type: ignore[misc]
    return 0


def cmd_locale(args: argparse.Namespace) -> int:
    en = english()
    target = load_locale(args.locale)
    for file_name, en_keys in en.files.items():
        got = target.keys(file_name)
        missing = [k for k in en_keys if k not in got]
        print(f"{file_name}: {len(got)}/{len(en_keys)} مفقود={len(missing)}")
        for key in missing[: args.limit]:
            print(f"    - {key}: {en_keys[key]}")
        if len(missing) > args.limit:
            print(f"    … و{len(missing) - args.limit} أخرى (استخدم --manifest لملف كامل)")
    problems = real_defects(en, target)
    print("عيب حقيقي:", problems or "لا شيء")
    return 0


def cmd_todo(args: argparse.Namespace) -> int:
    """يطبع أسطر المفاتيح المفقودة إلى **المخرجات**، لا إلى ملف.

    السبب وجودي: ملفات `build/` متجاهَلة في `.gitignore`، وأدوات الاستكشاف
    (`glob` / البحث) **لا تراها**. فأي وكيل يقرأ وصفة تقول «اقرأ
    build/i18n/todo_de.txt» لن يجدها بالاستكشاف. الأمر يُنتجها متى شئت:

        python3 tools/i18n_coverage.py --todo de | sed -n '1,200p'   # دفعة
        python3 tools/i18n_coverage.py --todo de | wc -l              # الحجم
    """
    en = english()
    target = load_locale(args.todo)
    total = 0
    for file_name, en_keys in en.files.items():
        got = target.keys(file_name)
        for key, text in en_keys.items():
            if key in got:
                continue
            # الأعمدة: الملف · المفتاح · النص الإنجليزي — نفس صيغة todo_*.txt السابقة
            print(f"{file_name}\t{key}\t{text}")
            total += 1
    if not total:
        print(f"لا شيء مفقود في {args.todo} — مكتملة", file=sys.stderr)
    return 0


def cmd_manifest(args: argparse.Namespace) -> int:
    en = english()
    target = load_locale(args.manifest)
    os.makedirs(MANIFEST_DIR, exist_ok=True)
    out = os.path.join(MANIFEST_DIR, f"to_translate_{args.manifest}.csv")
    written = 0
    with open(out, "w", encoding="utf-8", newline="") as fh:
        writer = csv.writer(fh)
        writer.writerow(["file", "key", "source_en", "existing_translation"])
        for file_name, en_keys in en.files.items():
            got = target.keys(file_name)
            for key, text in en_keys.items():
                if key in got:
                    continue
                writer.writerow([f"values/{file_name}", key, text, ""])
                written += 1
    print(f"كُتب {written} مفتاحًا إلى {out}")
    print("أرسل هذا الملف إلى مترجم، أو ارفعه إلى Crowdin — ولا تُكتب الترجمات هنا.")
    return 0


def locales_config_tags() -> list[str]:
    path = os.path.join(RES, "xml", "locales_config.xml")
    if not os.path.exists(path):
        return []
    return re.findall(r'<locale\s+android:name="([^"]+)"', open(path, encoding="utf-8").read())


def cmd_write_manifests(args: argparse.Namespace) -> int:
    """يولّد CSV بالمفاتيح الناقصة لكل لغة — هذه هي المادة القابلة للترجمة (لا تُترجم هنا)."""
    en = english()
    os.makedirs(MANIFEST_DIR, exist_ok=True)
    total = 0
    for folder in locale_folders():
        target = load_locale(folder)
        rows = []
        for file_name, en_keys in en.files.items():
            got = target.keys(file_name)
            for key, text in en_keys.items():
                if key not in got:
                    rows.append([f"values/{file_name}", key, text, ""])
        if not rows:
            continue
        out = os.path.join(MANIFEST_DIR, f"to_translate_{folder.replace('+', '_')}.csv")
        with open(out, "w", encoding="utf-8", newline="") as fh:
            writer = csv.writer(fh)
            writer.writerow(["file", "key", "source_en", "translation"])
            writer.writerows(rows)
        total += len(rows)
        print(f"{folder:<10}{len(rows):>6} مفتاحًا")
    print(f"\nالإجمالي: {total} مفتاحًا في {len(os.listdir(MANIFEST_DIR))} ملف CSV داخل {MANIFEST_DIR}/")
    return 0


def escape_android(text: str) -> str:
    """تهريب قيمة أندرويد. بدونه يُفشل `aapt2` البناء على فاصلة عليا أو `&` غير مهربة."""
    text = text.replace("&", "&amp;").replace("<", "&lt;")
    if len(text) > 1 and text.startswith('"') and text.endswith('"'):
        return '"' + text[1:-1].replace('"', '\\"') + '"'
    return text.replace("'", "\\'")


def lone_percent(text: str) -> bool:
    """هل بقي `%` بعد إزالة الوسائط و`%%` المهرّبة؟

    `%%` مهرَّبة مقصودة (تظهر في نصوص فيها نسبة مئوية حقيقية) ولا تُعدّ عيبًا — أول نسخة من هذا الفحص
    رفضت ٤ نصوص صحيحة لأنها لم تستثنها. ما يبقى بعد الاثنين هو `%` عارية تُقرأ موضوعًا للوسيط
    في `String.format` فتُطلق استثناءً وقت التشغيل.
    """
    return "%" in SPECIFIER.sub("", text).replace("%%", "")


def cmd_apply_csv(args: argparse.Namespace) -> int:
    """يدمج CSV مُترجمًا في `values-<locale>/` بأسلوب الإضافة فقط.

    مبدآن لا يتنازلان هنا:
    ① لا يُعاد كتابة الملف القائم أبدًا — تُضاف المفاتيح الجديدة قبل `</resources>` فقط،
      فالتعليقات والترتيب والسلاسل القائمة تبقى حرفيًا كما هي.
    ② لا يُكتب صف قبل التحقق: وسيط يطلبه النص ولا يمرّره الكود، أو `%` مفرد داخل نص
      يُنسّق، أو مفتاح موجود أصلًا — كلها تُرفض وتُطبع ولا تصل إلى المورد.
    """
    en = english()
    path = args.apply_csv
    if not os.path.exists(path):
        print(f"لا يوجد ملف: {path}")
        return 1
    rows = list(csv.DictReader(open(path, encoding="utf-8")))
    existing = load_locale(args.locale)
    accepted: dict[str, list[tuple[str, str]]] = {}
    seen: set[tuple[str, str]] = set()  # يمنع مفتاحًا مكرّرًا في الدفعة نفسها (خطأ بناء في aapt2)
    rejected: list[str] = []
    for row in rows:
        file_name = os.path.basename(row["file"])
        key, text = row["key"], (row.get("translation") or "").strip()
        if not text:
            continue
        source = en.files.get(file_name, {}).get(key)
        if source is None:
            rejected.append(f"{file_name}:{key} — المفتاح غير موجود في المصدر الإنجليزي")
            continue
        if key in existing.keys(file_name):
            rejected.append(f"{file_name}:{key} — موجود أصلًا في values-{args.locale} (لا يُستبدل صامتًا)")
            continue
        if (file_name, key) in seen:
            rejected.append(f"{file_name}:{key} — مكرّر في الدفعة نفسها")
            continue
        seen.add((file_name, key))
        extra = set(SPECIFIER.findall(text)) - set(SPECIFIER.findall(source))
        if extra:
            rejected.append(f"{file_name}:{key} — يطلب {sorted(extra)} ولا يمرّره الكود")
            continue
        if lone_percent(text) and SPECIFIER.search(source):
            rejected.append(f"{file_name}:{key} — `%` مفرد في نص منسّق")
            continue
        accepted.setdefault(file_name, []).append((key, escape_android(text)))
    written = sum(len(v) for v in accepted.values())
    for file_name, pairs in accepted.items():
        if args.dry_run:
            continue
        target_path = os.path.join(RES, f"values-{args.locale}", file_name)
        os.makedirs(os.path.dirname(target_path), exist_ok=True)
        block = "\n".join(f'    <string name="{k}">{v}</string>' for k, v in pairs)
        if os.path.exists(target_path):
            body = open(target_path, encoding="utf-8").read()
            if "</resources>" not in body:
                rejected.append(f"{file_name} — لا يوجد </resources>، تُرك كما هو")
                written -= len(pairs)
                continue
            head, _, _tail = body.rpartition("</resources>")
            with open(target_path, "w", encoding="utf-8") as fh:
                fh.write(f"{head.rstrip()}\n{block}\n</resources>\n")
        else:
            with open(target_path, "w", encoding="utf-8") as fh:
                fh.write(
                    '<?xml version="1.0" encoding="utf-8"?>\n'
                    f"<!-- ترجمات مضافة — المصدر: values/{file_name} -->\n<resources>\n{block}\n</resources>\n"
                )
    print(f"صفوف: {len(rows)}  ·  مقبولة: {written}  ·  مرفوضة: {len(rejected)}")
    for r in rejected:
        print("  ", r)
    if args.dry_run:
        print("(--dry-run: لم تُكتب أي ملفات)")
    return 1 if rejected else 0


def prune_locale(locale: str, dry_run: bool) -> tuple[int, list[str]]:
    """يحذف من `values-<locale>/` كل مفتاح لا نظير له في `values/`، ويعيد (العدد، ملاحظات).

    لماذا وُجد: `--apply-csv` **يضيف فقط ولا يحذف شيئًا** — وهذا مبدأ صحيح في مكانه (لا يُعاد
    كتابة ملف قائم فتُفقد تعليقاته وترتيبه)، لكن ثمنه أن إعادة تسمية مفتاح في `values/` تُبقي
    القديم في كل لغة مترجمة إلى الأبد، ولا يراه إلا lint (ExtraTranslation، خطأ قاتل).

    والحذف **سطري** لا بإعادة التسلسل: يُزال السطر الذي يحمل الاسم وحده، فلا يتغيّر تنسيق الملف
    ولا ترتيبه ولا تعليقاته — وهو نفس الأسلوب الذي يكتب به `--apply-csv`. وعنصر لا يُغلق في سطره
    (`</string>` غائب) يُترك ويُذكر صريحًا لئلا يُقطع عنصر متعدّد الأسطر.
    """
    en = english()
    removed = 0
    notes: list[str] = []
    for file_name, en_keys in en.files.items():
        path = os.path.join(RES, f"values-{locale}", file_name)
        if not os.path.exists(path):
            continue
        lines = open(path, encoding="utf-8").read().splitlines(keepends=True)
        stale: list[int] = []
        for index, line in enumerate(lines):
            match = re.match(r'\s*<(?:string|plurals)\s+name="([^"]+)"', line)
            if not match or match.group(1) in en_keys:
                continue
            if not re.search(r"</(?:string|plurals)>|/>", line):
                notes.append(f"{file_name}:{match.group(1)} عنصر متعدّد الأسطر — يُترك للحذف اليدوي")
                continue
            stale.append(index)
        if not stale:
            continue
        removed += len(stale)
        if not dry_run:
            drop = set(stale)
            with open(path, "w", encoding="utf-8") as fh:
                fh.writelines(line for index, line in enumerate(lines) if index not in drop)
    return removed, notes


def cmd_prune(args: argparse.Namespace) -> int:
    targets = locale_folders() if args.prune == "all" else [args.prune]
    total = 0
    for locale in targets:
        if not os.path.isdir(os.path.join(RES, f"values-{locale}")):
            print(f"  لا يوجد مجلد values-{locale}")
            continue
        removed, notes = prune_locale(locale, args.dry_run)
        total += removed
        if removed or notes:
            print(f"values-{locale}: يتيمة محذوفة={removed}")
        for note in notes:
            print("   ", note)
    print(f"لغات مفحوصة: {len(targets)}  ·  مفاتيح يتيمة: {total}")
    if args.dry_run:
        print("(--dry-run: لم تُكتب أي ملفات)")
    return 0


def cmd_check_codes(args: argparse.Namespace) -> int:
    """يطابق ثلاثة أشياء يجب ألا تتباعد: مجلدات `values-*`، المنتقي في الكوتلن، و`locales_config`."""
    expected = {"en"} | {folder_to_tag(f) for f in locale_folders()}  # `en` = `values/` الافتراضية
    codes = set(app_language_codes())
    config = locales_config_tags()
    problems = []
    if codes != expected:
        problems.append(f"المنتقي: ناقص={sorted(expected - codes)} زائد={sorted(codes - expected)}")
    if not config:
        problems.append("res/xml/locales_config.xml غائب — تبديل اللغة يفشل على أندرويد 13+")
    elif set(config) != expected:
        problems.append(f"locales_config: ناقص={sorted(expected - set(config))} زائد={sorted(set(config) - expected)}")
    print(f"مجلدات values-*: {len(expected) - 1} + en  ·  أكواد المنتقي: {len(codes)}  ·  locales_config: {len(config)}")
    for p in problems:
        print("  ", p)
    print("تطابق الأكواد الثلاثة:", "OK" if not problems else "FAIL")
    return 1 if problems else 0


def cmd_assert(args: argparse.Namespace) -> int:
    en = english()
    failures = []
    for folder in locale_folders():
        problems = real_defects(en, load_locale(folder))
        failures.extend(f"{folder}: {p}" for p in problems)
    codes_rc = cmd_check_codes(args)
    print("عوائق (specifiers/تكرار/تطابق الأكواد):", len(failures))
    for f in failures:
        print("  ", f)
    return 1 if (failures or codes_rc != 0) else 0


def main() -> int:
    parser = argparse.ArgumentParser(description="تجميع مفاتيح النصوص وقياس التغطية لكل لغة")
    parser.add_argument("--locale", help="تفصيل لغة واحدة (مقطع المجلد مثل de أو zh-rCN)")
    parser.add_argument("--manifest", metavar="LOCALE", help="كتابة CSV بالمفاتيح المفقودة لغة واحدة")
    parser.add_argument(
        "--todo",
        metavar="LOCALE",
        help="طبع المفاتيح المفقودة إلى المخرجات (لا ملف) — يفيد لأن build/ متجاهَل وغير مرئي للاستكشاف",
    )
    parser.add_argument("--write-manifests", action="store_true", help="كتابة CSV لكل اللغات دفعة واحدة")
    parser.add_argument("--apply-csv", metavar="FILE", help="دمج CSV مُترجم في values-<locale> (يستلزم --locale)")
    parser.add_argument("--dry-run", action="store_true", help="مع --apply-csv: تحقق بلا كتابة")
    parser.add_argument("--check-codes", action="store_true", help="تطابق AppLanguage مع مجلدات values-*")
    parser.add_argument(
        "--prune",
        metavar="LOCALE",
        help="حذف المفاتيح التي لا نظير لها في values/ من لغة واحدة (أو all) — يقبل --dry-run",
    )
    parser.add_argument(
        "--assert", dest="gate", action="store_true", help="ضع بوابة: يخرج بخطأ عند عيب حقيقي فقط"
    )
    parser.add_argument("--limit", type=int, default=25, help="عدد المفاتيح المعروضة في --locale")
    args = parser.parse_args()

    if args.gate:
        return cmd_assert(args)
    if args.check_codes:
        return cmd_check_codes(args)
    if args.prune:
        return cmd_prune(args)
    if args.apply_csv:
        if not args.locale:
            parser.error("--apply-csv يستلزم --locale لتحديد مجلد الهدف")
        return cmd_apply_csv(args)
    if args.write_manifests:
        return cmd_write_manifests(args)
    if args.todo:
        return cmd_todo(args)
    if args.manifest:
        return cmd_manifest(args)
    if args.locale:
        return cmd_locale(args)
    return cmd_report(args)


if __name__ == "__main__":
    # `--todo de | head -200` أمر موصى به في الوصفات، و`head` يغلق الأنبوب. بايثون افتراضيًا
    # يستقبل SIGPIPE كاستثناء فيرمي تتبعًا وينهي بـ120 — تعليمة مقصودة تبدو كعطل.
    # إرجاع السلوك الافتراضي للنظام (الخروج الصامت) هو الصواب هنا. (أُجري له اختبار: exit 0)
    if hasattr(signal, "SIGPIPE"):
        signal.signal(signal.SIGPIPE, signal.SIG_DFL)
    sys.exit(main())
