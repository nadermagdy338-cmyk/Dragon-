/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * الصوت — **التقاط الطيف** (`AQ-08`): غلافٌ ضيّق حول `Visualizer`، **ولا شيء غيره في المشروع يلمسه**.
 *
 * **وشرطه مقيس:** `Visualizer` يحتاج `RECORD_AUDIO` **وجلسةً** — والإذن غير مُعلَن في بياننا قبل هذه
 * الموجة (مقيس في `AUDIO-ADVANCED-PLAN` §2.5). فلا يُنشأ هنا شيء إن لم يكن الإذن ممنوحًا: يعود
 * `null`، والشاشة تقول السبب من القدرات لا من فراغ.
 *
 * **ولا يُخترع حجم الالتقاط:** `Visualizer.setCaptureSize` **غير موجود في `android.jar` الذي يُصرَّف
 * عليه المشروع** (مقيس بـ`javap` في هذه الجولة) — فالحجم يبقى ما تختاره المنصّة، و[`audioSpectrumBandsOf`]
 * يقسم ما وصل فعلًا بلا افتراض حجم. ولو افترضناه لرسمنا نصف البيانات بصمت.
 *
 * **والنمط مُعلَن:** `SCALING_MODE_NORMALIZED` (فالرسم لا يقفز مع جهارة المصدر) و
 * `MEASUREMENT_MODE_PEAK_RMS` للذروة والـRMS — وكلاهما يُقرأ في [`AudioSpectrumFrame`].
 *
 * **والتحرير في `stop` شرط:** مُلتقِطٌ يبقى مشتغلًا يستهلك ويُبقي مؤشّر التسجيل مضاءً — وهذا أثرٌ
 * على المستخدم بعد مغادرة الشاشة.
 */
package nd.max.core.audio

import android.media.audiofx.Visualizer
import javax.inject.Inject
import javax.inject.Singleton

/** عدد نطاقات الرسم — **قرار عرضٍ واحد**، يُقال هنا ولا يتفرّق. */
const val AUDIO_SPECTRUM_BANDS = 32

@Singleton
class AudioSpectrumCapture @Inject constructor() {

    private var visualizer: Visualizer? = null

    /**
     * يبدأ الالتقاط على الجلسة العامة، ويُبلّغ بكل إطار.
     *
     * @param permissionGranted إفصاحٌ صريح لا يُخمَّن هنا: إن كان `false` **لا محاولة ولا استثناء** —
     *   فلا يظهر في السجلّ رفضٌ لم يُطلب، والشاشة تقول «يحتاج الإذن» لأنّ القدرات قالتها.
     * @return `null` حين لا يبدأ الالتقاط، **وليس** كائنًا صامتًا يوهم بأنه يعمل.
     */
    fun start(permissionGranted: Boolean, onFrame: (AudioSpectrumFrame) -> Unit): AudioSpectrumCapture? {
        if (!spectrumCapturable(permissionGranted)) return null
        if (visualizer != null) return this
        val created = runCatching { Visualizer(GLOBAL_SESSION) }.getOrNull() ?: return null
        val listener = object : Visualizer.OnDataCaptureListener {
            override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) = Unit

            override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                val measurement = runCatching {
                    Visualizer.MeasurementPeakRms().also { v?.getMeasurementPeakRms(it) }
                }.getOrNull()
                onFrame(
                    AudioSpectrumFrame(
                        bands = audioSpectrumBandsOf(fft, AUDIO_SPECTRUM_BANDS),
                        peakMb = measurement?.mPeak,
                        rmsMb = measurement?.mRms,
                    ),
                )
            }
        }
        val started = runCatching {
            created.scalingMode = Visualizer.SCALING_MODE_NORMALIZED
            created.measurementMode = Visualizer.MEASUREMENT_MODE_PEAK_RMS
            // والمعدّل نصف الأقصى المُعلَن — لا `20` مكتوبة بيد: الأقصى من المنصّة بالاسم.
            val rate = (Visualizer.getMaxCaptureRate() / 2).takeIf { it > 0 } ?: DEFAULT_RATE_MILLIHZ
            created.setDataCaptureListener(listener, rate, false, true)
            created.enabled = true
            created.enabled
        }.getOrDefault(false)
        if (!started) {
            runCatching { created.release() }
            return null
        }
        visualizer = created
        return this
    }

    /** يُوقف الالتقاط ويُحرّر المؤشّر — **يُنادى في `onDispose` وفي `onCleared`**. */
    fun stop() {
        val current = visualizer ?: return
        visualizer = null
        runCatching { current.enabled = false }
        runCatching { current.release() }
    }

    private companion object {
        const val GLOBAL_SESSION = 0

        /** نصف الأقصى ≈ ٢٠ إطارًا/ثانية ⇒ `10000` بالملّي‑هرتز — تُستعمل فقط إن أعلنت المنصّة صفرًا. */
        const val DEFAULT_RATE_MILLIHZ = 10_000
    }
}
