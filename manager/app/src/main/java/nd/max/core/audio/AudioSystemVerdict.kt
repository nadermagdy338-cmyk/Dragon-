/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * الصوت — **حكم الطبقة النظاميّة** (`AQ-09`): أيّ شرطٍ ناقص، وبأيّ سبب.
 *
 * **وهو جوهر الصدق في هذه الموجة:** ما يمنع أن تُعرض «طبقة مؤثّرات» وكأنّها تعمل وهي لا تعمل. فالحكم
 * يُقرأ من **ما قِيس** لا من نيّة: جذرٌ مُمنوح؟ ملفّ تهيئةٍ قُرئ وتحلّل؟ إضافةٌ صالحة؟ مسار الوحدة
 * قابل للكتابة؟ وأيٌّ غاب يُسمّى بسببه — ويُقال معه أنّ التطبيق **يحتاج إعادة تشغيل**.
 *
 * **وما لا يُقاس هنا مقيسٌ على جهاز:** هل يقبل `audioserver` الطبقة فعلًا بعد الإقلاع (وسم SELinux ·
 * ترتيب `mount` · توقيع) — وهذا لا يُدَّعى، بل يبقى «غير مُتحقَّق في هذه البيئة» (§0.1).
 *
 * **وصافٍ تمامًا:** لا `android.*` — يُقاس على JVM.
 */
package nd.max.core.audio

/** حالة الطبقة النظاميّة — ستٌّ لا سابع، وكلٌّ لها سببٌ مكتوب. */
enum class AudioSystemLayerStatus(val token: String) {
    /** كل شرط قِيس وتحقّق ⇒ الكتابة ممكنة، **والتطبيق يحتاج إعادة تشغيل**. */
    READY("ready"),

    /** لا جذر — فلا كتابة في `/data/adb/modules` أصلًا. */
    NEEDS_ROOT("needs_root"),

    /** لا ملفّ تهيئة في أيّ من مسارات المنصّة — فلا شيء نُبنى عليه. */
    SOURCE_UNAVAILABLE("source_unavailable"),

    /** ملفٌّ موجود ولم يُقرأ أو لم يُحلَّل — والقياس الفاشل ليس نفيًا (ADR-07). */
    SOURCE_UNREADABLE("source_unreadable"),

    /** الإضافة نفسها مرفوضة: غير صالحة، أو تناقض ما على الجهاز. */
    ADDITION_REFUSED("addition_refused"),

    /** مسار الوحدة غير قابل للكتابة (لا مجلّد `modules` · ولا صلاحية). */
    MODULE_UNWRITABLE("module_unwritable"),
}

/** رموز أسباب الحكم — في موضع واحد. */
object AudioSystemReason {
    const val ROOT_REQUIRED = "root-required"
    const val NO_EFFECTS_FILE = "no-audio-effects-xml-on-device"
    const val SOURCE_UNPARSABLE = "audio-effects-xml-unparsable"
    const val NO_ADDITION = "no-addition-supplied"
    const val MODULE_PATH_UNWRITABLE = "module-path-not-writable"
    const val READY = "ready-needs-reboot"

    /** لا وحدة قائمة أصلًا — فلا شيء يُلغى (وهو غير «فشل الحذف»). */
    const val NOT_INSTALLED = "layer-not-installed"

    /**
     * الوحدة كُتبت **ولم تُقرأ** — صلاحية ملفّ الطبقة ليست قراءةً للعالم (٠٦٠٠ مثلًا).
     *
     * **وهو فشلٌ لا نجاحٌ يتأخّر:** الطبقة تُركَّب فعلًا، ثم يمنع `audioserver` من قراءتها، فيقرأ المستخدم
     * «ثُبّتت» ويسمع صوتًا لم يزد حرفًا — والعطب لا يُنسب إلى سببه.
     */
    const val OVERLAY_NOT_READABLE = "overlay-not-world-readable"

    // ── مكتبة المؤثّر (تكملة ٢٤٠) — **وهي الفرق المقيس بيننا وبين V4A:** عندها `cp` إلى مجلّد
    // المكتبات، وعندنا كان صفرُ سطرٍ ينسخ مكتبة. وهذه الرموز تُقال بدل أن يُقرأ «نجح» ثمّ لا يُسمع فرق.

