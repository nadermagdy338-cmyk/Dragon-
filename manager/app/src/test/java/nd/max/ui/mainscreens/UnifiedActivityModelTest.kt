package nd.max.ui.mainscreens

import nd.max.ui.mainscreens.UnifiedActivityModel.CardMotion
import nd.max.ui.mainscreens.UnifiedActivityModel.CardOptions
import nd.max.ui.mainscreens.UnifiedActivityModel.CardStyle
import nd.max.ui.mainscreens.UnifiedActivityModel.CardVerbosity
import nd.max.ui.mainscreens.UnifiedActivityModel.SessionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * سياسة البطاقة الواحدة تُقاس هنا — في النموذج الذي **يُنفَّذ**، لا في نسخة موازية داخل Compose.
 *
 * وكل حالة أدناه تقابل ملاحظة مقيسة على الجهاز أو في المراجعة: سطر مرّتين في بطاقة واحدة، وسطر
 * اختيار (بلا «قبل») كان يُطرح فيختفي «Kill Background Apps: on» من الشاشة، وسطر فشل لا مكانه هنا.
 * ثم تُضاف حالات التخصيص: **التخصيص لا يوسّع الصدق** — لا خيار يجعل سطرًا غير متحقّق يُعرض.
 */
class UnifiedActivityModelTest {

    private fun done(knob: String, to: String? = "on", from: String? = null) =
        StoryLine(knob, from, to, LineTone.DONE)

    private fun scene(
        kind: SceneKind,
        lines: List<StoryLine>,
        appLabel: String? = null,
        atMs: Long? = null,
    ) = StoryboardScene(
        kind = kind,
        labelKey = "scene",
        appLabel = appLabel,
        lines = lines,
        atMs = atMs,
    )

    private fun perApp(lines: List<StoryLine>, atMs: Long? = null) =
        scene(SceneKind.PER_APP, lines, appLabel = "App", atMs = atMs)

    private fun maxAi(lines: List<StoryLine>) = scene(SceneKind.MAX_AI, lines)

    // ── قواعد الصدق ────────────────────────────────────────────────────────────

    @Test
    fun `a failure line is never rendered on the home card`() {
        val scenes = listOf(
            perApp(
                listOf(
                    StoryLine("gpu_profile", null, null, LineTone.FAILED, "not-verified"),
                    done("gpu_profile"),
                ),
            ),
        )

        val model = UnifiedActivityModel.build(scenes)

        assertEquals(1, model.scenes.single().lines.size)
        assertEquals(LineTone.DONE, model.scenes.single().lines.single().tone)
    }

    @Test
    fun `a chosen setting with no before value is still shown`() {
        // هذه هي الحالة التي كانت تُطرح: اختيار بلا قياس (Kill Background Apps · بروفايل · حاكم).
        val scenes = listOf(perApp(listOf(done("kill_bg_apps"), done("gpu_profile", to = "gaming"))))

        val knobs = UnifiedActivityModel.build(scenes).scenes.single().lines.map { it.knob }

        assertEquals(listOf("kill_bg_apps", "gpu_profile"), knobs)
    }

    @Test
    fun `a success line with nothing to say is dropped`() {
        val scenes = listOf(perApp(listOf(done("refresh_rate", to = null))))

        assertTrue(UnifiedActivityModel.build(scenes).isEmpty)
    }

    @Test
    fun `one knob is never rendered twice across two scenes`() {
        // العطب المقيس: المشهد الأول يُعرض كاملًا ثم تُضاف أسطر الثاني بترشيح على قائمتها وحدها،
        // فمقبض واحد يُقرأ مرّتين في بطاقة واحدة.
        val scenes = listOf(
            perApp(listOf(done("cpu_limits:policy0", to = "300:1300"))),
            scene(
                SceneKind.MANUAL,
                listOf(done("cpu_limits:policy0", to = "300:1300", from = "300:2400")),
            ),
        )

        val model = UnifiedActivityModel.build(scenes)

        assertEquals(1, model.scenes.size)
        assertEquals(SceneKind.PER_APP, model.scenes.single().kind)
    }

    @Test
    fun `a card with nothing verified says so instead of rendering an empty frame`() {
        assertFalse(UnifiedActivityModel.shouldShow(emptyList()))
        assertFalse(
            UnifiedActivityModel.shouldShow(
                listOf(scene(SceneKind.PER_APP, listOf(StoryLine("gpu_profile", null, null, LineTone.HELD, "held")))),
            ),
        )
        assertTrue(UnifiedActivityModel.shouldShow(listOf(perApp(listOf(done("gpu_profile"))))))
    }

    @Test
    fun `the max ai active marker is kept although it carries no value`() {
        val marker = StoryLine("max_ai_active", null, null, LineTone.DONE)

        assertTrue("بادئة الذكاء معنى لا فراغ", marker.knob.startsWith(UnifiedActivityModel.MAX_AI_PREFIX))
        assertEquals(
            "max_ai_active",
            UnifiedActivityModel.build(listOf(maxAi(listOf(marker)))).scenes.single().lines.single().knob,
        )
    }

