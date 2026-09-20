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

/**
 * **كرت التفاصيل**: الأنوية والشاشة والشبكة والجهد — مفتوح دائمًا.
 *
 * وكان يُطوى افتراضيًّا «ليبقى أول شاشة هادئة»، وتبيّن أن الثمن أعلى من الفائدة: ما يُطوى
 * لا يُقرأ — إنه يحتاج نقرةً ليكشف معلومة **لا تظهر في مكان آخر**. فالحجم هنا يُدار
 * بالترتيب لا بالإخفاء: التفاصيل في آخر الصفحة، فيراها من ينزل إليها ويتركها من لا ينزل.
 *
 * **ومصفوفة الأنوية تتبع بنية الشريحة لا الشبكة**: تُجمع الأنوية بعناقيدها (`SILVER` ·
 * `GOLD` · `PRIME` كما تُقرأ من نواة النظام) وبلون كل عنقود، ومع متوسّط تردّده. فتُقرأ
 * «أي كتلة تعمل الآن» من الصورة قبل أي رقم — بينما شبكة ٤×٢ كانت تقول عدد الأنوية فقط
 * وتُخفي أن الصغيرة والكبيرة ليستا شيئًا واحدًا.
 *
 * ولا معلومة تتكرر في هذا الكرت: البطارية نسبتها في الأعلى (النبضة)، وهنا **الجهد** —
 * رقم مختلف بتسمية مختلفة، ومن يقرأ «البطارية» في موضعين بقيمتين يتعلّم ألّا يثق بالشاشة.
 */
package nd.max.ui.mainscreens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nd.max.R
import nd.max.ui.component.NeuralCaption
import nd.max.ui.component.NeuralIconChip
import nd.max.ui.component.NeuralPanel
import nd.max.ui.component.NeuralSectionHeader
import nd.max.ui.component.NeuralTile
import nd.max.ui.component.NeuralTrack
import nd.max.ui.component.NeuralValue
import nd.max.ui.component.neuralPalette
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.theme.MonoValueStyleSmall
import nd.max.ui.viewmodel.CpuCoreState
import nd.max.ui.viewmodel.DashboardState

/** أقصى عدد أنوية في السطر — ٤ يقرأ بالعين على أضيق شاشة بلا قصّ للتردّد. */
private const val CORES_PER_ROW = 4

@Composable
internal fun HomeDetailsPanel(dashboard: DashboardState, onNavigate: (String) -> Unit) {
    val p = neuralPalette()
    val cores = dashboard.cores
    NeuralPanel(accent = p.accentAlt) {
        NeuralSectionHeader(
            title = stringResource(R.string.home_details_title),
            caption = stringResource(R.string.home_details_caption),
            accent = p.accentAlt,
        )
        if (cores.isEmpty()) {
            NeuralCaption(stringResource(R.string.home_waiting_core_data))
        } else {
            NeuralCaption(stringResource(R.string.home_cpu_cores_online, cores.count { it.online }, cores.size))
            // هوية النواة موضعية: C0 يُقرأ أولًا في كل لغة، فلا تُقلب المصفوفة مع الواجهة.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    cores.groupBy { it.clusterTag }.forEach { (tag, cluster) ->
                        CoreCluster(tag = tag, cores = cluster)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FabricChip(
                icon = Icons.Rounded.DisplaySettings,
                label = stringResource(R.string.max_hub_display),
                value = if (dashboard.displayWidth > 0) {
                    "${dashboard.displayWidth}x${dashboard.displayHeight}"
                } else {
                    "\u2014"
                },
                support = if (dashboard.displayRefreshHz > 0) "${dashboard.displayRefreshHz} Hz" else null,
                accent = p.accent,
                modifier = Modifier.weight(1f),
                onClick = { onNavigate(MaxDestination.DisplayStudio.route) }
            )
            FabricChip(
                icon = Icons.Rounded.NetworkCheck,
                label = stringResource(R.string.home_network_status),
                value = netSpeed(dashboard.downloadSpeedKbps),
                support = "\u2191 ${netSpeed(dashboard.uploadSpeedKbps)}",
                accent = p.ok,
                modifier = Modifier.weight(1f),
                onClick = { onNavigate(MaxDestination.NetworkDetail.route) }
            )
            FabricChip(
                icon = Icons.Rounded.BatteryChargingFull,
                label = stringResource(R.string.charging_voltage),
                value = "${dashboard.batteryVoltageV.oneDecimal()} V",
                // الحالة نصوصها مترجمة بدل السلسلة الإنجليزية الخام التي كان الجهاز يعيدها.
                support = stringResource(
                    if (dashboard.isCharging) R.string.charging_status_charging
                    else R.string.charging_status_not_charging
                ),
                accent = p.warn,
                modifier = Modifier.weight(1f),
                // الشاشتان دُمجتا: "BatteryDetail" لم يعد موجودًا، وهذا المدخل يذهب إلى
                // الشاشة المدمجة نفسها التي يذهب إليها مدخل البطارية في الأعلى.
                onClick = { onNavigate(MaxDestination.Charging.route) }
            )
        }
    }
}

