package nd.max.ui.util

import android.content.Context

/** Persistent per-app thermal/GPU preset tuning. Values are percentages of the
 * device's detected stock GPU maximum; the actual OPP is selected at runtime.
 */
object ProfilePresetStore {
    private const val PREFS = "per_app_profile_presets"
    private const val POWER = "power"
    private const val BALANCED = "balanced"
    private const val GAMING = "gaming"
    private const val PERFORMANCE = "performance"
    private const val CUSTOM = "custom"

    private const val DEFAULT_POWER = 65
    private const val DEFAULT_BALANCED = 70
    private const val DEFAULT_GAMING = 85
    private const val DEFAULT_PERFORMANCE = 100
    private const val DEFAULT_CUSTOM = 55

    @Volatile
    private var packageContext: Context? = null

    /**
     * AppMonitor is launched through app_process and receives Android's framework
     * system Context (packageName=android). SharedPreferences must instead be
     * opened through MaxManager's own package Context, otherwise Android 16 can
     * resolve the data directory as /data/.../android and throw:
     * "No data directory found for package android".
     *
     * UI callers already provide an nd.max Context, so this is a no-op there.
     */
    private fun prefs(context: Context): android.content.SharedPreferences {
        val resolved = packageContext?.takeIf { it.packageName == "nd.max" }
            ?: if (context.packageName == "nd.max") {
                context.applicationContext
            } else {
                runCatching {
                    context.createPackageContext("nd.max", Context.CONTEXT_IGNORE_SECURITY)
                }.getOrNull()
            }
            ?: context

        if (resolved.packageName == "nd.max") packageContext = resolved.applicationContext
        return resolved.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun percentFor(context: Context?, profile: String): Int {
        if (context == null) return defaultPercent(profile)
        return runCatching {
            prefs(context).getInt(profile.lowercase(), defaultPercent(profile)).coerceIn(20, 100)
        }.getOrDefault(defaultPercent(profile))
    }

    fun setPercent(context: Context, profile: String, percent: Int) {
        prefs(context).edit().putInt(profile.lowercase(), percent.coerceIn(20, 100)).apply()
    }

    fun reset(context: Context) {
        prefs(context).edit()
            .putInt(POWER, DEFAULT_POWER)
            .putInt(BALANCED, DEFAULT_BALANCED)
            .putInt(GAMING, DEFAULT_GAMING)
            .putInt(PERFORMANCE, DEFAULT_PERFORMANCE)
            .putInt(CUSTOM, DEFAULT_CUSTOM)
            .apply()
    }

    fun defaultPercent(profile: String): Int = when (profile.lowercase()) {
        POWER -> DEFAULT_POWER
        BALANCED -> DEFAULT_BALANCED
        GAMING -> DEFAULT_GAMING
        PERFORMANCE -> DEFAULT_PERFORMANCE
        CUSTOM -> DEFAULT_CUSTOM
        else -> 100
    }
}
