/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * بطاقة الهوية في الرئيسية — **ما هذا الجهاز، وبأي صلاحية أعمل عليه، ومن يقرّر فيه.**
 *
 * كانت `PulsePanel` أضخم كتلة في الشاشة: حرارة بخطّ ٤٤sp وثلاث بلاطات حقائق وزرّان، وفي لقطة
 * المالك تحمل رقمًا واحدًا هو `—` بين فراغين. فصارت الهوية بطاقةً **مضغوطة** تجيب سؤالًا واحدًا
 * لكل سطر، والأرقام الحيّة (تردد · ذاكرة · حرارة · بطارية) انتقلت إلى `HomeVitalsGrid` حيث
 * يجاور بعضها بعضًا فتُقارَن بنظرة، بدل أن يُرسم كلٌّ منها وحده في بطاقة.
 *
 * ثلاثة أسطر بترتيب القراءة (`MAX-MANAGER-LEVEL-UP.md` §5.2):
 *  1. اسم الجهاز الحقيقي + الشريحة (+ مدّة التشغيل إن قُرئت).
 *  2. **وضع الوصول حالةً** (Root / Shizuku / Basic) وجملة صدق واحدة تقول ما يُفتح وما يبقى
 *     مقفلاً. والوسم لا يُضغط عن قصد: يقرأ حالة، والفعل مكانه الإعدادات.
 *  3. الباب إلى Max AI (حالته تحملها ألوانه) والباب إلى معلومات الجهاز.
 *
 * **ولا هوية مستعارة:** الأيقونة والألوان والنصوص من لغة Max (`NeuralPanel` · الأكسنت من ثيم
 * الجهاز)، والأخضر الثابت يبقى للجذر وحده كحالة إيجابية.
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nd.max.R
import nd.max.core.privilege.PrivilegeLevel
import nd.max.ui.component.MaxAiEntryButton
import nd.max.ui.component.NeuralIconChip
import nd.max.ui.component.NeuralPanel
import nd.max.ui.component.NeuralPill
import nd.max.ui.component.neuralPalette
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
            NeuralIconChip(Icons.Rounded.PhoneAndroid, p.accent, size = 44.dp)
            Spacer(Modifier.width(MaxSpace.md))
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaxSpace.hairline),
            ) {
                Text(
                    deviceName,
                    color = p.text,
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // سطران: السطر يحمل رمز القطعة بين قوسين، وبسطر واحد كان الاقتطاع سيأكل الرمز
                // وهو ما جاء المستخدم لأجله.
                Text(
                    chipsetName,
                    color = p.muted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // مدّة التشغيل لا مالك آخر لها في التطبيق، فتبقى هنا بدل أن تضيع مع حذف
                // بلاطات الحقائق. وتسقط حين لا تُقرأ (`—` كلمة لا فائدة منها في سطر وحدها).
                if (uptimeMinutes > 0) {
                    Text(
                        stringResource(R.string.home_hero_uptime, compactUptime(uptimeMinutes)),
                        color = p.muted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
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
                fontSize = 12.sp,
                lineHeight = 16.sp,
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
