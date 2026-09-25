/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.util

import nd.max.core.hardware.RootFileAccess

/**
 * قراءات الرسّام على شرائح MediaTek: أيّ عقدة نثق بها، وماذا تقول.
 *
 * **قاعدة النطاق:** هذا الملف **يقرأ فقط**. لا يكتب عقدة ولا يقفل تردّدًا ولا يُبدّل سياسة؛
 * فالكتابة على عتاد الجهاز مسارها واحد في MaxManager (الـarbiter/`RootFileAccess.write`
 * من طبقة التحكم) — لا دالّة صغيرة في أداة جانبية تتجاوزها (ADR-11).
 *
 * **كل قراءة تمرّ بـ[RootFileAccess]** الذي يعرف الطرق الأربعة مرتّبة (قارئ أصلي ← IPC الجذر ←
 * ملف ← صدفة). ولهذا لا يوجد في هذا الملف `Shell` مباشر: الطريق الاحتياطي موجود هناك مرة واحدة،
 * وتكراره هنا كان يعني ترتيبًا ثانيًا يتباعد عن الأول على أوّل جهاز.
 *
 * **ولماذا الثوابت بهذا الشكل:** أجهزة البائع لا تُجمع على مسار واحد. جهاز يعلن التردّد في
 * `/sys/kernel/ged/hal`، وآخر في `devfreq`، وثالث لا يكشف إلا جدول OPP في `/proc`. فكل دالّة
 * هنا تُجرّب مرشّحيها **بالترتيب المعلن** وتُرجع `null`/`"N/A"` بلا تخمين حين لا يوجد شيء —
 * لأن صفرًا مصنوعًا يُقرأ في الواجهة كـ«الرسّام خامل» وهو أسوأ من فراغ.
 */
object MtkUtils {

    // ── المسارات ────────────────────────────────────────────────────────────────

    private const val DEVFREQ_ROOT = "/sys/class/devfreq"
    private const val GED_PARAMS = "/sys/module/ged/parameters"
    private const val GED_HAL = "/sys/kernel/ged/hal"

    /**
     * عقدتان معروفتان بالاسم تُجرَّبان **قبل** الاستقصاء.
     *
     * والسبب عطب حقيقي: على جهاز له أكثر من جهاز `devfreq` كان الاستقصاء العام يختار أوّلها
     * فيُقرأ تردّد جهاز آخر — فالعقدة المسمّاة أولى من مطابقة الاسم.
     */
    private val NAMED_GPU_NODES = listOf(
        "$DEVFREQ_ROOT/13000000.mali",
        "/sys/class/misc/mali0/device/devfreq/13000000.mali",
    )

    /** ما يدلّ على أنّ مجلّد `devfreq` يخصّ الرسّام حين لا اسم معروف. */
    private val GPU_NODE_HINTS = listOf("mali", "gpu", "dfrgx")

    /** جدول الدرجات (OPP): الأنسب أولًا — v2 ثم صيغة `working` ثم الصيغة القديمة. */
    private val OPP_TABLES = listOf(
        "/proc/gpufreqv2/stack_signed_opp_table",
        "/proc/gpufreqv2/gpu_working_opp_table",
        "/proc/gpufreq/gpufreq_opp_dump",
    )

    /** عقد التحميل: نسبة الخمول أوّلها (أدقّ)، ثم عقدة النسبة الجاهزة. */
    private const val GPU_IDLE_NODE = "$GED_PARAMS/gpu_idle"
    private val GPU_LOAD_NODES = listOf("$GED_HAL/gpu_utilization", "/proc/mali/utilization")

    /** العقدة الحيّة للتردّد على أنظمة GED. */
    private const val GED_LIVE_FREQ = "$GED_HAL/current_freqency"

    // ── رمز القيم ───────────────────────────────────────────────────────────────

    private val INTEGER = Regex("-?\\d+")
    private val NUMBER_WITH_UNIT = Regex("(?i)(\\d+(?:\\.\\d+)?)\\s*(ghz|mhz|khz)?")

    // ── العقدة ──────────────────────────────────────────────────────────────────

    /**
     * مسار عقدة `devfreq` التي تخصّ الرسّام، أو `null` إن لم تُعرَّف.
     *
     * يُحلّ مرة واحدة عند المستدعي ويُحفظ (كالرئيسية تفعل)؛ فالاستقصاء هنا ليس مجّانيًّا على جهاز
     * بلا خدمة جذر مربوطة: كل نداء قد ينتهي بصدفة.
     */
    fun getGpuDevfreqNode(): String? {
        val named = RootFileAccess.firstExisting(NAMED_GPU_NODES) { it }
        if (named != null) return named

        val entry = RootFileAccess.listDirectories(DEVFREQ_ROOT).firstOrNull { name ->
            val lower = name.lowercase()
            GPU_NODE_HINTS.any { hint -> lower.contains(hint) }
        } ?: return null
        return "$DEVFREQ_ROOT/$entry"
    }

