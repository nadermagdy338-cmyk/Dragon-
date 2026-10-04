/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.spoof

/**
 * وسوم الحزم في ملفّ COPG (`"com.app:cpu=x:dnd"`) — **نقيّ**: لا قراءة ولا كتابة.
 *
 * ### المصدر وحدّه (مقيس 2026-10-04، من README المستودع العام لا من شفرة المحرّك)
 * README الإصدار 6.8.0 يسرد: `cpu=` `gpu=` `cow` `aid` `serial` `gaid` `appset` `drm` `imei` `sim=` `simx=`
 * `tz=` `lang=` `ua=` `uptime=` `mock` `vpn` `vpns` `hidedev` `blocked` `dnd` `dab` `kso` `nolog` `dpi=`.
 * وREADME أقدم من المستودع نفسه يسرد `with_cpu` و`got` بدل `cpu=`؛ أي أن **النحو تغيّر بين الإصدارات**،
 * والحدّ الفاصل بينهما **غير مقيس**. لذلك:
 *
 * - [CopgGrammar.COMFORT_STABLE] — `dnd dab kso nolog` فقط: **مكتوبة بالحرف في الـREADME القديم والجديد معًا**.
 * - [CopgGrammar.README_6_8] — الباقي: لا يُكتب إلا لوحدة مثبّتة بإصدار **≥ 6.8.0** (أقدم إصدار قُرئ نحوه).
 * - إصدار مجهول ⇒ الأول فقط. المجهول لا يُخمَّن (ADR-07).
 *
 * الوسوم التي لا نفهمها ([Unknown]) **تُحفظ وتُعاد كما هي** عند القراءة والكتابة — لا نُسقط وسمًا لا نعرفه.
 * واستُبعد عمدًا وسم «Play Source / PairIP» ولا يوجد له نوع هنا (قرار المالك/المساعد، انظر PROMPT-SPOOF-STUDIO-V2 §0).
 */
enum class CopgGrammar { COMFORT_STABLE, README_6_8 }

/** مَن يدفع ثمن الوسم: مجاني، أم PRO بمفتاح ترخيص عند صاحب المحرّك. واجهتنا لا تتجاوز الترخيص. */
enum class CopgTier { FREE, PRO }

/** هل الوسم يُبقي شيئًا في ذاكرة التطبيق؟ المقيم قد يكشفه مضاد غش صارم — وREADME نفسه يحذّر. */
enum class CopgFootprint { STEALTH, RESIDENT, SYSTEM_SIDE }

sealed class CopgTag(val key: String, val grammar: CopgGrammar, val tier: CopgTier, val footprint: CopgFootprint) {
    open val value: String? get() = null

    /** النصّ كما يُلحَق بعد `:` في اسم الحزمة. */
    open fun render(): String = if (value == null) key else "$key=$value"

    data object DoNotDisturb : CopgTag("dnd", CopgGrammar.COMFORT_STABLE, CopgTier.FREE, CopgFootprint.SYSTEM_SIDE)
    data object DisableAutoBrightness : CopgTag("dab", CopgGrammar.COMFORT_STABLE, CopgTier.FREE, CopgFootprint.SYSTEM_SIDE)
    data object KeepScreenOn : CopgTag("kso", CopgGrammar.COMFORT_STABLE, CopgTier.FREE, CopgFootprint.SYSTEM_SIDE)
    data object NoLog : CopgTag("nolog", CopgGrammar.COMFORT_STABLE, CopgTier.FREE, CopgFootprint.SYSTEM_SIDE)

    data object PerAppSerial : CopgTag("serial", CopgGrammar.README_6_8, CopgTier.FREE, CopgFootprint.STEALTH)
    data object BlockCpu : CopgTag("blocked", CopgGrammar.README_6_8, CopgTier.FREE, CopgFootprint.STEALTH)
    data object HideVpn : CopgTag("vpn", CopgGrammar.README_6_8, CopgTier.FREE, CopgFootprint.STEALTH)
    data object HideVpnStrict : CopgTag("vpns", CopgGrammar.README_6_8, CopgTier.FREE, CopgFootprint.STEALTH)
    data object HideDeveloper : CopgTag("hidedev", CopgGrammar.README_6_8, CopgTier.FREE, CopgFootprint.STEALTH)

