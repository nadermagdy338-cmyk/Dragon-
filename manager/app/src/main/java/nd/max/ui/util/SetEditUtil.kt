/*
 * Adapted from ZKM (Zuan Kernel Manager) ui/setedit/SetEditScreen.kt
 * (the shell-command logic embedded in that composable was extracted here).
 * Original: Copyright (c) 2025 ZKM, licensed GPL-3.0.
 * Adaptation: Copyright (C) 2026-2027 Zexshia
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

import com.topjohnwu.superuser.Shell

/** Which store a [SetEditItem] came from / should be written back to. */
enum class SetEditCategory(val listCommand: String, val settingsNamespace: String?) {
    GLOBAL("settings list global", "global"),
    SECURE("settings list secure", "secure"),
    SYSTEM("settings list system", "system"),
    ANDROID_PROP("getprop", null)
}

data class SetEditItem(val key: String, val value: String, val category: SetEditCategory) {
    /**
     * مفتاح الصف في القائمة الكسولة — **في مكان واحد** لا في نصّ مبنِي داخل الشاشة.
     *
     * وسبب وجوده ليس الترتيب: Compose يرمي إن تكرّر مفتاح صفّ، والشاشة تعتمد على تفرده. ولا يصحّ
     * أن يعرف الاختبارُ صيغةً تُكتب في الشاشة وتتغيّر فيها وحدها.
     */
    val lazyKey: String get() = "$category:$key"
}

/** Keys that can break the device if fat-fingered; edits to these get an extra confirm step. */
private val SENSITIVE_KEY_FRAGMENTS = listOf(
    "adb_enabled", "development_settings_enabled", "install_non_market_apps",
    "enabled_input_methods", "accessibility_enabled", "location_providers_allowed",
    "assisted_gps_enabled", "mock_location", "verifier_verify_adb_installs",
    "ro.secure", "ro.debuggable", "selinux", "ro.build.type"
)

fun isSensitiveSetEditKey(key: String): Boolean =
    SENSITIVE_KEY_FRAGMENTS.any { key.contains(it, ignoreCase = true) }

object SetEditUtil {

    /** Loads every item in [category], or every item across all categories when null. */
    fun loadItems(category: SetEditCategory?): List<SetEditItem> {
        val categories = category?.let { listOf(it) } ?: SetEditCategory.entries
        val result = mutableListOf<SetEditItem>()
        for (cat in categories) {
            try {
                val output = Shell.cmd(cat.listCommand).exec().out
                result += parseOutput(cat, output)
            } catch (e: Exception) {
                // Root shell can legitimately fail (denied, not yet granted, shell
                // died mid-command). Skip this category instead of propagating the
                // exception up through the IO coroutine, which would crash the app.
            }
        }
        return dedupe(result, category)
    }

    /**
     * يحوّل مخرج أمر إلى عناصر — **خالص وقابل للاختبار** بلا وصول إلى shell.
     *
     * وكل سطر يُحلَّل في `try` خاص به: سطر مشوّه (نصّ خطأ دخل في stdout، أو صيغة مصنّع غريبة)
     * **يُسقَط** ولا يُسقط الشاشة كلها.
     */
    fun parseOutput(category: SetEditCategory, output: List<String>): List<SetEditItem> =
        output.mapNotNull { line ->
            try {
                if (category == SetEditCategory.ANDROID_PROP) {
                    parseGetpropLine(line, category)
                } else {
                    parseSettingsLine(line, category)
                }
            } catch (e: Exception) {
                // سطر واحد مشوّه لا يُسقط الشاشة.
                null
            }
        }

    /**
     * **يضمن تفرد `(category, key)` — وهذا ليس ترتيبًا جماليًّا بل منع كراش.**
     *
     * شاشة `SetEdit` تعرض هذه العناصر في `LazyColumn` بمفتاح `"category:key"`، وCompose **يرمي**
     * حين يتكرّر مفتاح:
     * `IllegalArgumentException: Key … was already used`. وهذا العطب لا يظهر عند الفتح بل
     * **عند التمرير**، لأن الصفّ المكرّر لا يُبنى إلا حين يدخل نطاق العرض — فتخبو الشاشة فجأةً
     * في منتصف تمرير.
     *
     * ومخرج الأوامر **يتكرّر فعلًا**، ولهذا يُنقّى في مواضع أخرى من المستودع
     * (`RootFileAccess.globDirectories` مثلًا يستدعي `distinct()` على مخرج shell). وكانت هذه
     * الشاشة الوحيدة التي تبني **مفاتيح قائمة** من مخرج خام بلا تنقية.
     *
     * ويُحفَظ **الأول** ويُسقَط ما بعده، مع تسجيل العدد — فالتكرار يبقى ظاهرًا في السجل بدل أن
     * يُسكَت عنه، والأصل أن لا يوجد.
     */
    internal fun dedupe(items: List<SetEditItem>, category: SetEditCategory?): List<SetEditItem> {
        val seen = HashSet<Pair<SetEditCategory, String>>(items.size)
        val unique = ArrayList<SetEditItem>(items.size)
        var duplicates = 0
        items.forEach { item ->
            if (seen.add(item.category to item.key)) unique += item else duplicates++
        }
        if (duplicates > 0) {
            // يُسجَّل عبر `error` لا `symptom`: الأخير للحالات **المقيسة بالزمن**، وهذا عطب
            // بيانات. والغرض واحد: لا يُسكت عن تكرار، حتى لو لم يُعد يسبب كراشًا.
            EventLog.error(
                screen = "SetEdit",
                operation = "duplicate_keys category=${category?.name ?: "ALL"} dropped=$duplicates",
            )
        }
        return if (category == null) unique.sortedBy { it.key } else unique
    }

    fun set(category: SetEditCategory, key: String, value: String): Boolean {
        return try {
            val cmd = writeCommand(category, key, value)
            Shell.cmd(cmd).exec().isSuccess
        } catch (e: Exception) {
            false
        }
    }

    /** Android system properties (getprop) can't be individually removed - only `settings` keys can. */
    fun delete(category: SetEditCategory, key: String): Boolean {
        if (category == SetEditCategory.ANDROID_PROP) return false
        return try {
            Shell.cmd("settings delete ${category.settingsNamespace} $key").exec().isSuccess
        } catch (e: Exception) {
            false
        }
    }

    private fun writeCommand(category: SetEditCategory, key: String, value: String): String {
        val escaped = value.replace("\"", "\\\"")
        return if (category == SetEditCategory.ANDROID_PROP) {
            "setprop $key \"$escaped\""
        } else {
            "settings put ${category.settingsNamespace} $key \"$escaped\""
        }
    }

    private fun parseSettingsLine(line: String, category: SetEditCategory): SetEditItem? {
        val parts = line.split("=", limit = 2)
        if (parts.size != 2) return null
        val key = parts[0].trim()
        // مفتاح فارغ صفٌّ بلا معنى: لايمكن قراءته ولا البحث عنه، وتكتبه الشاشة لاحقًا إلى الجهاز
        // باسم `settings put <ns> ""` — أي أمر يكتب في لا شيء. يُسقَط.
        if (key.isEmpty()) return null
        return SetEditItem(key, parts[1].trim(), category)
    }

    private fun parseGetpropLine(line: String, category: SetEditCategory): SetEditItem? {
        // getprop output lines look like: [ro.build.version.sdk]: [34]
        val parts = line.split("]: [")
        if (parts.size != 2) return null
        val key = parts[0].removePrefix("[").trim()
        if (key.isEmpty()) return null
        val value = parts[1].removeSuffix("]").trim()
        return SetEditItem(key, value, category)
    }
}
