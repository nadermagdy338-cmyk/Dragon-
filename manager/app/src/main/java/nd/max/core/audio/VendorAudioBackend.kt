/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * الصوت — **محرّك مؤثّر المصنّع** (تكملة ٢٣٠): يفتحه، يقرؤه، **يكتب فيه عبر المحكِّم**، ويقرأه بعد
 * الكتابة، ويُحرّره.
 *
 * **وكل قاعدة المستودع ساريةً هنا بلا استثناء:** كل `apply` يقابله `read` من المنصّة نفسها، والمقارنة
 * **حرفيّة** (النصّ المُشتقّ من القيم)، وما لم يطابق **يُسترجَع** ويُقال `failed` بسببه. والتحرير
 * يُنادى في كل مسار مغادرة، وإلّا بقي أثرُنا على صوت المستخدم.
 *
 * **وهذا هو الفرق الذي جعل نقل DolbyUI هنا نقلًا لا نسخًا:** عندهم `setParameter` ثمّ **انتهى**
 * (`DolbyConstants.dlog` وحده شاهدًا)؛ وعندنا **لا كتابة بلا قراءة مطابقة ولا استرجاع**.
 *
 * **وما لا يُعرض هنا — وسببه مقيس:** `resetProfileSpecificSettings` **لا يُبنى مقبضًا**، لأنّ أثره
 * (تفريغ إعدادات الملفّ الشخصيّ) **لا تقرؤه واجهة**: القراءة المتاحة تقرأ المعاملات، ولا تقرأ «هل
 * أُعيد الضبط». ومقبضٌ لا يُقرأ يعني `applied` بلا دليل — وهو مرفوض عندنا. فالمسار مسجَّل ومؤجَّل،
 * لا منسيّ.
 */
package nd.max.core.audio

import nd.max.core.hardware.ControlOwnership
import nd.max.core.hardware.HardwareControlArbiter
import nd.max.core.hardware.HardwareControlKey
import nd.max.core.hardware.SharedHardwareOwnershipStore
import javax.inject.Inject
import javax.inject.Singleton

/** نتيجة الفتح — **الجلسة أو السبب، ولا ثالث**. */
data class VendorAudioOpen(
    val session: VendorEffectSession?,
    val reason: String? = null,
)

