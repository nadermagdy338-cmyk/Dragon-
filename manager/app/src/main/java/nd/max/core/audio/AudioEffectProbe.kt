/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * Audio — **مسبار المؤثرات**: `AudioEffect.queryEffects()` → وصف ما أعلنته المنصّة.
 *
 * **قراءة فقط ولا يمسّ الصوت:** لا يُنشئ `AudioEffect` ولا يفتح جلسة ولا يعلّق مؤثرًا — يسأل
 * المنصّة عمّا تُعلنه، وهذا لا يحتاج معرّف جلسة ولا إذنًا. وما يُكتب في مستوى أو مؤثر مؤجَّل
 * إلى `AU-03` وما بعده.
 *
 * **وحدٌّ مقيس (ADR-07):** كشفنا من مصدر المنصّة أن `AudioEffect.Descriptor` أربعة أعضاء:
 * `name` · `type` · `implementor` · `connectMode` — **ولا حقل «مُحمَّل مسبقًا»** (كلمة
 * `preload` لا ترد في `AudioEffect.java`). ⇒ ذلك العمود مُسقَط، ولا يُكتب مكانه صفر ولا وعد.
 */
package nd.max.core.audio

import android.media.audiofx.AudioEffect

object AudioEffectProbe {

    /**
     * كل مؤثّر أعلنته المنصّة، مرتَّبًا — أو `null` حين لا تُقرأ القائمة أصلًا
     * (‏`queryEffects` قد تُعيد `null` في المصدر نفسه). والقائمة الفارغة **قراءة**: المنصّة
     * أعلنت أنها لا تملك مؤثرات، وهي حالة تُقال بصراحة لا تُخلط بغياب القراءة.
     */
    fun effects(): List<AudioEffectInfo>? = runCatching {
        val descriptors = AudioEffect.queryEffects() ?: return null
        audioEffectsSorted(
            descriptors.map { descriptor ->
                AudioEffectInfo(
                    name = descriptor.name?.takeIf { it.isNotBlank() },
                    // النوع يُمرَّر نصًّا: الثوابت `EFFECT_TYPE_*` كائنات تُهيَّأ وقت التشغيل،
                    // فالمقارنة على النصّ تجعل القاعدة الصافية مقيسةً على JVM بلا أندرويد.
                    typeUuid = descriptor.type?.toString(),
                    implementor = descriptor.implementor?.takeIf { it.isNotBlank() },
                    connectMode = descriptor.connectMode?.takeIf { it.isNotBlank() },
                    // ومعرّف التنفيذ يُقرأ كما يُقرأ غيره: بعض المنصّات تُسجّل مؤثّرًا بمعرّفٍ غائب،
                    // فيبقى `null` **ولا يُستبدل** بمعرّف النوع — فالملء هنا يعني اكتشافًا كاذبًا.
                    uuid = descriptor.uuid?.toString()?.takeIf { it.isNotBlank() },
                )
            },
        )
    }.getOrNull()
}
