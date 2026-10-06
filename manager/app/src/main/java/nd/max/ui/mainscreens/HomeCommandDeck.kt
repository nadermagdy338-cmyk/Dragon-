/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * «منصة التحكم» — بطاقات الرئيسية التي تُفتح بضغطة.
 *
 * أُخرجت من `LegendaryHomeDashboard.kt` لسقف حجم الملفّ (`tools/code_health.py`).
 *
 * **وصارت بيانات لا أربع بطاقات مكتوبة (أمر المالك، الجولة ٢٠٢):** «عناصر التحكم هنخليها ٤
 * خيارات ثابتين لو لسه مستخدم جديد، ونضيف زرّ إعداد في نفس البطاقة: خيار بيضع أكثر ما يستخدمه
 * المستخدم تلقائيًّا، وخيار يدوي يختار فيه — إلى ٦ خيارات أقصى شيء، والافتراضي ٤ وتلقائي».
 *
 * فالملفّ صار **رسمًا فقط**: البطاقات تأتيه من [homeDeckSelection] (‏٤ إلى ٦، بترتيب
 * الأكثر استعمالًا أو باختيار المستخدم)، وزرّ الإعداد في **رأس هذا القسم نفسه** لا في شريط
 * الشاشة: من يريد تغيير ما يعرضه هذا القسم يجده فيه، وهذا نصّ الأمر («في نفس البطاقة»).
 *
 * **وقرارات سابقة باقية كما هي** (ولا تُنقض بالأمر الجديد):
 * - البطاقة الأولى «العرض» بدل «ملف الأداء» الذي أُزيل بأمر المالك — والمُزال كان **مدخلًا
 *   مكرّرًا** لا قدرة: تبديل الملف قائم في لوحة الحكم في الرئيسية (`VerdictPanel` ←
 *   `profileRequest`) وفي بلاطة الإعدادات السريعة — وكان قسم Max AI (`ProfilesSection`)
 *   مدخلًا ثالثًا، فأُزيل أيضًا بأمر المالك (`MAXAI-CONTROLS-TRIM-01`). والمستبدل مجال لا
 *   يُوصَل إليه من هذه الشاشة من مدخل آخر.
 * - وكانت أربع بطاقات في صفّين مستقلّين بلا عقد ارتفاع، فاختلفت مواضع العناوين بينها؛ وهي الآن
 *   شبكة واحدة بارتفاع واحد لكل صفّ، وعمودان لا أربعة (`maxColumns = 2`) بأمر المالك.
 * - ونصوص البطاقات **نصوص الشاشات نفسها** (`display_studio_title` · `max_hub_*` · `home_action_*`)
 *   لا نصوص ثانية لها: اسم واحد للشيء الواحد، والتغطية في ٨٤ لغة قائمة.
 *
 * **وبمقاس متوسّط بأمر المالك (الجولة ٢٠٧):** «اجعل بطاقة منصة التحكم بحجم متوسّط ليست كبيرة
 * وليست صغيرة بل مناسب أكثر». والقرار **وسيط يُمرَّر** (`size = MaxCardSize.Medium`) لا أرقام
 * تُكتب هنا: الأربعة القابلة للتبدّل (الحشو، حاوية الأيقونة، الأيقونة، أرضية الارتفاع) تعيش في
 * `MaxCardMetrics` وحده، والمقاس يختار مجموعةً منها — فلو نُسخت هنا لصارت للبطاقة نسختان.
 * **والمتوسّط لا يمسّ العقد** الذي بُني لأجله هذا الملفّ: عمودان، وارتفاع واحد للصفّ، وسطران
 * محجوزان للعنوان — فالنصّ لا يُقصّ بحجة أن الشبكة صغرت.
 */
package nd.max.ui.mainscreens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import nd.max.R
import nd.max.ui.component.NeuralPanel
import nd.max.ui.component.NeuralSectionHeader
import nd.max.ui.component.neuralPalette
import nd.max.ui.design.MaxCardData
import nd.max.ui.design.MaxCardGrid
import nd.max.ui.design.MaxCardSize

@Composable
internal fun CommandDeck(
    entries: List<HomeDeckEntry>,
    onOpen: (HomeDeckEntry) -> Unit,
    onConfigure: () -> Unit,
) {
    val palette = neuralPalette()
    NeuralPanel {
        NeuralSectionHeader(
            title = stringResource(R.string.home_quick_actions),
            caption = stringResource(R.string.home_quick_actions_desc),
            accent = palette.accentAlt,
            trailing = {
                IconButton(onClick = onConfigure) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = stringResource(R.string.home_deck_settings_cd),
                        tint = palette.muted,
                    )
                }
            },
        )
        // شبكة واحدة لا صفوف مستقلّة: عقد ارتفاع واحد، وتُقلَّص الأعمدة بدل أن يُقصّ نصّ.
        MaxCardGrid(
            cards = entries.map { entry ->
                MaxCardData(
                    title = stringResource(entry.titleRes),
                    icon = entry.icon,
                    description = stringResource(entry.descriptionRes),
                    tone = entry.tone,
                    onClick = { onOpen(entry) },
                )
            },
            maxColumns = 2,
            minColumns = 2,
            // «متوسّط» بأمر المالك: بطاقة بعنوان ووصف قصيرين لا تحتاج مقاس الشاشات الكاملة
            // (حشوة ١٦ وحاوية أيقونة ٤٠ وأرضية ٩٢) — وهي أرقامها في `MaxCardMetrics.Medium`.
            size = MaxCardSize.Medium,
        )
    }
}
