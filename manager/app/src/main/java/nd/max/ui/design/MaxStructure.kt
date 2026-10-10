/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * MaxManager Design Language — page structure.
 *
 * The old UI expressed every idea as a rounded Card, so nothing had priority:
 * a critical thermal reading and a link to the changelog looked identical.
 *
 * The new structure has exactly three levels:
 *   1. Section  — titled band of the page, separated by whitespace, not boxes.
 *   2. Group    — one hairline container holding related rows (settings, specs).
 *   3. Row      — a single fact or a single action, 48dp minimum.
 * Elevation is not used to group things; borders and space are. Elevation is
 * reserved for things that float above the page (sheets, dialogs, snackbars).
 */
package nd.max.ui.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nd.max.ui.component.maxPressMotion
import nd.max.ui.component.rememberAnimationsEnabled

/**
 * A titled band of a page.
 *
 * @param title short noun phrase. Rendered as an accessibility heading so
 *        screen-reader users can jump between sections.
 * @param description one line of "why this exists", shown only when it adds
 *        information the title cannot carry.
 * @param trailing optional section-level action (e.g. "Reset").
 * @param collapsible make the whole band fold. **The title and its summary stay visible**, so a
 *        folded section never hides the fact it is showing — only its detail. Use this on the
 *        large bands, where scrolling past ten rows to reach the next card is the real cost.
 * @param summary the one line that stays readable while folded («10 bands», «Not available»).
 *        It replaces `description` while folded, so nothing is lost by folding.
 */
@Composable
fun MaxSection(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    collapsible: Boolean = false,
    summary: String? = null,
    initiallyExpanded: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val open = !collapsible || expanded
    val animationsEnabled = rememberAnimationsEnabled()
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(MaxDuration.quick),
        label = "sectionExpandRotation"
    )
    // والوصف **يتبدّل بالحالة لا يُحذف:** المطويّ يعرض السطر الذي يكفي ليُقرأ دون فتح.
    val descriptionText = if (collapsible && !expanded) summary ?: description else description
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaxSpace.md)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (collapsible) {
                        Modifier.clickable(role = Role.Button) { expanded = !expanded }
                    } else {
                        Modifier
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaxSpace.md)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaxSpace.xs)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )
                descriptionText?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            trailing?.invoke()
            if (collapsible) {
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(if (animationsEnabled) rotation else (if (expanded) 180f else 0f)),
                )
            }
        }
        if (collapsible && animationsEnabled) {
            AnimatedVisibility(
                visible = open,
                enter = expandVertically(tween(MaxDuration.standard)) + fadeIn(tween(MaxDuration.standard)),
                exit = shrinkVertically(tween(MaxDuration.quick)) + fadeOut(tween(MaxDuration.quick)),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(MaxSpace.md),
                    content = content
                )
            }
        } else {
            if (open) {
                content()
            }
        }
    }
}

/**
 * Hairline container for related rows. One border around many rows instead of
 * one card per row: fewer edges, faster scanning, less vertical waste.
 */
@Composable
fun MaxGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    // **الترحيل إلى القشرة (الدفعة الأولى من الترحيل التدريجي):** شكل + خلفية + حدّ + قصّ
    // كانت مكتوبة بيد صاحبها، وصارت من العقد.
    // و`contentPadding = 0.dp` و`Arrangement.Top` **ليسا تفريطًا** بل حفظ حرفيّ لما كان:
    // الحشو هنا **رأسيّ فقط** (`groupPadding`) ولا تعبّر عنه معلمة واحدة، والفراغ بين الصفوف
    // يرسمه فصل داخلي. فلو تُرك الافتراضيّ لصار الحشو `MaxSpace.lg` من الجهات الأربع
    // **ولظهر بين كل صفّين فراغ 8dp لم يكن** — أي أن الترحيل يغيّر شكل المجموعة بدل أن ينظّفها.
    MaxCardShell(
        modifier = modifier.fillMaxWidth(),
        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = MaxAlpha.border),
        contentPadding = 0.dp,
        verticalArrangement = Arrangement.Top,
    ) {
        Column(modifier = Modifier.padding(vertical = MaxSpace.groupPadding), content = content)
    }
}

/**
 * مجموعةٌ **منطوية**: عنوانٌ وسطرُ حالة يبقيان ظاهرين دائمًا، والمحتوى يُطوى ويُفتح بلمسة.
 *
 * **ولماذا هذا ليس ترفًا:** الشاشة التي تُظهر كلّ شيء دائمًا لا تُظهر شيئًا — كان على المستخدم أن
 * يمرّ على عشرات الصفوف ليصل إلى ما يبحث عنه. والطويّ **لا يُخفي حالةً**: العنوان يبقى ومعه
 * سطرُ حالته، فالمهمّ يُقرأ دون فتح، والتفصيل يُفتح عند الحاجة.
 *
 * **والعقد الذي لا يُكسر:** المحتوى المطويّ **يظل محسوبًا في التخطيط** ولا يُزال من الشجرة، فلا
 * تتغيّر حالةٌ مقروءة عند الطيّ، ولا يُعاد بناء قسمٍ كلّما فُتح. والحالة تعيش في `rememberSaveable`
 * فلا تعود مطويّةً بعد تدوير الجهاز.
 *
 * @param summary سطرُ الحالة الذي يبقى ظاهرًا وهو المطويّ — **وليس نصّا تزيينيًّا**: عليه أن يقول
 *   ما يكفي ليُقرأ دون فتح («١٠ نطاقات» · «غير متاح»).
 */
