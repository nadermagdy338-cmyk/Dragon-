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
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import nd.max.ui.component.NeuralSensorOption
import nd.max.ui.component.NeuralSensorPicker
import nd.max.ui.component.neuralClickable
import nd.max.ui.component.neuralPalette
import nd.max.ui.util.ThermalGridModel
import nd.max.ui.util.ThermalGridPreferences
import nd.max.ui.viewmodel.DashboardState

/**
 * الحرارة — **مجسّات يختارها المستخدم**، ولون الخطر قبل الرقم.
 *
 * ولا رقم واحد «للحرارة العامة»: جهاز قد يكون غلافه هادئًا وبطاريته ساخنة، والاثنان
 * يقودان إلى إجراء مختلف. والغلاف (`Skin`) هو ما يلمسه المستخدم فعلًا، ولهذا يُعرض ضمن
 * الافتراضي إلى جانب ما يقيسه المحرّك.
 *
 * والشبكة **مثبَّتة بالاختيار** (مرجع DevCheck: لمس بطاقة الحرارة يختار المجسّات المعروضة):
 * أربعة مواضع كحدّ أقصى، تُملأ من فئات هذه النواة فعلًا (`ThermalGridModel`)، ويبقى الاختيار
 * بعد إغلاق الشاشة (`ThermalGridPreferences`). وما لا تقيسه هذه النواة لا يُعرض له صفّ.
 */
