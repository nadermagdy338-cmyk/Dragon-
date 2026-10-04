/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.mainscreens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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

/**
 * لوحة «ما يحدث الآن» — الشاشة الرئيسية تحكي **أثر اختياراتك** بدل أن تُعيد قياس الجهاز.
 *
 * لماذا هذا الملف
 * ---------------
 * الحرارة والحمل والأنوية تُقاس في شاشاتها المالكة (`Thermal` · `CPU` · `GPU`)، وعرضها في
 * الرئيسية مرّة ثانية كان يُنتج لوحةً مكرَّرة تجيب سؤالًا لم يسأله أحد («كم الحرارة؟») وتترك
 * السؤال الحقيقي: هل فعّلتُ شيئًا؟ وهل عمل؟ وهذا السؤال جوابه موزّع على إحدى وخمسين شاشة.
 *
 * فالقاعدة: **لا رقم يُخترع هنا**. كل سطر يأتي من مصدره:
 *
 * | السطر | مصدره |
 * |---|---|
 * | تغيير مقبض بتطبيق | `PerAppHardwareStatus` (مكتوب في العتاد ثم مقروء) |
 * | البروفايل/المفتاح الذي اخترته | `AppConfig` (ملف اختياراتك) |
 * | «الذكاء يعمل» والمقابض التي يملكها | `MaxAiState.ownership` (دفتر الملكية) |
 * | ما قفلته بيدك | `ManualControlLocks` (قفل المستخدم) |
 *
 * وقراءة هذا الملف تُظهر النيّة بلا تجميل: لا يظهر صفّ «تمّ» لشيء لم يُقس، والاختيار يُكتب
 * بلا «من/إلى» لأنه ليس تغييرًا داخل قيمة قائمة — والقرار نفسه مُقاس في `StoryboardModel`.
 *
 * والحركة مقصودة: تبديل المشهد ينزلق (`AnimatedContent`) ليُقرأ كأنه يروي، لكن **القراءة لا
 * تعتمد عليها**: النصّ كامل في الحالتين (من يستعمل إعدادات تقليل الحركة يرى المحتوى نفسه).
 *
 * وسياسة الأسطر (أيّها يُعرض، وبلا تكرار، وبأي حدّ) ليست هنا بل في [UnifiedActivityModel]:
 * فالقرار يُقاس في JVM، وهذه الدالة ترسم ما يعود منها وحدها. وكانت السياسة مكتوبة هنا مرّة
 * واختيارات المستخدم (`Kill Background Apps` · البروفايل · الحاكمان) تُطرح فيها بشرط «وجود قبل» —
 * فيقرأ المستخدم بطاقةً لا تذكر اختياراته.
 *
 * والتخصيص (المرحلة ٥): الخيارات تُقرأ من `ActivityCardPreferences` وتُمرّر للنموذج، **والنمط الفعّال
 * يعود من النموذج** لأن الرسم يختلف به — شارات · وقت على كل سطر · مشهد واحد بملء العرض. ولا تُخمَّن
 * السياسة مرّتين في مكانين (وهو العطب الذي وُلد هذا الملف لإصلاحه).
 */
