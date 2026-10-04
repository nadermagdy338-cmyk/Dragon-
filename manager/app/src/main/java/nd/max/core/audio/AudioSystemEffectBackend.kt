/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * الصوت — **الطبقة النظاميّة: القياس والتخطيط والكتابة** (`AQ-09`).
 *
 * **وما تفعله بالضبط، بلا تجميل:** تقرأ ملفّ `audio_effects.xml` الذي يقرؤه `audioserver` من جهازك،
 * **تَدمج** فيه مكتبةً ومؤثّرًا صرّح بهما المستخدم، ثم تكتب وحدة Magisk مستقلّة تحلّ محلّ ذلك الملفّ عند
 * الإقلاع. ولا تُعدَّل بايتةٌ واحدة في ملفّ النظام نفسه: الطبقة وحدها تُكتب، وحذفها يعيد كل شيء.
 *
 * **ولماذا الطبقة الأخطر في الموجة كلها:** لوحة الألوان أو المؤثّرات العاديّة تُنشئ `AudioEffect` في
 * عملية التطبيق؛ وعطبها ينتهي بمغادرة الشاشة. وهذه **تُقاس داخل `audioserver`** — فعطبُها في الملفّ
 * يعني ألّا تُقرأ تهيئة المؤثّرات أصلًا. ولذلك كل كتابة هنا: (١) تُولَّد من ملفّ الجهاز **بإلحاقٍ فقط**
 * ([`audioEffectsOverlay`])، (٢) تمرّ بالمحكِّم بمفتاح `audio_system:`، (٣) **تُقرأ بعد الكتابة**،
 * (٤) وتُتحقَّق صلاحيّة قراءتها لـ`audioserver` قبل أن تُعلَن ناجحة، (٥) ويُعرض تحذير صريح قبلها.
 *
 * **وما لا يُقاس في هذه البيئة يُقال ولا يُدَّعى (§0.1):** هل يقبل `audioserver` الطبقة فعلًا بعد
 * الإقلاع (وسم SELinux · ترتيب التركيب · صلاحية الملفّ)؟ هذا **يحتاج جهازًا**. والمقيس هنا: التخطيط،
 * والرفض بأسبابه، والكتابة من داخل المعاملة، والقراءة بعدها، وصلاحيّة القراءة كشرطٍ للنجاح.
 */
package nd.max.core.audio

import nd.max.core.hardware.ControlOwnership
import nd.max.core.hardware.HardwareControlArbiter
import nd.max.core.hardware.HardwareControlKey
import nd.max.core.hardware.RootFileAccess
import nd.max.core.hardware.SharedHardwareOwnershipStore
import nd.max.core.privilege.PrivilegeManager
import javax.inject.Inject
import javax.inject.Singleton

/** وصف الوحدة كما يقرؤه تطبيق Magisk — سطرٌ واحد (والطيّ في `moduleProp`). */
private const val MODULE_DESCRIPTION =
    "MaxManager system audio effects layer — طبقة مؤثّرات صوت نظاميّة"

/**
 * ما قِيس على الجهاز الآن — **كل حقل قراءة، والغياب `null`** (ADR-07): لا وجود ⇒ لا ملفّ، ولا قراءة ⇒
 * لا حكم. والعدّ في الشاشة يُبنى من [document] (ما على الجهاز) و[planned] (ما سيُكتب) لا من أرقام محفوظة.
 */
