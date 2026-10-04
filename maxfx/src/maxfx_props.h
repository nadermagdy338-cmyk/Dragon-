/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * MaxFx — **قناة التحكّم الوحيدة العالميّة**: خاصيّات النظام.
 *
 * **ولماذا ليست `AudioEffect.setParameter`:** القياس بـ`javap` على `android.jar` يقول إنّ
 * `setParameter`/`getParameter` والبانيّ **غير عامّين** (`@hide`)، والانعكاس محجوبٌ على أجهزة
 * (قياسٌ على جهاز المالك: `hidden-api-blocked-or-absent`) — فمؤثّرٌ يعتمد عليه لا يعمل «على أغلب
 * الهواتف»، وهو عكس الغاية. والخاصيّة يقرأها `audioserver` بلا صلاحيّة وبلا سياقٍ خاصّ، ويكتبها
 * الجذر (`setprop`) — فالمشروع كاملٌ على أيّ ROM.
 *
 * والقراءة **دوريّة** (مرّة كلّ ~250ms من `process`) لأنّ الخاصيّة لا تُنبّه عند التغيير — وهي
 * مقايضة مُعلنة: تأخيرٌ أقصى ربع ثانيةٍ لضبطٍ لا يطلب سرعةً (معادل · جير · عرض).
 *
 * وهذه الطبقة وحدها تختلف بين المضيف والجهاز: على Android تنفّذها `maxfx_props_android.c`
 * بـ`__system_property_get`، وعلى المضيف بديلٌ مُتحكَّم فيه من الاختبارات — فتُقاس دورة
 * «خاصية تتغيّر ← المعالجة تلتقطها» بلا جهاز.
 */
#ifndef MAXFX_PROPS_H
#define MAXFX_PROPS_H

#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * يقرأ قيمة خاصية نظام.
 *
 * @param key اسم الخاصية كاملًا (بلا اقتباس).
 * @param out مخزن النصّ.
 * @param out_len حجمه (يُكتب داخله NUL دائمًا حين تُوجد الخاصية).
 * @return 1 إن وُجدت الخاصية، 0 إن غابت (والغياب ليس صفرًا — ADR-07).
 */
int maxfx_prop_get(const char *key, char *out, size_t out_len);

#ifdef __cplusplus
}
#endif

#endif /* MAXFX_PROPS_H */
