/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * ───────────────────────── Attribution (Apache-2.0) ─────────────────────────
 * The parameter protocol below is adapted from the **DolbyUI** implementation — branch `rodin` of
 * `Digimend-X-Rodin/packages_apps_DolbyUI` (app module `LunarisDolby`), licensed **Apache-2.0** as
 * stated in its own file headers (`audio/DolbyAudioEffect.kt`, `DolbyConstants.kt`), and used with
 * the copyright holder's written permission. What was taken is the **wire protocol**: the header
 * layout of each call, the parameter ids, and the little-endian byte order. The credit is
 * recorded in `docs/PROVENANCE.md`.
 */
/*
 * الصوت — **بروتوكول معاملات Dolby DAP**، صافيًا وقابلًا للقياس على JVM بلا جهاز.
 *
 * **مصدر البروتوكول مقيسٌ لا مُقدَّر:** ملفّان من مستودع `Digimend-X-Rodin/packages_apps_DolbyUI`
 * (فرع `rodin`، وحدة `LunarisDolby`، رخصة Apache-2.0) — `audio/DolbyAudioEffect.kt`
 * و`DolbyConstants.kt`؛ قُرئا كاملين في هذه الجولة، ونُقل **البروتوكول** (ترويسة كل نداء وأرقامه
 * وترتيب بايتاته) لا شيفرة الواجهة ولا تصميمها. وانظر `docs/PROVENANCE.md` — مدخل
 * `DolbyUI (Lunaris AOSP)` موجود، ونسبة الفضل شرطُ النقل.
 *
 * **ولماذا يُنقل البروتوكول أصلًا وهو لا يُستعمل بعد؟** لأنّه **القابل للقياس** في مسار Dolby كلّه:
 * الـ`android.jar` الذي نُصرَّف عليه (مقيس بـ`javap`) **لا يحمل** `AudioEffect(UUID,UUID,int,int)`
 * ولا `setParameter`/`getParameter` (كلّها `@hide`)، فالوصول إليها لا يكون إلا بالانعكاس أو بطبقة
 * نظاميّة — أي أنّ **التحقّق على جهاز**. أما ترويسة النداء فتُقاس هنا: تُبنى، وتُقرأ، وتُقابَل بالحرف.
 *
 * **وهذا هو الفرق بين الوعد والقياس:** ما يُقاس هنا هو أنّ لنا وصفةً صحيحةً موثّقةً بالبايت إن
 * اتّضح على الجهاز أنّ المسار مفتوح؛ وما لا يُقاس هو أنّ المسار مفتوح — ويُقال ذلك صراحةً.
 *
 * **وحدود البروتوكول كما قُرئت حرفيًّا من المصدر:**
 * - `setIntParam(param, value)`  ⇒ صندوق **١٢ بايت**: `[param, 1, value]` ثمّ `setParameter(5, buf)`.
 * - `getIntParam(param)`         ⇒ صندوق ١٢ بايت (‏`[param]`)، والقراءة **أوّل** عدد صحيح داخله.
 * - `setDapParameter(p, values)` ⇒ صندوق `(len+4)*4`: `[0x1000000, len+1, profile, p.id, …values]`.
 * - `getDapParameter(p, profile)`⇒ معرّف الطلب `(p.id shl 16) + (profile shl 8) + 0x1000005`.
 * - `resetProfileSpecificSettings(profile)` ⇒ `setIntParam(0xC000000, profile)`.
 * - كلّ الأعداد **إنديّة صغيرة** (little-endian): البايت الأدنى أوّلًا، والرابع هو الأعلى.
 *
 * **وما زِدنا على المصدر — لأنهما حراسة لا تفصيل:** المطابقة بين `values.size` و`DolbyDapParam.length`
 * (وإلا رفضنا البناء)، وحدود إزاحة معرّف القراءة (فلا يتداخل معرّف المعامل مع بايت الملفّ الشخصيّ).
 */
