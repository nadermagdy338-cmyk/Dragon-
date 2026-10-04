/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * إعدادات «منصة التحكم» — الورقة التي يفتحها زرّ الإعداد **داخل البطاقة نفسها** (طلب المالك:
 * «نضيف بقا زر إعداد في نفس البطاقة»).
 *
 * وثلاثة قرارات هنا:
 *
 * 1. **وضعان بنصّ المالك:** «تلقائي» يعرض أكثر ما يفتحه، و«أختار بنفسي» يختار فيه بنفسه —
 *    والتلقائيّ هو الافتراضيّ.
 * 2. **والانتقال إلى اليدويّ يبدأ من ما تراه الآن** لا من قائمة فارغة: قائمةٌ فارغة تُقرأ كأنّ
 *    الاختيارات ضاعت، وهي **مفارقة على الشاشة نفسها** إن ظلّت البطاقات الأربع معروضة والمفاتيح
 *    كلّها مغلقة.
 * 3. **والحدّان مفروضان على المفتاح لا على الحفظ:** لا يمكن النزول تحت البطاقتين ولا الصعود
 *    فوق السادسة، ويُقال السبب في السطر نفسه (`lockedReason`) بدل مفتاح لا يستجيب بلا كلمة
 *    تشرح. **والنصّان يتبعان الرقمين لا العكس:** كان يُقرأ «أربعة هو الأدنى» لأن الأدنى كان ٤،
 *    وقد صار ٢ بأمر المالك — فلو بقي النصّ لما وافق المفتاحُ كلمتَه.
 *    **والحدّان معًا في الوضعين** (مُعلَنان): التلقائيّ لا ينزل تحت اثنتين (بطاقة واحدة ليست
 *    منصة)، واليدويّ كذلك.
 *
 * والترتيب والعدد يأتيان من [homeDeckSelection] وحدها، فلا نسخة ثانية من القاعدة في الشاشة.
 *
 * **والجولة ٢٠٣ أضافت العدد المقيس إلى السطر (أمر المالك: «اعطي لفكرتك معلومات اكثر»):**
 * كان السطر يشرح الشاشة فقط، ولا يقول **لماذا** صعدت هذه البطاقة في «التلقائي». فصار يقول
 * «فُتحت ٣ مرات» بجانب الوصف، من العدّاد الواحد نفسه الذي تقرأ منه المنصة والمُوجِّد — فيرى
 * المستخدم أساس الترتيب لا عنوانه وحده. **وسطر «‏٤ من ٦‎» ظهر في الوضعين:** كان في
 * اليدوي وحده، فصار التلقائيّ يقول عدد بطاقاته أيضًا (وما لا يُقاس لا يُعرض: من لم يفتح شيئًا
 * لا يُكتب له سطر فتحات، لا «٠ مرة»).
 */
package nd.max.ui.mainscreens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nd.max.R
import nd.max.ui.component.CustomBottomSheet
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSegmented
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxSwitchRow

/** أقصى ارتفاع لقائمة العناصر قبل أن تبدأ بالتمرير (تبقى الورقة ورأسها مرئيّين). */
private val DECK_LIST_MAX_HEIGHT = 400.dp

