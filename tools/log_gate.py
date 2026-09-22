#!/usr/bin/env python3
"""بوابة سجل الجهاز — تحويل «حزمة سجل» إلى حكم PASS/FAIL بثوابت مُعلَنة.

لماذا وُجدت هذه الأداة
----------------------
أفضل ما لدينا من دليل هو حزمة سجل من جهاز حقيقي، وكانت تُقرأ **بالعين** مرة واحدة ثم تموت داخل
محادثة. والقياس الذي بُنيت عليه تكملة ٩٨ (طلبٌ عند ٧٠٢ على سقف ٥٢٠ سُجّل `applied=true verified=true`
**بصفر كتابة** على العقدة) كان يمكن أن يخرج من أداة تقول «فشل» في ثانية، لا من قراءة ٤٢٠٠ سطر.

فالأداة تُعلن الثوابت مرة واحدة، وتحكم على أي حزمة بمقاييس لا بحدس:

| البوابة | الثابت | لماذا |
| --- | --- | --- |
| `write-proof` | كل `PERAPP_COMMIT … verified=true` يجب أن يسبقه سطر كتابة على عقدة ذلك المقبض بقيمة الطلب — **أو** سطرُ كتابةٍ لتحرير قفل (`-1` بشهادة تحرير من النواة) — أو أن تقرأ العقدة القيمة المطلوبة أصلًا | «لا ادّعاء نجاح بلا كتابة» — وهو العطب المقيس حرفيًّا |
| `drift-proof` | `APPLY_DRIFT_REPAIRED` لا يُعلن إصلاحًا وقيمةُ العقدة ليست المطلوبة إلا إن سُجّل السطر الذي يحاول الكتابة | نفس العطب في مسار الانحراف (ظهر مرّتين في حزمة المالك) |
| `session-label` | كل سطر مقبض يحمل `sw=` و`pkg=` يجب أن يوافق حزمة `APP_SWITCH` لذلك المعرّف | ٧٤٩ حالة عدم تطابق موثّقة (`REPAIR_NOTES`) تجعل العطل يُنسب لتطبيق آخر |
| `switch-latency` | وسيط وأسوأ زمن من `APP_SWITCH` إلى `PERAPP_COMMIT` لنفس المعرّف | زمن الوصول إلى الهدف مقياس المنتج لا زينته |
| `named-failure` | كل `outcome=not_verified`/`blocked`/`verified=false` يحمل `reason`/`error` قابلًا للقراءة | فشلٌ بلا رمز لا يُصلَح |

والأداة **لا تُصلح ولا تكتب**: تقرأ حزمة (`.tar.gz` أو مجلَّدًا أو ملف `.log`) وتطبع الأرقام والحكم.

الاستعمال
--------
```sh
python3 tools/log_gate.py <bundle.tar.gz|dir|MaxManager.log>        # تقرير
python3 tools/log_gate.py <...> --json                              # للقراءة الآلية
python3 tools/log_gate.py <...> --assert                            # exit 1 عند أي فشل
python3 tools/log_gate.py --self-test                               # يقيس الأداة نفسها
```

وملاحظة صيغة مقصودة: قارئ القيمة هنا يعكس `HardwareVerification.ceilingOf` في Kotlin (مدى `min:max`
سقفه حقلها الثاني، وقراءة السقف المرمَّزة `node|…` أولها، ورقم مجرّد كما هو). أداتان في لغتين لا
يمكن أن تتشاركا دالة، فالخيار بين انعكاس مكتوب وموثَّق وبين عدم قياس أصلًا — والانعكاس يُقاس
(`--self-test`) ويُذكر مرجعه.
"""

from __future__ import annotations

import argparse
import json
import re
import statistics
import sys
import tarfile
import tempfile
from dataclasses import dataclass, field
from pathlib import Path

# ── ثوابت مُعلَنة (قابلة للتجاوز بأعلام) ──────────────────────────────────────────
MAX_MEDIAN_SWITCH_LATENCY_MS = 20_000
MAX_SWITCH_LATENCY_MS = 30_000
WRITE_WINDOW_MS = 60_000

