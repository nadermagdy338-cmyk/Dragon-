package nd.max.core.hardware

/**
 * «كيف يُنفَّذ سقف GPU لكل تطبيق؟» — قرار **خالص**: بلا عقدة ولا كتابة ولا جهاز.
 *
 * لماذا وُجد هذا الملف
 * --------------------
 * قياس من حزمة سجل لجهاز حقيقي (rodin · MT6899 · app 5.2 · 2026-09-22):
 *
 * ```
 * PERAPP_GPU_CAPABILITY_SCAN … advertised_max=1300000000 live_max=1300000000 current=260000000
 * PERAPP_KNOB  knob=gpu_profile outcome=applied reason=verified expected=1300000000 live=1300000000
 * PERAPP_COMMIT knob=gpu_frequency:13000000.mali requested=1300000000 applied=true verified=true
 * ```
 *
 * أي أن `devfreq/max_freq` يقرأ **أعلى درجة عند الجهاز أصلًا**، والتردد الحقيقي (260) يمسكه
 * سقف المنصّة. فكانت ٧٥ جلسة `profile=performance` كلها «نجحت» **بصفر كتابة على أي عقدة GPU**:
 * الحاكم قرأ القيمة نفسها فقال «مُلبّى»، فتخطّى `apply` — و**داخل `apply` وحدها** كانت تسكن
 * كتابة تحرير سقف المنصّة. فمن اختار «أداء» لم يحرّك شيئًا، والسجل يقول إنه حرّك.
 *
 * والقواعد التي تُشتقّ من ذلك القياس، وهي منطق هذا الملف:
 *
 * 1. **طلبٌ عند أعلى درجة مُعلنة ليس كتابة تردد بل تحرير سقف.** لا شيء نرفعه فوق قدرة الجهاز؛
 *    المطلوب أن يزول ما خنقه دون قدرته. ولذلك `RELEASE_ONLY`، وهي **لا تثبّت درجة أبدًا**:
 *    التثبيت يجمّد التردد على قيمة واحدة (وهو ما يجعل الجهاز يبدو عالقًا على ٦٥٠).
 * 2. **والقفل الثابت نفسه سقفٌ خفيّ**: على MediaTek يقصّ `fix_target_opp_index` التردد من **خارج**
 *    `devfreq`، فجهاز مُثبَّت على فهرس OPP يقرأ `max_freq` عند القدرة بينما هو عالق على درجة
 *    واحدة. فهو جزء من القراءة ([CeilingReading.lockActive])، ولا يُقال «مُلبّى» وعليه قفل قائم.
 * 3. **وسقفٌ أدنى من القدرة يبقى سقفًا**: إن لم يقبل الجهاز كتابة مدى، فالمسار الوحيد المتاح
 *    هو التثبيت (`PIN`) — أسوأ من السقف، فلا يُستعمل إلا حيث لا يوجد غيره. ووجود مسار تثبيت
 *    (`fix_target_opp_index`) لا يعني أن المدى غير قابل للكتابة: الخلط بينهما هو ما حوّل كل
 *    سقف على هذا الجهاز إلى تثبيت.
 * 4. **ولا يُدّعى تحرير لم يُقس**: الحكم على «هل السقف مُحرَّر؟» يُبنى على **قراءة** سقف العقدة،
 *    وسقف المنصّة إن كان مقروءًا، وحالة تبريد GPU، وحالة القفل الثابت. وقيمة غير مقروءة تعني
 *    «لا حكم» لا «نجاح».
 *
 * وكل مُدخل هنا **مقيس** (قدرة معلنة، قابلية كتابة، مسار تثبيت، قراءة سقف) ولا شيء منه مُفترض.
 */
object GpuCeilingPolicy {

