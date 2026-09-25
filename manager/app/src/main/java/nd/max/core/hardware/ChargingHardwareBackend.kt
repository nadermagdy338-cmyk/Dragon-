/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.hardware

/**
 * الكاتب الوحيد لعقد الشحن من جهة التطبيق.
 *
 * **لماذا وُجد:** كان `ChargingViewModel` يكتب ثلاثة عقد شحن بـ`Shell.cmd("echo … > …")`
 * من طبقة العرض مباشرةً — وهو ما يمنعه `ADR-11` (لا كتابة عتاد من حزمة الواجهة)، وأخطر من
 * المخالفة الشكلية أن الكتابة كانت **عمياء**: لا قراءة بعدها، والنتيجة تُهمَل، والحالة
 * والخاصية المحفوظة تُضبطان على **المطلوب** لا على **الحيّ**. فالمستخدم يرى «الحدّ
 * مفعّل» والجهاز يشحن إلى ١٠٠٪.
 *
 * **ولماذا لا يمرّ عبر الـarbiter:** الـarbiter ملكية لها عمر واسترجاع خط أساس، وهي
 * مناسبة لمقبض يُعاد عند تبديل التطبيق أو إغلاق الحلقة. أمّا حدّ الشحن فإعداد مستخدم
 * يجب أن **يبقى** — فلا خط أساس يُسترجع. فالمكسب هنا هو **التحقق** لا الملكية، وهو
 * ما يطلبه `ADR-11` نصًّا: كتابة واحدة من مكان واحد مع حكم على القيمة.
 *
 * **والحكم ثلاثيّ لا ثنائيّ** (وهذا مقصود): عقدة غائبة **تُرفض**؛ وعقدة تُقرأ للخلف
 * يُتحقَّق منها بمقارنة القيمة الحيّة؛ وعقدة **لا تُقرأ للخلف** (يكتبها السائق ولا
 * يعرضها) لا يُدّعى لها تحقّق **ولا فشل** — فالثانية ادّعاء معرفة، والأولى اتهام ظالم.
 * والفرق بين «لم ينجح» و«لم يُتحقَّق منه» هو نفسه الفرق الذي يمنعه `01-PLAN` §2.4
 * في أطلس، مطبَّقًا هنا على الشحن.
 */
object ChargingHardwareBackend {

    enum class Verdict {
        /**
         * العتاد يقرأ المطلوب بعد المحاولة (أو كان يقرؤه أصلًا فلا كتابة بلا داعٍ):
         * هذه هي المعرفة الوحيدة التي تُبنى عليها الحالة.
         */
        VERIFIED,

        /** كُتب ولم يمكن التحقق منه (عقدة لا تُقرأ للخلف): لا ادّعاء ولا اتهام. */
        APPLIED_UNVERIFIED,

        /** لم يُكتب: لا عقدة، أو العقدة غائبة، أو رفضها السائق. */
        REFUSED,
    }

    data class Outcome(
        val verdict: Verdict,
        val requested: String,
        val actual: String?,
        val error: String? = null,
        /** العقدة التي حُكم عليها — يحملها الحكم لئلا يحتفظ المتصل بنسخة قديمة منها. */
        val path: String? = null,
    ) {
        val verified: Boolean get() = verdict == Verdict.VERIFIED
        val written: Boolean get() = verdict != Verdict.REFUSED
    }

    /**
     * نافذة الاستقرار: عقد الشحن لا تُطبَّق في الخيط نفسه في أكثر السائقين، بل يقرؤها
     * خيط الشاحن في دورة لاحقة. فقراءة فورية بعد الكتابة كانت ستُصنّف كتابةً سليمة
     * «مرفوضة». ولأن هذه مفاتيح إعداد لمرّة واحدة (لا حلقة تحكم ولا كتابة دورية)
     * فزمن الانتظار هنا رخيص، بخلاف مقابض التردّد التي تُكتب في حلقة ضيّقة.
     */
    private const val SETTLE_ATTEMPTS = 6
    private const val SETTLE_DELAY_MS = 250L

    /**
     * يكتب قيمة إلى عقدة شحن ويحكم على **القيمة** لا على الأمر.
     *
     * @param path مسار العقدة؛ `null`/فارغ يعني «لا عقدة مرشّحة على هذا الجهاز» ⇒ رفض.
     */
    fun write(
        path: String?,
        value: String,
        maxAttempts: Int = SETTLE_ATTEMPTS,
        retryDelayMs: Long = SETTLE_DELAY_MS,
    ): Outcome {
        if (path.isNullOrBlank()) return Outcome(Verdict.REFUSED, value, null, "no-node")
        if (!RootFileAccess.exists(path)) return Outcome(Verdict.REFUSED, value, null, "node-absent", path)

        // قراءة مسبقة واحدة تفصل ثلاث حالات: عقدة **لا تُقرأ للخلف** (فلا معنى لإعادة
        // المحاولة عليها — وهذا ما كان يجعل التحقّق القديم يكتب ثلاثًا بلا فائدة)،
        // وعقدة **تقرأ المطلوب أصلًا** (فلا كتابة بلا داعٍ — والهدف قائم ومُثبت)،
        // وعقدة تحتاج كتابة ثم قراءة.
        val before = RootFileAccess.read(path)?.trim()?.takeIf(String::isNotEmpty)
        if (before == null) {
            val wrote = runCatching { RootFileAccess.write(path, value) }.getOrDefault(false)
            return if (wrote) Outcome(Verdict.APPLIED_UNVERIFIED, value, null, "node-not-readable-back", path)
            else Outcome(Verdict.REFUSED, value, null, "write-rejected", path)
        }
        if (equivalent(value, before)) return Outcome(Verdict.VERIFIED, value, before, path = path)

        val result = VerifiedControl.apply(
            requested = value,
            write = { RootFileAccess.write(path, it) },
            read = { RootFileAccess.read(path)?.trim()?.takeIf(String::isNotEmpty) },
            equals = ::equivalent,
            maxAttempts = maxAttempts.coerceAtLeast(1),
            retryDelayMs = retryDelayMs,
        )
        return if (result.verified) {
            Outcome(Verdict.VERIFIED, value, result.actual, path = path)
        } else {
            Outcome(Verdict.REFUSED, value, result.actual, result.error ?: "live-value-differs", path)
        }
    }

    /** قراءة حيّة للعقدة كما يعرضها السائق — أو `null` إن لم تُقرأ. */
    fun read(path: String?): String? =
        path?.takeIf(String::isNotBlank)?.let { RootFileAccess.read(it)?.trim()?.takeIf(String::isNotEmpty) }

    /**
     * تكافؤ المطلوب والمقروء: نصًّا، أو عددًا.
     *
     * والقبول العددي ليس تساهلًا: `charge_control_limit` يعرض عند بعض السائقين القيمة
     * نفسها بصيغة عددية مختلفة (`080` أو `80` مع محارف بيضاء) — ورفض ذلك كان سيُصنّف
     * كتابةً ناجحة فاشلةً. أمّا اختلاف **القيمة** فلا يُقبل في أي صيغة.
     */
    fun equivalent(expected: String, actual: String?): Boolean {
        if (actual == null) return false
        if (actual == expected) return true
        val a = actual.trim().toLongOrNull() ?: return false
        val e = expected.trim().toLongOrNull() ?: return false
        return a == e
    }
}
