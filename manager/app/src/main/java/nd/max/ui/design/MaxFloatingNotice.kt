/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * MaxManager Design Language — النافذة العائمة لنتيجة الإجراء.
 *
 * **لماذا وُجدت (والعطب مقيس لا موصوف):** نتيجة الإجراء في شاشة التحكّم بالنواة
 * (`CpuCoreControlScreen`) كانت تُعرض في **أوّل الشاشة** — `MaxListScreen` تُركّب الإشعار في
 * `item(key = "max_banner")` قبل الرأس وقبل أوّل قسم. والزرّ الذي يُنشئ النتيجة («تطبيق» ·
 * «استعادة») في **أسفل** الصفحة داخل بطاقة العنقود. فالنتيجة تظهر على بُعد شاشة أو أكثر من
 * العين التي طلبتها، ومن ينزل ليتحقّق يفقد موضعه. وهو نصّ الطلب حرفيًّا: «بدل أن يظهر في
 * بداية الشاشة … اجعلها نافذة عائمة … وتأكّد ألّا تُغطّي شيئًا».
 *
 * **والنمط المعماري: عائمتان لا واحدة — والتشخيص هو ما يفرّق بينهما.**
 *
 *   ① `androidx.compose.ui.window.Popup` (كما في `MaxContextMenu`): نافذة أخرى فوق الشجرة
 *      كلّها. ميزتها أنها **لا تُزاح** ولا تتأثّر بتدفّق الصفحة… وهي نفسها عطبها هنا: لأنها
 *      لا تعلم شيئًا عن الصفحة، **لا تستطيع أن تُبلّغها بارتفاعها** فتحجز لها الصفحة فراغًا
 *      في أسفلها. أي أنها تضمن «لا يُزاح شيء» و**لا تضمن «لا يُغطّى شيء»** — وهو الشرط الذي
 *      شدّد عليه المالك.
 *   ② `Surface` داخل `Box` مع `Modifier.align(BottomCenter)` — وهي المستعملة هنا: النافذة
 *      تُقاس (`onSizeChanged`) ويُبلَّغ ارتفاعها إلى الهيكل، فيُضاف إلى
 *      `contentPadding.bottom` للقائمة. فالنتيجة: النافذة تطفو فوق **فراغ محجوز لها**، وآخر
 *      صفّ في الصفحة يظلّ قابلًا للتمرير إلى ما فوقها — صفر صفّ مغطّى.
 *
 * **وموضع التركيب يبقى ثابتًا أثناء التمرير** لأنها خارج `LazyColumn` لا داخلها: إشعار
 * يُدفن في وسط القائمة يخرج من الشاشة مع أوّل تمرير — وهو أسوأ من إشعار في أوّلها.
 *
 * **والشكل من مفردات الطبقة لا من رقم مخترع:** خلفية **معتمة** (`surfaceContainerHigh`) لا
 * غسل نبرة شفّاف — وهذا فرق جوهريّ عن `MaxConditionNotice` المضمَّنة: تلك تسكن فراغها في
 * الصفحة فيكفيها غسل خفيف، وهذه تطفو **فوق نصّ متحرّك** فلا بدّ أن تُقرأ فوقه. ومعها حدّ
 * شعريّ بنبرة الحالة (`tone.border(strong = true)`) وظلّ `MaxSpace.sm` يفصلها عن المحتوى،
 * وشكل `MaxRadius.group` (أكبر من `MaxRadius.row` المضمَّن) لأنها **بطاقة طافية** لا صفًّا
 * داخل قسم. والحالة تُقرأ بالرمز والعنوان والنبرة معًا لا باللون وحده.
 *
 * **وحدّها المُعلن:** الحجب والموضع على الجهاز (RTL، شريط التنقّل، حركة الخروج) **لم تُقَس في
 * هذه البيئة** — الترجمة نفسها غير مُتحقّقة هنا. وما قيس هو البوابات البنيوية وحدها.
 */
package nd.max.ui.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import nd.max.ui.theme.MonoValueStyleSmall