    /**
     * كيف يُنفَّذ الطلب على هذا الجهاز — قرار واحد صريح بدل تفرّع ضمني في موضع الكتابة.
     */
    enum class Realization(val token: String) {
        /** عند أعلى درجة مُعلنة: يُحرَّر سقف المنصّة، ولا تُكتب درجة ولا تُثبَّت. */
        RELEASE_ONLY("release-only"),

        /** سقف دون القدرة على جهاز يقبل كتابة مدى `min:max`. */
        RANGE("range"),

        /** سقف دون القدرة على جهاز لا يقبل إلا تثبيت درجة واحدة (فهرس OPP على MediaTek). */
        PIN("pin"),

        /** لا مسار يمسّ السقف على هذا الجهاز — ولا يُدّعى عمل. */
        UNSUPPORTED("unsupported"),
    }

    /**
     * هل الطلب عند أعلى درجة **مُعلنة** من الجهاز؟
     *
     * وهذا هو الحدّ الذي يفصل «تحرير سقف» من «كتابة سقف». و`advertisedMaxHz` من قائمة OPP —
     * لا من `max_freq` الحيّ: الأرشيف كتالوج قدرة، والحيّ قد تكون سياسة المنصّة خفّضته تحته.
     */
    fun atCapability(requestedHz: Long, advertisedMaxHz: Long?): Boolean =
        advertisedMaxHz != null && advertisedMaxHz > 0L && requestedHz >= advertisedMaxHz

    /**
     * القرار. والترتيب مقصود: **التحرير يُقدَّم على أي كتابة**، لأن الطلب عند القدرة لا يحتمل
     * كتابةً أصلًا؛ ثم يُنظر أيّ مسار كتابة يقبله الجهاز فعلًا.
     */
    fun realize(
        requestedHz: Long?,
        advertisedMaxHz: Long?,
        rangeWritable: Boolean,
        pinAvailable: Boolean,
    ): Realization = when {
        requestedHz == null || requestedHz <= 0L -> Realization.UNSUPPORTED
        atCapability(requestedHz, advertisedMaxHz) -> Realization.RELEASE_ONLY
        rangeWritable -> Realization.RANGE
        pinAvailable -> Realization.PIN
        else -> Realization.UNSUPPORTED
    }

    /**
     * قراءة السقف كما يمكن قياسه — ثلاثة حقول مستقلّة، وكل واحد يجوز أن يكون **غير مقروء**.
     *
     * - [nodeCeilingHz] السقف الذي تحمله عقدة الترددات نفسها (بوحدة Hz بعد تحويلها).
     * - [platformUpbound] القيمة الخام لعقدة سقف المنصّة (GED)؛ `0` تعني «بلا سقف مخصّص»،
     *   وغيرُ الصفر يعني أن سقفًا قائم — و**مقداره لا يُفسَّر هنا** لأن وحدته غير مؤكَّدة على
     *   كل إصدار، فلا يُبنى على تفسيرها حكم.
     * - [platformCoolingHeld] هل جهاز تبريد GPU رافعٌ حالته (غير صفر)؟ `null` = لا يُقرأ أو لا وجود له.
     * - [lockActive] هل قفل OPP ثابت قائم (فهرس ≠ `-1` على MediaTek)؟ `null` = لا مسار قفل على هذا
     *   الجهاز أصلًا. وهذا الحقل هو ما يجعل «عالقًا على درجة واحدة» مرئيًّا: قفلٌ قائم من جلسة سابقة
     *   (أو من أداة أخرى) يقرأ `max_freq` عند القدرة بينما التردد مجمَّد — فلا يُقال «مُلبّى» أبدًا.
     *
     * والقراءة تُحوَّل إلى **نصّ واحد** لأن المُحكِّم يقارن نصًّا بنصّ (المطلوب/المقروء، وخط الأساس).
     * ولذلك `parse` قرينها هنا: صيغة واحدة تُكتب وتُقرأ في ملف واحد، فلا يفسّرها موضعان.
     */
    data class CeilingReading(
        val nodeCeilingHz: Long?,
        val platformUpbound: Long?,
        val platformCoolingHeld: Boolean?,
        val lockActive: Boolean? = null,
    ) {
        val token: String
            get() = listOf(
                nodeCeilingHz?.toString() ?: UNREADABLE,
                platformUpbound?.toString() ?: ABSENT,
                when (platformCoolingHeld) {
                    true -> HELD
                    false -> RELEASED
                    null -> ABSENT
                },
                when (lockActive) {
                    true -> LOCKED
                    false -> UNLOCKED
                    null -> ABSENT
                },
            ).joinToString(SEPARATOR)

        companion object {
            fun parse(token: String?): CeilingReading? {
                val parts = token?.split(SEPARATOR) ?: return null
                if (parts.size != 4) return null
                val node = parts[0]
                val upbound = parts[1]
                if (node != UNREADABLE && node.toLongOrNull() == null) return null
                if (upbound != ABSENT && upbound.toLongOrNull() == null) return null
                val cooling = parts[2]
                if (cooling != HELD && cooling != RELEASED && cooling != ABSENT) return null
                val lock = parts[3]
                if (lock != LOCKED && lock != UNLOCKED && lock != ABSENT) return null
                return CeilingReading(
                    nodeCeilingHz = node.toLongOrNull(),
                    platformUpbound = upbound.toLongOrNull(),
                    platformCoolingHeld = when (cooling) {
                        HELD -> true
                        RELEASED -> false
                        else -> null
                    },
                    lockActive = when (lock) {
                        LOCKED -> true
                        UNLOCKED -> false
                        else -> null
                    },
                )
            }
        }
    }

