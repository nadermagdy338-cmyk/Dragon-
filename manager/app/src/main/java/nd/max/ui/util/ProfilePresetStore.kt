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

    /**
     * ١٠٠٪ تعني **بلا سقف** لا «سقف كامل» (`ThermalCurve.cappingPercent` تُرجع `null` عندها،
     * فلا يُكتب سقف على CPU ولا GPU).
     *
     * وكان ٨٥ هنا، أي أن اختيار «Gaming» يترك الجهاز **أدنى من الحالة الافتراضية** التي لا تسقّف
     * شيئًا. وهذا يقرأه صاحبه كما هو: «اخترت الأداء للعبة فصار أضعف من غير اختيار». فصار المنحنى
     * كلّه بلا سقف عند `gaming`؛ والفرق بينه وبين `performance` يبقى في سلوك اللعب نفسه (DND،
     * preload، الأولوية…) لا في اقتطاع قدرة العتاد.
     */
    private const val DEFAULT_GAMING = 100

    private const val DEFAULT_PERFORMANCE = 100
    private const val DEFAULT_CUSTOM = 55

    /**
     * البذرة القديمة لـ`gaming` (٨٥٪) — تُهاجَر بقيمتها عند القراءة.
     *
     * ولماذا لزم هذا: القيمة تُقرأ من تخزين المستخدم لا من الثابت، فجهاز فتح المحرّر مرّة واحدة
     * (أو أعاد الإعدادات) يحمل ٨٥ محفوظة، ولو غيّرنا الثابت وحده لقرأ غيرنا… ولم يتغيّر شيء على جهازه.
     * ويُعامل «القيمة تساوي البذرة» كما يُعامل في `ProfileSharing`: مشتقّة من البذرة لا مضبوطة بيد
     * (فهي تحسم المصدر بنفس المقارنة). ومن ضبط ٨٥ بنفسه فقد صارت هي القيمة الجديدة ١٠٠ له — وهي
     * الحالة التي رُفضت أصلًا، فلا معنى للإبقاء عليها في أي جهاز.
     */
    private const val LEGACY_GAMING_SEED = 85

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
            val stored = prefs(context).getInt(profile.lowercase(), defaultPercent(profile))
            // ترحيل البذرة القديمة: جهاز حفظ ٨٥ قبل هذا الإصدار يقرأ ١٠٠ بدلًا منها بلا أي خطوة من
            // المستخدم (وإلا لبقي «Gaming» أدنى من الافتراضي على كل جهاز سبق أن فتح المحرّر).
            val migrated = if (profile.equals(GAMING, true) && stored == LEGACY_GAMING_SEED) {
                DEFAULT_GAMING
            } else {
                stored
            }
            migrated.coerceIn(20, 100)
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
