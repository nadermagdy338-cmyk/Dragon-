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
import nd.max.core.platform.PropertyUtils

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * `AR-33` — **بروفايل قابل للمشاركة بمصدر معلن**.
 *
 * المشكلة التي يحلّها: قيم البروفايلات عندنا (نِسَب الطاقة/التوازن/الألعاب) تُحفَظ في
 * `ProfilePresetStore` **بلا أي وسم مصدر**. فحين يشارك مستخدم إعداداته، أو حين نعرض قيمة
 * افتراضية، **لا يستطيع أحد أن يعرف**: هل هذا الرقم قيس على جهاز؟ أم قيمة مبدئية شُحنت مع
 * التطبيق؟ أم أن المستخدم حرّكها بيده؟ وهذا بالضبط ما يمنعه `XR-R5` («تعليم قيم تبدو معقولة
 * كقيم مُعلَنة») و`ADR-07` (لا ثقة مصنوعة).
 *
 * **القواعد الملزمة في هذا الملف:**
 * 1. **لا يُشتقّ `MEASURED` أبدًا.** الاشتقاق الوحيد الممكن عندنا: القيمة = الافتراضي المشحون
 *    ⇒ `SEED`، والقيمة ≠ الافتراضي ⇒ `USER_SET`. أما «مقيسة» فلا تُقال إلا إذا **صرّح بها
 *    المُصدِّر**، وتُعرض عندنا **كادّعاء مُصدِّر** لا كقياس لنا.
 * 2. **مصدر مجهول يبقى مجهولًا** (`UNKNOWN`) ولا يُرقّى إلى `SEED` بالصمت.
 * 3. **بروفايل قِيس على SoC آخر ليس قياسًا على هذا الجهاز** — نُعلن [DeviceMatch] بدل السكوت.
 * 4. **المدخل غير الصالح يُرفض بسبب معلَن**، ولا يُقصّ إلى حدّ مقبول بصمت.
 */
object ProfileSharing {

    /** رقم صيغة المستند — وجوده يسمح بأن يرفض الإصدار القديم ما لا يفهمه بدل أن يفسّره خطأً. */
    const val SCHEMA = 1

    /** حدود النسبة المعتمدة — نفس حدود `ProfilePresetStore`، معلنة هنا للمقارنة والرفض. */
    const val MIN_PERCENT = 20
    const val MAX_PERCENT = 100

    /** المصدر **كما صرّح به مُصدِّر المستند**. */
    enum class DeclaredSource {
        /** صرّح المُصدِّر أنه قياس. عندنا: **ادّعاؤه**، لا قياس لنا. */
        MEASURED,

        /** قيمة مبدئية مشحونة مع التطبيق. */
        SEED,

        /** ضبطها المستخدم بيده. */
        USER_SET,

        /** لا مصدر معلن، أو مصدر بكلمة لا نعرفها ⇒ **مجهول**، ولا يُرقّى. */
        UNKNOWN,
    }

    data class Entry(val profile: String, val percent: Int, val source: DeclaredSource)

    /** سياق الجهاز الذي صدر منه المستند — بلا سياق لا يمكن الحكم على قابلية النقل. */
    data class DeviceContext(val soc: String?, val androidSdk: Int?) {
        val known: Boolean get() = !soc.isNullOrBlank()
    }

    data class Document(
        val schema: Int = SCHEMA,
        val exportedAtMs: Long,
        val device: DeviceContext,
        val entries: List<Entry>,
    )

    /** هل البروفايل المشترك يخصّ هذا الجهاز؟ */
    enum class DeviceMatch {
        /** نفس المعرّف (بصيغة موحّدة). */
        SAME,

        /** معرّفان معروفان ومختلفان ⇒ **ليس نقلًا للقياس**. */
        DIFFERENT,

        /** معرّف ناقص/غير مقروء ⇒ لا حكم، ولا اطمئنان مصنوع. */
        UNKNOWN,
    }

    /** سبب رفض مدخل — عددي لا نصّي، كي يُترجَم في الواجهة ويُختبر هنا. */
    enum class RejectReason {
        /** نسبة خارج النطاق المعتمد. */
        PERCENT_OUT_OF_RANGE,

        /** نسبة غير رقم أصلاً. */
        PERCENT_NOT_A_NUMBER,

