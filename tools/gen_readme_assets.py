#!/usr/bin/env python3
"""Generates the README visual assets with computed geometry, then re-reads each
file to verify it against the claims made about it.

Design rules applied here (from the measured review of the previous assets):
  · every label must survive a phone width — target >= 11px at 390px display;
  · the static state must be complete and correct with SMIL stripped;
  · no internal code names on a product surface;
  · one palette across all assets;
  · SMIL only (GitHub strips scripts and CSS keyframes), keyTimes 0..1;
  · every panel size is computed from its content, never typed as a constant
    (the first gates.svg held a 5-row grid in a 4-row canvas: four tiles were
    never rendered anywhere, and only tools/svg_review.py caught it).

Regenerate with:  python3 tools/gen_readme_assets.py
Then measure what was generated:  python3 tools/svg_review.py --assert
"""
import math
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "docs", "assets")

# ── one palette, used by every asset ──────────────────────────────────────────
# اللوحة صارت **عائلة** لا رمزين: `panelTop`/`panelBottom` طرفا تدرّج اللوح، و`raise` سطحٌ
# مرتفع داخل اللوح، و`shimmer` جار `accent` في التدريج (لا لون بعيد عنه — القريب وحده يُنتج
# وسيطًا نظيفًا). وسبب التدرّج **مقيس**: اللوح المسطّح بلون واحد يُقرأ مستطيلًا لا سطحًا، و`rect`
# بحافة واحدة يجعل كل الأصول تبدو مخطّطات تقنية لا واجهة منتج. و`linearGradient` ممّا **يحفظه**
# GitHub في الـSVG داخل الـREADME (بخلاف `filter` الثقيل)، فهو العمق الوحيد المتاح بلا ثمن.
# **والنغمات الثلاث (تنبيه/تحذير/خطر) ليست ألوانًا اخترعتها الصفحة:** هي حرفيًّا قيم
# `MaxTokens.kt` (`PositiveOnDark` · `CautionOnDark` · `CriticalOnDark` والفروع الفاتحة منها).
# أي أنّ لون «الحرارة مرتفعة» في الرسم هو لون «الحرارة مرتفعة» في التطبيق — وهذا ما تقيسه
# القاعدة ⑦ في `tools/design_doc.py`: تطابق اللوحة مع ثوابت الشيفرة، لا تشابهها بالعين.
DARK = dict(bg="#0E1418", panel="#151C22", edge="#243340", ink="#E8F1F7",
            tonePositive="#5FD9AC", toneCaution="#FFB86B", toneCritical="#FF9A90",
            muted="#93A9B8", accent="#5FD9AC", steel="#9BBACB", clay="#D99A6C",
            panelTop="#1B242C", panelBottom="#101820", lift="#151F27",
            shimmer="#A8F0D6", tile="#0C1116",
            # حدّ بلاطة الرمز — وُجد في الشجرة **رقمًا بلا اسم** (`stroke="#2E4150"` في `ICON_TILE`)
            # بينما التعليق فوقه يقول «والألوان من اللوحة نفسها». أي أنّ الملفّ كان يدّعي ما لم يصنعه،
            # وهو بعينه ما كشفته أداة `design_doc.py` أوّل تشغيل. والقيمة **محفوظة كما هي**: صفر
            # تغيير بصريّ، والاسم هو المُكتسَب. ولا نظير لها في `LIGHT` عن قصد: بلاطة الرمز
            # **داكنة دائمًا** (الرمز يُحمَّل في `<img>` فلا يرى الوضع الفاتح)، واللوحة الفاتحة
            # تخدم اللافتة وحدها — ونقص المفتاح مقصود لا منسيّ.
            tileEdge="#2E4150")
LIGHT = dict(bg="#FFFFFF", panel="#F4F7FA", edge="#D3DDE5", ink="#12212B",
             muted="#4C6373", accent="#0E9E74", steel="#3C5A72", clay="#B36A42",
             panelTop="#FFFFFF", panelBottom="#EDF2F6", lift="#F7FAFC",
             shimmer="#0B7A59", tile="#FFFFFF",
             tonePositive="#0B6B4F", toneCaution="#8A5200", toneCritical="#9A1B1B")

SANS = "-apple-system, 'Segoe UI', Inter, Roboto, Helvetica, Arial, sans-serif"
MONO = "'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace"

# phone-legibility budget: viewBox width is scaled to 390px on a phone
PHONE = 390.0
LEGIBLE = 11.0


def px_at_phone(size, vb_width):
    return size * PHONE / vb_width


def esc(s):
    return (s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))


def text(x, y, s, size, fill, anchor="start", weight=None, family=SANS, opacity=None):
    a = f' text-anchor="{anchor}"' if anchor != "start" else ""
    w = f' font-weight="{weight}"' if weight else ""
    o = f' opacity="{opacity}"' if opacity is not None else ""
    return (f'  <text x="{x:.1f}" y="{y:.1f}" font-size="{size}" fill="{fill}"'
            f'{a}{w}{o} font-family="{family}">{esc(s)}</text>')


def circle_arrow(x, y, angle_deg, fill, scale=1.0):
    """Small triangle pointing along the tangent (clockwise, y-down)."""
    return (f'  <path d="M{-5*scale:.1f},{-5*scale:.1f} L{7*scale:.1f},0 '
            f'L{-5*scale:.1f},{5*scale:.1f} Z" fill="{fill}" '
            f'transform="translate({x:.1f},{y:.1f}) rotate({angle_deg:.1f})"/>')


# ═══════════════════════════════════════════════════════════════════════════
# 0. لغة بصرية واحدة — تُستهلك من كل أصل، فلا يكون للأصل أسلوبه الخاص
# ═══════════════════════════════════════════════════════════════════════════
# **العطب الذي أُصلح هنا مقيس لا مُتخيَّل:** كانت الأصول تُرسم بلغتين متعارضتين — المولَّد منها
# (atlas-cycle، max-ai، control-plane، gates، locales) بلوح `#151C22` ونصف قطر ٢٢ وحدّ `#243340`،
# والمكتوب بيد (`domains.svg`، `integration.svg`) بلوح `#12202A` ونصف قطر ١٤ **وبلا حدّ على اللوح
# نفسه**. أي أنّ زائرًا يرى في الصفحة نفسها شكلين لـ«البطاقة» — وهذا بعينه ما يجعل الصفحة تبدو
# كأنها جُمعت من مصادر لا كأنها منتج واحد. والعلاج **ليس** إعادة رسم كل أصل بيد، بل أن يصير
# للشكل الواحد تعريف واحد يستهلكه الجميع — وهو ما يلي.

def defs_panel(uid, pal, top=None, bottom=None):
    """تدرّج اللوح — يُعرَّف مرّة لكل ملفّ (لا مرجع مشترك بين ملفّات، فهي صور مستقلّة)."""
    top = top or pal["panelTop"]
    bottom = bottom or pal["panelBottom"]
    return (f'  <defs>\n'
            f'    <linearGradient id="{uid}" x1="0" y1="0" x2="0" y2="1">\n'
            f'      <stop offset="0" stop-color="{top}"/>\n'
            f'      <stop offset="1" stop-color="{bottom}"/>\n'
            f'    </linearGradient>\n'
            f'  </defs>')


def defs_all(uid, pal, card=None):
    """تدرّجات الأصل: واحد للوح وواحد للبطاقة — يُعرَّفان مرّة واحدة في الملفّ الواحد.

    ولماذا تدرّج أصلًا للبطاقة أيضًا: البطاقة واللوح بلون **واحد** يُلغيان الفرق بينهما، فيقرأ
    القارئ مربّعًا داخل مربّع بدل طبقتين. والتدرّج يعيد الفرق بلا حدّ ثانٍ ولا ظلّ.
    """
    lines = ['  <defs>',
             f'    <linearGradient id="{uid}" x1="0" y1="0" x2="0" y2="1">',
             f'      <stop offset="0" stop-color="{pal["panelTop"]}"/>',
             f'      <stop offset="1" stop-color="{pal["panelBottom"]}"/>',
             '    </linearGradient>']
    if card:
        lines += [f'    <linearGradient id="{card}" x1="0" y1="0" x2="0" y2="1">',
                  f'      <stop offset="0" stop-color="{pal["lift"]}"/>',
                  f'      <stop offset="1" stop-color="{pal["bg"]}"/>',
                  '    </linearGradient>']
    lines.append('  </defs>')
    return "\n".join(lines)


def panel(uid, x, y, w, h, r, pal, stroke=None):
    """اللوح: تدرّج + حدّ شعريّ. كل لوح في كل أصل يمرّ من هنا."""
    return (f'  <rect x="{x:.1f}" y="{y:.1f}" width="{w:.1f}" height="{h:.1f}" rx="{r}" '
            f'fill="url(#{uid})" stroke="{stroke or pal["edge"]}" stroke-width="1.5"/>')


def edge_light(x, y, w, pal, opacity=0.10):
    """خطّ ضوء على الحافة العليا — العمق كلّه من هذه الحافة.

    وحدّه معلَن: هذا **لا يُضيف معلومة**، ولذلك `opacity` منخفضة دائمًا ولا يُعتمد عليه في تمييز
    عنصر. والقراءة بلا حركة وبلا عمق تبقى صحيحة — والعمق طبقة فوقها لا تحلّ محلّها.
    """
    return (f'  <rect x="{x:.1f}" y="{y:.1f}" width="{w:.1f}" height="1.4" rx="0.7" '
            f'fill="{pal["ink"]}" opacity="{opacity:.2f}"/>')


