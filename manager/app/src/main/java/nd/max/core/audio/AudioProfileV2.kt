/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * الصوت — **بصمات الصوت، مرقّمة** (`AQ-07`): نموذج + مُرمِّز/مُفكِّك **صافٍ** يُقاس على JVM.
 *
 * **ولماذا صيغة خطوط نملكها لا JSON:** شرط القبول هو «ترحيل نسخة أقدم **يُختبر على JVM**» — و`org.json`
 * موجودة في `android.jar` وحده، فلا تُقاس على JVM. فالصيغة هنا **نصٌّ خطّيّ صريح** نكتبه ونقرؤه بأنفسنا:
 * صفر تبعيات، والترحيل مقيسٌ بالاختبار لا بالثقة.
 *
 * **والترحيل لا يُخمَّن:** النسخة تُقرأ من الترويسة. `v1` (اسم + مدخلات، بلا معرّف ولا جهاز) تُرحَّل
 * بمعرّفٍ مشتقّ من الاسم وربطِ جهازٍ فارغ؛ ونسخةٌ **أحدث من نسختنا لا تُقرأ** — لأنّنا لا نعرف ما فيها،
 * وتفسيرها بتخمين أسوأ من تركها (`skipped` في [`AudioProfileList`]).
 *
 * **ولا نُسقط شيئًا بصمت:** كل مدخل يخصّ مؤثّرًا غير قابل للاستعمال يُسقط **ويُسمّى** في
 * [`ProfilePrune`]، فتقول الشاشة «أُسقط ٣ مدخلات: جهازك لا يُعلن هذا المؤثّر» — لا تحمّل نمطًا ناقصًا
 * وتدّعي أنه كامل.
 *
 * **والمنع بالتصميم:** [`pruneProfileForCapabilities`] هو الباب الذي تُبنى منه البصمة، فـ«لا نمط يَعِد
 * بمؤثّر غير موجود» شرطٌ في الكود لا وصيّة في تعليق.
 */
package nd.max.core.audio

import nd.max.core.hardware.HardwareControlKey

/** نسخة البصمة التي نكتبها اليوم — وترتفع فقط حين تتغيّر الصيغة تغيّرًا يُفسد القراءة القديمة. */
const val AUDIO_PROFILE_SCHEMA = 2

/** ترويسة الملفّ — تُقرأ للترحيل، ولا تُستنتج الصيغة من غيابها. */
const val AUDIO_PROFILE_HEADER = "#maxmanager-audio-profile"

/**
 * بصمة صوت واحدة.
 *
 * @param entries مقبض ← قيمته، والمفاتيح **مفاتيح المحكِّم نفسها** (`HardwareControlKey.*`) — فلا صيغة
 *   ثانية للقيم تُترجم بين الطبقات.
 * @param deviceFingerprint الطراز/الجهاز الذي كُتبت عليه — تُعرض لا تُفرض: البصمة من جهاز آخر تُقرأ
 *   وتُنقل، ويُقال من أين جاءت.
 */
data class AudioProfileV2(
    val id: String,
    val name: String,
    val schemaVersion: Int = AUDIO_PROFILE_SCHEMA,
    val deviceFingerprint: String? = null,
    val entries: Map<String, String> = emptyMap(),
)

/** مخرَج القراءة: ما قُرئ، وكم رُحِّل، وكم تُرك — وكلٌّ عددٌ يُعرض لا يُخفى. */
data class AudioProfileList(
    val profiles: List<AudioProfileV2> = emptyList(),
    val migratedFromV1: Int = 0,
    val skipped: Int = 0,
)

/** مخرَج التنقية: البصمة بعد إسقاط ما لا يعمل، وأسماء ما أُسقط. */
data class ProfilePrune(
    val profile: AudioProfileV2,
    val droppedKeys: List<String>,
)

/**
 * يُسقط كل مدخل يخصّ مؤثّرًا **لا يمكن استعماله على هذا الجهاز**.
 *
 * والقاعدة من القدرات (‏`AQ-01`) لا من قائمة ثانية: رمز مؤثّرٍ غير معروف عندنا يُسقط أيضًا — لأنّنا لا
 * نعرف كيف نُعيد كتابته، ونمطٌ يحمل مدخلًا لا نفهمه أسوأ من نمطٍ يقول إنه أسقطه.
 */
fun pruneProfileForCapabilities(
    profile: AudioProfileV2,
    attachableEffectTokens: Set<String>,
): ProfilePrune {
    val kept = LinkedHashMap<String, String>()
    val dropped = mutableListOf<String>()
    profile.entries.forEach { (key, value) ->
        val token = HardwareControlKey.audioEffectToken(key)
        if (token == null) {
            kept[key] = value
            return@forEach
        }
        val kind = AudioEffectKind.ofToken(token)
        if (kind == null || kind.token !in attachableEffectTokens) {
            dropped += key
        } else {
            kept[key] = value
        }
    }
    return ProfilePrune(profile.copy(entries = kept), dropped)
}