@Singleton
class VendorAudioBackend @Inject constructor(
    private val arbiter: HardwareControlArbiter,
) {

    // ───────────────────────────────── الفتح والإغلاق ─────────────────────────────────

    /**
     * يفتح مؤثّر المصنّع بمعرّف تنفيذه — **ولا يرمي أبدًا**: كل مسار فاشل يعود بسبب مكتوب من المحوّل.
     *
     * والفرق بين هذه وبين `probeAttach` في [`VendorAudioDiscovery`]: تلك تُحرّر فورًا لتقيس المسار،
     * وهذه **تُبقي** المقبض ليُكتب فيه — فمن استدعاها **يجب** أن يستدعي [`close`].
     */
    fun open(effectUuid: String, session: Int = VendorAudioAdapter.GLOBAL_SESSION): VendorAudioOpen {
        if (!SharedHardwareOwnershipStore.isConfigured()) {
            return VendorAudioOpen(null, AudioEffectReason.STORE_UNCONFIGURED)
        }
        val attempt = VendorAudioAdapter.attach(effectUuid, session)
        val handle = attempt.handle ?: return VendorAudioOpen(null, attempt.evidence.reason)
        return VendorAudioOpen(VendorEffectSession(effectUuid.trim().lowercase(), handle))
    }

    /** يُحرّر المؤثّر — ويُنادى في كل مسار مغادرة، والتحرير مأمون التكرار. */
    fun close(session: VendorEffectSession) {
        session.release()
    }

    // ─────────────────────────────────────── القراءة ───────────────────────────────────────

    /** قراءة معامل صحيح مفرد (التمكين · الملفّ الشخصيّ) — و`null` لا تعني صفرًا. */
    fun readIntParam(session: VendorEffectSession, param: Int): Int? =
        VendorAudioAdapter.readIntParam(session.handle, param)

    /** قراءة معامل ملفّ شخصيّ — بعدد قيمه المُعلَن، وبلا اختراع أصفار لقيم لم تُكتب. */
    fun readDapValues(session: VendorEffectSession, param: DolbyDapParam, profile: Int): IntArray? =
        VendorAudioAdapter.readDapValues(session.handle, param, profile)

    /** حالة التمكين كما تقولها المنصّة — تُقرأ للعرض ولا تُخزَّن. */
    fun enabled(session: VendorEffectSession): Boolean? = session.enabled

    // ────────────────────────────────────── الكتابات ──────────────────────────────────────

    /**
     * تمكين/تعطيل **المؤثّر نفسه** (`AudioEffect.setEnabled`) — وهي غير تمكين معالج Dolby (`dap_enable`).
     * والاثنان مقبضان منفصلان عندنا لأنّهما كذلك في المنصّة، وخلطُهما يجعل «مطفأ» تعني شيئين.
     */
    fun setEffectEnabled(session: VendorEffectSession, enabled: Boolean, auditToken: String): AudioKnobVerdict {
        val desired = if (enabled) "1" else "0"
        return knob(
            session = session,
            paramKey = KEY_EFFECT_ENABLED,
            desired = desired,
            auditToken = auditToken,
            apply = { session.handle.applyEnabled(it == "1") },
            read = { session.enabled?.let { value -> if (value) "1" else "0" } },
        )
    }

    /** تمكين **معالج Dolby** (`EFFECT_PARAM_ENABLE`) — يُقرأ بعده من المادّة. */
    fun writeDapEnabled(session: VendorEffectSession, enabled: Boolean, auditToken: String): AudioKnobVerdict {
        val desired = if (enabled) "1" else "0"
        return knob(
            session = session,
            paramKey = KEY_DAP_ENABLE,
            desired = desired,
            auditToken = auditToken,
            apply = { value ->
                VendorAudioAdapter.writeIntParam(
                    session.handle, DolbyDapProtocol.ENABLE_PARAM, value.toIntOrNull() ?: 0,
                )
            },
            read = { readIntParam(session, DolbyDapProtocol.ENABLE_PARAM)?.toString() },
        )
    }

    /** اختيار الملفّ الشخصيّ (`EFFECT_PARAM_PROFILE`) — يُقرأ بعده. */
    fun writeProfile(session: VendorEffectSession, profile: Int, auditToken: String): AudioKnobVerdict {
        val desired = profile.toString()
        return knob(
            session = session,
            paramKey = KEY_DAP_PROFILE,
            desired = desired,
            auditToken = auditToken,
            apply = { value ->
                VendorAudioAdapter.writeIntParam(
                    session.handle, DolbyDapProtocol.PROFILE_PARAM, value.toIntOrNull() ?: 0,
                )
            },
            read = { readIntParam(session, DolbyDapProtocol.PROFILE_PARAM)?.toString() },
        )
    }

    /**
     * يكتب معامل ملفّ شخصيّ (فيزيائيّ · عدّاد · عشرون نطاقًا معادلًا) — **ويقرأه بعده**.
     *
     * والقيم تُطلب **بطولها المُعلَن بالضبط** (وإلا رُفض البناء في الطبقة الصافية بسبب)، والمقارنة
     * حرفيّة على النصّ المُشتقّ: ما قرأته المادّة هو ما يُعرض، ولو قيّدته.
     */
    fun writeDapValues(
        session: VendorEffectSession,
        param: DolbyDapParam,
        values: IntArray,
        profile: Int,
        auditToken: String,
    ): AudioKnobVerdict {
        val block = DolbyDapProtocol.profileParameterBlock(param, values, profile)
        if (block == null) {
            return audioKnobNotAttempted(
                reason = VendorAudioReason.PARAM_LENGTH_MISMATCH,
                expected = joinValues(values),
            )
        }
        val desired = joinValues(values)
        return knob(
            session = session,
            paramKey = KEY_DAP_PREFIX + param.name.lowercase() + ":" + profile,
            desired = desired,
            auditToken = auditToken,
            apply = { text ->
                val parsed = parseValues(text, param.length) ?: return@knob false
                VendorAudioAdapter.writeDapValues(session.handle, param, parsed, profile)
            },
            read = { readDapValues(session, param, profile)?.let(::joinValues) },
        )
    }

    // ────────────────────────────────────────── الأدوات ──────────────────────────────────────────

    /**
     * الطريق الواحد للكتابة: حراسة الملكيّة ← حراسة المخزن ← المحكِّم ← حكم.
     * والترتيب مقصود كما في محرّك المنصّة: الملكيّة والمخزن قبل المحكِّم، فلا يُسجَّل طلبٌ لا أمل فيه.
     */
    private fun knob(
        session: VendorEffectSession,
        paramKey: String,
        desired: String,
        auditToken: String,
        apply: (String) -> Boolean,
        read: () -> String?,
    ): AudioKnobVerdict {
        val live = runCatching { read() }.getOrNull()
        if (session.hasControl != true) {
            return audioKnobNotAttempted(AudioEffectReason.CONTROL_NOT_OWNED, desired, live)
        }
        if (!SharedHardwareOwnershipStore.isConfigured()) {
            return audioKnobNotAttempted(AudioEffectReason.STORE_UNCONFIGURED, desired, live)
        }
        val key = HardwareControlKey.audioVendor(session.effectUuid, paramKey)
        val result = runCatching {
            arbiter.submit(
                key = key,
                owner = ControlOwnership.Owner.GLOBAL_PROFILE,
                token = auditToken,
                desired = desired,
                apply = apply,
                read = read,
                // والاسترجاع هو الكتابة نفسها بالقيمة القديمة — والمحكِّم يقرأ خط الأساس بنفسه.
                restore = apply,
            )
        }.getOrNull() ?: return audioKnobNotAttempted(AudioEffectReason.ARBITER_UNAVAILABLE, desired, live)

        return audioKnobVerdict(
            attempted = true,
            blocked = result.blocked,
            applied = result.applied,
            verified = result.verified,
            expected = desired,
            actual = result.actual,
            error = result.error,
        )
    }

    /** صياغة القيم نصًّا **موحَّدة بين الكتابة والقراءة** — وهذا شرط الحكم الحرفيّ. */
    private fun joinValues(values: IntArray): String = values.joinToString(",")

    /** تحليل النصّ إلى قيم بالطول المتوقّع بالضبط — ونقصٌ أو زيادة يُرفض ولا يُكمَّل بأصفار. */
    private fun parseValues(text: String, expected: Int): IntArray? {
        val parts = text.split(',')
        if (parts.size != expected) return null
        val values = IntArray(expected)
        parts.forEachIndexed { index, part ->
            values[index] = part.trim().toIntOrNull() ?: return null
        }
        return values
    }

    private companion object {
        const val KEY_EFFECT_ENABLED = "effect_enabled"
        const val KEY_DAP_ENABLE = "dap_enable"
        const val KEY_DAP_PROFILE = "dap_profile"
        const val KEY_DAP_PREFIX = "dap:"
    }
}
