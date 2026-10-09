/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * MaxManager Design Language — token layer.
 *
 * Why this file exists:
 * screens used to hard-code dp values, alphas and status colours inline, so the
 * same idea (a section gap, a warning, a disabled control) looked different on
 * every screen. Reworked surfaces must consume these tokens instead of
 * literals, which is what makes the app read as one product instead of a pile
 * of screens.
 *
 * Tokens and pure colour resolution only — no composable UI lives here.
 */
package nd.max.ui.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * 4dp-based spacing scale.
 *
 * `gutter`, `section`, `row` and `pageBottom` are the only values a screen body
 * should need; the raw steps exist for component internals.
 */
object MaxSpace {
    val hairline: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 28.dp

    /** Horizontal page gutter shared by every scrollable screen body. */
    val gutter: Dp = 20.dp

    /** Vertical gap between two sections of a screen. */
    val section: Dp = 28.dp

    /** Gap between sibling rows inside one section. */
    val row: Dp = 8.dp

    /** Inner padding of a grouped container. */
    val groupPadding: Dp = 4.dp

    /** Inner padding of a single row. */
    val rowPaddingHorizontal: Dp = 14.dp
    val rowPaddingVertical: Dp = 12.dp

    /** Breathing room so the bottom navigation never covers the last row. */
    val pageBottom: Dp = 40.dp

    /**
     * Safe minimum footprint of the floating pill bottom bar (pill height +
     * its own top/bottom margins), *excluding* the system navigation-bar
     * inset, which is layered on separately.
     *
     * Primary screens reserve at least this much space before the bar's real
     * pixel height is known. It must stay generous enough to cover a wrapped
     * two-line label (long Arabic strings) without the last row of content
     * ever sitting behind the bar — see [pageBottom], which adds further
     * clearance on top of this.
     */
    val bottomBarReserve: Dp = 112.dp
}

/**
 * Shape language: rows are calm (14dp), groups frame them (22dp), sheets and
 * dialogs are the softest (28dp). Pills are reserved for state, never for
 * primary actions — a pill in MaxManager means "this is a status".
 */
object MaxRadius {
    val control: Dp = 12.dp
    val row: Dp = 14.dp
    val group: Dp = 22.dp
    val sheet: Dp = 28.dp
    val pill: Dp = 100.dp

    /**
     * بلاطة أصغر من بطاقة (نقشة، بلاطة عدّاد، نقشة إحصاء).
     *
     * أُضيفت لأن الشجرة كانت تحمل `18.dp` في **١٦ موضعًا** بلا اسم: قيمة تُقرأ رقمًا فلا يعرف
     * قارئها إن كانت قصدًا أو بقايا، ولا يمكن تغييرها من مكان واحد — وهو نصّ `§١١` حرفيًّا
     * («If I later change the card radius or spacing, I should be able to change it globally»).
     * والقيمة **محفوظة كما هي**: صفر تغيير بصريّ، والاسم هو المُكتسَب.
     */
    val tile: Dp = 18.dp

    /** حاوية داخليّة (`16.dp` في ١٥ موضعًا) — نفس السبب ونفس الحفظ. */
    val inset: Dp = 16.dp

    /**
     * أصغر حاوية: صندوق أيقونة، وسم حالة، قصّة أيقونة تطبيق، عَدّاد قراءة.
     *
     * أُضيف لأن `10.dp` كانت في **٩ مواضع** بلا اسم، وقد قِيست تلك المواضع فتبيّن أنها **صنف
     * واحد وحقًّا**: كلها حاوية صغيرة تسكن داخل صفّ أو بطاقة أو معاينة — لا بطاقة ولا رأس. والقيمة
     * **محفوظة كما هي** (صفر تغيير بصريّ)، والمُكتسَب هو الاسم: تغييرها يقع الآن من مكان واحد.
     *
     * وما لا يزال حرفيًّا بعدها **٢ · ٣ · ٤ · ٦ · ٨dp** — أشكال مجهرية (شرائط ومقابض ونقاط)،
     * وهي مُعلَنة لا منسيّة.
     */
    val chip: Dp = 10.dp
}

