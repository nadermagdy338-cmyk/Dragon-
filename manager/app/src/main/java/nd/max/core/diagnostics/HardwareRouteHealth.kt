package nd.max.core.diagnostics

import nd.max.core.atlas.AtlasCapability
import nd.max.core.atlas.AtlasCapabilityInputs
import nd.max.core.atlas.AtlasCapabilityRules
import nd.max.core.atlas.AtlasControlGoal
import nd.max.core.atlas.AtlasControlIntent
import nd.max.core.atlas.AtlasControlTarget
import nd.max.core.atlas.AtlasControlTransport
import nd.max.core.atlas.AtlasRouteCandidate
import nd.max.core.atlas.AtlasRouteDecision
import nd.max.core.atlas.AtlasRouteEvidence
import nd.max.core.atlas.AtlasRoutePlanner
import nd.max.core.atlas.AtlasRouteReason
import nd.max.core.atlas.AtlasRouteStatus
import nd.max.core.atlas.AtlasSafetyPolicy
import nd.max.core.hardware.AccessLevel
import nd.max.core.hardware.CpuHardwareBackend
import nd.max.core.hardware.GpuHardwareBackend
import nd.max.core.hardware.HardwareCapabilitySnapshot
import nd.max.core.hardware.HardwareFeature

/**
 * The production consumer of `AtlasRoutePlanner`: it answers **why** a control can or cannot be
 * activated on this device, from facts only.
 *
 * Until now the planner was a tested substrate with nothing calling it, which is the same as not
 * existing from the user's side. This class builds the input it was designed for — per-target route
 * evidence — and hands back a verdict per control, so the diagnostics surface can say
 * "no route: the baseline cannot be read back" instead of leaving the user with a control that
 * silently does nothing.
 *
 * Three rules keep it honest:
 *
 * 1. **Read-only, twice over.** CPU facts come through [CpuHardwareBackend.DiscoveryIo] and GPU facts
 *    through [GpuHardwareBackend.ReadIo]: neither seam *has* a write member, so this class cannot
 *    reach a writer even by mistake. Writability is only ever *read* as a fact, from
 *    [HardwareCapabilitySnapshot.access].
 * 2. **No invented routes.** Only the route that this build actually implements — the arbiter's
 *    verified sysfs path — is declared. A control with no declared route is reported as
 *    [AtlasRouteStatus.REVIEW_REQUIRED], never as "supported" and never as "broken": those are
 *    different claims and the second one would be a guess.
 * 3. **It answers about activation, not about goals.** `measuredGoalAvailable` is left true here
 *    because a capability check cannot know whether a target FPS or temperature is reachable; the
 *    goal-gated check belongs to a caller that actually measures. The verdict says "there is a proven
 *    way to apply this", not "this will hit your target".
 *
 * Lives in `core/diagnostics`, not in `core/atlas`, on purpose: `core/atlas` is forbidden from naming
 * writability at all (see `AtlasArchitectureTest`), and putting the evidence builder there would have
 * broken that guard or forced it to be weakened.
 */
object HardwareRouteHealth {

    /**
     * One control's activation verdict, in the planner's own vocabulary — plus its place on the
     * **capability map** (`Map` stage of the Atlas cycle), derived by the same single truth table the
     * execution path uses ([AtlasCapabilityRules]): the diagnostics matrix and a write attempt may
     * never grow two answers to "what can this device do".
     */
    data class Verdict(
        val target: AtlasControlTarget,
        val feature: HardwareFeature,
        val status: AtlasRouteStatus,
        val reason: AtlasRouteReason?,
        val transport: AtlasControlTransport? = null,
        val providerId: String? = null,
        val evidence: AtlasRouteEvidence? = null,
        val capability: AtlasCapability,
    ) {
        /**
         * A stable machine code for this verdict — never a sentence.
         *
         * The UI owns the words (and the translation of them); a core type that carried user-facing
         * Arabic would surface Arabic inside an English interface, which is a defect this project has
         * already paid for once.
         */
        val code: String get() = listOf(
            status.name.lowercase(),
            reason?.name?.lowercase() ?: transport?.name?.lowercase() ?: "no-route-evidence",
        ).joinToString(":")
    }

