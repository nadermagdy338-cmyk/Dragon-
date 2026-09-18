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

package nd.max.ui.util

import android.Manifest
import java.util.Locale

/**
 * `Max Backup` — **بيانات النظام** لا بيانات التطبيقات: شبكات الواي‑فاي · أزواج البلوتوث ·
 * جهات الاتصال وأرقامها · سجل المكالمات · الرسائل.
 *
 * وهذا الملف **جدول لا كود**: كل فئة بيانات هي صف في [SOURCES] يعلن من أين تُقرأ، وبأي وسيلة،
 * وبأي أذونات، و**ما معنى استرجاعها**. ولذلك إضافة فئة جديدة لاحقًا سطر واحد، لا شاشة ثانية.
 *
 * والقواعد التي تحكمه هي نفس قواعد النسخ الاحتياطي للتطبيقات، وبنفس الصرامة:
 *
 * 1. **القدرة تُعلَن ولا تُخمَّن.** فئة تحتاج جذرًا تظهر `NEEDS_ROOT`، وأخرى تحتاج إذنًا تظهر
 *    `NEEDS_PERMISSION` — ولا واحدة منهما تُعرَض «متاحة» ثم تفشل عند الضغط.
 * 2. **ما تحجبه المنصّة يُقال.** استرجاع الرسائل يحتاج أن يكون التطبيق **تطبيق الرسائل
 *    الافتراضي** (سياسة أندرويد)، فنقرأها ونؤرشفها و**نعلن أن الكتابة محجوبة** بدل زر يفشل.
 * 3. **الدمج ليس استبدالًا، والفرق مكتوب.** استرجاع الجهات وسجل المكالمات **يُدرج** صفوفًا
 *    غائبة ولا يمسح ما ليس في النسخة — ومفتاح التطبيع يجعل الإدراج المتكرّر لا يضاعف الصفوف.
 * 4. **الصف الذي لا مفتاح له لا يُنسب إلى براءة.** يُدرَج (فالبيانات أهم) لكن يُعَدّ ويُعرَض
 *    باسمه: `undedupable` — لأن الادّعاء بأنه لن يتكرّر ادّعاء بلا سند.
 */
object MaxBackupSystem {

    /** الفئات المدعومة. المعرّف يُكتب في المستند، والاسم المترجم في الشاشة. */
    enum class Kind(val id: String, val entryFile: String) {
        WIFI("wifi", "wifi.tar.gz"),
        BLUETOOTH("bluetooth", "bluetooth.tar.gz"),
        CONTACTS("contacts", "contacts.json"),
        CALL_LOG("call_log", "call_log.json"),
        SMS("sms", "sms.json"),

        /** قاموس المستخدم: الكلمات التي أضافها تدقيقًا للوحة المفاتيح. */
        USER_DICTIONARY("user_dictionary", "user_dictionary.json"),
        ;

        companion object {
            fun fromId(raw: String?): Kind? = entries.firstOrNull { it.id == raw }
        }
    }

    /** كيف يُقرأ هذا النوع ويُكتب. */
    enum class Method {
        /** ملفات نظام: تُقرأ بالجذر وتُؤرشف بـ`tar`. */
        ROOT_FILES,

        /** مزوّد محتوى: يُقرأ ويُكتب عبر `ContentResolver` بأذونات صريحة. */
        PROVIDER,
    }

    /** ما يعنيه الاسترجاع لهذا النوع — يُعلن في الواجهة قبل الضغط لا بعده. */
    enum class Semantics {
        /** استبدال ملفات النظام بأرشيفنا. */
        REPLACE_FILES,

        /** دمج صفوف: يُدرَج الغائب، ولا يُمسح ما ليس في النسخة. */
        MERGE_ROWS,

        /** القراءة ممكنة والكتابة محجوبة بسياسة المنصّة (أندرويد يطلب تطبيق الرسائل الافتراضي). */
        READ_ONLY_BY_POLICY,
    }

    /** تحذير يجب أن يُقرأ قبل الاسترجاع. */
    enum class RestoreWarning {
        /** خدمة النظام قد تكتب فوق ما استرجعناه — لأنها تعمل الآن. */
        SERVICE_MAY_OVERWRITE,

        /** الاسترجاع يُدرج صفوفًا ولا يمسح، فالتكرار ممكن إن تغيّرت البيانات بين النسختين. */
        MERGE_ADDS_ROWS,

        /** المنصّة تحجب الكتابة لهذا النوع. */
        BLOCKED_BY_PLATFORM,
    }