/**
 * عنقود واحد: سطر تعريفه (اسمه ولونه ومتوسّط تردّده) ثم أنويته.
 * والمتوسط **للمتصل وحده** — عنقود مُطفأ بالكامل لا يُعلن متوسطًا لعيّنات لا وجود لها.
 */
@Composable
private fun CoreCluster(tag: String, cores: List<CpuCoreState>) {
    val accent = clusterAccent(tag)
    val online = cores.filter { it.online }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (tag.isNotBlank()) {
                NeuralCaption(tag, Modifier.weight(1f), color = accent)
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (online.isNotEmpty()) {
                NeuralValue(
                    stringResource(R.string.home_stat_avg) + " " +
                        compactFrequency(online.map { it.freqMhz }.average().toInt()),
                    style = MonoValueStyleSmall.copy(fontSize = 10.sp),
                    color = accent
                )
            }
        }
        cores.chunked(CORES_PER_ROW).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { core -> CoreTile(core, Modifier.weight(1f)) }
                repeat(CORES_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * نواة واحدة: رقمها، وترددها الحيّ، وحملها كشريط.
 *
 * وتلوينها بلون عنقودها هو ما يجعل المصفوفة تُقرأ كبنية: صفٌّ فضي وصفٌّ ذهبي وواحدة رئيسية،
 * بدل ثمانية مربّعات متطابقة يُعرف منها العدد لا الترتيب. والنواة المُطفأة تفقد لونها
 * وتُكتب `OFF` — لا «صفر هرتز» الذي يُظنّ حملًا هادئًا.
 */
@Composable
private fun CoreTile(core: CpuCoreState, modifier: Modifier) {
    val p = neuralPalette()
    val accent = if (core.online) clusterAccent(core.clusterTag) else p.muted
    NeuralTile(
        modifier,
        accent = if (core.online) accent else null,
        verticalSpacing = 6.dp,
        contentPadding = PaddingValues(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NeuralCaption("C${core.cpu}", Modifier.weight(1f), color = accent)
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = if (core.online) 1f else .35f))
            )
        }
        NeuralValue(
            if (core.online) compactFrequency(core.freqMhz) else "OFF",
            style = MonoValueStyleSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
            color = if (core.online) p.text else p.muted
        )
        NeuralTrack(core.loadFraction, accent, height = 4.dp)
    }
}

/** لون العنقود من وسمه كما تعطيه نواة النظام — بلا جدول أسماء ثابت يفشل على شرائح جديدة. */
@Composable
private fun clusterAccent(tag: String): Color {
    val p = neuralPalette()
    return when {
        tag.contains("PRIME", ignoreCase = true) -> p.accentAlt
        tag.contains("GOLD", ignoreCase = true) -> p.accent
        tag.contains("SILVER", ignoreCase = true) -> p.ok
        else -> p.accent
    }
}

@Composable
private fun FabricChip(
    icon: ImageVector,
    label: String,
    value: String,
    support: String?,
    accent: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val p = neuralPalette()
    NeuralTile(modifier, onClick = onClick, verticalSpacing = 6.dp, contentPadding = PaddingValues(12.dp)) {
        NeuralIconChip(icon, accent, size = 26.dp)
        NeuralCaption(label, color = accent)
        NeuralValue(
            value,
            style = MonoValueStyleSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = p.text
        )
        if (support != null) {
            NeuralValue(
                support,
                style = MonoValueStyleSmall.copy(fontSize = 10.sp),
                color = p.muted
            )
        }
    }
}
