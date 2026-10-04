/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * استوديو الصوت — **شريط التبويبات العائم**.
 *
 * ───────────────────────── Attribution (Apache-2.0) ─────────────────────────
 * The floating tab toolbar (a pill whose selected item carries the label, with an animated
 * equalizer glyph) is adapted from the **DolbyUI** interface — branch `rodin` of
 * `Digimend-X-Rodin/packages_apps_DolbyUI` (a fork of `swiitch-OFF-Lab/packages_apps_DolbyUI`),
 * licensed **Apache-2.0** as stated in its own file headers, and used with the copyright holder's
 * permission. This is a rework, not a copy — the bar here is filled evenly so selecting a tab
 * never reflows it; the credit is in `docs/PROVENANCE.md`.
 *
 * ────────────────────── العطب الذي أُغلق هنا (مقيس لا مُتخيَّل) ──────────────────────
 * كان اختيار التبويب `MaxSegmented` **داخل** تمرير الشاشة، فمن مرّر إلى أسفل قسم المحرّك (‏٧٩٣ سطرًا
 * من الأقسام) لم يبقَ أمامه إلّا أن يعود إلى أعلى الصفحة ليبدّل تبويبًا. وهذا ليس ذوقًا في الشكل بل
 * **عملٌ مطلوب لا يمكن أداؤه** إلّا بالتمرير إلى الطرف الآخر.
 *
 * فالشريط هنا **خارج التمرير** (يُعلَّق في `Box` فوق الشاشة لا داخلها)، و**يُملأ العرض بالتساوي**:
 * كل تبويب يأخذ `weight(1f)`، فالاختيار لا يغيّر عرض الشريط ولا يزحزحه — بخلاف شريط المصدر الذي
 * يتمدّد مع التسمية المختارة فيُعيد ترتيب الثلاثة في كل تبديل. والتسمية تظهر مع الاختيار بحركةٍ قصيرة
 * (`MaxDuration.quick`) **داخل عرضها المحجوز**، فلا قفزةً ولا انزلاقًا خارج الإطار.
 *
 * **ومفردة المصدر محفوظة في أثرها:** التبويب المختار حبّةٌ ممتلئة بلون التمييز، وغير المختار رمزٌ
 * هادئ؛ وتبويب المحرّك يحمل **أعمدة معادل تنبض حين يعمل المحرّك فعلًا** (`engineActive`) وتسكن حين
 * لا يعمل — فالأيقونة تقول حالةً لا تتزيّن.
 */
package nd.max.ui.subscreens.audio

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nd.max.R
import nd.max.ui.component.maxPressMotion
import nd.max.ui.design.MaxDuration
import nd.max.ui.design.MaxEqualizerBars
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace

/**
 * تبويبات الاستوديو — **ثلاثة حدٌّ لا اختيار**: «مستويات» ما يكتبه الطريق القائم (دفقات)، و«محرّك»
 * المؤثّرات، و«نظام» ما يُقاس ويُخزَّن. و«ثلاثة» مقيَّدة بأنّ الشريط يسع ثلاثةً على شاشةٍ ضيّقة بأسماءٍ
 * عربيّة غير مبثورة — وزيادة رابع تُبتر أسماءه.
 */
internal enum class AudioTab(@StringRes val labelRes: Int, val icon: ImageVector) {
    /** **أوّلًا** — وما يُطلب منه أكثر شيء: المنحنى والأنماط ومؤثّر المصنّع. */
    Engine(R.string.max_audio_tab_engine, Icons.Rounded.GraphicEq),

    /** ثانياً — التحكّم العاديّ بالمستويات (أقلّ ما يُطلب، فليس في المقدّمة). */
    Levels(R.string.max_audio_tab_live, Icons.AutoMirrored.Rounded.VolumeUp),

    /** ثالثًا — القياس والتشخيص والطبقة النظاميّة. */
    System(R.string.max_audio_tab_system, Icons.Rounded.Tune),
}

/**
 * ما يُحجَز من أسفل الصفحة للشريط العائم: ارتفاعه الحقيقيّ (لمسةٌ كاملة + حشوه) — **رمزٌ يُشار إليه**،
 * فما يُحجَز هو ما يُرسم لا تقديرٌ بجانبه.
 */
internal val audioTabBarReserve: Dp = MaxSize.minTouchTarget + MaxSpace.sm

/**
 * الشريط العائم.
 *
 * @param engineActive هل يعمل المحرّك فعلًا؟ — به تنبض أعمدة تبويب المحرّك وتسكن، فلا أيقونة تتحرّك
 *   بلا قراءةٍ تسندها.
 */
@Composable
internal fun AudioTabBar(
    selected: AudioTab,
    onSelect: (AudioTab) -> Unit,
    modifier: Modifier = Modifier,
    engineActive: Boolean = false,
) {
    val tabs = AudioTab.entries
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MaxRadius.pill),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = MaxSpace.sm,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaxSpace.xs),
            horizontalArrangement = Arrangement.spacedBy(MaxSpace.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                AudioTabItem(
                    tab = tab,
                    selected = tab == selected,
                    engineActive = engineActive,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * تبويبٌ واحد — **نصف قطره ثابت فلا يقفز الشريط**، ولونه ودائرته يقولان الاختيار.
 *
 * و«الحبّة» المختارة بلون التمييز مع **نصف قطر الحبّة** (`MaxRadius.pill`) لا الحبّة داخل مستطيل،
 * فيقرؤها الإصبع هدفًا واحدًا قبل أن يقرأها العين حالة.
 */
@Composable
private fun AudioTabItem(
    tab: AudioTab,
    selected: Boolean,
    engineActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val label = stringResource(tab.labelRes)
    val ink = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        modifier = modifier.maxPressMotion(interaction),
        shape = RoundedCornerShape(MaxRadius.pill),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = ink,
        interactionSource = interaction,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MaxSize.minTouchTarget)
                .padding(horizontal = MaxSpace.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            val isEngine = tab == AudioTab.Engine
            if (isEngine) {
                // وأعمدة المعادل **تحلّ محلّ الرمز** في تبويب المحرّك: هي نفسها أيقونته، وتقول الحالة.
                MaxEqualizerBars(
                    active = selected && engineActive,
                    color = ink,
                    size = MaxSize.iconGlyph + MaxSpace.xs,
                )
            } else {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = label,
                    tint = ink,
                    modifier = Modifier.size(MaxSize.iconGlyph),
                )
            }

            AnimatedVisibility(
                visible = selected,
                enter = expandHorizontally(
                    animationSpec = tween(MaxDuration.quick, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Start,
                ) + fadeIn(animationSpec = tween(MaxDuration.quick)),
                exit = shrinkHorizontally(
                    animationSpec = tween(MaxDuration.quick, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.Start,
                ) + fadeOut(animationSpec = tween(MaxDuration.quick)),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = ink,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = MaxSpace.sm),
                )
            }
        }
    }
}
