package nd.max.core.atlas

/**
 * معرفة العتاد الشاذّة (quirks) — والقاعدة الوحيدة الحاكمة هنا: **تُخفض فقط**.
 *
 * الدافع من الشجرة نفسها: أطلس يعرف أن عقدة «مقروءة على هذا الجهاز» من قياس، لكنه لا يعرف شيئًا
 * عن **هذا النوع من الأجهزة**: `time_in_state` وحدة تِكّاته غير معلنة أصلًا، و`fix_target_opp_index`
 * سلوكه يختلف بين نواة وأخرى، وعقدة تعمل عند قراءة وتُرفض عند كتابة. وهذه معرفة **شاذّة بطبيعتها**:
 * تُبنى من بلاغات وملاحظات ميدانية، لا من وثيقة عامة.
 *
 * والخطر الذي وُلد هذا الملف ليمنعه بالضبط: أن يدخل بلاغ بشري («عندي لا يعمل») ثم يخرج من الطرف
 * الآخر كحقيقة مراجَعة («مصدر مُتحقَّق») تُبنى عليها كتابة على العتاد. فالآثار هنا **كلها تخفيضية**
 * بنوعها: لا يوجد في [AtlasClaimEffect] عضو يرفع توفّرًا أو دورًا أو ثقة — وإضافة واحد كهذا تُكسر
 * الاختبار التوافقي في `AtlasQuirksTest` قبل أن تصل إلى أي جهاز.
 */

/**
 * ما **نتوقّعه** على هذا الصنف من الأجهزة قبل أن نجرّب — وهو غير [AtlasAccess].
 *
 * والفرق ليس لفظيًّا: [AtlasAccess] تقول «ما حدث في هذه المحاولة» (قُرئ · موجود ولم يُقرأ ·
 * غائب مُثبت)، وهذه تقول «ما نتوقّعه على هذا النوع من الأجهزة» (معرفة، لا ملاحظة). وخلطهما هو
 * سبب وجود `EXPECTED_DENIED` أصلًا: سطح نعرف أنه محجوب على هذا الصنف لا يُقرأ كل مرة ويُنتج ضجيج
 * `avc: denied` بلا فائدة.
 */
enum class AtlasAvailability {
    /** متوقَّع أن يعمل على كل الأجهزة من هذا الصنف. */
    EXPECTED,

    /** يعتمد على النواة/المنصة: لا يُدَّعى غياب ولا حضور قبل المحاولة. */
    DEVICE_DEPENDENT,

    /** متوقَّع ألّا يُقرأ على هذا الصنف. */
    EXPECTED_DENIED,
}

/**
 * دور الواجهة (I-15 / `G-06`).
 *
 * و`CONTROL_PLANE_OWNED` ليست حكمًا على العقدة بل على **من يكتب فيها**: عقدة يملكها وكيل آخر
 * (مثل `fix_target_opp_index` التي تُدار من جلسة أخرى) يمكن **قراءتها** كملاحظة، لكن لا يجوز
 * اتّخاذ قيمتها دليلًا على «ما نستطيع كتابته» — لأن آخر كاتب قد يكون غيرنا.
 */
enum class AtlasInterfaceRole {
    /** تُقرأ وتُفسَّر، وقيمتها ملاحظة عن الجهاز. */
    OBSERVABLE,

    /** تُقرأ، لكن الكتابة فيها تملكها جهة أخرى: قيمتها ملاحظة عن ذلك الكاتب لا عن قدرتنا. */
    CONTROL_PLANE_OWNED,

    /** لا شيء معلوم عن دورها — وهو الافتراضي، ولا يُرفع إلى `OBSERVABLE` بلا دليل. */
    UNKNOWN,
}

/** أثر قاعدة شاذّة. **كلها تخفيضية**، وترتيب الأعضاء مقصود ليكون النوع نفسه هو الضمانة. */
enum class AtlasClaimEffect {
    /** يُخفض التوفّر إلى [AtlasAvailability.DEVICE_DEPENDENT]. */
    LOWER_TO_DEVICE_DEPENDENT,

