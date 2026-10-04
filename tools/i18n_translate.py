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
    gtx     — **بلا مفتاح**: نقطة Google العمومية (`client=gtx`) — المسار المعتمد في «زامن»
    deepl   — DEEPL_API_KEY            (٥٠٠ ألف حرف/شهر مجانًا، ثم ~٢٥ $/مليون)
    google  — GOOGLE_TRANSLATE_KEY     (٥٠٠ ألف حرف/شهر مجانًا، ثم ~٢٠ $/مليون)
    openai  — MT_API_KEY + MT_BASE_URL + MT_MODEL   (أي واجهة متوافقة مع OpenAI، وتشمل DeepSeek
              ونماذج محلية — وهي الأفضل للمصطلحات الطويلة والسياقية)
    stub    — بلا شبكة: للتحقق من الأنابيب فقط (لا ينتج ترجمة صالحة للنشر)

الاستخدام:
    python3 tools/i18n_translate.py --estimate                     # الأثر والتكلفة بلا أي استدعاء
    python3 tools/i18n_translate.py --provider gtx --locales all   # «زامن»: بلا مفتاح
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
import urllib.parse
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
    """يرفض الترجمة التالفة في **الاتجاهين معًا** — لا في اتجاه واحد.

    كان الفحص `translation - source` وحده (وسيط زائد)، وهو يُسقط التطبيق فعلًا؛ لكنه
    **أعمى عن الاتجاه الآخر**: مزوّد يحذف الحارس `␟3␞` لا يُنتج وسيطًا زائدًا، فيمرّ
    الاختبار وتصل الواجهة نصًّا ناقصًا بلا أن يسقط شيء. وقِيس هذا في الشجرة: **٦٠ قيمة
    في الـ٨٤ لغة تُسقط وسيطًا** — إحداها عربية، وأخرى صارت `%2$s` فيها حرفيًّا `«1»`.

    فالقاعدة الآن: مجموعة الوسائط في الترجمة **تساوي** مجموعة وسائط الأصل. والحذف عطب
    وليس اختيارًا، لأنّ كل وسيط يحمل قيمة تُعرض للمستخدم.
    """
    return set(SPECIFIER.findall(translation)) == set(SPECIFIER.findall(source))


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