    /**
     * نسبة انشغال الرسّام كما تُعرض: `"47%"` أو `"N/A"`.
     *
     * الخمول أوّلًا لأنّه الأصدق: عقدة `gpu_idle` تعطي الباقي من ١٠٠، وبعض الأنوية تكتب فيها
     * `100` عند التعطيل — فتُترجم إلى `0%` لا إلى رقم عشوائي. وإن غابت، تُقرأ عقدة النسبة الجاهزة
     * ويُؤخذ أوّل رقم منها (النصّ يتغيّر بين نواة وأخرى، والرقم لا).
     */
    fun getGpuLoad(): String {
        if (RootFileAccess.exists(GPU_IDLE_NODE)) {
            val idle = readInt(GPU_IDLE_NODE) ?: 100
            return "${(100 - idle).coerceIn(0, 100)}%"
        }
        val node = RootFileAccess.firstExisting(GPU_LOAD_NODES) { it } ?: return "N/A"
        val percent = readInt(node) ?: return "N/A"
        return "${percent.coerceIn(0, 100)}%"
    }

    /**
     * التردّد الحالي للرسّام بالميغاهرتز: `"754 MHz"` أو `"N/A"`.
     *
     * المصدر الأوّل هو عقدة GED الحيّة لأنها تعرض الدرجة **المقفلة**؛ و`devfreq` احتياطٌ لمن
     * لا يملكها. وقيمة GED تأتي بثلاث وحدات مختلفة بين الأنوية (هرتز/كيلوهرتز/ميغاهرتز) ولا
     * علامة تدلّ عليها، فالحكم بالمقدار — وهو مقيس: ١_٣٠٠ ميجاهرتز تفصل بين الصيغتين.
     */
    fun getCurrentGpuFreq(): String {
        if (RootFileAccess.exists(GED_LIVE_FREQ)) {
            val raw = RootFileAccess.read(GED_LIVE_FREQ).orEmpty()
            // آخر رقم في السطر: الأوّل قد يكون رقم OPP لا التردّد.
            val value = INTEGER.findAll(raw).lastOrNull()?.value?.toLongOrNull()
            if (value != null && value > 0L) {
                return formatMHz(normalizeToHz(value.toDouble(), unit = null))
            }
        }
        val node = getGpuDevfreqNode() ?: return "N/A"
        val hz = readInt("$node/cur_freq")?.toLongOrNull()?.takeIf { it > 0L } ?: return "N/A"
        return formatMHz(hz)
    }

    /**
     * درجات OPP المتاحة بالهرتز، مرتّبة تصاعديًّا وبلا تكرار.
     *
     * يُقرأ أول جدول **موجود** من [OPP_TABLES]، ويُستخرج من كل سطر فهرسه `[n]` ورقمه. والصيغ
     * بين الأنوية ثلاثة كما في التردّد الحيّ — والرقم المجرَّد يُقرأ بالوحدات المعلَنة إن وُجدت
     * وإلّا بالمقدار. والعائد فارغ إن لم يوجد جدول: أعلى درجة ليست حقيقة يعرفها هذا الملف،
     * فلا تُختلق.
     */
    fun oppFrequenciesHz(): List<Long> {
        val table = RootFileAccess.firstExisting(OPP_TABLES) { it } ?: return emptyList()
        val lines = RootFileAccess.read(table)?.lines().orEmpty()
        return lines.mapNotNull { line ->
            val tail = line.substringAfterLast(']', line)
            val match = NUMBER_WITH_UNIT.find(tail) ?: return@mapNotNull null
            val value = match.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
            normalizeToHz(value, match.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }).takeIf { it > 0L }
        }.distinct().sorted()
    }

    // ── مساعدات ─────────────────────────────────────────────────────────────────

    /** أوّل عدد صحيح في نصّ عقدة، أو `null`. يحتمل نصًّا يحمل وحدات أو أعمدة. */
    private fun readInt(path: String): Int? =
        INTEGER.find(RootFileAccess.read(path).orEmpty())?.value?.toIntOrNull()

    /**
     * توحيد أيّ رقم تردّد إلى هرتز.
     *
     * - وحدة معلَنة ⇒ تحويل مباشر بلا تخمين.
     * - بلا وحدة ⇒ بالمقدار: أكبر من ١٠ ملايين هرتز (١٠ ميجاهرتز)، أكبر من ١٣٠٠ (أعلى درجة
     *   على هذه الشرائح ≈ ١٣٠٠ ميجاهرتز) كيلوهرتز، وإلّا ميغاهرتز. والحدّ ١٣٠٠ مقيس من واقع
     *   الشرائح لا مفترض: أدنى منه بالكيلو هرتز مستحيل عمليًّا، وأعلى منه بالميغاهرتز كذلك.
     */
    private fun normalizeToHz(value: Double, unit: String?): Long = when (unit?.lowercase()) {
        "ghz" -> (value * 1_000_000_000.0).toLong()
        "mhz" -> (value * 1_000_000.0).toLong()
        "khz" -> (value * 1_000.0).toLong()
        else -> when {
            value >= 10_000_000.0 -> value.toLong()
            value > 1_300.0 -> (value * 1_000.0).toLong()
            else -> (value * 1_000_000.0).toLong()
        }
    }

    /** عرض هرتز بالميغاهرتز كما تقرأه الواجهة (`"754 MHz"`). */
    private fun formatMHz(hz: Long): String = "${hz / 1_000_000} MHz"
}
