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

package nd.max.ui.util


import kotlinx.serialization.Serializable
import nd.max.MaxManagerPaths
import nd.max.core.hardware.PerAppHardwareStatus
import nd.max.core.hardware.RootFileAccess

/**
 * Compact per-policy CPU control persisted inside [AppConfig]. The format stays
 * flat because AppMonitor reads individual app fields from the existing JSON
 * document without materialising the whole profile.
 */
enum class PerAppCpuControlMode(val token: String) {
    DEFAULT("default"),
    DYNAMIC_RANGE("range"),
    EXACT_LOCK("lock");

    companion object {
        fun fromToken(value: String): PerAppCpuControlMode? = entries.firstOrNull { it.token == value }
    }
}

data class PerAppCpuPolicyControl(
    val policyName: String,
    val mode: PerAppCpuControlMode,
    val minKHz: Long,
    val maxKHz: Long,
)

data class PerAppCpuRuntimeStatus(
    val packageName: String = "",
    val state: String = "idle",
    val message: String = "",
) {
    val isFailure: Boolean get() = state == "failed"
    val isApplied: Boolean get() = state == "applied"
}

fun readPerAppCpuRuntimeStatus(packageName: String?): PerAppCpuRuntimeStatus {
    if (packageName.isNullOrBlank()) return PerAppCpuRuntimeStatus()
    val values = runCatching {
        RootFileAccess.read(MaxManagerPaths.PER_APP_CPU_STATUS)
            .orEmpty()
            .lineSequence()
            .mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 } }
            .associate { it[0] to it[1] }
    }.getOrDefault(emptyMap())
    return if (values["package"] == packageName) {
        PerAppCpuRuntimeStatus(packageName, values["state"].orEmpty().ifBlank { "idle" }, values["message"].orEmpty())
    } else PerAppCpuRuntimeStatus()
}

/**
 * نتيجة مقبض عتاد واحد للقراءة في الواجهة — نفس ما كتبه المحرّك، بلا تفسير مُضاف.
 *
 * والرمز [reason] هو المفتاح: «رُفض» و«غير مدعوم» و«لم يتحقّق» أعطال مختلفة تمامًا، وكل واحد
 * منها له إجراء آخر (حرّر القفل · غيّر الاختيار · أبلغ عن العتاد).
 */
data class PerAppHardwareOutcome(
    val knob: String,
    val outcome: String,
    val reason: String,
    val expected: String,
    val live: String,
) {
    /**
     * ورمز لا نعرفه يُعدّ فشلًا لا نجاحًا: مجهولٌ في قناة تشخيص يجب أن يلفت النظر، لا أن يمرّ
     * سليمًا — وسطر برمز لا نعرفه يعني إصدارًا أحدث كتب شيئًا لم نتعلّم قراءته بعد.
     */
    val isFailure: Boolean
        get() = PerAppHardwareStatus.Outcome.fromToken(outcome)?.let {
            it != PerAppHardwareStatus.Outcome.APPLIED && it != PerAppHardwareStatus.Outcome.SKIPPED
        } ?: true
}

data class PerAppHardwareRuntimeStatus(
    val packageName: String = "",
    val atMs: Long = 0L,
    val outcomes: List<PerAppHardwareOutcome> = emptyList(),
) {
    val failures: List<PerAppHardwareOutcome> get() = outcomes.filter { it.isFailure }
}

/**
 * يقرأ سجل نتائج per-app المكتوب من مراقب الخلفية.
 *
 * ويُشترط تطابق اسم الحزمة: السجل يخصّ التطبيق الذي طُبِّق عليه آخر مرة، وعرض نتيجة تطبيق آخر
 * على هذا التطبيق أسوأ من عدم عرض شيء.
 */
fun readPerAppHardwareRuntimeStatus(packageName: String?): PerAppHardwareRuntimeStatus {
    if (packageName.isNullOrBlank()) return PerAppHardwareRuntimeStatus()
    val snapshot = runCatching { PerAppHardwareStatus.read() }.getOrNull()
        ?: return PerAppHardwareRuntimeStatus()
    if (snapshot.pkg != packageName) return PerAppHardwareRuntimeStatus()
    return PerAppHardwareRuntimeStatus(
        packageName = snapshot.pkg,
        atMs = snapshot.atMs,
        outcomes = snapshot.records.map {
            PerAppHardwareOutcome(it.knob, it.outcome, it.reason, it.expected, it.live)
        },
    )
}

fun decodePerAppCpuPolicyControls(encoded: String): List<PerAppCpuPolicyControl> = encoded
    .split(';')
    .mapNotNull { record ->
        val parts = record.split('|')
        if (parts.size != 4) return@mapNotNull null
        val policy = parts[0].takeIf { it.matches(Regex("(?:policy|cpu)\\d+")) } ?: return@mapNotNull null
        val mode = parts.getOrNull(1)?.let(PerAppCpuControlMode::fromToken) ?: return@mapNotNull null
        val min = parts.getOrNull(2)?.toLongOrNull() ?: return@mapNotNull null
        val max = parts.getOrNull(3)?.toLongOrNull() ?: return@mapNotNull null
        if (mode == PerAppCpuControlMode.DEFAULT || min < 0L || max < min) null
        else PerAppCpuPolicyControl(policy, mode, min, max)
    }
    .distinctBy { it.policyName }

