package nd.max.core.maxai

import nd.max.core.hardware.CpuHardwareBackend
import nd.max.core.hardware.GpuHardwareBackend
import nd.max.core.hardware.HardwareCapabilitySnapshot
import nd.max.core.hardware.HardwareControlKey
import nd.max.core.hardware.HardwareFeature

/**
 * مفردات Max AI — ليست أسماء أفعال، بل المقابض الحقيقية المكتشفة على
 * هذا العتاد.
 *
 * قبل هذا الملف كانت مفردات العقل ثماني سلاسل نصية عربية تنتهي ستٌّ
 * منها إلى تبديل ملف عام، فكان "ذكاءً" لا يستطيع التعبير عن «اخفض GPU
 * 15% وأبقِ CPU». هنا يصبح لكل مقبض: سُلّم قيم مشتق من العتاد، اتجاه
 * معلن، تكلفة، ودالة تطبيق موثّقة بقراءة بعدية.
 *
 * القاعدة الصارمة (INV-5): ما لا تُثبِت القدرات أنه قابل للكتابة لا
 * يوجد في المفردات أصلًا — لا يُفلتَر لاحقًا. جهاز بلا تحكم GPU لا
 * يرى العقل فيه مقبض GPU إطلاقًا، فيعمل نفس الكود على أي جهاز.
 */
object ControlRegistry {

    /** أثر المقبض المعلن: ما الذي يرفعه وما الذي يكلفه. */
    enum class Direction {
        /** يرفع الأداء ويستهلك طاقة/حرارة أكثر. */
        RAISE_PERFORMANCE,

        /** يخفض الاستهلاك والحرارة على حساب الأداء. */
        SAVE_ENERGY,
    }

    /**
     * مقبض واحد قابل للتخطيط.
     *
     * @param ladder سلّم القيم المسموح بها مرتبًا تصاعديًا بالأداء —
     *        مشتق من العتاد (OPP/نِسب)، لا من ثوابت مكتوبة يدويًا.
     * @param cost تكلفة التغيير النسبية (0..1): كم هو تدخل خشن؟
     *        المخطِّط يفضّل الأرخص عند تساوي الأثر.
     * @param apply تطبيق موثّق: يعيد القيمة المقروءة فعليًا بعد الكتابة
     *        أو null عند الفشل — لا ادعاء نجاح بلا قراءة.
     */
    data class Control(
        val key: String,
        val feature: HardwareFeature,
        val label: String,
        val ladder: List<String>,
        val cost: Float,
        val read: () -> String?,
        val apply: (String) -> String?,
    ) {
        /** موضع القيمة الحالية على السلّم، أو null إن كانت خارجه. */
        fun indexOf(value: String?): Int? = value?.let { v ->
            ladder.indexOf(v).takeIf { it >= 0 }
        }

        /**
         * حجم الانتقال نسبةً إلى مدى المقبض كاملًا: موجب للرفع، سالب
         * للخفض، و0 حين يتعذّر تحديد الموضع.
         *
         * هذه هي وحدة التعلّم القابلة للنقل بين الأجهزة: نموذج
         * الاستجابة يتعلّم "أثر رفع 12% من المدى" لا "أثر 2.1 جيجاهرتز"،
         * فينتقل ما تعلّمه إلى جهاز بترددات مختلفة تمامًا.
         */
        fun stepFraction(from: String?, to: String?): Float {
            val span = ladder.size - 1
            if (span <= 0) return 0f
            val fromIdx = indexOf(from) ?: return 0f
            val toIdx = indexOf(to) ?: return 0f
            return (toIdx - fromIdx).toFloat() / span
        }

        /** خطوة واحدة نحو الاتجاه المطلوب — أساس "أصغر تدخل كافٍ". */
        fun step(current: String?, direction: Direction): String? {
            val idx = indexOf(current) ?: return null
            val next = when (direction) {
                Direction.RAISE_PERFORMANCE -> idx + 1
                Direction.SAVE_ENERGY -> idx - 1
            }
            return ladder.getOrNull(next)
        }
    }