    /**
     * هل السقف مُلبٍّ للطلب؟ — **بالمعنى لا بالحرف**، وبقراءة واحدة أو أكثر.
     *
     * والقواعد:
     *  - لا قراءة ⇒ لا تلبية. «لم أقِس» ليست «نجح» (نفس عقيدة `HardwareVerification`).
     *  - سقف العقدة أعلى من الطلب ⇒ لم يُلبَّ (الجهاز يسمح بأكثر ممّا طُلب).
     *  - سقف منصّة مخصّص **غير صفري** ⇒ لم يُثبت التحرير: قيمة غير صفرية على عقدة سقف تعني أن
     *    أحدًا يقصّ، ومقدارها لا نعرفه (وحداتها غير مؤكَّدة على كل إصدار) فلا نجعلها تُلبّي طلبًا.
     *  - ورفعُ حالة تبريد GPU (غير صفر) ⇒ المنصّة تقصّ ⇒ لا تلبية. وهذا هو الشرط الذي يجعل
     *    «أداء» لا يقول «نجح» بينما الجهاز ما زال مقيّدًا.
     *  - و**قفل OPP قائم** ⇒ لا تلبية: التردد مجمَّد على درجة واحدة من خارج `devfreq`، فقراءةُ
     *    السقف عند القدرة لا تعني أن الجهاز يتوسّع. وهذا العطب لا يظهر في أي قراءة أخرى.
     *  - **واتجاه السقف يتبع نوع الطلب** ([capabilityHz] معلومًا):
     *    · سقفٌ **أدنى** من القدرة (تبريد): يُلبَّى حين لا يسمح الجهاز بأكثر من المطلوب (`node ≤ desired`).
     *    · طلبٌ **عند القدرة** (تحرير): يُلبَّى حين لا يسمح الجهاز بأقلّ من قدرته (`node ≥ capability`).
     *    وهذا هو الفرق الذي كان مُسقَطًا: قراءةُ سقفٍ = ٧٥٤ لطلب ١٣٠٠ كانت تُقرأ «مُلبّاة» لأن
     *    القاعدة الوحيدة كانت `node ≤ desired` — فيُقال للمستخدم «نُفِّذ» وجهازه ما زال على ٧٥٤.
     */
    fun ceilingSatisfied(desiredHz: Long, reading: CeilingReading?, capabilityHz: Long? = null): Boolean {
        val node = reading?.nodeCeilingHz ?: return false
        if (reading.platformUpbound != null && reading.platformUpbound > 0L) return false
        if (reading.platformCoolingHeld == true) return false
        if (reading.lockActive == true) return false
        val capability = capabilityHz?.takeIf { it > 0L }
        if (capability != null && desiredHz >= capability) return node >= capability
        return node <= desiredHz
    }

