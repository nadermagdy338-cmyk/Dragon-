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
 * أجزاء شاشة السجل: صفوف العرض، وورقتا المشاركة والإعدادات.
 *
 * ولماذا فصلت عن `LogsViewerScreen.kt`: الشاشة كانت ستتجاوز ألف سطر، وسقف هذا المستودع
 * (`code_health`) يمنع تجاوزها. والفصل هنا طبيعي لا قسري: الشاشة تنسّق الحالة، وهذه الملفات
 * ترسم ما تُعطى.
 */
package nd.max.ui.subscreens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nd.max.R
import nd.max.core.diagnostics.LogTargetSummary
import nd.max.core.diagnostics.LogVerdict
import nd.max.ui.component.CustomBottomSheet
import nd.max.ui.component.ExpressiveCheckboxItem
import nd.max.ui.component.ExpressiveList
import nd.max.ui.component.ExpressiveListItem
import nd.max.ui.component.ExpressiveSwitchItem
import nd.max.ui.viewmodel.LogsViewerViewModel

/**
 * ورقة المشاركة: ملف واحد يخرج، وما يختاره المستخدم هو نوعه.
 *
 * ولماذا ورقة لا زرّان: الشريط العلوي يحمل بالفعل إيقافين ومسحًا ومشاركة، وإضافة زرّ خامس تجعل
 * اللمس الخاطئ أسهل. والاختيار الذي يظهر مرّة عند الضغط أوضح من رمزين متشابهين يفرّق بينهما
 * النصّ وحده.
 */
@Composable
internal fun LogsShareSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onRawLog: () -> Unit,
    onReport: () -> Unit
) {
    CustomBottomSheet(visible = visible, onDismiss = onDismiss) {
        Text(
            text = stringResource(R.string.logsviewer_share_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
        )
        ExpressiveList(
            title = stringResource(R.string.logsviewer_share_section),
            content = listOf(
                {
                    ExpressiveListItem(
                        onClick = onReport,
                        headlineContent = { Text(stringResource(R.string.logsviewer_share_report)) }
                    )
                },
                {
                    ExpressiveListItem(
                        onClick = onRawLog,
                        headlineContent = { Text(stringResource(R.string.logsviewer_share_raw)) }
                    )
                }
            )
        )
        Text(
            text = stringResource(R.string.logsviewer_share_report_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Spacer(Modifier.height(12.dp))
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
internal fun LogViewerStatusHeader(
    mode: LogsViewerViewModel.ViewerMode,
    lineCount: Int,
    paused: Boolean,
    filtered: Int
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (mode == LogsViewerViewModel.ViewerMode.LOGCAT) "System log" else "MaxManager log",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (paused) "Stream paused" else "Live stream",
                style = MaterialTheme.typography.bodySmall,
                color = if (paused) colors.error else colors.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = lineCount.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${filtered} shown",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun LogLineRow(
    entry: LogsViewerViewModel.LogEntry,
    showPid: Boolean,
    showTid: Boolean
) {
    val colorScheme = MaterialTheme.colorScheme
    val highlight = entry.level == LogsViewerViewModel.LogLevel.ERROR || entry.level == LogsViewerViewModel.LogLevel.ASSERT
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = colorScheme.onSurfaceVariant)) {
            append(entry.time)
            append("  ")
        }
        if (showPid) {
            withStyle(SpanStyle(color = colorScheme.onSurfaceVariant)) {
                append(entry.pid.padStart(6))
                append(' ')
            }
        }
        if (showTid) {
            withStyle(SpanStyle(color = colorScheme.onSurfaceVariant)) {
                append(entry.tid.padStart(6))
                append(' ')
            }
        }
        withStyle(SpanStyle(color = entry.level.color, fontWeight = FontWeight.Bold)) {
            append(entry.level.letter)
            append(' ')
        }
        withStyle(SpanStyle(color = entry.level.color, fontWeight = FontWeight.SemiBold)) {
            append(entry.tag)
        }
        withStyle(SpanStyle(color = colorScheme.onSurfaceVariant)) { append(": ") }
        withStyle(SpanStyle(color = colorScheme.onSurface)) { append(entry.message) }
    }

    Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        overflow = TextOverflow.Clip,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (highlight) Modifier.background(entry.level.color.copy(alpha = 0.07f)) else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 3.dp)
    )
}