/*
 * The one card contract.
 *
 * Why this exists (measured, not asserted): before this object the tree carried **20 distinct
 * corner radii** and **14 padding values** as literals, **38 separate `*Card` composables**, and a
 * second metrics object (`MaxUiMetrics` in `ui/component`) that restated the same four numbers with
 * different values (cardRadius 28 against MaxRadius.group 22; pagePadding 20 against MaxSpace.gutter
 * 20 — same idea, two spellings). A grid built from that reads as a pile of screens, not a product.
 *
 * Every rule the audit asked for lives here as a number, so a card cannot drift by being written in
 * a different file: radius, border, padding, icon container, the icon→title→description gaps, the
 * reserved title lines that keep two cards in a row aligned, the height floor, and the grid gutters.
 *
 * [minColumnWidth] is the responsive lever and the reason `Powe…`/`Gami…` truncation stops: a grid
 * asks for room and drops to one column instead of squeezing words into a column too narrow to hold
 * them. No call site gets to lower it to fit more cards in.
 */
object MaxSectionSpec {
    /**
     * عقد عنوان القسم — واحد لكل شاشة في التطبيق.
     *
     * **وسبب وجوده مقيس:** كانت الشاشات الثلاث التي فيها «قسم» ترسم ثلاثة رؤوس مختلفة:
     * `MaxSectionHeader` في طبقة التصميم (شرطة ٢٨×٤ + `titleLarge` عريض + تسمية
     * `bodyMedium`)، و`NeuralSectionHeader` في اللوحة (`3dp` دائرياً + `15sp` + تسمية `11sp`)،
     * و`SettingsSectionTitle` في إعدادات التطبيق (صندوق ٢٨dp + `titleSmall`). فالقارئ لا
     * يعرف أيّها عنوان صفحة وأيّها عنوان قسم — وهو نصّ الطلب حرفياً («The user should
     * immediately understand what is a page title, section title, card title, and supporting
     * description»).
     *
     * **والترتيب الملزم الآن**، من الأكبر إلى الأصغر، ولا طبقتان بنفس الحجم:
     * عنوان صفحة (شارات التطبيق: `headlineSmall`+ ) ← **عنوان قسم** (`titleMedium` عريض) ←
     * عنوان بطاقة (`titleSmall`، في `MaxCard`) ← وصف مساند (`bodySmall`).
     */
    val accentWidth: Dp = MaxSpace.xs
    val accentHeight: Dp = MaxSpace.lg + 2.dp
    val accentRadius: Dp = 2.dp

    /** فراغ بين الشرطة **الجانبية** (في صفّ: الشرطة ثم النصّ) وبين نصّ العنوان. */
    val accentGap: Dp = MaxSpace.md

    /**
     * **الشريط الذي فوق الاسم: أفقيّ لا عموديّ — بأمر المالك** («اجعل الأشرطة التي فوق
     * الأسماء … أفقية وليس عمودية»).
     *
     * **والعطب مقيس في العقد لا في الذوق:** الأبعاد كانت `accentWidth × accentHeight` =
     * `٤×١٨dp` وتُستعمل في **موضعين مختلفي الاتجاه**: `MaxSectionHeader` يضعها **فوق** العنوان
     * (في `Column`)، و`NeuralSectionHeader` يضعها **بجانبه** (في `Row`). فالقيمة نفسها تُقرأ
     * «شرطة تمييز» في الصفّ و«عمودًا» فوق الاسم — وثمنها فوق الاسم **٣٠dp** من الفراغ الرأسي
     * (`١٨` ارتفاعًا + `١٢` فراغًا) على **كل** قسم في **كل** شاشة، وهو أوّل ما يراه القارئ فوق
     * اسمَي «مسارات الأداء» و«الحرارة والطاقة» في شاشة التحكّم.
     *
     * **ولماذا لا تُوحَّد القيمتان كما وُحّد عنوان القسم:** الاتجاهان يطلبان شريطين مختلفين لا
     * قيمة واحدة: شريطٌ **بجانب** الاسم يُقرأ خطًّا رأسيًّا يُسند العنوان، وشريطٌ **فوقه** يُقرأ
     * قاعدةً (rule) تُقدّمه — والأرقام نفسها لا تصلح للاثنين. فصار لكل اتجاه عقدُه المُسمّى،
     * والمشترك بينهما هو الرمز لا القياس. وبهذا يُصلح العطب في أصله لا في موضع واحد.
     */
    val accentRuleWidth: Dp = MaxSpace.xxl
    val accentRuleHeight: Dp = MaxSpace.xs

