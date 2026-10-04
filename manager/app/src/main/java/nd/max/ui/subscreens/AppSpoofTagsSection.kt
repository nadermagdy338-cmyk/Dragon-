/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.ui.subscreens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import nd.max.R
import nd.max.core.spoof.AppSpoofProfile
import nd.max.core.spoof.CopgTag
import nd.max.core.spoof.CopgTier
import nd.max.ui.component.MaxInfoStrip
import nd.max.ui.design.MaxCollapsibleGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSection
import nd.max.ui.viewmodel.SpoofStudioViewModel

/** صفّ واحد من خيارات COPG: المفتاح، العنوان، الأيقونة، ومُنشئ الوسم (قيمة اختيارية). */
private class TagEntry(
    val key: String,
    val titleRes: Int,
    val icon: ImageVector,
    val valued: Boolean,
    val make: (String?) -> CopgTag?,
)

private fun flag(key: String, titleRes: Int, icon: ImageVector, tag: CopgTag) =
    TagEntry(key, titleRes, icon, valued = false) { tag }

private fun valued(key: String, titleRes: Int, icon: ImageVector, build: (String) -> CopgTag) =
    TagEntry(key, titleRes, icon, valued = true) { value -> value?.let(build) }

/** ترتيب العرض: الراحة (تعمل في كل الإصدارات) ثم الخصوصية ثم العتاد ثم المعرّفات ثم الشبكة. */
private val ENTRIES: List<TagEntry> = listOf(
    flag("dnd", R.string.copg_tag_dnd, Icons.Rounded.Tune, CopgTag.DoNotDisturb),
    flag("dab", R.string.copg_tag_dab, Icons.Rounded.Tune, CopgTag.DisableAutoBrightness),
    flag("kso", R.string.copg_tag_kso, Icons.Rounded.Tune, CopgTag.KeepScreenOn),
    flag("nolog", R.string.copg_tag_nolog, Icons.Rounded.Tune, CopgTag.NoLog),
    valued("dpi", R.string.copg_tag_dpi, Icons.Rounded.Tune) { v -> v.toIntOrNull()?.let { CopgTag.ScreenDpi(it) } ?: CopgTag.Unknown("dpi=$v") },
    flag("vpn", R.string.copg_tag_vpn, Icons.Rounded.Visibility, CopgTag.HideVpn),
    flag("vpns", R.string.copg_tag_vpns, Icons.Rounded.Visibility, CopgTag.HideVpnStrict),
    flag("hidedev", R.string.copg_tag_hidedev, Icons.Rounded.Visibility, CopgTag.HideDeveloper),
    flag("mock", R.string.copg_tag_mock, Icons.Rounded.Visibility, CopgTag.MockLocationHide),
    valued("cpu", R.string.copg_tag_cpu, Icons.Rounded.Memory) { CopgTag.Cpu(it) },
    flag("blocked", R.string.copg_tag_blocked, Icons.Rounded.Memory, CopgTag.BlockCpu),
    valued("gpu", R.string.copg_tag_gpu, Icons.Rounded.Memory) { CopgTag.Gpu(it) },
    flag("cow", R.string.copg_tag_cow, Icons.Rounded.Memory, CopgTag.PropCow),
    flag("serial", R.string.copg_tag_serial, Icons.Rounded.Fingerprint, CopgTag.PerAppSerial),
    flag("aid", R.string.copg_tag_aid, Icons.Rounded.Fingerprint, CopgTag.AndroidId),
    flag("gaid", R.string.copg_tag_gaid, Icons.Rounded.Fingerprint, CopgTag.AdvertisingId),
    flag("appset", R.string.copg_tag_appset, Icons.Rounded.Fingerprint, CopgTag.AppSetId),
    flag("imei", R.string.copg_tag_imei, Icons.Rounded.Fingerprint, CopgTag.Imei),
    flag("drm", R.string.copg_tag_drm, Icons.Rounded.Fingerprint, CopgTag.Widevine),
    valued("tz", R.string.copg_tag_tz, Icons.Rounded.Wifi) { CopgTag.Timezone(it) },
    valued("lang", R.string.copg_tag_lang, Icons.Rounded.Wifi) { CopgTag.Language(it) },
    valued("sim", R.string.copg_tag_sim, Icons.Rounded.Wifi) { CopgTag.Sim(it) },
    valued("simx", R.string.copg_tag_simx, Icons.Rounded.Wifi) { CopgTag.SimAggressive(it) },
    valued("ua", R.string.copg_tag_ua, Icons.Rounded.Wifi) { CopgTag.UserAgent(it) },
)

private class TagGroup(val titleRes: Int, val keys: List<String>)

/** خمس مجموعات بدل قائمة مسطّحة من 24 صفًّا: الراحة ثم الخصوصية ثم العتاد ثم المعرّفات ثم الشبكة. */
private val GROUPS: List<TagGroup> = listOf(
    TagGroup(R.string.copg_group_comfort, listOf("dnd", "dab", "kso", "nolog", "dpi")),
    TagGroup(R.string.copg_group_privacy, listOf("vpn", "vpns", "hidedev", "mock")),
    TagGroup(R.string.copg_group_hardware, listOf("cpu", "blocked", "gpu", "cow")),
    TagGroup(R.string.copg_group_ids, listOf("serial", "aid", "gaid", "appset", "imei", "drm")),
    TagGroup(R.string.copg_group_network, listOf("tz", "lang", "sim", "simx", "ua")),
)

