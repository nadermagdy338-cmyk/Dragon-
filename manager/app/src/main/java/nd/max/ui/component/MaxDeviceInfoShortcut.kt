/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.navigation.NavController
import nd.max.R
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.navigation.deviceInfoRouteOf
import nd.max.ui.subscreens.deviceInfoShortcutSection

/**
 * الباب من الشاشة التي **تعمل** على الموضوع إلى القسم الذي **يشرحه** في «معلومات الجهاز»:
 * الحرارة ← قسم الحرارة، والتشخيص ← قسم المستشعرات، والذاكرة ← قسم الذاكرة.
 *
 * ==ما تغيّر، ولماذا (نقد المالك، الجولة ٢٠١)==
 *
 * كان سابقًا **كبسولة**: شارة دائرية ٢٨dp + إطار + خلفية بلون التطبيق + سهم، بارتفاع مرئيّ
 * ٤٠dp — أي **نسخة حرفية** من كبسولة `Max AI` في الرئيسية. وثلاثة أشياء أساءت:
 *
 * 1. **أكلت الصفّ الذي دخلت فيه.** كانت تُرسم `Row(fillMaxWidth(), Arrangement.End)`،
 *    وتُمرَّر في خانة `trailing` لـ[nd.max.ui.design.MaxRow] وعنوانها `weight(1f)`. فطفلٌ
 *    يطلب العرض كاملًا في خانة جانبية يلتهم حقّ الوزن، فيصير عرض العنوان **صفرًا**: قاست لقطة
 *    المالك أن صفّ هويّة بطاقة الرسوم لم يرسم إلا الكبسولة — بلا اسم المعالج ولا سطره.
 * 2. **بدت من خارج البطاقة** لا من داخلها: شريطٌ بعرض الصفحة (‏`fillMaxWidth`)، و
 *    `Arrangement.End` تعني في العربية **يسار** الشاشة (الاتجاه يُقلب مع اللغة) — أي الطرف
 *    المقابل لبداية القراءة.
 * 3. **قرأها العين زرًّا ثانيًا** بنفس حجم وشكل زرّ آخر لوظيفة مختلفة (نسخة `Max AI`).
 *
 * ==العقد الجديد: سطرُ بابٍ، لا زرّ==
 *
 * - **الشكل:** سطرٌ واحد بلا خلفية ولا إطار ولا شارة: علامة **الوجهة المقصودة** ١٦dp، ثم الكلمة
 *   «معلومات أكثر» (أمر المالك: مكتوبة لا أيقونة)، ثم **اسم القسم** من الخريطة نفسها، ثم سهم
 *   يتّجه مع اتجاه اللغة. الحبر المرئيّ ~٢٤dp، ومنطقة اللمس تبقى ٤٨dp من المنصّة
 *   (`minimumInteractiveComponentSize`) كما في كل عنصر قابل للضغط هنا.
 * - **لا `fillMaxWidth` هنا ولا في أي خانة جانبية:** السطر يأخذ عرض محتواه، فيُحاذى على
 *   **بداية القراءة** (يمينًا في العربية) ولا يزاحم شيئًا ولا يُفرغ صفًّا.
 * - **وموضعه آخر البطاقة التي تشرح موضوع الشاشة**، لا في صفّ عنوانها: هو إجراء عليها بعد أن
 *   تُقرأ بياناتها. و[inset] يحمل حاشية الحاوية: المجموعة المكوّنة من صفوف
 *   (`MaxGroup`) تُباعد صفوفها [MaxSpace.rowPaddingHorizontal] فهو الافتراضيّ، فيسقط أيقونة
 *   الباب **على محاذاة نصّ الصفوف وخطّها الفاصل بالضبط**؛ وبطاقة تسبق أن تحشو نفسها (بطاقة
 *   العرض، وبطاقة مصفوفة القدرات) تُمرَّر لها `0.dp` فيسقط على محاذاة عنوانها.
 * - **ولا يُرسم في صفحات المحور التسع** (بأمر المالك): الصفحة نفسها فهرس أبواب، وإضافة بابٍ
 *   لمعلومات الجهاز فيها زيادة لا خبر.
 *
 * ==وتعديل الجولة ٢٠٤: العلامة صارت **علامة الوجهة** لا علامة الشاشة (مُعلَن، لا مسكوت عنه)==
 *
 * قرار الجولة ٢٠١ كان: «أيقونة الشاشة نفسها، لأن الباب جزء من الشاشة التي يقف فيها». وهذا
 * **يُنقض هنا بطلب المالك وبسبب مقيس** — نصّه: «اريد فقط زر يوصل الي قسم المناسب device info
 * داخل الشاشات ٩ بطريقة احترافيه وجميله ومتناسقه ولا تكون واضحه اوي او مجهوله اوي»، وترك لي
 * الاختيار («اختر الافضل والاكثر احترافيه»). والسبب:
 *
 * 1. **التناسق ليس في الأيقونة الشاشة بل في المعلومة الثابتة:** الباب في التسع يقول الكلمة نفسه
 *    (`more_info`) ثم يتغيّر اسم القسم وحده. فأيّقونة الشاشة تجعل **العلامة الأولى** مختلفة في
 *    كلٍّ منها (وهي مرسومة أصلًا في رأس الشاشة أو بطلها)، فلا يتعلّم المستخدم شكلًا واحدًا بل
 *    تسعة. وعلامة الوجهة (`MaxDestination.DeviceInfo.icon` من السجلّ، ADR-02) تُعطي التسعة
 *    **علامةً واحدة وقسمًا متغيّرًا** — أي نمط واحد يُتعلَّم مرّة واحدة.
 * 2. **و«لا مجهولة ولا واضحة أوي» هو ضبط اللمسة لا حجم الزرّ:** الحبر المرئيّ يبقى سطرًا واحدًا
 *    و٤٨dp لمسًا، ويُدار **بوزنٍ واحد ولونين**: الكلمة (فعل) والسهم (اتجاه) بلون التمييز، وما
 *    عدا ذلك (`onSurfaceVariant`) بيانات وصفية. فذهب `SemiBold` — كان يجعل الباب أبرز من
 *    الأرقام التي يشرحها، وهو معنى «واضح أوي»، وبقيت الكلمة ملؤنة فأقلّها ينزل عن حدّ
 *    «مجهول». **والسهم لا يختفي أبدًا:** اسم قسمٍ طويل يُقصّ بـ`Ellipsis` بـ`weight(1f,
 *    fill = false)` بدل أن يدفع السهم خارج السطر (بابٌ بلا سهم بابٌ لا يُقرأ).
 * 3. **والموضع لا يتغيّر:** آخر البطاقة التي تشرح الموضوع، و[inset] كما كانت، وكلمة «معلومات
 *    أكثر» كما هي (أمر المالك في الجولة ٢٠٠: «زرّ **وليس أيقونة** باسم more info»)، وما يقوله
 *    لقارئة الشاشة يسمّي القسم صراحةً (`devinfo_shortcut_cd`).
 *
 * ==ولماذا الشاشة لا تُسمّي القسم بيد==
 *
 * [deviceInfoShortcutSection] هي الخريطة الواحدة (وجهة ← قسم)، وهي صافية ومقيسة على JVM —
 * فلا يمكن أن يقول بابٌ في شاشة إنه يفتح قسمًا ويفتح غيره، ولا أن يُرسم بابٌ في شاشة لا قسم
 * لها (‏`ResponsivenessHub`) فيوعد المستخدم بضغطة تفتح نظرة عامة عامّة — وهو الوعد الكاذب نفسه
 * الذي يمنعه ADR-07 في الأرقام.
 *
 * @param from الشاشة الحالية نفسها (لا الوجهة المقصودة): الوجهة تُترجم إلى قسمها في الخريطة.
 * @param inset حاشية الحاوية الداخلية؛ الافتراضيّ يطابق حاشية الصفوف في [nd.max.ui.design.MaxGroup].
 */
