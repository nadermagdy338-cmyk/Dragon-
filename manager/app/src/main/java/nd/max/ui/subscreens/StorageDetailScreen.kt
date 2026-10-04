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
 * شاشة التخزين.
 *
 * كانت تُبنى بلغة لوحة البداية (`DashCardWrapper` + `LiveHeader` + `GlowLinearBar`) لا
 * بلغة التطبيق: بطاقات متوهّجة بشبكة مختلفة، وعناوين إنجليزية صلبة داخل شاشة عربية
 * («Internal Data» · «GB total» · «used · free»)، وصفٌّ **لا يقول** أيّ مصرف أخذ المساحة.
 *
 * والآن تُبنى من نفس ما تُبنى منه بقية الشاشات: `MaxListScreen` + `MaxSection` +
 * `MaxGroup` + `MaxMetric`، فالشبكة والهوامش والخطوط تأتي من طبقة التصميم لا من هنا.
 *
 * وأُضيف ما يجعلها شاشة تخزين لا شاشة أرقام (على نمط `StorageAnalyzer` في SD Maid SE):
 *   ١. **العُقد (inodes)**: مساحة الأسماء لا البيانات — جهاز بمساحة حرّة يقبل ملفًا جديدًا
 *      أو لا يقبله، وهذا الرقم هو الفرق.
 *   ٢. **وضع التركيب**: هل المسار للقراءة فقط؟ سؤال يسبق كل محاولة كتابة في مدير الملفات.
 *   ٣. **تحليل المساحة بمصارف** يُشغّله المستخدم بنفسه — المسح طويل، فلا يُفرض في كل فتح.
 *   ٤. **أكبر العناصر** من المسح نفسه.
 *
 * وما لم يُقس يُعلَن: مجلدات لم تُقرأ، وسقف مسح بلغه البحث. ولا يُقدَّم ناتج ناقص كأنه
 * الشجرة كاملة (ADR-07).
 */
package nd.max.ui.subscreens

import android.content.ClipData
import android.os.Environment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nd.max.R
import nd.max.ui.component.MaxSurface
import nd.max.ui.design.MAX_VALUE_UNAVAILABLE
import nd.max.ui.design.MaxCondition
import nd.max.ui.design.MaxConditionKind
import nd.max.ui.design.MaxDataTrust
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxHelpAction
import nd.max.ui.design.MaxListScreen
import nd.max.ui.design.MaxMetric
import nd.max.ui.design.MaxMetricLine
import nd.max.ui.design.MaxMetricReadout
import nd.max.ui.design.MaxMetricSize
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxTone
import nd.max.ui.design.MaxUsageBar
import nd.max.ui.design.content
import nd.max.ui.component.MaxDeviceInfoShortcut
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.navigation.MaxNavActions
import nd.max.ui.theme.MonoValueStyleSmall
import nd.max.ui.util.MountInfo
import nd.max.ui.util.StorageBucket
import nd.max.ui.util.StorageBucketKind
import nd.max.ui.util.StorageScanModel
import nd.max.ui.util.StorageScanResult
import nd.max.ui.util.StorageUtil
import androidx.navigation.NavHostController
import kotlin.math.roundToInt

/** الأرقام لاتينية داخل جملة عربية: العلامتان تمنعان إعادة ترتيبها بصريًّا. */
private const val LTR = "\u200E"

private const val MOUNT_REFRESH_MS = 5_000L

/**
 * سقف المسح.
 *
 * قيمة عالية بما يكفي لشجرة `/sdcard` كاملة على هاتف ممتلئ، ومنخفضة بما يكفي ألا تتجمّد
 * الشاشة إن كان في المجلد ما هو أسوأ. وعند بلوغها يُعلَن التوقّف.
 */
private const val SCAN_ENTRY_CAP = 120_000

/** أين ينتهي «قريب من الامتلاء» ويبدأ «ممتلئ» — نفس سقوف الشاشة السابقة، لم تُغيَّر. */
private const val DANGER_FRACTION = 0.90f
private const val BUSY_FRACTION = 0.75f

