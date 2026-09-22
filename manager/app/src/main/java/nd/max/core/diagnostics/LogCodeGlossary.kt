package nd.max.core.diagnostics

import nd.max.core.hardware.HardwareControlKey

/**
 * قاموس السجل: **معنى كل حقل وكل رمز** — يُكتب داخل ملف السجل نفسه، لا في المستودع.
 *
 * لماذا وُجد
 * ----------
 * الغرض المعلَن: أن يُرسَل ملف السجل وحده فيُشخَّص العطل منه بلا جهاز ولا واجهة ولا سؤال. وهذا
 * يفرض شرطًا لم يكن قائمًا: **الملف يجب أن يشرح نفسه**. وقبل هذا الملف كان السطر يقول
 * `expected=1300000000 live=754000000 verdict=differs` — وثلاثة أرقام ورموز لا تعني شيئًا
 * لمن لا يعرف أن `gpu_profile` بالهرتز و`cpu_limits:policy0` بالكيلوهرتز، وأن `differs` تعني
 * «كُتب ثم قُرئ مختلفًا» لا «فشل الأمر».
 *
 * فالقاموس هنا **ثلاث طبقات**:
 *
 * 1. [fieldGuide] — شكل السطر ومعنى كل حقل موجود فيه.
 * 2. [unitGuide] — **وحدة كل مقبض**؛ وهذا أخطر ما كان غائبًا: `1300000000` و`1300000` قد يكونان
 *    الطلب نفسه بوحدتين، ومن يقرأ بلا وحدة يستنتج عطلًا غير موجود.
 * 3. [codes] — معنى كل `reason`/`outcome`/`verdict` يكتبه المحرّك، مع **ما يعنيه عمليًّا** لا
 *    ترجمة حرفية له.
 *
 * وقواعد هذا الملف:
 *
 * - **كل رمز هنا موجود في الكود.** لا رمز مُتخيَّل ولا مُترجَم من الذاكرة؛ والقائمة جُمعت من
 *   مواضع الكتابة الفعلية (`HardwareControlArbiter` · `PerAppHardwareStatus` · `WriteVerification` ·
 *   `ThermalCeilingRouter` · `AtlasAdaptiveExecutor` · `AtlasRoutePlanner` · `AppMonitor`).
 * - **النصّ بلا علامة `=`** لأن السطر يُفكَّك على «بداية حقل»؛ فمعنى فيه `=` كان سيُقسَم إلى حقلين.
 * - **الترتيب ثابت** (لا مُرتَّب أبجديًّا): الرموز المتقاربة تبقى متجاورة ليُقرأ الفرق بينها بلمحة.
 */
object LogCodeGlossary {

    /**
     * المقابض ذات البادئة، **مكتوبة من [HardwareControlKey] لا منسوخة منه**.
     *
     * ولماذا لزم هذا: الدليل يشرح الوحدة بالاسم الكامل للمقبض (`cpu_limits:<policy>`)، وهي
     * الكتابة الثانية للمقبض نفسه. ونسخها نصًّا يجعل تعديل البادئة في موضع يترك الدليل يشرح
     * مقبضًا لم يبقَ له وجود — وهو نفس ما تمنعه بوابة عدم اختراع المفاتيح. فيُبنى النصّ من مالكه.
     *
     * والقيمة المعروضة لم تتغيّر حرفًا واحدًا: `POLICY` و`DEVICE` هما ما يطبع القراءة.
     */
    private val cpuLimitsKnob: String = HardwareControlKey.CPU_LIMITS_PREFIX + "POLICY"
    private val gpuFrequencyKnob: String = HardwareControlKey.GPU_FREQUENCY_PREFIX + "DEVICE"

