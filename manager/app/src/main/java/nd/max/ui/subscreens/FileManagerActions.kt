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
 * عقود شاشة مدير الملفات ودالّة تنفيذ إجراءات التحديد — **خارج ملف الشاشة** لسبب
 * يُقاس: الشاشة كانت تتجاوز حدّ الحجم المعلن في المستودع (١٠٠٠ سطر)، والحدّ ليس ذوقًا
 * بل كلفة قراءة: من يعدّل في الشاشة يقرأ ٩٠٠ سطر لا ١٠٦٣.
 *
 * والمفصول هنا ليس «بقايا»: هو **الجزء الذي يُقرأ بمعزل** — أنواع اللوحة المفتوحة،
 * وطلب النقل، وخريطة الإجراء → النداء، ومواقع التنقّل السريع. أما الشاشة نفسها فتبقى
 * تركيب الحالة والواجهة.
 *
 * والرؤية `internal` لا `private`: الحدّ في Kotlin على مستوى **الملف**، وهذه تصريحات
 * تُستدعى من ملف الشاشة — فالرؤية تُوسَّع إلى الحزمة وحدها، ولا تصير جزءًا من واجهة
 * التطبيق.
 */
package nd.max.ui.subscreens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.ViewColumn
import androidx.compose.ui.graphics.vector.ImageVector
import nd.max.R
import nd.max.ui.util.FileAction
import nd.max.ui.util.FileArchive
import nd.max.ui.util.FileBrowser
import nd.max.ui.util.FileEntry
import nd.max.ui.util.FileOpRequest
import nd.max.ui.util.FileOperation
import nd.max.ui.util.FilePaneState
import nd.max.ui.util.PaneLayout
import nd.max.ui.util.PaneSide

/** اللوحة المفتوحة بدل اللوحين: تفاصيل أو معاينة. */
internal sealed interface FilePanel {
    data class Details(val entry: FileEntry) : FilePanel
    data class Preview(val entry: FileEntry) : FilePanel
}

/** طلب نسخ/نقل: أيّ لوح طلبه، وماذا، وإلى أين. */
internal data class TransferRequest(
    val operation: FileOperation,
    val sources: List<String>,
    val from: PaneSide,
)

/** موقع سريع: اسمه في الموارد ومساره — ولا يُخمَّن مسار في الواجهة. */
internal data class QuickLocation(val labelRes: Int, val path: String)

/**
 * المواقع السريعة: الخمسة التي يزورها المستخدم في هذه الشاشة فعلًا.
 *
 * ولا «مواقع ذكية» تُخترع من القرص: قائمة المواقع قرار تصميمي معلن، ومن احتاج غيرها كتب
 * مسارًا أو ثبّت تبويبًا. وكل موقع منها قد لا يكون موجودًا على جهاز بعينه — واللوح سيقول
 * ذلك بنصّه (`Path not found`) بدل أن يُخفي الموقع أو يخمّن بديلًا عنه.
 */
internal fun quickLocations(): List<QuickLocation> = listOf(
    QuickLocation(R.string.max_files_location_root, "/"),
    QuickLocation(R.string.max_files_location_storage, "/sdcard"),
    QuickLocation(R.string.max_files_location_download, "/sdcard/Download"),
    QuickLocation(R.string.max_files_location_appdata, "/sdcard/Android/data"),
    QuickLocation(R.string.max_files_location_system, "/system"),
)

internal fun layoutLabel(layout: PaneLayout): Int = when (layout) {
    PaneLayout.Auto -> R.string.max_files_layout_auto
    PaneLayout.SideBySide -> R.string.max_files_layout_side
    PaneLayout.Stacked -> R.string.max_files_layout_stack
}

internal fun layoutIcon(layout: PaneLayout): ImageVector = when (layout) {
    PaneLayout.Auto -> Icons.Rounded.AspectRatio
    PaneLayout.SideBySide -> Icons.Rounded.ViewColumn
    PaneLayout.Stacked -> Icons.Rounded.ViewAgenda
}

/**
 * إجراءات شريط التحديد — دالّة واحدة تأخذ الحالة وترجع الأفعال، فتبقى الشاشة قابلة
 * للقراءة ولا تتفرّع قائمة `when` داخل تركيب الواجهة.
 *
 * وملاحظة تحمي بيانات: **الفكّ يذهب إلى مجلد اللوح الحالي** لا إلى حوار وجهة إضافي،
 * و**الضغط** يبني اسم الأرشيف من محرّك الأرشيف نفسه ([FileArchive.archiveNameFor]) فلا
 * يفترق اسم الأرشيف الموعود عن اسمه المكتوب.
 */
internal fun onSelectionAction(
    side: PaneSide,
    action: FileAction,
    pane: FilePaneState,
    onOpenPanel: (FilePanel) -> Unit,
    onRename: (FileEntry) -> Unit,
    onDelete: (List<String>) -> Unit,
    onTransfer: (FileOperation, List<String>) -> Unit,
    onRun: (PaneSide, FileOpRequest) -> Unit,
    onClear: () -> Unit,
) {
    val sources = pane.selection.paths.toList().sorted()
    // المدخلات المختارة فعلًا، لا المسارات وحدها: نوع المدخل (أرشيف؟) يحدّد الإجراء.
    val chosen = pane.entries.filter { it.path in pane.selection.paths }
    when (action) {
        FileAction.Copy -> onTransfer(FileOperation.Copy, sources)
        FileAction.Move -> onTransfer(FileOperation.Move, sources)
        FileAction.Delete -> onDelete(sources)
        FileAction.Clear -> onClear()
        FileAction.Compress -> {
            val first = chosen.firstOrNull() ?: return
            val archive = FileBrowser.childPath(pane.path, FileArchive.archiveNameFor(first.name))
            onRun(side, FileOpRequest(FileOperation.Compress, sources = sources, destination = archive))
        }
        FileAction.Extract -> {
            val archive = chosen.singleOrNull() ?: return
            onRun(
                side,
                FileOpRequest(
                    operation = FileOperation.Extract,
                    sources = listOf(archive.path),
                    destination = pane.path,
                )
            )
        }
        FileAction.Rename -> chosen.singleOrNull()?.let(onRename) ?: return
        FileAction.Details -> onOpenPanel(FilePanel.Details(chosen.singleOrNull() ?: return))
    }
}
