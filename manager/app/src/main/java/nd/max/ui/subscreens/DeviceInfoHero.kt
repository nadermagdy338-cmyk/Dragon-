/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * Device Info — **رأس القسم**: المقياس الدائريّ وبلاطات الإحصاء وشرائح القدرات.
 *
 * **ولماذا هذه الطبقة:** طلب المالك تصميمًا «فيه إبهار» لا قائمة أرقام مسطّحة — وهو طلبٌ
 * **عرضيّ** لا قرار بيانات، فالبيانات كلها في `DeviceInfoFacts` وتُقاس على JVM، وهنا
 * **تركيبٌ لمكوّنات نظام التصميم نفسه** لا لغة موازية: `NeuralPanel` و`NeuralKpiTile`
 * (لغة «السطح الفاخر» القائمة في الرئيسية) و`RadialGaugeCard` (المقياس الموقّع لكل شاشة
 * عتاد حيّة) و`MaxCapsule` للشرائح. **ولا نسخٌ لشكل المرجعين** (ADR-55): الأدوات التي
 * أُرسلت لقطاتها مرجعُ محتوى لا مظهر، والشكل هنا من مفرداتنا نحن.
 *
 * **وقاعدة الثقة واحدة مع `MaxMetric`:** قيمةٌ تُطبع إن كانت قراءة، و`—` مع وسم الثقة إن
 * كانت غيابًا — فلا تُملأ بلاطة بـ«0» لأن الرقم جميل.
 */
package nd.max.ui.subscreens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import nd.max.R
import nd.max.ui.component.NeuralKpiTile
import nd.max.ui.component.NeuralPanel
import nd.max.ui.component.RadialGaugeCard
import nd.max.ui.design.MAX_VALUE_UNAVAILABLE
import nd.max.ui.design.MaxCapsule
import nd.max.ui.design.MaxDataTrust
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxTone
import nd.max.ui.design.content
import nd.max.ui.design.maxTrustLabel
import nd.max.ui.design.visual

/**
 * رأس القسم كاملًا — يُبنى من [DeviceInfoHero] الصافي (المقيس في `DeviceInfoFactsTest`)
 * ولا يقرّر شيئًا هنا: الرسم يُرجم التسميات ويوزّع الألوان، لا يختار ما يُعرض.
 */
@Composable
internal fun DeviceInfoHeroView(hero: DeviceInfoHero, pending: Boolean) {
    if (hero.title == null && hero.subtitle == null && hero.gauges.isEmpty() && hero.tiles.isEmpty()) return
    NeuralPanel(accent = MaterialTheme.colorScheme.primary) {
        hero.title?.takeIf { it.isNotBlank() }?.let { title ->
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        hero.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        hero.gauges.forEach { gauge -> DeviceInfoGaugeView(gauge) }
        DeviceInfoTileGrid(hero.tiles, pending)
    }
}

/**
 * المقياس الدائريّ — **ولا يُبنى إلا من كسرٍ حقيقيّ** (النموذج لا يُنتج مقياسًا بلا قراءة)،
 * وهو المقياس الموقّع نفسه الذي تحمله شاشات العتاد الحيّة، فتبدو الشاشة جزءًا من المنتج.
 */
@Composable
private fun DeviceInfoGaugeView(gauge: DeviceInfoGauge) {
    RadialGaugeCard(
        title = stringResource(gauge.titleRes),
        valueText = gauge.valueText,
        unitText = gauge.unitText,
        fraction = gauge.fraction,
        subtitle = gauge.subtitle,
        isLive = gauge.live,
    )
}

/** شبكة البلاطات: صفّان في كل صفّ — وصفٌّ أخير وحده يبقى على نصف العرض لا يُمدّد. */
@Composable
private fun DeviceInfoTileGrid(tiles: List<DeviceInfoTile>, pending: Boolean) {
    tiles.chunked(2).forEach { rowTiles ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaxSpace.md),
        ) {
            rowTiles.forEach { tile ->
                Box(modifier = Modifier.weight(1f)) {
                    DeviceInfoTileView(tile, pending)
                }
            }
            if (rowTiles.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

/**
 * بلاطة واحدة — والقيمة تُقرأ كما في `MaxMetric`: المورد المترجَم أولًا، ثم الرقم المُهيّأ،
 * ثم `—` مع وسم الثقة. ولون التسمية هو **لون الثقة** لا لونٌ للزينة: الحيّ تمييزٌ،
 * والغائب تحذيرٌ هادئ، والمجهول بارد — فيُقرأ الفرق قبل قراءة الرقم.
 */
@Composable
private fun DeviceInfoTileView(tile: DeviceInfoTile, pending: Boolean) {
    val trust = tile.trust.asMaxTrust(pending)
    val showsValue = trust.visual().showsValue && (tile.value != null || tile.valueRes != null)
    val valueText = when {
        tile.valueRes != null -> stringResource(tile.valueRes)
        showsValue -> tile.value.orEmpty()
        else -> MAX_VALUE_UNAVAILABLE
    }
    NeuralKpiTile(
        caption = stringResource(tile.captionRes),
        value = valueText,
        accent = trust.visual().tone.content(),
        support = if (showsValue) tile.unit else maxTrustLabel(trust),
    )
}

/**
 * شرائح القدرات — صفٌّ لكل خاصية، ووسمٌ يقول حكم المنصّة لا انطباعنا. والترجمة في
 * الموارد (`devinfo_cap_*`) فلا يُطبع «Supported» في واجهة عربية.
 */
@Composable
internal fun DeviceInfoChipGroup(chips: List<DeviceInfoChipRow>, title: String) {
    if (chips.isEmpty()) return
    MaxSection(title = title) {
        MaxGroup {
            chips.forEachIndexed { position, chip ->
                if (position > 0) MaxGroupDivider()
                MaxRow(
                    title = stringResource(chip.labelRes),
                    trailing = {
                        MaxCapsule(
                            text = chipStateLabel(chip.state),
                            tone = chipStateTone(chip.state),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun chipStateLabel(state: CapabilityState): String = when (state) {
    CapabilityState.Supported -> stringResource(R.string.devinfo_cap_supported)
    CapabilityState.NotDeclared -> stringResource(R.string.devinfo_cap_not_declared)
    CapabilityState.Unreadable -> maxTrustLabel(MaxDataTrust.Unreadable)
}

/** واللون **يُقوّي النصّ لا يُغني عنه**: الوسم يظلّ مكتوبًا في كل الحالات. */
private fun chipStateTone(state: CapabilityState): MaxTone = when (state) {
    CapabilityState.Supported -> MaxTone.Positive
    CapabilityState.NotDeclared -> MaxTone.Inactive
    CapabilityState.Unreadable -> MaxTone.Caution
}
