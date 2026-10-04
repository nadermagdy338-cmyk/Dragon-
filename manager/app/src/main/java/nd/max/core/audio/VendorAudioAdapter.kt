/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * الصوت — **المحوّل إلى مؤثّر المصنّع** (تكملة ٢٣٠): المسار الوحيد الذي يجرّب اللمس لا الرؤية.
 *
 * **ولماذا انعكاس (reflection) لا نداء مباشر — وهذا قياسٌ لا اختيار:** الـ`android.jar` الذي
 * يُصرَّف عليه المشروع **لا يحمل** ما يلزم للوصول إلى مؤثّر بمعرّفه:
 *
 * ```
 * $ javap -classpath android.jar android.media.audiofx.AudioEffect
 *   android.media.audiofx.AudioEffect();                ← لا مُنشئ عامًّا بـ(UUID,UUID,int,int)
 *   ... setControlStatusListener · setEnableStatusListener · setEnabled ...
 *   (ولا setParameter ولا getParameter ولا EFFECT_TYPE_NULL في السطح العامّ)
 * ```
 *
 * ومقابل ذلك، في مصدر AOSP نفسه (`frameworks/base`، `media/java/android/media/audiofx/AudioEffect.java`):
 * `public AudioEffect(UUID,UUID,int,int)` · `public int setParameter(int,byte[])` ·
 * `public int getParameter(int,byte[])` · `public static final UUID EFFECT_TYPE_NULL` — **كلّها `@hide`**.
 *
 * ⇒ فلا سبيل عامًّا لبناء مؤثّر المصنّع ولا لقراءة معاملاته؛ والسبيل الوحيد بلا امتياز نظاميّ هو
 * **الانعكاس على الأعضاء المخفيّة**، وهو **يُحظر أو يُسمح بحسب نسخة النظام (hidden API policy)** —
 * ولهذا **لا يُوعد به**، بل **يُجرَّب ويُقال ناتجه**: نجح ⇒ `ATTACHED`، مُنع ⇒ `REFUSED` بسبب
 * `hidden-api-blocked-or-absent`. وهذا هو الفرق بين محوّلٍ صادق وميزةٍ مُدّعاة.
 *
 * **وحراسة الملفّ كلّه:** كل نداء انعكاس داخل `runCatching` · لا كائن يبقى حيًّا في هذا الملفّ
 * (الحيّ يُسلَّم للـ[`VendorAudioBackend`] وحده) · ولا نصّ استثناء يُنقل إلى الشاشة.
 */
package nd.max.core.audio

import android.media.audiofx.AudioEffect
import java.lang.reflect.Method
import java.util.UUID

/**
 * مقبض مؤثّر مصنّع مفتوح.
 *
 * و`hasControl`/`enabled` **يُقاسان بعد الإنشاء ولا يُفترضان** — ومؤثّرٌ أنشأناه ولا نملكه يعني أنّ
 * كتابتنا ستُرفض، فذلك حكمٌ يُقال لا خطأ يُخفى.
 */
internal class VendorEffectHandle(
    internal val effect: AudioEffect,
    private val setParameter: Method,
    private val getParameter: Method,
) {
    /** ثلاثيّات كما في كل المستودع: `null` = لم يُقس. */
    val hasControl: Boolean? get() = runCatching { effect.hasControl() }.getOrNull()

    val enabled: Boolean? get() = runCatching { effect.enabled }.getOrNull()

    val id: Int get() = runCatching { effect.id }.getOrDefault(-1)

    /** تمكين/تعطيل المؤثّر نفسه (لا معامل DAP) — ويُقرأ بعده من المنصّة. */
    internal fun applyEnabled(value: Boolean): Boolean =
        runCatching { effect.setEnabled(value) == AudioEffect.SUCCESS }.getOrDefault(false)

    /** يرسل معاملًا صحيحًا: `setParameter(int, byte[])` المخفيّة. */
    internal fun setIntParam(param: Int, block: ByteArray): Boolean =
        runCatching { setParameter.invoke(effect, param, block) == AudioEffect.SUCCESS }.getOrDefault(false)

    /**
     * يقرأ معاملًا صحيحًا: `getParameter(int, byte[])` **تملأ الصندوق** بنفسها، فالصندوق مُدخَلٌ
     * ومخرَجٌ في الوقت نفسه — ولو لم تُحسب على هذا لقرأنا أصفارًا نظنّها قيمًا.
     */
    internal fun getIntParam(param: Int, block: ByteArray): Boolean =
        runCatching { getParameter.invoke(effect, param, block) == AudioEffect.SUCCESS }.getOrDefault(false)

    internal fun release() {
        runCatching { effect.release() }
    }
}

