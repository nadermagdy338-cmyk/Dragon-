/*
 * The Control page's layout model.
 *
 * The page has two presentations of the same content — a compact grouped list and
 * an expanded page that opens every screen out — and the requirement on it is
 * that they can never drift: adding or removing a destination updates both, with
 * nothing to remember twice.
 *
 * That is why this file exists: both layouts read this one model, and the model
 * itself is derived from [MaxDestination] (the registry) plus
 * [maxDestinationRole] (the role table). Only the editorial part — which hubs
 * belong to which band of the page, and in what order — is written down here.
 *
 * Pure Kotlin on purpose: no Compose imports, so the sync contract is testable in
 * a plain JVM unit test (see ControlLayoutModelTest).
 */
package nd.max.ui.mainscreens

import androidx.annotation.StringRes
import nd.max.R
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.navigation.maxDestinationRole
import nd.max.ui.navigation.maxHubDescription
import nd.max.ui.navigation.maxHubRows

/** A destination plus the one line that explains it on this page. */
data class ControlEntry(val destination: MaxDestination, @StringRes val subtitleRes: Int)

/** A domain hub together with every screen it owns. */
data class ControlHubSpec(
    val hub: MaxDestination,
    @StringRes val hubSubtitleRes: Int,
    val features: List<ControlEntry>,
)

/** One titled band of the page. */
data class ControlGroupSpec(
    val key: String,
    @StringRes val titleRes: Int,
    val hubs: List<ControlHubSpec>,
)

/**
 * The gated toolbox: not a domain, so it is not part of the bands above.
 *
 * `Max Backup` و`AppOps` انتقلا إلى هنا من شاشة التطبيقات بقرار مالك: كلاهما أداة تفتح
 * على **كل** التطبيقات ولها شاشة رئيسية مستقلة، فلا معنى لعرضها داخل شاشة تطبيق واحد.
 * والترتيب مقصود: الأدوات التي تُفتح بلا نيّة سابقة (نسخ · صلاحيات) قبل أدوات النظام.
 *
 * ومراقب المهام ووحدة السجل وطرفية الأوامر انتقلت إلى هنا من **مسار التشخيص في الإعدادات**:
 * الأدوات تُطلب من مكان واحد هو شاشة التحكّم، والتنقل إليها كان يشترط أن يُعرف أنها تحت الإعدادات
 * أو تحت التشخيص — وهذا شرط لا معنى له من جهة المستخدم (الأربع كلها تقرأ وتُظهر، ولا تكتب سقفًا
 * ولا سياسة عتاد).
 *
 * وأما **السمة** فقد عادت إلى الإعدادات بأمر مالك: صارت صفًّا فوق بطاقة اللغة، لأن من يغيّر
 * السمة يبحث عنها في الإعدادات لا في صندوق الأدوات. ومخطّط الألوان بقي هنا — مختلف عنه:
 * ذاك يسأل «أي ألوان تُشتقّ من بذرة»، وهذا يسأل «ما السمة نفسها».
 */
val ControlToolDestinations: List<MaxDestination> = listOf(
    MaxDestination.MaxBackup,
    MaxDestination.Permissions,
    MaxDestination.FileManager,
    MaxDestination.ProcessManager,
    MaxDestination.Logs,
    MaxDestination.ColorScheme,
    MaxDestination.SetEdit,
    MaxDestination.ActivityLauncher,
    // الأخطف آخرًا: لا يُفتح بلمسة عابرة.
)

/** Which hubs belong to which band, and in what order. Editorial, nothing more. */
private class ControlBand(@StringRes val titleRes: Int, val hubs: List<MaxDestination>)

private val ControlBands = listOf(
    ControlBand(
        R.string.control_group_performance,
        listOf(
            MaxDestination.CpuHub,
            MaxDestination.GpuHub,
            MaxDestination.MemoryHub,
            MaxDestination.ResponsivenessHub,
        ),
    ),
    ControlBand(
        R.string.control_group_environment,
        listOf(
            MaxDestination.ThermalHub,
            MaxDestination.PowerHub,
            MaxDestination.DisplayHub,
        ),
    ),
    ControlBand(
        R.string.control_group_system,
        listOf(
            MaxDestination.StorageHub,
            MaxDestination.NetworkHub,
        ),
    ),
)

/**
 * The single description of the page, consumed by both layouts.
 *
 * A band with no hubs left is dropped rather than rendered empty, so removing the
 * last hub of a band reads as a layout change and not as a blank section.
 */
fun controlLayoutModel(): List<ControlGroupSpec> = ControlBands.mapNotNull { band ->
    val hubs = band.hubs.map { hub ->
        ControlHubSpec(
            hub = hub,
            hubSubtitleRes = maxHubDescription(hub),
            features = maxHubRows(hub).map { feature -> ControlEntry(feature, maxDestinationRole(feature)) },
        )
    }
    ControlGroupSpec(
        key = hubKey(band.titleRes),
        titleRes = band.titleRes,
        hubs = hubs,
    ).takeIf { hubs.isNotEmpty() }
}

/** Rows of the advanced-tools band, from the same role table as everything else. */
fun controlToolEntries(): List<ControlEntry> =
    ControlToolDestinations.map { destination -> ControlEntry(destination, maxDestinationRole(destination)) }

/**
 * Stable list key: the band's own title resource, so reordering bands does not
 * recycle the wrong item state in either layout.
 */
private fun hubKey(@StringRes titleRes: Int): String = "control_band_$titleRes"
