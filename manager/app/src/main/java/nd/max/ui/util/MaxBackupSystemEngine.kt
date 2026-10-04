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
import nd.max.core.platform.EventLog

import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import android.provider.UserDictionary
import java.io.File
import nd.max.ui.util.MaxBackupEngine.SYSTEM_PKG
import nd.max.ui.util.MaxBackupModel.ComponentKind
import nd.max.ui.util.MaxBackupModel.Manifest
import nd.max.ui.util.MaxBackupModel.ManifestEntry
import nd.max.ui.util.MaxBackupSystem.Availability
import nd.max.ui.util.MaxBackupSystem.Component
import nd.max.ui.util.MaxBackupSystem.Kind
import nd.max.ui.util.MaxBackupSystem.Method

/**
 * `Max Backup` — تنفيذ **بيانات النظام**.
 *
 * مسلكان لا مسلك واحد، وكل منهما يعلن صدقه:
 *
 * - **`ROOT_FILES`** (واي‑فاي · بلوتوث): ملفات نظام تُقرأ بالجذر وتُؤرشف بـ`tar`، وتُسترجع
 *   بفكّ الأرشيف — **بعد التحقّق من مساراته** مقابل قائمة مُعلَنة ([tarEntriesAllowed]).
 * - **`PROVIDER`** (جهات · مكالمات · رسائل): صفوف تُقرأ بـ`ContentResolver` وتُؤرشف **JSON**،
 *   وتُسترجع **بالدمج**: يُدرَج الغائب فقط، ويُعَدّ ما تُخُطّي وما لا مفتاح له.
 *
 * ولماذا JSON لا نسخة من `.db`: أخذ نسخة من SQLite يعمل عليه مزوّد تعني نسخة قد تكون نصف
 * مكتوبة. والمزوّد نفسه يضمن الاتّساق إن قرأناه عبر واجهته. ليست تفصيلة شكلية — هي الفرق بين
 * أرشيف يُسترجع وأرشيف يبدو سليمًا.
 */
object MaxBackupSystemEngine {

    /** معرّف الشاشة في السجل. */
    const val SCREEN = "MaxBackupSystem"

    /** معرّفا المرحلتين — عامّان ليُترجَما في الشاشة، كبقية مراحل النسخ. */
    const val STAGE_ROOT = "system_files"
    const val STAGE_PROVIDER = "system_rows"

    private const val PROVIDER_SCHEMA = 1

    // ────────────────────────────────────────────────────────────────────────
    // الجرد
    // ────────────────────────────────────────────────────────────────────────

    fun inventory(context: Context, hasRoot: Boolean, granted: Set<String>): MaxBackupSystem.Plan =
        MaxBackupSystem.Plan(
            hasRoot = hasRoot,
            components = MaxBackupSystem.SOURCES.map { source ->
                when (source.method) {
                    Method.ROOT_FILES -> rootFilesComponent(source, hasRoot)
                    Method.PROVIDER -> providerComponent(context, source, granted)
                }
            },
        )

    /**
     * جرد ملفات النظام: نجرّب **كل** مسار مُعلَن ونُعلن ما وُجد فعلًا لا ما تمنّيناه.
     * وغياب الملف عند وجود الجذر يُقاس **صفرًا** (بحثنا ولم نجد)، لا `null` (لم نبحث).
     */
    private fun rootFilesComponent(source: MaxBackupSystem.Source, hasRoot: Boolean): Component {
        if (!hasRoot) {
            return Component(source.kind, Availability.NEEDS_ROOT, sizeBytes = null)
        }
        val resolved = source.paths.filter(::fileExists)
        if (resolved.isEmpty()) {
            return Component(source.kind, Availability.UNAVAILABLE, sizeBytes = 0L)
        }
        val sizes = resolved.mapNotNull { path ->
            MaxBackupEngine.shell("du -sk ${MaxBackupEngine.quote(path)} 2>/dev/null")
                ?.firstOrNull()?.split(Regex("\\s+"))?.firstOrNull()?.toLongOrNull()
        }
        return Component(
            kind = source.kind,
            availability = Availability.AVAILABLE,
            // لا نجمع إن نقص قياس مسار: مجموع ناقص يُعرَض كأنه كامل.
            sizeBytes = if (sizes.size == resolved.size) sizes.sum() * 1024L else null,
            resolved = resolved,
        )
    }

