/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * MaxFx — **غلاف مؤثّر أندرويد النظاميّ** (`audio_effect_library_t` + `effect_interface_s`).
 *
 * هذه هي الطبقة التي يحمّلها `audioserver` من `system/lib64/soundfx/libmaxfx.so` (ومثيله 32-بت) بعد تركيب طبقة
 * `AQ-09` — والآلية فكرة ViPER4Android (المؤثّر النظاميّ في سلسلة `audioserver`) **بلا سطرٍ
 * واحد من كوده** (GPL-3.0 ⇒ صفر كود، ADR-55): العقود هنا من AOSP (Apache-2.0، بنسبة الفضل)،
 * والمعالجة من `maxfx_dsp` ملكُنا.
 *
 * **وقناة التحكّم هي الخاصيّات** (`persist.audio.maxfx.*`) لا `AudioEffect.setParameter` — لأنّه
 * `@hide` ومحجوبٌ على أجهزة (قياسٌ مُثبَت)، والخاصية يقرأها الجميع. والقراءة دوريّة (~250ms)
 * من `process` — تأخيرُها مُعلن، وبدائلها (نداءٌ مباشر عبر `EFFECT_CMD_SET_PARAM`) مُطبَّقة أيضًا
 * لمن يملك القدرة.
 *
 * **و`process()` آمنة للزمن الحقيقيّ:** لا `malloc` ولا `lock` ولا `I/O` — مكدّسٌ بحجم 256 إطارًا
 * وفكُّ تشابكٍ فيه. والتحويل بين `s16`/`f32` هنا، والمعالجة في النواة على `float`.
 */
#include "maxfx_effect.h"

#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "maxfx_dsp.h"

/* ── الهويّة ─────────────────────────────────────────────────────────────────────── */

static const effect_uuid_t MAXFX_TYPE_UUID = {
    0x8f2c4a19, 0x5d3b, 0x4c8e, 0x9a71, { 0x2b, 0x60, 0xd4, 0x1f, 0x8c, 0x33 }
};
static const effect_uuid_t MAXFX_IMPL_UUID = {
    0x3b7e5c02, 0x91af, 0x4d6b, 0x8c14, { 0x7e, 0x2a, 0x50, 0xb9, 0xd6, 0x41 }
};

static int hex_val(char c)
{
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
}

int maxfx_uuid_parse(const char *str, effect_uuid_t *out)
{
    /* الصيغة: 8-4-4-4-12 حرفيًّا مع الشرطات في مواضعها — ولا يُقبل ما دون ذلك. */
    static const int DASH[4] = { 8, 13, 18, 23 };
    if (str == NULL || out == NULL) return -1;
    int digits = 0;
    uint8_t bytes[16];
    for (int i = 0; i < 36; i++) {
        if (i == DASH[0] || i == DASH[1] || i == DASH[2] || i == DASH[3]) {
            if (str[i] != '-') return -1;
            continue;
        }
        int h = hex_val(str[i]);
        if (h < 0) return -1;
        if (digits % 2 == 0) bytes[digits / 2] = (uint8_t)(h << 4);
        else bytes[digits / 2] |= (uint8_t)h;
        digits++;
    }
    if (str[36] != '\0' || digits != 32) return -1;
    out->timeLow = ((uint32_t)bytes[0] << 24) | ((uint32_t)bytes[1] << 16) |
                   ((uint32_t)bytes[2] << 8) | bytes[3];
    out->timeMid = (uint16_t)(((uint32_t)bytes[4] << 8) | bytes[5]);
    out->timeHiAndVersion = (uint16_t)(((uint32_t)bytes[6] << 8) | bytes[7]);
    out->clockSeq = (uint16_t)(((uint32_t)bytes[8] << 8) | bytes[9]);
    for (int i = 0; i < 6; i++) out->node[i] = bytes[10 + i];
    return 0;
}