package nd.max.core.audio

/**
 * معاملات DAP كما قُرئت من `DolbyConstants.DsParam` — **والأرقام والأطوال منقولة بأمانة**:
 * `GEQ_BAND_GAINS` وحدها ذات طولٍ ٢٠، والبقية معاملٌ واحد.
 */
enum class DolbyDapParam(val id: Int, val length: Int = 1) {
    HEADPHONE_VIRTUALIZER(101),
    SPEAKER_VIRTUALIZER(102),
    VOLUME_LEVELER_ENABLE(103),
    IEQ_PRESET(104),
    DIALOGUE_ENHANCER_ENABLE(105),
    DIALOGUE_ENHANCER_AMOUNT(108),
    GEQ_BAND_GAINS(110, 20),
    BASS_ENHANCER_ENABLE(111),
    STEREO_WIDENING_AMOUNT(113),
}

/**
 * البروتوكول: بناء ما يُرسل وقراءة ما يعود — **صافيٌّ تمامًا**: لا `AudioEffect` ولا انعكاس هنا.
 *
 * وكل دالّة تُعيد `null` بدل أن تخترع قيمة: أكبر معرّف يخرج من المدى، أو قيمٌ لا تطابق طول معاملها،
 * تُرفض بسببها — والقاعدة هي نفسها في كل المستودع: لا طلب مشوّه يصل إلى المادّة.
 */
object DolbyDapProtocol {

    /** معرّف المعامل الذي يُرسل مع `setParameter` لحمل الأعداد الصحيحة. */
    const val CPDP_VALUES = 5

    /** معامل التمكين — يُرسل بصندوق `[0, 1, value]`. */
    const val ENABLE_PARAM = 0

    /** معامل الملفّ الشخصيّ (Profile). */
    const val PROFILE_PARAM = 0xA000000

    /** ترويسة `setDapParameter`: بايتها الأوّل. */
    const val SET_PROFILE_PARAMETER = 0x1000000

    /** آخر بايت في معرّف `getDapParameter`. */
    const val GET_PROFILE_PARAMETER = 0x1000005

    /** معامل إعادة ضبط إعدادات الملفّ الشخصيّ. */
    const val RESET_PROFILE_SETTINGS = 0xC000000

    /** أقصى معرّفٍ يمكن إزاحته ١٦ بتًا بلا تداخل مع بايت الملفّ الشخصيّ. */
    private const val MAX_PARAM_ID = 0xFFFF

    /** أقصى رقم ملفّ شخصيّ يمكن إزاحته ٨ بتات بلا تجاوز. */
    private const val MAX_PROFILE = 0xFF

    // ─────────────────────────────── الأعداد: إنديّة صغيرة، مصدرها واحد ───────────────────────────────

    /** يكتب عددًا صحيحًا (little-endian) في [buffer] عند [index]. */
    fun writeInt32Le(buffer: ByteArray, index: Int, value: Int) {
        val idx = index
        buffer[idx] = (value and 0xff).toByte()
        buffer[idx + 1] = ((value ushr 8) and 0xff).toByte()
        buffer[idx + 2] = ((value ushr 16) and 0xff).toByte()
        buffer[idx + 3] = ((value ushr 24) and 0xff).toByte()
    }

    /** يقرأ عددًا صحيحًا (little-endian) من [buffer] عند [index] — و`null` إن لم تكفِ البايتات. */
    fun readInt32Le(buffer: ByteArray, index: Int): Int? {
        if (index < 0 || index + 4 > buffer.size) return null
        return ((buffer[index + 3].toInt() and 0xff) shl 24) or
            ((buffer[index + 2].toInt() and 0xff) shl 16) or
            ((buffer[index + 1].toInt() and 0xff) shl 8) or
            (buffer[index].toInt() and 0xff)
    }

    // ────────────────────────────── الأعداد المفردة: تمكين/ملفّ شخصيّ ──────────────────────────────

