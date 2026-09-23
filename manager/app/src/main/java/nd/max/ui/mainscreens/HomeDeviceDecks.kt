/**
 * ألواح الرئيسية السفلية — الحرارة، وتفاصيل الجهاز، وطاولة الأوامر.
 *
 * فُصلت عن `MaxHomeDashboard.kt` لأن الملف تجاوز سقف المستودع (١٠٠٠ سطر) بالتقسيم الجديد،
 * والفصل **بالمسؤولية** لا بالحجم: الملف الأصلي يحكي **من أنت ومن يتحكّم** (الهوية، وحالات
 * الخطر، وسلّم الملفات، وشريط الذكاء)، وهذا الملف يحكي **ما يقيسه العتاد وما يمكن فتحه**.
 *
 * وكل دوال هذا الملف `internal` لا `private`: الشاشة الأمّ في الملف الآخر تستدعيها، والخصائص
 * الواحدة (نفس `neuralPalette` ونفس `temperatureAccent`) تعني أن الفصل لا يُنتج مظهرين.
 */
package nd.max.ui.mainscreens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import nd.max.R
import nd.max.ui.component.NeuralActionTile
import nd.max.ui.component.NeuralAreaPlot
import nd.max.ui.component.NeuralCategoryRow
import nd.max.ui.component.NeuralDataRow
import nd.max.ui.component.NeuralDivider
import nd.max.ui.component.NeuralFactTile
import nd.max.ui.component.NeuralPanel
import nd.max.ui.component.NeuralPill
import nd.max.ui.component.NeuralReadoutTile
import nd.max.ui.component.NeuralSectionHeader
import nd.max.ui.component.NeuralStatCard
import nd.max.ui.component.neuralPalette
import nd.max.ui.viewmodel.DashboardState

/**
 * الحرارة — أربعة مجسّات، ولون الخطر قبل الرقم.
 *
 * ولا رقم واحد «للحرارة العامة»: جهاز قد يكون غلافه هادئًا وبطاريته ساخنة، والاثنان
 * يقودان إلى إجراء مختلف. والغلاف (`Skin`) هو ما يلمسه المستخدم فعلًا، ولهذا يُعرض إلى
 * جانب ما يقيسه المحرّك.
 */
