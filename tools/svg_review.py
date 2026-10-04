#!/usr/bin/env python3
"""بوابة جودة الأصول المرئية: الوضوح والتجاوز والصحّة الساكنة — لا صحة الـXML فقط.

**ولماذا هذه أداة مستقلّة عن `readme_assets.py`:** تلك البوابة تقيس ما يُفسد الملفّ (XML مبتور،
SMIL غير مدعوم، حجم، مرجع مفقود)، وهي **لا ترى** ما يُفسد *التصميم*: نصًّا لا يُقرأ عند العرض الحقيقي،
أو خارج حدود اللوحة، أو مرسومًا في مكان لا يصحّ فيه إلا بوجود الحركة. وهذا ليس افتراضًا: قيس في
تكملة ١٥٠ أن `atlas-cycle.svg` المنشور كان **كل نصوصه الـ٢٣** دون ١١ بكسل على الهاتف (أصغرها ٣٫٥px)،
وأن `control-plane.svg` كان فيه نصّ يخرج من الـ`viewBox`، ونقطة متحرّكة تسقط في موضع خاطئ حين تُزال
الحركة. وكلّ ذلك مرّ على البوّابة الأولى بلا بلاغ.

المقاييس هنا معلَنة لا مُفترَضة:

  · **حدّ الوضوح:** كل نصّ يبلغ ≥ `MIN_PHONE_PX` (11 بكسل) حين يُعرض الأصل بعرض هاتف 390px.
    وما دون ذلك يُحسب **زينة** لا معلومة — والقاعدة تسمّيه.
  · **الحدود:** لا نصّ ولا بلاطة يخرجان من `viewBox` (بهامش `EDGE_MARGIN`) — **أفقيًّا ورأسيًّا**؛
    والدائرة الكاملة خارج اللوحة عنصر غير مرئي. (والتوهّج الذي ينزف عن حدّ اللوحة مقصود فليس
    عيبًا.) **وأُضيف العمود الرأسيّ لأنّ الأفقيّ وحده كان عميًا:** `gates.svg` كانت بلاطاته السفلى
    خارج الـ`viewBox` بالكامل فلم تُرَ قطّ، ومرّت النسخة الأولى من هذه البوّابة على أسوأ عطب في
    الأصول سبعةً صفر عيوب.
  · **التصادم:** نصّان على خطّ أساس واحد لا يتداخلان.
  · **مسار يمرّ في نصّ:** مسار **مرسوم** (`fill="none"`) لا يدخل صندوق نصّ، بفجوة
    `PATH_TEXT_CLEARANCE`. وهذا الصنف كان **خارج القياس أصلًا** («`path` لا يُقاس» كان حدًّا معلنًا)،
    فوقع في `control-plane.svg`: قوس العودة كان يقطع جملة «then read back…» في منتصفها، ومرّ
    على جميع البوّابات سبع مرّات حتّى رآه المالك بعينه. والقياس هنا على **نقاط على المنحنى**
    لا على صندوق محيط، ولا يهمل التحويلات.
  · **الصحّة الساكنة:** كل سمة متحرّكة بموضع (`x`/`y`/`cx`/`cy`/…) يجب أن **تبدأ** من قيمتها
    الأساسية، فالأصل يُقرأ صحيحة حين تُجرَّد الحركة (وGitHub يجرّد ما لا يدعمه).
  · **قواعد GitHub للحركة:** SMIL فقط، بلا `<script>` ولا CSS keyframes، و`keyTimes` يبدأ بـ0
    وينتهي بـ1 — وهو نفس النطاق الذي تحرسه `readme_assets`، مُعاد هنا كجزء من فحص واحد.

الاستعمال:
    python3 tools/svg_review.py --assert       # الأصول في docs/assets/
    python3 tools/svg_review.py --self-test    # يقيس الأداة على أصول مصغّرة معلومة النتيجة
    python3 tools/svg_review.py --json         # التقرير بنصّ JSON للمعالجة
"""
from __future__ import annotations

