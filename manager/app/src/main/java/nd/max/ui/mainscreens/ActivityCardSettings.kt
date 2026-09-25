/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.mainscreens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nd.max.R
import nd.max.ui.component.ExpressiveList
import nd.max.ui.component.ExpressiveListItem
import nd.max.ui.component.ExpressiveSwitchItem
import nd.max.ui.component.LeadingIcon
import nd.max.ui.mainscreens.UnifiedActivityModel.CardMotion
import nd.max.ui.mainscreens.UnifiedActivityModel.CardOptions
import nd.max.ui.mainscreens.UnifiedActivityModel.CardStyle
import nd.max.ui.mainscreens.UnifiedActivityModel.CardVerbosity
import nd.max.ui.mainscreens.UnifiedActivityModel.SessionPolicy
import nd.max.ui.util.ActivityCardPreferences

/**
 * صفّ الإعدادات الذي يفتح تخصيص بطاقة النشاط.
 *
 * لماذا هنا لا في شاشة التحكّم
 * ---------------------------
 * هذه تفضيلات **عرض** لا تحكّم عتاد: لا مقبض ولا كتابة ولا جذر. فمكانها مع بقية تفضيلات التطبيق،
 * ولو وُضعت في `Control` لقرأها من يبحث عن سلطة على المعالج على أنها سلطة على المعالج.
 *
 * ولماذا داخل `ExpressiveList`
 * ---------------------------
 * لأن الشاشة كلها تُبنى من مجموعات: الصفّ المفرد بحاجة إلى `expressiveCardSurface` الذي ترسمه
 * `ExpressiveList`، وهذا ما يفصل «صفّ إعداد» عن «نصّ على الخلفية». وكان هذا الصفّ يُلقى في
 * القائمة **مجردًا** بينما كل جار له داخل مجموعة، فظهر مختلف المجموعة والعنوان عن بقية الشاشة
 * (بلا سطح بطاقة وبلا زوايا) — وهو خلل تناسق حقيقي، لا ذوق. والدليل في الكود لا في اللقطة:
 * `ExpressiveListItem` وحده لا يرسم سطحًا؛ الرسم في `ExpressiveList`.
 *
 * ولماذا تُكتب الخيارات فورًا
 * --------------------------
 * البطاقة تقرأ التفضيلات في دورتها (١٠ ثوانٍ)، فمن غيّر شيئًا يراه يتحرّك في الرئيسية بلا خطوة
 * إضافية. وحفظٌ ينتظر زرًّا هو أصل شكوى «غيّرت ولم يتغيّر شيء» — وهي شكوى لا يجد لها أحد تفسيرًا
 * لأن الفرق بين «لم يُحفظ» و«لم يُقرأ» و«لا أثر لهذا الخيار» غير مرئي.
 *
 * والافتراضي (بلا لمس) هو التلقائي، فلا يُفرض على أحد إعداد لم يطلبه.
 */
@Composable
internal fun ActivityCardSettingsItem() {
    val context = LocalContext.current
    var options by remember { mutableStateOf(ActivityCardPreferences.read(context)) }
    var open by remember { mutableStateOf(false) }

    ExpressiveList(
        content = listOf({
            ExpressiveListItem(
                onClick = { open = true },
                headlineContent = { Text(stringResource(R.string.settings_activity_card_title)) },
                supportingContent = { Text(activityCardSummary(options)) },
                leadingContent = { LeadingIcon(icon = Icons.Rounded.Timeline) },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null
                    )
                },
            )
        }),
    )

    if (open) {
        ActivityCardDialog(
            options = options,
            onChange = { updated ->
                options = updated
                ActivityCardPreferences.write(context, updated)
            },
            onDismiss = { open = false },
        )
    }
}

/**
 * وصف سطر واحد للحالة الحالية — وهو **مشتقّ من الخيارات نفسها** لا مكتوب بيد.
 *
 * والقاعدتان: «مخصّص» تُقال حين يخرج خيار واحد عن الافتراضي (وإلا قرأ صاحبها «تلقائي» وهو قد
 * أوقف حركةً أو أخفى مشهدًا)، واسم النمط يُقال حين اختِير بنفسه — فالسطر يُخبر بالحالة التي ستقع
 * لا بالحالة التي كُتبت في القائمة.
 */
