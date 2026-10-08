/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.emuhub

/**
 * كتالوج الأنظمة والامتدادات — **معلومة منسوبة، لا كود منقول**.
 *
 * جدول الأنظمة وامتداداتها مأخوذ من `README` الخاصّ بـ`Swordfish90/Lemuroid` كما وثّقته
 * `docs/ai/EMULATOR-HUB-PLAN.md` §٣.٢. وترخيص Lemuroid **GPLv3** ⇒ **صفر كود ولو سطرًا**
 * (ADR-55 · `AGENTS.md` §0.4-١)؛ وما أُخذ **واقعة** لا تأليفًا: أيّ نظام يقبل أيّ امتداد.
 * والفضل مُعلَن في `THIRD_PARTY_NOTICES.md` وفي قسم `Credits` بـ`README.md` — لأنّ الإعلان
 * واجب حتى حين لا يُنقل كود (§0.4-٣).
 *
 * ### قاعدتان تحكمان هذا الملفّ
 *
 * ١. **الامتداد قرينة لا إثبات** — `.bin` و`.iso` يقبلهما أكثر من نظام، فلا يُخمَّن صامتًا
 *    (روح ADR-07). التصنيف هنا يُرجع **مرشّحين**، والحسم في [EmuRomIndexer] بقرائن،
 *    وإن لم ترجحن قرينة يُسأل المستخدم مرّة واحدة ويُحفظ اختياره.
 * ٢. **لا امتداد مُخترع** — كل سطر هنا منسوب إلى الجدول أعلاه. وما أضافه المستخدم بنفسه
 *    (`extraExtensions`) يُعرض بحالة «نظام غير معروف» ولا يُنسب إلى نظام لم يقله أحد.
 *
 * وأسماء الأنظمة **أعلام لا تُترجم** (`PSX` · `GBA` · `N64`)، فلا مفتاح ترجمة لكلّ نظام؛
 * وما يُترجم فعلًا هو **حالة** العنصر («نظام ملتبس»، «غير معروف») وهي في `strings.xml`.
 */
enum class EmuSystem(val id: String, val label: String, val extensions: Set<String>) {
    ATARI_2600("atari2600", "Atari 2600", setOf("a26")),
    ATARI_7800("atari7800", "Atari 7800", setOf("a78")),
    ATARI_LYNX("atarilynx", "Atari Lynx", setOf("lnx")),
    NES("nes", "NES", setOf("nes", "unf", "fds")),
    SNES("snes", "SNES", setOf("sfc", "smc")),
    GB("gb", "Game Boy", setOf("gb")),
    GBC("gbc", "Game Boy Color", setOf("gbc")),
    GBA("gba", "Game Boy Advance", setOf("gba")),
    GENESIS("genesis", "Genesis", setOf("md", "gen", "smd")),
    SEGA_CD("segacd", "Sega CD", setOf("cue", "bin")),
    SMS("sms", "Master System", setOf("sms")),
    GG("gg", "Game Gear", setOf("gg")),
    N64("n64", "N64", setOf("z64", "n64", "v64")),
    PSX("psx", "PSX", setOf("cue", "bin", "chd", "pbp", "iso")),
    PSP("psp", "PSP", setOf("iso", "cso")),
    NDS("nds", "NDS", setOf("nds")),
    PCE("pce", "PC Engine", setOf("pce")),
    NGP("ngp", "Neo Geo Pocket", setOf("ngp", "ngc")),
    WS("ws", "WonderSwan", setOf("ws", "wsc")),
    N3DS("n3ds", "3DS", setOf("3ds", "cia")),
    ARCADE("arcade", "Arcade", setOf("zip")),
    ;

    companion object {
        private val byId = EmuSystem.entries.associateBy { it.id }

        /** `null` للمعرّف المجهول — النداء يُقرأ فيُعالج، بدل `valueOf` الذي يرمي. */
        fun of(id: String?): EmuSystem? = id?.let(byId::get)
    }
}

/**
 * ما آل إليه ملفّ واحد بعد التصنيف — **أربع حالات لا واحدة**، وهذا هو الفرق بين مكتبة
 * صادقة ومكتبة تُخمّن.
 */
