package nd.max.ui.mainscreens

import nd.max.core.hardware.HardwareControlKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اللوحة تُقاس هنا لا على الجهاز: كل قرار فيها (ما يُعرض، وما لا يُعرض، وبأي ترتيب) خالص.
 *
 * وأهمّ ما تحرسه هذه الاختبارات هو **الصدق**: ألّا يُكتب «طُبِّق» لاختيار لم يُقس، وأن يتقدّم
 * الإخفاق النجاح، وألّا تُعرض «افتراضي» كإنجاز.
 */
class StoryboardModelTest {

    private fun applied(knob: String, expected: String, live: String) = MeasuredOutcome(
        knob = knob, outcome = "applied", reason = "verified", expected = expected, live = live,
    )

    private fun failed(knob: String, expected: String, live: String, reason: String = "not-verified") =
        MeasuredOutcome(knob = knob, outcome = "not-verified", reason = reason, expected = expected, live = live)

    @Test
    fun `a profile with no measured outcome is a choice, not a result`() {
        val scene = StoryboardModel.perAppScene(
            PerAppSceneInput(packageName = "com.x", appLabel = "X", profile = "gaming"),
        )!!
        assertEquals(SceneKind.PER_APP, scene.kind)
        // الاختيار يُكتب بلا «من/إلى» لأنه ليس تغييرًا داخل قيمة قائمة — ولا يُدَّعى قياسه.
        assertEquals(listOf("gpu_profile"), scene.lines.map { it.knob })
        assertNull(scene.lines.first().from)
        assertEquals("gaming", scene.lines.first().to)
        assertNull("بلا قياس فلا وقت مصدر", scene.atMs)
    }

    @Test
    fun `a measured change carries both values and its time`() {
        val scene = StoryboardModel.perAppScene(
            PerAppSceneInput(
                packageName = "com.x", appLabel = "X", profile = "balanced",
                outcomes = listOf(applied("gpu_profile", "754000000", "500000000")),
                measuredAtMs = 1_790_000_000_000L,
            ),
        )!!
        val line = scene.lines.single()
        assertEquals("754000000", line.from)
        assertEquals("500000000", line.to)
        assertEquals(LineTone.DONE, line.tone)
        assertEquals(1_790_000_000_000L, scene.atMs)
    }

    @Test
    fun `a failure comes before a success so the board answers the real question first`() {
        val scene = StoryboardModel.perAppScene(
            PerAppSceneInput(
                packageName = "com.x", appLabel = "X", profile = "gaming",
                outcomes = listOf(
                    applied("gpu_profile", "754000000", "754000000"),
                    failed("cpu_limits:policy0", "300000:1800000", "300000:1100000", "outside-proven-hardware-bounds"),
                ),
            ),
        )!!
        assertEquals("cpu_limits:policy0", scene.lines.first().knob)
        assertEquals(LineTone.FAILED, scene.lines.first().tone)
        assertEquals("outside-proven-hardware-bounds", scene.lines.first().reason)
        assertEquals("gpu_profile", scene.lines.last().knob)
    }

    @Test
    fun `a default profile with nothing configured produces no scene at all`() {
        assertNull(StoryboardModel.perAppScene(PerAppSceneInput("com.x", "X", "default")))
    }

    @Test
    fun `a skipped knob is shown as off and never as an achievement`() {
        val scene = StoryboardModel.perAppScene(
            PerAppSceneInput(
                packageName = "com.x", appLabel = "X", profile = "gaming",
                outcomes = listOf(
                    MeasuredOutcome("gpu_profile", "skipped", "profile-is-default", "", ""),
                    applied("gpu_governor", "default", "performance"),
                ),
            ),
        )!!
        assertEquals(LineTone.OFF, scene.lines.first { it.knob == "gpu_profile" }.tone)
        assertEquals(LineTone.DONE, scene.lines.first { it.knob == "gpu_governor" }.tone)
        assertEquals("ما نُفِّذ يتقدّم ما أُوقف عمدًا", "gpu_governor", scene.lines.first().knob)
    }

    @Test
    fun `cpu policies are listed from your choice and never duplicated by a measured line`() {
        val choices = listOf(
            CpuPolicyChoice("policy0", "range", 300_000L, 1_100_000L),
            CpuPolicyChoice("policy4", "lock", 500_000L, 1_500_000L),
        )
        val lines = StoryboardModel.cpuPolicyLines(choices, measuredKnobs = setOf("cpu_limits:policy4"))
        // المفتاح من `HardwareControlKey` نفسه: سطر المُختار وسطر العتاد يجب أن يتكلّما عن المقبض
        // ذاته، وإلا ظهر المقبض مرتين (أو لم يُربط أحدهما بالآخر أبدًا).
        assertEquals(listOf(HardwareControlKey.cpuLimits("policy0")), lines.map { it.knob })
        assertEquals("range 300 MHz \u2192 1.10 GHz", lines.single().to)
    }

