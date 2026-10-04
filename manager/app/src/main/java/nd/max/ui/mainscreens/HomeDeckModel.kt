/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * «منصة التحكم» في الرئيسية — **قاعدة الاختيار وحدها**، بلا رسم ولا تخزين.
 *
 * وفكرة المالك بنصّه: «في الشاشة الرئيسية عناصر التحكم هنخليها ٤ خيارات ثابتين لو لسه مستخدم
 * جديد، ونضيف زرّ إعداد في نفس البطاقة: خيار بيضع أكثر ما يستخدمه المستخدم تلقائيًّا بشكل زكي،
 * وخيار يدوي يختار فيه الي عايزه — إلى ٦ خيارات أقصى شيء، والافتراضي ٤ وتلقائي. بنعمل كل ده
 * علشان نوفّر عليه وقت البحث: يظهره الي بيستخدمه أكثر حاجة، أو الي هو بيفضّله يدويًّا».
 *
 * ⇒ وثلاثة أرقام وثلاث قواعد هي كل هذا الملفّ:
 *
 * 1. **٤ هو الافتراضيّ، و٢ الأدنى، و٦ الأقصى.** والأربعة الافتراضية هي **البطاقات الأربع القائمة
 *    اليوم** بنصّها ونبرتها (العرض · الحرارة · الطاقة · تحكّم متقدّم) — فالمستخدم الجديد لا يرى
 *    تغييرًا في أوّل تشغيل، وهو عين ما طلبه المالك («٤ خيارات ثابتين لو لسه مستخدم جديد»).
 *    **ونزل الأدنى من ٤ إلى ٢ بأمر المالك** («الافتراضي ٤ … والحد الأدنى ٢ بدل ٤»)، فصار ممكنًا
 *    أن يرى المستخدم بطاقتين. **والافتراضيّ لم ينزل معه، وهذا مربط الفرس:** كان الرقم نفسه
 *    (`HOME_DECK_MIN`) يخدم الغرضين، فلو حُرّك لتغيّر الاثنان معًا — أي نزل الافتراضيّ إلى اثنتين
 *    صامتًا. ففُصلا رقمين مسمّيين: [HOME_DECK_DEFAULT] للافتراضيّ و[HOME_DECK_MIN] للأدنى.
 * 2. **والترتيب في التلقائي بالأكثر استعمالًا**، وعدد البطاقات = عدد ما استعمله فعلًا محصورًا
 *    بين ٢ و٦: من لم يستعمل شيئًا يرى **٤** (الافتراضيّ لا الأدنى)، ومن استعمل شاشةً واحدة يرى
 *    **٢** (المستعملة ثم تاليةٌ بترتيب البركة)، ومن استعمل ثماني شاشات يرى ٦ (الأكثر)، ولا تُعرض
 *    سبع بطاقات لأن السقف ستة.
 * 3. **واليدويّ لا ينزل تحت ٢ ولا يعلو على ٦** — والقاعدة تُفرض هنا لا في الشاشة فقط، فحفظٌ
 *    قديم أو ناقص من نسخة سابقة يُكمَّل بالافتراضيّ بدل أن يُعرض ناقصًا أو يرمي.
 *
 * **والعدّ محليّ على الجهاز فقط**: عددُ فتحات شاشة في هذا الهاتف، في `SharedPreferences`، ولا
 * يخرج منه ولا يُقرأ من الشبكة. وهو **ليس** قياسًا مُصنَّعًا (ADR-07 يمنع ادّعاء حالة عتاد لم
 * تُقرأ): العدد هنا **مقيس فعلًا** لأننا نحن من سجّله عند فتح الشاشة.
 *
 * **والجولة ٢٠٣ وحّدت العدّاد:** كان للمنصة عدّادها الخاص (`home_deck_uses_*`) وللمُوجِّد لا
 * عدّاد، فسجلّان يقيسان الفتح نفسه يفترقان يومًا فيقول أحدهما «الأكثر استعمالًا» ويقول الآخر
 * غيره. صار العدّ الواحد **بالمسار** في `ScreenUsageStore`، و[homeDeckUsage] يحوّل المسارات إلى
 * مفاتيح البطاقات — فالمنصة والمُوجِّد يقرآن العدد نفسه بحرفيّته.
 */
package nd.max.ui.mainscreens

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import nd.max.R
import nd.max.ui.design.MaxTone
import nd.max.ui.navigation.MaxDestination

/** بطاقة واحدة في «منصة التحكم». */
data class HomeDeckEntry(
    /** الثابت في التخزين: لا يتغيّر بتغيّر نصّ ولا مسار (المسار للإطلاق، والمفتاح للعدّ). */
    val key: String,
    val destination: MaxDestination,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: ImageVector,
    val tone: MaxTone,
)

