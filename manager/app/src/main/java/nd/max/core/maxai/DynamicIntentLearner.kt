package nd.max.core.maxai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import nd.max.core.hardware.DeviceStateCollector
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device-aware adaptive strategy layer:
 * 1. Learns user patterns (e.g., "games 3pm-7pm")
 * 2. Learns app-specific preferences
 * 3. Reuses canonical knob credibility for control ordering
 * 4. Adapts objective weights dynamically from observed foreground context
 */
@Singleton
class DynamicIntentLearner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val credibility: CredibilityStore,
) {
    private val stateDir: File by lazy {
        File(context.filesDir, "maxai-intent-learner").apply { mkdirs() }
    }

    private val appPatterns: File by lazy {
        File(stateDir, "app-patterns.json")
    }
    private val timePatterns: File by lazy {
        File(stateDir, "time-patterns.json")
    }
    // In-memory caches
    private val appPreferenceCache = mutableMapOf<String, Float>() // packageName -> performance weight
    private val timePreferenceCache = mutableMapOf<Int, Float>() // hour (0-23) -> performance weight

    init {
        loadFromDisk()
    }

    /**
     * Learn from device state and app context. Should be called every decision cycle.
     */
    fun observe(
        state: DeviceStateCollector.DeviceSnapshot,
        currentApp: String,
        screenOn: Boolean,
    ) {
        // Background work must not be mistaken for user intent. Screen-off
        // samples are handled by the explicit SCREEN_OFF objective instead of
        // teaching the foreground preference model from system activity.
        if (!screenOn) return

        val hourOfDay = java.time.LocalTime.now().hour
        val cpuSignal = state.cpuLoad.coerceIn(0f, 1f)
        val intentSignal = state.appIntent.coerceIn(0f, 1f)
        val observedPerformance = (0.15f + intentSignal * 0.55f + cpuSignal * 0.30f)
            .coerceIn(0.1f, 0.9f)

        // Learn time pattern from actual foreground/game intent plus load,
        // rather than treating high background CPU as proof of user intent.
        timePreferenceCache[hourOfDay] =
            (timePreferenceCache[hourOfDay] ?: 0.5f) * 0.85f + observedPerformance * 0.15f

        // Learn app pattern only for a real foreground context.
        if (currentApp.isNotBlank() && currentApp != "system") {
            appPreferenceCache[currentApp] =
                (appPreferenceCache[currentApp] ?: 0.5f) * 0.9f + observedPerformance * 0.1f
        }

        saveToDisk()
    }

    /**
     * Prioritize controls by:
     * 1. Cost (cheaper first)
     * 2. Canonical execution credibility for this knob/direction/context
     * 3. Whether the knob can express the current direction
     */
    fun prioritizeControls(
        controls: List<ControlRegistry.Control>,
        currentApp: String,
        direction: ControlRegistry.Direction,
    ): List<ControlRegistry.Control> {
        return controls.sortedWith(
            compareBy<ControlRegistry.Control> { it.cost }
                .thenByDescending { control ->
                    // Reuse the canonical execution-credibility store; do not
                    // maintain a second, conflicting success database here.
                    credibility.credibility(control.key, direction, currentApp)
                }
                .thenByDescending { control ->
                    val canMove = control.ladder.firstOrNull()?.let {
                        control.step(it, direction) != null
                    } == true
                    if (canMove) 1 else 0
                }
        )
    }

    /**
     * Generate a dynamic objective based on learned patterns and current state.
     */
    fun dynamicObjective(
        state: DeviceStateCollector.DeviceSnapshot,
        currentApp: String,
        screenOn: Boolean,
        userPreference: Objective = Objective.BALANCED,
    ): Objective {
        if (!screenOn) {
            return Objective.SCREEN_OFF
        }

        val hour = java.time.LocalTime.now().hour
        val timePerf = timePreferenceCache[hour] ?: 0.5f
        val appPerf = appPreferenceCache[currentApp] ?: 0.5f

        // Combine user preference with learned patterns
        val learnedPerf = (timePerf * 0.6f + appPerf * 0.4f)
        val finalPerf = (userPreference.performance * 0.65f + learnedPerf * 0.35f)
            .coerceIn(0.1f, 0.8f)

        // Adjust thermal/battery based on current state
        val thermalBias = if (state.thermal > 0.55f) 0.35f else 0.2f
        val batteryBias = if (state.battery < 0.35f) 0.6f else 0.3f

        val total = finalPerf + batteryBias + thermalBias

        return Objective(
            performance = finalPerf / total,
            battery = batteryBias / total,
            thermalHeadroom = thermalBias / total,
        )
    }

    // --- Persistence helpers ---

    private fun saveToDisk() {
        trySave(appPatterns) {
            JSONObject().apply {
                put("version", 1)
                val array = JSONArray()
                appPreferenceCache.forEach { (pkg, weight) ->
                    array.put(JSONObject().apply {
                        put("package", pkg)
                        put("performance_weight", weight)
                    })
                }
                put("app_patterns", array)
            }
        }

        trySave(timePatterns) {
            JSONObject().apply {
                put("version", 1)
                val array = JSONArray()
                timePreferenceCache.forEach { (hour, weight) ->
                    array.put(JSONObject().apply {
                        put("hour", hour)
                        put("performance_weight", weight)
                    })
                }
                put("time_patterns", array)
            }
        }
    }

    private fun loadFromDisk() {
        tryLoad(appPatterns) { json ->
            val array = json.optJSONArray("app_patterns") ?: return@tryLoad
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val pkg = obj.optString("package").takeIf { it.isNotBlank() } ?: continue
                val weight = obj.optDouble("performance_weight", 0.5).toFloat().coerceIn(0f, 1f)
                appPreferenceCache[pkg] = weight
            }
        }

        tryLoad(timePatterns) { json ->
            val array = json.optJSONArray("time_patterns") ?: return@tryLoad
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val hour = obj.optInt("hour", -1).takeIf { it in 0..23 } ?: continue
                val weight = obj.optDouble("performance_weight", 0.5).toFloat().coerceIn(0f, 1f)
                timePreferenceCache[hour] = weight
            }
        }
    }

    private inline fun trySave(file: File, jsonProducer: () -> JSONObject) {
        try {
            val json = jsonProducer()
            val tmp = File(file.parent, "${file.name}.tmp")
            tmp.outputStream().use { out ->
                out.write(json.toString().toByteArray(StandardCharsets.UTF_8))
                out.flush()
            }
            tmp.renameTo(file)
        } catch (_: Throwable) {}
    }

    private inline fun tryLoad(file: File, jsonConsumer: (JSONObject) -> Unit) {
        try {
            if (!file.exists()) return
            val json = JSONObject(file.readText())
            jsonConsumer(json)
        } catch (_: Throwable) {}
    }
}
