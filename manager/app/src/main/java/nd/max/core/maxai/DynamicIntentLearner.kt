package nd.max.core.maxai

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nd.max.core.hardware.DeviceStateCollector
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Device-aware adaptive strategy layer:
 * 1. Learns user patterns (e.g., "games 3pm-7pm")
 * 2. Learns app-specific preferences
 * 3. Prioritizes knobs that actually work (credibility)
 * 4. Adapts objective weights dynamically
 */
@Singleton
class DynamicIntentLearner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val credibility: CredibilityStore,
) {
    private val mutex = Mutex()
    private val stateDir: File by lazy {
        File(context.filesDir, "maxai-intent-learner").apply { mkdirs() }
    }

    private val appPatterns: File by lazy {
        File(stateDir, "app-patterns.json")
    }
    private val timePatterns: File by lazy {
        File(stateDir, "time-patterns.json")
    }
    private val knobPriorities: File by lazy {
        File(stateDir, "knob-priorities.json")
    }

    // In-memory caches
    private val appPreferenceCache = mutableMapOf<String, Float>() // packageName -> performance weight
    private val timePreferenceCache = mutableMapOf<Int, Float>() // hour (0-23) -> performance weight
    private val knobSuccessCache = mutableMapOf<String, Float>() // knobKey -> success rate

    // Screen off tracking
    private var lastScreenOffTime: Long = 0L

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
        val hourOfDay = java.time.LocalTime.now().hour

        // Learn time pattern
        val timePerfWeight = if (state.cpuLoad > 0.65f) 0.65f else 0.3f
        timePreferenceCache[hourOfDay] = (timePreferenceCache[hourOfDay] ?: 0.5f) * 0.85f + timePerfWeight * 0.15f

        // Learn app pattern
        if (currentApp.isNotBlank()) {
            val appPerfWeight = if (state.cpuLoad > 0.7f) 0.75f else 0.3f
            appPreferenceCache[currentApp] = (appPreferenceCache[currentApp] ?: 0.5f) * 0.9f + appPerfWeight * 0.1f
        }

        // Track screen off for special handling
        if (!screenOn) {
            lastScreenOffTime = System.currentTimeMillis()
        }

        saveToDisk()
    }

    /**
     * Prioritize controls by:
     * 1. Credibility (did they work before?)
     * 2. Cost (cheaper first)
     * 3. App-specific patterns
     */
    fun prioritizeControls(
        controls: List<ControlRegistry.Control>,
        currentApp: String,
    ): List<ControlRegistry.Control> {
        val hour = java.time.LocalTime.now().hour
        val timeWeight = timePreferenceCache[hour] ?: 0.5f
        val appWeight = appPreferenceCache[currentApp] ?: 0.5f
        val combinedPerfBias = (timeWeight * 0.6f + appWeight * 0.4f)

        return controls.sortedWith(
            compareBy<ControlRegistry.Control> { control ->
                // Lowest cost first
                control.cost
            }.thenByDescending { control ->
                // Highest credibility first
                knobSuccessCache[control.key] ?: 0.5f
            }.thenByDescending { control ->
                // Prefer performance knobs if perf bias is high, energy otherwise
                val direction = if (combinedPerfBias > 0.55f)
                    ControlRegistry.Direction.RAISE_PERFORMANCE
                else
                    ControlRegistry.Direction.SAVE_ENERGY
                val stepAway = control.ladder.size / 2
                if (control.step(control.ladder.first(), direction) != null) 1 else 0
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

    /**
     * Record a knob attempt (success/failure) to update credibility.
     */
    fun recordKnobAttempt(
        knobKey: String,
        successful: Boolean,
        verified: Boolean,
    ) {
        val currentSuccess = knobSuccessCache[knobKey] ?: 0.5f
        val update = if (verified && successful) 0.1f else -0.05f
        knobSuccessCache[knobKey] = (currentSuccess + update).coerceIn(0.05f, 0.95f)
        saveToDisk()
    }

    /**
     * Return success rate for a given knob (0.0-1.0).
     */
    fun knobSuccessRate(knobKey: String): Float = knobSuccessCache[knobKey] ?: 0.5f

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

        trySave(knobPriorities) {
            JSONObject().apply {
                put("version", 1)
                val array = JSONArray()
                knobSuccessCache.forEach { (key, rate) ->
                    array.put(JSONObject().apply {
                        put("knob_key", key)
                        put("success_rate", rate)
                    })
                }
                put("knob_priorities", array)
            }
        }
    }

    private fun loadFromDisk() {
        tryLoad(appPatterns) { json ->
            val array = json.optJSONArray("app_patterns") ?: return@tryLoad
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val pkg = obj.optString("package") ?: continue
                val weight = obj.optDouble("performance_weight", 0.5).toFloat()
                appPreferenceCache[pkg] = weight
            }
        }

        tryLoad(timePatterns) { json ->
            val array = json.optJSONArray("time_patterns") ?: return@tryLoad
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val hour = obj.optInt("hour", -1).takeIf { it in 0..23 } ?: continue
                val weight = obj.optDouble("performance_weight", 0.5).toFloat()
                timePreferenceCache[hour] = weight
            }
        }

        tryLoad(knobPriorities) { json ->
            val array = json.optJSONArray("knob_priorities") ?: return@tryLoad
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val key = obj.optString("knob_key") ?: continue
                val rate = obj.optDouble("success_rate", 0.5).toFloat()
                knobSuccessCache[key] = rate
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
