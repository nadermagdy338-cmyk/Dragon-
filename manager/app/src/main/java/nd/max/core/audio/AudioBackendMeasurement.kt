/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * قياس مسار المحرّك الكامل — **نُقل من `AudioStudioViewModel` حرفيًّا (تكملة ٢٣٣) بلا تغيير سلوك**،
 * ونقلُه ليس تجميلًا: بوابة `code_health` تُجمِّد «الملفات فوق 1000 سطر» عند سقفها، وكان الـViewModel
 * قد تجاوزه (1074) بعد تسجيل عمليات الصوت — والقياس **منطقٌ صافٍ** يعيش في `core/audio/` أصلًا
 * («والقياس في `core/audio/` وحده»)، فلا وجه لبقائه في طبقة الواجهة.
 *
 * والحدّ الذي لا يُكسر كما كان: هذا القياس **لا يلمس `state` ولا يقرّر شيئًا للواجهة** — يأخذ
 * قياساتٍ ويُعيد حصيلة. ويكتب سطرَيْ حصيلة القياس في السجلّ ([EventLog.audioOp]، تكملة ٢٣٣) لأنّهما
 * دليل «أين مؤثّر المصنّع؟ ومن يقود الآن؟» على جهاز صاحب الشكوى — فالمصفوفة على الشاشة لا تصل إلى
 * ملفّ السجلّ، والذي يُشخَّص من ملفٍّ هو ما يجب أن يُكتب فيه.
 */
package nd.max.core.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nd.max.core.platform.EventLog

/**
 * حصيلة قياس المسار كلّه — **لا تُخزَّن**: تُقاس فتُعرض.
 *
 * ووجودُها كنوعٍ واحد يمنع أسوأ ما يُمكن: أن يصل الجردة إلى الشاشة والحكم لا، أو أن يُبنى السلّم
 * من حكمٍ أقدم من جردته.
 */
internal data class AudioBackendMeasurement(
    val vendor: VendorAudioSnapshot?,
    val probe: VendorAttachProbe?,
    val ladder: List<AudioBackendCandidate>?,
    val selection: AudioBackendSelection?,
)

/**
 * يقيس مؤثّر المصنّع ويبني السلّم — **بترتيبٍ ملزم:** الجردة (قراءةٌ لا تلمس) ← اللمس (إن وُجد
 * مؤثّر) ← الحكم ← السلّم ← الاختيار.
 *
 * **ولماذا اللمس تلقائيًّا ولا يُترك للزرّ:** لأنّ «هل يعمل Dolby؟» هو السؤال الذي وُلدت هذه
 * الموجة من أجله، وجوابُه بلا محاولة هو `detected_but_unavailable` بسبب «لم يُقس» — وهو أصدق
 * من الوعد وأبخس من محاولةٍ كلفتها إرفاقٌ وتحريرٌ في اللحظة نفسها. فإن رُفضت المحاولة (حماية
 * الواجهات المخفيّة) صار السبب مكتوبًا: `hidden-api-blocked-or-absent`.
 */
internal suspend fun measureAudioBackends(
    abilities: DeclaredAudioAbilities?,
    system: AudioSystemSnapshot?,
): AudioBackendMeasurement {
    // والمصدر الثاني يُقرأ **قبل الجردة**: فهو لا يلمس شيئًا، ويجيب «أين يوجد؟» — ووثيقة تهيئة
    // النظام قُرئت أصلًا في `systemLayer.snapshot()`، فلا قراءة جذريّة ثانية لمجرّد العرض.
    val config = withContext(Dispatchers.IO) {
        system?.document?.let { runCatching { vendorConfigIdentities(it) }.getOrNull() }.orEmpty()
    }
    val vendor = withContext(Dispatchers.IO) {
        runCatching { VendorAudioDiscovery.snapshot(config) }.getOrNull()
    }
    // واللمس لمعرّفٍ **مكتشَف** فقط: لا نجرب معرّفًا نعرفه على جهاز لم يُعلنه (وإلا صار «التجريب»
    // تخمينًا — وهو ما يمنعه هذا المستودع).
    val probe = vendor?.detected?.firstOrNull()?.let { identity ->
        withContext(Dispatchers.IO) {
            runCatching { VendorAudioDiscovery.probeAttach(identity.uuid) }.getOrNull()
        }
    }
    val verdict: VendorAudioVerdict? = vendor?.let {
        vendorAudioVerdict(VendorAudioEvidence(it.descriptors, probe?.evidence, config))
    }
    val ladder = abilities?.let {
        audioBackendLadder(
            AudioBackendEvidence(
                abilities = it,
                vendor = verdict,
                systemLayerInstalled = system?.installed,
                systemLayerWritable = system?.moduleWritable,
            ),
        )
    }
    val selection = ladder?.let { audioBackendSelection(it) }
    // **وحصيلة القياس تُكتب في السجلّ لا في الشاشة وحدها (تكملة ٢٣٣):** «أين مؤثّر المصنّع؟»
    // و«من يقود الآن؟» أوّلُ سؤالَي «لماذا لا يعمل؟»، وجوابُهما يُقرأ من ملفّ صاحب الجهاز.
    verdict?.let {
        EventLog.audioOp(
            target = "vendor_discovery",
            outcome = it.presence.token,
            reason = it.reason,
            expected = it.effectUuid,
            actual = it.detail,
        )
    }
    selection?.let {
        EventLog.audioOp(
            target = "backend_selection",
            outcome = it.primary?.token ?: "none",
            reason = it.reason,
        )
    }
    return AudioBackendMeasurement(
        vendor = vendor,
        probe = probe,
        ladder = ladder,
        selection = selection,
    )
}
