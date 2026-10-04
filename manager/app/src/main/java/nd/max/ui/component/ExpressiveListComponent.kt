/*
 * Copyright (C) 2026-2027 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nd.max.ui.component


import android.annotation.SuppressLint
import androidx.compose.animation.animateColorAsState
import nd.max.ui.design.MaxAlpha
import nd.max.ui.design.MaxCardSpec
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxSpace
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.alpha
import kotlin.math.roundToInt


/*
 * نصف قطر القوائم المجمَّعة — **من عقد البطاقة لا من رقم محلّي**.
 *
 * كان `26.dp`، بينما `MaxCardSpec.radius` = `MaxRadius.group` = 22dp. أي أنّ **أكثر بطاقة
 * استعمالًا في التطبيق** (كل صفوف القوائم المجمَّعة، في العشرات من الشاشات) كانت على نصف قطر
 * لا يعرفه العقد ولا يقدر أحد تغييره من مكان واحد. وهذان الرقمان كانا **داخل الملفّ** لا في
 * موضع الاستعمال، فلم يصلهما توحيد الجولة الماضية (18/24/26 ← 22) — وهذا نصّ §١١ حرفيًّا
 * («If I later change the card radius or spacing, I should be able to change it globally»).
 */
private val largeCorner = MaxCardSpec.radius
private val smallCorner = MaxSpace.xs

/**
 * الفاصل بين مقاطع **سطح واحد** متّصل (لا بين بطاقتين منفصلتين).
 *
 * فاصل داخل السطح المتّصل، وليس شبكة: `MaxCardSpec.gridSpacing` (12dp) يفصل بطاقتين
 * مستقلّتين، و`MaxSpace.row` (8dp) يفصل صفّين في قائمة الهيكل — أمّا هنا فالمقاطع تُرسم بحوافّ
 * مكمّلة (عليا/وسطى/سفلى) لتُقرأ بطاقة واحدة، والفراغ بينها **لحام** لا فجوة. سُمّي ولذلك
 * ليعرف قارئه أنّه **مقصود** لا بقية رقم قديم.
 */
private val GroupedRowSeam = 6.dp

// Grouped-list geometry is intentionally different from standalone cards:
// only the outer top/bottom edges are rounded, while the rows remain visually
// connected. Icon containers must never inherit these shapes.
private val topShape = RoundedCornerShape(
    topStart = largeCorner,
    topEnd = largeCorner,
    bottomStart = smallCorner,
    bottomEnd = smallCorner
)
private val middleShape = RoundedCornerShape(smallCorner)
private val bottomShape = RoundedCornerShape(
    topStart = smallCorner,
    topEnd = smallCorner,
    bottomStart = largeCorner,
    bottomEnd = largeCorner
)
private val singleShape = RoundedCornerShape(largeCorner)
private val iconContainerShape = RoundedCornerShape(MaxRadius.control)

/**
 * The card treatment shared by every grouped-list item across the app
 * (settings rows, tweak rows, presets, hardware facts...). A slightly higher
 * tonal elevation than before plus a hairline outline gives each segment a
 * touch of "instrument panel" depth instead of reading as one flat block of
 * color — the same handful of pixels repeated thousands of times across the
 * app, so worth getting right once, here.
 */
@Composable
private fun Modifier.expressiveCardSurface(shape: RoundedCornerShape): Modifier {
    val colorScheme = MaterialTheme.colorScheme
    return this
        .clip(shape)
        .background(colorScheme.surfaceContainerLow, shape)
        // العرض والشفافية من العقد لا أرقامًا: كان `1.dp` مكتوبًا هنا و`0.32f` لا وجود لها في
        // سلّم `MaxAlpha` أصلًا (أقرب قيمة `borderStrong` = 0.28f). والقيمة الآن مسمّاة.
        .border(
            BorderStroke(
                MaxCardSpec.borderWidth,
                colorScheme.outlineVariant.copy(alpha = MaxAlpha.borderStrong)
            ),
            shape
        )
}