    data class Cpu(val model: String) : CopgTag("cpu", CopgGrammar.README_6_8, CopgTier.FREE, CopgFootprint.STEALTH) {
        override val value: String get() = model
    }
    data class Timezone(val zone: String) : CopgTag("tz", CopgGrammar.README_6_8, CopgTier.FREE, CopgFootprint.SYSTEM_SIDE) {
        override val value: String get() = zone
    }
    data class Language(val bcp47: String) : CopgTag("lang", CopgGrammar.README_6_8, CopgTier.FREE, CopgFootprint.SYSTEM_SIDE) {
        override val value: String get() = bcp47
    }
    data class ScreenDpi(val dpi: Int) : CopgTag("dpi", CopgGrammar.README_6_8, CopgTier.FREE, CopgFootprint.SYSTEM_SIDE) {
        override val value: String get() = dpi.toString()
    }

    // ── PRO عند COPG: نكتب الوسم فقط، والمحرّك وحده يقرّر الترخيص. ──
    data class Gpu(val model: String) : CopgTag("gpu", CopgGrammar.README_6_8, CopgTier.PRO, CopgFootprint.RESIDENT) {
        override val value: String get() = model
    }
    data object PropCow : CopgTag("cow", CopgGrammar.README_6_8, CopgTier.PRO, CopgFootprint.STEALTH)
    data object AndroidId : CopgTag("aid", CopgGrammar.README_6_8, CopgTier.PRO, CopgFootprint.STEALTH)
    data object AdvertisingId : CopgTag("gaid", CopgGrammar.README_6_8, CopgTier.PRO, CopgFootprint.STEALTH)
    data object AppSetId : CopgTag("appset", CopgGrammar.README_6_8, CopgTier.PRO, CopgFootprint.RESIDENT)
    data object Widevine : CopgTag("drm", CopgGrammar.README_6_8, CopgTier.PRO, CopgFootprint.STEALTH)
    data object Imei : CopgTag("imei", CopgGrammar.README_6_8, CopgTier.PRO, CopgFootprint.STEALTH)
    data class Sim(val carrier: String) : CopgTag("sim", CopgGrammar.README_6_8, CopgTier.PRO, CopgFootprint.STEALTH) {
        override val value: String get() = carrier
    }
    data class SimAggressive(val carrier: String) : CopgTag("simx", CopgGrammar.README_6_8, CopgTier.PRO, CopgFootprint.RESIDENT) {
        override val value: String get() = carrier
    }
    data class UserAgent(val profile: String) : CopgTag("ua", CopgGrammar.README_6_8, CopgTier.PRO, CopgFootprint.RESIDENT) {
        override val value: String get() = profile
    }
    data object MockLocationHide : CopgTag("mock", CopgGrammar.README_6_8, CopgTier.PRO, CopgFootprint.STEALTH)

    /** وسم لا نعرفه: يُحفظ حرفيًّا ولا يُفسَّر، ولا تُعرض له واجهة تحرير. */
    data class Unknown(val raw: String) : CopgTag(raw.substringBefore('='), CopgGrammar.README_6_8, CopgTier.FREE, CopgFootprint.STEALTH) {
        override val value: String? get() = raw.substringAfter('=', "").ifEmpty { null }
        /** حرفيًّا — كي لا يضيع `=` الطرفي في وسمٍ لا نفهمه. */
        override fun render(): String = raw
    }

    /** وسوم لا تُقاس على ألعاب مضادة الغش: المقيمة (README يحذّر منها صراحةً). */
    val riskyForAntiCheat: Boolean get() = footprint == CopgFootprint.RESIDENT

    companion object {
        /** قيمة وسم: محارف آمنة فقط (تشمل `/` و`+` لمناطق IANA مثل `Asia/Tokyo` و`Etc/GMT+3`) — لا `:` ولا `=` ولا فراغ طرفي ولا تحكّم، كي لا يُفسَد تقسيم الاسم. */
        private val SAFE_VALUE = Regex("[A-Za-z0-9._ /+-]{1,60}")
        internal fun safeValue(text: String): Boolean = SAFE_VALUE.matches(text) && text == text.trim()

        /** يحلّل لاحقة واحدة (بلا `:` البادئة). لا يرمي: ما لا يُفهم يصير [Unknown]. */
        fun parse(token: String): CopgTag {
            val key = token.substringBefore('=')
            val hasValue = token.contains('=')
            val value = token.substringAfter('=', "")
            fun needValue(build: (String) -> CopgTag): CopgTag =
                if (hasValue && safeValue(value)) build(value) else Unknown(token)
            return when (key) {
                "dnd" -> if (!hasValue) DoNotDisturb else Unknown(token)
                "dab" -> if (!hasValue) DisableAutoBrightness else Unknown(token)
                "kso" -> if (!hasValue) KeepScreenOn else Unknown(token)
                "nolog" -> if (!hasValue) NoLog else Unknown(token)
                "serial" -> if (!hasValue) PerAppSerial else Unknown(token)
                "blocked" -> if (!hasValue) BlockCpu else Unknown(token)
                "vpn" -> if (!hasValue) HideVpn else Unknown(token)
                "vpns" -> if (!hasValue) HideVpnStrict else Unknown(token)
                "hidedev" -> if (!hasValue) HideDeveloper else Unknown(token)
                "cow" -> if (!hasValue) PropCow else Unknown(token)
                "aid" -> if (!hasValue) AndroidId else Unknown(token)
                "gaid" -> if (!hasValue) AdvertisingId else Unknown(token)
                "appset" -> if (!hasValue) AppSetId else Unknown(token)
                "drm" -> if (!hasValue) Widevine else Unknown(token)
                "imei" -> if (!hasValue) Imei else Unknown(token)
                "mock" -> if (!hasValue) MockLocationHide else Unknown(token)
                "cpu" -> needValue(::Cpu)
                "gpu" -> needValue(::Gpu)
                "tz" -> needValue(::Timezone)
                "lang" -> needValue(::Language)
                "sim" -> needValue(::Sim)
                "simx" -> needValue(::SimAggressive)
                "ua" -> needValue(::UserAgent)
                "dpi" -> if (hasValue) value.toIntOrNull()?.takeIf { it in 80..960 }?.let(::ScreenDpi) ?: Unknown(token) else Unknown(token)
                else -> Unknown(token)
            }
        }
    }
}

