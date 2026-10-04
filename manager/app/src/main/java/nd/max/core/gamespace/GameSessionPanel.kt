/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.gamespace

import nd.max.core.platform.ForegroundAppResolver

/**
 * لوحة الألعاب الجانبية — **القواعد وحدها، بلا أندرويد وبلا Compose**.
 *
 * ### ما هذا الملفّ ولماذا ليس في `ui/`
 *
 * سؤال اللوحة الجانبية ليس سؤالًا بصريًّا: «أيّ لعبة تُعرض لها اللوحة الآن؟»، و«متى تكون
 * مطويّة ومتى تُفتح؟»، و«أين تُوضع على حافة شاشة عرضية فيها شرائح نظام؟» — ثلاثة قرارات
 * تُخطئ في ثلاث حالات حقيقية (لا قراءة بعد · اللعبة ليست في المكتبة · تدوير الجهاز)، وكلّها
 * قابلة للقياس بلا شاشة. فوُضعت هنا لتُختبر وحدها كما تُختبر `GameLibrary` و`compareVisibility`،
 * وليبقى ملفّ الخدمة قيادةً لا منطقًا.
 *
 * ### والحدّ المعلن
 *
 * لا كتابة عتاد هنا ولا قراءته: هذا الملفّ يحسب **أيضًا** هل اللوحة تغطّي اللعبة ([coversGame])
 * لتُقال في الواجهة صراحةً، لا ليُبنى محرّك ثانٍ.
 */

/** ما تعرفه اللوحة عن اللعبة التي فوقها. */
sealed interface PanelSubject {

    /** اللعبة الحالية **وهي في المكتبة** — الحالة الوحيدة التي تُعرض فيها اللوحة. */
    data class Tracked(val packageName: String) : PanelSubject

    /**
     * التطبيق الأمامي صالح لكنّه **ليس** لعبة في المكتبة.
     *
     * وفُصل عن [NotAGame] لأنّ الإجراء مختلف: هنا نقول اسم التطبيق، وهناك نقول «لا قراءة بعد».
     */
    data class Other(val packageName: String) : PanelSubject

    /** لا قراءة مقروءة بعد (الخدمة بدأت للتوّ أو المصدر لم يجب). */
    data object Unknown : PanelSubject
}

/**
 * قرار المطابقة: اسم حزمة صالح ⟶ لعبة في المكتبة أو غيره، وغائب/غير صالح ⟶ [PanelSubject.Unknown].
 *
 * **والغائب ليس «غير لعبة»:** `FpsMonitorUtil.getForegroundPackage()` تُعيد `""` حين لا يجيب
 * المصدر، وقراءة `""` كـ«التطبيق الأمامي ليس لعبة» تُطوي اللوحة عن لعبة قائمة — وهو نفس عطب
 * `status_unknown` في طبقة البيانات بلون آخر. فما لم يُقَس لا يُحكم عليه.
 */
fun panelSubject(foreground: String?, library: Set<String>): PanelSubject = when {
    !ForegroundAppResolver.isPackageName(foreground) -> PanelSubject.Unknown
    foreground!! in library -> PanelSubject.Tracked(foreground)
    else -> PanelSubject.Other(foreground)
}

/** حالات اللوحة الثلاث. ولا رابعة: كل ما ليس هذين مخفيّ. */
enum class GamePanelMode {
    /** لا شيء على الشاشة — اللعبة كاملة الرؤية. */
    Hidden,

    /**
     * مقبض رقيق ملتصق بالحافة: **لا يغطّي اللعبة** (سِتّةَ عشرَ dp على الحافة) ويفتح اللوحة بلمسة.
     *
     * **ولماذا مقبض لا لوحة مطويّة:** المطلوب «قائمة جانبية مثل REDMAGIC» لا تراكب يغطّي اللعب.
     * والمقبض هو أصغر تمثيل ممكن يبقى **قابلًا للفتح** بلا لوحة نظام ولا اختصار مفاتيح.
     */
    Handle,

    /** اللوحة مفتوحة — **وهي الحالة الوحيدة التي تشغل من مساحة اللعبة**. */
    Open
}

/** حافة الالتصاق. والاتجاه يُقرأ من التخطيط لا من اللغة وحدها. */
enum class PanelSide { Start, End }

/**
 * حالة اللوحة كما تصل الواجهة.
 *
 * @param enabled هل اللوحة مُفعَّلة لهذه اللعبة؟ (تفضيل لكل لعبة، لا مفتاح عامّ واحد)
 * @param openByUser هل فتحها المستخدم في هذه الجلسة؟ (يُصفَّر عند مغادرة اللعبة)
 * @param side الحافة الحالية — تبقى عبر الطيّ فلا تقفز اللوحة من جانب إلى آخر.
 */
