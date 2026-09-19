#!/usr/bin/env python3
"""Read the text inside an image: download → preprocess → OCR → pick the best variant.

Why this exists
---------------
This repository's agent sessions can fetch web *pages* but not *images*: a screenshot sent
as a link (Google Play, ibb.co, a store listing) never reached the model, so every review of
a UI reference was done by measuring geometry — panel edges and dividers — and guessing the
labels. That is exactly the kind of "I did not read it but I will describe it anyway" this
project forbids everywhere else.

So the reading is done here, by tools that do not need eyes: `tesseract` for the glyphs and
`Pillow` for the preprocessing that makes dark-mode UI text readable at all (upscale,
grayscale, invert to dark-on-light, contrast stretch). Several variants are rendered and the
one with the highest mean word confidence wins, because a screenshot's polarity, scale and
contrast are not known in advance and a single fixed pipeline reads some of them as noise.

Not a substitute for looking at the picture: it returns glyphs and their boxes. Layout that
carries no text — a divider, a colour state, an icon — still has to be measured, and
`tools/` has no part in that.

Requires the `tesseract` binary (with the `ara` language pack for Arabic) and `Pillow`.
Install on Debian/Ubuntu:

    sudo apt-get install -y tesseract-ocr tesseract-ocr-ara tesseract-ocr-eng
    python3 -m pip install --user pillow

Usage
-----
    python3 tools/read_image_text.py shot.png
    python3 tools/read_image_text.py https://i.ibb.co/xxxx/Screenshot.jpg --lang ara+eng
    python3 tools/read_image_text.py shot.png --crop 0,0,1,0.15   # top strip only
    python3 tools/read_image_text.py shot.png --json              # blocks with boxes
    python3 tools/read_image_text.py shot.png --all               # every variant, not just best
"""

from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys
import tempfile
import urllib.request
from pathlib import Path
from urllib.parse import urlparse

try:  # imported lazily so --list-langs still works without Pillow
    from PIL import Image, ImageOps
except ImportError:  # pragma: no cover
    Image = None
    ImageOps = None


def require(binary: str) -> str:
    found = shutil.which(binary)
    if not found:
        sys.exit(f"missing `{binary}`; see the docstring for the install lines")
    return found


def load(source: str, workdir: Path) -> Path:
    """Path or URL → a local image file. A URL is fetched with urllib, not curl, so the
    script has no shell dependency and no quoting surface."""
    if source.startswith(("http://", "https://")):
        target = workdir / "downloaded"
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
        target = target.with_suffix(suffix)
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
    box = (
        int(parts[0] * width),
        int(parts[1] * height),
        int(parts[2] * width),
        int(parts[3] * height),
    )
    return image.crop(box)


