package nd.max.core.atlas

/**
 * جرد التغطية — كم من أهداف التحكّم يملك **دليل أثر مقيسًا**، وكم لا يملك، ولماذا.
 *
 * وهذا الملف هو الوجه الصادق لِما يَعِد به أطلس. فعبارة «خريطة قدرات» تعني في أحسن حال أن نقول
 * لكل هدف: «قِسناه» أو «لم نقِسه» — لا أن نعرض قائمة أهداف فارغة توهم بأنها كلها مقيسة. وتقرير
 * واحد كهذا يجيب سؤال المالك («ما الذي ينقص أطلس؟») بالأرقام لا بالحدس: عدد مغطّى، وعدد ناقص،
 * وسبب النقص لكل هدف على حِدة.
 *
 * والفرق بين هذا و[AtlasAvailability] أنه **ليس معرفة عن الجهاز** بل **معرفة عن معرفتنا**: لا
 * يقول «هذه العقدة تدعم أو لا تدعم»، بل «دليلنا على أثرها كافٍ أو غير كافٍ» — وهو الفرق بين
 * كتالوج القدرات وجرد الأدلّة.
 */

/** لماذا لا يوجد دليل أثر كافٍ لهذا الهدف. وكل سبب مختلف عن الآخر، ولا واحد منها «لا شيء». */
enum class AtlasCoverageGap {
    /** لا عيّنة أثر واحدة لهذا الهدف: لم يُقَس شيء. */
    NO_SAMPLE,

    /** عيّنات موجودة لكنها من إقلاع آخر: قياس عن جهاز آخر سلوكيًّا. */
    OTHER_BOOT_SAMPLE,

    /** عيّنات موجودة لكنها أقدم من نافذة الحداثة: قياس عن ماضٍ. */
    STALE_SAMPLE,

    /** عيّنة واحدة طازجة: كافية للقراءة، غير كافية للحكم على تغيّر. */
    SINGLE_SAMPLE,
}

data class AtlasCoverageEntry(
    val target: String,
    val usableSamples: Int,
    val latestAtElapsedMs: Long?,
    val gap: AtlasCoverageGap? = null,
) {
    val covered: Boolean get() = gap == null

    /** رمز السبب بصيغة ثابتة تُقرأ في التقرير. */
    val gapToken: String get() = gap?.name?.lowercase()?.replace('_', '-') ?: "covered"
}

data class AtlasCoverageReport(
    val entries: List<AtlasCoverageEntry>,
    val minSamples: Int,
    val freshnessMs: Long,
) {
    init {
        require(minSamples > 0) { "the sample requirement is positive" }
        require(freshnessMs > 0L) { "the freshness window is positive" }
    }

    val totalTargets: Int get() = entries.size
    val coveredTargets: Int get() = entries.count { it.covered }
    val gaps: List<AtlasCoverageEntry> get() = entries.filter { !it.covered }

    /** بالألف، لتُقرأ نسبة ولا تُقرَّب إلى «نصف» كاذب. */
    val coveragePermille: Int
        get() = if (entries.isEmpty()) 0 else coveredTargets * 1000 / entries.size

    /**
     * أسطر ثابتة للتقرير: بلا نصوص مُترجَمة ولا أرقام مُخترعة — الرقم الظاهر محسوب من الجرد.
     * والواجهة تُترجم ما تشاء من هذه الرموز، أما التقرير فيبقى قابلًا للقراءة الآلية.
     */
    fun lines(): List<String> {
        val header = "atlas-coverage covered=$coveredTargets/$totalTargets" +
            " permille=$coveragePermille min_samples=$minSamples freshness_ms=$freshnessMs"
        return listOf(header) + gaps.map { "atlas-coverage-gap target=${it.target} reason=${it.gapToken} samples=${it.usableSamples}" }
    }
}

object AtlasEvidenceCoverage {

    /**
     * يبني الجرد من خريطة: هدف ← عيّناته.
     *
     * وترتيب الأسباب على سلّم صريح، لأن لكل سبب علاجًا مختلفًا:
     * لا عيّنة ⇒ نحتاج أن نقيس؛ إقلاع آخر ⇒ نحتاج إعادة القياس الحالية؛ قديمة ⇒ نحتاج قياسًا جديدًا؛
     * واحدة ⇒ نحتاج ثانية قبل أي حكم. ولو جُمعت تحت «ناقص» لضاع العلاج.
     */
    fun report(
        targets: List<String>,
        samplesByTarget: Map<String, List<AtlasEffectSample>>,
        nowElapsedMs: Long,
        bootGeneration: Long,
        freshnessMs: Long = DEFAULT_FRESHNESS_MS,
        minSamples: Int = MIN_SAMPLES,
    ): AtlasCoverageReport {
        require(nowElapsedMs >= 0L) { "elapsed time is non-negative" }
        val entries = targets.map { target ->
            val all = samplesByTarget[target].orEmpty()
            val sameBoot = all.filter { it.bootGeneration == bootGeneration }
            val fresh = sameBoot.filter { nowElapsedMs - it.observedAtElapsedMs in 0..freshnessMs }
            when {
                all.isEmpty() -> AtlasCoverageEntry(target, 0, null, AtlasCoverageGap.NO_SAMPLE)
                sameBoot.isEmpty() -> AtlasCoverageEntry(target, 0, null, AtlasCoverageGap.OTHER_BOOT_SAMPLE)
                fresh.isEmpty() -> AtlasCoverageEntry(
                    target = target,
                    usableSamples = 0,
                    latestAtElapsedMs = sameBoot.maxOf { it.observedAtElapsedMs },
                    gap = AtlasCoverageGap.STALE_SAMPLE,
                )

                fresh.size < minSamples -> AtlasCoverageEntry(
                    target = target,
                    usableSamples = fresh.size,
                    latestAtElapsedMs = fresh.maxOf { it.observedAtElapsedMs },
                    gap = AtlasCoverageGap.SINGLE_SAMPLE,
                )

                else -> AtlasCoverageEntry(
                    target = target,
                    usableSamples = fresh.size,
                    latestAtElapsedMs = fresh.maxOf { it.observedAtElapsedMs },
                    gap = null,
                )
            }
        }
        return AtlasCoverageReport(entries = entries, minSamples = minSamples, freshnessMs = freshnessMs)
    }

    /** عشر دقائق: قياس أقدم منها لا يصف التدخّل الذي ندرسه الآن. */
    const val DEFAULT_FRESHNESS_MS: Long = 600_000L

    /** عيّنتان: واحدة «قبل» وواحدة «بعد» — وما دون ذلك لا يمثّل تغيّرًا. */
    const val MIN_SAMPLES: Int = 2
}