    /** فراغ بين الشريط الأفقي ونصّ العنوان تحته (`٨` لا `١٢`: الشريط نفسه صار قصيرًا). */
    val accentRuleGap: Dp = MaxSpace.sm

    /** فراغ داخل عمود العنوان: بين العنوان والوصف المساند. */
    val titleGap: Dp = MaxSpace.xs

    /**
     * فراغ قبل عنوان القسم (فصل الأقسام عن بعضها) وبعده (فصله عن بطاقاته).
     *
     * **وكان `24.dp` حرفيًّا — فراغان للأقسام في التطبيق الواحد:** الهيكل
     * (`MaxScreenScaffold`) و`ControlScreen` يستعملان `MaxSpace.section` = ٢٨،
     * وهذا العنوان ٢٤. فالصفحتان تُفصلان أقسامهما بـ٢٨، وشاشات الرؤوس
     * (`AppSettings` · `CustomTheme` · `ScreenChrome`) بـ٢٤ — والفرق يُقرأ
     * إيقاعًا مختلفًا لا فرق ٤dp. وُحّد على `MaxSpace.section` لأنه:
     * (١) رمز مُسمّى موجود («الفراغ الرأسي بين قسمين»)، (٢) القيمة التي
     * يفرضها الهيكل على كل صفحة تمرّ منه، (٣) ولا يبقى في طبقة الرموز
     * رقم حرفي بلا اسم. والتغيير البصري (+٤dp) **لم يُقَس على جهاز**.
     *
     * **ونُقص إلى `MaxSpace.xl` بأمر المالك** («قلّل الحشو في شاشة التحكّم لأنّه كبير زيادة عن
     * اللازم»). **والعطب كان عدًّا مزدوجًا مقيسًا لا ذوقًا:** هذا الفراغ يُطبَّق **داخل** قائمة
     * تُضيف أصلًا `MaxSpace.row` (`٨dp`) بين عناصرها (`MaxListScreen` ←
     * `Arrangement.spacedBy(MaxSpace.row)`) ومعها حشوة القسم من الجانبين، فالمسافة الفعلية بين
     * آخر صفّ في قسم وعنوان القسم التالي كانت `٨ + ٢٨ = ٣٦dp` لا ٢٨. أي أن الرمز المسمّى
     * «الفراغ الرأسي بين قسمين» لم يكن ما بين القسمين، بل ما بينهما **زائد** فاصل القائمة.
     * وبـ`MaxSpace.xl`: `٨ + ٢٠ = ٢٨` بالضبط = `MaxSpace.section` نصًّا — فالرمز صار يصدُق على ما
     * يصف، **ولم يُنقَص شيء من إيقاع الأقسام المعلن**. وهذا تغيير على مستوى التطبيق (العقد واحد
     * لكلّ شاشة)، لا استثناء لشاشة واحدة — والاستثناء كان سيُنشئ الفرق الذي أُغلق سابقًا.
     */
    val spaceBefore: Dp = MaxSpace.xl
    val spaceAfter: Dp = MaxSpace.md

    /** الصندوق التمييزي البديل للشرطة حين يحمل القسم أيقونة بدل لون. */
    val accentBox: Dp = MaxSize.rowIconContainer
    val accentBoxRadius: Dp = MaxRadius.control
}

object MaxCardSpec {
    /** One card radius app-wide. [MaxRadius.group] is that value; this names the intent. */
    val radius: Dp = MaxRadius.group

    val borderWidth: Dp = MaxSize.hairlineBorder

    /** Internal padding of a card. */
    val padding: Dp = MaxSpace.lg

    /** The tinted square behind a card's glyph. */
    val iconContainer: Dp = MaxSize.iconContainer

    /** The glyph itself; smaller than its container so the tint reads as a frame. */
    val iconGlyph: Dp = MaxSize.iconGlyph

    /** Vertical gap between icon, title and description. */
    val gap: Dp = MaxSpace.sm

    val gapTight: Dp = MaxSpace.xs

    /** Height floor so a one-word card does not collapse next to a wordy neighbour. */
    val minHeight: Dp = 92.dp

    /** Gap between grid cells, both axes. */
    val gridSpacing: Dp = MaxSpace.md

    /**
     * A title always reserves this many lines, so the description starts at the same height in every
     * card of a row even when one title wraps and its neighbour does not.
     */
    val titleLines: Int = 2