    // ── التلقائي: الشكل من البيانات، وقاعدة معلنة ─────────────────────────────

    @Test
    fun `the adaptive style picks a form from the data itself`() {
        assertEquals(
            "مشهد واحد ⇒ سينمائي",
            CardStyle.CINEMATIC,
            UnifiedActivityModel.adaptiveStyle(listOf(perApp(listOf(done("gpu_profile"))))),
        )
        assertEquals(
            "مشهدان ⇒ قائمة عملية",
            CardStyle.SUMMARY,
            UnifiedActivityModel.adaptiveStyle(
                listOf(
                    perApp(listOf(done("gpu_profile"))),
                    maxAi(listOf(done("max_ai_objective", to = "balanced"))),
                ),
            ),
        )
        assertEquals(
            "ميزات كثيرة ⇒ شارات",
            CardStyle.CHIPS,
            UnifiedActivityModel.adaptiveStyle(listOf(perApp((1..6).map { done("knob$it") }))),
        )
    }

    @Test
    fun `auto chooses the style while the content rules still apply`() {
        // قاعدة مقصودة: «التلقائي» يختار الشكل، ولا يُلغي ما ضبطه المستخدم بيده.
        val scenes = listOf(maxAi(listOf(done("max_ai_active", to = null))))

        val model = UnifiedActivityModel.build(scenes, CardOptions(auto = true, showMaxAi = false))

        assertTrue("التلقائي لا يُحيي مشهدًا أسكته المستخدم", model.isEmpty)
    }

    // ── التخصيص ───────────────────────────────────────────────────────────────

    @Test
    fun `a fixed style keeps its own budgets instead of the adaptive one`() {
        val scenes = listOf(perApp((1..6).map { done("knob$it") }))

        val cinematic = UnifiedActivityModel.build(scenes, CardOptions(auto = false, style = CardStyle.CINEMATIC))
        val chips = UnifiedActivityModel.build(scenes, CardOptions(auto = false, style = CardStyle.CHIPS))

        assertEquals(CardStyle.CINEMATIC, cinematic.style)
        assertEquals(CardStyle.CINEMATIC.maxLines, cinematic.scenes.single().lines.size)
        assertEquals(CardStyle.CHIPS, chips.style)
        assertEquals(CardStyle.CHIPS.maxLines, chips.scenes.single().lines.size)
        assertTrue(
            "الأسلوب يجب أن يُغيّر ما يُرسم فعلًا لا عنوانه",
            chips.scenes.single().lines.size > cinematic.scenes.single().lines.size,
        )
    }

    @Test
    fun `hiding a scene kind removes it without inventing a replacement`() {
        val scenes = listOf(
            perApp(listOf(done("gpu_profile"))),
            maxAi(listOf(done("max_ai_objective", to = "balanced"))),
        )

        val model = UnifiedActivityModel.build(scenes, CardOptions(showPerApp = false))

        assertEquals(1, model.scenes.size)
        assertEquals(SceneKind.MAX_AI, model.scenes.single().kind)
    }

    @Test
    fun `monitoring without a verified change can be silenced`() {
        // «الذكاء يعمل ويراقب» بلا تغيير مثبت: حالة، يطلبها المستخدم أو يُسكتها.
        val monitoring = listOf(maxAi(listOf(StoryLine("max_ai_active", null, null, LineTone.DONE))))

        assertTrue(UnifiedActivityModel.build(monitoring, CardOptions(showMonitoring = false)).isEmpty)
        assertFalse("الافتراضي يعرضها", UnifiedActivityModel.build(monitoring).isEmpty)
        // وخفضها لا يُسقط نتيجةً مثبتة للذكاء: سطرها له قيمة.
        val verified = listOf(maxAi(listOf(done("cpu_limits:policy0", to = "300:1300"))))
        assertFalse(UnifiedActivityModel.build(verified, CardOptions(showMonitoring = false)).isEmpty)
    }

    @Test
    fun `the session policy decides whether last session's summary survives`() {
        val now = 1_000_000_000L
        val fresh = listOf(perApp(listOf(done("gpu_profile")), atMs = now - 60_000L))
        val old = listOf(
            perApp(
                listOf(done("gpu_profile")),
                atMs = now - UnifiedActivityModel.SESSION_RECENT_MS - 1L,
            ),
        )

        assertFalse(UnifiedActivityModel.build(fresh, CardOptions(session = SessionPolicy.RECENT), now).isEmpty)
        assertTrue(UnifiedActivityModel.build(old, CardOptions(session = SessionPolicy.RECENT), now).isEmpty)
        assertFalse(UnifiedActivityModel.build(old, CardOptions(session = SessionPolicy.ALWAYS), now).isEmpty)
        assertTrue(UnifiedActivityModel.build(fresh, CardOptions(session = SessionPolicy.HIDE), now).isEmpty)
    }

