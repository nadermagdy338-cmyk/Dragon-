/*
 * Copyright (C) 2026-2027 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * `OCR-01` — **النسخ المجدول**: خطة زمنية تُحسب قواعدها هنا وتنفّذها المنصّة.
 *
 * **ولماذا الحساب خالص:** السؤال «متى التشغيل القادم؟» هو كل ما تملكه هذه الميزة من منطق،
 * وهو سؤال حسابي بحت: دقيقة في اليوم، وأيام مسموحة، والآن. وكتابته داخل `JobScheduler` كانت
 * ستصيّره غير قابل للقياس — أي أن خطأ ساعة واحدة في «٣:٠٠ صباحًا» لا يُكتشف إلا بالانتظار.
 * فالحساب هنا، والجدولة هناك (`MaxBackupScheduler`).
 *
 * **وما لا تعِد به هذه الميزة، معلَنًا:** `JobScheduler` **لا** يضمن دقيقة محدّدة. المنصّة
 * تُشغّل المهمة في نافذة تسمح بها حالة الجهاز، و«٣:٠٠ صباحًا» تعني «**أول فرصة بعد** ٣:٠٠»،
 * ويُقال ذلك للمستخدم في الواجهة بدل أن يُوعَد بتوقيت لا يملكه أحد. وهذا هو نفس انضباط الشاشة
 * في «الجهاز تجاهل ما كتبناه»: النتيجة تُعلَن كما هي.
 *
 * **وشرط البطارية والواي فاي شرطان فحسب لا حساب:** `JobScheduler` ينفّذهما (`setRequiresCharging`
 * و`setRequiredNetworkType`)، فلا يُعاد تنفيذهما في الشيفرة.
 */
package nd.max.ui.util

object MaxBackupSchedule {

    /** اسم ملف التخزين، في مجلد التطبيق الخاص (كأصناف المجموعات: سياسة يملكها المستخدم). */
    const val FILE_NAME = "schedule.txt"

    const val MINUTES_PER_DAY = 1440

    /**
     * الدورة التي تُجدول بها المنصّة: يوم. والموعد المطلوب ليس دورة بل **تأخير** أول تشغيل
     * (`minutesUntilNextRun`)، وبعدها تتكرّر الدورة من عنده.
     */
    const val PERIOD_MS = 24L * 60L * 60L * 1000L

    /** لماذا لا يعمل الجدول — أو `null` إن كان يعمل. */
    enum class Blocker { DISABLED, NO_TARGET }

    /** نتيجة آخر تشغيل، بالأسماء لا بالرسائل: النصّ يأتي من الموارد في الواجهة. */
    enum class Result { OK, FAILED, SKIPPED }

    /** الاثنين=١ … الأحد=٧ (ISO)، وفارغ = كل يوم. */
    val ALL_DAYS: Set<Int> = (1..7).toSet()

    data class Plan(
        val enabled: Boolean = false,
        val minuteOfDay: Int = 180,
        val chargingOnly: Boolean = true,
        val wifiOnly: Boolean = false,
        val days: Set<Int> = emptySet(),
        /** أسماء مجموعات المجلدات (`MaxBackupFolders`). */
        val sets: List<String> = emptyList(),
        /** أسماء أصناف النظام (`MaxBackupSystem.Kind`). */
        val kinds: List<String> = emptyList(),
        val lastRunAtMs: Long? = null,
        val lastResult: Result? = null,
    ) {
        /** الأيام الفعلية: الفارغ يعني كل يوم، ويُقال صراحةً بدل تركه ضمنيًّا. */
        val effectiveDays: Set<Int> get() = if (days.isEmpty()) ALL_DAYS else days

        val hasTarget: Boolean get() = sets.isNotEmpty() || kinds.isNotEmpty()
    }

