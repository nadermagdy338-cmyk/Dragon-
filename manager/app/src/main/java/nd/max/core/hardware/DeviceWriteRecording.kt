package nd.max.core.hardware

/**
 * تسجيل **كتابة** من جهاز حقيقي — الطبقة التي كانت تنقص كي لا يبقى «needs device» حالةً دائمة.
 *
 * لماذا وُجد هذا الملف
 * -------------------
 * [nd.max.core.atlas.AtlasFixture] أغلق هذه الفجوة **للقراءة**: أجوبة نواة حقيقية تُحفظ كبيانات
 * وتُعاد عبر نفس الموصل المُشحون. لكن الكتابة بقيت على حالها: كل اختبار كتابة يقيس **جهازًا كتبه
 * مؤلفه** في `FakeIo`، أي يقيس افتراض المؤلف لا الجهاز. وكل ما بعد الكتابة (‏`differs` ⇒ احتياطي ·
 * تراجع خط الأساس · انحراف يُصلَح · ملكية مشتركة) لم يُقَس إلا على هاتف.
 *
 * وهذا الملف هو النصف الثاني: تُسجَّل **أزواج (مسار، مكتوب، مقروء، حكم)** حرفيًّا من
 * `EVENT=WRITE_CHECK`، ويُعاد الجهاز منها عبر [RecordingIo] فتمرّ معاملة الكتابة الحقيقية
 * ([GpuHardwareBackend.applyValidated]) والمُحكِّم كما تمرّ على هاتف.
 *
 * وثلاثة قواعد تجعل الإعادة ذات قيمة (وهي نفس قواعد تسجيل القراءة، لأن العطب واحد في الاثنين):
 *
 * 1. **لا قيمة مُخترعة في وضع الإعادة.** كتابةٌ لم تُسجَّل تُرفض وتُعدّ — إلا إن كانت قيمتها من
 *    **قائمة ترددات رأيناها على هذه العقدة** (معلنة أو مقبولة في الحزمة)، وذاك تصريح معلن يُعدّ
 *    منفصلًا (`inferred`). فـ«الجهاز قبل ما كتبناه» لا يُدَّعى أبدًا بلا سطر في التسجيل.
 * 2. **حدود التسجيل معلنة.** عدد الكتابات، وقائمة الترددات، والأحكام الثلاثة — كلها صريحة، فلا
 *    يتحوّل التسجيل إلى ملف يُخزَّن فيه أي شيء «للاحتياط».
 * 3. **المصدر مكتوب.** تسجيلٌ من هاتف يُقال عنه `DEVICE`، ومن مُشغّل بناء `HOST`، ومكتوب بيد
 *    `SYNTHETIC` — وهو الفرق بين «عندنا تغطية جهاز» و«عندنا ماكينة تعمل».
 *
 * وما لا يُدَّعى هنا: **سلطة المنصّة**. `PlatformCeilingAuthority` تقرأ عدة عقد بلا موصل، فلا
 * يستطيع تسجيلٌ واحد أن يشهد لها؛ و[MockIo] يُرث `permitVendorCeiling = false` فنبقى بلا ادّعاء.
 */

/** أي آلة أنتجت التسجيل. و`HOST` تُثبت الماكينة ولا تشهد على هاتف. */
enum class DeviceRecordingOrigin { DEVICE, HOST, SYNTHETIC }

/** سطر كتابة واحد كما ورد في `WRITE_CHECK`. */
data class RecordedWrite(
    val path: String,
    val wrote: String,
    val read: String,
    val verdict: Verdict,
    /** `TIME_ONLY` = الوقت من السطر، ليربط القارئ السطر بمصدره. */
    val at: String = "",
) {
    /** الحكم كما كُتب على الجهاز: `matched` أو `differs`. ولا ثالث يُخترع. */
    enum class Verdict { MATCHED, DIFFERS }

    init {
        require(path.startsWith("/")) { "a recorded write path is absolute: $path" }
        require(wrote.isNotBlank()) { "a recorded write carries the value it wrote" }
        require(read.isNotBlank()) { "a recorded write records what the node answered" }
        require(!wrote.contains(' ') && !read.contains('\n')) { "a recorded write is one line per field" }
    }
}