/**
 * مهلة بقاء **تأكيد النجاح** قبل أن يزول من نفسه (ملّي ثانية).
 *
 * وهي هنا لا في الشاشة لأنها صفة العرض العائم نفسه: إشعار يطفو فوق نصّ ولا يحجب شيئًا، لكنه
 * يحجز فراغًا في أسفل الصفحة — فبقاؤه الأبديّ انتظارٌ بلا سبب. وستّ ثوانٍ تكفي لقراءة سطرين
 * (العنوان + «القيم المقروءة الآن تطابق ما طُلب») بلا استعجال، ومن فاتهما يجد الحقيقة كاملة
 * في صفوف العنقود فوقه (الحدود الحيّة وسطور التحقّق) — **فلا معلومة تُفقد بزواله**.
 *
 * و**لا يُطبَّق على ما يحتاج إقرارًا** (فشل · انتظار · غير مدعوم): تلك لا تزول إلا بيد القارئ،
 * فلا يمرّ عطب بصريًّا في ومضة. والقرار في الشاشة المستدعية لا في هذا الملفّ، لأنها وحدها
 * تعرف معنى الإشعار الذي تمرّره.
 */
internal const val FloatingNoticeDwellMillis: Long = 6_000L

/**
 * بطاقة نتيجة الإجراء الطافية: أيقونة النبرة + العنوان + التفصيل، وزرّ إغلاق في النهاية.
 *
 * @param condition الحالة المعروضة؛ نبرتها وأيقونتها ونصّها هي المصدر — لا نصّ جديد هنا.
 * @param modifier يُمرّر من الحاوي ([MaxFloatingNoticeHost]) الذي يملك العرض والموضع.
 */