/**
 * شكل مقطع واحد في مجموعة — **تعريف واحد بدل ثلاثة**.
 *
 * كان الاختيار الثلاثي مكرّرًا بنسخه في `ExpressiveList` و`ExpressiveLazyList` و`ExpressiveColumn`،
 * فتصلح واحدة وتُنسى الثانية. وهذا نصّ §١٢ («prefer reusable components over duplicated UI
 * implementations»).
 */
private fun groupedShape(index: Int, count: Int): RoundedCornerShape = when {
    count == 1 -> singleShape
    index == 0 -> topShape
    index == count - 1 -> bottomShape
    else -> middleShape
}

/** شكل الحاوية التي تُقصّ فوق كل مقاطع المجموعة. */
private val groupedContainerShape = RoundedCornerShape(largeCorner)

/**
 * A grouped list: one card per row, stacked with a gap so every row reads as its
 * own item instead of running into the next one.
 *
 * @param rowSpacing gap between two cards. The default is the app-wide rhythm;
 *        a screen whose rows are heavier than a settings row (the Control page's
 *        domain lists) passes a wider value, because the same 6dp that separates
 *        two one-line rows reads as zero between two tall bordered cards.
 */
@Composable
fun ExpressiveList(
    modifier: Modifier = Modifier,
    title: String = "",
    content: List<@Composable () -> Unit>,
    rowSpacing: Dp = GroupedRowSeam,
) {
    if (content.isEmpty()) return

    Column(modifier = modifier) {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                // **والمحاذاة مع مقاطع القائمة نفسها لا مع حافة الشاشة.**
                //
                // كان `start = 16.dp` ثابتًا: في ورقة سفلية (‏`CustomBottomSheet` لا يحمل حشوًا
                // أفقيًّا) صار العنوان عند 16dp والمقاطع عند 0dp ⇒ عنوانان لا يحاذي ما تحته؛ وفي
                // صفحة يحمل هيكلها الهامش صار 36dp على 360dp. والقاعدة الآن واحدة: عنوان القائمة
                // **يحاذي مقاطعها**، ومن أراد إزاحة الاثنين معًا أعطى `modifier` للقائمة.
                modifier = Modifier.padding(bottom = MaxSpace.sm)
            )
        }
        Column(
            modifier = Modifier.clip(groupedContainerShape),
            verticalArrangement = Arrangement.spacedBy(rowSpacing)
        ) {
            content.forEachIndexed { index, itemContent ->
                Column(
                    modifier = Modifier.expressiveCardSurface(groupedShape(index, content.size))
                ) {
                    itemContent()
                }
            }
        }
    }
}

