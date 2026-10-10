/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * عداد رقمي متدحرج حي (Rolling Ticker Counter) لأرقام الأداء الحية (تردد المعالج، الرام، الحرارة، الإطارات).
 *
 * بدلاً من القفز المفاجئ للقيم، تتدحرج الخانات المتغيرة فقط رأسياً بانسيابية تشبه عدادات أجهزة القياس
 * الدقيقة (Precision Instrumentation Odometer)، مع احترام تفضيل [rememberAnimationsEnabled].
 */
@Composable
fun MaxRollingTicker(
    value: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null
) {
    if (!rememberAnimationsEnabled()) {
        Text(
            text = value,
            modifier = modifier,
            style = style,
            color = color,
            fontWeight = fontWeight
        )
        return
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterHorizontally
    ) {
        value.forEachIndexed { index, char ->
            if (char.isDigit()) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        val duration = MaxMotion.fast
                        if (targetState > initialState) {
                            (slideInVertically(tween(duration, easing = FastOutSlowInEasing)) { it / 2 } + fadeIn(tween(duration)))
                                .togetherWith(slideOutVertically(tween(duration, easing = FastOutSlowInEasing)) { -it / 2 } + fadeOut(tween(duration)))
                        } else {
                            (slideInVertically(tween(duration, easing = FastOutSlowInEasing)) { -it / 2 } + fadeIn(tween(duration)))
                                .togetherWith(slideOutVertically(tween(duration, easing = FastOutSlowInEasing)) { it / 2 } + fadeOut(tween(duration)))
                        }
                    },
                    label = "tickerChar_$index"
                ) { targetChar ->
                    Text(
                        text = targetChar.toString(),
                        style = style,
                        color = color,
                        fontWeight = fontWeight
                    )
                }
            } else {
                Text(
                    text = char.toString(),
                    style = style,
                    color = color,
                    fontWeight = fontWeight
                )
            }
        }
    }
}
