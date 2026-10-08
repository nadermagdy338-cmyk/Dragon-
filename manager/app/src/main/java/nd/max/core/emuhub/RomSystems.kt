/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 *
 * MaxManager proprietary source. See LICENSE at the repository root: this file is
 * MaxManager-owned and carries no third-party licence obligations.
 */

package nd.max.core.emuhub

/**
 * جدول الأنظمة والامتدادات — منطق نقيّ يُقاس بلا جهاز.
 *
 * **المصدر منسوب لا مُخترع:** قائمة الأنظمة ومحرّكاتها من `README` الخاص بـ`Swordfish90/Lemuroid`
 * (٢٤ نظامًا بمحرّكات مُسمّاة)، كما سُجّل في `docs/ai/EMULATOR-HUB-PLAN.md` §٣.٢. لا يُضاف نظام
 * ولا امتداد من عندنا.
 *
 * **وقاعدة الصدق:** الامتداد **قرينة لا إثبات** (نصّ الخطة نفسه). فما كان منها يحمل أكثر من نظام
 * يُعلَن ملتبسًا (`AMBIGUOUS_EXTENSIONS`) ولا يُنسب إلى نظام بعينه — والواجهة تقول «ملتبس» بدل أن
 * تخمّن وتُوهم المستخدم بأنها تعرف ما لا تعرفه.
 */
enum class RomSystem {
    ATARI,
    NES,
    SNES,
    GB,
    GBA,
    NDS,
    N64,
    GENESIS,
    SMS,
    GG,
    PSX,
    PSP,
    ARCADE,
    PCE,
    NGP,
    WS,
    N3DS,
}

/** نظام واحد: امتداداته، وهل هو قرص (يحتاج `.cue` غالبًا) فلا يُعرض إلا بعنصر واحد. */
data class RomSystemSpec(
    val system: RomSystem,
    val extensions: Set<String>,
    val discBased: Boolean = false,
)

object RomSystems {
    /**
     * الامتدادات التي تحمل أكثر من نظام: `.bin` (Genesis · PSX · Atari) · `.iso` (PSX · PSP ·
     * GameCube) · `.chd` (PSX · Saturn) · `.zip`/`.7z` (Arcade · حزم ROM) · `.m3u` (قائمة أقراص).
     * وترتيب الفحص في [systemFor] يجعلها تُفحص **قبل** الجدول، فلا يبتلعها أوّل نظام يطابق.
     */
    val AMBIGUOUS_EXTENSIONS: Set<String> = setOf("bin", "iso", "chd", "zip", "7z", "m3u", "cue")

    val SPECS: List<RomSystemSpec> = listOf(
        RomSystemSpec(RomSystem.ATARI, setOf("a26", "a78", "lnx")),
        RomSystemSpec(RomSystem.NES, setOf("nes", "unf", "fds")),
        RomSystemSpec(RomSystem.SNES, setOf("sfc", "smc")),
        RomSystemSpec(RomSystem.GB, setOf("gb", "gbc")),
        RomSystemSpec(RomSystem.GBA, setOf("gba")),
        RomSystemSpec(RomSystem.NDS, setOf("nds")),
        RomSystemSpec(RomSystem.N64, setOf("z64", "n64", "v64")),
        RomSystemSpec(RomSystem.GENESIS, setOf("md", "gen", "smd"), discBased = true),
        RomSystemSpec(RomSystem.SMS, setOf("sms")),
        RomSystemSpec(RomSystem.GG, setOf("gg")),
        RomSystemSpec(RomSystem.PSX, setOf("pbp"), discBased = true),
        RomSystemSpec(RomSystem.PSP, setOf("cso"), discBased = true),
        RomSystemSpec(RomSystem.ARCADE, setOf("fba", "fbneo")),
        RomSystemSpec(RomSystem.PCE, setOf("pce", "sgx")),
        RomSystemSpec(RomSystem.NGP, setOf("ngp", "ngc")),
        RomSystemSpec(RomSystem.WS, setOf("ws", "wsc")),
        RomSystemSpec(RomSystem.N3DS, setOf("3ds", "cia"), discBased = true),
    )

    private val byExtension: Map<String, RomSystem> =
        buildMap { SPECS.forEach { spec -> spec.extensions.forEach { put(it, spec.system) } } }

    /** الامتداد بالحروف الصغيرة، أو `""` لاسم بلا امتداد. */
    fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase()

    /**
     * نظام الملفّ، أو `null` حين لا نعرفه أو حين يكون الامتداد ملتبسًا.
     *
     * `null` ليست فشلًا بل **الحكم الصادق**: اسم بلا امتداد معروف لا يُنسب إلى نظام، والواجهة
     * تقول «مجهول» ولا تخمّن (ADR-07: ما لا يُقاس يُعرض غيابًا لا صفرًا).
     */
    fun systemFor(name: String): RomSystem? = byExtension[extensionOf(name)]

    /** هل الامتداد يحمل أكثر من نظام؟ يُعرض «ملتبس» بنصّه بدل نسبة كاذبة. */
    fun isAmbiguous(name: String): Boolean = extensionOf(name) in AMBIGUOUS_EXTENSIONS

    /**
     * الاسم بلا امتداد — مفتاح تجميع مجموعات الأقراص (`Final Fantasy VII.cue` و`Final Fantasy VII.bin`
     * يصيران عنصرًا واحدًا). المسار الكامل يُجرَّد أولًا لأن الاسم قد يحمل نقطة في مجلدٍ أعلى.
     */
    fun baseName(name: String): String {
        val leaf = name.substringAfterLast('/').substringAfterLast('\\')
        return leaf.substringBeforeLast('.', leaf)
    }
}
