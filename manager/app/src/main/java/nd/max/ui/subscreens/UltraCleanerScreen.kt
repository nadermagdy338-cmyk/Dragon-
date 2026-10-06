/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * التنظيف الفائق — شاشة كاملة (عقد §6).
 *
 * والسؤال الذي تجيبه: **ما الذي يمكن تحريره الآن، بكم، وما الذي يمنعني؟** ولذلك ثلاثة أقسام
 * بهذا الترتيب بالضبط:
 *
 * 1. **السعة والنطاق:** كم ممتلئ القرص، **وعند أي طبقة امتياز أعمل** — وهذا السطر هو الفرق
 *    بين «التطبيق لا ينظّف» و«هذا كل ما يُنظَّف في وضعك» (§10.3).
 * 2. **الفئات:** صفّ لكل فئة، بمقاس **مقيس** أو «لم يُقس» — لا صفر مكان الفشل، ولا مقاس
 *    مُقدَّر. والتأشير الافتراضيّ يأتي من النموذج (`InstallerFiles` و`EmptyFolders` مطفأتان).
 * 3. **الزرّ:** يحمل مجموع المختار، ويتعطّل حين لا مجموع — لا `Clean 0 B`.
 *
 * **وما لا تفعله الشاشة:** لا تكتب مسارًا ولا تمرّر مسارًا إلى المحرّك (القوالب كلها في
 * `UltraCleanEngine`)، ولا تُخفي فئة مقفلة لتُظهر زرًّا يعمل نصف عمل، ولا تعرض «حُرِّر» قبل أن
 * يُقاس الفرق فعلًا.
 */
package nd.max.ui.subscreens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import nd.max.R
import nd.max.core.privilege.PrivilegeManager
import nd.max.ui.component.NeuralCaption
import nd.max.ui.component.NeuralPanel
import nd.max.ui.component.NeuralPill
import nd.max.ui.component.NeuralSectionHeader
import nd.max.ui.component.NeuralTrack
import nd.max.ui.component.neuralPalette
import nd.max.ui.design.MaxCondition
import nd.max.ui.design.MaxConditionKind
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxListScreen
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxTone
import nd.max.ui.navigation.MaxNavActions
import nd.max.ui.util.CleanMeasurement
import nd.max.ui.util.StorageScanModel
import nd.max.ui.util.StorageUtil
import nd.max.ui.util.UltraCleanCategory
import nd.max.ui.util.UltraCleanModel
import nd.max.ui.viewmodel.UltraCleanerUiState
import nd.max.ui.viewmodel.UltraCleanerViewModel

@Composable
fun UltraCleanerScreen(
    navController: NavHostController,
    viewModel: UltraCleanerViewModel = hiltViewModel(),
) {
    val navActions = MaxNavActions(navController)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accent = MaterialTheme.colorScheme.primary

    // قياس واحد عند أول ظهور؛ وما بعده بتأشير المستخدم أو بعد التنظيف.
    LaunchedEffect(Unit) { viewModel.measureAll() }

    val primary = remember { StorageUtil.readMounts().firstOrNull { it.path == "/data" } }
    val usedFraction = primary?.usedFraction

    MaxListScreen(
        title = stringResource(R.string.ultra_cleaner_title),
        subtitle = stringResource(R.string.ultra_cleaner_tagline),
        accentIcon = Icons.Rounded.CleaningServices,
        accent = accent,
        onBack = { navActions.back() },
        condition = if (state.measuring && !state.measuredOnce) {
            MaxCondition(
                kind = MaxConditionKind.Loading,
                title = stringResource(R.string.ultra_cleaner_title),
                detail = stringResource(R.string.ultra_cleaner_measuring),
            )
        } else {
            null
        },
    ) {
        item {
            CapacityPanel(usedFraction = usedFraction, accent = accent)
        }
        item {
            ScopeLine()
        }
        item {
            MaxGroup {
                for (category in UltraCleanCategory.entries) {
                    CategoryRow(
                        category = category,
                        measurement = state.measurements.of(category),
                        checked = category in state.selection,
                        measuredOnce = state.measuredOnce,
                        onToggle = { viewModel.toggle(category) },
                    )
                }
            }
        }
        item {
            CleanActionPanel(
                state = state,
                onClean = viewModel::clean,
                onSelectSafe = viewModel::selectSafe,
                onMeasureAgain = viewModel::measureAll,
                accent = accent,
            )
        }
    }
}

