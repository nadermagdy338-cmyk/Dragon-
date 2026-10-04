#!/usr/bin/env python3
"""معرض اللقطات في الـREADME — مولَّد من الملفّات الموجودة، فلا صورة مكسورة أبدًا.

**لماذا أداة لا بلاطات تُكتب بيد.** الصفحة التي طُلب تقليد أسلوبها تكتب كلّ إطار بيد داخل
`<p align="center">`، وفيها ثلاثة أعطاب **مقيسة** في ملفّها المرجعي: التسمية مكتوبة مرّتين
(الصورة والوصف) فينزاح أحدهما عن الآخر، والصفوف بأطوال مختلفة (٢/٣/٣/٢/٣) بلا نظام، وكل
لقطةٍ مفتوحةٌ في الصفحة فيطول الـREADME بمقدار ما يزيد عدد الصور. وأظهر من ذلك: `README` هناك
يقول «إسقاط إطار بالاسم الصحيح يجعله يظهر بلا تعديل» — ولا آلية تُحقّق ذلك، فالاسم يُكتب مرّة
ثانية بيد، وخطأ حرف فيه يعني إطارًا لا يظهر أبدًا بلا أن يسقط شيء.

فهذه الأداة تقلب الطرفين: **الشجرة تقول ما يوجد، والصفحة تُولَّد منه.** ولا يُكتب `<img>` إلا
لما وُجد:

  ① **لا صورة مكسورة**: الإطار يُرسم فقط إذا كان `<stem>.png` موجودًا في `docs/screenshots/`.
  ② **شبكة مقتَّنة**: `columns` عمودًا ثابتًا و`thumb_width` واحدًا، والصفّ الأخير يُكمَّل بخلايا
     فارغة فيبقى التراصف قائمًا مهما بلغ عدد الإطارات (وهذا هو الفرق المقيس عن المرجع).
  ③ **لا ازدحام**: المجموعات المفتوحة تُعلَن في العقد (`open: true`) والباقي مطويّ في `<details>`،
     والتسمية داخل خليّتها لا في سطر مشترك — فلا تُفسد لقطة جديدة تراصف الصفوف ولا يطول الـREADME.
     **واليوم مفتوحتان اثنتان بأمر المالك** («الرئيسية و Max AI ثمّ التحكّم لكل تطبيق… والباقي
     مطويّ»): هما أوّل ما يمرّ عليه الزائر في الرحلة، وما عداهما يُفتح بلمسة. والقاعدةُ التي
     تحكمها ليست «واحدة»، بل **أن يكون المفتوح قليلًا ومعروفًا**؛ والعدد لا يُكتب في شيفرة
     المولِّد أصلًا — يُقرأ من العقد، فلا ينكسر إن تغيّر مرة أخرى.
  ④ **العقد يُقاس لا يُصدَّق**: كل `route` يُقابل على `MaxDestinations.kt` (ويقبل السابقة
     `app_settings/{pkg}`)، والتسميات تحت `caption_max`، والجذر فريد في العقد كلّه.
  ⑤ **الملفّ نفسه يُقاس**: PNG سليم (توقيع + IHDR + IDAT + IEND + CRC لكل مقطع)، ونسبته نسبة
     الشاشة المُعلَنة، وحجمه تحت السقف — وملفّ باسم لا يذكره العقد **يُبلَّغ عنه** بدل أن يختفي
     بصمت (وهو العطب الذي يحذّر منه عقد الأسماء نفسه).
  ⑥ **لا يد على المولَّد**: الكتلة بين `screenshots:start` و`screenshots:end` تُقارن بالمولَّد
     حرفيًّا؛ فتعديلها بيد يسقط البوّابة بدل أن يتقادم المعرض بصمت.

وحدّها: الأداة **لا ترى الصور** — تقيس وجودها وأبعادها وحجمها وصحّة ملفّها، ولا تقول إن اللقطة
تُظهر فعلًا ما يعد به وصفها. وذاك يبقى لأوّل من يفتح الصفحة على GitHub، ولا تدّعيه الأداة.

الاستعمال:  python3 tools/screenshot_gallery.py --write · --assert · --self-test · --list
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import sys
import tempfile
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

CONTRACT_REL = "docs/screenshots/gallery.json"
SHOTS_REL = "docs/screenshots"
CAPTURE_PAGE = "docs/screenshots/README.md"
NAV_REL = "manager/app/src/main/java/nd/max/ui/navigation/MaxDestinations.kt"
READMES = (("README.md", "en"), ("README.ar.md", "ar"))

CONTRACT_PAGE_LINK = f'<a href="{CAPTURE_PAGE}">'

BEGIN = "<!-- screenshots:start -->"
END = "<!-- screenshots:end -->"

PNG_MAGIC = b"\x89PNG\r\n\x1a\n"
ROUTE_RE = re.compile(r'MaxDestination\("([^"]+)"')
STEM_RE = re.compile(r"^\d{2}-[a-z0-9-]+$")

# تسمية كل متغيّر في الصفحتين. والمتغيّر الذي لا تُعرف تسميته في هذه الخريطة **يُبلَّغ عنه**
# في العقد بدل أن يُرسم بلا علامة: العقد ينمو، والخريطة تُحدَّث معه لا بعده.
VARIANT_LABEL = {
    "light": {"en": "Light", "ar": "فاتح"},
    "ar": {"en": "AR", "ar": "عربي"},
}

WORDS = {
    "en": {"link": "capture contract"},
    "ar": {"link": "عقد الالتقاط"},
}


def frames_label(count: int, lang: str) -> str:
    """`١ إطارات` تُقرأ خطأً، والتسمية جزء من الصفحة لا زينة فيها."""
    if lang == "en":
        return f"{count} frame" if count == 1 else f"{count} frames"
    number = localised_number(count, "ar")
    if count == 1:
        return "إطار واحد"
    if count == 2:
        return "إطاران"
    if 3 <= count <= 10:
        return f"{number} إطارات"
    return f"{number} إطارًا"


# ── أدوات صغيرة ──────────────────────────────────────────────────────────────

def read(path: str) -> str:
    with open(path, encoding="utf-8", errors="replace") as handle:
        return handle.read()


def localised_number(value: int, lang: str) -> str:
    """الأرقام بالأرقام العربيّة-الهنديّة في الصفحة العربيّة، كما في بقيّة الوثائق."""
    text = str(value)
    return text.translate(str.maketrans("0123456789", "٠١٢٣٤٥٦٧٨٩")) if lang == "ar" else text


def png_report(path: str) -> tuple[tuple[int, int] | None, str | None]:
    """يقرأ PNG فعلًا: توقيع، ومقاطع بأطوال صحيحة، وCRC، وIHDR، وبيانات، وخاتمة.

    وليس ترفًا: لقطة نصف مُنزَّلة أو ملفّ غُيّر امتداده إلى `.png` لهما الأثر نفسه على القارئ —
    صورة مكسورة. والتوقيع وحده لا يكفي (٨ بايت يمرّان في ملفّ مبتور).
    """
    with open(path, "rb") as handle:
        data = handle.read()
    if not data.startswith(PNG_MAGIC):
        return None, "ليس PNG (التوقيع غائب)"
    offset, size, kinds = 8, None, []
    while offset + 8 <= len(data):
        length = int.from_bytes(data[offset:offset + 4], "big")
        kind = data[offset + 4:offset + 8]
        body = data[offset + 8:offset + 8 + length]
        if len(body) != length:
            return None, "ملفّ مقطوع (مقطع يعلن طولًا يتجاوز نهاية الملفّ)"
        tail = data[offset + 8 + length:offset + 12 + length]
        if len(tail) != 4:
            return None, "ملفّ مقطوع (لا CRC للمقطع الأخير)"
        if zlib.crc32(kind + body) & 0xFFFFFFFF != int.from_bytes(tail, "big"):
            return None, f"CRC فاسد في المقطع {kind.decode('ascii', 'replace')}"
        kinds.append(kind)
        if kind == b"IHDR":
            if length != 13:
                return None, "IHDR بطول غير 13"
            size = (int.from_bytes(body[0:4], "big"), int.from_bytes(body[4:8], "big"))
        offset += 12 + length
        if kind == b"IEND":
            break
    if size is None:
        return None, "لا مقطع IHDR"
    if b"IDAT" not in kinds:
        return None, "لا بيانات صورة (لا IDAT)"
    if kinds[-1] != b"IEND":
        return None, "لا يُختم الملفّ بـIEND (ملفّ ناقص)"
    return size, None


# ── العقد ────────────────────────────────────────────────────────────────────

def load_contract(root: str) -> tuple[dict | None, list[str]]:
    path = os.path.join(root, CONTRACT_REL)
    if not os.path.exists(path):
        return None, [f"{CONTRACT_REL} مفقود — لا عقد يُقاس ولا معرض يُولَّد"]
    try:
        data = json.loads(read(path))
    except json.JSONDecodeError as exc:
        return None, [f"{CONTRACT_REL} ليس JSON سليمًا: {exc}"]
    return data, validate_contract(root, data)


def declared_names(data: dict) -> set[str]:
    """كل اسم ملفّ يذكره العقد — به تُقاس الملفّات الزائدة عن العقد."""
    variants = data.get("variants") or []
    names = set()
    for group in data.get("groups") or []:
        for frame in group.get("frames") or []:
            stem = frame.get("stem")
            if not isinstance(stem, str):
                continue
            names.add(f"{stem}.png")
            names.update(f"{stem}-{variant}.png" for variant in variants)
    return names


def validate_contract(root: str, data: dict) -> list[str]:
    """يبني العقد ويقيسه: بنيته، وجذوره، ومسارات شاشاته، وطول تسمياته."""
    problems: list[str] = []
    for key in ("columns", "thumb_width", "caption_max"):
        if not isinstance(data.get(key), int) or data.get(key, 0) <= 0:
            problems.append(f"العقد: `{key}` يجب أن يكون عددًا صحيحًا موجبًا")
    expected = data.get("expected")
    if not isinstance(expected, dict) or any(
        not isinstance(expected.get(key), int) for key in ("width", "height", "max_bytes")
    ):
        problems.append("العقد: `expected` يجب أن يحمل width وheight وmax_bytes أعدادًا")
    variants = data.get("variants")
    if not isinstance(variants, list) or not variants:
        problems.append("العقد: `variants` قائمة غير فارغة")
        variants = []
    for variant in variants:
        if variant not in VARIANT_LABEL:
            problems.append(
                f"العقد: المتغيّر '{variant}' لا تسمية له في هذه الأداة — "
                f"الصفحة عربيّة/إنجليزيّة، فالخريطة تُحدَّث مع العقد لا بعده"
            )

    groups = data.get("groups")
    if not isinstance(groups, list) or not groups:
        problems.append("العقد: `groups` قائمة غير فارغة")
        return problems

    patterns = navigation_routes(root)
    if not patterns:
        problems.append(f"{NAV_REL}: لا مسار واحد مقروء منه — فلا مسار يمكن مقابلته")
    caption_max = data.get("caption_max") if isinstance(data.get("caption_max"), int) else 0
    seen_keys: dict[str, int] = {}
    seen_stems: dict[str, str] = {}
    for index, group in enumerate(groups):
        where = f"groups[{index}]"
        if not isinstance(group, dict):
            problems.append(f"{where}: ليس كائنًا")
            continue
        for key in ("key", "en", "ar", "frames"):
            if not group.get(key):
                problems.append(f"{where}: `{key}` مطلوب")
        key = group.get("key")
        if isinstance(key, str):
            if key in seen_keys:
                problems.append(f"{where}: مفتاح مجموعة مكرّر '{key}'")
            seen_keys[key] = index
        frames = group.get("frames")
        if not isinstance(frames, list) or not frames:
            problems.append(f"{where}: لا إطارات")
            continue
        for frame_index, frame in enumerate(frames):
            spot = f"{where}.frames[{frame_index}]"
            if not isinstance(frame, dict):
                problems.append(f"{spot}: ليس كائنًا")
                continue
            for field in ("stem", "route", "en", "ar"):
                if not isinstance(frame.get(field), str) or not frame[field].strip():
                    problems.append(f"{spot}: `{field}` مطلوب")
            stem = frame.get("stem")
            if isinstance(stem, str):
                if not STEM_RE.match(stem):
                    problems.append(f"{spot}: الاسم '{stem}' خارج الصيغة `NN-slug` من حروف لاتينيّة صغيرة")
                if stem in seen_stems:
                    problems.append(f"{spot}: الجذر '{stem}' مكرّر (في {seen_stems[stem]})")
                seen_stems[stem] = spot
            route = frame.get("route")
            if isinstance(route, str) and patterns and not route_ok(route, patterns):
                problems.append(
                    f"{spot}: المسار '{route}' غير موجود في MaxDestinations.kt — "
                    f"العقد يعد بشاشة لا وجود لها"
                )
            for lang in ("en", "ar"):
                caption = frame.get(lang)
                if isinstance(caption, str) and caption_max and len(caption) > caption_max:
                    problems.append(
                        f"{spot}: تسمية {lang} بطول {len(caption)} فوق `caption_max` "
                        f"({caption_max}) — الطول هو ما يمنع التسمية من إفساد تراصف الصفّ"
                    )

    return problems


def navigation_routes(root: str) -> set[str]:
    path = os.path.join(root, NAV_REL)
    if not os.path.exists(path):
        return set()
    return set(ROUTE_RE.findall(read(path)))


def route_ok(route: str, patterns: set[str]) -> bool:
    """المسار المُعلَن يطابق مسارًا في التنقّل، أو سابقةً له مع وسيط (`app_settings/{pkg}`)."""
    if route in patterns:
        return True
    return any(p.startswith(route + "/") or p.startswith(route + "?") for p in patterns)


# ── التوليد ──────────────────────────────────────────────────────────────────

def shots_dir(root: str) -> str:
    return os.path.join(root, SHOTS_REL)


def existing_names(root: str) -> set[str]:
    directory = shots_dir(root)
    if not os.path.isdir(directory):
        return set()
    return {name for name in os.listdir(directory) if name.lower().endswith(".png")}


def found_files(root: str, stem: str, variants: list[str]) -> dict[str, str]:
    names = existing_names(root)
    found = {key: name for key, name in frame_names(stem, variants).items() if name in names}
    return found if "base" in found else {}


def frame_names(stem: str, variants: list[str]) -> dict[str, str]:
    return {"base": f"{stem}.png", **{v: f"{stem}-{v}.png" for v in variants}}


def cell_html(frame: dict, found: dict, lang: str, thumb: int, width: int, contract_order: list[str]) -> str:
    base = f"{SHOTS_REL}/{found['base']}"
    # والنصّ البديل يحمل اسم التطبيق: صورةٌ يتيمةٌ بنصّ «Home» لا تقول شيئًا لقارئ الشاشة
    alt = f"MaxManager — {frame[lang]}"
    caption = f"<b>{frame[lang]}</b>"
    links = [
        f'<a href="{SHOTS_REL}/{found[variant]}">{VARIANT_LABEL[variant][lang]}</a>'
        for variant in contract_order
        if variant in found
    ]
    if links:
        caption += " · " + " · ".join(links)
    return (
        f'<td align="center" width="{width}%">'
        f'<a href="{base}"><img src="{base}" width="{thumb}" alt="{alt}"></a>'
        f"<br><sub>{caption}</sub></td>"
    )


def table_html(cells: list[str], columns: int) -> str:
    width = round(100 / columns)
    empty = f'<td align="center" width="{width}%"></td>'
    lines = ["<table>"]
    for start in range(0, len(cells), columns):
        row = cells[start:start + columns]
        lines.append("  <tr>")
        lines.extend(f"    {cell}" for cell in row)
        lines.extend(f"    {empty}" for _ in range(columns - len(row)))
        lines.append("  </tr>")
    lines.append("</table>")
    return "\n".join(lines)


def group_html(group: dict, cells: list[str], lang: str, columns: int) -> str:
    title = group[lang]
    summary = f"<b>{title}</b> · {frames_label(len(cells), lang)}"
    opened = " open" if group.get("open") else ""
    return (
        f"<details{opened}>\n<summary>{summary}</summary>\n\n"
        f"{table_html(cells, columns)}\n\n</details>"
    )


def pending_line(lang: str, required: int, extras: int) -> str:
    """ما يُرسم بدل الشبكة ما دام المجلّد فارغًا — صادق، ومُصمَّم لا متروك.

    **ولماذا كتلة اقتباس لا سطر `<sub>` صغير.** هذا هو **الشيء الوحيد** الذي يراه القارئ في قسم
    «كيف يبدو» حتى تُلتقط الإطارات. وكان سطرًا رماديًّا في حجم ٨٠٪ يبدو كأثر نسيان لا كحالة مقصودة،
    فيقرأه الزائر «صفحة ناقصة» بدل «صفحة تقول الحقيقة عن نفسها». والكتلة تُقرأ كملاحظة تحريريّة
    مقصودة: عنوان عريض، ثم ما يجري، ثم أين التفاصيل.

    **ولا صورة هنا عمدًا**، ولا `<img>` ولا `<details>`: قسم يقول «اللقطات قيد الالتقاط» ثم يرسم
    صورة سيكون هو نفسه ما يُنتقد. وهذا مقيس في الفحص الذاتي لا موعودًا.

    **وبلا أيّ رقم لاتينيّ في الصفحة العربيّة** (يقيسه الفحص الذاتي): ولذلك لا يُكتب هنا `<table
    width="100%">` ولا أيّ سمة رقميّة — وسمة من هذا النوع كانت ستُسقط الفحص لأن `١٠٠` أرقامها
    لاتينيّة بينما بقيّة الصفحة عربيّة-هنديّة.
    """
    link = f'{CONTRACT_PAGE_LINK}{WORDS[lang]["link"]}</a>'
    if lang == "en":
        return (
            f"> **Captures pending — {required} frames specified, none captured yet.**\n"
            ">\n"
            "> This grid is generated from the frames that exist in `docs/screenshots/`, so a frame\n"
            "> that has not been captured is simply **absent here — never a broken image**. Names,\n"
            f"> sizes, routes and what each frame shows are in the {link}; the\n"
            f"> {extras} optional extras follow the same rule."
        )
    return (
        f"> **اللقطات قيد الالتقاط — الإطارات {localised_number(required, 'ar')} موصوفة، ولم "
        f"يُلتقط منها شيء بعد، والخيارات الإضافيّة {localised_number(extras, 'ar')}.**\n"
        ">\n"
        "> هذه الشبكة مولَّدة من الإطارات الموجودة في `docs/screenshots/`، فالإطار غير الملتقط\n"
        "> **يغيب من هنا — ولا يظهر كصورة مكسورة**. والأسماء والأبعاد والمسارات وما يُظهره كلّ إطار\n"
        f"> في {link}؛ والخيارات الإضافيّة تتبع القاعدة نفسها."
    )


def captured_line(lang: str, captured: int, required: int, extras: int) -> str:
    """سطر الحالة حين توجد لقطات فعلًا — بنفس شكل كتلة الانتظار، فلا يتبدّل شكل القسم مرّتين."""
    link = f'{CONTRACT_PAGE_LINK}{WORDS[lang]["link"]}</a>'
    if lang == "en":
        return (
            f"> **{captured} of {required} frames captured** · {extras} optional extras. "
            f"A frame not captured yet is absent from the grid above rather than broken; "
            f"the {link} lists all of them."
        )
    return (
        f"> **الملتقط {localised_number(captured, 'ar')} من "
        f"{localised_number(required, 'ar')} إطارًا** · والخيارات الإضافيّة "
        f"{localised_number(extras, 'ar')}. والإطار غير الملتقط يغيب من الشبكة أعلاه ولا يظهر "
        f"مكسورًا؛ و{link} يذكرها كلّها."
    )


def render_region(root: str, data: dict, lang: str) -> str:
    """الكتلة كاملة بين العلامتين — للصفحة الإنجليزيّة أو العربيّة."""
    variants = data.get("variants") or []
    columns = max(1, int(data.get("columns") or 1))
    thumb = int(data.get("thumb_width") or 140)
    width = round(100 / columns)
    groups = data.get("groups") or []
    required = sum(len(g.get("frames") or []) for g in groups if not g.get("optional"))
    extras = sum(len(g.get("frames") or []) for g in groups if g.get("optional"))

    blocks: list[str] = []
    captured = 0
    for group in groups:
        cells = [
            cell_html(frame, found, lang, thumb, width, variants)
            for frame in (group.get("frames") or [])
            if (found := found_files(root, frame["stem"], variants))
        ]
        if not cells:
            continue
        captured += len(cells)
        blocks.append(group_html(group, cells, lang, columns))
    if not blocks:
        return pending_line(lang, required, extras)
    blocks.append(captured_line(lang, captured, required, extras))
    return "\n\n".join(blocks)


# ── الكتلة داخل الـREADME ───────────────────────────────────────────────────

def region_parts(text: str) -> tuple[str, str, str] | None:
    """(قبل، كتلة، بعد) — أو None إن غابت إحدى العلامتين أو تكرّرت."""
    if text.count(BEGIN) != 1 or text.count(END) != 1:
        return None
    start = text.index(BEGIN) + len(BEGIN)
    end = text.index(END)
    if end < start:
        return None
    return text[:start], text[start:end], text[end:]


def splice(text: str, body: str) -> str | None:
    parts = region_parts(text)
    if parts is None:
        return None
    before, _, after = parts
    return f"{before}\n{body}\n{after}"


def check_readme(root: str, readme: str, lang: str, data: dict) -> list[str]:
    path = os.path.join(root, readme)
    if not os.path.exists(path):
        return [f"{readme}: مفقود"]
    text = read(path)
    parts = region_parts(text)
    if parts is None:
        return [
            f"{readme}: علامتا `{BEGIN}` و`{END}` مطلوبتان، كلٌّ منهما مرّة واحدة — "
            f"بلا علامة لا مكان مولَّد يُقاس فيه التصادم"
        ]
    want = render_region(root, data, lang)
    if parts[1].strip("\n") != want.strip("\n"):
        return [
            f"{readme}: كتلة المعرض لا تطابق المولَّد من الشجرة — الأداة تحرس المولَّد، "
            f"فتُكتب من جديد بـ`--write` (المولَّد {len(want)} حرفًا · الموجود {len(parts[1])})"
        ]
    return []


def check_contract_page(root: str) -> list[str]:
    if not os.path.exists(os.path.join(root, CAPTURE_PAGE)):
        return [f"{CAPTURE_PAGE}: مفقود، والكتلة المولَّدة تُشير إليه في الصفحتين"]
    return []


def check_files(root: str, data: dict) -> list[str]:
    """الملفّات الموجودة: صحّة الملفّ، ونسبته، وحجمه — وما لا يذكره العقد يُبلَّغ عنه."""
    problems: list[str] = []
    names = existing_names(root)
    declared = declared_names(data)
    variants = data.get("variants") or []
    expected = data.get("expected") if isinstance(data.get("expected"), dict) else {}
    want_w = expected.get("width") if isinstance(expected.get("width"), int) else 0
    want_h = expected.get("height") if isinstance(expected.get("height"), int) else 0
    cap_bytes = expected.get("max_bytes") if isinstance(expected.get("max_bytes"), int) else 0

    for name in sorted(names - declared):
        problems.append(
            f"{SHOTS_REL}/{name}: ملفّ لا يذكره العقد — اسم مخالف لا يظهر على أيّ صفحة "
            f"(وهو ما يحذّر منه عقد الأسماء نفسه): يُعاد تسميته أو يُدرج في العقد"
        )

    for name in sorted(names):
        path = os.path.join(shots_dir(root), name)
        size, why = png_report(path)
        if size is None:
            problems.append(f"{SHOTS_REL}/{name}: {why}")
            continue
        width, height = size
        if want_w and want_h and height:
            want_ratio, got_ratio = want_w / want_h, width / height
            if abs(got_ratio - want_ratio) > 0.02 * want_ratio:
                problems.append(
                    f"{SHOTS_REL}/{name}: {width}×{height} بنسبة {got_ratio:.3f} تخالف نسبة "
                    f"الشاشة المُعلَنة {want_w}×{want_h} ({want_ratio:.3f}) — الصفوف لا تتراصف"
                )
        actual = os.path.getsize(path)
        if cap_bytes and actual > cap_bytes:
            problems.append(
                f"{SHOTS_REL}/{name}: {actual}B فوق سقف اللقطة ({cap_bytes}B) — "
                f"والـREADME يحملها كلّها"
            )

    # متغيّر بلا أصل: الرابط يظهر والقاعدة لا توجد، فيُبنى عليه رابط مكسور
    for group in data.get("groups") or []:
        for frame in group.get("frames") or []:
            names_for = frame_names(frame["stem"], variants)
            for variant in variants:
                if names_for[variant] in names and names_for["base"] not in names:
                    problems.append(
                        f"{SHOTS_REL}/{names_for[variant]}: لقطة النسخة '{variant}' موجودة والأصل "
                        f"{names_for['base']} غائب — الخليّة لا تُرسم بلا الأصل"
                    )
    return problems


def measure(root: str, data: dict) -> tuple[int, int, list[tuple[str, int, int]]]:
    """(الموجود، المُتوقَّع، لكل مجموعة: المفتاح/الموجود/الكليّ) — للحساب في `--list` والسطور."""
    variants = data.get("variants") or []
    groups: list[tuple[str, int, int]] = []
    found_total = 0
    expected_total = 0
    for group in data.get("groups") or []:
        frames = group.get("frames") or []
        found = sum(1 for frame in frames if found_files(root, frame["stem"], variants))
        groups.append((group.get("key", "?"), found, len(frames)))
        found_total += found
        expected_total += len(frames)
    return found_total, expected_total, groups


def check(root: str, assert_mode: bool) -> int:
    data, problems = load_contract(root)
    if data is None:
        for problem in problems:
            print("  ✗", problem)
        print(f"\nالحصيلة: {len(problems)} عائقًا")
        return 1 if assert_mode else 0

    problems = list(problems)
    if not problems:
        problems += check_contract_page(root)
        problems += check_files(root, data)
        for readme, lang in READMES:
            problems += check_readme(root, readme, lang, data)

    found, expected, groups = measure(root, data)
    print(f"العقد: {len(groups)} مجموعات · {expected} إطارًا موصوفًا · {found} لقطة موجودة")
    for key, got, total in groups:
        mark = "✓" if got == total else ("•" if got else " ")
        print(f"  {mark} {key:<16} {got}/{total}")
    if problems:
        print("\nعوائق:")
        for problem in problems:
            print("  ✗", problem)
        print(f"\nالحصيلة: {len(problems)} عائقًا")
        return 1 if assert_mode else 0
    print("\nالحصيلة: العقد سليم، والكتلة في الـREADMEين مطابقة للمولَّد من الشجرة")
    return 0


def listing(root: str) -> int:
    data, problems = load_contract(root)
    if data is None:
        for problem in problems:
            print("  ✗", problem)
        return 1
    variants = data.get("variants") or []
    names = existing_names(root)
    for group in data.get("groups") or []:
        frames = group.get("frames") or []
        got = sum(1 for frame in frames if found_files(root, frame["stem"], variants))
        flag = "مفتوحة" if group.get("open") else ("اختياريّة" if group.get("optional") else "")
        print(f"\n{group['key']} — {group['en']} · {group['ar']} {('· ' + flag) if flag else ''}  [{got}/{len(frames)}]")
        for frame in frames:
            present = [name for name in frame_names(frame["stem"], variants).values() if name in names]
            mark = "✓" if present else "…"
            extra = ("  +" + ", ".join(present[1:])) if len(present) > 1 else ""
            print(f"  {mark} {frame['stem']:<24} {frame['route']:<18} {frame['en']}{extra}")
    found, expected, _ = measure(root, data)
    print(f"\nالموجود {found} من {expected} إطارًا موصوفًا في العقد")
    if problems:
        print("\nعوائق في العقد نفسه:")
        for problem in problems:
            print("  ✗", problem)
        return 1
    return 0


def write(root: str) -> int:
    data, problems = load_contract(root)
    if data is None or problems:
        print("لا يُكتب معرض من عقد مكسور — أصلح العقد أوّلًا:")
        for problem in problems:
            print("  ✗", problem)
        return 1
    status = 0
    for readme, lang in READMES:
        path = os.path.join(root, readme)
        if not os.path.exists(path):
            print(f"  ✗ {readme}: مفقود")
            status = 1
            continue
        text = read(path)
        body = render_region(root, data, lang)
        updated = splice(text, body)
        if updated is None:
            print(f"  ✗ {readme}: علامتا `{BEGIN}` و`{END}` مطلوبتان، كلٌّ منهما مرّة واحدة")
            status = 1
            continue
        if updated == text:
            print(f"  = {readme}: بلا تغيير")
            continue
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(updated)
        print(f"  ✓ {readme}: كُتبت الكتلة ({len(body.splitlines())} سطرًا)")
    found, expected, groups = measure(root, data)
    print(f"\nالمولَّد: {len(groups)} مجموعات · {found} لقطة ظاهرة · {expected - found} في انتظار الالتقاط")
    return status


# ── قياس الأداة نفسها ────────────────────────────────────────────────────────
#
# كل حالة تُكسر مرّة، والأداة تُطالب بالبلاغ بعينه. والصور في الشجرة المصنوعة **PNG حقيقيّ**
# (IHDR + IDAT مضغوط + IEND + CRC) لأن الأداة تقرأ الملفّ فعلًا: كسرُ ما لا يُقرأ لا يُقاس.

GOOD_NAV = 'data object Now : MaxDestination("now", R.string.x, Icons.Rounded.Home)\n' \
           'data object AppSettings : MaxDestination("app_settings/{pkg}", R.string.y, Icons.Rounded.A)\n'

FIXTURE_CONTRACT = {
    "columns": 4,
    "thumb_width": 140,
    "caption_max": 20,
    "expected": {"width": 1080, "height": 2400, "max_bytes": 409600},
    "variants": ["light", "ar"],
    "groups": [
        {"key": "alpha", "en": "Alpha", "ar": "ألفا", "open": True,
         "frames": [{"stem": "01-one", "route": "now", "en": "One", "ar": "واحد"},
                    {"stem": "02-two", "route": "app_settings", "en": "Two", "ar": "اثنان"}]},
        {"key": "beta", "en": "Beta", "ar": "بيتا", "optional": True,
         "frames": [{"stem": "03-three", "route": "now", "en": "Three", "ar": "ثلاثة"}]},
    ],
}

READMES_FIXTURE = {
    "README.md": f"# T\n\n{BEGIN}\nplaceholder\n{END}\n\ntail\n",
    "README.ar.md": f"# ت\n\n{BEGIN}\nplaceholder\n{END}\n\nذيل\n",
}


def _write_png(path: str, width: int, height: int, noise: bool = False) -> None:
    """PNG سليم بلا مكتبات: IHDR + IDAT (zlib) + IEND بتواقيع CRC حقيقيّة."""

    def chunk(kind: bytes, body: bytes) -> bytes:
        return (len(body).to_bytes(4, "big") + kind + body
                + (zlib.crc32(kind + body) & 0xFFFFFFFF).to_bytes(4, "big"))

    rows = bytearray()
    for _ in range(height):
        rows.append(0)
        rows += os.urandom(width * 3) if noise else b"\x00" * (width * 3)
    blob = (PNG_MAGIC
            + chunk(b"IHDR", width.to_bytes(4, "big") + height.to_bytes(4, "big") + bytes([8, 2, 0, 0, 0]))
            + chunk(b"IDAT", zlib.compress(bytes(rows), 6))
            + chunk(b"IEND", b""))
    with open(path, "wb") as handle:
        handle.write(blob)


def build_fixture() -> str:
    fixture = tempfile.mkdtemp(prefix="screenshot-gallery-")
    os.makedirs(os.path.join(fixture, SHOTS_REL))
    os.makedirs(os.path.join(fixture, os.path.dirname(NAV_REL)))
    with open(os.path.join(fixture, CONTRACT_REL), "w", encoding="utf-8") as handle:
        json.dump(FIXTURE_CONTRACT, handle, ensure_ascii=False, indent=2)
    with open(os.path.join(fixture, CAPTURE_PAGE), "w", encoding="utf-8") as handle:
        handle.write("# contract\n")
    with open(os.path.join(fixture, NAV_REL), "w", encoding="utf-8") as handle:
        handle.write(GOOD_NAV)
    for name, text in READMES_FIXTURE.items():
        with open(os.path.join(fixture, name), "w", encoding="utf-8") as handle:
            handle.write(text)
    return fixture


def _mutate_json(fixture: str, edit) -> None:
    path = os.path.join(fixture, CONTRACT_REL)
    data = json.loads(read(path))
    edit(data)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(data, handle, ensure_ascii=False, indent=2)


def _plant(fixture: str, name: str, width: int, height: int, noise: bool = False) -> str:
    path = os.path.join(fixture, SHOTS_REL, name)
    _write_png(path, width, height, noise=noise)
    return path


def self_test() -> int:
    cases, failures = 0, []

    def expect(condition: bool, message: str) -> None:
        nonlocal cases
        cases += 1
        if not condition:
            failures.append(message)

    def problems_of(root: str) -> list[str]:
        data, contract_problems = load_contract(root)
        out = list(contract_problems)
        if data is None or out:
            return out
        return out + check_contract_page(root) + check_files(root, data) + [
            problem for readme, lang in READMES for problem in check_readme(root, readme, lang, data)
        ]

    # (١) مجلّد فارغ: لا صورة، ولا مكان لصورة مكسورة — والمولَّد يحمل السطر الصادق وحده
    fixture = build_fixture()
    try:
        body = render_region(fixture, FIXTURE_CONTRACT, "en")
        expect("<img" not in body, "المجلّد الفارغ أنتج صورة — وهذا ما لا يجوز أن يحدث")
        expect("<details" not in body, "المجلّد الفارغ أنتج شبكة مجموعات بدل السطر الصادق")
        expect("capture contract" in body, "السطر الصادق لا يشير إلى عقد الالتقاط")
        expect(write(fixture) == 0, "الكتابة على عقد سليم فشلت")
        expect(problems_of(fixture) == [], "الشجرة السليمة الفارغة أبلغت عن عوائق: %s" % problems_of(fixture))
        # أرقام الصفحة العربيّة عربيّة-هنديّة (٢ موصوفًا + ١ اختياريًّا)، ولا رقم لاتينيّ فيها
        arabic = read(os.path.join(fixture, "README.ar.md")).split(BEGIN)[1].split(END)[0]
        expect("٢" in arabic and "١" in arabic and not any(d in arabic for d in "0123456789"),
               "الصفحة العربيّة لم تُكتب بأرقام عربيّة-هنديّة: %s" % arabic.strip()[:60])
    finally:
        shutil.rmtree(fixture, ignore_errors=True)

    # (٢) لقطة واحدة حقيقيّة: تُرسم، وهي وحدها، والقاعدة ② تُكمَّل بخلايا فارغة
    fixture = build_fixture()
    try:
        _plant(fixture, "01-one.png", 1080, 2400)
        body = render_region(fixture, FIXTURE_CONTRACT, "en")
        expect(body.count("<img") == 1, "لقطة واحدة موجودة فظهر %d صورة" % body.count("<img"))
        expect(body.count("<td") == 4, "الصفّ الأخير لم يُكمَّل بخلايا فارغة (وجد %d خليّة)" % body.count("<td"))
        expect('src="docs/screenshots/01-one.png"' in body, "مسار الصورة داخل الـREADME غير صحيح")
        expect("02-two" not in body and "03-three" not in body,
               "إطار مفقود ظهر في المولَّد — وهذا ما يمنع الصورة المكسورة")
        expect("<details open>" in body, "المجموعة المفتوحة لم تُفتح")
        # والمجموعة التي لا لقطة لها **لا تُرسم أصلًا**: تفاصيل فارغة أثرٌ لا معلومة
        expect("<details>" not in body, "مجموعة بلا لقطة رُسمت شبكةً فارغة")
        write(fixture)
        expect(problems_of(fixture) == [], "لقطة سليمة واحدة أبلغت عن عوائق: %s" % problems_of(fixture))
    finally:
        shutil.rmtree(fixture, ignore_errors=True)

    # (٢ب) ولقطة في مجموعة غير مفتوحة: تُطوى ولا تُخفى
    fixture = build_fixture()
    try:
        _plant(fixture, "01-one.png", 1080, 2400)
        _plant(fixture, "03-three.png", 1080, 2400)
        body = render_region(fixture, FIXTURE_CONTRACT, "en")
        expect(body.count("<img") == 2, "لقطتان موجودتان فظهر %d" % body.count("<img"))
        expect("<details open>" in body and "<details>" in body,
               "المجموعة غير المفتوحة لم تُطوَ في شبكة من مجموعتين")
        expect(body.index("<details>") > body.index("<details open>"),
               "ترتيب المجموعات في المولَّد مخالف لترتيب العقد")
        expect(body.count("<td") == 8, "كل صفّ يجب أن يُكمَّل إلى أربع خلايا (وجد %d)" % body.count("<td"))
    finally:
        shutil.rmtree(fixture, ignore_errors=True)

    # (٣) متغيّرات: تظهر روابطها حين توجد، ولا تظهر حين تغيب
    fixture = build_fixture()
    try:
        _plant(fixture, "01-one.png", 1080, 2400)
        _plant(fixture, "01-one-ar.png", 1080, 2400)
        body = render_region(fixture, FIXTURE_CONTRACT, "en")
        expect("01-one-ar.png" in body, "لقطة RTL موجودة ولم تُربط")
        expect("01-one-light.png" not in body, "لقطة نسخة غير موجودة رُبطت")
        expect(body.count("<img") == 1, "رابط النسخة رُسم صورةً مستقلّة بدل علامة")
    finally:
        shutil.rmtree(fixture, ignore_errors=True)

    # (٤) انزياح اليد: كتلة تُعدَّل بيد تسقط البوّابة
    fixture = build_fixture()
    try:
        write(fixture)
        path = os.path.join(fixture, "README.md")
        text = read(path)
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(text.replace("Captures pending", "Screenshots soon"))
        expect(any("لا تطابق المولَّد" in p for p in problems_of(fixture)),
               "تعديل الكتلة بيد لم يُمسك — والمولَّد بلا حارس يتقادم")
    finally:
        shutil.rmtree(fixture, ignore_errors=True)

    # (٥) علامة غائبة
    fixture = build_fixture()
    try:
        path = os.path.join(fixture, "README.md")
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(read(path).replace(END, ""))
        expect(any("علامتا" in p for p in problems_of(fixture)), "علامة غائبة لم تُمسك")
    finally:
        shutil.rmtree(fixture, ignore_errors=True)

    # (٦) ملفّ لا يذكره العقد
    fixture = build_fixture()
    try:
        _plant(fixture, "99-typo.png", 1080, 2400)
        expect(any("لا يذكره العقد" in p for p in problems_of(fixture)),
               "لقطة باسم مخالف لم تُبلَّغ — وهي التي لا تظهر على أي صفحة")
    finally:
        shutil.rmtree(fixture, ignore_errors=True)

    # (٧) ملفّ مقطوع، وملفّ ليس PNG، ونسبة مخالفة، وحجم فوق السقف
    fixture = build_fixture()
    try:
        path = _plant(fixture, "01-one.png", 1080, 2400)
        blob = open(path, "rb").read()
        with open(path, "wb") as handle:
            handle.write(blob[:len(blob) // 2])
        expect(any("مقطوع" in p or "CRC" in p for p in problems_of(fixture)), "PNG مبتور لم يُمسك")
        with open(path, "wb") as handle:
            handle.write(b"not an image at all")
        expect(any("ليس PNG" in p for p in problems_of(fixture)), "ملفّ ليس PNG لم يُمسك")
        _plant(fixture, "01-one.png", 800, 2400)
        expect(any("نسبة" in p for p in problems_of(fixture)), "نسبة مخالفة لم تُمسك")
        _plant(fixture, "01-one.png", 1080, 2400, noise=True)
        expect(any("فوق سقف اللقطة" in p for p in problems_of(fixture)), "حجم فوق السقف لم يُمسك")
    finally:
        shutil.rmtree(fixture, ignore_errors=True)

    # (٨) نسخة موجودة وأصلها غائب
    fixture = build_fixture()
    try:
        _plant(fixture, "01-one-light.png", 1080, 2400)
        expect(any("الأصل" in p for p in problems_of(fixture)), "نسخة بلا أصل لم تُمسك")
    finally:
        shutil.rmtree(fixture, ignore_errors=True)

    # (٩) عقد مكسور: جذر مكرّر · مسار غير موجود · تسمية أطول من الحدّ · متغيّر لا تسمية له
    for label, edit, needle in (
        ("جذر مكرّر", lambda d: d["groups"][0]["frames"][1].update(stem="01-one"), "مكرّر"),
        ("مسار مجهول", lambda d: d["groups"][0]["frames"][0].update(route="nowhere"), "غير موجود"),
        ("تسمية طويلة", lambda d: d["groups"][0]["frames"][0].update(en="Twenty one letters cap!"), "caption_max"),
        ("متغيّر مجهول", lambda d: d.update(variants=["light", "sepia"]), "لا تسمية له"),
        ("اسم خارج الصيغة", lambda d: d["groups"][0]["frames"][0].update(stem="Home!"), "خارج الصيغة"),
    ):
        fixture = build_fixture()
        try:
            _mutate_json(fixture, edit)
            expect(any(needle in p for p in problems_of(fixture)),
                   f"عقد مكسور ({label}) لم يُمسك")
        finally:
            shutil.rmtree(fixture, ignore_errors=True)

    # (١٠) النسخة السليمة تمرّ: أداة تفشل دائمًا لا تفرّق بين عطب وسلامة
    fixture = build_fixture()
    try:
        _plant(fixture, "01-one.png", 1080, 2400)
        _plant(fixture, "02-two.png", 1080, 2400)
        expect(write(fixture) == 0, "الكتابة على شجرة فيها لقطات فشلت")
        leftover = problems_of(fixture)
        expect(leftover == [], "الشجرة السليمة أبلغت عن عوائق: %s" % leftover)
    finally:
        shutil.rmtree(fixture, ignore_errors=True)

    print(f"الفحص الذاتي: {cases} حالة · {len(failures)} فشل")
    for failure in failures:
        print("  ✗", failure)
    return 1 if failures else 0


def main() -> int:
    parser = argparse.ArgumentParser(description="معرض اللقطات في الـREADME (مولَّد من الملفّات الموجودة)")
    parser.add_argument("--write", action="store_true",
                        help=f"يكتب الكتلة بين {BEGIN} و{END} في الـREADMEين")
    parser.add_argument("--assert", dest="assert_mode", action="store_true",
                        help="يفشل عند عقد مكسور أو كتلة منزاحة")
    parser.add_argument("--self-test", action="store_true", help="يقيس الأداة على شجرة مصنوعة")
    parser.add_argument("--list", dest="list_only", action="store_true",
                        help="لكلّ إطار: موجود أم في انتظار الالتقاط")
    args = parser.parse_args()
    if args.self_test:
        return self_test()
    if args.write:
        return write(ROOT)
    if args.list_only:
        return listing(ROOT)
    return check(ROOT, assert_mode=args.assert_mode)


if __name__ == "__main__":
    sys.exit(main())