@Composable
fun <T> ExpressiveLazyList(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    // **صفر لا 16dp.** حشو القائمة يملكه **المكان** لا المكوّن: على صفحة يحمل هيكلها هامشه
    // (`MaxSpace.gutter`) فيصير 16dp فوقه = 36dp؛ وفي ورقة سفلية يحمله منادٍ واحد. والافتراضيّ
    // الذي يفرض إزاحة على كل مستدعٍ هو نفس عطب «الهامش المضاعف» في صورة المالك، مكتوبًا مرّة واحدة
    // في مكان يشترك فيه كل شيء.
    contentPadding: PaddingValues = PaddingValues(),
    title: String = "",
    key: ((T) -> Any)? = null,
    items: List<T>,
    itemContent: @Composable (T) -> Unit
) {
    Column(modifier = modifier) {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = MaxSpace.sm)
            )
        }
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(GroupedRowSeam),
            contentPadding = contentPadding
        ) {
            itemsIndexed(
                items = items,
                key = if (key != null) { _, item -> key(item) } else null
            ) { index, item ->
                val shape = groupedShape(index, items.size)
                Column(
                    modifier = Modifier

                        .animateItem(
                            fadeInSpec = null,
                            fadeOutSpec = null,
                            placementSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        )
                        .expressiveCardSurface(shape)
                ) {
                    itemContent(item)
                }
            }
        }

    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpressiveListItem(
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    headlineContent: @Composable () -> Unit,
    /**
     * خلفية الصفّ حين يكون **مُختارًا** (شفّافة إن لم يكن).
     *
     * أُضيف لأنّ `ExpressiveListItemHighlight` كان **نسخة ثانية من هذا الصفّ بحرفه** — تسعون
     * سطرًا تُكرّر المنطق نفسه (الضغط، وقصّ الذيل، وألوان المحتوى، و`LineBreak.Heading`) وتزيد
     * عليها سطرًا واحدًا: `.background(containerColor)`. فكل إصلاح كان يُكتب مرّتين، وواحد
     * منهما يُنسى — وهو نصّ §١٤ («prefer reusable components over duplicated UI implementations»).
     * الآن **تعريف واحد**، والتمييز معاملٌ لا نسخة.
     */
    containerColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Transparent,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    supportingContent: @Composable (() -> Unit)? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = tween(110),
        label = "expressiveItemPress"
    )
    // **حدّ عرض خانة الذيل هو سبب وجود هذا الصندوق.**
    //
    // العطب الذي أبلغ عنه المالك: كلمة «Language» في الإعدادات انكسرت **حرفًا في كل سطر**.
    // والسبب ليس النصّ ولا `maxLines`: خانة الذيل كانت `Box` بلا أي قيد عرض، فتُقاس عند عرضها
    // الأقصى (اسم لغة طويل + سهم)، ويأخذ العمود الموزون `weight(1f)` ما بقي — وقد يبقى عرض
    // حرف واحد. فالعلاج **هندسي**: الذيل لا يُسمح له بأكثر من [TRAILING_MAX_FRACTION] من الصفّ،
    // فيبقى العنوان ≥٥٥٪ منها عنده ما يكفي لأطول كلمة فيه.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val trailingMaxWidth = maxWidth * TRAILING_MAX_FRACTION
        Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(pressScale)
            .background(containerColor)
            .let {
                if (onClick != null || onLongClick != null) {
                    it.combinedClickable(interactionSource = interactionSource, indication = null, onClick = onClick ?: {}, onLongClick = onLongClick)
                } else {
                    it
                }
            }
            .then(modifier)
            // حشو **داخل البطاقة** لا على الصفحة: القيم لم تتغيّر، وإنّما سُمّيت من العقد
            // (`MaxCardSpec.padding` = 16dp · `MaxSpace.sm` = 8dp) فلا تبقى أرقامًا بلا مرجع.
            .padding(horizontal = MaxCardSpec.padding, vertical = MaxSpace.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingContent != null) {
            Box(
                modifier = Modifier.padding(end = MaxCardSpec.padding),
                contentAlignment = Alignment.Center
            ) {
                leadingContent()
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = MaxSpace.sm)
        ) {
            // The headline and the trailing slot inherit LocalContentColor, and these rows
            // are painted directly onto the screen surface — whose Scaffold is transparent,
            // so contentColorFor() leaves the ambient content colour unresolved and the text
            // fell through to black. That is why the Control screen's list layout read as
            // unthemed while its card layout (which sets colours explicitly) looked right.
            // Pinning on-surface here themes every caller; a caller that wants another tone
            // still wins by passing its own colour.
            // و`LineBreak.Heading` يمنع كسر **داخل الكلمة** حيث تُسحَب الكلمة إلى عمود أضيق من
            // عرضها: بلا هذا التقييد يقسم المفكّك الجشع كلمة واحدة إلى حروف قبل أن يُقصّها.
            // يُقدَّم لكل عنوان في المستودع يمرّ من هنا (ADR-12: منع التعطّل لا إصلاح مثيل).
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                LocalTextStyle provides LocalTextStyle.current.copy(lineBreak = LineBreak.Heading)
            ) {
                headlineContent()
                if (supportingContent != null) {
                    CompositionLocalProvider(
                        LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        ProvideTextStyle(value = MaterialTheme.typography.bodySmall) {
                            supportingContent()
                        }
                    }
                }
            }
        }
        if (trailingContent != null) {
            // Same reason as the headline above: an untinted icon (the row caret) would
            // otherwise resolve to black instead of the surface's content colour.
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onSurface
            ) {
                Box(
                    modifier = Modifier
                        .padding(start = MaxCardSpec.padding)
                        .widthIn(max = trailingMaxWidth),
                    contentAlignment = Alignment.Center
                ) {
                    ProvideTextStyle(value = MaterialTheme.typography.bodySmall) {
                        trailingContent()
                    }
                }
            }
        }
    }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpressiveInfoCard(
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    containerColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Transparent, 
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    supportingContent: @Composable (() -> Unit)? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = tween(110),
        label = "expressiveItemPress"
    )
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val trailingMaxWidth = maxWidth * TRAILING_MAX_FRACTION
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(pressScale)
            .background(containerColor) 
            .let {
                if (onClick != null || onLongClick != null) {
                    it.combinedClickable(interactionSource = interactionSource, indication = null, onClick = onClick ?: {}, onLongClick = onLongClick)
                } else {
                    it
                }
            }
            .then(modifier)
            // حشو **داخل البطاقة** لا على الصفحة: القيم لم تتغيّر، وإنّما سُمّيت من العقد
            // (`MaxCardSpec.padding` = 16dp · `MaxSpace.sm` = 8dp) فلا تبقى أرقامًا بلا مرجع.
            .padding(horizontal = MaxCardSpec.padding, vertical = MaxSpace.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingContent != null) {
            Box(
                modifier = Modifier.padding(end = MaxCardSpec.padding),
                contentAlignment = Alignment.Center
            ) {
                leadingContent()
            }
        }
        

        if (supportingContent != null) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    ProvideTextStyle(value = MaterialTheme.typography.bodyMedium) {
                        supportingContent()
                    }
                }
            }
        } else {

            Spacer(modifier = Modifier.weight(1f))
        }
        
        if (trailingContent != null) {
            Box(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .widthIn(max = trailingMaxWidth),
                contentAlignment = Alignment.Center
            ) {
                ProvideTextStyle(value = MaterialTheme.typography.bodySmall) {
                    trailingContent()
                }
            }
        }
    }
    }
}