@Composable
internal fun ThermalPanel(dashboard: DashboardState, onThermal: () -> Unit) {
    val p = neuralPalette()
    val headline = dashboard.batteryTempC.takeIf { it > 0f }?.roundToInt()
        ?: dashboard.cpuTempC.takeIf { it > 0 }
    val headlineAccent = temperatureAccent(headline)
    val calm = headline == null || headline < 43

    NeuralPanel(accent = headlineAccent, onClick = onThermal, verticalSpacing = 12.dp) {
        NeuralSectionHeader(
            title = stringResource(R.string.home_system_vitals),
            caption = stringResource(R.string.max_home_thermal_desc),
            accent = headlineAccent,
            trailing = {
                NeuralPill(
                    text = stringResource(
                        if (calm) R.string.home_system_stable else R.string.home_system_attention
                    ),
                    accent = if (calm) p.ok else headlineAccent,
                    dot = true,
                )
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThermalTile(
                caption = stringResource(R.string.home_cpu_tag),
                value = dashboard.cpuTempC,
                modifier = Modifier.weight(1f)
            )
            ThermalTile(
                caption = stringResource(R.string.home_gpu_tag),
                value = dashboard.gpuTempC,
                modifier = Modifier.weight(1f)
            )
            ThermalTile(
                caption = stringResource(R.string.home_skin_tag),
                value = dashboard.skinTempC,
                modifier = Modifier.weight(1f)
            )
            ThermalTile(
                caption = stringResource(R.string.max_home_battery),
                value = dashboard.batteryTempC.roundToInt(),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** بلاطة مجسّ: صفر تعني «المجسّ لا يُقرأ»، فلا تُلوَّن بالأخضر كأنها برودة مقيسة. */
@Composable
private fun ThermalTile(caption: String, value: Int, modifier: Modifier) {
    val p = neuralPalette()
    val known = value > 0
    NeuralReadoutTile(
        caption = caption,
        value = if (known) "$value\u00b0" else "\u2014",
        accent = if (known) temperatureAccent(value) else p.muted,
        modifier = modifier,
        sub = if (known) null else stringResource(R.string.home_sensor_unavailable),
    )
}

/**
 * تفاصيل الجهاز — الأرقام التي تُفتح لها تطبيقات «معلومات الجهاز»، في شبكة كثيفة.
 *
 * ولماذا شبكة من ثلاث خانات لا قائمة: القائمة سطر لكل معلومة تُطيل الصفحة، وهذه أرقام
 * تُقرأ بالعين لا تُقرأ كجمل؛ ثلاث خانات بعرض الشاشة تجعل التسع كلّها في ثلاثة أسطر.
 * والقيَم تُرسم بـ[NeuralValue] (LTR مثبّت) فلا يقلب RTL «1080×2400» أو «4.2 V».
 */
@Composable
internal fun DeviceDetailsPanel(
    dashboard: DashboardState,
    onDisplay: () -> Unit,
    onNetwork: () -> Unit,
    onPower: () -> Unit,
) {
    val p = neuralPalette()
    val dash = "\u2014"
    val resolution = if (dashboard.displayWidth > 0 && dashboard.displayHeight > 0) {
        "${dashboard.displayWidth}\u00d7${dashboard.displayHeight}"
    } else {
        dash
    }

    NeuralPanel(accent = p.accent, verticalSpacing = 12.dp) {
        NeuralSectionHeader(
            title = stringResource(R.string.home_device_details),
            caption = stringResource(R.string.home_device_details_desc),
            accent = p.accent,
        )
        // هوية النظام صفوفًا لا بلاطات: هذه قراءات ثابتة (تُقرأ مرة من `Build`) لا تتغيّر كل
        // دقيقتَي قياس، فصفّها هو ما لا يُنافس الأرقام الحيّة على الانتباه — وهي في الوقت نفسه
        // ما يضعه DevCheck أسفل لوحته: الإصدار والموديل بالنصّ كاملًا.
        NeuralDataRow(
            label = stringResource(R.string.home_android_version),
            value = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        )
        NeuralDataRow(
            label = stringResource(R.string.device_model),
            value = Build.MODEL.ifBlank { dash },
        )
        NeuralDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeuralFactTile(
                caption = stringResource(R.string.max_home_uptime),
                value = compactUptime(dashboard.uptimeMinutes),
                accent = p.muted,
                modifier = Modifier.weight(1f)
            )
            NeuralFactTile(
                caption = stringResource(R.string.home_resolution),
                value = resolution,
                accent = p.muted,
                modifier = Modifier.weight(1f),
                onClick = onDisplay
            )
            NeuralFactTile(
                caption = stringResource(R.string.home_current_refresh),
                value = if (dashboard.displayRefreshHz > 0) "${dashboard.displayRefreshHz} Hz" else dash,
                accent = p.muted,
                modifier = Modifier.weight(1f),
                onClick = onDisplay
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeuralFactTile(
                caption = stringResource(R.string.home_download),
                value = netSpeed(dashboard.downloadSpeedKbps),
                accent = p.ok,
                modifier = Modifier.weight(1f),
                onClick = onNetwork
            )
            NeuralFactTile(
                caption = stringResource(R.string.home_upload),
                value = netSpeed(dashboard.uploadSpeedKbps),
                accent = p.accentAlt,
                modifier = Modifier.weight(1f),
                onClick = onNetwork
            )
            NeuralFactTile(
                caption = stringResource(R.string.home_density),
                value = if (dashboard.displayDensityDpi > 0) "${dashboard.displayDensityDpi} dpi" else dash,
                accent = p.muted,
                modifier = Modifier.weight(1f),
                onClick = onDisplay
            )
            NeuralFactTile(
                caption = stringResource(R.string.home_battery_current),
                value = dashboard.batteryCurrentMa?.let { "$it mA" } ?: dash,
                accent = p.muted,
                modifier = Modifier.weight(1f),
                onClick = onPower
            )
            NeuralFactTile(
                caption = stringResource(R.string.home_battery_voltage),
                value = if (dashboard.batteryVoltageV > 0f) {
                    "${dashboard.batteryVoltageV.oneDecimal()} V"
                } else {
                    dash
                },
                accent = p.muted,
                modifier = Modifier.weight(1f),
                onClick = onPower
            )
        }
    }
}

/**
 * بطاقة الأداء التاريخي — الجواب عن سؤال لا يجيبه أي رقم لحظي: **ماذا كان الحمل طوال النافذة؟**
 *
 * والبيانات كلها مقيسة ومحفوظة: `LoadHistory` يحفظ 36 عيّنة بطابعها الزمني، و`ramLoadHistory`
 * يحفظ نسبة الذاكرة على الإيقاع نفسه. ولا صفر يُرسم حيث لا قراءة: مخطط المكتبة صار يترك
 * **فجوة** عند العيّنة المجهولة (`gpu = null` على جهاز لا تُعلن نواته عقدة رسوم) — فالمنحنى
 * ينكسر بدل أن ينزل إلى القاع ويقول كذبة «الرسوم كانت هادئة».
 *
 * وصفوف التلخيص تعرض **متوسط النافذة** لا اللحظة — فلا تكرّر الأقواس الأربعة فوقها، وهي في
 * الوقت نفسه صيغة «قوائم التصنيفات بأشرطة التقدّم» التي جاءت من الصورة `5.jpg`.
 */
@Composable
internal fun PerformanceHistoryCard(dashboard: DashboardState) {
    val p = neuralPalette()
    val samples = dashboard.loadSamples
    val windowed = samples.size >= 3
    val cpuAvg = if (windowed) samples.map { it.cpu }.average().toFloat() else null
    val gpuSeries = samples.map { it.gpu }
    val gpuMeasured = gpuSeries.filterNotNull()
    val gpuAvg = if (gpuMeasured.size >= 3) gpuMeasured.average().toFloat() else null
    val ramSeries = dashboard.ramLoadHistory
    val ramAvg = if (ramSeries.size >= 3) ramSeries.average().toFloat() else null

    NeuralStatCard(
        title = stringResource(R.string.home_history_title),
        caption = stringResource(R.string.home_history_desc),
        accent = p.accent,
        badge = if (windowed) null else stringResource(R.string.max_home_unavailable),
        chart = {
            NeuralAreaPlot(
                values = samples.map { it.cpu },
                accent = p.accent,
                secondary = gpuSeries,
                secondaryAccent = p.accentAlt,
                maxValue = 100f,
                adaptive = false,
                modifier = Modifier.fillMaxWidth().height(84.dp),
            )
        },
    ) {
        NeuralCategoryRow(
            label = stringResource(R.string.home_cpu_tag),
            value = if (cpuAvg == null) "\u2014" else "${cpuAvg.roundToInt()}%",
            fraction = cpuAvg?.let { it / 100f },
            accent = p.accent,
        )
        NeuralCategoryRow(
            label = stringResource(R.string.home_gpu_tag),
            value = if (gpuAvg == null) "\u2014" else "${gpuAvg.roundToInt()}%",
            fraction = gpuAvg?.let { it / 100f },
            accent = p.accentAlt,
        )
        NeuralCategoryRow(
            label = stringResource(R.string.home_ram_tag),
            value = if (ramAvg == null) "\u2014" else "${ramAvg.roundToInt()}%",
            fraction = ramAvg?.let { it / 100f },
            accent = p.warn,
        )
    }
}

/** لون الحكم: خط الأساس هادئ، والحرارة بلون الحرارة، وما عدا ذلك بلون المسار. */
@Composable
internal fun limiterAccent(limiter: String, heatAccent: Color): Color {
    val p = neuralPalette()
    return when (limiter) {
        "Baseline" -> p.ok
        "Thermal", "Power/Thermal" -> heatAccent
        "CPU", "GPU" -> p.accent
        "Memory" -> p.accentAlt
        else -> p.muted
    }
}

/**
 * Where to go next. Four destinations that people actually reach for; the profile
 * action is gone (the rail replaced it) and every tile here owns a screen this
 * home does not duplicate.
 */
@Composable
internal fun CommandDeck(
    onThermal: () -> Unit,
    onBattery: () -> Unit,
    onApps: () -> Unit,
    onAdvanced: () -> Unit,
) {
    val p = neuralPalette()
    NeuralPanel {
        NeuralSectionHeader(
            title = stringResource(R.string.home_quick_actions),
            caption = stringResource(R.string.home_quick_actions_desc),
            accent = p.accentAlt
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NeuralActionTile(
                icon = Icons.Rounded.Thermostat,
                title = stringResource(R.string.home_action_thermal),
                support = stringResource(R.string.home_action_thermal_desc),
                accent = p.warn,
                onClick = onThermal,
                modifier = Modifier.weight(1f)
            )
            NeuralActionTile(
                icon = Icons.Rounded.BatteryChargingFull,
                title = stringResource(R.string.home_action_battery),
                support = stringResource(R.string.home_action_battery_desc),
                accent = p.ok,
                onClick = onBattery,
                modifier = Modifier.weight(1f)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NeuralActionTile(
                icon = Icons.Rounded.Apps,
                title = stringResource(R.string.max_home_app_profiles),
                support = stringResource(R.string.home_action_apps_desc),
                accent = p.accent,
                onClick = onApps,
                modifier = Modifier.weight(1f)
            )
            NeuralActionTile(
                icon = Icons.Rounded.Tune,
                title = stringResource(R.string.home_action_advanced),
                support = stringResource(R.string.home_action_advanced_desc),
                accent = p.accentAlt,
                onClick = onAdvanced,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