    /** Description budget. Wrapping first; ellipsis only when this many lines is genuinely exceeded. */
    val descriptionLines: Int = 2

    /**
     * Narrowest a card may become before the grid falls back to fewer columns.
     * Below roughly this width an Arabic label or a long English word has nowhere to wrap.
     */
    val minColumnWidth: Dp = 148.dp
}

/**
 * ما يتبدّل من البطاقة مع المقاس — **أربعة أرقام لا تُنسخ، بل يُشار إليها**.
 *
 * وُجدت لأن المقاس الواحد لم يكفِ: البطاقة القياسيّة صُمّمت لمحتوى يحمل عنوانًا وسطر وصف،
 * وشبكة «منصة التحكم» في الرئيسية تحمل كلمتين قصيرتين — فتبدو هناك **كبيرة أكثر من اللازم**
 * بأمر المالك («اجعل بطاقة منصة التحكم بحجم متوسّط ليست كبيرة وليست صغيرة»). والحلّ ليس رقمًا
 * جديدًا في الرسم (‏`padding = 12.dp` في `HomeCommandDeck`) لأن ذلك يعيد **الانزياح الذي أُغلق**:
 * أربعة أرقام في موضعين تفترق يومًا. فهي هنا بأسماء، وتُختار بمقاس واحد.
 */
@Immutable
data class MaxCardMetrics(
    /** الحشو الداخلي. */
    val padding: Dp,
    /** مربّع الأيقونة الملوّن. */
    val iconContainer: Dp,
    /** الأيقونة نفسها — أصغر من حاويتها فيبقى الإطار مقروءًا. */
    val iconGlyph: Dp,
    /** أرضية الارتفاع كي لا تنكمش بطاقة قصيرة بجانب جارتها. */
    val minHeight: Dp,
)

/**
 * مقاسا البطاقة: [Regular] هو القائم في كل الشاشات، و[Medium] أضيق منه لمحتوى قصير.
 *
 * **[Medium] ليس «صغيرًا» معرّفًا ثانيًا:** هو القائم نفسه بأرقامه الأصغر في **الاتجاهين**
 * (حشو وأرضية ارتفاع وأيقونة)، ولا يُنزل الأيقونة تحت حاويتها ولا يلمس نصف القطر — فنصف قطر
 * واحد للتطبيق يبقى قاعدة، والفرق يُقرأ **مقاسًا** لا مكوّنًا آخر. والقيم من طبقة الرموز لا
 * حرفيّة: ‏`MaxSpace.md` (١٢) و`MaxSize.rowIconContainer` (٣٤) و`MaxSize.iconGlyphSmall` (١٦).
 *
 * و[HomeCommandDeck] هو المستعمل الوحيد له حتى الآن، وما لم يُطلب لا يُعمَّم (ADR-18).
 */
enum class MaxCardSize(val metrics: MaxCardMetrics) {
    Regular(
        MaxCardMetrics(
            padding = MaxCardSpec.padding,
            iconContainer = MaxCardSpec.iconContainer,
            iconGlyph = MaxCardSpec.iconGlyph,
            minHeight = MaxCardSpec.minHeight,
        )
    ),
    Medium(
        MaxCardMetrics(
            padding = MaxSpace.md,
            iconContainer = MaxSize.rowIconContainer,
            iconGlyph = MaxSize.iconGlyphSmall,
            // ٨٠ = ٩٢ (الأرضية القائمة) − ٨ (الحشو الموفَّر رأسيًّا) − ٦ (الأيقونة الأقصر)
            // تقريبًا: الأرضية تنزل بما نزل محتواها، فلا تُضاف أرضية تحفظ ارتفاعًا لم يبقَ له.
            minHeight = 80.dp,
        )
    ),
}

object MaxSize {
    /** Accessibility floor for anything clickable. Never go below this. */
    val minTouchTarget: Dp = 48.dp

    val iconGlyph: Dp = 20.dp
    val iconGlyphSmall: Dp = 16.dp

    /** Icon container inside a list row. */
    val rowIconContainer: Dp = 34.dp

    /** Icon container in headers and notices. */
    val iconContainer: Dp = 40.dp

    val hairlineBorder: Dp = 1.dp