import argparse
import glob
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "docs", "assets")

# ── الحدود المعلَنة ──────────────────────────────────────────────────────────
PHONE_W = 390.0       # عرض مساحة المحتوى على هاتف GitHub تقريبًا
README_W = 1012.0     # عرضها على سطح المكتب
MIN_PHONE_PX = 11.0   # دون هذا الحدّ النصّ زينة لا معلومة
EDGE_MARGIN = 4.0     # هامش أمان عن حدود الـviewBox
# تقدير عرض الحرف: للعرض الأحادي 0.6em وللعرض النصّي 0.52em — تقدير محافظّ لا قياس محرك رسم.
ADV_MONO, ADV_SANS = 0.6, 0.52
# **والسطر الأخير لم يكن موجودًا حتى قِيس عطبه:** الحروف الكبيرة أعرض من التقدير المتوسّط — الـ'M'
# والـ'W' والـ'D' لا تساوي 'i' و'l' — وكان تقدير واحد (0.52em) يُمرّر عنوانًا كلّه حروف كبيرة
# ينتهي عند حدّ اللوحة. وقد وقع فعلًا في لوحة لغة التصميم: عنوان فرعيّ يبلغ تقديره الصارم ٧٦٥ في
# لوح عرضه ٧٢٠، أي أنّه يُقصّ عند العرض الحقيقيّ — **ومرّ من هذه البوّابة** لأن تقديرها كان أضيق
# من نصف الحقيقة. فصار الوزن بحسب النصّ نفسه: 0.62em إذا كان أكثر حروفه كبيرة.
ADV_CAPS = 0.62
# فجوة المسار المرسوم عن صندوق النصّ (بوحدات الـviewBox). والعطب الأوّل الذي وُلدت منه هذه
# القاعدة دخل **1.4** وحدة فقط فيمرّ من فجوة صفر لو كان حدًّا لاصقًا؛ وقِيست القاعدة على الأصول
# الـ٤٢ الحالية فكان الإنذار الكاذب **صفرًا** حتى فجوة ٦ — فهذه فجوة مقيسة لا مُقدَّرة.
PATH_TEXT_CLEARANCE = 2.0

POSITION_ATTRS = ("x", "y", "cx", "cy", "d", "points", "transform")


def attr_num(attrs: str, name: str, default: float = 0.0) -> float:
    mm = re.search(rf'\b{name}="([-\d.]+)"', attrs)
    return float(mm.group(1)) if mm else default


def parse_texts(svg: str) -> list[dict]:
    out = []
    for m in re.finditer(r"<text\b([^>]*)>(.*?)</text>", svg, re.S):
        attrs, body = m.group(1), m.group(2)
        plain = re.sub(r"\s+", " ", re.sub(r"<[^>]+>", "", body)).strip()
        if not plain:
            continue

        def a(name, default=None):
            mm = re.search(rf'\b{name}="([^"]*)"', attrs)
            return mm.group(1) if mm else default

        out.append({
            "txt": plain,
            "x": float(a("x", "0") or 0),
            "y": float(a("y", "0") or 0),
            "size": float(a("font-size", "16") or 16),
            "anchor": a("text-anchor", "start"),
            "family": a("font-family", "sans"),
        })
    return out


def span(t: dict, vw: float) -> tuple[float, float]:
    """المدى الأفقي المقدَّر للنصّ في وحدات الـviewBox.

    والتقدير هنا **مقدَّر لا مقيس** (لا محرّك رسم في هذه البيئة) — ولذلك يُختار التقدير الأوسع
    حين يكون النصّ بحروف كبيرة، لأن الخطأ في التقدير الضيّق يمرّر نصًّا مقصوصًا، والخطأ في التقدير
    الواسع يُسائل نصًّا سليمًا — والثاني يُصلَح بالتضييق، والأول يُرى في الصفحة.
    """
    letters = [c for c in t["txt"] if c.isalpha()]
    caps = sum(1 for c in letters if c.isupper()) / max(1, len(letters))
    adv = ADV_MONO if "mono" in t["family"] else (ADV_CAPS if caps > 0.7 else ADV_SANS)
    w = adv * t["size"] * len(t["txt"])
    if t["anchor"] == "middle":
        return t["x"] - w / 2, t["x"] + w / 2
    if t["anchor"] == "end":
        return t["x"] - w, t["x"]
    return t["x"], t["x"] + w