/** نتيجة محاولة الإرفاق: **إمّا مقبض حيّ، وإمّا سببٌ مكتوب** — ولا حال ثالثة. */
internal data class VendorAttachAttempt(
    val handle: VendorEffectHandle?,
    val evidence: VendorAttachEvidence,
)

/**
 * المحوّل: مسار واحد معلن (`HIDDEN_API`) ونتيجة مقيسة.
 *
 * **ولا يتذكّر شيئًا:** لا مخزن ثابت ولا كائن حيّ — من يريد إبقاء المؤثّر يستدعي `attach` ويحمل
 * المقبض بنفسه، ويُحرّره في النهاية. فلو نسي، لم يبق في هذا الملفّ أثرٌ يغيّر صوت المستخدم.
 */
internal object VendorAudioAdapter {

    /** اسم المُنشئ وأسمار الأعضاء — كما قُرئت من المصدر، موضعٌ واحد فلا تتفرّق. */
    private const val EFFECT_CLASS = "android.media.audiofx.AudioEffect"
    private const val METHOD_SET_PARAMETER = "setParameter"
    private const val METHOD_GET_PARAMETER = "getParameter"

    /** جلسة المزج العامّ — الرقم من المنصّة بالاسم لا سحرًا في موضعين. */
    const val GLOBAL_SESSION = 0

    /**
     * يجرّب الإرفاق بمعرّف التنفيذ — **ولا يرمي أبدًا**.
     *
     * ويُرجع `ATTACHED` بثلاثيّ تحكّمٍ مقيس: `true` نملكه، `false` يملكه غيرنا، `null` عجزنا عن سؤاله.
     * و`REFUSED` بسببه: معرّف غير صالح · منشئ مخفيّ غير متاح · إنشاءٌ رمى · أو أعضاء المعاملات غير
     * موجودة في هذه النسخة.
     */
    fun attach(uuidText: String, session: Int = GLOBAL_SESSION): VendorAttachAttempt {
        val uuid = runCatching { UUID.fromString(uuidText.trim()) }.getOrNull()
            ?: return refused(VendorAudioReason.INVALID_UUID)

        val typeNull = runCatching { UUID.fromString(VendorEffectCatalog.EFFECT_TYPE_NULL_UUID) }.getOrNull()
            ?: return refused(VendorAudioReason.INVALID_UUID)

        val members = runCatching { members() }.getOrNull()
            ?: return refused(VendorAudioReason.HIDDEN_API_BLOCKED)

        val effect = runCatching {
            members.constructor.newInstance(typeNull, uuid, 0, session) as AudioEffect
        }.getOrNull() ?: return refused(VendorAudioReason.HIDDEN_API_BLOCKED)

        val handle = VendorEffectHandle(effect, members.setParameter, members.getParameter)
        return VendorAttachAttempt(
            handle = handle,
            evidence = VendorAttachEvidence(
                route = VendorAccessRoute.HIDDEN_API,
                outcome = VendorAttachOutcome.ATTACHED,
                // **والتحكّم يُقاس فورًا**: مؤثّرٌ أُنشئ ولا `hasControl()` له لا يُكتب فيه.
                controlOwned = handle.hasControl,
            ),
        )
    }