@Composable
private fun activityCardSummary(options: CardOptions): String = if (options.isDefault) {
    stringResource(R.string.activity_card_summary_auto)
} else if (!options.auto) {
    styleLabel(options.style)
} else {
    stringResource(R.string.activity_card_summary_custom)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivityCardDialog(
    options: CardOptions,
    onChange: (CardOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_activity_card_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.settings_activity_card_desc),
                    style = MaterialTheme.typography.bodySmall,
                )

                // ── الشكل ────────────────────────────────────────────────────────────
                ExpressiveSwitchItem(
                    title = stringResource(R.string.activity_card_auto),
                    summary = stringResource(R.string.activity_card_auto_desc),
                    checked = options.auto,
                    onCheckedChange = { onChange(options.copy(auto = it)) },
                )
                if (!options.auto) {
                    ChipRow(
                        title = stringResource(R.string.activity_card_style_title),
                        choices = CardStyle.entries
                            .filter { it != CardStyle.AUTO }
                            .map { style -> style.token to styleLabel(style) },
                        selected = options.style.token,
                        onSelect = { token ->
                            CardStyle.entries.firstOrNull { it.token == token }?.let { style ->
                                onChange(options.copy(style = style))
                            }
                        },
                    )
                }

                // ── مستوى التفاصيل ───────────────────────────────────────────────────
                ChipRow(
                    title = stringResource(R.string.activity_card_detail_title),
                    choices = CardVerbosity.entries.map { it.token to verbosityLabel(it) },
                    selected = options.verbosity.token,
                    onSelect = { token ->
                        CardVerbosity.entries.firstOrNull { it.token == token }?.let { verbosity ->
                            onChange(options.copy(verbosity = verbosity))
                        }
                    },
                )

                // ── الحركة ───────────────────────────────────────────────────────────
                ChipRow(
                    title = stringResource(R.string.activity_card_motion_title),
                    choices = CardMotion.entries.map { it.token to motionLabel(it) },
                    selected = options.motion.token,
                    onSelect = { token ->
                        CardMotion.entries.firstOrNull { it.token == token }?.let { motion ->
                            onChange(options.copy(motion = motion))
                        }
                    },
                )

                // ── المحتوى ──────────────────────────────────────────────────────────
                Text(
                    stringResource(R.string.activity_card_content_title),
                    style = MaterialTheme.typography.labelLarge,
                )
                ExpressiveSwitchItem(
                    title = stringResource(R.string.storyboard_scene_per_app),
                    summary = stringResource(R.string.activity_card_show_per_app_desc),
                    checked = options.showPerApp,
                    onCheckedChange = { onChange(options.copy(showPerApp = it)) },
                )
                ExpressiveSwitchItem(
                    title = stringResource(R.string.storyboard_scene_max_ai),
                    summary = stringResource(R.string.activity_card_show_max_ai_desc),
                    checked = options.showMaxAi,
                    onCheckedChange = { onChange(options.copy(showMaxAi = it)) },
                )
                ExpressiveSwitchItem(
                    title = stringResource(R.string.storyboard_scene_manual),
                    summary = stringResource(R.string.activity_card_show_manual_desc),
                    checked = options.showManual,
                    onCheckedChange = { onChange(options.copy(showManual = it)) },
                )
                ExpressiveSwitchItem(
                    // العنوان لا يُعاد استعمال «محرك ذكاء MAX»: مفتاح بهذا الاسم يُقرأ كأنه يُطفئ
                    // المحرك، وهو هنا يُسكت **سطرًا** يقول إنه يعمل.
                    title = stringResource(R.string.activity_card_show_monitoring),
                    summary = stringResource(R.string.activity_card_show_monitoring_desc),
                    checked = options.showMonitoring,
                    onCheckedChange = { onChange(options.copy(showMonitoring = it)) },
                )
                ChipRow(
                    title = stringResource(R.string.activity_card_session_title),
                    choices = SessionPolicy.entries.map { it.token to sessionLabel(it) },
                    selected = options.session.token,
                    onSelect = { token ->
                        SessionPolicy.entries.firstOrNull { it.token == token }?.let { policy ->
                            onChange(options.copy(session = policy))
                        }
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.activity_card_close)) }
        },
        dismissButton = {
            TextButton(onClick = { onChange(CardOptions.DEFAULT) }) {
                Text(stringResource(R.string.activity_card_restore))
            }
        },
    )
}

/**
 * صفّ خيارات من شرائح.
 *
 * والشرائح (`FilterChip`) لا قائمة منسدلة: الخيارات قليلة ومعلنة، ورؤيتها كلها معًا تجعل الوضع
 * الحالي مقروءًا بلا نقرة — وهذا كل ما يُطلب من إعداد عرض. والقائمة المنسدلة تُخفي الاختيار
 * الحالي خلف نقرة، وهو عكس المقصود.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(
    title: String,
    choices: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            choices.forEach { (token, label) ->
                FilterChip(
                    selected = token == selected,
                    onClick = { onSelect(token) },
                    label = { Text(label) },
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun styleLabel(style: CardStyle): String = stringResource(
    when (style) {
        CardStyle.AUTO -> R.string.activity_card_style_auto
        CardStyle.CINEMATIC -> R.string.activity_card_style_cinematic
        CardStyle.SUMMARY -> R.string.activity_card_style_summary
        CardStyle.TIMELINE -> R.string.activity_card_style_timeline
        CardStyle.CHIPS -> R.string.activity_card_style_chips
    },
)

@Composable
private fun verbosityLabel(verbosity: CardVerbosity): String = stringResource(
    when (verbosity) {
        CardVerbosity.SIMPLE -> R.string.activity_card_detail_simple
        CardVerbosity.TECHNICAL -> R.string.activity_card_detail_technical
    },
)

@Composable
private fun motionLabel(motion: CardMotion): String = stringResource(
    when (motion) {
        CardMotion.FULL -> R.string.activity_card_motion_full
        CardMotion.REDUCED -> R.string.activity_card_motion_reduced
        CardMotion.OFF -> R.string.activity_card_motion_off
    },
)

@Composable
private fun sessionLabel(policy: SessionPolicy): String = stringResource(
    when (policy) {
        SessionPolicy.ALWAYS -> R.string.activity_card_session_always
        SessionPolicy.RECENT -> R.string.activity_card_session_recent
        SessionPolicy.HIDE -> R.string.activity_card_session_hide
    },
)