@Composable
fun ExpressiveSwitchItem(
    icon: ImageVector? = null,
    title: String,
    summary: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }

    ExpressiveListItem(
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier.toggleable(
            value = checked,
            interactionSource = interactionSource,
            role = Role.Switch,
            enabled = enabled,
            indication = LocalIndication.current,
            onValueChange = onCheckedChange
        ),
        headlineContent = { Text(title) },
        leadingContent = icon?.let { { LeadingIcon(icon = it, contentDescription = title) } },
        trailingContent = {
            MaxSwitch(
                checked = checked,
                enabled = enabled,
                thumbContent = {
                    if (checked) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    }
                }, 
                onCheckedChange = onCheckedChange,
                interactionSource = interactionSource
            )
        },
        supportingContent = summary?.let { { Text(it) } }
    )
}

@Composable
fun ExpressiveDropdownItem(
    icon: ImageVector? = null,
    title: String,
    summary: String? = null,
    items: List<String>,
    enabled: Boolean = true,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    val hasItems = items.isNotEmpty()
    val safeIndex = if (hasItems) {
        selectedIndex.coerceIn(0, items.lastIndex)
    } else {
        -1
    }

    ExpressiveListItem(
        modifier = Modifier
            .animateContentSize()
            .then(
                if (enabled) {
                    Modifier.clickable { expanded = true }
                } else {
                    Modifier
                }
            ),
        leadingContent = icon?.let { { LeadingIcon(icon = it, contentDescription = title) } },
        headlineContent = { Text(text = title) },
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = {
            Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
                Text(
                    text = if (hasItems && safeIndex >= 0) items[safeIndex] else "",
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    items.forEachIndexed { index, text ->
                        DropdownMenuItem(
                            text = { Text(text) },
                            onClick = {
                                if (index in items.indices) {
                                    onItemSelected(index)
                                }
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    )
}


@Composable
fun ExpressiveRadioItem(
    title: String,
    summary: String? = null,
    selected: Boolean,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    ExpressiveListItem(
        onClick = onClick,
        modifier = Modifier.toggleable(
            value = selected,
            onValueChange = { onClick() },
            enabled = enabled,
            role = Role.RadioButton
        ),
        headlineContent = {
            Text(
                title,
                color = if (danger) colorScheme.error else Color.Unspecified
            )
        },
        leadingContent = {
            RadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled,
                colors = if (danger) {
                    RadioButtonDefaults.colors(
                        selectedColor = colorScheme.error,
                        unselectedColor = colorScheme.error.copy(alpha = 0.6f)
                    )
                } else {
                    RadioButtonDefaults.colors()
                }
            )
        },
        supportingContent = summary?.let { { Text(it) } }
    )
}

@Composable
fun ExpressiveCheckboxItem(
    title: String,
    summary: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }

    ExpressiveListItem(
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier.toggleable(
            value = checked,
            interactionSource = interactionSource,
            role = Role.Checkbox,
            enabled = enabled,
            indication = LocalIndication.current,
            onValueChange = onCheckedChange
        ),
        headlineContent = { Text(title) },
        leadingContent = {
            Checkbox(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
                interactionSource = interactionSource,
                modifier = Modifier.size(24.dp)
            )
        },
        supportingContent = summary?.let { { Text(it) } }
    )
}

@Composable
fun ExpressiveColumn(
    modifier: Modifier = Modifier,
    title: String = "",
    content: List<@Composable () -> Unit>,
) {
    if (content.isEmpty()) return

    Column(modifier = modifier) {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = MaxSpace.sm)
            )
        }
        Column(
            modifier = Modifier.clip(groupedContainerShape),
            verticalArrangement = Arrangement.spacedBy(GroupedRowSeam)
        ) {
            content.forEachIndexed { index, itemContent ->
                Column(
                    modifier = Modifier.expressiveCardSurface(groupedShape(index, content.size))
                ) {
                    itemContent()
                }
            }
        }
    }
}