/**
 * سعة القرص: النسبة الكبيرة، المستخدم من الإجمالي، والمتاح، وشريط.
 *
 * وتُقرأ من **نفس مصدر شاشة التخزين** (`StorageUtil.readMounts`) لا من حساب جديد: رقمان
 * لسعة واحدة يفترقان.
 */
@Composable
private fun CapacityPanel(usedFraction: Float?, accent: Color) {
    val p = neuralPalette()
    val tone = when {
        usedFraction == null -> p.muted
        usedFraction >= DANGER_FRACTION -> p.danger
        usedFraction >= BUSY_FRACTION -> p.warn
        else -> accent
    }
    NeuralPanel(accent = tone) {
        NeuralSectionHeader(
            title = stringResource(R.string.detail_storage),
            caption = stringResource(R.string.ultra_cleaner_intro),
            accent = tone,
        )
        if (usedFraction == null) {
            // «لا تُقرأ» ليست «فارغة»: بلا قراءة لا شريط ولا نسبة.
            Text(
                stringResource(R.string.max_home_unavailable),
                color = p.muted,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        } else {
            val percent = (usedFraction.coerceIn(0f, 1f) * 100f).toInt()
            Text(
                stringResource(R.string.ultra_cleaner_nearly_full, percent),
                color = tone,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            NeuralTrack(usedFraction.coerceIn(0f, 1f), tone)
        }
    }
}

/**
 * سطر النطاق: عند أي طبقة نعمل، وما الذي ينقص لتوسيع التنظيف.
 *
 * وهو **دائمًا ظاهر** لا عند غياب الجذر وحده: من عنده جذر يقرأ أن سجلات النظام **مشمولة**،
 * فيعرف لماذا يرى صفًّا لا يراه غيره — ومن لا جذر له يقرأ ما ينقصه بالاسم.
 */
@Composable
private fun ScopeLine() {
    val p = neuralPalette()
    val root = PrivilegeManager.cachedRootGranted()
    NeuralPanel(accent = if (root) p.ok else p.muted) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NeuralPill(
                text = stringResource(
                    if (root) R.string.max_privilege_level_root else R.string.home_access_basic
                ),
                accent = if (root) p.ok else p.muted,
                filled = true,
                dot = true,
            )
            Spacer(Modifier.width(MaxSpace.sm))
            Text(
                stringResource(
                    if (root) R.string.ultra_cleaner_scope_root
                    else R.string.ultra_cleaner_scope_limited
                ),
                color = p.muted,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * صفّ فئة: اسم، وصف، مقاس، ومربّع تأشير.
 *
 * **والمقاس هو الحكم على الصفّ:** `null` (فشل قياس) أو `not measured yet` يُكتبان نصًّا، وفئة
 * حجمها صفر بطبيعتها (`EmptyFolders`) تُعرض **بالعدد** لا بـ`0 B` — لأن العدد هو الخبر.
 */
@Composable
private fun CategoryRow(
    category: UltraCleanCategory,
    measurement: CleanMeasurement?,
    checked: Boolean,
    measuredOnce: Boolean,
    onToggle: () -> Unit,
) {
    val p = neuralPalette()
    val sizeText = when {
        measurement == null -> if (measuredOnce) {
            stringResource(R.string.ultra_cleaner_unmeasured)
        } else {
            stringResource(R.string.ultra_cleaner_measuring)
        }

        measurement.bytes == null -> stringResource(R.string.ultra_cleaner_unmeasured)

        UltraCleanModel.showsCount(category) ->
            stringResource(R.string.ultra_cleaner_cat_empty) + " · " + measurement.count.toString()

        else -> StorageScanModel.formatBytes(measurement.bytes)
    }
    val measured = measurement?.bytes != null
    MaxRow(
        title = stringResource(categoryTitle(category)),
        subtitle = stringResource(categoryDescription(category)),
        onClick = onToggle,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    sizeText,
                    color = if (measured) p.text else p.muted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = if (measured) FontWeight.SemiBold else FontWeight.Normal,
                )
                // والمربّع يعكس الحالة ويقبل اللمس بنفسه — والصفّ كله يقبلها أيضًا، فلا منطقة
                // صغيرة تُطلب بدقّة.
                Checkbox(checked = checked, onCheckedChange = { onToggle() })
            }
        },
        iconTone = if (checked) MaxTone.Accent else MaxTone.Neutral,
    )
}

/**
 * الزرّ وسطر النتيجة.
 *
 * **والزرّ يحمل الرقم لا «تنظيف» فقط** (§6.2): ما سيُحذف هو مجموع المختار المقيس. وحين لا
 * مجموع (`0`) لا يُعرض `Clean 0 B` بل يُعطَّل الزرّ ويُقال «اختر فئة» — والزرّ المعطّل ليس
 * صامتًا: لونه يتغيّر وسببه مكتوب تحته.
 */
@Composable
private fun CleanActionPanel(
    state: UltraCleanerUiState,
    onClean: () -> Unit,
    onSelectSafe: () -> Unit,
    onMeasureAgain: () -> Unit,
    accent: Color,
) {
    val p = neuralPalette()
    val total = state.totalBytes
    val enabled = total > 0L && !state.cleaning
    val freed = state.freedBytes
    NeuralPanel(accent = if (enabled) accent else p.muted) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NeuralPill(
                text = if (total > 0L) {
                    stringResource(R.string.ultra_cleaner_clean_amount, StorageScanModel.formatBytes(total))
                } else {
                    stringResource(R.string.ultra_cleaner_clean_selected)
                },
                accent = if (enabled) accent else p.muted,
                filled = enabled,
                onClick = if (enabled) onClean else null,
            )
            Spacer(Modifier.width(MaxSpace.sm))
            NeuralPill(
                text = stringResource(R.string.ultra_cleaner_select_safe),
                accent = p.muted,
                compact = true,
                onClick = onSelectSafe,
            )
            Spacer(Modifier.width(MaxSpace.sm))
            NeuralPill(
                text = stringResource(R.string.ultra_cleaner_measure_again),
                accent = p.muted,
                compact = true,
                onClick = onMeasureAgain,
            )
        }
        // سطر الصدق: مجموع ناقص يُعلَن، ونتيجة التنظيف تُعلَن بالرقم المقيس فقط.
        if (state.totalIsPartial) {
            NeuralCaption(stringResource(R.string.ultra_cleaner_partial), color = p.warn)
        }
        if (freed != null) {
            NeuralCaption(
                if (freed > 0L) {
                    stringResource(R.string.ultra_cleaner_freed, StorageScanModel.formatBytes(freed))
                } else {
                    stringResource(R.string.ultra_cleaner_freed_none)
                },
                color = if (freed > 0L) p.ok else p.muted,
            )
        }
    }
}