# ── مصفوفات التحويل: بلا هذه، القياس على مسار متحوّل **قياس كاذب** ───────────────
IDENT = (1.0, 0.0, 0.0, 1.0, 0.0, 0.0)


def _mul(m: tuple, n: tuple) -> tuple:
    """m·n — ترتيب SVG: `transform="A B"` يعني A ثم B."""
    a1, b1, c1, d1, e1, f1 = m
    a2, b2, c2, d2, e2, f2 = n
    return (a1 * a2 + c1 * b2, b1 * a2 + d1 * b2,
            a1 * c2 + c1 * d2, b1 * c2 + d1 * d2,
            a1 * e2 + c1 * f2 + e1, b1 * e2 + d1 * f2 + f1)


def _apply(m: tuple, x: float, y: float) -> tuple[float, float]:
    a, b, c, d, e, f = m
    return a * x + c * y + e, b * x + d * y + f


def parse_transform(tr: str) -> tuple[tuple, list[str]]:
    """نصّ التحويل → مصفوفة + أسماء دوالّ لم تُفهم.

    والمجهول **يُبلَّغ عنه** ولا يُهمَل: إهماله يُنتج نفس المرض الذي وُلدت القاعدة منه —
    قياسًا يظنّ نفسه قياسًا. والأصول الحالية تستعمل `translate` و`scale` و`rotate` وحدها.
    """
    import math
    m, unknown = IDENT, []
    for fn, args in re.findall(r"([A-Za-z]+)\s*\(([^)]*)\)", tr):
        v = [float(z) for z in re.findall(r"-?\d*\.?\d+(?:e-?\d+)?", args)]
        name = fn.lower()
        if name == "translate" and v:
            m = _mul(m, (1.0, 0.0, 0.0, 1.0, v[0], v[1] if len(v) > 1 else 0.0))
        elif name == "scale" and v:
            m = _mul(m, (v[0], 0.0, 0.0, v[1] if len(v) > 1 else v[0], 0.0, 0.0))
        elif name == "rotate" and v:
            r = math.radians(v[0])
            rot = (math.cos(r), math.sin(r), -math.sin(r), math.cos(r), 0.0, 0.0)
            if len(v) >= 3:  # rotate(deg, cx, cy)
                m = _mul(m, (1.0, 0.0, 0.0, 1.0, v[1], v[2]))
                m = _mul(m, rot)
                m = _mul(m, (1.0, 0.0, 0.0, 1.0, -v[1], -v[2]))
            else:
                m = _mul(m, rot)
        elif name == "matrix" and len(v) >= 6:
            m = _mul(m, tuple(v[:6]))
        else:
            unknown.append(fn)
    return m, unknown