void maxfx_descriptor(effect_descriptor_t *out)
{
    memset(out, 0, sizeof(*out));
    out->type = MAXFX_TYPE_UUID;
    out->uuid = MAXFX_IMPL_UUID;
    out->apiVersion = EFFECT_CONTROL_API_VERSION;
    /* مؤثّر إدراجٍ في آخر السلسلة، بلا ادّعاء عمليّات الحجم (`VOLUME_*`): نحن لا نتولّى الصوت. */
    out->flags = EFFECT_FLAG_TYPE_INSERT | EFFECT_FLAG_INSERT_LAST | EFFECT_FLAG_VOLUME_NONE;
    out->cpuLoad = 100;      /* تقديرٌ مُعلن لا قياس: ~1% نواة على 48k. */
    out->memoryUsage = 20;   /* بالكيلوبايت: الحالة + مكدّس الكتل. */
    snprintf(out->name, EFFECT_STRING_LEN_MAX, "%s", MAXFX_EFFECT_NAME);
    snprintf(out->implementor, EFFECT_STRING_LEN_MAX, "%s", "MaxManager Project");
}

/* ── الوحدة ─────────────────────────────────────────────────────────────────────── */

#define MAXFX_BLOCK_FRAMES 256

typedef struct maxfx_module_s {
    const struct effect_interface_s *itfe; /* يجب أن يكون أوّل حقل: `effect_handle_t` == &itfe */
    maxfx_state_t dsp;
    maxfx_config_t cfg;
    effect_config_t config;
    int configured;
    int framework_enabled;
    int channels;              /* 1 أو 2؛ وما عداهما يُتخطّى (مُعلن). */
    uint8_t format;            /* MAXFX_FORMAT_PCM_16_BIT أو MAXFX_FORMAT_PCM_FLOAT. */
    int accumulate;            /* EFFECT_BUFFER_ACCESS_ACCUMULATE. */
    int32_t session_id;
    int32_t io_id;
    long frames_since_props;   /* عدّاد القراءة الدوريّة للخاصيّات. */
    long prop_interval_frames; /* ~250ms بالعيّنات. */
} maxfx_module_t;

/* ── الخاصيّات ← الإعداد ────────────────────────────────────────────────────────── */

static int prop_apply(maxfx_module_t *mod, const maxfx_param_def_t *p)
{
    char key[96];
    char value[96];
    snprintf(key, sizeof(key), "%s%s", MAXFX_PROP_PREFIX, p->key);
    if (!maxfx_prop_get(key, value, sizeof(value))) return 0; /* غياب ≠ صفر (ADR-07). */
    if (p->is_int) {
        int32_t raw = (int32_t)strtol(value, NULL, 10);
        return maxfx_config_set_param(&mod->cfg, p->id, &raw, sizeof(raw));
    }
    float f = strtof(value, NULL);
    return maxfx_config_set_param(&mod->cfg, p->id, &f, sizeof(f));
}

static void props_refresh(maxfx_module_t *mod)
{
    int changed = 0;
    int n = maxfx_param_count();
    for (int i = 0; i < n; i++) {
        changed |= prop_apply(mod, maxfx_param_at(i));
    }
    if (changed) maxfx_apply_config(&mod->dsp, &mod->cfg);
}

/* ── واجهة المؤثّر ─────────────────────────────────────────────────────────────── */

static inline int16_t clamp_s16(float x)
{
    float y = x * 32767.0f;
    if (y > 32767.0f) return 32767;
    if (y < -32768.0f) return -32768;
    return (int16_t)lrintf(y);
}