TIME_RE = re.compile(r"^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})")
FIELD_RE = re.compile(r"([A-Za-z_][A-Za-z0-9_.]*)=([^\s]*)")
UNREADABLE = {"", "none", "unknown", "unreadable", "n/a", "-"}
STATUS_LINE_TAGS = ("appmonitor:", "ui:", "diag:", "MaxManager:")


def timestamp_ms(line: str) -> int | None:
    """زمن السطر بالمللي‌ثانية من بداية اليوم — كافٍ للفروق داخل الجلسة الواحدة."""
    match = TIME_RE.match(line)
    if not match:
        return None
    stamp = match.group(1)
    hh, mm, rest = stamp[11:13], stamp[14:16], stamp[17:]
    ss, _, millis = rest.partition(".")
    return ((int(hh) * 60 + int(mm)) * 60 + int(ss)) * 1000 + int((millis + "000")[:3])


def fields_of(line: str) -> dict[str, str]:
    return {name: value for name, value in FIELD_RE.findall(line)}


def event_of(line: str) -> str:
    match = re.search(r"EVENT=([A-Z0-9_]+)", line)
    return match.group(1) if match else ""


def ceiling_of(value: str | None) -> int | None:
    """سقف القيمة في صيغ المشروع الثلاث — مرآة `HardwareVerification.ceilingOf`."""
    if value is None:
        return None
    head = value.split("|", 1)[0].strip()
    field = head.rsplit(":", 1)[-1].strip() if ":" in head else head
    try:
        return int(field)
    except ValueError:
        return None


def knob_node(knob: str) -> str | None:
    """عقدة المقبض كما تظهر في `WRITE_CHECK path=` … أو `None` لمقبض لا عقدة له."""
    if knob.startswith("gpu_frequency:"):
        return knob.split(":", 1)[1]
    if knob.startswith("cpu_limits:"):
        return f"/{knob.split(':', 1)[1]}/scaling_max_freq"
    if knob.startswith("cpu_governor:"):
        return f"/{knob.split(':', 1)[1]}/scaling_governor"
    if knob.startswith("gpu_governor:"):
        return f"{knob.split(':', 1)[1]}/governor"
    return None


@dataclass
class Write:
    at_ms: int
    path: str
    wrote: str
    verdict: str
    read: str = ""
    #: السطر الخام: شهادة التحرير جملةٌ فيها مسافة (`read=[GPUFREQ-DEBUG] … is disabled`)،
    #: فيُقرأ من السطر لا من الحقل المقتطع عند أوّل مسافة — والفرق مقيس: بالحقل وحده رسب الاختبار.
    raw: str = ""


# شهادات النواة على أن الكتابة **تحريرُ قفل** لا قيمة تردد.
#
# ولماذا يلزم ذلك في الحكم: على MediaTek يُكتب `-1` في `fix_target_opp_index` فتُجيب النواة بجملة
# (`[GPUFREQ-DEBUG] fix GPU/STACK OPP index is disabled`) لا برقم ⇒ طبقة الكتابة العامة تُسجّل
# `verdict=differs`، وهو **اختلاف تمثيل لا اختلاف قيمة** (نظيره في Kotlin: `MtkGpuOppTable.parseIndex`
# يقرأ جملة «disabled» تحريرًا). وحصرُ الدليل في `matched` يجعل كل تحرير قفل يُقرأ «بلا كتابة».
RELEASE_ACK_TOKENS = ("disabled", "dynamic", "unlimited", "no limit", "unfixed")


def is_release_acknowledgement(write: Write) -> bool:
    """هل هذا سطرُ تحرير قفل شهدته النواة بجملة لا برقم؟"""
    haystack = (write.raw or write.read).lower()
    return write.wrote.strip() == "-1" and any(token in haystack for token in RELEASE_ACK_TOKENS)


@dataclass
class Finding:
    gate: str
    detail: str