/** اسم حزمة مع وسومه كما يظهر في مصفوفة COPG. */
data class CopgPackageEntry(val packageName: String, val tags: List<CopgTag>) {
    fun render(): String = (listOf(packageName) + tags.map(CopgTag::render)).joinToString(":")

    companion object {
        fun parse(entry: String): CopgPackageEntry {
            val parts = entry.split(':')
            return CopgPackageEntry(parts.first(), parts.drop(1).filter { it.isNotEmpty() }.map(CopgTag::parse))
        }
    }
}

/** أي نحوٍ يجوز الكتابة به لوحدةٍ بهذا الإصدار؟ إصدار مجهول ⇒ المستقرّ فقط. */
object CopgGrammarGate {
    /** أقدم إصدار قُرئ نحوه كاملًا (README الرئيسي). */
    val README_6_8_MIN = intArrayOf(6, 8, 0)

    fun allowed(moduleVersion: String?): Set<CopgGrammar> =
        if (moduleVersion != null && atLeast(moduleVersion, README_6_8_MIN)) CopgGrammar.entries.toSet()
        else setOf(CopgGrammar.COMFORT_STABLE)

    /** يقارن `vX.Y.Z` أو `X.Y.Z` (يتجاهل اللاحقة بعد الرقم). غير قابل للتحليل ⇒ `false`. */
    fun atLeast(version: String, minimum: IntArray): Boolean {
        val numbers = version.trim().removePrefix("v").removePrefix("V")
            .split('.', '-', ' ').take(3).map { it.takeWhile(Char::isDigit).toIntOrNull() ?: return false }
        if (numbers.size < 3) return false
        for (index in minimum.indices) {
            if (numbers[index] != minimum[index]) return numbers[index] > minimum[index]
        }
        return true
    }

    /** يُسقط من القائمة ما لا يجوز كتابته بهذا الإصدار — ويعيده منفصلًا ليُعرض للمستخدم لا أن يضيع صامتًا. */
    fun partition(tags: Collection<CopgTag>, moduleVersion: String?): Pair<List<CopgTag>, List<CopgTag>> =
        tags.partition { it.grammar in allowed(moduleVersion) || it is CopgTag.Unknown }
}

/** قواعد مجموعة وسوم تطبيق واحد — نقيّة، وتُفرض في [AppSpoofProfile] فلا تدخل مجموعة متناقضة مساحة العمل. */
object CopgTagRules {
    const val MAX_TAGS = 24

    /** وسمٌ معروف بصيغته القانونية فقط (`cpu=x` لا `cpu= x`، ولا وسم مجهول). */
    fun valid(tag: String): Boolean = CopgTag.parse(tag).let { it !is CopgTag.Unknown && it.render() == tag }

    private val EXCLUSIVE = listOf(setOf("sim", "simx"), setOf("vpn", "vpns"), setOf("cpu", "blocked"))

    /** أزواج المفاتيح المتعارضة: مفتاح مكرّر بقيمتين، أو وسمان متنافيان. فارغة = سليمة. */
    fun conflicts(tags: Collection<String>): List<Set<String>> {
        val keys = tags.map { it.substringBefore('=') }
        val duplicated = keys.groupBy { it }.filterValues { it.size > 1 }.keys.map { setOf(it) }
        val exclusive = EXCLUSIVE.filter { pair -> pair.all { it in keys } }
        return duplicated + exclusive
    }
}
