/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * تنفيذ قناة الخاصيّات على Android — `__system_property_get` من Bionic.
 *
 * قراءةٌ بلا صلاحيّة (خاصيةٌ عامّة)، وطول القيمة محدودٌ عند Bionic (‏91 محرفًا + NUL) —
 * وكلّ قيمتنا أرقامٌ قصيرة، فلا يُقطع شيء. وهذا الملفّ **لا يُترجم على المضيف** أصلًا
 * (يبقى في `Android.mk` وحده)، والبديل المضيفيّ يوفّره `maxfx/tests/props_stub.c`.
 */
#include "maxfx_props.h"

#include <sys/system_properties.h>

int maxfx_prop_get(const char *key, char *out, size_t out_len)
{
    if (key == NULL || out == NULL || out_len == 0) return 0;
    char value[PROP_VALUE_MAX];
    int len = __system_property_get(key, value);
    if (len <= 0) return 0;
    /* والقصّ عند الحجم المطلوب مع NUL — لا فيض، وقيمتنا الرقميّة أقصر من الحدّ. */
    size_t copy = ((size_t)len < out_len - 1) ? (size_t)len : out_len - 1;
    for (size_t i = 0; i < copy; i++) out[i] = value[i];
    out[copy] = '\0';
    return 1;
}