    private fun fileExists(path: String): Boolean =
        MaxBackupEngine.shell("test -f ${MaxBackupEngine.quote(path)} && echo y")?.firstOrNull() == "y"

    /**
     * جرد مزوّد: الإذن **شرط** لا خيار. وبلا إذن لا نقرأ ولا نُقدّر — `NEEDS_PERMISSION`
     * بحجم `null`. أما بعد الإذن فالحجم هو **ما سيُكتب فعلًا** (بايتات JSON)، لا حجم قاعدة
     * بيانات المزوّد: الأولى هي ما سننسخه، والثانية رقم لا علاقة له بالنسخة.
     */
    private fun providerComponent(
        context: Context,
        source: MaxBackupSystem.Source,
        granted: Set<String>,
    ): Component {
        val required = source.permissions
        val have = required.count { it in granted }
        val permissionPart = have to required.size
        if (required.isNotEmpty() && have < required.size) {
            return Component(
                kind = source.kind,
                availability = Availability.NEEDS_PERMISSION,
                sizeBytes = null,
                grantedPermissions = permissionPart.first,
                requiredPermissions = permissionPart.second,
            )
        }
        val rows = readRows(context, source.kind) ?: return Component(
            kind = source.kind,
            availability = Availability.UNKNOWN,
            sizeBytes = null,
            grantedPermissions = permissionPart.first,
            requiredPermissions = permissionPart.second,
        )
        if (rows.isEmpty()) {
            return Component(
                kind = source.kind,
                availability = Availability.UNAVAILABLE,
                sizeBytes = 0L,
                rowCount = 0,
                grantedPermissions = permissionPart.first,
                requiredPermissions = permissionPart.second,
            )
        }
        return Component(
            kind = source.kind,
            availability = Availability.AVAILABLE,
            sizeBytes = encodeRows(source.kind, rows).toByteArray(Charsets.UTF_8).size.toLong(),
            rowCount = rows.size,
            grantedPermissions = permissionPart.first,
            requiredPermissions = permissionPart.second,
        )
    }

    // ────────────────────────────────────────────────────────────────────────
    // الإنشاء
    // ────────────────────────────────────────────────────────────────────────

