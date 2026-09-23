#!/usr/bin/env python3
"""Read the text inside an image: download → preprocess → OCR → pick the best variant.

Why this exists
---------------
This repository's agent sessions can fetch web *pages* but not *images*: a screenshot sent
as a link (Google Play, ibb.co, a store listing) never reached the model, so every review of
a UI reference was done by measuring geometry — panel edges and dividers — and guessing the
labels. That is exactly the kind of "I did not read it but I will describe it anyway" this
project forbids everywhere else.

So the reading is done here, by tools that do not need eyes. Several renderings of the same
image (as-is, inverted, contrast-stretched, upscaled, binarised) are OCR'd and the one with
the highest mean word confidence wins, because a screenshot's polarity, scale and contrast
are not known in advance and one fixed pipeline reads some of them as noise.

Two engines, because they fail differently:

  * `tesseract` (default) — fast, excellent on Latin UI text and numbers, weak on small
    anti-aliased Arabic; needs the `tessdata_best` models to be usable at all.
  * `easyocr` — a neural detector+recogniser, far better on Arabic UI labels, much slower on
    CPU. Use it when the screenshot matters and the text is Arabic.

`--self-test` is the honesty check for the whole thing: it renders known Arabic and English
strings with Pillow (shaped properly through Raqm), OCRs them back and reports how much of
the known text was recovered. An engine that cannot read its own rendered samples must not
be trusted to describe somebody's screenshot.

Not a substitute for looking at the picture: it returns glyphs and their boxes. Layout that
carries no text — a divider, a colour state, an icon — still has to be measured, and this
tool has no part in that.

Install (Debian/Ubuntu)
-----------------------
    sudo apt-get install -y tesseract-ocr tesseract-ocr-ara tesseract-ocr-eng \
                            fonts-noto-core libraqm0
    python3 -m pip install --user pillow
    # accuracy for Arabic: the distro ships the 1.4 MB "fast" model, which is not enough
    mkdir -p ~/.local/share/tessdata
    for l in ara eng; do
      curl -sL -o ~/.local/share/tessdata/$l.traineddata \\
        https://github.com/tesseract-ocr/tessdata_best/raw/main/$l.traineddata
    done
    # optional second engine
    python3 -m pip install --user easyocr --extra-index-url https://download.pytorch.org/whl/cpu

Usage
-----
    python3 tools/read_image_text.py shot.png
    python3 tools/read_image_text.py https://i.ibb.co/xxxx/Screenshot.jpg
    python3 tools/read_image_text.py shot.png --engine easyocr --lang ar+en
    python3 tools/read_image_text.py shot.png --crop 0,0.10,1,0.35   # a strip, as fractions
    python3 tools/read_image_text.py shot.png --json                 # blocks with boxes
    python3 tools/read_image_text.py --self-test                     # known text, both engines
"""

from __future__ import annotations

import argparse
import difflib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.request
from pathlib import Path
from urllib.parse import urlparse

try:  # imported lazily so --langs still works without Pillow
    from PIL import Image, ImageDraw, ImageFont, ImageOps
except ImportError:  # pragma: no cover
    Image = ImageDraw = ImageFont = ImageOps = None


# ────────────────────────────────────────────────────────────────────────────
# المحرّكان
# ────────────────────────────────────────────────────────────────────────────


def require(binary: str) -> str:
    found = shutil.which(binary)
    if not found:
        sys.exit(f"missing `{binary}`; see the docstring for the install lines")
    return found


def find_tessdata() -> str | None:
    """An explicit tessdata directory, preferring one that holds a *large* `ara` model.

    The distro package ships the 1.4 MB "fast" Arabic model; `tessdata_best` is 13 MB. The
    difference is the difference between noise and words on a phone screenshot, so an
    installed best model is picked up automatically instead of only on an explicit flag.
    """
    candidates = [
        os.environ.get("TESSDATA_PREFIX"),
        str(Path.home() / ".local/share/tessdata"),
        "/usr/share/tesseract-ocr/5/tessdata",
        "/usr/share/tesseract-ocr/4.00/tessdata",
        "/usr/share/tessdata",
    ]
    best: tuple[int, str] | None = None
    for directory in candidates:
        if not directory or not Path(directory).is_dir():
            continue
        for model in Path(directory).glob("*.traineddata"):
            size = model.stat().st_size
            if best is None or size > best[0]:
                best = (size, directory)
    return best[1] if best else None


