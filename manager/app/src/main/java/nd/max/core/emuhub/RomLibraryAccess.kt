/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 *
 * MaxManager proprietary source. See LICENSE at the repository root: this file is
 * MaxManager-owned and carries no third-party licence obligations.
 */

package nd.max.core.emuhub

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile

/**
 * الوصول إلى القرص — كل ما يلمس Android في `emuhub` مجموع هنا، والمجلد `core/emuhub` الباقي نقيّ.
 *
 * **الكتابة الوحيدة في هذا الملفّ هي في مفاتيح التطبيق نفسه** (`SharedPreferences`): الفهرس
 * والروابط. **صفر كتابة إلى ملفّات المستخدم، وصفر قراءة لمحتوى أيّ ملفّ** — الاسم والحجم والختم
 * من `DocumentFile` وحده. لعبة بحجم ٤ جيجابايت لا تُفتح هنا أبدًا.
 *
 * **وسبب وجوده أصلًا:** مجلد ممنوح بصلاحية مستمرّة قد يفقدها بعد إعادة الإقلاع أو بعد إزالة
 * المجلد من مزوّده، فتصير قائمة فارغة تُقرأ خطأً «لا ألعاب». لذلك [granted] تُفحص **قبل** المشي،
 * والنتيجة تحمل [ScanOutcome.NO_ACCESS] بنصّها لا صمتًا.
 */
object RomLibraryAccess {
    private const val PREFS = "settings"
    private const val KEY_FOLDERS = "emulator_folders"
    private const val KEY_DOCUMENTS = "emulator_documents"
    private const val KEY_INDEX = "emulator_index"

    /** ما انتهى إليه المسح — النقص يُعلَن بحالته لا يُطوى. */
    enum class ScanOutcome { OK, EMPTY, NO_ACCESS, TRUNCATED, FAILED }

    data class ScanResult(
        val outcome: ScanOutcome,
        val files: List<RomFile>,
    )

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ───────────────────────────── الصلاحيات والمخزن ─────────────────────────────

    /** هل ما زالت الصلاحية المستمرّة قائمة لهذا الشجرة/الملفّ؟ */
    fun granted(context: Context, uri: String): Boolean = runCatching {
        context.contentResolver.persistedUriPermissions.any { it.uri.toString() == uri && it.isReadPermission }
    }.getOrDefault(false)

    fun folders(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_FOLDERS, emptySet()).orEmpty().toSet()

    fun documents(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_DOCUMENTS, emptySet()).orEmpty().toSet()

    /** حفظ المجلّدات والروابط في معاملة واحدة: لا تُحفظ نصف نيّة. */
    fun saveSources(context: Context, folders: Set<String>, documents: Set<String>): Boolean = runCatching {
        prefs(context).edit()
            .putStringSet(KEY_FOLDERS, folders.take(RomIndexStore.MAX_FOLDERS).toSet())
            .putStringSet(KEY_DOCUMENTS, documents.take(RomIndexStore.MAX_FILES).toSet())
            .commit()
    }.getOrDefault(false)

    /** آخر فهرس محفوظ — يُرسم به الرفّ فورًا عند الفتح، ثم يُستبدل بنتيجة مسح طازج. */
    fun index(context: Context): List<RomFile> =
        RomIndexStore.decodeAll(prefs(context).getStringSet(KEY_INDEX, emptySet()).orEmpty())

    fun saveIndex(context: Context, files: List<RomFile>): Boolean = runCatching {
        prefs(context).edit()
            .putStringSet(KEY_INDEX, files.take(RomIndexStore.MAX_FILES).map(RomIndexStore::encode).toSet())
            .commit()
    }.getOrDefault(false)

