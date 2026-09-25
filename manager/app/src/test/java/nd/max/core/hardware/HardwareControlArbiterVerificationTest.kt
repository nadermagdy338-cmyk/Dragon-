/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.hardware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

/**
 * انحدار مقيس من جهاز حقيقي: `APPLY_VERIFY_FAILED knob=gpu_profile expected=1300000000
 * live=754000000` ثم `APPLY_DRIFT_REASSERT_FAILED` بعد ثانيتين — مرّتين لكل تطبيق.
 *
 * والقراءة الصحيحة: جهاز قيّدته سياسة الـvendor عند 754MHz **لبّى** طلب سقفٍ عند 1.3GHz.
 * فالمُحكِّم كان يكتب ثم يرى قيمة أقل ثم يَحكم بالفشل ثم **يسترجع خط الأساس** ويعيد الكرّة كل
 * دورة انحراف. وهذه الاختبارات تثبّت الحدّين معًا: لا فشل كاذب حين يكون الطلب مُلبّى، ولا
 * نجاح كاذب حين لا يكون.
 */
class HardwareControlArbiterVerificationTest {

    @Before
    fun configureSharedOwner() {
        val root = Files.createTempDirectory("arbiter-verification-test").toFile()
        SharedHardwareOwnershipStore.configure(
            root,
            appUid = 0,
            processId = ProcessHandle.current().pid().toInt(),
        )
        ManualControlLocks.configure(root)
        ManualControlLocks.clearAll()
        ControlOwnership.snapshot().forEach { ControlOwnership.release(it.key) }
    }

    @Test
    fun `a ceiling already below the request verifies without touching the hardware`() {
        val arbiter = HardwareControlArbiter()
        var writes = 0

        val result = arbiter.submit(
            key = "gpu_frequency:kgsl-3d0",
            owner = ControlOwnership.Owner.PER_APP,
            token = "per-app:com.example",
            desired = "1300000000",
            apply = { writes += 1; true },
            read = { "754000000" },
            baseline = "754000000",
            verify = HardwareVerification::ceilingAtMost,
        )

        assertTrue("السقف الأضيق من الطلب = مُلبّى", result.verified)
        assertEquals("ولا كتابة لأن لا شيء يحتاج تغييرًا", 0, writes)
        assertEquals("754000000", result.actual)
    }

    @Test
    fun `the same value fails verification under the literal default`() {
        val arbiter = HardwareControlArbiter()
        var writes = 0

        val result = arbiter.submit(
            key = "gpu_frequency:kgsl-3d0",
            owner = ControlOwnership.Owner.PER_APP,
            token = "per-app:com.example",
            desired = "1300000000",
            apply = { writes += 1; true },
            read = { "754000000" },
            baseline = "754000000",
        )

        assertFalse("التساوي الحرفي هو السلوك القديم، محفوظ لمن لم يُمرّر حكمًا", result.verified)
        assertTrue("وكان يكتب فعلًا ثم يسترجع — وهذا ما كان يخفق في وجه مُلطِّف الـvendor", writes > 0)
        assertTrue("والاسترجاع خط الأساس كان يقع أيضًا", result.rollbackAttempted)
        assertEquals(
            "سبب معروف ومكتوب، لا صمت",
            "apply-not-verified-baseline-restored",
            result.error,
        )
    }

    @Test
    fun `a request the hardware refuses still rolls back to the baseline`() {
        val arbiter = HardwareControlArbiter()
        var live = "300000000"

        val result = arbiter.submit(
            key = "gpu_frequency:kgsl-3d0",
            owner = ControlOwnership.Owner.PER_APP,
            token = "per-app:com.example",
            desired = "200000000",
            // نواة ترفض النزول تحت 300MHz: التطبيق يُنادى ثم لا تُتحرّك القيمة.
            apply = { live = "300000000"; true },
            read = { live },
            baseline = "300000000",
            restore = { live = it; true },
            verify = HardwareVerification::ceilingAtMost,
        )

        assertFalse(result.verified)
        assertTrue("الفشل الحقيقي يُرجع الحالة كما كانت", result.rollbackAttempted)
        assertEquals("300000000", live)
        assertEquals("300000000", result.actual)
    }
}