@dataclass
class Report:
    lines: int = 0
    sessions: int = 0
    commits: int = 0
    commits_checked: int = 0
    commits_unchecked: int = 0
    writes: int = 0
    #: كم ادّعاءَ نجاحٍ استند إلى كتابة **تحرير** لا إلى كتابة تردد مطابقة (يُعلَن في التقرير).
    release_backed: int = 0
    drift_claims: int = 0
    median_latency_ms: int | None = None
    worst_latency_ms: int | None = None
    findings: list[Finding] = field(default_factory=list)

    def failed(self, gate: str) -> list[Finding]:
        return [item for item in self.findings if item.gate == gate]

    @property
    def ok(self) -> bool:
        return not self.findings


def analyse(text: str, window_ms: int = WRITE_WINDOW_MS) -> Report:
    """يقرأ سجلًّا موحّدًا ويُنتج تقريرًا — دالة خالصة، فلا قرص ولا شبكة، وتُقاس باختبار."""
    report = Report()
    # سلّم زمني واحد: الكتابات ومواضعها، وسطر كل مقبض بعده.
    writes: list[Write] = []
    switch_pkg: dict[str, str] = {}
    commit_times: dict[str, list[int]] = {}
    label_drift = 0
    label_examples: list[str] = []
    unnamed_failure = 0
    unnamed_examples: list[str] = []

    for line in text.splitlines():
        event = event_of(line)
        if not event:
            continue
        report.lines += 1
        at_ms = timestamp_ms(line)
        data = fields_of(line)
        status_line = any(tag in line for tag in STATUS_LINE_TAGS)

        if event == "APP_SWITCH":
            sw, pkg = data.get("sw"), data.get("pkg")
            if sw and pkg and sw not in switch_pkg:
                switch_pkg[sw] = pkg
            continue

        if event == "WRITE_CHECK":
            report.writes += 1
            if at_ms is not None:
                writes.append(
                    Write(
                        at_ms,
                        data.get("path", ""),
                        data.get("wrote", ""),
                        data.get("verdict", ""),
                        data.get("read", ""),
                        line,
                    )
                )
            continue

        if event == "PERAPP_COMMIT":
            report.commits += 1
            knobs = data.get("knob", "")
            node = knob_node(knobs)
            if data.get("verified") == "true":
                if node is None:
                    report.commits_unchecked += 1
                else:
                    report.commits_checked += 1
                    wanted = ceiling_of(data.get("requested"))
                    live = ceiling_of(data.get("live"))
                    enough = live is not None and wanted is not None and live >= wanted
                    if not enough:
                        # كتابةٌ على عقدة المقبض لا تناقض الطلب — أو الكتابة التي نسقط عندها
                        # هي التي نمنعها: أثرُ خطوةٍ سابقة يُقرأ «مُلبّى».
                        def _frequency_evidence(write: Write) -> bool:
                            return (
                                node in write.path
                                and write.verdict == "matched"
                                and (ceiling_of(write.wrote) or 0) >= (wanted or 0)
                            )

                        in_window = [
                            write
                            for write in writes
                            if at_ms is not None and write.at_ms <= at_ms and at_ms - write.at_ms <= window_ms
                        ]
                        backed = any(_frequency_evidence(write) for write in in_window)
                        if not backed:
                            # الشكل الثاني للدليل: طلبٌ لا يُلبّى بكتابة تردد بل بزوال قفلٍ يخنقه
                            # (تحرير سقف المنصّة/قفل OPP) — يُقبل **ويُعلَن** عدده في التقرير.
                            backed = any(is_release_acknowledgement(write) for write in in_window)
                            if backed:
                                report.release_backed += 1
                        if not backed:
                            report.findings.append(
                                Finding(
                                    "write-proof",
                                    f"{line.split(' ', 2)[-1][:200]} — طُلب {data.get('requested')} "
                                    f"و`live={data.get('live')}` بلا سطر كتابة على `{node}`",
                                )
                            )
            if knobs and at_ms is not None and data.get("sw"):
                commit_times.setdefault(data["sw"], []).append(at_ms)
            # الحزمة في سطر المقبض يجب أن توافق معرّف التبديل نفسه.
            if data.get("sw") and data.get("pkg") and status_line:
                expected_pkg = switch_pkg.get(data.get("sw", ""))
                if expected_pkg and expected_pkg != data.get("pkg"):
                    label_drift += 1
                    if len(label_examples) < 3:
                        label_examples.append(f"sw={data['sw']} يقول pkg={data['pkg']} والتبديل لتطبيق {expected_pkg}")
            if data.get("verified") == "false":
                reason = data.get("error", "")
                if reason.strip().lower() in UNREADABLE or not reason.strip():
                    unnamed_failure += 1
                    if len(unnamed_examples) < 3:
                        unnamed_examples.append(line.strip()[:200])
            continue

        if event == "PERAPP_KNOB":
            if data.get("sw") and data.get("pkg") and status_line:
                expected_pkg = switch_pkg.get(data.get("sw", ""))
                if expected_pkg and expected_pkg != data.get("pkg"):
                    label_drift += 1
                    if len(label_examples) < 3:
                        label_examples.append(f"sw={data['sw']} يقول pkg={data['pkg']} والتبديل لتطبيق {expected_pkg}")
            if data.get("outcome") in {"not_verified", "blocked"}:
                reason = data.get("reason", "")
                if reason.strip().lower() in UNREADABLE or not reason.strip():
                    unnamed_failure += 1
                    if len(unnamed_examples) < 3:
                        unnamed_examples.append(line.strip()[:200])
            continue

        if event in {"APPLY_DRIFT_REPAIRED", "APPLY_DRIFT"}:
            report.drift_claims += 1
            wanted = ceiling_of(data.get("expected"))
            live = ceiling_of(data.get("live"))
            if wanted is None or live is None or live >= wanted:
                continue
            # الكتابة تجعل الإصلاح حقيقة، وغيابها تجعله ادّعاءً.
            backed = at_ms is not None and any(
                write.at_ms <= at_ms and at_ms - write.at_ms <= window_ms and (ceiling_of(write.wrote) or 0) >= wanted
                for write in writes
            )
            if not backed:
                report.findings.append(
                    Finding(
                        "drift-proof",
                        f"{event} expected={data.get('expected')} live={data.get('live')} knob={data.get('knob')}"
                        " — إعلان إصلاح بلا سطر كتابة",
                    )
                )
            continue

        if event == "APPLY_DRIFT_REASSERT_FAILED":
            reason = data.get("error", "")
            if reason.strip().lower() in UNREADABLE or not reason.strip():
                unnamed_failure += 1
                if len(unnamed_examples) < 3:
                    unnamed_examples.append(line.strip()[:200])

    # زمن الوصول: من `APP_SWITCH` إلى آخر `PERAPP_COMMIT` لنفس المعرّف.
    latencies: list[int] = []
    for sw, times in commit_times.items():
        switch_line_at = _switch_time_ms(text, sw)
        if switch_line_at is None or not times:
            continue
        latencies.append(max(times) - switch_line_at)
    report.sessions = len(commit_times)
    if latencies:
        report.median_latency_ms = int(statistics.median(latencies))
        report.worst_latency_ms = max(latencies)

    if label_drift:
        report.findings.append(
            Finding("session-label", f"{label_drift} سطرًا يحمل حزمةً تخالف معرّف تبديله — مثال: " + " · ".join(label_examples))
        )
    if unnamed_failure:
        report.findings.append(
            Finding("named-failure", f"{unnamed_failure} فشلًا بلا رمز سبب مقروء — مثال: " + " · ".join(unnamed_examples))
        )
    return report


