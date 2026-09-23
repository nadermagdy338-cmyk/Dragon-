/**
 * Neural controls — the kit's interactive surfaces.
 *
 * Split from `NeuralDashboardKit.kt` on purpose: that file owns *readouts and
 * plots*, this one owns *controls*, and the kit crossed the repository's
 * oversized-file ceiling when the segmented picker moved in. The split is by
 * responsibility, not by size excuse — every symbol here speaks the same
 * palette, shapes and press feedback as the kit (via `neuralClickable`).
 */
package nd.max.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import nd.max.ui.theme.MonoValueStyleSmall

/**
 * Segmented picker — one surface, a few states of a single choice (base
 * profile, activity-card scenes, …).
 *
 * Why a segmented picker and not a row of buttons: closely-related options
 * (three base profiles) are one decision with several states, and drawing them
 * as one control says exactly that. It also removes the confirm tap: the touch
 * *is* the switch. Selection moves by background color only, so the row never
 * reflows mid-switch, and the applied state is always the one with the lit
 * segment — the same rule the home profile rail relies on.
 *
 * [selectedIndex] of `-1` renders no lit segment: «no known state yet» must not
 * borrow one of the options as its face.
 */
@Composable
fun NeuralSegmented(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color? = null,
) {
    val p = neuralPalette()
    val tone = accent ?: p.accent
    // تذكرة لمس خفيفة على التبديل — الميكرو-تفاعل الوحيد الذي يملكه المبدّل. وإحساسها النهائي
    // يُحكم على جهاز لا هنا، ويسكن تلقائيًّا مع تعطيل حركة النظام.
    val haptics = LocalHapticFeedback.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(NeuralTileShape)
            .background(p.tile)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            // المقطع المختار **مفتاح مُضاء**: تدرّج رأسي (لا لون مسطّح) + هالة خفيفة تحته،
            // فيُقرأ الاختيار كضغط مفتاح فيزيائي لا كتغيير لون. والتدرّجان يُحرَّكان معًا، فلا
            // يقفز الشكل لحظة التبديل.
            val fillTop by animateColorAsState(
                targetValue = if (selected) tone.copy(alpha = .28f) else Color.Transparent,
                animationSpec = tween(MaxMotion.fast, easing = FastOutSlowInEasing),
                label = "neural-segmented-fill-top",
            )
            val fillBottom by animateColorAsState(
                targetValue = if (selected) tone.copy(alpha = .10f) else Color.Transparent,
                animationSpec = tween(MaxMotion.fast, easing = FastOutSlowInEasing),
                label = "neural-segmented-fill-bottom",
            )
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Brush.verticalGradient(listOf(fillTop, fillBottom)))
                    .border(
                        BorderStroke(1.dp, if (selected) tone.copy(alpha = .42f) else Color.Transparent),
                        RoundedCornerShape(11.dp),
                    )
                    .neuralClickable {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSelect(index)
                    }
                    .padding(horizontal = 6.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (selected) tone else p.muted,
                    fontSize = 11.5.sp,
                    lineHeight = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Status dot. Pulses **only** while the state it represents is genuinely live
 * (the AI engine running); a resting state is a static dot — a pulsing dot for a
 * stopped engine would be decoration lying about the device.
 */
@Composable
fun NeuralLiveDot(
    active: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 7.dp,
) {
    val alpha = if (active) {
        val transition = rememberInfiniteTransition(label = "neural-live-dot")
        transition
            .animateFloat(
                initialValue = .45f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1_100), RepeatMode.Reverse),
                label = "neural-live-dot-alpha",
            )
            .value
    } else {
        1f
    }
    // النقطة وهي تعمل تتنفّس **داخل هالة**: هالة بلا نبض تظلّ علامة ساكنة، والنبض بلا هالة
    // يظلّ نقطة تتلاشى. والاثنان معًا: محرّك يعمل، بلا مؤثّر يدّعي قراءة.
    Box(
        modifier
            .size(size * 2.6f)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(color.copy(alpha = if (active) .30f * alpha else 0f), Color.Transparent),
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .graphicsLayer { this.alpha = alpha }
                .clip(CircleShape)
                .background(color),
        )
    }
}

/** Hairline rule between related sections of one panel. */
@Composable
fun NeuralDivider(modifier: Modifier = Modifier) {
    val p = neuralPalette()
    Box(modifier.fillMaxWidth().height(1.dp).background(p.border))
}

/** خيار مجسّ واحد في [NeuralSensorPicker]: مفتاحه الداخلي، اسمه، قراءته اللحظية، وحالته. */
@Immutable
data class NeuralSensorOption(
    val key: String,
    val label: String,
    val reading: String,
    val selected: Boolean,
    val accent: Color,
)

/**
 * اختيار المجسّات — حوار يحدّد أي القراءات تثبَّت في شبكة العرض.
 *
 * والمرجع: DevCheck، حيث لمس بطاقة الحرارة يفتح اختيار المجسّات المعروضة. وقاعدتان هنا:
 * لا تثبيت على [limit] (والخيارات غير المثبَّتة تُعطَّل عند الامتلاء بدل أن تُصرَّح ثم تُرفض)،
 * ولا يُلغى آخر مثبَّت — والمنع من الطرف النموذجي (`ThermalGridModel`). والقراءة بجانب كل
 * اسم لحظية كما قيسَت: اختيارٌ أعمى لا يخدم أحدًا.
 */
@Composable
fun NeuralSensorPicker(
    title: String,
    caption: String,
    limit: Int,
    options: List<NeuralSensorOption>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = neuralPalette()
    val selectedCount = options.count { it.selected }
    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            NeuralPanel(Modifier.widthIn(max = 340.dp), verticalSpacing = 4.dp) {
                NeuralSectionHeader(title = title, caption = caption, accent = p.accent)
                options.forEach { option ->
                    val disabled = !option.selected && selectedCount >= limit
                    val toggle: (() -> Unit)? = if (disabled) null else ({ onToggle(option.key) })
                    Row(
                        Modifier.fillMaxWidth()
                            .neuralClickable(toggle)
                            .padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(option.accent))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            option.label,
                            Modifier.weight(1f),
                            color = if (disabled) p.muted else p.text,
                            fontSize = 12.5.sp,
                            lineHeight = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        NeuralValue(
                            option.reading,
                            style = MonoValueStyleSmall.copy(fontSize = 11.sp),
                            color = p.muted,
                        )
                        Spacer(Modifier.width(10.dp))
                        Box(
                            Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(
                                    if (option.selected) option.accent.copy(alpha = .18f) else Color.Transparent
                                )
                                .border(
                                    BorderStroke(1.dp, if (option.selected) option.accent else p.border),
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (option.selected) {
                                Icon(Icons.Rounded.Check, null, Modifier.size(12.dp), tint = option.accent)
                            }
                        }
                    }
                }
            }
        }
    }
}
