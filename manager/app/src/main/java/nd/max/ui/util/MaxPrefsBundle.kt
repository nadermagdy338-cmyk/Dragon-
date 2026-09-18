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

import android.content.Context
import android.content.SharedPreferences
import nd.max.ui.util.ConfigBackupInventory.PrefValue

/**
 * قراءة/كتابة تفضيلاتنا في نسخة الإعداد — الطبقة التي تلمس أندرويد، والقرار كله في
 * [ConfigBackupInventory].
 *
 * القاعدتان الملزمتان هنا:
 *
 * 1. **الحماية بالقائمة البيضاء.** لا يُقرأ ولا يُكتَب ملف إلا من `copyablePrefFiles()`. فحتى
 *    لو حمل ملف النسخة اسمًا لم نتوقّعه، لا يصل منه شيء إلى تخزيننا — و`maxai_safety` بالتحديد
 *    **لا يُستورد أبدًا** لأن سياسة السلامة تُضبط في مكانها لا في ملف يُنقل بين الأجهزة.
 * 2. **الاستيراد دمج لا محو.** لا نستدعي `clear()`: نسخة أقدم من هذه النسخة قد لا تعرف كل
 *    مفاتيح الإصدار الحالي، والمحو يعني إسقاط ما لم نُدرجه باسم «الاستعادة». فالمفاتيح موجودة
 *    في الملف تُكتب، وما ليس فيه **يبقى كما هو** — ويُعلَن ذلك للمستخدم.
 */
object MaxPrefsBundle {

    /** الملفات المعلَنة الموجودة فعلًا. */
    fun presentFiles(context: Context): Set<String> =
        ConfigBackupInventory.copyablePrefFiles()
            .filter { runCatching { prefFile(context, it).all.isNotEmpty() }.getOrDefault(false) }
            .toSet()

    /** منها ما وُجد لكنه بلا مفتاح واحد ⇒ «فارغ» لا «غائب». */
    fun emptyFiles(context: Context): Set<String> =
        ConfigBackupInventory.copyablePrefFiles()
            .filter {
                runCatching { prefFile(context, it).all.isEmpty() }.getOrDefault(false)
            }
            .toSet()

    private fun prefFile(context: Context, name: String): SharedPreferences =
        context.getSharedPreferences(name, Context.MODE_PRIVATE)

    /** قراءة ملف واحد بقيمها **بأنواعها**. `null` = تعذّرت القراءة — ولا نُخترع خريطة فارغة. */
    fun readFile(context: Context, name: String): Map<String, PrefValue>? {
        if (!ConfigBackupInventory.isCopyable(name)) return null
        return runCatching {
            prefFile(context, name).all
                .mapNotNull { (key, raw) -> raw?.toPrefValue()?.let { key to it } }
                .toMap()
        }.getOrNull()
    }

    /** كل الملفات المسموحة المدعومة بـ`files`، أو `null` إن تعذّر أحدها. */
    fun read(context: Context, files: Collection<String>): Map<String, Map<String, PrefValue>>? {
        val out = linkedMapOf<String, Map<String, PrefValue>>()
        files.forEach { name ->
            if (!ConfigBackupInventory.isCopyable(name)) return null
            val values = readFile(context, name) ?: return null
            if (values.isNotEmpty()) out[name] = values
        }
        return out
    }

    /** نتيجة تطبيق حِزمة: عدد الملفات المطبَّقة، والمرفوضة بأسمائها. */
    data class ApplyResult(val appliedFiles: List<String>, val rejectedFiles: List<String>)

    /**
     * يطبّق الحِزمة المفكوكة. ويرفض **كل** الحِزمة إن حملت ملفًا غير مسموح — لا يطبّق ما فهمه
     * ويتجاهل الباقي، لأن نصف استعادة لا يفهم المستخدم مصدرها أسوأ من عدمها.
     */
    fun apply(context: Context, bundles: Map<String, Map<String, PrefValue>>): ApplyResult {
        val rejected = bundles.keys.filterNot { ConfigBackupInventory.isCopyable(it) }
        if (rejected.isNotEmpty()) return ApplyResult(emptyList(), rejected)

        val applied = mutableListOf<String>()
        bundles.forEach { (name, values) ->
            val ok = runCatching {
                val editor = prefFile(context, name).edit()
                values.forEach { (key, value) -> editor.putTyped(key, value) }
                editor.commit()
            }.getOrDefault(false)
            if (ok) applied += name
        }
        return ApplyResult(appliedFiles = applied, rejectedFiles = emptyList())
    }

    /** الكتابة بالنوع الأصلي — لا تحويل، لأن تحويل `Int` إلى `Long` يكسر قارئًا يستعمل `getInt`. */
    private fun SharedPreferences.Editor.putTyped(key: String, value: PrefValue): SharedPreferences.Editor =
        when (value) {
            is PrefValue.Text -> putString(key, value.value)
            is PrefValue.Int32 -> putInt(key, value.value)
            is PrefValue.Whole -> putLong(key, value.value)
            is PrefValue.Decimal -> putFloat(key, value.value.toFloat())
            is PrefValue.Flag -> putBoolean(key, value.value)
            is PrefValue.TextSet -> putStringSet(key, value.value.toSet())
        }
}

/**
 * تحويل قيمة `SharedPreferences` الخام إلى نوع معلَن.
 *
 * و`Int` تبقى `Int32` ولا تُوسَّع إلى `Long`: قراءة مفتاح كُتب `Long` بـ`getInt` **ترمي**،
 * فالتوسيع هنا لا يُفسد القيمة بل يُفسد قارئها. وما ليس نوعًا نعرفه **يُسقَط** لا يُحوَّل نصًّا.
 */
internal fun Any.toPrefValue(): PrefValue? = when (this) {
    is String -> PrefValue.Text(this)
    is Int -> PrefValue.Int32(this)
    is Long -> PrefValue.Whole(this)
    is Float -> PrefValue.Decimal(this.toDouble())
    is Boolean -> PrefValue.Flag(this)
    is Set<*> -> PrefValue.TextSet(filterIsInstance<String>().toList())
    else -> null
}
