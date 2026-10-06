/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.spoof

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * بوّابة المحرّك: تقيس **التمييز والترتيب** لا وجودَ رمزٍ مكتوب.
 *
 * والعطب الذي وُلدت منه مقيس في تصدير ٢٠٢٦-١٠-٠٦: `W diag: apply refused: ENGINE_UNAVAILABLE`
 * و`COPG` **مثبَّتة** في القائمة نفسها — أي أنّ رمزًا واحدًا كان يُعرض عن أربع حالات علاجها مختلف.
 * فالدعاوى هنا: كلّ علامة تُسمّى بسببها · لا تُقبل وحدة بالاسم المعروض أو ببادئة معرّف · والغائب
 * («لا يُقرأ») لا يُقرأ «متاحًا».
 */
class SpoofEngineGateTest {

    /** `module.prop` كما يكتبه `build.sh` في COPG (مقيس من المصدر، ٢٠٢٦-١٠-٠٦). */
    private val prop = listOf(
        "id=COPG",
        "name=✨ COPG SPOOF ✨",
        "version=6.9.8",
        "versionCode=698",
    ).joinToString("\n")

    @Test fun moduleThatIdentifiesItselfPasses() {
        assertNull(SpoofEngineGate.identityReason(prop, "COPG"))
    }

    @Test fun unreadableOrUnidentifyingModuleIsNeverAvailable() {
        assertEquals(SpoofEngineReason.ENGINE_MODULE_ABSENT, SpoofEngineGate.identityReason(null, "COPG"))
        assertEquals(SpoofEngineReason.ENGINE_MODULE_ABSENT, SpoofEngineGate.identityReason("", "COPG"))
        assertEquals(SpoofEngineReason.ENGINE_MODULE_ABSENT, SpoofEngineGate.identityReason("name=COPG", "COPG"))
    }

    @Test fun displayNameAndIdentifierPrefixAreNotIdentity() {
        // الاسم المعروض يحمل COPG والسطر لا يحمله ⇒ لا تُقبل بالاسم (المعرّف هو الحاكم).
        assertEquals(SpoofEngineReason.ENGINE_MODULE_ABSENT,
            SpoofEngineGate.identityReason("id=other\nname=✨ COPG SPOOF ✨", "COPG"))
        // `id=COPG-VD` معرّف محرّك آخر — و`startsWith` كانت ستخلط بينهما.
        assertEquals(SpoofEngineReason.ENGINE_MODULE_ABSENT, SpoofEngineGate.identityReason("id=COPG-VD", "COPG"))
        // والبياض يُطوى كما يقرؤه مدير الجذر (سطر مفرد بلا سطر جديد في آخره).
        assertNull(SpoofEngineGate.identityReason("   id=COPG   ", "COPG"))
    }

    @Test fun noMarkerMeansNoRefusal() {
        assertNull(SpoofEngineGate.markerReason(emptySet()))
    }

    @Test fun everyListedMarkerProducesItsOwnCause() {
        assertEquals(SpoofEngineReason.ENGINE_DISABLED, SpoofEngineGate.markerReason(setOf(SpoofEngineGate.DISABLE)))
        assertEquals(SpoofEngineReason.ENGINE_REMOVAL_PENDING, SpoofEngineGate.markerReason(setOf(SpoofEngineGate.REMOVE)))
        assertEquals(SpoofEngineReason.ENGINE_UPDATE_PENDING, SpoofEngineGate.markerReason(setOf(SpoofEngineGate.UPDATE)))
    }

    @Test fun disabledOutranksRemovalAndUpdate() {
        // وحدةٌ معطَّلة لا يُصلحها انتظارُ إقلاع: أوّل ما يُفعل تفعيلها.
        assertEquals(SpoofEngineReason.ENGINE_DISABLED, SpoofEngineGate.markerReason(
            setOf(SpoofEngineGate.UPDATE, SpoofEngineGate.REMOVE, SpoofEngineGate.DISABLE)))
        assertEquals(SpoofEngineReason.ENGINE_REMOVAL_PENDING, SpoofEngineGate.markerReason(
            setOf(SpoofEngineGate.UPDATE, SpoofEngineGate.REMOVE)))
    }

    @Test fun theFourEngineCausesAreDistinctAndTheOpaqueCodeIsGone() {
        val causes = listOf(
            SpoofEngineReason.ENGINE_MODULE_ABSENT,
            SpoofEngineReason.ENGINE_DISABLED,
            SpoofEngineReason.ENGINE_REMOVAL_PENDING,
            SpoofEngineReason.ENGINE_UPDATE_PENDING,
        )
        assertEquals(causes.size, causes.distinct().size)
        // والعقد مع العائلة نفسها: الرمز الواحد الذي كان يجمعها لم يبقَ عضوًا.
        assertTrue(SpoofEngineReason.values().none { it.name == "ENGINE_UNAVAILABLE" })
    }

    @Test fun markerListAndHandlingCannotDriftApart() {
        // قائمة أسماء الملفات وعلاجها في موضع واحد: إضافة علامة إلى القائمة بلا سببٍ لها تُسقط هذا.
        assertEquals(listOf("disable", "remove", "update"), SpoofEngineGate.markers)
        SpoofEngineGate.markers.forEach { marker ->
            assertNotNull("marker without a cause: $marker", SpoofEngineGate.markerReason(setOf(marker)))
        }
    }
}
