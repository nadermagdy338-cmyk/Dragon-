/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * ورقة **مُوجِّد الشاشات** — طبقة البحث التي تُفتح من شريط التحكّم.
 *
 * وثلاثة قرارات في الرسم لا في مكان آخر:
 *
 * 1. **النتيجة سطرٌ كصفوف التطبيق** (`MaxRow` بأيقونة الوجهة وسطرها الواحد) لا قائمةً بشكل
 *    ثالث: من يبحث عن شاشة يريد أن يراها كما سيراها في القائمة، فالضغطة الثانية لا تفاجئه.
 * 2. **ومكان الشاشة مكتوب في السطر** (`where` في `trailing`): كتابة «مُحرّر القيم» وهو في
 *    الإعدادات لا في التحكّم تعني أن المستخدم يعرف أين تسكن بعد الضغط لا قبله — والغاية
 *    **توفير البحث** لا توفير ضغطة واحدة.
 * 3. **والحالة الفارغة تقول عدد الشاشات** ولا تعرضها: القائمة كلها على شاشة التحكّم أصلًا،
 *    فسردُها هنا تكرار. وحالة «لا نتيجة» تقول ما الذي لم يُعثر عليه، لا «لا شيء» فقط.
 *
 * **وأربع معلومات في السطر الواحد (الجولة ٢٠٣ — أمر المالك: «اعطي لفكرتك معلومات اكثر في
 * الشاشات التي فعلتها جيد»):** ما تضبطه الشاشة (`subtitle`) · أين تسكن (`trailing`) · **وكم
 * مرة فتحتها** (يلتصق بالوصف حين يوجد عدد) · **ووسم الخطورة** إن كانت موسومة به. وكلّها مقيسة:
 * الثلاثة الأولى من السجلّ والشجرة، والرابع من وسم ADR-16 نفسه — ولا سطر يخبر عن شيء لم يُقرأ.
 *
 * **والحقل الفارغ صار يعمل لا يشرح فقط:** يُعرض أعلى أربع شاشاتٍ فتحتها فعلًا
 * ([screenFinderSuggestions])، وتحتها سطر الشرح — فالفتح نفسه أصبح جوابًا لسؤال «ماذا كنت
 * أريد؟». ومن لم يفتح شيئًا بعدُ يرى سطر الشرح وحده: لا اقتراحات من عدّاد فارغ.
 *
 * والتركيز يذهب إلى الحقل عند الفتح (فمن فتح المُوجِّد جاء ليكتب)، ويُفرَّغ البحث عند الإغلاق
 * فلا يعود على نتيجة قديمة. **والعدّاد يُقرأ عند كل فتحة** لا مرّة واحدة عند التركيب، فلا يبقى
 * الترتيب صورةً من أوّل تشغيل.
 */
package nd.max.ui.mainscreens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nd.max.R
import nd.max.ui.component.CustomBottomSheet
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSearchField
import nd.max.ui.design.MaxSpace
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.util.ScreenUsageStore

/** أقصى ارتفاع لنتائج البحث قبل أن تبدأ القائمة بالتمرير (الشاشة تبقى مرئيّة خلفها). */
private val RESULTS_MAX_HEIGHT = 420.dp

/** فاصل السطر الواحد بين الوصف وعدد الفتحات — علامة، لا نصّ يُترجَم. */
private const val FACT_SEPARATOR = " · "

