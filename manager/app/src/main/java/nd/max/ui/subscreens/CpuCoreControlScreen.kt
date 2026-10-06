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

@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package nd.max.ui.subscreens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay
import nd.max.R
import nd.max.ui.component.*
import nd.max.ui.design.MaxAlpha
import nd.max.ui.design.MaxBullets
import nd.max.ui.design.MaxCardShell
import nd.max.ui.design.MaxCondition
import nd.max.ui.design.MaxConditionKind
import nd.max.ui.design.FloatingNoticeDwellMillis
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxHelpAction
import nd.max.ui.design.MaxListScreen
import nd.max.ui.design.MaxMetric
import nd.max.ui.design.MaxMetricLine
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSliderRow
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxSwitchRow
import nd.max.ui.design.MaxTone
import nd.max.ui.mainscreens.IconBadge
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.util.CpuTopologyUtil
import nd.max.ui.viewmodel.CpuActionNotice
import nd.max.ui.viewmodel.CpuActionReason
import nd.max.ui.viewmodel.CpuCoreControlViewModel
import nd.max.ui.viewmodel.CpuCoreRow
import nd.max.ui.viewmodel.CpuFrequencyControlState

/** Nodes this screen reads; shown as machine truth on condition panels. */
private const val CPU_SOURCES = "/sys/devices/system/cpu"

/** Keeps core counters left-to-right inside an RTL layout. */
private const val LTR_MARK = "\u200E"