@Composable
internal fun UnifiedActivityCard(
    maxAi: MaxAiState,
    modifier: Modifier = Modifier,
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
    if (model.isEmpty) return

    val palette = neuralPalette()
    val scheme = MaterialTheme.colorScheme
    val scene = model.scenes.first()
    val duration = state.options.motion.enterMs
    NeuralPanel(modifier = modifier.fillMaxWidth(), accent = palette.accent) {
        NeuralSectionHeader(
            title = stringResource(R.string.home_activity_title),
            caption = stringResource(R.string.storyboard_title),
            accent = palette.accent,
        )
        // «الحركة موقوفة» تُلغي `AnimatedContent` نفسه لا مدّته فقط: صفر مدّة مع عنصر رسوم
        // متحرّكة يبقى عنصرًا يشارك في إطار الرسم، والإيقاف الحقيقي هو عدم استخدامه.
        if (duration <= 0) {
            SceneBody(model, scene, scheme, palette)
        } else {
            AnimatedContent(
                targetState = scene,
                transitionSpec = { fadeIn(tween(duration)) togetherWith fadeOut(tween(duration / 2)) },
                label = "unified-activity-scene",
            ) { current ->
                SceneBody(model, current, scheme, palette)
            }
        }
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
            // «خط زمني»: كل حدث برأسه ووقته (حيث وُجد وقت) بدل دمج الأسطر في قائمة تفقد متى وقع
            // كل حدث. وهذا هو الفرق المقصود بينه وبين «قائمة مختصرة»: ليست كثافة أسطر بل أحداث.
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

/**
 * سطر واحد في نمط الشارات: الاسم والقيمة في سطح مختصر واحد.
 *
 * وهو يُستعمل حين تكثر الميزات (قاعدة التلقائي) أو حين يختاره المستخدم، لأن السرد الطويل على
 * شاشة صغيرة يقرأه أحدهم مرّة ثم يتوقّف عن قراءته أصلًا.
 */
@Composable
private fun SceneChip(
    line: StoryLine,
    showReason: Boolean,
    scheme: ColorScheme,
    modifier: Modifier = Modifier,
) {
    val accent = when (line.tone) {
        LineTone.FAILED -> scheme.error
        LineTone.HELD -> scheme.onSurfaceVariant
        LineTone.DONE -> scheme.primary
        LineTone.OFF -> scheme.onSurfaceVariant
    }
    val change = changeText(line)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(MaxRadius.chip))
            .background(scheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(
            text = knobLabel(line.knob),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (change.isNotEmpty()) {
            Text(
                text = change,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        line.reason?.takeIf { showReason && it.isNotBlank() }?.let { reason ->
            Text(
                text = stringResource(R.string.storyboard_why, reason),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SceneLine(line: StoryLine, scheme: ColorScheme, showReason: Boolean = false) {
    val accent = when (line.tone) {
        LineTone.FAILED -> scheme.error
        LineTone.HELD -> scheme.onSurfaceVariant
        LineTone.DONE -> scheme.primary
        LineTone.OFF -> scheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = when (line.tone) {
                // رموز موجودة ومستعملة في هذا المستودع أصلًا: رمزٌ جديد هنا يعني احتمال أيقونة
                // غير موجودة، وهو فشل تصريف في CI لا فشل تجربة — والفرق كبير وقت الضغط.
                LineTone.FAILED -> Icons.Outlined.ErrorOutline
                LineTone.HELD -> Icons.Rounded.Lock
                LineTone.DONE -> Icons.Rounded.CheckCircle
                LineTone.OFF -> Icons.Rounded.RadioButtonUnchecked
            },
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = knobLabel(line.knob),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurface,
                )
                val change = changeText(line)
                if (change.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = change,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // والسبب يظهر لسببين فقط: إمّا أن السطر لم يُنفّذ (فيقول لماذا)، وإمّا أن المستخدم
            // طلب الوضع التقني المختصر — وسطر السبب على كل نجاح هو ما يجعله لا يُقرأ في الحالتين.
            line.reason?.takeIf { (showReason || line.tone != LineTone.DONE) && it.isNotBlank() }?.let { reason ->
                Text(
                    // السبب كما كتبه العتاد/المحرّك (`not-verified` · `sconfig_missing` …) لا
                    // ترجمة إنشائية: من يرسل السجل يجد نفس الرمز، ومن يقرأ يعرف ما وقع.
                    text = stringResource(R.string.storyboard_why, reason),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** النصّ المرافق للمقبض: «من كذا إلى كذا» عند وجود قياس، وإلا القيمة وحدها (اختيارك). */
private fun changeText(line: StoryLine): String {
    val to = line.to?.takeIf { it.isNotBlank() } ?: return ""
    val from = line.from?.takeIf { it.isNotBlank() }
    return if (from != null && from != to) "$from → $to" else to
}

/** اسم المقبض بلغتك، ويرجع المفتاح نفسه حين لا نعرفه — صدق أوضح من اسم مُخترع. */
@Composable
private fun knobLabel(knob: String): String = when {
    knob == "gpu_profile" -> stringResource(R.string.storyboard_knob_gpu_profile)
    // الفحص من المفتاح القانوني لا من نصّ مكتوب هنا: صيغة ثانية للمقبض نفسه تُنتج سطرًا لا
    // يُعرَف، والتسمية تسقط إلى المفتاح الخام فيقرأ المستخدم `cpu_limits:policy4` بلا معنى.
    HardwareControlKey.isCpuLimits(knob) -> stringResource(R.string.storyboard_knob_cpu_limits)
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