    @Test
    fun `the session policy never touches a scene that is not a stored session`() {
        // مشهد الذكاء وتحكّمك اليدوي مصدرهما الحالة القائمة لا ملف جلسة، فلا وقت لهما ولا يُسقطهما
        // خيار يخصّ الجلسات.
        val manual = listOf(scene(SceneKind.MANUAL, listOf(done("gpu_max_freq", to = "650000000", from = "754000000"))))

        assertFalse(UnifiedActivityModel.build(manual, CardOptions(session = SessionPolicy.HIDE), 12L).isEmpty)
    }

    @Test
    fun `the technical verbosity is the only thing that reveals a success reason`() {
        val scenes = listOf(perApp(listOf(done("gpu_profile", to = "gaming"))))

        assertFalse(UnifiedActivityModel.build(scenes, CardOptions(verbosity = CardVerbosity.SIMPLE)).showReason)
        assertTrue(UnifiedActivityModel.build(scenes, CardOptions(verbosity = CardVerbosity.TECHNICAL)).showReason)
    }

    @Test
    fun `the customization defaults are the auto ones`() {
        val defaults = CardOptions.DEFAULT

        assertTrue("التلقائي هو الأساس", defaults.auto)
        assertTrue(defaults.showPerApp)
        assertTrue(defaults.showMaxAi)
        assertTrue(defaults.showManual)
        assertTrue(defaults.showMonitoring)
        assertEquals(CardVerbosity.SIMPLE, defaults.verbosity)
        assertEquals(CardMotion.FULL, defaults.motion)
        assertEquals(SessionPolicy.ALWAYS, defaults.session)
    }

    @Test
    fun `a saved auto style still chooses from the data when customization is on`() {
        // الحالة الواقعة: أُطفي «التلقائي» قبل اختيار نمط، فيبقى المحفوظ `AUTO`. ولو قُرئت `AUTO`
        // كأرقامها لتبعت البطاقة حدًّا لا يعني أحدًا، بلا أن يظهر ذلك في أي مكان.
        val scenes = listOf(
            perApp(listOf(done("gpu_profile"))),
            maxAi(listOf(done("max_ai_objective", to = "balanced"))),
        )

        val model = UnifiedActivityModel.build(scenes, CardOptions(auto = false, style = CardStyle.AUTO))

        assertEquals(CardStyle.SUMMARY, model.style)
    }

    @Test
    fun `the summary line only claims a default when every option is the default`() {
        assertTrue(CardOptions.DEFAULT.isDefault)
        // والنمط المحفوظ وحده لا يُخرج عن الافتراضي ما دام «التلقائي» يعمل: قيمته حينها لا تُنفَّذ.
        assertTrue(CardOptions(style = CardStyle.CHIPS).isDefault)
        assertFalse(
            "من أوقف الحركة فعّل خيارًا، ولا يُقال له إنه على الافتراضي",
            CardOptions(motion = CardMotion.OFF).isDefault,
        )
        assertFalse(CardOptions(auto = false).isDefault)
        assertFalse(CardOptions(showPerApp = false).isDefault)
        assertFalse(CardOptions(verbosity = CardVerbosity.TECHNICAL).isDefault)
        assertFalse(CardOptions(session = SessionPolicy.RECENT).isDefault)
        assertFalse(CardOptions(showMonitoring = false).isDefault)
    }

    @Test
    fun `the timeline style keeps each event with its own time`() {
        // الفرق المقصود بين «خط زمني» وبين بقية الأنماط: الحدث لا يُفرش إلى أسطر في قائمة واحدة،
        // فيبقى وقته ملتصقًا به. وزمن المشهد اليدوي لا يُخترع: يبقى `null` فلا يُكتب له سطر زمن.
        val scenes = listOf(
            perApp(listOf(done("gpu_profile", to = "gaming")), atMs = 5_000L),
            scene(SceneKind.MANUAL, listOf(done("gpu_max_freq", to = "650000000"))),
        )

        val model = UnifiedActivityModel.build(scenes, CardOptions(auto = false, style = CardStyle.TIMELINE))

        assertEquals(CardStyle.TIMELINE, model.style)
        assertEquals(2, model.scenes.size)
        assertEquals(5_000L, model.scenes.first().atMs)
        assertNull(model.scenes[1].atMs)
        assertTrue("كل حدث يحتفظ بأسطره", model.scenes.all { it.lines.isNotEmpty() })
    }

    @Test
    fun `offscreen motion means no motion rather than a zero length animation`() {
        assertEquals(0, CardMotion.OFF.enterMs)
        assertTrue("«مخفّفة» أهدأ من الكاملة لا مساوية لها", CardMotion.REDUCED.enterMs < CardMotion.FULL.enterMs)
    }
}