@Composable
fun CpuCoreControlScreen(
    navController: NavHostController,
    viewModel: CpuCoreControlViewModel = hiltViewModel()
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val accent = colorScheme.primary
    val screenTitle = stringResource(R.string.cpu_core_control_title)

    LaunchedEffect(Unit) { viewModel.loadState(context) }

    // The old screen opened with an AI banner, an insight paragraph and a
    // duplicate "CORE GRID / Manual Core Control" heading before any control,
    // then repeated the online count in a decision card that the hero already
    // showed. The chrome is now the title bar, and the safety note is in help.
    val condition = when (viewModel.isAvailable) {
        null -> MaxCondition(
            kind = MaxConditionKind.Loading,
            title = screenTitle,
            detail = stringResource(R.string.cpu_core_probe_detail),
            technicalDetail = CPU_SOURCES
        )

        false -> MaxCondition(
            kind = MaxConditionKind.Unsupported,
            title = screenTitle,
            detail = stringResource(R.string.cpu_core_unavailable),
            technicalDetail = CPU_SOURCES
        )

        else -> null
    }

    // **نتيجة الإجراء عائمة في أسفل الشاشة لا شريطًا في أوّلها.** الزرّ الذي يُنشئ النتيجة
    // («تطبيق» · «استعادة») في أسفل الصفحة، وكان الإشعار يعرض في `item(key = "max_banner")`
    // — أوّل عنصر قبل الرأس — فيظهر التأكيد على بُعد شاشة من العين التي طلبتها. والعرض الآن
    // نافذة عائمة تُركَّب فوق أسفل القائمة، والصفحة تحجز لها فراغها بارتفاعها المقيس فلا
    // يُغطّى صفّ (انظر `MaxFloatingNoticeHost`).
    //
    // والحالة تأتي من رمز نتيجة الإجراء نفسه، لا من صياغة النصّ: كتابة مؤجّلة تقول
    // «محفوظ ولم يُطبَّق» ولا يجوز أن تكون خضراء.
    val actionNotice = viewModel.actionNotice
    val floatingNotice = actionNotice?.let { notice ->
        cpuActionNotice(notice, viewModel::consumeActionNotice)
    }

    // **تأكيد النجاح يزول من نفسه، وما يحتاج إقرارًا لا.** النافذة العائمة لا تحجب شيئًا،
    // لكنها تحجز فراغًا في أسفل الصفحة؛ وتأكيد النجاح خبرٌ انتهى أمره — الحقيقة كاملة في
    // صفوف العنقود أعلاه (الحدود الحيّة وسطور التحقّق)، فمهلته [FloatingNoticeDwellMillis]
    // ولا معلومة تُفقد بزواله. أمّا الفشل والانتظار و«غير مدعوم» فتبقى حتى يغلقها القارئ
    // بنفسه، فلا يمرّ عطب بصريًّا في ومضة. والإغلاق هو إجراء الإشعار نفسه (زرّ الإغلاق في
    // النافذة والزرّ الذي مرّرته الشاشة نداء واحد)، فلا سلوك ثانٍ يُبنى لهذه الحالة.
    LaunchedEffect(actionNotice) {
        if (actionNotice != null && cpuActionKind(actionNotice.reason) == MaxConditionKind.Applied) {
            delay(FloatingNoticeDwellMillis)
            viewModel.consumeActionNotice()
        }
    }

    ScreenAccentProvider(accent) {
        MaxListScreen(
            title = screenTitle,
            subtitle = stringResource(R.string.cpu_core_control_subtitle),
            onBack = { navController.popBackStack() },
            accentIcon = Icons.Outlined.Memory,
            accent = accent,
            condition = condition,
            floatingNotice = floatingNotice,
            actions = {
                MaxHelpAction(
                    title = screenTitle,
                    body = stringResource(R.string.cpu_core_safety_note)
                )
            },
            // اختصار Max AI **أولًا وفي مكان ثابت**: `manual = true` لأن سقوف التردّد التي
            // تُكتب هنا هي نفس المقابض التي يكتبها المحرّك (`ControlRegistry` ← `CPU_FREQUENCY`)،
            // فلا يُوعَد المستخدم بشيء غير مقيس.
            header = {
                MaxAiShortcut(navController = navController, manual = true)
            }
        ) {
            /*
             * **البطل الجديد: التردد الحي (`MAX-MANAGER-LEVEL-UP.md` §8.1).**
             *
             * وكان الأوّل خريطةَ ألقاب (`CPU0 Efficiency…`) بلا رقم واحد حيّ — وهي تجيب سؤالًا
             * لم يسأله أحد عند فتح الشاشة. والبطل الآن يجيب السؤال المفروض: «ما تردد كل نواة الآن؟»
             * — ولا تُحذف الخريطة، بل تنتقل إلى ما بعد الترددات (خريطة هويّة لا بطلًا).
             */
            item {
                CpuLiveClockHero(
                    coreRows = viewModel.coreRows,
                    coreFreqMhz = viewModel.coreFreqMhz,
                    clusterMaxFreqMhz = viewModel.clusterMaxFreqMhz,
                )
            }

            item {
                CpuLiveClockGrid(
                    clusters = viewModel.clusters,
                    coreRows = viewModel.coreRows,
                    coreFreqMhz = viewModel.coreFreqMhz,
                    coreFreqHistory = viewModel.coreFreqHistory,
                    clusterMaxFreqMhz = viewModel.clusterMaxFreqMhz,
                )
            }

            item {
                CpuHeroCard(
                    chipsetName = viewModel.chipsetName,
                    coreRows = viewModel.coreRows,
                    trailing = {
                        // وبطاقة المعالج بلا حاوية تحشوها (عمودٌ على الصفحة)، فحاشية الباب
                        // صفر ويُحاذى على حاشية الصفحة كبقيّة محتواها.
                        MaxDeviceInfoShortcut(
                            navController = navController,
                            from = MaxDestination.CpuCoreControl,
                            inset = 0.dp,
                        )
                    },
                )
            }

            item {
                CpuFrequencyControlSection(
                    clusters = viewModel.clusters,
                    controls = viewModel.frequencyControls,
                    hasSessionChanges = viewModel.hasSessionFrequencyChanges,
                    onApply = viewModel::applyFrequencyLimits,
                    onRestore = viewModel::resetFrequencyLimits,
                    onRestoreSession = viewModel::restoreSessionFrequencyLimits
                )
            }

            item {
                CpuCoresSection(
                    clusters = viewModel.clusters,
                    coreRows = viewModel.coreRows,
                    clusterMaxFreqMhz = viewModel.clusterMaxFreqMhz
                )
            }

            item {
                MaxSection(title = stringResource(R.string.cpu_core_quick_title)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm)
                    ) {
                        CpuCoreControlViewModel.QUICK_CONFIGS.forEach { config ->
                            CoreQuickConfigTile(
                                modifier = Modifier.weight(1f),
                                icon = quickConfigIcon(config.id),
                                // **النصّ من الموارد التي يحملها النموذج نفسه** — كان الإعداد يحمل
                                // اسمي الموردين ثم تُكتب النصوص بيدها بالإنجليزية
                                // (`quickConfigLabel`/`quickConfigDesc`) ولا تُترجم أبدًا، مع عكس معنى
                                // `balanced` («Top cluster» والمقصود «العنقود الأعلى معطّل»).
                                label = stringResource(config.labelRes),
                                description = stringResource(config.descRes),
                                accent = quickConfigAccent(config.id),
                                // الحالة المقروءة من الحالة لا من نيّة محليّة: الصفّ يعلن
                                // أي نمط جرّبه المستخدم آخر مرة.
                                selected = viewModel.appliedQuickConfig == config.id,
                                onClick = { viewModel.applyQuickConfig(config.id) }
                            )
                        }
                    }
                }
            }

            item {
                CpuManualControlCard(
                    enabled = viewModel.manualControlEnabled,
                    onEnabledChange = viewModel::setManualControlEnabled
                )
            }

            viewModel.clusters.forEach { cluster ->
                val rows = viewModel.coreRows
                    .filter { it.cluster.policyPath == cluster.policyPath }

                item {
                    MaxSection(title = clusterDisplayName(cluster)) {
                        ClusterCoreSummary(cluster = cluster, rows = rows)
                        ScreenAccentProvider(clusterAccent(cluster)) {
                            ExpressiveList(
                                content = rows.map { row ->
                                    {
                                        CoreRowItem(
                                            row = row,
                                            enabled = viewModel.manualControlEnabled && !row.isMaster,
                                            onToggle = { viewModel.setCoreOnline(row.cpu, it) }
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (viewModel.cpusetGroups.isNotEmpty()) {
                item {
                    MaxSection(title = stringResource(R.string.cpu_affinity_title)) {
                        Text(
                            text = stringResource(R.string.cpu_affinity_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        ExpressiveList(
                            content = viewModel.cpusetGroups.map { group ->
                                {
                                    CpusetGroupRow(
                                        group = group,
                                        totalCores = viewModel.totalCores,
                                        onApply = { cores -> viewModel.setCpusetGroupCores(group, cores) }
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * رمز النتيجة → نوع الحالة.
 *
 * ودالّة مستقلّة لأن السؤال عنها يُسأل **مرّتين**: أيّ بطاقة تُعرض ([cpuActionNotice]) وهل
 * يزول الإشعار من نفسه (مهلة التأكيد في [CpuCoreControlScreen]). وسؤالان يُجابان من منطق
 * واحد، لا من نسختين تفترقان عند أوّل رمز جديد يُضاف.
 */
private fun cpuActionKind(reason: CpuActionReason): MaxConditionKind = when (reason) {
    CpuActionReason.AppliedVerified,
    CpuActionReason.HardwareRangeRestored,
    CpuActionReason.Restored,
    CpuActionReason.PresetApplied,
    -> MaxConditionKind.Applied

    CpuActionReason.DeferredSafety,
    CpuActionReason.DeferredOwner,
    CpuActionReason.NoManualIntents,
    -> MaxConditionKind.Applying

    CpuActionReason.PresetRefused,
    CpuActionReason.CoresUnreadable,
    CpuActionReason.UnknownClusterTopology,
    -> MaxConditionKind.Unsupported

    CpuActionReason.NotVerified,
    CpuActionReason.UnknownNode,
    CpuActionReason.HardwareRangeUnknown,
    CpuActionReason.PartialRestore,
    -> MaxConditionKind.Failed
}

/**
 * The action notice, built from the outcome code rather than from the wording.
 *
 * `Deferred*` is shown as *waiting*, not as success: the write was accepted and
 * stored, but it is not in effect, and saying otherwise is the exact mistake this
 * screen used to make.
 *
 * ويُغذّي هذه البطاقة **النافذة العائمة** لا شريط أوّل الشاشة (انظر نداءها في
 * [CpuCoreControlScreen]) — والاسم صار على ما تفعله لا على موضعها القديم.
 */
@Composable
private fun cpuActionNotice(notice: CpuActionNotice, onDismiss: () -> Unit): MaxCondition {
    val kind = cpuActionKind(notice.reason)

    val titleRes = when (kind) {
        MaxConditionKind.Applied -> R.string.cpu_freq_action_applied
        MaxConditionKind.Failed -> R.string.cpu_freq_action_failed
        else -> R.string.cpu_action_pending_title
    }

    val detail = when (notice.reason) {
        CpuActionReason.AppliedVerified -> stringResource(R.string.cpu_notice_applied_verified)
        CpuActionReason.HardwareRangeRestored -> stringResource(R.string.cpu_notice_hardware_range_restored)
        CpuActionReason.Restored -> stringResource(R.string.cpu_notice_restored)
        CpuActionReason.DeferredSafety -> stringResource(R.string.cpu_notice_deferred_safety)
        CpuActionReason.DeferredOwner -> stringResource(
            R.string.cpu_notice_deferred_owner,
            notice.detail ?: stringResource(R.string.cpu_notice_owner_unknown),
        )
        CpuActionReason.NotVerified -> stringResource(R.string.cpu_notice_not_verified)
        CpuActionReason.UnknownNode -> stringResource(R.string.cpu_notice_unknown_node)
        CpuActionReason.HardwareRangeUnknown -> stringResource(R.string.cpu_notice_hardware_range_unknown)
        CpuActionReason.NoManualIntents -> stringResource(R.string.cpu_notice_no_manual_intents)
        CpuActionReason.PartialRestore -> stringResource(R.string.cpu_notice_partial_restore)
        CpuActionReason.UnknownClusterTopology -> stringResource(R.string.cpu_notice_unknown_topology)
        CpuActionReason.CoresUnreadable -> stringResource(R.string.cpu_notice_cores_unreadable)
        CpuActionReason.PresetRefused -> stringResource(R.string.cpu_notice_preset_refused)
        CpuActionReason.PresetApplied -> stringResource(
            R.string.cpu_notice_preset_applied,
            notice.onlineCores,
            notice.totalCores,
        )
    }

    return MaxCondition(
        kind = kind,
        title = stringResource(titleRes),
        detail = detail,
        primaryActionLabel = stringResource(R.string.max_action_dismiss),
        onPrimaryAction = onDismiss,
    )
}

/**
 * Cluster line: which physical cores the cluster owns and how many are online,
 * assembled from the shared labels instead of a hand-written English sentence.
 */
@Composable
private fun ClusterCoreSummary(
    cluster: CpuTopologyUtil.CpuCluster,
    rows: List<CpuCoreRow>
) {
    val coresLabel = stringResource(R.string.cpu_core_cores_label)
    val onlineLabel = stringResource(R.string.cpu_core_online_label)
    val coreName = rows.firstOrNull()?.coreName
    val online = rows.count { it.online }
    val range = if (cluster.cores.size > 1) {
        "${cluster.cores.first()}\u2013${cluster.cores.last()}"
    } else {
        "${cluster.cores.first()}"
    }

    Text(
        text = buildString {
            if (coreName != null) append("$coreName \u00b7 ")
            append("$coresLabel $range \u00b7 $online/${cluster.cores.size} $onlineLabel")
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * سقف النواة المعلن — سقف عنقودها من `cpuinfo_max_freq`.
 *
 * **وهو المواصفة لا القياس الحيّ:** لو قسمنا على أعلى عيّنة رأيناها لصار الرقم يتغيّر من
 * نفسه (٠٫٦ ثم ٠٫٨ لأن نواة أخرى صعدت)، ولو قسمنا على `scaling_max_freq` الحيّ لصار حدّ الخنق
 * الحراري «١٠٠٪». والسقف المعلن هو المقام الوحيد الذي لا يتحرّك — وهو نفسه الذي تعرضه بقية
 * الشاشة في حدود التردد.
 */
private fun coreCeilingMhz(row: CpuCoreRow, clusterMaxFreqMhz: Map<String, Int>): Int =
    clusterMaxFreqMhz[row.cluster.policyPath] ?: 0

/**
 * نسبة النواة من سقفها: `التردد الجاري ÷ أقصاها`.
 *
 * **و`null` ليست صفرًا:** نواة تخفي `cpufreq` لا تُقاس، ونواة مطفأة لا تردّد لها أصلًا؛
 * وكلتاهما تُنتج `null` فتُكتب حالةٌ لا «٠٪» (وهو نصّ خطة المستوى §8.2 حرفيًّا).
 */
private fun coreSharePercent(freqMhz: Int?, ceilingMhz: Int): Int? {
    if (freqMhz == null || freqMhz <= 0 || ceilingMhz <= 0) return null
    return ((freqMhz.toFloat() / ceilingMhz) * 100f).toInt().coerceIn(0, 100)
}

/**
 * بطل الشاشة: **متوسط · الأعلى الآن · عدد الأنوية** (`MAX-MANAGER-LEVEL-UP.md` §8.1).
 *
 * وثلاثة أرقام لا أكثر، لأن الثلاثة هي ما يجيب «كيف حال المعالج الآن»: أين يقف في المتوسط،
 * وأين ذروته، وكم نواة تعمل أصلًا. وكلٌّ منها يُحسب من القراءة الحيّة وحدها — لا رقم محفوظ.
 *
 * **وسطر الصدق تحتهم جزء من البطل لا تذييل:** أندرويد يحجب الاستخدام الحقيقي (`/proc/stat`)
 * منذ أندرويد ٨، فالرقم المعروض **نسبة من السقف** لا «حملًا». وبلا هذا السطر يُقرأ الرقم
 * «استخدام معالج» ويُصدَّق — وهو أصدق ما يمكن أن تفعله شاشة تعرض قياسًا محدودًا.
 */
@Composable
private fun CpuLiveClockHero(
    coreRows: List<CpuCoreRow>,
    coreFreqMhz: Map<Int, Int?>,
    clusterMaxFreqMhz: Map<String, Int>,
) {
    val p = neuralPalette()
    val shares = coreRows.mapNotNull { row ->
        coreSharePercent(coreFreqMhz[row.cpu], coreCeilingMhz(row, clusterMaxFreqMhz))
    }
    val average = shares.takeIf { it.isNotEmpty() }?.let { it.sum() / it.size }
    val highestMhz = coreRows.mapNotNull { coreFreqMhz[it.cpu]?.takeIf { frequency -> frequency > 0 } }
        .maxOrNull()
    val online = coreRows.count { it.online }
    MaxSection(title = stringResource(R.string.cpu_live_subtitle)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm)
        ) {
            NeuralFactTile(
                caption = stringResource(R.string.cpu_live_average),
                value = average?.let { "$it%" } ?: "\u2014",
                accent = p.accent,
                modifier = Modifier.weight(1f),
            )
            NeuralFactTile(
                caption = stringResource(R.string.cpu_live_highest),
                value = formatCpuFrequency((highestMhz ?: 0) * 1000L),
                accent = p.accentAlt,
                modifier = Modifier.weight(1f),
            )
            NeuralFactTile(
                caption = stringResource(R.string.cpu_live_cores),
                value = "$LTR_MARK$online/${coreRows.size}$LTR_MARK",
                accent = p.ok,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = stringResource(R.string.cpu_live_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * بلاطات النواة الحيّة، **مجموعة حسب العنقود** — وهذا ما يفرّقها فعلًا.
 *
 * والتجميع ليس تجميلًا: على big.LITTLE كل نوى العنقود تشترك في سياسة تردّد واحدة، فعنوان
 * «الأنوية ٠–٣ · حتى 2.1 GHz» يقول للقارئ لماذا تشترك الأربع في سقف — وهو المعنى الذي كانت
 * خريطة الأسماء تقوله بلا رقم.
 */
@Composable
private fun CpuLiveClockGrid(
    clusters: List<CpuTopologyUtil.CpuCluster>,
    coreRows: List<CpuCoreRow>,
    coreFreqMhz: Map<Int, Int?>,
    coreFreqHistory: Map<Int, List<Int>>,
    clusterMaxFreqMhz: Map<String, Int>,
) {
    val scheme = MaterialTheme.colorScheme
    clusters.forEach { cluster ->
        val rows = coreRows.filter { it.cluster.policyPath == cluster.policyPath }
        if (rows.isEmpty()) return@forEach
        val ceiling = clusterMaxFreqMhz[cluster.policyPath] ?: 0
        MaxSection(
            title = stringResource(
                R.string.cpu_live_cluster_range,
                rows.minOf { it.cpu },
                rows.maxOf { it.cpu },
                formatCpuFrequency(ceiling.toLong() * 1000L),
            )
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm),
                verticalArrangement = Arrangement.spacedBy(MaxSpace.sm),
                maxItemsInEachRow = 2,
            ) {
                rows.forEach { row ->
                    CoreClockTile(
                        row = row,
                        freqMhz = coreFreqMhz[row.cpu],
                        ceilingMhz = clusterMaxFreqMhz[row.cluster.policyPath] ?: 0,
                        history = coreFreqHistory[row.cpu].orEmpty(),
                        accent = if (row.online) clusterAccent(row.cluster) else scheme.outline,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * بلاطة نواة واحدة: `Core N` · نسبة من السقف · **التردد الكبير** · موجة.
 *
 * **وترتيب القراءة مقصود:** الرقم الكبير هو التردد لا النسبة، لأن المالك طلب صراحةً أن يرى
 * «التردد الحقيقي والديناميكي» — والنسبة سطرٌ صغير بجانب الاسم يشرح موضع الرقم من سقفه.
 *
 * **وحالات ثلاث لا رقم واحد:** مطفأة تكتب «متوقفة»، وذاكرة تُخفي `cpufreq` تكتب «النواة تخفي
 * `cpufreq`»، والمقروءة تكتب تردّدها. ولا تُعرض ٠ ميغاهرتز في أيّ منها.
 */
@Composable
private fun CoreClockTile(
    row: CpuCoreRow,
    freqMhz: Int?,
    ceilingMhz: Int,
    history: List<Int>,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val share = coreSharePercent(freqMhz, ceilingMhz)
    val live = row.online && freqMhz != null && freqMhz > 0
    val tileShape = RoundedCornerShape(MaxRadius.row)
    Column(
        modifier
            .clip(tileShape)
            .background(
                accent.copy(alpha = if (row.online) MaxAlpha.toneContainerStrong else MaxAlpha.toneContainer)
            )
            .border(
                width = MaxSize.hairlineBorder,
                color = accent.copy(alpha = if (row.online) MaxAlpha.borderStrong else MaxAlpha.border),
                shape = tileShape,
            )
            .padding(vertical = MaxSpace.sm, horizontal = MaxSpace.xs),
        verticalArrangement = Arrangement.spacedBy(MaxSpace.hairline),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${stringResource(R.string.cpu_label)}$LTR_MARK${row.cpu}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = share?.let { "$LTR_MARK$it%$LTR_MARK" } ?: "\u2014",
                style = MaterialTheme.typography.labelSmall,
                color = accent,
            )
        }
        Text(
            text = when {
                !row.online -> stringResource(R.string.cpu_live_offline)
                freqMhz == null -> stringResource(R.string.cpu_live_hidden)
                else -> formatCpuFrequency(freqMhz.toLong() * 1000L)
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (live) scheme.onSurface else scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // والموجة **تاريخ هذه النواة وحدها** (§8.2): موجة العنقود كانت سترسم لكل نوى
        // العنقود الشكل نفسه، فتُقرأ الأربع كأنها تعمل وتتوقف معًا.
        NeuralSparkline(
            samples = if (ceilingMhz > 0) {
                history.map { (it.toFloat() / ceilingMhz).coerceIn(0f, 1f) }
            } else {
                emptyList()
            },
            accent = accent,
            modifier = Modifier.fillMaxWidth().height(MaxSpace.xxl),
        )
    }
}

@Composable
private fun CpuHeroCard(
    chipsetName: String,
    coreRows: List<CpuCoreRow>,
    trailing: (@Composable () -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme

    /*
     * **ومع سقوط «كم نواة متصلة» من هنا:** الرقم صار في [CpuLiveClockHero] مرّة واحدة، وكان
     * مكرّرًا في موضعين (سطر هنا · وعدّاد في الخريطة). والقاعدة في هذا المشروع لا تُكرّر قياسًا
     * على سطح واحد — وهذا هو نفس الحذف الذي وقع في الرئيسية بحرفيّته.
     *
     * فالمنزلة الجديدة لهذه البطاقة **خريطة هويّة**: الشريحة، وأيّ نواة إلى أيّ عنقود تنتمي.
     * وهي معلومة ثابتة لا قياس متغيّر، ولذلك يجوز أن تُقرأ مرّة.
     */
    Column(verticalArrangement = Arrangement.spacedBy(MaxSpace.md)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaxSpace.md)
        ) {
            IconBadge(icon = Icons.Outlined.Memory, tint = scheme.secondary, size = 40)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaxSpace.hairline)
            ) {
                Text(
                    text = chipsetName.ifBlank { stringResource(R.string.cpu_core_chipset_unknown) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.cpu_live_identity_map),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
        CoreGridMap(coreRows = coreRows)
        // وباب قسم المعالج في «معلومات الجهاز» آخر بطاقة الشريحة: من عمل على سقوف التردّد هنا
        // يصل بضغطة إلى ما تُعلنه النواة عن الأنوية والعناقيد. وبخطّ فاصل قبله لأن ما فوقه
        // **محتوى** (شريحة ونواة وقراءة) وما تحته **إجراء على البطاقة** — والحدّ يُقرأ بأول نظرة.
        MaxGroupDivider(inset = false)
        trailing?.invoke()
    }
}

/**
 * The grid this screen is named after: one tile per CPU, tinted by its cluster
 * while online and dropped to the outline tone once the kernel parks it.
 */
@Composable
private fun CoreGridMap(coreRows: List<CpuCoreRow>) {
    val scheme = MaterialTheme.colorScheme
    val cpuLabel = stringResource(R.string.cpu_label)
    val offLabel = stringResource(R.string.cpu_core_row_offline)

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm),
        verticalArrangement = Arrangement.spacedBy(MaxSpace.sm),
        maxItemsInEachRow = 4
    ) {
        coreRows.forEach { row ->
            val accent = if (row.online) clusterAccent(row.cluster) else scheme.outline
            val tileShape = RoundedCornerShape(MaxRadius.row)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(tileShape)
                    .background(
                        accent.copy(
                            alpha = if (row.online) MaxAlpha.toneContainerStrong else MaxAlpha.toneContainer
                        )
                    )
                    .border(
                        width = MaxSize.hairlineBorder,
                        color = accent.copy(
                            alpha = if (row.online) MaxAlpha.borderStrong else MaxAlpha.border
                        ),
                        shape = tileShape
                    )
                    .padding(vertical = MaxSpace.sm, horizontal = MaxSpace.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MaxSpace.hairline)
            ) {
                Text(
                    text = "$cpuLabel$LTR_MARK${row.cpu}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface
                )
                Text(
                    text = if (row.online) clusterShortName(row.cluster) else offLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Frequency limits: one group per controllable cluster.
 *
 * The old section stacked a description paragraph, a restore card, a gradient
 * card per cluster, a metric strip, a verification card and a conflict card:
 * six surfaces for one idea. The same facts now live in rows inside a single
 * hairline group, and the copy comes from resources instead of the source.
 */
@Composable
private fun CpuFrequencyControlSection(
    clusters: List<CpuTopologyUtil.CpuCluster>,
    controls: Map<String, CpuFrequencyControlState>,
    hasSessionChanges: Boolean,
    onApply: (String, Long, Long) -> Unit,
    onRestore: (String) -> Unit,
    onRestoreSession: () -> Unit
) {
    val controllable = clusters.filter { controls[it.policyPath]?.canControl == true }
    if (controllable.isEmpty()) return

    MaxSection(
        title = stringResource(R.string.cpu_freq_section_title),
        description = stringResource(R.string.cpu_freq_section_desc)
    ) {
        if (hasSessionChanges) {
            MaxGroup {
                MaxRow(
                    title = stringResource(R.string.cpu_freq_session_title),
                    subtitle = stringResource(R.string.cpu_freq_session_desc),
                    icon = Icons.Outlined.History,
                    iconTone = MaxTone.Caution,
                    onClick = onRestoreSession
                )
            }
        }

        controllable.forEach { cluster ->
            CpuFrequencyControlGroup(
                cluster = cluster,
                control = controls.getValue(cluster.policyPath),
                onApply = { min, max -> onApply(cluster.policyPath, min, max) },
                onRestore = { onRestore(cluster.policyPath) }
            )
        }
    }
}

@Composable
private fun CpuFrequencyControlGroup(
    cluster: CpuTopologyUtil.CpuCluster,
    control: CpuFrequencyControlState,
    onApply: (Long, Long) -> Unit,
    onRestore: () -> Unit
) {
    val lower = control.hardwareMinKHz ?: control.minKHz ?: 0L
    val upper = control.hardwareMaxKHz ?: control.maxKHz ?: lower
    // Edit state is deliberately keyed on the policy only: the 3s live poll
    // updates control.minKHz/maxKHz, and keying remember() on those values
    // reset the sliders mid-drag (the old "hard to use" behavior).
    var editMin by remember(control.policyPath) { mutableStateOf(control.minKHz ?: lower) }
    var editMax by remember(control.policyPath) { mutableStateOf(control.maxKHz ?: upper) }
    val allowed = remember(control.availableFrequenciesKHz, lower, upper) {
        control.availableFrequenciesKHz.filter { it in lower..upper }.ifEmpty {
            listOf(lower, upper).distinct().sorted()
        }
    }
    val editable = allowed.size > 1 && lower < upper
    val activeMin = editMin.coerceIn(lower, upper)
    val activeMax = editMax.coerceIn(activeMin, upper)
    val isModified = activeMin != control.minKHz || activeMax != control.maxKHz
    var pinned by remember(control.policyPath) { mutableStateOf(false) }
    var pinSelection by remember(control.policyPath) { mutableStateOf<Long?>(null) }
    val effectivePin = pinSelection ?: activeMax
    val pinModified = pinned && (effectivePin != control.maxKHz || effectivePin != control.minKHz)
    val tableMissing = stringResource(R.string.cpu_freq_table_missing)
    val liveLimits = "${formatCpuFrequency(control.minKHz ?: 0L)} \u2013 " +
        formatCpuFrequency(control.maxKHz ?: 0L)

    MaxGroup {
        MaxRow(
            title = clusterDisplayName(cluster),
            subtitle = stringResource(
                R.string.cpu_freq_cluster_summary,
                cluster.cores.size,
                control.governor ?: stringResource(R.string.cpu_freq_governor_default)
            ),
            icon = clusterIcon(cluster),
            iconTone = MaxTone.Accent
        )

        MaxGroupDivider()

        // حشو القراءة يسكن في `MaxMetricLine` نفسها؛ فلا يُضاعف هنا.
        Column(verticalArrangement = Arrangement.spacedBy(MaxSpace.hairline)) {
            MaxMetricLine(
                MaxMetric(
                    label = stringResource(R.string.cpu_freq_current_label),
                    value = control.currentKHz?.let(::formatCpuFrequency),
                    source = control.policyPath
                )
            )
            MaxMetricLine(
                MaxMetric(
                    label = stringResource(R.string.cpu_freq_range_label),
                    value = liveLimits
                )
            )
        }

        MaxGroupDivider()

        MaxSwitchRow(
            title = stringResource(R.string.cpu_freq_pin_title),
            checked = pinned,
            onCheckedChange = { pinned = it },
            subtitle = stringResource(
                if (pinned) R.string.cpu_freq_pin_on_desc else R.string.cpu_freq_pin_off_desc
            ),
            enabled = editable,
            lockedReason = tableMissing.takeIf { !editable }
        )

        if (editable) {
            if (pinned) {
                FrequencyStepRow(
                    title = stringResource(R.string.cpu_freq_pinned_label),
                    value = effectivePin,
                    options = allowed,
                    onValueChange = { selected -> pinSelection = selected }
                )
            } else {
                FrequencyStepRow(
                    title = stringResource(R.string.cpu_freq_min_label),
                    value = activeMin,
                    options = allowed.filter { it <= activeMax },
                    onValueChange = { selected -> editMin = selected.coerceAtMost(editMax) }
                )
                FrequencyStepRow(
                    title = stringResource(R.string.cpu_freq_max_label),
                    value = activeMax,
                    options = allowed.filter { it >= activeMin },
                    onValueChange = { selected -> editMax = selected.coerceAtLeast(editMin) }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = MaxSpace.rowPaddingHorizontal,
                    vertical = MaxSpace.xs
                ),
            horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onRestore,
                modifier = Modifier.weight(1f),
                enabled = lower < upper
            ) {
                Icon(
                    imageVector = Icons.Outlined.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(MaxSize.iconGlyphSmall)
                )
                Spacer(Modifier.width(MaxSpace.xs))
                Text(stringResource(R.string.cpu_freq_hardware_range))
            }
            Button(
                onClick = {
                    if (pinned) onApply(effectivePin, effectivePin) else onApply(activeMin, activeMax)
                },
                modifier = Modifier.weight(1f),
                enabled = editable && (if (pinned) pinModified else isModified)
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(MaxSize.iconGlyphSmall)
                )
                Spacer(Modifier.width(MaxSpace.xs))
                Text(
                    stringResource(
                        if (pinned) R.string.cpu_freq_pin_action else R.string.dialog_apply
                    )
                )
            }
        }

        val notes = buildList {
            control.verification?.let { addAll(frequencyVerificationLines(it)) }
            if (control.externalConflict) add(stringResource(R.string.cpu_freq_conflict_detail))
            if (control.sessionOwned) add(stringResource(R.string.cpu_freq_session_owned_note))
        }
        if (notes.isNotEmpty()) {
            MaxBullets(
                lines = notes,
                tone = when {
                    control.externalConflict -> MaxTone.Critical
                    control.verification?.verified == false -> MaxTone.Caution
                    else -> MaxTone.Accent
                },
                modifier = Modifier.padding(
                    horizontal = MaxSpace.rowPaddingHorizontal,
                    vertical = MaxSpace.xs
                )
            )
        }
    }
}

/**
 * Verification result as reading lines: what was requested, what the hardware
 * reported back, and why it differs. The old card printed three frequency
 * ranges; the live-limits row above already carries the current one.
 */
@Composable
private fun frequencyVerificationLines(
    verification: nd.max.ui.viewmodel.CpuFrequencyVerification
): List<String> {
    val headline = when {
        verification.verified && verification.reassertions > 0 -> stringResource(
            R.string.cpu_freq_verified_reasserted,
            verification.reassertions
        )

        verification.verified -> stringResource(R.string.cpu_freq_verified)
        verification.writeAccepted -> stringResource(R.string.cpu_freq_overridden)
        else -> stringResource(R.string.cpu_freq_rejected)
    }
    val requested = "${formatCpuFrequency(verification.requestedMinKHz)} \u2013 " +
        formatCpuFrequency(verification.requestedMaxKHz)
    val measured = "${formatCpuFrequency(verification.actualMinKHz ?: 0L)} \u2013 " +
        formatCpuFrequency(verification.actualMaxKHz ?: 0L)

    return buildList {
        add(headline)
        add(stringResource(R.string.cpu_freq_verification_detail, requested, measured))
        // الترتيب مقصود: سبب «لم تصل القيمة إلى العقدة» يُقال قبل تفسيري
        // الرفض/الانقلاب، لأن هذين يفترضان أن العقدة رأت القيمة أصلًا.
        if (verification.outsideProvenRange) {
            add(stringResource(R.string.cpu_freq_outside_proven_range_explain))
        } else if (!verification.verified) {
            add(
                stringResource(
                    if (verification.writeAccepted) R.string.cpu_freq_overridden_explain
                    else R.string.cpu_freq_rejected_explain
                )
            )
        }
    }
}

/** One frequency step picked from the kernel's own table, as a house slider row. */
@Composable
private fun FrequencyStepRow(
    title: String,
    value: Long,
    options: List<Long>,
    onValueChange: (Long) -> Unit
) {
    val safeOptions = options.ifEmpty { listOf(value) }
    val index = safeOptions.indexOf(value).takeIf { it >= 0 } ?: 0
    MaxSliderRow(
        title = title,
        value = index.toFloat(),
        onValueChange = { position ->
            onValueChange(safeOptions[position.toInt().coerceIn(0, safeOptions.lastIndex)])
        },
        valueText = formatCpuFrequency(safeOptions[index]),
        valueRange = 0f..safeOptions.lastIndex.toFloat().coerceAtLeast(1f),
        steps = (safeOptions.size - 2).coerceAtLeast(0)
    )
}

private fun formatCpuFrequency(kHz: Long): String = when {
    kHz <= 0L -> "—"
    kHz >= 1_000_000L -> String.format(java.util.Locale.US, "%.2f GHz", kHz / 1_000_000f)
    else -> "${kHz / 1000} MHz"
}

@Composable
private fun CpuCoresSection(
    clusters: List<CpuTopologyUtil.CpuCluster>,
    coreRows: List<CpuCoreRow>,
    clusterMaxFreqMhz: Map<String, Int>
) {
    MaxSection(title = stringResource(R.string.cpu_core_cores_label)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm),
            verticalArrangement = Arrangement.spacedBy(MaxSpace.sm),
            maxItemsInEachRow = 3
        ) {
            clusters.forEach { cluster ->
                CpuClusterSummaryCard(
                    modifier = Modifier.weight(1f, fill = true),
                    cluster = cluster,
                    rows = coreRows.filter { it.cluster.policyPath == cluster.policyPath },
                    maxFreqMhz = clusterMaxFreqMhz[cluster.policyPath] ?: 0
                )
            }
        }
    }
}

@Composable
private fun CpuClusterSummaryCard(
    modifier: Modifier,
    cluster: CpuTopologyUtil.CpuCluster,
    rows: List<CpuCoreRow>,
    maxFreqMhz: Int
) {
    val scheme = MaterialTheme.colorScheme
    val accent = clusterAccent(cluster)
    val title = clusterDisplayName(cluster)
    val frequency = if (maxFreqMhz > 0) String.format(java.util.Locale.US, "%.2f GHz", maxFreqMhz / 1000f) else "—"

    val online = rows.count { it.online }

    // Three rows instead of five, and the title reserves two of them so the
    // three summary tiles keep one height whatever the cluster name is. The
    // previous card stacked icon / title / GHz / "3 / 3" / "Online" vertically,
    // which made a summary tile taller than the controls it summarises and left
    // the three tiles visibly ragged next to each other.
    // **ترحيل إلى القشرة:** الحدّ يُمرّر بنبرة `borderStrong` التي يستعملها الآن (وهي أعلى من
    // `edgeLight` التي تُعطيها معلمة `accent`) — فالترحيل لا يغيّر شدّة الحدّ.
    MaxCardShell(
        modifier = modifier,
        borderColor = accent.copy(alpha = MaxAlpha.borderStrong),
        contentPadding = 0.dp,
        verticalArrangement = Arrangement.Top,
    ) {
        Column(
            modifier = Modifier.padding(MaxSpace.md),
            verticalArrangement = Arrangement.spacedBy(MaxSpace.xs)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm)
            ) {
                Icon(
                    imageVector = clusterIcon(cluster),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(MaxSize.iconGlyph)
                )
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = frequency,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface
            )
            Text(
                text = "$LTR_MARK$online/${rows.size}$LTR_MARK " +
                    stringResource(R.string.cpu_core_online_label),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CpuManualControlCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    // A titled section holding one switch row, the shape every other redesigned
    // screen uses, instead of a card that carried its own section title inside
    // it and stretched its icon and its switch to opposite edges.
    MaxSection(title = stringResource(R.string.cpu_core_manual_control)) {
        MaxGroup {
            MaxSwitchRow(
                title = stringResource(R.string.cpu_manual_enable_title),
                subtitle = stringResource(R.string.cpu_core_manual_control_desc),
                checked = enabled,
                onCheckedChange = onEnabledChange,
                icon = Icons.Outlined.Tune,
                iconTone = MaxTone.Accent
            )
        }
    }
}

/** Cluster name from resources, so section titles and tiles agree in every locale. */
@Composable
private fun clusterDisplayName(cluster: CpuTopologyUtil.CpuCluster): String = when (cluster.shortTag) {
    "PRIME" -> stringResource(R.string.cpu_cluster_prime)
    "GOLD" -> stringResource(R.string.cpu_cluster_gold)
    "SILVER" -> stringResource(R.string.cpu_cluster_silver)
    else -> cluster.label
}

@Composable
private fun quickConfigIcon(id: String) = when (id) {
    "all_on" -> Icons.Filled.Bolt
    "balanced" -> Icons.Outlined.Balance
    "power_saver" -> Icons.Outlined.EnergySavingsLeaf
    else -> Icons.Outlined.Tune
}

/**
 * اسم العنقود **القصير** للوسوم والبلاطات الصغيرة.
 *
 * `CpuTopologyUtil.shortTag` قيمة ماشينية إنجليزية (`SILVER`/`GOLD`/`PRIME`) وكانت تُطبع حرفيًّا
 * في بلاطة الشبكة وفي عنوان صفّ النواة — فظهر في لقطة المالك «CPU1 · SILVER» وسط واجهة عربية
 * مع أن أسماء العناقيد كلها مترجمة (`cpu_cluster_*`). والاسم الطويل («أنوية الأداء») لا يسع في
 * بلاطة ربع العرض؛ فالمطلوب اسم قصير مترجم، وهو [cpu_cluster_short_*] لا اسم العنقود الكامل.
 */
@Composable
private fun clusterShortName(cluster: CpuTopologyUtil.CpuCluster): String = when (cluster.shortTag) {
    "PRIME" -> stringResource(R.string.cpu_cluster_short_prime)
    "GOLD" -> stringResource(R.string.cpu_cluster_short_gold)
    "SILVER" -> stringResource(R.string.cpu_cluster_short_silver)
    else -> cluster.label
}

/**
 * اسم مجموعة cpuset مترجمًا بمفتاحها.
 *
 * والمفاتيح في `CpuTopologyUtil` هي مفاتيح النواة نفسها (`top-app` …) وهي مُعرّفات لا تُترجم،
 * وأمّا ما كان يُعرض فهو `label` الإنجليزي المكتوب هناك بيده («Top App» · «Foreground» …)
 * فيظهر في أسفل شاشة عربية. والمفتاح هو الجسر: ما لا نعرفه يبقى على `label` كما كان.
 */
@Composable
private fun cpusetGroupName(group: CpuTopologyUtil.CpusetGroup): String = when (group.key) {
    "top-app" -> stringResource(R.string.cpu_cpuset_top_app)
    "foreground" -> stringResource(R.string.cpu_cpuset_foreground)
    "background" -> stringResource(R.string.cpu_cpuset_background)
    "system-background" -> stringResource(R.string.cpu_cpuset_system_background)
    else -> group.label
}

/**
 * Fixed identity color per cluster tier -- amber/gold for the Prime
 * supercore, blue for the mid Performance cluster(s), green for Efficiency
 * -- keyed off the shortTag CpuTopologyUtil.detectClusters() already
 * assigns, so this reads correctly whether the chip has 2, 3, or 4
 * clusters, the same way that function's own label/tag logic does. Falls
 * back to the screen's theme accent for the single-cluster case.
 */
@Composable
private fun clusterAccent(cluster: CpuTopologyUtil.CpuCluster): Color = when (cluster.shortTag) {
    "PRIME" -> MaterialTheme.colorScheme.tertiary
    "GOLD" -> MaterialTheme.colorScheme.primary
    "SILVER" -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.tertiary
}

private fun clusterIcon(cluster: CpuTopologyUtil.CpuCluster): ImageVector = when (cluster.shortTag) {
    "PRIME" -> Icons.Filled.Bolt
    "GOLD" -> Icons.Outlined.Balance
    "SILVER" -> Icons.Outlined.EnergySavingsLeaf
    else -> Icons.Outlined.Memory
}

@Composable
private fun quickConfigAccent(id: String): Color = when (id) {
    "all_on" -> MaterialTheme.colorScheme.tertiary
    "balanced" -> MaterialTheme.colorScheme.primary
    "power_saver" -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.primary
}

/**
 * A quick-config preset as its own colored tile -- icon in a glow badge,
 * bold label, one-line description -- instead of the screen falling back
 * to ExpressiveTile's generic neutral treatment. Kept local to this screen
 * (not a change to ExpressiveTile itself) since that component is shared
 * with the tweak workspace and this per-preset coloring is specific to core
 * presets, not something every ExpressiveTile caller should inherit.
 */
@Composable
private fun CoreQuickConfigTile(
    icon: ImageVector,
    label: String,
    description: String,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = MaterialTheme.shapes.large
    // التحديد بإطار أعرض وتحوّل في التعبئة **معًا**: الإطار وحده لا يُقرأ من بعيد،
    // والتعبئة وحدها لا تُقرأ في تباين منخفض.
    val container = if (selected) {
        Brush.linearGradient(listOf(accent.copy(alpha = 0.32f), MaterialTheme.colorScheme.surfaceContainerHigh))
    } else {
        Brush.linearGradient(listOf(accent.copy(alpha = 0.16f), MaterialTheme.colorScheme.surfaceContainerLow))
    }
    Column(
        modifier = modifier
            .clip(shape)
            .background(container)
            .border(
                width = if (selected) MaxSize.activeRing else 1.dp,
                color = accent.copy(alpha = if (selected) MaxAlpha.borderStrong else MaxAlpha.border),
                shape = shape,
            )
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(vertical = 12.dp, horizontal = MaxSpace.xs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconBadge(icon = icon, tint = accent, size = 32)
        Spacer(Modifier.height(MaxSpace.sm))
        // Two reserved lines: the longest preset name must wrap instead of being
        // cut mid-word, which is what "Performanc" was in the old one-line tile.
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Text(
            text = description,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun CoreRowItem(
    row: CpuCoreRow,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val title = if (row.isMaster) {
        "CPU${row.cpu} \u00b7 " + stringResource(R.string.cpu_core_master_tag)
    } else {
        "CPU${row.cpu} \u00b7 " + clusterShortName(row.cluster)
    }
    val summary = when {
        row.isMaster -> stringResource(R.string.cpu_core_master_note)
        row.online -> stringResource(R.string.cpu_core_row_online)
        else -> stringResource(R.string.cpu_core_row_offline)
    }
    ExpressiveSwitchItem(
        icon = clusterIcon(row.cluster),
        title = title,
        summary = summary,
        checked = row.online,
        enabled = enabled,
        onCheckedChange = onToggle
    )
}

@Composable
fun CpuCoreControlTopAppBar(scrollBehavior: TopAppBarScrollBehavior, onBack: () -> Unit) {
    MaxManagerSubScreenTopBar(
        scrollBehavior = scrollBehavior,
        title = stringResource(R.string.cpu_core_control_title),
        onBack = onBack,
        accentIcon = Icons.Filled.Memory,
        accent = MaterialTheme.colorScheme.tertiary
    )
}

/**
 * مجموعة cpuset واحدة («top-app» · «foreground» …) مع تخصيصها الحالي للأنوية في سطر واحد،
 * ونافذة لاختيار الأنوية المسموح لها بالجدولة.
 *
 * وهذا هو التكامل الصحيح مع «إيقاف الأنوية» أعلاه: ذاك يقرّر أيّ الأنوية موجودة، وهذا يقرّر
 * أيّها يُسمح لكل مجموعة بجدولته — فعرضهما معًا مقصود لأنّ السؤال واحد.
 */
@Composable
private fun CpusetGroupRow(
    group: CpuTopologyUtil.CpusetGroup,
    totalCores: Int,
    onApply: (List<Int>) -> Unit
) {
    var dialogVisible by remember { mutableStateOf(false) }
    var pendingSelection by remember(group.cores) { mutableStateOf(group.cores.toSet()) }

    val rangeText = summarizeCoreList(group.cores)

    ExpressiveListItem(
        onClick = {
            pendingSelection = group.cores.toSet()
            dialogVisible = true
        },
        leadingContent = { LeadingIcon(icon = Icons.Outlined.Hub) },
        headlineContent = { Text(cpusetGroupName(group)) },
        supportingContent = { Text(stringResource(R.string.cpu_affinity_cores_summary, rangeText)) }
    )

    CustomContentDialog(
        visible = dialogVisible,
        title = group.label,
        onDismiss = { dialogVisible = false },
        onConfirm = {
            onApply(pendingSelection.toList())
            dialogVisible = false
        },
        confirmEnabled = pendingSelection.isNotEmpty()
    ) {
        Column {
            Text(
                text = stringResource(R.string.cpu_affinity_dialog_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (cpu in 0 until totalCores) {
                    val selected = pendingSelection.contains(cpu)
                    FilterChip(
                        selected = selected,
                        onClick = {
                            pendingSelection = if (selected) {
                                pendingSelection - cpu
                            } else {
                                pendingSelection + cpu
                            }
                        },
                        label = { Text("CPU$cpu") }
                    )
                }
            }
        }
    }
}

/** Collapses a sorted core list like [0,1,2,3,6,7] into "0\u20133,6\u20137" for the summary line. */
private fun summarizeCoreList(cores: List<Int>): String {
    if (cores.isEmpty()) return "\u2014"
    val sorted = cores.sorted()
    val ranges = mutableListOf<IntRange>()
    var start = sorted.first()
    var prev = sorted.first()
    for (c in sorted.drop(1)) {
        if (c == prev + 1) {
            prev = c
        } else {
            ranges.add(start..prev)
            start = c
            prev = c
        }
    }
    ranges.add(start..prev)
    return ranges.joinToString(",") { if (it.first == it.last) "${it.first}" else "${it.first}\u2013${it.last}" }
}