def rule(x, y, w, pal, accent=None, height=6):
    """الشريط التمييزيّ — وله نمط «املأ واثبت» من كتاب وصفات SVG المتحرّكة: يطول ثم يثبت ثم يعود.

    و**لماذا لا يطول ويعود بلا ثبات:** القيمة نفسها في طرفَي الرحلة تُنتج نبضة لا إشارة؛ والثبات
    في المنتصف هو ما يجعله يُقرأ إيقاعًا لا اهتزازًا. وهو من `patterns.md` §1 حرفيًّا (`values`
    بقيمتين متجاورتين متطابقتين = إطار ثابت).
    """
    tone = accent or pal["accent"]
    return "\n".join([
        f'  <rect x="{x:.1f}" y="{y:.1f}" width="{w:.0f}" height="{height}" '
        f'rx="{height/2:.1f}" fill="{tone}">',
        f'    <animate attributeName="width" values="{w:.0f};{w*2.4:.0f};{w*2.4:.0f};{w:.0f}" '
        f'keyTimes="0;0.16;0.84;1" dur="8s" repeatCount="indefinite"/>',
        f'    <animate attributeName="fill" values="{tone};{pal["shimmer"]};{tone}" '
        f'keyTimes="0;0.5;1" dur="8s" repeatCount="indefinite"/>',
        '  </rect>',
    ])


def header(x, y, title, subtitle, pal, rule_w=90):
    """رأس كل أصل: شريط مميّز، ثم العنوان، ثم سطر توضيحيّ — بترتيب واحد في كل ملفّ."""
    return "\n".join([
        rule(x, y, rule_w, pal),
        text(x, y + 52, title, 34, pal["ink"], weight="800"),
        text(x, y + 88, subtitle, 21, pal["muted"]),
    ])


def chip(x, y, label, size, tone):
    """وسم دائريّ: خلفية مصبوغة + حدّ خفيف + نصّ. يُقاس عرضه من طول نصّه لا يُكتب رقمًا."""
    w = 0.56 * size * len(label) + 28
    h = size + 18
    return "\n".join([
        f'  <rect x="{x:.1f}" y="{y:.1f}" width="{w:.1f}" height="{h:.1f}" '
        f'rx="{h/2:.1f}" fill="{tone}" opacity="0.10"/>',
        f'  <rect x="{x:.1f}" y="{y:.1f}" width="{w:.1f}" height="{h:.1f}" '
        f'rx="{h/2:.1f}" fill="none" stroke="{tone}" stroke-width="1.2" opacity="0.40"/>',
        text(x + w / 2, y + h * 0.70, label, size, tone, anchor="middle", weight="600"),
    ])


def breathe(cx, cy, r, tone, lo=0.06, hi=0.13, dur=9.0):
    """هالة تتنفّس — حركة `opacity` وحدها: لا موضع يتزحزح، فالأصل بلا SMIL يبقى صحيحًا تمامًا."""
    return "\n".join([
        f'  <circle cx="{cx:.1f}" cy="{cy:.1f}" r="{r:.1f}" fill="{tone}" opacity="{lo:.2f}">',
        f'    <animate attributeName="opacity" values="{lo:.2f};{hi:.2f};{lo:.2f}" '
        f'keyTimes="0;0.5;1" dur="{dur:.1f}s" repeatCount="indefinite"/>',
        '  </circle>',
    ])


def tick(cx, cy, r, pal, dur=6.0):
    """نقطة «الحيّ» — نمط `heartbeat` من كتاب وصفات SVG المتحرّكة: صمت طويل، ثم نبضة قصيرة.

    وسمة الأساس `opacity=0` مقصودة: الأصل بلا حركة يُظهر نقطة ثابتة في موضعها لا فراغًا، وهي
    هنا **زينة لا معلومة** (المعلومة في النصّ المجاور لها) فلا تُفقد شيئًا إن غابت الحركة.
    """
    return "\n".join([
        f'  <circle cx="{cx:.1f}" cy="{cy:.1f}" r="{r}" fill="{pal["accent"]}" opacity="0">',
        f'    <animate attributeName="opacity" values="0;0;1;0.55;0" '
        f'keyTimes="0;0.90;0.94;0.97;1" dur="{dur:.1f}s" repeatCount="indefinite"/>',
        '  </circle>',
    ])


def draw_in(d, stroke, width, length, dur=1.6, begin=0.0):
    """خطّ يُرسم — سمة الأساس تُظهره كاملًا، فمن لا يدعم SMIL يرى الرسم كاملًا لا فراغًا.

    و**لا `fill="freeze"` هنا:** الحركة تُعاد كلّ دورة، فالرسم يعود ويُرسم — وهو إيقاع التحقّق
    نفسه (يُقرأ ثم يُثبت). ولو انتهت مرّة واحدة لبقي الأصل بلا معنى بعد الثانية الأولى.
    """
    return (f'  <path d="{d}" fill="none" stroke="{stroke}" stroke-width="{width}" '
            f'stroke-linecap="round" stroke-linejoin="round" '
            f'stroke-dasharray="{length:.0f}" stroke-dashoffset="0">\n'
            f'    <animate attributeName="stroke-dashoffset" '
            f'values="{length:.0f};0;0;{length:.0f}" keyTimes="0;0.14;0.86;1" '
            f'dur="{dur + 8:.1f}s" begin="{begin:.1f}s" repeatCount="indefinite"/>\n'
            f'  </path>')


# ═══════════════════════════════════════════════════════════════════════════
# 1. atlas-cycle.svg — the Max Atlas loop, redrawn from scratch
# ═══════════════════════════════════════════════════════════════════════════
def atlas_cycle(pal, dark=True):
    W, H = 720, 690
    cx, cy, R = 360.0, 350.0, 196.0
    stages = ["Discover", "Understand", "Map", "Adapt", "Execute", "Verify", "Learn"]
    n = len(stages)
    out = []
    out.append(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" '
               f'width="{W}" height="{H}" role="img" aria-labelledby="at ac">')
    out.append('  <title id="at">Max Atlas — the loop that learns how this device works</title>')
    out.append('  <desc id="ac">Seven stages on a ring, clockwise from the top: Discover, Understand, '
               'Map, Adapt, Execute, Verify, Learn. The centre states the promise: what works on this '
               'device is proven here, not assumed. Nothing is claimed until it was read back.</desc>')
    out.append(defs_all("at", pal, card="at-card"))
    out.append(panel("at", 0, 0, W, H, 22, pal))
    out.append(edge_light(34, 6, W - 68, pal))
    out.append(breathe(614, 74, 152, pal["accent"], 0.04, 0.09, 10.0))

    # header
    out.append(text(36, 62, "Max Atlas", 38, pal["ink"], weight="700"))
    out.append(text(36, 96, "It learns how your device works before changing anything.",
                    22, pal["muted"]))

    # the track
    out.append(f'  <circle cx="{cx:.1f}" cy="{cy:.1f}" r="{R:.1f}" fill="none" '
               f'stroke="{pal["edge"]}" stroke-width="2"/>')

    # direction arrowheads at the midpoint of every gap
    for i in range(n):
        th = math.radians(-90 + (i + 0.5) * 360 / n)
        ax = cx + R * math.cos(th)
        ay = cy + R * math.sin(th)
        out.append(circle_arrow(ax, ay, math.degrees(th) + 90, pal["steel"], 0.95))

    # stations + labels
    for i, name in enumerate(stages):
        th = math.radians(-90 + i * 360 / n)
        c, s = math.cos(th), math.sin(th)
        dx, dy = cx + R * c, cy + R * s
        out.append(f'  <circle cx="{dx:.1f}" cy="{dy:.1f}" r="11" fill="{pal["accent"]}">')
        # safe SMIL: only opacity breathes, so the static state is already correct
        out.append(f'    <animate attributeName="opacity" values="1;0.55;1" keyTimes="0;0.5;1" '
                   f'dur="6s" begin="{i*0.6:.1f}s" repeatCount="indefinite"/>')
        out.append('  </circle>')
        out.append(f'  <circle cx="{dx:.1f}" cy="{dy:.1f}" r="4.5" fill="{pal["panel"]}"/>')
        lx, ly = cx + (R + 38) * c, cy + (R + 38) * s
        anchor = "start" if c > 0.28 else ("end" if c < -0.28 else "middle")
        out.append(text(lx, ly + 7, name, 22, pal["ink"], anchor=anchor, weight="600"))

    # centre card — the promise, in product words
    out.append(f'  <rect x="{cx-160:.1f}" y="{cy-72:.1f}" width="320" height="144" rx="18" '
               f'fill="url(#at-card)" stroke="{pal["edge"]}"/>')
    out.append(text(cx, cy - 26, "What works here", 26, pal["ink"], anchor="middle", weight="700"))
    out.append(text(cx, cy + 8, "is proven here.", 26, pal["accent"], anchor="middle", weight="700"))
    out.append(text(cx, cy + 46, "unproven stays unknown", 21, pal["muted"], anchor="middle"))

    # footer
    out.append(f'  <line x1="36" y1="{H-96}" x2="{W-36}" y2="{H-96}" stroke="{pal["edge"]}"/>')
    out.append(text(36, H - 62, "Atlas finds the path — and remembers it.", 21, pal["steel"]))
    out.append(text(36, H - 32, "Max AI decides when to act. Neither crosses into the other.",
                    21, pal["muted"]))
    out.append('</svg>')
    return "\n".join(out) + "\n"