static int32_t maxfx_fx_process(effect_handle_t self, audio_buffer_t *inBuffer, audio_buffer_t *outBuffer)
{
    maxfx_module_t *mod = (maxfx_module_t *)self;
    if (mod == NULL || !mod->configured) return -EINVAL;
    if (inBuffer == NULL || outBuffer == NULL) return -EINVAL;
    if (inBuffer->raw == NULL || outBuffer->raw == NULL) return -EINVAL;

    size_t frames = inBuffer->frameCount;
    if (frames == 0) return 0;

    mod->frames_since_props += (long)frames;
    if (mod->frames_since_props >= mod->prop_interval_frames) {
        mod->frames_since_props = 0;
        props_refresh(mod);
    }

    /* بلا معالجة (معطّلة على مستوى العتاد أو غير مدعومة القنوات/الصيغة): نسخٌ أو تجميع. */
    if (!mod->framework_enabled || mod->channels < 1 || mod->channels > 2) {
        if (inBuffer->raw != outBuffer->raw) {
            if (mod->accumulate) {
                if (mod->format == MAXFX_FORMAT_PCM_FLOAT) {
                    for (size_t i = 0; i < frames * (size_t)mod->channels; i++)
                        outBuffer->f32[i] += inBuffer->f32[i];
                } else {
                    for (size_t i = 0; i < frames * (size_t)mod->channels; i++)
                        outBuffer->s16[i] = (int16_t)clamp_s16(
                            (float)outBuffer->s16[i] / 32767.0f + (float)inBuffer->s16[i] / 32767.0f);
                }
            } else if (mod->format == MAXFX_FORMAT_PCM_FLOAT) {
                memcpy(outBuffer->raw, inBuffer->raw, frames * (size_t)mod->channels * sizeof(float));
            } else {
                memcpy(outBuffer->raw, inBuffer->raw, frames * (size_t)mod->channels * sizeof(int16_t));
            }
        }
        return 0;
    }

    size_t done = 0;
    while (done < frames) {
        size_t block = frames - done;
        if (block > MAXFX_BLOCK_FRAMES) block = MAXFX_BLOCK_FRAMES;
        float left[MAXFX_BLOCK_FRAMES], right[MAXFX_BLOCK_FRAMES];

        if (mod->format == MAXFX_FORMAT_PCM_FLOAT) {
            const float *in = inBuffer->f32 + done * (size_t)mod->channels;
            for (size_t i = 0; i < block; i++) {
                left[i] = in[i * (size_t)mod->channels];
                right[i] = (mod->channels == 2) ? in[i * 2 + 1] : in[i];
            }
        } else {
            const int16_t *in = inBuffer->s16 + done * (size_t)mod->channels;
            for (size_t i = 0; i < block; i++) {
                left[i] = (float)in[i * (size_t)mod->channels] / 32768.0f;
                right[i] = (mod->channels == 2) ? (float)in[i * 2 + 1] / 32768.0f : left[i];
            }
        }

        /* أحاديّ ⇒ المعالجة على نسختين متطابقتين والنسخة الثانية تُهمَل (العرض أحاديّ آمن). */
        maxfx_process(&mod->dsp, left, right, (int)block);

        if (mod->format == MAXFX_FORMAT_PCM_FLOAT) {
            float *out = outBuffer->f32 + done * (size_t)mod->channels;
            for (size_t i = 0; i < block; i++) {
                if (mod->accumulate) {
                    out[i * (size_t)mod->channels] += left[i];
                    if (mod->channels == 2) out[i * 2 + 1] += right[i];
                } else {
                    out[i * (size_t)mod->channels] = left[i];
                    if (mod->channels == 2) out[i * 2 + 1] = right[i];
                }
            }
        } else {
            int16_t *out = outBuffer->s16 + done * (size_t)mod->channels;
            for (size_t i = 0; i < block; i++) {
                if (mod->accumulate) {
                    out[i * (size_t)mod->channels] = (int16_t)clamp_s16(
                        (float)out[i * (size_t)mod->channels] / 32767.0f + left[i]);
                    if (mod->channels == 2) out[i * 2 + 1] = (int16_t)clamp_s16(
                        (float)out[i * 2 + 1] / 32767.0f + right[i]);
                } else {
                    out[i * (size_t)mod->channels] = clamp_s16(left[i]);
                    if (mod->channels == 2) out[i * 2 + 1] = clamp_s16(right[i]);
                }
            }
        }
        done += block;
    }
    return 0;
}

static int32_t maxfx_fx_get_descriptor(effect_handle_t self, effect_descriptor_t *pDescriptor)
{
    (void)self;
    if (pDescriptor == NULL) return -EINVAL;
    maxfx_descriptor(pDescriptor);
    return 0;
}

/* قيمتُ `effect_param_t`: القيمة تبدأ على حدّ 32-بت بعد المعامل. */
static size_t param_value_offset(size_t psize)
{
    return ((psize - 1) / sizeof(int32_t) + 1) * sizeof(int32_t);
}