    /**
     * حدّ أثقل من [hairlineBorder] بقليل (١٫٥dp) للعنصر الذي يحمل لونه **وحده** بلا حشو يميّزه.
     *
     * أُضيف مع دمج لوحة «المكعّب» (`CUBE-OVERLAY-MERGE-01`): الحبّة السداسية (وضع النظام /
     * الإشعارات / الحرارة) كانت تُرسم بـ`1.5.dp` حرفيًّا في `CubeParts.kt` — وهو **رقم المصمّم** —
     * فشُرب إلى الطبقة بقيمته بدل تخفيفه إلى الشعري، لأن `design_tokens --assert` كان يسقط على
     * تجاوز سقف `border` (‏٢٣ > ٢٢). القيمة **محفوظة كما هي**: صفر تغيير بصريّ.
     */
    val emphasisBorder: Dp = 1.5.dp

    /**
     * Border width of the one element on a split page that owns the next action.
     *
     * Added for the file manager's two panes: with two identical columns the user has
     * no way to tell where an action will land, and a colour alone is not an answer for
     * anyone who cannot see it — so the active pane is also wider, not only tinted.
     */
    val activeRing: Dp = 2.dp

    /** Keeps long explanatory copy readable on tablets/landscape. */
    val readingMaxWidth: Dp = 560.dp

    /** Height of the inline history strip used by telemetry rows. */
    val sparklineHeight: Dp = 28.dp

    /**
     * Largest height a scrollable list may take inside a dialog.
     *
     * Added for the `AppOps` operation picker: a device exposes a couple of hundred
     * operation names, and a dialog that grows to fit them covers the screen it is
     * asking about. The list scrolls inside this cap instead, so the title and the
     * confirm button stay visible while choosing.
     */
    val dialogListMax: Dp = 360.dp

    /**
     * Height of a fractional usage bar (storage share, zone heat, cooling state).
     *
     * A token rather than a literal at each call site: the storage screen and the
     * thermal screen both draw these, and two bar heights for one idea is exactly the
     * drift this token layer exists to stop.
     */
    val barHeight: Dp = 8.dp
}

/**
 * Opacity vocabulary. Borders stay hairline-quiet; tone containers stay low so
 * numbers remain the brightest thing on screen.
 */
object MaxAlpha {
    const val border = 0.16f
    const val borderStrong = 0.28f
    /** Lowest tone step: an accent used only to wash a surface, never to mark an element. */
    const val toneWash = 0.045f

    const val toneContainer = 0.10f
    const val toneContainerStrong = 0.18f
    const val disabledContent = 0.38f
    const val supportingText = 0.80f

    /**
     * Inner highlight drawn along a panel's leading edge.
     *
     * Absorbed here from `MaxUiAlpha.edgeLight` (ADR-05): the studio components had invented a
     * local opacity vocabulary beside this one, and two vocabularies for "a faint edge" is the
     * drift this layer exists to stop. The value is unchanged — only its home is.
     */
    const val edgeLight = 0.22f

    /** Ambient glow behind an accented panel. Same absorption, same value. */
    const val haloGlow = 0.07f
}

/**
 * Motion budget. Motion exists to explain a transition or confirm a state
 * change; anything longer than [deliberate] is decoration and is not allowed.
 */
object MaxDuration {
    const val instant = 90
    const val quick = 160
    const val standard = 240
    const val deliberate = 360
}

/**
 * Semantic tone.
 *
 * Tone is never the only carrier of meaning: every component that renders a
 * tone also renders an icon and a text label, so state survives colour
 * blindness, greyscale screenshots and high-contrast modes.
 */
enum class MaxTone {
    /** Ordinary information. */
    Neutral,

    /** The screen's own accent — "this is the thing you came here for". */
    Accent,

    /** Confirmed good: applied, verified, within limits. */
    Positive,

    /** Needs attention but still working: throttling, near a limit. */
    Caution,

    /** Failed, blocked or unsafe. */
    Critical,

    /** Off, unsupported or not owned by MaxManager. */
    Inactive
}

/*
 * Caution / Critical / Positive are intentionally NOT taken from the dynamic
 * colour scheme. Material You can turn `tertiary` into a pastel that reads as
 * decoration, and a thermal alert that looks decorative is a correctness bug in
 * a performance tool. These hues are fixed and contrast-checked against both
 * light and dark MaxManager surfaces; only Accent follows the user's theme.
 */