    fun create(
        context: Context,
        plan: MaxBackupSystem.Plan,
        scope: MaxBackupSystem.Scope,
        onStage: (String) -> Unit = {},
    ): MaxBackupEngine.CreateOutcome {
        val startedAt = System.currentTimeMillis()
        // `ensureRoot` لا `backupsRoot`: هذه دالة قرص، فتُثبَّت هنا بفحص كتابة فعلي بدل
        // الاعتماد على قرار الصلاحية وحده.
        val folder = File(MaxBackupEngine.ensureRoot(context), SYSTEM_PKG)
            .let { File(it, MaxBackupModel.folderName(startedAt)) }
        if (!folder.exists() && !folder.mkdirs()) {
            EventLog.error(SCREEN, "create_folder")
            return MaxBackupEngine.CreateOutcome(false, null, 0, 0, listOf("folder_unwritable"), null)
        }

        val selected = plan.components.filter {
            scope.selects(it.kind) && it.availability == Availability.AVAILABLE
        }
        if (selected.isEmpty()) {
            folder.delete()
            return MaxBackupEngine.CreateOutcome(false, null, 0, 0, listOf("nothing_selected"), null)
        }

        val entries = mutableListOf<ManifestEntry>()
        val omissions = mutableListOf<String>()
        var failedStage: String? = null

        selected.forEach { component ->
            val source = MaxBackupSystem.source(component.kind) ?: return@forEach
            when (source.method) {
                Method.ROOT_FILES -> {
                    onStage(STAGE_ROOT)
                    val target = File(folder, source.kind.entryFile)
                    if (tarSystemFiles(component.resolved, target)) {
                        entries += MaxBackupEngine.entryFor(target, ComponentKind.SYSTEM, folder)
                    } else {
                        omissions += source.kind.id
                        failedStage = failedStage ?: STAGE_ROOT
                        target.delete()
                    }
                }

                Method.PROVIDER -> {
                    onStage(STAGE_PROVIDER)
                    val rows = readRows(context, source.kind)
                    if (rows == null) {
                        omissions += source.kind.id
                        failedStage = failedStage ?: STAGE_PROVIDER
                        return@forEach
                    }
                    val target = File(folder, source.kind.entryFile)
                    if (runCatching { target.writeText(encodeRows(source.kind, rows)) }.isSuccess) {
                        entries += MaxBackupEngine.entryFor(target, ComponentKind.SYSTEM, folder)
                    } else {
                        omissions += source.kind.id
                        failedStage = failedStage ?: STAGE_PROVIDER
                    }
                }
            }
        }

        onStage(MaxBackupEngine.STAGE_MANIFEST)

        // التحقّق قبل إعلان النجاح: نعيد قراءة كل ملف كتبناه ونقارن بصمته بما سجّلنا.
        val verifiedEntries = entries.map { entry ->
            val actual = MaxBackupEngine.sha256Of(File(folder, entry.file).absolutePath)
            if (entry.sha256 != null && actual != null && entry.sha256.equals(actual, ignoreCase = true)) {
                entry
            } else {
                omissions += "unverified:${entry.file}"
                failedStage = failedStage ?: MaxBackupEngine.STAGE_MANIFEST
                entry
            }
        }
        val verified = verifiedEntries.isNotEmpty() && omissions.none { it.startsWith("unverified:") }

        val manifest = Manifest(
            pkg = SYSTEM_PKG,
            label = null,
            versionCode = 0,
            versionName = null,
            createdAtMs = startedAt,
            deviceModel = MaxBackupEngine.currentDeviceModel(context),
            soc = ProfileSharing.currentSoc(),
            hadRoot = plan.hasRoot,
            encrypted = false,
            complete = verified,
            entries = verifiedEntries,
            omissions = omissions,
        )
        if (!MaxBackupEngine.writeManifest(folder, manifest)) {
            return MaxBackupEngine.CreateOutcome(
                false, folder.absolutePath, 0, 0, omissions, MaxBackupEngine.STAGE_MANIFEST,
            )
        }

        try {
            EventLog.result(
                screen = SCREEN,
                action = "system_backup",
                target = SYSTEM_PKG,
                success = verified,
                durationMs = System.currentTimeMillis() - startedAt,
            )
        } catch (_: Exception) {
            // التسجيل لا يُسقط نسخة نجحت.
        }

        return MaxBackupEngine.CreateOutcome(
            success = verified,
            folder = folder.absolutePath,
            bytes = verifiedEntries.sumOf { it.bytes },
            entryCount = verifiedEntries.size,
            omissions = omissions,
            failedStage = failedStage,
        )
    }

    /**
     * أرشفة ملفات نظام بمسارات **مطلقة** (`-C /`).
     *
     * وهذا يجعل الفكّ يُعيد الملف إلى مكانه بالضبط — **ولذلك** صار لزامًا التحقّق من مدخلات
     * الأرشيف قبل الفكّ، وإلا صار أرشيف قابل للتعديل طريقًا للكتابة في أي مكان باسم «استرجاع».
     */
    private fun tarSystemFiles(paths: List<String>, target: File): Boolean {
        if (paths.isEmpty()) return false
        val quoted = paths.map { MaxBackupEngine.quote(it.removePrefix("/")) }.joinToString(" ")
        return MaxBackupEngine.shell(
            "tar -c -z -f ${MaxBackupEngine.quote(target.absolutePath)} -C / $quoted 2>/dev/null",
            "test -s ${MaxBackupEngine.quote(target.absolutePath)} && echo y",
        )?.firstOrNull() == "y"
    }

