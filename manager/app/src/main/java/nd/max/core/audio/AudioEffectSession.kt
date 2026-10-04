/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * الصوت — **جلسة مؤثّر مفتوحة** (`AQ-02`): الملفّ الوحيد الذي **يُبقي** `AudioEffect` حيًّا بعد إنشائه.
 *
 * **ولماذا ملفّ منفصل عن المحرّك:** الفصل هنا هو شرط القبول نفسه. المسبار (`AudioCapabilityProbe`)
 * يُنشئ ويُحرّر في نفس اللحظة، فحكمه «هل تُقبل الجلسة ٠؟». أمّا هذه فتحمل المؤثّر **بين نداءين**
 * (كتابةٌ ثمّ قراءة، ثمّ كتابةٌ أخرى) — ولذلك وُجد `release()` صريحًا، ووُجد معه تسجيل ملكيّة التحكّم:
 * مؤثّرٌ أُنشئ ولا `hasControl()` له **لا نملك الكتابة فيه**، فالكتابة عليه تُمنع بسببٍ مكتوب لا بفشل
 * مبهم (وهذا نصّ معيار `AQ-02`: «`hasControl=false` ⇒ مملوك لتطبيق آخر لا فشل»).
 *
 * **ولا شيء هنا يحكم ولا يعرض:** لا نصوص ولا ألوان ولا `Composable`. حالةٌ للقراءة، وعمليتان
 * (تمكين/تحرير)، والبقية في `AudioEffectBackend`.
 */
package nd.max.core.audio

import android.media.audiofx.AudioEffect

/**
 * مؤثّر مفتوح على الجلسة العامة، ومعه رمز نوعه.
 *
 * @param kind النوع الذي طُلب — ويُحمل هنا لأنّ المُنشئين مختلفون (بعضهم بلا وسيط أولويّة) ولا
 *   يُستدلّ على النوع من المؤثّر نفسه بلا مرآة.
 * @param effect المؤثّر الحيّ — `internal` فلا يُلمس من خارج `core/audio` (ADR-11: لا كتابة من `ui/`).
 */
class AudioEffectSession internal constructor(
    val kind: AudioEffectKind,
    internal val effect: AudioEffect,
) {

    /**
     * **حالة التحكّم المُستمع إليها** — تُكتب من خيط المنصّة وتُقرأ من خيوطنا، فتُحمل في `volatile`
     * مُبدَّلٍ ذرّيًّا (لا `var` عاريةً تُقرأ نصفها مكتوبةً).
     *
     * والفرق بينها وبين [`hasControl`] **مقصودٌ لا تكرار**: `hasControl()` استطلاعٌ في لحظة الكتابة
     * (مرجعيّتها كاملة هناك)، وهذه **ما أُعلن بين الكتابتين** — وهي التي تكشف «الطبقة ثُبّتت ثمّ أخذها
     * غيرنا» بلا أن يلمس المستخدم مقبضًا. وكلٌّ يُستعمل في موضعه.
     */
    @Volatile
    var control: AudioEffectControlState = AudioEffectControlState()
        private set

    init {
        // **والاستماع لا يفشل الإرفاق:** مستمعٌ لا يُقبل (`IllegalStateException` عند مؤثّر مُحرَّر)
        // لا يُسقط الجلسة — تُبقى `controlled = null` أي «لم تُبلَّغ»، ولا يُدّعى ملكٌ ولا نفي.
        runCatching {
            effect.setControlStatusListener { _, hasControl ->
                control = control.onControlStatus(hasControl)
            }
        }
        runCatching {
            effect.setEnableStatusListener { _, enabled ->
                control = control.onEnableStatus(enabled)
            }
        }
    }

    /**
     * هل التحكّم بأيدينا؟ — **ثلاثيّات لا ثنائيّات**: `null` تعني «لم يُسأل»، و`false` نفيٌ مقيس.
     * ولا تعود `true` افتراضًا عند استثناء: كتابةٌ مبنية على افتراضٍ ليست كتابة.
     */
    // و`hasControl()` **دالّة لا خاصيّة**: Kotlin يُولّد خاصيّة من `getX`/`isX` وحدهما — و`hasX`
    // خارج القاعدتين، فتُنادى بقوسين. (وهذا عطبٌ لا يمسكه إلا المُصرّف، وقد أمسكته هذه الجولة بقياس
    // طبقة الأوديو وحدها، إذ كان مُثبَّطًا في القياس الكامل تحت سقف الأخطاء.
    val hasControl: Boolean?
        get() = runCatching { effect.hasControl() }.getOrNull()

    /** حالة التمكين كما تقولها المنصّة — و`null` حين لا تُقرأ. */
    val enabled: Boolean?
        get() = runCatching { effect.enabled }.getOrNull()

    /** معرّف المؤثّر — يُسجَّل في التدقيق، ولا يُعرض للمستخدم. */
    val id: Int
        get() = runCatching { effect.id }.getOrDefault(-1)

    /**
     * تمكين/تعطيل المؤثّر — **ويُقرأ بعدها من المنصّة لا من الطلب** (قاعدة المستودع: لا `applied`
     * بلا قراءة مطابقة). و`setEnabled` تُعيد رمزًا لا `Unit`، فنُصفّي الرمز إلى `Boolean` هنا.
     */
    internal fun applyEnabled(value: Boolean): Boolean =
        runCatching { effect.setEnabled(value) == AudioEffect.SUCCESS }.getOrDefault(false)

    /** تحرير المؤثّر — **الشرط الذي بدونه يبقى أثرُنا على صوت المستخدم بعد مغادرة الشاشة**. */
    fun release() {
        runCatching { effect.release() }
    }
}
