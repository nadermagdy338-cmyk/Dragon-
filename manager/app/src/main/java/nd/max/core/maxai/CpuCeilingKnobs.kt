package nd.max.core.maxai

import nd.max.core.hardware.ControlOwnership
import nd.max.core.hardware.CpuHardwareBackend
import nd.max.core.hardware.HardwareControlArbiter
import nd.max.core.hardware.HardwareControlKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * عقدة "سقف تردد CPU لكل سياسة cpufreq" عبر المُحكِّم الموحد — نفس
 * مسار التحكيم المجرَّب (مفتاح cpu_limits:<policy> بصيغة "min:max"،
 * كتابة موثّقة بقراءة حية بعد الكتابة، وخط أساس للاسترجاع).
 *
 * يستخدمه محرك MAX AI (Owner.MAX_AI) ومحرك الأمان (Owner.SAFETY):
 * عندما يتدخل الأمان يسبق ملكيته كل مالك آخر فيُحجب سقف AI تلقائيًا
 * في نفس المُحكِّم — هذا هو تنفيذ "الأمان فوق الجميع" على العتاد.
 */
@Singleton
class CpuCeilingKnobs @Inject constructor(
    private val arbiter: HardwareControlArbiter,
) {
    data class KnobOutcome(val applied: Int, val blocked: Int, val failed: Int, val detail: String)

    /**
     * يثبّت سقفًا لكل سياسة عند جزء محدد من مدى العتاد
     * ([fractionOfRange] من 0.0 إلى 1.0؛ 1.0 = تحرير كامل).
     */
    fun cap(fractionOfRange: Float, owner: ControlOwnership.Owner, token: String): KnobOutcome {
        val fraction = fractionOfRange.coerceIn(0.1f, 1f)
        return submitPerPolicy(owner, token) { policy ->
            val hwMin = policy.hwMinKHz ?: policy.minKHz ?: 0L
            val hwMax = policy.hwMaxKHz ?: policy.maxKHz ?: return@submitPerPolicy null
            val cappedMax = hwMin + ((hwMax - hwMin) * fraction).toLong()
            "$hwMin:$cappedMax"
        }
    }

    /** يحرر السقف إلى مدى العتاد الكامل (أداء مفتوح). */
    fun release(owner: ControlOwnership.Owner, token: String): KnobOutcome =
        submitPerPolicy(owner, token) { policy ->
            val hwMin = policy.hwMinKHz ?: policy.minKHz ?: return@submitPerPolicy null
            val hwMax = policy.hwMaxKHz ?: policy.maxKHz ?: return@submitPerPolicy null
            "$hwMin:$hwMax"
        }

    /** يترك كل مفاتيح هذا المالك (مع استرجاع خط الأساس عند آخر مغادرة). */
    fun leaveAll(token: String) {
        arbiter.releaseToken(token, restore = true)
    }

    private fun submitPerPolicy(
        owner: ControlOwnership.Owner,
        token: String,
        desiredFor: (CpuHardwareBackend.Policy) -> String?,
    ): KnobOutcome {
        val policies = CpuHardwareBackend.policies()
        if (policies.isEmpty()) {
            return KnobOutcome(0, 0, 0, "no cpufreq policies")
        }

        var applied = 0
        var blocked = 0
        var failed = 0
        val details = StringBuilder()

        policies.forEach { policy ->
            val desired = desiredFor(policy) ?: return@forEach
            val key = HardwareControlKey.cpuLimits(policy.name)
            val baseline = "${policy.minKHz ?: ""}:${policy.maxKHz ?: ""}"

            val result = arbiter.submit(
                key = key,
                owner = owner,
                token = token,
                desired = desired,
                apply = { value -> setLimits(policy, value) },
                read = {
                    CpuHardwareBackend.policies().firstOrNull { it.name == policy.name }
                        ?.let { "${it.minKHz ?: ""}:${it.maxKHz ?: ""}" }
                },
                baseline = baseline,
                restore = { value -> setLimits(policy, value) },
            )

            when {
                result.blocked -> {
                    blocked++
                    details.append("${policy.name}=محجوز(${result.winner});")
                }
                result.verified -> {
                    applied++
                    details.append("${policy.name}=${result.actual};")
                }
                else -> {
                    failed++
                    details.append("${policy.name}=فشل(${result.actual ?: "none"});")
                }
            }
        }
        return KnobOutcome(applied, blocked, failed, details.toString())
    }

    private fun setLimits(policy: CpuHardwareBackend.Policy, value: String): Boolean {
        val parts = value.split(":", limit = 2)
        val min = parts.getOrNull(0)?.takeIf { it.isNotBlank() }?.toLongOrNull()
        val max = parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.toLongOrNull()
        return CpuHardwareBackend.setPolicyLimits(policy.path, min, max).successful
    }
}