/**
 * سطر حدث موحّد، وحقوله على بعد ضغطة.
 *
 * ولماذا الضغط: الأسطر تُقرأ بالمئات، والحقول الحقيقية عشرات لكل حدث. عرضها كلها دائمًا يجعل
 * السطر سطرين ويُفقد الرصد. فالافتراضي هو ما يُقرأ دائمًا (الوقت، المستوى، المصدر، الحدث،
 * `reason=`)، والتفصيل عند الطلب.
 *
 * ورمز الفشل يُعرض بجانب السطر لا بدلًا منه: من يفتح السجل يبحث عن الفشل أولًا، ومحاذاته في
 * عمود واحد تسرّع المسح بالعين.
 */
@Composable
internal fun UnifiedLogLineRow(
    entry: LogsViewerViewModel.UnifiedLogEntry,
    expanded: Boolean,
    onToggle: () -> Unit,
    onFocusTarget: (String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val highlight = entry.level == LogsViewerViewModel.UnifiedLogLevel.ERROR ||
        entry.level == LogsViewerViewModel.UnifiedLogLevel.FATAL
    // الاسم يُحضر قبل الباني لأن `stringResource` لا تُستدعى إلا داخل دالّة `@Composable`،
    // و`buildAnnotatedString` باني نصّ عادي.
    val sourceLabel = stringResource(entry.source.labelRes)
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = colorScheme.onSurfaceVariant)) {
            append(entry.timestamp.substringAfter(' ')) // time only, date rarely needed inline
            append("  ")
        }
        withStyle(SpanStyle(color = entry.level.color, fontWeight = FontWeight.Bold)) {
            append(entry.level.letter)
            append(' ')
        }
        withStyle(SpanStyle(color = entry.level.color, fontWeight = FontWeight.SemiBold)) {
            append(sourceLabel)
        }
        withStyle(SpanStyle(color = colorScheme.onSurfaceVariant)) { append(": ") }
        if (entry.eventType != null) {
            withStyle(
                SpanStyle(
                    color = colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Bold,
                    background = colorScheme.secondaryContainer
                )
            ) {
                append(' ')
                append(entry.eventType)
                append(' ')
            }
            withStyle(SpanStyle(color = colorScheme.onSurface)) {
                append(' ')
                append(entry.message.substringAfter("EVENT=${entry.eventType}").trim())
            }
        } else {
            withStyle(SpanStyle(color = colorScheme.onSurface)) { append(entry.message) }
        }
    }

    val verdictColor = when (entry.verdict) {
        LogVerdict.FAIL -> colorScheme.error
        LogVerdict.OK -> entry.level.color
        LogVerdict.UNKNOWN -> colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .then(
                if (highlight) Modifier.background(entry.level.color.copy(alpha = 0.07f)) else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            overflow = TextOverflow.Clip,
            modifier = Modifier.fillMaxWidth()
        )
        if (expanded) {
            // الحقول كما كتبها المحرّك، حقلًا في سطر — وبلا إعادة صياغة: ما سيُبحث عنه في الملف
            // هو نفسه ما يُعرض هنا.
            Column(modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp)) {
                entry.fields.forEach { field ->
                    Text(
                        text = "${field.key}=${field.value}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                entry.target?.let { target ->
                    TextButton(
                        onClick = { onFocusTarget(target) },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.logsviewer_show_target_only, target),
                            fontSize = 11.sp,
                            color = verdictColor
                        )
                    }
                }
            }
        }
    }
}

/**
 * صفّ مقبض واحد: آخر حكم، وسببه، وعدد مرّات الفشل، وآخر قراءة.
 *
 * والعدد مقصود: مقبض فشل مرّة وفشل ستّين مرّة يبدوان في الخط الزمني كسطرين متطابقين؛ وهنا
 * يظهر الفرق الذي يفرّق عطلًا عارضًا عن عطل مزمن.
 */
