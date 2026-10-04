#!/usr/bin/env python3
"""تجهيز المستودع العام (الوثائق واللقطات) لمشروع مغلق المصدر.

الأمر الذي وُجدت له
-------------------
أمر المالك: «انقل كل شيء إلى Releases و README.md والذي آخره، وليس المشروع بأكمله لأنّي
أجعله مغلق المصدر». فالمستودع العام يحمل **النصف المكتوب**: القصة، واللقطات، واليدويّ،
وملاحظات الإصدار — ولا يحمل مصدرًا. ويفعل ثلاث خطوات بهذا الترتيب:

1. **النسخ الانتقائي** إلى `build/publish-maxmanager/repo/` (والقائمة هي الحقيقة: ما ليس
   فيها يبقى خاصًّا عن قصد).
2. **إعادة كتابة ما لا يصحّ إلا داخل المستودع الخاص** — لا حذفًا ولا صمتًا: ادّعاء
   «عدّة الدمج في هذا المستودع» يُصاغ «تُسلَّم بطلب كتابي»، ورابط التثبيت يصير Releases،
   وأوامر البوّابات تُوسم بأنها تُشغَّل في المستودع الخاص.
3. **إثبات أن الشجرة مكتفية بذاتها:** كل رابط وكل صورة يجب أن يُحلّا داخلها، ولا رابط
   يغور في مسار خاصّ. هذا هو `--assert` الحقيقي: **٠ روابط ميتة و٠ تغلغل**، مقيسان لا
   مُدَّعين. والفرق بين الروابط التي **تُسمّى** في النثر (مشروعة: تصف أدوات المستودع
   الخاص) والتي **تُربط** (ميتة بالبناء) هو ما وُجد هذا المدقّق ليفصله.

المدقّق الأوّل كان فظًّا فعدّ `align="center"` رابطًا: **٤٦٤٣ رابطًا ميتًا وهميًّا**. صار
يقرأ خصائص الجلب وحدها (`href` · `src` · `srcset` · أهداف markdown)، فلا يفحص إلا ما
يطلبه العارض فعلًا.

من جذر المشروع:  python3 tools/publish_docs_repo.py
"""
from __future__ import annotations

import os
import re
import shutil
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
STAGE = os.path.join(ROOT, "build", "publish-maxmanager", "repo")
NEW_REPO = "https://github.com/nader295/Max-Manger"
SOURCE_REPO = "https://github.com/catui0041-alt/Gg"

# What the public repository holds. Anything not listed stays private on purpose.
FILES = [
    "README.md",
    "README.ar.md",
    "LICENSE",
    "THIRD_PARTY_NOTICES.md",
    "DESIGN.md",
    "docs/compatibility.md",
    "docs/features.md",
    "docs/profiles.md",
    "docs/thermal.md",
    "docs/faq.md",
    "docs/max-atlas.md",
    "docs/max-ai.md",
    "docs/architecture.md",
    "docs/verification.md",
    "docs/rom-integration.md",
    "docs/releases/v1.0.md",
]
TREES = ["docs/assets", "docs/screenshots"]
# Screenshots are copied by name filter: only the PNGs, not the contract file twice.
TREE_GLOBS = {"docs/assets": ("*.svg",), "docs/screenshots": ("*.png",)}