        /** اسم بروفايل فارغ. */
        EMPTY_PROFILE,
    }

    data class RejectedEntry(val profile: String?, val reason: RejectReason)

    /** نتيجة فكّ الترميز: ما قُبل، وما رُفض ولماذا، وما جهله المصدر، وحكم الجهاز. */
    data class ImportReport(
        val schemaSupported: Boolean,
        val document: Document?,
        val accepted: List<Entry>,
        val rejected: List<RejectedEntry>,
        val missingSource: List<String>,
        val deviceMatch: DeviceMatch,
    ) {
        val hasAnything: Boolean get() = accepted.isNotEmpty()
    }

    // ---- الوسم المشتق (معلَن بلا مواربة) ----------------------------------------------

    /**
     * الوسم الذي **نستطيع** اشتقاقه بأنفسنا: هل القيمة هي الافتراضي المشحون أم غيّرها المستخدم؟
     * و**لا يعود هذا أبدًا `MEASURED`**: لا خطّ قياس عندنا لهذه النِسَب.
     */
    fun deriveSource(value: Int, shippedDefault: Int): DeclaredSource =
        if (value == shippedDefault) DeclaredSource.SEED else DeclaredSource.USER_SET

    /** كلمة المصدر في المستند (معرّف ثابت، بلا ترجمة: يُقرأ من ملفات لا تُعرَض). */
    fun sourceWord(source: DeclaredSource): String = when (source) {
        DeclaredSource.MEASURED -> "measured"
        DeclaredSource.SEED -> "seed"
        DeclaredSource.USER_SET -> "user_set"
        DeclaredSource.UNKNOWN -> "unknown"
    }

    /** قراءة كلمة المصدر — وأي كلمة لا نعرفها تبقى [DeclaredSource.UNKNOWN]. */
    fun parseSource(raw: String?): DeclaredSource = when (raw?.trim()?.lowercase()) {
        "measured" -> DeclaredSource.MEASURED
        "seed" -> DeclaredSource.SEED
        "user_set" -> DeclaredSource.USER_SET
        else -> DeclaredSource.UNKNOWN
    }

    // ---- حكم الجهاز (خالص) ------------------------------------------------------------

    /** توحيد المعرّف قبل المقارنة: `SM8650` و`sm8650 ` و`SM-8650` لا تُخلط بحروف. */
    private fun normaliseSoc(raw: String?): String? =
        raw?.trim()?.lowercase()?.replace(Regex("[^a-z0-9]"), "")?.takeIf { it.isNotEmpty() }

    /**
     * حكم قابلية النقل — **خالص**. غياب معرّف على أي طرف يعني [DeviceMatch.UNKNOWN]،
     * لا [DeviceMatch.SAME].
     */
    fun matchDevice(declaredSoc: String?, currentSoc: String?): DeviceMatch {
        val declared = normaliseSoc(declaredSoc) ?: return DeviceMatch.UNKNOWN
        val current = normaliseSoc(currentSoc) ?: return DeviceMatch.UNKNOWN
        return if (declared == current) DeviceMatch.SAME else DeviceMatch.DIFFERENT
    }

    // ---- الترميز ------------------------------------------------------------------

    /** ترميز خالص — لا يعتمد على أندرويد في شيء. */
    fun encode(document: Document): String {
        val root = JSONObject()
        root.put("schema", document.schema)
        root.put("exported_at", document.exportedAtMs)
        val device = JSONObject()
        device.put("soc", document.device.soc ?: JSONObject.NULL)
        device.put("android_sdk", document.device.androidSdk ?: JSONObject.NULL)
        root.put("device", device)
        val entries = JSONArray()
        document.entries.forEach { entry ->
            entries.put(
                JSONObject().apply {
                    put("profile", entry.profile)
                    put("percent", entry.percent)
                    put("source", sourceWord(entry.source))
                }
            )
        }
        root.put("entries", entries)
        return root.toString()
    }

