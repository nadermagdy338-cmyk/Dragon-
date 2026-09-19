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
 * `OCR-06` — **عدّاد النسخ وتجميعها**: رقم واحد يخبرك بحجم ما عندك، وصفّ واحد يقول كم بقي مخفيًّا.
 *
 * **ولماذا نموذج مستقل بلا `Handle`:** `Handle` يُفكّ من مستند `org.json` فلا يُترجَم على JVM،
 * وأي حساب يعتمد عليه لا يُقاس إلا بجهاز. والعدّ هنا لا يحتاج المستند: ثلاثة أرقام عن كل نسخة
 * (زمنها وحجمها وعدد عناصرها) تكفي. فالواجهة تُسقط `Handle` إلى [CopyFact] بسطر واحد، والحساب
 * يبقى قابلًا للقياس في JVM.
 *
 * **ولماذا لا يُخفي شيء:** التجميع (‹+N›) **إظهار مؤجَّل** لا حذف — الرقم يُعلن، والصفّ يُنقر
 * فتظهر البقية في مكانها. وقائمة تُقصّ بصمت أسوأ من قائمة طويلة.
 */
package nd.max.ui.util

object MaxBackupCounts {

    /** ما تحتاجه الحسابات من نسخة واحدة. */
    data class CopyFact(
        val pkg: String,
        val createdAtMs: Long,
        val bytes: Long,
        val entryCount: Int,
        val complete: Boolean = true,
    )

    /**
     * حصيلة كل ما عندنا. و`bytes` و`entries` **مجموعان** لا قيمتان من نسخة واحدة:
     * السؤال الذي يجيبانه هو «كم يشغل التخزين وكم عنصرًا أستطيع استرجاعه».
     */
    data class Summary(
        val copies: Int,
        val entries: Int,
        val bytes: Long,
        val newestAtMs: Long?,
        val incomplete: Int,
    ) {
        val isEmpty: Boolean get() = copies == 0
    }

    fun summarize(copies: List<CopyFact>): Summary = Summary(
        copies = copies.size,
        entries = copies.sumOf { it.entryCount.toLong() }.let { if (it > Int.MAX_VALUE) Int.MAX_VALUE else it.toInt() },
        bytes = copies.sumOf { it.bytes },
        newestAtMs = copies.maxOfOrNull { it.createdAtMs },
        incomplete = copies.count { !it.complete },
    )

    /** عدد النسخ لكل تطبيق — يُستعمل حيث يُعرض «N نسخة» في صفّ تطبيق واحد. */
    fun perPackage(copies: List<CopyFact>): Map<String, Int> =
        copies.groupingBy { it.pkg }.eachCount()

    /**
     * كم بقي مخفيًّا بعد عرض [shown] من [total].
     *
     * و`0` ليست تفصيلًا: هي الحالة التي **لا يُعرض فيها صفّ التجميع أصلًا**، فلا يظهر «+0 other»
     * الذي يعلن تجميعًا غير موجود.
     */
    fun overflow(shown: Int, total: Int): Int = (total - shown).coerceAtLeast(0)

    /**
     * ما يُعرض فعلًا: الكل إن أراد المستخدم، وإلا الحدّ.
     * ودالّة صريحة لأن «الكل» و«الحدّ» لا يجتمعان في تعبير واحد في الشاشة بلا خطأين محتملين.
     */
    fun visibleCount(total: Int, limit: Int, expanded: Boolean): Int =
        if (expanded) total else total.coerceAtMost(limit)
}