    /**
     * حكم [ceilingSatisfied] نفسه كرمز سجل ثابت — ليكون الفشل مقروءًا لا صامتًا.
     *
     * وموقع الحكمين من بعضهما مقصود ولا يتكرّر: [releaseVerdict] هي ما يُحكَم به على مقبض
     * per-app (لأن التحقّق من **فعل نملكه**)، و[ceilingReason] يُكتب في سطر التشخيص معها.
     * أما [ceilingSatisfied] فهي سؤال أضيق: «هل بلغ الجهاز القدرة؟» — يُستعمل حين يكون الطلب
     * **عند قدرة معلنة** ويُحكم بمعناها لا بمعنى التحرير.
     */
    fun ceilingReason(desiredHz: Long, reading: CeilingReading?, capabilityHz: Long? = null): String = when {
        reading?.nodeCeilingHz == null -> "gpu-node-ceiling-unreadable"
        reading.platformUpbound != null && reading.platformUpbound > 0L -> "gpu-ceiling-held"
        reading.platformCoolingHeld == true -> "gpu-ceiling-held"
        reading.lockActive == true -> "gpu-opp-lock-held"
        capabilityHz != null && capabilityHz > 0L && desiredHz >= capabilityHz && reading.nodeCeilingHz < capabilityHz -> "gpu-ceiling-held"
        reading.nodeCeilingHz > desiredHz -> "gpu-ceiling-held"
        else -> "gpu-ceiling-released"
    }

    /**
     * هل يحتاج هذا الطلب **تحرير سقف المنصّة** قبل كتابته؟
     *
     * والسؤال ليس «هل النسبة ١٠٠٪؟» بل **أين يقع الطلب من السقف الحيّ**:
     *
     * - طلبٌ **عند السقف الحيّ أو فوقه** (١٣٠٠ لجهاز يسمح بـ٧٥٤، أو ١١٠٥ لسقف ٧٥٤) طلبُ قدرة:
     *   كتابتُه بلا رفع سقف المصنّع لا تُنفَّذ — النواة تقصّه إلى السقف فتقرأ القيمة نفسها، فيبدو
     *   الأمر «طُبِّق» بلا فرق. وهذا بالحرف سبب «Performance لا يعمل»: الحكم على التحرير كان
     *   `request >= capability` وحدها، فطلبٌ قدرته ١١٥٪ من سقفه الحيّ (gaming ٨٥٪ من ١٣٠٠) كان
     *   يُكتب فيُقصّ، والتحرير الذي كان يُنفِّذه لا يُنادى أبدًا.
     * - وطلبٌ **دون السقف الحيّ** طلبُ تبريد: لا يجوز أن يرفع حمايةً وضعها المصنّع (وإلا صار طلب
     *   التبريد تسخينًا)، ويكفيه أن يُكتب.
     *
     * و`liveCeilingHz == null` («لا سقف حيّ مقروء») تعني **قدرة**: بلا قياس لا يُدَّعى أن الطلب
     * تحت سقف، فيُسلَك مسلك التحرير (وهو المسلك الذي يترك الكتابة للنواة إن رفضت).
     */
    fun releaseRequired(requestedHz: Long, liveCeilingHz: Long?): Boolean =
        liveCeilingHz == null || liveCeilingHz <= 0L || requestedHz >= liveCeilingHz