    /** شكل السطر ومعنى كل حقل — أول ما يحتاجه من يفتح الملف. */
    val fieldGuide: List<Pair<String, String>> = listOf(
        // بلا علامة `=` داخل النصّ: الترويسة تُكتب في السجل بنفس صيغة الحقول، وعلامة `=` هنا
        // كانت ستُقسّم هذا السطر إلى حقلين عند القراءة.
        "line" to "TIME LEVEL TAG: an EVENT token followed by space-separated key-value fields; TAG says which process wrote it",
        "knob" to "the control being written: $cpuLimitsKnob $gpuFrequencyKnob cpu_boost gpu_profile thermal refresh_rate",
        "path" to "the exact node a write went to when the event is not knob-scoped",
        "outcome" to "applied | not-verified | not-writable | unsupported | blocked | skipped",
        "reason" to "stable machine code; each one is explained in the legend below",
        "expected" to "what the app asked for, in the unit of that knob",
        "live" to "the value read back after the write (unreadable means the read itself failed)",
        "verdict" to "matched | differs | write_failed | unreadable",
        "ok" to "true only when the operation itself finished without an error",
        "duration_ms" to "measured duration of the operation, not an estimate",
        "decision" to "the Atlas route decision: status-REASON (for example eligible-selected)",
        "realization" to "how the GPU ceiling was realised here: release-only (no frequency written, the platform ceiling was lifted) | range (a min:max write) | pin (a fixed OPP index, the only writer this device accepts) | unsupported",
        "clock_before/clock_now" to "the GPU frequency the device itself reports, in Hz, before and after the request; unreadable means no frequency node answered",
        "pinned" to "the OPP frequency the fixed index now names, or none when no index was written",
        "judgement" to "the measured verdict of the request: a ceiling verdict, a pin verdict, or range when the ceiling is not what was measured",
        "chosen" to "the Atlas route that was actually executed; none means no route was picked",
        "skipped" to "routes not attempted, each with its own reason code",
        "pressure" to "platform thermal status: unknown none light moderate severe critical emergency shutdown",
        "from" to "the value before this decision",
        "to" to "the value this decision requested",
        "pkg" to "the app the decision was scoped to; absent for global settings",
        "sw" to "the session id, so events from one session can be separated from the next",
        "screen" to "the UI screen that caused the action (user actions only)",
        "action" to "the operation name (user actions and measured results only)",
        "field/old/new" to "the setting that changed and its before and after values",
    )

    /**
     * وحدة كل مقبض — **الطبقة التي كان غيابها يُنتج استنتاجات خاطئة**.
     *
     * والمبدأ: الوحدة تتبع المقبض لا السطر، لأن نفس السطر يحمل مقابض مختلفة الوحدات في نفس الجلسة.
     */
    val unitGuide: List<Pair<String, String>> = listOf(
        cpuLimitsKnob to "kHz, as min:max (an empty side means leave that side alone)",
        "cpu_boost" to "kHz, as min:max over the policy minimums",
        gpuFrequencyKnob to "Hz, a single ceiling value",
        "gpu_profile" to "percent of the highest advertised GPU step, not a frequency",
        "refresh_rate" to "Hz",
        "thermal" to "platform thermal status 0..6, not a temperature",
        "value_ms/duration_ms/worst_ms" to "milliseconds",
        "snapshot values" to "whatever unit the source node prints; the unit is recorded in the text itself",
    )

