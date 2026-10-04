/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * Audio — **جردة واحدة**: ما يُعلنه هذا الجهاز، مقروءًا مرّةً واحدة.
 *
 * **ولماذا وُجد هذا الملفّ:** `AudioCapabilities` نقيّ و`AudioDeviceCatalog` و`AudioEffectProbe`
 * يلمسان المنصّة، فالشاشة كانت ستجمع ثلاث نداءات في ثلاثة مواضع وتُخزّن ثلاثة أحوال. والجردة
 * تجعل الحالة واحدة، وتُبقي **قرار العرض** في الشاشة: هنا لا نُنسّق نصوصًا ولا نُرتّب ترتيبًا
 * عرضيًّا — الترتيب في الطبقة الصافية، والصياغة في الموارد.
 *
 * **وكل حقل هنا قد يكون `null`، ومعناه «غير مقروء» لا صفر:** المنصّة قد تُعيد قائمة فارغة
 * (وهي **قراءة**: لا أجهزة/لا مؤثرات مُعلَنة) أو تُعجز القارئ فيبقى `null` (وهو **غياب قراءة**)
 * — والفرق يُعرض «لا مؤثرات مُعلَنة» مقابل «غير مقروء» ولا يُخلط أحدهما بالآخر (ADR-07).
 */
package nd.max.core.audio

import android.content.Context

/** لقطة واحدة من قراءة الصوت: كل حقل مستقلّ، و`null` فيه = «غير مقروء». */
data class AudioInventorySnapshot(
    val output: AudioOutputCapabilities?,
    val devices: List<AudioDeviceDescriptor>?,
    val effects: List<AudioEffectInfo>?,
)

object AudioInventory {

    /**
     * القراءة الكاملة، بلا استثناء يُفلت: كل قارئ يعود `null` عند العجز، فاللقطة لا تفشل —
     * تُعلن ما قُرئ وما لم يُقرأ.
     */
    fun read(context: Context): AudioInventorySnapshot = AudioInventorySnapshot(
        output = AudioDeviceCatalog.capabilities(context),
        devices = AudioDeviceCatalog.devices(context),
        effects = AudioEffectProbe.effects(),
    )
}
