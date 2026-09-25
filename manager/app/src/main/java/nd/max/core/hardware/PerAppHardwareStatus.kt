/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.hardware

import nd.max.MaxManagerPaths

/**
 * سجل نتيجة **كل مقبض** في جلسة تطبيق واحد — قناة الحقيقة بين المحرّك والواجهة.
 *
 * لماذا وُجد هذا الملف
 * --------------------
 * كان الفشل في مسار التطبيقات يُعرف بطريقتين فقط: `PER_APP_CPU_STATUS` (سياسات CPU وحدها)
 * وسطور `EVENT=PERAPP_COMMIT` في السجل الموحّد. وكل ما قبل التسجيل كان صامتًا: كتلة GPU مثلًا
 * تخرج من خمس بوابات بـ`return@runCatching` **بلا حالة وبلا سطر** (لا مزوّد GPU، أو عقدة غير
 * قابلة للكتابة، أو لا سقف حيّ، أو لا OPP مُعلن). فيقرأ المستخدم النتيجة «لم يحدث شيء» — وهي
 * بالضبط الجملة التي لا يمكن إصلاحها: لا تعرف أيّ بوابة أُغلقت ولا لماذا.
 *
 * فالعقد هنا: **كل مقبض يخرج بنتيجة مكتوبة**، إمّا `APPLIED` وإمّا فشل يحمل **رمز سببه**.
 * والواجهة تقرأ هذا السجل فتُظهر السبب بدل الصمت، والسجل الموحّد يحمل السطر نفسه بـ
 * `EVENT=PERAPP_KNOB` — فيصير التشخيص `grep` واحد.
 *
 * الكاتب واحد
 * -----------
 * مراقب الخلفية ([nd.max.AppMonitor]) هو الكاتب الوحيد؛ الواجهة تقرأ فقط. ومبدأ المشروع أن
 * مقبضًا واحدًا، وكاتبًا واحدًا، وسجلًّا واحدًا — وتعدّد الكُتّاب هو ما جعل «من كتب آخر مرة؟»
 * سؤالًا بلا جواب في هذا المستودع سابقًا.
 *
 * والصيغة سطر واحد لكل مقبض، بحقول `k=v` مفصولة بفراغ (نفس شكل ملفات الحالة الأخرى هنا)،
 * والقيم مُنقّاة من الأسطر والفراغات كي يبقى كل سطر حقلًا واحدًا قابلًا للبحث.
 */
object PerAppHardwareStatus {

    /**
     * نتيجة مقبض واحد. الرموز ثابتة ومقصودة: الواجهة تترجمها، والسجل يبقى قابلًا للمقارنة
     * بين إصدارات دون تغيّر نصوصه.
     */
    enum class Outcome(val token: String) {
        /** كُتب القيمة وتحقّقت (بمعنى الطلب لا بحرفه — انظر [HardwareVerification]). */
        APPLIED("applied"),

        /** بوّابة الأسبقية رفضت: قفل يدوي للمستخدم، أو مالك أعلى يملك الإيجار. */
        BLOCKED("blocked"),

        /** الجهاز لا يُعلن هذا المقبض أصلًا (لا مزوّد GPU، لا سياسة، لا عقدة). */
        UNSUPPORTED("unsupported"),

        /** المقبض موجود لكن الكتابة إليه غير متاحة (سقف قراءة، أو provider للقياس فقط). */
        NOT_WRITABLE("not-writable"),

        /** كُتب ثم لم يتحقّق، فاستُرجع خط الأساس. */
        NOT_VERIFIED("not-verified"),

        /** طُلب ثم تُرك عمدًا: المستخدم اختار `default` فلا شيء يُنفَّذ. */
        SKIPPED("skipped");

        companion object {
            fun fromToken(value: String): Outcome? = entries.firstOrNull { it.token == value }
        }
    }

    data class Record(
        val knob: String,
        val outcome: String,
        val reason: String,
        val expected: String,
        val live: String,
    ) {
        val isFailure: Boolean get() = outcome != Outcome.APPLIED.token && outcome != Outcome.SKIPPED.token
    }

    data class Snapshot(val pkg: String, val atMs: Long, val records: List<Record>)

    private const val PKG_PREFIX = "pkg="
    private const val AT_PREFIX = "at="
    private const val KNOB_PREFIX = "knob="
    private const val OUTCOME_PREFIX = "outcome="
    private const val REASON_PREFIX = "reason="
    private const val EXPECTED_PREFIX = "expected="
    private const val LIVE_PREFIX = "live="

    // ── حالة الكاتب (مراقب الخلفية) ────────────────────────────────────────────
    private val session = linkedMapOf<String, Record>()
    private var sessionPkg = ""
    private var dirty = false

    /** جلسة تطبيق جديدة: كل نتيجة سابقة تخصّ تطبيقًا آخر كانت مضلِّلة، فتُطرح. */
    @Synchronized
    fun beginSession(pkg: String) {
        if (sessionPkg == pkg) return
        sessionPkg = pkg
        session.clear()
        dirty = true
        flush()
    }