def _d_points(d: str, steps: int = 24) -> list[tuple[float, float]]:
    """نقاط **على** المسار — عيّنات على المنحنى لا صندوقًا محيطًا.

    **ولماذا لا صندوق نقاط التحكّم:** الصندوق يكبر مع انحناء القوس، فيُسائل رسمًا سليمًا (مسار يمرّ
    فوق جملة وصندوقه يحيطها) — وهو أسوأ من عدم القياس لأنّه يُفقد البوّابة ثقتها. والعيّنة تكلّف
    عشرات عمليات حسابية ولا تكذب.

    والحدّ المعلن: القوس (`A`/`a`) غير مدعوم — ولا وجود له في أصول هذا المستودع (قِيس: الحروف
    المستعملة `M L H V C Q Z` وحدها)، وإن ظهر فالمقطع يُسقَط — وهذا يُقال هنا لا يُسكَت عنه.
    """
    toks = re.findall(r"[MmLlHhVvCcQqSsTtAaZz]|-?\d*\.?\d+", d)
    pts: list[tuple[float, float]] = []
    i, cx, cy, sx, sy, cmd = 0, 0.0, 0.0, 0.0, 0.0, ""
    while i < len(toks):
        t = toks[i]
        if re.match(r"[A-Za-z]", t):
            cmd = t
            i += 1
            if cmd in "Zz":
                for k in range(1, 9):
                    pts.append((cx + (sx - cx) * k / 8, cy + (sy - cy) * k / 8))
                cx, cy = sx, sy
            continue
        u = cmd.upper()
        rel = cmd.islower()
        try:
            if u == "M":
                x, y = float(toks[i]), float(toks[i + 1])
                x, y = (cx + x, cy + y) if rel else (x, y)
                cx = sx = x
                cy = sy = y
                pts.append((x, y))
                i += 2
                cmd = "l" if rel else "L"
            elif u == "L":
                x, y = float(toks[i]), float(toks[i + 1])
                x, y = (cx + x, cy + y) if rel else (x, y)
                for k in range(1, 9):
                    pts.append((cx + (x - cx) * k / 8, cy + (y - cy) * k / 8))
                cx, cy = x, y
                i += 2
            elif u == "H":
                x = float(toks[i])
                x = cx + x if rel else x
                for k in range(1, 9):
                    pts.append((cx + (x - cx) * k / 8, cy))
                cx = x
                i += 1
            elif u == "V":
                y = float(toks[i])
                y = cy + y if rel else y
                for k in range(1, 9):
                    pts.append((cx, cy + (y - cy) * k / 8))
                cy = y
                i += 1
            elif u in ("C", "Q"):
                n = 6 if u == "C" else 4
                v = [float(z) for z in toks[i:i + n]]
                if rel:
                    v = [v[0] + cx, v[1] + cy, v[2] + cx, v[3] + cy] + (
                        [v[4] + cx, v[5] + cy] if n == 6 else [])
                p1 = (v[0], v[1])
                p2 = (v[2], v[3]) if u == "C" else p1
                p3 = (v[4], v[5]) if u == "C" else (v[2], v[3])
                for k in range(1, steps + 1):
                    tt = k / steps
                    mt = 1 - tt
                    pts.append((mt ** 3 * cx + 3 * mt ** 2 * tt * p1[0]
                                + 3 * mt * tt ** 2 * p2[0] + tt ** 3 * p3[0],
                                mt ** 3 * cy + 3 * mt ** 2 * tt * p1[1]
                                + 3 * mt * tt ** 2 * p2[1] + tt ** 3 * p3[1]))
                cx, cy = p3
                i += n
            else:
                i += 1
        except (IndexError, ValueError):
            break
    return pts


def stroked_paths(svg: str) -> tuple[list[tuple[list, float]], list[str]]:
    """المسارات **المرسومة** بإحداثيات اللوحة بعد تحويلاتها، ومعه تحويلات لم تُفهم.

    والمرسوم هو ما `fill="none"` — أمّا المملوءة فهي بطاقات خلفيّة، والنصّ فوق البطاقة مشروع.
    وتحويلات الآباء تُجمَّع (شجرة XML تُمسَح، لا تعبير نمطيّ) لأن `atlas-cycle` و`domains`
    تضع مساراتها داخل `<g transform=…>` — وقياس بلا جمعها كان سيقول «نظيف» عن ملفّين لم يُقرآ.
    """
    import xml.etree.ElementTree as ET
    out: list[tuple[list, float]] = []
    unknown: list[str] = []
    try:
        root = ET.fromstring(svg)
    except ET.ParseError:
        return out, unknown

    def walk(el, m: tuple, tr_depth: int):
        tag = el.tag.split("}")[-1]
        tr = (el.get("transform") or "").strip()
        if tr:
            sub, unk = parse_transform(tr)
            if unk:
                unknown.extend(unk)
            m = _mul(m, sub)
            if tag == "text":
                # كل قياسات النصّ في هذه الأداة تفترض إحداثيات اللوحة — فنصّ مُحوَّل يُبلَّغ عنه
                unknown.append("<text transform>")
        a = el.attrib
        if tag in ("path", "line") and a.get("fill") == "none":
            if tag == "line":
                d = (f'M{a.get("x1", "0")},{a.get("y1", "0")}'
                     f'L{a.get("x2", "0")},{a.get("y2", "0")}')
            else:
                d = a.get("d", "")
            pts = [_apply(m, x, y) for x, y in _d_points(d)]
            if pts:
                try:
                    sw = float(a.get("stroke-width", "1"))
                except ValueError:
                    sw = 1.0
                out.append((pts, sw))
        for child in el:
            walk(child, m, tr_depth + 1)

    walk(root, IDENT, 0)
    return out, sorted(set(unknown))