    /**
     * كل رمز يكتبه المحرّك، ومعناه العمليّ.
     *
     * والشرح مقصود أن يقول **ما يترتّب على الرمز** لا أن يعيد صياغته: الفرق بين
     * `apply-not-verified-baseline-restored` و`...-and-rollback-failed` هو الفرق بين «تعافى»
     * و«الحالة غير معروفة» — وهذا هو ما يُبنى عليه القرار التالي.
     */
    val codes: List<Pair<String, String>> = listOf(
        // نتائج المقابض
        "applied" to "the request is satisfied (for a ceiling: the live value does not exceed it)",
        "skipped" to "not attempted on purpose, for example the user chose default",
        "blocked" to "refused by precedence: a manual user lock or a higher-priority owner holds the knob",
        "unsupported" to "the device does not advertise this control at all",
        "not-writable" to "the control exists but cannot be written here (read-only or measurement provider)",
        "not-verified" to "written and then not satisfied, so the baseline was restored",
        // لماذا لم يُطبَّق (أسباب per-app)
        "verified" to "the write was read back and satisfied the request",
        "profile-is-default" to "the user left this knob at default, so nothing is enforced",
        "curve-does-not-cap" to "the chosen thermal profile asks for no ceiling below the device capability, so no cap was written",
        "no-lower-advertised-step" to "the device advertises no step below the requested percentage, so no cap was written instead of writing one above it",
        "power-profile-above-live" to "performance or gaming raised the live CPU ceiling to the capability the driver advertises, and the write was verified",
        "power-profile-never-lowers" to "performance or gaming would not raise the live ceiling (it already meets the requested share of capability), so nothing was written: a power profile never lowers it",
        "cooling-below-live" to "the cooling profile wrote a ceiling below the live one, snapped down inside the device capability",
        "cooling-already-at-or-below-percent" to "the live ceiling is already at or below the requested percentage, so no cap was written",
        "governor-is-default" to "the user left the governor at default",
        "governor-not-advertised" to "the requested governor name is not in this kernel's list",
        "policy-unavailable" to "the CPU policy named in the key does not exist right now",
        "no-gpu-provider" to "no GPU frequency provider was discovered on this device",
        "gpu-ceiling-released" to "the ceiling was measured after the request and nothing caps the GPU below the requested value: a platform ceiling was released, or none was held",
        "gpu-ceiling-held" to "the ceiling was measured and something still caps the GPU below the request: a custom GED upbound or a raised GPU cooling state",
        "gpu-opp-lock-held" to "a fixed-OPP lock is active on the GPU, so the clock is frozen at one step no matter what the frequency nodes say: the ceiling was not achieved",
        "gpu-ceiling-open-below-request" to "the release was executed and measured: every channel this app owns is open, and a ceiling it does not own still sits below the request, so the device is as open as this platform allows; the number after the colon is that live ceiling in Hz",
        "gpu-pinned-at-request" to "the range could not carry the request, so the GPU was pinned through a fixed OPP index at or above it; the index in use before the pin is restored when the app leaves",
        "gpu-node-ceiling-unreadable" to "the node ceiling could not be read, or its frequency unit is unknown, so no claim is made either way",
        "explicit-frequency-wins-over-profile" to "this app config carries both a GPU profile and an explicit GPU frequency, which the screen no longer allows: the explicit frequency is what runs, because it is the value shown in the app's own frequency row",
        "pin-verified" to "the OPP index was accepted and the device reports exactly the pinned frequency as its running clock",
        "verified-by-index" to "the OPP index was accepted and the running clock could not be read, so the kernel echo is the strongest evidence available",
        "pin-clock-mismatch" to "the kernel accepted the OPP index while the GPU runs at another frequency: the index does not name the frequency we asked for",
        "pin-unreadable" to "the fixed-index node could not be read, so neither the pin nor its frequency is confirmed",
        "no-advertised-frequency-range" to "the GPU node exposes no frequency list to clamp against",
        "provider-disappeared" to "the provider existed at discovery time and is gone now",
        "unsupported-frequency" to "the requested frequency is below the lowest advertised step",
        "outside-proven-hardware-bounds" to "the request is outside the range the device proved it accepts",
        "live-value-mismatch" to "the read back value differs from what was asked for",
        "restored" to "the baseline was put back",
        "undecided" to "the route planner returned no decision for this attempt",
        "unknown" to "the cause was not recognised; kept as unknown instead of guessed",
        // بوّابة الملكية (المُحكِّم)
        "manual-lock" to "the user pinned this knob; automated owners must not write it",
        "preempted-by-OWNER" to "a higher-priority owner holds the knob; OWNER is SYSTEM MAX_AI or PER_APP",
        "no-winner" to "the shared journal has no winner for this knob",
        "baseline-unreadable" to "the current value could not be read, so no transaction may start",
        "live-read-unavailable" to "the read failed inside the transaction",
        "apply-not-verified-baseline-restored" to "the write did not take and the baseline came back verified",
        "apply-not-verified-and-rollback-failed" to "the write did not take and the baseline could not be confirmed: state unknown",
        "restore-not-verified" to "the restore ran but the read back did not match the baseline",
        "handoff-awaiting-owner-process" to "another process owns the knob; this one only registered its intent",
        // حكم الكتابة
        "matched" to "wrote and read back the same value",
        "differs" to "wrote a value and read back a different one; a vendor governor or a driver clamp can do this",
        "write_failed" to "the write command itself failed",
        "unreadable" to "the write happened but the read back failed; no verdict is claimed",
        // الحارس الحراري (Atlas)
        "route-verified" to "the chosen route was executed and confirmed",
        "ceiling-already-held" to "the platform route needs no write: the live ceiling already satisfies the request",
        "user-ceiling-already-held" to "the user ceiling is already in place and the platform signal is unavailable",
        "ceiling-not-planable" to "the requested ceiling could not be parsed into a plan",
        "knob-not-owned" to "this process does not own the knob, so the guard refuses to touch it",
        "no-route-transaction" to "no route produced a usable transaction",
        "thermal-router-unavailable" to "the Atlas route router could not be initialised; the guard stays silent",
        "rollback-not-verified" to "fallback stopped because the baseline could not be confirmed",
        "guard-static-only:platform-thermal-status-unavailable" to
            "the platform thermal signal is unavailable, so only the user ceiling is enforced",
        "guard-idle:PRESSURE" to "the platform reports no thermal throttling; nothing to change",
        // Atlas
        "route-quarantined-after-unverified-rollback" to
            "this route stranded the baseline earlier in this boot; it is not attempted again until reboot",
        "privilege-unavailable" to "the route needs a privilege this process does not have",
        "rollback-unproven" to "the route has no proven way to restore the baseline",
        "unit-ambiguous" to "the route's value unit could not be established",
        "provider-ambiguous" to "more than one provider matched, so none was chosen",
        "goal-unmeasurable" to "the route has no measurable signal for the goal it claims",
        "route-not-reviewed" to "the route is not reviewed yet, so it is not executed",
        "selected" to "the planner selected this route; no rejection reason applies",
        "platform-route-available" to "a platform-owned route exists",
        "verified-vendor-route" to "a vendor bridge route was verified on this device",
        "verified-daemon-route" to "a root daemon route was verified on this device",
        "verified-arbiter-route" to "an arbiter sysfs route was verified on this device",
    )