    /**
     * يبني المفردات من لقطة القدرات. كل مقبض يُضاف فقط عندما تكون
     * قدرته READ_WRITE مثبتة وسُلّمه غير فارغ.
     */
    fun build(capabilities: HardwareCapabilitySnapshot): List<Control> = buildList {
        if (capabilities.canWrite(HardwareFeature.CPU_FREQUENCY)) {
            addAll(cpuCeilingControls())
        }
        if (capabilities.canWrite(HardwareFeature.GPU_FREQUENCY)) {
            gpuCeilingControl()?.let(::add)
        }
        if (capabilities.canWrite(HardwareFeature.CPU_BOOST)) {
            cpuBoostControl()?.let(::add)
        }
    }

    // ── CPU: سقف لكل سياسة، بسلّم من ترددات العتاد المعلنة ───────────

    private fun cpuCeilingControls(): List<Control> =
        CpuHardwareBackend.policies().mapNotNull { policy ->
            val ladder = policy.availableFrequenciesKHz
                .filter { it > 0L }
                .distinct()
                .sorted()
                .map { frequency -> "${policy.minKHz ?: policy.provenMinKHz ?: frequency}:$frequency" }
            if (ladder.size < 2) return@mapNotNull null
            Control(
                key = HardwareControlKey.cpuLimits(policy.name),
                feature = HardwareFeature.CPU_FREQUENCY,
                label = "سقف ${policy.name}",
                ladder = ladder,
                // تعديل سقف عنقود واحد تدخل دقيق ورخيص مقارنة بملف عام.
                cost = 0.2f,
                read = {
                    CpuHardwareBackend.policies()
                        .firstOrNull { it.path == policy.path }?.let { live ->
                            "${live.minKHz ?: ""}:${live.maxKHz ?: ""}"
                        }
                },
                apply = { value ->
                    val parts = value.split(":", limit = 2)
                    val min = parts.getOrNull(0)?.toLongOrNull() ?: return@Control null
                    val max = parts.getOrNull(1)?.toLongOrNull() ?: return@Control null
                    val result = CpuHardwareBackend.setPolicyLimits(policy.path, min, max)
                    if (!result.verified) null else {
                        CpuHardwareBackend.policies()
                            .firstOrNull { it.path == policy.path }?.let { live ->
                                "${live.minKHz ?: ""}:${live.maxKHz ?: ""}"
                            }
                    }
                },
            )
        }

    // ── GPU: سقف من جدول OPP الذي يثبته الدرايفر ─────────────────────

    private fun gpuCeilingControl(): Control? {
        val selection = GpuHardwareBackend.selection()
        val device = selection.device ?: return null
        if (!device.rangeWritable && !device.exactLockWritable) return null
        val ladder = device.frequencies.map(Long::toString)
        if (ladder.size < 2) return null
        return Control(
            key = HardwareControlKey.gpuFrequency(device.name),
            feature = HardwareFeature.GPU_FREQUENCY,
            label = "سقف GPU",
            ladder = ladder,
            cost = 0.25f,
            read = {
                GpuHardwareBackend.refresh(device.path)?.let(GpuHardwareBackend::effectiveFrequency)?.toString()
            },
            apply = { value ->
                val target = value.toLongOrNull() ?: return@Control null
                val live = GpuHardwareBackend.refresh(device.path) ?: return@Control null
                val request = if (live.rangeWritable) {
                    GpuHardwareBackend.Request(minFreq = live.frequencies.first(), maxFreq = target)
                } else {
                    GpuHardwareBackend.Request(minFreq = target, maxFreq = target)
                }
                val result = GpuHardwareBackend.apply(live, request)
                if (!result.verified) null else result.actual?.let(GpuHardwareBackend::effectiveFrequency)?.toString()
            },
        )
    }

    // ── CPU boost: مقبض ثنائي رخيص وسريع الأثر ───────────────────────

    private fun cpuBoostControl(): Control? {
        val node = CpuHardwareBackend.boostNode() ?: return null
        return Control(
            key = HardwareControlKey.CPU_BOOST,
            feature = HardwareFeature.CPU_BOOST,
            label = "تعزيز CPU",
            ladder = listOf("0", "1"),
            cost = 0.1f,
            read = { nd.max.core.hardware.RootFileAccess.read(node)?.trim() },
            apply = { value ->
                val result = CpuHardwareBackend.setBoost(value == "1")
                if (!result.verified) null else result.actual
            },
        )
    }
}
