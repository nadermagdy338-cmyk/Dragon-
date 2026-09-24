package nd.max.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import nd.max.R
import nd.max.ui.component.CustomBottomSheet
import nd.max.ui.design.MaxChoiceRow
import nd.max.ui.design.MaxSpace

/**
 * منتقي لغة التطبيق: ورقة سفلية بخيار **تلقائي (النظام)** أولًا، ثم اللغات الـ٨٥ المدعومة.
 *
 * قرارات مقصودة:
 * - البحث موجود لأن القائمة ٨٦ خيارًا؛ بلا بحث تصبح ورقة تمرير طويلة.
 * - كل لغة تُعرض **باسمها بلغة الواجهة** («الأوردية»، «Portuguese») وباسمها الأصلي كسطر ثانوي إن
 *   اختلفا، فالسطر الأول يُقرأ ويُمسح بعين القارئ، والاسم الأصلي يبقى للذي لا يقرأ لغة الواجهة
 *   (وبالبحث يصل إليه بأيّهما). كان العكس (الأصلي عنوانًا)، فرُتّبت القائمة بـ`Collator` الأصلي
 *   وخرجت **متعدّدة الأبجديات** — الإصلاح في `AppLanguage.entries`.
 * - **لغات الجهاز تُرفع إلى الأعلى** (`AppLanguage.deviceTags`): هي مرشّح المستخدم الأول،
 *   فلا يُمرَّر على ٨٥ صفًّا ليصل إلى لغته. واختياره الحالي يُثبّت فوقها مباشرة.
 * - «تلقائي» يبقى خارج التصفية كي لا يختفي عند البحث.
 * - وحين يُكتب في البحث تسقط المقاطع وتُعرض النتائج قائمة واحدة — فالتقسيم يصير ضجيجًا لا هيكلًا.
 * - سطر أخير صريح عن التغطية: بعض اللغات مترجمة جزئيًا والنص الناقص يظهر بالإنجليزية — لا ندّعي غير ذلك.
 */
@Composable
fun AppLanguageSheet(
    visible: Boolean,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val entries = remember { AppLanguage.entries() }
    val automaticName = remember { AppLanguage.displayName(AppLanguage.AUTO) }
    var query by remember { mutableStateOf("") }

    val filtered = remember(query, entries) {
        if (query.isBlank()) {
            entries
        } else {
            val needle = query.trim()
            entries.filter { entry ->
                entry.nativeName.contains(needle, ignoreCase = true) ||
                    entry.localizedName.contains(needle, ignoreCase = true) ||
                    entry.tag.contains(needle, ignoreCase = true)
            }
        }
    }

    CustomBottomSheet(visible = visible, onDismiss = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Text(
                text = stringResource(R.string.max_language_title),
                style = MaterialTheme.typography.titleMedium.copy(lineBreak = LineBreak.Heading),
                fontWeight = FontWeight.SemiBold,
                // عنوان الورقة يُعلن نفسه كعنوان، كما في بقية الشاشات.
                modifier = Modifier
                    .padding(start = MaxSpace.xs, end = MaxSpace.xs, bottom = MaxSpace.md)
                    .semantics { heading() },
            )

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
                            Icon(Icons.Filled.Close, contentDescription = null)
                        }
                    }
                },
            )

            Spacer(Modifier.height(8.dp))

            // التقسيم يُبنى من حقيقتين مقروءتين: اختيار المستخدم الحالي، ولغات جهازه المضبوطة.
            // ولا يُكرَّر صفّ: ما ثُبّت في الأعلى يُستثنى من قائمة «كل اللغات» أسفله.
            val searching = query.isNotBlank()
            val deviceTags = remember { AppLanguage.deviceTags() }
            val selectedEntry = filtered.firstOrNull { it.tag == selected }
            val deviceEntries = if (searching) {
                emptyList()
            } else {
                filtered.filter { it.tag in deviceTags && it.tag != selected }
            }
            val rest = filtered.filterNot { entry ->
                entry.tag == selected || deviceEntries.any { it.tag == entry.tag }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item(key = AppLanguage.AUTO) {
                    MaxChoiceRow(
                        title = stringResource(R.string.max_language_auto),
                        subtitle = automaticName,
                        selected = selected == AppLanguage.AUTO,
                        onSelect = { onSelect(AppLanguage.AUTO) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (!searching) {
                    // اللغة الحالية قبل كل شيء: من فتح الورقة ليتأكد ممّا اختاره يجده بلا تمرير،
                    // ومن أراد تغييره لا يعنيه موضعه الأبجدي.
                    selectedEntry?.let { entry ->
                        item(key = "selected_${entry.tag}") {
                            LanguageChoiceRow(entry = entry, isSelected = true, onSelect = onSelect)
                        }
                    }

                    if (deviceEntries.isNotEmpty()) {
                        item(key = "device_label") {
                            SheetSectionLabel(stringResource(R.string.max_language_device_section))
                        }
                        items(items = deviceEntries, key = { "device_${it.tag}" }) { entry ->
                            LanguageChoiceRow(entry = entry, isSelected = false, onSelect = onSelect)
                        }
                    }

                    if (rest.isNotEmpty()) {
                        item(key = "all_label") {
                            SheetSectionLabel(stringResource(R.string.max_language_all_section))
                        }
                    }
                }

                items(items = rest, key = { it.tag }) { entry ->
                    LanguageChoiceRow(entry = entry, isSelected = entry.tag == selected, onSelect = onSelect)
                }

                if (filtered.isEmpty()) {
                    item(key = "empty") {
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

            Spacer(Modifier.height(MaxSpace.md))

            Text(
                text = stringResource(R.string.max_language_partial_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = MaxSpace.gutter,
                    end = MaxSpace.gutter,
                    bottom = MaxSpace.sm,
                ),
            )
        }
    }
}

/** صفّ لغة واحد: اسم يُقرأ بلغة الواجهة، واسمها الأصلي تحته إن اختلفا. */
@Composable
private fun LanguageChoiceRow(
    entry: AppLanguage.Entry,
    isSelected: Boolean,
    onSelect: (String) -> Unit,
) {
    MaxChoiceRow(
        title = entry.localizedName,
        subtitle = entry.nativeName.takeIf { it != entry.localizedName },
        selected = isSelected,
        onSelect = { onSelect(entry.tag) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** عنوان مقطع داخل الورقة. */
@Composable
private fun SheetSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(
            start = MaxSpace.gutter,
            end = MaxSpace.gutter,
            top = MaxSpace.md,
            bottom = MaxSpace.xs,
        ),
    )
}
