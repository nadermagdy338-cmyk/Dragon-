/*
 * Copyright (C) 2026-2027 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * التنظيف الفائق — **القرارات وحدها**، بلا مسار ولا ملفّ ولا صلاحية.
 *
 * طلب المالك (`MAX-MANAGER-LEVEL-UP.md` §6): فئات بمقاس **مقيس**، وملفات المستخدم لا تُمس
 * إلا بتأشير صريح، والزرّ يحمل مجموع المختار، وبلا صلاحية يُعلَن النطاق الأضيق بدل أن يُخفي
 * الزرّ. وهذه كلها **قرارات** لا أوامر: أيّ فئة مُشّرة افتراضيًّا، وأيّها متاحة عند الطبقة
 * الحالية، وما الرقم الذي يحمله الزرّ، وماذا يعني «فشل القياس» في المجموع.
 *
 * فصُلت عن المحرّك (`UltraCleanEngine`) للسبب نفسه الذي فُصل به `StorageScanModel`: قرار
 * يُقاس في JVM مقابل أمر `rm` لا يُقاس إلا على جهاز — وخلطهما هو ما يجعل عطبًا في الجمع أو
 * في التأشير الافتراضي يظهر كرقم غريب على شاشة المستخدم بلا اختبار يمسكه.
 *
 * **وثلاثة عقود مثبّتة هنا بالنصّ:**
 *
 * 1. **`InstallerFiles` و`EmptyFolders` مطفأتان افتراضيًّا** (§6.2). وهما الفئتان الوحيدتان
 *    اللتان قد تكونان **ملكًا للمستخدم** (ملفّ نزّله، مجلدٌ أنشأه)، فالتأشير عليهما فعل واعٍ
 *    لا افتراض.
 * 2. **`SystemLogs` تحتاج جذرًا** — وهي الفئة الوحيدة المخفيّة عند غياب الجذر، لأن مسارها خارج
 *    ما يقرؤه uid التطبيق أصلًا (لا قياس ولا حذف). وما عداها يُقاس ويُعلن نطاقه.
 * 3. **`null` ليست صفرًا.** فئة فشل قياسها لا تُحسب في المجموع، ويُعلَن أن المجموع ناقص
 *    ([CleanTotal.hasUnmeasured]) — ولا يُكتب «0 B» على فئة فيها ملفات، ولا يُخفى النقص.
 */
package nd.max.ui.util

/** فئة تنظيف واحدة: ثابت تخزينها، وتأشيرها الافتراضي، وصلاحيتها. */
enum class UltraCleanCategory(
    /** الثابت: لا يتغيّر بتغيّر نصّ ولا مسار (نفس عقد `ScreenUsageStore`). */
    val key: String,
    /**
     * هل تُشّر عند فتح الشاشة؟
     *
     * والتأشير **لا يُخزَّن**: هو حالة جلسة واحدة تُبنى من هذه القيمة في كل فتح، فلا تعود
     * فئةٌ أطفأها المستخدم مرّةً معلَّمةً صامتة في جولة لاحقة.
     */
    val defaultChecked: Boolean,
    /** هل تحتاج جذرًا للقياس **والحذف** معًا؟ */
    val needsRoot: Boolean,
) {
    /** كاش التطبيقات في التخزين الداخلي (`/data/data/<pkg>/cache`). */
    AppCaches("app_caches", defaultChecked = true, needsRoot = true),

    /** كاش التخزين المشترك الذي تتركه التطبيقات (`Android/data/<pkg>/cache`). */
    SharedCaches("shared_caches", defaultChecked = true, needsRoot = true),

    /** مجلدات الصور المصغّرة — تُعاد بناؤها عند الحاجة، فهي أأمن ما يُحذف. */
    Thumbnails("thumbnails", defaultChecked = true, needsRoot = false),

    /** ملفات APK في التنزيلات — **قد تكون ملك المستخدم، فمطفأة افتراضيًّا**. */
    InstallerFiles("installer_files", defaultChecked = false, needsRoot = false),

    /** مجلدات فارغة خارج `Android/` — **مطفأة افتراضيًّا**، ولا تُحذف إلا بتأشير. */
    EmptyFolders("empty_folders", defaultChecked = false, needsRoot = false),

    /** آثار ANR وtombstones وصندوق النظام — خارج ما يقرؤه التطبيق بلا جذر. */
    SystemLogs("system_logs", defaultChecked = true, needsRoot = true),
}