@Composable
internal fun MaxFloatingNotice(
    condition: MaxCondition,
    modifier: Modifier = Modifier
) {
    val tone = condition.kind.tone
    val toneContent = tone.content()
    val scheme = MaterialTheme.colorScheme
    // الإغلاق هو **إجراء الإشعار نفسه** لا زرًّا موازيًا له: الشاشة تمرّر «تجاهل» مع
    // مُناديها، فالإشعار لا يخترع سلوكًا ثانيًا ولا يبني دالّة إضافية تُنسى لاحقًا. وإن لم
    // يمرّر المستدعي إجراءً يبقى الإشعار بلا زرّ إغلاق بدل أن يُوهم بزرّ لا يفعل شيئًا.
    val dismissLabel = condition.primaryActionLabel
    val onDismiss = condition.onPrimaryAction

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(MaxRadius.group),
        color = scheme.surfaceContainerHigh,
        contentColor = scheme.onSurface,
        border = BorderStroke(MaxSize.hairlineBorder, tone.border(strong = true)),
        shadowElevation = MaxSpace.sm
    ) {
        Row(
            modifier = Modifier.padding(
                start = MaxSpace.md,
                end = MaxSpace.xs,
                top = MaxSpace.md,
                bottom = MaxSpace.md
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaxSpace.md)
        ) {
            Surface(
                modifier = Modifier.size(MaxSize.iconContainer),
                shape = RoundedCornerShape(MaxRadius.control),
                color = tone.container(strong = true)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // الكتابة في الطريق تُعرض بحلقة تقدّم لا بأيقونة «ساعة»: `isBusy` هي التي
                    // تفرّق بين «يكتب الآن» و«انتهى» — وهو تمييز ADR-07 نفسه (لا يُدَّعى
                    // نتيجة لم تُقرأ).
                    if (condition.kind.isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(MaxSize.iconGlyph),
                            strokeWidth = MaxSize.activeRing,
                            color = toneContent
                        )
                    } else {
                        Icon(
                            imageVector = condition.kind.icon,
                            contentDescription = null,
                            tint = toneContent,
                            modifier = Modifier.size(MaxSize.iconGlyph)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaxSpace.xs)
            ) {
                Text(
                    text = condition.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = toneContent
                )
                if (condition.detail.isNotBlank()) {
                    Text(
                        text = condition.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
                condition.technicalDetail?.takeIf { it.isNotBlank() }?.let { technical ->
                    Text(
                        text = technical,
                        style = MonoValueStyleSmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            if (dismissLabel != null && onDismiss != null) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(MaxSize.minTouchTarget)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = dismissLabel,
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(MaxSize.iconGlyph)
                    )
                }
            }
        }
    }
}

/**
 * حاوي النافذة العائمة: يضعها في أسفل المنطقة المرئية ويُبلّغ عن ارتفاعها لتحجزه الصفحة.
 *
 * **وهو `BoxScope` عن قصد:** موضع «أسفل الحاوي» لا يُعرف من داخل الإشعار، بل من الشجرة التي
 * تركب فيها — فالحاوي يأخذ الموضع من سياقه ولا يفترضه.
 *
 * **وحركتان لا واحدة:** الدخول والخروج بتلاشٍ وانزلاق ([MaxDuration.standard] للدخول و
 * [MaxDuration.quick] للخروج، من ميزانية الحركة في `MaxDuration` لا أرقامًا مخترعة)، ويبقى
 * آخر إشعار مرسومًا حتى تكتمل حركة الخروج — فلا يختفي الإشعار في منتصفها فتبدو الحركة بلا
 * معنى. والمساحة المحجوزة تُحرَّر بعد اكتمال الخروج، فلا يقفز المحتوى بينما النافذة تُطوى.
 *
 * @param condition الإشعار الحالي، أو `null` حين لا إشعار.
 * @param bottomGap الفراغ بين أسفل النافذة وأسفل منطقة المحتوى — يُمرَّر من الشاشة لأنها
 *        وحدها تعرف حشوة الشاشة وشريط التنقّل العائم.
 * @param onReservedHeightChange الارتفاع المقيس لتحجزه الصفحة في أسفل قائمتها، و`0.dp` عند
 *        تحريره (بعد الخروج أو قبل أوّل قياس).
 */
@Composable
internal fun BoxScope.MaxFloatingNoticeHost(
    condition: MaxCondition?,
    bottomGap: Dp,
    onReservedHeightChange: (Dp) -> Unit
) {
    // آخر إشعار غير فارغ: يبقى مرسومًا حتى تكتمل حركة الخروج (انظر أعلاه).
    var lastNotice by remember { mutableStateOf<MaxCondition?>(null) }
    // الكتابة أثناء التركيب لا في `LaunchedEffect`: كتابة الـstate هنا **متقاربة** (تُكتب مرّة
    // ثم تُعاد بنفس القيمة)، فلا حلقة إعادة تركيب — وأثرها أن الإشعار يُرسم في **نفس** الإطار
    // الذي ظهر فيه، بلا إطار فارغ في أوّل الحركة.
    if (condition != null) lastNotice = condition
    val density = LocalDensity.current

    LaunchedEffect(condition) {
        if (condition == null) {
            // مهلة الخروج قبل تحرير المساحة ونسيان الإشعار — الرقمان مرتبطان: تحرير المساحة
            // في منتصف الحركة يزيح آخر صفّ والنافذة لم تُطوَ بعد.
            delay(MaxDuration.quick.toLong())
            lastNotice = null
            onReservedHeightChange(0.dp)
        }
    }

    AnimatedVisibility(
        visible = condition != null,
        enter = fadeIn(tween(MaxDuration.standard)) +
            slideInVertically(tween(MaxDuration.standard)) { height -> height / 4 },
        exit = fadeOut(tween(MaxDuration.quick)) +
            slideOutVertically(tween(MaxDuration.quick)) { height -> height / 4 },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .widthIn(max = MaxSize.readingMaxWidth)
            .fillMaxWidth()
            .padding(horizontal = MaxSpace.gutter)
            .padding(bottom = bottomGap),
        label = "max_floating_notice"
    ) {
        lastNotice?.let { notice ->
            MaxFloatingNotice(
                condition = notice,
                // القياس هو ما يجعل «لا يُغطّى شيء» وعدًا لا نيّة: الرقم المبلَّغ عنه هنا هو
                // نفسه الذي يوسّع به الهيكل أسفل قائمته.
                modifier = Modifier.onSizeChanged { measured ->
                    onReservedHeightChange(with(density) { measured.height.toDp() })
                }
            )
        }
    }
}