# (file, old, new) — every replacement spells out why it exists.
EDITS: list[tuple[str, str, str]] = [
    # 1. The build badge pointed at a workflow that lives with the private source.
    (
        "README.md",
        '  <a href="https://github.com/catui0041-alt/Gg/actions/workflows/build.yml">'
        '<img alt="Build" src="https://github.com/catui0041-alt/Gg/actions/workflows/build.yml/badge.svg?v=2"></a>',
        f'  <a href="{NEW_REPO}/releases/latest"><img alt="Download" '
        'src="https://img.shields.io/badge/download-latest_release-2ea44f?style=for-the-badge"></a>',
    ),
    (
        "README.ar.md",
        '  <a href="https://github.com/catui0041-alt/Gg/actions/workflows/build.yml">'
        '<img alt="البناء" src="https://github.com/catui0041-alt/Gg/actions/workflows/build.yml/badge.svg?v=2"></a>',
        f'  <a href="{NEW_REPO}/releases/latest"><img alt="التنزيل" '
        'src="https://img.shields.io/badge/download-latest_release-2ea44f?style=for-the-badge"></a>',
    ),
    # 2. The one-line summary promised the kit sits in this repository.
    (
        "README.md",
        "[For ROM developers](#for-rom-developers) — the integration kit in `android/aosp/` |",
        "[For ROM developers](#for-rom-developers) — the integration kit, on request |",
    ),
    (
        "README.ar.md",
        "[لمطوّري الروم](#لمطوّري-الروم) — عدّة الدمج في `android/aosp/` |",
        "[لمطوّري الروم](#لمطوّري-الروم) — عدّة الدمج، عند الطلب |",
    ),
    # 3. Install pointed at a file name the release does not use, and at a build page
    #    that describes a private toolchain.
    (
        "README.md",
        "1. Flash `MaxManager-v1.0.zip` in **Magisk** or **KernelSU** (or a compatible root manager).",
        "1. Download `MaxManager-v1.0-module.zip` from "
        f"**[Releases]({NEW_REPO}/releases/latest)** and flash it in **Magisk** or **KernelSU** "
        "(or a compatible root manager).",
    ),
    (
        "README.ar.md",
        "1. افلش `MaxManager-v1.0.zip` في **Magisk** أو **KernelSU** (أو مدير جذر متوافق).",
        "1. نزّل `MaxManager-v1.0-module.zip` من "
        f"**[الإصدارات]({NEW_REPO}/releases/latest)** وافلشه في **Magisk** أو **KernelSU** "
        "(أو مدير جذر متوافق).",
    ),
    (
        "README.md",
        "Builds come from CI as workflow artifacts: the flashable module, a developer bundle, and checksums —\n"
        "see **[docs/building.md](docs/building.md#releases)** for what each release channel contains. If the",
        "The same release carries **`MaxManager-checksums.txt`** — one digest for the package and one for\n"
        "each of its contents — so you can verify the download before flashing it; the two commands are in\n"
        "**[docs/releases/v1.0.md](docs/releases/v1.0.md)**. If the",
    ),
    (
        "README.ar.md",
        "والنسخ تأتي من CI كمخرجات سير العمل: الوحدة القابلة للتفليش، وحزمة المطوّر، وملفات التحقّق — انظر\n"
        "**[docs/building.md](docs/building.md#releases)** لمحتوى كل قناة إصدار. وإذا فشلت الوحدة في بلوغ إقلاع",
        "والإصدار نفسه يحمل **`MaxManager-checksums.txt`** — بصمة للحزمة وبصمة لكل ما فيها — فتُتحقّق من\n"
        "التنزيل قبل تفليشه؛ والأمران في **[docs/releases/v1.0.md](docs/releases/v1.0.md)**. وإذا فشلت\n"
        "الوحدة في بلوغ إقلاع",
    ),
    # 4. "The kit ships in this repository" — it does not, and saying so would be false.
    (
        "README.md",
        "The integration kit ships in this repository under **`android/aosp/`** — Soong build files, an init\n"
        "service, a sepolicy domain and a privileged-permission allowlist. `android/kernelsu/` holds the same\n"
        "module packaged for KernelSU Next.",
        "The integration kit is **not published here** — the source is private. It is released to ROM\n"
        "maintainers **on written request**: Soong build files, an init service, a sepolicy domain and a\n"
        "privileged-permission allowlist, plus the same module packaged for KernelSU Next.",
    ),
    (
        "README.ar.md",
        "عدّة الدمج موجودة في هذا المستودع تحت **`android/aosp/`** — ملفّات Soong، وخدمة init، ونطاق sepolicy،\n"
        "وقائمة صلاحيات مُمتَزَجة. و`android/kernelsu/` تحمل الوحدة نفسها مُحزَّمة لـKernelSU Next.",
        "عدّة الدمج **غير منشورة هنا** — المصدر مغلق. وتُسلَّم لمطوّري الروم **بطلب كتابي**: ملفّات Soong،\n"
        "وخدمة init، ونطاق sepolicy، وقائمة صلاحيات مُمتَزَجة، والوحدة نفسها مُحزَّمة لـKernelSU Next.",
    ),
    # 5. The docs index listed a page that exists only with the private toolchain.
    (
        "README.md",
        "| [building.md](docs/building.md) | Building from source, CI artifacts, releases, and the numbers behind every claim |\n",
        "| [releases/v1.0.md](docs/releases/v1.0.md) | The v1.0 release notes, and how to verify the download |\n",
    ),
    (
        "README.ar.md",
        "| [building.md](docs/building.md) | البناء من المصدر، ومخرجات CI، والإصدارات، والأرقام خلف كل ادّعاء |\n",
        "| [releases/v1.0.md](docs/releases/v1.0.md) | ملاحظات إصدار ‎v1.0‎، وكيف تتحقّق من التنزيل |\n",
    ),
    # 6. A private installer script, cited as if it were reachable.
    (
        "docs/verification.md",
        "[`mainfiles/customize.sh`](../mainfiles/customize.sh)",
        "the module's own installer script",
    ),
    # 7. A generator named with a path that cannot resolve here.
    (
        "DESIGN.md",
        "[`tools/gen_readme_assets.py`](tools/gen_readme_assets.py)",
        "the README asset generator (private repository)",
    ),
    # 8. The release notes claimed the AOSP kit ships in "the repository".
    (
        "docs/releases/v1.0.md",
        "The integration kit is in the repository under **`android/aosp/`** — Soong build files, an init\n"
        "service, a sepolicy domain and a privileged-permission allowlist; `android/kernelsu/` holds the same\n"
        "module packaged for KernelSU Next.",
        "The integration kit is **released to ROM maintainers on written request** — Soong build files, an\n"
        "init service, a sepolicy domain and a privileged-permission allowlist, plus the same module packaged\n"
        "for KernelSU Next.",
    ),
    # 9. Every page opens by saying what this repository is — and what it is not.
    (
        "README.md",
        '  <a href="docs/README.md"><b>Read the docs</b></a>\n</p>',
        '  <a href="docs/README.md"><b>Read the docs</b></a>\n</p>\n\n'
        '<p align="center"><b>This repository is MaxManager\'s public face</b> — the documentation, the\n'
        'screenshots and the downloads. The application and its native layer are <b>private source</b>:\n'
        'nothing here builds, and the command lines quoted in these pages run inside that repository.</p>',
    ),
    (
        "README.ar.md",
        '  <a href="docs/README.md"><b>اقرأ الوثائق</b></a>\n</p>',
        '  <a href="docs/README.md"><b>اقرأ الوثائق</b></a>\n</p>\n\n'
        '<p align="center"><b>هذا المستودع هو الوجه العام لـMaxManager</b> — الوثائق واللقطات والتنزيلات.\n'
        'والتطبيق وطبقته الأصلية <b>مصدر مغلق</b>: لا شيء هنا يُبنى، وسطور الأوامر المنقولة في هذه\n'
        'الصفحات تُشغَّل داخل ذلك المستودع.</p>',
    ),
    # 10. The verification page quotes gate commands; the reader must know where they run.
    (
        "docs/verification.md",
        "# Verification — how the claims on this page are made\n\nEvery number in this repository",
        "# Verification — how the claims on this page are made\n\n> The commands quoted below run inside the **private repository**. This page is here so you can read\n> *what* is measured and *what is not* — not to hand you a script you could run from here.\n\nEvery number in this repository",
    ),
    # 11. The design document instructs its reader to run those same gates.
    (
        "DESIGN.md",
        "---\nversion: alpha\nname: MaxManager Design Language",
        "> **Context.** The gate commands quoted in this document run inside the **private repository**\n> that holds the app; this page is the language itself, published so the interface can be read and\n> judged.\n\n---\nversion: alpha\nname: MaxManager Design Language",
    ),
    # 12. The gallery contract page stays private (it is a maintenance page for the
    #     private tooling), so links to it point at the folder that is published.
    (
        "README.md",
        "[`docs/screenshots/`](docs/screenshots/README.md)",
        "[`docs/screenshots/`](docs/screenshots/)",
    ),
    (
        "README.ar.md",
        "[`docs/screenshots/`](docs/screenshots/README.md)",
        "[`docs/screenshots/`](docs/screenshots/)",
    ),
    # 13. Internal audits are named, never linked: a link from a published page would be dead.
    (
        "THIRD_PARTY_NOTICES.md",
        "[`docs/PROVENANCE.md`](docs/PROVENANCE.md)",
        "`docs/PROVENANCE.md`",
    ),
    (
        "THIRD_PARTY_NOTICES.md",
        "[`docs/AUTHENTICITY.md`](docs/AUTHENTICITY.md)",
        "`docs/AUTHENTICITY.md`",
    ),
    # 14. The design document cited the private tree's own contract files.
    ("DESIGN.md", "[`AGENTS.md`](AGENTS.md)", "`AGENTS.md`"),
    (
        "DESIGN.md",
        "[`MAX_TOKENS`](manager/app/src/main/java/nd/max/ui/design/MaxTokens.kt)",
        "`MAX_TOKENS`",
    ),
    # 15. Same rule on the verification page, in five places.
    ("docs/verification.md", "[`screenshots/`](screenshots/README.md)", "[`screenshots/`](screenshots/)"),
    ("docs/verification.md", "[`PROVENANCE.md`](PROVENANCE.md)", "`PROVENANCE.md`"),
    ("docs/verification.md", "[`AUTHENTICITY.md`](AUTHENTICITY.md)", "`AUTHENTICITY.md`"),
    ("docs/verification.md", "[`KNOWN_ISSUES.md`](ai/KNOWN_ISSUES.md)", "`KNOWN_ISSUES.md`"),
    ("docs/verification.md", "[`HANDOFF.md`](ai/HANDOFF.md)", "`HANDOFF.md`"),
    # 15b. The gallery footnote links the same private contract page in raw HTML.
    (
        "README.md",
        '<a href="docs/screenshots/README.md">capture contract</a> lists all of them.',
        '<a href="docs/screenshots/">screenshots folder</a> holds all of them.',
    ),
    (
        "README.ar.md",
        '<a href="docs/screenshots/README.md">عقد الالتقاط</a> يذكرها كلّها.',
        '<a href="docs/screenshots/">مجلّد اللقطات</a> يحملها كلّها.',
    ),
    # 16. The release notes linked the notices file from one directory too deep.
    (
        "docs/releases/v1.0.md",
        "[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)",
        "[`THIRD_PARTY_NOTICES.md`](../../THIRD_PARTY_NOTICES.md)",
    ),
    # 17. The AOSP kit paragraph, matched as the file actually wraps it.
    (
        "docs/releases/v1.0.md",
        "The integration kit is in the repository under **`android/aosp/`** — Soong build files, an init service, a\nsepolicy domain and a privileged-permission allowlist; `android/kernelsu/` holds the same module packaged\nfor KernelSU Next.",
        "The integration kit is **released to ROM maintainers on written request** — Soong build files, an init\nservice, a sepolicy domain and a privileged-permission allowlist, plus the same module packaged for\nKernelSU Next.",
    ),
]

