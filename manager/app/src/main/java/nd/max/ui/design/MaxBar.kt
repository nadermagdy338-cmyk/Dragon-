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
 * شريط النسبة المشترك.
 *
 * كان في التطبيق شريطان لنفس الفكرة: `GlowLinearBar` بلغة لوحة البداية، وشريط مكتوب
 * داخل كل شاشة بلا لغة. ولأن الشاشتين المعنيتين (التخزين والحرارة) تحتاجان الشيء نفسه،
 * صار هنا مرة واحدة: نفس الارتفاع، ونفس اللون الدلالي، ونفس دلالة الإتاحة.
 *
 * و[marker] هو الفرق الذي يجعل الشريط يقول شيئًا لا يقوله الرقم وحده: نقطة التخفيف
 * الحرارية تُرسم على الشريط، فيرى المستخدم **كم بقي** لا درجة فقط.
 */
package nd.max.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import nd.max.ui.component.rememberAnimationsEnabled

@Composable
fun MaxUsageBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    tone: MaxTone = MaxTone.Accent,
    marker: Float? = null,
    height: Dp = MaxSize.barHeight
) {
    val safeFraction = if (fraction.isFinite()) fraction.coerceIn(0f, 1f) else 0f
    val animationsEnabled = rememberAnimationsEnabled()
    val animatedFraction by animateFloatAsState(
        targetValue = safeFraction,
        animationSpec = tween(durationMillis = MaxDuration.standard, easing = FastOutSlowInEasing),
        label = "usageBarFraction"
    )
    val displayFraction = if (animationsEnabled) animatedFraction else safeFraction

    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val targetFill = tone.content()
    val animatedFill by animateColorAsState(
        targetValue = targetFill,
        animationSpec = tween(durationMillis = MaxDuration.quick),
        label = "usageBarFill"
    )
    val fill = if (animationsEnabled) animatedFill else targetFill
    val markerColor = MaterialTheme.colorScheme.onSurfaceVariant

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(MaxRadius.pill))
            .background(track)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(safeFraction, 0f..1f) }
    ) {
        val available = maxWidth
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(available * displayFraction)
                .background(fill)
        )
        marker?.takeIf { it.isFinite() }?.let {
            val position = (available * it.coerceIn(0f, 1f)) - (MaxSize.activeRing / 2)
            Box(
                modifier = Modifier
                    .offset(x = position)
                    .fillMaxHeight()
                    .width(MaxSize.activeRing)
                    .background(markerColor)
            )
        }
    }
}
