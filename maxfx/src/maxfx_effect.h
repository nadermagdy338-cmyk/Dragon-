/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * MaxFx — هويّة المؤثّر (UUIDs · أسماء · بادئة الخاصيّات).
 *
 * **وهذه القيم عقدٌ ثلاثيّ:** الرمز هنا، والنصّ في `core/audio/MaxFxModel.kt`، وصفوف
 * `fixtures/contracts/maxfx_identity.tsv` — فاختبارٌ لكل طرفٍ يقرأ العقد ويقارن، ولا تتغيّر
 * هويّة مؤثّرٍ على جهازٍ مثبَّت دون أن يسقط قياس.
 */
#ifndef MAXFX_EFFECT_H
#define MAXFX_EFFECT_H

#include "maxfx_effect_abi.h"
#include "maxfx_props.h"

#ifdef __cplusplus
extern "C" {
#endif

/* نوع المؤثّر (العائلة) — ومُنفِّذه (هذا المحرّك بالذات). وصيغة النصّ هي صيغة UUID القياسيّة. */
#define MAXFX_TYPE_UUID_STR "8f2c4a19-5d3b-4c8e-9a71-2b60d41f8c33"
#define MAXFX_IMPL_UUID_STR "3b7e5c02-91af-4d6b-8c14-7e2a50b9d641"

/* بادئة خاصيّات التحكّم — كل معاملٍ في العقد لاحقته `<key>` تحتها. */
#define MAXFX_PROP_PREFIX "persist.audio.maxfx."

/* اسم المكتبة والمؤثّر كما يظهران في `queryEffects()` ووثيقة التهيئة. */
#define MAXFX_LIBRARY_NAME "MaxManager MaxFx"
#define MAXFX_EFFECT_NAME "MaxFx Deep Audio Engine"

/* رمز مكتبة المؤثّرات — **اسمه المُصدَّر حرفيًّا** هو ما يبحث عنه `EffectsFactory`. */
extern audio_effect_library_t AELI;

/** يحلّ نصًّا بصيغة UUID القياسيّة إلى `effect_uuid_t` — يُعاد 0 عند النجاح. */
int maxfx_uuid_parse(const char *str, effect_uuid_t *out);

/** الوصف الوحيد للمؤثّر (يملؤه الغلاف والاختبار يقارنه بالعقد). */
void maxfx_descriptor(effect_descriptor_t *out);

#ifdef __cplusplus
}
#endif

#endif /* MAXFX_EFFECT_H */