    /** Every feature that has a control surface, in display order. */
    val monitoredFeatures: List<HardwareFeature> = listOf(
        HardwareFeature.CPU_FREQUENCY,
        HardwareFeature.CPU_GOVERNOR,
        HardwareFeature.GPU_FREQUENCY,
        HardwareFeature.GPU_GOVERNOR,
        HardwareFeature.THERMAL_COOLING,
        HardwareFeature.DISPLAY_REFRESH,
        HardwareFeature.ZRAM,
    )

    /**
     * Verdicts for every monitored control.
     *
     * Both readers are injectable so the whole decision is testable against a fake device: a fixture
     * that claims an unambiguous writable GPU must produce an eligible route, and one that claims two
     * equally proven GPUs must produce exactly one blocked verdict naming the ambiguity.
     */
    fun verdicts(
        snapshot: HardwareCapabilitySnapshot,
        cpuIo: CpuHardwareBackend.DiscoveryIo = CpuHardwareBackend.SystemDiscoveryIo,
        gpuReads: GpuHardwareBackend.ReadIo = GpuHardwareBackend.SystemIo,
    ): List<Verdict> {
        val policies = runCatching { CpuHardwareBackend.policies(cpuIo) }.getOrDefault(emptyList())
        val gpu = runCatching { GpuHardwareBackend.observe(gpuReads) }
            .getOrNull()
        return monitoredFeatures.map { feature ->
            when (feature) {
                HardwareFeature.CPU_FREQUENCY -> decide(
                    snapshot = snapshot,
                    feature = feature,
                    target = AtlasControlTarget.CPU_FREQUENCY,
                    candidate = cpuFrequencyRoute(snapshot, policies),
                )

                HardwareFeature.CPU_GOVERNOR -> decide(
                    snapshot = snapshot,
                    feature = feature,
                    target = AtlasControlTarget.CPU_GOVERNOR,
                    candidate = cpuGovernorRoute(snapshot, policies),
                )

                HardwareFeature.GPU_FREQUENCY -> decide(
                    snapshot = snapshot,
                    feature = feature,
                    target = AtlasControlTarget.GPU_FREQUENCY,
                    candidate = gpuFrequencyRoute(snapshot, gpu),
                )

                HardwareFeature.GPU_GOVERNOR -> decide(
                    snapshot = snapshot,
                    feature = feature,
                    target = AtlasControlTarget.GPU_GOVERNOR,
                    candidate = gpuGovernorRoute(snapshot, gpu),
                )

                // No verified route exists in this build for these yet. Reporting them as
                // `REVIEW_REQUIRED` is the truthful answer: it says "not reviewed", which is a fact
                // about our knowledge, rather than "unsupported", which would be a claim about the
                // device that nobody has established.
                else -> decide(snapshot, feature, targetFor(feature), candidate = null)
            }
        }
    }

    private fun decide(
        snapshot: HardwareCapabilitySnapshot,
        feature: HardwareFeature,
        target: AtlasControlTarget,
        candidate: AtlasRouteCandidate?,
    ): Verdict {
        val intent = AtlasControlIntent(target = target, goal = AtlasControlGoal.PERFORMANCE)
        val decision: AtlasRouteDecision = AtlasRoutePlanner.choose(
            intent = intent,
            candidates = listOfNotNull(candidate),
        )
        val chosen = decision.selected
        return Verdict(
            target = target,
            feature = feature,
            status = decision.status,
            reason = decision.reason,
            transport = chosen?.evidence?.transport,
            providerId = chosen?.evidence?.providerId,
            evidence = chosen?.evidence,
            capability = capabilityOf(snapshot, feature, target, candidate, decision),
        )
    }