    /**
     * **حرس مسارات الأرشيف.** كل مدخل في `tar -t` يجب أن يكون أحد المسارات المُعلَنة لهذه
     * الفئة **بالضبط**، بعد تطبيع `./` والشرطة البادئة.
     *
     * وليس تزيّدًا: الأرشيفات تُنسخ وتُشارَك وتُخزَّن. بلا هذا الحرس يصبح مستند قابل للتعديل
     * طريقًا للكتابة في `/system` أو `/data/adb` باسم «استرجاع نسخة».
     */
    fun tarEntriesAllowed(kind: Kind, entries: List<String>): Boolean {
        val allowed = MaxBackupSystem.source(kind)?.paths.orEmpty()
            .map(::normalizeTarPath)
            .toSet()
        if (allowed.isEmpty()) return false
        val actual = entries.map(::normalizeTarPath).filter { it.isNotEmpty() }
        return actual.isNotEmpty() && actual.all { it in allowed }
    }

    private fun normalizeTarPath(raw: String): String =
        raw.trim().removePrefix("./").removePrefix("/").trimEnd('/')

    fun tarEntries(archive: File): List<String>? =
        MaxBackupEngine.shell("tar -t -z -f ${MaxBackupEngine.quote(archive.absolutePath)} 2>/dev/null")

    // ────────────────────────────────────────────────────────────────────────
    // الاسترجاع
    // ────────────────────────────────────────────────────────────────────────

    /**
     * نتيجة مفصّلة بالعدّ، لأن «تم» ليست نتيجة: المستخدم يحتاج أن يعرف كم صفًّا أُدرج، وكم
     * تُخُطّي لوجوده، وكم صفًّا كان بلا مفتاح فلا نضمن عدم تكراره.
     */
    data class SystemRestoreOutcome(
        val success: Boolean,
        val failedKind: Kind?,
        val block: MaxBackupSystem.RestoreBlock,
        val inserted: Int = 0,
        val skipped: Int = 0,
        val undedupable: Int = 0,
    )

    fun restore(
        context: Context,
        handle: MaxBackupModel.Handle,
        granted: Set<String>,
        onStage: (String) -> Unit = {},
    ): SystemRestoreOutcome {
        val folder = File(handle.folder)
        val manifest = MaxBackupEngine.readManifest(folder)
            ?: return SystemRestoreOutcome(false, null, MaxBackupSystem.RestoreBlock.EMPTY)
        val verdicts = MaxBackupEngine.verify(handle)
        val hasRoot = MaxBackupEngine.hasRoot()

        val present = manifest.entries.mapNotNull { entry ->
            Kind.entries.firstOrNull { it.entryFile == entry.file }?.let { it to entry }
        }
        if (present.isEmpty()) {
            return SystemRestoreOutcome(false, null, MaxBackupSystem.RestoreBlock.EMPTY)
        }

        var inserted = 0
        var skipped = 0
        var undedupable = 0
        var failedKind: Kind? = null

        present.forEach { (kind, entry) ->
            val source = MaxBackupSystem.source(kind) ?: return@forEach
            val decision = MaxBackupSystem.restoreDecision(
                kind = kind,
                entryCount = 1,
                availability = Availability.AVAILABLE,
                hasRoot = hasRoot,
                permissionsGranted = source.permissions.all { it in granted },
            )
            // الفحص **لكل مدخل حين يُسترجع**، لا مرّة واحدة في الأوّل: نسخة تحمل فئة تالفة
            // وأخرى سليمة يجب أن تُنقذ الثانية، لا أن تُسقَط كلها بسبب الأولى.
            if (!decision.allowed || verdicts[entry.file] != MaxBackupModel.Integrity.VERIFIED) {
                failedKind = failedKind ?: kind
                return@forEach
            }

            when (source.method) {
                Method.ROOT_FILES -> {
                    onStage(STAGE_ROOT)
                    val archive = File(folder, entry.file)
                    val listed = tarEntries(archive)
                    if (listed == null || !tarEntriesAllowed(kind, listed)) {
                        // أرشيف يحمل مسارًا لم نُعلنه: لا نفكّه. الحرس **قبل** الكتابة لا بعدها.
                        EventLog.error(SCREEN, "restore_path_rejected:${kind.id}")
                        failedKind = failedKind ?: kind
                        return@forEach
                    }
                    val ok = MaxBackupEngine.shell(
                        "tar -x -z -f ${MaxBackupEngine.quote(archive.absolutePath)} -C / 2>/dev/null && echo y",
                    )?.firstOrNull() == "y"
                    if (ok) {
                        source.paths.forEach { path ->
                            MaxBackupEngine.shell("restorecon ${MaxBackupEngine.quote(path)} 2>/dev/null")
                        }
                    } else {
                        failedKind = failedKind ?: kind
                    }
                }

                Method.PROVIDER -> {
                    onStage(STAGE_PROVIDER)
                    val rows = readProviderArchive(File(folder, entry.file), kind)
                    if (rows == null) {
                        failedKind = failedKind ?: kind
                        return@forEach
                    }
                    val existing = readRows(context, kind)
                        ?.mapNotNull { MaxBackupSystem.naturalKey(kind, it) }
                        ?.toSet()
                        .orEmpty()
                    val merge = MaxBackupSystem.mergePlan(kind, existing, rows)
                    val added = applyRows(context, kind, merge.insert)
                    if (added == null) {
                        failedKind = failedKind ?: kind
                    } else {
                        inserted += added
                        skipped += merge.skippedExisting
                        undedupable += merge.undedupable
                    }
                }
            }
        }

        val success = failedKind == null
        try {
            EventLog.result(
                screen = SCREEN,
                action = "system_restore",
                target = SYSTEM_PKG,
                success = success,
                durationMs = 0L,
            )
        } catch (_: Exception) {
            // التسجيل لا يُسقط استرجاعًا نجح.
        }

        return SystemRestoreOutcome(
            success = success,
            failedKind = failedKind,
            block = if (success) MaxBackupSystem.RestoreBlock.NONE else MaxBackupSystem.RestoreBlock.UNAVAILABLE,
            inserted = inserted,
            skipped = skipped,
            undedupable = undedupable,
        )
    }