class Gtx(Provider):
    """نقطة Google العمومية **بلا مفتاح** (`client=gtx`) — مسار «زامن» المُوثّق.

    **ولماذا عادت إلى `tools/`:** كانت `build/i18n/gtx_fill.py`، و`build/` متجاهَل في `.gitignore`
    فحُذفت مع اللقطة (تكملة ٨٥) وضاع المسار الذي تُملأ به الـ٨٣ لغة — وبقيت الإشارة إليه في
    `AGENTS.md` §0.2 وفي `HANDOFF`. فهي هنا حيث لا تُحذف.

    وثلاثة أشياء قِيست قبل كتابتها:
    ① **‏GET لا POST**: الـPOST على النقطة نفسها ردّ `429`، والـGET ردّ `200`.
    ② **شكل الردّ اثنان**: `["نصّ"]` لنصّ واحد و`[["أ","ب","ج"]]` لدفعة — فالتسطيح يجب أن
       يقبل الشكلين، وإلا انكسر العدّ في نصف الحالات.
    ③ **الأحرف الحارسة تعبر سليمة**: `␟0␞ activities` عادت `␟0␞ Aktivitäten` — أي أن حماية
       الوسائط التي في هذا الملفّ تعمل مع هذا المزوّد كما تعمل مع غيره.

    **وحدّه مُعلَن:** نقطة عمومية بلا اتفاق خدمة ولا سقف موثَّق، فالتقسيم والتأخير هنا
    للالتزام الأدبي ومنع الـ`429` — لا لأن أحدًا ضمن لنا ذلك.
    """

    ENDPOINT = "https://translate.googleapis.com/translate_a/t"
    # ‏حدّ الرابط المقصود: رابط الـGET يمرّ في الرؤوس، وتجهيزه طويل يقصّه الخادم بلا رسالة مفهومة.
    MAX_URL = 7000
    MAX_ITEMS = 150

    def __init__(self) -> None:
        super().__init__("gtx")

    @classmethod
    def chunk(cls, texts: list[str]) -> list[list[str]]:
        """تقسيم ثانٍ بحسب **طول الرابط المرمَّز** لا العدد وحده — نصّ طويل واحد قد يقصّه الخادم."""
        chunks: list[list[str]] = []
        current: list[str] = []
        size = len(cls.ENDPOINT) + 120
        for text in texts:
            cost = len(urllib.parse.quote(text)) + 4
            if current and (size + cost > cls.MAX_URL or len(current) >= cls.MAX_ITEMS):
                chunks.append(current)
                current, size = [], len(cls.ENDPOINT) + 120
            current.append(text)
            size += cost
        if current:
            chunks.append(current)
        return chunks

    @staticmethod
    def flatten(payload: object) -> list[str]:
        """يُسطّح الشكلين المقيسين، وكل ما يظهر من تداخل بينهما."""
        flat: list[str] = []

        def walk(node: object) -> None:
            if isinstance(node, str):
                flat.append(node)
            elif isinstance(node, list):
                for child in node:
                    walk(child)

        walk(payload)
        return flat

    def _request(self, chunk: list[str], target: str) -> list[str]:
        params: list[tuple[str, str]] = [
            ("client", "gtx"), ("sl", "en"), ("tl", target.split("-")[0]), ("format", "text")
        ]
        params += [("q", text) for text in chunk]
        url = f"{self.ENDPOINT}?{urllib.parse.urlencode(params)}"
        for attempt in range(4):
            try:
                with urllib.request.urlopen(url, timeout=60) as response:
                    return self.flatten(json.loads(response.read().decode("utf-8")))
            except urllib.error.HTTPError as error:
                if error.code in (429, 500, 502, 503) and attempt < 3:
                    time.sleep(3 * 2**attempt)
                    continue
                raise RuntimeError(f"HTTP {error.code}") from error
        raise RuntimeError("unreachable")

    def translate(self, texts: list[str], target: str) -> list[str]:
        out: list[str] = []
        for index, chunk in enumerate(self.chunk(texts)):
            if index:
                time.sleep(0.7)  # فاصل بين الطلبات: النقطة عمومية، والضغط بلا فاصل يُنتج 429
            got = self._request(chunk, target)
            if len(got) != len(chunk):
                # **ولا تُهدَر الدفعة كلها من أجل نصّ واحد:** الذين عادوا سليمين يُستعملون،
                # والشاذّ يُعاد **نصًّا نصًّا** (وكلفته رابط واحد لكل نصّ، لا لكل دفعة).
                # وهذا مقيس: الردّ المتداخل يُفسد العدّ، ورميه كاملًا يضيّع ١٤٩ ترجمة صحيحة.
                out.extend(self._one_by_one(chunk, target))
                continue
            out.extend(got)
        return out

    def _one_by_one(self, chunk: list[str], target: str) -> list[str]:
        singles: list[str] = []
        for text in chunk:
            time.sleep(0.4)
            got = self._request([text], target)
            singles.append(got[0] if len(got) == 1 else "")
        return singles