# ═══════════════════════════════════════════════════════════════════════════
# 2. max-ai.svg — what Max AI does, as the user experiences it
# ═══════════════════════════════════════════════════════════════════════════
def max_ai(pal):
    W, H = 720, 660
    steps = [
        ("Notice", "reads", "the device"),
        ("Decide", "one", "change"),
        ("Ask", "safety", "first"),
        ("Verify", "read", "back"),
        ("Remember", "trust", "earned"),
    ]
    out = []
    out.append(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" '
               f'width="{W}" height="{H}" role="img" aria-labelledby="mt md">')
    out.append('  <title id="mt">Max AI — one measured change at a time</title>')
    out.append('  <desc id="md">Five steps in order: Notice, Decide, Ask, Verify, Remember. '
               'Max AI changes one thing at a time, only when the readings say so, and it keeps '
               'a record of what the device actually did afterwards.</desc>')
    out.append(defs_all("ma", pal, card="ma-card"))
    out.append(panel("ma", 0, 0, W, H, 22, pal))
    out.append(edge_light(34, 6, W - 68, pal))
    out.append(breathe(614, 74, 152, pal["accent"], 0.04, 0.09, 10.0))

    out.append(text(36, 62, "Max AI", 38, pal["ink"], weight="700"))
    out.append(text(36, 96, "One measured change at a time — never a stack of tweaks.", 22, pal["muted"]))

    # five cards
    n = len(steps)
    margin, gap = 30.0, 16.0
    cw = (W - 2 * margin - (n - 1) * gap) / n
    ch, cy0 = 176.0, 132.0
    for i, (label, cap1, cap2) in enumerate(steps):
        x = margin + i * (cw + gap)
        out.append(f'  <rect x="{x:.1f}" y="{cy0:.1f}" width="{cw:.1f}" height="{ch:.0f}" rx="16" '
                   f'fill="url(#ma-card)" stroke="{pal["edge"]}">')
        # safe SMIL: the loop runs once per cycle, and stops dead when SMIL is absent
        out.append(f'    <animate attributeName="opacity" values="1;0.62;1" keyTimes="0;0.5;1" '
                   f'dur="7s" begin="{i*0.8:.1f}s" repeatCount="indefinite"/>')
        out.append('  </rect>')
        out.append(f'  <circle cx="{x + cw/2:.1f}" cy="{cy0 + 40:.1f}" r="17" fill="{pal["accent"]}"/>')
        out.append(text(x + cw / 2, cy0 + 48, str(i + 1), 22, pal["bg"],
                        anchor="middle", weight="700"))
        out.append(text(x + cw / 2, cy0 + 96, label, 26, pal["ink"], anchor="middle", weight="600"))
        out.append(text(x + cw / 2, cy0 + 128, cap1, 21, pal["muted"], anchor="middle"))
        out.append(text(x + cw / 2, cy0 + 156, cap2, 21, pal["muted"], anchor="middle"))
        if i < n - 1:
            ax = x + cw + gap / 2
            out.append(f'  <path d="M{ax-6:.1f},{cy0 + 40:.1f} L{ax+5:.1f},{cy0 + 40:.1f} '
                       f'L{ax:.1f},{cy0 + 34:.1f} Z" fill="{pal["steel"]}"/>')

    # the difference from a preset
    y0 = cy0 + ch + 44
    out.append(text(36, y0, "Why it is not a preset", 26, pal["ink"], weight="700"))
    rows = [
        ("A preset applies the same numbers to every device.", pal["muted"]),
        ("Max AI asks what this device is doing now, changes one thing,", pal["ink"]),
        ("then measures whether it actually helped.", pal["accent"]),
    ]
    for i, (line, col) in enumerate(rows):
        out.append(text(36, y0 + 38 + i * 32, line, 21, col))

    # the promise strip
    ys = y0 + 38 + 3 * 32 + 24
    out.append(f'  <rect x="30" y="{ys:.1f}" width="{W-60:.0f}" height="112" rx="14" '
               f'fill="url(#ma-card)" stroke="{pal["edge"]}"/>')
    out.append(text(52, ys + 38, "It will never", 21, pal["clay"], weight="700"))
    out.append(text(52, ys + 72, "write a protected interface · invent a reading",
                    21, pal["muted"]))
    out.append(text(52, ys + 102, "claim a change it did not measure",
                    21, pal["muted"]))
    out.append('</svg>')
    return "\n".join(out) + "\n"


# ═══════════════════════════════════════════════════════════════════════════
# 3. banners — the hero, product words only
# ═══════════════════════════════════════════════════════════════════════════
def banner(pal, dark=True):
    """اللافتة — سطح منتج لا سطرا نصّ على مستطيل.

    **والعطب الذي أُصلح مقيس لا مُتخيَّل:** النسخة السابقة كانت ثلاثة أسطر نصّ على مستطيل مسطّح،
    وفيها **ثلث أيمن فارغ لا يحمل شيئًا** إلا دائرتين بتعتيم ٧٪. أي أنّ أوّل ما يراه الزائر كان
    نصًّا بلا علامة منتج، وصفحة كاملة من الرسم الهندسي لا يعرف منه أنّ للتطبيق هوية. فصارت
    اللافتة مركّبة من ثلاث طبقات تعمل معًا: **لوح بعمق**، و**علامة المنتج** (الدرع والتأشير —
    نفس مفردات الرماز في `ic-shield` و`ic-ask`، فلا تكون اللافتة عالمًا بصريًّا آخر)، و**إيقاع**
    (شريط يطول ويثبت، وتأشير يُرسم، ونقطة تنبض). والنصّ يبقى البطل: لا يُزاحم، ولا تُوضع معلومة
    هندسية عليه.

    **وأرقام الاختبارات والبوّابات ليست هنا عمدًا** — هي حقائق هندسية وموضعها `docs/verification.md`،
    فاللافتة ليست صفحة حالة.
    """
    W, H = 960, 280
    uid = "bn"
    out = []
    out.append(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" '
               f'width="{W}" height="{H}" role="img" aria-labelledby="bt bd">')
    out.append('  <title id="bt">MaxManager — performance control that asks before it acts</title>')
    out.append('  <desc id="bd">MaxManager, adaptive chip-aware performance control for rooted '
               'Android, with Max AI and Max Atlas, in 84 languages, and with no telemetry.</desc>')
    out.append(defs_panel(uid, pal))
    # الاسم يُمحى إليه بالكشف لا بالظهور: `clipPath` يُجرَّد على GitHub إن جُرِّد، ومرجعٌ غير موجود
    # في `clip-path` لا يقطع شيئًا — فالاسم يبقى **كاملًا ومرئيًّا** بلا الحركة. أي أنّ الزينة
    # تُضاف فوق قراءة صحيحة، ولا يُبنى عليها.
    out.append('  <defs>')
    out.append('    <clipPath id="bn-wipe">')
    out.append('      <rect x="40" y="44" width="480" height="88">')
    out.append('        <animate attributeName="width" values="0;480" keyTimes="0;1" '
               'dur="1.3s" repeatCount="1" fill="freeze"/>')
    out.append('      </rect>')
    out.append('    </clipPath>')
    out.append('  </defs>')
    out.append(panel(uid, 0, 0, W, H, 22, pal))
    # الحافة الداخليّة عند `y = 6` لا عند حدّ اللوح: البوّابة تشترط هامش `EDGE_MARGIN` (٤px)
    # عن حدود الـ`viewBox`، وقد قاست الإصدار الأوّل منها على `y = 1.6` وأبلغته — فالحدّ البنيويّ
    # عاد التصميم لا العكس.
    out.append(edge_light(34, 6, W - 68, pal))
    out.append(breathe(838, 58, 150, pal["accent"], 0.06, 0.13, 9.0))
    out.append(breathe(772, 238, 112, pal["steel"], 0.05, 0.11, 11.0))

    out.append(f'  <circle cx="880" cy="140" r="62" fill="none" stroke="{pal["steel"]}" '
               f'stroke-width="1.4" opacity="0.42"/>')
    out.append(f'  <circle cx="880" cy="140" r="46" fill="none" stroke="{pal["edge"]}" '
               f'stroke-width="1.4"/>')
    out.append(f'  <circle cx="880" cy="140" r="38" fill="{pal["accent"]}" opacity="0.08">')
    out.append('    <animate attributeName="opacity" values="0.06;0.14;0.06" '
               'keyTimes="0;0.5;1" dur="7s" repeatCount="indefinite"/>')
    out.append('  </circle>')
    out.append(f'  <path d="M880 106 910 117v22c0 16-12 28-30 34-18-6-30-18-30-34v-22z" '
               f'fill="none" stroke="{pal["steel"]}" stroke-width="2.6" opacity="0.85"/>')
    out.append(draw_in("M866 140 876 150 896 128", pal["accent"], 4.4, 48))
    out.append(tick(880, 78, 5.5, pal, dur=6.0))

    out.append('  <g clip-path="url(#bn-wipe)">')
    out.append(text(40, 112, "MaxManager", 60, pal["ink"], weight="800"))
    out.append('  </g>')
    out.append(rule(40, 130, 96, pal, height=6))
    out.append(text(40, 190, "Performance control that asks before it acts.",
                    31, pal["steel"]))
    out.append(text(40, 234, "Every switch you see works here — or it is not shown.",
                    28, pal["muted"]))
    out.append('</svg>')
    return "\n".join(out) + "\n"


