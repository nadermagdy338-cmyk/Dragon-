/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.component

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import nd.max.R
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxNavigationRow
import nd.max.ui.design.MaxTone
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.navigation.navigateTypedTo
import nd.max.ui.viewmodel.MaxAiViewModel

/**
 * اختصار Max AI الموحّد داخل الشاشات — **صفٌّ في المجموعة، لا لافتة فوقها.**
 *
 * كان في الشاشات لافتة (`MaxAiActiveBanner`) تُعرض في شاشة واحدة وتختفي في غيرها، وتظهر
 * **فقط** حين يكون المحرّك مُشغَّلًا. وهذا كان يُنتج ثلاثة عيوب مقيسة:
 *
 * 1. **مدخل يظهر ويختفي:** المستخدم الذي يريد إيقاف المحرّك من شاشة الحاكم لا يجده إن كان
 *    متوقّفًا — أي أن المدخل يغيب في الحالة التي يُطلب فيها وجوده.
 * 2. **شكل ثانٍ للفكرة نفسها:** الرئيسية فيها زرّ Max AI (`MaxAiEntryButton`)، والشاشات
 *    فيها لافتة بـ`Surface` وحشوٍ ولونٍ خاصّين — فالاسم نفسه بلونين وشكلين وموضعين.
 * 3. **حالة يقرؤها اللون وحده:** نشِط/متوقّف كانا في لون الـ`Surface`، فلا يقرؤهما قارئ شاشة
 *    ولا من لا يميّز اللون.
 *
 * فصار الاختصار **صفًّا من عقد التصميم نفسه** (`MaxNavigationRow` في `MaxGroup`) — كأي مدخل
 * آخر في التطبيق: أيقونة واحدة (`AutoAwesome`، هي أيقونة Max AI في الرئيسية والسجلّ)، واسم
 * واحد (`max_nav_max_ai`)، و**الحالة كلمةً مكتوبة** لا وحدها لونًا، وسطر ثانٍ يقول ما يفعله
 * المحرّك. ويُعرض **دائمًا** في الشاشات التي يمسّها، فيبقى المدخل في مكانه قبل الحاجة إليه.
 *
 * **وحدّه المعلن:** الذي يظهر فيه ليست «كل شاشة»: نطاقُ المحرّك هو مجموعته المسموح بها
 * (`ControlRegistry`: سقوف تردّد المعالج، وسقف الرسوم، والـboost) **وما يتقاطع معه**
 * (الحاكم الذي يقرّر كيف يُستعمل السقف، والحرارة التي يقيّدها). فلا يُوضع في شاشة لا يمسّها
 * المحرّك: اختصارٌ هناك ضجيجٌ لا اختصار.
 *
 * @param manual `true` **فقط** في الشاشات التي تكتب مقبضًا يكتبه المحرّك نفسه (سقف تردّد
 *        المعالج، سقف الرسوم — `ControlRegistry`)؛ فيقول السطر الثاني **سببًا** لا وصفًا:
 *        «قد يُتجاوز تغييرك اليدوي» — وهي العبارة التي وُجدت اللافتة القديمة لأجلها، فلم
 *        تُسقَط بل صارت عند الحالة التي تعنيها. والافتراض `false` لأن **وصف تجاوزٍ لم يقع
 *        ادّعاءٌ بلا دليل** (ADR-07): اللافتة القديمة كانت تقوله في شاشة الحاكم، والمقيس أن
 *        المحرّك لا يملك مقبض حاكم أصلًا.
 *
 * والنوع [NavController] لا `NavHostController`: شاشة تعرض الاختصار لا تُنشئ رسمًا ملاحيًّا،
 * وبعضها (`GpuStudioScreen`) يستلم النوع الأعمّ فعلًا.
 */
@Composable
fun MaxAiShortcut(
    navController: NavController,
    modifier: Modifier = Modifier,
    manual: Boolean = false,
    viewModel: MaxAiViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MaxAiShortcutRow(
        active = state.aiEnabled,
        manual = manual,
        onClick = { navController.navigateTypedTo(MaxDestination.MaxAi) },
        modifier = modifier,
    )
}

/**
 * الاختصار بحالته كمعامل — فيُعرض بلا `ViewModel` (اختبار، معاينة، أو شاشة تعرف الحالة أصلًا).
 */
@Composable
fun MaxAiShortcutRow(
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    manual: Boolean = false,
) {
    MaxGroup(modifier = modifier) {
        MaxNavigationRow(
            title = stringResource(R.string.max_nav_max_ai),
            subtitle = maxAiShortcutSubtitle(active = active, manual = manual),
            valueText = stringResource(maxAiShortcutStateLabel(active)),
            icon = Icons.Rounded.AutoAwesome,
            iconTone = if (active) MaxTone.Accent else MaxTone.Neutral,
            onClick = onClick,
        )
    }
}

/**
 * كلمة الحالة — **مصدر واحد** للاختصار ولزرّ الرئيسية، فلا يقول أحدهما «مُشغَّل» والآخر
 * «نشِط» عن الحالة نفسها.
 */
@StringRes
fun maxAiShortcutStateLabel(active: Boolean): Int =
    if (active) R.string.maxai_shortcut_value_on else R.string.maxai_shortcut_value_off

/** السطر الثاني: السبب حين يوجد، وإلا التعريف. */
@Composable
fun maxAiShortcutSubtitle(active: Boolean, manual: Boolean): String = stringResource(
    when {
        active && manual -> R.string.maxai_banner_active_desc
        active -> R.string.maxai_banner_active_title
        else -> R.string.maxai_shortcut_subtitle
    }
)
