/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * جولة الرئيسية: **المحتوى والقاعدة وحدهما**، بلا رسم وبلا Compose.
 *
 * طلب المالك من `MAX-MANAGER-LEVEL-UP.md` §5.4: «بانرات التعليم على الرئيسية — 4 إلى 8 بطاقات،
 * مسح أفقي، تلقائي كل 4–6 ث، يتوقف عند اللمس»، و«تُخفى بعد إتمام الجولة أو Skip-all، مع إمكانية
 * إعادة العرض من ?».
 *
 * ⇒ فالقرارات هنا ثلاثة، كلها صافية وتُقاس على JVM:
 *
 * 1. **[HomeGuideBanner] قائمة مرتَّبة** — الترتيب هو ترتيب القراءة، وعدد الخطوات يُشتقّ من طولها
 *    (`size`) لا من رقم مكتوب بيد: إضافة شريحة أو حذفها لا تترك «الخطوة 5 من 6» تكذب.
 * 2. **الانتقال دَوريّ** في الاتجاهين ([next] / [previous])، فآخر بطاقة تعود إلى الأولى بلا زرّ
 *    معطّل — وهي جولة تعليم لا معالج إدخال، ولا شيء فيها «إلزاميّ».
 * 3. **الإتمام حدثٌ يُسجَّل** ([HomeGuideStore.finished])، و`?` في رأس الشاشة يعيد العرض
 *    ([HomeGuideStore.restart]) — وهذا نصّ العقد.
 *
 * **وما لا يفعله هذا الملفّ:** لا يخزّن خطوةً بخطوة. من أغلق الجولة أُغلق العلم، ولا يُعاد فتحه من
 * تلقائه — لأن بطاقةً تعود بعد إغلاق صريح ليست تعليمًا بل إصرارًا. وما دامت مفتوحة فالبداية من
 * الأولى في كل ظهور: الجولة تُقرأ في جلسة واحدة لا تُستأنف بترتيب لا يتذكّره المستخدم.
 *
 * ولا لون ولا أيقونة هنا: الشكل في `HomeGuideBanners.kt`، وهذه القاعدة لا تعرف `Compose`.
 */
package nd.max.ui.mainscreens

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import nd.max.R

/**
 * خطوة واحدة من الجولة: عنوان مترجم + جملة صدق واحدة.
 *
 * ولماذا لا يوجد `id` ثانٍ بجانب الاسم: الاسم نفسه هو الثابت — `SharedPreferences` تحفظ
 * «أُتمّت» فقط لا موضعًا، فلا داعي لمفتاح تخزين لكل خطوة.
 */
enum class HomeGuideBanner(@StringRes val titleRes: Int, @StringRes val bodyRes: Int) {
    /**
     * البطاقة الأولى: أين تُطبَّق الإعدادات.
     *
     * وهي أسبق من وصف أي زرّ لأنها الجواب عن السؤال الذي يُسأل قبل أول ضغطة: «هل يخرج شيء من
     * هاتفي؟» — والجواب في المستودع مقيس: لا شبكة في مسار الكتابة أصلًا.
     */
    Local(R.string.home_banner_local_title, R.string.home_banner_local_body),

    /**
     * وضع الوصول — **وهي خطوة اللقطة عند Moha بنصّ العقد** («Step 2 of 10 · Access mode»).
     *
     * وموضعها الثاني مقصود: بعد أن يعرف المستخدم أن الإعدادات تبقى هنا، يأتي السؤال الذي يفسّر
     * سبب انقفال نصف التطبيق عليه: أي طبقة امتياز يحمل الآن.
     */
    Access(R.string.home_banner_access_title, R.string.home_banner_access_body),

    /** من يقرّر: المحرّك أم أنت. */
    MaxAi(R.string.home_banner_ai_title, R.string.home_banner_ai_body),