/**
 * الجهاز كما رأيناه في التسجيل: مساره، وتردداتٌ **رأيناها معلنة أو مقبولة**، وقفل OPP إن وُجد.
 *
 * و`observedFrequencies` ليست «جدول OPP كاملًا» — هي ما ظهر في الحزمة (مُعلَنًا أو مقبولًا في
 * كتابة ناجحة)، ويُقال ذلك في الاسم لا في تعليق فقط. الجدول الموقّع لم يكن في الحزمة فلا يُخترع.
 */
data class RecordedGpuDevice(
    val path: String,
    val name: String,
    val advertisedMaxHz: Long,
    val liveMaxHz: Long,
    val liveMinHz: Long,
    val currentHz: Long?,
    val observedFrequencies: List<Long>,
    val governor: String,
    val governors: List<String>,
    val fixIndexPath: String? = null,
    /** صدى عقدة الفهرس كما قرأها الجهاز (نصّ التشخيص)، أو `null` إن لم تُقرأ. */
    val fixIndexEcho: String? = null,
    val governorWritable: Boolean = true,
    val lockWritable: Boolean = true,
) {
    init {
        require(path.startsWith("/") && name.isNotBlank()) { "a recorded device has a path and a name" }
        require(advertisedMaxHz > 0L && liveMaxHz > 0L && liveMinHz > 0L) { "recorded frequencies are positive" }
        require(liveMinHz <= liveMaxHz) { "a recorded range is ordered" }
        require(observedFrequencies.isNotEmpty() && observedFrequencies.all { it > 0L }) {
            "a recording states the frequencies it actually saw"
        }
        require(observedFrequencies == observedFrequencies.distinct().sorted()) {
            "recorded frequencies are distinct and sorted, so a replay is deterministic"
        }
        if (advertisedMaxHz !in observedFrequencies) {
            require(false) { "the advertised maximum must be one of the observed frequencies" }
        }
    }

    /** الكائن الذي يقرؤه المُحكِّم — يُبنى من التسجيل، فلا قيمة فيه غير مسجَّلة. */
    fun toDevice(): GpuHardwareBackend.Device = GpuHardwareBackend.Device(
        path = path,
        name = name,
        governor = governor,
        governors = governors,
        minFreq = liveMinHz,
        maxFreq = liveMaxHz,
        currentFreq = currentHz,
        frequencies = observedFrequencies,
        frequencyUnit = GpuHardwareBackend.FrequencyUnit.HZ,
        minWritable = true,
        maxWritable = true,
        governorWritable = governorWritable,
        // ولا مسار قفل يُدَّعى: جدول OPP الموقّع لم يكن في الحزمة، و`mtkOppIndexByFrequency` فارغة
        // تجعل `exactLockWritable` = false، فلا يختار المُحكِّم تثبيتًا لم يُقَس.
        mtkFixedIndexPath = null,
        mtkLockWritable = false,
        mtkOppIndexByFrequency = emptyMap(),
        evidence = listOf("recorded-write-fixture"),
    )
}

/** تسجيل كامل: مصدره، وبصمة البناء، والجهاز، وسطور الكتابة. */
data class DeviceWriteRecording(
    val origin: DeviceRecordingOrigin,
    val build: String,
    val device: RecordedGpuDevice,
    val writes: List<RecordedWrite>,
    /** قراءات أخرى مسجَّلة (عقد سلطة/حالة) تُخدَم كما وردت بلا تفسير. */
    val extraReads: Map<String, String> = emptyMap(),
) {
    init {
        require(build.isNotBlank()) { "a recording states the build it came from" }
        require(writes.isNotEmpty()) { "a recording with no write proves nothing about writing" }
        require(writes.size <= MAX_WRITES) { "a recording is bounded; refuse rather than keep a dump" }
        extraReads.keys.forEach { path -> require(path.startsWith("/")) { "an extra read key is absolute: $path" } }
    }

    companion object {
        const val MAX_WRITES: Int = 512
    }
}

