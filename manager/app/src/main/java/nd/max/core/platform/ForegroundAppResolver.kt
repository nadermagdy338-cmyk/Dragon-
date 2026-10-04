/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.platform

/**
 * التطبيق الذي في المقدّمة — **قواعد صافية تُقاس على JVM**، بلا أندرويد ولا جهاز.
 *
 * **ولماذا وُجد هذا الملف.** كان `AppMonitor` يستخرج «اسم الحزمة» من أيّ كائن بالنصّ وحده:
 * يُنظَّف النصّ ثم تُقبل أوّل كلمة فيها نقطة. فقبل أن تُقبل `0.85` **اسمًا لحزمة** —
 * وهي قيمة عشريّة لا تطبيق. وقيست النتيجة في حزمة سجلّات جهاز حقيقيّ (TECNO LH8n · Android 14 ·
 * 2026-10-01): `focused_app 0.85 0 0` في `app_status`، و`APP_SWITCH pkg=0.85` في سجلّ المراقب،
 * والخادم الأصليّ يقارن هذا الحقل **حرفيًّا** بمفاتيح قائمة التطبيقات (`get_gamestart` ←
 * `strcmp`)، فلا يطابق شيئًا ⇒ **لا إدارة لتطبيق واحد**، ويبدو للمستخدم أنّ «الحاكم من داخل
 * التطبيق لا يعمل» وهو لم يجرّب أصلًا.
 *
 * فالقاعدة هنا واحدة: **اسم الحزمة يُتحقَّق من صيغته**، ولا يُقبل شيء يُشبه الرقم. والقاعدة
 * صيغة أندرويد نفسها: حرف في أوّل كل مقطع، ومقطعان على الأقلّ تفصلهما نقطة.
 *
 * وحدّها المعلن: هذه **صيغة**، لا وجود. اسم حزمة صالح الصيغة لا يعني تطبيقًا مثبَّتًا — ووجود
 * التطبيق يُقاس بعده بعملية له، وهو ما يفعله [buildAppInfo] في `AppMonitor`.
 */
object ForegroundAppResolver {

    /** حدّ أندرويد لطول اسم الحزمة. */
    private const val MAX_PACKAGE_LENGTH = 255

    /** اسم حزمة: حرف في أوّل المقطع، ونقطة بين مقطع ومقطع، ومقطعان على الأقلّ. */
    private val PACKAGE_NAME = Regex("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+")

    /** قدرٌ يكفي للتعرّف على `pkg/activity` داخل نصّ `dumpsys`. */
    private val SLASHED_PACKAGE = Regex("([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)*)/")

    /**
     * وسوم السطور التي **يسمّي فيها النظام** التطبيق المقيم. والترتيب مقصود: أوّل ما يُقرأ من
     * نظرة واحدة على السجلّ هو الأقرب إلى «المقدّمة الآن» (`mResumedActivity` ثمّ
     * `topResumedActivity` ثمّ `mFocusedApp` ثمّ `mCurrentFocus`).
     */
    private val MARKERS = listOf("mResumedActivity", "topResumedActivity", "mFocusedApp", "mCurrentFocus")

    /**
     * مجالات ثوابت أندرويد **التي تحمل شكل اسم الحزمة وليست حزمة**: `Intent.toString()` يكتب
     * الفعل والتصنيف قبل المكوّن، و`android.intent.action.MAIN` صالح الصيغة تمامًا. فبلا هذا
     * التخطّي يُرسَل **الفعل** إلى الخادم فلا يطابق مفتاحًا — أي نفس العطب بلون آخر.
     */
    private val NOT_A_PACKAGE_NAMESPACE = listOf("android.intent.", "android.permission.")

    /**
     * هل هذا **اسم حزمة صالح**؟ والمقارنة بلا حالة لأن الصيغة بحروف صغيرة، والقيمة المُعادة
     * تبقى بحالتها (‏`extractPackageName` لا تُصغّر)، فاسمٌ حقيقيّ بحرف كبير يبقى كما هو
     * ويطابق مفتاحه في قائمة التطبيقات.
     */
    fun isPackageName(value: String?): Boolean {
        val candidate = value?.trim().orEmpty()
        if (candidate.length < 3 || candidate.length > MAX_PACKAGE_LENGTH) return false
        return PACKAGE_NAME.matches(candidate.lowercase())
    }

