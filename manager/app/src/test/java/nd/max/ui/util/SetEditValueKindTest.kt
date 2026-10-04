/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * نوع القيمة وصيغة الكتابة الثنائية — منطق نقيّ يُختبر بلا جهاز.
 *
 * **ولماذا يستحق اختبارًا:** هذان القراران يقرّران ما يُعرض للمستخدم (مفتاح تبديل أم حقل نصّ) وما
 * يُكتب على الجهاز فعلًا. والخطأ فيهما صامت: قيمة `true` تُكتب `1` فتبدو ناجحة، والقراءة التالية
 * تُظهر تغييرًا لم يطلبه أحد.
 */
class SetEditValueKindTest {

    @Test
    fun `الأنواع الثنائية الأربعة تُقرأ ثنائية`() {
        listOf("0", "1", "true", "false", "TRUE", " False ").forEach { value ->
            assertEquals("القيمة '$value' ثنائية", SetEditValueKind.Boolean, setEditValueKind(value))
        }
    }

    @Test
    fun `ما ليس ثنائيًا يبقى حقلًا نصيًّا`() {
        listOf("2", "-1", "unknown", "10", "en-US", "/data/local/tmp", "", "1.5").forEach { value ->
            assertEquals("القيمة '$value' نصّية", SetEditValueKind.Text, setEditValueKind(value))
        }
    }

    @Test
    fun `الكتابة تردّ الصيغة التي قرأتها من الجهاز`() {
        // `getprop` يكتب `true`، و`Settings` يكتب `1` — ومن يوحّدهما يُفسد مفتاحًا كان سليمًا.
        assertEquals("true", SetEditItem("k", "true", SetEditCategory.ANDROID_PROP).booleanTrue)
        assertEquals("1", SetEditItem("k", "1", SetEditCategory.GLOBAL).booleanTrue)
        assertEquals("false", SetEditItem("k", "false", SetEditCategory.ANDROID_PROP).booleanFalse)
        assertEquals("0", SetEditItem("k", "0", SetEditCategory.GLOBAL).booleanFalse)
    }

    @Test
    fun `تبديل قيمة ثنائية يعطي النقيض في الصيغة نفسها`() {
        val prop = SetEditItem("k", "true", SetEditCategory.ANDROID_PROP)
        assertEquals("true", prop.booleanTrue)
        assertEquals("false", prop.booleanFalse)

        val setting = SetEditItem("k", "0", SetEditCategory.GLOBAL)
        assertEquals("1", setting.booleanTrue)
        assertEquals("0", setting.booleanFalse)
    }
}