    /**
     * مصدر فئة واحدة.
     *
     * @param paths مسارات الجذر (`ROOT_FILES`) — قائمة لأن الملف نفسه انتقل بين إصدارات أندرويد
     *        والمُصنّعين، ولا نعرف أيّها على هذا الجهاز قبل الفحص.
     * @param authority سلطة المزوّد (`PROVIDER`).
     * @param permissions الأذونات التي يطلبها التطبيق فعلًا لهذه الفئة.
     */
    data class Source(
        val kind: Kind,
        val method: Method,
        val semantics: Semantics,
        val paths: List<String> = emptyList(),
        val authority: String? = null,
        val permissions: List<String> = emptyList(),
        val restoreWarning: RestoreWarning? = null,
    )

    /**
     * الجدول. ولا يحتوي أي نصّ واجهيّ: الأسماء المترجمة في الشاشة، والأذونات ثوابت المنصّة
     * (وهي ثوابت وقت التصريف، فلا تكسر اختبارات JVM).
     */
    val SOURCES: List<Source> = listOf(
        Source(
            kind = Kind.WIFI,
            method = Method.ROOT_FILES,
            semantics = Semantics.REPLACE_FILES,
            // أندرويد ١٤ نقل مخزن الإعداد إلى apexdata، والأقدم في misc/wifi. نجرّب الاثنين.
            paths = listOf(
                "/data/misc/apexdata/com.android.wifi/WifiConfigStore.xml",
                "/data/misc/wifi/WifiConfigStore.xml",
                "/data/misc/wifi/networkHistory.txt",
            ),
            restoreWarning = RestoreWarning.SERVICE_MAY_OVERWRITE,
        ),
        Source(
            kind = Kind.BLUETOOTH,
            method = Method.ROOT_FILES,
            semantics = Semantics.REPLACE_FILES,
            paths = listOf(
                "/data/misc/bluedroid/bt_config.conf",
                "/data/misc/bluetooth/bt_config.conf",
            ),
            restoreWarning = RestoreWarning.SERVICE_MAY_OVERWRITE,
        ),
        Source(
            kind = Kind.CONTACTS,
            method = Method.PROVIDER,
            semantics = Semantics.MERGE_ROWS,
            authority = "content://com.android.contacts",
            permissions = listOf(
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_CONTACTS,
            ),
            restoreWarning = RestoreWarning.MERGE_ADDS_ROWS,
        ),
        Source(
            kind = Kind.CALL_LOG,
            method = Method.PROVIDER,
            semantics = Semantics.MERGE_ROWS,
            authority = "content://call_log/calls",
            permissions = listOf(
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.WRITE_CALL_LOG,
            ),
            restoreWarning = RestoreWarning.MERGE_ADDS_ROWS,
        ),
        Source(
            kind = Kind.SMS,
            method = Method.PROVIDER,
            semantics = Semantics.READ_ONLY_BY_POLICY,
            authority = "content://sms",
            // القراءة فقط: الكتابة تطلب أن يكون التطبيق تطبيق الرسائل الافتراضي.
            permissions = listOf(Manifest.permission.READ_SMS),
            restoreWarning = RestoreWarning.BLOCKED_BY_PLATFORM,
        ),
        Source(
            kind = Kind.USER_DICTIONARY,
            method = Method.PROVIDER,
            semantics = Semantics.MERGE_ROWS,
            authority = "content://user_dictionary/words",
            // أسماء نصّية لأن `Manifest.permission` **لا تُعلنها**: أذونات عادية معروفة
            // للمنصّة لكنها غير مكشوفة في `android.jar` العام. واختبار
            // `MaxBackupSystemPermissionsTest` يقابلها بسطور `AndroidManifest.xml`
            // فلا يمرّ اسم مكتوب خطأً بصمت.
            permissions = listOf(READ_USER_DICTIONARY, WRITE_USER_DICTIONARY),
            restoreWarning = RestoreWarning.MERGE_ADDS_ROWS,
        ),
    )

    /** أذونات معروفة للمنصّة وغير مُعلَنة في `Manifest.permission` العام. */
    const val READ_USER_DICTIONARY = "android.permission.READ_USER_DICTIONARY"
    const val WRITE_USER_DICTIONARY = "android.permission.WRITE_USER_DICTIONARY"

    fun source(kind: Kind): Source? = SOURCES.firstOrNull { it.kind == kind }

    /** كل الأذونات التي قد تحتاجها الفئات مجتمعة — تُطلَب مرّة واحدة لا فئة فئة. */
    val ALL_PERMISSIONS: List<String> = SOURCES.flatMap { it.permissions }.distinct()

