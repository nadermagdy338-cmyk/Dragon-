/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * الصوت — **مخزن البصمات** (`AQ-07`): ملفٌّ واحد في `filesDir`، وصيغته من [`AudioProfileV2`] وحدها.
 *
 * **ولماذا ملفّ لا قاعدة بيانات:** البصمات عشراتٌ لا آلاف، والحجم كلّه نصٌّ صغير؛ وملفٌّ واحد يُقرأ
 * في `Dispatchers.IO` **يُصدّر ويُنقل** كما هو (شرط الخطّة: حفظٌ وتحميلٌ وتصدير)، وقاعدةُ بيانات لا
 * تُصدَّر نصًّا. فلا تبعية جديدة ولا مخزن ثانٍ.
 *
 * **وكل عملية قراءة/كتابة محميّة:** ما لم يُقرأ يعود قائمةً فارغة **مع عدّاد تجاوز**، وما لم يُكتب يعود
 * `false` — والشاشة تقول الفشل بسببه ولا تدّعي نجاحًا (قاعدة المستودع نفسها في الكتابة).
 *
 * **وبصمة الجهاز تُبنى من `Build` ولا تُخزَّن سرًّا:** `MANUFACTURER MODEL` — معلومة يراها المستخدم في
 * «معلومات الجهاز» أصلًا، بها يُعرف أنّ نمطًا كُتب لجهازٍ آخر.
 */
package nd.max.core.audio

import android.content.Context
import android.os.Build
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioProfileStore @Inject constructor() {

    /** كل البصمات المحفوظة — وغياب الملفّ **ليس فشلًا**: يعني «لم يُحفظ بعد». */
    fun load(context: Context): AudioProfileList {
        val file = fileOf(context)
        if (!file.exists()) return AudioProfileList()
        val text = runCatching { file.readText() }.getOrNull()
            ?: return AudioProfileList(skipped = 1)
        return decodeProfiles(text)
    }

    /** يكتب القائمة كاملة (استبدال ذرّيّ) — ويعود `false` بلا ادّعاء نجاح عند الفشل. */
    fun save(context: Context, profiles: List<AudioProfileV2>): Boolean = runCatching {
        val file = fileOf(context)
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(encodeProfiles(profiles))
        temporary.renameTo(file)
    }.getOrDefault(false)

    /** النصّ الخامّ للتصدير — نفس ما في الملفّ حرفيًّا، فلا صيغة تصديرٍ ثانية تنحرف عنه. */
    fun export(context: Context): String = decodeProfiles(runCatching { fileOf(context).readText() }.getOrNull())
        .let { encodeProfiles(it.profiles) }

    /** بصمة الجهاز — تُكتب مع البصمة وتُعرض في الشاشة، ولا تُستعمل للحجب. */
    fun deviceFingerprint(): String =
        listOfNotNull(
            Build.MANUFACTURER.takeIf { it.isNotBlank() },
            Build.MODEL.takeIf { it.isNotBlank() },
        ).joinToString(" ").ifBlank { Build.DEVICE }

    private fun fileOf(context: Context): File =
        File(context.filesDir, FILE_NAME)

    private companion object {
        const val FILE_NAME = "audio_profiles.txt"
    }
}