@Composable
fun MaxCollapsibleGroup(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val animationsEnabled = rememberAnimationsEnabled()
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(MaxDuration.quick),
        label = "collapsibleGroupRotation"
    )
    MaxCardShell(
        modifier = modifier.fillMaxWidth(),
        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = MaxAlpha.border),
        contentPadding = 0.dp,
        verticalArrangement = Arrangement.Top,
    ) {
        Column {
            MaxRow(
                title = title,
                subtitle = summary,
                onClick = { expanded = !expanded },
                trailing = {
                    // أيقونةٌ زخرفيّة: المعنى يحمله العنوان وسطر الحالة (وهو عقد هذا الملفّ)،
                    // ودور الزرّ يمنحه `MaxRow` نفسه فلا يبقى عنوانٌ بلا قابلية نقر.
                    Icon(
                        imageVector = Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.rotate(if (animationsEnabled) rotation else (if (expanded) 180f else 0f)),
                    )
                },
            )
            if (animationsEnabled) {
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(tween(MaxDuration.standard)) + fadeIn(tween(MaxDuration.standard)),
                    exit = shrinkVertically(tween(MaxDuration.quick)) + fadeOut(tween(MaxDuration.quick)),
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = MaxSpace.groupPadding),
                        content = content,
                    )
                }
            } else {
                if (expanded) {
                    Column(
                        modifier = Modifier.padding(vertical = MaxSpace.groupPadding),
                        content = content,
                    )
                }
            }
        }
    }
}

/** Separator between rows of the same group. Inset to align with row text. */
@Composable
fun MaxGroupDivider(modifier: Modifier = Modifier, inset: Boolean = true) {
    HorizontalDivider(
        modifier = modifier.padding(
            start = if (inset) MaxSpace.rowPaddingHorizontal else 0.dp,
            end = if (inset) MaxSpace.rowPaddingHorizontal else 0.dp
        ),
        thickness = MaxSize.hairlineBorder,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = MaxAlpha.border)
    )
}

/**
 * One row: a fact, a setting, or a destination.
 *
 * Accessibility contract enforced here so no screen can regress it:
 * - minimum 48dp height
 * - clickable rows expose Role.Button
 * - the icon is decorative; the text carries the meaning
 */
@Composable
fun MaxRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTone: MaxTone = MaxTone.Neutral,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val animationsEnabled = rememberAnimationsEnabled()
    val clickModifier = if (onClick != null && enabled) {
        Modifier
            .then(if (animationsEnabled) Modifier.maxPressMotion(interaction, pressedScale = 0.985f) else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
    } else {
        Modifier
    }
    val contentAlpha = if (enabled) 1f else MaxAlpha.disabledContent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier)
            .heightIn(min = MaxSize.minTouchTarget)
            .padding(
                horizontal = MaxSpace.rowPaddingHorizontal,
                vertical = MaxSpace.rowPaddingVertical
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.md)
    ) {
        if (icon != null) {
            Surface(
                shape = RoundedCornerShape(MaxRadius.control),
                color = iconTone.container(),
                modifier = Modifier.size(MaxSize.rowIconContainer)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTone.content().copy(alpha = contentAlpha),
                        modifier = Modifier.size(MaxSize.iconGlyphSmall)
                    )
                }
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MaxSpace.hairline)
        ) {
            // `LineBreak.Heading` keeps a long single word from being split into
            // individual letters when the row is narrow; the overflow guard then
            // truncates it instead. See MaxControlRows.kt for the full note.
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(lineBreak = LineBreak.Heading),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall.copy(lineBreak = LineBreak.Heading),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * Segmented control for 2–4 mutually exclusive, instantly-applied choices
 * (profile, scope, time window).
 *
 * Hand-built rather than using the experimental Material segmented button so
 * it can carry MaxManager's shape/tone language and correct selection
 * semantics without pulling an unstable API into 40 screens.
 */
@Composable
fun MaxSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (options.isEmpty()) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MaxRadius.row),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            MaxSize.hairlineBorder,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = MaxAlpha.border)
        )
    ) {
        Row(
            modifier = Modifier.padding(MaxSpace.xs),
            horizontalArrangement = Arrangement.spacedBy(MaxSpace.xs)
        ) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val background = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = MaxAlpha.toneContainerStrong)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                }
                val labelColor = when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                        .copy(alpha = MaxAlpha.disabledContent)
                    selected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .selectable(
                            selected = selected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { onSelect(index) }
                        )
                        .heightIn(min = 40.dp),
                    shape = RoundedCornerShape(MaxRadius.control),
                    color = background
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = labelColor,
                            modifier = Modifier.padding(
                                horizontal = MaxSpace.sm,
                                vertical = MaxSpace.sm
                            )
                        )
                    }
                }
            }
        }
    }
}