data class GamePanelState(
    val mode: GamePanelMode = GamePanelMode.Hidden,
    val subject: PanelSubject = PanelSubject.Unknown,
    val enabled: Boolean = false,
    val openByUser: Boolean = false,
    val side: PanelSide = PanelSide.End
) {
    /** هل يُرسم شيء أصلًا؟ */
    val visible: Boolean get() = mode != GamePanelMode.Hidden

    /**
     * هل تشغل اللوحة من مساحة اللعبة الآن؟ — **يُقال في الواجهة صراحةً** («لا تغطّي اللعبة»
     * مقابل «اللوحة مفتوحة») بدل أن يُدَّعى أن التراكب لا يغطّي أبدًا.
     */
    val coversGame: Boolean get() = mode == GamePanelMode.Open

    /** تُفتح اللوحة فقط للعبة مُفعَّلة ومُتابَعة؛ وما عدا ذلك مقبض أو لا شيء. */
    val canOpen: Boolean get() = enabled && subject is PanelSubject.Tracked
}

/**
 * الدالّة الوحيدة التي تُنتج الحالة من الوقائع — كل تغيير يمرّ من هنا.
 *
 * **والقاعدة التي تحكمها:** مفعَّل + لعبة في المكتبة ⟹ **مقبض على الأقلّ**؛ فمن فعّل اللوحة
 * لا يُترك بلا أثر يفتحه (وهو العطب الذي يجعل «الميزة مفعَّلة ولا أراها»). وما عدا ذلك ⟹ مخفيّ.
 *
 * **والفتح لا يُحفظ:** [openByUser] حالة جلسة، فمغادرة اللعبة تُعيدها إلى المقبض — وإلّا فُتحت
 * اللوحة تلقائيًّا فوق أوّل لعبة تُشغَّل بعد ساعة.
 */
fun reconcileGamePanel(
    state: GamePanelState,
    subject: PanelSubject,
    enabled: Boolean,
    openByUser: Boolean
): GamePanelState {
    val tracked = subject is PanelSubject.Tracked
    val mode = when {
        !enabled || !tracked -> GamePanelMode.Hidden
        openByUser -> GamePanelMode.Open
        else -> GamePanelMode.Handle
    }
    return state.copy(mode = mode, subject = subject, enabled = enabled, openByUser = openByUser)
}

/** موضع اللوحة بالبكسل بعد تطبيق الشرائح وحدود الشاشة. */
data class PanelPlacement(val x: Int, val y: Int)

/**
 * موضع اللوحة على الحافة: **ملتصقة تمامًا** بها، ومحترمةً شرائح النظام، وباقيةً داخل الشاشة.
 *
 * **والعطب الذي تمنعه:** على شاشة عرضية فيها شريحة حالة وشريحة تنقّل، الالتصاق عند `y = 0`
 * يضع رأس اللوحة **تحت شريحة الحالة** فيصير أوّل زرّ غير قابل للمس. وهذا يُقاس بلا جهاز.
 *
 * والحدّ الأدنى محفوظ: لو ضاقت الشاشة عن اللوحة تُقصّ إلى ما تبقّى بدل أن تُخرج خارجها.
 *
 * **والشرائح تُطبَّق بعد القياس لا قبله:** لو حُسب الارتفاع المتاح أوّلًا ثم طُلب من المحتوى أن
 * يسكن فيه، لبقي ارتفاع اللوحة محسوبًا قبل أن يُعرف. فتُطبَّق `insetTop` على `y` الناتج، وتُحصر
 * النتيجة بين `insetTop` و`screenHeight - insetBottom` — وهذا هو مسار الشاشة الضيّقة الذي كانت
 * `coerceAtLeast(insetTop)` وحدها تُفلته (شاشة ٢٠٠px وشريحة سفلية ٤٨ ⟹ `y = 52`، أي أسفل
 * الشاشة).
 */
fun panelPlacement(
    screenWidth: Int,
    screenHeight: Int,
    panelWidth: Int,
    panelHeight: Int,
    insetTop: Int,
    insetBottom: Int,
    side: PanelSide
): PanelPlacement {
    val width = panelWidth.coerceIn(0, screenWidth.coerceAtLeast(0))
    val x = if (side == PanelSide.End) (screenWidth - width).coerceAtLeast(0) else 0
    val maxY = (screenHeight - insetBottom - panelHeight).coerceAtLeast(insetTop)
    val y = (insetTop + (screenHeight - panelHeight) / 2).coerceIn(insetTop, maxY)
    return PanelPlacement(x = x, y = y)
}

/**
 * هل يجب أن **تعمل الخدمة** الآن؟ — سؤال مختلف عن «هل يُرسم شيء؟».
 *
 * اللوحة تُفتح تلقائيًّا عند دخول لعبة مُفعَّلة من أيّ طريق — من داخل التطبيق أو من مشغّل
 * خارجي — وهذا هو سبب وجود الخدمة. ومتى لا تكون هناك لعبة مُفعَّلة أمامية، لا سبب لإبقاء
 * خدمة أمامية تعمل: تُوقف نفسها، فتُوفَّر البطارية ويُحرَّر القارئ المشترك لتراكب الإطارات.
 *
 * **والمقارنة على اسم صالح فقط:** قراءة غير مقروءة ليست دليلًا على أن اللعبة غادرت، فإيقاف
 * الخدمة عندها كان سيُطفئ اللوحة كلما تعذّرت قراءة واحدة — وهو عطب يظهر لحظة الإشعارات.
 */