fun encodePerAppCpuPolicyControls(controls: Collection<PerAppCpuPolicyControl>): String = controls
    .filter { it.mode != PerAppCpuControlMode.DEFAULT && it.policyName.matches(Regex("(?:policy|cpu)\\d+")) && it.minKHz >= 0L && it.maxKHz >= it.minKHz }
    .sortedBy { it.policyName }
    .joinToString(";") { "${it.policyName}|${it.mode.token}|${it.minKHz}|${it.maxKHz}" }

@Serializable
data class AppConfig(
    // ── Performance ─────────────────────────────────────────
    val perf_lite_mode: String = "default",
    val app_priority: String = "default",
    val game_preload: String = "default",
    val cpu_boost: String = "default",          // boost CPU clocks on app launch
    val cpu_policy_controls: String = "",        // policy|range/lock|minKHz|maxKHz;...

    // ── Per-App GPU / Governor ────────────────────────────────
    // gpu_profile changes only the GPU frequency ceiling. Default means no GPU override.
    val gpu_profile: String = "default",        // default | power | balanced | gaming | performance
    val cpu_governor: String = "default",       // actual kernel-supported CPU governor
    val gpu_governor: String = "default",       // actual kernel-supported GPU governor
    val gpu_max_freq: String = "default",       // actual value from the GPU available_frequencies node
    // Legacy field kept for JSON compatibility; migrated to gpu_profile by the ViewModel.
    val thermal_profile: String = "default",

    // ── Display & Render ─────────────────────────────────────
    val refresh_rate: String = "default",
    val renderer: String = "default",
    val resolution_downscale: String = "default",

    // ── Experience & Extras ──────────────────────────────────
    val dnd_on_gaming: String = "default",
    val bypass_charging: String = "default",
    val touch_boost: String = "default",        // boost touchscreen responsiveness
    val haptic_feedback: String = "default",    // reduce haptics for better perf
    val kill_bg_apps: String = "default",       // aggressively kill background apps
    val force_hw_ui: String = "default",        // force hardware UI rendering
    val disable_notifs: String = "default",     // mute notifications during session
    val wifi_no_sleep: String = "default",      // keep WiFi active, no sleep
)

/**
 * How many fields in this config are no longer "default" — i.e. how many
 * App Overrides are actually active. Used by the app list ("Customized (N)"
 * status) and by the per-tab badges in AppSettingsScreen. Keeping this in one
 * place means both call sites agree on what "customized" means as new fields
 * get added to AppConfig later.
 */
fun AppConfig.customizedFieldCount(): Int = listOf(
    perf_lite_mode, app_priority, game_preload, cpu_boost,
    gpu_profile, cpu_governor, gpu_governor, gpu_max_freq,
    refresh_rate, renderer, resolution_downscale,
    dnd_on_gaming, bypass_charging, touch_boost, haptic_feedback,
    kill_bg_apps, force_hw_ui, disable_notifs, wifi_no_sleep
).count { it != "default" } + if (decodePerAppCpuPolicyControls(cpu_policy_controls).isNotEmpty()) 1 else 0

/**
 * مقبض سقف GPU: **مالك واحد** لكل تطبيق — والاختياران لا يجتمعان.
 *
 * ولماذا هذا دالّة خالصة لا سطرين داخل `updateSetting`: لأن العطب الذي أوجبتها **مقيس من سجل
 * جهاز حقيقي** ولا يجوز أن يبقى بلا اختبار انحدار. والحالة:
 *
 * ```
 * gpu_profile=performance  gpu_max_freq=650000000   ← إعداد باقٍ من زمن كانت فيه القائمة مقيَّدة
 * PERAPP_KNOB knob=gpu_profile outcome=applied expected=650000000 live=650000000
 * ```
 *
 * فقد كان اختيار البروفايل يكتب `gpu_profile` وحده ويُبقي التردد الصريح، ثم يقدّمه `AppMonitor`
 * على البروفايل — فالبروفايل **يُلغى صامتًا** ويُنفَّذ الرقم القديم. وهو بالحرف: «أختار الأداء
 * فيعطيني ٦٥٠».
 *
 * والقاعدة: من اختار أحد الاثنين فقد ملك المقبض، و`default` في أيّهما تعني «لا شيء مفروض» فيتحرّر
 * الآخر. فالبروفايل نسبة من **قدرة** الجهاز، والتردد الصريح خطوة بعينها؛ واجتماعهما كان يجعل ما تراه
 * في الشاشة غير ما يُنفَّذ. و`thermal_profile` مفتاح قديم يدلّ على الاختيار نفسه (يُرقّى إلى
 * `gpu_profile` عند القراءة) فيتبع الحكم نفسه.
 *
 * وما لا تفعله — عن قصد: لا تُبطل قيمةً لم يطلب المستخدم تبديلها. تمرير المفتاح نفسه بلا تغيير
 * (إعادة اختيار المعروض) يُفرغ الآخر أيضًا، وهذا مقصود: «اخترت هذا الآن» تعني أن هذا هو المطلوب.
 */
fun applyGpuCeilingChoice(config: AppConfig, key: String, value: String): AppConfig = when (key) {
    "gpu_profile" -> config.copy(
        gpu_profile = value,
        thermal_profile = "default",
        gpu_max_freq = "default",
    )
    "gpu_max_freq" -> config.copy(
        gpu_max_freq = value,
        gpu_profile = "default",
        thermal_profile = "default",
    )
    // مُرقّى إلى `gpu_profile` (انظر الـViewModel)، والقيمة تحمل الاسم القديم `powersave`.
    "thermal_profile" -> config.copy(
        gpu_profile = if (value == "powersave") "power" else value,
        thermal_profile = "default",
        gpu_max_freq = "default",
    )
    else -> config
}
