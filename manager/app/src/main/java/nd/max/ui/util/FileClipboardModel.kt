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
 * `MT-FM` — **حافظة الملفات**: نسخ/قصّ ← لصق.
 *
 * ولماذا حافظة بدل حوار «إلى أين؟»: حوار الوجهة يطلب من المستخدم أن **يكتب** مسارًا
 * أو يثق بأن اللوح الآخر هو المقصد. والحافظة تفعل ما يفعله كل مستخدم نظام ملفات:
 * يعرض ما نسخ، يتنقّل بحرّية، ثم يلصق حيث يقف. والنتيجة أن وجهة اللصق **مقروءة**
 * (المجلد المفتوح أمام عينيه) لا مُخمَّنة.
 *
 * والقرارات كلها هنا: ما يُبنى من الطلب، ومتى يكون اللصق بلا معنى أصلًا. أما الحكم
 * النهائي فـ[FileOpGuard] — لا تُكتب قواعد الحماية مرّتين.
 */
package nd.max.ui.util

/** نمط الحافظة: نسخ يُبقي المصدر، وقصّ ينقله. */
enum class ClipboardMode { Copy, Cut }

/**
 * ما تحمله الحافظة: النمط · المسارات · المجلد الذي أُخذت منه.
 *
 * و`origin` ليس للزينة: به يُعلَن للمستخدم «مأخوذة من كذا»، وبه يُعرف أن قصًّا إلى
 * المصدر نفسه بلا معنى فلا يُعرض زرّ لصق كاذب.
 */
data class FileClipboard(
    val mode: ClipboardMode,
    val sources: List<String>,
    val origin: String,
) {
    val count: Int get() = sources.size

    val isNotEmpty: Boolean get() = sources.isNotEmpty()

    /** ماذا يبقى بعد لصق ناجح: «قصّ» يُفرّغ، و«نسخ» يبقى ليُلصق في مكان آخر. */
    fun afterPaste(): FileClipboard? = if (mode == ClipboardMode.Cut) null else this
}

object FileClipboardRules {

    /** حافظة فارغة ليست حافظة: تُعاد `null` فيُخفى الشريط كله بدل شريط يقول «٠ عنصر». */
    fun of(mode: ClipboardMode, sources: List<String>, origin: String): FileClipboard? {
        val cleaned = sources.map(FileBrowser::normalize).filter { it.isNotEmpty() }.distinct()
        if (cleaned.isEmpty()) return null
        return FileClipboard(mode = mode, sources = cleaned.sorted(), origin = FileBrowser.normalize(origin))
    }

    /**
     * الطلب الذي يُنفَّذ عند اللصق: «قصّ» نقل و«نسخ» نسخ — ثم يمرّ الطلب على الحرس
     * نفسه الذي تمرّ عليه كل العمليات.
     */
    fun pasteRequest(clipboard: FileClipboard, destination: String): FileOpRequest = FileOpRequest(
        operation = if (clipboard.mode == ClipboardMode.Cut) FileOperation.Move else FileOperation.Copy,
        sources = clipboard.sources,
        destination = FileBrowser.normalize(destination),
    )

    /**
     * هل اللصق في هذا المجلد بلا معنى؟
     *
     * يخصّ **القصّ فقط**: كل عنصر مقصوص أصلُه هو هذا المجلد نفسه، فلا يوجد ما يُنقل.
     * أما النسخ في المجلد نفسه فله معنى دائمًا (نسخة ثانية بالاسم نفسه بعد إعادة
     * تسمية) — ولذلك يُعرض ويُحلّ تعارضه بحوار لا برفض.
     */
    fun pointlessHere(clipboard: FileClipboard, destination: String): Boolean {
        if (clipboard.mode != ClipboardMode.Cut) return false
        val target = FileBrowser.normalize(destination)
        return clipboard.sources.all { source ->
            val parent = FileBrowser.parentOf(source) ?: "/"
            FileBrowser.normalize(parent) == target
        }
    }

    /** الحافظة كما تُعرض: عددها والمسار الذي أُخذت منه — بلا نصّ جاهز (الشاشة تترجم). */
    fun summary(clipboard: FileClipboard): Pair<Int, String> = clipboard.count to clipboard.origin
}
