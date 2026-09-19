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

/**
 * `OCR-04` — **المفضّلة**: علامة على تطبيق يُرجَّع إليها كثيرًا.
 *
 * **ولماذا ليست مرشّحًا خامسًا:** المرشّحات في الشاشة شريط واحد بأربع خيارات، والخيار الخامس
 * يضغطه إلى نصّ لا يُقرأ. والمفضّلة ليست صفة يُصفّى بها التطبيق فقط، بل **أولوية في الترتيب**:
 * من وسم تطبيقًا يريد أن يجده في الأعلى، لا أن يفعّل مرشّحًا ليجده. فالقاعدتان هنا:
 *
 *  ١. **يُقدَّم ولا يُخفى**: المفضّلة تتقدّم القائمة، وبقية التطبيقات تبقى كما هي — لا صفّ
 *     يختفي لأن المستخدم وسم غيره.
 *  ٢. **مرشّح «المفضّلة فقط» موجود كزرّ صريح** لمن يريد القصر فعلًا، لكنه **ليس** الطريق الوحيد
 *     إلى المفضّلة.
 *
 * والقواعد كلها نصّية خالصة: تُخزَّن في سطر واحد (اسم حزمة لكل سطر) — لأن ملفًّا لا يُعرَب لا
 * يُنتج استثناءً في مسار لا يشاهده أحد، والقراءة متسامحة كقراءة المجموعات.
 */
package nd.max.ui.util

object MaxBackupFavorites {

    /** ملف في مجلد التطبيق الخاص: قائمة شخصية لا تسافر مع نسخة، ولا مرآة لتفضيل النظام. */
    const val FILE_NAME = "favorites.txt"

    /** حدّ يمنع ملفًّا مريضًا من إبطاء كل قراءة — لا حدّ استخدام. */
    const val MAX = 400

    /**
     * شكل اسم حزمة صالح. والتحقّق ليس زخرفة: القائمة تُقرَأ من ملف، وسطر مشوّه لا يُطابق
     * تطبيقًا مثبَّتًا، فيصير صفًّا لا معنى له في أطول قائمة في التطبيق.
     */
    private val PACKAGE = Regex("""^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+${'$'}""")

    fun isPackageName(raw: String): Boolean = PACKAGE.matches(raw.trim())

    /** ينقّي: يُبقي أسماء الحزم الصالحة، يحذف المكرّر، ويرتّب — فلا يختلف الملف بترتيب الإدخال. */
    fun sanitize(raw: List<String>): List<String> = raw
        .map { it.trim() }
        .filter { isPackageName(it) }
        .distinct()
        .sorted()
        .take(MAX)

    fun isFavorite(current: Collection<String>, pkg: String): Boolean = pkg in current

    /** إضافة أو إزالة. والمفاتيح مرتّبة دائمًا، فالملف لا يتغيّر لأن المستخدم وسَم بترتيب آخر. */
    fun toggle(current: List<String>, pkg: String): List<String> =
        sanitize(if (isFavorite(current, pkg)) current - pkg else current + pkg)

    /**
     * المفضّلة أولًا، ثم بقية العناصر بترتيبها كما هو.
     *
     * **ولماذا `sortedByDescending` على قيمة منطقية لا فرز كامل:** الفرز الكامل يمحو ترتيب
     * القائمة الأصلي (الأبجدي) فيصير كل ما ليس مفضّلًا فوضى؛ وهذه الدالّة تُبقي الترتيب
     * الأصلي كما جاء وتقدّم عليه فحسب. والفرز **مستقرّ** في Kotlin، فالعنصران المتساويان
     * يبقيان بترتيبهما.
     */
    fun <T> ordered(items: List<T>, key: (T) -> String, favorites: Collection<String>): List<T> {
        if (favorites.isEmpty()) return items
        return items.sortedByDescending { key(it) in favorites }
    }

    fun encode(favorites: List<String>): String =
        sanitize(favorites).joinToString("\n", postfix = "\n")

    /** قراءة متسامحة: سطر لا يصلح اسمَ حزمة يُتخطّى، والبقية تُقرأ. */
    fun decode(text: String): List<String> = sanitize(text.lines())
}