sealed interface RomClassification {
    /** امتداد لا يقبله إلا نظام واحد. */
    data class Known(val system: EmuSystem) : RomClassification

    /** امتداد مشترك: [candidates] مرتّبة، والقرار للمستخدم إن لم ترجحن قرينة. */
    data class Ambiguous(val candidates: List<EmuSystem>) : RomClassification

    /** `.exe` — برنامج ويندوز: يحتاج بيئة تشغيل، ولا عقد تشغيل مباشر (الخطة §٣.٤). */
    data object PcRuntime : RomClassification

    /** أرشيف (`.zip`): النظام **داخل** الملفّ ولا يُقرأ من خارجه، فلا يُخمَّن. */
    data class Archive(val container: String) : RomClassification

    /** امتداد لا نعرفه (ولا أضافه المستخدم). */
    data object Unknown : RomClassification
}

/**
 * فهرس الامتداد ⇒ الأنظمة، **مشتقّ من [EmuSystem] لا مكتوب بيد ثانية**.
 *
 * وهذا ليس تنظيمًا: نسختان من الجدول تفترقان عند أوّل إضافة نظام (نفس درس `GameLibrary`:
 * مفتاح واحد يقرأه موضعان). والاشتقاق يضمن أن كل نظام جديد يُسجّل نفسه في الفهرس تلقائيًّا.
 */
object EmuExtensionIndex {
    /** امتدادات غير أنظمة محاكاة، لكنّها تُعرض في المكتبة بحالتها الصادقة. */
    const val PC_EXTENSION = "exe"
    val ARCHIVE_EXTENSIONS: Set<String> = setOf("zip", "7z")
    const val PLAYLIST_EXTENSION = "m3u"

    /** امتداد (بلا نقطة، بحروف صغيرة) ⇒ الأنظمة التي تقبله، مرتّبة بترتيب الإعلان. */
    val byExtension: Map<String, List<EmuSystem>> = buildMap {
        EmuSystem.entries.forEach { system ->
            system.extensions.forEach { extension -> put(extension, (get(extension).orEmpty() + system)) }
        }
    }

    /** كل امتداد يُعرض في المكتبة — بلا `.exe` (له حالته) وبلا `.m3u` (حاوية لا لعبة). */
    val listedExtensions: Set<String> = byExtension.keys - PC_EXTENSION - PLAYLIST_EXTENSION

    /** امتداد الملفّ بحروف صغيرة وبلا نقطة، أو `""`. */
    fun extensionOf(fileName: String): String =
        fileName.substringAfterLast('.', "").lowercase()

    /**
     * تصنيف بالامتداد وحده — **بلا قرائن**. هذا هو المدخل الخام، والحسم في
     * [EmuRomIndexer.classify].
     *
     * @param extraExtensions امتدادات أضافها المستخدم: تُعرض ولا تُنسب إلى نظام.
     */
    fun classifyByName(fileName: String, extraExtensions: Set<String> = emptySet()): RomClassification {
        val extension = extensionOf(fileName)
        if (extension == PC_EXTENSION) return RomClassification.PcRuntime
        if (extension in ARCHIVE_EXTENSIONS) return RomClassification.Archive(extension)
        val systems = byExtension[extension]
        return when {
            systems == null -> RomClassification.Unknown
            systems.size == 1 -> RomClassification.Known(systems.single())
            else -> RomClassification.Ambiguous(systems)
        }
    }

    /**
     * هل يُدرج هذا الملفّ في المكتبة؟ — امتداد معروف، أو امتداد أضافه المستخدم، أو `.exe`.
     * و`.m3u` **لا** يُدرج: هو قائمة تشير إلى أقراص تُدرج هي بأنفسها.
     */
    fun isListable(fileName: String, extraExtensions: Set<String> = emptySet()): Boolean {
        val extension = extensionOf(fileName)
        if (extension == PLAYLIST_EXTENSION) return false
        if (extension in extraExtensions) return true
        return extension in listedExtensions || extension == PC_EXTENSION
    }
}
