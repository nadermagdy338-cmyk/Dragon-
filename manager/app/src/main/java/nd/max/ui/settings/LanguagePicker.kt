/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * منتقي لغة التطبيق — مكوّن واحد يُنادى من كل مكان، لا واجهة مبنيّة داخل شاشة الإعدادات.
 *
 * **ما تغيّر بأمر المالك (تكملة ١٣٣)، ولماذا كل تغيير مقصود:**
 *
 * 1. **«لغات جهازك» أُزيلت، و«لغة النظام» أُعيدت.** أُزيلت الأولى بأمر المالك: «لا داعي
 *    لإجبار المستخدم على المرور بخيار لغة النظام». ثم **أمر المالك الجديد**: «أضف في نافذة
 *    اللغة لغة النظام» — فصارت صفًّا **مثبَّتًا أوّل** لا مقطعًا مرتّبًا كبيرًا: سطر واحد
 *    يُرى بلا تمرير، لا قسم كامل كان يأخذ مساحة اللغات كلها. والباقي: عنوان ← بحث ←
 *    صفّ لغة النظام ← ٨٤ لغة. (و`max_language_device_section` باقٍ في الموارد بلا استعمال:
 *    حذفه عملٌ لا يُعاد — ADR-18.)
 *
 *    والصفّ الكائت **ليس تراجعًا عن القرار السابق** بل تنفيذ للاثنين معًا: سبب إزالة
 *    المقطع كان أنه يدفع اللغات الـ٨٤ أسفل الشاشة ويقف أمام المستخدم الذي يعرف لغته،
 *    وهذا يزول بصفّ واحد أوّل. و`deviceTags()` باقية قياسًا صحيحًا لكن بلا موضع في الواجهة.
 * 2. **الاسم الإنجليزي عنوانًا والاسم الأصلي تحته** (`Arabic` / `العربية`) كما في الطلب.
 *    وكان العكس؛ والفرق ليس ذوقًا: القائمة الآن تُرتَّب **بالاسم المعروض نفسه**، فالبحث
 *    بالمرئي يجده في موضعه.
 * 3. **الاختيار الحالي لا يُرفَع إلى الأعلى.** كان يُثبَّت فوق القائمة فتُقفز القائمة عند كل
 *    اختيار، والطلب: «no unnecessary visual movement — the list should remain stable».
 *    وهي الآن قائمة واحدة ثابتة، وحالة الاختيار تُقرأ من مقبض الراديو لا من الموضع.
 * 4. **ملاحظة الترجمة الجزئية أُزيلت من الورقة.** نصّ الطلب: «do NOT want this message
 *    displayed prominently… unless there is a strong UX reason». ولا سبب قويّ: هي تشرح
 *    حالة كل اللغات الـ٨٤ لا خيارًا في القائمة، وتأخذ سطرًا من مساحة اللغات في كل فتحة.
 * 5. **البحث يطابق أربعة مفاتيح**: الإنجليزي والعربي والأصلي والوسم — وطلب المالك صريح
 *    أنه يجب أن يعمل «بالإنجليزية والاسم الأصلي ومدخل RTL والمختلط».
 *
 * 6. **وعدد اللغات المدعومة يُعرض في الورقة** بأمر المالك («أضف عدد اللغات المدعومة بخط واضح
 *    في نافذة اللغات في شاشة الإعدادات»)، **ثم بترتيبه الجديد** («لا تجعل عدد اللغات المدعومة
 *    أسفل كلمة «اللغة» بل في الجهة المقابلة، ولا تجعل كلمة «اللغة» باللون الأسود، واجعل
 *    كلاهما بخط كبير وواضح»):
 *
 *    | | قبل | بعد |
 *    | --- | --- | --- |
 *    | الموضع | سطر تحت العنوان | **سطر واحد، كلٌّ في جهته** (`Row` · `SpaceBetween`) |
 *    | العنوان | `titleMedium` SemiBold بلون النصّ الكامل | **`titleLarge` عريض بلون `primary`** |
 *    | العدد | `labelLarge` عريض بلون النصّ الكامل | **`titleMedium` عريض بلون `primary`** |
 *
 *    **ولماذا هذه الألوان ليست ذوقًا:** كان العنوان والعدد يُرسمان بـ`onSurface` (النصّ الكامل،
 *    وهو الأسود في السمة الفاتحة) على مسافة سطرين لمعنى واحد، فيقرأهما المستخدم كتلة نصّ
 *    واحدة لا رأمًا لورقة. ولون العلامة (`primary`) يميّز الرأس عن متن الورقة، وهو
 *    **الاستعمال القائم نفسه** في `SetEditScreen` (عدّاد السجلّ بـ`primary`) فلا نمط جديد.
 *    و**موضع العدد يتبع الاتجاه لا محورًا ثابتًا**: `weight(1f)` + `TextAlign.End` تضعانه في
 *    آخر السطر (يسارًا في العربية، يمينًا في الإنجليزية) بلا `left`/`right` واحدا، **وتمنعان
 *    التزاحم**: "اللغة" في إحدى اللغات الـ٨٤ قد تطول، فلو تقاسم السطرين عرضهما بالحجم الطبيعي
 *    لأزاح أحدهما الآخر؛ والوزن يعطي العنوان عرضه الحقيقي ويعطي الباقي للعدد.
 *
 *    **ورقمه مشتقّ من `entries.size`** لا مكتوبٍ بيد: القائمة هي المصدر، فلو أُضيفت لغة أو
 *    حُذفت تبعه الرقم؛ و`i18n_coverage.py` يقيس تطابق أكواد المنتقي (٨٥) مع مجلدات
 *    `values-*` (٨٤ + en) ومع `locales_config`، فلا يزحف العدد بصمت.
 *
 * **وRTL:** كل حشو هنا منطقيّ (`start`/`end`)، وصفّ الاختيار هو `MaxChoiceRow` الذي يضع
 * المقبض في خانة الذيل فينقلب موضعه مع الاتجاه تلقائيًّا — ولا `left`/`right` في الملف.
 */