    @Test
    fun `a chosen governor or gpu ceiling is a line of its own unless hardware already reported it`() {
        val scene = StoryboardModel.perAppScene(
            PerAppSceneInput(
                packageName = "com.x", appLabel = "X", profile = "default",
                cpuGovernor = "performance",
                gpuGovernor = "default",
                gpuMaxFreq = "754000000",
                outcomes = listOf(applied("gpu_governor", "performance", "performance")),
            ),
        )!!
        assertEquals(
            listOf("gpu_governor", "cpu_governor", "gpu_max_freq"),
            scene.lines.map { it.knob },
        )
        assertEquals("سقف الرسوم يُقرأ كتردد لا كمعرّف", "754 MHz", scene.lines.last().to)
    }

    @Test
    fun `frequency values are converted, and anything that is not a frequency is left untouched`() {
        assertEquals("1.30 GHz", StoryboardModel.readableValue("1300000000"))
        assertEquals("754 MHz", StoryboardModel.readableValue("754000000"))
        assertEquals("1.80 GHz", StoryboardModel.readableValue("1800000"))
        assertEquals("300 MHz", StoryboardModel.readableValue("300000"))
        assertEquals("performance", StoryboardModel.readableValue("performance"))
        assertEquals("on", StoryboardModel.readableValue("on"))
        assertEquals("300 MHz \u2192 1.80 GHz", StoryboardModel.readableRange(300_000L, 1_800_000L))
    }

    @Test
    fun `kill background apps appears when you turned it on for that app`() {
        val scene = StoryboardModel.perAppScene(
            PerAppSceneInput("com.x", "X", "balanced", killsBackground = true),
        )!!
        assertTrue(scene.lines.any { it.knob == "kill_bg_apps" && it.tone == LineTone.DONE })
    }

    @Test
    fun `max ai scene exists while it works and explains the objective as a choice`() {
        val scene = StoryboardModel.maxAiScene(MaxAiSceneInput(active = true, objective = "performance"))!!
        val objective = scene.lines.single { it.knob == "max_ai_objective" }
        assertEquals(LineTone.DONE, objective.tone)
        assertNull("الهدف اختيار لا تغيير داخل قيمة قائمة", objective.from)
        assertNull("ولا مشهد لذكاء متوقّف بلا تغيير مقيس", StoryboardModel.maxAiScene(MaxAiSceneInput(active = false)))
    }

    @Test
    fun `manual scene reports what you touched with the measured before and after`() {
        val scene = StoryboardModel.manualScene(
            listOf(ManualSceneInput("cpu_limits:policy0", "300000:1200000", "300000:1800000")),
        )!!
        assertEquals(SceneKind.MANUAL, scene.kind)
        assertEquals("300000:1200000", scene.lines.single().from)
        assertEquals("300000:1800000", scene.lines.single().to)
        assertNull("بلا لمس لا مشهد", StoryboardModel.manualScene(emptyList()))
    }

    @Test
    fun `the board keeps order and never exceeds the scene and line caps`() {
        val app = StoryboardModel.perAppScene(PerAppSceneInput("com.x", "X", "gaming"))!!
        val ai = StoryboardModel.maxAiScene(MaxAiSceneInput(active = true))!!
        val manual = StoryboardModel.manualScene(listOf(ManualSceneInput("k", "1", "2")))!!

        val board = StoryboardModel.storyboard(listOf(manual, null, ai, app))
        assertEquals(listOf(SceneKind.PER_APP, SceneKind.MAX_AI, SceneKind.MANUAL), board.map { it.kind })
        assertTrue(board.size <= StoryboardModel.MAX_SCENES)
        board.forEach { assertTrue(it.lines.size <= StoryboardModel.MAX_LINES_PER_SCENE) }
    }

    @Test
    fun `a knob is never listed twice in one scene`() {
        val scene = StoryboardModel.perAppScene(
            PerAppSceneInput(
                packageName = "com.x", appLabel = "X", profile = "balanced",
                outcomes = listOf(applied("gpu_profile", "754000000", "500000000")),
            ),
        )!!
        assertEquals(scene.lines.map { it.knob }.distinct(), scene.lines.map { it.knob })
    }
}
