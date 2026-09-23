/**
 * بطاقات الإحصاءات وصفوف المصفوفات — العائلات (ب) و(ج) و(د) من `docs/ai/ui-ux-spec.md` §8.
 *
 * وهذه هي المكوّنات الثلاثة التي وُجدت في الصور المرجعية الثلاث الأغنى محتوى ولم تكن عندنا:
 *
 *  - **بطاقة إحصاء بمخطط** (`Monthly Activity` في `3.jpg` · `Statistics` في `1.jpg`/`4.jpg`):
 *    عنوان + مخطط تاريخي + صفوف تلخيص. والمخطط **يُبنى فوق أدوات المكتبة الأصلية**
 *    (`NeuralAreaPlot`) — فقد كانت مهجورة بلا مستهلك، واليوم تجد مستهلكها
 *    الوحيد الصادق: تاريخ مقيس محفوظ (`LoadHistory` بـ36 عيّنة مع طابعها الزمني).
 *  - **صفوف التصنيفات بأشرطة التقدّم** (`Categories` في `5.jpg`): اسم + قيمة + شريط نسبته.
 *  - **صف المعلومة** (`Free: 180.3 GB, Total: 226.5 GB` في `image.jpg`): اسم صغير + قيمة
 *    تقنية LTR — وهو ما ستتحول إليه عشرات الصفوف المكرّرة في ٦٩ شاشة.
 *
 * وثلاث قواعد صدق محفوظة في الثلاثة:
 *
 *  1. **`fraction = null` ≠ صفر**: الشريط يُرسم فارغًا والقيمة `—` — لا تعبئة تخترع قيمة.
 *  2. **لا لون مثبَّت**: كل الألوان تأتي من `neuralPalette()` أي من ثيم الإعدادات الذي يختاره
 *     المستخدم (لون مفتاح + AMOLED + إصدار Material) — فاللون هنا هوية المستخدم لا هوية ثابتة.
 *  3. **القيمة التقنية LTR مثبّتة** عبر [NeuralValue]، فلا يقلبها RTL.
 */
package nd.max.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nd.max.ui.theme.MonoValueStyleSmall

/**
 * صف معلومة: اسم صغير على جهة، وقيمة تقنية على الجهة الأخرى.
 *
 * وهذا هو «السطر» في لوحة معلومات الجهاز: عشرات القيم التي لا تستحق بلاطة كلها، ولا صفًّا
 * كلها. والاسم يأخذ الوزن المتبقّي فيقصَّ عند الضرورة، والقيمة لا تُقصّ أبدًا — المعلومة
 * التقنية أولاً.
 */
@Composable
fun NeuralDataRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val p = neuralPalette()
    Row(
        modifier
            .fillMaxWidth()
            .neuralClickable(onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = p.muted,
            fontSize = 10.5.sp,
            lineHeight = 14.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(10.dp))
        NeuralValue(
            value,
            style = MonoValueStyleSmall.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold),
            color = accent ?: p.text,
        )
    }
}

/**
 * صف تصنيف بشريط نسبته — «Categories» من `5.jpg`.
 *
 * والنسبة هنا **لا تحتاج مقامًا معروضًا** (المقام يُقال في القيمة نفسها)،
 * ونقطة اللون على الاسم هي ما يجعل الصفوف العشرة تُقرأ كسلسلة واحدة لا كبطاقات متناثرة.
 */
@Composable
fun NeuralCategoryRow(
    label: String,
    value: String,
    fraction: Float?,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val p = neuralPalette()
    Column(
        modifier
            .fillMaxWidth()
            .neuralClickable(onClick),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                color = p.text,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            NeuralValue(
                value,
                style = MonoValueStyleSmall.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold),
                color = if (fraction == null) p.muted else p.text,
            )
        }
        NeuralTrack(fraction ?: 0f, accent, Modifier.fillMaxWidth(), height = 5.dp)
    }
}

/**
 * Compact live metric card used for CPU/GPU and other high-frequency signals.
 * The layout follows the reference dashboards: label + large value + technical
 * secondary value + a readable trend chart, without decorative gauge chrome.
 */
@Composable
fun NeuralMetricTrendCard(
    title: String,
    value: String,
    secondaryValue: String,
    accent: Color,
    series: List<Float?>,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val p = neuralPalette()
    NeuralTile(
        modifier = modifier,
        accent = accent,
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 11.dp),
        verticalSpacing = 7.dp,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                color = p.text,
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            NeuralValue(
                value,
                style = nd.max.ui.theme.MonoValueStyleSmall.copy(fontSize = 24.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold),
                color = if (value == "—") p.muted else p.text,
            )
            NeuralValue(
                secondaryValue,
                style = nd.max.ui.theme.MonoValueStyleSmall.copy(fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold),
                color = if (secondaryValue == "—") p.muted else accent,
            )
        }

        Box(Modifier.fillMaxWidth().height(56.dp)) {
            if (series.size >= 2) {
                NeuralAreaPlot(
                    values = series,
                    accent = accent,
                    maxValue = 100f,
                    adaptive = false,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                )
            }
        }
    }
}

/** Compact capacity widget: large percentage, used/total line and progress track. */
@Composable
fun NeuralCapacityCard(
    title: String,
    value: String,
    detail: String,
    fraction: Float?,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val p = neuralPalette()
    NeuralTile(
        modifier = modifier,
        accent = accent,
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        verticalSpacing = 7.dp,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                color = p.text,
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            NeuralValue(
                value,
                style = MonoValueStyleSmall.copy(fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold),
                color = if (fraction == null) p.muted else p.text,
            )
        }
        NeuralTrack(fraction ?: 0f, accent, Modifier.fillMaxWidth(), height = 6.dp)
        NeuralValue(
            detail,
            style = MonoValueStyleSmall.copy(fontSize = 9.5.sp, lineHeight = 12.sp),
            color = p.muted,
            maxLines = 1,
        )
    }
}

/**
 * بطاقة إحصاء: ترويسة + خانة مخطط + محتوى تلخيصي.
 *
 * والخانة منفصلة عن المحتوى لأن المخطط **يُرسم من بيانات الشاشة لا من المكتبة**: كل شاشة
 * تمرّر مخططها كما هو (`NeuralAreaPlot` أو أي مخطط زمني تملكه)، فتبقى المكتبة بلا افتراض عن
 * نوع السلاسل الزمنية التي يملكها كل مقياس.
 */
@Composable
fun NeuralStatCard(
    title: String,
    accent: Color,
    modifier: Modifier = Modifier,
    caption: String? = null,
    badge: String? = null,
    chart: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    NeuralPanel(modifier, accent = accent, verticalSpacing = 12.dp) {
        NeuralSectionHeader(
            title = title,
            caption = caption,
            accent = accent,
            trailing = if (badge == null) null else {
                { NeuralPill(text = badge, accent = accent, filled = true) }
            },
        )
        if (chart != null) chart()
        content()
    }
}