@Composable
fun LeadingIcon(
    icon: ImageVector,
    contentDescription: String? = null,
    containerColor: androidx.compose.ui.graphics.Color? = null,
    contentColor: androidx.compose.ui.graphics.Color = LocalScreenAccent.current ?: MaterialTheme.colorScheme.primary
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(iconContainerShape)
            .background(containerColor ?: contentColor.copy(alpha = 0.09f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp),
            tint = contentColor
        )
    }
}

@Composable
fun SmallLeadingIcon(icon: ImageVector) {
    val accent = LocalScreenAccent.current ?: MaterialTheme.colorScheme.primary
    Surface(
        shape = iconContainerShape,
        color = accent.copy(alpha = 0.09f),
        modifier = Modifier.size(36.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier
                .padding(7.dp)
                .size(22.dp)
        )
    }
}

private val sliderTrackHeight = 14.dp
private val sliderTouchWidth = 28.dp
private val sliderTouchHeight = 48.dp
private val sliderThumbWidth = 8.dp
private val sliderThumbHeight = 30.dp
private val sliderThumbCoreWidth = 3.dp
private val sliderThumbCoreHeight = 20.dp
private val sliderGlowWidth = 22.dp
private val sliderGlowHeight = 36.dp

/**
 * The control bar shared by every tunable value in the app (frequency
 * floors/ceilings, currents, swappiness, resolution...). Redesigned as one
 * cohesive "instrument panel" widget rather than a stock Material slider:
 * a machined, inset groove for the track, a glowing gradient fill that
 * answers to the screen's accent color, a two-layer capsule thumb that
 * lifts and glows on touch, and a status badge that comes alive while the
 * user is actively dragging. Track and thumb are custom-drawn via the
 * Slider's `track`/`thumb` slots so gesture handling, accessibility and RTL
 * layout still come from the platform component -- only the paint job is ours.
 */