def variants(image, upscale: float):
    """The candidate renderings, darkest-text-on-lightest-background first.

    Order is not preference: every variant is scored by OCR confidence, and this list only
    has to contain the shapes real screenshots come in — dark theme (invert), thin small
    text (upscale), washed-out overlays (autocontrast), and flat light pages (as-is).
    """
    gray = image.convert("L")
    inverted = ImageOps.invert(gray)
    stretched = ImageOps.autocontrast(inverted, cutoff=1)
    scaled = gray.resize(
        (int(gray.width * upscale), int(gray.height * upscale)),
        Image.LANCZOS,
    )
    scaled_inverted = ImageOps.autocontrast(ImageOps.invert(scaled), cutoff=1)
    threshold = sum(index * count for index, count in enumerate(scaled_inverted.histogram())) / (
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


def ocr(binary: str, path: Path, lang: str, psm: int):
    """Run tesseract in TSV mode and return (lines, mean confidence, word count).

    TSV rather than plain text on purpose: it carries a confidence per word, which is what
    lets the caller pick the best variant **and** say how much of the page was read poorly
    instead of presenting noise as a label.
    """
    result = subprocess.run(
        [binary, str(path), "stdout", "-l", lang, "--psm", str(psm), "tsv"],
        capture_output=True,
        text=True,
        timeout=300,
    )
    if result.returncode != 0:
        return [], 0.0, 0, result.stderr.strip()
    lines: dict[tuple[str, str, str], dict] = {}
    confidences: list[float] = []
    for row in result.stdout.splitlines()[1:]:
        cells = row.split("\t")
        if len(cells) < 12:
            continue
        text = cells[11].strip()
        if not text:
            continue
        try:
            confidence = float(cells[10])
        except ValueError:
            continue
        key = (cells[1], cells[2], cells[3])
        entry = lines.setdefault(
            key,
            {
                "block": key,
                "text": [],
                "conf": [],
                "left": int(cells[6]),
                "top": int(cells[7]),
                "right": int(cells[6]) + int(cells[8]),
                "bottom": int(cells[7]) + int(cells[9]),
            },
        )
        entry["text"].append(text)
        entry["conf"].append(confidence)
        confidences.append(confidence)
        entry["left"] = min(entry["left"], int(cells[6]))
        entry["top"] = min(entry["top"], int(cells[7]))
        entry["right"] = max(entry["right"], int(cells[6]) + int(cells[8]))
        entry["bottom"] = max(entry["bottom"], int(cells[7]) + int(cells[9]))
    ordered = [
        {
            "text": " ".join(entry["text"]),
            "conf": round(sum(entry["conf"]) / len(entry["conf"]), 1),
            "bbox": [entry["left"], entry["top"], entry["right"], entry["bottom"]],
        }
        for entry in sorted(lines.values(), key=lambda item: (item["top"], item["left"]))
    ]
    mean = round(sum(confidences) / len(confidences), 1) if confidences else 0.0
    return ordered, mean, len(confidences), ""


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("images", nargs="+", help="local paths or http(s) URLs")
    parser.add_argument("--lang", default="ara+eng", help="tesseract languages (default ara+eng)")
    parser.add_argument("--psm", type=int, default=3, help="page segmentation mode (default 3)")
    parser.add_argument("--crop", help="fractions x0,y0,x1,y1")
    parser.add_argument("--upscale", type=float, default=2.0, help="scale for the upscaled variants")
    parser.add_argument("--json", action="store_true", help="emit blocks with boxes")
    parser.add_argument("--all", action="store_true", help="show every variant, not only the best")
    parser.add_argument("--langs", action="store_true", help="list installed language packs and exit")
    args = parser.parse_args()

    binary = require("tesseract")
    if args.langs:
        print(subprocess.run([binary, "--list-langs"], capture_output=True, text=True).stdout)
        return 0

    if Image is None:
        sys.exit("missing Pillow; see the docstring for the install line")

    with tempfile.TemporaryDirectory(prefix="read-image-") as workdir:
        work = Path(workdir)
        for source in args.images:
            print("=" * 72)
            print(f"image: {source}")
            local = load(source, work)
            image = Image.open(local)
            if args.crop:
                image = crop(image, args.crop)
            print(f"size: {image.width}x{image.height}" + (f"  crop={args.crop}" if args.crop else ""))

            scored = []
            for index, (name, candidate) in enumerate(variants(image, args.upscale)):
                path = work / f"variant-{index}.png"
                candidate.save(path)
                lines, mean, words, error = ocr(binary, path, args.lang, args.psm)
                scored.append((mean, words, name, lines, error))
                print(f"  variant {name:<28} mean-conf {mean:>5}  words {words:>4} {error}".rstrip())
            if not any(entry[1] for entry in scored):
                print("no text recognised in any variant")
                continue

            best = max(scored, key=lambda entry: (entry[0], entry[1]))
            print(f"chosen: {best[2]} (mean confidence {best[0]}, {best[1]} words)")
            if args.json:
                print(json.dumps(best[3], ensure_ascii=False))
            else:
                for line in best[3]:
                    print(f"  [{line['conf']:>5}] {line['text']}")
            if args.all:
                for mean, words, name, lines, _ in scored:
                    if name == best[2]:
                        continue
                    print(f"--- variant {name} (mean {mean}, {words} words) ---")
                    for line in lines:
                        print(f"  [{line['conf']:>5}] {line['text']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