    /** يُخفض التوفّر إلى [AtlasAvailability.EXPECTED_DENIED]. */
    MARK_EXPECTED_DENIED,

    /** يُثبّت الدور `CONTROL_PLANE_OWNED` (يُخفض من `OBSERVABLE`/`UNKNOWN`). */
    MARK_ROLE_CONTROL_PLANE_OWNED,

    /** يُخفض سقف الثقة إلى [AtlasSourceConfidence.REPORTED] مهما كان سندها. */
    MARK_CONFIDENCE_REPORTED,

    /** يُرشّح مصدرًا بديلًا للمعلومة نفسها — ولا يُلغي المصدر الأصلي. */
    PREFER_ALTERNATE_SOURCE,
}

/** متى تنطبق القاعدة. الشرط: **حقل واحد على الأقل**، فلا توجد قاعدة مطلقة بلا نطاق. */
data class AtlasQuirkMatch(
    val socModel: String? = null,
    val kernelPrefix: String? = null,
    val apiAtLeast: Int? = null,
    val sourceScope: String? = null,
) {
    init {
        require(listOfNotNull(socModel, kernelPrefix, apiAtLeast, sourceScope).isNotEmpty()) {
            "a quirk without a scope would apply to every device"
        }
        require(apiAtLeast == null || apiAtLeast > 0) { "an API level is positive" }
    }

    fun matches(socModel: String?, kernelRelease: String?, apiLevel: Int?, sourceId: String): Boolean {
        if (this.sourceScope != null && this.sourceScope != sourceId) return false
        // `String?` لا يملك `equalsIgnoreCase`: المقارنة غير الحسّاسة لحالة الأحرف تُكتب صراحةً،
        // و`null` لا يطابق قاعدة محدَّدة (فالجهاز مجهول الهوية لا يُنسب إليه سلوك).
        if (this.socModel != null && socModel?.equals(this.socModel, ignoreCase = true) != true) return false
        if (this.kernelPrefix != null && !(kernelRelease?.startsWith(this.kernelPrefix) ?: false)) return false
        if (this.apiAtLeast != null && !((apiLevel ?: 0) >= this.apiAtLeast)) return false
        return true
    }
}

/**
 * قاعدة شاذّة واحدة، بكل ما يجعلها قابلة للمحاسبة: معرّفها، ومصدرها، ونسختها، والجهاز الذي
 * رُصدت عليه. و[alternateSourceId] إلزامي حين يكون الأثر [AtlasClaimEffect.PREFER_ALTERNATE_SOURCE]،
 * لأن «رشّح بديلًا» بلا بديل ليس معلومة.
 */
data class AtlasQuirk(
    val id: String,
    val description: String,
    val match: AtlasQuirkMatch,
    val effect: AtlasClaimEffect,
    val sourceId: String,
    val revision: String,
    val reportedOn: String? = null,
    val alternateSourceId: String? = null,
) {
    init {
        require(AtlasIds.isValidObservationId(id)) { "quirk id is not canonical: $id" }
        require(description.isNotBlank()) { "a quirk without a description is unfalsifiable" }
        require(sourceId.isNotBlank()) { "a quirk without a source is not knowledge" }
        require(revision.isNotBlank()) { "a quirk without a revision cannot be re-checked" }
        if (effect == AtlasClaimEffect.PREFER_ALTERNATE_SOURCE) {
            require(!alternateSourceId.isNullOrBlank()) { "preferring an alternate needs the alternate" }
        }
    }
}

/** نتيجة تطبيق القواعد على سند واحد — وكل حقل فيها لا يمكن أن يكون أعلى من مبدئه. */
data class AtlasQuirkVerdict(
    val availability: AtlasAvailability,
    val role: AtlasInterfaceRole,
    val confidenceCeiling: AtlasSourceConfidence,
    val alternateSourceId: String? = null,
    val applied: List<AtlasQuirk> = emptyList(),
) {
    val lowered: Boolean get() = applied.isNotEmpty()
}