def tesseract_read(binary: str, path: Path, lang: str, psm: int, tessdata: str | None):
    """tesseract in TSV mode → (lines, mean confidence, word count).

    TSV rather than plain text on purpose: it carries a confidence per word, which is what
    lets the caller pick the best variant **and** say how much of the page was read poorly
    instead of presenting noise as a label.
    """
    command = [binary, str(path), "stdout", "-l", lang, "--psm", str(psm), "tsv"]
    if tessdata:
        command += ["--tessdata-dir", tessdata]
    result = subprocess.run(command, capture_output=True, text=True, timeout=600)
    if result.returncode != 0:
        return [], 0.0, 0
    grouped: dict[tuple[str, str, str], dict] = {}
    confidences: list[float] = []
    for row in result.stdout.splitlines()[1:]:
        cells = row.split("\t")
        if len(cells) < 12 or not cells[11].strip():
            continue
        try:
            confidence = float(cells[10])
        except ValueError:
            continue
        key = (cells[1], cells[2], cells[3])
        entry = grouped.setdefault(
            key,
            {"text": [], "conf": [], "left": 10**9, "top": 10**9, "right": 0, "bottom": 0},
        )
        entry["text"].append(cells[11].strip())
        entry["conf"].append(confidence)
        entry["left"] = min(entry["left"], int(cells[6]))
        entry["top"] = min(entry["top"], int(cells[7]))
        entry["right"] = max(entry["right"], int(cells[6]) + int(cells[8]))
        entry["bottom"] = max(entry["bottom"], int(cells[7]) + int(cells[9]))
        confidences.append(confidence)
    lines = [
        {
            "text": " ".join(entry["text"]),
            "conf": round(sum(entry["conf"]) / len(entry["conf"]), 1),
            "bbox": [entry["left"], entry["top"], entry["right"], entry["bottom"]],
        }
        for entry in sorted(grouped.values(), key=lambda item: (item["top"], item["left"]))
    ]
    mean = round(sum(confidences) / len(confidences), 1) if confidences else 0.0
    return lines, mean, len(confidences)


_EASY_READERS: dict[tuple[str, ...], object] = {}


def easyocr_read(path: Path, langs: list[str]):
    """easyocr → the same (lines, mean, words) shape as the tesseract reader.

    The reader is cached per language set: building it re-loads the detection and
    recognition networks, and doing that per variant would cost minutes for no gain.
    """
    try:
        import easyocr
    except ImportError:
        sys.exit("easyocr is not installed; see the docstring for the install line")
    key = tuple(langs)
    reader = _EASY_READERS.get(key)
    if reader is None:
        reader = easyocr.Reader(list(langs), gpu=False, verbose=False)
        _EASY_READERS[key] = reader
    results = reader.readtext(str(path))
    lines = [
        {
            "text": str(text),
            "conf": round(float(confidence) * 100, 1),
            "bbox": [int(min(p[0] for p in box)), int(min(p[1] for p in box)),
                     int(max(p[0] for p in box)), int(max(p[1] for p in box))],
        }
        for box, text, confidence in results
        if str(text).strip()
    ]
    lines.sort(key=lambda item: (item["bbox"][1], item["bbox"][0]))
    confidences = [line["conf"] for line in lines]
    mean = round(sum(confidences) / len(confidences), 1) if confidences else 0.0
    return lines, mean, len(lines)