@Composable
internal fun TargetSummaryRow(
    summary: LogTargetSummary,
    focused: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val verdictColor = when (summary.verdict) {
        LogVerdict.FAIL -> colorScheme.error
        LogVerdict.OK -> colorScheme.secondary
        LogVerdict.UNKNOWN -> colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .then(if (focused) Modifier.background(colorScheme.secondaryContainer.copy(alpha = 0.5f)) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = summary.target,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = summary.verdict.token,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = verdictColor
            )
        }
        if (summary.reason.isNotEmpty()) {
            Text(
                text = summary.reason,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = colorScheme.onSurfaceVariant
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.logsviewer_target_events, summary.observations),
                fontSize = 10.sp,
                color = colorScheme.onSurfaceVariant
            )
            if (summary.failures > 0) {
                Text(
                    text = stringResource(R.string.logsviewer_target_failures, summary.failures),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.error
                )
            }
            if (summary.hasReadback) {
                Text(
                    text = "${summary.expected} -> ${summary.live}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun LogsViewerSettingsSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    viewModel: LogsViewerViewModel
) {
    CustomBottomSheet(visible = visible, onDismiss = onDismiss) {
        Text(
            text = stringResource(R.string.logsviewer_settings_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
        )

        ExpressiveList(
            title = stringResource(R.string.logsviewer_buffers_section),
            content = LogsViewerViewModel.LogBuffer.entries.map { buffer ->
                {
                    ExpressiveCheckboxItem(
                        title = stringResource(buffer.labelRes),
                        checked = buffer in viewModel.selectedBuffers,
                        onCheckedChange = { checked ->
                            val updated = if (checked) {
                                viewModel.selectedBuffers + buffer
                            } else {
                                viewModel.selectedBuffers - buffer
                            }
                            viewModel.setBuffers(updated)
                        }
                    )
                }
            }
        )
        Text(
            text = stringResource(R.string.logsviewer_buffers_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        ExpressiveList(
            title = stringResource(R.string.logsviewer_display_section),
            content = listOf(
                {
                    ExpressiveSwitchItem(
                        title = stringResource(R.string.logsviewer_show_pid),
                        checked = viewModel.showPid,
                        onCheckedChange = { viewModel.setShowPid(it) }
                    )
                },
                {
                    ExpressiveSwitchItem(
                        title = stringResource(R.string.logsviewer_show_tid),
                        checked = viewModel.showTid,
                        onCheckedChange = { viewModel.setShowTid(it) }
                    )
                }
            )
        )

        // إدارة الملف من هنا لا من شاشة أخرى: من يقرأ سجلًا كبيرًا هو من يريد تحديد حدّه،
        // والرقم يطابق ما يفرضه الأصل (64KB..16MB) فلا يعرض حدًّا لا ينفّذه أحد.
        ExpressiveList(
            title = stringResource(R.string.logsviewer_file_section),
            content = listOf(
                {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            text = stringResource(R.string.logsviewer_file_max_kb),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LOG_MAX_KB_CHOICES.forEach { kb ->
                                FilterChip(
                                    selected = viewModel.logMaxKb == kb,
                                    onClick = { viewModel.setLogMaxKb(kb) },
                                    label = { Text(stringResource(R.string.logsviewer_file_kb_value, kb)) }
                                )
                            }
                        }
                    }
                },
                {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            text = stringResource(R.string.logsviewer_file_min_level),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LogsViewerViewModel.UnifiedLogLevel.entries.forEachIndexed { index, level ->
                                FilterChip(
                                    selected = viewModel.logMinLevel == index,
                                    onClick = { viewModel.setLogMinLevel(index) },
                                    label = {
                                        Text(
                                            if (index == 0) stringResource(R.string.logsviewer_file_level_all)
                                            else level.letter
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = level.color.copy(alpha = 0.22f),
                                        selectedLabelColor = level.color
                                    )
                                )
                            }
                        }
                    }
                }
            )
        )
        Text(
            text = stringResource(R.string.logsviewer_file_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Spacer(Modifier.height(16.dp))
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

/**
 * الخيارات المعروضة لحدّ حجم الملف — كلها داخل مدى الأصل المعلَن.
 *
 * وليست مُولّدة من المدى عمدًا: قائمة تُحسب رياضيًّا تعرض قيمًا لا يريدها أحد (64KB؟ 100KB؟)،
 * والثلاثة هنا تغطي الاستعمالات الحقيقية: اقتصادي، افتراضي، تشخيص مطوّل.
 */
private val LOG_MAX_KB_CHOICES = listOf(1024, 3072, 8192)