/**
 * **الافتراضيّ بطلب المالك: ٤** — عدد البطاقات التي يراها المستخدم الجديد، ومن لم يُسجَّل له
 * فتحٌ بعد.
 *
 * **وهو ليس الأدنى، ولا يُشتقّ منه:** كان الرقم واحدًا (`HOME_DECK_MIN` = ٤ يخدم الغرضين)،
 * ولو بقي الافتراضيّ مشتقًّا من الأدنى لنزل إلى ٢ صامتًا في اللحظة التي نزل فيها الأدنى —
 * أي أن أمرًا واحدًا (الأدنى ٢) كان سيغيّر شيئين بلا أن يُقال. ولهذا فُصل رقمٌ مسمًّى لكل
 * غرض، ومعه اختبار يقيس العددين منفصلين.
 */
const val HOME_DECK_DEFAULT = 4

/**
 * **الأدنى بطلب المالك: ٢** («الافتراضي ٤ … والحد الأدنى ٢ بدل ٤») — في اليدويّ لا يُطفأ ما
 * دون البطاقتين، وفي التلقائيّ لا تُعرض بطاقة واحدة أبدًا.
 */
const val HOME_DECK_MIN = 2

/** الأقصى بطلب المالك («إلى ٦ خيارات أقصى شيء»). */
const val HOME_DECK_MAX = 6

/** وضع الاختيار — والتلقائيّ هو الافتراضيّ بطلب المالك. */
enum class HomeDeckMode { Auto, Manual }

/**
 * البركة التي يُختار منها — **والأربعة الأولى هي القائمة اليوم**، فالافتراضيّ ليس مفهومًا
 * جديدًا بل الحالة القائمة، وما بعده بذورٌ تُظهرها الاستعمالات لا الأذواق.
 */
val HomeDeckPool: List<HomeDeckEntry> = listOf(
    HomeDeckEntry(
        key = "display",
        destination = MaxDestination.DisplayStudio,
        titleRes = R.string.display_studio_title,
        descriptionRes = R.string.max_hub_display_desc,
        icon = Icons.Rounded.DisplaySettings,
        tone = MaxTone.Accent,
    ),
    HomeDeckEntry(
        key = "thermal",
        destination = MaxDestination.ThermalDetail,
        titleRes = R.string.home_action_thermal,
        descriptionRes = R.string.home_action_thermal_desc,
        icon = Icons.Rounded.Thermostat,
        tone = MaxTone.Caution,
    ),
    HomeDeckEntry(
        key = "battery",
        destination = MaxDestination.Charging,
        titleRes = R.string.home_action_battery,
        descriptionRes = R.string.home_action_battery_desc,
        icon = Icons.Rounded.BatteryChargingFull,
        tone = MaxTone.Positive,
    ),
    HomeDeckEntry(
        key = "control",
        destination = MaxDestination.Control,
        titleRes = R.string.home_action_advanced,
        descriptionRes = R.string.home_action_advanced_desc,
        icon = Icons.Rounded.Tune,
        tone = MaxTone.Neutral,
    ),
    HomeDeckEntry(
        key = "zram",
        destination = MaxDestination.ZramManager,
        titleRes = R.string.zram_title,
        descriptionRes = R.string.max_role_zram,
        icon = Icons.Rounded.Storage,
        tone = MaxTone.Accent,
    ),
    HomeDeckEntry(
        key = "storage",
        destination = MaxDestination.StorageDetail,
        titleRes = R.string.detail_storage,
        descriptionRes = R.string.max_role_storage,
        icon = Icons.Rounded.DataUsage,
        tone = MaxTone.Neutral,
    ),
    HomeDeckEntry(
        key = "network",
        destination = MaxDestination.NetworkDetail,
        titleRes = R.string.detail_network,
        descriptionRes = R.string.max_role_network_detail,
        icon = Icons.Rounded.Wifi,
        tone = MaxTone.Accent,
    ),
    HomeDeckEntry(
        key = "cpu",
        destination = MaxDestination.CpuCoreControl,
        titleRes = R.string.cpu_core_control_title,
        descriptionRes = R.string.max_role_cpu_core,
        icon = Icons.Rounded.DeveloperBoard,
        tone = MaxTone.Positive,
    ),
    HomeDeckEntry(
        key = "gpu",
        destination = MaxDestination.GpuStudio,
        titleRes = R.string.max_title_gpu_studio,
        descriptionRes = R.string.max_role_gpu_studio,
        icon = Icons.Rounded.Speed,
        tone = MaxTone.Accent,
    ),
    HomeDeckEntry(
        key = "device",
        destination = MaxDestination.DeviceInfo,
        titleRes = R.string.device_info,
        descriptionRes = R.string.max_role_device_info,
        icon = Icons.Rounded.Info,
        tone = MaxTone.Neutral,
    ),
)

