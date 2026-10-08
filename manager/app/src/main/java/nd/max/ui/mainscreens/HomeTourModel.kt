/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * جولة أول فتح — **شرحٌ خطوةً خطوة فوق عناصر الشاشة الحقيقية**، بترتيب القراءة نفسه (طلب المالك).
 *
 * الترتيب هنا هو ترتيب الشاشة من أعلى إلى أسفل، فالجولة تنزل مع القارئ ولا تقفز بين أطراف الصفحة.
 * الخطوات تشير إلى مراسي (`HomeTourTarget`) تعلّمها الرئيسية بـ`homeTourTarget(...)`، ويحرس
 * `HomeShapeContractTest` أن لكل هدف علامةً في الشاشة.
 */
package nd.max.ui.mainscreens

import androidx.annotation.StringRes
import nd.max.R

/** مواضع الرئيسية التي تستطيع الجولة إضاءتها — كلٌّ منها كتلة واحدة في `LegendaryHomeDashboard`. */
internal enum class HomeTourTarget { Hero, Vitals, Actions, Pulse, Memory, Cleaner, Deck }

/** خطوات الجولة بترتيب الشاشة: البطاقة الأولى، ثم CPU/GPU، ثم الفعل، ثم القراءات الحيّة، ثم البقية. */
internal enum class HomeTourStep(
    val target: HomeTourTarget,
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
) {
    /** أوّلًا: البطاقة الأولى — ما هذا الجهاز وأي صلاحية يحملها Max عليه. */
    Identity(HomeTourTarget.Hero, R.string.home_tour_identity_title, R.string.home_tour_identity_body),

    /** ثم CPU وGPU: تاريخ التردد كما يظهر تحتهما. */
    Pulse(HomeTourTarget.Pulse, R.string.home_tour_pulse_title, R.string.home_tour_pulse_body),

    /** ثم زرّا التعزيز والتحكم. */
    Actions(HomeTourTarget.Actions, R.string.home_tour_actions_title, R.string.home_tour_actions_body),

    /** ثم القراءات الحيّة التي لا تتكرر في مكان آخر. */
    Vitals(HomeTourTarget.Vitals, R.string.home_tour_vitals_title, R.string.home_tour_vitals_body),

    /** السعات. */
    Memory(HomeTourTarget.Memory, R.string.home_tour_memory_title, R.string.home_tour_memory_body),

    /** التنظيف وما لا يفعله. */
    Cleaner(HomeTourTarget.Cleaner, R.string.home_tour_cleaner_title, R.string.home_tour_cleaner_body),

    /** منصة التحكم: الاختصارات الشخصية. */
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