    // ─────────────── البروتوكول: القراءة والكتابة على المقبض (والبروتوكول نفسه صافٍ) ───────────────

    /** حجم صندوق المعامل المفرد — ١٢ بايت كما في المصدر. */
    private const val INT_PARAM_BLOCK_BYTES = 12

    /**
     * يقرأ معاملًا صحيحًا مفردًا (**التمكين · الملفّ الشخصيّ**) — و`null` تعني «لم يُقرأ» لا صفرًا.
     *
     * **ومعرّف الطلب من المصدر:** `getIntParam(param)` تسأل عن `CPDP_VALUES + param` — والجمع هنا
     * مكتوبًا في موضعٍ واحد، فلا يُنسخ الرقم في موضعين.
     */
    fun readIntParam(handle: VendorEffectHandle, param: Int): Int? {
        val buffer = ByteArray(INT_PARAM_BLOCK_BYTES)
        if (!handle.getIntParam(DolbyDapProtocol.CPDP_VALUES + param, buffer)) return null
        return DolbyDapProtocol.intParamValue(buffer)
    }

    /** يكتب معاملًا صحيحًا مفردًا — والصندوق يُبنى في الطبقة الصافية لا هنا. */
    fun writeIntParam(handle: VendorEffectHandle, param: Int, value: Int): Boolean =
        handle.setIntParam(DolbyDapProtocol.CPDP_VALUES, DolbyDapProtocol.intParamBlock(param, value))

    /** يقرأ معامل ملفّ شخصيّ بعدد قيمه المُعلَن — وبلا اختراع أصفار لقيم لم تُكتب. */
    fun readDapValues(handle: VendorEffectHandle, param: DolbyDapParam, profile: Int): IntArray? {
        val requestId = DolbyDapProtocol.profileParameterRequestId(param, profile) ?: return null
        val buffer = DolbyDapProtocol.profileParameterBuffer(param)
        if (!handle.getIntParam(requestId, buffer)) return null
        return DolbyDapProtocol.profileParameterValues(buffer, param)
    }

    /** يكتب معامل ملفّ شخصيّ — **والقيم بطولها المُعلَن بالضبط** وإلا رُفض البناء في الطبقة الصافية. */
    fun writeDapValues(
        handle: VendorEffectHandle,
        param: DolbyDapParam,
        values: IntArray,
        profile: Int,
    ): Boolean {
        val block = DolbyDapProtocol.profileParameterBlock(param, values, profile) ?: return false
        return handle.setIntParam(DolbyDapProtocol.CPDP_VALUES, block)
    }

    /** يُصوغ رفضًا بمسار الانعكاس — فلا رفض بلا مسار مكتوب. */
    private fun refused(reason: String) = VendorAttachAttempt(
        handle = null,
        evidence = VendorAttachEvidence(
            route = VendorAccessRoute.HIDDEN_API,
            outcome = VendorAttachOutcome.REFUSED,
            controlOwned = null,
            reason = reason,
        ),
    )

    /** أعضاء الصنف المخفيّة التي نحتاجها — تُبحَث مرّة عند كل محاولة (لا تُخزَّن حالة). */
    private class Members(
        val constructor: java.lang.reflect.Constructor<*>,
        val setParameter: Method,
        val getParameter: Method,
    )

    private fun members(): Members {
        val effectClass = Class.forName(EFFECT_CLASS)
        val constructor = effectClass.getDeclaredConstructor(
            UUID::class.java,
            UUID::class.java,
            Integer.TYPE,
            Integer.TYPE,
        ).apply { isAccessible = true }
        val intType = Integer.TYPE
        return Members(
            constructor = constructor,
            setParameter = effectClass.getDeclaredMethod(METHOD_SET_PARAMETER, intType, ByteArray::class.java)
                .apply { isAccessible = true },
            getParameter = effectClass.getDeclaredMethod(METHOD_GET_PARAMETER, intType, ByteArray::class.java)
                .apply { isAccessible = true },
        )
    }
}
