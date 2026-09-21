/*
 * مصادر لوحة «ما يحدث الآن» — الطبقة الوحيدة التي تعرف **من أين** يُقرأ كل سطر.
 *
 * لماذا وُجد هذا الملف
 * --------------------
 * `StoryboardModel` قرّر **كيف يُبنى** السطر، وهو خالص ومُقاس في JVM. وما بقي هو الطرف الآخر:
 * ترجمة المصادر الحقيقية (سجل نتائج العتاد، ملف اختياراتك، أقفالك اليدوية، عبارة التطبيق) إلى
 * أنواع اللوحة. ووضع هذه الترجمة هنا لا في الشاشة مقصود: الشاشة تُركّب، وهذا الملف **يقرأ** —
 * فإن تغيّر مصدر (اسم ملف، صيغة حقل) تبقى الشاشة كما هي.
 *
 * وما لا يُقرأ لا يُخترع
 * ----------------------
 * كل دالة هنا تُعيد `null` أو قائمة فارغة حين لا مصدر: بلا جلسة تطبيق مسجَّلة لا مشهد تطبيق،
 * وبلا أقفال لا مشهد يدوي. وهذا ما يمنع لوحةً تقول «تمّ» لشيء لم يُقس أصلًا — وهو العطب الذي
 * وُلدت اللوحة لتمنعه.
 *
 * والأسماء تبقى كما كتبها المصدر: `perf_lite_mode` أو `applied` لا تُترجم هنا؛ الترجمة للعرض
 * في `StoryboardHome` (جدول واحد)، وهذا الملف ينقل القيمة لا معناها.
 */
package nd.max.ui.util

import android.content.Context
import kotlinx.serialization.json.Json
import nd.max.MaxManagerPaths
import nd.max.core.hardware.ManualControlLocks
import nd.max.core.hardware.PerAppHardwareStatus
import nd.max.core.hardware.RootFileAccess
import nd.max.ui.mainscreens.CpuPolicyChoice
import nd.max.ui.mainscreens.ManualSceneInput
import nd.max.ui.mainscreens.MeasuredOutcome
import nd.max.ui.mainscreens.PerAppSceneInput
import nd.max.ui.mainscreens.StoryboardModel
import nd.max.ui.mainscreens.StoryboardScene

/**
 * قراءة حيّة للوحة. تُنادى من الشاشة الرئيسية بدورة بطيئة (١٠ ثوانٍ) لا كل ثانيتين: ما يعرضه
 * هو **نتيجة اختيار** يتغيّر عند فتح تطبيق أو قفل مقبض، لا قياس لحظي يتحرّك كل نبضة.
 */
object StoryboardSources {

    private val json = Json { ignoreUnknownKeys = true }

    /** المشهدان اللذان لا يملكهما الـViewModel: التطبيق، وتحكّمك اليدوي. */
    fun scenes(context: Context): List<StoryboardScene> =
        listOfNotNull(perAppScene(context), manualScene())

    /**
     * مشهد آخر جلسة تطبيق — من **سجل نتائج العتاد** الذي كتبه مراقب الخلفية.
     *
     * والسجل يبقى بعد الخروج من التطبيق (يُصفَّر عند إقلاع المراقب وحده)، فمن فتح الرئيسية بعد
     * لعبة عليه `gaming` يرى ما وقع فيها — لا ما يظنّه وقع. ووقت القياس من الملف نفسه (`at`)
     * لا ساعة الواجهة.
     */
    fun perAppScene(context: Context): StoryboardScene? {
        val snapshot = runCatching { PerAppHardwareStatus.read() }.getOrNull() ?: return null
        if (snapshot.pkg.isBlank()) return null
        val config = readConfig(snapshot.pkg)
        return StoryboardModel.perAppScene(
            PerAppSceneInput(
                packageName = snapshot.pkg,
                appLabel = appLabel(context, snapshot.pkg),
                // نفس احتياط شاشة إعدادات التطبيق: الإعدادات القديمة حملت `thermal_profile`
                // وحده، فقراءة `gpu_profile` وحدها تُظهر «لا شيء» لاختيارٍ قائم — واللوحة تصير
                // كذبة بسبب رحلة بيانات لا بسبب اختيار.
                profile = config?.gpu_profile?.takeIf { it != "default" }
                    ?: config?.thermal_profile
                    ?: "default",
                cpuPolicies = config?.cpu_policy_controls?.let(::cpuPolicyChoices).orEmpty(),
                killsBackground = config?.kill_bg_apps == "true",
                refreshRate = config?.refresh_rate ?: "default",
                renderer = config?.renderer ?: "default",
                cpuGovernor = config?.cpu_governor ?: "default",
                gpuGovernor = config?.gpu_governor ?: "default",
                gpuMaxFreq = config?.gpu_max_freq ?: "default",
                outcomes = snapshot.records.map { record ->
                    MeasuredOutcome(
                        knob = record.knob,
                        outcome = record.outcome,
                        reason = record.reason,
                        // القيم الخام من العتاد تُعرض بتردّدها المقروء: `1300000000` وحدها
                        // تبدو معرّفًا لا سرعة، والرقم واحد في الحالتين.
                        expected = StoryboardModel.readableValue(record.expected),
                        live = StoryboardModel.readableValue(record.live),
                    )
                },
                measuredAtMs = snapshot.atMs.takeIf { it > 0L },
            ),
        )
    }