@Composable
internal fun ThermalPanel(dashboard: DashboardState, onThermal: () -> Unit) {
    val context = LocalContext.current
    val p = neuralPalette()
    val headline = dashboard.batteryTempC.takeIf { it > 0f }?.roundToInt()
        ?: dashboard.cpuTempC.takeIf { it > 0 }
    val headlineAccent = temperatureAccent(headline)
    val calm = headline == null || headline < 43
    var pinned by remember { mutableStateOf(ThermalGridPreferences.read(context)) }
    var picking by remember { mutableStateOf(false) }
    val available = remember(dashboard.thermalByCategory) {
        ThermalGridModel.options(dashboard.thermalByCategory.keys)
    }

    NeuralPanel(accent = headlineAccent, onClick = onThermal, verticalSpacing = 12.dp) {
        NeuralSectionHeader(
            title = stringResource(R.string.home_system_vitals),
            caption = stringResource(R.string.max_home_thermal_desc),
            accent = headlineAccent,
            trailing = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NeuralPill(
                        text = stringResource(
                            if (calm) R.string.home_system_stable else R.string.home_system_attention
                        ),
                        accent = if (calm) p.ok else headlineAccent,
                        dot = true,
                    )
                    SensorPickerButton(onClick = { picking = true })
                }
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pinned.forEach { category ->
                ThermalTile(
                    caption = categoryLabel(category),
                    value = dashboard.sensorReading(category),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (picking) {
        NeuralSensorPicker(
            title = stringResource(R.string.home_sensors_title),
            caption = stringResource(R.string.home_sensors_desc),
            limit = ThermalGridModel.LIMIT,
            options = available.map { key ->
                val value = dashboard.sensorReading(key)
                NeuralSensorOption(
                    key = key,
                    label = categoryLabel(key),
                    reading = thermalReadingText(value),
                    selected = key in pinned,
                    accent = temperatureAccent(value.takeIf { it > 0 }),
                )
            },
            onToggle = { key ->
                pinned = ThermalGridModel.toggle(pinned, key)
                ThermalGridPreferences.write(context, pinned)
            },
            onDismiss = { picking = false },
        )
    }
}

/** اسم فئة المجسّ بلغة الواجهة؛ ومجهول الإملاء يُعرض كما جاء من العتاد (رمز تقني لا نصّ). */
@Composable
private fun categoryLabel(category: String): String = when (category) {
    "CPU" -> stringResource(R.string.home_cpu_tag)
    "GPU" -> stringResource(R.string.home_gpu_tag)
    "Skin" -> stringResource(R.string.home_skin_tag)
    "Battery" -> stringResource(R.string.max_home_battery)
    "Charger" -> stringResource(R.string.home_cat_charger)
    "Modem" -> stringResource(R.string.home_cat_modem)
    "WiFi" -> stringResource(R.string.home_cat_wifi)
    "Camera" -> stringResource(R.string.home_cat_camera)
    "Flash" -> stringResource(R.string.home_cat_flash)
    "PA" -> stringResource(R.string.home_cat_pa)
    "System" -> stringResource(R.string.home_cat_system)
    else -> category
}

/** قراءة فئة: الرقم المخصّص له بأفضل تراجعاته، والبقية من خريطة الدورة. والصفر «لا قراءة». */
private fun DashboardState.sensorReading(category: String): Int = when (category) {
    "CPU" -> cpuTempC
    "GPU" -> gpuTempC
    "Skin" -> skinTempC
    "Battery" -> batteryTempC.roundToInt()
    else -> thermalByCategory[category] ?: 0
}

/** صيغة القراءة: الدرجة أو `—` — لا صفر يقول «صفر درجة». */
private fun thermalReadingText(value: Int): String =
    if (value > 0) "$value\u00b0" else "\u2014"

/** زر اختيار المجسّات — لمسة صغيرة في الترويسة لا تسرق عرض البطاقة. */
@Composable
private fun SensorPickerButton(onClick: () -> Unit) {
    val p = neuralPalette()
    Box(
        Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(p.tile)
            .neuralClickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Tune,
            contentDescription = stringResource(R.string.home_sensors_choose),
            modifier = Modifier.size(15.dp),
            tint = p.muted,
        )
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
    onDeviceInfo: () -> Unit,
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
            onClick = onDeviceInfo,
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
 * متوسط نافذة السجل — لا يُقال «متوسط» دون ثلاث عيّنات مقيسة، والفجوة (قيمة مجهولة) لا تُحسب
 * أصفارًا تنزل المنحنى نحو القاع. والواجهة تعرض `—` حين تُعيد هذه الدالة `null`.
 * (مُقاس في JVM: `HistoryWindowTest`.)
 */
internal fun windowedAverage(series: List<Float?>): Float? {
    val measured = series.filterNotNull()
    return if (measured.size >= 3) measured.average().toFloat() else null
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
    val cpuAvg = windowedAverage(samples.map { it.cpu })
    val gpuSeries = samples.map { it.gpu }
    val gpuAvg = windowedAverage(gpuSeries)
    val ramSeries = dashboard.ramLoadHistory
    val ramAvg = windowedAverage(ramSeries)

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
 * Where to go next — **one compact scrolling row** (the DevCheck command row), five
 * destinations this home does not duplicate. The support lines the old 2×2 deck carried
 * went with it: at five destinations the titles say everything those lines repeated,
 * and one row hands the screen back a full band of readings.
 */
@Composable
internal fun CommandDeck(
    onThermal: () -> Unit,
    onBattery: () -> Unit,
    onApps: () -> Unit,
    onAdvanced: () -> Unit,
    onSystemSettings: () -> Unit,
) {
    val p = neuralPalette()
    NeuralPanel {
        NeuralSectionHeader(
            title = stringResource(R.string.home_quick_actions),
            caption = stringResource(R.string.home_quick_actions_desc),
            accent = p.accentAlt
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NeuralActionTile(
                icon = Icons.Rounded.Thermostat,
                title = stringResource(R.string.home_action_thermal),
                accent = p.warn,
                onClick = onThermal,
                modifier = Modifier.width(116.dp)
            )
            NeuralActionTile(
                icon = Icons.Rounded.BatteryChargingFull,
                title = stringResource(R.string.home_action_battery),
                accent = p.ok,
                onClick = onBattery,
                modifier = Modifier.width(116.dp)
            )
            NeuralActionTile(
                icon = Icons.Rounded.Apps,
                title = stringResource(R.string.max_home_app_profiles),
                accent = p.accent,
                onClick = onApps,
                modifier = Modifier.width(116.dp)
            )
            NeuralActionTile(
                icon = Icons.Rounded.Tune,
                title = stringResource(R.string.home_action_advanced),
                accent = p.accentAlt,
                onClick = onAdvanced,
                modifier = Modifier.width(116.dp)
            )
            NeuralActionTile(
                icon = Icons.Rounded.Settings,
                title = stringResource(R.string.home_action_system_settings),
                accent = p.muted,
                onClick = onSystemSettings,
                modifier = Modifier.width(116.dp)
            )
        }
    }
}