/** مفاتيح المستخدم الجديد — الأربعة الأولى بالترتيب نفسه (الافتراضيّ لا الأدنى). */
val HomeDeckDefaultKeys: List<String> = HomeDeckPool.take(HOME_DECK_DEFAULT).map { it.key }

/** مفتاح البطاقة من مسار الشاشة — للعدّاد عند فتح شاشة من أي مكان في التطبيق. */
fun homeDeckKeyOfRoute(route: String?): String? =
    route?.let { path -> HomeDeckPool.firstOrNull { it.destination.route == path }?.key }

/**
 * عدّادات المنصة من السجلّ الواحد: سجلُّ الفتحات **بالمسار** ⇒ عدّادات **بمفتاح البطاقة**.
 *
 * وكل مفتاح في البركة له قيمة (وصفرًا إن لم يُفتح) — فالغائب في الخريطة لا يفرّق بين «لم
 * يُفتح» و«غير مسجَّل»، والقاعدة في [homeDeckSelection] تتعامل مع الصفر لا مع الغياب. وما في
 * السجلّ من مسارات خارج البركة يُتجاهل: بطاقةً لم تُعرَض لا يُعرض لها عدّاد.
 */
fun homeDeckUsage(counts: Map<String, Int>): Map<String, Int> = HomeDeckPool.associate { entry ->
    entry.key to (counts[entry.destination.route] ?: 0)
}

/** البطاقة من مفتاحها؛ ومفتاح لا وجود له يُهمَل ولا يرمي. */
fun homeDeckEntry(key: String): HomeDeckEntry? = HomeDeckPool.firstOrNull { it.key == key }

/**
 * البطاقات التي تُعرض الآن — القاعدة الواحدة التي تحكم الوضعين.
 *
 * @param usage عدد فتحات كل مفتاح كما سُجّلت على الجهاز (مفتاح غائب = صفر).
 */
fun homeDeckSelection(
    mode: HomeDeckMode,
    manualKeys: List<String>,
    usage: Map<String, Int>,
): List<HomeDeckEntry> = when (mode) {
    HomeDeckMode.Manual -> {
        // **وبترتيب البركة لا بترتيب النقر:** من أطفأ بطاقة ثم أشعلها لا يراها تنتقل إلى الآخر،
        // واختيارٌ يُعاد ترتيبه مع كل لمسة يُقرأ كعطب لا كاختيار. والمفتاح المجهول يُهمَل هنا
        // (فلترة لا خريطة)، فلا ترمي الشاشة بسبب حالة محفوظة من نسخة أقدم.
        val picked = HomeDeckPool.filter { it.key in manualKeys }
        if (picked.size >= HOME_DECK_MIN) {
            picked.take(HOME_DECK_MAX)
        } else {
            // ونقصٌ عن الأدنى (‏٢) يُكمَّل بالافتراضيّ بترتيبه: لا تُعرض منصة ببطاقة واحدة، ولا
            // يُفرض على المستخدم اختيار لم يُحفظ بعد. (ومن اختار الاثنتين حدَّهما يُعرض له
            // اختياره كما هو — `picked.size >= HOME_DECK_MIN` أعلاه.)
            (picked + HomeDeckDefaultKeys.mapNotNull(::homeDeckEntry))
                .distinct()
                .take(HOME_DECK_MAX)
        }
    }

    HomeDeckMode.Auto -> {
        // **والعدد يتبع ما استُعمل فعلًا، إلا صفرًا:** من لم يستعمل شيئًا يرى الافتراضيّ ٤ (وهو
        // أوّل تشغيل كما كان)، ومن استعمل واحدًا أو اثنين يرى اثنتين لا أربعًا — لأن الأدنى صار
        // ٢ بأمر المالك — وسقفه ٦. ولو كان `used` صفرًا داخل `coerceIn` لعرض على المستخدم الجديد
        // بطاقتين، وهو عكس «الافتراضي ٤» نصًّا: فالافتراضيّ **حالة الصفر** لا حدّ الحصر.
        val used = HomeDeckPool.count { (usage[it.key] ?: 0) > 0 }
        val size = if (used == 0) {
            HOME_DECK_DEFAULT
        } else {
            used.coerceIn(HOME_DECK_MIN, HOME_DECK_MAX)
        }
        HomeDeckPool
            .withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<HomeDeckEntry>> { usage[it.value.key] ?: 0 }
                    // والتعادل بترتيب البركة نفسه: نتيجةٌ تتغيّر بترتيب لا يراه المستخدم عطبٌ
                    // في نفسه، ولو كانت البطاقات هي هي.
                    .thenBy { it.index }
            )
            .take(size)
            .map { it.value }
    }
}