/** عنوان الفئة — من سجلّ واحد، فلا نصّان لفئة واحدة. */
private fun categoryTitle(category: UltraCleanCategory): Int = when (category) {
    UltraCleanCategory.AppCaches -> R.string.ultra_cleaner_cat_app_caches
    UltraCleanCategory.SharedCaches -> R.string.ultra_cleaner_cat_shared
    UltraCleanCategory.Thumbnails -> R.string.ultra_cleaner_cat_thumbs
    UltraCleanCategory.InstallerFiles -> R.string.ultra_cleaner_cat_apk
    UltraCleanCategory.EmptyFolders -> R.string.ultra_cleaner_cat_empty
    UltraCleanCategory.SystemLogs -> R.string.ultra_cleaner_cat_logs
}

private fun categoryDescription(category: UltraCleanCategory): Int = when (category) {
    UltraCleanCategory.AppCaches -> R.string.ultra_cleaner_cat_app_caches_desc
    UltraCleanCategory.SharedCaches -> R.string.ultra_cleaner_cat_shared_desc
    UltraCleanCategory.Thumbnails -> R.string.ultra_cleaner_cat_thumbs_desc
    UltraCleanCategory.InstallerFiles -> R.string.ultra_cleaner_cat_apk_desc
    UltraCleanCategory.EmptyFolders -> R.string.ultra_cleaner_cat_empty_desc
    UltraCleanCategory.SystemLogs -> R.string.ultra_cleaner_cat_logs_desc
}