/**
 * خيارات COPG لتطبيق — **الغرفة المشتركة**: يرسمها `AppSpoofSection` نفسه، فيراها المستخدم بلا فرق من
 * استوديو التزييف أو من تبويب «تزييف» في إعدادات التطبيق، وكلاهما يكتب عبر `SpoofStudioViewModel.setTag`.
 *
 * - **مطوية افتراضيًّا:** كل مجموعة تقول في سطر حالتها كم خيارًا فعّلت، فلا يحتاج المستخدم فتحها ليعرف.
 *   والمجموعة التي فيها خيار مفعّل تُفتح من البداية.
 * - **كل صفّ يقول الحقيقة:** `PRO` عند COPG (نكتب الوسم والمحرّك وحده يقرّر الترخيص)، و«يبقى في ذاكرة
 *   التطبيق» للوسوم المقيمة — وهذه لا تُفعَّل قبل إقرار المخاطر المسجَّل لهذا التطبيق.
 * - **لا ادّعاء أثر:** الحفظ نيّة، والكتابة إلى المحرّك تمرّ بالمعاملة المعتادة ثم قراءة بعد الكتابة.
 */
@Composable
internal fun AppSpoofTagsSection(
    packageName: String,
    policy: AppSpoofProfile,
    hasDevice: Boolean,
    acknowledged: Boolean,
    busy: Boolean,
    viewModel: SpoofStudioViewModel,
) {
    var editing by remember(packageName) { mutableStateOf<TagEntry?>(null) }
    val proLabel = stringResource(R.string.copg_tier_pro)
    val residentLabel = stringResource(R.string.copg_res_short)
    val needAck = stringResource(R.string.copg_need_ack)
    val byKey = remember { ENTRIES.associateBy { it.key } }
    MaxSection(title = stringResource(R.string.copg_tags_title_short), description = stringResource(R.string.copg_tags_desc_short)) {
        if (!hasDevice) MaxInfoStrip(text = stringResource(R.string.copg_tag_needs_device))
        GROUPS.forEach { group ->
            val entries = group.keys.mapNotNull(byKey::get)
            val enabledCount = entries.count { e -> policy.tags.any { it.substringBefore('=') == e.key } }
            MaxCollapsibleGroup(
                title = stringResource(group.titleRes),
                summary = if (enabledCount == 0) stringResource(R.string.copg_group_none)
                else stringResource(R.string.copg_group_n, enabledCount),
                initiallyExpanded = enabledCount > 0,
            ) {
                entries.forEachIndexed { index, entry ->
                    if (index > 0) MaxGroupDivider()
                    val current = policy.tags.firstOrNull { it.substringBefore('=') == entry.key }
                    val on = current != null
                    val sample = entry.make(if (entry.valued) "x" else null)
                    val resident = sample?.riskyForAntiCheat == true
                    val blockedByRisk = resident && !acknowledged && !on
                    val subtitle = listOfNotNull(
                        current?.substringAfter('=', "")?.takeIf { it.isNotEmpty() },
                        proLabel.takeIf { sample?.tier == CopgTier.PRO },
                        residentLabel.takeIf { resident },
                        needAck.takeIf { blockedByRisk },
                    ).joinToString(" · ").ifEmpty { null }
                    MaxRow(
                        title = stringResource(entry.titleRes),
                        subtitle = subtitle,
                        enabled = !busy && !blockedByRisk,
                        onClick = {
                            when {
                                on && !entry.valued -> toggle(viewModel, packageName, entry, null, false)
                                entry.valued -> editing = entry
                                else -> toggle(viewModel, packageName, entry, null, true)
                            }
                        },
                        trailing = { Switch(checked = on, onCheckedChange = null, enabled = !busy && !blockedByRisk) },
                    )
                }
            }
        }
    }
    editing?.let { entry ->
        val existing = policy.tags.firstOrNull { it.substringBefore('=') == entry.key }?.substringAfter('=', "").orEmpty()
        var text by remember(entry.key) { mutableStateOf(existing) }
        val valid = text.isNotEmpty() && CopgTag.safeValue(text) && entry.make(text).let { it != null && it !is CopgTag.Unknown }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(entry.titleRes)) },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it.take(60) }, singleLine = true,
                    label = { Text(stringResource(R.string.copg_tag_value_hint)) },
                    isError = text.isNotEmpty() && !valid,
                    supportingText = { if (text.isNotEmpty() && !valid) Text(stringResource(R.string.copg_tag_bad_value)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(enabled = valid && !busy, onClick = {
                    toggle(viewModel, packageName, entry, text, true); editing = null
                }) { Text(stringResource(R.string.copg_tag_save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (existing.isNotEmpty()) toggle(viewModel, packageName, entry, existing, false)
                    editing = null
                }) { Text(stringResource(if (existing.isNotEmpty()) R.string.copg_tag_remove else R.string.spoof_cancel)) }
            },
        )
    }
}

private fun toggle(viewModel: SpoofStudioViewModel, pkg: String, entry: TagEntry, value: String?, enabled: Boolean) {
    val tag = entry.make(value) ?: return
    viewModel.setTag(pkg, tag, enabled)
}