    /**
     * هل يلزم التحرير لكتابة **هذه القيمة** على مقبض سُجِّل بطلب آخر؟
     *
     * ولماذا حكم ثانٍ بعد [releaseRequired]: لأن المقبض الواحد يُعاد استهدافه بقيم مختلفة
     * (`ThermalCeilingRouter` يغيرة عند الضغط وعند زواله)، وحكم التحرير كان يُحسب **مرّة واحدة**
     * عند تسجيل المقبض ثم يُجمَّد داخل إغلاقة الكتابة. فمقبضٌ سُجّل بطلب تبريد (`power` · ٥٢٠)
     * يحمل `plannedRelease = false`، ثم حين يُعاد استهدافه **برفع** عند زوال الضغط تُقيَّد كتابتُه
     * بالسقف الحيّ (وهو خفضنا نفسه) — فلا يرتفع أبدًا. وهو معنى العطب المُبلَّغ عنه حرفيًّا:
     * «التردد لا يزيد عمّا نقص».
     *
     * والقاعدة: `plannedRelease` تُبقى (لا يُلغى قرارُ تحرير اتُّخذ، وبنية المعاملة شُكِّلت عليه)،
     * ويُضاف التحرير حين **يزيد الطلب على السقف الحيّ**، وهو ما لا يمكن أن يُلبّى بلا تحرير.
     *
     * ولا تُستخدم [releaseRequired] هنا بحرفها (`>=`): طلبُ تبريدٍ **يساوي** السقف الحيّ (وقد يكون
     * خفضَنا نحن) لا يجوز أن يرفع حماية المصنّع — وإلا صار التبريد تسخينًا. والمقارنة هنا **صارمة**.
     *
     * @param liveCeilingHz السقف الحيّ الآن (`null` = لا قياس ⇒ لا يُضاف تحرير بالتشقيق).
     */
    fun releaseRequiredForRetarget(
        requestedHz: Long,
        liveCeilingHz: Long?,
        plannedRelease: Boolean,
    ): Boolean = plannedRelease || (liveCeilingHz != null && liveCeilingHz > 0L && requestedHz > liveCeilingHz)

    /**
     * حكم طلب **تحرير السقف** (أي طلب عند السقف الحيّ أو فوقه) كما يُقاس بعد التنفيذ.
     *
     * ولماذا حكم ثانٍ غير [ceilingSatisfied]: لأن التحرير **فعل نملكه** وبلوغ القدرة **حكم منصّة**.
     * فحين تُرفع كل قنواتنا (تبريد GPU · سقف GED · قفل OPP) ويبقى سقف العقدة أدنى من الطلب، فالحقيقة
     * أنّنا فعلنا كل ما نملك وما تحتفظ به المنصّة قياسٌ يُعلَن — لا فشلٌ يُعاد به الجهاز إلى ما كان
     * عليه. وإعادة خط الأساس في تلك الحالة **تمحو التحرير نفسه** وتضمن ألّا يقع تغيير أبدًا (وهو
     * ما جعل «Performance» بلا أثر على الجهاز المقيس).
     *
     * والحالات:
     * - **قفل OPP ثابت** قائم: يُقبل إن كان عند الطلب أو فوقه (تثبيتٌ على أعلى درجة هو مسلك الجهاز
     *   الوحيد حين لا يقبل المدى)، ويُرفض إن كان دونه — ذاك جمودٌ من جلسة سابقة يخنق الجهاز.
     * - **تبريد GPU رافع** أو **سقف GED مخصّص**: أحدٌ يقصّ ⇒ لم يُحرَّر ⇒ لا نجاح كاذب.
     * - **بلا قفل وبلا سقف مخصّص**: بلوغ الطلب = تحرير كامل، وسقفٌ أدنى منه = `OPEN_BELOW_REQUEST`:
     *   نجاحٌ لِما نملك، ورقمٌ يُعرض للذي لا نملك.
     */
    data class ReleaseVerdict(val token: String, val satisfied: Boolean, val measuredHz: Long?) {
        /**
         * نصّ السبب كما يُكتب في السجل واللوحة: الرمز، ومعه الرقم حين كان الرقم هو الفرق بين
         * «تحرير كامل» و«منصّةٌ تحتفظ بسقف» — بلا الرقم يصير السطر عتابًا بلا مقدار.
         */
        val reason: String
            get() = if (measuredHz != null && token == OPEN_BELOW_REQUEST) "$token:$measuredHz" else token