def review(rel: str, svg: str) -> list[str]:
    problems: list[str] = []
    vb = re.search(r'viewBox="([^"]+)"', svg)
    if not vb:
        return [f"{rel}: بلا viewBox — لا يمكن قياسه ولا ضمان حجمه"]
    vbs = [float(v) for v in re.split(r"[,\s]+", vb.group(1).strip())]
    vw, vh = vbs[2], vbs[3]
    scale = PHONE_W / vw

    # ── قواعد GitHub للحركة والوسوم ─────────────────────────────────────────
    if "<script" in svg:
        problems.append("فيه <script> — GitHub يجرّده، وهو غير مدعوم هنا")
    if "keyframes" in svg or "@keyframes" in svg:
        problems.append("فيه حركة CSS keyframes — GitHub يجرّدها صامتًا")
    if "xlink:href" in svg:
        problems.append("فيه xlink:href — يُجرَّد فيُهمل التحريك بصمت")
    if re.search(r'xml:space="preserve"', svg):
        problems.append('xml:space="preserve" يُرسم إزاحةً في أغلب المحرّكات')
    for m in re.finditer(r'<tspan\b[^>]*opacity="', svg):
        problems.append("tspan بشفافية — سلوكه غير موثوق في محرّكات GitHub")
    for m in re.finditer(r'keyTimes="([^"]+)"', svg):
        ks = [float(v) for v in re.split(r"[;\s]+", m.group(1).strip())]
        if not ks or ks[0] != 0 or ks[-1] != 1:
            problems.append(f'keyTimes={m.group(1)!r} يجب أن تبدأ بـ0 وتنتهي بـ1')
    for m in re.finditer(r'values="([^"]+)"', svg):
        if ";" not in m.group(1):
            problems.append("animate بقيمة واحدة لا يفعل شيئًا")

    # ── الصحّة الساكنة: الأصل يجب أن يُقرأ صحيحة بلا حركة ──────────────────
    for m in re.finditer(r"<animate\b([^>]*)/?>", svg):
        attrs = m.group(1)
        nm = re.search(r'attributeName="([^"]+)"', attrs)
        vals = re.search(r'values="([^"]+)"', attrs)
        if not (nm and vals) or nm.group(1) not in POSITION_ATTRS:
            continue
        attr = nm.group(1)
        hits = list(re.finditer(rf'\b{re.escape(attr)}="([^"]*)"', svg[: m.start()]))
        base = hits[-1].group(1) if hits else None
        first = vals.group(1).split(";")[0].strip()
        if base is not None and base.split(";")[0].strip() != first:
            problems.append(
                f"الصحّة الساكنة: <{attr}> أساسه {base!r} لا يساوي أول إطار {first!r} "
                f"⇒ بلا SMIL يقع العنصر في موضع خاطئ")

    # ── الوضوح والحدود والتصادم ────────────────────────────────────────────
    rows = []
    for t in parse_texts(svg):
        x0, x1 = span(t, vw)
        rows.append((t, x0, x1))
        px = t["size"] * scale
        if px < MIN_PHONE_PX:
            problems.append(
                f"غير مقروء على الهاتف: «{t['txt'][:38]}» بحجم {t['size']:g}px "
                f"= {px:.1f}px عند {PHONE_W:g}px (الحدّ {MIN_PHONE_PX:g})")
        if x1 > vw - EDGE_MARGIN or x0 < EDGE_MARGIN:
            problems.append(
                f"خارج حدود اللوحة: «{t['txt'][:38]}» بمدى x {x0:.0f}..{x1:.0f} من {vw:g}")
        # صعود الحرف ≈ 0.8 من الحجم وهبوطه ≈ 0.25 — تقدير محافظّ لا قياس محرك رسم
        top, bot = t["y"] - 0.8 * t["size"], t["y"] + 0.25 * t["size"]
        if top < EDGE_MARGIN or bot > vh - EDGE_MARGIN:
            problems.append(
                f"رأسيًّا خارج اللوحة: «{t['txt'][:38]}» بمدى y {top:.0f}..{bot:.0f} من {vh:g}")
    # ── مسار يمرّ في نصّ: الصنف الذي كان **خارج القياس** في هذه الأداة ───────────
    drawn, unknown_tr = stroked_paths(svg)
    for fn in unknown_tr:
        problems.append(
            f"تحويل غير مُفسَّر ({fn}) — القياس على مساراته متوقّف، وهذا يُقال ولا يُسكَت عنه")
    for pts, sw in drawn:
        for t, x0, x1 in rows:
            top, bot = t["y"] - 0.8 * t["size"], t["y"] + 0.25 * t["size"]
            pad = sw / 2 + PATH_TEXT_CLEARANCE
            for px, py in pts:
                if x0 - pad <= px <= x1 + pad and top - pad <= py <= bot + pad:
                    problems.append(
                        f"مسار يمرّ في نصّ: «{t['txt'][:32]}» صندوقه x {x0:.0f}..{x1:.0f} "
                        f"وy {top:.1f}..{bot:.1f}، والمسار يبلغه عند ({px:.1f},{py:.1f}) "
                        f"بعرض سطر {sw:g} وفجوة {PATH_TEXT_CLEARANCE:g}")
                    break

    by_y: dict[float, list] = {}
    for t, x0, x1 in rows:
        by_y.setdefault(round(t["y"]), []).append((x0, x1, t))
    for y, items in by_y.items():
        items.sort(key=lambda z: z[0])
        for i in range(len(items) - 1):
            if items[i][1] > items[i + 1][0] - 2:
                problems.append(
                    f"تصادم على خطّ أساس y={y}: «{items[i][2]['txt'][:24]}» ↔ "
                    f"«{items[i + 1][2]['txt'][:24]}»")

    # ── الأشكال: البلاطة لا تُقصّ، والدائرة لا تكون كاملة خارج اللوحة ──────────
    for m in re.finditer(r"<rect\b([^>]*?)/?>", svg):
        a = m.group(1)
        x, y = attr_num(a, "x"), attr_num(a, "y")
        w, h = attr_num(a, "width"), attr_num(a, "height")
        if w <= 0 or h <= 0:
            continue
        if w >= 0.9 * vw and h >= 0.9 * vh:
            continue  # خلفية اللوحة كاملة — ليست بلاطة مقصوصة
        if (x < EDGE_MARGIN or y < EDGE_MARGIN
                or x + w > vw - EDGE_MARGIN or y + h > vh - EDGE_MARGIN):
            problems.append(
                f"بلاطة مقصوصة أو خارج اللوحة: rect عند x {x:.0f}..{x + w:.0f} "
                f"وy {y:.0f}..{y + h:.0f} من {vw:g}×{vh:g}")
    for m in re.finditer(r"<circle\b([^>]*?)/?>", svg):
        a = m.group(1)
        cx, cy, r = attr_num(a, "cx"), attr_num(a, "cy"), attr_num(a, "r")
        if r <= 0:
            continue
        if (cx + r < EDGE_MARGIN or cx - r > vw - EDGE_MARGIN
                or cy + r < EDGE_MARGIN or cy - r > vh - EDGE_MARGIN):
            problems.append(
                f"دائرة كاملة خارج اللوحة — عنصر غير مرئي: circle عند "
                f"({cx:.0f},{cy:.0f}) r {r:.0f}")
    return problems