# The public index page is written rather than patched: it described the private tree.
DOCS_INDEX = """# MaxManager documentation

This folder is the written half of MaxManager. The [README](../README.md) is the tour; these pages are
the answers you reach for when the tour raised a question.

## Where to start, by who you are

| You are | Read this first |
| --- | --- |
| **A user** deciding whether to install it | [README → Install](../README.md#install) · then [compatibility](compatibility.md) |
| **A user** wondering what a screen does | [features](features.md) · [screenshots](screenshots/) |
| **A user** with a problem | [FAQ](faq.md) · the app's own **Settings → Diagnostics** and **Logs** |
| **A ROM developer** | [rom-integration](rom-integration.md) — the whole guide |
| **A kernel / platform engineer** | [architecture](architecture.md) · [thermal](thermal.md) · [profiles](profiles.md) |
| **Curious how it thinks** | [Max Atlas](max-atlas.md) · [Max AI](max-ai.md) |
| **Someone checking our claims** | [verification](verification.md) |

## The pages

| Page | One line |
| --- | --- |
| [features.md](features.md) | Every capability, what it is for, and where it lives in the app |
| [max-atlas.md](max-atlas.md) | The adaptation engine: discover → understand → map → adapt → execute → verify → learn |
| [max-ai.md](max-ai.md) | The decision engine: objective, safety, measurement, journal, learning |
| [architecture.md](architecture.md) | How the app, the daemons and the kernel interfaces fit together |
| [rom-integration.md](rom-integration.md) | Three integration paths, the AOSP kit, SELinux, verification |
| [compatibility.md](compatibility.md) | Android versions, ABIs, root managers, chipsets, what is not supported |
| [thermal.md](thermal.md) | The thermal daemon: policy, learning, cooling, prediction |
| [profiles.md](profiles.md) | Chipset strategies and the module's native executables |
| [verification.md](verification.md) | How every claim in this repository is measured, and what cannot be |
| [releases/v1.0.md](releases/v1.0.md) | The v1.0 release notes, and how to verify the download |
| [faq.md](faq.md) | The questions that keep coming back |
| [../DESIGN.md](../DESIGN.md) | The design language: tokens, roles, components, motion, and the gaps we know about |
| [screenshots/](screenshots/) | The 48 screens, in the order the app shows them |

## What this repository contains, and what it deliberately keeps

MaxManager is **proprietary software** (see [LICENSE](../LICENSE)): it is not an open-source project and
this repository is not a contribution surface. What it is, is the project's public face — the written
half and the downloads.

| Layer | Where | Audience |
| --- | --- | --- |
| Product story and documentation | `README.md` · `README.ar.md` · `docs/` | Everyone |
| Screenshots | `docs/screenshots/` | Everyone |
| Visual assets for the docs | `docs/assets/` | Everyone |
| Flashable module and checksums | the repository's **Releases** | Everyone |
| The app, the daemons, the installer | private | The project itself |

**The source is not published.** There is no build here and nothing to compile: the pages describe the
shipped product, and the measurements they quote are re-derived inside the private repository rather
than by a reader of this one. Where a number cannot be checked from here, the page says so instead of
implying otherwise — see [verification](verification.md).

**No secrets live here.** No keystore, no signing password, no API key, no device identifier.
"""