/**
 * قياس فئة واحدة.
 *
 * @param bytes المساحة بالبايت، أو `null` إن **فشل القياس** — لا صفرًا. وهذا الفرق هو كل
 *        الفرق بين «لا شيء» و«لم أعرف»، وقد بُني عليه المجموع كله.
 * @param count عدد العناصر المقيسة. مفيد لفئة **حجمها صفر بطبيعتها** (`EmptyFolders`): عدد
 *        المجلدات هو كل الخبر، فلا يُعرض لها «0 B».
 */
data class CleanMeasurement(
    val category: UltraCleanCategory,
    val bytes: Long?,
    val count: Int,
)

/** مجموعة قياسات الشاشة، والفشل فيها لا يُسقط الجلسة. */
data class CleanMeasurementSet(val rows: List<CleanMeasurement> = emptyList()) {

    fun of(category: UltraCleanCategory): CleanMeasurement? = rows.firstOrNull { it.category == category }

    /** هل فشل قياس واحدة على الأقل؟ يغذّي سطر الصدق في الشاشة (§6.2: «الفئة — وسبب قصير»). */
    val anyFailed: Boolean get() = rows.any { it.bytes == null }
}

/**
 * مجموع المختار كما يُعرض على الزرّ.
 *
 * @param bytes ما قيس فعلًا من المختار.
 * @param hasUnmeasured هل في المختار فئة فشل قياسها؟ — فالمجموع **حدّ أدنى** لا مجموع،
 *        ويُقال ذلك في الشاشة بدل أن يُقدَّم كأنه الكل.
 */
data class CleanTotal(val bytes: Long, val hasUnmeasured: Boolean) {
    val hasAnything: Boolean get() = bytes > 0L
}

object UltraCleanModel {

    /** المجموعة الآمنة: كل ما هو مُشّر افتراضيًّا — الكاش والصور المصغّرة والسجلات. */
    fun safeSelection(): Set<UltraCleanCategory> =
        UltraCleanCategory.entries.filter { it.defaultChecked }.toSet()

    /**
     * هل تُعرض هذه الفئة كـ«مقفلة» بدل أن تُخفى؟
     *
     * العقد (§10.3) يقول: المقفل **يُعرض ويقول ما ينقصه**، لا يختفي. لكن هنا استثناءٌ مقصود
     * ومكتوب: `SystemLogs` مسارها خارج ما يقرؤه uid التطبيق أصلًا، فعرضها كصفّ معطّل يعني
     * صفًّا **لا يمكن أن يمتلئ أبدًا في هذا الوضع** — أي دعوة دائمة لتحذير. ولذلك تُخفى هذه
     * الواحدة، ويبقى **سطر النطاق** أعلى الشاشة يقول للمستخدم إن الجذر يوسّع التنظيف.
     */
    fun visibleAt(rootAvailable: Boolean, category: UltraCleanCategory): Boolean =
        rootAvailable || !category.needsRoot

    /**
     * المجموع المُعلن على الزرّ.
     *
     * وقياسان مقصودان:
     * - فئة **مطفأة** لا تدخل المجموع ولو قيست (الزرّ يقول ما سيُحذف لا ما هو موجود).
     * - فئة **فشل قياسها** لا تدخل الرقم، وإنما ترفع `hasUnmeasured`.
     */
    fun total(
        selection: Set<UltraCleanCategory>,
        measurements: CleanMeasurementSet,
    ): CleanTotal {
        var sum = 0L
        var unmeasured = false
        for (category in selection) {
            val row = measurements.of(category) ?: continue
            val bytes = row.bytes
            if (bytes == null) unmeasured = true else sum += bytes
        }
        return CleanTotal(bytes = sum, hasUnmeasured = unmeasured)
    }

    /**
     * هل تُعرض هذه الفئة بعدد لا بحجم؟ (المجلدات الفارغة حجمها صفر بالتعريف).
     *
     * والتمييز هنا لا في الشاشة: لو عرضت الشاشة «0 B» لمجلدات فارغة لقالت للمستخدم إن هناك
     * ما يُحرَّر وهو لا يحرّر شيئًا — والعدد (٤ مجلدات) هو الخبر الصحيح.
     */
    fun showsCount(category: UltraCleanCategory): Boolean =
        category == UltraCleanCategory.EmptyFolders
}
