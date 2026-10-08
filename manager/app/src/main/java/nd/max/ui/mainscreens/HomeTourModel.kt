/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * جولة أول فتح — **شرحٌ خطوةً خطوة فوق عناصر الشاشة الحقيقية** (عقد §3.4 من خطة المستوى).
 *
 * الفرق عن البانرات: البانرات تقول **ما هو Max** (صفحات عامّة تتحرك وحدها)، والجولة تقول **أين
 * أجد كل شيء هنا** بإضاءة العنصر نفسه وتعتيم ما حوله. فالأولى تُقرأ، والثانية تُلمس.
 *
 * **النموذج صافٍ بلا Compose:** الخطوات والترتيب هنا، والقياس والرسم في `HomeTourOverlay`، فيبقى
 * الترتيب مقيسًا على JVM وتبقى الأيقونة واللون من لغة التصميم لا من الموديل.
 *
 * **كل خطوة تشير إلى مرساة** (`HomeTourTarget`) تعلّمها الرئيسية بـ`homeTourTarget(...)`؛ وخطوة بلا
 * مرساة تُضيء لا شيء ولذلك يحرس `HomeShapeContractTest` أن لكل هدف علامةً في الشاشة.
 */
package nd.max.ui.mainscreens

import androidx.annotation.StringRes
import nd.max.R

/** مواضع الرئيسية التي تستطيع الجولة إضاءتها — كلٌّ منها كتلة واحدة في `LegendaryHomeDashboard`. */
internal enum class HomeTourTarget { Hero, Vitals, Actions, Pulse, Memory, Cleaner, Deck }

/** خطوات الجولة بترتيبها، كلٌّ بعنوان وجملة صدق واحدة ومرساتها. */
internal enum class HomeTourStep(
    val target: HomeTourTarget,
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
) {
    /** أوّلًا: ما هذا الجهاز وأي صلاحية يحملها Max عليه — لأنها سبب انقفال نصف التطبيق. */
    Identity(HomeTourTarget.Hero, R.string.home_tour_identity_title, R.string.home_tour_identity_body),

    /** القراءات الحيّة وأنها أبواب. */
    Vitals(HomeTourTarget.Vitals, R.string.home_tour_vitals_title, R.string.home_tour_vitals_body),

    /** الفعلان السريعان. */
    Actions(HomeTourTarget.Actions, R.string.home_tour_actions_title, R.string.home_tour_actions_body),

    /** تاريخ التردد (بطاقة CPU/GPU). */
    Pulse(HomeTourTarget.Pulse, R.string.home_tour_pulse_title, R.string.home_tour_pulse_body),

    /** السعات. */
    Memory(HomeTourTarget.Memory, R.string.home_tour_memory_title, R.string.home_tour_memory_body),

    /** التنظيف وما لا يفعله. */
    Cleaner(HomeTourTarget.Cleaner, R.string.home_tour_cleaner_title, R.string.home_tour_cleaner_body),

    /** الاختصارات الشخصية. */
    Deck(HomeTourTarget.Deck, R.string.home_tour_deck_title, R.string.home_tour_deck_body),
}

internal object HomeTourModel {
    val steps: List<HomeTourStep> = HomeTourStep.entries.toList()

    val count: Int get() = steps.size

    /** الفهرس بعد خطوة إلى الأمام، ويثبت عند الأخيرة (الإتمام قرار الواجهة لا الموديل). */
    fun next(index: Int): Int = (index + 1).coerceAtMost(steps.lastIndex)

    /** الفهرس بعد خطوة إلى الخلف، ويثبت عند الأولى. */
    fun previous(index: Int): Int = (index - 1).coerceAtLeast(0)

    fun isLast(index: Int): Boolean = index >= steps.lastIndex
}
