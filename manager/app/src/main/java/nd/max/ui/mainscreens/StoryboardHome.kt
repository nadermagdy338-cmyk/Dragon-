/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.mainscreens

import nd.max.ui.design.MaxSpace

import nd.max.ui.design.MaxSize

import androidx.annotation.StringRes

import androidx.compose.ui.graphics.Color

import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight

import androidx.compose.ui.semantics.Role

import androidx.compose.foundation.clickable

import androidx.compose.runtime.compositionLocalOf

import androidx.compose.runtime.CompositionLocalProvider

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nd.max.ui.design.MaxRadius
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import nd.max.R
import nd.max.core.hardware.ControlOwnership
import nd.max.core.hardware.HardwareControlKey
import nd.max.core.maxai.KnobOwnershipSnapshot
import nd.max.core.maxai.MaxAiState
import nd.max.core.maxai.OwnershipCommitState
import nd.max.ui.component.NeuralPalette
import nd.max.ui.component.NeuralPanel
import nd.max.ui.component.NeuralSectionHeader
import nd.max.ui.component.neuralPalette
import nd.max.ui.util.ActivityCardPreferences
import nd.max.ui.util.StoryboardSources

/**
 * دورة القراءة: عشر ثوانٍ لا ثانيتان.
 *
 * اللوحة تحكي **نتيجة اختيار** يتغيّر عند فتح تطبيق أو قفل مقبض، لا قياسًا لحظيًّا؛ فالقراءة
 * الأسرع تُنتج الأسطر نفسها وتزيد نداءات الجذر بينما الشاشة مفتوحة بلا فائدة.
 */
private const val STORYBOARD_REFRESH_MS = 10_000L



/** حالة القراءة: المشاهد والخيارات معًا — تُقرآن في نفس الدورة، فلا تُرسم بطاقة بخيارات قديمة. */
private data class CardState(
    val scenes: List<StoryboardScene> = emptyList(),
    val options: UnifiedActivityModel.CardOptions = UnifiedActivityModel.CardOptions.DEFAULT,
)

/**
 * جسم البطاقة: رأس المشهد ثم أسطره بشكل النمط الفعّال.
 *
 * والشارات (`CHIPS`) تُرسم هنا أيضًا لأن المشهد الأول هو ما يُعرض بحركته في كل الأنماط.
 */
@Composable
private fun SceneBody(
    model: UnifiedActivityModel.CardModel,
    scene: StoryboardScene,
    scheme: ColorScheme,
    palette: NeuralPalette,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SceneHeading(scene, palette)
        if (model.style == UnifiedActivityModel.CardStyle.CHIPS) {
            for (pair in scene.lines.chunked(2)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (line in pair) {
                        SceneChip(line, model.showReason, scheme, Modifier.weight(1f))
                    }
                }
            }
        } else {
            for (line in scene.lines) {
                SceneLine(line, scheme, showReason = model.showReason)
            }
        }
    }
}

/**
 * رأس الحدث: نقطة الحالة ثم نوعه ثم التطبيق ثم **زمنه**.
 *
 * وهو مستخرج لأن نمط «خط زمني» يحتاجه لكل حدث، لا للحدث الأول وحده — ونسخة ثانية منه كانت
 * ستفترق عن هذه عند أول تعديل (وهو عين العطب الذي وُلد هذا الملف لإصلاحه في السياسة).
 *
 * والزمن من وقت المصدر (`atMs`) لا من ساعة الواجهة: الفرق بين «الآن» و«قبل دقيقتين» هو الفرق
 * بين حكم على الجهاز وحكم على قياس قديم. ومشهد بلا وقت (تحكّم يدوي أو ذكاء) يُكتب بلا زمن
 * أصلاً، ولا زمن يُخترع له. وإذا كان الزمن **صفراً أو سالباً** لا يُعرض أيضاً: كلاهما قيمة
 * «لا أعرف» لا لحظة حقيقية (وبدونهما كان يُقرأ «قبل ٥٦ سنة»).
 */
@Composable
private fun SceneHeading(scene: StoryboardScene, palette: NeuralPalette) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(8.dp).clip(CircleShape).background(palette.ok),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(sceneLabelRes(scene.kind)),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = palette.text,
        )
        scene.appLabel?.let {
            Spacer(Modifier.width(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        scene.atMs?.takeIf { it > 0L }?.let { atMs ->
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.storyboard_measured_ago, relativeAge(atMs)),
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted,
            )
        }
    }
}







