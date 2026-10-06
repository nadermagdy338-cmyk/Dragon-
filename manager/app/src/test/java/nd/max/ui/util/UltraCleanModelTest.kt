/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * التنظيف الفائق — **القرارات وحدها**، مقيسة على JVM بلا أندرويد وبلا ملفّ.
 *
 * وأربعة عقود من `MAX-MANAGER-LEVEL-UP.md` §6 §7 §10.3 محفورة هنا، فلا يحرّكها أحد بضغطة
 * عابرة: مطفأ افتراضيًّا (APK والمجلدات الفارغة) · المقفل يُعلن ما ينقصه ولا يختفي · لا صفر
 * مكان فشل القياس · المجموع يتبع التأشير لا الوجود.
 */
class UltraCleanModelTest {

    @Test
    fun `installers and empty folders are off by default, by the owner's contract`() {
        val safe = UltraCleanModel.safeSelection()
        assertFalse("APK قد تكون ملك المستخدم", safe.contains(UltraCleanCategory.InstallerFiles))
        assertFalse("مجلد فارغ قد يكون مجلد المستخدم", safe.contains(UltraCleanCategory.EmptyFolders))
        assertEquals(
            "وما عدا الاثنين مُشّر: كاش، تخزين مشترك، صور مصغّرة، سجلات",
            setOf(
                UltraCleanCategory.AppCaches,
                UltraCleanCategory.SharedCaches,
                UltraCleanCategory.Thumbnails,
                UltraCleanCategory.SystemLogs,
            ),
            safe,
        )
    }

    @Test
    fun `a root-only category is hidden without root, and the rest is shown`() {
        // والمخفيّة واحدة فقط: مسار السجلات خارج ما يقرؤه التطبيق بلا جذر، فعرضها كصفّ
        // معطّل كان صفًّا لا يمتلئ أبدًا.
        assertFalse(UltraCleanModel.visibleAt(rootAvailable = false, UltraCleanCategory.SystemLogs))
        assertTrue(UltraCleanModel.visibleAt(rootAvailable = true, UltraCleanCategory.SystemLogs))
        for (category in UltraCleanCategory.entries.filter { !it.needsRoot }) {
            assertTrue(
                "ما يُقاس بلا جذر يُعرض بلا جذر: ${category.key}",
                UltraCleanModel.visibleAt(rootAvailable = false, category),
            )
        }
    }

    @Test
    fun `the total follows the ticks, and a failed measurement is not a zero`() {
        val measurements = CleanMeasurementSet(
            listOf(
                CleanMeasurement(UltraCleanCategory.AppCaches, 1_000L, 3),
                CleanMeasurement(UltraCleanCategory.Thumbnails, 500L, 2),
                // فشل قياس: ليس صفرًا، ولا يُحسب.
                CleanMeasurement(UltraCleanCategory.SharedCaches, null, 0),
                CleanMeasurement(UltraCleanCategory.SystemLogs, 7_000L, 9),
            )
        )

        val ticked = setOf(UltraCleanCategory.AppCaches, UltraCleanCategory.Thumbnails)
        val small = UltraCleanModel.total(ticked, measurements)
        assertEquals(1_500L, small.bytes)
        assertFalse("كل ما أُشّر قيس، فالمجموع تامّ", small.hasUnmeasured)

        val withFailure = UltraCleanModel.total(
            ticked + UltraCleanCategory.SharedCaches,
            measurements,
        )
        assertEquals(
            "الفئة التي فشل قياسها لا تدخل الرقم، ولا تُقرأ صفرًا",
            1_500L,
            withFailure.bytes,
        )
        assertTrue("لكن المجموع يُعلن أنه ناقص", withFailure.hasUnmeasured)

        val all = UltraCleanModel.total(
            setOf(
                UltraCleanCategory.AppCaches,
                UltraCleanCategory.Thumbnails,
                UltraCleanCategory.SystemLogs,
            ),
            measurements,
        )
        assertEquals(8_500L, all.bytes)

        assertFalse(
            "وما لم يُشّر لا يدخل المجموع ولو قيس",
            UltraCleanModel.total(setOf(UltraCleanCategory.SystemLogs), measurements).bytes == 0L,
        )
    }

    @Test
    fun `an untouched category contributes nothing, and an empty measurement set is not a failure`() {
        val empty = UltraCleanModel.total(UltraCleanModel.safeSelection(), CleanMeasurementSet())
        assertEquals(0L, empty.bytes)
        // ولم تُقس فئة واحدة بعد ⇒ لا «ناقص» بل «لا شيء بعد»: الشاشة تفرّق بينهما بـ
        // `measuredOnce`، والنموذج لا يَدّعي نقصًا لم يقع.
        assertFalse(empty.hasUnmeasured)
    }

    @Test
    fun `empty folders are reported by count, because their size is zero by definition`() {
        assertTrue(UltraCleanModel.showsCount(UltraCleanCategory.EmptyFolders))
        assertFalse(UltraCleanModel.showsCount(UltraCleanCategory.Thumbnails))
        assertFalse(UltraCleanModel.showsCount(UltraCleanCategory.InstallerFiles))
    }

    @Test
    fun `every key is distinct, so a stored key can never mean two categories`() {
        val keys = UltraCleanCategory.entries.map { it.key }
        assertEquals(keys.distinct(), keys)
        assertTrue("والمفاتيح لاتينية صغيرة بلا فراغ", keys.all { it.matches(Regex("[a-z_]+")) })
    }
}
