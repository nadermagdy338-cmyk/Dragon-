/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * فهرس الشاشات — **مُوجِّد واحد لكل وجهة في التطبيق**.
 *
 * ولماذا وُلد: شاشة التحكّم تعرض نحو أربعين صفًّا في تسع مجالات، فمن يريد «مُحرّر القيم» أو
 * `FPSGO` أو «سياسة Doze» يشترط أن **يعرف مجاله أوّلًا** — ثلاث ضغطات ومعرفة سابقة. ومن
 * أراد شاشةً في الإعدادات أو `معلومات الجهاز` فطريقه مختلف أصلًا. والفهرس يجعل الطريق واحدًا:
 * اكتب حرفين من الاسم أو ممّا تضبطه الشاشة، فتُعرض الشاشة ومكانها.
 *
 * **وثلاثة قرارات مقيَّدة هنا لا في الشاشات:**
 *
 * 1. **المصدر هو السجلّ نفسه** (`MaxDestination.All`) لا قائمة مكتوبة بيد: وجهة جديدة تُضاف
 *    في السجلّ تظهر في الفهرس بلا سطرٍ هنا، ولا يمكن أن يبقى في الفهرس وجهة محذوفة.
 * 2. **والوجهة التي لا تُفتح بلا معرّف لا تُعرض.** `AppSettings` مسارها `app_settings/{pkg}`
 *    فلا قيمة افتراضية لمعرّفها، وعرضُها يعني ضغطة تفتح تفصيل حزمة اسمها فارغ — وهو الوعد
 *    الكاذب نفسه الذي يمنعه ADR-07 في الأرقام. تُستثنى بـ`needsLaunchArgument` (وهو الفحص
 *    الذي يفرضه `MaxNavActions.navigateTo` بحارس وقت التشغيل أيضًا).
 * 3. **و«أين تسكن» من الشجرة لا من جدول ثانٍ:** أبُ الوجهة في السجلّ هو جواب «أين» (مجالها أو
 *    الإعدادات)، والتبويبات الأربعة تُعلن نفسها، ومن لا أب له يُفتح من الرئيسية. فلا خريطة
 *    موازية تنحرف عن السجلّ يومًا.
 *
 * **والجولة ٢٠٣ أضافت المعلومات الأربع التي يراها المستخدم في السطر نفسه** (طلب المالك:
 * «اعطي لفكرتك معلومات اكثر في الشاشات التي فعلتها جيد»):
 *
 * 4. **ما تضبطه الشاشة** (`role` من كتالوج السجلّ) و**أين تسكن** (`where` من الشجرة) — كانا.
 * 5. **وكم مرة فتحتها أنت** (`usage`): والعدد يُقرأ من `ScreenUsageStore` — السجلّ الواحد الذي
 *    تقرأ منه «منصة التحكم» أيضًا — فلا يقول المُوجِّد «الأكثر استعمالًا» ومنصةٌ أخرى تقول غيره.
 *    والعدد **مقيس لا مُصنَّع**: نحن من سجّله عند فتح الشاشة، ويبقى على الجهاز.
 * 6. **ووسم الخطورة حين تحمله الوجهة** (`riskNote` من `maxRiskLabel` نفسه الذي يوسم به
 *    الإعداد ‹أدوات متقدّمة›، ADR-16) — فلا يفتح المستخدم «مُحرّر القيم» بضغطة وهو يظنّها
 *    شاشة معلومات.
 * 7. **وما تفتحه أكثر يُعرض قبل أن تكتب حرفًا** ([screenFinderSuggestions]): الحقل الفارغ كان
 *    يشرح فقط، وصار يعمل: أربعة صفوف هي شاشاتك، ثم سطر الشرح تحتها.
 *
 * والصافي هنا لا يرسم: `ScreenFinderSheet` هو الذي يرسمه، و`ScreenFinderTest` يقيسه على JVM
 * (الترتيب · الاستثناءات · الطيّ العربي · العدّاد · الوسم).
 */
package nd.max.ui.mainscreens

import androidx.annotation.StringRes
import nd.max.R
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.navigation.MaxRisk
import nd.max.ui.navigation.maxDestinationRole
import nd.max.ui.util.maxSearchFold
import nd.max.ui.util.maxSearchRankFolded

/**
 * وجهة جاهزة للعرض في نتائج البحث: الاسم الذي يراه المستخدم، وما تضبطه، ومكانها، وعدد فتحاتها.
 *
 * والنصوص تُمرَّر **محلولة** لا كمعرّفات مورد: الفهرس يقارن نصوصًا، والقارئ (`stringResource`
 * أو `Context.getString`) يعيش في طبقة الرسم، فيبقى هذا الملفّ صافيًا ومقيسًا بلا منصّة.
 */
data class ScreenFinderTarget(
    val destination: MaxDestination,
    val title: String,
    val role: String,
    val where: String,
    /** عدد فتحات هذه الشاشة على هذا الجهاز؛ و٠ تعني «لم تُفتح بعدُ» لا «غير معروفة». */
    val usage: Int = 0,
    /** وسم الخطورة إن كانت الوجهة موسومة بها، و`null` للوجهة العادية — لا نصّ يُصنَّع لها. */
    val riskNote: String? = null,
)