    /** المكتبة **غير مشحونة في التطبيق** — فلا شيء يُنسخ، ولا يُعلن نجاحٌ بلا ملفّ. */
    const val LIBRARY_NOT_SHIPPED = "effect-library-not-shipped-in-app"

    /** نُسخت الوحدة **ولم توجد المكتبة** على مسار البحث، فلا يجدها المصنع. */
    const val LIBRARY_NOT_INSTALLED = "effect-library-not-installed"

    /** المكتبة موجودة وصالحيتها ليست قراءةً للعالم — فيجدها المسار ويمنعها الوسم. */
    const val LIBRARY_NOT_READABLE = "effect-library-not-world-readable"
}

/**
 * ما قِيس قبل الحكم — **وكل حقل إجابةٌ عن سؤال، لا توقّع.**
 *
 * @param rootAvailable الجذر مُمنوح **قراءةً سلبيّة** (بلا استدعاء نافذة صلاحية عند رسم الشاشة).
 * @param sourcePath أوّل مسارٍ موجود من مرشّحات المنصّة — و`null` يعني «لا ملفّ».
 * @param sourceParsed هل تحلّل الملفّ إلى وثيقة؟
 * @param additionReason سبب رفض الإضافة إن رُفضت (`AudioOverlayReason`)، وإلا `null`.
 * @param overlayReady هل أُعدّت الوثيقة المدموجة والملفّات؟
 * @param modulePathWritable هل المجلّد الذي ستُكتب فيه الوحدة قابل للكتابة؟
 */
data class AudioSystemEvidence(
    val rootAvailable: Boolean,
    val sourcePath: String?,
    val sourceParsed: Boolean,
    val additionReason: String?,
    val overlayReady: Boolean,
    val modulePathWritable: Boolean,
)

/** حكمٌ واحد: حالته وسببه، وهل يحتاج التطبيق إعادة تشغيل (يحتاجها دائمًا في حالة [AudioSystemLayerStatus.READY]). */
data class AudioSystemLayerVerdict(
    val status: AudioSystemLayerStatus,
    val reason: String,
    val requiresReboot: Boolean,
) {
    val isReady: Boolean get() = status == AudioSystemLayerStatus.READY
}

/**
 * الأحكام بترتيبٍ **ملزم**: الشرط الأسبق هو الذي يُعرض، فلا يُقال «مسار الوحدة غير قابل للكتابة» لمن
 * لا جذر عنده أصلًا (وهو تشتيتٌ يخفي السبب الحقيقي)، ولا يُقال «إضافة غير صالحة» قبل أن نعرف أنّ
 * ملفّ التهيئة نفسه لم يُقرأ.
 */
fun audioSystemLayerVerdict(evidence: AudioSystemEvidence): AudioSystemLayerVerdict = when {
    !evidence.rootAvailable ->
        AudioSystemLayerVerdict(AudioSystemLayerStatus.NEEDS_ROOT, AudioSystemReason.ROOT_REQUIRED, false)

    evidence.sourcePath == null ->
        AudioSystemLayerVerdict(AudioSystemLayerStatus.SOURCE_UNAVAILABLE, AudioSystemReason.NO_EFFECTS_FILE, false)

    !evidence.sourceParsed ->
        AudioSystemLayerVerdict(AudioSystemLayerStatus.SOURCE_UNREADABLE, AudioSystemReason.SOURCE_UNPARSABLE, false)

    evidence.additionReason != null ->
        AudioSystemLayerVerdict(AudioSystemLayerStatus.ADDITION_REFUSED, evidence.additionReason, false)

    !evidence.overlayReady ->
        AudioSystemLayerVerdict(AudioSystemLayerStatus.ADDITION_REFUSED, AudioSystemReason.NO_ADDITION, false)

    !evidence.modulePathWritable ->
        AudioSystemLayerVerdict(AudioSystemLayerStatus.MODULE_UNWRITABLE, AudioSystemReason.MODULE_PATH_UNWRITABLE, false)

    else -> AudioSystemLayerVerdict(AudioSystemLayerStatus.READY, AudioSystemReason.READY, true)
}