/**
 * الجهاز المُسجَّل كموصل كتابة/قراءة — يُمرَّر إلى [GpuHardwareBackend.applyValidated] نفسه.
 *
 * وحدود الإعادة معلنة بالعدّ لا بالوصف: [replayed] كتابة سطرُها في التسجيل، و[inferred] كتابة
 * قيمتها من قائمة الترددات المرصودة (فالتسجيل لا يشهد لها)، و[refused] كتابة لم تُسجَّل ولم
 * تُرصد ⇒ تُرفض ولا تُنفَّذ. ومَن يقرأ الأرقام يعرف بالضبط أيّ ثلاث حالات وقعت.
 */
class RecordingIo(
    private val recording: DeviceWriteRecording,
    /** وضع النمذجة: يقبل قيمةً من الترددات المرصودة على عقدة الترددات. `false` = إعادة صارمة. */
    private val modelObservedFrequencies: Boolean = true,
) : GpuHardwareBackend.Io {

    private val state = linkedMapOf<String, String>()
    private val device = recording.device

    var replayed: Int = 0
        private set
    var inferred: Int = 0
        private set
    var refused: Int = 0
        private set

    /** ما كُتب فعلًا، بالترتيب — يُقاس به «هل وقعت الكتابة» لا «ماذا قال السجل». */
    val applied = mutableListOf<Pair<String, String>>()

    init {
        state["${device.path}/device_name"] = device.name
        state["${device.path}/available_frequencies"] = device.observedFrequencies.joinToString(" ")
        state["${device.path}/cur_freq"] = (device.currentHz ?: device.liveMinHz).toString()
        state["${device.path}/governor"] = device.governor
        state["${device.path}/available_governors"] = device.governors.joinToString(" ")
        state["${device.path}/min_freq"] = device.liveMinHz.toString()
        state["${device.path}/max_freq"] = device.liveMaxHz.toString()
        recording.extraReads.forEach { (path, value) -> state[path] = value }
    }

    fun value(path: String): String? = state[path]

    override fun exists(path: String): Boolean =
        path in state || path == device.path || path == device.fixIndexPath ||
            path.startsWith("${device.path}/") || path.startsWith("${device.path}")

    override fun writable(path: String): Boolean = path.endsWith("/min_freq") || path.endsWith("/max_freq") ||
        path.endsWith("/governor") || path == device.fixIndexPath

    override fun read(path: String): String? = state[path]

    override fun write(path: String, value: String): Boolean {
        // ١) سطرٌ في التسجيل: يُعاد حرفيًّا — بما فيه حكم `differs` (صدى عقدة لا يساوي المكتوب).
        val recorded = recording.writes.lastOrNull { it.path == path && it.wrote == value }
        if (recorded != null) {
            replayed += 1
            applied += path to value
            state[path] = recorded.read
            return true
        }
        // ٢) قيمةٌ من الترددات المرصودة على عقدة الترددات: نمذجة معلنة، وتُعدّ منفصلة.
        val isFrequencyNode = path.endsWith("/min_freq") || path.endsWith("/max_freq")
        val asHz = value.toLongOrNull()
        if (modelObservedFrequencies && isFrequencyNode && asHz != null && asHz in device.observedFrequencies) {
            inferred += 1
            applied += path to value
            state[path] = value
            return true
        }
        // ٣) ما لا تسجيلَ له ولا رصد ⇒ لا تنفيذ. اختبارٌ يمرّ هنا كان سيقيس جهازًا لم نُسجّله.
        refused += 1
        return false
    }

    override fun listDirectories(path: String): List<String> =
        if (path == "/sys/class/devfreq") listOf(device.name) else emptyList()

    override fun permitVendorCeiling(lock: Boolean, release: Boolean): Boolean = false
}