/** اسم المقبض بلغتك، ويرجع المفتاح نفسه حين لا نعرفه — صدق أوضح من اسم مُخترع. */
@Composable
private fun knobLabel(knob: String): String = when {
    knob == "gpu_profile" -> stringResource(R.string.storyboard_knob_gpu_profile)
    // الفحص من المفتاح القانوني لا من نصّ مكتوب هنا: صيغة ثانية للمقبض نفسه تُنتج سطرًا لا
    // يُعرَف، والتسمية تسقط إلى المفتاح الخام فيقرأ المستخدم `cpu_limits:policy4` بلا معنى.
    HardwareControlKey.isCpuLimits(knob) -> cpuLimitsTitle(knob)
    knob == "gpu_max_freq" -> stringResource(R.string.storyboard_knob_gpu_max_freq)
    knob == "thermal" -> stringResource(R.string.storyboard_knob_thermal)
    knob == HardwareControlKey.CPU_BOOST -> stringResource(R.string.storyboard_knob_cpu_boost)
    knob == "kill_bg_apps" -> stringResource(R.string.storyboard_knob_kill_bg)
    knob == "refresh_rate" -> stringResource(R.string.storyboard_knob_refresh)
    knob == "renderer" -> stringResource(R.string.storyboard_knob_renderer)
    knob == "gpu_governor" -> stringResource(R.string.storyboard_knob_gpu_governor)
    knob == "cpu_governor" -> stringResource(R.string.storyboard_knob_cpu_governor)
    knob == "max_ai_active" -> stringResource(R.string.storyboard_knob_max_ai)
    knob == "max_ai_objective" -> stringResource(R.string.storyboard_knob_max_ai_objective)
    else -> knob
}

private fun sceneLabelRes(kind: SceneKind): Int = when (kind) {
    SceneKind.PER_APP -> R.string.storyboard_scene_per_app
    SceneKind.MAX_AI -> R.string.storyboard_scene_max_ai
    SceneKind.MANUAL -> R.string.storyboard_scene_manual
}

@Composable
private fun relativeAge(atMs: Long): String {
    val seconds = ((System.currentTimeMillis() - atMs) / 1000L).coerceAtLeast(0L)
    val minutes = seconds / 60L
    return when {
        seconds < 60 -> stringResource(R.string.storyboard_age_seconds, seconds)
        // الساعة تلي الدقيقة: جلسة تطبيق فُتحت قبل ساعات تُقرأ «قبل ٣ ساعات» لا «قبل 240 د»،
        // والمقصد واحد: أن يعرف القارئ كم يبعد هذا القياس عن الآن قبل أن يحكم به على جهازه.
        minutes < 60 -> stringResource(R.string.storyboard_age_minutes, minutes)
        else -> stringResource(R.string.storyboard_age_hours, minutes / 60L)
    }
}

/**
 * مشهد الذكاء من حالته الحقيقية — ولا سطر فيه بلا مصدر:
 *
 * - `aiEnabled` ⇒ سطر «يعمل».
 * - `objectivePreference`/`strategyLabel` ⇒ سطر الهدف (اختيارك لا إنجازك).
 * - `ownership` ⇒ سطر لكل مقبض **يملكه الذكاء فعلًا** (لا كل مقبض لُمس): الملكية هي الدليل،
 *   والحالة `VERIFIED` تعني استقرّت. وما لم يستقرّ يظهر `HELD` بسببه بدل أن يُعرض كنجاح.
 */
internal fun maxAiSceneFrom(state: MaxAiState): StoryboardScene? {
    val ownedLines = state.ownership
        .filter { it.owner == ControlOwnership.Owner.MAX_AI }
        .map { owned: KnobOwnershipSnapshot ->
            StoryLine(
                knob = owned.key,
                from = null,
                to = owned.desired.takeIf { it.isNotBlank() },
                // الحالة مصدرها الدفتر: `VERIFIED` تعني استقرّ المقبض على القيمة، و`PENDING`
                // تعني لم يُحسم بعد — فالسطر يقولها كما هي بدل أن يعدّها نجاحًا.
                tone = if (owned.state == OwnershipCommitState.VERIFIED) LineTone.DONE else LineTone.HELD,
                reason = if (owned.state == OwnershipCommitState.VERIFIED) null else owned.state.name.lowercase(),
            )
        }
    return StoryboardModel.maxAiScene(
        MaxAiSceneInput(
            active = state.aiEnabled,
            // `strategyLabel` («يدوي») احتياط للهدف **فقط وهو يعمل**: عرضه والذكاء متوقّف يقول
            // «الهدف: يدوي» لذكاء لا يعمل — جملة صحيحة لغويًّا ومضلِّلة فنيًّا.
            objective = state.objectivePreference
                ?: state.strategyLabel.takeIf { it.isNotBlank() && state.aiEnabled },
            ownedLines = ownedLines,
        ),
    )
}