    // ────────────────────────────────────────────────────────────────────────
    // الجرد
    // ────────────────────────────────────────────────────────────────────────

    /**
     * توفّر فئة. و`NEEDS_PERMISSION` ليست فشلًا: هي «نستطيع، امنحنا الإذن» — والفرق بينها
     * وبين `UNAVAILABLE` هو الفرق بين أن نطلب وأن نصمت.
     */
    enum class Availability { AVAILABLE, NEEDS_ROOT, NEEDS_PERMISSION, UNAVAILABLE, UNKNOWN }

    data class Component(
        val kind: Kind,
        val availability: Availability,
        /** الحجم المقيس. `null` = لم يُقس — **لا** صفرًا. */
        val sizeBytes: Long?,
        /** عدد الصفوف المقروء (`PROVIDER`). `null` = لم نقرأ. */
        val rowCount: Int? = null,
        /** المسارات التي وُجدت فعلًا على هذا الجهاز (`ROOT_FILES`). */
        val resolved: List<String> = emptyList(),
        /** عدد الأذونات الممنوحة من المطلوب. يُعرض ليفهم المستخدم سبب الحجب. */
        val grantedPermissions: Int = 0,
        val requiredPermissions: Int = 0,
    )

    data class Plan(val hasRoot: Boolean, val components: List<Component>) {
        fun component(kind: Kind): Component? = components.firstOrNull { it.kind == kind }

        val knownBytes: Long get() = components.sumOf { it.sizeBytes ?: 0L }

        val hasUnmeasured: Boolean
            get() = components.any { it.availability == Availability.AVAILABLE && it.sizeBytes == null }

        /** الفئات التي يمكن أرشفتها فعلًا الآن. */
        val backupable: List<Component>
            get() = components.filter { it.availability == Availability.AVAILABLE }

        val hasAnything: Boolean get() = components.any { it.availability != Availability.UNAVAILABLE }
    }

    // ────────────────────────────────────────────────────────────────────────
    // الدمج
    // ────────────────────────────────────────────────────────────────────────

