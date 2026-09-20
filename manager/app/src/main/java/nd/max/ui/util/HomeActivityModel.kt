/*
 * Copyright (C) 2026-2027 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * **بطاقة «ما يحدث الآن؟»** — إسقاط حالة النظام القائمة إلى حالة واحدة تُعرض.
 *
 * هذه الطبقة **لا تقيس شيئًا ولا تحفظ شيئًا**: تأخذ إشارات قائمة فعلًا (حالة المحرك،
 * والسلامة، وآخر حلقة قرار، والدورة الجارية، والتطبيق في المقدمة، ونقص القراءات) وتُخرج
 * سطرًا واحدًا صادقًا. فلا نظام بيانات ثانٍ، ولا تخزين موازٍ، ولا مصدر حقيقة مكرَّر.
 *
 * **والترتيب ليس ذوقًا** — هذه أسبقية تحكمها القاعدة: ما هو **خطر الآن** يسبق ما هو
 * **جارٍ الآن**، والذي يسبق ما **وقع قبل لحظات**، والذي يسبق **الحالة الأساسية**:
 *
 * | # | الحالة | متى | طابعها |
 * | --- | --- | --- | --- |
 * | ١ | `SAFETY` | تدخّل الأمان قائم (مستوى غير عادي أو مفعَّل) | خطر · ثابت |
 * | ٢ | `APPLYING` | طلب ملف يدوي قيد التنفيذ | عمل · ثابت |
 * | ٣ | حدث حالٍّ (تحقّق/تراجع/رفض/فتح تطبيق) داخل نافذة [EVENT_TTL_MS] | | عابر |
 * | ٤ | `UNSUPPORTED` | قراءات حيّة مفقودة أصلًا على هذا الجهاز | انتباه · ثابت |
 * | ٥ | `MONITORING` / `IDLE` | لا شيء مما سبق | هدوء · ثابت |
 *
 * وثلاثة قرارات صياغة تحكم الصدق، لا الشكل:
 *
 * 1. **«تم التطبيق» لا تُقال إلا لحكم يقول إن العتاد أكّده**، والفروق محفوظة كما هي:
 *    `IMPROVED` = كُتب ثم قيس أثره ⇒ `VERIFIED` مع `measured = true`؛
 *    `UNMEASURED` = **ثبتت الكتابة وتعذّر قياس الأثر** ⇒ `VERIFIED` مع `measured = false`
 *    (فالنصّ يقول «طُبِّق · الأثر لم يُقس» لا «تحسّن»)؛
 *    `WRITE_FAILED` = **لم تثبت الكتابة** ⇒ `REFUSED`.
 * 2. **الاسترجاع حالة مستقلة عن الفشل**: `REGRESSED_ROLLED_BACK` تعني أن النظام أصلح نفسه
 *    (طابع هادئ)، و`REGRESSED_STUCK` تعني أن التراجع وقع وتعذّر إثبات الاسترجاع (انتباه).
 *    وكلتاهما `ROLLED_BACK` لأن ما حدث واحد — والفارق في الطابع والنصّ المقيس من المحرك.
 * 3. **الحدث عابر والحالة ثابتة**: حدثٌ مضت نافذته يعود بالبطاقة إلى حالتها الأساسية،
 *    ولا يبقى وعدًا بحدث مضى (نفس قاعدة `CadenceStatus`).
 */
package nd.max.ui.util

import nd.max.core.maxai.DecisionResult
import nd.max.core.maxai.MaxAiVerdict
import nd.max.core.maxai.SafetyLevel

/** الحالة التي تُعرض الآن — إحدى هذه فقط في كل لحظة. */
enum class ActivityState {
    /** لا تحكّم آلي، ولا عطل: البطاقة تعرض قياسات الجهاز وحدها. */
    IDLE,

    /** المحرك يعمل ويراقب، بلا إجراء هذه اللحظة. */
    MONITORING,

    /** كتابة/تطبيق قيد التنفيذ — لا يُدّعى بعدُ أنها نجحت. */
    APPLYING,

    /** العتاد أكّد الطلب (وأثره مقيس أو غير مقيس — يفرّقه [HomeActivity.measured]). */
    VERIFIED,