# ═══════════════════════════════════════════════════════════════════════════
# 4. control-plane.svg — the one write path, in product words (no file names)
# ═══════════════════════════════════════════════════════════════════════════
def control_plane(pal):
    W, H = 720, 510
    steps = [("Your screen", "your tap"),
             ("One arbiter", "one owner"),
             ("Root bridge", "the border"),
             ("Kernel", "answers")]
    out = []
    out.append(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" '
               f'width="{W}" height="{H}" role="img" aria-labelledby="ct cd">')
    out.append('  <title id="ct">One write path</title>')
    out.append('  <desc id="cd">Every change goes through one arbiter to a single root bridge, and the '
               'device is read back afterwards. A screen can never write to the kernel directly.</desc>')
    out.append(defs_all("cp", pal, card="cp-card"))
    out.append(panel("cp", 0, 0, W, H, 22, pal))
    out.append(edge_light(34, 6, W - 68, pal))
    out.append(breathe(614, 74, 152, pal["accent"], 0.04, 0.09, 10.0))
    out.append(text(36, 62, "One write path", 38, pal["ink"], weight="700"))
    out.append(text(36, 96, "Nothing touches the kernel behind your back.", 22, pal["muted"]))

    bw, gap, bh, by = 156.0, 18.0, 116.0, 132.0
    for i, (label, caption) in enumerate(steps):
        x = 30 + i * (bw + gap)
        out.append(f'  <rect x="{x:.1f}" y="{by:.1f}" width="{bw:.1f}" height="{bh:.0f}" rx="16" '
                   f'fill="url(#cp-card)" stroke="{pal["edge"]}"/>')
        out.append(text(x + bw / 2, by + 50, label, 22, pal["ink"], anchor="middle", weight="600"))
        out.append(text(x + bw / 2, by + 86, caption, 21, pal["muted"], anchor="middle"))
        if i < 3:
            ax = x + bw + gap / 2
            out.append(f'  <path d="M{ax-7:.1f},{by + bh/2:.1f} L{ax+5:.1f},{by + bh/2:.1f} '
                       f'L{ax:.1f},{by + bh/2 - 7:.1f} Z" fill="{pal["steel"]}"/>')

    # the read-back loop, drawn as a return arc
    ry = by + bh + 40
    # **ونقطة تحكّم القوس كانت تقطع جملة العودة** — عطب مقيس لا مذوق.
    #
    # القوس يبدأ وينتهي عند `by + bh + 8` = 256، وأدنى نقطة له = `s + 0.75·(c − s)`.
    # ونقطة التحكّم `ry + 40` = 328 تعطي أدنى نقطة **310**، وصندوق جملة العودة يقابلها
    # `306.2..327.2` (سطر أساس 322 وحجم 21) ⇒ **المسار يمرّ في وسط الجملة**: قِيس فعليًّا
    # x 291.7..446.3 · y 307.8..310.0. وبـ`by + bh + 36` = 284 تكون الأدنى **277**،
    # أي فرجة **29px** فوق الجملة وتحت البطاقات (248).
    #
    # **ولم تُكشف بأداة:** `svg_review` تشترط «نصّان على خطّ أساس واحد لا يتداخلان» — فهي
    # تقيس نصًّا مع نصّ، ولا تقيس **مسارًا يمرّ في نصّ**؛ وهذا الصنف كان يمرّ صامتًا في كل
    # تشغيل حتّى رآه المالك بعينه. وقيست الـSVG الإحدى عشرة الأخرى فكانت نظيفة.
    arc_ctrl_y = by + bh + 36
    # ومسار العودة **يسير**: تحريك `stroke-dashoffset` على مسار منقّط يُبحر بالنقاط من النواة إلى
    # الشاشة — وهي بعينها القراءة المرتدّة التي يعد بها الرسم. و`<animate>` **ابنٌ للمسار** لا
    # عنصرًا مستقلًّا (القاعدة ⑤: المستقلّ يُجرَّد على GitHub فيُهمل بصمت). والساكن صحيح: بلا SMIL
    # تبقى النقاط منقّطة في مواضعها بإزاحة صفر.
    out.append(f'  <path d="M{30 + 3*(bw+gap) + bw/2:.1f},{by + bh + 8:.1f} '
               f'C{30 + 3*(bw+gap) + bw/2:.1f},{arc_ctrl_y:.1f} {30 + bw/2:.1f},{arc_ctrl_y:.1f} '
               f'{30 + bw/2:.1f},{by + bh + 8:.1f}" fill="none" stroke="{pal["accent"]}" '
               f'stroke-width="2" stroke-dasharray="6 6">')
    out.append('    <animate attributeName="stroke-dashoffset" values="0;-24" keyTimes="0;1" '
               'dur="1.6s" repeatCount="indefinite"/>')
    out.append('  </path>')
    out.append(text(W / 2, ry + 34, "then read back — a change that did not hold is not a success",
                    21, pal["accent"], anchor="middle"))

    ys = ry + 74
    out.append(f'  <rect x="30" y="{ys:.1f}" width="{W-60:.0f}" height="112" rx="14" '
               f'fill="url(#ma-card)" stroke="{pal["edge"]}"/>')
    out.append(text(52, ys + 38, "There is no shortcut", 21, pal["clay"], weight="700"))
    out.append(text(52, ys + 68, "no screen can write to the kernel itself,", 21, pal["muted"]))
    out.append(text(52, ys + 96, "and a test fails the build if one ever does", 21, pal["muted"]))
    out.append('</svg>')
    return "\n".join(out) + "\n"


# ═══════════════════════════════════════════════════════════════════════════
# 5. gates.svg — what the checks are for, not what they are called
# ═══════════════════════════════════════════════════════════════════════════
def gates_strip(pal):
    W = 720
    # وسابعَ عشر: «mark geometry» — بوّابة علامة Max AI (تكملة ١٥٣) تقرأ أرقام الرسم من الكود
    # وترسمه بمسح ضوئيّ عند ١× و٢× و٣× و٤×. **ولا تُضف بلاطة إلى هذه القائمة بيد في الـSVG**:
    # كان ذلك قد وقع فعلًا (بلاطة «screenshots» أُضيفت بيد)، فصار المولّد يكتب ١٤ والمنشور فيه ١٥ —
    # والصحيح أن القائمة هنا هي المصدر، والـSVG مُعاد توليدُه منها.
    checks = ["balanced code", "clean tree", "translations", "orphan keys", "JNI contract",
              "unused modules", "design tokens", "packaging", "resources", "RTL layout",
              "licences", "file manifest", "links resolve", "readable assets", "screenshots",
              "mark geometry"]
    cols, cw, chh, gx, gy = 3, 212.0, 62.0, 12.0, 12.0
    top = 132.0
    rows = math.ceil(len(checks) / cols)
    # الارتفاع يُحسب من الصفوف — لا يُكتب رقمًا: النسخة الأولى كانت H=400 وصفّها
    # الأخير ينتهي عند 490، فأربع بلاطات لم تُرَ قطّ في أي محرّك.
    H = int(top + rows * (chh + gy) - gy + 30)
    out = []
    out.append(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" '
               f'width="{W}" height="{H}" role="img" aria-labelledby="gt gd">')
    out.append(f'  <title id="gt">{len(checks)} checks before the compiler runs</title>')
    out.append(f'  <desc id="gd">A grid of the {len(checks)} checks that run in seconds, before any '
               'heavy build step, so a mistake is named early instead of after minutes of compiling.</desc>')
    out.append(defs_all("gt", pal, card="gt-card"))
    out.append(panel("gt", 0, 0, W, H, 22, pal))
    out.append(edge_light(34, 6, W - 68, pal))
    out.append(breathe(614, 74, 152, pal["accent"], 0.04, 0.09, 10.0))
    out.append(text(36, 62, "Checked before it is built", 38, pal["ink"], weight="700"))
    out.append(text(36, 96, f"{len(checks)} checks run in seconds, ahead of every build.", 22, pal["muted"]))
    for i, label in enumerate(checks):
        r, c = divmod(i, cols)
        x = 30 + c * (cw + gx)
        y = top + r * (chh + gy)
        out.append(f'  <rect x="{x:.1f}" y="{y:.1f}" width="{cw:.1f}" height="{chh:.0f}" rx="12" '
                   f'fill="url(#gt-card)" stroke="{pal["edge"]}"/>')
        out.append(f'  <circle cx="{x+28:.1f}" cy="{y+31:.1f}" r="7" fill="{pal["accent"]}">')
        # بداية متتالية (نمط «staggered begin»): الصفّ لا ينبض في لحظة واحدة، والترتيب يُقرأ
        # «فحوصًا تمرّ» لا وميضًا. والحركة `opacity` وحدها — لا موضع يتحرّك، فالساكن صحيح.
        out.append(f'    <animate attributeName="opacity" values="1;0.45;1" keyTimes="0;0.5;1" '
                   f'dur="6s" begin="{i * 0.35:.2f}s" repeatCount="indefinite"/>')
        out.append('  </circle>')
        out.append(text(x + 48, y + 38, label, 21, pal["ink"]))
    out.append('</svg>')
    return "\n".join(out) + "\n"


# ═══════════════════════════════════════════════════════════════════════════
# 6. locales.svg — languages, as a product fact
# ═══════════════════════════════════════════════════════════════════════════
def locales_art(pal):
    W, H = 720, 300
    names = ["English", "العربية", "简体中文", "Español", "Русский", "Français",
             "Português", "Türkçe", "Indonesia", "Deutsch"]
    out = []
    out.append(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" '
               f'width="{W}" height="{H}" role="img" aria-labelledby="lt ld">')
    out.append('  <title id="lt">84 languages, and right-to-left is first class</title>')
    out.append('  <desc id="ld">The interface ships in 84 languages plus English. Arabic, Farsi, '
               'Hebrew and Urdu are laid out right-to-left and checked on every run.</desc>')
    out.append(defs_all("lo", pal))
    out.append(panel("lo", 0, 0, W, H, 22, pal))
    out.append(edge_light(34, 6, W - 68, pal))
    out.append(breathe(614, 74, 152, pal["accent"], 0.04, 0.09, 10.0))
    out.append(text(36, 62, "84 languages", 38, pal["ink"], weight="700"))
    out.append(text(36, 96, "Arabic, Farsi, Hebrew and Urdu are laid out right-to-left.", 22, pal["muted"]))
    # two rows of names, evenly spaced
    rows = [names[:5], names[5:]]
    for r, row in enumerate(rows):
        y = 152 + r * 62
        step = (W - 60) / len(row)
        for i, n in enumerate(row):
            # وكل اسم يتنفّس بإزاحة تصاعديّة عبر اللوحتين (`staggered begin`): القراءة لا تحتاج
            # الحركة، فبقيت الأسماء هي النصّ نفسه وحركتها `opacity` وحدها — لا موضع يزحف.
            x = 30 + step * (i + 0.5)
            out.append(f'  <text x="{x:.1f}" y="{y:.1f}" font-size="22" fill="{pal["steel"]}" '
                       f'text-anchor="middle" font-family="{SANS}" opacity="1">')
            out.append(f'    <animate attributeName="opacity" values="1;0.5;1" keyTimes="0;0.5;1" '
                       f'dur="6s" begin="{(r * 5 + i) * 0.3:.1f}s" repeatCount="indefinite"/>')
            out.append(f'    {esc(n)}')
            out.append('  </text>')
    out.append(text(W / 2, 268, "+ 75 more, and English is authored by hand", 21, pal["muted"],
                    anchor="middle"))
    out.append('</svg>')
    return "\n".join(out) + "\n"