@Composable
fun ScreenFinderSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onOpen: (MaxDestination) -> Unit,
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // **السجلّ الواحد** (`ScreenUsageStore`): العدّاد الذي تقرأ منه «منصة التحكم» هو نفسه هنا،
    // فلا يقول المُوجِّد «الأكثر استعمالًا» ومنصةٌ في الرئيسية تقول غيره.
    val usage = remember(context, visible) {
        if (visible) ScreenUsageStore.of(context).counts() else emptyMap()
    }
    // الفهرس يُبنى مرّة لكل فتحة: نصوصه من موارد لغة الواجهة، وتغيير اللغة يُعيد إنشاء النشاط
    // (`AppLanguage.reload`) فلا حاجة لبناء صافٍ في كل حرف يكتبه المستخدم.
    val targets = remember(context, usage) { screenFinderTargets(context::getString, usage) }
    val results = screenFinderResults(query, targets)
    val suggestions = if (query.isBlank()) screenFinderSuggestions(targets) else emptyList()

    LaunchedEffect(visible) {
        if (visible) {
            // التركيز يفتح لوحة المفاتيح من نفسه، فلا نحتاج `SoftwareKeyboardController`
            // (ولا واجهة تجريبيّة أخرى) لضغطةٍ واحدة يُوفّرها.
            focusRequester.requestFocus()
        } else {
            query = ""
        }
    }

    CustomBottomSheet(visible = visible, onDismiss = onDismiss) {
        Text(
            text = stringResource(R.string.screen_finder_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = MaxSpace.xl, vertical = MaxSpace.xs),
        )
        MaxSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.screen_finder_placeholder),
            clearContentDescription = stringResource(R.string.screen_finder_clear),
            modifier = Modifier
                .padding(horizontal = MaxSpace.gutter, vertical = MaxSpace.sm)
                .focusRequester(focusRequester),
        )

        val list = if (query.isBlank()) suggestions else results
        when {
            list.isEmpty() && query.isBlank() ->
                FinderNote(stringResource(R.string.screen_finder_hint, targets.size))

            list.isEmpty() ->
                FinderNote(stringResource(R.string.screen_finder_no_results, query))

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = RESULTS_MAX_HEIGHT)
                    .padding(horizontal = MaxSpace.gutter),
            ) {
                if (query.isBlank()) {
                    item(key = "screen_finder_recent") {
                        FinderLabel(stringResource(R.string.screen_finder_recent))
                    }
                }
                item(key = "screen_finder_rows") {
                    MaxGroup {
                        list.forEachIndexed { index, target ->
                            if (index > 0) MaxGroupDivider()
                            FinderRow(
                                target = target,
                                onOpen = {
                                    query = ""
                                    onOpen(target.destination)
                                },
                            )
                        }
                    }
                }
                if (query.isBlank()) {
                    item(key = "screen_finder_hint") {
                        FinderNote(stringResource(R.string.screen_finder_hint, targets.size))
                    }
                }
            }
        }
    }
}

/**
 * الصفّ كما يراه المستخدم — **والمعلومات الأربع في مكانها الثابت**: الاسم عنوانًا، وما تضبطه
 * وكم فتحته تحتَه، وأين تسكن ووسم الخطورة في الطرف. والوسم في الطرف لا في الوصف لأن الخطورة
 * **حالة** لا وصف، فتقرأها العين في العمود نفسه لكل الصفوف.
 */
@Composable
private fun FinderRow(
    target: ScreenFinderTarget,
    onOpen: () -> Unit,
) {
    val opens = if (target.usage > 0) {
        stringResource(R.string.screen_opens_count, target.usage)
    } else {
        null
    }
    val facts = if (opens == null) target.role else target.role + FACT_SEPARATOR + opens

    MaxRow(
        title = target.title,
        subtitle = facts,
        icon = target.destination.icon,
        onClick = onOpen,
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = target.where,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (target.riskNote != null) {
                    Text(
                        text = target.riskNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                    )
                }
            }
        },
    )
}

/** عنوان القائمة التي لم يطلبها المستخدم: يقول لماذا هذه الصفوف بعينها. */
@Composable
private fun FinderLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = MaxSpace.xs, vertical = MaxSpace.sm),
    )
}

/** سطرٌ واحد يقول ما الذي ينتظره الحقل — لا قائمة فارغة تُقرأ كعطب عرض. */
@Composable
private fun FinderNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = MaxSpace.xl, vertical = MaxSpace.md),
    )
}