def _switch_time_ms(text: str, sw: str) -> int | None:
    for line in text.splitlines():
        if event_of(line) == "APP_SWITCH" and f"sw={sw}" in line:
            return timestamp_ms(line)
    return None


def apply_latency_gate(report: Report, max_median: int, max_worst: int) -> None:
    if report.median_latency_ms is None:
        return
    if report.median_latency_ms > max_median:
        report.findings.append(
            Finding("switch-latency", f"وسيط زمن التبديل {report.median_latency_ms}ms يتجاوز السقف {max_median}ms")
        )
    if report.worst_latency_ms is not None and report.worst_latency_ms > max_worst:
        report.findings.append(
            Finding("switch-latency", f"أسوأ زمن تبديل {report.worst_latency_ms}ms يتجاوز السقف {max_worst}ms")
        )


# ── قراءة الحزمة ────────────────────────────────────────────────────────────────
LOG_CANDIDATES = ("log/MaxManager.log", "MaxManager.log", "debug/MaxManager.log", "MaxManagerConfig/debug/MaxManager.log")


def read_bundle(path: Path) -> tuple[str, str]:
    """يعيد (نصّ السجل، اسم المصدر). ويقبل `.tar.gz` · مجلَّدًا · ملف سجل مباشرةً."""
    if path.is_file() and tarfile.is_tarfile(path):
        with tempfile.TemporaryDirectory() as tmp:
            with tarfile.open(path) as archive:
                archive.extractall(tmp, filter="data")
            return read_bundle(Path(tmp))
    if path.is_dir():
        for candidate in LOG_CANDIDATES:
            target = path / candidate
            if target.is_file():
                return target.read_text(encoding="utf-8", errors="replace"), candidate
        found = sorted(path.rglob("*.log"))
        if found:
            return found[0].read_text(encoding="utf-8", errors="replace"), found[0].name
        raise SystemExit(f"لا ملف سجل داخل: {path}")
    if path.is_file():
        return path.read_text(encoding="utf-8", errors="replace"), path.name
    raise SystemExit(f"مسار غير موجود: {path}")