    /**
     * أوّل اسم حزمة صالح داخل نصّ — كما يستخرجها فحصٌ لنصوص العتاد (وصف `toString()` لكائن،
     * أو حقل نصّيّ فيه).
     *
     * **والترتيب مقصود، لا اعتباطيّ — سببان من شكل أندرويد نفسه:**
     * (١) **شكل المكوّن `pkg/Activity` أوّلًا**، لأنه الشكل الذي يكتبه النظام عن التطبيق نفسه
     *     (`cmp=` في `Intent.toString()` و`ActivityRecord{}` في `dumpsys`). وبلا هذه الأسبقيّة
     *     يُقرأ من `Intent { act=android.intent.action.MAIN cat=[…LAUNCHER] …
     *     cmp=com.franco.kernel/.MainActivity }` **اسمُ الفعل** `android.intent.action.MAIN`،
     *     وهو صالح الصيغة فيُقبل ويُرسَل إلى الخادم ولا يطابق مفتاحًا — نفس العطب بلون آخر.
     *     والبحث `findAll` لا `find`: مكوّن غير صالح الصيغة (مثل `remote/` في
     *     `com.example.app:remote/Activity`) يُتخطّى إلى التالي ثمّ إلى المسار الأخير.
     * (٢) ثمّ **أوّل رمز صالح** بعد تنقية النصّ، مع تخطّي مجالات ثوابت أندرويد
     *     ([NOT_A_PACKAGE_NAMESPACE]).
     *
     * **وحدّها:** تُنظَّف النقطة والشرطة السفليّة والشرطة ([`.`] · [_] · [-]) فتبقى مقاطع الاسم
     * متّصلة، وما عداها (الشرطة المائلة والنقطتان والمساواة) يصير فراغًا فلا يلتصق اسمٌ بما ليس
     * منه. و**القيمة المُعادة بحالتها لا مُصغَّرة** — فاسم بحرف كبير يبقى كما هو ويطابق مفتاحه
     * في قائمة التطبيقات (والمقارنة في [isPackageName] بلا حالة).
     */
    fun extractPackageName(text: String?): String? {
        val source = text?.trim().orEmpty()
        if (source.isBlank()) return null
        if (isPackageName(source)) return source
        SLASHED_PACKAGE.findAll(source)
            .map { match -> match.groupValues[1] }
            .firstOrNull { candidate -> isPackageName(candidate) }
            ?.let { return it }
        val normalized = source.replace(Regex("[^A-Za-z0-9._-]"), " ")
        return normalized.split(Regex("\\s+")).firstOrNull { token ->
            isPackageName(token) && NOT_A_PACKAGE_NAMESPACE.none { token.lowercase().startsWith(it) }
        }
    }

    /**
     * اسم الحزمة من نصّ `dumpsys` (‏`activity activities` أو `window`).
     *
     * **ولماذا هذا المصدر:** على Android 14 صارت `getTasks`/`getRunningTasks` مقيَّدة على
     * التطبيقات غير النظاميّة، فما يبقى من التفكير (reflection) قد يعجز — أو يُعيد شيئًا
     * لا علاقة له بالحزمة كما وقع مقيسًا. والنظام نفسه يقول اسم التطبيق المقيم في سطره،
     * فيُقرأ من هناك بدل تخمينه من كائن.
     *
     * **وحدّها:** تقرأ `pkg/activity`، وتقبل ما يجتاز [isPackageName] فقط؛ ونصّ لا يحمل
     * وسمًا منها يُعيد `null` (ولا يُخترع تطبيق).
     */
    fun parseResumedPackage(dumpsys: String?): String? {
        if (dumpsys.isNullOrBlank()) return null
        for (line in dumpsys.lineSequence()) {
            if (MARKERS.none { line.contains(it) }) continue
            for (match in SLASHED_PACKAGE.findAll(line)) {
                val candidate = match.groupValues[1]
                if (isPackageName(candidate)) return candidate
            }
        }
        return null
    }
}