    /** تراجع مقيس، فاسترجع النظام خط الأساس (أو تعذّر إثباته — الطابع يفرّق). */
    ROLLED_BACK,

    /** لم تثبت الكتابة على العتاد، أو كل المرشحين مستبعدون. */
    REFUSED,

    /** قراءات لازمة غير متاحة على هذا الجهاز — لا رقم مصطنع مكانها. */
    UNSUPPORTED,

    /** حماية الجهاز قائمة الآن. */
    SAFETY,

    /** تطبيق دخل المقدمة قبل لحظات. */
    APP_SWITCH,
}

/** نبرة العرض — تعبير عن الثقة والاستعجال، لا عن نوع الحدث. */
enum class ActivityTone { CALM, WORKING, ATTENTION, DANGER }

/** قراءة حيّة يحتاجها العرض ولا يوفرها هذا الجهاز. */
enum class ActivityReading { GPU, THERMAL, CORES }

/**
 * لقطة إشارات قائمة — تُبنى من مصادر المشروع نفسها (`MaxAiEngine.state` و`safety` و
 * `episodes` و`profileRequest`) ولا تُخزَّن.
 *
 * والأسماء هنا **لا تُشتق بأي حال**: `latest*` صفر/null تعني «لم تُقرأ»، وليست «لا شيء حدث».
 */
data class ActivitySignals(
    val aiEnabled: Boolean = false,
    /** طلب ملف يدوي قيد التنفيذ (`ProfileRequestState.inFlight`). */
    val profileRequestInFlight: Boolean = false,
    val safetyLevel: SafetyLevel = SafetyLevel.NORMAL,
    /** تدخّل الأمان قائم (`SafetyStatus.engaged`). */
    val safetyEngaged: Boolean = false,
    /** سبب آخر تدخّل أمان، بنصّه المقيس. */
    val safetyReason: String? = null,
    /**
     * حكم آخر حلقة قرار من **اليومية المقيسة** (`MaxAiVerdict`).
     *
     * وهو المصدر الأول للصياغة لأنّ `DecisionResult` وحدَه لا يكفي: `FAILED` عند المحرك
     * تعني **شيئين مختلفين** — «لم تثبت الكتابة» و«ثبتت الكتابة وتعذّر قياس الأثر»
     * (السطر ٦٥٥ من `MaxAiEngine` مقابل ٥٨١) — وعرض الثانية بعنوان فشل اتهام ظالم،
     * وعرض الأولى بعنوان نجاح ادّعاء كاذب.
     */
    val lastVerdict: MaxAiVerdict? = null,
    /**
     * ملخّص آخر قرار (`DecisionRecord.result`) — يُستعمل **وحده** حين لا حلقة مسجَّلة
     * (كطلب ملف يدوي)، فيبقى الحدث معروضًا بملخَّص المحرك بدل أن يُسقط.
     */
    val lastResult: DecisionResult? = null,
    val lastKnobLabel: String? = null,
    /** القيمة التي استقرّ عليها المقبض بعد التطبيق، أو المطلوبة إن لم تُقرأ. */
    val lastValue: String? = null,
    /** نصّ المحرك نفسه (حقيقة الآلة كما هي) — يُعرض بلا إعادة صياغة. */
    val lastDetail: String? = null,
    val lastAtMs: Long = 0L,
    /** كانت الحلقة تجربة معرفية لا تحسينًا مطلوبًا. */
    val lastExploration: Boolean = false,
    /** بدأ تطبيق التطبيق الحالي في هذا الزمن (0 = لا تطبيق معروف). */
    val appContext: String? = null,
    val appSinceMs: Long = 0L,
    /** قراءات لازمة مفقودة على هذا الجهاز — قائمة صريحة، لا استنتاج من أصفار. */
    val missingReadings: Set<ActivityReading> = emptySet(),
)

/**
 * ما يجب عرضه الآن.
 *
 * [transient] يعني أن هذه حالة حدث: [expiresAtMs] موعد عودتها تلقائيًّا إلى الحالة الأساسية.
 * و[measured] فرقٌ لا يُطوى: `true` = الأثر قيس، `false` = الكتابة ثبتت والأثر لم يُقس.
 */
