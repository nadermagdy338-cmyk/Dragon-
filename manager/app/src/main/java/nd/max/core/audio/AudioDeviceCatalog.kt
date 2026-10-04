/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * Audio — **قارئ الأجهزة**: `getDevices` ومعدّل العيّنة والدورة الحيّة.
 *
 * **قراءةٌ فقط وبلا إذن:** `getDevices` و`getProperty` و`registerAudioDeviceCallback` لا تحتاج
 * `MODIFY_AUDIO_SETTINGS` ولا `RECORD_AUDIO` — ولذلك لا يُعلن البيان شيئًا في هذه المرحلة،
 * والإعلان يأتي مع الكتابة (`AU-03`) بما تحتاجه فعلًا لا أكثر.
 *
 * وكل دالّة `runCatching` وتُرجع `null` عند العجز: «غير مقروء» لا صفرًا (ADR-07).
 */
package nd.max.core.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

object AudioDeviceCatalog {

    /**
     * ما تُعلنه المنصّة عن المخرج الأساسيّ + جردة المخارج. والخاصيّتان نصّيّتان من المنصّة،
     * وتحويلهما وترتيب الجردة **في `audioOutputCapabilitiesOf` الصافية** لا هنا — فما يُقاس على
     * الـJVM لا يكون في دالّة تلمس `AudioManager`. ورقمٌ لا يُقرأ يبقى `null` ولا يُكمَّل بـ`48000`
     * (وهو الشائع فيذكر القارئ رقمًا لم يُقرأ).
     */
    fun capabilities(context: Context): AudioOutputCapabilities? = runCatching {
        val manager = managerOf(context) ?: return null
        audioOutputCapabilitiesOf(
            sampleRateText = manager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE),
            framesPerBufferText = manager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER),
            devices = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map(::descriptorOf),
        )
    }.getOrNull()

    /** جردة الأجهزة بالطلب الذي يُمرَّر (مخارج · مداخل · الكل) — مرتَّبة بنفس القاعدة. */
    fun devices(context: Context, flags: Int = AudioManager.GET_DEVICES_ALL): List<AudioDeviceDescriptor>? =
        runCatching {
            val manager = managerOf(context) ?: return null
            audioDevicesSorted(manager.getDevices(flags).map(::descriptorOf))
        }.getOrNull()

    /**
     * تدفّق حيّ: المنصّة تنادي عند إضافة جهاز أو نزعه (سماعة رأس تُوصل · بلوتوث يصل).
     *
     * **والمرجع يُعاد لصاحبه:** الدالّة تُعيد إلغاءً يُنادى عند خروج الشاشة، فلا يبقى مستمع
     * حيّ بعدها (`LifecycleStartEffect`-style في الاستوديو). والإلغاء `runCatching` كسائر
     * مسار القراءة — لا يرمي على مستدعٍ انتهى دوره.
     */
    fun observeDevices(context: Context, onChanged: () -> Unit): (() -> Unit)? = runCatching {
        val manager = managerOf(context) ?: return null
        // والتوقيع **غير قابل للعدم** كما في المنصّة (`AudioDeviceCallback.onAudioDevicesAdded`
        // يأخذ `AudioDeviceInfo[]` بلا `@Nullable`) — فلا نستقبل `null` بلا سبب.
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = onChanged()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = onChanged()
        }
        manager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        return { runCatching { manager.unregisterAudioDeviceCallback(callback) } }
    }.getOrNull()

    private fun managerOf(context: Context): AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    /**
     * جهاز المنصّة ← نموذجنا. **و`getAddress()` لا يُقرأ بقصد**: عنوان جهاز بلوتوث معرّفٌ
     * شخصيّ، ولا حاجة له لقراءة تسمية جهاز أو مداه.
     */
    private fun descriptorOf(info: AudioDeviceInfo) = AudioDeviceDescriptor(
        id = info.id,
        productName = info.productName?.toString()?.takeIf { it.isNotBlank() },
        typeCode = info.type,
        isSink = info.isSink,
        // والثلاثة `@NonNull int[]` في المصدر نفسه، فالقائمة الفارغة **قراءة**: «لا يعلن العتاد
        // معدّلات مقيَّدة» — لا «غير مقروء».
        sampleRatesHz = info.sampleRates.toList(),
        channelCounts = info.channelCounts.toList(),
        encodings = info.encodings.toList(),
    )
}