# ═══════════════════════════════════════════════════════════════════════════
# قياس الأداة نفسها: أصل مصغّر لكل عطب معلوم النتيجة
# ═══════════════════════════════════════════════════════════════════════════
def self_test() -> int:
    cases = []

    def case(name: str, svg: str, marker: str, expect_hit: bool):
        """حالة معلومة النتيجة: expect_hit صريحة، لا استنتاج من اسم الحالة.

        (كانت النسخة الأولى تستنتج القطبية من الاسم — «لا يُقرأ»/«مرفوضة» صُنّفت
        سلبيّة وهي إيجابية — فكانت الأداة تُقرّ إخفاقها بحالة خاطئة.)
        """
        probs = review("case.svg", svg)
        hit = any(marker in p for p in probs)
        cases.append((name, hit == expect_hit, probs if hit != expect_hit else []))

    head = ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 400 200" '
            'width="400" height="200">')
    # إيجابية: العطب المسمّى يجب أن يُكتشف
    case("نصّ صغير لا يُقرأ", head + '<text x="20" y="40" font-size="8">tiny</text></svg>',
         "غير مقروء على الهاتف", True)
    case("خارج حدود اللوحة", head + '<text x="360" y="40" font-size="30">overflowing</text></svg>',
         "خارج حدود اللوحة", True)
    case("تصادم على خطّ واحد",
         head + '<text x="20" y="40" font-size="30">left side</text>'
                '<text x="30" y="40" font-size="30">right</text></svg>',
         "تصادم", True)
    case("مسار مرسوم يقطع نصًّا",
         head + '<text x="20" y="40" font-size="30">under a rule</text>'
                '<path d="M20,40 L380,40" fill="none" stroke="#000" stroke-width="2"/></svg>',
         "مسار يمرّ في نصّ", True)
    case("مسار يقطع نصًّا داخل تحويل مُجمَّع",
         head + '<text x="20" y="40" font-size="30">under a rule</text>'
                '<g transform="translate(0,-40) scale(1)">'
                '<path d="M20,80 L380,80" fill="none" stroke="#000"/></g></svg>',
         "مسار يمرّ في نصّ", True)
    case("تحويل غير مُفسَّر يُبلَّغ ولا يُسكَت عنه",
         head + '<path d="M20,180 L380,180" fill="none" stroke="#000" transform="skewX(4)"/></svg>',
         "تحويل غير مُفسَّر", True)
    case("الصحّة الساكنة مكسورة",
         head + '<circle cx="10" cy="10" r="4"><animate attributeName="cx" '
                'values="50;100" keyTimes="0;1" dur="2s" repeatCount="indefinite"/></circle></svg>',
         "الصحّة الساكنة", True)
    case("keyTimes لا تنتهي بـ1",
         head + '<rect width="4" height="4"><animate attributeName="opacity" '
                'values="1;0" keyTimes="0;0.5" dur="2s"/></rect></svg>',
         "keyTimes", True)
    case("script مرفوض", head + "<script>x</script></svg>", "script", True)
    case("keyframes مرفوضة", head + "<style>@keyframes a{}</style></svg>", "keyframes", True)
    case("tspan بشفافية", head + '<text x="20" y="40" font-size="30">'
          '<tspan opacity="0.5">half</tspan></text></svg>', "tspan", True)
    case("بلا viewBox", '<svg xmlns="http://www.w3.org/2000/svg">', "بلا viewBox", True)
    case("نصّ رأسيًّا خارج اللوحة", head + '<text x="20" y="195" font-size="30">low</text></svg>',
         "رأسيًّا خارج اللوحة", True)
    case("بلاطة مقصوصة", head + '<rect x="20" y="350" width="100" height="62"/></svg>',
         "بلاطة", True)
    case("دائرة كاملة خارج اللوحة", head + '<circle cx="200" cy="450" r="10"/></svg>',
         "دائرة كاملة", True)
    # سلبية: الصحيح يجب ألّا يُبلَّغ عنه
    case("نصّ كبير يُقرأ", head + '<text x="20" y="40" font-size="30">readable</text></svg>',
         "غير مقروء على الهاتف", False)
    case("خلفية اللوحة ليست بلاطة مقصوصة",
         head + '<rect x="0" y="0" width="400" height="200"/></svg>', "بلاطة", False)
    case("دائرة تنزف عن عمد (توهّج)", head + '<circle cx="390" cy="100" r="112"/></svg>',
         "دائرة", False)
    case("مسار بفجوة تحت النصّ ليس عطبًا",
         head + '<text x="20" y="40" font-size="30">clear</text>'
                '<path d="M20,100 L380,100" fill="none" stroke="#000" stroke-width="2"/></svg>',
         "مسار يمرّ في نصّ", False)
    case("بطاقة مملوءة والنصّ فوقها ليست عطبًا",
         head + '<path d="M0,10 L400,10 L400,80 L0,80 Z" fill="#eeeeee"/>'
                '<text x="20" y="40" font-size="30">on a card</text></svg>',
         "مسار يمرّ في نصّ", False)
    case("الصحّة الساكنة سليمة",
         head + '<circle cx="50" cy="10" r="4"><animate attributeName="cx" '
                'values="50;100" keyTimes="0;1" dur="2s" repeatCount="indefinite"/></circle></svg>',
         "الصحّة الساكنة", False)

    fails = [n for n, ok, _ in cases if not ok]
    print("قياس الأداة: " + f"{len(cases)} حالة · إخفاقات {len(fails)}")
    for n, ok, probs in cases:
        print(f"  {'✓' if ok else '✗'} {n}")
        if not ok:
            for p in probs:
                print(f"        {p}")
    print("الأداة تقيس ما تدّعيه." if not fails else "الأداة لا تقيس ما تدّعيه.")
    return 0 if not fails else 1


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--assert", dest="assert_", action="store_true")
    ap.add_argument("--self-test", dest="self_test", action="store_true")
    ap.add_argument("--json", dest="as_json", action="store_true")
    ap.add_argument("--all", action="store_true", help="يشمل docs/ كلّها لا docs/assets/ وحده")
    args = ap.parse_args()

    if args.self_test:
        return self_test()

    files = sorted(glob.glob(os.path.join(ASSETS, "*.svg")))
    if args.all:
        files = sorted(set(files) | set(glob.glob(os.path.join(ROOT, "docs", "**", "*.svg"),
                                                recursive=True)))
    report = {}
    problems = 0
    for path in files:
        rel = os.path.relpath(path, ROOT)
        text = open(path, encoding="utf-8").read()
        probs = review(rel, text)
        report[rel] = probs
        problems += len(probs)

    if args.as_json:
        print(json.dumps(report, ensure_ascii=False, indent=2))
        return 0

    min_size = 0.0
    for path in files:
        for m in re.findall(r'font-size="([\d.]+)"', open(path, encoding="utf-8").read()):
            min_size = min(min_size or 99, float(m))
    print(f"أصول مفحوصة: {len(files)} · أصغر حجم نصّ: {min_size:g}px · عيوب: {problems}")
    for rel in sorted(report):
        if report[rel]:
            print(f"  {rel}")
            for p in report[rel]:
                print(f"     ✗ {p}")
        else:
            print(f"  ✓ {rel}")
    if problems and args.assert_:
        print(f"\nبوابة الأصول المرئية: exit 1 — {problems} عيبًا")
        return 1
    print("بوابة الأصول المرئية: exit 0")
    return 0


if __name__ == "__main__":
    sys.exit(main())