data class HomeActivity(
    val state: ActivityState,
    val tone: ActivityTone,
    val transient: Boolean,
    val expiresAtMs: Long,
    val measured: Boolean = false,
    /**
     * الحلقة كانت **تجربة معرفية** لا تحسينًا مطلوبًا: الجهاز كان محققًا لهدفه، والقياس جرى
     * لأن أثر المقبض مجهول. عرضها بعنوان «تحسين» كان سيُوهم المستخدم بتغيير لم يطلبه.
     */
    val exploration: Boolean = false,
    val knobLabel: String? = null,
    val value: String? = null,
    val detail: String? = null,
    val appPackage: String? = null,
    val missingReadings: Set<ActivityReading> = emptySet(),
)

object HomeActivityModel {

    /**
     * نافذة الحدث: كم يبقى الحدث معروضًا قبل أن تعود البطاقة إلى حالتها الأساسية.
     * ثماني ثوانٍ = ما يكفي ليُقرأ سطران ثم يعود العرض هادئًا (لا بطاقة «تتنقّل» كل ثانية).
     */
    const val EVENT_TTL_MS: Long = 8_000L

    fun project(signals: ActivitySignals, nowMs: Long): HomeActivity {
        // ١ — الأمان أولًا: خطر قائم يسبق أي خبر.
        if (signals.safetyEngaged || signals.safetyLevel != SafetyLevel.NORMAL) {
            return HomeActivity(
                state = ActivityState.SAFETY,
                tone = ActivityTone.DANGER,
                transient = false,
                expiresAtMs = 0L,
                detail = signals.safetyReason?.takeIf { it.isNotBlank() },
            )
        }

        // ٢ — ثم ما هو جارٍ الآن: لا يُدّعى نجاحه قبل أن يُكتب.
        if (signals.profileRequestInFlight) {
            return HomeActivity(
                state = ActivityState.APPLYING,
                tone = ActivityTone.WORKING,
                transient = false,
                expiresAtMs = 0L,
            )
        }

        // ٣ — ثم الأحداث الحالّة، بترتيب زمني: الأحدث يقود.
        val verdictAge = if (signals.lastAtMs > 0L) nowMs - signals.lastAtMs else Long.MAX_VALUE
        val appAge = if (signals.appSinceMs > 0L) nowMs - signals.appSinceMs else Long.MAX_VALUE
        // الحدث يحتاج **مصدرًا** (حكم حلقة أو ملخّص قرار) و**زمنًا** حديثًا؛ وبلا الزمن لا
        // يُعرض شيء: حكم بلا لحظة يُقرأ «الآن» أبدًا.
        val hasEventSource = signals.lastVerdict != null || signals.lastResult != null
        val verdictFresh = hasEventSource && signals.lastAtMs > 0L && verdictAge in 0 until EVENT_TTL_MS
        val appFresh = signals.appContext != null && appAge in 0 until EVENT_TTL_MS

        if (verdictFresh) {
            val activity = fromVerdict(signals, expiresAtMs = signals.lastAtMs + EVENT_TTL_MS)

            // وفتح تطبيق أحدث من الحلقة يسبقها: ما رآه المستخدم آخرًا هو «الآن».
            if (appFresh && signals.appSinceMs > signals.lastAtMs) {
                return appSwitch(signals, signals.appSinceMs + EVENT_TTL_MS)
            }
            return activity
        }
        if (appFresh) return appSwitch(signals, signals.appSinceMs + EVENT_TTL_MS)

        // ٤ — ثم نقص القدرة: صريح لا صامت.
        if (signals.missingReadings.isNotEmpty()) {
            return HomeActivity(
                state = ActivityState.UNSUPPORTED,
                tone = ActivityTone.ATTENTION,
                transient = false,
                expiresAtMs = 0L,
                missingReadings = signals.missingReadings,
            )
        }

        // ٥ — ثم الحالة الأساسية: مفيدة في الحالين.
        return HomeActivity(
            state = if (signals.aiEnabled) ActivityState.MONITORING else ActivityState.IDLE,
            tone = ActivityTone.CALM,
            transient = false,
            expiresAtMs = 0L,
        )
    }