def languages(spec: str, engine: str) -> list[str] | str:
    """`ara+eng` for tesseract, `['ar','en']` for easyocr — one user-facing spelling."""
    if engine == "easyocr":
        return [code.strip() for code in spec.replace("+", ",").split(",") if code.strip()]
    return spec


# ────────────────────────────────────────────────────────────────────────────
# الصورة: تحميل · قص · تجهيز
# ────────────────────────────────────────────────────────────────────────────


def load(source: str, workdir: Path) -> Path:
    """Path or URL → a local image file. Fetched with urllib, so there is no shell surface."""
    if source.startswith(("http://", "https://")):
        request = urllib.request.Request(source, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(request, timeout=60) as response:
            payload = response.read()
            content_type = (response.headers.get("Content-Type") or "").split(";")[0].strip()
        suffix = {
            "image/png": ".png",
            "image/jpeg": ".jpg",
            "image/webp": ".webp",
            "image/gif": ".gif",
            "image/bmp": ".bmp",
        }.get(content_type, Path(urlparse(source).path).suffix or ".img")
        target = (workdir / "downloaded").with_suffix(suffix)
        target.write_bytes(payload)
        print(f"downloaded {len(payload)} bytes ({content_type or 'unknown type'}) -> {target.name}")
        return target
    path = Path(source)
    if not path.is_file():
        sys.exit(f"not a file: {source}")
    return path


def crop(image, spec: str):
    """Fractions, not pixels: `0,0,1,0.15` is the top 15% of whatever was sent."""
    parts = [float(value) for value in spec.split(",")]
    if len(parts) != 4:
        sys.exit("--crop takes four fractions: x0,y0,x1,y1")
    width, height = image.size
    return image.crop(
        (int(parts[0] * width), int(parts[1] * height), int(parts[2] * width), int(parts[3] * height))
    )


def variants(image, upscale: float):
    """The candidate renderings. Order is not preference: every one is scored.

    This list only has to contain the shapes real screenshots come in — dark theme
    (invert), thin small text (upscale), washed-out overlays (autocontrast), flat light
    pages (as-is) — and the scoring decides which one this image actually needed.
    """
    gray = image.convert("L")
    inverted = ImageOps.invert(gray)
    stretched = ImageOps.autocontrast(inverted, cutoff=1)
    scaled = gray.resize((int(gray.width * upscale), int(gray.height * upscale)), Image.LANCZOS)
    scaled_inverted = ImageOps.autocontrast(ImageOps.invert(scaled), cutoff=1)
    histogram = scaled_inverted.histogram()
    threshold = sum(index * count for index, count in enumerate(histogram)) / (
        scaled_inverted.width * scaled_inverted.height
    )
    binarised = scaled_inverted.point(lambda value: 255 if value > threshold else 0)
    return [
        ("as-is", gray),
        ("inverted", inverted),
        ("inverted+autocontrast", stretched),
        (f"upscaled x{upscale:g}+inverted", scaled_inverted),
        (f"upscaled x{upscale:g}+binary", binarised),
    ]


# ────────────────────────────────────────────────────────────────────────────
# اختبار الذات: نصّ معروف يُرسَم ثم يُقرأ
# ────────────────────────────────────────────────────────────────────────────

SELF_TEST_CASES = [
    ("maxmanager", "Management console", "latin"),
    ("storage", "Storage 479 GB / Files 23", "latin+digits"),
    ("arabic-title", "مدير الملفات", "arabic"),
    ("arabic-mixed", "النسخ الاحتياطي: ٣ نسخ", "arabic+digits"),
]

_ARABIC_NOISE = re.compile(r"[\u0640\u064b-\u0652\u0670\u06d6-\u06ed]")
_KEEP = re.compile(r"[^\w\u0600-\u06ff]+", re.UNICODE)


def normalize(text: str) -> str:
    """Compare letters and digits only, with Arabic spelling variants unified.

    Without this the self-test would fail on differences that carry no meaning (`أ` vs `ا`,
    a diacritic, a stray bullet) and hide the differences that do.
    """
    text = _ARABIC_NOISE.sub("", text)
    text = re.sub(r"[أإآٱ]", "ا", text).replace("ى", "ي").replace("ة", "ه")
    return _KEEP.sub(" ", text).strip()


ARABIC_FONTS = [
    "/usr/share/fonts/truetype/noto/NotoNaskhArabic-Regular.ttf",
    "/usr/share/fonts/truetype/noto/NotoSansArabic-Regular.ttf",
]
LATIN_FONTS = [
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
]


def font_for(kind: str) -> Path | None:
    """Per-case font, and this is not cosmetic: Noto Naskh Arabic carries Latin glyphs that
    are *not* the Latin alphabet — rendering "Management console" with it produced
    "0000000 Conoood", which an OCR engine is right to read as nonsense. A self-test that
    renders the sample wrong measures the sample, not the engine."""
    candidates = ARABIC_FONTS if kind.startswith("arabic") else LATIN_FONTS
    for candidate in candidates:
        if Path(candidate).is_file():
            return Path(candidate)
    return None


def self_test(engine: str, tesseract: str, tessdata: str | None, psm: int) -> int:
    """Render known strings, read them back, report how much survived.

    Two polarities on purpose: dark-theme screenshots are the common case for this app and
    the one that fails first if the pipeline forgets to invert.
    """
    if Image is None:
        sys.exit("missing Pillow; see the docstring for the install line")
    from PIL import features

    if not features.check("raqm"):
        print("تحذير: Raqm غير متاح ⇒ النصّ العربي يُرسَم غير مشكول، فالنتيجة لا تُقاس عليه")
    failures = 0
    with tempfile.TemporaryDirectory(prefix="self-test-") as workdir:
        work = Path(workdir)
        for name, ground_truth, kind in SELF_TEST_CASES:
            font_path = font_for(kind)
            if font_path is None:
                sys.exit(f"no font for «{kind}»; install fonts-noto-core and fonts-dejavu-core")
            for polarity, background, ink in (("dark", 28, 235), ("light", 245, 20)):
                image = Image.new("L", (900, 90), background)
                draw = ImageDraw.Draw(image)
                draw.text(
                    (20, 25),
                    ground_truth,
                    font=ImageFont.truetype(str(font_path), 34),
                    fill=ink,
                    direction="rtl" if kind.startswith("arabic") else "ltr",
                    language="ar" if kind.startswith("arabic") else None,
                    features=["-liga"] if kind.startswith("arabic") else None,
                )
                path = work / f"{name}-{polarity}.png"
                image.save(path)

                best = (0.0, "", "")
                for variant, candidate in variants(image, 2.0):
                    candidate_path = work / f"{name}-{polarity}-v.png"
                    candidate.save(candidate_path)
                    if engine == "easyocr":
                        lines, mean, words = easyocr_read(candidate_path, languages("ar+en", engine))
                    else:
                        lines, mean, words = tesseract_read(tesseract, candidate_path, "ara+eng", psm, tessdata)
                    if not words:
                        continue
                    read = normalize(" ".join(line["text"] for line in lines))
                    ratio = difflib.SequenceMatcher(None, normalize(ground_truth), read).ratio()
                    if ratio > best[0]:
                        best = (ratio, read, variant)

                status = "OK  " if best[0] >= 0.75 else "فشل "
                if best[0] < 0.75:
                    failures += 1
                print(
                    f"{status}{name:<14} {polarity:<5} ratio {best[0]:.2f} via {best[2]:<24} "
                    f"«{best[1]}» (المطلوب: «{ground_truth}»)"
                )
    print(f"\nself-test: {len(SELF_TEST_CASES) * 2 - failures}/{len(SELF_TEST_CASES) * 2} مقبول")
    return 1 if failures else 0


# ────────────────────────────────────────────────────────────────────────────


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("images", nargs="*", help="local paths or http(s) URLs")
    parser.add_argument("--engine", choices=["tesseract", "easyocr"], default="tesseract")
    parser.add_argument("--lang", default="ara+eng", help="tesseract codes (ara+eng) or easyocr codes (ar+en)")
    parser.add_argument("--psm", type=int, default=3, help="tesseract page segmentation mode")
    parser.add_argument("--crop", help="fractions x0,y0,x1,y1")
    parser.add_argument("--upscale", type=float, default=2.0, help="scale for the upscaled variants")
    parser.add_argument("--json", action="store_true", help="emit blocks with boxes")
    parser.add_argument("--all", action="store_true", help="show every variant, not only the best")
    parser.add_argument("--fast", action="store_true", help="two variants only (as-is, inverted+autocontrast)")
    parser.add_argument("--variants", help="comma-separated variant names to run (prefix match)")
    parser.add_argument("--self-test", action="store_true", help="read known rendered text and report")
    parser.add_argument("--langs", action="store_true", help="list tesseract language packs and exit")
    args = parser.parse_args()

    binary = require("tesseract")
    if args.langs:
        print(subprocess.run([binary, "--list-langs"], capture_output=True, text=True).stdout)
        return 0

    if Image is None:
        sys.exit("missing Pillow; see the docstring for the install line")

    tessdata = find_tessdata()
    if args.self_test:
        print(f"engine={args.engine} · tessdata={tessdata}")
        return self_test(args.engine, binary, tessdata, args.psm)

    if not args.images:
        parser.error("give at least one image, or use --self-test")

    code = 0
    with tempfile.TemporaryDirectory(prefix="read-image-") as workdir:
        work = Path(workdir)
        for source in args.images:
            print("=" * 72)
            print(f"image: {source}")
            image = Image.open(load(source, work))
            if args.crop:
                image = crop(image, args.crop)
            print(f"size: {image.width}x{image.height}" + (f"  crop={args.crop}" if args.crop else ""))

            # اختيار الأنماط: على `easyocr` النمط الواحد يكلّف عشرات الثواني على لقطة كاملة،
            # وتشغيل الخمسة على أربع عشرة لقطة يصير ساعات. `--fast` يُبقي النمطين الذين
            # فازا فعلاً على لقطات الواجهة، و`--variants` يسمّي أيّها بدقّة.
            candidates = variants(image, args.upscale)
            if args.fast:
                keep = {"as-is", "inverted+autocontrast"}
                candidates = [item for item in candidates if item[0] in keep]
            if args.variants:
                wanted = [part.strip() for part in args.variants.split(",") if part.strip()]
                candidates = [item for item in candidates if any(item[0].startswith(w) for w in wanted)]
            if not candidates:
                sys.exit("no variant matched; run without --variants to see the names")

            scored = []
            for index, (name, candidate) in enumerate(candidates):
                path = work / f"variant-{index}.png"
                candidate.save(path)
                if args.engine == "easyocr":
                    lines, mean, words = easyocr_read(path, languages(args.lang, args.engine))
                else:
                    lines, mean, words = tesseract_read(binary, path, args.lang, args.psm, tessdata)
                scored.append((mean, words, name, lines))
                print(f"  {name:<28} mean-conf {mean:>5}  words {words:>4}")

            if not any(entry[1] for entry in scored):
                print("no text recognised in any variant")
                code = 2
                continue

            best = max(scored, key=lambda entry: (entry[0], entry[1]))
            print(f"chosen: {best[2]} (mean confidence {best[0]}, {best[1]} words)")
            if args.json:
                print(json.dumps(best[3], ensure_ascii=False))
            else:
                for line in best[3]:
                    print(f"  [{line['conf']:>5}] {line['text']}")
            if args.all:
                for mean, words, name, lines in scored:
                    if name == best[2]:
                        continue
                    print(f"--- variant {name} (mean {mean}, {words} words) ---")
                    for line in lines:
                        print(f"  [{line['conf']:>5}] {line['text']}")
    return code


if __name__ == "__main__":
    raise SystemExit(main())