# ── العرض ───────────────────────────────────────────────────────────────────────
def render(report: Report) -> str:
    gates = [
        ("write-proof", "لا ادّعاء نجاح بلا كتابة"),
        ("drift-proof", "لا إصلاح انحراف بلا كتابة"),
        ("session-label", "لا خلط حزم في السجل"),
        ("switch-latency", "زمن التبديل داخل السقف"),
        ("named-failure", "كل فشل يحمل رمز سببه"),
    ]
    out = [
        "",
        f"أسطر مقروءة: {report.lines} · جلسات: {report.sessions} · كتابات: {report.writes}",
        f"PERAPP_COMMIT: {report.commits} (مفحوص {report.commits_checked} · بلا عقدة {report.commits_unchecked})",
        f"إعلانات انحراف: {report.drift_claims}",
        f"نجاحات مسنودة بكتابة **تحرير** (لا تردد مطابق): {report.release_backed}",
        f"زمن التبديل: وسيط {report.median_latency_ms if report.median_latency_ms is not None else '—'}ms"
        f" · أسوأ {report.worst_latency_ms if report.worst_latency_ms is not None else '—'}ms",
        "",
    ]
    for gate, title in gates:
        found = report.failed(gate)
        mark = "PASS" if not found else f"FAIL ({len(found)})"
        out.append(f"[{mark}] {gate} — {title}")
        for item in found[:5]:
            out.append(f"        · {item.detail}")
        if len(found) > 5:
            out.append(f"        · … و{len(found) - 5} أخرى")
    out.append("")
    out.append("الحكم: PASS ✅" if report.ok else "الحكم: FAIL ❌")
    return "\n".join(out)


# ── قياس الأداة نفسها ───────────────────────────────────────────────────────────
GOOD_LOG = """2026-09-22 22:00:00.000 I appmonitor: EVENT=APP_SWITCH pkg=com.example.game prev=com.example.home sw=sw-1
2026-09-22 22:00:03.000 I ui: EVENT=WRITE_CHECK path=/sys/class/devfreq/mali0/min_freq wrote=260000000 read=260000000 verdict=matched
2026-09-22 22:00:03.100 I ui: EVENT=WRITE_CHECK path=/sys/class/devfreq/mali0/max_freq wrote=702000000 read=702000000 verdict=matched
2026-09-22 22:00:04.000 I appmonitor: EVENT=PERAPP_KNOB knob=gpu_profile outcome=applied reason=verified expected=702000000 live=702000000 pkg=com.example.game sw=sw-1
2026-09-22 22:00:05.000 I appmonitor: EVENT=PERAPP_COMMIT pkg=com.example.game knob=gpu_frequency:mali0 requested=702000000 applied=true verified=true attempts=1 live=702000000 error=none sw=sw-1
"""


