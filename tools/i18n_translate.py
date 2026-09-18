#!/usr/bin/env python3
"""مشغّل الترجمة الآلية لدفعة كاملة — يملأ الـ٨٤ لغة ويُسلّم إلى الدمج المتحقِّق.

لماذا هذه الأداة: التطبيق يشحن ٦ ملفات نصوص / ٢١٠٦ مفاتيح، والـ٨٤ لغة غير العربية عند ٣٨٠ مفتاحًا
لكل منها (١٤٤٠٤٣ نصًّا ناقصًا). لا أحد يكتبها يدويًا، وهذه الأداة لا تُخفي ذلك: تُرسل النص إلى مزوّد
ترجمة مُعلَن، تُعيده، ثم **لا تكتبه في الموارد** — تُخرجه CSV ويمرّ على `tools/i18n_coverage.py
--apply-csv` الذي يتحقق من الوسائط والتكرار ويمنع الكتابة عند أي عيب. أي أن الأمان في الطبقة الأخيرة
لا هنا، فسوء ترجمة يعطي نصًّا رديئًا، ووسيط تالف يُرفض قبل أن يصل.

مبدأ حماية الوسائط: تُستبدل `%1$s` بأحرف حارسة قبل الإرسال وتُستعاد بعده، فلا يستطيع أي مزوّد أن
يحذفها أو يغيّر ترقيمها أو يقلب `%1$s` إلى `%s`. هذا هو العيب الذي يُسقط التطبيق وقت التشغيل، ويُعالج
هنا في المصدر لا بالمراجعة بعده.

المزوّدات (بالأولوية حسب الجودة للواجهات):
    deepl   — DEEPL_API_KEY            (٥٠٠ ألف حرف/شهر مجانًا، ثم ~٢٥ $/مليون)
    google  — GOOGLE_TRANSLATE_KEY     (٥٠٠ ألف حرف/شهر مجانًا، ثم ~٢٠ $/مليون)
    openai  — MT_API_KEY + MT_BASE_URL + MT_MODEL   (أي واجهة متوافقة مع OpenAI، وتشمل DeepSeek
              ونماذج محلية — وهي الأفضل للمصطلحات الطويلة والسياقية)
    stub    — بلا شبكة: للتحقق من الأنابيب فقط (لا ينتج ترجمة صالحة للنشر)

الاستخدام:
    python3 tools/i18n_translate.py --estimate                     # الأثر والتكلفة بلا أي استدعاء
    python3 tools/i18n_translate.py --provider deepl --locales all
    python3 tools/i18n_translate.py --provider openai --locales ar,de --limit-keys 200
    python3 tools/i18n_coverage.py --locale de --apply-csv build/i18n/translated_de.csv
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from i18n_coverage import (  # noqa: E402  (الأداة الشقيقة: مصدر واحدة للمفاتيح والمجلدات)
    SPECIFIER,
    english,
    load_locale,
    locale_folders,
)

MANIFEST_DIR = os.path.join("build", "i18n")
CACHE_DIR = os.path.join("build", "i18n", "cache")
GLOSSARY = os.path.join("tools", "i18n_glossary.csv")

# ما لا يُترجم أبدًا: أسماء حزم/أعلام/وحدات تُفهم كما هي. تُحمى قبل الإرسال مثل الوسائط.
DO_NOT_TRANSLATE = [
    "MaxManager", "Max AI", "KernelSU", "Magisk", "AOSP", "IRQ", "CPU", "GPU", "RAM", "ZRAM",
    "MB", "GB", "KB", "MHz", "GHz", "mA", "mV", "°C", "ID", "APK", "ADB", "JSON", "URL",
]

SENTINEL_OPEN = "␟"  # U+241F: لا يظهر في نصوص التطبيق ولا يستخدمه أي مزوّد ترجمة
SENTINEL_CLOSE = "␞"


def protect(text: str, keep: list[str]) -> tuple[str, list[str]]:
    """يستبدل الوسائط والمصطلحات التي لا تُترجم بأحرف حارسة مرقّمة، بالترتيب."""
    tokens: list[str] = []

    def swap(match: re.Match[str]) -> str:
        tokens.append(match.group(0))
        return f"{SENTINEL_OPEN}{len(tokens) - 1}{SENTINEL_CLOSE}"

    guarded = SPECIFIER.sub(swap, text)
    for term in keep:
        guarded = re.sub(rf"\b{re.escape(term)}\b", swap, guarded)
    return guarded, tokens


def restore(text: str, tokens: list[str]) -> str:
    for index, token in enumerate(tokens):
        text = text.replace(f"{SENTINEL_OPEN}{index}{SENTINEL_CLOSE}", token)
    return text


def protected_ok(source: str, translation: str) -> bool:
    """يرفض الوسيط الذي يطلب ما لا يمرّره الكود (نفس قاعدة البوابة)."""
    return not (set(SPECIFIER.findall(translation)) - set(SPECIFIER.findall(source)))


class Provider:
    """واجهة واحدة، ثلاث مزوّدات. كل مزوّد يُترجم **دفعة** نصوص إلى لغة واحدة."""

    def __init__(self, name: str, model: str = "") -> None:
        self.name = name
        self.model = model

    def translate(self, texts: list[str], target: str) -> list[str]:
        raise NotImplementedError

    # ── HTTP ──
    @staticmethod
    def _post(url: str, body: object, headers: dict[str, str]) -> dict:
        request = urllib.request.Request(
            url, data=json.dumps(body).encode("utf-8"), headers={**headers, "Content-Type": "application/json"}
        )
        for attempt in range(4):
            try:
                with urllib.request.urlopen(request, timeout=120) as response:
                    return json.loads(response.read().decode("utf-8"))
            except urllib.error.HTTPError as error:
                if error.code in (429, 500, 502, 503) and attempt < 3:
                    time.sleep(2**attempt * 2)
                    continue
                raise RuntimeError(f"HTTP {error.code}: {error.read()[:300]!r}") from error
        raise RuntimeError("unreachable")


class DeepL(Provider):
    def __init__(self) -> None:
        super().__init__("deepl")
        self.key = os.environ.get("DEEPL_API_KEY", "")
        if not self.key:
            raise SystemExit("DEEPL_API_KEY غير مضبوط")
        # المفتاح المجاني ينتهي بـ:fx ويستخدم مضيفًا آخر
        self.host = "api-free.deepl.com" if self.key.endswith(":fx") else "api.deepl.com"

    def translate(self, texts: list[str], target: str) -> list[str]:
        request = urllib.request.Request(
            f"https://{self.host}/v2/translate",
            data=json.dumps(
                {"text": texts, "target_lang": target.upper().split("-")[0], "preserve_formatting": True}
            ).encode("utf-8"),
            headers={"Authorization": f"DeepL-Auth-Key {self.key}", "Content-Type": "application/json"},
        )
        with urllib.request.urlopen(request, timeout=180) as response:
            payload = json.loads(response.read().decode("utf-8"))
        return [item["text"] for item in payload["translations"]]


class Google(Provider):
    def __init__(self) -> None:
        super().__init__("google")
        self.key = os.environ.get("GOOGLE_TRANSLATE_KEY", "")
        if not self.key:
            raise SystemExit("GOOGLE_TRANSLATE_KEY غير مضبوط")

    def translate(self, texts: list[str], target: str) -> list[str]:
        payload = self._post(
            f"https://translation.googleapis.com/language/translate/v2?key={self.key}",
            {"q": texts, "target": target.split("-")[0], "format": "text"},
            {},
        )
        return [item["translatedText"] for item in payload["data"]["translations"]]


class OpenAiCompatible(Provider):
    """أي واجهة `/chat/completions`: OpenAI، DeepSeek، Groq، OpenRouter، أو نموذج محلي."""

    def __init__(self, model: str) -> None:
        super().__init__("openai", model)
        self.key = os.environ.get("MT_API_KEY", "")
        self.base = os.environ.get("MT_BASE_URL", "https://api.openai.com/v1").rstrip("/")
        if not self.key:
            raise SystemExit("MT_API_KEY غير مضبوط")

    def translate(self, texts: list[str], target: str) -> list[str]:
        numbered = "\n".join(f"{i}\t{t}" for i, t in enumerate(texts))
        prompt = (
            "You translate Android app UI strings. Reply with the same numbered lines and nothing else.\n"
            f"Target language: {target}. Keep every placeholder token (␟0␞, ␟1␞ …) byte-for-byte and in place.\n"
            "Keep it short like a UI label: no explanations, no quotes around the result, no extra words.\n"
            f"Lines:\n{numbered}"
        )
        payload = self._post(
            f"{self.base}/chat/completions",
            {"model": self.model, "messages": [{"role": "user", "content": prompt}], "temperature": 0},
            {"Authorization": f"Bearer {self.key}"},
        )
        content = payload["choices"][0]["message"]["content"]
        out: dict[int, str] = {}
        for line in content.splitlines():
            match = re.match(r"^\s*(\d+)\s*[\t:.]\s*(.*)$", line)
            if match:
                out[int(match.group(1))] = match.group(2).strip()
        if len(out) != len(texts):
            raise RuntimeError(f"رد غير مكتمل: {len(out)}/{len(texts)} سطرًا")
        return [out[i] for i in range(len(texts))]


class Stub(Provider):
    """بلا شبكة — للتحقق من الأنابيب. نتيجته معلَّمة ولا تصلح للنشر."""

    def __init__(self) -> None:
        super().__init__("stub")

    def translate(self, texts: list[str], target: str) -> list[str]:
        return [f"[{target}] {text}" for text in texts]


def build_provider(name: str, model: str) -> Provider:
    return {
        "deepl": lambda: DeepL(),
        "google": lambda: Google(),
        "openai": lambda: OpenAiCompatible(model),
        "stub": lambda: Stub(),
    }[name]()


def glossary_terms() -> list[str]:
    """مصطلحات لا تُترجم، من ملف يحرّره المستخدم بلا لمس الكود.

    صف واحد لكل مصطلح في `tools/i18n_glossary.csv` (عمود `term`)، مثل `KernelSU` أو `Max AI`.
    البديل اللاحق الممكن: معجم لكل لغة (مصطلح ← ترجمة معتمدة)، وهذا يحتاج ملفًا لكل لغة.
    """
    if not os.path.exists(GLOSSARY):
        return []
    rows = csv.DictReader(open(GLOSSARY, encoding="utf-8"))
    return [row["term"].strip() for row in rows if (row.get("term") or "").strip()]


def pending(locale: str, limit: int) -> list[dict[str, str]]:
    en = english()
    target = load_locale(locale)
    rows: list[dict[str, str]] = []
    for file_name, keys in en.files.items():
        have = target.keys(file_name)
        for key, text in keys.items():
            if key in have:
                continue
            rows.append({"file": f"values/{file_name}", "key": key, "source_en": text})
            if limit and len(rows) >= limit:
                return rows
    return rows


def cache_path(locale: str) -> str:
    return os.path.join(CACHE_DIR, f"{locale}.json")


def load_cache(locale: str) -> dict[str, str]:
    path = cache_path(locale)
    if not os.path.exists(path):
        return {}
    return json.load(open(path, encoding="utf-8"))


def save_cache(locale: str, cache: dict[str, str]) -> None:
    os.makedirs(CACHE_DIR, exist_ok=True)
    json.dump(cache, open(cache_path(locale), "w", encoding="utf-8"), ensure_ascii=False, indent=0)


def cache_key(file_name: str, key: str, source: str) -> str:
    return f"{file_name}|{key}|{hashlib.sha1(source.encode('utf-8')).hexdigest()[:12]}"


def target_code(locale: str) -> str:
    """Crowdin/المزوّدون يقبلون الوسم الحديث؛ المجلدات القديمة تُحوّل قبل الإرسال."""
    from i18n_coverage import LEGACY

    tag = locale.replace("b+", "").replace("+", "-").replace("-r", "-")
    return LEGACY.get(tag, tag)


def run_locale(provider: Provider, locale: str, batch: int, limit: int, keep: list[str]) -> dict[str, int]:
    rows = pending(locale, limit)
    cache = load_cache(locale)
    translated: dict[str, str] = {}
    rejected = 0
    chars = 0
    todo: list[dict[str, str]] = []
    for row in rows:
        cached = cache.get(cache_key(row["file"], row["key"], row["source_en"]))
        if cached:
            translated[row["key"]] = cached
        else:
            todo.append(row)

    for start in range(0, len(todo), batch):
        chunk = todo[start : start + batch]
        guarded: list[str] = []
        tokens: list[list[str]] = []
        for row in chunk:
            text, found = protect(row["source_en"], keep)
            guarded.append(text)
            tokens.append(found)
        chars += sum(len(g) for g in guarded)
        try:
            out = provider.translate(guarded, target_code(locale))
        except Exception as error:  # مزوّد رفض: لا نكتب شيئًا ونكمل الدفعة التالية
            print(f"  ! {locale} دفعة {start // batch}: {error}")
            rejected += len(chunk)
            continue
        for row, text, found in zip(chunk, out, tokens):
            value = restore(text, found)
            if not protected_ok(row["source_en"], value):
                rejected += 1
                continue
            translated[row["key"]] = value
            cache[cache_key(row["file"], row["key"], row["source_en"])] = value

    save_cache(locale, cache)
    # ملف مخرج مختلف لمزوّد الاختبار: أخطر ما يمكن أن يحدث هنا أن يُدمج مخرج `stub` في الموارد لأن اسمه
    # يبدو كدفعة حقيقية. الفصل في الاسم يجعل الخطأ مستحيلًا لا مجرد مذكور في تنبيه.
    prefix = "STUB_" if provider.name == "stub" else "translated_"
    out_path = os.path.join(MANIFEST_DIR, f"{prefix}{locale.replace('+', '_')}.csv")
    os.makedirs(MANIFEST_DIR, exist_ok=True)
    with open(out_path, "w", encoding="utf-8", newline="") as fh:
        writer = csv.writer(fh)
        writer.writerow(["file", "key", "source_en", "translation"])
        for row in rows:
            writer.writerow([row["file"], row["key"], row["source_en"], translated.get(row["key"], "")])
    print(
        f"{locale:<10}{len(translated):>6} مترجمًا / {len(rows):>6} مطلوبًا"
        f"{f'  · مرفوض: {rejected}' if rejected else ''}  → {out_path}"
    )
    return {"translated": len(translated), "requested": len(rows), "chars": chars, "rejected": rejected}


def cmd_estimate(args: argparse.Namespace) -> int:
    en = english()
    locales = locale_folders() if args.locales == "all" else args.locales.split(",")
    total_chars = 0
    total_strings = 0
    per_locale: list[tuple[str, int, int]] = []
    for locale in locales:
        target = load_locale(locale)
        chars = strings = 0
        for file_name, keys in en.files.items():
            have = target.keys(file_name)
            for key, text in keys.items():
                if key in have:
                    continue
                strings += 1
                chars += len(text)
        per_locale.append((locale, strings, chars))
        total_chars += chars
        total_strings += strings
    print(f"{len(locales)} لغة · {total_strings} نصًّا · {total_chars} حرفًا مصدرًا\n")
    print("التكلفة التقديرية بعد الحصة المجانية (500 ألف حرف/شهر لكل من DeepL وGoogle):")
    for label, per_million in (("DeepL Pro", 25.0), ("Google NMT", 20.0)):
        billable = max(0, total_chars - 500_000)
        print(f"  {label:<12}{billable / 1_000_000 * per_million:>8.2f} $ لو دُفع في جلسة واحدة")
    months = -(-total_chars // 500_000)
    print(f"  بلا دفع: {months} شهرًا على الحصة المجانية (الأداة تستكمل من الذاكرة المؤقتة بلا إعادة فاتورة)")
    # العرض حسب الحجم لا حسب ترتيب أبجدي: أكثر اللغات متساوية في النقص، والعربية وحدها مختلفة — وتمييز ذلك
    # هو ما يُخبر أي لغة تُنفّذ أولًا إن كان الرصيد محدودًا.
    buckets: dict[tuple[int, int], list[str]] = {}
    for locale, strings, chars in per_locale:
        buckets.setdefault((strings, chars), []).append(locale)
    print("\nالتوزيع:")
    for (strings, chars), locales in sorted(buckets.items(), key=lambda item: -item[0][1]):
        sample = ", ".join(locales[:6]) + ("…" if len(locales) > 6 else "")
        print(f"  {len(locales)} لغة × {strings} نصًّا / {chars} حرفًا  ({sample})")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="مشغّل الترجمة الآلية لكل اللغات")
    parser.add_argument("--provider", default=os.environ.get("MT_PROVIDER", "stub"),
                        choices=["deepl", "google", "openai", "stub"])
    parser.add_argument("--model", default=os.environ.get("MT_MODEL", "gpt-4o-mini"),
                        help="اسم النموذج لمزوّد openai")
    parser.add_argument("--locales", default="all", help="all أو قائمة مثل ar,de,fr")
    parser.add_argument("--batch", type=int, default=25, help="عدد النصوص في الطلب الواحد")
    parser.add_argument("--limit-keys", type=int, default=0, help="سقف نصوص لكل لغة (٠ = بلا سقف)")
    parser.add_argument("--estimate", action="store_true", help="عرض الأثر والتكلفة بلا أي استدعاء شبكة")
    args = parser.parse_args()

    if args.estimate:
        return cmd_estimate(args)
    if args.provider == "stub":
        print("تنبيه: مزوّد stub لا يُترجم — مخرجاته معلَّمة وللاختبار فقط. لا تمرّرها إلى --apply-csv.\n")

    provider = build_provider(args.provider, args.model)
    keep = DO_NOT_TRANSLATE + glossary_terms()
    locales = locale_folders() if args.locales == "all" else args.locales.split(",")
    totals = {"translated": 0, "requested": 0, "chars": 0, "rejected": 0}
    for locale in locales:
        for name, value in run_locale(provider, locale, args.batch, args.limit_keys, keep).items():
            totals[name] += value
    print(
        f"\nالإجمالي: {totals['translated']} مترجمًا / {totals['requested']} مطلوبًا"
        f" · {totals['chars']} حرفًا مُرسلًا · مرفوض: {totals['rejected']}"
    )
    if args.provider == "stub":
        print("\nمخرجات stub كُتبت باسم STUB_* ولا يجوز دمجه في الموارد.")
        return 0
    print("الخطوة التالية لكل لغة:  python3 tools/i18n_coverage.py --locale <locale> --apply-csv build/i18n/translated_<locale>.csv")
    return 0


if __name__ == "__main__":
    sys.exit(main())
