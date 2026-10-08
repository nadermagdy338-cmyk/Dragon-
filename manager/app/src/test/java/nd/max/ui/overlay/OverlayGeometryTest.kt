/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.ui.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * رياضيات موضع التراكب — كانت داخل `OverlayWindow` بلا اختبار، وصارت هنا.
 *
 * أخطر ما يُقاس: نافذة خرجت عن الشاشة **لا تُسحَب عودة** على أغلب الأجهزة، فخطأ في [maxOffset]
 * يعني تراكبًا مفقودًا إلى الأبد بلا أيّ إشارة للمستخدم.
 */
class OverlayGeometryTest {

    // ───────────────────────────── أقصى إزاحة ─────────────────────────────

    @Test fun maxOffsetIsScreenMinusWindow() {
        assertEquals(640, OverlayGeometry.maxOffset(screen = 1080, size = 440))
    }

    @Test fun aWindowWiderThanTheScreenGivesZeroNotANegative() {
        // السالب هنا هو العطب: `coerceIn(0, سالب)` يرمي، ونافذة بإزاحة سالبة تخرج بلا رجعة.
        assertEquals(0, OverlayGeometry.maxOffset(screen = 400, size = 440))
        assertEquals(0, OverlayGeometry.maxOffset(screen = 1080, size = 1080))
    }

    // ───────────────────────────── الحبس ─────────────────────────────

    @Test fun clampKeepsTheWindowOnScreen() {
        assertEquals(0, OverlayGeometry.clamp(-50, screen = 1080, size = 440))
        assertEquals(640, OverlayGeometry.clamp(9999, screen = 1080, size = 440))
        assertEquals(300, OverlayGeometry.clamp(300, screen = 1080, size = 440))
    }

    @Test fun clampAcceptsTheExactEdges() {
        assertEquals(0, OverlayGeometry.clamp(0, screen = 1080, size = 440))
        assertEquals(640, OverlayGeometry.clamp(640, screen = 1080, size = 440))
    }

    @Test fun anUnmeasuredWindowIsLeftWhereItIsNotThrown() {
        // `view.width` صفر قبل أوّل تخطيط — حال حقيقيّ عند الالتقاط المبكر. ولا يُسحب إلى الأصل:
        // جرّ نافذة قبل قياسها يقفز بها إلى الزاوية، وهو أسوأ من تركها. والمهمّ ألّا يرمي
        // (`coerceIn(0, سالب)` يرمي) وأن يبقى المدى صالحًا.
        assertEquals(500, OverlayGeometry.clamp(500, screen = 1080, size = 0))
        assertEquals(0, OverlayGeometry.clamp(-5, screen = 1080, size = 0))
    }

    // ───────────────────────────── الحافة الأقرب ─────────────────────────────

    @Test fun snapGoesToTheNearestEdgeByCenter() {
        // مركز النافذة قبل المنتصف ⇒ يسار.
        assertEquals(0, OverlayGeometry.snapTarget(offset = 100, screen = 1080, size = 440))
        // مركزها بعد المنتصف ⇒ يمين (أقصى إزاحة).
        assertEquals(640, OverlayGeometry.snapTarget(offset = 600, screen = 1080, size = 440))
    }

    @Test fun snapDecidesByTheCenterNotTheLeadingEdge() {
        // حافّتها اليسرى (700) بعد المنتصف، لكن مركزها (920) أيضًا بعده ⇒ يمين. والقرار بالمركز.
        assertEquals(640, OverlayGeometry.snapTarget(offset = 700, screen = 1080, size = 440))
        // ونافذة حافّتها اليسرى في المنتصف تمامًا لكن مركزها بعده ⇒ يمين لا يسار.
        assertEquals(640, OverlayGeometry.snapTarget(offset = 320, screen = 1080, size = 440))
    }

    @Test fun anUnmeasuredWindowIsNotSnappedAnywhere() {
        // بلا عرض لا مركز ⇒ لا تخمين، تبقى حيث هي.
        assertEquals(777, OverlayGeometry.snapTarget(offset = 777, screen = 1080, size = 0))
        assertEquals(0, OverlayGeometry.snapTarget(offset = 0, screen = 1080, size = 0))
    }

    @Test fun snapIsIdempotent() {
        val left = OverlayGeometry.snapTarget(offset = 0, screen = 1080, size = 440)
        assertEquals(left, OverlayGeometry.snapTarget(offset = left, screen = 1080, size = 440))
        val right = OverlayGeometry.snapTarget(offset = 640, screen = 1080, size = 440)
        assertEquals(right, OverlayGeometry.snapTarget(offset = right, screen = 1080, size = 440))
    }

    @Test fun aFullWidthWindowHasNowhereToSnap() {
        // عرض == الشاشة ⇒ أقصى إزاحة صفر ⇒ الحافتان نقطة واحدة، ولا حركة.
        assertEquals(0, OverlayGeometry.snapTarget(offset = 0, screen = 1080, size = 1080))
    }
}