/**
 * لوحة «النشاط الحالي» — تحكي **أثر اختياراتك** بجمل مفهومة، وتقع تحت بطاقتَي CPU وGPU مباشرة (طلب المالك).
 *
 * ما تعرضه، بالترتيب:
 * 1. ملخّصٌ من سطر: كم مُطبَّقًا، وكم محجوزًا، وكم فشل — فلا يحتاج القارئ إلى عدّ الأسطر.
 * 2. الأسطر: كل مقبض بعنوانه الواضح (ومعه عنقود المعالج إن وُجد)، وقيمته جملةً («من … إلى …»)،
 *    وسببه بلغة بشرية إن لم يُطبَّق.
 * 3. كل سطر باب: يفتح شاشة صاحب المقبض، فلا قيمة تُقرأ بلا مكان تُضبط فيه.
 * 4. إن لم يوجد شيء تقول البطاقة ذلك صراحةً، ولا تختفي.
 *
 * القاعدة باقية: لا رقم يُخترع هنا. والصياغة (من/إلى، النطاق، أسماء الملفات) في `ActivityFormat` و`StoryboardModel`.
 */
@Composable
internal fun UnifiedActivityCard(
    maxAi: MaxAiState,
    modifier: Modifier = Modifier,
    onNavigate: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val state by produceState(initialValue = CardState()) {
        while (true) {
            value = withContext(Dispatchers.IO) {
                CardState(
                    scenes = StoryboardSources.scenes(context),
                    options = ActivityCardPreferences.read(context),
                )
            }
            delay(STORYBOARD_REFRESH_MS)
        }
    }
    // الترتيب والحذف والتكرار والحدّ والشكل: كلّها في النموذج المختبر، لا في هذه الدالة.
    val model = UnifiedActivityModel.build(
        scenes = StoryboardModel.storyboard(state.scenes + maxAiSceneFrom(maxAi)),
        options = state.options,
        nowMs = System.currentTimeMillis(),
    )

    val palette = neuralPalette()
    val scheme = MaterialTheme.colorScheme
    CompositionLocalProvider(LocalActivityNavigate provides onNavigate) {
        NeuralPanel(modifier = modifier.fillMaxWidth(), accent = palette.accent) {
            NeuralSectionHeader(
                title = stringResource(R.string.home_activity_title),
                caption = stringResource(R.string.home_activity_caption),
                accent = palette.accent,
            )
            if (model.isEmpty) {
                ActivityEmpty(palette)
            } else {
                ActivitySummary(model.scenes.flatMap { it.lines }, palette)
                SceneBody(model, model.scenes.first(), scheme, palette)
                // ما تبقّى: مُرشَّح سلفًا (لا تكرار مع المشهد الأول)، ومحدود برصيد البطاقة، وبشكله.
                val rest = model.scenes.drop(1)
                when (model.style) {
                    UnifiedActivityModel.CardStyle.CHIPS -> {
                        for (pair in rest.flatMap { it.lines }.chunked(2)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (line in pair) {
                                    SceneChip(line, model.showReason, scheme, Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    // «خط زمني»: كل حدث برأسه ووقته، لا قائمة تفقد متى وقع كل حدث.
                    UnifiedActivityModel.CardStyle.TIMELINE -> {
                        for (event in rest) {
                            Spacer(Modifier.height(10.dp))
                            SceneHeading(event, palette)
                            for (line in event.lines) {
                                SceneLine(line, scheme, showReason = model.showReason)
                            }
                        }
                    }
                    else -> {
                        for (line in rest.flatMap { it.lines }) {
                            SceneLine(line, scheme, showReason = model.showReason)
                        }
                    }
                }
            }
        }
    }
}

/** مسار الفتح يصل إلى كل سطر دون أن يمرّ وسيطًا في كل دالة. الافتراضي لا يفعل شيئًا. */
private val LocalActivityNavigate = compositionLocalOf<(String) -> Unit> { { _ -> } }

/** الملخّص من سطر: مُطبَّق · محجوز · فشل. ولا يظهر حين لا حدث أصلًا. */
@Composable
private fun ActivitySummary(lines: List<StoryLine>, palette: NeuralPalette) {
    val applied = lines.count { it.tone == LineTone.DONE }
    val held = lines.count { it.tone == LineTone.HELD }
    val failed = lines.count { it.tone == LineTone.FAILED }
    if (applied + held + failed == 0) return
    Text(
        stringResource(R.string.activity_summary, applied, held, failed),
        color = palette.muted,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** الحالة الفارغة تقول الحقيقة: كل شيء على الافتراضي، وMax يراقب فقط. */
@Composable
private fun ActivityEmpty(palette: NeuralPalette) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MaxSpace.xs)) {
        Text(
            stringResource(R.string.activity_empty_title),
            color = palette.text,
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            stringResource(R.string.activity_empty_detail),
            color = palette.muted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * سطر واحد: أيقونة الحالة، ثم عنوان واضح، ثم القيمة جملةً، ثم السبب إن لم يُطبَّق.
 * وإن كان للمقبض باب معروف فالسطر كلّه زرّ يفتح شاشته، والسهم في نهايته منعكس تلقائيًا في العربية.
 * والنص يلتفّ على أسطره بدل أن يُقطع في منتصف الرقم.
 */
@Composable
private fun SceneLine(line: StoryLine, scheme: ColorScheme, showReason: Boolean = false) {
    val navigate = LocalActivityNavigate.current
    val route = ActivityFormat.routeFor(line.knob)
    val accent = when (line.tone) {
        LineTone.FAILED -> scheme.error
        LineTone.HELD -> scheme.onSurfaceVariant
        LineTone.DONE -> scheme.primary
        LineTone.OFF -> scheme.onSurfaceVariant
    }
    val title = knobLabel(line.knob)
    val value = changeText(line).ifEmpty { stateWord(line.tone) }
    val reason = line.reason
        ?.takeIf { (showReason || line.tone != LineTone.DONE) && it.isNotBlank() }
        ?.let { reasonText(it) }
    val tap = if (route != null) Modifier.clickable(role = Role.Button) { navigate(route) } else Modifier
    Row(
        Modifier
            .fillMaxWidth()
            .then(tap)
            .padding(vertical = MaxSpace.xs),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = when (line.tone) {
                LineTone.FAILED -> Icons.Outlined.ErrorOutline
                LineTone.HELD -> Icons.Rounded.Lock
                LineTone.DONE -> Icons.Rounded.CheckCircle
                LineTone.OFF -> Icons.Rounded.RadioButtonUnchecked
            },
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(MaxSize.iconGlyphSmall),
        )
        Spacer(Modifier.width(MaxSpace.sm))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MaxSpace.hairline)) {
            Text(
                title,
                color = scheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                value,
                color = accent,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
            if (reason != null) {
                Text(
                    reason,
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (route != null) {
            Spacer(Modifier.width(MaxSpace.xs))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(MaxSize.iconGlyphSmall),
            )
        }
    }
}

/** شريحة في شبكة الشرائح: العنوان والقيمة والسبب في عمود واحد، وكلها باب إلى شاشة صاحبها إن وُجدت. */
@Composable
private fun SceneChip(line: StoryLine, showReason: Boolean, scheme: ColorScheme, modifier: Modifier) {
    val navigate = LocalActivityNavigate.current
    val route = ActivityFormat.routeFor(line.knob)
    val accent = when (line.tone) {
        LineTone.FAILED -> scheme.error
        LineTone.HELD -> scheme.onSurfaceVariant
        LineTone.DONE -> scheme.primary
        LineTone.OFF -> scheme.onSurfaceVariant
    }
    val title = knobLabel(line.knob)
    val value = changeText(line).ifEmpty { stateWord(line.tone) }
    val reason = line.reason
        ?.takeIf { (showReason || line.tone != LineTone.DONE) && it.isNotBlank() }
        ?.let { reasonText(it) }
    val tap = if (route != null) Modifier.clickable(role = Role.Button) { navigate(route) } else Modifier
    Column(
        modifier
            .clip(RoundedCornerShape(MaxRadius.row))
            .background(scheme.surfaceContainerHigh)
            .then(tap)
            .padding(MaxSpace.sm),
        verticalArrangement = Arrangement.spacedBy(MaxSpace.hairline),
    ) {
        Text(
            title,
            color = scheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            value,
            color = accent,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
        if (reason != null) {
            Text(
                reason,
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * القيمة كما تُقرأ: «من … إلى …» بالكلمات، لا سهمًا بين قيمتين. السهم كان ينعكس في النصّ العربي فيبدو
 * الانتقال من الحالي إلى السابق، والكلمات لا يغيّرها اتجاه السطر.
 */
@Composable
private fun changeText(line: StoryLine): String {
    val to = line.to?.takeIf { it.isNotBlank() }?.let { valueFor(line.knob, it) } ?: return ""
    val from = line.from?.takeIf { it.isNotBlank() }?.let { valueFor(line.knob, it) }
    return if (from != null && from != to) stringResource(R.string.activity_from_to, from, to) else to
}

/** الخام يصير مفهومًا: حدّ المعالج نطاقًا بوحداته، والملف الشخصي اسمه، والمفتاح المنطقي كلمةً. */
@Composable
private fun valueFor(knob: String, raw: String): String {
    ActivityFormat.khzPair(raw)?.let { (min, max) -> return StoryboardModel.readableRange(min, max) }
    if (knob == "gpu_profile" || knob == "max_ai_objective") {
        ActivityFormat.profileOf(raw)?.let { return stringResource(profileRes(it)) }
    }
    return when (raw.trim().lowercase()) {
        "on", "true" -> stringResource(R.string.activity_value_on)
        "off", "false" -> stringResource(R.string.activity_value_off)
        else -> raw
    }
}

@StringRes
private fun profileRes(profile: ActivityProfile): Int = when (profile) {
    ActivityProfile.DEFAULT -> R.string.activity_profile_default
    ActivityProfile.BALANCED -> R.string.profile_balanced
    ActivityProfile.PERFORMANCE -> R.string.profile_performance
    ActivityProfile.POWERSAVE -> R.string.profile_powersave
    ActivityProfile.GAMING -> R.string.profile_label_gaming
}

@StringRes
private fun clusterRes(cluster: ActivityCluster): Int = when (cluster) {
    ActivityCluster.EFFICIENCY -> R.string.cpu_cluster_short_silver
    ActivityCluster.PERFORMANCE -> R.string.cpu_cluster_short_gold
    ActivityCluster.PRIME -> R.string.cpu_cluster_short_prime
}

/** «حدود المعالج» وحدها تكرّرت لعنقودين مختلفين؛ الآن يُذكر العنقود: «حدود المعالج · كفاءة». */
@Composable
private fun cpuLimitsTitle(knob: String): String {
    val base = stringResource(R.string.storyboard_knob_cpu_limits)
    val cluster = ActivityFormat.clusterOf(knob.substringAfter(':', "")) ?: return base
    return stringResource(R.string.activity_knob_cpu_cluster, base, stringResource(clusterRes(cluster)))
}

/** السبب الخام من العتاد يصير واحدًا من أسباب قليلة يفهمها المستخدم، لا رمزًا داخليًا. */
@Composable
private fun reasonText(raw: String): String = stringResource(
    when (ActivityFormat.reasonOf(raw)) {
        ActivityReason.UNVERIFIED -> R.string.activity_reason_unverified
        ActivityReason.UNSUPPORTED -> R.string.activity_reason_unsupported
        ActivityReason.NOT_WRITABLE -> R.string.activity_reason_not_writable
        ActivityReason.HELD -> R.string.activity_reason_held
        ActivityReason.FAILED -> R.string.activity_reason_failed
        ActivityReason.OTHER -> R.string.activity_reason_other
    }
)

/** مقبض بلا قيمة (مثل محرّك الذكاء) يقول حالته بكلمة، لا بعلامة صحّ وحدها. */
@Composable
private fun stateWord(tone: LineTone): String = stringResource(
    when (tone) {
        LineTone.DONE -> R.string.activity_value_on
        LineTone.HELD -> R.string.activity_value_held
        LineTone.FAILED -> R.string.activity_value_failed
        LineTone.OFF -> R.string.activity_value_off
    }
)