    /**
     * فكّ الترميز **بالتحقّق**، لا بالتصديق: يرفض صيغة لا يفهمها، ويرفض مدخلًا خارج النطاق،
     * ويُعلن المصادر المجهولة، ويحكم على قابلية النقل. ولا يُصلح شيئًا بصمت.
     */
    fun decode(raw: String?, currentSoc: String?): ImportReport {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return emptyReport(schemaSupported = false)

        val root = try {
            JSONObject(text)
        } catch (_: Exception) {
            // جرّب صفًّا داخل [] إن كان المستند ملفوفًا في مصفوفة.
            try {
                JSONArray(text).optJSONObject(0)
            } catch (_: Exception) {
                null
            }
        } ?: return emptyReport(schemaSupported = false)

        val schema = root.optInt("schema", -1)
        if (schema != SCHEMA) return emptyReport(schemaSupported = false)

        val deviceObject = root.optJSONObject("device")
        val declaredSoc = deviceObject?.optString("soc")?.takeIf { it.isNotBlank() && it != "null" }
        val sdk = deviceObject?.optInt("android_sdk", -1)?.takeIf { it > 0 }
        val device = DeviceContext(soc = declaredSoc, androidSdk = sdk)

        val rawEntries = root.optJSONArray("entries")
        val accepted = mutableListOf<Entry>()
        val rejected = mutableListOf<RejectedEntry>()
        val missingSource = mutableListOf<String>()

        for (index in 0 until (rawEntries?.length() ?: 0)) {
            val item = rawEntries?.optJSONObject(index) ?: continue
            val profile = item.optString("profile").trim()
            if (profile.isEmpty()) {
                rejected += RejectedEntry(null, RejectReason.EMPTY_PROFILE)
                continue
            }
            if (!item.has("percent")) {
                rejected += RejectedEntry(profile, RejectReason.PERCENT_NOT_A_NUMBER)
                continue
            }
            val percent = item.optInt("percent", Int.MIN_VALUE)
            if (percent == Int.MIN_VALUE) {
                rejected += RejectedEntry(profile, RejectReason.PERCENT_NOT_A_NUMBER)
                continue
            }
            if (percent < MIN_PERCENT || percent > MAX_PERCENT) {
                // لا نقصّ القيمة إلى الحدّ: القيمة المرفوضة تُعلَن، لا تُعدّل.
                rejected += RejectedEntry(profile, RejectReason.PERCENT_OUT_OF_RANGE)
                continue
            }
            val source = parseSource(item.optString("source", null))
            if (source == DeclaredSource.UNKNOWN) missingSource += profile
            accepted += Entry(profile, percent, source)
        }

        return ImportReport(
            schemaSupported = true,
            document = Document(
                schema = schema,
                exportedAtMs = root.optLong("exported_at", 0L),
                device = device,
                entries = accepted,
            ),
            accepted = accepted,
            rejected = rejected,
            missingSource = missingSource,
            deviceMatch = matchDevice(declaredSoc, currentSoc),
        )
    }

    private fun emptyReport(schemaSupported: Boolean) = ImportReport(
        schemaSupported = schemaSupported,
        document = null,
        accepted = emptyList(),
        rejected = emptyList(),
        missingSource = emptyList(),
        deviceMatch = DeviceMatch.UNKNOWN,
    )

    // ---- القراءة من تخزيننا (وسم مشتق معلَن) -----------------------------------------

    /** أسماء البروفايلات التي نعرفها في المتجر. */
    val KNOWN_PROFILES = listOf("power", "balanced", "gaming", "performance", "custom")

    /** معرّف SoC من النظام — `null` إن لم يُعلنه (ولا نخترعه). */
    fun currentSoc(): String? =
        PropertyUtils.get("ro.soc.model")?.takeIf { it.isNotBlank() }
            ?: PropertyUtils.get("ro.board.platform")?.takeIf { it.isNotBlank() }

    /**
     * مستند من إعداداتنا الحالية، بوسم **مشتق ومعلَن**: قيمة = الافتراضي المشحون ⇒ `SEED`،
     * وغيرها ⇒ `USER_SET`. **لا `MEASURED`** — لأننا لا نملك دليل قياس لهذه النِسَب.
     */
    fun read(context: Context, atMs: Long = System.currentTimeMillis()): Document {
        val entries = KNOWN_PROFILES.map { profile ->
            val value = ProfilePresetStore.percentFor(context, profile)
            Entry(
                profile = profile,
                percent = value,
                source = deriveSource(value, ProfilePresetStore.defaultPercent(profile)),
            )
        }
        return Document(
            exportedAtMs = atMs,
            device = DeviceContext(soc = currentSoc(), androidSdk = android.os.Build.VERSION.SDK_INT),
            entries = entries,
        )
    }
}