    /** الساعة تُقيَّد في نطاقها، والأيام تُنقّى: يوم ٩ لا وجود له فلا يُحفظ ثم يُنفَّذ خطأً. */
    fun normalized(plan: Plan): Plan = plan.copy(
        minuteOfDay = plan.minuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1),
        days = plan.days.filter { it in 1..7 }.toSet(),
        sets = plan.sets.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
        kinds = plan.kinds.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
    )

    fun blocker(plan: Plan): Blocker? = when {
        !plan.enabled -> Blocker.DISABLED
        !plan.hasTarget -> Blocker.NO_TARGET
        else -> null
    }

    fun runsOn(plan: Plan, isoDay: Int): Boolean = isoDay in plan.effectiveDays

    /**
     * الدقائق حتى التشغيل القادم — أو `null` إن كان الجدول لا يعمل أصلًا.
     *
     * والطريقة: تُحسب لـ**كل** يوم مسموح مسافته، ويُؤخذ الأصغر. ولماذا لا «اليوم ثم اليوم
     * التالي»: الخاصيتان تمرّان عبر منتصف الليل، والحساب المباشر يخطئ في أسبوع كامل عند
     * «يوم واحد في الأسبوع» — والخطأ أسبوع، لا ساعة.
     */
    fun minutesUntilNextRun(plan: Plan, nowMinuteOfDay: Int, isoDay: Int): Long? {
        val clean = normalized(plan)
        if (blocker(clean) != null) return null
        val now = nowMinuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
        val today = if (isoDay in 1..7) isoDay else 1

        // `Long.MAX_VALUE` لا `Long?`: أفضل-قيمة تُحسب داخل حلقة، والمتغيّر الملتقط
        // في closure لا يُحكَم نوعه بذكاء في Kotlin — فالتخزين بقيمة محايدة يمنع حارسًا لا يترجم.
        var best = Long.MAX_VALUE
        clean.effectiveDays.forEach { day ->
            val shift = ((day - today) % 7 + 7) % 7
            var minutes = shift * MINUTES_PER_DAY.toLong() + (clean.minuteOfDay - now)
            // الوقت مضى اليوم ⇒ الموعد القادم للـ**يوم نفسه** بعد أسبوع، والأصغر عبر بقية
            // الأيام هو الفائز — فالخطأ لو قارنّا يومًا واحدًا فقط كان أسبوعًا لا ساعة.
            if (minutes < 0) minutes += 7L * MINUTES_PER_DAY
            if (minutes < best) best = minutes
        }
        return best.takeIf { it != Long.MAX_VALUE }
    }

    /** هل حان الوقت الآن؟ يُستخدم في عامل التشغيل قبل بدء النسخ. */
    fun isDue(plan: Plan, nowMinuteOfDay: Int, isoDay: Int): Boolean {
        val clean = normalized(plan)
        if (blocker(clean) != null) return false
        if (!runsOn(clean, isoDay)) return false
        return nowMinuteOfDay >= clean.minuteOfDay
    }

    /** هل تشغيل هذا اليوم مُجاب بشرط البطارية؟ يُقاس ما نعرف، ولا يُفترض ما لا نعرف. */
    fun allowsRun(plan: Plan, charging: Boolean, unmetered: Boolean): Boolean {
        val clean = normalized(plan)
        if (clean.chargingOnly && !charging) return false
        if (clean.wifiOnly && !unmetered) return false
        return true
    }

    /**
     * «٠٣:٠٠» — تنسيق حسابي بحت بلا `String.format`.
     * والسبب ليس الأسلوب: `format` يتبع لغة الجهاز فيتحوّل رقم الساعة إلى أرقام هندية في
     * العربية بينما بقية أرقام الشاشة لاتينية — فيقرأ المستخدم ساعتين بخطّين.
     */
    fun clock(minuteOfDay: Int): String {
        val minute = minuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
        return (minute / 60).toString().padStart(2, '0') + ":" + (minute % 60).toString().padStart(2, '0')
    }

    /**
     * قراءة «HH:MM» — أو `null` إن لم تكن وقتًا.
     *
     * **ولماذا لا تُترك للمنصّة:** حقل الوقت نصّ حرّ، وقبول ما لا يُفهم يعني حفظ وقت لم
     * يقصده المستخدم ثم تشغيل نسخة في ساعة لا يتوقّعها. والقراءة هنا **متعمّدة الشدّة**: فاصل
     * واحد، ورقمان لا غير (أو ثلاثة/أربعة أرقام مجتمعة مثل `0300`)، والساعة `0..23` والدقيقة
     * `0..59`. و`24:00` مرفوض لأن اليوم ينتهي عند `23:59`، و`7:5` مرفوض لأن معناه «٧:٥٠» أو
     * «٧:٠٥» — والتخمين في الوقت أسوأ من طلب إعادة الكتابة.
     *
     * **ولا تُحوَّل الأرقام العربية-هندية يدويًّا، لأنها تُقرأ أصلًا:** جرّبتُ التخويل اليدوي
     * وأشلته بعد أن أثبت اختبار التبديل (`parseClock reads Arabic-Indic digits`) أنه لا يحمل
     * شيئًا — `toInt` في مكتبة Kotlin يقرأ `٠٣` و`۰۳` مثل `03`. وإبقاء سطور لا تحمل شيئًا مع
     * تعليق يدّعي أنها ضرورية أسوأ من حذفها.
     */
    fun parseClock(raw: String): Int? {
        val parts = raw.trim().split(':')
        val hours: Int
        val minutes: Int
        when (parts.size) {
            1 -> {
                val digits = parts[0]
                if (digits.length !in 3..4 || digits.any { !it.isDigit() }) return null
                hours = digits.dropLast(2).toInt()
                minutes = digits.takeLast(2).toInt()
            }

            2 -> {
                val (h, m) = parts
                // الحدّ على الطول قبل `toInt` لا بعده: سلسلة أطول من `toInt` ترمي استثناءً
                // لا تُعيد `null`، والقراءة هنا يجب ألا تُسقط شاشة عند كتابة طويلة.
                if (h.length !in 1..2 || m.length != 2) return null
                if (h.any { !it.isDigit() } || m.any { !it.isDigit() }) return null
                // الدقائق رقم ان لا ورقم — `3:5` غامضة («٣:٥٠» أم «٣:٠٥»)، فتُرفض.
                hours = h.toInt()
                minutes = m.toInt()
            }

            else -> return null
        }
        if (hours !in 0..23 || minutes !in 0..59) return null
        return hours * 60 + minutes
    }

    /** اسم مجموعة أو صنف حُذف من مكان آخر: لا يُنفَّذ، ولا يُخترع له بديل. */
    fun missing(plan: Plan, existingSets: Collection<String>, existingKinds: Collection<String>): List<String> =
        (plan.sets.filterNot { it in existingSets } + plan.kinds.filterNot { it in existingKinds })

    // ────────────────────────────────────────────────────────────────────────
    // التخزين: `key=value` سطرًا سطرًا، والقراءة متسامحة كقراءة المجموعات.
    // ────────────────────────────────────────────────────────────────────────

    private const val LIST_SEPARATOR = '\u001f'

    private fun encodeList(values: List<String>): String =
        values.filter { it.isNotEmpty() && !it.contains(LIST_SEPARATOR) && !it.contains('\n') }
            .joinToString(LIST_SEPARATOR.toString())

    private fun decodeList(raw: String?): List<String> =
        raw.orEmpty().split(LIST_SEPARATOR, ',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    fun encode(plan: Plan): String {
        val clean = normalized(plan)
        return buildString {
            appendLine("enabled=${if (clean.enabled) 1 else 0}")
            appendLine("minute=${clean.minuteOfDay}")
            appendLine("charging=${if (clean.chargingOnly) 1 else 0}")
            appendLine("wifi=${if (clean.wifiOnly) 1 else 0}")
            appendLine("days=${clean.effectiveDays.sorted().joinToString(",")}")
            appendLine("sets=${encodeList(clean.sets)}")
            appendLine("kinds=${encodeList(clean.kinds)}")
            appendLine("lastRun=${clean.lastRunAtMs ?: 0L}")
            appendLine("lastResult=${clean.lastResult?.name ?: ""}")
        }
    }

    fun decode(text: String): Plan {
        val values = linkedMapOf<String, String>()
        text.lineSequence().forEach { line ->
            if (line.isBlank() || '=' !in line) return@forEach
            val key = line.substringBefore('=').trim().lowercase()
            values[key] = line.substringAfter('=').trim()
        }
        // المفاتيح كلها صغيرة هنا (`lastrun` لا `lastRun`)، والقراءة بها كذلك. والخطأ في حالة
        // الأحرف لا يرمي شيئًا: يُعيد `null` بصمت — أي أن «آخر محاولة» تختفي بعد كل إعادة
        // قراءة. وهذا ما قاسه الاختبار (`the plan survives a round trip`).
        val result = values["lastresult"].orEmpty()
        return normalized(
            Plan(
                enabled = values["enabled"] == "1",
                minuteOfDay = values["minute"]?.toIntOrNull() ?: 180,
                chargingOnly = values["charging"] != "0",
                wifiOnly = values["wifi"] == "1",
                days = decodeList(values["days"]).mapNotNull { it.toIntOrNull() }.toSet(),
                sets = decodeList(values["sets"]),
                kinds = decodeList(values["kinds"]),
                lastRunAtMs = values["lastrun"]?.toLongOrNull()?.takeIf { it > 0L },
                lastResult = Result.entries.firstOrNull { it.name == result },
            )
        )
    }

    /** يُسجّل نتيجة تشغيل بلا لمس بقية الخطة — تُستدعى من العامل بعد نهايته. */
    fun recorded(plan: Plan, atMs: Long, result: Result): Plan =
        plan.copy(lastRunAtMs = atMs, lastResult = result)
}
