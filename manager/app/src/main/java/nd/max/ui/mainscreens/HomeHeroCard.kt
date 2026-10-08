/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * بطاقة الهوية في الرئيسية — **ما هذا الجهاز، وبأي صلاحية أعمل عليه، ومن يقرّر فيه.**
 *
 * ثلاثة أسطر بترتيب القراءة (`MAX-MANAGER-LEVEL-UP.md` §5.2):
 *  1. الجهاز: اسمه بعنوان الصفحة (`headlineSmall`، سطران كحدّ البطاقة)، ثم الشريحة، ثم مدّة التشغيل
 *     تسمية صغيرة بحروف كبيرة.
 *  2. وضع الوصول حالةً (Root / Shizuku / Basic) وجملة صدق واحدة تقول ما يُفتح وما يبقى مقفلاً.
 *  3. الباب إلى Max AI (حالته تحملها ألوانه) والباب إلى معلومات الجهاز.
 *
 * **والأيقونة بحاوية `MaxSize.iconContainer`** كما كل بطاقة في التطبيق، لا بمقاس مكتوب بيد.
 * **والتوهّج** بلون الوصول من `NeuralPanel` نفسها: اللون يقول الحالة، لا زخرفة.
 */
package nd.max.ui.mainscreens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import nd.max.R
import nd.max.core.privilege.PrivilegeLevel
import nd.max.ui.component.MaxAiEntryButton
import nd.max.ui.component.NeuralIconChip
import nd.max.ui.component.NeuralPanel
import nd.max.ui.component.NeuralPill
import nd.max.ui.component.neuralPalette
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace
import nd.max.ui.navigation.MaxDestination

@Composable
internal fun HomeHeroCard(
    deviceName: String,
    chipsetName: String,
    uptimeMinutes: Long,
    /** طبقة الامتياز المكتشفة — من `PrivilegeManager` لا من قراءة ثانية. */
    accessLevel: PrivilegeLevel,
    /** حالة Max AI الحقيقية (`MaxAiState.aiEnabled`) — تُعلّم الزرّ بلونه بلا سطر يشرح. */
    aiEnabled: Boolean,
    /** الباب إلى `Device Info`: المالك الوحيد لفكرة «نظرة على الجهاز» بأقسامها. */
    onOverview: () -> Unit,
    onMaxAi: () -> Unit,
) {
    val p = neuralPalette()
    val access = accessAccent(accessLevel, p)
    NeuralPanel(accent = access, verticalSpacing = MaxSpace.md) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NeuralIconChip(Icons.Rounded.PhoneAndroid, p.accent, size = MaxSize.iconContainer)
            Spacer(Modifier.width(MaxSpace.md))
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaxSpace.xs),
            ) {
                // الاسم بعنوان الصفحة، وسطران هما حدّ عنوان البطاقة (`MaxCardSpec.titleLines`)، فلا
                // يُقطع اسم طويل بثلاث نقاط عند أوّل قراءة.
                Text(
                    deviceName,
                    color = p.text,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    chipsetName,
                    color = p.muted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // مدّة التشغيل تسمية لا قيمة: حروف كبيرة بخط الأسماء الصغيرة، وتسقط حين لا تُقرأ.
                if (uptimeMinutes > 0) {
                    Text(
                        stringResource(R.string.home_hero_uptime, compactUptime(uptimeMinutes)).uppercase(),
                        color = p.muted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NeuralPill(
                text = stringResource(accessLabelRes(accessLevel)),
                accent = access,
                filled = true,
                dot = true,
            )
            Spacer(Modifier.width(MaxSpace.sm))
            Text(
                stringResource(accessNoteRes(accessLevel)),
                color = p.muted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MaxAiEntryButton(
                text = stringResource(R.string.max_nav_max_ai),
                active = aiEnabled,
                onClick = onMaxAi,
            )
            Spacer(Modifier.weight(1f))
            // الاسم من سجلّ الوجهات (`DeviceInfo.titleRes`) لا نصًّا مكتوبًا بيد (ADR-02).
            NeuralPill(
                text = stringResource(MaxDestination.DeviceInfo.titleRes),
                accent = p.muted,
                navigates = true,
                compact = true,
                onClick = onOverview,
            )
        }
    }
}