/** يُرمِّز كل البصمات نصًّا واحدًا — والترتيب محفوظ فلا يتغيّر الملفّ بين حفظين متساويين. */
fun encodeProfiles(profiles: List<AudioProfileV2>): String = buildString {
    append(AUDIO_PROFILE_HEADER).append(' ').append('v').append(AUDIO_PROFILE_SCHEMA).append('\n')
    profiles.forEach { profile ->
        append("profile")
            .append('|').append(escape(profile.id))
            .append('|').append(escape(profile.name))
            .append('|').append(escape(profile.deviceFingerprint ?: ""))
            .append('\n')
        profile.entries.forEach { (key, value) ->
            append("entry")
                .append('|').append(escape(key))
                .append('|').append(escape(value))
                .append('\n')
        }
        append("end\n")
    }
}

/**
 * يقرأ الملفّ **بلا أن يرمي أبدًا**: سطرٌ مشوّه يُتجاهل ويُعدّ، وبصمةٌ بلا اسم أو بلا معرّف تُبنى من
 * المتاح، ونسخةٌ أحدث تُترك وتُعدّ.
 */
fun decodeProfiles(text: String?): AudioProfileList {
    if (text.isNullOrBlank()) return AudioProfileList()
    val lines = text.split('\n').map { it.trim() }
    val header = lines.firstOrNull { it.startsWith(AUDIO_PROFILE_HEADER) }
        ?: return AudioProfileList(skipped = 1)
    val version = header.substringAfterLast(" v", "").toIntOrNull() ?: 1
    if (version > AUDIO_PROFILE_SCHEMA) return AudioProfileList(skipped = 1)

    val profiles = mutableListOf<AudioProfileV2>()
    val migrated = intArrayOf(0)
    var currentId: String? = null
    var currentName: String? = null
    var currentDevice: String? = null
    var currentSchema = version
    var entries = LinkedHashMap<String, String>()

    fun flush() {
        val id = currentId
        if (id.isNullOrBlank()) {
            entries = LinkedHashMap()
            return
        }
        profiles += AudioProfileV2(
            id = id,
            name = currentName?.takeIf { it.isNotBlank() } ?: id,
            schemaVersion = currentSchema,
            deviceFingerprint = currentDevice?.takeIf { it.isNotBlank() },
            entries = entries,
        )
        entries = LinkedHashMap()
    }

    lines.forEach { line ->
        when {
            line.isBlank() || line.startsWith("#") -> Unit
            line == "end" -> {
                flush()
                currentId = null
                currentName = null
                currentDevice = null
            }
            line.startsWith("profile|") -> {
                flush()
                val parts = line.substringAfter("profile|").split('|')
                currentId = unescape(parts.getOrNull(0))
                currentName = unescape(parts.getOrNull(1))
                currentDevice = unescape(parts.getOrNull(2))
                // v1 كان يحمل الاسم وحده في هذا السطر — فيُبنى المعرّف منه ويُعدّ الترحيل.
                if (version < AUDIO_PROFILE_SCHEMA) {
                    migrated[0] += 1
                    currentSchema = AUDIO_PROFILE_SCHEMA
                    if (currentId.isNullOrBlank()) currentId = slugOf(currentName)
                    currentDevice = null
                }
            }
            line.startsWith("entry|") -> {
                val parts = line.substringAfter("entry|").split('|')
                val key = unescape(parts.getOrNull(0))
                val value = unescape(parts.getOrNull(1))
                if (!key.isNullOrBlank() && value != null) entries[key] = value
            }
            // و`v1` كان يكتب الاسم مجرّدًا في أوّل سطر — نُقرؤه ولا نُخمّن غيره.
            version < AUDIO_PROFILE_SCHEMA && !line.contains('|') -> {
                if (currentId == null) {
                    currentName = unescape(line)
                    currentId = slugOf(currentName)
                    currentDevice = null
                    currentSchema = AUDIO_PROFILE_SCHEMA
                    migrated[0] += 1
                }
            }
            else -> Unit
        }
    }
    flush()
    return AudioProfileList(
        profiles = profiles.filter { it.id.isNotBlank() },
        migratedFromV1 = migrated[0],
        skipped = 0,
    )
}

/** معرّفٌ من الاسم: حروف صغيرة وأرقام وشرطات — بلا اعتماد على عشوائيّة فلا يتغيّر بين قراءتين. */
fun slugOf(name: String?): String {
    val raw = name.orEmpty().lowercase()
    val builder = StringBuilder()
    raw.forEach { char ->
        when {
            char.isLetterOrDigit() -> builder.append(char)
            builder.isNotEmpty() && builder.last() != '-' -> builder.append('-')
        }
    }
    val slug = builder.toString().trim('-')
    return slug.ifBlank { "profile" }
}

/**
 * هروبٌ صريح للحروف التي تفصل الحقول — `%` أوّلًا فلا يُهرب مرّتين، والترتيب ملزم.
 */
private fun escape(text: String): String = text
    .replace("%", "%25")
    .replace("|", "%7C")
    .replace("\n", "%0A")
    .replace("\r", "%0D")
    .replace("\t", "%09")

private fun unescape(text: String?): String {
    if (text == null) return ""
    return text
        .replace("%0A", "\n")
        .replace("%0D", "\r")
        .replace("%09", "\t")
        .replace("%7C", "|")
        .replace("%25", "%")
}