    /**
     * مشهد التحكّم اليدوي — من **أقفال المستخدم** الدائمة، لا من ذاكرة الواجهة: قفلٌ بعد إعادة
     * إقلاع التطبيق يبقى مقفلًا على العتاد، فيجب أن يبقى معروضًا هنا («هل نسيتُ أن أطفئه؟»).
     */
    fun manualScene(): StoryboardScene? {
        val locks = runCatching { ManualControlLocks.snapshot() }.getOrDefault(emptyList())
        if (locks.isEmpty()) return null
        return StoryboardModel.manualScene(
            locks.map { lock ->
                ManualSceneInput(
                    knob = lock.key,
                    from = lock.baseline?.takeIf(String::isNotBlank)?.let(StoryboardModel::readableValue),
                    to = StoryboardModel.readableValue(lock.desired),
                    verified = true,
                )
            },
        )
    }

    /** اختياراتك لسياسات CPU: فكّ ترميز ملف الإعدادات إلى أنواع اللوحة، مع اسم العنقود. */
    private fun cpuPolicyChoices(encoded: String): List<CpuPolicyChoice> {
        val controls = decodePerAppCpuPolicyControls(encoded)
        if (controls.isEmpty()) return emptyList()
        // أسماء العناقيد تُقرأ فقط حين توجد سياسات مضبوطة، فلا تُدفع كلفة اكتشاف العناقيد في
        // الحالة الشائعة (تطبيق بلا ضبط CPU).
        val tags = runCatching {
            CpuTopologyUtil.detectClusters().associate { cluster ->
                cluster.policyPath.substringAfterLast('/') to cluster.shortTag
            }
        }.getOrDefault(emptyMap())
        return controls.map { control ->
            CpuPolicyChoice(
                policyName = control.policyName,
                label = tags[control.policyName] ?: control.policyName.uppercase(),
                minKHz = control.minKHz,
                maxKHz = control.maxKHz,
            )
        }
    }

    /** إعدادات تطبيق واحد من ملف اختياراتك؛ `null` حين لا مدخل له (تطبيق غير مُدار). */
    private fun readConfig(packageName: String): AppConfig? = try {
        // نفس قراءة شاشة قائمة التطبيقات: مصدر واحد للإعدادات، فلا تفسيران مختلفان للملف.
        val content = RootFileAccess.read(MaxManagerPaths.APPLIST_JSON)
        if (content.isNullOrBlank()) null
        else json.decodeFromString<Map<String, AppConfig>>(content)[packageName]
    } catch (_: Exception) {
        // ملف تالف أو حزمة أُزيلت: لا مشهد تطبيق، بلا إسقاط الشاشة.
        null
    }

    /**
     * اسم التطبيق كما يعرضه المشغّل. وعند تعذّره (حزمة أُزيلت بعد جلسة قديمة) يبقى اسم الحزمة:
     * إظهار `com.x.y` أوضح من إظهار سطر بلا اسم.
     */
    private fun appLabel(context: Context, packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
    }.getOrDefault(packageName)
}