data class AudioSystemSnapshot(
    val verdict: AudioSystemLayerVerdict,
    val sourcePath: String?,
    val document: AudioEffectsDocument?,
    val moduleRoot: String,
    val moduleWritable: Boolean,
    val overlayPath: String?,
    val installed: Boolean,
    val installedFiles: List<String>,
    val signature: String,
    val planned: List<AudioSystemModuleFile>?,
    val plannedSignature: String?,
    /**
     * **سببُ فشل التحليل** — اسمُ الجذر الفعليّ حين يكون الملف XML صالحًا وجذره ليس `audio_effects`،
     * و`null` حين لا ملفّ أو نجح التحليل. ووجوده مع `document == null` يعني: «الملف بصيغةٍ لا
     * نعرفها» — وهو قياسٌ يُصلَح عليه، لا جملة «لا يُحلَّل» التي كانت تُسدّ الباب.
     */
    val sourceRoot: String?,
    /**
     * **خطّة مكتبة المؤثّر** — تُبنى في الواجهة من مسار مكتبات التطبيق وعموده ([`audioEffectLibraryPlan`])،
     * و`null` حين لا مكتبةَ مشحونة. ووجودُها **لا يعني أنّها نُسخت** — ذلك يُقاس في [`libraryInstalled`].
     */
    val libraryPlan: AudioEffectLibraryPlan? = null,
    /**
     * `true` ⇒ المكتبة موجودة على المسار الذي يبحث فيه المصنع · `false` ⇒ غائبة/غير مقروءة ·
     * `null` ⇒ لم تُقَس (لا خطّة، أو لا جذر). **ولا تُقرأ «ناجح» بلا قياس** (ADR-07).
     */
    val libraryInstalled: Boolean? = null,
    /** صلاحية المكتبة المقروءة بعد النسخ — `null` حين لا قراءة (فالجهل ليس نفيًا). */
    val libraryMode: String? = null,
)