    /**
     * ما يُفعل عند كل عطل — طبقة **العمل** لا الشرح.
     *
     * ولماذا لزمت: `explain` يقول «كُتب فلم يتحقّق فاستُرجع خط الأساس»، وهي جملة صحيحة ولا تعين
     * على شيء. الفرق بين ملف سجل يُقرأ وملف يُشخَّص هو أن الثاني يجيب «وبعدين؟» — وقد كُتب هذا
     * الجدول ليكون ذلك الجواب: **ما يفعله من يقرأ الملف**، لا معنى الرمز مرّة أخرى.
     *
     * وقاعدة صارمة: لا يدخل هنا رمز **يُعدّ عملًا صحيحًا** (`applied` · `verified` ·
     * `profile-is-default`)، لأن إدراجه يزرع في القارئ فشلًا غير موجود. ومن لا يوجد له إصلاح
     * يُعلَن صراحةً في التقرير بـ`fix=no-fix-encoded` — فهو رمز جديد يحتاج درسًا، لا عطلًا يُخفي.
     */
    val remedies: List<Pair<String, String>> = listOf(
        // فشل التطبيق: القيمة نفسها
        "unsupported-frequency" to "raise the request to at least the lowest advertised step printed in this line",
        "outside-proven-hardware-bounds" to "bring the request back into the proven range, or clear the proven-bounds record after a kernel update",
        "no-advertised-frequency-range" to "the node exposes no step list, so no ceiling can be clamped; treat this control as unsupported on this unit",
        "no-gpu-provider" to "support gap, not a setting; check the module install and this device's GPU node list",
        "gpu-ceiling-held" to "the app re-asserts the release on every drift pass; if it keeps being held, the vendor thermal service is rewriting the cap and the control is effectively co-owned by it",
        "gpu-opp-lock-held" to "the app clears the fixed-OPP lock inside the same owned transaction and restores the old index on exit; if the lock keeps coming back, another tool (kernel manager or the vendor game service) is writing the same node",
        "gpu-node-ceiling-unreadable" to "read the node named in the capability scan line by hand before judging this request",
        "pin-clock-mismatch" to "the index and the clock disagree: treat this device as range-only (clear the fixed-index path) instead of pinning an OPP",
        "pin-unreadable" to "read the fixed-index node by hand; if it does not echo a number the pin cannot be verified on this build",
        "provider-disappeared" to "re-open the app screen to re-discover the provider; if it keeps disappearing the vendor driver is resetting",
        "live-value-mismatch" to "re-read the node after a few seconds; if it keeps differing the vendor governor is rewriting it and the control is effectively unsupported",
        "unsupported" to "support gap: the control does not exist on this device; no setting makes it work",
        "not-writable" to "the node is read-only or a measurement provider; no app-side fix exists",
        "write_failed" to "the write command failed; check root and write permission on the path named in this line",
        "unreadable" to "the write may still have succeeded; read the node by hand before judging",
        "differs" to "a governor or driver clamped the value; request a value inside the advertised range",
        // الملكية والتزامن
        "blocked" to "another owner (or the safety gate) holds this knob, so nothing was written; the same line names the winner, and the app screen names the owner to release",
        "manual-lock" to "expected when you pin a knob; unpin it in the app screen to let automated owners write again",
        "preempted-by-OWNER" to "another owner holds the knob: the log names it; release that owner or wait for the handoff",
        "no-winner" to "the shared journal has no owner for this knob; re-apply from the app screen to re-register",
        "baseline-unreadable" to "no transaction may start until the node can be read; check the path and permissions",
        "live-read-unavailable" to "the read failed inside the transaction, so the end state is unconfirmed; read the node by hand",
        "handoff-awaiting-owner-process" to "working as designed: another process holds the knob and will apply this intent",
        "undecided" to "the planner returned no decision; check that the knob is routed at all (router initialisation)",
        "unknown" to "the cause was not recognised: repeat the action and read the raw line above it; if it repeats the engine is missing a reason code for it",
        // التراجع
        "not-verified" to "the request was written and not satisfied, so the baseline came back; retry with a value inside the advertised range",
        "restored" to "the baseline is back at the value this line names; the request is the part that failed",
        "apply-not-verified-baseline-restored" to "the value was refused and the baseline is safe; retry with a value inside the advertised range",
        "apply-not-verified-and-rollback-failed" to "state unknown: read the node by hand and re-apply the intended value before anything else",
        "restore-not-verified" to "compare expected with live before assuming the baseline is back",
        "rollback-not-verified" to "the fallback stopped to avoid stranding the baseline; read the knob by hand",
        // الحارس الحراري وAtlas
        "thermal-router-unavailable" to "the Atlas thermal router failed to initialise; verify access to the route memory directory",
        "guard-static-only:platform-thermal-status-unavailable" to "expected on this unit: the platform exposes no thermal status, so only your ceiling is enforced",
        "ceiling-not-planable" to "the ceiling has no plan; check the unit recorded in this line (kHz against Hz)",
        "knob-not-owned" to "apply the app from its own screen first; the guard only touches knobs this process owns",
        "no-route-transaction" to "the planner picked a route but produced no transaction; report the skipped reasons",
        "route-quarantined-after-unverified-rollback" to "a reboot frees the quarantine; the other routes still apply until then",
        "privilege-unavailable" to "root or the required privilege is missing for this route; the fallback route should run instead",
        "rollback-unproven" to "the route cannot prove it restores the baseline, so it is refused on purpose",
        "unit-ambiguous" to "fix the route definition, not the device: its value unit was never established",
        "provider-ambiguous" to "more than one provider matched, so the route is refused to avoid writing the wrong node",
        "goal-unmeasurable" to "the route cannot measure the goal it claims; do not enable it without a measurable signal",
        "route-not-reviewed" to "the route has not been reviewed; review it before enabling",
        "policy-unavailable" to "the CPU policy named in the key is not present now (hotplug or rename); target a policy that exists",
        "governor-not-advertised" to "pick a governor from this device's list; the requested name is not in this kernel",
    )