static int32_t maxfx_fx_command(effect_handle_t self, uint32_t cmdCode, uint32_t cmdSize,
                                void *pCmdData, uint32_t *replySize, void *pReplyData)
{
    maxfx_module_t *mod = (maxfx_module_t *)self;
    if (mod == NULL) return -EINVAL;

    switch (cmdCode) {
    case EFFECT_CMD_INIT:
        return 0;

    case EFFECT_CMD_SET_CONFIG: {
        if (pCmdData == NULL || cmdSize != sizeof(effect_config_t)) return -EINVAL;
        if (pReplyData == NULL || replySize == NULL || *replySize < sizeof(int32_t)) return -EINVAL;
        memcpy(&mod->config, pCmdData, sizeof(effect_config_t));
        uint32_t rate = mod->config.outputCfg.samplingRate;
        if (rate == 0) rate = mod->config.inputCfg.samplingRate;
        if (rate == 0) rate = 48000;
        maxfx_state_init(&mod->dsp, (float)rate);
        maxfx_apply_config(&mod->dsp, &mod->cfg);
        uint32_t mask = mod->config.outputCfg.channels;
        mod->channels = (int)__builtin_popcount(mask ? mask : MAXFX_CHANNEL_OUT_STEREO);
        mod->format = mod->config.outputCfg.format;
        if (mod->format != MAXFX_FORMAT_PCM_FLOAT) mod->format = MAXFX_FORMAT_PCM_16_BIT;
        mod->accumulate = (mod->config.outputCfg.accessMode == EFFECT_BUFFER_ACCESS_ACCUMULATE);
        mod->configured = 1;
        mod->prop_interval_frames = (long)(rate / 4);
        mod->frames_since_props = 0;
        props_refresh(mod);
        int32_t status = 0;
        memcpy(pReplyData, &status, sizeof(status));
        *replySize = sizeof(status);
        return 0;
    }

    case EFFECT_CMD_RESET:
        maxfx_state_reset(&mod->dsp);
        if (replySize != NULL) *replySize = 0;
        return 0;

    case EFFECT_CMD_ENABLE:
        mod->framework_enabled = 1;
        if (replySize != NULL) *replySize = 0;
        return 0;

    case EFFECT_CMD_DISABLE:
        mod->framework_enabled = 0;
        if (replySize != NULL) *replySize = 0;
        return 0;

    case EFFECT_CMD_SET_PARAM: {
        if (pCmdData == NULL || cmdSize < sizeof(effect_param_t)) return -EINVAL;
        if (pReplyData == NULL || replySize == NULL || *replySize < sizeof(int32_t)) return -EINVAL;
        effect_param_t *ep = (effect_param_t *)pCmdData;
        int32_t status = -EINVAL;
        if (ep->psize == sizeof(int32_t) &&
            cmdSize >= sizeof(effect_param_t) + param_value_offset(ep->psize) + ep->vsize) {
            int32_t id;
            memcpy(&id, ep->data, sizeof(id));
            const void *value = ep->data + param_value_offset(ep->psize);
            if (maxfx_config_set_param(&mod->cfg, id, value, ep->vsize)) {
                maxfx_apply_config(&mod->dsp, &mod->cfg);
                status = 0;
            } else if (maxfx_param_by_id(id) != NULL) {
                status = 0; /* مُستقبلٌ صحيح بلا تغيير — ليس خطأً. */
            }
        }
        memcpy(pReplyData, &status, sizeof(status));
        *replySize = sizeof(status);
        return 0;
    }

    case EFFECT_CMD_GET_PARAM: {
        if (pCmdData == NULL || cmdSize < sizeof(effect_param_t)) return -EINVAL;
        if (pReplyData == NULL || replySize == NULL) return -EINVAL;
        effect_param_t *req = (effect_param_t *)pCmdData;
        if (req->psize != sizeof(int32_t)) return -EINVAL;
        int32_t id;
        memcpy(&id, req->data, sizeof(id));
        const maxfx_param_def_t *p = maxfx_param_by_id(id);
        if (p == NULL) return -EINVAL;
        size_t vsize = p->is_int ? sizeof(int32_t) : sizeof(float);
        size_t total = sizeof(effect_param_t) + param_value_offset(req->psize) + vsize;
        if (*replySize < total) return -EINVAL;
        effect_param_t *reply = (effect_param_t *)pReplyData;
        reply->status = 0;
        reply->psize = req->psize;
        reply->vsize = (uint32_t)vsize;
        memcpy(reply->data, req->data, req->psize);
        if (!maxfx_config_get_param(&mod->cfg, id, reply->data + param_value_offset(req->psize), &vsize)) {
            return -EINVAL;
        }
        *replySize = (uint32_t)total;
        return 0;
    }

    case EFFECT_CMD_GET_CONFIG: {
        if (pReplyData == NULL || replySize == NULL || *replySize < sizeof(effect_config_t)) return -EINVAL;
        memcpy(pReplyData, &mod->config, sizeof(effect_config_t));
        *replySize = sizeof(effect_config_t);
        return 0;
    }

    case EFFECT_CMD_SET_VOLUME:
    case EFFECT_CMD_SET_DEVICE:
    case EFFECT_CMD_SET_AUDIO_MODE:
    case EFFECT_CMD_SET_INPUT_DEVICE:
    case EFFECT_CMD_SET_AUDIO_SOURCE:
    case EFFECT_CMD_SET_CONFIG_REVERSE:
    case EFFECT_CMD_OFFLOAD:
    case EFFECT_CMD_DUMP:
        /* نُبلِّغ القبول بلا أثر: نحن لا نتولّى الحجم ولا نطلب مرجعًا — والصمت هنا قبولٌ لا إهمال. */
        if (replySize != NULL) *replySize = 0;
        return 0;

    default:
        return -EINVAL;
    }
}