    /**
     * صندوق `setIntParam` — **١٢ بايت دائمًا** (ثلاثة أعداد): المعامل، ثمّ ١ (كثابتٍ في المصدر)،
     * ثمّ القيمة.
     */
    fun intParamBlock(param: Int, value: Int): ByteArray {
        val buffer = ByteArray(12)
        writeInt32Le(buffer, 0, param)
        writeInt32Le(buffer, 4, 1)
        writeInt32Le(buffer, 8, value)
        return buffer
    }

    /** قيمة ما عاد من `getIntParam` — **العدد الأوّل** وحده، كما يقرؤه المصدر. */
    fun intParamValue(block: ByteArray): Int? = readInt32Le(block, 0)

    /** إعادة ضبط إعدادات ملفّ شخصيّ — `setIntParam(0xC000000, profile)`. */
    fun resetProfileBlock(profile: Int): ByteArray = intParamBlock(RESET_PROFILE_SETTINGS, profile)

    // ─────────────────────────────────── معاملات الملفّ الشخصيّ ───────────────────────────────────

    /**
     * صندوق `setDapParameter`: `(values.size + 4) * 4` بايت —
     * `[0x1000000, values.size + 1, profile, param.id, …values]`.
     *
     * **ويُرفض** (`null`) إذا خالف عددُ القيم طولَ المعامل المُعلَن: كتابةٌ بطولٍ مخالف تُفسد
     * المعامل التالي في المادّة، وهي عطبٌ لا يُنتجه مُصرّف — فالمطابقة شرطُ بناءٍ هنا لا نيّة حسنة.
     */
    fun profileParameterBlock(param: DolbyDapParam, values: IntArray, profile: Int): ByteArray? {
        if (values.size != param.length) return null
        if (profile < 0) return null
        val buffer = ByteArray((values.size + 4) * 4)
        writeInt32Le(buffer, 0, SET_PROFILE_PARAMETER)
        writeInt32Le(buffer, 4, values.size + 1)
        writeInt32Le(buffer, 8, profile)
        writeInt32Le(buffer, 12, param.id)
        values.forEachIndexed { offset, value -> writeInt32Le(buffer, 16 + offset * 4, value) }
        return buffer
    }

    /**
     * معرّف طلب القراءة: `(param.id shl 16) + (profile shl 8) + 0x1000005`.
     *
     * **والحدّ مضافٌ من عندنا:** لو تجاوز `param.id` ما يسعه ١٦ بتًا، أو تجاوز الملفّ الشخصيّ بايتًا
     * واحدًا، لتداخلت الإزاحات وصار الطلب يسأل عن معامل آخر — فيُرفض الطلب بدل أن يُرسل مشوَّهًا.
     */
    fun profileParameterRequestId(param: DolbyDapParam, profile: Int): Int? {
        if (param.id < 0 || param.id > MAX_PARAM_ID) return null
        if (profile < 0 || profile > MAX_PROFILE) return null
        return (param.id shl 16) + (profile shl 8) + GET_PROFILE_PARAMETER
    }

    /** صندوق قراءة معامل ملفّ شخصيّ: `(length + 2) * 4` بايت — وهو ما يملؤه `getParameter`. */
    fun profileParameterBuffer(param: DolbyDapParam): ByteArray = ByteArray((param.length + 2) * 4)

    /**
     * القيم التي عادت في صندوق القراءة — بعددٍ لا يتجاوز طول المعامل ولا حجم الصندوق
     * (والمصدر نفسه يقرأ الأصغر من الاثنين، فلا نقرأ بايتات لم تُكتب).
     */
    fun profileParameterValues(block: ByteArray, param: DolbyDapParam): IntArray {
        val available = block.size / 4
        val count = minOf(param.length, available)
        if (count <= 0) return IntArray(0)
        return IntArray(count) { index -> readInt32Le(block, index * 4) ?: 0 }
    }
}