@Singleton
class AudioSystemEffectBackend @Inject constructor(
    private val arbiter: HardwareControlArbiter,
) {

    /**
     * قياسٌ **سلبيّ** للشاشة: لا `su` ولا كتابة — ولا نافذة صلاحية تُستدعى لمجرّد رسم قسم.
     * وطلب الجذر فعلٌ صريح في [install] و[remove] وحدهما (ADR-20).
     *
     * @param addition الطلب المُدخَل إن وُجد؛ و`null` يعني «اعرض الحالة وحدها» — وحينها لا يُقال «رفض
     *   إضافة» أبدًا، بل يُقال إن لا إضافة مُدخَلة.
     */
    fun snapshot(
        addition: AudioEffectAddition? = null,
        library: AudioEffectLibraryPlan? = null,
    ): AudioSystemSnapshot {
        val measured = measure(addition, library)
        val libraryPath = library?.let { "${AudioSystemModule.MODULE_ROOT}/${it.moduleRelativePath}" }
        return AudioSystemSnapshot(
            verdict = measured.verdict,
            sourcePath = measured.sourcePath,
            document = measured.document,
            moduleRoot = AudioSystemModule.MODULE_ROOT,
            moduleWritable = measured.moduleWritable,
            overlayPath = measured.relativePath?.let(::absolute),
            installed = measured.installed,
            installedFiles = measured.installedFiles,
            signature = measured.diskSignature,
            planned = measured.files,
            plannedSignature = measured.expected,
            sourceRoot = measured.sourceRoot,
            libraryPlan = library,
            libraryInstalled = libraryPath?.let { RootFileAccess.exists(it) },
            libraryMode = libraryPath?.let(::modeOf),
        )
    }

    /**
     * يثبّت الطبقة: يقرأ ملفّ الجهاز، يدمج الإضافة، ثم يكتب الوحدة **عبر المحكِّم** ويقرأ ما كتب.
     *
     * **وطلب الجذر هنا فعلُ المستخدم نفسه:** الزرّ الذي ضُغط يقول «ثبّت الطبقة»، فلا يُفاجأ المستخدم
     * بنافذة صلاحية — بل يتوقّعها.
     */
    fun install(
        addition: AudioEffectAddition,
        auditToken: String,
        /**
         * مكتبة المؤثّر — **تمرّرها الواجهة من مسار مكتبات التطبيق وعموده**، وهي الجزء الذي كان
         * غائبًا بالكامل (تكملة ٢٤٠): بلا نسخها لا يجدها المصنع ويُطبع `can't find libmaxfx.so`.
         */
        library: AudioEffectLibraryPlan? = null,
    ): AudioKnobVerdict {
        if (!SharedHardwareOwnershipStore.isConfigured()) {
            return audioKnobNotAttempted(AudioEffectReason.STORE_UNCONFIGURED)
        }
        if (!PrivilegeManager.cachedRootGranted() && !PrivilegeManager.requestRoot()) {
            return audioKnobNotAttempted(AudioSystemReason.ROOT_REQUIRED)
        }
        // **والحكم يقع على المكتبة قبل إعلان النجاح:** طبقةٌ تُركّب ولا مكتبةَ لها تُقرأ «نجحت» ثمّ
        // لا يُسمع فرق — وهو أسوأ من فشلٍ ظاهر. ولا يُطلب النسخ إلّا إن كانت المكتبة موجودة أصلًا
        // في التطبيق (التحقّق من المصدر بلا جذر: `File.exists`).
        if (library != null && !java.io.File(library.sourcePath).exists()) {
            return audioKnobNotAttempted(AudioSystemReason.LIBRARY_NOT_SHIPPED, library.sourcePath)
        }
        val measured = measure(addition, library)
        if (measured.verdict.status != AudioSystemLayerStatus.READY) {
            return audioKnobNotAttempted(measured.verdict.reason, measured.expected)
        }
        val files = measured.files
            ?: return audioKnobNotAttempted(AudioSystemReason.MODULE_PATH_UNWRITABLE)
        val relativePath = measured.relativePath
            ?: return audioKnobNotAttempted(AudioSystemReason.MODULE_PATH_UNWRITABLE)
        // والبصمة نفسها التي طلبها الحكم — فما يُقيَّم هو ما سيُكتب، لا حسابٌ ثانٍ.
        val desired = measured.expected ?: AudioSystemModule.comparisonSignature(files)
        val relatives = moduleFiles(relativePath)

        val result = runCatching {
            arbiter.submit(
                key = HardwareControlKey.audioSystem(AudioEffectsPaths.FILE_NAME),
                // والمالك `GLOBAL_PROFILE`: قرارٌ عامّ للمستخدم، لا سلامةٌ ولا عقل — فلا يتقدّم على غيرهما.
                owner = ControlOwnership.Owner.GLOBAL_PROFILE,
                token = auditToken,
                desired = desired,
                // والمكتبة تُنسخ **داخل نفس المعاملة** — فحذف الوحدة (الرجوع) يحذفها معها.
                apply = { writeFiles(files) && (library == null || copyLibrary(library)) },
                read = { diskSignature(relatives) },
                // والمقارنة حرفيّة: البصمة نصٌّ واحد، وتساويه هو التحقّق كلّه.
                verify = { expected, actual -> actual != null && actual == expected },
                // والاسترجاع: حذف الوحدة — **رجوعٌ تامّ**، فملفّ النظام لم يُلمَس قطّ.
                restore = { removeModule() },
            )
        }.getOrNull() ?: return audioKnobNotAttempted(AudioEffectReason.ARBITER_UNAVAILABLE, desired)

        val verdict = audioKnobVerdict(
            attempted = true,
            blocked = result.blocked,
            applied = result.applied,
            verified = result.verified,
            expected = desired,
            actual = result.actual,
            error = result.error,
        )
        return downgradeIfUnreadable(verdict, relativePath, library)
    }

    /**
     * يلغي الطبقة بحذف مجلّد وحدتها — وهو الرجوع الكامل: لا شيء في ملفّ النظام تغيّر قطّ.
     *
     * ولا يُقال «فشل» لمن لا طبقة عنده: تلك حالةٌ معلَنة (`layer-not-installed`) لا عطب.
     */
    fun remove(auditToken: String): AudioKnobVerdict {
        if (!SharedHardwareOwnershipStore.isConfigured()) {
            return audioKnobNotAttempted(AudioEffectReason.STORE_UNCONFIGURED, AudioSystemModule.ABSENT)
        }
        if (!PrivilegeManager.cachedRootGranted() && !PrivilegeManager.requestRoot()) {
            return audioKnobNotAttempted(AudioSystemReason.ROOT_REQUIRED, AudioSystemModule.ABSENT)
        }
        if (!RootFileAccess.exists(AudioSystemModule.MODULE_ROOT)) {
            return audioKnobNotAttempted(AudioSystemReason.NOT_INSTALLED, AudioSystemModule.ABSENT)
        }

        val result = runCatching {
            arbiter.submit(
                key = HardwareControlKey.audioSystem(AudioEffectsPaths.FILE_NAME),
                owner = ControlOwnership.Owner.GLOBAL_PROFILE,
                token = auditToken,
                desired = AudioSystemModule.ABSENT,
                apply = { removeModule() },
                read = { if (RootFileAccess.exists(AudioSystemModule.MODULE_ROOT)) PRESENT else AudioSystemModule.ABSENT },
                // ولا شيء يُسترجَع عند فشل الحذف: الحذف نفسه هو الرجوع، وفشله يترك الحالة كما كانت.
                restore = { true },
            )
        }.getOrNull() ?: return audioKnobNotAttempted(AudioEffectReason.ARBITER_UNAVAILABLE, AudioSystemModule.ABSENT)

        return audioKnobVerdict(
            attempted = true,
            blocked = result.blocked,
            applied = result.applied,
            verified = result.verified,
            expected = AudioSystemModule.ABSENT,
            actual = result.actual,
            error = result.error,
        )
    }

    // ── القياس ────────────────────────────────────────────────────────

    private data class Measured(
        val verdict: AudioSystemLayerVerdict,
        val sourcePath: String?,
        val document: AudioEffectsDocument?,
        val relativePath: String?,
        val moduleWritable: Boolean,
        val installed: Boolean,
        val installedFiles: List<String>,
        val diskSignature: String,
        val files: List<AudioSystemModuleFile>?,
        val expected: String?,
        /** **اسمُ الجذر الفعليّ** حين يوجد الملف ولا يُحلَّل — و`null` حين لا ملفّ أصلًا أو نجح التحليل. */
        val sourceRoot: String?,
    )

    /**
     * يقيس كل شرطٍ **مرّة واحدة** ويبني منه الدليل والحكم معًا — فلا تختلف الشاشة عن التثبيت في ترتيب
     * الأسباب (وهو الترتيب الذي يحرسه `AudioSystemVerdictTest`).
     */
    private fun measure(
        addition: AudioEffectAddition?,
        library: AudioEffectLibraryPlan? = null,
    ): Measured {
        // ولا يُلمَس الجذر إلّا إن كان ممنوحًا مسبقًا: كل نداء صدفةٍ غير جذريّ قد يستدعي نافذة صلاحية.
        val root = PrivilegeManager.cachedRootGranted()
        val existing = if (root) {
            AudioEffectsPaths.DEVICE_CANDIDATES.filter { RootFileAccess.exists(it) }
        } else {
            emptyList()
        }
        val texts = existing.mapNotNull { path -> RootFileAccess.read(path)?.let { path to it } }
        // **وأوّل ما يُحلَّل هو ما نطبّقه — لا أوّل ما يوجد.** على `Xiaomi 24129RT7CC` يوجد
        // `audio_effects.xml` **ولا يُحلَّل**، والمُحلَّل فعلًا هو `audio_effects_config.xml` —
        // فقاعدة «الأوّل الموجود» كانت ستختار ملفًّا لا يقرأه أحد، فتُكتب طبقةٌ فوقه فتُقرأ «نجحت»
        // ولا يراها `audioserver` أصلًا (وهو بالضبط عطب تكملة ٢٢٩ في لونٍ آخر).
        val parsed = texts.firstOrNull { AudioEffectsDocument.parse(it.second) != null }
        val sourcePath = parsed?.first ?: texts.firstOrNull()?.first
        val sourceText = parsed?.second ?: texts.firstOrNull()?.second
        val document = sourceText?.let(AudioEffectsDocument::parse)
        // والتشخيص يحوّل طريقًا مسدودًا إلى قياس: «لا يُحلَّل» بلا تفصيلٍ لا تُصلَح، واسمُ الجذر
        // الفعليّ يقول فورًا هل الملف بصيغةٍ لا نعرفها أم فاسد.
        val sourceRoot = if (document == null) sourceText?.let(AudioEffectsDocument::parseDiagnosis) else null
        val relativePath = sourcePath?.let(AudioEffectsPaths::moduleRelativePath)
        // «قابل للكتابة» مجلّد الوحدات نفسه لا مجلّدنا: أوّل تثبيت لا مجلّد لنا فيه بعد.
        val moduleWritable =
            root && relativePath != null && RootFileAccess.writable(AudioSystemModule.MODULES_DIR)

        val relatives = relativePath?.let(::moduleFiles).orEmpty()
        val installedFiles = relatives.filter { RootFileAccess.exists(absolute(it)) }
        val installed = relatives.isNotEmpty() && installedFiles.size == relatives.size
        val diskSignature = if (relatives.isEmpty()) AudioSystemModule.ABSENT else diskSignature(relatives)

        var additionReason: String? = null
        var files: List<AudioSystemModuleFile>? = null
        var expected: String? = null
        if (addition != null && document != null && sourcePath != null) {
            when (val overlay = audioEffectsOverlay(document, addition)) {
                is AudioOverlayResult.Refused -> additionReason = overlay.reason
                is AudioOverlayResult.Overlay -> {
                    files = AudioSystemModule.files(
                        document = overlay.document,
                        devicePath = sourcePath,
                        description = MODULE_DESCRIPTION,
                        libraryRelativePath = library?.moduleRelativePath,
                    )
                    expected = files?.let(AudioSystemModule::comparisonSignature)
                    // ولا يقع عمليًّا (المعرّف ثابتٌ صالح، والمسار تحقّق أعلاه) — لكنّه يُقال بسببه
                    // الصريح بدل أن يُقرأ «لا إضافة مُدخَلة»، وهما ليسا الشيء نفسه.
                    if (files == null) additionReason = AudioSystemReason.MODULE_PATH_UNWRITABLE
                }
            }
        }

        val verdict = audioSystemLayerVerdict(
            AudioSystemEvidence(
                rootAvailable = root,
                sourcePath = sourcePath,
                sourceParsed = document != null,
                additionReason = additionReason,
                // «الطبقة معدّة»: في التثبيت = الملفّات مولَّدة؛ وفي العرض وحده = الوحدة قائمة على القرص.
                overlayReady = if (addition != null) files != null else installed,
                modulePathWritable = moduleWritable,
            ),
        )
        return Measured(
            verdict = verdict,
            sourcePath = sourcePath,
            document = document,
            relativePath = relativePath,
            moduleWritable = moduleWritable,
            installed = installed,
            installedFiles = installedFiles,
            diskSignature = diskSignature,
            files = files,
            expected = expected,
            sourceRoot = sourceRoot,
        )
    }

    /** ملفّات الوحدة المتوقّعة: `module.prop` والسكربتات وملفّ الطبقة — وهي عقد التثبيت والحذف معًا. */
    private fun moduleFiles(relativePath: String): List<String> =
        listOf(MODULE_PROP, relativePath, CUSTOMIZE_SCRIPT, POST_FS_DATA_SCRIPT)

    private fun absolute(relative: String): String = "${AudioSystemModule.MODULE_ROOT}/$relative"

    /**
     * بصمة ما على القرص — **بنفس دالّة الكتابة** ([`AudioSystemModule.signature`]) ونفس المحتوى المقصوص
     * الذي يعيده [`RootFileAccess.read`]. وغياب أيّ ملفّ يجعلها `ABSENT`، وهو غير «مطابق».
     */
    private fun diskSignature(relatives: List<String>): String =
        AudioSystemModule.signature(relatives.map { it to RootFileAccess.read(absolute(it)) })

    /**
     * يكتب ملفّات الوحدة — **من داخل معاملة المحكِّم**، ولا شيء غيرها يُنشئ ملفًّا في هذا التطبيق.
     *
     * والكتابة ذرّية لكل ملفّ (`atomicWriteText`: كتابة مؤقّتة ثم `mv`)، فلا يبقى ملفّ نصف مكتوب حتى
     * لو انقطع التنفيذ. وفشل أيّ ملفّ يُسقط الكتابة كلها — فيُسترجع (يُحذف) ما كُتب جزئيًّا.
     */
    private fun writeFiles(files: List<AudioSystemModuleFile>): Boolean {
        val written = files.all { file ->
            runCatching { RootFileAccess.atomicWriteText(absolute(file.relativePath), file.content) }
                .getOrDefault(false)
        }
        if (!written) return false
        fixModes(files)
        return true
    }

    /**
     * يضبط صلاحيّات ما كُتب. **والأولوية لملفّ الطبقة:** `atomicWriteText` تُخرجه بصلاحية **٠٦٠٠** (وهي
     * الصحيحة لملفّ يُكتب بالجذر)، و`audioserver` **لا يقرأ ٠٦٠٠ مملوكًا للجذر** — فتُركَّب الطبقة
     * ويُمنع صاحب الصوت من قراءتها. والسكربتات ٠٧٥٥ (يقرؤها Magisk وينفّذها).
     *
     * **ولا يُبنى أمرٌ من نصّ مستخدم:** المسارات جذر ثابت + مسار نسبيّ تحقّق بمصفوفة محارف في طبقة
     * `audio_effects.xml` النقيّة (لا فراغ ولا `'` ولا `$`). وفشل الضبط **لا يُعلَن نجاحًا** — يُقاس
     * الصلاحيّة بعدها ويُخفَّض الحكم ([downgradeIfUnreadable]).
     */
    private fun fixModes(files: List<AudioSystemModuleFile>): Boolean {
        val scripts = files.filter { it.relativePath.endsWith(".sh") }.map { absolute(it.relativePath) }
        val others = files.filterNot { it.relativePath.endsWith(".sh") }.map { absolute(it.relativePath) }
        var ok = true
        if (others.isNotEmpty()) ok = RootFileAccess.exec("chmod 0644 ${others.joinToString(" ")}") == 0 && ok
        if (scripts.isNotEmpty()) ok = RootFileAccess.exec("chmod 0755 ${scripts.joinToString(" ")}") == 0 && ok
        return ok
    }

    /** يحذف مجلّد الوحدة كاملًا — الحذف هو الرجوع، والتحقّق بعده بالوجود لا برمز الخروج. */
    private fun removeModule(): Boolean =
        RootFileAccess.exec("rm -rf ${AudioSystemModule.MODULE_ROOT}") == 0 &&
            !RootFileAccess.exists(AudioSystemModule.MODULE_ROOT)

    /**
     * صلاحيّة الملفّ بالأرقام الثمانيّة بلا صفرٍ بادئ (`644`) — و`null` حين لا قراءة، فلا يُدّعى حكم.
     * (`stat -c %a` من toybox على أندرويد؛ وغيابها يعود `null` فلا يُصنَّف الملفّ مقروءًا ولا ممنوعًا.)
     */
    private fun modeOf(path: String): String? =
        RootFileAccess.readCommand("stat -c %a '$path'")?.trim()?.trimStart('0')?.takeIf { it.isNotEmpty() }

    /**
     * **قياسٌ ثانٍ لا يُنقِص حكم المحكِّم:** بعد أن يُثبت المحكِّم أن المحتوى كُتب كما طُلب، تُقاس
     * صلاحية قراءة ملفّ الطبقة. فإن كانت مقروءة (٠٦٤٤) بقي الحكم؛ وإن كانت غير مقروءة **خُفِّض إلى
     * `failed` بسببه الصريح**؛ وإن لم تُقرأ الصلاحيّة أصلًا بقي الحكم كما هو (فالجهل ليس نفيًا).
     */
    private fun downgradeIfUnreadable(
        verdict: AudioKnobVerdict,
        relativePath: String,
        library: AudioEffectLibraryPlan?,
    ): AudioKnobVerdict {
        if (!verdict.isApplied) return verdict
        val mode = modeOf(absolute(relativePath)) ?: return verdict
        if (mode != WORLD_READABLE_MODE) {
            return AudioKnobVerdict(
                outcome = AudioWriteOutcome.FAILED,
                expected = verdict.expected,
                actual = mode,
                reason = AudioSystemReason.OVERLAY_NOT_READABLE,
            )
        }
        if (library == null) return verdict
        // **وقياسٌ ثانٍ للمكتبة بعينه:** الوجود أوّلًا ثمّ الصلاحية. ولا يُخفض الحكم بجهلٍ: عدم
        // إمكان قراءة الوجود (بلا جذر) يُبقيه، وغيابٌ مقروء يُخفضه بسببه الصريح.
        val libraryPath = absolute(library.moduleRelativePath)
        if (!RootFileAccess.exists(libraryPath)) {
            return AudioKnobVerdict(
                outcome = AudioWriteOutcome.FAILED,
                expected = library.devicePath,
                actual = AudioSystemModule.ABSENT,
                reason = AudioSystemReason.LIBRARY_NOT_INSTALLED,
            )
        }
        val libraryMode = modeOf(libraryPath) ?: return verdict
        if (libraryMode == LIBRARY_MODE) return verdict
        return AudioKnobVerdict(
            outcome = AudioWriteOutcome.FAILED,
            expected = verdict.expected,
            actual = libraryMode,
            reason = AudioSystemReason.LIBRARY_NOT_READABLE,
        )
    }

    /**
     * ينسخ مكتبة المؤثّر إلى مجلّد مكتبات المنصّة **داخل الوحدة** — خطوةٌ كانت غائبة بالكامل.
     *
     * **والمصدر/الهدف من خطّةٍ نقيّة** ([`audioEffectLibraryPlan`])، لا من نصّ مستخدم: العمود يحدّد
     * `lib64` أو `lib`، والملفّ اسمٌ مجرَّد لا يقبل `/`. والتشغيل ذرّيّ بقدر ما تسمح به الأداة: نسخٌ
     * إلى مسارٍ مؤقّت في المجلّد نفسه ثمّ `mv` — فلا يبقى ملفّ نصف مكتوب يبدو مكتبةً صالحة.
     * **وفشل النسخ يُسقط الكتابة كلها** فيُحذف المجلّد (استرجاعٌ تامّ).
     */
    private fun copyLibrary(library: AudioEffectLibraryPlan): Boolean {
        val destination = absolute(library.moduleRelativePath)
        val directory = destination.substringBeforeLast('/')
        val staged = "$destination.tmp"
        val steps = listOf(
            "mkdir -p '$directory'",
            "cp -f '${library.sourcePath}' '$staged'",
            "mv -f '$staged' '$destination'",
            "chown 0:0 '$destination'",
            // **ونفس صلاحية ملفّ التهيئة بالحرف:** `atomicWrite` تُخرج 0600، والمصنع لا يقرأ 0600.
            "chmod 0${library.mode} '$destination'",
            "chcon '${library.label}' '$destination'",
        )
        val allRan = steps.all { command -> RootFileAccess.exec(command) == 0 }
        // والتحقّق بالوجود بعد الأمر (لا برمز الخروج وحده) — وهو ما يُقاس في الحكم أيضًا.
        return allRan && RootFileAccess.exists(destination)
    }

    private companion object {
        const val MODULE_PROP = "module.prop"
        const val CUSTOMIZE_SCRIPT = "customize.sh"
        const val POST_FS_DATA_SCRIPT = "post-fs-data.sh"

        /** صلاحية القراءة للعالم — ما يحتاجه `audioserver` ليقرأ الطبقة. */
        const val WORLD_READABLE_MODE = "644"

        /** علامة «الوحدة قائمة» في مقبض الحذف — نصٌّ صريح لا فراغ. */
        const val PRESENT = "installed"
    }
}
