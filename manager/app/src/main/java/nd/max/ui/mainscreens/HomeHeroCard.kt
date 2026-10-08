/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * البطاقة الأولى في الرئيسية — **الشكل القديم المعتمد بتحسين، لا بتغيير** (طلب المالك).
 *
 * ترتيب القراءة كما في اللقطة المعتمدة: الجهاز أولًا (اسمه وشريحته)، ثم الحرارة قراءةً رئيسية
 * بجانبها Max AI، ثم ثلاث بلاطات قراءة (مدة التشغيل · البطارية · استهلاك الطاقة)، ثم باب إلى نظرة
 * الجهاز.
 *
 * ما أُضيف على الشكل القديم، ولماذا:
 *  - **شارة الوصول** فوق الاسم: الحالة (جذر / شيزوكو / أساسي) تُقرأ قبل أي رقم.
 *  - **كلمة الحكم بجانب الحرارة** بلونها: اللون وحده لا يحمل المعنى (DESIGN.md).
 *  - **الغياب شرطة** (ADR-07): لا `0%` ولا `0 W` لقيمة لم تُقرأ.
 *
 * وتستعمل البطاقة الرموز لا الأرقام: المسافات من `MaxSpace`، والحجم من `MaxSize`، والخطوط من
 * سلّم الطباعة و`MonoFontFamily`، والبلاطات من `NeuralTile` بلونها.
 */
package nd.max.ui.mainscreens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import nd.max.R
import nd.max.core.privilege.PrivilegeLevel
import nd.max.ui.component.MaxAiEntryButton
import nd.max.ui.component.NeuralIconChip
import nd.max.ui.component.NeuralPanel
import nd.max.ui.component.NeuralPill
import nd.max.ui.component.NeuralTile
import nd.max.ui.component.NeuralValue
import nd.max.ui.component.neuralPalette
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.theme.MonoFontFamily
import nd.max.ui.theme.MonoValueStyleMedium
import nd.max.ui.viewmodel.DashboardState

@Composable
internal fun HomeHeroCard(
    deviceName: String,
    dashboard: DashboardState,
    /** طبقة الامتياز المكتشفة — من `PrivilegeManager` لا من قراءة ثانية. */
    accessLevel: PrivilegeLevel,
    /** حالة Max AI الحقيقية (`MaxAiState.aiEnabled`) — تُعلّم الزرّ بلونه. */
    aiEnabled: Boolean,
    /** الباب إلى نظرة الجهاز: المالك الوحيد لفكرة «نظرة على الجهاز» بأقسامها. */
    onOverview: () -> Unit,
    onMaxAi: () -> Unit,
) {
    val p = neuralPalette()
    val access = accessAccent(accessLevel, p)

    // الحرارة: الواحدة نفسها التي تقرؤها الشبكة (`deviceHeatC`)، وكلمة حكمها بلونها.
    val heat = deviceHeatC(dashboard)
    val heatAccent = temperatureAccent(heat)

    // البطارية والطاقة: الصفر غير مقروء، فيُكتب شرطة لا رقمًا كاذبًا.
    val battery = dashboard.batteryPercent.takeIf { it > 0 }
    val batteryAccent = when {
        dashboard.isCharging -> p.ok
        battery != null && battery <= 20 -> p.warn
        else -> p.accentAlt
    }
    val power = dashboard.powerWatt.takeIf { it > 0.05f }

    NeuralPanel(accent = access, verticalSpacing = MaxSpace.md) {
        // الحالة قبل الاسم: شارة الوصول (جذر · شيزوكو · أساسي).
        NeuralPill(
            text = stringResource(accessLabelRes(accessLevel)),
            accent = access,
            filled = true,
            dot = true,
        )

        // الجهاز: الأيقونة ثم الاسم بعنوان الصفحة (سطران كحدّ البطاقة)، ثم الشريحة.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NeuralIconChip(Icons.Rounded.PhoneAndroid, p.accent, size = MaxSize.iconContainer)
            Spacer(Modifier.width(MaxSpace.md))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MaxSpace.xs)) {
                Text(
                    deviceName,
                    color = p.text,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    dashboard.chipsetName,
                    color = p.muted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // الحرارة قراءةً رئيسية: كلمة الحكم بلونها فوق الرقم الكبير بالخط الأحادي، والوحدة بجانبه،
        // و`Max AI` في الطرف المقابل كما كان في الشكل القديم.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MaxSpace.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.home_temperature_short).uppercase(),
                        color = p.muted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                    if (heat != null) {
                        Spacer(Modifier.width(MaxSpace.sm))
                        Text(
                            stringResource(heatWordRes(heat)),
                            color = heatAccent,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    NeuralValue(
                        heat?.toString() ?: "\u2014",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = p.text,
                    )
                    if (heat != null) {
                        Spacer(Modifier.width(MaxSpace.xs))
                        Text(
                            stringResource(R.string.home_unit_celsius),
                            color = p.muted,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                        )
                    }
                }
            }
            MaxAiEntryButton(
                text = stringResource(R.string.max_nav_max_ai),
                active = aiEnabled,
                onClick = onMaxAi,
            )
        }

        // ثلاث بلاطات قراءة بألوانها: مدة التشغيل · البطارية · استهلاك الطاقة.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm)) {
            HeroTile(
                label = stringResource(R.string.max_home_uptime),
                value = compactUptime(dashboard.uptimeMinutes),
                accent = p.accent,
                modifier = Modifier.weight(1f),
            )
            HeroTile(
                label = stringResource(R.string.max_home_battery),
                value = battery?.let { "$it%" } ?: "\u2014",
                accent = batteryAccent,
                modifier = Modifier.weight(1f),
            )
            HeroTile(
                label = stringResource(R.string.home_power_draw),
                value = power?.let { "${it.oneDecimal()} W" } ?: "\u2014",
                accent = p.accentAlt,
                modifier = Modifier.weight(1f),
            )
        }

        // باب إلى نظرة الجهاز بزرّ مستطيل بحواف المجموعة، لا حبّة: الحبّة للحالة فقط (DESIGN.md).
        HomeActionButton(
            text = stringResource(R.string.home_hero_open_overview),
            icon = MaxDestination.DeviceInfo.icon,
            filled = false,
            accent = p.accent,
            onClick = onOverview,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** بلاطة قراءة في البطاقة الأولى: تسمية بلونها، وقيمة بالخط الأحادي، على لون البلاطة نفسه. */
@Composable
private fun HeroTile(label: String, value: String, accent: Color, modifier: Modifier) {
    val p = neuralPalette()
    NeuralTile(modifier = modifier, accent = accent, verticalSpacing = MaxSpace.xs) {
        Text(
            label,
            color = accent,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        NeuralValue(value, style = MonoValueStyleMedium, color = p.text, maxLines = 1)
    }
}