    /**
     * كيف يُقرأ الملف — بترتيب العمل لا بترتيب الأقسام.
     *
     * ولماذا هي أول ما يُكتب: من يستلم الملف بلا هذه الأسطر يبدأ من أوّله فيقرأ مئة سطر شرحي
     * قبل أن يصل إلى سطر واحد مفيد. والخطوات هنا هي **مسار الفشل** مضغوطًا: من الإعداد إلى
     * الفشل إلى رمزه إلى إصلاحه.
     */
    val howToRead: List<String> = listOf(
        "the file opens with LOG_HEADER lines grouped by kind: device, settings, field, unit, code, fix, howto, then the live events oldest first",
        "when something did not work, look for lines whose outcome is not applied or whose verdict reads differs, write_failed or unreadable; then take the reason code to the code block and its action to the fix block",
        "expected is the value that was asked for and live is the value read back; both are in the unit named by the unit block, and a knob left at default or held by a manual lock means nothing was enforced",
    )

    /**
     * يشرح رمزًا، أو `null` حين لا نعرفه — ولا يُخترع شرح.
     *
     * ويُدعم بادئة الرمز لأن بعض الرموز تُبنى: `preempted-by-SYSTEM` و`guard-idle:severe`
     * و`gpu-ceiling-open-below-request:754000000`.
     * والقاعدة: البادئة الأطول المطابقة تفوز، فلا يبتلع `guard-` معنى `guard-idle:`.
     */
    fun explain(code: String): String? = resolve(codes, code)