    /**
     * مكان هذا التحكم على خريطة القدرة — مشتقّ لا مُقاس هنا: كل مدخل مما أثبته هذا السطح فعلًا،
     * وما لم يُثبته يبقى `false` («لم يُقاس» لا «غير موجود»). وذاكرة التعلّم خارج هذا السطح
     * الثابت، فتبقى `verifiedThisGeneration = false` هنا — لا يُدَّعى نجاح من عرض تشخيصي.
     */
    private fun capabilityOf(
        snapshot: HardwareCapabilitySnapshot,
        feature: HardwareFeature,
        target: AtlasControlTarget,
        candidate: AtlasRouteCandidate?,
        decision: AtlasRouteDecision,
    ): AtlasCapability {
        val visible = snapshot.capability(feature)?.access?.let { it != AccessLevel.NONE } ?: false
        return AtlasCapabilityRules.derive(
            AtlasCapabilityInputs(
                target = target,
                safety = AtlasSafetyPolicy.verdictForKey(candidate?.id ?: feature.name),
                measured = true,
                readable = candidate?.evidence?.readable ?: visible,
                // هذا السطح لا يُثبت غيابًا أبدًا: الإثبات يحتاج جردًا غير فارغ، وهو عمل مسار المسح.
                absenceProved = false,
                routeKnown = candidate != null,
                routeStatus = decision.status,
                routeReason = decision.reason,
                verifiedThisGeneration = false,
            ),
        )
    }

    private fun targetFor(feature: HardwareFeature): AtlasControlTarget = when (feature) {
        HardwareFeature.CPU_FREQUENCY -> AtlasControlTarget.CPU_FREQUENCY
        HardwareFeature.CPU_GOVERNOR -> AtlasControlTarget.CPU_GOVERNOR
        HardwareFeature.GPU_FREQUENCY -> AtlasControlTarget.GPU_FREQUENCY
        HardwareFeature.GPU_GOVERNOR -> AtlasControlTarget.GPU_GOVERNOR
        HardwareFeature.THERMAL_COOLING, HardwareFeature.THERMAL_ZONES -> AtlasControlTarget.THERMAL_PROFILE
        HardwareFeature.DISPLAY_REFRESH, HardwareFeature.DISPLAY_RESOLUTION -> AtlasControlTarget.DISPLAY_REFRESH
        HardwareFeature.ZRAM -> AtlasControlTarget.MEMORY
        else -> AtlasControlTarget.THERMAL_PROFILE
    }

    // ---- per-route evidence ------------------------------------------------------------------------

    private fun cpuFrequencyRoute(
        snapshot: HardwareCapabilitySnapshot,
        policies: List<CpuHardwareBackend.Policy>,
    ): AtlasRouteCandidate? {
        if (policies.isEmpty()) return null
        val writable = snapshot.canWrite(HardwareFeature.CPU_FREQUENCY)
        // The cpufreq ABI is kHz, but a *table* still has to exist: a policy with no proven bound cannot
        // be written at all (`setPolicyLimits` refuses with `unknown-hardware-bounds`), and a bound
        // whose unit was never established is how a value lands on the wrong scale.
        val boundsProven = policies.all { it.provenMinKHz != null && it.provenMaxKHz != null }
        val baselineReadable = policies.all { it.minKHz != null && it.maxKHz != null }
        return candidate(
            id = "cpu.frequency.arbiter",
            target = AtlasControlTarget.CPU_FREQUENCY,
            providerId = snapshot.capability(HardwareFeature.CPU_FREQUENCY)?.backend ?: "cpufreq",
            readable = true,
            privilegeAvailable = writable,
            unitProven = boundsProven,
            baselineReadable = baselineReadable,
            rollbackProven = baselineReadable && writable,
        )
    }

    private fun cpuGovernorRoute(
        snapshot: HardwareCapabilitySnapshot,
        policies: List<CpuHardwareBackend.Policy>,
    ): AtlasRouteCandidate? {
        if (policies.isEmpty()) return null
        if (policies.none { it.governor != null || it.governors.isNotEmpty() }) return null
        val writable = snapshot.canWrite(HardwareFeature.CPU_GOVERNOR)
        // A governor is a name, so there is no unit to establish; what must hold is that *every* policy
        // has a readable governor, otherwise a global change cannot be rolled back on the clusters the
        // read did not reach.
        val baselineReadable = policies.all { it.governor != null }
        return candidate(
            id = "cpu.governor.arbiter",
            target = AtlasControlTarget.CPU_GOVERNOR,
            providerId = snapshot.capability(HardwareFeature.CPU_GOVERNOR)?.backend ?: "cpufreq",
            readable = true,
            privilegeAvailable = writable,
            unitProven = true,
            baselineReadable = baselineReadable,
            rollbackProven = baselineReadable && writable,
        )
    }

