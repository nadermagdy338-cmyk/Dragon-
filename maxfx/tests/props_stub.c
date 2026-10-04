/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * بديل قناة الخاصيّات على المضيف (`maxfx_prop_get`) — خريطةٌ في الذاكرة يملؤها الاختبار.
 *
 * وبه تُقاس **دورة «خاصية تتغيّر ← المعالجة تلتقطها»** كاملةً بلا جهاز: الغلاف يقرأ من هنا
 * كما يقرأ على Android من `__system_property_get`، والاختبار يكتب الخريطة كما يكتب الجذر
 * `setprop` — والسلوك بينهما واحد.
 */
#include "maxfx_props.h"

#include <stdio.h>
#include <string.h>

typedef struct {
    char key[96];
    char value[96];
    int used;
} stub_prop_t;

static stub_prop_t PROPS[64];

void test_prop_set(const char *key, const char *value)
{
    for (int i = 0; i < 64; i++) {
        if (PROPS[i].used && strcmp(PROPS[i].key, key) == 0) {
            snprintf(PROPS[i].value, sizeof(PROPS[i].value), "%s", value);
            return;
        }
    }
    for (int i = 0; i < 64; i++) {
        if (!PROPS[i].used) {
            snprintf(PROPS[i].key, sizeof(PROPS[i].key), "%s", key);
            snprintf(PROPS[i].value, sizeof(PROPS[i].value), "%s", value);
            PROPS[i].used = 1;
            return;
        }
    }
}

void test_prop_clear(void)
{
    memset(PROPS, 0, sizeof(PROPS));
}

int maxfx_prop_get(const char *key, char *out, size_t out_len)
{
    if (key == NULL || out == NULL || out_len == 0) return 0;
    for (int i = 0; i < 64; i++) {
        if (PROPS[i].used && strcmp(PROPS[i].key, key) == 0) {
            snprintf(out, out_len, "%s", PROPS[i].value);
            return 1;
        }
    }
    return 0; /* الغياب ليس صفرًا — يُعاد 0 ويبقى الإعداد على حاله (ADR-07). */
}