    /**
     * ما يُفعل برمز عطل، أو `null` حين لا إصلاح مُرمَّز له.
     *
     * والغياب مقصود ومفيد: هو علامة «رمز جديد لم يُدرَس» — والتقارير تُعلنه بدل أن تمرّ عليه.
     */
    fun remedyOf(code: String): String? = resolve(remedies, code)

    /**
     * الحلّ المشترك: مطابقة مباشرة، ثم قوالب الرموز المبنيّة، ثم أطول لاحقة معروفة.
     *
     * ووُحِّد لأن الشرح والإصلاح يجب أن يتطابقا على الرمز نفسه؛ لو تفرّقت المطابقتان لصار
     * `preempted-by-SYSTEM` مشروحًا في موضع ومجهولًا في آخر.
     */
    private fun resolve(table: List<Pair<String, String>>, code: String): String? {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return null
        table.firstOrNull { it.first == trimmed }?.let { return it.second }

        // الرموز المبنيّة من قالب: `preempted-by-SYSTEM` و`guard-idle:severe`.
        if (trimmed.startsWith("preempted-by-")) return meaningOf(table, "preempted-by-OWNER")
        if (trimmed.startsWith("guard-idle:")) return meaningOf(table, "guard-idle:PRESSURE")
        if (trimmed.startsWith("guard-static-only:")) return meaningOf(table, "guard-static-only:platform-thermal-status-unavailable")
        // ورمز تحرير السقف يُلحَق **برقمه المقيس** (`gpu-ceiling-open-below-request:754000000`)،
        // وهو الرقم الذي يفرّق «حرّرنا فانفتح كل شيء» من «حرّرنا والمنصّة تحتفظ بسقف» — فلا
        // يجوز أن يسقط شرحه لأن اللاحقة ليست رمزًا نعرفه.
        if (trimmed.startsWith("gpu-ceiling-open-below-request:")) {
            return meaningOf(table, "gpu-ceiling-open-below-request")
        }

        // وقرار Atlas يُكتب `STATUS-REASON` (`blocked-privilege-unavailable`)، فالتطابق على الذيل
        // بأطول رمز معروف — والأطول قبل الأقصر لأن `-restored` قد تطابق وحدها وتبتلع معنى أطول منها.
        return table
            .sortedByDescending { it.first.length }
            .firstOrNull { (known, _) -> trimmed.endsWith("-$known") }
            ?.second
    }

    private fun meaningOf(table: List<Pair<String, String>>, code: String): String? =
        table.firstOrNull { it.first == code }?.second

    /** أسطر الدليل كما تُكتب في التقرير وفي ملف السجل — نصّ واحد لمصدر واحد. */
    fun fieldGuideLines(): List<String> =
        fieldGuide.map { (name, meaning) -> "$name = $meaning" } +
            unitGuide.map { (knob, unit) -> "unit $knob = $unit" }

    /** أسطر القاموس كاملة، بترتيبها الثابت. */
    fun codeLines(): List<String> = codes.map { (code, meaning) -> "$code = $meaning" }

    /** القاموس بصيغة حقول ملف السجل: `code=... meaning=...`. */
    fun codeLogLines(): List<String> = codes.map { (code, meaning) -> "code=$code meaning=$meaning" }

    /** أسطر الإصلاح كما تُقرأ في التقرير: `code = fix`. */
    fun fixLines(): List<String> = remedies.map { (code, fix) -> "$code = $fix" }

    /** أسطر الإصلاح بصيغة حقول ملف السجل: `code=... fix=...`. */
    fun fixLogLines(): List<String> = remedies.map { (code, fix) -> "code=$code fix=$fix" }

    /** أسطر «كيف يُقرأ» بصيغة حقول ملف السجل: `kind=howto step=... text=...`. */
    fun howToLogLines(): List<String> = howToRead.mapIndexed { index, text ->
        "kind=howto step=${index + 1} text=$text"
    }

    /** الدليل بصيغة حقول ملف السجل. */
    fun guideLogLines(): List<String> =
        fieldGuide.map { (name, meaning) -> "kind=field name=$name meaning=$meaning" } +
            unitGuide.map { (knob, unit) -> "kind=unit knob=$knob unit=$unit" }
}
