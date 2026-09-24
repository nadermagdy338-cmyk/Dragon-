package nd.max.core.atlas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * قواعد عدم اللمس — أضيق قائمة تحمي أخطر شيء: حماية العتاد التي صمّمها المُصنِّع نفسه.
 *
 * ما يُقاس هنا ليس «أن القائمة صحيحة» بل **أن مطابقتها مغلقة الفشل**: فرع جديد من عائلة خطيرة
 * (`trip_point_14_hyst`) يُمنع بمجرّد الحروف، واسم عقدة ممنوع يُمنع سواء جاء في مفتاح تحكم أو في
 * مسار — والمرفوض يحمل رمز قاعدة ثابتًا لا جملة. والمقابل مهمّ بالقدر نفسه: المقبض المشروع
 * (`cpu_limits:policy0`) لا يُمسّه شيء من هذا.
 */
class AtlasSafetyPolicyTest {

    @Test
    fun `thermal protection is refused in every spelling this project has seen`() {
        listOf(
            "trip_point_0_temp",
            "trip_point_13_hyst",
            "emul_temp",
            "/sys/class/thermal/thermal_zone0/trip_point_0_temp",
        ).forEach { candidate ->
            val verdict = AtlasSafetyPolicy.verdictFor(
                name = candidate.substringAfterLast('/'),
                path = candidate,
            )
            assertTrue("$candidate must be denied", verdict is AtlasSafetyVerdict.Denied)
            assertEquals("thermal-trips", (verdict as AtlasSafetyVerdict.Denied).ruleId)
            assertTrue("the refusal carries its reason", verdict.reason.isNotBlank())
        }
    }

    @Test
    fun `kernel stability, watchdog, power state and uevent are refused`() {
        val expectations = mapOf(
            "sysrq-trigger" to "kernel-stability",
            "panic_on_oops" to "kernel-stability",
            "watchdog_thresh" to "watchdog",
            "state" to "power-state",
            "uevent" to "uevent",
        )
        val paths = mapOf(
            "sysrq-trigger" to "/proc/sysrq-trigger",
            "panic_on_oops" to "/proc/sys/kernel/panic_on_oops",
            "watchdog_thresh" to "/proc/sys/kernel/watchdog_thresh",
            "state" to "/sys/power/state",
            "uevent" to "/sys/devices/system/cpu/cpu0/uevent",
        )
        expectations.forEach { (name, rule) ->
            val verdict = AtlasSafetyPolicy.verdictFor(name = name, path = paths.getValue(name))
            assertTrue("$name must be denied", verdict is AtlasSafetyVerdict.Denied)
            assertEquals(rule, (verdict as AtlasSafetyVerdict.Denied).ruleId)
        }
    }

    @Test
    fun `a control key naming a denied node is refused as a key`() {
        val verdict = AtlasSafetyPolicy.verdictForKey("thermal_trip_points")
        assertTrue(verdict is AtlasSafetyVerdict.Denied)
        assertEquals("thermal-trips", (verdict as AtlasSafetyVerdict.Denied).ruleId)
    }

    @Test
    fun `a denied node reached through its path is refused even when the caller named it something else`() {
        // فشل مغلق: المُستدعي سمّى العقدة «policy0» لكن مسارها عقدة حماية حرارية — والحكم على
        // الطرفين معًا هو ما يمنع اسمًا بريئًا يمرّر خطرًا.
        val verdict = AtlasSafetyPolicy.verdictFor(
            name = "policy0",
            path = "/sys/class/thermal/thermal_zone0/trip_point_0_temp",
        )
        assertTrue(verdict is AtlasSafetyVerdict.Denied)
    }

    @Test
    fun `the knobs this project legitimately drives stay allowed`() {
        listOf(
            "cpu_limits:policy0" to "/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq",
            "scaling_min_freq" to "/sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq",
            "gpu_profile" to "/sys/class/devfreq/1300000000.mali/max_freq",
            "cpu_governor" to "/sys/devices/system/cpu/cpufreq/policy0/scaling_governor",
        ).forEach { (name, path) ->
            val verdict = AtlasSafetyPolicy.verdictFor(name = name, path = path)
            assertFalse("$name must stay allowed", verdict.denied)
        }
    }

    @Test
    fun `an allowed verdict is the same object story in both directions`() {
        assertTrue(AtlasSafetyPolicy.verdictFor("cpu_limits:policy0") == AtlasSafetyVerdict.Allowed)
        assertFalse(AtlasSafetyVerdict.Allowed.denied)
        assertTrue(AtlasSafetyVerdict.Denied("thermal-trips", "reason").denied)
    }

    @Test
    fun `every rule names a family, not a lone file`() {
        AtlasSafetyPolicy.RULES.forEach { rule ->
            assertTrue("a rule must carry an id: $rule", rule.id.isNotBlank())
            assertTrue("a rule must carry its reason: $rule", rule.reason.isNotBlank())
            assertTrue(
                "matching must be broad (fail closed): ${rule.id}",
                rule.deniedNameFragments.isNotEmpty() || rule.deniedPathFragments.isNotEmpty(),
            )
        }
    }
}