# Paths that only exist inside the private repository. A published page may name none of them.
PRIVATE = [
    "android/aosp", "android/kernelsu", "mainfiles/", "manager/", "archdaemon/", "thermalcore/",
    "binprofiles/", "binutils/", "preloadbin/", "fixtures/", "tools/", "docs/ai/", "docs/aegis/",
    "changelog.md", "building.md", "PROVENANCE.md", "AUTHENTICITY.md", SOURCE_REPO,
]


def copy_set() -> tuple[int, int]:
    files = 0
    total = 0
    for rel in FILES:
        src, dst = os.path.join(ROOT, rel), os.path.join(STAGE, rel)
        if not os.path.exists(src):
            sys.exit(f"missing source file: {rel}")
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copy2(src, dst)
        files += 1
        total += os.path.getsize(src)
    import glob
    for tree, patterns in TREE_GLOBS.items():
        os.makedirs(os.path.join(STAGE, tree), exist_ok=True)
        for pattern in patterns:
            for path in sorted(glob.glob(os.path.join(ROOT, tree, pattern))):
                dst = os.path.join(STAGE, tree, os.path.basename(path))
                shutil.copy2(path, dst)
                files += 1
                total += os.path.getsize(path)
    return files, total


def apply_edits() -> int:
    """Apply every edit to every occurrence, and print the count.

    All-occurrences is deliberate: where a sentence that is false in the public
    repository appears twice, both copies are false. The count is printed so a
    surprising number is visible rather than silent.
    """
    done = 0
    for rel, old, new in EDITS:
        path = os.path.join(STAGE, rel)
        with open(path, encoding="utf-8") as fh:
            text = fh.read()
        hits = text.count(old)
        if not hits:
            print(f"  ! anchor not found in {rel}: {old[:60]!r}")
            continue
        with open(path, "w", encoding="utf-8") as fh:
            fh.write(text.replace(old, new))
        done += 1
        if hits > 1:
            print(f"  · {rel}: {hits} occurrences")
    index = os.path.join(STAGE, "docs", "README.md")
    with open(index, "w", encoding="utf-8") as fh:
        fh.write(DOCS_INDEX)
    return done