def build_provider(name: str, model: str) -> Provider:
    return {
        "gtx": lambda: Gtx(),
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


TAG_BOUND = re.compile(rf"{SENTINEL_OPEN}\d+{SENTINEL_CLOSE}")


def segment_rows(provider: Provider, rows: list[dict[str, str]], keep: list[str], target: str) -> dict[str, str]:
    """مسار احتياطي: يُترجم **المقاطع الحرفية** حول الحرّاس، ويُعيد الحرّاس بيده.

    **ولماذا هذا وكيف قِيس:** حماية الوسائط في هذا الملفّ تُرسل الحارس `␟3␞` إلى المزوّد
    وتستعيده بعده — وهي كافية في الغالب لا دائمًا: قِيس أنّ ٢٤ نصًّا من ٥٩ أعادها المزوّد
    **بلا حارس** (يحذف الجملة مع الحارس، أو يقصّ النصّ الطويل ذا السبعة وسائط)، فترفضه
    `protected_ok` — وتُرفض معه الترجمة كلها.

    **والحلّ بنيوي لا تجميليّ:** لا يُرسل الحارس أصلًا. يُشقّ النصّ على الحرّاس، وتُترجم
    المقاطع الحرفية وحدها في دفعة واحدة، ثم **يُعاد التجميع عندنا**. فالوسيط لا يمكن أن
    يُسقط لأنّ مزوّدًا لم يحمله، ولا أن يتغيّر ترقيمه.

    **وثمنه مُعلَن:** المقاطع تُترجم بلا سياق الجملة الكاملة، فقد تقلّ سلامتها اللغوية عن
    الترجمة الكاملة. فالسقوط إلى هذا المسار **محدود بما فشل**، وليس هو المسار الأول.
    """
    if not rows:
        return {}
    # كل مقطع: هل يُترجم أصلاً؟ وما المسافة المحيطة التي يجب أن تعود كما هي؟
    # **وهذا مقيس لا احتياط:** أوّل تشغيل أعطى `%1$dMB%2$s·Swappiness` بلا مسافات —
    # لأنّ المزوّد **يقصّ كل مقطع** على حدوده. فالحدود تُحفظ هنا وتُعاد بعد الترجمة،
    # والمقطع الذي لا حرف فيه (فاصل · أو شرطة أو رقم) لا يُرسل أصلاً.
    pieces: list[str] = []
    layouts: list[list[tuple[bool, bool, str, str, str]]] = []
    token_sets: list[list[str]] = []
    for row in rows:
        guarded, tokens = protect(row["source_en"], keep)
        token_sets.append(tokens)
        parts = [p for p in re.split(rf"({SENTINEL_OPEN}\d+{SENTINEL_CLOSE})", guarded) if p]
        layout: list[tuple[bool, bool, str, str, str]] = []
        for part in parts:
            if TAG_BOUND.fullmatch(part):
                layout.append((True, False, part, "", ""))
                continue
            lead = part[: len(part) - len(part.lstrip())]
            trail = part[len(part.rstrip()) :]
            core = part.strip()
            # **والمقطع الذي لا يُترجم يُعاد كما هو لا مُفكَّكًا:** أوّل نسخة أعادت
            # `lead + core + trail`، فمقطع من مسافتين (`MB` محميٌّ بوسيط فبقي حوله فراغ)
            # صار أربع مسافات — والخروج `%1$d  MB` بمسافتين. وهو نفس درس
            # `bundle_contract`: البناء من الأجزاء يضيف حيث يُعاد الحدّ مرّتين.
            trans = bool(core) and any(ch.isalpha() for ch in core)
            layout.append((False, True, core, lead, trail) if trans else (False, False, part, "", ""))
            if trans:
                pieces.append(core)
        layouts.append(layout)
    out_texts = provider.translate(pieces, target) if pieces else []
    if len(out_texts) != len(pieces):
        raise RuntimeError(f"رد مجزّأ غير مكتمل: {len(out_texts)}/{len(pieces)}")
    stream = iter(out_texts)
    values: dict[str, str] = {}
    for row, layout, tokens in zip(rows, layouts, token_sets):
        joined = "".join(
            part if (is_tag or not trans) else f"{lead}{next(stream)}{trail}"
            for is_tag, trans, part, lead, trail in layout
        )
        values[row["key"]] = restore(joined, tokens)
    return values


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
    broken: list[dict[str, str]] = []  # ما لم يتجاوز حماية الوسائط في المحاولة الأولى
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
        # **ودفعة أقصر من المطلوب تُرفض كاملة** — قبل ذلك كان `zip` يقصّها بصمت، فيُسجَّل
        # نصر على مفتاح لم يُترجم ويُذهب مفتاح آخر في التقرير. (وهو خطأ الصنف نفسه الذي
        # منعه `--apply-csv`: لا تُكتب قيمة لم تُتحقّق.)
        if len(out) != len(chunk):
            print(f"  ! {locale} دفعة {start // batch}: ردّ {len(out)} مقابل {len(chunk)} — رُفضت")
            broken.extend(chunk)
            continue
        for row, text, found in zip(chunk, out, tokens):
            value = restore(text, found)
            if not value.strip() or not protected_ok(row["source_en"], value):
                broken.append(row)
                continue
            translated[row["key"]] = value
            cache[cache_key(row["file"], row["key"], row["source_en"])] = value

    # والمسار الاحتياطي: ما أسقط مزوّده حارسًا يُترجم **مجزّأً** فلا يُمكن إسقاط الوسيط.
    for start in range(0, len(broken), batch):
        chunk = broken[start : start + batch]
        try:
            values = segment_rows(provider, chunk, keep, target_code(locale))
        except Exception as error:
            print(f"  ! {locale} تجزئة {start // batch}: {error}")
            rejected += len(chunk)
            continue
        for row in chunk:
            value = values.get(row["key"], "")
            if not value.strip() or not protected_ok(row["source_en"], value):
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
                        choices=["gtx", "deepl", "google", "openai", "stub"])
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