@Composable
fun MaxDeviceInfoShortcut(
    navController: NavController,
    from: MaxDestination,
    modifier: Modifier = Modifier,
    inset: Dp = MaxSpace.rowPaddingHorizontal,
) {
    // ولا قسم ⇒ لا باب: شاشة لا موضوع لها في «معلومات الجهاز» لا يُوعد المستخدم بشيء.
    val section = deviceInfoShortcutSection(from) ?: return
    val sectionTitle = stringResource(section.titleRes)
    // والوصف يُقرأ **قبل** كتلة الدلالات: تلك ليست `@Composable`، فنداء `stringResource`
    // داخلها لا يترجم (أمسكه `compileReleaseKotlin` في تشغيل سابق).
    val shortcutLabel = stringResource(R.string.devinfo_shortcut_cd, sectionTitle)

    Row(
        modifier = modifier
            .padding(horizontal = inset)
            // منطقة اللمس ٤٨dp من المنصّة، والحبر المرئيّ سطرٌ واحد — لا يكبر الشكل ولا تصغر
            // منطقة الضغط (نفس عقد الصفوف في [nd.max.ui.design.MaxRow]).
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(MaxRadius.control))
            .semantics(mergeDescendants = true) {
                // وما يفعله الباب: النصّ المرئيّ «معلومات أكثر» يُدمج في هذه القيمة، فتسمع
                // قارئة الشاشة **إلى أين** ينقل لا كلمتين مبهمتين.
                contentDescription = shortcutLabel
            }
            .neuralClickable(
                onClick = { navController.navigate(deviceInfoRouteOf(section.wireKey)) },
                role = Role.Button,
            )
            // وحشوٌ **رأسي فقط**: أيّ حشو أفقيّ هنا يُضاف إلى [inset] فينزلق الحبر عن محاذاة
            // نصّ الصفوف بضعة dp — وهي بالضبط ما يُقرأ «مُلصقًا من الخارج» لا جزءًا من السطر.
            .padding(vertical = MaxSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm),
    ) {
        // **علامة الوجهة لا علامة الشاشة** (تعديل الجولة ٢٠٤، أعلاه): من السجلّ لا من استيراد
        // أيقونة بيد — ومنها يتعلّم المستخدم الشكل الواحد في التسعة كلّها.
        Icon(
            imageVector = MaxDestination.DeviceInfo.icon,
            contentDescription = null,
            modifier = Modifier.size(MaxSize.iconGlyphSmall),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.more_info),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            // **وفعلٌ لا عنوان:** الوزن الوسط يقرؤه فعلًا بلا أن يزاحم الأرقام (ذهب SemiBold).
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
        // واسم القسم بلون هادئ: الباب يقول **عن ماذا** لا «معلومات أكثر» فقط.
        Text(
            text = stringResource(R.string.devinfo_shortcut_role, sectionTitle),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            // ولا يدفع السهم خارج السطر: التقصير هنا، والسهم باقٍ في كل الأحوال. و`fill = false`
            // فلا يتوسّع اسمٌ قصير ويترك وحده السهم في الطرف البعيد.
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(MaxSize.iconGlyphSmall),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
