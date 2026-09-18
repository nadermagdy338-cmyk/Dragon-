/*
 * Copyright (C) 2026-2027 MaxManager contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nd.max.core.hardware

import nd.max.core.maxai.MemoryStall

/**
 * قارئ ضغط الذاكرة من `/proc/pressure/memory`.
 *
 * القرار التصميمي الوحيد هنا: **التخزين السلبي للقدرة**. `RootFileAccess.read`
 * يجرّب IPC الجذر ثم قراءة الملف مباشرةً ثم **قشرة** — والقشرة الأخيرة هي
 * المشكلة: على نواة لا تصدّر PSI أو صلاحية مرفوضة، كان الاستقصاء كل ثانية
 * يُنشئ عملية قشرة كل ثانية إلى الأبد، أي أن قياسًا لا يُنتج رقمًا واحدًا كان
 * سيستهلك أكثر من كل ما يوفّره. لذلك:
 *
 *  - نتيجة **مدعومة** تُقرأ في كل مرة (قراءة ملف واحدة، رخيصة) فتبقى الأرقام طازجة.
 *  - نتيجة **غير مدعومة** تُحفظ فلا يُعاد سؤال النواة إلا كل [RETRY_AFTER_MS].
 *
 * وهي نفس قاعدة `Unsupported` في العقد: القدرة تُقاس مرة وتُعلن، ولا تُختبَر
 * على حساب المستخدم كل ثانية.
 */
object MemoryPressureReader {

    /** مسار PSI للذاكرة — ثابت في نواة لينكس منذ 4.20. */
    const val PATH = "/proc/pressure/memory"

    /** بعد كم يُعاد اختبار نواة أعلنت عدم الدعم (نادر: تحديث نواة لا يقع). */
    const val RETRY_AFTER_MS = 10 * 60_000L

    @Volatile private var cached: MemoryStall.Sample = MemoryStall.UNSUPPORTED

    /**
     * لحظة آخر استقصاء، أو `null` إن لم يقع استقصاء بعد.
     *
     * ولماذا `null` لا `0L`: الصفر لحظة زمنية صحيحة (بدء التشغيل)، فاستعماله
     * كعلامة «لم يُستقصَ بعد» كان سيمنع أول قراءة لمدة [RETRY_AFTER_MS] كاملة
     * على جهاز قُرئت ساعته عند الصفر — نفس عيب العلامة المُشفَّرة في ADR-33.
     */
    @Volatile private var lastProbeAtMs: Long? = null

    /**
     * يقرأ عيّنة الآن. آمن من أي خيط، ولا يرمي استثناء أبدًا: كل فشل قراءة أو
     * تحليل ينتهي بـ[MemoryStall.UNSUPPORTED] — الجهل يُعلن لا يُملأ.
     *
     * @param nowMs لحظة الاستدعاء (تُمرَّر لتُختبر سياسة إعادة الاختبار بلا وقت حقيقي).
     */
    fun read(nowMs: Long = System.currentTimeMillis()): MemoryStall.Sample {
        val previous = cached
        val probedAt = lastProbeAtMs
        if (!shouldProbe(previous, probedAt, nowMs)) return previous

        val text = runCatching { RootFileAccess.read(PATH) }.getOrNull()
        val sample = MemoryStall.parse(text, MemoryStall.DEFAULT_WINDOW_SECONDS)
        cached = sample
        lastProbeAtMs = nowMs
        return sample
    }

    /**
     * سياسة إعادة الاختبار — دالة نقيّة لها اختبارها: المدعوم يُقرأ دائمًا كي
     * تبقى الأرقام طازجة، وغير المدعوم يُترك حتى تنتهي [RETRY_AFTER_MS].
     */
    fun shouldProbe(
        cached: MemoryStall.Sample,
        lastProbeAtMs: Long?,
        nowMs: Long,
        retryAfterMs: Long = RETRY_AFTER_MS,
    ): Boolean = cached.measured ||
        lastProbeAtMs == null ||
        (nowMs - lastProbeAtMs) >= retryAfterMs
}