object AtlasQuirkBase {

    /**
     * يطبّق القواعد المنطبقة على سند واحد ويُعيد الحكم المخفَّض.
     *
     * وثلاث نقاط سلوك صريحة:
     * - **الترتيب لا يؤثّر**: النتيجة هي الأدنى مهما كان ترتيب القواعد (فالتطبيق تبديلي الجمع).
     * - **غير المنطبقة لا تُسجَّل**: [AtlasQuirkVerdict.applied] يحوي ما انطبق فعلًا فقط، فلا
     *   يتضخّم السجل بقواعد لم تفعل شيئًا.
     * - **قاعدة المصدر لا تُلغي الأخرى**: `PREFER_ALTERNATE_SOURCE` تُرشّح بديلًا وتبقى القاعدة
     *   الأصلية قائمة، لأن البديل عرضٌ إضافي لا حُكم على الأصل.
     */
    fun apply(
        quirks: List<AtlasQuirk>,
        sourceId: String,
        confidence: AtlasSourceConfidence,
        socModel: String? = null,
        kernelRelease: String? = null,
        apiLevel: Int? = null,
        availability: AtlasAvailability = AtlasAvailability.EXPECTED,
        role: AtlasInterfaceRole = AtlasInterfaceRole.UNKNOWN,
    ): AtlasQuirkVerdict {
        var loweredAvailability = availability
        var loweredRole = role
        var ceiling = confidence
        var alternate: String? = null
        val applied = mutableListOf<AtlasQuirk>()

        quirks.forEach { quirk ->
            if (!quirk.match.matches(socModel, kernelRelease, apiLevel, sourceId)) return@forEach
            when (quirk.effect) {
                AtlasClaimEffect.LOWER_TO_DEVICE_DEPENDENT ->
                    loweredAvailability = lowerAvailability(loweredAvailability, AtlasAvailability.DEVICE_DEPENDENT)

                AtlasClaimEffect.MARK_EXPECTED_DENIED ->
                    loweredAvailability = lowerAvailability(loweredAvailability, AtlasAvailability.EXPECTED_DENIED)

                AtlasClaimEffect.MARK_ROLE_CONTROL_PLANE_OWNED ->
                    loweredRole = controlPlaneOwned(loweredRole)

                AtlasClaimEffect.MARK_CONFIDENCE_REPORTED ->
                    ceiling = AtlasConfidenceRules.lower(ceiling, AtlasSourceConfidence.REPORTED)

                AtlasClaimEffect.PREFER_ALTERNATE_SOURCE ->
                    alternate = quirk.alternateSourceId
            }
            applied += quirk
        }

        return AtlasQuirkVerdict(
            availability = loweredAvailability,
            role = loweredRole,
            confidenceCeiling = ceiling,
            alternateSourceId = alternate,
            applied = applied,
        )
    }

    /** الأدنى فقط: `EXPECTED_DENIED` ثم `DEVICE_DEPENDENT` ثم `EXPECTED`. */
    fun lowerAvailability(current: AtlasAvailability, target: AtlasAvailability): AtlasAvailability =
        if (availabilityRank(target) <= availabilityRank(current)) target else current

    /** الدور يتّجه إلى `CONTROL_PLANE_OWNED` فقط، ولا يعود إلى `OBSERVABLE` أبدًا. */
    fun controlPlaneOwned(current: AtlasInterfaceRole): AtlasInterfaceRole =
        if (current == AtlasInterfaceRole.OBSERVABLE || current == AtlasInterfaceRole.UNKNOWN) {
            AtlasInterfaceRole.CONTROL_PLANE_OWNED
        } else {
            current
        }

    private fun availabilityRank(availability: AtlasAvailability): Int = when (availability) {
        AtlasAvailability.EXPECTED_DENIED -> 0
        AtlasAvailability.DEVICE_DEPENDENT -> 1
        AtlasAvailability.EXPECTED -> 2
    }
}
