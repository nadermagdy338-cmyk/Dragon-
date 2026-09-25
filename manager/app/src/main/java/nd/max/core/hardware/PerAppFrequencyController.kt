/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.hardware

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Frequency controls for per-app profiles. Requests are capability-driven and
 * are never silently substituted with an unsupported value.
 */
@Singleton
class PerAppFrequencyController @Inject constructor(
    private val arbiter: HardwareControlArbiter,
) {
    data class Result(
        val requested: String,
        val applied: Boolean,
        val verified: Boolean,
        val actual: String? = null,
        val error: String? = null,
    )

    fun applyCpuLimits(policyPath: String, minKHz: Long?, maxKHz: Long?): Result {
        if (minKHz == null && maxKHz == null) return Result("default", true, true, "default")
        val token = "per-app-cpu-${System.currentTimeMillis()}"
        val baselinePolicy = CpuHardwareBackend.policies().firstOrNull { it.path == policyPath }
            ?: return Result("${minKHz ?: ""}:${maxKHz ?: ""}", false, false, error = "unsupported-policy")
        // المفتاح بـ**اسم السياسة** لا بمسارها: هذا هو المفتاح الذي يُنشئه كل كاتب آخر
        // (`AppMonitor` · `CpuCeilingKnobs` · `ControlRegistry` · `CpuCoreControlViewModel`)
        // وبه يُقفل المستخدم مقبضًا يدويًّا (`ManualControlLocks`). ومفتاحٌ بمسار كامل لمقبض
        // واحد يعني سجلَّي أسبقية لمقبض واحد: قفلٌ من شاشة الأنوية لا يرى هذا الطلب، وهذا
        // الطلب لا يراه ذلك القفل — و«قفل يحترمه كاتب ويجهله آخر» ليس قفلًا.
        val key = HardwareControlKey.cpuLimits(baselinePolicy.name)
        val baseline = "${baselinePolicy.minKHz ?: ""}:${baselinePolicy.maxKHz ?: ""}"
        // القيمة المطلوبة تُصاغ من جدول OPP المُعلَن: قيمة بين الحدّين وليست في الجدول
        // لا يرفضها السائق بل يُبدّلها، فيصير التحقق فشلًا لتغيير ناجح ثم يُسترجع خط
        // الأساس (`CpuHardwareBackend.snapToAvailableAtOrBelow` يحمل القياس).
        val minSnapped = minKHz?.let { CpuHardwareBackend.snapToAvailableAtOrBelow(baselinePolicy, it) }
        val maxSnapped = maxKHz?.let { CpuHardwareBackend.snapToAvailableAtOrBelow(baselinePolicy, it) }
        val desired = "${minSnapped ?: ""}:${maxSnapped ?: ""}"

        val r = arbiter.submit(
            key = key,
            owner = ControlOwnership.Owner.PER_APP,
            token = token,
            desired = desired,
            apply = { value ->
                val parts = value.split(":", limit = 2)
                CpuHardwareBackend.setPolicyLimits(
                    policyPath,
                    parts.getOrNull(0)?.takeIf { it.isNotBlank() }?.toLongOrNull(),
                    parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.toLongOrNull(),
                ).verified
            },
            read = {
                CpuHardwareBackend.policies().firstOrNull { it.path == policyPath }?.let {
                    "${it.minKHz ?: ""}:${it.maxKHz ?: ""}"
                }
            },
            baseline = baseline,
            restore = { value ->
                val parts = value.split(":", limit = 2)
                CpuHardwareBackend.setPolicyLimits(
                    policyPath,
                    parts.getOrNull(0)?.takeIf { it.isNotBlank() }?.toLongOrNull(),
                    parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.toLongOrNull(),
                ).verified
            },
            // السقف والأرضية المُعلنان قد يقيّدهما الـvendor أكثر؛ فيكون المدى الحيّ **داخل**
            // الطلب = الطلب مُلبّى. والتساوي الحرفي كان يُقرأ فشلًا فيسترجع خط الأساس.
            verify = HardwareVerification::rangeContained,
            // والسقف الأدنى الذي **نحن** كتبناه ليس سقفًا للجهاز: طلبُ الرفع يُكتب
            // ([HardwareVerification.ceilingReached])، وإلا بقي الجهاز على أدنى قيمة اخترناها.
            realized = HardwareVerification::ceilingReached,
        )
        return Result(desired, r.applied, r.verified, r.actual, r.error)
    }

    fun applyGpuCeiling(maxHz: Long): Result {
        val selection = GpuHardwareBackend.selection()
        val device = selection.device
            ?: return Result(maxHz.toString(), false, false, error = selection.reason)
        if (device.frequencies.isEmpty()) {
            return Result(maxHz.toString(), false, false, error = "no-advertised-frequency-range")
        }
        // Refresh immediately before planning: the advertised OPP table is a
        // capability catalogue, while a vendor daemon may lower live max_freq.
        val liveAtPlan = GpuHardwareBackend.refresh(device.path)
            ?: return Result(maxHz.toString(), false, false, error = "provider-disappeared")
        // والتخطيط من **القدرة المُعلنة** لا من السقف الحيّ: `max_freq` قيمةٌ يكتبها هذا التطبيق
        // نفسه (سقف بروفايل/شاشة)، فتصير بعد أول خفض «سقفَ الجهاز» في نظر كل طلب تالٍ — وهو
        // العطب المقيس (rodin · MT6899 · 2026-09-22): سقفٌ ٥٢٠ كتبناه، ثم طلبُ ٧٠٢ لم يُكتب على
        // العقدة أصلًا فبقي التردد على ٥٢٠. فالسقف الحيّ يُقاس ويُعلَن بعد الكتابة، ولا يُستخدم
        // في التخطيط ضدّ المستخدم.
        val target = GpuHardwareBackend.snapToAvailableAtOrBelow(liveAtPlan, maxHz, respectLiveCeiling = false)
            ?: return Result(maxHz.toString(), false, false, error = "unsupported-frequency")
        val low = liveAtPlan.frequencies.firstOrNull { it <= target } ?: target

        val key = HardwareControlKey.gpuFrequency(device.name)
        val token = "per-app-gpu-${System.currentTimeMillis()}"
        val baseline = GpuHardwareBackend.captureBaseline(liveAtPlan)
        val request = if (liveAtPlan.rangeWritable) {
            GpuHardwareBackend.Request(minFreq = low, maxFreq = target)
        } else {
            GpuHardwareBackend.Request(minFreq = target, maxFreq = target)
        }
        // الصيغة القياسية نفسها التي يستعملها GPU Studio: حقل المدى مع المُحكِّم وقفل OPP.
        // مفتاح GPU مشترك بين الكاتبين (`gpu_frequency:<device>`)، فوَصْفٌ مختلف لكل كاتب يجعل
        // نصوص «المطلوب» و«المقروء» غير قابلين للمقارنة بين الكتابين — وهو فخّ لأي قارئ لاحق
        // للـjournal المشترك. صيغة واحدة تعني أن التساوي يعني الشيء نفسه في كل مكان.
        val desired = GpuHardwareBackend.encodeRequest(request)

        val r = arbiter.submit(
            key = key,
            owner = ControlOwnership.Owner.PER_APP,
            token = token,
            desired = desired,
            // الطلب المُتحقَّق منه هو نفسه الذي حُسبت منه القيمة المطلوبة: إعادةُ بناء طلب من
            // النصّ كانت تُسقط أي حقل يُضاف للمخطط لاحقًا (نفس العطب الذي أُصلح في GPU Studio).
            apply = {
                GpuHardwareBackend.refresh(device.path)?.let { live ->
                    val boundedTarget = GpuHardwareBackend
                        .snapToAvailableAtOrBelow(live, maxHz, respectLiveCeiling = false)
                        ?: return@let false
                    val boundedRequest = if (live.rangeWritable) {
                        val boundedLow = live.frequencies.firstOrNull { it <= boundedTarget } ?: boundedTarget
                        request.copy(minFreq = boundedLow, maxFreq = boundedTarget)
                    } else {
                        request.copy(minFreq = boundedTarget, maxFreq = boundedTarget)
                    }
                    GpuHardwareBackend.applyValidated(live, boundedRequest).verified
                } ?: false
            },
            read = {
                GpuHardwareBackend.refresh(device.path)?.let { live ->
                    GpuHardwareBackend.encodeLive(live, request)
                }
            },
            baseline = GpuHardwareBackend.encodeLive(liveAtPlan, request),
            restore = { GpuHardwareBackend.restoreBaseline(baseline) },
            verify = GpuHardwareBackend::requestSatisfied,
            realized = HardwareVerification::ceilingReached,
        )
        return Result(desired, r.applied, r.verified, r.actual, r.error)
    }
}