    private fun appSwitch(signals: ActivitySignals, expiresAtMs: Long) = HomeActivity(
        state = ActivityState.APP_SWITCH,
        tone = ActivityTone.CALM,
        transient = true,
        expiresAtMs = expiresAtMs,
        appPackage = signals.appContext,
    )

    private fun fromVerdict(signals: ActivitySignals, expiresAtMs: Long): HomeActivity {
        val verdict = signals.lastVerdict
        val tone: ActivityTone
        val state: ActivityState
        var measured = false
        when (verdict) {
            // أثر مقيس بعد كتابة ثبتت — هذه الحالة الوحيدة التي تُقرأ «تحسّن».
            MaxAiVerdict.IMPROVED -> {
                state = ActivityState.VERIFIED
                // تجربة معرفية لا تُقرأ «تحسينا مطلوبًا»: الطابع يخفت معنىً لا شكلاً.
                tone = if (signals.lastExploration) ActivityTone.CALM else ActivityTone.WORKING
                measured = true
            }
            // الكتابة ثبتت والأثر لم يُقس: نجاح تطبيق بنصف معرفة — فلا يُسمى تحسّنًا.
            MaxAiVerdict.UNMEASURED -> {
                state = ActivityState.VERIFIED
                tone = ActivityTone.CALM
                measured = false
            }
            // لم تثبت الكتابة، أو لم يبقَ مرشح: النبرة تعتمد على وجود إجراء أصلًا.
            MaxAiVerdict.WRITE_FAILED -> {
                state = ActivityState.REFUSED
                tone = ActivityTone.ATTENTION
            }
            MaxAiVerdict.NO_ACTION -> {
                state = ActivityState.REFUSED
                tone = ActivityTone.CALM
            }
            // تراجع: إصلاح نجح (هادئ) أو استرجاع تعذّر إثباته (انتباه).
            MaxAiVerdict.REGRESSED_ROLLED_BACK -> {
                state = ActivityState.ROLLED_BACK
                tone = ActivityTone.CALM
            }
            MaxAiVerdict.REGRESSED_STUCK -> {
                state = ActivityState.ROLLED_BACK
                tone = ActivityTone.ATTENTION
            }
            // حجب الأمان يصل عادةً من `safety` أعلاه (المستوى صار غير عادي)؛ ووصوله هنا
            // وحده يعني حجبًا قبل الكتابة — وهو تدخّل أمان كذلك، لا عطب قدرة.
            MaxAiVerdict.BLOCKED_SAFETY -> {
                state = ActivityState.SAFETY
                tone = ActivityTone.ATTENTION
            }
            // لا حلقة مسجَّلة (مثل طلب ملف يدوي): ملخّص المحرك هو المتاح، ولا يُسقط الحدث.
            null -> {
                when (signals.lastResult) {
                    DecisionResult.VERIFIED -> {
                        state = ActivityState.VERIFIED
                        tone = ActivityTone.WORKING
                        measured = true
                    }
                    DecisionResult.EXECUTED -> {
                        state = ActivityState.VERIFIED
                        tone = ActivityTone.CALM
                    }
                    DecisionResult.ADJUSTED -> {
                        state = ActivityState.ROLLED_BACK
                        tone = ActivityTone.CALM
                    }
                    DecisionResult.BLOCKED_FOR_SAFETY -> {
                        state = ActivityState.SAFETY
                        tone = ActivityTone.ATTENTION
                    }
                    DecisionResult.SKIPPED -> {
                        state = ActivityState.REFUSED
                        tone = ActivityTone.CALM
                    }
                    DecisionResult.FAILED, null -> {
                        state = ActivityState.REFUSED
                        tone = ActivityTone.ATTENTION
                    }
                }
            }
        }
        return HomeActivity(
            state = state,
            tone = tone,
            transient = true,
            expiresAtMs = expiresAtMs,
            measured = measured,
            knobLabel = signals.lastKnobLabel,
            value = signals.lastValue,
            // النصّ المقيس من المحرك كما هو: إعادة صياغته هنا كانت ستضيف طبقة تُخفي السبب.
            detail = signals.lastDetail?.takeIf { it.isNotBlank() } ?: signals.lastKnobLabel,
            exploration = signals.lastExploration,
        )
    }
}