def self_test() -> int:
    """يقيس الأداة: سجل سليم يمرّ، وكل عطب مُحقَن يُكتشف وحدَه."""
    cases: list[tuple[str, str, str]] = []

    good = analyse(GOOD_LOG)
    assert good.ok, f"سجل سليم رسب: {[item.detail for item in good.findings]}"
    assert good.commits_checked == 1 and good.median_latency_ms == 5000, (good.commits_checked, good.median_latency_ms)
    cases.append(("سجل سليم", "PASS", "PASS" if good.ok else "FAIL"))

    # العطب المقيس حرفيًّا: طلبُ رفع بعد خفضٍ كتبناه، بلا كتابة.
    skipped = GOOD_LOG.replace(
        "2026-09-22 22:00:03.100 I ui: EVENT=WRITE_CHECK path=/sys/class/devfreq/mali0/max_freq wrote=702000000 read=702000000 verdict=matched\n",
        "",
    ).replace("requested=702000000 applied=true verified=true attempts=1 live=702000000", "requested=702000000 applied=true verified=true attempts=1 live=520000000")
    bad = analyse(skipped)
    assert not bad.ok and bad.failed("write-proof"), "تخطّي الكتابة لم يُكتشف"
    cases.append(("نجاح بلا كتابة", "FAIL", "FAIL" if not bad.ok else "PASS"))

    # والانحراف يُبنى على سجل **بلا** كتابة مطابقة: وإلا فالكتابة التي سبقته هي الدليل نفسه.
    drifted = skipped + (
        "2026-09-22 22:00:06.000 I appmonitor: EVENT=APPLY_DRIFT_REPAIRED knob=gpu_frequency:mali0"
        " pkg=com.example.game expected=702000000 live=520000000 sw=sw-1\n"
    )
    drift_bad = analyse(drifted)
    assert drift_bad.failed("drift-proof"), "إعلان إصلاح بلا كتابة لم يُكتشف"
    cases.append(("إصلاح انحراف كاذب", "FAIL", "FAIL" if not drift_bad.ok else "PASS"))

    labelled = GOOD_LOG.replace("pkg=com.example.game sw=sw-1", "pkg=com.example.other sw=sw-1")
    label_bad = analyse(labelled)
    assert label_bad.failed("session-label"), "خلط الحزم لم يُكتشف"
    cases.append(("سطر منسوب لتطبيق آخر", "FAIL", "FAIL" if not label_bad.ok else "PASS"))

    slow = GOOD_LOG.replace("22:00:05.000", "22:00:40.000")
    slow_report = analyse(slow)
    apply_latency_gate(slow_report, 20_000, 30_000)
    assert slow_report.failed("switch-latency"), "زمن التبديل البطيء لم يُكتشف"
    cases.append(("زمن تبديل فوق السقف", "FAIL", "FAIL" if not slow_report.ok else "PASS"))

    unnamed = GOOD_LOG + (
        "2026-09-22 22:00:07.000 I appmonitor: EVENT=PERAPP_KNOB knob=cpu_governor:policy0"
        " outcome=not_verified reason=none expected=performance live=none pkg=com.example.game sw=sw-1\n"
    )
    unnamed_bad = analyse(unnamed)
    assert unnamed_bad.failed("named-failure"), "فشل بلا رمز لم يُكتشف"
    cases.append(("فشل بلا رمز سبب", "FAIL", "FAIL" if not unnamed_bad.ok else "PASS"))

    # وقراءة السقف المرمَّزة (`node|upbound|cooling|lock`) تُقرأ من حقلها الأول، فلا يُصنّف
    # النجاح فشلًا لأن الصيغة ليست رقمًا مجرّدًا.
    held = GOOD_LOG.replace("requested=702000000 applied=true verified=true attempts=1 live=702000000", "requested=702000000 applied=true verified=true attempts=1 live=702000000|0|released|unlocked")
    held_report = analyse(held)
    assert held_report.ok, f"قراءة السقف المرمَّزة رسبت: {[i.detail for i in held_report.findings]}"
    cases.append(("قراءة سقف مُرمَّزة", "PASS", "PASS" if held_report.ok else "FAIL"))

    # ولا يُقرأ**تحرير القفل** «بلا كتابة»: على MTK تُجيب النواة بجملة (`disabled`) فيُسجّل
    # `verdict=differs`، وهو اختلاف تمثيل لا اختلاف قيمة. والمُدَّعى: الدليل يُقبل **ويُعلَن عدده**.
    released_log = skipped.replace(
        "2026-09-22 22:00:03.000 I ui: EVENT=WRITE_CHECK path=/sys/class/devfreq/mali0/min_freq wrote=260000000 read=260000000 verdict=matched\n",
        "2026-09-22 22:00:03.000 I ui: EVENT=WRITE_CHECK path=/proc/gpufreqv2/fix_target_opp_index"
        " wrote=-1 read=[GPUFREQ-DEBUG] fix GPU/STACK OPP index is disabled verdict=differs\n",
    )
    released_report = analyse(released_log)
    assert released_report.ok, f"كتابة التحرير رسبت: {[i.detail for i in released_report.findings]}"
    assert released_report.release_backed == 1, released_report.release_backed
    cases.append(("تحرير قفل بشهادة النواة", "PASS", "PASS" if released_report.ok else "FAIL"))

    print("قياس الأداة نفسها:")
    ok = True
    for title, expected, actual in cases:
        mark = "✅" if expected == actual else "❌"
        ok = ok and expected == actual
        print(f"  {mark} {title}: متوقَّع {expected} · حصلنا {actual}")
    print("\nالأداة تقيس ما تدّعيه." if ok else "\nالأداة لا تقيس ما تدّعيه.")
    return 0 if ok else 1


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="بوابة حكم على حزمة سجل جهاز حقيقي")
    parser.add_argument("bundle", nargs="?", help="حزمة .tar.gz أو مجلَّد أو ملف سجل")
    parser.add_argument("--max-median-latency-ms", type=int, default=MAX_MEDIAN_SWITCH_LATENCY_MS)
    parser.add_argument("--max-latency-ms", type=int, default=MAX_SWITCH_LATENCY_MS)
    parser.add_argument("--write-window-ms", type=int, default=WRITE_WINDOW_MS)
    parser.add_argument("--json", action="store_true", help="تقرير للقراءة الآلية")
    parser.add_argument("--assert", dest="assert_on_fail", action="store_true", help="exit 1 عند أي فشل")
    parser.add_argument("--self-test", action="store_true", help="تقيس الأداة نفسها")
    args = parser.parse_args(argv)

    if args.self_test:
        return self_test()
    if not args.bundle:
        parser.error("يلزم مسار حزمة (أو --self-test)")

    text, source = read_bundle(Path(args.bundle))
    report = analyse(text, window_ms=args.write_window_ms)
    apply_latency_gate(report, args.max_median_latency_ms, args.max_latency_ms)

    if args.json:
        print(
            json.dumps(
                {
                    "source": source,
                    "lines": report.lines,
                    "sessions": report.sessions,
                    "commits": report.commits,
                    "commits_checked": report.commits_checked,
                    "commits_unchecked": report.commits_unchecked,
                    "writes": report.writes,
                    "release_backed": report.release_backed,
                    "median_latency_ms": report.median_latency_ms,
                    "worst_latency_ms": report.worst_latency_ms,
                    "ok": report.ok,
                    "findings": [{"gate": item.gate, "detail": item.detail} for item in report.findings],
                },
                ensure_ascii=False,
                indent=2,
            )
        )
    else:
        print(f"المصدر: {source}")
        print(render(report))

    return 1 if (args.assert_on_fail and not report.ok) else 0


if __name__ == "__main__":
    sys.exit(main())
