/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * بطاقات السعة في الرئيسية: **مصفوفة الذاكرة** و**بطاقة التنظيف** ورموز وضع الوصول.
 *
 * أُخرجت من `LegendaryHomeDashboard.kt` لسقف حجم الملفّ (`tools/code_health.py`)، ولسبب
 * أصدق من العدد: هذه الثلاثة تقرأ **سعة** (كم بقي، ما يمكن تحريره، أي طبقة امتياز تعمل)
 * بينما البقية في تلك الشاشة تقرأ **حالة آنية** (حرارة، تردّد، نشاط). والفصل يجعل الحدّ
 * مقروءًا: من عدّل مصفوفة الذاكرة لم يلمس موجة التردّد.
 *
 * **والنقل نقل لا إعادة تصميم:** النصوص والشروط والعتبات كما كانت حرفيًّا، والوظائف صارت
 * `internal` ليراها الملفّان، ولم يُغيَّر رقم واحد.
 */
package nd.max.ui.mainscreens

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import nd.max.R
import nd.max.core.privilege.PrivilegeLevel
import nd.max.ui.component.NeuralCaption
import nd.max.ui.component.NeuralPalette
import nd.max.ui.component.NeuralPanel
import nd.max.ui.component.NeuralIconChip
import nd.max.ui.component.NeuralSectionHeader
import nd.max.ui.component.NeuralTile
import nd.max.ui.component.NeuralTrack
import nd.max.ui.component.NeuralValue
import nd.max.ui.component.neuralPalette
import nd.max.ui.design.MaxSpace
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.subscreens.BUSY_FRACTION
import nd.max.ui.subscreens.DANGER_FRACTION
import nd.max.ui.theme.MonoValueStyleSmall
import nd.max.ui.viewmodel.DashboardState

/*
 * رموز طبقة الوصول — ثلاثة رموز لكل شيء، فلا نصّ محليّ مكتوب في الرسم.
 *
 * **والاسم من سجلّ الصلاحيات** (`PrivilegeLevel.labelRes`) لطبقتَي الجذر والشيزوكو، لأنهما
 * مسمّيتان في المستودع أصلًا؛ و«أساسي» رمز جديد لأن `PrivilegeLevel.NONE` عنوانه «بلا امتياز»
 * — وهو وصف لغياب لا اسم لوضع، والخطة تسمّي هذا الوضع `Basic` (§3.2 و§16.1).
 */
@StringRes
internal fun accessLabelRes(level: PrivilegeLevel): Int = when (level) {
    PrivilegeLevel.ROOT -> R.string.max_privilege_level_root
    PrivilegeLevel.SHIZUKU -> R.string.max_privilege_level_shizuku
    PrivilegeLevel.NONE -> R.string.home_access_basic
}

/** لون الشارة: الإيجابي للجذر، والأكسنت البديل لشيزوكو، والرمادي للأساسي. */
internal fun accessAccent(level: PrivilegeLevel, p: NeuralPalette) = when (level) {
    PrivilegeLevel.ROOT -> p.ok
    PrivilegeLevel.SHIZUKU -> p.accentAlt
    PrivilegeLevel.NONE -> p.muted
}

/**
 * بطاقة التنظيف في الرئيسية — سعة + باب + زرّ فعل (عقد §6.1).
 *
 * **شكلها:** رأس (أيقونة · الاسم · `98% used` بلون السعة) ثم شريط **سميك** يُقرأ من بعيد، ثم
 * سطر «المستخدَم من الإجمالي · المتاح»، ثم جملة الصدق (**الكاش أولًا، ملفاتك فقط إذا أشّرت**)،
 * ثم زرّ `Clean` بعرض البطاقة. وكان الزرّ حبّة صغيرة في آخر البطاقة، فلا تُقرأ كفعل.
 *
 * **وهي لا تقيس شيئًا هنا، عن قصد:** قياس الفئات (كاش التطبيقات والصور المصغّرة وسجلات النظام)
 * مسحٌ يطول ويحتاج صلاحية، والخطة تنصّ أنه يجري **عند فتح الشاشة** («عند فتح الشاشة: مسح خلفي
 * يقيس البايتات»). فالبطاقة تعرض ما هو مقروء الآن في هذه اللحظة — سعة القرص — وزرّها لا يحمل رقم
 * «يمكن تحريره» لم يُقس بعد، فلا تقدير في المكان الذي وُعد بأنه قياس.
 *
 * **وعتبتا اللون مستعارتان من مالك السعة** (`DANGER_FRACTION`/`BUSY_FRACTION` في
 * `StorageDetailScreen`) لا مكتوبتان هنا: سعةٌ واحدة بحكمان تفترق يومًا.
 */