package nd.max.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nd.max.R
import nd.max.ui.component.CustomBottomSheet
import nd.max.ui.design.MaxChoiceRow
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace

/**
 * منتقي اللغة: ورقة سفلية واحدة قابلة لإعادة الاستخدام في أي شاشة تحتاج اختيار لغة.
 *
 * @param selected وسم اللغة الحالي (`AppLanguage.AUTO` أو وسم لغة). ويُعرض خيار «لغة النظام»
 *   في **الأول دائمًا** حين لا بحث، فيظلّ في موضعه ولا يقفز مع كلّ اختيار؛ وحين يوجد بحث
 *   يخضع للتصفية كغيره — فلا يبقى صفًّا لا يطابق ما كُتب في حقل البحث.
 */
@Composable
fun LanguagePickerSheet(
    visible: Boolean,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val entries = remember { AppLanguage.entries() }
    var query by remember { mutableStateOf("") }

    // صفّ «لغة النظام» — مُشتقّ من الجهاز لا ثابت، فيقرأ المستخدم ما سيُطبَّق فعلًا.
    val systemTitle = stringResource(R.string.max_language_auto)
    val systemName = remember { AppLanguage.systemLanguageName() }
    val systemMatches = remember(query, systemTitle, systemName) {
        val needle = query.trim()
        needle.isEmpty() || systemTitle.contains(needle, ignoreCase = true) ||
            systemName.contains(needle, ignoreCase = true) ||
            AppLanguage.AUTO.contains(needle, ignoreCase = true)
    }

    val filtered = remember(query, entries) {
        val needle = query.trim()
        if (needle.isEmpty()) {
            entries
        } else {
            entries.filter { entry ->
                entry.englishName.contains(needle, ignoreCase = true) ||
                    entry.nativeName.contains(needle, ignoreCase = true) ||
                    entry.localizedName.contains(needle, ignoreCase = true) ||
                    entry.tag.contains(needle, ignoreCase = true)
            }
        }
    }

    CustomBottomSheet(visible = visible, onDismiss = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            // ── رأس الورقة: العنوان والعدد في **سطر واحد**، كلٌّ في جهته (بأمر المالك) ──
            //
            // كان العدد سطرًا كاملًا تحت العنوان، فصارت الفتحة تبدأ بسطرين لمعنى واحد، ويقع
            // العدد في موضع "الوصف التابع" لا موضع الرقم الذي يحيط بالقائمة. والآن هما سطر
            // واحد: العنوان في أوّل السطر (يمينًا في العربية) والعدد في آخره (يسارًا) — وهو
            // الانعكاس نفسه يحدث في الإنجليزية بلا فرع شرطي ولا `left`/`right`.
            //
            // واللون `primary` لا `onSurface`: كان الاثنان بلون النصّ الكامل (الأسود في
            // السمة الفاتحة)، فأمر المالك ألّا يكون العنوان أسود، ولون العلامة هو الاستعمال
            // القائم في هذا المشروع لرأس/عدّاد (`SetEditScreen` عدّاد السجلّ) فلا نمط جديد.
            // والحجم: `titleLarge` للعنوان و`titleMedium` للعدد — أكبر من السابق بمقامين
            // مرئيّين، وكلاهما عريض فلا يعتمد التمييز على اللون وحده (قاعدة إتاحة قائمة).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = MaxSpace.xs, end = MaxSpace.xs, bottom = MaxSpace.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.max_language_title),
                    style = MaterialTheme.typography.titleLarge.copy(lineBreak = LineBreak.Heading),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    // يُعلن نفسه عنوانًا كما في بقية الشاشات، فيقرأه قارئ الشاشة عنوانًا لا سطرًا.
                    modifier = Modifier.semantics { heading() },
                )

                // والعدد مشتقّ من القائمة نفسها (`entries.size`) لا مكتوب بيد، فلو أُضيفت لغة
                // أو حُذفت تبعه الرقم؛ و`i18n_coverage.py` يقيس تطابق أكواد المنتقي مع مجلدات
                // `values-*` ومع `locales_config`، فلا يزحف العدد بصمت.
                //
                // و`weight(1f)` + `TextAlign.End`: العدد يأخذ ما بقي من السطر ويقف في آخره —
                // و«الآخر» يتبع الاتجاه (يسارًا في العربية) بلا `left`/`right`؛ ويُرفع خطر
                // تزاحم الاسم مع العدد في اللغات التي يطول فيها العنوان.
                Text(
                    text = stringResource(R.string.max_language_count, entries.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .padding(start = MaxSpace.sm)
                        .weight(1f),
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.max_language_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.max_language_clear_search),
                            )
                        }
                    }
                },
            )

            Spacer(Modifier.height(MaxSpace.sm))

            // `heightIn(max)` لا `height` ثابت: ورقةٌ بخيارات قليلة (نتيجة بحث ضيّقة) تُقاس
            // بمحتواها فلا تترك فراغًا، وورقةٌ بـ٨٤ لغة تُستخدم المساحة المتاحة كلها.
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = MaxSize.dialogListMax + 180.dp),
                verticalArrangement = Arrangement.spacedBy(MaxSpace.hairline),
            ) {
                if (systemMatches) {
                    item(key = "language_picker_system") {
                        MaxChoiceRow(
                            title = systemTitle,
                            // اسم لغة الجهاز تحته — فلا يبقى «تلقائي» وصفًا مبهمًا، ويبقى
                            // الصفّ سطرًا واحدًا لا يتغيّر عرضه عند تبديل الاختيار.
                            subtitle = systemName.takeIf { it.isNotEmpty() },
                            selected = selected == AppLanguage.AUTO,
                            onSelect = { onSelect(AppLanguage.AUTO) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                items(items = filtered, key = { it.tag }) { entry ->
                    MaxChoiceRow(
                        title = entry.englishName,
                        // السطر الثاني يظهر فقط حين يختلف الاسم عن عنوانه، فلا يُكرَّر
                        // `English` مرّتين في صفّ واحد.
                        subtitle = entry.nativeName.takeIf { it != entry.englishName },
                        selected = entry.tag == selected,
                        onSelect = { onSelect(entry.tag) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // والفراغ يُعلن فقط حين لا لغة **ولا** صفّ نظامي: وإلا لأخبر المستخدم أن
                // بحثه بلا نتيجة وهو يرى صفًّا مطابقًا أمامه.
                if (filtered.isEmpty() && !systemMatches) {
                    item(key = "language_picker_empty") {
                        Text(
                            text = stringResource(R.string.max_language_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                start = MaxSpace.gutter,
                                end = MaxSpace.gutter,
                                top = MaxSpace.md,
                                bottom = MaxSpace.md,
                            ),
                        )
                    }
                }
            }
        }
    }
}