private val PositiveOnLight = Color(0xFF0B6B4F)
private val PositiveOnDark = Color(0xFF5FD9AC)
private val CautionOnLight = Color(0xFF8A5200)
private val CautionOnDark = Color(0xFFFFB86B)
private val CriticalOnLight = Color(0xFF9A1B1B)
private val CriticalOnDark = Color(0xFFFF9A90)

/** True when the active scheme is dark, derived from the surface itself. */
@Composable
fun maxIsDarkSurface(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/** Foreground colour for a tone (text, icon, stroke). */
@Composable
fun MaxTone.content(): Color {
    val scheme = MaterialTheme.colorScheme
    val dark = maxIsDarkSurface()
    return when (this) {
        MaxTone.Neutral -> scheme.onSurfaceVariant
        MaxTone.Accent -> scheme.primary
        MaxTone.Positive -> if (dark) PositiveOnDark else PositiveOnLight
        MaxTone.Caution -> if (dark) CautionOnDark else CautionOnLight
        MaxTone.Critical -> if (dark) CriticalOnDark else CriticalOnLight
        MaxTone.Inactive -> scheme.outline
    }
}

/** Low-emphasis container behind a tone's content. */
@Composable
fun MaxTone.container(strong: Boolean = false): Color =
    content().copy(alpha = if (strong) MaxAlpha.toneContainerStrong else MaxAlpha.toneContainer)

/** Hairline border for a tone. */
@Composable
fun MaxTone.border(strong: Boolean = false): Color =
    content().copy(alpha = if (strong) MaxAlpha.borderStrong else MaxAlpha.border)

/**
 * How trustworthy a displayed number is.
 *
 * MaxManager is a measurement tool, so the UI must distinguish "this is live",
 * "this is the last good sample" and "this sensor does not exist on your
 * device". Formatting a stale or missing value as if it were live is treated as
 * a bug, not a cosmetic issue.
 */
enum class MaxDataTrust {
    /** Sampled within the expected interval. */
    Live,

    /** Real reading, but older than the expected interval. */
    Stale,

    /** Read once at startup / on demand; not a stream. */
    Snapshot,

    /**
     * **السؤال لم يُطرح بعد** — الدورة الأولى لم تكتمل (تكملة ٢٠٥).
     *
     * **ولماذا حالة سادسة وليست `Unreadable`:** «غير مقروء» **حكم على مصدر** بأنه لم يُعط
     * قراءة، فاستعماله قبل أول قراءة ادّعاءٌ لعطب لم يُقس — وهو ما يمنعه نصّ روح ADR-07 في
     * الاتجاه المقابل (كما لا يُدَّعى رقم لم يُقرأ، لا يُدَّعى عطب لم يُقس). وقد رآه المالك
     * فعلًا: «يكتب لا قراءة وبعد دقيقة تعمل».
     */
    Loading,

    /** The source exists but returned nothing readable right now. */
    Unreadable,

    /** The source does not exist on this device or kernel. */
    Unsupported
}

@Immutable
data class MaxTrustVisual(val tone: MaxTone, val showsValue: Boolean)

fun MaxDataTrust.visual(): MaxTrustVisual = when (this) {
    MaxDataTrust.Live -> MaxTrustVisual(MaxTone.Accent, showsValue = true)
    MaxDataTrust.Stale -> MaxTrustVisual(MaxTone.Caution, showsValue = true)
    MaxDataTrust.Snapshot -> MaxTrustVisual(MaxTone.Neutral, showsValue = true)
    // والانتظار ليس إنذارًا: لون هادئ بلا قيمة — لا «تحذير» على قراءةٍ في الطريق.
    MaxDataTrust.Loading -> MaxTrustVisual(MaxTone.Neutral, showsValue = false)
    MaxDataTrust.Unreadable -> MaxTrustVisual(MaxTone.Caution, showsValue = false)
    MaxDataTrust.Unsupported -> MaxTrustVisual(MaxTone.Inactive, showsValue = false)
}

/**
 * Placeholder rendered instead of a number when there is nothing real to show.
 * Deliberately not "0", not "--%" and not an empty string: a zero would be read
 * as a measurement.
 */
const val MAX_VALUE_UNAVAILABLE = "—"

/** Tabular figure size used by metric readouts so digits never reflow. */
object MaxMetricType {
    val valueLarge: TextUnit = 34.sp
    val valueMedium: TextUnit = 22.sp
    val valueSmall: TextUnit = 16.sp
}