@Composable
internal fun UltraCleanerHomeCard(dashboard: DashboardState, onNavigate: (String) -> Unit) {
    val p = neuralPalette()
    val total = dashboard.storageTotalGb
    val used = dashboard.storageUsedGb
    val free = (total - used).coerceAtLeast(0f)
    val fraction = if (total > 0f) (used / total).coerceIn(0f, 1f) else null
    val percent = fraction?.let { (it * 100f).roundToInt() }
    val accent = when {
        fraction == null -> p.accent
        fraction >= DANGER_FRACTION -> p.danger
        fraction >= BUSY_FRACTION -> p.warn
        else -> p.accent
    }
    val open = { onNavigate(MaxDestination.UltraCleaner.route) }

    NeuralPanel(accent = accent, onClick = open, verticalSpacing = MaxSpace.md) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NeuralIconChip(Icons.Rounded.CleaningServices, accent, size = 40.dp)
            Spacer(Modifier.width(MaxSpace.md))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MaxSpace.hairline)) {
                Text(
                    stringResource(R.string.ultra_cleaner_title),
                    color = p.text,
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                NeuralCaption(stringResource(R.string.ultra_cleaner_tagline))
            }
            if (percent != null) {
                Spacer(Modifier.width(MaxSpace.sm))
                Text(
                    stringResource(R.string.home_cleaner_percent_used, percent),
                    color = accent,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
        if (fraction == null) {
            // مجهول لا صفر: شريط فارغ مع «0%» يُقرأ «القرص فارغ» وهو عكس الخبر أصلًا.
            Text(
                stringResource(R.string.max_home_unavailable),
                color = p.muted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        } else {
            NeuralTrack(fraction, accent, height = 10.dp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                NeuralCaption(
                    stringResource(
                        R.string.ultra_cleaner_used_of,
                        "${used.oneDecimal()} GB",
                        "${total.oneDecimal()} GB",
                    ),
                    modifier = Modifier.weight(1f),
                )
                NeuralCaption(
                    stringResource(R.string.ultra_cleaner_free, "${free.oneDecimal()} GB"),
                    color = accent,
                )
            }
        }
        Text(
            stringResource(R.string.ultra_cleaner_intro),
            color = p.muted,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
        HomeActionButton(
            text = stringResource(R.string.home_cleaner_action),
            icon = Icons.Rounded.CleaningServices,
            filled = true,
            accent = accent,
            onClick = open,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * مصفوفة الذاكرة — سعة RAM وZRAM والتخزين الداخلي في بطاقة واحدة.
 *
 * **والفعل ليس هنا:** كان للبطاقة صفّ Boost يُنفّذ ويقيس، ثم صار زرّ **Boost** مالكَه الوحيد في
 * `HomeActionRow` فوق — فبقاء الزرّ في الموضعين كان سيرسم فعلًا واحدًا مرّتين على شاشة واحدة.
 * وكل صفّ هنا بابٌ إلى شاشة مالكة (مركز الذاكرة · مدير ZRAM · تفصيل التخزين)، وبطاقة التنظيف
 * تحتها فعل التخزين. ونتيجة آخر Boost تُكتب تحت زرّها لا هنا.
 *
 * **ما هي وما ليست:** تعرض **حقائق سعة** (المستخدَم من الإجمالي، والمتاح)، ولا تُصدر حُكم ضغط.
 * وهذا ليس تحفّظًا شكليًّا: `ADR-34` يقرّر أن ضغط الذاكرة يُقاس بPSI لا بنسبة الامتلاء،
 * وأجهزة بنسبة امتلاء متقاربة تختلف في أثرها على الأداء اختلافًا كبيرًا. فالحُكم في هذه الشاشة
 * يبقى في `FocusCard` وحدها، وهذه البطاقة تجيب السؤال الآخر: «كم بقي؟».
 *
 * **ولذلك لا عتبات ولا ألوان إنذار هنا:** لون كلّ صفّ هوية (الأزرق/التركوا/الثانوي) لا حكم،
 * والمقارنة تكفلها الأشرطة والرقم المكتوب. ولون تحذير مستحدث هنا يعني عتبة امتلاء هي بالضبط
 * ما نهى عنه ADR-34 — والقارئ ينسى أن العتبة أُضيفت في الواجهة.
 *
 * **والمصادر أوعية موجودة، لا أوعية جديدة:** الأرقام من نفس لقطة اللوحة، والإجراءات إلى
 * الشاشتين المالكين للرقم (`ZramManager` · `StorageDetail`) — ولهذا صار كل صفّ قابلًا للنقر
 * بذاته: نقر بطاقة كاملة كان سيوصل صفّ التخزين إلى شاشة الذاكرة، وهي كذبة صغيرة.
 *
 * **وما لم يُقرأ لا يُصاغ:** غياب التبديل أو إجمالي الذاكرة يُكتب نصًّا («غير متاح»)
 * وبلا شريط، لا أحد عشرًا صفرًا ولا شريطًا فارغًا يُقرأ كـ«فارغ».
 */
@Composable
internal fun MemoryMatrixCard(
    dashboard: DashboardState,
    onNavigate: (String) -> Unit,
) {
    val p = neuralPalette()
    val ramTotal = dashboard.ramTotalMb
    val ramUsed = dashboard.ramUsedMb
    val swapTotal = dashboard.swapTotalMb
    val swapUsed = dashboard.swapUsedMb
    val storageTotal = dashboard.storageTotalGb
    val storageFree = (storageTotal - dashboard.storageUsedGb).coerceAtLeast(0f)

    NeuralPanel(accent = p.accent) {
        NeuralSectionHeader(
            title = stringResource(R.string.home_memory_storage),
            caption = stringResource(R.string.home_memory_storage_desc),
            accent = p.accent,
        )

        MemoryFactRow(
            label = stringResource(R.string.ram_label),
            detail = if (ramTotal > 0) "${gigabytes(ramUsed)} / ${gigabytes(ramTotal)}" else null,
            status = if (ramTotal > 0) {
                stringResource(R.string.home_available_memory, gigabytes(ramTotal - ramUsed))
            } else {
                stringResource(R.string.max_home_unavailable)
            },
            fraction = if (ramTotal > 0) fractionOf(ramUsed, ramTotal) else null,
            accent = p.accent,
            // صفّ RAM كان يفتح **مدير ZRAM** — عطب مقصود (نسخ الصفّ المجاور) لا خيار: من
            // يضغط «RAM» يسأل عن الذاكرة العشوائية، ومدير ZRAM شاشةٌ أخرى. الصحيح مركز
            // الذاكرة (`MemoryHub`) الذي يضمّ RAM وZRAM معًا؛ وصفّ ZRAM تحت يبقى على مديره.
            onClick = { onNavigate(MaxDestination.MemoryHub.route) },
        )

        MemoryFactRow(
            label = stringResource(R.string.home_memory_swap_label),
            detail = if (swapTotal != null && swapTotal > 0 && swapUsed != null) {
                "${gigabytes(swapUsed)} / ${gigabytes(swapTotal)}"
            } else {
                null
            },
            status = if (swapTotal != null && swapTotal > 0 && swapUsed != null) {
                stringResource(R.string.home_available_swap, gigabytes(swapTotal - swapUsed))
            } else {
                stringResource(R.string.home_zram_unavailable)
            },
            fraction = if (swapTotal != null && swapTotal > 0 && swapUsed != null) {
                fractionOf(swapUsed, swapTotal)
            } else {
                null
            },
            accent = p.accentAlt,
            onClick = { onNavigate(MaxDestination.ZramManager.route) },
        )

        MemoryFactRow(
            label = stringResource(R.string.home_internal_storage),
            detail = if (storageTotal > 0f) {
                "${dashboard.storageUsedGb.oneDecimal()} / ${storageTotal.oneDecimal()} GB"
            } else {
                null
            },
            status = if (storageTotal > 0f) {
                stringResource(R.string.home_available_storage, storageFree.oneDecimal())
            } else {
                stringResource(R.string.max_home_unavailable)
            },
            fraction = if (storageTotal > 0f) {
                (dashboard.storageUsedGb / storageTotal).coerceIn(0f, 1f)
            } else {
                null
            },
            accent = p.ok,
            onClick = { onNavigate(MaxDestination.StorageDetail.route) },
        )
    }
}

/**
 * صفّ سعة واحد: اسم الوعاء · المستخدَم/الإجمالي · المتاح · شريط.
 *
 * والمتاح هو السطر الأبرز لأنه سؤال المستخدم فعلًا («كم بقي؟»)، والمستخدَم/الإجمالي يبقى
 * بجانب الاسم لأن بدون إجمالي لا يُقرأ المتاح على أنه كثير أو قليل.
 */
@Composable
private fun MemoryFactRow(
    label: String,
    detail: String?,
    status: String,
    fraction: Float?,
    accent: Color,
    onClick: () -> Unit
) {
    val p = neuralPalette()
    NeuralTile(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        verticalSpacing = 6.dp,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(accent))
                Spacer(Modifier.width(7.dp))
                NeuralCaption(label)
            }
            if (detail != null) {
                NeuralValue(
                    detail,
                    style = MonoValueStyleSmall.copy(fontSize = 12.sp),
                    color = p.text
                )
            }
            // السهم في نهاية السطر: هذا الصفّ **بابٌ** لا بيان (`NeuralTile(onClick)` يقود
            // إلى وجهة مختلفة لكل صفّ: مركز الذاكرة · مدير ZRAM · تفصيل التخزين) — وكان
            // يُقرأ رقمًا وبطاقة فحسب، وهو نفس العطب الذي أبلغ عنه المالك في وسم Max AI
            // («لا يدل على أنه سيدخلك إلى شاشة أخرى»)، مُقاسًا هنا في ثلاثة صفوف معًا.
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                Modifier.size(15.dp),
                tint = accent,
            )
        }
        Text(status, color = accent, fontSize = 11.sp, lineHeight = 15.sp)
        fraction?.let { NeuralTrack(it, accent.copy(alpha = .85f), height = 5.dp) }
    }
}