# ═══════════════════════════════════════════════════════════════════════════
# 6.5 design-language.svg — لغة التصميم معروضة كنظام، لا كذوق
# ═══════════════════════════════════════════════════════════════════════════
# **ولماذا أصلٌ كهذا:** مجموعة `VoltAgent/awesome-design-md` تبني كلّ عرضها على لوحة تُري القارئ
# الألوان والمقامات والمسافات وأدوار المحارف مجتمعة، فيفهم النظام في نظرة واحدة بدل أن يقرأ عنه.
# ولم يكن عندنا نظير: الصفحة كانت تشرح المنتج بالإنجليزية الدقيقة ولا تُري ماهيّته البصرية — وهي
# منتج يبيع نفسه بالوضوح. فصُنعت هذه اللوحة **من الرموز نفسها** (`MaxTokens.kt` · `theme/Type.kt`):
# كل رقم تراه هنا مسحوب من الشيفرة، و`tools/design_doc.py` تفشل إن انحرف أحدها.
#
# **وحدّها المعلَن:** هّذه اللوحة تُعرض بعرض الهاتف 390px، فكل نصّ فيها ≥ 21px في الـviewBox
# (720) — أي ≥ 11px عند العرض الحقيقي، وهو حدّ `svg_review`. ولذلك لا تُعرض فيها المقاسات الصغيرة
# (11sp · 12sp) بمقاسها الحقيقيّ بل بأسمائها: الرسم التوضيحي لـ`11sp` عند 11px يكون غير مقروء.
# **والخريطة لا تُقاس على العرض الحقيقيّ، والأدوار تُذكر بأسماءها ومعها ما يلزم لقراءتها.**
def design_language(pal, dark=True):
    W = 720
    out = []
    H = 1160
    out.append(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" '
               f'width="{W}" height="{H}" role="img" aria-labelledby="dl-t dl-d">')
    out.append('  <title id="dl-t">The MaxManager design language: palette, type, spacing, '
               'radii and motion, taken from the app itself</title>')
    out.append('  <desc id="dl-d">One accent that follows the user theme, three warning colours that '
               'never do, three typefaces with one job each, a single 4dp spacing scale, five '
               'meaningful corner radii, and a motion budget whose ceiling is 360 milliseconds. '
               'Every value shown is read from the app token layer.</desc>')
    out.append(defs_all("dl", pal, card="dl-card"))
    out.append(panel("dl", 0, 0, W, H, 22, pal))
    out.append(edge_light(34, 6, W - 68, pal))
    out.append(breathe(620, 70, 140, pal["accent"], 0.03, 0.07, 12.0))

    out.append(header(36, 44, "Design language",
                      "Drawn by rules, not taste — as the app's own tokens.", pal))

    def eyebrow(y, s):
        out.append(text(36, y, s, 21, pal["muted"], weight="600"))

    # ── A. اللوحة ──
    eyebrow(188, "PALETTE — ONE ACCENT, THREE FIXED WARNINGS")
    swatches = [("Accent", "your theme", pal["accent"]),
                ("Positive", pal["tonePositive"], pal["tonePositive"]),
                ("Caution", pal["toneCaution"], pal["toneCaution"]),
                ("Critical", pal["toneCritical"], pal["toneCritical"])]
    for i, (name, note, tone) in enumerate(swatches):
        x = 36 + i * 168
        # نصف قطر البلاطة 10 هو `MaxRadius.chip` — فاللوحة تُرسم بالرمز الذي تصفه.
        out.append(f'  <rect x="{x}" y="206" width="88" height="88" rx="10" fill="{tone}"/>')
        if i == 0:
            out.append(f'  <rect x="{x}" y="206" width="88" height="88" rx="10" fill="none" '
                       f'stroke="{pal["ink"]}" stroke-width="1.5" stroke-dasharray="6 5" '
                       f'opacity="0.55"/>')
        out.append(text(x, 322, name, 21, pal["ink"], weight="600"))
        out.append(text(x, 350, note, 21, pal["muted"]))
    out.append(text(36, 390, "Contrast 10.2 · 10.5 · 8.8 on the dark surface,", 21, pal["muted"]))
    out.append(text(36, 416, "6.5 · 6.4 · 8.3 on light. Only the accent moves.", 21, pal["muted"]))

    # ── B. المحارف ──
    eyebrow(462, "TYPE — THREE FACES, EACH WITH ONE JOB")
    out.append(text(36, 498, "Space Grotesk · display", 21, pal["muted"]))
    out.append(text(360, 498, "Page title", 34, pal["ink"], weight="700"))
    out.append(text(36, 556, "Manrope · body and labels", 21, pal["muted"]))
    out.append(text(360, 556, "Supporting copy", 22, pal["ink"]))
    out.append(text(36, 614, "Mono · live values only", 21, pal["muted"]))
    out.append(text(360, 614, "42.5 °C · 3.1 GHz", 30, pal["accent"], weight="600", family=MONO))

    # ── C. المسافات ──
    eyebrow(660, "SPACE — ONE 4dp SCALE")
    x = 36.0
    for step in (4, 8, 12, 16, 20, 28):
        w = step * 3
        out.append(f'  <rect x="{x:.0f}" y="692" width="{w}" height="8" rx="4" '
                   f'fill="{pal["accent"]}" opacity="0.85"/>')
        out.append(text(x, 726, str(step), 21, pal["muted"]))
        x += w + 16
    out.append(text(36, 760, "8 between rows · 20 as the page gutter · 28 between sections",
                    21, pal["steel"]))

    # ── D. أنصاف الأقطار ──
    eyebrow(800, "RADII — A ROW, A CARD AND A SHEET DIFFER")
    for i, (radius, label) in enumerate(((12, "12"), (14, "14"), (18, "18"),
                                         (22, "22"), (28, "28"))):
        x = 36 + i * 100
        out.append(f'  <rect x="{x}" y="832" width="60" height="60" rx="{radius}" '
                   f'fill="url(#dl-card)" stroke="{pal["edge"]}" stroke-width="1.5"/>')
        out.append(text(x + 30, 924, label, 21, pal["muted"], anchor="middle"))
    out.append(f'  <rect x="520" y="838" width="132" height="48" rx="24" fill="none" '
               f'stroke="{pal["accent"]}" stroke-width="1.5"/>')
    out.append(text(586, 924, "status only", 21, pal["muted"], anchor="middle"))

    # ── E. الحركة ──
    eyebrow(964, "MOTION — AT MOST 360ms, AND NO LOOPS")
    x = 36.0
    for i, ms in enumerate((90, 160, 240, 360)):
        w = ms * 0.6
        # الأعمدة تتحرّك **من قيمتها الكاملة** وتعود: أي أنّ الأصل بلا SMIL يقرأ صحيحًا تمامًا،
        # والحركة تُضاف فوق قراءة سليمة لا تُبنى عليها — نفس قاعدة بقية الأصول.
        out.append(f'  <rect x="{x:.0f}" y="996" width="{w:.0f}" height="10" rx="5" '
                   f'fill="{pal["accent"]}" opacity="0.85">')
        out.append(f'    <animate attributeName="opacity" values="0.85;0.45;0.85" keyTimes="0;0.5;1" '
                   f'dur="4.5s" begin="{i * 0.25:.2f}s" repeatCount="indefinite"/>')
        out.append('  </rect>')
        out.append(text(x, 1034, f"{ms}", 21, pal["muted"]))
        x += w + 18
    out.append(text(600, 1034, "ms", 21, pal["muted"]))

    # ── التذييل ──
    out.append(f'  <line x1="36" y1="1076" x2="{W-36}" y2="1076" stroke="{pal["edge"]}"/>')
    out.append(text(36, 1110, "Every number is read from the app's own tokens.",
                    21, pal["steel"]))
    out.append(text(36, 1136, "A gate fails when this picture and the code disagree.",
                    21, pal["muted"]))
    out.append('</svg>')
    return "\n".join(out) + "\n"


# ═══════════════════════════════════════════════════════════════════════════
# 7. domains.svg / integration.svg — آلتان كانتا مكتوبتين بيد، فصارتا من المولّد
# ═══════════════════════════════════════════════════════════════════════════
# **لماذا نُقلتا إلى هنا:** كانتا الملفّين الوحيدين في `docs/assets/` لا يولّدهما شيء — فبقيا
# بلغتين أُخريين: لوح `#12202A` لا `#151C22`، ونصف قطر ١٤ لا ٢٢، **وبلا حدّ على اللوح نفسه**
# ولا عمق. أي أنّ قارئًا يمرّ في الصفحة يرى شكلين لـ«البطاقة» بلا سبب، وهو بعينه ما يجعل الأصول
# تبدو مجموعة من مصادر مختلفة. وكون الأصل خارج المولّد يعني أيضًا أنّه خارج أيّ إعادة توليد —
# فقطع أيّ تحسين عليه نصف الشجرة. ونقلُهما هنا يُصلح السبب لا العَرَض.

