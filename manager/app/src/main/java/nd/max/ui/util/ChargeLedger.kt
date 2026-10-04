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

package nd.max.ui.util
import nd.max.core.platform.EventLog

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import org.json.JSONArray
import org.json.JSONObject

/** جلسة شحن واحدة: كم ارتفعت النسبة، وكم استهلك العدّاد مقابل ذلك. */
data class ChargeSession(
    val atMs: Long,
    val levelFrom: Int,
    val levelTo: Int,
    val chargeCounterDeltaUah: Long,
) {
    /** نسبة صعود النسبة المئوية في هذه الجلسة. */
    val levelDelta: Int get() = levelTo - levelFrom

    /**
     * تكلفة نقطة النسبة الواحدة بالـµAh — **مؤشّر تدهور طولي**، لا رقم صحة بطارية.
     * كلما ارتفع، احتاج الشحن طاقةً أكثر للنقطة الواحدة.
     */
    val uahPerPoint: Long?
        get() = if (levelDelta <= 0) null else chargeCounterDeltaUah / levelDelta
}

/** حكم طولي مبني على عدّة جلسات، أو `UNKNOWN` بصراحة. */
sealed interface ChargeVerdict {
    /** لا عدّاد شحن على هذا الجهاز/الإصدار ⇒ لا مسار قياس، ولا ادّعاء. */
    data object CounterUnavailable : ChargeVerdict

    /** جلسات أقل من الحدّ ⇒ لا حكم بعد (ونعرض التقدّم). */
    data class InsufficientData(val have: Int, val need: Int) : ChargeVerdict

    /** التكلفة الأخيرة قريبة من المعتاد ⇒ لا انحراف مقيس. */
    data class Stable(val medianUahPerPoint: Long) : ChargeVerdict

    /** التكلفة الأخيرة أعلى من المعتاد بفارق معلن ⇒ انحراف يستحق النظر. */
    data class Drifting(val medianUahPerPoint: Long, val recentUahPerPoint: Long) : ChargeVerdict
}

/**
 * `AR-09` — **سجل دورات الشحن: حكم طاقة طولي لا لحظي**.
 *
 * الفكرة: قراءة واحدة تقول «البطارية ٤٠٠٠ مللي أمبير» لا تُثبت شيئًا عن التدهور. الذي يُثبته هو
 * **تكلفة النقطة عبر الزمن**: كم µAh احتاجت كل نقطة نسبة، وهل ارتفعت التكلفة.
 *
 * **المنهج (وأساس القياس معلَن بلا مواربة):** نقرأ **عند فتح التطبيق فقط** — لا خدمة خلفية ولا
 * مستشعر دائم — لقطة من: النسبة، عدّاد الشحن (`BATTERY_PROPERTY_CHARGE_COUNTER`)، وهل الجهاز
 * يشحن. ثم كل لقطتين متتاليتين تصلحان (نسبة ارتفعت ≥ [MIN_LEVEL_RISE]، والعدّاد مقروء في
 * كليهما، والفرق موجب) تُنتج **جلسة**. التخزين داخل التطبيق فقط (لا ملفات نظام).
 *
 * **ما لا يقوله هذا الملف:** ليس «صحة بطارية» ولا «عمر متبقٍّ» ولا درجات تسويقية (`AR-R5`)،
 * ولا يقارن بين أجهزة (لا أسطول قياس). وحين لا يكون العدّاد مدعومًا تكون الحالة
 * [ChargeVerdict.CounterUnavailable] — **لا صفر ولا تخمين**.
 */
object ChargeLedger {

    private const val PREFS = "charge_ledger"
    private const val KEY_SESSIONS = "sessions"
    private const val KEY_LAST_LEVEL = "last_level"
    private const val KEY_LAST_COUNTER = "last_counter_uah"
    private const val KEY_LAST_CHARGING = "last_charging"
    private const val KEY_LAST_AT = "last_at_ms"

    /** أقصى عدد جلسات محفوظة — نافذة متحرّكة بدل ملف ينمو بلا حدّ. */
    const val MAX_SESSIONS = 30

    /** أقلّ صعود في النسبة يُعتبر جلسة شحن (تحت ذلك ضجيج قراءة). */
    const val MIN_LEVEL_RISE = 5

    /** أقلّ عدد جلسات قبل أن يُسمح بحكم. */
    const val MIN_SESSIONS_FOR_VERDICT = 3

    /** نسبة الارتفاع في التكلفة التي تُعتبر انحرافًا (١٥٪). */
    private const val DRIFT_RATIO = 1.15

    /** لقطة واحدة من النظام. */
    data class Observation(
        val levelPct: Int,
        val counterUah: Long?,
        val charging: Boolean,
        val atMs: Long,
    )

    /**
     * استخراج جلسة من لقطتين — **خالصة وقابلة للاختبار**. تُعيد `null` حين لا تكفي الأدلّة،
     * ولا تُنتج جلسة من فرق سالب أو من عدّاد مفقود.
     */
    fun sessionBetween(
        previous: Observation,
        current: Observation,
        minLevelRise: Int = MIN_LEVEL_RISE,
    ): ChargeSession? {
        val prevCounter = previous.counterUah ?: return null
        val nowCounter = current.counterUah ?: return null
        val levelDelta = current.levelPct - previous.levelPct
        if (levelDelta < minLevelRise) return null
        val counterDelta = nowCounter - prevCounter
        if (counterDelta <= 0L) return null
        return ChargeSession(
            atMs = current.atMs,
            levelFrom = previous.levelPct,
            levelTo = current.levelPct,
            chargeCounterDeltaUah = counterDelta,
        )
    }

