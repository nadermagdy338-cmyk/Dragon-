/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 *
 * MaxManager proprietary source. See LICENSE at the repository root: this file is
 * MaxManager-owned and carries no third-party licence obligations.
 */

package nd.max.ui.overlay

/**
 * رياضيات موضع نافذة التراكب — **نقيّة وبلا Android**، فتُقاس بلا جهاز.
 *
 * **ولماذا خرجت من `OverlayWindow`:** كانت داخل دوالّ خاصّة تلمس `params` و`View` و`WindowManager`،
 * فلا سبيل لاختبارها إلا بجهاز أو Robolectric. وهي أخطر منطق في التراكب: نافذة تخرج عن الشاشة
 * **لا تُسحَب عودة** على أغلب الأجهزة، وحساب حافة خاطئ يعني تراكبًا مفقودًا بلا أيّ إشارة.
 * خطة `GAME-SPACE-PLAN.md` §GS-03 تطلب صراحةً «اختبارات لرياضيات الموضع/الحدود».
 *
 * **والحدّ مُعلَن:** كل القيم بكسل صحيح في إحداثيّات النافذة (`WindowManager.LayoutParams.x/y`)،
 * وهي **غير سالبة أصلًا** لأن `x/y` في `LayoutParams` تُقاس من أعلى-يسار الشاشة بلا سالب. لذلك
 * [clamp] لا يقبل سالبًا ولا يُنتجه.
 */
object OverlayGeometry {

    /**
     * أقصى `x` مسموح: عرض الشاشة ناقص عرض النافذة، **ولا ينزل تحت الصفر**.
     *
     * ونافذة أعرض من الشاشة (`width > screenW`) تعطي صفرًا لا سالبًا — وهذا هو الفرق بين نافذة
     * ملتصقة بالحافة اليسرى ونافذة مُزاحة خارجها بلا رجعة.
     */
    fun maxOffset(screen: Int, size: Int): Int = (screen - size).coerceAtLeast(0)

    /**
     * يحبس الإزاحة داخل الشاشة. النافذة التي خرجت لا تُسحَب عودةً في أغلب الأجهزة.
     *
     * **والقياس الصفريّ (`size <= 0`) يُعيد الإزاحة كما هي** لأن [maxOffset] يعطي عرض الشاشة
     * كاملًا: المدى `[0, screen]` لا يمسّ إزاحة مشروعة، فلا تقفز نافذة لم تُقس بعد إلى الزاوية.
     */
    fun clamp(offset: Int, screen: Int, size: Int): Int = offset.coerceIn(0, maxOffset(screen, size))

    /**
     * الحافة الأقرب أفقيًّا: `0` لليسار، أو أقصى إزاحة لليمين.
     *
     * والقرار بمركز النافذة لا بحافّتها — نافذة تجاوز مركزها منتصف الشاشة تُسحَب إلى الحافة
     * الأخرى، وهو ما يتوقّعه الإصبع.
     *
     * **والعرض الصفريّ (`width <= 0`) يُعيد الإزاحة كما هي:** نافذة لم تُقس بعد (`view.width` قبل
     * أوّل تخطيط) لا مركز لها، فسحبها إلى حافة يكون تخمينًا. وهذا حال حقيقيّ: الالتقاط يقع قبل
     * `onGlobalLayout` أحيانًا.
     */
    fun snapTarget(offset: Int, screen: Int, size: Int): Int {
        if (size <= 0) return offset
        val far = maxOffset(screen, size)
        return if (offset + size / 2 < screen / 2) 0 else far
    }
}