def domains_art(pal):
    """عشرة مجالات — والرمز داخل كل بلاطة هو **هندسة `ic-*.svg` نفسها** مكبَّرة لا رسمًا ثانيًا.

    وهذا مقصود: القارئ يرى الرمز في قائمة النصّ وفي الصورة، فلوناه مختلفان شكلان لشيء واحد. وموضع
    الرسم يُحسب من مربّع الرمز المحفوظ `[4, 20]` لا يُخمَّن.
    """
    W = 720
    arts = icons_domains()
    captions = ["Cores · governors", "Freqs · policies", "ZRAM · swappiness",
                "Refresh · colour", "Touch · frames", "Heat · throttling",
                "Charge · bypass", "Compiler · health", "Link · scheduling",
                "Devices · effects"]
    cols, tw, th, gx, gy, top = 3, 218.0, 132.0, 17.0, 14.0, 132.0
    rows = math.ceil(len(arts) / cols)
    H = int(top + rows * (th + gy) - gy + 30)
    out = []
    out.append(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" '
               f'width="{W}" height="{H}" role="img" aria-labelledby="dt dd">')
    out.append('  <title id="dt">What you can control — ten domains</title>')
    out.append('  <desc id="dd">Ten control domains, each with the app\'s own one-line '
               'description: CPU cores and governors, GPU frequencies and policies, memory ZRAM '
               'and swappiness, display refresh and colour, responsiveness touch and frames, '
               'thermal heat and throttling, power charge and bypass, storage and compiler '
               'health, network scheduling and link state, and audio output devices with the '
               'effects the engine declares. A control the device does not expose is not '
               'shown.</desc>')
    out.append(defs_all("do", pal, card="do-card"))
    out.append(panel("do", 0, 0, W, H, 22, pal))
    out.append(edge_light(34, 6, W - 68, pal))
    out.append(breathe(614, 74, 152, pal["accent"], 0.04, 0.09, 10.0))
    out.append(text(36, 62, "What you can control", 34, pal["ink"], weight="700"))
    # والسطر التوضيحيّ **قِيس فقُصر**: البوّابة أبلغت أنّ الصيغة الأطول تنتهي ٤px خارج اللوحة
    # على عرض ٣٩٠px — وهي اللوحة التي تُقاس فعلًا لأنّ الصورة تُعرض بعرض الصفحة على الهاتف.
    out.append(text(36, 96, "Anything your device does not expose is simply not listed.",
                    21, pal["muted"]))
    # والأسماء **قصيرة معلنة** لا `title` الرمز: عنوان الرمز كان يصف فيُطيل («Storage and
    # compiler») — وهو صحيح في `ic-storage.svg` ومصادم داخل بلاطة عرضها ٢١٨. فالإسم المعلَن هنا
    # هو ما يُرسم، والتفصيل في النصّ وفي الرمز.
    names = ["CPU", "GPU", "Memory", "Display", "Responsiveness", "Thermal",
             "Power", "Storage", "Network", "Audio"]
    for i, ((_icon, _title, _desc, body), name, caption) in enumerate(
            zip(arts, names, captions)):
        r, c = divmod(i, cols)
        x = 16 + c * (tw + gx)
        y = top + r * (th + gy)
        out.append(f'  <rect x="{x:.1f}" y="{y:.1f}" width="{tw:.1f}" height="{th:.0f}" '
                   f'rx="18" fill="url(#do-card)" stroke="{pal["edge"]}" stroke-width="1.4"/>')
        # الورقة [4, 20] من شبكة 24 تُوضع عند (x+14.4, y+14.4) بمقياس ١٫٤
        # وكل بلاطة تتنفّس بإزاحة مختلفة (`staggered begin`) — فتقرأها العين "حيّة" لا "وامضة"،
        # وهذا فرق مقصود: الأعمدة المتساوية في الطور تُنتج وميضًا، والإزاحة تُنتج إيقاعًا.
        # و`<animate>` ابن لمجموعة الرمز لا عنصر مستقلّ (القاعدة ⑤)، والساكن صحيح بلا حركة.
        out.append(f'  <g transform="translate({x + 8.4:.1f},{y + 8.4:.1f}) scale(1.4)" opacity="1">')
        out.append(f'    <animate attributeName="opacity" values="1;0.55;1" keyTimes="0;0.5;1" '
                   f'dur="7s" begin="{i * 0.4:.1f}s" repeatCount="indefinite"/>')
        out.extend("    " + frag for frag in body)
        out.append('  </g>')
        out.append(text(x + 20, y + 92, name, 24, pal["ink"], weight="700"))
        out.append(text(x + 20, y + 120, caption, 21, pal["steel"]))
    out.append('</svg>')
    return "\n".join(out) + "\n"


def integration_art(pal):
    """ثلاثة مسارات للدمج، والأسماء الثلاثة التي يجب أن تتفق — بأرقام حقيقية لا مُختصرة.

    والمسارات الثلاثة والأسماء الأربعة مأخوذة من `android/aosp/` ونصوص الكود كما هي، فتظلّ صورة
    يمكن التحقّق منها عند مراجعتها مع الشجرة — لا وعدًا بصورة تقول «هكذا يُدمج».
    """
    W = 720
    paths = [
        ("Systemless module",
         "Flash the zip in Magisk or KernelSU. Nothing in /system",
         "is modified permanently, and uninstall puts it back."),
        ("AOSP integration",
         "android/aosp/: Soong files, an init service, a sepolicy",
         "domain and a privileged-permission allowlist."),
        ("KernelSU Next",
         "android/kernelsu/: the same module packaged for",
         "KernelSU Next."),
    ]
    names = [("path", "/system/bin/sys.maxmanager-service"),
             ("service", "sys.maxmanager-service"),
             ("seclabel", "u:r:maxmanager:s0"),
             ("file", "u:object_r:maxmanager_exec:s0")]
    card_h, gap, top = 116.0, 14.0, 124.0
    names_y = top + 3 * (card_h + gap)
    names_h = 210.0
    H = int(names_y + names_h + 26)
    out = []
    out.append(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" '
               f'width="{W}" height="{H}" role="img" aria-labelledby="it id">')
    out.append('  <title id="it">Integrating MaxManager into a ROM — three paths and the three '
               'names that must agree</title>')
    out.append('  <desc id="id">Three integration paths: flash the systemless module in Magisk '
               'or KernelSU; build it into an AOSP tree from android/aosp with Soong files, an '
               'init service, a sepolicy domain and a privileged-permission allowlist; or use the '
               'KernelSU Next package in android/kernelsu. The binary path, the init service name '
               'and the SELinux labels must agree exactly: /system/bin/sys.maxmanager-service, '
               'service sys.maxmanager-service, u:r:maxmanager:s0 and '
               'u:object_r:maxmanager_exec:s0.</desc>')
    out.append(defs_all("it", pal, card="it-card"))
    out.append(panel("it", 0, 0, W, H, 22, pal))
    out.append(edge_light(34, 6, W - 68, pal))
    out.append(breathe(614, 74, 152, pal["accent"], 0.04, 0.09, 10.0))
    out.append(text(36, 62, "Three integration paths", 34, pal["ink"], weight="700"))
    out.append(text(36, 96, "What must agree, and where the kit lives.", 21, pal["muted"]))
    for i, (title, line1, line2) in enumerate(paths):
        y = top + i * (card_h + gap)
        out.append(f'  <rect x="16" y="{y:.1f}" width="688" height="{card_h:.0f}" rx="18" '
                   f'fill="url(#it-card)" stroke="{pal["edge"]}" stroke-width="1.4"/>')
        out.append(f'  <rect x="16" y="{y:.1f}" width="4" height="{card_h:.0f}" rx="2" '
                   f'fill="{pal["accent"]}" opacity="0.75">')
        out.append(f'    <animate attributeName="opacity" values="0.75;0.30;0.75" '
                   f'keyTimes="0;0.5;1" dur="6s" begin="{i * 0.6:.1f}s" '
                   f'repeatCount="indefinite"/>')
        out.append('  </rect>')
        out.append(text(40, y + 42, title, 24, pal["accent"], weight="700"))
        out.append(text(40, y + 74, line1, 21, pal["steel"]))
        out.append(text(40, y + 100, line2, 21, pal["muted"]))
    out.append(f'  <rect x="16" y="{names_y:.1f}" width="688" height="{names_h:.0f}" rx="18" '
               f'fill="url(#it-card)" stroke="{pal["accent"]}" stroke-width="1.6"/>')
    out.append(text(40, names_y + 44, "The three names that must agree", 24, pal["ink"],
                    weight="700"))
    for i, (kind, value) in enumerate(names):
        y = names_y + 82 + i * 34
        out.append(text(40, y, f"{kind:<9}{value}", 21, pal["steel"], family=MONO))
    out.append(tick(688, names_y + 40, 5.5, pal, dur=7.0))
    out.append('</svg>')
    return "\n".join(out) + "\n"


