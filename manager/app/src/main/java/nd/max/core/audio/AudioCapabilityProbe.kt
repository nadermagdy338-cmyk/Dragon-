/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * الصوت — **مسبار القدرات**: يقيس على المنصّة ما يجعل الأحكام ممكنة، ثمّ يُسلّمه للطبقة الصافية.
 *
 * **وهذا الملفّ هو الوحيد الذي يلمس `AudioEffect` اليوم** (تكملة ٢٢٨، المرحلة `AQ-01`)، وهو **لا
 * يعالج صوتًا ولا يُبقي مؤثّرًا**: يُنشئ مؤثّرًا واحدًا لقياس المزج العامّ، ثمّ **يُحرّره فورًا**
 * (`release()`)، ويعود. فلا يبقى أثرٌ على صوت المستخدم من مجرّد فتح شاشة التشخيص — وهذا شرط قبول
 * المرحلة، لا نيّة حسنة.
 *
 * **وثلاثيّات لا ثنائيّات:** كل قارئ يعود `true`/`false`/`null`، و`null` = «لم تُقرأ» — فلا يُكتب
 * «غير متاح» عن شيء عجزنا عن سؤاله (ADR-07). والفرق يُقاس في الطبقة الصافية بلا جهاز.
 *
 * **وحدُّ الإرفاق مقصود:** نجاح إنشاء المؤثّر **مع** `hasControl()` هو «مقبول». فمؤثّرٌ أُنشئ ولا
 * نملك التحكّم به لا نستطيع الكتابة فيه → ويُقال «يحتاج جلسةً نملكها» بدل ادّعاء قبول.
 */
package nd.max.core.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.Equalizer
import android.os.Build

object AudioCapabilityProbe {

    /** الأحكام جاهزة للعرض: قياسٌ ثمّ حكمٌ — ولا قرار في الشاشة. */
    fun verdicts(context: Context): List<AudioFeatureVerdict> = audioCapabilityVerdicts(abilities(context))

    /** القياس الخامّ وحده — يُفصل عن الحكم ليُقاس الحكم على JVM بأمثلة مصنوعة. */
    fun abilities(context: Context): DeclaredAudioAbilities {
        val manager = managerOf(context)
        return DeclaredAudioAbilities(
            // والقائمة الفارغة قراءةٌ («لا مؤثّرات») و`null` غياب قراءة — والفرق يصل كما هو.
            declaredEffects = AudioEffectProbe.effects()
                ?.mapNotNull { audioEffectTypeToken(it.typeUuid) }
                ?.distinct()
                ?.sorted(),
            globalAttach = globalAttach(),
            spectrumPermissionGranted = hasRecordAudio(context),
            mixerAttributesSupported = mixerAttributes(manager),
            communicationRoutingSupported = communicationRouting(manager),
            volumeGroupsSupported = volumeGroups(manager),
        )
    }

    /**
     * محاولة المزج العامّ (الجلسة ٠) — **الأخطر في هذا الملفّ وأكثرها ضبطًا:**
     *
     * المؤثّر يُنشأ بجلسة تشخيص (`priority` صفر) ثمّ **يُحرَّر في `finally`**، فلا يبقى معالجٌ حيّ
     * إن رمى شيء بينهما. وما عاد برميه المنصّة (`RuntimeException` · `IllegalStateException` ·
     * `UnsupportedOperationException`) يُقرأ **«رفضت» لا «فشلنا»** — وهذا هو الفرق الذي يجعل الشاشة
     * تقول الحقيقة عن هذه الميزة المشكوك فيها أصلًا (مُهمَلة في المنصّة منذ أندرويد ٩).
     */
    private fun globalAttach(): GlobalAttach {
        var effect: Equalizer? = null
        return try {
            effect = Equalizer(0, GLOBAL_SESSION)
            // والتحكّم شرطٌ ثانٍ: بلا `hasControl()` لا تُثبت كتابة، فلا يُقال «مقبول».
            if (effect.hasControl()) GlobalAttach.ACCEPTED else GlobalAttach.DENIED
        } catch (error: Throwable) {
            GlobalAttach.DENIED
        } finally {
            runCatching { effect?.release() }
        }
    }

    /**
     * `RECORD_AUDIO` يُفحص **ولا يُطلب** هنا: الطلب قرارُ المستخدم من داخل قسمه بشرحه (أمر المالك)،
     * وهذا المسبار لا يفتح نافذة إذن من تلقاء نفسه.
     */
    private fun hasRecordAudio(context: Context): Boolean = runCatching {
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /**
     * سمات المازج (المعدّل/القناة/‏bit-perfect): تُسأل عن **مخرج حقيقيّ** — لأنّ السؤال بلا جهاز
     * سؤالٌ عن لا شيء. وإصدارٌ أقدم يعود `false` بتفصيلٍ مكتوب (`below-platform-version`) لا `null`،
     * لأنّ المنصّة القديمة **قاطعت** لا «لم تُقرأ».
     */
    private fun mixerAttributes(manager: AudioManager?): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        val audioManager = manager ?: return null
        return runCatching {
            val sink: AudioDeviceInfo = audioManager
                .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .firstOrNull { it.isSink } ?: return null
            audioManager.getSupportedMixerAttributes(sink).isNotEmpty()
        }.getOrNull()
    }

    /** توجيه صوت المكالمة: يُسأل عمّا تُعلنه المنصّة **من أجهزة مكالمة** الآن. */
    private fun communicationRouting(manager: AudioManager?): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val audioManager = manager ?: return null
        return runCatching { audioManager.availableCommunicationDevices.isNotEmpty() }.getOrNull()
    }

    /** مجموعات الجهارة (أندرويد ١١+): هل تُعيد المنصّة معرّفًا موجبًا لسمات الوسائط؟ */
    private fun volumeGroups(manager: AudioManager?): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val audioManager = manager ?: return null
        return runCatching {
            val media = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
            audioManager.getVolumeGroupIdForAttributes(media) > 0
        }.getOrNull()
    }

    private fun managerOf(context: Context): AudioManager? =
        runCatching { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }.getOrNull()

    /** جلسة المزج العامّ — الرقم من المنصّة بالاسم لا سحرًا في موضعين. */
    private const val GLOBAL_SESSION = 0
}
