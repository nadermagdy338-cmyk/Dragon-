#!/usr/bin/env python3
"""render_design_mockups.py — لقطات مستند التصميم المرسومة لمشروع MaxManager.

الموضع في العقد: `docs/ai/ui-ux-spec.md` §14 — «لقطات مرسومة تُجمَّع في مستند تصميم» هي
وسيلة حكم المالك على النتيجة **قبل** مزيد من الكود. وهذه الأداة ترسمها برمجيًّا، لا يدويًّا،
حتى تبقى اللقطة **مرتبطة بلغة التصميم نفسها**:

  - الألوان كلها مشتقّة من بذرة الثيم `MaxManagerBrandSeed = #007F78` بقاعدة G1 (السطح
    المصبوغ: محايد ممزوج ٧٪ بلون المفتاح) — لا لون مختار داخل الأداة؛
  - التباين يُحسب هنا (WCAG 2.1) ويُكتب في `contrast-report.txt` بجانب اللقطات، فالقبول
    ليس ذوقًا: الرقم هو الشرط (≥ 4.5:1 للنصّ العادي و≥ 3:1 للنصّ الكبير)؛
  - **وصدقٌ لا يتغيّر:** الأرقام في اللقطات **قيم عيّنة للتصميم** بأشكال البيانات الحقيقية
    (`DashboardState` و`MaxAiState`) — لا قياسات جهاز، ولا تُقرأ اللقطة على أنها قياس. وكل
    لقطة تحمل هذه الجملة في تذييلها.

والخطوط بديلة عن خطوط التطبيق الحقيقية (`Fonts.kt`: Space Grotesk / Manrope / JetBrains Mono
عبر Google Fonts) — هنا NotoKufiArabic للعربية وDejaVu للاتينية والأرقام. فالذي يُقاس باللقطة
**الهيكل والألوان والتباين والكثافة** لا رسم الحرف.

الاستعمال:
    python3 tools/render_design_mockups.py                  # كل اللقطات إلى docs/ai/design
    python3 tools/render_design_mockups.py --only home      # الرئيسية فقط
    python3 tools/render_design_mockups.py --self-test      # يقيس الأداة نفسها أولًا
    python3 tools/render_design_mockups.py --verify         # يقيس مفردات العمق في اللقطة
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

# ────────────────────────────────────────────────────────────────────────────
# الألوان — مشتقّة من بذرة الثيم، لا مختارة
# ────────────────────────────────────────────────────────────────────────────

BRAND_SEED = (0x00, 0x7F, 0x78)          # MaxManagerBrandSeed (Theme.kt)
SECONDARY = (0x59, 0x6F, 0x6B)           # Theme.kt: secondary
TERTIARY = (0xB3, 0x6A, 0x42)            # Theme.kt: tertiary (نحاسي)
ERROR = (0xBA, 0x1A, 0x1A)               # Material error

SURFACE_HUE = 0.07                        # SurfaceHueFraction (NeuralDashboardKit.kt)


def mix(a: tuple, b: tuple, t: float) -> tuple:
    """مزج لونين — نفس معنى `lerp` في المكتبة."""
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def lighten(c: tuple, t: float) -> tuple:
    return mix(c, (255, 255, 255), t)


def darken(c: tuple, t: float) -> tuple:
    return mix(c, (0, 0, 0), t)


def _lum(c: tuple) -> float:
    def channel(v: float) -> float:
        v /= 255.0
        return v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4

    r, g, b = (channel(x) for x in c)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contrast(a: tuple, b: tuple) -> float:
    """نسبة التباين WCAG 2.1 — 21:1 لأسود على أبيض."""
    la, lb = _lum(a), _lum(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)


class Scheme:
    """لوحة وضع واحد (داكن مصبوغ أو فاتح) كما يقررها ثيم المستخدم."""

    def __init__(self, dark: bool) -> None:
        self.dark = dark
        if dark:
            self.bg = darken(mix((0x0A, 0x0F, 0x0F), BRAND_SEED, 0.05), 0.0)
            self.panel_top = mix((0x1B, 0x22, 0x22), BRAND_SEED, SURFACE_HUE)
            self.panel = mix((0x15, 0x1B, 0x1B), BRAND_SEED, SURFACE_HUE)
            self.tile = mix((0x22, 0x29, 0x29), BRAND_SEED, SURFACE_HUE * 0.7)
            self.border = (0x2C, 0x36, 0x35)
            self.text = (0xE6, 0xED, 0xEC)
            self.muted = (0x90, 0xA1, 0x9F)
            # شبكة الرسم/التدرّج: في المكتبة `onSurfaceVariant` بشفافية ١٣٪ فوق السطح.
            self.grid = mix(self.panel, self.text, 0.17)
            self.accent = lighten(BRAND_SEED, 0.42)      # primary في الداكن فاتح كـmaterialkolor
            self.accent_alt = lighten(TERTIARY, 0.35)
            self.ok = lighten(SECONDARY, 0.45)
            self.warn = lighten(TERTIARY, 0.35)
            self.danger = lighten(ERROR, 0.4)
        else:
            self.bg = mix((0xF5, 0xF7, 0xF7), BRAND_SEED, 0.04)
            self.panel_top = mix((0xFF, 0xFF, 0xFF), BRAND_SEED, SURFACE_HUE)
            self.panel = mix((0xFC, 0xFE, 0xFE), BRAND_SEED, SURFACE_HUE)
            self.tile = mix((0xE9, 0xEE, 0xED), BRAND_SEED, SURFACE_HUE * 0.7)
            self.border = (0xC5, 0xD0, 0xCE)
            self.text = (0x10, 0x18, 0x17)
            self.muted = (0x4E, 0x5F, 0x5C)
            self.grid = mix(self.panel, self.text, 0.13)
            self.accent = darken(BRAND_SEED, 0.16)       # primary في الفاتح غامق بما يكفي
            self.accent_alt = darken(TERTIARY, 0.1)
            self.ok = darken(SECONDARY, 0.1)
            self.warn = darken(TERTIARY, 0.1)
            self.danger = ERROR


# ────────────────────────────────────────────────────────────────────────────
# النصّ — تشكيل العربية عند توفره
# ────────────────────────────────────────────────────────────────────────────

_AR_RESHAPER = None
_AR_DISPLAY = None
try:  # اختياري: اللقطة العربية تحتاجه، واللاتينية لا
    import arabic_reshaper
    from bidi.algorithm import get_display

    _AR_RESHAPER = arabic_reshaper
    _AR_DISPLAY = get_display
except ImportError:  # pragma: no cover
    pass

FONT_CANDIDATES = {
    "ar": [
        "/usr/share/fonts/truetype/noto/NotoKufiArabic-Regular.ttf",
        "/usr/share/fonts/truetype/noto/NotoKufiArabic-Bold.ttf",
    ],
    "lat": [
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    ],
    "mono": [
        "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf",
    ],
}

_font_cache: dict[tuple[str, int], ImageFont.FreeTypeFont] = {}


def font(kind: str, size: int) -> ImageFont.FreeTypeFont:
    key = (kind, size)
    if key not in _font_cache:
        path = next((p for p in FONT_CANDIDATES[kind] if Path(p).exists()), None)
        if path is None:
            sys.exit(f"no font for {kind}; edit FONT_CANDIDATES")
        _font_cache[key] = ImageFont.truetype(path, size)
    return _font_cache[key]


def shape(s: str, rtl: bool) -> str:
    if rtl and _AR_RESHAPER is not None:
        return _AR_DISPLAY(_AR_RESHAPER.reshape(s))
    return s


# ────────────────────────────────────────────────────────────────────────────
# لوحة الرسم — وحدة ١dp = ٣ بكسل (1080 عرضًا = 360dp)
# ────────────────────────────────────────────────────────────────────────────

SCALE = 3
PAGE_W = 1080
MARGIN = 48


def dp(v: float) -> int:
    return round(v * SCALE)


class Sheet:
    """صفحة بمحور طولي واحد؛ والعناصر تُرسم من اليمين في RTL."""

    def __init__(self, height: int, rtl: bool, scheme: Scheme, footnote: str) -> None:
        self.rtl = rtl
        self.s = scheme
        self.img = Image.new("RGB", (PAGE_W, height), scheme.bg)
        self.draw = ImageDraw.Draw(self.img)
        self.footnote = footnote

    # ---- نص ----
    def text(self, x: int, y: int, s: str, size: int, color: tuple, kind: str = "lat",
             bold: bool = False, align: str = "start", mono: bool = False) -> int:
        f = font("mono" if mono else ("ar" if self.rtl and kind == "ar" else "lat"), size)
        shown = shape(s, self.rtl)
        width = self.draw.textlength(shown, font=f)
        if align == "start":
            left = x if not self.rtl else x - width
        elif align == "end":
            left = x - width if not self.rtl else x
        else:
            left = x - width / 2
        self.draw.text((left, y), shown, font=f, fill=color)
        return width

    def text_end(self, x: int, y: int, s: str, size: int, color: tuple, mono: bool = False) -> None:
        """نصّ مثبَّت عند الحافة البعيدة (اليسار في RTL)."""
        f = font("mono" if mono else ("ar" if self.rtl else "lat"), size)
        shown = shape(s, self.rtl)
        width = self.draw.textlength(shown, font=f)
        left = x - width if not self.rtl else x
        self.draw.text((left, y), shown, font=f, fill=color)

    # ---- أشكال ----
    def rrect(self, box: tuple, fill: tuple, radius: int, outline: tuple | None = None, width: int = 1) -> None:
        self.draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)

    # ---- العمق: ظلّ مُلوَّن · هالة ركنية · لمعة حافة (نفس مفردات NeuralDepth.kt) ----

    def _blend(self, box: tuple, layer: Image.Image) -> None:
        """يركّب طبقة RGBA على منطقة من الصفحة بلا مساس بما حولها."""
        self.img.paste(
            Image.alpha_composite(self.img.crop(box).convert("RGBA"), layer).convert("RGB"),
            (box[0], box[1]),
        )

    def shadow(self, box: tuple, tint: tuple, blur: int = 9, alpha: int = 62) -> None:
        """ظلّ **بلون المفتاح** لا أسود: الظلّ الأسود يبدو اتساخًا على سطح مصبوغ."""
        pad = dp(12)
        w, h = box[2] - box[0], box[3] - box[1]
        layer = Image.new("RGBA", (w + pad * 2, h + pad * 2), (0, 0, 0, 0))
        ImageDraw.Draw(layer).rounded_rectangle(
            (pad, pad + dp(2), w + pad, h + pad + dp(2)), radius=dp(24), fill=tint + (alpha,)
        )
        layer = layer.filter(ImageFilter.GaussianBlur(blur))
        self._blend((box[0] - pad, box[1] - pad, box[2] + pad, box[3] + pad), layer)

    def aura(self, box: tuple, accent: tuple, strength: float = 1.0, radius: int = dp(24)) -> None:
        """هالتان شعاعيتان: قوية من **ركن القراءة** (يمين في RTL)، خافتة من الركن المقابل."""
        w, h = box[2] - box[0], box[3] - box[1]
        if w <= 0 or h <= 0:
            return
        span = max(w, h)
        front = (w, 0) if self.rtl else (0, 0)
        back = (0, h) if self.rtl else (w, h)
        layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        draw = ImageDraw.Draw(layer)
        for cx, cy, reach, tone, peak in (
            (front[0], front[1], span * 0.78, accent, 0.22 * strength),
            (back[0], back[1], span * 0.55, accent, 0.11 * strength),
        ):
            steps = 20
            for i in range(steps, 0, -1):
                r = reach * i / steps
                a = int(255 * peak * (1 - i / steps) ** 1.7)
                if a <= 0:
                    continue
                draw.ellipse((cx - r, cy - r, cx + r, cy + r), fill=tone + (min(a, 255),))
        mask = Image.new("L", (w, h), 0)
        ImageDraw.Draw(mask).rounded_rectangle((0, 0, w - 1, h - 1), radius=radius, fill=255)
        self._blend(box, Image.composite(layer, Image.new("RGBA", (w, h), (0, 0, 0, 0)), mask))

    def sheen(self, box: tuple, height: int | None = None) -> None:
        """لمعة الحافة العليا: الضوء من فوق — وهي ما يجعل السطح مقببًا لا مسطّحًا."""
        hairline = height or max(1, dp(0.6))
        light = mix(self.s.panel_top, (255, 255, 255), 0.55 if not self.s.dark else 0.14)
        self.draw.rectangle((box[0] + dp(6), box[1], box[2] - dp(6), box[1] + hairline), fill=light)

    def panel(self, y: int, height: int, accent: tuple | None = None, glow: bool = True) -> tuple:
        box = (MARGIN, y, PAGE_W - MARGIN, y + height)
        top = (box[0], box[1], box[2], box[1] + height // 2)
        bottom = (box[0], box[1] + height // 2, box[2], box[3])
        self.shadow(box, accent or self.s.accent, alpha=70 if accent else 46)
        self.draw.rounded_rectangle(box, radius=dp(24), fill=self.s.panel)
        self.draw.rectangle(top, fill=self.s.panel_top)
        if glow:
            self.aura(box, accent or self.s.accent, 1.0 if accent else 0.55)
        self.sheen(box)
        self.draw.rounded_rectangle(box, radius=dp(24), fill=None, outline=accent or self.s.border, width=dp(0.8))
        return box

    def ring(self, cx: int, cy: int, radius: int, fraction: float | None, accent: tuple, label: str,
             sub: str) -> None:
        """حلقة نسبة — الرقم نجمة البطاقة (G4)، والمقام معلوم ولا يُملأ بلا مقام.

        وفيها نفس مفردات `NeuralRing`: تدرّج دائم (يُضاء بما بلغه القوس)، ومينا داخلية،
        وهالتان، ورأس قوس مضيء. والتدرّج يُرسم **دائمًا** فيقرأ الصفر «أداة بلا قيمة» لا
        «حلقة فارغة».
        """
        import math

        stroke = dp(9)
        extent = radius * 2
        box = (cx - radius, cy - radius, cx + radius, cy + radius)
        start, span = 135, 270  # يبدأ كجيم القياس المألوفة
        drawn = None if fraction is None else span * max(0.0, min(1.0, fraction))
        self.draw.arc(box, 0, 360, fill=self.s.tile, width=stroke)
        # ١ · التدرّج: خمس علامات على الأرباع، داخل الحلقة كعدّاد حقيقي.
        for step in range(5):
            at = step / 4.0
            angle = math.radians(start + span * at)
            cos, sin = math.cos(angle), math.sin(angle)
            lit = drawn is not None and drawn >= span * at - 1e-4
            self.draw.line(
                (
                    cx + cos * (radius - stroke * 2.0), cy + sin * (radius - stroke * 2.0),
                    cx + cos * (radius - stroke * 1.35), cy + sin * (radius - stroke * 1.35),
                ),
                fill=accent if lit else self.s.grid,
                width=max(1, round(stroke * 0.26)),
            )
        if fraction is not None:
            # ٢ · المينا: قرص خافت تحت الرقم يجعل المقياس وعاءً لا حلقة فارغة.
            well = radius - stroke * 1.1
            self.draw.ellipse((cx - well, cy - well, cx + well, cy + well),
                              fill=mix(self.s.panel, accent, 0.10))
            # ٣ · هالتان: عريضة خافتة ثم قريبة أقوى.
            self.draw.arc(box, start, start + span * max(0.0, min(1.0, fraction)),
                          fill=mix(self.s.panel, accent, 0.30), width=int(stroke * 2.6))
            self.draw.arc(box, start, start + span * max(0.0, min(1.0, fraction)),
                          fill=mix(self.s.panel, accent, 0.55), width=int(stroke * 1.9))
            # ٤ · القوس المقيس.
            self.draw.arc(box, start, start + span * max(0.0, min(1.0, fraction)), fill=accent, width=stroke)
            # ٥ · رأس القوس: نقطة وهالة ضوء حولها.
            tip = math.radians(start + span * max(0.0, min(1.0, fraction)))
            tx, ty = cx + radius * math.cos(tip), cy + radius * math.sin(tip)
            halo = stroke * 1.9
            self.draw.ellipse((tx - halo, ty - halo, tx + halo, ty + halo),
                              fill=mix(self.s.panel, accent, 0.34))
            self.draw.ellipse((tx - stroke * .62, ty - stroke * .62,
                               tx + stroke * .62, ty + stroke * .62), fill=accent)
        value = "—" if fraction is None else label
        fs = max(dp(15), min(dp(30), round(extent * 0.22)))
        self.text(cx, cy - fs // 2 - dp(4), value, fs, self.s.text if fraction is not None else self.s.muted,
                  mono=True, align="center")
        self.text(cx, cy + fs // 2 + dp(2), sub, dp(9), self.s.muted, align="center")

    def track(self, x: int, y: int, w: int, fraction: float | None, accent: tuple) -> None:
        h = dp(6)
        self.rrect((x, y, x + w, y + h), self.s.tile, h // 2)
        # حوض غائر: حدّ رفيع حول المسار يجعل التعبئة تبدو داخله.
        self.rrect((x, y, x + w, y + h), None, h // 2,
                   outline=mix(self.s.border, self.s.panel, 0.45), width=1)
        if fraction:
            fw = max(h, int(w * max(0.0, min(1.0, fraction))))
            if self.rtl:
                x0 = x + w - fw
            else:
                x0 = x
            self.rrect((x0, y, x0 + fw, y + h), accent, h // 2)

    def pill(self, x: int, y: int, s: str, accent: tuple, filled: bool = False) -> int:
        f = font("ar" if self.rtl else "lat", dp(11))
        shown = shape(s, self.rtl)
        w = self.draw.textlength(shown, font=f) + dp(24)
        h = dp(26)
        left = x - w if self.rtl else x
        self.rrect((left, y, left + w, y + h),
                   mix(accent, self.s.panel, 0.80) if filled else self.s.panel,
                   h // 2, outline=accent, width=dp(1))
        if filled:
            # الشارة الممتلئة مصبوغة بتدرّج: قطعة واحدة مع هوية الشاشة بلا أن تصرخ.
            self.rrect((left + dp(1), y + dp(1), left + w - dp(1), y + h // 2),
                       mix(accent, self.s.panel, 0.66), h // 2)
        self.draw.ellipse((left + dp(10), y + dp(10), left + dp(16), y + dp(16)), fill=accent)
        self.draw.text((left + dp(22), y + dp(5)), shown, font=f, fill=accent)
        return w

    def gradient_text(self, x: int, y: int, s: str, size: int, colors: tuple) -> None:
        """نصّ بتدرّج لونَي الهوية — علامة التطبيق في لوح البطل."""
        f = font("ar" if self.rtl else "lat", size)
        shown = shape(s, self.rtl)
        w = max(1, int(self.draw.textlength(shown, font=f)))
        h = size * 2
        mask = Image.new("L", (w + 8, h + 8), 0)
        ImageDraw.Draw(mask).text((0, 0), shown, font=f, fill=255)
        grad = Image.new("RGB", mask.size, colors[0])
        painter = ImageDraw.Draw(grad)
        for i in range(mask.size[0]):
            painter.line((i, 0, i, mask.size[1]), fill=mix(colors[0], colors[1], i / (mask.size[0] - 1)))
        left = x - w if self.rtl else x
        self.img.paste(grad, (int(left), y), mask)

    def ribbon(self, box: tuple, accent: tuple, second: tuple) -> None:
        """شريط الهوية: خيط ٢dp بلونَي العلامة يتلاشى على طول الحافة العليا."""
        thickness = max(2, dp(0.7))
        w = box[2] - box[0]
        grad = Image.new("RGB", (w, thickness), accent)
        painter = ImageDraw.Draw(grad)
        for i in range(w):
            t = i / max(1, w - 1)
            # الأولى ← الثانية ← الشفاف (يذوب في السطح عند الطرف البعيد)
            tone = mix(accent, second, min(1.0, t / 0.5)) if t < 0.5 else mix(second, self.s.panel_top, (t - 0.5) / 0.5)
            painter.line((i, 0, i, thickness), fill=tone)
        mask = Image.new("L", (w, thickness), 0)
        pixels = mask.load()
        for i in range(w):
            fade = int(255 * max(0.0, 1.0 - i / max(1, w - 1)))
            for j in range(thickness):
                pixels[i, j] = fade
        self.img.paste(grad, (box[0], box[1]), mask)

    def caption(self, x: int, y: int, s: str, accent: tuple) -> int:
        return self.text(x, y, s.upper() if not self.rtl else s, dp(10), accent)

    def finish(self, path: Path) -> None:
        self.draw.rectangle((0, self.img.height - dp(30), PAGE_W, self.img.height), fill=self.s.tile)
        self.text(MARGIN, self.img.height - dp(22), self.footnote, dp(9), self.s.muted)
        self.img.save(path)


# ────────────────────────────────────────────────────────────────────────────
# قيم العيّنة — أشكال البيانات الحقيقية (ليست قياسات)
# ────────────────────────────────────────────────────────────────────────────

SAMPLE = {
    "device": ("Galaxy S23 Ultra", "جالكسي S23 ألترا"),
    "soc": ("Snapdragon 8 Gen 2", "سناب دراغون ٨ الجيل ٢"),
    "limiter": ("Baseline", "خط الأساس"),
    "cpu": 0.47,
    "cpu_clock": ("2.41 GHz", "3.20 GHz"),
    "gpu": 0.12,
    "gpu_clock": ("670 MHz", "1.00 GHz"),
    "cores": [0.35, 0.62, 0.48, 0.40, 0.71, 0.55, 0.83, 0.66],
    "core_clocks": ["1.80", "2.40", "2.40", "2.40", "2.80", "2.80", "3.20", "3.20"],
    "ram": (0.68, "21.8 / 32 GB"),
    "zram": (0.43, "2.1 / 5.0 GB"),
    "storage": (0.52, "Free: 180.3 GB · Total: 226.5 GB"),
    "battery": (0.78, "8.4 W"),
    "temps": [("CPU", 47), ("GPU", 43), ("Skin", 36), ("Battery", 39)],
    "history_cpu": [32, 38, 45, 51, 47, 42, 55, 61, 58, 49, 44, 47],
    "history_gpu": [8, 12, 9, 15, 22, 18, 12, 9, 14, 11, 10, 12],
    "uptime": "3h 42m",
    "display": ("1080×2400", "120 Hz"),
    "net": ("1.2 MB/s", "180 KB/s"),
    "power": ("840 mA", "4.21 V"),
    "android": "15 (API 35)",
    "model": "SM-S918B",
}


def temperature_accent(scheme: Scheme, c: int) -> tuple:
    """نفس منطق `temperatureAccent`: هادئ < 43، دافئ < 48، وما فوق خطير."""
    if c < 43:
        return scheme.ok
    if c < 48:
        return scheme.warn
    return scheme.danger


def home_footnote(rtl: bool) -> str:
    return ("قيمة عيّنة للتصميم — أشكال البيانات من DashboardState/MaxAiState · لا قياسات جهاز"
            if rtl else
            "Sample values for the design — data shapes from DashboardState/MaxAiState · not device measurements")


# ────────────────────────────────────────────────────────────────────────────
# مقاطع الرئيسية (§10.2) — اثنا عشر مقطعًا بالترتيب نفسه
# ────────────────────────────────────────────────────────────────────────────

def draw_home(path: Path, rtl: bool, dark: bool) -> None:
    s = Scheme(dark)
    page = Sheet(dp(2020), rtl, s, home_footnote(rtl))
    # إضاءة الصفحة: هالة واحدة خلف كل شيء من حافة القراءة (نفس `neuralPageBackdrop` في
    # المكتبة) — فالخلفية ليست مسطّحة بل مضاءة، والمحتوى يُرسم فوقها.
    page.aura((0, 0, PAGE_W, page.img.height), s.accent, strength=0.5, radius=0)
    d = page.draw
    y = MARGIN
    T = lambda key: SAMPLE[key][0 if not rtl else 1]

    # 1 · لوح البطل: العلامة بتدرّج لونَي الهوية + شريط الهوية + حالة المحرّك + الفعلان
    hero = page.panel(y, dp(76), accent=s.accent)
    page.ribbon(hero, s.accent, s.accent_alt)
    page.gradient_text(hero[0] + dp(16) if not rtl else hero[2] - dp(16), y + dp(22),
                       "MAX" if not rtl else "ماكس", dp(28), (s.accent, s.accent_alt))
    page.pill(hero[2] - dp(112), y + dp(24), "Module active" if not rtl else "الوحدة نشطة", s.ok, filled=True)
    for i, label in enumerate(("P", "S")):
        bx = hero[2] - dp(60) + i * dp(44) if not rtl else hero[0] + dp(16) + i * dp(44)
        page.rrect((bx, y + dp(19), bx + dp(38), y + dp(57)), s.tile, dp(13), outline=s.border, width=dp(0.7))
        page.text(bx + dp(19), y + dp(32), label, dp(12), s.muted, align="center")
    y += dp(88)

    # 2 · الهوية: اسم الجهاز + الشريحة + كلمة المحدِّد
    box = page.panel(y, dp(108), accent=mix(s.accent, s.panel, 0.55))
    page.text(box[0] + dp(20), y + dp(18), T("device"), dp(20), s.text)
    page.text(box[0] + dp(20), y + dp(52), T("soc"), dp(12), s.muted)
    page.pill(box[2] - dp(20) if rtl else box[2] - dp(150), y + dp(38),
              T("limiter"), s.accent, filled=True)
    y += dp(120)

    # 3 · التحكّم: سلّم الملفات + شريط الذكاء
    box = page.panel(y, dp(150))
    x0, x1 = box[0] + dp(20), box[2] - dp(20)
    page.text(x0 if not rtl else x1, y + dp(16), "Base profile" if not rtl else "الملف الأساسي",
              dp(12), s.text, align="start")
    # السلّم: ثلاثة مقاطع، «متوازن» مضاء
    seg_y = y + dp(46)
    seg_w = (x1 - x0 - dp(8)) // 3
    labels = (["Performance", "Balanced", "Battery Saver"] if not rtl
              else ["أداء", "متوازن", "توفير"])
    for i, lab in enumerate(labels):
        sx = (x1 - seg_w * (i + 1) - dp(4) * i) if rtl else (x0 + (seg_w + dp(4)) * i)
        lit = i == 1
        page.rrect((sx, seg_y, sx + seg_w, seg_y + dp(38)),
                   mix(s.accent, s.panel, 0.84) if lit else s.tile,
                   dp(11), outline=s.accent if lit else None, width=dp(1))
        page.text(sx + seg_w / 2, seg_y + dp(9), lab, dp(12),
                  s.accent if lit else s.muted, align="center")
    ai_y = seg_y + dp(52)
    d.line((x0, ai_y - dp(6), x1, ai_y - dp(6)), fill=s.border, width=1)
    page.draw.ellipse((x0 if not rtl else x1 - dp(8), ai_y + dp(4),
                       (x0 if not rtl else x1 - dp(8)) + dp(8), ai_y + dp(12)), fill=s.ok)
    page.text(x0 + dp(18) if not rtl else x1 - dp(18), ai_y,
              "Max AI · objective: keep the frame steady" if not rtl
              else "ماكس · الهدف: إبقاء الإطار ثابتًا", dp(11), s.text, align="start")
    page.text_end(x1 if not rtl else x0, ai_y, "82%" if not rtl else "٨٢٪", dp(11), s.accent, mono=True)
    y += dp(162)

    # 4 · المعالج والرسوم جنبًا إلى جنب (طلب صريح)
    card_w = (PAGE_W - 2 * MARGIN - dp(12)) // 2
    for i, key in enumerate(("cpu", "gpu")):
        frac = SAMPLE[key]
        cx0 = (PAGE_W - MARGIN - card_w * (i + 1) - dp(12) * i) if rtl else (MARGIN + (card_w + dp(12)) * i)
        box = (cx0, y, cx0 + card_w, y + dp(200))
        page.rrect(box, s.panel, dp(24), outline=s.border, width=dp(1))
        name = ("CPU" if key == "cpu" else "GPU")
        page.text(box[0] + dp(18) if not rtl else box[2] - dp(18), y + dp(16), name, dp(10),
                  s.accent if key == "cpu" else s.accent_alt, align="start")
        page.ring((box[0] + box[2]) // 2, y + dp(105), dp(48), frac,
                  s.accent if key == "cpu" else s.accent_alt,
                  f"{round(frac * 100)}%", name)
        clock = SAMPLE["cpu_clock"] if key == "cpu" else SAMPLE["gpu_clock"]
        page.text((box[0] + box[2]) // 2, y + dp(168), f"{clock[0]} / {clock[1]}", dp(9), s.muted,
                  mono=True, align="center")
    y += dp(212)

    # 5 · مصفوفة الأنوية: ثمانية أعمدة (٤ بالصف)
    box = page.panel(y, dp(170))
    page.text(box[0] + dp(20) if not rtl else box[2] - dp(20), y + dp(16),
              "Core matrix" if not rtl else "مصفوفة الأنوية", dp(15), s.text, align="start")
    page.text_end(box[2] - dp(20) if not rtl else box[0] + dp(20), y + dp(20),
                  "8 cores" if not rtl else "٨ أنوية", dp(10), s.muted)
    col_w = dp(56)
    gap = ((box[2] - box[0]) - dp(40) - col_w * 4) // 3
    for row in range(2):
        for col in range(4):
            i = row * 4 + col
            cx = (box[2] - dp(20) - col_w * (col + 1) - gap * col) if rtl else (box[0] + dp(20) + (col_w + gap) * col)
            base = y + dp(150) if row == 0 else y + dp(78)
            top = base - dp(44)
            page.rrect((cx, top, cx + col_w, base), s.tile, dp(6))
            h = round(dp(44) * SAMPLE["cores"][i])
            page.rrect((cx, base - h, cx + col_w, base), mix(s.accent, s.tile, 0.2), dp(6))
            page.text(cx + col_w / 2, base + dp(2), SAMPLE["core_clocks"][i], dp(8), s.muted,
                      mono=True, align="center")
    y += dp(182)

    # 6 · الذاكرة: RAM | ZRAM (بطاقتا دائرة متجاورتان)
    half = (PAGE_W - 2 * MARGIN - dp(12)) // 2
    for i, (key, name, accent) in enumerate((
            ("ram", "RAM", s.warn), ("zram", "ZRAM", s.accent_alt))):
        frac, sub = SAMPLE[key]
        cx0 = (PAGE_W - MARGIN - half * (i + 1) - dp(12) * i) if rtl else (MARGIN + (half + dp(12)) * i)
        page.rrect((cx0, y, cx0 + half, y + dp(150)), s.panel, dp(24), outline=s.border, width=dp(1))
        page.text(cx0 + dp(18) if not rtl else cx0 + half - dp(18), y + dp(14), name, dp(10), accent, align="start")
        page.ring(cx0 + half // 2, y + dp(72), dp(38), frac, accent, f"{round(frac * 100)}%", sub)
    y += dp(162)

    # 7 · السعة: التخزين | البطارية
    for i, (key, name, accent) in enumerate((
            ("storage", "Storage" if not rtl else "التخزين", s.accent),
            ("battery", "Battery" if not rtl else "البطارية", s.ok))):
        frac, sub = SAMPLE[key]
        cx0 = (PAGE_W - MARGIN - half * (i + 1) - dp(12) * i) if rtl else (MARGIN + (half + dp(12)) * i)
        page.rrect((cx0, y, cx0 + half, y + dp(150)), s.panel, dp(24), outline=s.border, width=dp(1))
        page.text(cx0 + dp(18) if not rtl else cx0 + half - dp(18), y + dp(14), name, dp(10), accent, align="start")
        page.ring(cx0 + half // 2, y + dp(72), dp(38), frac, accent, f"{round(frac * 100)}%", sub)
    y += dp(162)

    # 8 · الحرارة: أربع بلاطات + شارة الحكم + زر اختيار المجسّات
    box = page.panel(y, dp(132))
    head_accent = temperature_accent(s, SAMPLE["temps"][3][1])
    page.text(box[0] + dp(20) if not rtl else box[2] - dp(20), y + dp(16),
              "System vitals" if not rtl else "مجسّات النظام", dp(15), s.text, align="start")
    page.text(box[0] + dp(20) if not rtl else box[2] - dp(20), y + dp(42),
              "Pinned sensors · tap to choose" if not rtl else "المجسّات المثبَّتة · اللمسة للاختيار",
              dp(10), s.muted, align="start")
    page.pill(box[2] - dp(150) if rtl else box[2] - dp(150), y + dp(14),
              "Stable" if not rtl else "مستقر", s.ok)
    # زر الاختيار
    bx = box[2] - dp(48) if not rtl else box[0] + dp(20)
    page.rrect((bx, y + dp(14), bx + dp(28), y + dp(42)), s.tile, dp(9))
    page.text(bx + dp(14), y + dp(19), "≡", dp(12), s.muted, align="center")
    tile_w = (box[2] - box[0] - dp(40) - dp(24)) // 4
    for i, (name, temp) in enumerate(SAMPLE["temps"]):
        tx = (box[2] - dp(20) - tile_w * (i + 1) - dp(8) * i) if rtl else (box[0] + dp(20) + (tile_w + dp(8)) * i)
        page.rrect((tx, y + dp(64), tx + tile_w, y + dp(112)), s.tile, dp(12))
        page.text(tx + dp(12) if not rtl else tx + tile_w - dp(12), y + dp(70), name, dp(9),
                  temperature_accent(s, temp), align="start")
        page.text(tx + dp(12) if not rtl else tx + tile_w - dp(12), y + dp(84), f"{temp}°", dp(14),
                  s.text, mono=True, align="start")
    y += dp(144)

    # 9 · السجل: مخطط + ثلاثة صفوف تصنيف (متوسط النافذة)
    box = page.panel(y, dp(232))
    page.text(box[0] + dp(20) if not rtl else box[2] - dp(20), y + dp(16),
              "Performance history" if not rtl else "سجل الأداء", dp(15), s.text, align="start")
    page.text(box[0] + dp(20) if not rtl else box[2] - dp(20), y + dp(42),
              "Last 36 samples · windowed averages" if not rtl else "آخر ٣٦ عيّنة · متوسط النافذة",
              dp(10), s.muted, align="start")
    chart = (box[0] + dp(20), y + dp(64), box[2] - dp(20), y + dp(140))
    d.rectangle(chart, fill=None)
    for g in range(1, 4):
        gy = chart[1] + (chart[3] - chart[1]) * g // 4
        d.line((chart[0], gy, chart[2], gy), fill=s.border, width=1)
    for series, accent in ((SAMPLE["history_cpu"], s.accent), (SAMPLE["history_gpu"], s.accent_alt)):
        pts = []
        n = len(series)
        for i, v in enumerate(series):
            px = chart[2] - (chart[2] - chart[0]) * i // (n - 1) if rtl else chart[0] + (chart[2] - chart[0]) * i // (n - 1)
            py = chart[3] - (chart[3] - chart[1]) * v // 100
            pts.append((px, py))
        d.line(pts, fill=accent, width=dp(2.5), joint="curve")
    rows = (("CPU", 52, s.accent), ("GPU", 18, s.accent_alt),
            ("RAM" if not rtl else "الذاكرة", 64, s.warn))
    ry = y + dp(152)
    for name, avg, accent in rows:
        page.rrect((box[0] + dp(20), ry + dp(4), box[0] + dp(26), ry + dp(10)), accent, dp(3))
        page.text(box[0] + dp(36) if not rtl else box[2] - dp(36), ry, name, dp(11), s.text, align="start")
        page.text_end(box[2] - dp(20) if not rtl else box[0] + dp(20), ry, f"{avg}%", dp(11), s.text, mono=True)
        page.track(box[0] + dp(20), ry + dp(20), box[2] - box[0] - dp(40), avg / 100, accent)
        ry += dp(26)
    y += dp(244)

    # 10 · تفاصيل الجهاز: صفّا النظام + شبكة قراءات
    box = page.panel(y, dp(220))
    page.text(box[0] + dp(20) if not rtl else box[2] - dp(20), y + dp(16),
              "Device details" if not rtl else "تفاصيل الجهاز", dp(15), s.text, align="start")
    rows = (("Android" if not rtl else "إصدار أندرويد", SAMPLE["android"]),
            ("Model" if not rtl else "موديل الجهاز", SAMPLE["model"]))
    ry = y + dp(52)
    for name, value in rows:
        page.text(box[0] + dp(20) if not rtl else box[2] - dp(20), ry, name, dp(11), s.muted, align="start")
        page.text_end(box[2] - dp(20) if not rtl else box[0] + dp(20), ry, value, dp(11), s.text, mono=True)
        ry += dp(28)
    facts = (("Uptime" if not rtl else "التشغيل", SAMPLE["uptime"]),
             ("Resolution" if not rtl else "الدقة", SAMPLE["display"][0]),
             ("Refresh" if not rtl else "التحديث", SAMPLE["display"][1]),
             ("Down" if not rtl else "تنزيل", SAMPLE["net"][0]),
             ("Up" if not rtl else "رفع", SAMPLE["net"][1]),
             ("Density" if not rtl else "الكثافة", "420 dpi"),
             ("Current" if not rtl else "التيار", SAMPLE["power"][0]),
             ("Voltage" if not rtl else "الجهد", SAMPLE["power"][1]))
    fw = (box[2] - box[0] - dp(40) - dp(16)) // 3
    for i, (name, value) in enumerate(facts[:6]):
        col, row = i % 3, i // 3
        fx = (box[2] - dp(20) - fw * (col + 1) - dp(8) * col) if rtl else (box[0] + dp(20) + (fw + dp(8)) * col)
        fy = y + dp(116) + row * dp(50)
        page.rrect((fx, fy, fx + fw, fy + dp(42)), s.tile, dp(12))
        page.text(fx + dp(12) if not rtl else fx + fw - dp(12), fy + dp(5), name, dp(9), s.muted, align="start")
        page.text(fx + dp(12) if not rtl else fx + fw - dp(12), fy + dp(19), value, dp(12), s.text,
                  mono=True, align="start")
    y += dp(232)

    # 11 · النشاط: مشهد واحد + مُنتقى المشاهد
    box = page.panel(y, dp(120))
    page.text(box[0] + dp(20) if not rtl else box[2] - dp(20), y + dp(16),
              "Activity" if not rtl else "النشاط", dp(15), s.text, align="start")
    scene = ("Profile raised for a heavy app — measured" if not rtl
             else "رُفع الملف لتطبيق ثقيل — قِيس")
    page.text(box[0] + dp(20) if not rtl else box[2] - dp(20), y + dp(48), scene, dp(11), s.muted, align="start")
    page.text(box[0] + dp(20) if not rtl else box[2] - dp(20), y + dp(74),
              "Auto · Apps · Max AI · Manual" if not rtl else "تلقائي · تطبيقات · ماكس · يدوي",
              dp(10), s.accent, align="start")
    y += dp(132)

    # 12 · صفّ الأوامر: خمس وجهات مضغوطة
    box = page.panel(y, dp(112))
    page.text(box[0] + dp(20) if not rtl else box[2] - dp(20), y + dp(16),
              "Command deck" if not rtl else "منصة التحكم", dp(15), s.text, align="start")
    chips = (["Thermal", "Power", "Apps", "Advanced", "Settings"] if not rtl
             else ["حرارة", "طاقة", "تطبيقات", "متقدّم", "إعدادات"])
    accents = [s.warn, s.ok, s.accent, s.accent_alt, s.muted]
    cw = dp(116)
    for i, (lab, ac) in enumerate(zip(chips, accents)):
        cx = (box[2] - dp(20) - cw * (i + 1) - dp(10) * i) if rtl else (box[0] + dp(20) + (cw + dp(10)) * i)
        page.rrect((cx, y + dp(48), cx + cw, y + dp(96)), mix(ac, s.panel, 0.86), dp(18), outline=mix(ac, s.panel, 0.5), width=dp(1))
        page.text(cx + cw / 2, y + dp(64), lab, dp(11), ac, align="center")
    y += dp(112)

    page.finish(path)


# ────────────────────────────────────────────────────────────────────────────
# ورقة المكوّنات وورقة الحالات
# ────────────────────────────────────────────────────────────────────────────

def draw_components(path: Path, rtl: bool, dark: bool) -> None:
    s = Scheme(dark)
    page = Sheet(dp(1300), rtl, s, home_footnote(rtl))
    y = MARGIN
    page.text(MARGIN if not rtl else PAGE_W - MARGIN, y,
              "Component sheet — MaxKit families (§8)" if not rtl
              else "ورقة المكوّنات — عائلات MaxKit (§8)", dp(18), s.text, align="start")
    y += dp(56)

    # عائلة أ: بطاقات الدوائر
    box = page.panel(y, dp(240))
    page.text(box[0] + dp(20) if not rtl else box[2] - dp(20), y + dp(16),
              "A · Gauge cards" if not rtl else "أ · بطاقات الدوائر", dp(12), s.accent, align="start")
    page.ring(PAGE_W // 4, y + dp(140), dp(64), 0.68, s.accent, "68%",
              "21.8 / 32 GB" if rtl else "Memory")
    page.ring(3 * PAGE_W // 4, y + dp(140), dp(64), None, s.muted, "—",
              "GPU busy" if not rtl else "مشغولة الرسوم")
    y += dp(252)

    # عائلة ب + ج: بطاقة إحصاء بصفوف تصنيف
    box = page.panel(y, dp(230))
    page.text(box[0] + dp(20) if not rtl else box[2] - dp(20), y + dp(16),
              "B/C · Stat card + category rows" if not rtl
              else "ب/ج · بطاقة إحصاء وصفوف تصنيف", dp(12), s.accent, align="start")
    rows = (("CPU", 52, s.accent), ("GPU", 18, s.accent_alt), ("RAM", 64, s.warn))
    ry = y + dp(56)
    for name, avg, accent in rows:
        page.rrect((box[0] + dp(20), ry + dp(4), box[0] + dp(26), ry + dp(10)), accent, dp(3))
        page.text(box[0] + dp(36) if not rtl else box[2] - dp(36), ry, name, dp(11), s.text, align="start")
        page.text_end(box[2] - dp(20) if not rtl else box[0] + dp(20), ry, f"{avg}%", dp(11), s.text, mono=True)
        page.track(box[0] + dp(20), ry + dp(20), box[2] - box[0] - dp(40), avg / 100, accent)
        ry += dp(40)
    # بلاطة قراءة + صف معلومة
    page.rrect((box[0] + dp(20), ry + dp(6), box[0] + dp(230), ry + dp(62)), s.tile, dp(12))
    page.text(box[0] + dp(32) if not rtl else box[0] + dp(218), ry + dp(12),
              "Download" if not rtl else "تنزيل", dp(9), s.muted, align="start")
    page.text(box[0] + dp(32) if not rtl else box[0] + dp(218), ry + dp(28), "1.2 MB/s", dp(14),
              s.text, mono=True, align="start")
    page.text(box[2] - dp(230) if not rtl else box[2] - dp(20), ry + dp(14),
              "Free" if not rtl else "الحرّ", dp(11), s.muted, align="start")
    page.text_end(box[2] - dp(20) if not rtl else box[2] - dp(230), ry + dp(14),
                  "180.3 GB", dp(11), s.text, mono=True)
    y += dp(242)

    # عائلة و: صفّ الأوامر + حوار اختيار المجسّات
    box = page.panel(y, dp(330))
    page.text(box[0] + dp(20) if not rtl else box[2] - dp(20), y + dp(16),
              "F · Command row + sensor picker" if not rtl
              else "و · صفّ الأوامر وحوار المجسّات", dp(12), s.accent, align="start")
    # الحوار مصغَّر داخل الورقة
    dlg = (box[0] + dp(40), y + dp(52), box[2] - dp(40), y + dp(310))
    page.rrect(dlg, s.panel_top, dp(24), outline=s.border, width=dp(1))
    page.text(dlg[0] + dp(20) if not rtl else dlg[2] - dp(20), dlg[1] + dp(16),
              "Pinned sensors" if not rtl else "المجسّات المثبَّتة", dp(14), s.text, align="start")
    page.text(dlg[0] + dp(20) if not rtl else dlg[2] - dp(20), dlg[1] + dp(40),
              "Choose what this grid shows — up to 4" if not rtl
              else "اختر ما تعرضه الشبكة — حتى ٤", dp(9), s.muted, align="start")
    opts = [("CPU", 47, True), ("GPU", 43, True), ("Skin", 36, True),
            ("Battery", 39, True), ("Charger", 31, False)]
    oy = dlg[1] + dp(72)
    for name, temp, pinned in opts:
        ac = temperature_accent(s, temp)
        page.draw.ellipse((dlg[0] + dp(20) if not rtl else dlg[2] - dp(28), oy + dp(4),
                           dlg[0] + dp(28) if not rtl else dlg[2] - dp(20), oy + dp(12)), fill=ac)
        page.text(dlg[0] + dp(40) if not rtl else dlg[2] - dp(48), oy, name, dp(11), s.text, align="start")
        page.text_end(dlg[2] - dp(56) if not rtl else dlg[0] + dp(56), oy, f"{temp}°", dp(10), s.muted, mono=True)
        bx = dlg[2] - dp(28) if not rtl else dlg[0] + dp(20)
        page.rrect((bx, oy - dp(2), bx + dp(18), oy + dp(16)),
                   mix(ac, s.panel, 0.82) if pinned else s.tile, dp(9), outline=ac if pinned else s.border)
        if pinned:
            page.text(bx + dp(9), oy - dp(1), "✓", dp(11), ac, align="center")
        oy += dp(38)
    y += dp(342)

    # عائلة ز: بنية الشاشات الفرعية — نفس السطوح التي ترسمها الرئيـسية، عبر `ui/design`
    # (`MaxSection` · `MaxGroup` · `MaxRow`). وهي المُشتركة التي تصل ٢٧ شاشة فتكون شاشة
    # واحدة في المنتج لا نظامان.
    box = page.panel(y, dp(232))
    page.text(box[0] + dp(20) if not rtl else box[2] - dp(20), y + dp(16),
              "G · Sub-screen structure (ui/design)" if not rtl
              else "ز · بنية الشاشات الفرعية", dp(12), s.accent, align="start")
    # ترويسة مقطع: شرطة ملوّنة + عنوان
    ry = y + dp(48)
    page.rrect((box[0] + dp(20), ry + dp(2), box[0] + dp(24), ry + dp(18)), s.accent, dp(2))
    page.text(box[0] + dp(32) if not rtl else box[2] - dp(32), ry,
              "Thermal control" if not rtl else "التحكّم الحراري", dp(14), s.text, align="start")
    ry += dp(30)
    # لوحة مجموعة بثلاثة صفوف: رقعة أيقونة متدرجة + عنوان + قيمة + سهم
    group = (box[0] + dp(20), ry, box[2] - dp(20), ry + dp(128))
    page.rrect(group, mix(s.panel, s.accent, 0.05), dp(22), outline=s.border, width=dp(0.7))
    rows = (("Governor" if not rtl else "الموزّع", "schedutil", True),
            ("Max frequency" if not rtl else "التردّد الأقصى", "3.20 GHz", False),
            ("State" if not rtl else "الحالة", "—", False))
    row_y = group[1] + dp(10)
    for label, value, chevron in rows:
        chip = (group[0] + dp(12), row_y + dp(6), group[0] + dp(46), row_y + dp(40))
        page.rrect(chip, mix(s.accent, s.panel, 0.80), dp(12), outline=mix(s.accent, s.panel, 0.60), width=dp(0.7))
        page.text(chip[0] + dp(17), row_y + dp(14), "•", dp(12), s.accent, align="center")
        page.text(group[0] + dp(58) if not rtl else group[2] - dp(58), row_y + dp(15),
                  label, dp(12), s.text, align="start")
        page.text_end(group[2] - dp(40) if not rtl else group[0] + dp(40), row_y + dp(15),
                      value, dp(11), s.muted, mono=True)
        if chevron:
            page.text(group[2] - dp(26) if not rtl else group[0] + dp(26), row_y + dp(14),
                      "›" if not rtl else "‹", dp(14), s.muted, align="center")
        row_y += dp(38)
    y += dp(244)

    # **لا هيكل تحميل** (قرار المالك): لا مستطيلات فارغة تنتظر البيانات — الصفحة تُرسم فورًا،
    # وما لم يُقرأ بعد يُقال `—` بلون خافت. فورقة المكوّنات لا ترسم هيكلًا لأن المنتج لا يرسمه.
    page.finish(path)


def draw_states(path: Path) -> None:
    """ورقة الحالات — بالإنجليزية وحدها (نصوص تقنية)، وضوح الحالات هو المقياس."""
    s = Scheme(True)
    page = Sheet(dp(760), False, s, home_footnote(False))
    y = MARGIN
    page.text(MARGIN, y, "States — §11 (dark, tinted)", dp(18), s.text)
    y += dp(56)
    states = [
        ("Applying…", "profile 2 → 3", s.accent, False),
        ("Applied", "governor written & verified", s.ok, True),
        ("Failed — retry", "write refused by kernel", s.danger, True),
        ("Safety engaged", "thermal ceiling crossed — throttle kept", s.warn, True),
        ("Unavailable", "GPU node not exposed — card hidden", s.muted, False),
    ]
    for title, sub, accent, filled in states:
        box = page.panel(y, dp(84), accent=mix(accent, s.panel, 0.5))
        page.text(box[0] + dp(20), y + dp(18), title, dp(13), accent)
        page.text(box[0] + dp(20), y + dp(46), sub, dp(10), s.muted)
        page.pill(box[2] - dp(120), y + dp(26), "state", accent, filled=filled)
        y += dp(96)
    page.finish(path)


# ────────────────────────────────────────────────────────────────────────────
# تقرير التباين + الذات
# ────────────────────────────────────────────────────────────────────────────

def write_contrast_report(out: Path) -> bool:
    lines = ["تقرير التباين WCAG 2.1 — محسوب لا مُقدَّر · الحدّ: 4.5:1 نصّ عادي و3:1 نصّ كبير",
             ""]
    ok_all = True
    for dark in (True, False):
        s = Scheme(dark)
        name = "داكن مصبوغ" if dark else "فاتح"
        pairs = [
            ("نصّ/سطح", s.text, s.panel, 4.5),
            ("نصّ/خلفية", s.text, s.bg, 4.5),
            ("تسمية خافتة/سطح", s.muted, s.panel, 4.5),
            ("لهجة/سطح (نصّ كبير)", s.accent, s.panel, 3.0),
            ("لهجة ثانية/سطح", s.accent_alt, s.panel, 3.0),
            ("حالة+تحذير/سطح", s.warn, s.panel, 3.0),
            ("خطر/سطح", s.danger, s.panel, 3.0),
            ("نجاح/سطح", s.ok, s.panel, 3.0),
        ]
        lines.append(f"== {name} ==")
        for label, fg, bg, need in pairs:
            ratio = contrast(fg, bg)
            ok = ratio >= need
            ok_all = ok_all and ok
            verdict = "PASS" if ok else "FAIL"
            lines.append(f"  [{verdict}] {label}: {ratio:.2f}:1 (الحد {need})")
        lines.append("")
    lines.append("النتيجة: " + ("كل الأزواج فوق حدودها" if ok_all else "زوج تحت حدّه — لا يُعتمد"))
    (out / "contrast-report.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
    return ok_all


def verify_home(out: Path) -> int:
    """يقيس **أن مفردات العمق وصلت إلى اللقطة**، لا أن الرسم انتهى بلا استثناء.

    هذا هو الفرق بين «شغّلت الأداة» و«اللقطة تحمل ما أقوله عنها»: الأربعة المقيسة هنا
    هي نفسها التي في الكود (`NeuralDepth.kt` + `NeuralRing`) — ولذلك تفشل هذه الدالة إن
    عاد أحدهم ليرسم لوحًا مسطحًا بلا هالة.
    """
    path = out / "home-dark-rtl-ar.png"
    img = Image.open(path).convert("RGB")
    s = Scheme(True)

    def dist(a: tuple, b: tuple) -> float:
        return sum((a[i] - b[i]) ** 2 for i in range(3)) ** 0.5

    failures = 0

    def check(name: str, ok: bool, measured: str) -> None:
        nonlocal failures
        print(f"  [{'ok' if ok else 'FAIL'}] {name} — {measured}")
        if not ok:
            failures += 1

    # 1 · شريط الهوية على الحافة العليا للوح البطل (2dp × عرض اللوح).
    ribbon = min(
        (dist(img.getpixel((x, MARGIN + 1)), s.accent), x)
        for x in range(MARGIN + dp(8), MARGIN + dp(160), 3)
    )
    check("شريط الهوية على حافة لوح البطل", ribbon[0] < 90, f"أقرب مسافة للهجة {ribbon[0]:.0f}")

    # 2 · الهالة: اللوح مصبوغ أكثر عند **ركن القراءة** (يمين في RTL) من ركنه المقابل.
    #     والمقارنة تكون مقابل السطح المسطّح **عند الارتفاع نفسه** — فاللوح نصفه العلوي
    #     `panel_top` ونصفه السفلي `panel`، ومقارنة النصفين بلون واحد تقيس التدرّج لا الهالة.
    hero = (MARGIN, MARGIN, PAGE_W - MARGIN, MARGIN + dp(76))

    def flat_at(y: int) -> tuple:
        mid = (hero[1] + hero[3]) // 2
        return s.panel_top if y < mid else s.panel

    def tinted(quad_x: int) -> float:
        w = (hero[2] - hero[0]) // 4
        x0 = hero[0] + quad_x * (hero[2] - hero[0] - w)
        total = 0.0
        for x in range(x0, x0 + w, 4):
            for y in range(hero[1] + dp(6), hero[3] - dp(4), 4):
                total += dist(img.getpixel((x, y)), flat_at(y))
        return total

    front, back = tinted(1), tinted(0)  # RTL ⇒ الركن القوي يمين = الربع الأخير
    check("هالة اللوح (ركن القراءة أقوى من المقابل)", front > back, f"أمامي {front:.0f} مقابل {back:.0f}")

    # 3 · لمعة الحافة العليا: أوّل صفّ داخل لوح عادي أفتح من لون سطحه.
    plain_top = MARGIN + dp(120)  # أعلى لوح الهوية (بلا شريط هوية)
    row = [img.getpixel((x, plain_top)) for x in range(MARGIN + dp(40), PAGE_W - MARGIN - dp(40), 5)]
    lighter = sum(1 for c in row if c[0] + c[1] + c[2] > s.panel_top[0] + s.panel_top[1] + s.panel_top[2])
    check("لمعة الحافة العليا في لوح مجاور", lighter > len(row) * 0.6,
          f"{lighter}/{len(row)} بكسل أفتح من السطح")

    # 3ب · العلامة بتدرّج لونَي الهوية: بكسلات تقع على القطعة (لهجة ← لهجة ثانية) داخل اللوح.
    def segment_distance(c: tuple) -> float:
        a, b = s.accent, s.accent_alt
        ab = [b[i] - a[i] for i in range(3)]
        ap = [c[i] - a[i] for i in range(3)]
        denom = sum(v * v for v in ab) or 1
        t = max(0.0, min(1.0, sum(ap[i] * ab[i] for i in range(3)) / denom))
        return sum((ap[i] - t * ab[i]) ** 2 for i in range(3)) ** 0.5

    brand = 0
    for x in range(hero[0] + dp(8), hero[2] - dp(8), 2):
        for y in range(hero[1] + dp(10), hero[3] - dp(10), 2):
            if segment_distance(img.getpixel((x, y))) < 60:
                brand += 1
    check("العلامة بتدرّج لونَي الهوية", brand > 200, f"{brand} بكسل على قطعة الهوية")

    # 4 · التدرّج داخل الحلقة: خمس علامات على قوس ٢٧٠° — وسطهندسية بطاقة CPU كما ترسمها
    #     `draw_home` نفسها (RTL: بطاقة CPU في اليمين)، فلا يُخمّن موضعٌ تخمينًا.
    import math

    card_w = (PAGE_W - 2 * MARGIN - dp(12)) // 2
    row_y = MARGIN + dp(88) + dp(120) + dp(162)
    cpu_cx0 = PAGE_W - MARGIN - card_w  # اللقطة المُتحقَّقة RTL ⇒ بطاقة CPU في اليمين
    cpu_center = (cpu_cx0 + card_w // 2, row_y + dp(105))
    radius, stroke = dp(48), dp(9)
    found = 0
    for step in range(5):
        angle = math.radians(135 + 270 * step / 4)
        mid = radius - stroke * 1.7
        px = int(cpu_center[0] + math.cos(angle) * mid)
        py = int(cpu_center[1] + math.sin(angle) * mid)
        if dist(img.getpixel((px, py)), s.panel) > 12:
            found += 1
    check("علامات التدرّج داخل حلقة CPU", found == 5, f"{found}/5 علامة مرئية")

    print(f"قياس اللقطة: {'كل المفردات وصلت' if failures == 0 else f'{failures} مفردة غائبة'}")
    return 1 if failures else 0


def self_test() -> int:
    failures = 0

    def check(name: str, condition: bool) -> None:
        nonlocal failures
        print(f"  [{'ok' if condition else 'FAIL'}] {name}")
        if not condition:
            failures += 1

    print("قياس الأداة نفسها:")
    check("نسبة أبيض/أسود = 21:1", abs(contrast((255, 255, 255), (0, 0, 0)) - 21.0) < 0.01)
    check("مزج الطرفين يحفظ النهايات", mix((10, 20, 30), (200, 100, 50), 0.0) == (10, 20, 30))
    check("مزج التمام يعطي الثاني", mix((10, 20, 30), (200, 100, 50), 1.0) == (200, 100, 50))
    check("تشكيل العربية يعيد سلسلة", isinstance(shape("حرارة", True), str))
    check("بدون تشكيل تبقى كما هي", shape("CPU", False) == "CPU")
    for dark in (True, False):
        s = Scheme(dark)
        mode = "داكن" if dark else "فاتح"
        check(f"{mode}: نصّ/سطح ≥ 4.5", contrast(s.text, s.panel) >= 4.5)
        check(f"{mode}: لهجة/سطح ≥ 3", contrast(s.accent, s.panel) >= 3.0)
        check(f"{mode}: خافت/سطح ≥ 4.5", contrast(s.muted, s.panel) >= 4.5)
    print(f"النتيجة: {'كل القياسات سليمة' if failures == 0 else f'{failures} فشل'}")
    return 1 if failures else 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", default="docs/ai/design", help="مجلد الإخراج")
    parser.add_argument("--only", help="home|components|states")
    parser.add_argument("--self-test", action="store_true", help="يقيس الأداة نفسها وينصرف")
    parser.add_argument("--verify", action="store_true", help="يقيس مفردات العمق في اللقطة وينصرف")
    args = parser.parse_args()

    if args.self_test:
        return self_test()

    if args.verify:
        return verify_home(Path(args.out))

    if _AR_RESHAPER is None and not args.only == "states":
        print("تحذير: arabic-reshaper غير مثبَّت — اللقطات العربية ستُرسم بلا تشكيل", file=sys.stderr)

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    todo = args.only or "all"

    if todo in ("all", "home"):
        for rtl, dark, name in (
            (True, True, "home-dark-rtl-ar"),
            (False, True, "home-dark-ltr-en"),
            (True, False, "home-light-rtl-ar"),
            (False, False, "home-light-ltr-en"),
        ):
            draw_home(out / f"{name}.png", rtl, dark)
            print(f"رُسم {name}.png")
    if todo in ("all", "components"):
        for rtl, dark, name in (
            (True, True, "components-dark-rtl-ar"),
            (False, False, "components-light-ltr-en"),
        ):
            draw_components(out / f"{name}.png", rtl, dark)
            print(f"رُسم {name}.png")
    if todo in ("all", "states"):
        draw_states(out / "states-dark-en.png")
        print("رُسم states-dark-en.png")

    if write_contrast_report(out):
        print("التباين: كل الأزواج فوق حدودها — contrast-report.txt")
        return 0
    print("التباين: زوج تحت حدّه — راجع contrast-report.txt")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