# ═══════════════════════════════════════════════════════════════════════════
# 8. ic-*.svg — أيقونات سياقية: واحدة بجانب كل عنوان، وواحدة بجانب كل وصف
# ═══════════════════════════════════════════════════════════════════════════
# لماذا: الصفحة كانت تُخبر القارئ *ماذا* يقرأ بنصّ فقط، والعين تبحث عن معلَم بصريّ قبل أن تقرأ
# سطرًا. فصار لكل قسم رمزه (المعالج لصفحة المعالج، والنجمة لـMax AI)، ولكل بند في قائمة المجالات
# التسعة رمزه — فيُمسح البند بالعين لا بالقراءة.
#
# وحدودها معلَنة: شبكة 24×24، والفنّ كلّه داخل [4.2, 19.8] فلا يلامس حدًّا ولا يُقصّ، والبلاطة
# كاملة 24×24 (فتُستثنى من فحص القصّ في `svg_review` كما تُستثنى خلفية اللوحة في الأصل الكبير).
# والألوان من اللوحة نفسها: بلاطة `DARK["bg"]` · حدّها `DARK["tileEdge"]` · نعناعي `DARK["accent"]`
# (العنصر الأساس) · فولاذي `DARK["steel"]`. والاختيار مقصود: **البلاطة الداكنة تُقرأ في الوضعين**
# — الرمز يُحمَّل في `<img>` فلا يرث ألوان الصفحة، ولو رُسم بخطّ ملوّن وحده لاختفى في الوضع الفاتح.
ICON_TILE = ('<rect x="0" y="0" width="24" height="24" rx="6.5" fill="' + DARK["bg"] + '" '
             'stroke="' + DARK["tileEdge"] + '" stroke-width="1"/>')
ICON_MINT, ICON_STEEL = DARK["accent"], DARK["steel"]


def _ink(d, color=ICON_MINT, w=1.5):
    """خطّ مرسوم: نهايات وزوايا مدوّرة — فلا تظهر رؤوس حراب في رمز 20px."""
    return (f'<path d="{d}" fill="none" stroke="{color}" stroke-width="{w}" '
            f'stroke-linecap="round" stroke-linejoin="round"/>')


def _solid(d, color=ICON_MINT):
    return f'<path d="{d}" fill="{color}"/>'


def _box(x, y, w, h, rx, color=ICON_STEEL, sw=1.4, fill="none"):
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="{fill}" '
            f'stroke="{color}" stroke-width="{sw}"/>')


def _disc(cx, cy, r, color=ICON_MINT):
    return f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{color}"/>'


def _ring(cx, cy, r, color=ICON_STEEL, sw=1.4):
    return (f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="none" stroke="{color}" '
            f'stroke-width="{sw}"/>')


def icons_sections():
    """رماز الأقسام: رمز واحد لكل عنوان في الـREADME، فيُمسح الدليل بالعين لا بالقراءة."""
    return [
        ("ic-timer", "Ten seconds", "A bolt: the whole idea in ten seconds.", [
            _solid("M13.6 4.4 7.2 13.3h3.7l-0.5 6.3 6.4-8.9h-3.7z"),
        ]),
        ("ic-layers", "Three layers", "Three stacked layers: Max Atlas, the control plane, Max AI.", [
            _solid("M12 4.5 19.4 8.4 12 12.3 4.6 8.4z"),
            _ink("M4.6 12.9 12 16.6 19.4 12.9", ICON_STEEL),
            _ink("M4.6 16.2 12 19.9 19.4 16.2", ICON_STEEL),
        ]),
        ("ic-atlas", "Max Atlas", "A route between two points: the path to an interface is discovered.", [
            _ink("M6 18C9.2 18 9 12.4 12 12.4 15 12.4 14.8 6 18 6", ICON_STEEL, 1.5),
            _disc(6, 18, 1.8),
            _disc(18, 6, 1.8),
        ]),
        ("ic-ai", "Max AI", "A four-point sparkle: the Max AI mark, the AI accent.", [
            _solid("M12 4.2Q12.85 11.15 19.8 12Q12.85 12.85 12 19.8"
                   "Q11.15 12.85 4.2 12Q11.15 11.15 12 4.2z"),
        ]),
        ("ic-sliders", "Controls", "Three faders: control you set yourself.", [
            _ink("M5 7.8H19M5 12H19M5 16.2H19", ICON_STEEL),
            _disc(9.8, 7.8, 2),
            _disc(14.6, 12, 2),
            _disc(8.4, 16.2, 2),
        ]),
        ("ic-pulse", "Live readings", "A reading inside a frame: measuring, not guessing.", [
            _box(4.6, 5.4, 14.8, 13.2, 2.6),
            _ink("M6.8 12.4h2.2l1.4-3.4 2 6.4 1.4-3h3.4"),
        ]),
        ("ic-phone", "Screens", "A phone with a lit screen.", [
            _box(6.6, 4.4, 10.8, 15.2, 2.4, sw=1.5),
            _box(8.4, 7.4, 7.2, 8, 1.2, fill=ICON_MINT, sw=1),
            _ink("M10.4 17.2h3.2"),
        ]),
        ("ic-globe", "Languages", "A globe with a meridian: the interface travels.", [
            _ring(12, 12, 7.2),
            f'<ellipse cx="12" cy="12" rx="3.3" ry="7.2" fill="none" '
            f'stroke="{ICON_MINT}" stroke-width="1.4"/>',
            _ink("M5.2 12h13.6", ICON_STEEL),
        ]),
    ]


def icons_setup():
    """رماز المسار العمليّ: المتطلبات · التثبيت · مطوّرو الروم · ما لن يفعله المنتج."""
    return [
        ("ic-checklist", "Requirements", "A clipboard with a tick: what the device must be.", [
            _box(6, 5.4, 12, 14.2, 2.4),
            _box(9.4, 4.2, 5.2, 2.8, 1.2, fill=ICON_MINT, sw=1),
            _ink("M8.6 12.4 10.8 14.6 14.8 10.2", ICON_MINT, 1.6),
            _ink("M8.6 17h4.6", ICON_STEEL),
        ]),
        ("ic-download", "Install", "An arrow onto a tray: the module is flashed, not compiled by you.", [
            _ink("M12 4.6v8.4M8.6 10.2 12 13.6 15.4 10.2", ICON_MINT, 1.7),
            _ink("M5.4 15.2v1.4c0 1.6 1.2 2.8 2.8 2.8h7.6c1.6 0 2.8-1.2 2.8-2.8v-1.4",
                 ICON_STEEL),
        ]),
        ("ic-cube", "ROM developers", "An isometric cube: the same product, built into the system.", [
            _ink("M12 4.6 19.2 8.6v7.2L12 19.8 4.8 16.2V8.6z", ICON_STEEL),
            _ink("M12 4.6v7.7M4.8 8.6 12 12.3 19.2 8.6"),
        ]),
        ("ic-shield", "Never touched", "A shield you cannot switch off, with a line through it.", [
            _ink("M12 4.2 18.8 6.8v5.2c0 3.6-2.8 6.4-6.8 7.7-4-1.3-6.8-4.1-6.8-7.7V6.8z",
                 ICON_STEEL, 1.5),
            _ink("M9 15.4 15.2 9.2", ICON_MINT, 1.8),
        ]),
    ]


def icons_reference():
    """رماز المرجع: الوثائق · الأسئلة · الدعم · الرخصة."""
    return [
        ("ic-doc", "Documentation", "A page with a folded corner: where the detail lives.", [
            _ink("M6.4 4.4h7.2l4 4v11.2H6.4z", ICON_STEEL),
            _ink("M13.6 4.4v4.2h4.2", ICON_STEEL),
            _ink("M8.8 12h5.6M8.8 15.4h5.6"),
        ]),
        ("ic-question", "FAQ", "A question mark in a circle.", [
            _ring(12, 12, 7.2, ICON_STEEL, 1.5),
            _ink("M9.4 10.2c0-2.6 5.4-2.6 5.4 0.2 0 2-2.4 1.8-2.4 4.2", ICON_MINT, 1.7),
            _disc(12.4, 17.1, 1.2),
        ]),
        ("ic-bubble", "Support", "A speech bubble with three dots: a report a person reads.", [
            _ink("M6.4 5.6h11.2c1.2 0 2.2 1 2.2 2.2v6.4c0 1.2-1 2.2-2.2 2.2h-6l-3.8 3.2"
                 "v-3.2H6.4c-1.2 0-2.2-1-2.2-2.2V7.8c0-1.2 1-2.2 2.2-2.2z", ICON_STEEL),
            _disc(9.4, 11, 1.2),
            _disc(12.4, 11, 1.2),
            _disc(15.4, 11, 1.2),
        ]),
        ("ic-seal", "Licence", "A seal on a ribbon: proprietary, stated plainly.", [
            _ring(12, 9.6, 5.4, ICON_STEEL, 1.5),
            _ink("M9.8 9.8 11.4 11.4 14.4 8.2"),
            _ink("M9 14.4 8 20 12 18 16 20 15 14.4", ICON_STEEL),
        ]),
        # كأس لا شريط وسام: `ic-seal` يحمل شريطًا تحت حلقة، فلو رُسم الوسام لالتبسا في 20px.
        # والكأس تفرّدت: لا رمز آخر فيه خطّان أفقيّان تحت بعضهما (فالفحص بالعين لا بالقراءة).
        ("ic-credit", "Credits", "A trophy: the work this project thanks.", [
            # الانحناءان مكتوبان صراحةً (`c…c…`) لا بالمنعكس المختصر `s`: أداة القراءة النصّية
            # في هذه البيئة لا تعرف `s`، فرسمٌ لا يُقرأ لا يُتحقّق منه — وتناظر الكأس أصلًا صريح.
            _ink("M9 5.2h6v3.4c0 2.5-1.4 4.4-3 4.4c-1.6 0-3-1.9-3-4.4z", ICON_MINT, 1.6),
            _ink("M9 6.9H7.6Q6.4 10 9 11", ICON_STEEL, 1.5),
            _ink("M15 6.9h1.4Q17.6 10 15 11", ICON_STEEL, 1.5),
            _ink("M12 13v2.2", ICON_STEEL, 1.5),
            _ink("M9.4 15.6h5.2", ICON_STEEL, 1.5),
            _ink("M8.4 18.4h7.2", ICON_STEEL, 1.5),
        ]),
    ]