    /** الوسيط — مُقاوم للقيم الشاذّة أكثر من المتوسط، وسهل التفسير. */
    private fun median(values: List<Long>): Long? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
    }

    /**
     * الحكم الطولي — **خالص**: يحتاج جلسات كافية، ويقارن **آخر جلسة** بوسيط ما سبقها.
     * ولا يقول «انحراف» إلا بفارق معلن ([DRIFT_RATIO]).
     */
    fun verdict(sessions: List<ChargeSession>): ChargeVerdict {
        val usable = sessions.mapNotNull { it.uahPerPoint }
        if (usable.isEmpty()) return ChargeVerdict.CounterUnavailable
        if (sessions.size < MIN_SESSIONS_FOR_VERDICT) {
            return ChargeVerdict.InsufficientData(sessions.size, MIN_SESSIONS_FOR_VERDICT)
        }
        val baseline = median(usable.dropLast(1)) ?: return ChargeVerdict.InsufficientData(
            sessions.size,
            MIN_SESSIONS_FOR_VERDICT,
        )
        val recent = usable.last()
        return if (baseline > 0L && recent.toDouble() > baseline.toDouble() * DRIFT_RATIO) {
            ChargeVerdict.Drifting(baseline, recent)
        } else {
            ChargeVerdict.Stable(baseline)
        }
    }

    // ---- التخزين: JSON صغير في SharedPreferences الخاصة بالتطبيق ----------------------

    /** ترميز الجلسات — دالة خالصة كي تُختبر بلا أندرويد فيها. */
    fun encode(sessions: List<ChargeSession>): String {
        val array = JSONArray()
        sessions.takeLast(MAX_SESSIONS).forEach { session ->
            array.put(
                JSONObject().apply {
                    put("at", session.atMs)
                    put("from", session.levelFrom)
                    put("to", session.levelTo)
                    put("uah", session.chargeCounterDeltaUah)
                }
            )
        }
        return array.toString()
    }

    /** فكّ الترميز — يتجاهل أي مدخل مشوّه بدل أن يُسقط السجل كله. */
    fun decode(raw: String?): List<ChargeSession> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val from = item.optInt("from", -1)
                    val to = item.optInt("to", -1)
                    val uah = item.optLong("uah", -1L)
                    if (from < 0 || to < 0 || uah <= 0L) continue
                    add(ChargeSession(item.optLong("at", 0L), from, to, uah))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    data class Snapshot(
        val sessions: List<ChargeSession>,
        val verdict: ChargeVerdict,
        val counterAvailable: Boolean,
    )

    /** قراءة اللقطة الحالية من التخزين (بلا أي مصدر نظام). */
    fun read(context: Context): Snapshot {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val sessions = decode(prefs.getString(KEY_SESSIONS, null))
        return Snapshot(
            sessions = sessions,
            verdict = verdict(sessions),
            counterAvailable = prefs.getBoolean(KEY_LAST_CHARGING, false) ||
                prefs.contains(KEY_LAST_COUNTER),
        )
    }

    /**
     * يسجّل لقطة الآن. يُنادى من نقاط نادرة (فتح التطبيق/شاشة التشخيص) — **لا خدمة خلفية**،
     * فهذه كلفتها قراءة sticky واحدة وكتابة صغيرة.
     *
     * @return اللقطة بعد التسجيل، أو `null` إن تعذّرت قراءة البطارية أصلًا.
     */
    fun observe(context: Context, atMs: Long = System.currentTimeMillis()): Snapshot? {
        val observation = observeSystem(context, atMs) ?: return null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val sessions = decode(prefs.getString(KEY_SESSIONS, null)).toMutableList()

        val previous = prefs.let { store ->
            val level = store.getInt(KEY_LAST_LEVEL, -1)
            if (level < 0) {
                null
            } else {
                Observation(
                    levelPct = level,
                    counterUah = store.getLong(KEY_LAST_COUNTER, -1L).takeIf { it >= 0L },
                    charging = store.getBoolean(KEY_LAST_CHARGING, false),
                    atMs = store.getLong(KEY_LAST_AT, 0L),
                )
            }
        }

        if (previous != null) {
            sessionBetween(previous, observation)?.let { session ->
                sessions += session
                while (sessions.size > MAX_SESSIONS) sessions.removeAt(0)
                EventLog.symptom(
                    screen = "ChargeLedger",
                    symptom = "charge_session",
                    valueMs = session.uahPerPoint ?: 0L,
                    count = sessions.size.toLong(),
                )
            }
        }

        prefs.edit()
            .putString(KEY_SESSIONS, encode(sessions))
            .putInt(KEY_LAST_LEVEL, observation.levelPct)
            .putLong(KEY_LAST_COUNTER, observation.counterUah ?: -1L)
            .putBoolean(KEY_LAST_CHARGING, observation.charging)
            .putLong(KEY_LAST_AT, observation.atMs)
            .apply()

        return Snapshot(sessions, verdict(sessions), observation.counterUah != null)
    }

    /**
     * لقطة من النظام — قراءة sticky بلا استهلاك، بنفس أسلوب المستودع القائم في
     * `BatteryHealthUtil`/`ThermalUtil`. العدّاد قد يكون غير مدعوم ⇒ `null` لا صفر.
     */
    fun observeSystem(context: Context, atMs: Long): Observation? {
        return try {
            val intent = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ) ?: return null
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (level < 0 || scale <= 0) return null
            val pct = (level * 100 / scale).coerceIn(0, 100)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            val counter = try {
                val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                    ?.takeIf { it > 0 }
                    ?.toLong()
            } catch (_: Exception) {
                null
            }
            Observation(levelPct = pct, counterUah = counter, charging = charging, atMs = atMs)
        } catch (_: Exception) {
            null
        }
    }
}