@Composable
fun ExpressiveSliderItem(
    icon: ImageVector? = null,
    title: String,
    subtitle: String? = null,
    badgeText: String,
    sliderPosition: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val accent = LocalScreenAccent.current ?: colorScheme.primary
    val haptic = LocalHapticFeedback.current

    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isActive = enabled && (isDragged || isPressed)

    val span = valueRange.endInclusive - valueRange.start
    val progressFraction = if (span > 0f) ((sliderPosition - valueRange.start) / span).coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "LabeledSliderProgress"
    )

    val animatedAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.4f,
        animationSpec = tween(durationMillis = 300),
        label = "SliderEnabledAlpha"
    )

    // A tiny tick of haptic feedback whenever the value crosses into a new
    // discrete step while actively dragging -- the kind of tactile detail a
    // proper hardware fader would give you for free.
    if (steps > 0) {
        val span = (valueRange.endInclusive - valueRange.start)
        val currentStepIndex = if (span > 0f) {
            (progressFraction * (steps + 1)).roundToInt()
        } else 0
        val lastStepIndex = remember { mutableStateOf(currentStepIndex) }
        LaunchedEffect(currentStepIndex, isActive) {
            if (isActive && currentStepIndex != lastStepIndex.value) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            lastStepIndex.value = currentStepIndex
        }
    }

    val badgeContainerColor by animateColorAsState(
        targetValue = accent.copy(alpha = if (isActive) 0.20f else 0.12f),
        animationSpec = tween(durationMillis = 200),
        label = "BadgeContainer"
    )
    val badgeScale by animateFloatAsState(
        targetValue = if (isActive) 1.03f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 380f),
        label = "BadgeScale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .alpha(animatedAlpha)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (icon != null) {
                LeadingIcon(icon = icon, contentDescription = null)
            }
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = colorScheme.onSurface)
        }
        Spacer(Modifier.height(10.dp))
        Surface(color = badgeContainerColor, shape = RoundedCornerShape(MaxRadius.chip), modifier = Modifier.scale(badgeScale)) {
            Text(badgeText, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = nd.max.ui.theme.MonoValueStyleSmall, color = accent)
        }
        Spacer(modifier = Modifier.height(4.dp))

        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        MaxSlider(
            value = sliderPosition,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            interactionSource = interactionSource,
            colors = SliderDefaults.colors(
                thumbColor = Color.Transparent,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                disabledThumbColor = Color.Transparent,
                disabledActiveTrackColor = Color.Transparent,
                disabledInactiveTrackColor = Color.Transparent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(sliderTouchHeight)
        )
    }
}

/**
 * The groove: an inset "machined" channel with a soft gradient fill that
 * glows and gains a glassy highlight as it advances, plus faint tick dots
 * for discrete steps. Fully hand-drawn so it stays crisp at any width and
 * mirrors correctly under RTL layouts (Arabic and friends).
 */
@Composable
private fun PremiumSliderTrack(
    fraction: Float,
    accent: Color,
    isActive: Boolean,
    steps: Int
) {
    val colorScheme = MaterialTheme.colorScheme
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val glowAlpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.45f,
        animationSpec = tween(durationMillis = 220),
        label = "TrackGlow"
    )

    val grooveColor = colorScheme.surfaceContainerHighest
    val tickColorLit = colorScheme.surface.copy(alpha = 0.65f)
    val tickColorUnlit = colorScheme.outline.copy(alpha = 0.35f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(sliderTrackHeight)
    ) {
        val corner = CornerRadius(size.height / 2f)
        val fillWidth = (size.width * fraction).coerceIn(0f, size.width)
        val fillStartX = if (isRtl) size.width - fillWidth else 0f

        // Inset shadow beneath the groove, then the groove surface itself.
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.10f),
            topLeft = Offset(0f, 1.dp.toPx()),
            size = size,
            cornerRadius = corner
        )
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(grooveColor.copy(alpha = 0.92f), grooveColor)),
            size = size,
            cornerRadius = corner
        )
        // Hairline glass edge along the top of the groove.
        drawRoundRect(
            color = Color.White.copy(alpha = 0.05f),
            size = Size(size.width, size.height * 0.45f),
            cornerRadius = CornerRadius(size.height * 0.45f / 2f)
        )

        if (fillWidth > 0f) {
            // Soft bloom behind the active fill, padded evenly on all sides.
            val bloomPad = 4.dp.toPx()
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(accent.copy(alpha = 0.18f * glowAlpha), accent.copy(alpha = 0.32f * glowAlpha))
                ),
                topLeft = Offset(fillStartX - bloomPad, -bloomPad),
                size = Size(fillWidth + bloomPad * 2f, size.height + bloomPad * 2f),
                cornerRadius = CornerRadius((size.height + bloomPad * 2f) / 2f)
            )
            // The fill itself: a rich gradient that answers to the accent color.
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(accent.copy(alpha = 0.82f), accent),
                    startX = fillStartX,
                    endX = fillStartX + fillWidth
                ),
                topLeft = Offset(fillStartX, 0f),
                size = Size(fillWidth, size.height),
                cornerRadius = corner
            )
            // Glossy top highlight, like light catching brushed metal.
            val inset = 1.dp.toPx()
            val highlightWidth = (fillWidth - inset * 2f).coerceAtLeast(0f)
            if (highlightWidth > 0f) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.16f),
                    topLeft = Offset(fillStartX + inset, inset),
                    size = Size(highlightWidth, size.height * 0.4f),
                    cornerRadius = CornerRadius(size.height * 0.4f / 2f)
                )
            }
        }

        // Discrete step ticks, tucked neatly inside the groove.
        if (steps > 0) {
            val totalGaps = steps + 1
            val radius = 1.4.dp.toPx()
            val cy = size.height / 2f
            for (i in 1..steps) {
                val t = i.toFloat() / totalGaps
                val cx = if (isRtl) size.width * (1f - t) else size.width * t
                val lit = if (isRtl) cx >= fillStartX else cx <= fillWidth
                drawCircle(
                    color = if (lit) tickColorLit else tickColorUnlit,
                    radius = radius,
                    center = Offset(cx, cy)
                )
            }
        }
    }
}