static const struct effect_interface_s MAXFX_INTERFACE = {
    maxfx_fx_process,
    maxfx_fx_command,
    maxfx_fx_get_descriptor,
    NULL, /* process_reverse: لا حاجة بمرجع. */
};

/* ── مكتبة المؤثّرات — الرمز المُصدَّر `AELI` ──────────────────────────────────── */

static int32_t maxfx_create_effect(const effect_uuid_t *uuid, int32_t sessionId, int32_t ioId,
                                   effect_handle_t *pHandle)
{
    if (uuid == NULL || pHandle == NULL) return -EINVAL;
    if (memcmp(uuid, &MAXFX_IMPL_UUID, sizeof(*uuid)) != 0) return -ENOENT;

    maxfx_module_t *mod = (maxfx_module_t *)calloc(1, sizeof(*mod));
    if (mod == NULL) return -ENOMEM;
    mod->itfe = &MAXFX_INTERFACE;
    mod->session_id = sessionId;
    mod->io_id = ioId;
    mod->format = MAXFX_FORMAT_PCM_16_BIT;
    mod->channels = 2;
    mod->prop_interval_frames = 12000; /* 250ms عند 48k حتى تأتي `SET_CONFIG`. */
    maxfx_config_default(&mod->cfg);
    maxfx_state_init(&mod->dsp, 48000.0f);
    props_refresh(mod);
    *pHandle = (effect_handle_t)mod;
    return 0;
}

static int32_t maxfx_release_effect(effect_handle_t handle)
{
    if (handle == NULL) return -EINVAL;
    free(handle);
    return 0;
}

static int32_t maxfx_get_descriptor(const effect_uuid_t *uuid, effect_descriptor_t *pDescriptor)
{
    if (uuid == NULL || pDescriptor == NULL) return -EINVAL;
    if (memcmp(uuid, &MAXFX_IMPL_UUID, sizeof(*uuid)) != 0) return -ENOENT;
    maxfx_descriptor(pDescriptor);
    return 0;
}

audio_effect_library_t AELI = {
    .tag = AUDIO_EFFECT_LIBRARY_TAG,
    .version = EFFECT_LIBRARY_API_VERSION,
    .name = MAXFX_LIBRARY_NAME,
    .implementor = "MaxManager Project",
    .create_effect = maxfx_create_effect,
    .release_effect = maxfx_release_effect,
    .get_descriptor = maxfx_get_descriptor,
    .create_effect_3_1 = NULL,
};