    /**
     * الحزمة التي تخصّها الجلسة الجارية — **نفس** الحزمة التي يحملها الملف المقروء في الواجهة.
     *
     * ووُجدت لأن سطر السجل كان يكتب `lastAppliedPkg`، وهو يُحدَّث **بعد** التطبيق في مسار تبديل
     * التطبيق — فتخرج أسطر `PERAPP_KNOB` باسم التطبيق السابق تحت معرّف تبديل التطبيق الجديد. والقياس
     * (حزمة المالك ٢٠٢٦-٠٩-٢٢): `APP_SWITCH pkg=translate sw=…05694` ثم أسطر بنفس المعرّف مكتوب عليها
     * `pkg=com.franco.kernel`، وهو نفس ما وُثّق في `REPAIR_NOTES` بـ٧٤٩ حالة عدم تطابق. والأثر تشخيصي
     * لا وظيفي — لكنه يجعل أقوى دليل عندنا يكذب في اسم صاحبه، فيُصلَّح من مصدر واحد لا بمقارنة نصّين.
     */
    @Synchronized
    fun sessionPackage(): String = sessionPkg

    /** نتيجة مقبض — تُستبدل بنتيجة أحدث لنفس المقبض، فلا ينمو السجل بلا سقف. */
    @Synchronized
    fun note(
        knob: String,
        outcome: Outcome,
        reason: String,
        expected: String = "",
        live: String = "",
    ) {
        if (knob.isBlank() || sessionPkg.isBlank()) return
        session[knob] = Record(
            knob = singleLine(knob),
            outcome = outcome.token,
            reason = singleLine(reason),
            expected = singleLine(expected),
            live = singleLine(live),
        )
        dirty = true
    }

    /**
     * كتابة الحالة على القرص بمعاملة واحدة (tmp + rename) — إمّا السجل كاملًا وإمّا لا شيء،
     * فلا تقرأ الواجهة نصف سجل يبدو سليمًا.
     */
    @Synchronized
    fun flush() {
        if (!dirty || sessionPkg.isBlank()) return
        val text = encode(sessionPkg, System.currentTimeMillis(), session.values)
        if (RootFileAccess.atomicWriteText(MaxManagerPaths.PER_APP_HW_STATUS, text)) dirty = false
    }

    /**
     * بناء نص السجل — دالة خالصة حتى يُقاس الترميز والفكّ باختبار وحدة بلا جذر وبلا قرص.
     * وكتابةُ ملف تحت `/data/adb` لا تُقاس في بيئة الاختبار أصلًا، فلو بقي الترميز داخل
     * `flush` لما أمكن إثبات أن ما يُكتب يُقرأ — وهي نقطة الفشل الحقيقية في قنوات الحالة.
     */
    internal fun encode(pkg: String, atMs: Long, records: Collection<Record>): String = buildString {
        appendLine("$PKG_PREFIX${singleLine(pkg)}")
        appendLine("$AT_PREFIX$atMs")
        records.forEach { record ->
            // التنقية هنا لا في الكاتب: `encode` هو المكان الوحيد الذي يُنتج صيغة الملف، فلو
            // مرّت قيمة فيها فراغ لانقسم السطر إلى حقلين وقرأت الواجهة سجلًا مشوَّهًا يبدو سليمًا.
            appendLine(
                "$KNOB_PREFIX${singleLine(record.knob)} ${OUTCOME_PREFIX}${singleLine(record.outcome)} " +
                    "$REASON_PREFIX${singleLine(record.reason).ifBlank { "unspecified" }} " +
                    "$EXPECTED_PREFIX${singleLine(record.expected).ifBlank { "none" }} " +
                    "$LIVE_PREFIX${singleLine(record.live).ifBlank { "unreadable" }}"
            )
        }
    }

    /** انتهاء جلسة التطبيق: لا حالة قديمة تُقرأ على أنها حالة الآن. */
    @Synchronized
    fun clear() {
        session.clear()
        sessionPkg = ""
        dirty = false
        RootFileAccess.atomicWriteText(MaxManagerPaths.PER_APP_HW_STATUS, "pkg=\nat=0\n")
    }

    /**
     * قراءة السجل — للواجهة والتشخيص.
     *
     * ويُعاد `null` لا سجلًّا فارغًا حين لا ملف: «لا نتيجة مسجَّلة» و«تطبيق بلا مقابض» سببان
     * مختلفان، وخلطهما يجعل الواجهة تقول «كل شيء سليم» لجهاز لم يقس شيئًا بعد.
     */
    fun read(): Snapshot? {
        val text = RootFileAccess.read(MaxManagerPaths.PER_APP_HW_STATUS)?.takeIf { it.isNotBlank() }
            ?: return null
        return parse(text)
    }

    /** فكّ نص السجل. سطر لا يحمل `pkg` صالحًا لا يُنتج لقطة — لأن «لا سجل» ليست «سجل سليم». */
    internal fun parse(text: String): Snapshot? {
        var pkg = ""
        var atMs = 0L
        val records = mutableListOf<Record>()
        text.lineSequence().forEach { line ->
            val fields = line.trim().split(' ').filter(String::isNotBlank).associate { field ->
                field.substringBefore('=') to field.substringAfter('=', "")
            }
            when {
                fields.containsKey("pkg") -> pkg = fields["pkg"].orEmpty()
                fields.containsKey("at") -> atMs = fields["at"]?.toLongOrNull() ?: 0L
                fields.containsKey("knob") -> records += Record(
                    knob = fields["knob"].orEmpty(),
                    outcome = fields["outcome"].orEmpty(),
                    reason = fields["reason"].orEmpty(),
                    expected = fields["expected"].orEmpty(),
                    live = fields["live"].orEmpty(),
                )
            }
        }
        if (pkg.isBlank()) return null
        return Snapshot(pkg = pkg, atMs = atMs, records = records)
    }

    /** قيمة حقل واحد: بلا سطر جديد وبلا فراغين، فيبقى السطر حقلًا واحدًا قابلًا للبحث. */
    private fun singleLine(value: String): String =
        value.replace(Regex("[\\s]+"), "_").take(160)
}