/**
 * The thumb: a machined capsule with a bright accent core, a bezel that
 * catches a real elevation shadow tinted to the screen's accent color, and
 * a soft halo that blooms outward while the user is dragging.
 */
@Composable
private fun PremiumSliderThumb(
    accent: Color,
    isActive: Boolean
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.16f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f),
        label = "ThumbScale"
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (isActive) 0.55f else 0.20f,
        animationSpec = tween(durationMillis = 220),
        label = "ThumbGlow"
    )
    val elevation by animateFloatAsState(
        targetValue = if (isActive) 6f else 2.5f,
        animationSpec = tween(durationMillis = 220),
        label = "ThumbElevation"
    )

    Box(
        modifier = Modifier.size(width = sliderTouchWidth, height = sliderTouchHeight),
        contentAlignment = Alignment.Center
    ) {
        // Soft halo, brightens on interaction.
        Box(
            modifier = Modifier
                .size(width = sliderGlowWidth, height = sliderGlowHeight)
                .scale(scale)
                .background(
                    Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = glowAlpha), Color.Transparent)
                    ),
                    shape = RoundedCornerShape(50)
                )
        )
        // Bezel: a real, accent-tinted elevation shadow gives it lift off the track.
        Box(
            modifier = Modifier
                .size(width = sliderThumbWidth, height = sliderThumbHeight)
                .scale(scale)
                .shadow(
                    elevation = elevation.dp,
                    shape = RoundedCornerShape(50),
                    ambientColor = accent,
                    spotColor = accent
                )
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surface)
        )
        // Bright accent core.
        Box(
            modifier = Modifier
                .size(width = sliderThumbCoreWidth, height = sliderThumbCoreHeight)
                .scale(scale)
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.78f)))
                )
        )
    }
}

/**
 * أقصى نصيب لخانة الذيل من عرض الصفّ (نسبة من العرض الكامل).
 *
 * قيمته **مقيسة لا مختارة**: أطول قيمة ذيل في الاستخدام الفعلي اسمُ لغة (وأطولها
 * `Português (Brasil)` ≈ ١٢٠dp بخطّ `labelMedium`)، وبقاء ٥٥٪ للعنوان يكفي أطولَ كلمة فيه
 * (`Language` ≈ ٦٥dp) حتى مع تضخيم خطّ النظام إلى الضعف على شاشة ٣٢٠dp.
 *
 * والعطب الذي وُلد منه: كلمة «Language» في الإعدادات كُسرت **حرفًا في كل سطر**، لأن خانة الذيل
 * كانت بلا قيد فتُقاس عند عرضها الأقصى ويأخذ العنوان الموزون ما يبقى.
 */
private const val TRAILING_MAX_FRACTION = 0.45f