    /**
     * مفتاح التطبيع الطبيعي لصف، أو `null` إن لم يمكن تعريفه.
     *
     * الوحدة والترتيب مقصودان: «‎+967 77 123 4567‎» و«‎0096777123 4567‎» رقم واحد، وإدراجهما
     * مرّتين يعني قائمة مكالمات مضاعفة. والأرقام تُقارَن **بآخر ٩ خانات** — لأن بادئة الدولة
     * تختلف بين النسختين بينما ما يميّز الرقم لا يختلف.
     */
    fun naturalKey(kind: Kind, row: Map<String, String?>): String? = when (kind) {
        Kind.CALL_LOG -> {
            val number = normalizeNumber(row["number"]) ?: return null
            val date = row["date"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val type = row["type"]?.trim() ?: ""
            val duration = row["duration"]?.trim() ?: ""
            "$number|$date|$type|$duration"
        }

        Kind.SMS -> {
            val address = normalizeNumber(row["address"]) ?: return null
            val date = row["date"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val body = row["body"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            "$address|$date|${body.hashCode()}"
        }

        Kind.CONTACTS -> {
            val name = row["display_name"]?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
                ?: return null
            val number = row["number"]?.let { normalizeNumber(it) } ?: ""
            val email = row["email"]?.trim()?.lowercase(Locale.ROOT) ?: ""
            // جهة بلا رقم وبلا بريد: الاسم وحده لا يميّز، لكنه يمنع إعادة إدراج الجهة نفسها
            // إن كانت بلا أي وسيلة اتصال أصلًا.
            "$name|$number|$email"
        }

        Kind.USER_DICTIONARY -> {
            val word = row["word"]?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
                ?: return null
            val locale = row["locale"]?.trim()?.takeIf { it.isNotEmpty() } ?: ""
            "$word|$locale"
        }

        Kind.WIFI, Kind.BLUETOOTH -> null
    }

    /** آخر ٩ خانات من الرقم بعد إسقاط كل ما ليس رقمًا. ولا شيء أقل من ذلك يُعتبر مفتاحًا. */
    fun normalizeNumber(raw: String?): String? {
        val digits = raw?.filter(Char::isDigit).orEmpty()
        if (digits.length < MIN_NUMBER_DIGITS) return null
        return digits.takeLast(9)
    }

    /** أقلّ من هذا لا يميّز رقما عن آخر، فلا نبنى عليه تطبيعًا. */
    private const val MIN_NUMBER_DIGITS = 6

    /**
     * خطة الدمج: ما يُدرَج، وما يُتخَطّى لوجوده، وما لا مفتاح له.
     *
     * و**`undedupable` تُدرَج ولا تُسقَط**: البيانات أهم من نظافة العدّ — لكنها تُعَدّ وتُعرَض
     * باسمها، فلا نَدّعي أن الاسترجاع لن يُنتج تكرارًا.
     */
    data class MergePlan(
        val insert: List<Map<String, String?>>,
        val skippedExisting: Int,
        val undedupable: Int,
    )

    fun mergePlan(
        kind: Kind,
        existingKeys: Set<String>,
        incoming: List<Map<String, String?>>,
    ): MergePlan {
        val insert = mutableListOf<Map<String, String?>>()
        val seen = existingKeys.toMutableSet()
        var skipped = 0
        var undedupable = 0

        incoming.forEach { row ->
            val key = naturalKey(kind, row)
            when {
                key == null -> {
                    undedupable++
                    insert += row
                }
                key in seen -> skipped++
                else -> {
                    seen += key
                    insert += row
                }
            }
        }
        return MergePlan(insert = insert, skippedExisting = skipped, undedupable = undedupable)
    }

    // ────────────────────────────────────────────────────────────────────────
    // بوابة الاسترجاع
    // ────────────────────────────────────────────────────────────────────────

    enum class RestoreBlock {
        NONE,
        EMPTY,

        /**
         * الملف لم يجتز فحص بصمته. وُضع منفصلًا عن [EMPTY] لأن الخلط بينهما يجعل «النسخة
         * تالفة» تُقرأ «لا شيء لاسترجاعه» — والعلاج يختلف تمامًا.
         */
        NOT_VERIFIED,
        READ_ONLY_BY_POLICY,
        NEEDS_ROOT,
        NEEDS_PERMISSION,
        UNAVAILABLE,
    }

    /** المعرّف المُعلَن لمكوّن فئة داخل مستند نسخة النظام. */
    fun kindOfEntry(fileName: String): Kind? = Kind.entries.firstOrNull { it.entryFile == fileName }

    data class RestoreDecision(val block: RestoreBlock, val warnings: List<RestoreWarning>) {
        val allowed: Boolean get() = block == RestoreBlock.NONE
    }

    /**
     * قرار استرجاع فئة واحدة. الترتيب **من الأشدّ إلى الأخف**: تُعرض أول علّة تمنع فعلًا،
     * ولا يُشغل المستخدم بتحذير على فئة لا يمكن استرجاعها أصلًا.
     */
    fun restoreDecision(
        kind: Kind,
        entryCount: Int,
        availability: Availability,
        hasRoot: Boolean,
        permissionsGranted: Boolean,
    ): RestoreDecision {
        val source = source(kind)
        val warnings = source?.restoreWarning?.let(::listOf).orEmpty()
        val block = when {
            entryCount <= 0 -> RestoreBlock.EMPTY
            source == null -> RestoreBlock.UNAVAILABLE
            source.semantics == Semantics.READ_ONLY_BY_POLICY -> RestoreBlock.READ_ONLY_BY_POLICY
            source.method == Method.ROOT_FILES && !hasRoot -> RestoreBlock.NEEDS_ROOT
            source.method == Method.PROVIDER && !permissionsGranted -> RestoreBlock.NEEDS_PERMISSION
            availability == Availability.UNAVAILABLE -> RestoreBlock.UNAVAILABLE
            else -> RestoreBlock.NONE
        }
        return RestoreDecision(block, warnings)
    }

    // ────────────────────────────────────────────────────────────────────────
    // النطاق
    // ────────────────────────────────────────────────────────────────────────

    data class Scope(private val selected: Set<Kind>) {
        fun selects(kind: Kind): Boolean = kind in selected
        fun toggle(kind: Kind, on: Boolean): Scope =
            Scope(if (on) selected + kind else selected - kind)

        val anySelected: Boolean get() = selected.isNotEmpty()
        val selectedKinds: List<Kind> get() = Kind.entries.filter { it in selected }

        companion object {
            /**
             * الافتراضي: كل ما يمكن أرشفته فعلًا. الفئة المتاحة لا يُوجد سبب لتركها،
             * والفئة غير المتاحة لا تُشغَل لأن إشعالها وعد كاذب.
             */
            fun from(plan: Plan, open: Boolean = true): Scope =
                Scope(if (open) plan.backupable.map { it.kind }.toSet() else emptySet())
        }
    }
}