/**
 * يبني الفهرس من السجلّ. [resolve] يحوّل معرّف مورد إلى نصّه في لغة الواجهة الحالية، و[usage]
 * هو سجلّ الفتحات المحلّي (غائب ⇒ لا نتيجة بعد، وكلّها بصفر).
 */
fun screenFinderTargets(
    resolve: (Int) -> String,
    usage: Map<String, Int> = emptyMap(),
): List<ScreenFinderTarget> =
    MaxDestination.All
        .filterNot { it.needsLaunchArgument }
        .map { destination ->
            ScreenFinderTarget(
                destination = destination,
                title = resolve(destination.titleRes),
                role = resolve(maxDestinationRole(destination)),
                where = resolve(screenFinderWhereRes(destination)),
                usage = usage[destination.route] ?: 0,
                riskNote = screenFinderRiskRes(destination)?.let(resolve),
            )
        }

/** «أين تسكن هذه الشاشة» — من الشجرة في السجلّ لا من جدول ثانٍ. */
@StringRes
internal fun screenFinderWhereRes(destination: MaxDestination): Int = when {
    destination.isPrimary -> R.string.screen_finder_where_bar
    destination.parent != null -> destination.parent.titleRes
    else -> R.string.max_nav_now
}

/**
 * وسم الخطورة — من الجدول الواحد (`maxRiskLabel` الذي يوسم به الإعداد ‹أدوات متقدّمة›)،
 * ولا جدول ثانٍ للوسم نفسه؛ والوجهة العادية لا وسم لها أصلًا فلا تُكتب «عادي» في كل صفّ.
 */
@StringRes
internal fun screenFinderRiskRes(destination: MaxDestination): Int? =
    if (destination.risk == MaxRisk.Normal) null else maxRiskLabel(destination.risk)

/**
 * نتائج [query] مرتّبةً: اسمُ الشاشة أوّلًا، ثم ما تضبطه، ثم مجالك. وداخل الرتبة نفسها
 * **الأكثر فتحًا أوّلًا** (فمن كتب «الحرارة» مرّتين في يومين يريد شاشته هو قبل شاشةٍ لم يفتحها)،
 * ثم ترتيبٌ أبجديّ مطويّ — لا ترتيبًا يعتمد على ترتيب السجلّ، فيتغيّر بترتيب لا يراه المستخدم.
 *
 * والفراغ ⇒ لا نتائج (لا «كل الشاشات»): القائمة كلها على الشاشة أصلًا، وردُّها هنا تكرار.
 */
fun screenFinderResults(
    query: String,
    targets: List<ScreenFinderTarget>,
    limit: Int = 24,
): List<ScreenFinderTarget> {
    val needle = maxSearchFold(query).trim()
    if (needle.isEmpty()) return emptyList()

    return targets
        .mapNotNull { target ->
            val titleRank = maxSearchRankFolded(target.title, needle)
            val roleRank = if (titleRank < 0) maxSearchRankFolded(target.role, needle) else -1
            val whereRank = if (titleRank < 0 && roleRank < 0) {
                maxSearchRankFolded(target.where, needle)
            } else {
                -1
            }
            val score = when {
                titleRank >= 0 -> titleRank
                roleRank >= 0 -> ROLE_BASE + roleRank
                whereRank >= 0 -> WHERE_BASE + whereRank
                else -> return@mapNotNull null
            }
            ScreenFinderHit(target, score, maxSearchFold(target.title))
        }
        .sortedWith(
            compareBy<ScreenFinderHit>({ it.score }, { -it.target.usage }, { it.foldedTitle })
        )
        .take(limit.coerceAtLeast(0))
        .map { it.target }
}

/** كم صفًّا يُقترح قبل أن يكتب المستخدم حرفًا — صفٌّ يُقرأ لا قائمةً تُمرَّر. */
const val SCREEN_FINDER_SUGGESTIONS = 4

/**
 * «ما تفتحه أكثر» — الحقل الفارغ لم يعد يشرح فقط، بل يعمل.
 *
 * **ولا اقتراح بلا سجلّ:** من لم يفتح شيئًا (أو أوّل تشغيل) تُعاد قائمة فارغة، ويُعرض سطر الشرح
 * وحده — ولا تُعرض «اقتراحات» من الصفر هي أعلى الصفر، فهذا ترتيب لا مقياس خلفه.
 */
fun screenFinderSuggestions(
    targets: List<ScreenFinderTarget>,
    limit: Int = SCREEN_FINDER_SUGGESTIONS,
): List<ScreenFinderTarget> = targets
    .filter { it.usage > 0 }
    .sortedWith(
        compareByDescending<ScreenFinderTarget> { it.usage }.thenBy { maxSearchFold(it.title) }
    )
    .take(limit.coerceAtLeast(0))

/** رتبةٌ واحدة تُحسب ثم تُرمى — لا تُعرَّض خارج الملفّ. */
private data class ScreenFinderHit(
    val target: ScreenFinderTarget,
    val score: Int,
    val foldedTitle: String,
)

/** مطابقةٌ في سطر الوصف تُرتَّب بعد كل مطابقات الاسم، ومطابقةٌ في المكان بعدها. */
private const val ROLE_BASE = 10
private const val WHERE_BASE = 20
