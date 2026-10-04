/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * الصوت — **تحكّم مؤثّر MaxFx** (تكملة ٢٣٥): يقرأ خصائصه ويكتبها **عبر المحكِّم**، ويقرأ بعده.
 *
 * **وكل قاعدة المستودع ساريةً هنا بلا استثناء:** كل `apply` يقابله `read` من المنصّة نفسها،
 * والمقارنة حرفيّة على النصّ المُصاغ من العقد ([MaxFxModel.propValue])، وما لم يطابق يُسترجع
 * ويُقال `failed` بسببه. ولا كتابة من حزمة `ui` ولا مفتاح مبتكر — المفتاح من
 * [`HardwareControlKey.audioMaxFx`] وحده (`ADR-59`).
 *
 * **وقيمة خطّ الأساس للخاصية غير المكتوبة:** المحكِّم لا يبني خطّ أساس من غير قراءة
 * (`baseline-unreadable`)، والخاصية غير المكتوبة **ليست عطبًا** بل هي «المحرّك على افتراض
 * العقد» (`maxfx_config_default` في الطرف C — والغياب ليس صفرًا: ADR-07). فخطّ الأساس يُقرأ
 * **فراغًا** ويُسترجع فراغًا: كتابةُ فراغٍ تُعيدها إلى «غير مكتوبة» فعلًا، فيعود الافتراض.
 */
package nd.max.core.audio

import nd.max.core.hardware.ControlOwnership
import nd.max.core.hardware.HardwareControlArbiter
import nd.max.core.hardware.HardwareControlKey
import nd.max.core.hardware.SharedHardwareOwnershipStore
import nd.max.core.platform.PropertyUtils
import javax.inject.Inject
import javax.inject.Singleton

/** رموز أسباب تحكّم MaxFx — في موضع واحد مع رموز [`AudioEffectReason`] أختها. */
object MaxFxReason {
    /** المعامل ليس في العقد (`maxfx_params.tsv`) — فلا تُكتب خاصيةٌ لا يقرأها أحد. */
    const val PARAM_UNKNOWN = "param-not-in-contract"

    /** القيمة ليست عددًا سليمًا (NaN/∞) — رقمٌ فاسدٌ يقرأه `strtof` قيمةً في الطرف C. */
    const val VALUE_INVALID = "value-not-finite"
}

@Singleton
class MaxFxControlBackend @Inject constructor(
    private val arbiter: HardwareControlArbiter,
) {

    /**
     * قراءة كل معاملات العقد كما هي على الجهاز — **وغياب المفتاح يُسقَط لا يُملأ** (ADR-07):
     * الخاصية غير المكتوبة تعني «المحرّك على افتراضه»، والافتراض يُعرض من العقد لا من هنا.
     */
    fun readAll(): Map<String, String> = MaxFxModel.params.mapNotNull { param ->
        read(param.key)?.let { value -> param.key to value }
    }.toMap()

    /** قيمة خاصية معامل — و`null` عند الغياب، لا `"0"`. */
    fun read(key: String): String? {
        val prop = MaxFxModel.propKey(key) ?: return null
        return PropertyUtils.get(prop, "").takeIf { it.isNotEmpty() }
    }

    /**
     * يكتب معاملًا بقيمةٍ خامّة — **يقصّها العقد ويصيغها** ([MaxFxModel.propValue])، ثمّ يمرّ
     * بالمحكِّم بمفتاح [`HardwareControlKey.audioMaxFx`]، ثمّ **يقرأ بعده**: فالحكم على ما صار
     * لا على ما طُلب. والاسترجاع هو الكتابة نفسها بالقيمة القديمة (والفراغ = «غير مكتوبة»).
     *
     * والمالك [`ControlOwnership.Owner.GLOBAL_PROFILE`]: هذه إعداداتٌ عامّة للمستخدم، لا سلامةٌ
     * ولا عقل — فلا تتقدّم على غيرهما عند التزاحم.
     */
    fun write(key: String, raw: Double, auditToken: String): AudioKnobVerdict {
        val desired = MaxFxModel.propValue(key, raw)
        if (desired == null) {
            // والسبب يفرّق بين المجهولين: مفتاحٌ خارج العقد ≠ قيمةٌ فاسدة — والثاني ممكن من
            // منزلقٍ يُرسل رقمًا غير سليم، والأول من نداءٍ بنيويّ خاطئ.
            val reason = if (MaxFxModel.propKey(key) == null) {
                MaxFxReason.PARAM_UNKNOWN
            } else {
                MaxFxReason.VALUE_INVALID
            }
            return audioKnobNotAttempted(reason, expected = raw.toString(), actual = read(key))
        }
        val prop = MaxFxModel.propKey(key) ?: return audioKnobNotAttempted(MaxFxReason.PARAM_UNKNOWN, desired)
        val live = read(key)
        if (!SharedHardwareOwnershipStore.isConfigured()) {
            return audioKnobNotAttempted(AudioEffectReason.STORE_UNCONFIGURED, desired, live)
        }
        val result = runCatching {
            arbiter.submit(
                key = HardwareControlKey.audioMaxFx(key),
                owner = ControlOwnership.Owner.GLOBAL_PROFILE,
                token = auditToken,
                desired = desired,
                apply = { value -> PropertyUtils.setAndConfirm(prop, value) },
                // والقراءة **لا تُعيد null أبدًا**: الفراغ هو خطّ الأساس المعهود (انظر ترويسة الملفّ)،
                // و`null` هنا يعني «لا يمكن الحكم» فيرفض المحكِّم الكتابة كلّها.
                read = { PropertyUtils.get(prop, "") },
                restore = { value -> PropertyUtils.setAndConfirm(prop, value) },
            )
        }.getOrNull() ?: return audioKnobNotAttempted(AudioEffectReason.ARBITER_UNAVAILABLE, desired, live)

        return audioKnobVerdict(
            attempted = true,
            blocked = result.blocked,
            applied = result.applied,
            verified = result.verified,
            expected = desired,
            // والفراغ بعد القراءة غيابُ قراءة لا قيمة مقروءة — فلا يُعرض نصٌّ فارغ كأنه قياس.
            actual = result.actual?.takeIf { it.isNotEmpty() },
            error = result.error,
        )
    }
}