@Composable
fun StorageDetailScreen(navController: NavHostController) {
    val context = LocalContext.current
    // موارد من `LocalResources.current`: نصوص تُقرأ داخل `scope.launch` (سياق غير composable)،
    // وهي تُبطل التركيب عند تغيّر التكوين بخلاف `LocalContext.current.resources`.
    val resources = LocalResources.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val navActions = MaxNavActions(navController)

    var mounts by remember { mutableStateOf<List<MountInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var apkBytes by remember { mutableStateOf<Long?>(null) }

    var scan by remember { mutableStateOf<StorageScanResult?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var scannedSoFar by remember { mutableIntStateOf(0) }

    val scanRoot = remember { Environment.getExternalStorageDirectory() }

    LaunchedEffect(Unit) {
        while (true) {
            val measured = withContext(Dispatchers.IO) {
                StorageUtil.readMounts() to StorageUtil.installedApkBytes(context)
            }
            mounts = measured.first
            apkBytes = measured.second
            isLoading = false
            delay(MOUNT_REFRESH_MS)
        }
    }

    // تُقبض في قيمة محلّية: `scan` خاصية مُفوَّضة، فلا يُسمح بتحويل ضمني (smart cast)
    // عليها داخل `when` — وهو ما يمنع قراءة `scan.scannedEntries` مباشرة.
    val scanResult = scan
    val primary = mounts.firstOrNull { it.path == "/data" }
        ?: mounts.firstOrNull { it.isMeasured }
        ?: mounts.firstOrNull()
    val usedFraction = primary?.usedFraction ?: 0f
    val accent = MaterialTheme.colorScheme.tertiary
    val usedTone = when {
        usedFraction >= DANGER_FRACTION -> MaxTone.Critical
        usedFraction >= BUSY_FRACTION -> MaxTone.Caution
        else -> MaxTone.Accent
    }
    val capacityTitle = when {
        primary == null -> stringResource(R.string.detail_storage_unavailable)
        usedFraction >= DANGER_FRACTION -> stringResource(R.string.detail_storage_nearly_full)
        usedFraction >= BUSY_FRACTION -> stringResource(R.string.detail_storage_busy)
        else -> stringResource(R.string.detail_storage_headroom)
    }

    // حالة غير جاهزة تُعلَن بلغة الحالات المشتركة، لا ببطاقة محلية: هذه الشاشة تُفتح من
    // اللوحة، ومن حقّ من فتحها أن يعرف أيّ الفشلين وقع — «لم تُقرأ بعد» أم «لا تُقرأ».
    val condition = when {
        isLoading -> MaxCondition(
            kind = MaxConditionKind.Loading,
            title = stringResource(R.string.detail_storage),
            detail = stringResource(R.string.detail_storage_reading_mounts)
        )

        primary == null -> MaxCondition(
            kind = MaxConditionKind.Unavailable,
            title = stringResource(R.string.detail_storage_unavailable),
            detail = stringResource(R.string.detail_no_filesystem_stats)
        )

        else -> null
    }

    MaxListScreen(
        title = stringResource(R.string.detail_storage),
        subtitle = stringResource(R.string.detail_filesystem_view),
        onBack = { navController.popBackStack() },
        accentIcon = Icons.Rounded.Storage,
        accent = accent,
        condition = condition,
        snackbarHostState = snackbarHostState,
        actions = {
            MaxHelpAction(
                title = stringResource(R.string.detail_storage),
                body = stringResource(R.string.detail_storage_note)
            )
        },
        header = {
            if (primary != null) {
                StorageHeadline(
                    mount = primary,
                    usedFraction = usedFraction,
                    usedTone = usedTone,
                    title = capacityTitle,
                    source = primary.path
                )
            }
        }
    ) {
        if (primary != null) {
            item(key = "storage_overview") {
                // وزرّ قسم التخزين في «معلومات الجهاز» في آخر بطاقة النظرة العامة — أوّل
                // بطاقة في الشاشة — كبسولة بأيقونة وكلمة لا أيقونة مجرّدة (أمر المالك).
                MaxSection(title = stringResource(R.string.detail_storage_overview)) {
                    MaxGroup {
                        MaxMetricLine(
                            metric = MaxMetric(
                                label = stringResource(R.string.detail_storage_total),
                                value = StorageUtil.formatBytes(primary.totalBytes) ?: MAX_VALUE_UNAVAILABLE,
                                trust = if (primary.totalBytes != null) MaxDataTrust.Live else MaxDataTrust.Unreadable,
                                source = primary.path
                            )
                        )
                        MaxGroupDivider()
                        MaxMetricLine(
                            metric = MaxMetric(
                                label = stringResource(R.string.detail_used),
                                value = StorageUtil.formatBytes(primary.usedBytes) ?: MAX_VALUE_UNAVAILABLE,
                                trust = if (primary.usedBytes != null) MaxDataTrust.Live else MaxDataTrust.Unreadable,
                                source = primary.path
                            )
                        )
                        MaxGroupDivider()
                        MaxMetricLine(
                            metric = MaxMetric(
                                label = stringResource(R.string.detail_free),
                                value = StorageUtil.formatBytes(primary.freeBytes) ?: MAX_VALUE_UNAVAILABLE,
                                trust = if (primary.freeBytes != null) MaxDataTrust.Live else MaxDataTrust.Unreadable,
                                source = primary.path
                            )
                        )
                        MaxGroupDivider()
                        // العُقد: أسماء الملفات لا محتواها. جهاز بغيغابايت حرّة قد يرفض ملفًّا
                        // جديدًا لأن أسماءه نفدت، وهذا الرقم هو ما يكشف ذلك.
                        MaxMetricLine(
                            metric = inodesMetric(primary, stringResource(R.string.detail_storage_inodes))
                        )
                        MaxGroupDivider()
                        MaxMetricLine(
                            metric = MaxMetric(
                                label = stringResource(R.string.detail_storage_filesystem),
                                value = primary.fileSystem.ifBlank { MAX_VALUE_UNAVAILABLE },
                                trust = if (primary.fileSystem.isBlank()) MaxDataTrust.Unreadable else MaxDataTrust.Live,
                                source = "/proc/mounts"
                            )
                        )
                        MaxGroupDivider()
                        MaxMetricLine(
                            metric = MaxMetric(
                                label = stringResource(R.string.detail_storage_mount_mode),
                                value = stringResource(
                                    if (primary.readOnly) R.string.detail_storage_read_only
                                    else R.string.detail_storage_read_write
                                ),
                                trust = MaxDataTrust.Live,
                                source = "/proc/mounts"
                            )
                        )
                        MaxGroupDivider()
                        MaxDeviceInfoShortcut(navController, MaxDestination.StorageDetail)
                    }
                }
            }

            item(key = "storage_breakdown") {
                MaxSection(
                    title = stringResource(R.string.detail_storage_breakdown),
                    description = stringResource(R.string.detail_storage_breakdown_desc)
                ) {
                    MaxGroup {
                        MaxRow(
                            title = if (scanning) {
                                stringResource(R.string.detail_storage_scan_running, scannedSoFar)
                            } else {
                                stringResource(R.string.detail_storage_scan_action)
                            },
                            subtitle = when {
                                scanning -> scanRoot.absolutePath
                                scanResult == null ->
                                    stringResource(R.string.detail_storage_scan_scope, scanRoot.absolutePath)
                                else -> stringResource(R.string.detail_storage_scan_done, scanResult.scannedEntries)
                            },
                            icon = Icons.Rounded.TravelExplore,
                            iconTone = MaxTone.Accent,
                            enabled = !scanning,
                            onClick = {
                                scope.launch {
                                    scanning = true
                                    scannedSoFar = 0
                                    val result = withContext(Dispatchers.IO) {
                                        StorageUtil.scan(roots = listOf(scanRoot)) { scannedSoFar = it }
                                    }
                                    scan = result
                                    scanning = false
                                }
                            },
                            trailing = {
                                if (scanning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(MaxSpace.xl),
                                        strokeWidth = MaxSize.activeRing
                                    )
                                }
                            }
                        )

                        val result = scanResult
                        if (result != null) {
                            if (result.buckets.isNotEmpty()) {
                                MaxGroupDivider()
                                result.buckets.forEachIndexed { index, bucket ->
                                    if (index > 0) MaxGroupDivider()
                                    BucketRow(bucket = bucket, measured = result.measuredBytes)
                                }
                            }
                            val apk = apkBytes
                            if (apk != null) {
                                MaxGroupDivider()
                                MaxMetricLine(
                                    metric = MaxMetric(
                                        label = stringResource(R.string.detail_storage_packages),
                                        value = StorageScanModel.formatBytes(apk),
                                        trust = MaxDataTrust.Live,
                                        source = "PackageManager",
                                        note = stringResource(R.string.detail_storage_packages_note)
                                    )
                                )
                            }
                            if (result.skippedDirectories > 0) {
                                MaxGroupDivider()
                                MaxRow(
                                    title = stringResource(
                                        R.string.detail_storage_scan_skipped,
                                        result.skippedDirectories
                                    ),
                                    icon = Icons.Rounded.Info,
                                    iconTone = MaxTone.Caution
                                )
                            }
                            if (result.truncated) {
                                MaxGroupDivider()
                                MaxRow(
                                    title = stringResource(R.string.detail_storage_scan_truncated, SCAN_ENTRY_CAP),
                                    icon = Icons.Rounded.Info,
                                    iconTone = MaxTone.Caution
                                )
                            }
                            if (result.isEmpty) {
                                MaxGroupDivider()
                                MaxRow(
                                    title = stringResource(R.string.detail_storage_scan_empty),
                                    subtitle = stringResource(R.string.detail_storage_scan_empty_desc),
                                    icon = Icons.Rounded.Info,
                                    iconTone = MaxTone.Neutral
                                )
                            }
                        }
                    }
                }
            }

            val largest = scanResult?.largest.orEmpty()
            if (largest.isNotEmpty()) {
                item(key = "storage_largest") {
                    MaxSection(
                        title = stringResource(R.string.detail_storage_largest),
                        description = stringResource(R.string.detail_storage_largest_desc)
                    ) {
                        MaxGroup {
                            largest.forEachIndexed { index, item ->
                                if (index > 0) MaxGroupDivider()
                                MaxRow(
                                    title = item.name,
                                    subtitle = item.path.substringBeforeLast('/', "").ifBlank { "/" },
                                    icon = Icons.Rounded.Folder,
                                    iconTone = MaxTone.Neutral,
                                    onClick = {
                                        // النسخ لا الفتح: مسار مدير الملفات يُدار من سجلّ
                                        // الوجهات وحده، وفتحُ مجلد بعينه يحتاج وجهةً بمعامل
                                        // — وهي قرار سجلّ لا قرار شاشة. والنتيجة تُقاس:
                                        // الحافظة قد ترفض، فلا يُقال «نُسخ» بلا قياس.
                                        scope.launch {
                                            val copied = runCatching {
                                                clipboard.setClipEntry(
                                                    ClipEntry(
                                                        ClipData.newPlainText(
                                                            resources.getString(R.string.max_files_detail_copy_label),
                                                            item.path
                                                        )
                                                    )
                                                )
                                            }.isSuccess
                                            snackbarHostState.showSnackbar(
                                                resources.getString(
                                                    if (copied) R.string.max_files_detail_copied
                                                    else R.string.max_files_detail_copy_failed
                                                )
                                            )
                                        }
                                    },
                                    trailing = {
                                        Text(
                                            text = StorageScanModel.formatBytes(item.bytes),
                                            style = MonoValueStyleSmall
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item(key = "storage_mounts") {
                MaxSection(
                    title = stringResource(R.string.detail_mount_views),
                    description = stringResource(R.string.detail_mount_views_desc)
                ) {
                    MaxGroup {
                        mounts.forEachIndexed { index, mount ->
                            if (index > 0) MaxGroupDivider()
                            MountBlock(mount)
                        }
                    }
                }
            }

            item(key = "storage_note") {
                MaxRow(
                    title = stringResource(R.string.detail_how_to_read),
                    subtitle = stringResource(R.string.detail_storage_note),
                    icon = Icons.Rounded.Info
                )
            }

            item(key = "storage_files") {
                MaxSection(title = stringResource(R.string.detail_storage_tools)) {
                    MaxGroup {
                        MaxRow(
                            title = stringResource(R.string.max_files_title),
                            subtitle = stringResource(R.string.detail_storage_open_files_desc),
                            icon = Icons.Rounded.Folder,
                            iconTone = MaxTone.Accent,
                            onClick = { navActions.navigateTo(MaxDestination.FileManager) }
                        )
                    }
                }
            }
        }
    }
}

/** القراءة الكبيرة: النسبة أولًا، ثم الشريط، ثم الجملة التي تفسّرها. */
@Composable
private fun StorageHeadline(
    mount: MountInfo,
    usedFraction: Float,
    usedTone: MaxTone,
    title: String,
    source: String
) {
    MaxSurface(accent = usedTone.content()) {
        MaxMetricReadout(
            metric = MaxMetric(
                // العنوان هو الذي وصل من الشاشة («ممتلئ»/«متّسع»)، لا كلمة إنجليزية
                // مكتوبة هنا — وهذا بالضبط ما كان يظهر للمستخدم في النسخة القديمة.
                label = title,
                value = (usedFraction * 100f).roundToInt().toString(),
                unit = "%",
                trust = MaxDataTrust.Live,
                source = source
            ),
            size = MaxMetricSize.Large
        )
        Spacer(Modifier.height(MaxSpace.md))
        MaxUsageBar(fraction = usedFraction, tone = usedTone)
        Spacer(Modifier.height(MaxSpace.md))
        Text(
            text = stringResource(
                R.string.detail_storage_summary,
                "$LTR${StorageUtil.formatBytes(mount.usedBytes) ?: MAX_VALUE_UNAVAILABLE}$LTR",
                "$LTR${StorageUtil.formatBytes(mount.totalBytes) ?: MAX_VALUE_UNAVAILABLE}$LTR"
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun inodesMetric(mount: MountInfo, label: String): MaxMetric {
    val total = mount.inodesTotal
    val free = mount.inodesFree
    return if (total == null || free == null) {
        MaxMetric(
            label = label,
            value = MAX_VALUE_UNAVAILABLE,
            trust = MaxDataTrust.Unreadable,
            source = mount.path,
            note = stringResource(R.string.detail_storage_inodes_unreadable)
        )
    } else {
        val used = (total - free).coerceAtLeast(0L)
        MaxMetric(
            label = label,
            value = StorageScanModel.percentOf(used, total).toString(),
            unit = "%",
            trust = MaxDataTrust.Live,
            source = mount.path,
            note = stringResource(R.string.detail_storage_inodes_note, free)
        )
    }
}

@Composable
private fun BucketRow(bucket: StorageBucket, measured: Long) {
    MaxMetricLine(
        metric = MaxMetric(
            label = bucketLabel(bucket.kind),
            value = StorageScanModel.formatBytes(bucket.bytes),
            trust = MaxDataTrust.Snapshot,
            note = stringResource(
                R.string.detail_storage_bucket_note,
                bucket.files,
                StorageScanModel.percentOf(bucket.bytes, measured)
            )
        )
    )
}

@Composable
private fun MountBlock(mount: MountInfo) {
    val tone = when {
        !mount.isMeasured -> MaxTone.Neutral
        mount.usedFraction >= DANGER_FRACTION -> MaxTone.Critical
        mount.usedFraction >= BUSY_FRACTION -> MaxTone.Caution
        else -> MaxTone.Accent
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = MaxSpace.rowPaddingHorizontal,
                vertical = MaxSpace.rowPaddingVertical
            ),
        verticalArrangement = Arrangement.spacedBy(MaxSpace.sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaxSpace.hairline)
            ) {
                Text(mountTitle(mount), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = buildString {
                        append("$LTR${mount.path}$LTR")
                        if (mount.fileSystem.isNotBlank()) append(" · ${mount.fileSystem}")
                        append(
                            " · " + stringResource(
                                if (mount.readOnly) R.string.detail_storage_read_only
                                else R.string.detail_storage_read_write
                            )
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = if (mount.isMeasured) {
                    "${(mount.usedFraction * 100f).roundToInt()}%"
                } else {
                    MAX_VALUE_UNAVAILABLE
                },
                style = MonoValueStyleSmall,
                color = if (mount.isMeasured) tone.content() else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        MaxUsageBar(fraction = mount.usedFraction, tone = tone)
        Text(
            text = stringResource(
                R.string.detail_storage_mount_facts,
                "$LTR${StorageUtil.formatBytes(mount.usedBytes) ?: MAX_VALUE_UNAVAILABLE}$LTR",
                "$LTR${StorageUtil.formatBytes(mount.freeBytes) ?: MAX_VALUE_UNAVAILABLE}$LTR",
                "$LTR${StorageUtil.formatBytes(mount.totalBytes) ?: MAX_VALUE_UNAVAILABLE}$LTR"
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** اسم النقطة بلغة المستخدم؛ وما لا اسم له يُعرض بمساره — ولا يُخترع له اسم. */
@Composable
private fun mountTitle(mount: MountInfo): String = when (mount.path) {
    "/data" -> stringResource(R.string.detail_mount_data)
    "/sdcard", "/storage/emulated/0" -> stringResource(R.string.detail_mount_internal)
    "/system" -> stringResource(R.string.detail_mount_system)
    "/system_ext" -> stringResource(R.string.detail_mount_system_ext)
    "/vendor" -> stringResource(R.string.detail_mount_vendor)
    "/product" -> stringResource(R.string.detail_mount_product)
    "/cache" -> stringResource(R.string.detail_mount_cache)
    "/metadata" -> stringResource(R.string.detail_mount_metadata)
    "/persist" -> stringResource(R.string.detail_mount_persist)
    else -> mount.path
}

@Composable
private fun bucketLabel(kind: StorageBucketKind): String = stringResource(
    when (kind) {
        StorageBucketKind.Apps -> R.string.detail_bucket_apps
        StorageBucketKind.Images -> R.string.detail_bucket_images
        StorageBucketKind.Video -> R.string.detail_bucket_video
        StorageBucketKind.Audio -> R.string.detail_bucket_audio
        StorageBucketKind.Documents -> R.string.detail_bucket_documents
        StorageBucketKind.Archives -> R.string.detail_bucket_archives
        StorageBucketKind.Other -> R.string.detail_bucket_other
    }
)