    // ────────────────────────────────────────────────────────────────────────
    // مزوّدات المحتوى
    // ────────────────────────────────────────────────────────────────────────

    /**
     * أعمدة كل مزوّد — من ثوابت المنصّة لا من سلاسل مكتوبة يدويًّا، فإعادة تسمية عمود في
     * إصدار جديد تُكشف وقت التصريف لا وقت التشغيل.
     *
     * و`_id` **لا يُنسخ إلى جهاز آخر**: إنه مرتهن بقاعدة المصدر، ونقله كان سيُنشئ مراجع
     * تشير إلى صفوف لا وجود لها هنا. يُقرأ للسجل ويُسقَط عند الكتابة.
     */
    private val COLUMNS: Map<Kind, List<String>> = mapOf(
        Kind.CALL_LOG to listOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.TYPE,
            CallLog.Calls.CACHED_NAME,
        ),
        Kind.SMS to listOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.BODY,
            Telephony.Sms.READ,
        ),
        Kind.CONTACTS to listOf("display_name", "number", "numbers", "email"),
        // `APP_ID` غير قابل للنقل: معرّف تطبيق على هذا الجهاز، ولا معنى له على غيره.
        Kind.USER_DICTIONARY to listOf(
            UserDictionary.Words.WORD,
            UserDictionary.Words.FREQUENCY,
            UserDictionary.Words.LOCALE,
        ),
    )

    private val NON_PORTABLE = setOf("_id")

    /**
     * قراءة صفوف مزوّد. `null` = **تعذّرت القراءة**، و`emptyList` = قرأنا فعلًا فلم نجد.
     * والفرق بينهما هو الفرق بين «لا أعرف» و«صفر» — وهو نفس الفرق الذي يحكم كل هذا المستودع.
     */
    fun readRows(context: Context, kind: Kind): List<Map<String, String?>>? = runCatching {
        when (kind) {
            Kind.CALL_LOG -> query(context, CallLog.Calls.CONTENT_URI, COLUMNS.getValue(kind))
            Kind.SMS -> query(context, Telephony.Sms.CONTENT_URI, COLUMNS.getValue(kind))
            Kind.CONTACTS -> readContacts(context)
            Kind.USER_DICTIONARY -> query(context, UserDictionary.Words.CONTENT_URI, COLUMNS.getValue(kind))
            Kind.WIFI, Kind.BLUETOOTH -> emptyList()
        }
    }.getOrElse {
        EventLog.error(SCREEN, "read_rows:${kind.id}", it)
        null
    }

    private fun query(context: Context, uri: Uri, projection: List<String>): List<Map<String, String?>> {
        val rows = mutableListOf<Map<String, String?>>()
        context.contentResolver.query(uri, projection.toTypedArray(), null, null, null)?.use { cursor ->
            val names = cursor.columnNames
            while (cursor.moveToNext()) {
                rows += names.associateWith { column ->
                    val index = cursor.getColumnIndex(column)
                    if (index < 0 || cursor.isNull(index)) null else cursor.getString(index)
                }
            }
        }
        return rows
    }

    /**
     * جهات الاتصال **مع أرقامها** — وهذا هو المقصود من «الأرقام» لا الأسماء وحدها.
     *
     * `Contacts` لا يحمل الأرقام (الأرقام في جدول `Data`)، فنجمع من `Phone` و`Email` بحسب
     * `CONTACT_ID`، ونُخرج **صفًّا واحدًا لكل جهة** يحمل كل أرقامها. ولماذا صف واحد: لأن صفًّا
     * لكل رقم كان سيُدرج **جهة مكرّرة لكل رقم** عند الاسترجاع — أي يُضاعف الأشخاص بدل أرقامهم.
     */
    private fun readContacts(context: Context): List<Map<String, String?>> {
        val byContact = linkedMapOf<String, MutableMap<String, String?>>()

        runCatching {
            query(
                context,
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                listOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                ),
            )
        }.getOrNull()?.forEach { row ->
            val id = row[ContactsContract.CommonDataKinds.Phone.CONTACT_ID] ?: return@forEach
            val entry = byContact.getOrPut(id) {
                mutableMapOf(
                    "display_name" to row[ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY],
                    "number" to null,
                    "numbers" to null,
                    "email" to null,
                )
            }
            val number = row[ContactsContract.CommonDataKinds.Phone.NUMBER]
            if (!number.isNullOrBlank()) {
                val all = (entry["numbers"]?.split("\n").orEmpty() + number).distinct()
                if (entry["number"] == null) entry["number"] = number
                entry["numbers"] = all.joinToString("\n")
            }
        }

        runCatching {
            query(
                context,
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                listOf(
                    ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Email.ADDRESS,
                ),
            )
        }.getOrNull()?.forEach { row ->
            val id = row[ContactsContract.CommonDataKinds.Email.CONTACT_ID] ?: return@forEach
            val address = row[ContactsContract.CommonDataKinds.Email.ADDRESS]
            if (address.isNullOrBlank()) return@forEach
            byContact.getOrPut(id) {
                mutableMapOf("display_name" to null, "number" to null, "numbers" to null, "email" to null)
            }["email"] = address
        }

        return byContact.values.map { it as Map<String, String?> }
    }

    /**
     * إدراج الصفوف، ويُعاد **عدد المُدرَج**. وصف يفشل لا يُسقط البقية: إسقاط ٤٠٠ رقم لأن
     * الرقم ٤٠١ له صيغة غير متوقّعة ليس سلوك أداة نسخ احتياطي — بل فقدان بيانات أعلنّا أنه
     * نجح.
     */
    private fun applyRows(context: Context, kind: Kind, rows: List<Map<String, String?>>): Int? {
        if (rows.isEmpty()) return 0
        return when (kind) {
            Kind.CALL_LOG -> insertPlain(context, CallLog.Calls.CONTENT_URI, rows)
            Kind.SMS -> insertPlain(context, Telephony.Sms.CONTENT_URI, rows)
            Kind.CONTACTS -> insertContacts(context, rows)
            Kind.USER_DICTIONARY -> insertPlain(context, UserDictionary.Words.CONTENT_URI, rows)
            Kind.WIFI, Kind.BLUETOOTH -> null
        }
    }

    private fun insertPlain(context: Context, uri: Uri, rows: List<Map<String, String?>>): Int {
        val resolver = context.contentResolver
        var inserted = 0
        rows.forEach { row ->
            val values = ContentValues()
            row.forEach inner@{ (key, value) ->
                if (key in NON_PORTABLE || value == null) return@inner
                val asNumber = value.toLongOrNull()
                if (asNumber != null && (key == CallLog.Calls.DATE || key == CallLog.Calls.DURATION ||
                        key == CallLog.Calls.TYPE || key == Telephony.Sms.DATE ||
                        key == Telephony.Sms.TYPE || key == Telephony.Sms.READ ||
                        key == UserDictionary.Words.FREQUENCY)
                ) {
                    values.put(key, asNumber)
                } else {
                    values.put(key, value)
                }
            }
            if (runCatching { resolver.insert(uri, values) }.getOrNull() != null) inserted++
        }
        return inserted
    }

    /**
     * إدراج جهة: صف `RawContacts` أوّلًا (كما يفعل مستورد vCard)، ثم صفوف `Data` لاسمها
     * وأرقامها وبريدها — لأن `Data` تشير إلى `RAW_CONTACT_ID` الذي **لا يُنسخ** من جهاز آخر،
     * بل يُنشأ الآن ويُربط بالمرجع `withValueBackReference`.
     */
    private fun insertContacts(context: Context, rows: List<Map<String, String?>>): Int {
        val resolver = context.contentResolver
        var inserted = 0
        rows.forEach { row ->
            val name = row["display_name"]?.takeIf { it.isNotBlank() }
            val numbers = row["numbers"]?.split("\n")?.map(String::trim)?.filter { it.isNotEmpty() }.orEmpty()
                .ifEmpty { listOfNotNull(row["number"]?.takeIf { it.isNotBlank() }) }
            val email = row["email"]?.takeIf { it.isNotBlank() }
            if (name == null && numbers.isEmpty() && email == null) return@forEach

            val ops = arrayListOf(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build()
            )
            name?.let {
                ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
                    )
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, it)
                    .build()
            }
            numbers.forEach { number ->
                ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                    )
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
                    .withValue(
                        ContactsContract.CommonDataKinds.Phone.TYPE,
                        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
                    )
                    .build()
            }
            email?.let {
                ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
                    )
                    .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, it)
                    .build()
            }

            if (runCatching { resolver.applyBatch(ContactsContract.AUTHORITY, ops) }.isSuccess) inserted++
        }
        return inserted
    }

    // ────────────────────────────────────────────────────────────────────────
    // صيغة الأرشيف (JSON)
    // ────────────────────────────────────────────────────────────────────────

    /**
     * صيغة صريحة بالنصّ: تُقرأ بعين، وتُقارَن، وتُدقَّق. و`schema` ليس زخرفة: مستند بإصدار
     * لا نفهمه **يُرفض** ولا يُفسَّر، وهذا هو نفس قرار `ManifestCodec` في نسخ التطبيقات.
     */
    fun encodeRows(kind: Kind, rows: List<Map<String, String?>>): String {
        val root = org.json.JSONObject()
        root.put("schema", PROVIDER_SCHEMA)
        root.put("kind", kind.id)
        root.put("columns", org.json.JSONArray(COLUMNS.getValue(kind)))
        root.put("rowCount", rows.size)
        val array = org.json.JSONArray()
        rows.forEach { row ->
            val node = org.json.JSONObject()
            row.forEach { (key, value) -> if (value != null) node.put(key, value) }
            array.put(node)
        }
        root.put("rows", array)
        return root.toString()
    }

    /** فكّ صارم: إصدار مختلف، أو نوع مختلف، أو صفوف بلا كائن ⇒ `null`. */
    fun readProviderArchive(file: File, kind: Kind): List<Map<String, String?>>? {
        if (!file.isFile) return null
        val root = runCatching { org.json.JSONObject(file.readText()) }.getOrNull() ?: return null
        if (root.optInt("schema", -1) != PROVIDER_SCHEMA) return null
        if (Kind.fromId(root.optString("kind")) != kind) return null
        val array = root.optJSONArray("rows") ?: return null
        return buildList {
            for (index in 0 until array.length()) {
                val node = array.optJSONObject(index) ?: return null
                add(node.keys().asSequence().associateWith { key -> node.optString(key).takeIf { it.isNotEmpty() } })
            }
        }
    }
}