# Only real link carriers count: an attribute that fetches a file, or a markdown target.
ATTR = re.compile(r'(?:href|src)="(?P<v>[^"]+)"')
SRCSET = re.compile(r'srcset="(?P<v>[^"]+)"')
MDLINK = re.compile(r'\[[^\]]*\]\((?P<v>[^)]+)\)')


def targets(text: str):
    """Yield every path the file asks a renderer to fetch."""
    for value in ATTR.findall(text):
        yield value
    for value in SRCSET.findall(text):
        # srcset is a comma-separated list of `url descriptor` pairs.
        for part in value.split(","):
            yield part.strip().split(" ")[0]
    for value in MDLINK.findall(text):
        yield value


def check() -> tuple[list[str], list[str]]:
    """Return (dead links, links that reach into the private tree)."""
    dead: list[str] = []
    leaked: list[str] = []
    for base, _dirs, names in os.walk(STAGE):
        for name in names:
            path = os.path.join(base, name)
            if not name.endswith((".md", ".svg")):
                continue
            rel = os.path.relpath(path, STAGE)
            with open(path, encoding="utf-8", errors="replace") as fh:
                text = fh.read()
            for target in targets(text):
                if target.startswith(("http", "#", "mailto", "data:")):
                    continue
                clean = target.split("#")[0].split("?")[0]
                if not clean:
                    continue
                if not os.path.exists(os.path.normpath(os.path.join(base, clean))):
                    dead.append(f"{rel} -> {target}")
            # A published page may *name* the private toolchain in prose; what it may not
            # do is link to it, because that link would be dead by construction.
            for match in MDLINK.finditer(text):
                target = match.group("v")
                if target.startswith(("http", "#", "mailto")):
                    continue
                if any(token.rstrip("/") in target for token in PRIVATE if token != SOURCE_REPO):
                    leaked.append(f"{rel} -> {target}")
            if SOURCE_REPO in text:
                leaked.append(f"{rel} -> {SOURCE_REPO}")
    return dead, leaked


def main() -> int:
    print(f"staging into {os.path.relpath(STAGE, ROOT)}")
    files, total = copy_set()
    print(f"copied {files} files · {total / 1048576:.2f} MiB")
    applied = apply_edits()
    print(f"applied {applied}/{len(EDITS)} edits + rewrote docs/README.md")
    dead, leaked = check()
    print(f"\ndead links: {len(dead)}")
    for item in dead:
        print("   x", item)
    print(f"private references: {len(leaked)}")
    for item in leaked:
        print("   x", item)
    return 0 if not (dead or leaked) else 1


if __name__ == "__main__":
    raise SystemExit(main())
