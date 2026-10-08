/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 *
 * MaxManager proprietary source. See LICENSE at the repository root: this file is
 * MaxManager-owned and carries no third-party licence obligations.
 */

package nd.max.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace

/**
 * بطاقة عنصر على رفّ المحاكيات — بلغة اللوبي البصرية نفسها، **وبلا أيقونة تطبيق**.
 *
 * **لماذا مكوّن جديد ولا يُعاد استخدام `LobbyGameCard`:** تلك البطاقة مفتاحها اسم حزمة، تقرأ
 * أيقونة التطبيق من `AppIconCache` وتستخرج لونها المميّز منها (`LobbyCarousel.kt`). ملفّ ROM ليس
 * تطبيقًا ولا أيقونة له، فتمرير حزمة وهميّة كان سيعطي بطاقة بلا صورة وبلا لون — أي سطحًا يشبه
 * اللوبي ولا يعمل. فاللغة تُشارَك (`LobbyPalette` · `lobbyPress` · الأشكال)، والبطاقة تُكتب.
 *
 * **الحالات الأربع:** الاسم · سطر البيانات (الحجم أو «—») · شارة النظام · زرّ تشغيل واحد. واللون
 * المميّز هنا **سيان** لا أحمر: الأحمر في اللوبي هو العلامة، والزرّ هو الفعل.
 */
@Composable
fun ShelfRomCard(
    title: String,
    meta: String,
    systemLabel: String,
    playDescription: String,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = remember { RoundedCornerShape(MaxRadius.group) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MaxSize.iconContainer + MaxSpace.xxl)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(LobbyPalette.Panel, LobbyPalette.Surface),
                )
            )
            .lobbyPress(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onPlay,
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MaxSpace.md),
            verticalArrangement = Arrangement.spacedBy(MaxSpace.xs),
        ) {
            Text(
                text = title,
                color = LobbyPalette.Ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm),
            ) {
                Text(
                    text = systemLabel,
                    color = LobbyPalette.Cyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(text = meta, color = LobbyPalette.Muted, fontSize = 12.sp)
            }
        }
        Box(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = MaxSpace.sm).size(MaxSize.minTouchTarget),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = playDescription,
                tint = LobbyPalette.Ink,
            )
        }
    }
}