@Composable
fun HomeDeckSettingsSheet(
    visible: Boolean,
    mode: HomeDeckMode,
    manualKeys: List<String>,
    usage: Map<String, Int>,
    onModeChange: (HomeDeckMode) -> Unit,
    onManualChange: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    // ما تعرضه المنصة الآن بالوضع القائم — منه يبدأ الاختيار اليدويّ، وفيه يرى المستخدم
    // أثر «التلقائي» قبل أن يتركه.
    val current = homeDeckSelection(mode, manualKeys, usage)
    val manual = manualKeys.distinct()

    CustomBottomSheet(visible = visible, onDismiss = onDismiss) {
        Text(
            text = stringResource(R.string.home_deck_settings_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = MaxSpace.xl, vertical = MaxSpace.xs),
        )
        Text(
            text = stringResource(R.string.home_deck_settings_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = MaxSpace.xl, vertical = MaxSpace.xs),
        )

        MaxSegmented(
            options = listOf(
                stringResource(R.string.home_deck_mode_auto),
                stringResource(R.string.home_deck_mode_manual),
            ),
            selectedIndex = if (mode == HomeDeckMode.Manual) MANUAL_INDEX else AUTO_INDEX,
            onSelect = { index ->
                if (index == MANUAL_INDEX) {
                    if (manual.isEmpty()) onManualChange(current.map { it.key })
                    onModeChange(HomeDeckMode.Manual)
                } else {
                    onModeChange(HomeDeckMode.Auto)
                }
            },
            modifier = Modifier.padding(horizontal = MaxSpace.gutter, vertical = MaxSpace.sm),
        )

        // العدد يُقال في الوضعين: من يرى أربع بطاقات يعرف أنّهنّ أربع، وهل يجوز أن تزيد.
        Text(
            text = stringResource(R.string.home_deck_count, current.size, HOME_DECK_MAX),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = MaxSpace.xl, vertical = MaxSpace.sm),
        )

        if (mode == HomeDeckMode.Auto) {
            Text(
                text = stringResource(R.string.home_deck_auto_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = MaxSpace.xl, vertical = MaxSpace.sm),
            )
            DeckPreview(current, usage)
        } else {
            DeckPicker(
                selected = manual,
                usage = usage,
                onManualChange = onManualChange,
            )
        }
    }
}

/** قائمة قراءة فقط: ما تعرضه المنصة الآن — ومعه العدد المقيس الذي رتّبها. */
@Composable
private fun DeckPreview(
    entries: List<HomeDeckEntry>,
    usage: Map<String, Int>,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = DECK_LIST_MAX_HEIGHT)
            .padding(horizontal = MaxSpace.gutter),
    ) {
        item(key = "deck_preview") {
            MaxGroup {
                entries.forEachIndexed { index, entry ->
                    if (index > 0) MaxGroupDivider()
                    MaxRow(
                        title = stringResource(entry.titleRes),
                        subtitle = deckSubtitle(entry, usage[entry.key] ?: 0),
                        icon = entry.icon,
                    )
                }
            }
        }
    }
}

/**
 * سطر البطاقة: وصفها، ومعه عدد فتحاتها **إن كان هناك عدد**. وما لم يُفتح لا يُقال عنه
 * «٠ مرة» — العدم يُسكَت عنه، كما تُسكَت عنه أرقام العتاد التي لم تُقرأ (ADR-07).
 */
@Composable
private fun deckSubtitle(entry: HomeDeckEntry, usage: Int): String {
    val description = stringResource(entry.descriptionRes)
    if (usage <= 0) return description
    return description + " · " + stringResource(R.string.screen_opens_count, usage)
}

/** الاختيار اليدويّ، والحدّان مفروضان على المفتاح مع سببٍ مكتوب. */
@Composable
private fun DeckPicker(
    selected: List<String>,
    usage: Map<String, Int>,
    onManualChange: (List<String>) -> Unit,
) {
    val canAdd = selected.size < HOME_DECK_MAX
    val canRemove = selected.size > HOME_DECK_MIN

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = DECK_LIST_MAX_HEIGHT)
            .padding(horizontal = MaxSpace.gutter),
    ) {
        item(key = "deck_picker") {
            MaxGroup {
                HomeDeckPool.forEachIndexed { index, entry ->
                    if (index > 0) MaxGroupDivider()
                    val checked = entry.key in selected
                    MaxSwitchRow(
                        title = stringResource(entry.titleRes),
                        // **والعدد في القائمة اليدوية أيضًا** لا في المعاينة وحدها: من يختار بنفسه
                        // يريد أن يعرف أيّ شاشاته يفتحها أكثر، فيرجّحها على شاشةٍ لا يستعملها.
                        subtitle = deckSubtitle(entry, usage[entry.key] ?: 0),
                        icon = entry.icon,
                        checked = checked,
                        enabled = if (checked) canRemove else canAdd,
                        lockedReason = when {
                            checked && !canRemove -> stringResource(R.string.home_deck_limit_min)
                            !checked && !canAdd -> stringResource(R.string.home_deck_limit_max)
                            else -> null
                        },
                        onCheckedChange = { on ->
                            onManualChange(
                                if (on) selected + entry.key else selected - entry.key
                            )
                        },
                    )
                }
            }
        }
    }
}

private const val AUTO_INDEX = 0
private const val MANUAL_INDEX = 1