        companion object {
            const val RELEASED = "gpu-ceiling-released"
            const val OPEN_BELOW_REQUEST = "gpu-ceiling-open-below-request"
            const val PINNED = "gpu-pinned-at-request"
            const val HELD = "gpu-ceiling-held"
            const val LOCK_HELD = "gpu-opp-lock-held"
            const val UNREADABLE = "gpu-node-ceiling-unreadable"
        }
    }

    fun releaseVerdict(reading: CeilingReading?, requestedHz: Long): ReleaseVerdict {
        val node = reading?.nodeCeilingHz ?: return ReleaseVerdict(ReleaseVerdict.UNREADABLE, false, null)
        if (reading.platformCoolingHeld == true) return ReleaseVerdict(ReleaseVerdict.HELD, false, node)
        if (reading.lockActive == true) {
            return if (node >= requestedHz) ReleaseVerdict(ReleaseVerdict.PINNED, true, node)
            else ReleaseVerdict(ReleaseVerdict.LOCK_HELD, false, node)
        }
        if (reading.platformUpbound != null && reading.platformUpbound > 0L) {
            return ReleaseVerdict(ReleaseVerdict.HELD, false, node)
        }
        return if (node >= requestedHz) ReleaseVerdict(ReleaseVerdict.RELEASED, true, node)
        else ReleaseVerdict(ReleaseVerdict.OPEN_BELOW_REQUEST, true, node)
    }

    /**
     * حكم **التثبيت**: هل الدرجة المثبَّتة هي التردد الذي يجرى عليه الجهاز؟
     *
     * وهذا سؤال ثانٍ غير «هل قبلت النواة الفهرس؟»: الفهرس قد يُقبل ويُصدّقه الصدى، بينما التردد
     * الحقيقي غيره (سقف منصّة أدنى، أو فهرس من جدول آخر). ولهذا:
     *
     * - قُرئ التردد وساوى المثبَّت ⇒ تثبيت مُثبَت.
     * - قُرئ وخالفه ⇒ `CLOCK_MISMATCH`: يُعلَن ولا يُخفى، ولا يُصلَح بالتخمين.
     * - لم يُقرأ التردد ⇒ `BY_INDEX`: أقوى دليل متاح هو صدى النواة لفهرسنا، فنجعله هو الحكم —
     *   بلا ادّعاء قياس لم يقع.
     * - ولم يُقرأ **فهرس القفل نفسه** ⇒ `UNREADABLE`: لا دليل أصلًا على أن تثبيتًا وقع؛ ولا
     *   يُترجم ذلك نجاحًا ولا فشلًا في حكم التثبيت.
     */
    enum class PinVerdict(val token: String) {
        VERIFIED("pin-verified"),
        BY_INDEX("verified-by-index"),
        CLOCK_MISMATCH("pin-clock-mismatch"),
        UNREADABLE("pin-unreadable"),
    }

    fun pinVerdict(pinnedHz: Long?, measuredHz: Long?): PinVerdict = when {
        pinnedHz == null -> PinVerdict.UNREADABLE
        measuredHz == null -> PinVerdict.BY_INDEX
        measuredHz == pinnedHz -> PinVerdict.VERIFIED
        else -> PinVerdict.CLOCK_MISMATCH
    }

    private const val SEPARATOR = "|"
    private const val UNREADABLE = "unreadable"
    private const val ABSENT = "absent"
    private const val HELD = "held"
    private const val RELEASED = "released"
    private const val LOCKED = "locked"
    private const val UNLOCKED = "unlocked"
}