    /**
     * يمشي المجلّدات الممنوحة ويبني الفهرس، ويحفظه.
     *
     * يُنادى على `Dispatchers.IO` فقط. والحدّان (عمق · عدد) يُنتجان [ScanOutcome.TRUNCATED] بدل
     * أن يعود نقصٌ صامت.
     */
    fun scan(context: Context, folders: Set<String>): ScanResult {
        val resolver = context.contentResolver
        val found = mutableListOf<RomFile>()
        var truncated = false
        var denied = false

        for (folder in folders) {
            if (!granted(context, folder)) {
                denied = true
                continue
            }
            val root = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(folder)) }.getOrNull()
            if (root == null || !root.isDirectory) {
                denied = true
                continue
            }
            walk(root, folder, 0, found) { truncated = true }
        }

        // ملفّات مفردة أضافها المستخدم: تُفهرس بلا مشي، وحجمها من المزوّد.
        for (uri in documents(context)) {
            if (found.size >= RomIndexStore.MAX_FILES) { truncated = true; break }
            if (found.any { it.uri == uri }) continue
            if (!granted(context, uri)) { denied = true; continue }
            describe(context, resolver, uri)?.let { found += it }
        }

        val outcome = when {
            folders.isEmpty() && documents(context).isEmpty() -> ScanOutcome.EMPTY
            truncated -> ScanOutcome.TRUNCATED
            denied && found.isEmpty() -> ScanOutcome.NO_ACCESS
            found.isEmpty() -> ScanOutcome.EMPTY
            else -> ScanOutcome.OK
        }
        val files = found.distinctBy { it.uri }.take(RomIndexStore.MAX_FILES)
        saveIndex(context, files)
        return ScanResult(outcome, files)
    }

    /** مشي شجرة بعمق محدود. [onTruncated] تُنادى مرة عند بلوغ السقف — والنقص يُعلَن لا يُطوى. */
    private fun walk(dir: DocumentFile, parent: String, depth: Int, out: MutableList<RomFile>, onTruncated: () -> Unit) {
        if (depth >= RomIndexStore.MAX_DEPTH) return
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return
        for (child in children) {
            if (out.size >= RomIndexStore.MAX_FILES) { onTruncated(); return }
            when {
                child.isDirectory -> walk(child, child.uri.toString(), depth + 1, out, onTruncated)
                child.isFile -> {
                    val name = child.name ?: continue
                    if (RomSystems.extensionOf(name).isEmpty()) continue
                    out += RomFile(
                        uri = child.uri.toString(),
                        name = name,
                        parent = parent,
                        sizeBytes = child.length().takeIf { it > 0 },
                        lastModified = child.lastModified().takeIf { it > 0 },
                    )
                }
            }
        }
    }

    /** وصف ملفّ واحد من مزوّده بلا فتحه: الاسم والحجم والختم. */
    private fun describe(context: Context, resolver: android.content.ContentResolver, uri: String): RomFile? {
        val name = runCatching {
            resolver.query(Uri.parse(uri), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: uri.substringAfterLast('/')
        val file = runCatching { DocumentFile.fromSingleUri(context, Uri.parse(uri)) }.getOrNull()
        return RomFile(
            uri = uri,
            name = name,
            parent = "",
            sizeBytes = file?.length()?.takeIf { it > 0 },
            lastModified = file?.lastModified()?.takeIf { it > 0 },
        )
    }

    /**
     * يفتح ملفًّا بمحاكٍ مثبَّت عبر منتقي النظام.
     *
     * **وهذا ليس تشغيلًا مُثبتًا:** الطلب يُسلَّم للنظام، والذي يبدأ اللعبة فعليًّا تطبيق آخر.
     * لذلك النتيجة `Boolean` تعني «طُلب المنتقي» فقط، والنصّ المعروض يقول ذلك بنصّه.
     */
    fun open(context: Context, file: RomFile): Boolean = runCatching {
        val uri = Uri.parse(file.uri)
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = android.content.ClipData.newRawUri(file.name, uri)
        context.startActivity(Intent.createChooser(intent, null))
        true
    }.getOrDefault(false)

    /** يحتفظ بالصلاحية لرابط واحد. `false` تعني أن الرابط لن يعمل بعد إعادة التشغيل. */
    fun keep(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    }.getOrDefault(false)
}