def icons_domains():
    """رماز المجالات العشرة — يأتي بجانب وصف كل مجال في قائمة «ما تتحكم فيه».

    الفرق بينها مقصود ومقيس: تسعة أشكال مختلفة بأيقونة التخزين — فيُعرَف المجال بالعين قبل
    قراءة اسمه. وكلّها خطوط ومضلّعات وبلا `A` (أقواس)، فتُقاس بمشيِّر واحد بسيط.
    """
    return [
        ("ic-cpu", "CPU", "A chip with pins: cores, governor, per-cluster bounds.", [
            _box(7, 7, 10, 10, 2, ICON_MINT, 1.6),
            _box(10.2, 10.2, 3.6, 3.6, 0.8, fill=ICON_STEEL, sw=1),
            _ink("M9.4 7V4.4M14.6 7V4.4M9.4 17v2.6M14.6 17v2.6M7 9.4H4.4M7 14.6H4.4"
                 "M17 9.4h2.6M17 14.6h2.6", ICON_STEEL, 1.3),
        ]),
        ("ic-gpu", "GPU", "A graphics card with a fan: frequencies and vendor parameters.", [
            _box(4.6, 7.4, 14.8, 9.2, 2),
            _ring(9.6, 12, 2.8, ICON_MINT),
            _ink("M14.4 10.4h3M14.4 13.6h3"),
        ]),
        ("ic-memory", "Memory", "A memory stick: compression, swappiness, reclaim.", [
            _box(4.6, 8, 14.8, 8, 1.6),
            _ink("M7.6 10.4v3.2M10.2 10.4v3.2M12.8 10.4v3.2M15.4 10.4v3.2"),
            _ink("M8 16v2.2M12 16v2.2M16 16v2.2", ICON_STEEL, 1.3),
        ]),
        ("ic-display", "Display", "A lit screen on a stand: refresh, colour, brightness.", [
            _box(4.6, 5.4, 14.8, 10.2, 2),
            _disc(12, 10.5, 2.6),
            _ink("M12 15.6v2.4M8.8 18.8h6.4", ICON_STEEL),
        ]),
        ("ic-touch", "Responsiveness", "A tap with two ripples: sampling, smoothing, frame pacing.", [
            _disc(12, 14, 2.2),
            _ink("M8 11.4C9.4 9.6 14.6 9.6 16 11.4", ICON_STEEL, 1.5),
            _ink("M5.4 9.2C7.6 6.4 16.4 6.4 18.6 9.2", ICON_STEEL, 1.5),
            _ink("M7.4 18.4h9.2", ICON_STEEL),
        ]),
        ("ic-thermal", "Thermal", "A thermometer with heat marks: zones and policy.", [
            _box(10.6, 4.4, 2.8, 10.6, 1.4, ICON_MINT, 1.5),
            _disc(12, 16.6, 3.4),
            _ink("M16.4 8.4h3M16.4 12h2M16.4 15.6h3", ICON_STEEL),
        ]),
        ("ic-battery", "Power", "A battery with a bolt: charging, bypass, battery health.", [
            _box(4.6, 8, 13, 8, 2.4),
            _box(17.6, 10.6, 1.8, 2.8, 0.9, fill=ICON_STEEL, sw=1),
            _solid("M11.8 9.6 9.2 13h2.2l-0.6 2.6 2.8-3.6h-2.2z"),
        ]),
        ("ic-storage", "Storage and compiler", "A drive with a pilot light: compilation mode, storage health.", [
            _box(4.8, 6.4, 14.4, 11.2, 2.4),
            _ink("M4.8 12.8h14.4", ICON_STEEL),
            _ink("M7 15.4h5"),
            _disc(16.4, 15.4, 1.1),
        ]),
        # والعاشرة (`AU-01`): الصوت — سمّاعة وموجتان. والموجات منحنيات `Q` لا أقواس،
        # وأبعد نقطة `19.8` (الحدّ ٢٠) لأنّ ورقة الأمان تُقاس على الإحداثيّ نفسه لا على الرسم.
        # وتفرّدت عن `ic-remember`/`ic-pulse` بالمثلّث الوحيد المائل إلى اليمين.
        ("ic-audio", "Audio", "A speaker with two waves: output devices and declared effects.", [
            _ink("M5.2 9.6h3.4l4.6-4.2v13.2l-4.6-4.2H5.2z", ICON_MINT, 1.5),
            _ink("M15.6 9.8Q17.2 12 15.6 14.2", ICON_STEEL, 1.5),
            _ink("M18 7.6Q19.8 12 18 16.4", ICON_STEEL, 1.5),
        ]),
        ("ic-network", "Network", "Three nodes on a link: congestion, SACK, scheduler tunables.", [
            _ink("M6.6 17.4 12 6.6M17.4 17.4 12 6.6M6.6 17.4h10.8", ICON_STEEL),
            _disc(12, 6.6, 2.4),
            _disc(6.6, 17.4, 1.9),
            _disc(17.4, 17.4, 1.9),
        ]),
    ]


def icons_ai_steps():
    """رماز حلقة Max AI الخمسة — بجانب كل خطوة في «راقب · قرّر · اسأل · تحقّق · تذكّر».

    والخمسة مختارة لتفترق بلا نصّ: عين (راقب) ≠ هدف (قرّر) ≠ درع بتأشير (اسأل) ≠ عدسة
    بتأشير (تحقّق) ≠ علامة كتاب (تذكّر). والحلقتان الأخيرتان هما الأقرب — فاختير للتحقّق
    مقبض خارج الدائرة وللتذكّر طرف مدبّب، فلا يُخلط بينهما في 20px.
    """
    return [
        ("ic-notice", "Notice", "An eye: it reads this device.", [
            _ink("M4.4 12C6.8 7.8 17.2 7.8 19.6 12 17.2 16.2 6.8 16.2 4.4 12z", ICON_STEEL),
            _disc(12, 12, 2.6),
        ]),
        ("ic-decide", "Decide", "A target: the smallest change that could close the gap.", [
            _ring(12, 12, 6.6),
            _ring(12, 12, 3.2, ICON_MINT, 1.4),
            _disc(12, 12, 1.3),
        ]),
        ("ic-ask", "Ask", "A shield with a tick: the safety layer answers first.", [
            _ink("M12 4.2 18.8 6.8v5.2c0 3.6-2.8 6.4-6.8 7.7-4-1.3-6.8-4.1-6.8-7.7V6.8z",
                 ICON_STEEL),
            _ink("M9.4 11.8 11.2 13.6 15 9.6", ICON_MINT, 1.7),
        ]),
        ("ic-verify", "Verify", "A lens with a tick: the value is read back.", [
            _ring(10.8, 10.8, 5.4, ICON_STEEL, 1.5),
            _ink("M14.8 14.8 19 19", ICON_STEEL, 1.6),
            _ink("M8.6 10.9 10.4 12.7 13.4 9.4"),
        ]),
        ("ic-remember", "Remember", "A bookmark: the outcome is kept.", [
            _ink("M7.2 4.6h9.6v15l-4.8-4-4.8 4z", ICON_MINT, 1.6),
        ]),
    ]


def all_icons():
    """كل الرماز في قائمة واحدة: ما يُولَّد هو ما يُكتب على القرص، وما يُقاس."""
    return (icons_sections() + icons_setup() + icons_reference()
            + icons_domains() + icons_ai_steps())


def icon_svg(name, title, desc, body):
    """ملفّ SVG واحد لكل رمز: `<title>` و`<desc>` كما تشترط البوّابة ⑩، والبلاطة أولا."""
    out = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" '
           f'height="24" role="img" aria-labelledby="{name}-t {name}-d">',
           f'  <title id="{name}-t">{title}</title>',
           f'  <desc id="{name}-d">{desc}</desc>',
           f'  {ICON_TILE}']
    out += ["  " + frag for frag in body]
    out.append("</svg>")
    return "\n".join(out) + "\n"


def write(name, body):
    path = os.path.join(OUT, name)
    with open(path, "w", encoding="utf-8") as f:
        f.write(body)
    return path


if __name__ == "__main__":
    files = [
        ("atlas-cycle.svg", atlas_cycle(DARK)),
        ("max-ai.svg", max_ai(DARK)),
        ("banner-dark.svg", banner(DARK)),
        ("banner-light.svg", banner(LIGHT, dark=False)),
        ("control-plane.svg", control_plane(DARK)),
        ("gates.svg", gates_strip(DARK)),
        ("locales.svg", locales_art(DARK)),
        ("design-language.svg", design_language(DARK)),
        ("design-language-light.svg", design_language(LIGHT, dark=False)),
        ("domains.svg", domains_art(DARK)),
        ("integration.svg", integration_art(DARK)),
    ]
    # الرماز السياقية: تُولَّد من التعريف نفسه الذي يُقّاس، فلا يُكتب رسم بيد في ملفّ SVG
    files += [(f"{n}.svg", icon_svg(n, t, d, b)) for n, t, d, b in all_icons()]
    for name, body in files:
        p = write(name, body)
        # read it back and verify the claims
        back = open(p, encoding="utf-8").read()
        assert back == body, f"{name}: read-back mismatch"
        assert "<script" not in back and "keyframes" not in back, f"{name}: unsupported construct"
        import re
        assert "<svg" in back and back.rstrip().endswith("</svg>"), f"{name}: not closed"
        for m in re.finditer(r'keyTimes="([^"]+)"', back):
            ks = [float(v) for v in re.split(r'[;\s]+', m.group(1).strip())]
            assert ks[0] == 0 and ks[-1] == 1, f"{name}: keyTimes must start 0 and end 1"
        for m in re.finditer(r'values="([^"]+)"', back):
            assert ';' in m.group(1), f"{name}: single-keyframe values"
        print(f"  ✓ {p:34s} {len(back):>6} bytes · "
              f"{back.count('<text')} text · {back.count('<animate')} SMIL")
    print("all assets regenerated and read back")