    /** الشرط الذي يجعل كل رقم في هذا التطبيق قابلًا للتصديق. */
    Verify(R.string.home_banner_verify_title, R.string.home_banner_verify_body),

    /** ما يفعله التنظيف وما لا يفعله — عقد الملفات الشخصية قبل أن يراه المستخدم هناك. */
    Clean(R.string.home_banner_clean_title, R.string.home_banner_clean_body),

    /** أخيرًا: أين تردد الأنوية (الخطوة التي تغيّرت في هذه الجولة). */
    Cpu(R.string.home_banner_cpu_title, R.string.home_banner_cpu_body),
}

/** ترتيب الجولة — والطول هو العدد، فلا رقم خطوات مكتوب بيد. */
val HomeGuideOrder: List<HomeGuideBanner> = HomeGuideBanner.entries.toList()

object HomeGuideModel {

    /**
     * إيقاع الانتقال التلقائي.
     *
     * الخطة تقول «كل 4–6 ث»، والمقيس هنا هو ما يسمح به الوسيط: أطول جملة إنجليزية في الجولة
     * (`home_banner_verify_body`) عند 12sp تُقرأ في نحو ثلاث ثوانٍ، فخمسٌ تترك وقتًا لملاحظة
     * الانتقال نفسه. وهذا **ليس** من سقف الحركة (360ms): السقف مدّة الحركة، وهذا **مُهلة بقاء**
     * بين حركتين — خلطهما كان سيُخفي بطاقة تُقرأ في 260ms، أي لا شيء.
     */
    const val AUTO_ADVANCE_MS = 5_000L

    /** البطاقة التالية، ودَوريّة عند الأخير. */
    fun next(current: HomeGuideBanner): HomeGuideBanner =
        HomeGuideOrder[(HomeGuideOrder.indexOf(current) + 1) % HomeGuideOrder.size]

    /** البطاقة السابقة، ودَوريّة عند الأول. */
    fun previous(current: HomeGuideBanner): HomeGuideBanner =
        HomeGuideOrder[(HomeGuideOrder.indexOf(current) - 1 + HomeGuideOrder.size) % HomeGuideOrder.size]

    /** رقم الخطوة المعروض (يبدأ من ١) — مشتقّ من الترتيب لا مخزَّن. */
    fun position(current: HomeGuideBanner): Int = HomeGuideOrder.indexOf(current) + 1

    /** عدد الخطوات — مقروء من القائمة، فيتبعها إن تغيّرت. */
    val count: Int get() = HomeGuideOrder.size

    /**
     * هل يُعرض الشريط الآن؟
     *
     * شرط واحد معلَن: ألا يكون المستخدم قد أتمّه. ولا شرط ثانٍ («أول جلسة» أو «جهاز جديد») —
     * فمَن لم يره سيأتي يومًا على شاشة رئيسية بلا شرح لِما يراه، وأول تجربة هي كل ما نملك.
     */
    fun visible(finished: Boolean): Boolean = !finished
}

/**
 * تخزين الجولة: **حقيقة واحدة** — هل أُتمّت.
 *
 * وبلا هذا السجلّ كانت الجولة ستعود في كل فتح للتطبيق، وهو أسوأ من عدمها: بطاقة تُشرح مرارًا
 * تُقرأ كإعلان لا كتعليم.
 */
class HomeGuideStore(private val prefs: SharedPreferences) {

    /** هل أُتمّت الجولة (بـ`Skip` أو بالوصول إلى آخر بطاقة وضغط `Next`)؟ */
    var finished: Boolean
        get() = prefs.getBoolean(KEY_FINISHED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_FINISHED, value).apply()
        }

    /** `?` في الرأس: يُعاد العرض بطلب صريح. */
    fun restart() {
        finished = false
    }

    companion object {
        private const val PREFS = "max_home_guide"
        private const val KEY_FINISHED = "finished"

        fun of(context: Context): HomeGuideStore =
            HomeGuideStore(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))
    }
}
