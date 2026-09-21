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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import nd.max.R
import nd.max.core.hardware.ControlOwnership
import nd.max.core.hardware.HardwareControlKey
import nd.max.core.maxai.KnobOwnershipSnapshot
import nd.max.core.maxai.MaxAiState
import nd.max.core.maxai.OwnershipCommitState
import nd.max.ui.component.MaxSurface
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
 */
@Composable
internal fun StoryboardBand(
    maxAi: MaxAiState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // المشهدان الحيّان (آخر جلسة تطبيق، وأقفالك اليدوية) يُقرآن من مصدرَيهما على مسار IO، في
    // دورة بطيئة. والقراءة الفاشلة تُعطي قائمة فارغة لا محتوى مُخترعًا — فلا بطاقة بلا دليل.
    val liveScenes by produceState(initialValue = emptyList<StoryboardScene>()) {
        while (true) {
            value = withContext(Dispatchers.IO) { StoryboardSources.scenes(context) }
            delay(STORYBOARD_REFRESH_MS)
        }
    }
    // مشهد الذكاء يُبنى من حالته هنا لا في `HomeDashboardViewModel`: تلك الشاشة تُقاس كل
    // ثانيتين وليست مالكة لحالة MAX AI، وصاحب الحالة هو مصدرها.
    val scenes = StoryboardModel.storyboard(liveScenes + maxAiSceneFrom(maxAi))
    // بلا مشاهد لا بطاقة: «افتراضي» ليست إنجازًا، ولوحة تقول «لا شيء» في كل مرة تُقرأ مرّة
    // ثم يُلغى النظر إليها — وهذا ضدّ غرضها.
    if (scenes.isEmpty()) return

    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.storyboard_title),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        AnimatedContent(
            targetState = scenes.first(),
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            label = "storyboard_headline",
        ) { scene ->
            SceneCard(scene, scheme)
        }
        scenes.drop(1).forEach { scene -> SceneCard(scene, scheme) }
    }
}

@Composable
private fun SceneCard(scene: StoryboardScene, scheme: ColorScheme) {
    MaxSurface(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(8.dp).clip(CircleShape).background(
                        when (scene.kind) {
                            SceneKind.PER_APP -> scheme.primary
                            SceneKind.MAX_AI -> scheme.tertiary
                            SceneKind.MANUAL -> scheme.secondary
                        }
                    )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(sceneLabelRes(scene.kind)),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                )
                scene.appLabel?.let { label ->
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.weight(1f))
                scene.atMs?.let { atMs ->
                    Text(
                        // «متى» من وقت المصدر نفسه، لا ساعة الواجهة: الفرق بين «الآن» و«قبل
                        // دقيقتين» هو الفرق بين حكم على الجهاز وحكم على قياس قديم.
                        text = stringResource(R.string.storyboard_measured_ago, relativeAge(atMs)),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            scene.lines.forEach { line -> SceneLine(line, scheme) }
        }
    }
}

@Composable
private fun SceneLine(line: StoryLine, scheme: ColorScheme) {
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
            line.reason?.takeIf { it.isNotBlank() }?.let { reason ->
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