fun panelServiceNeeded(enabledPackages: Set<String>, foreground: String?): Boolean =
    ForegroundAppResolver.isPackageName(foreground) && foreground in enabledPackages

/** خيارات معدّل التحديث المعروضة في اللوحة، بالهرتز. */
val PANEL_REFRESH_CHOICES: List<Int> = listOf(60, 90, 120)

/**
 * الحلقة التالية في معدّل التحديث: 60 ⟶ 90 ⟶ 120 ⟶ **الافتراضي** ⟶ 60…
 *
 * **و`null` تعني «بلا فرض»:** الوضع الأخير في الحلقة ليس رقمًا بل **إزالة الفرض** ليختار
 * النظام من جديد. وهذا الفرق هو الذي يمنع تسمية الرجوع إلى الافتراضيّ «٦٠Hz» — وهو ما كان
 * يُثبّت الجهاز على ٦٠ ظنًّا أنّه أرجعه.
 *
 * وما لا يُعرف يُبدأ من أوّله: قيمة حالية غائبة أو غير مدرجة ⟹ أوّل خيار، لا رقم مخترع.
 */
fun nextRefreshRate(current: Int?, choices: List<Int> = PANEL_REFRESH_CHOICES): Int? {
    if (choices.isEmpty()) return null
    val index = choices.indexOf(current)
    if (index < 0) return choices.first()
    return if (index == choices.lastIndex) null else choices[index + 1]
}

/** ما تعرضه اللوحة من أدوات. ولا يُعرض زرّ لا يُعرف ما يفعله. */
enum class GamePanelTile {
    /** معدّل التحديث — يُرسَل إلى مالكه القائم (`RefreshRateReceiver`)، ولا يُكتب من هنا. */
    RefreshRate,

    /** عدم الإزعاج — يُقرأ حاله ويُحرَّر في `App Settings` لأنّ أثره عالميّ ومالكه هناك. */
    DoNotDisturb,

    /** تسجيل الجلسة — الحائز القائم `HudRecorder`. */
    RecordSession,

    /** التقاط الشاشة — **غير مُنفَّذ بعد**، ويُعرض بهذه الحالة لا كزرّ يعمل. */
    Capture
}

/**
 * حالة أداة: تُعرض، أو تُعرض مع سبب، أو تُعرض بلا تنفيذ.
 *
 * **وهذا هو نصّ ADR-08:** المقابض المطفأة تُعلن سببها بدل أن تُخفى أو تدّعي عملًا. وأداة
 * التقاط الشاشة تُعلن «غير متاحة بعد» لأنّ التزام الصمت عنها يعني زرًّا يبدو معطوبًا.
 */
enum class GamePanelTileState { Ready, ControlledElsewhere, NotAvailableYet }

fun gamePanelTileState(tile: GamePanelTile): GamePanelTileState = when (tile) {
    GamePanelTile.RefreshRate -> GamePanelTileState.Ready
    GamePanelTile.RecordSession -> GamePanelTileState.Ready
    GamePanelTile.DoNotDisturb -> GamePanelTileState.ControlledElsewhere
    GamePanelTile.Capture -> GamePanelTileState.NotAvailableYet
}

/** قرار عمر الخدمة بعد كل استطلاع. */
enum class PanelLifetime { Continue, Stop }

/**
 * متى تُوقف الخدمة نفسها — **وهذا ليس تفصيلًا**: لو أُوقفت لحظة عدم رؤية اللعبة لتوقّفت فور
 * تفعيل المستخدم للخيار من داخل التطبيق (لأن المقدّمة لحظتها تطبيقنا)، ولو لم تُوقف أبدًا لبقيت
 * خدمة أمامية تعمل بعد أن ينسى المستخدم الأمر. فالقاعدة تحمل الحالتين:
 *
 * 1. لعبة مُفعَّلة أمامية ⟹ **استمرّ** (ويُصفّر عدّاد الانتظار).
 * 2. رأينا اللعبة ثم غادرت ⟹ **توقّف** — انتهى سبب الخدمة.
 * 3. لم نرَها بعد، ولم ينفد الانتظار ⟹ **استمرّ** (اللعبة في الطريق).
 * 4. لم نرَها أبدًا ونفد الانتظار ⟹ **توقّف** — نيّة قديمة بلا لعبة.
 *
 * والانتظار بالعدد لا بالزمن: الاستطلاع ثابت الفاصل، فالعدّاد زمنًا مقنّعًا بلا ساعة إضافية.
 */
fun panelServiceLifetime(
    seenGame: Boolean,
    needed: Boolean,
    idlePolls: Int,
    idleLimit: Int
): PanelLifetime = when {
    needed -> PanelLifetime.Continue
    seenGame -> PanelLifetime.Stop
    idlePolls < idleLimit -> PanelLifetime.Continue
    else -> PanelLifetime.Stop
}