    private fun gpuFrequencyRoute(
        snapshot: HardwareCapabilitySnapshot,
        gpu: GpuHardwareBackend.GpuObservation?,
    ): AtlasRouteCandidate? {
        // Ambiguity is a fact we know, so it must reach the planner as evidence rather than being
        // dropped: the scanner reports `bestPath == null` when several providers are equally proven, and
        // returning "no route" for that would turn "we cannot tell which GPU this is" into "there is no
        // GPU", which is a different and false claim.
        val ambiguous = gpu?.state == GpuHardwareBackend.GpuObservationState.AMBIGUOUS
        val device = gpu?.bestDevice()
            ?: gpu?.devices?.firstOrNull()?.takeIf { ambiguous }
            ?: return null
        val writable = snapshot.canWrite(HardwareFeature.GPU_FREQUENCY)
        val readable = device.frequencies.isNotEmpty() || device.currentFreq != null
        val baselineReadable = device.frequencies.isNotEmpty() && device.minFreq != null && device.maxFreq != null
        return candidate(
            id = "gpu.frequency.arbiter",
            target = AtlasControlTarget.GPU_FREQUENCY,
            providerId = snapshot.capability(HardwareFeature.GPU_FREQUENCY)?.backend
                ?: "gpu-backend:${device.family.name.lowercase()}",
            readable = readable,
            privilegeAvailable = writable,
            unitProven = device.unitTrusted,
            baselineReadable = baselineReadable,
            rollbackProven = baselineReadable && writable,
            ambiguous = ambiguous,
        )
    }

    private fun gpuGovernorRoute(
        snapshot: HardwareCapabilitySnapshot,
        gpu: GpuHardwareBackend.GpuObservation?,
    ): AtlasRouteCandidate? {
        val ambiguous = gpu?.state == GpuHardwareBackend.GpuObservationState.AMBIGUOUS
        val device = gpu?.bestDevice()
            ?: gpu?.devices?.firstOrNull()?.takeIf { ambiguous }
            ?: return null
        val writable = snapshot.canWrite(HardwareFeature.GPU_GOVERNOR)
        val readable = device.governor != null || device.governors.isNotEmpty()
        if (!readable) return null
        val baselineReadable = device.governor != null
        return candidate(
            id = "gpu.governor.arbiter",
            target = AtlasControlTarget.GPU_GOVERNOR,
            providerId = snapshot.capability(HardwareFeature.GPU_GOVERNOR)?.backend
                ?: "gpu-backend:${device.family.name.lowercase()}",
            readable = true,
            privilegeAvailable = writable,
            unitProven = true,
            baselineReadable = baselineReadable,
            rollbackProven = baselineReadable && writable,
            ambiguous = ambiguous,
        )
    }

    private fun candidate(
        id: String,
        target: AtlasControlTarget,
        providerId: String,
        readable: Boolean,
        privilegeAvailable: Boolean,
        unitProven: Boolean,
        baselineReadable: Boolean,
        rollbackProven: Boolean,
        ambiguous: Boolean = false,
    ) = AtlasRouteCandidate(
        id = id,
        priority = ARBITER_PRIORITY,
        evidence = AtlasRouteEvidence(
            providerId = providerId,
            transport = AtlasControlTransport.ARBITER_SYSFS,
            target = target,
            readable = readable,
            privilegeAvailable = privilegeAvailable,
            unitProven = unitProven,
            baselineReadable = baselineReadable,
            rollbackProven = rollbackProven,
            // Declared here because it is a statement about *this build's* code: the arbiter route below
            // is the one a maintainer reviewed. A route discovered at runtime would not be reviewed.
            reviewed = true,
            ambiguous = ambiguous,
            reason = "arbiter-verified-write",
        ),
    )

    /** The best device the scanner proved, or `null` when it proved none or several. */
    private fun GpuHardwareBackend.GpuObservation.bestDevice(): GpuHardwareBackend.GpuFact? =
        bestPath?.let { path -> devices.firstOrNull { it.path == path } }

    private const val ARBITER_PRIORITY = 10
}
