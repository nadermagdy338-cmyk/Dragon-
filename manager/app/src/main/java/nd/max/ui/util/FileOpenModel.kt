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
 * `MT-FM` — **إلى أين تذهب النقرة**: مجلد يُدخل، أو نصّ يُفتح في المحرّر، أو ملف يُسلَّم
 * لتطبيق خارجي (قرار المالك: النقرة تفتح بالتطبيق الافتراضي كما في MT).
 *
 * ولماذا نموذج: الفرق بين «فتح في المحرّر» و«تسليم لتطبيق آخر» يحدّد ما يراه المستخدم
 * بعد النقرة، ولون الصفّ ورمزه يُشتقّان منه. وحدّ الحجم معلن هنا فلا يُفتح ملف بحجم
 * قرص في محرّر يبتلعه.
 */
package nd.max.ui.util

import java.util.Locale

/** مصير النقرة. ثلاثة مغلقة: لا يوجد «لا شيء يحدث» في مدير ملفات. */
enum class FileOpenRoute { EnterFolder, TextEditor, External }

object FileOpenPlan {

    /**
     * الحدّ المعلَن لفتح النصّ في المحرّر. فوقه يُسلَّم الملف لتطبيق خارجي بدل أن
     * يُفتح محرّر لا يستطيع التعامل معه — والقرار يُعلَن للمستخدم لا يُخفى.
     */
    const val MAX_EDITOR_BYTES: Long = 2L * 1024L * 1024L

    /**
     * امتدادات النصوص. القائمة مغلقة ومعلنة عن قصد: «كل ما ليس صورةً نصٌّ» تخمين،
     * والتخمين يفتح محرّرًا على ملف ثنائي فيعرض رمزًا لا معنى له.
     */
    private val TEXT_EXTENSIONS = setOf(
        "txt", "md", "log", "json", "xml", "yml", "yaml", "ini", "cfg", "conf", "prop",
        "properties", "sh", "rc", "csv", "smali", "kt", "java", "js", "ts", "html", "css",
        "toml", "rules", "desktop", "list", "service", "te",
    )

    fun routeOf(entry: FileEntry): FileOpenRoute = when {
        entry.isDirectory -> FileOpenRoute.EnterFolder
        isTextLike(entry) -> FileOpenRoute.TextEditor
        else -> FileOpenRoute.External
    }

    /**
     * هل يُفتح هذا الملف في المحرّر؟
     *
     * وحجم **لم يُقرأ** (`null`) لا يمنع: المحرّر نفسه يقيس الحجم عند الفتح ويعلن
     * الامتناع إن تجاوز الحدّ — أما منع الفتح بناءً على `null` فكان سيحجب ملفًا نصيًّا
     * سليمًا لأن صفة واحدة سقطت في القراءة.
     */
    fun isTextLike(entry: FileEntry): Boolean {
        if (entry.isDirectory) return false
        if (extensionOf(entry.name) !in TEXT_EXTENSIONS) return false
        val size = entry.sizeBytes ?: return true
        return size <= MAX_EDITOR_BYTES
    }

    /** الامتداد بصيغة موحّدة، أو نصّ فارغ إن لم يكن للاسم امتداد. */
    fun extensionOf(name: String): String {
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.lastIndex) return ""
        return name.substring(dot + 1).lowercase(Locale.ROOT)
    }
}
