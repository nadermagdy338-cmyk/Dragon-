/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nd.max.R
import nd.max.ui.design.MaxAlpha
import nd.max.ui.design.MaxSize

/**
 * مدخل Max AI في الشاشة الرئيسية — زرّ مستقلّ بنيّ من الصفر.
 *
 * البنية: كبسولة بتدرّج أفقيّ هادئ من لون التطبيق، وفي بدايتها شارة دائرية ممتلئة تحمل
 * أيقونة عاديّة (`AutoAwesome`)، ثم الاسم، ثم سهم متّجه مع اتجاه اللغة يدلّ أن الزرّ
 * ينقل إلى شاشة أخرى.
 *
 * - **الحالة** ([active]): مُشغَّل = لون التطبيق كاملًا؛ متوقّف = ألوان محايدة مطفأة.
 *   (كانت الحالة تُحمل في توهّج العلامة المحذوفة، فانتقلت إلى اللون بدل أن تُسقَط.)
 *   **واللون لا يقول شيئًا لقارئ الشاشة ولا لمن لا يميّز اللون** — فالحالة تُعلن نصًّا في
 *   الـ`stateDescription`، والزرّ نفسه يُوصف بما يفعله («افتح Max AI») بدل أن يُقرأ
 *   «Max AI، زرّ» بلا فعل. و[stateLabel] تأتي من [maxAiShortcutStateLabel] — العين نفسها
 *   التي يقرأ منها الاختصار في الشاشات، فلا يقول سطح «مُشغَّل» وآخر «نشِط».
 * - **الإتاحة**: الشكل المرئي ٤٠dp، ومنطقة اللمس ٤٨dp (`MaxSize.minTouchTarget`) عبر
 *   `minimumInteractiveComponentSize` فلا يكبر الشكل ولا تصغر منطقة الضغط. والدور `Button`
 *   والضغط (تصغير ٢٫٥٪ + ripple) من `neuralClickable` نفسه المستعمل في بقية اللوحة.
 * - **لا ألوان جديدة**: كل الألفا من `MaxAlpha`، وكل اللون من `MaterialTheme.colorScheme`.
 */
@Composable
fun MaxAiEntryButton(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val openLabel = stringResource(R.string.maxai_banner_open)
    val stateLabel = stringResource(maxAiShortcutStateLabel(active))
    val accent = if (active) scheme.primary else scheme.onSurfaceVariant

    val surface = if (active) {
        Brush.horizontalGradient(
            listOf(
                accent.copy(alpha = MaxAlpha.toneContainerStrong),
                accent.copy(alpha = MaxAlpha.toneContainerStrong * 0.5f),
            )
        )
    } else {
        Brush.horizontalGradient(listOf(scheme.surfaceContainerHigh, scheme.surfaceContainerHigh))
    }
    val outline = if (active) accent.copy(alpha = MaxAlpha.borderStrong) else scheme.outlineVariant
    val badge = if (active) scheme.primary else scheme.onSurfaceVariant.copy(alpha = MaxAlpha.border)
    val badgeIcon = if (active) scheme.onPrimary else scheme.onSurfaceVariant
    val label = if (active) scheme.onSurface else scheme.onSurfaceVariant

    Row(
        modifier
            .minimumInteractiveComponentSize()
            .height(BUTTON_HEIGHT)
            .clip(CircleShape)
            .background(surface)
            .border(MaxSize.hairlineBorder, outline, CircleShape)
            .semantics(mergeDescendants = true) {
                // ما يفعله الزرّ، لا اسمه مرّتين: النصّ المرئيّ `Max AI` يُدمج في هذه القيمة.
                contentDescription = openLabel
                stateDescription = stateLabel
            }
            .neuralClickable(onClick, role = Role.Button)
            .padding(start = 6.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(BADGE_SIZE).clip(CircleShape).background(badge),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(MaxSize.iconGlyphSmall),
                tint = badgeIcon,
            )
        }
        Text(
            text,
            color = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = accent,
        )
    }
}

private val BUTTON_HEIGHT = 40.dp
private val BADGE_SIZE = 28.dp
