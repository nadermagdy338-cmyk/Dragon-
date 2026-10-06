/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.maxai

/**
 * توزيع السقف الحراري على **عناقيد المعالج بالدور لا بالنسبة نفسها**.
 *
 * ### العطب الذي يحلّه (مقيس من شكوى المالك على جهازه: «يجعل كل ترددات الـ8 أنوية إلى أقلّ
 * تردد فيصير الهاتف لاجًّا شديدًا … يجب أن يعرف الأفضل فلا يستخدم سكينًا لذبح نملة»)
 *
 * كان [CpuCeilingKnobs.cap] يكتب **الكسر نفسه** لكل سياسات cpufreq: عند الحرج ٠٫٣٥ من المدى
 * لكل عنقود. والنتيجة أن نواة الأداء الواحدة (أغلى نواة حرارةً وأقلّها فائدةً عند التبريد لأنها
 * واحدة) تُخفض بنفس مقدار أربع نوى صغيرة يحتاجها النظام ليبقى **قابلًا للاستعمال**: تنخفض
 * الحرارة فعليًّا، لكن الواجهة تتجمّد لأن كل شيء صار بنفس البطء.
 *
 * ### القاعدة المُتبنّاة
 *
 * **التدخل بالأدوار: العنقود الأصغر يُخفَّض أقل، والعنقود الأكبر يُخفَّض أكثر.**
 * سببه مقيس لا ذوقيّ: القدرة المبدَّدة تتناسب مع التردد عند الجهد نفسه، وعنقود الأداء يعمل على
 * جهد أعلى، فخفضه هو **أرخص خفضٍ للحرارة**؛ وفي المقابل بقاء نوى الخلفية بتردد معقول يحفظ
 * استجابة النظام فيبقى التحكم نفسه (سحب، كتابة، تنقّل) ممكنًا — وهو ما فُقد حين خُفض الجميع.
 *
 * والأرضيّات ([Config]) هي ما يمنع «أقلّ تردد مباشرةً»: لا ينزل عنقود صغير تحت ٦٠٪ من مداه ولا
 * عنقود وسط تحت ٤٥٪، وعنقود الأداء وحده يُسمح له بالنزول إلى ٢٥٪ لأنه الدرع الأخير عند التجاوز.
 *
 * ### الحدّ الذي لا يُخترق
 * التوزيع **لا يرخي الحماية**: كل عنقود يأخذ كسرًا ≥ الكسر المطلوب (النواة تقصّ عند ٠٫١ قبل
 * الاستدعاء)، فمجموع القدرة المخصومة يبقى في مرتبة القاعدة القديمة، والفرق أنه **موزَّع**.
 * والفئات كلها نقية بلا Android وبلا وقت، فتُقاس باختبار JVM كما تُقاس [MaxAiThermalCurve].
 */
object ThermalCapDistribution {

    /** دور العنقود في التوزيع — يُشتقّ من **ترتيب مداه** لا من أسماء النواة الخاصة بكل شركة. */
    enum class Role { LITTLE, MID, PRIME }

    /**
     * عنقود كما يراه الموزِّع: اسم السياسة ومداها المُعلن.
     * ولا يُطلب عدد الأنوية: تصنيف الدور بالتردد هو ما يهمّ للحرارة، والاسم يفصل التعادل.
     */
    data class Cluster(val name: String, val maxKHz: Long)

    /**
     * ثوابت التوزيع. الرفع والأرضية معًا: الرفع يجعل العنقود الأصغر أعلى من القاعدة، والأرضية
     * تمنعه أن يهبط — حتى في التجاوز الأقصى — إلى حدّ يعطّل الجهاز.
     */
    data class Config(
        /** يُضاف لكسر العنقود الأصغر. */
        val littleLift: Float = 0.25f,
        /** يُضاف لكسر العناقيد الوسطى. */
        val midLift: Float = 0.12f,
        /** حدّدنيا العنقود الأصغر — بلده أن يبقى النظام قابلًا للاستعمال. */
        val littleFloor: Float = 0.60f,
        /** حدّدنيا العنقود الوسطى. */
        val midFloor: Float = 0.45f,
        /** حدّدنيا عنقود الأداء: يُسمح له بالنزول أكثر، لأنه نواة واحدة أثرها الحراري أعلى. */
        val primeFloor: Float = 0.25f,
    )

    /**
     * يصنّف العناقيد بالترتيب: **الأدنى مدًى = صغير، والأعلى مدًى = أداء، وما بينهما وسط**.
     * والترتيب ثابت (مدى ثم اسم) فلا يتبدّل دور عنقودين متساويين بين دورتين.
     *
     * **والعنقود الوحيد يُصنَّف `PRIME` لا `LITTLE`** — وهو حدّ أمان لا تفصيل شكليّ: جهاز بعنقود
     * واحد لا معنى فيه لتوزيع «أخفّ على الخلفية وأثقل على الأداء»، فالرفع هناك كان سيُخفّف السقف
     * عن كل العتاد في أخطر لحظة (تجاوز حرج) بلا مقابل. فيبقى على القاعدة القديمة بالحرف
     * (والأرضية ٠٫٢٥)، وهو ما يحفظ الشرط المُعلن: **التوزيع لا يجعل أي عنقود أخفّ ممّا كان**.
     */
    fun roles(clusters: List<Cluster>): Map<String, Role> {
        if (clusters.isEmpty()) return emptyMap()
        val sorted = clusters.sortedWith(compareBy({ it.maxKHz }, { it.name }))
        return buildMap {
            sorted.forEachIndexed { index, cluster ->
                put(
                    cluster.name,
                    when {
                        sorted.size == 1 -> Role.PRIME
                        index == sorted.lastIndex -> Role.PRIME
                        index == 0 -> Role.LITTLE
                        else -> Role.MID
                    },
                )
            }
        }
    }

    /** كسر عنقود واحد بالدور: رفعٌ ثم أرضية. `base` مقصوص إلى ٠..١، والنتيجة لا تتجاوز ١. */
    fun fractionFor(role: Role, base: Float, config: Config = Config()): Float {
        val clamped = base.coerceIn(0f, 1f)
        val lifted = when (role) {
            Role.LITTLE -> clamped + config.littleLift
            Role.MID -> clamped + config.midLift
            Role.PRIME -> clamped
        }
        val floor = when (role) {
            Role.LITTLE -> config.littleFloor
            Role.MID -> config.midFloor
            Role.PRIME -> config.primeFloor
        }
        return lifted.coerceIn(floor.coerceIn(0f, 1f), 1f)
    }

    /**
     * خريطة الكسور لكل عنقود باسمه. سقفٌ حراري واحد (`base`) يصير كسورًا مختلفة بالأدوار.
     * و`base >= 1` تعني «لا سقف» فترجع الكسور كلها ١ (بلا كتابة على العتاد).
     */
    fun fractions(
        clusters: List<Cluster>,
        base: Float,
        config: Config = Config(),
    ): Map<String, Float> {
        val byRole = roles(clusters)
        return clusters.associate { cluster ->
            cluster.name to fractionFor(byRole[cluster.name] ?: Role.MID, base, config)
        }
    }
}
