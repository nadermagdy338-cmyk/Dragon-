/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * ────────────────────────────── Attribution (Apache-2.0) ──────────────────────────────
 * **مُجزَّأ ومُصدَّر حرفيًّا من رؤوس AOSP الثلاثة** (نقلٌ من ترخيص متسامح بنسبة الفضل، ADR-55):
 *
 *   • `hardware/libhardware/include/hardware/audio_effect.h`
 *   • `system/media/audio/include/system/audio_effect.h`
 *   • `system/media/audio/include/system/audio_effect-base.h`
 *
 * التجزئة: حُذف ما لا يستعمله مؤثّرٌ برمجيٌّ (cutils · الوسائط · الأوامر الخاصّة بالـHIDL)،
 * وبقي **الحقول والتعاميم والأوامر كما هي حرفيًّا** — لأنّ هذا رأس **عقد ثنائيّ** مع `audioserver`:
 * قيمةٌ واحدة مُغيَّرة أو ترتيبُ حقلٍ مقلوب يعني مكتبةً لا تُحمَّل أو ذاكرةً تُفسد. والوحدات المُعرَّفة
 * هنا مطابقة لقيم AOSP (والتيارات العدديّة مكتوبة كما هي، لا مُحسوبة).
 *
 * ولا يُنشئ هذا الملفّ أيّ حق جديد: الأسماء والحقول من AOSP، والتعديل الوحيد هو الإبقاء على اللازم.
 * ─────────────────────────────────────────────────────────────────────────────────────
 */
#ifndef MAXFX_EFFECT_ABI_H
#define MAXFX_EFFECT_ABI_H

#include <errno.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ── من `system/audio-base.h`: مُعرّف UUID وقناع القناة ─────────────────────────────── */
typedef struct {
    uint32_t timeLow;
    uint16_t timeMid;
    uint16_t timeHiAndVersion;
    uint16_t clockSeq;
    uint8_t  node[6];
} audio_uuid_t;

typedef uint32_t audio_channel_mask_t;

/* AUDIO_CHANNEL_OUT_* (positional bits) — MONO = LEFT وحده، STEREO = LEFT|RIGHT. */
#define MAXFX_CHANNEL_OUT_MONO   0x1u
#define MAXFX_CHANNEL_OUT_STEREO 0x3u

/* AUDIO_FORMAT_PCM_* — القيم كما في `audio-base.h`. */
#define MAXFX_FORMAT_PCM_16_BIT 0x1u
#define MAXFX_FORMAT_PCM_FLOAT  0x5u

/* ── من `system/audio_effect.h`: الوحدات الأساسية ─────────────────────────────────── */

typedef audio_uuid_t effect_uuid_t;

#define EFFECT_STRING_LEN_MAX 64

#define EFFECT_UUID_INITIALIZER { 0xec7178ec, 0xe5e1, 0x4432, 0xa3f4, \
                                  { 0x46, 0x57, 0xe6, 0x79, 0x52, 0x10 } }

typedef struct effect_descriptor_s {
    effect_uuid_t type;
    effect_uuid_t uuid;
    uint32_t apiVersion;
    uint32_t flags;
    uint16_t cpuLoad;
    uint16_t memoryUsage;
    char name[EFFECT_STRING_LEN_MAX];
    char implementor[EFFECT_STRING_LEN_MAX];
} effect_descriptor_t;

/* **الترتيب مُلزم (ABI):** قيم التعداد الآتية صفرية وتتزايد، فهي مواضع لا أرقام تُختار. */
enum effect_command_e {
    EFFECT_CMD_INIT,
    EFFECT_CMD_SET_CONFIG,
    EFFECT_CMD_RESET,
    EFFECT_CMD_ENABLE,
    EFFECT_CMD_DISABLE,
    EFFECT_CMD_SET_PARAM,
    EFFECT_CMD_SET_PARAM_DEFERRED,
    EFFECT_CMD_SET_PARAM_COMMIT,
    EFFECT_CMD_GET_PARAM,
    EFFECT_CMD_SET_DEVICE,
    EFFECT_CMD_SET_VOLUME,
    EFFECT_CMD_SET_AUDIO_MODE,
    EFFECT_CMD_SET_CONFIG_REVERSE,
    EFFECT_CMD_SET_INPUT_DEVICE,
    EFFECT_CMD_GET_CONFIG,
    EFFECT_CMD_GET_CONFIG_REVERSE,
    EFFECT_CMD_GET_FEATURE_SUPPORTED_CONFIGS,
    EFFECT_CMD_GET_FEATURE_CONFIG,
    EFFECT_CMD_SET_FEATURE_CONFIG,
    EFFECT_CMD_SET_AUDIO_SOURCE,
    EFFECT_CMD_OFFLOAD,
    EFFECT_CMD_DUMP,
    EFFECT_CMD_FIRST_PROPRIETARY = 0x10000
};

/* وسائط الصوت متعدّدة القنوات دائمًا متشابكة (interleaved)، وعدد الإطارات هو عدد العيّنات
 * لكل القنوات في لحظةٍ واحدة. */
typedef struct audio_buffer_s {
    size_t frameCount;
    union {
        void*     raw;
        float*    f32;
        int32_t*  s32;
        int16_t*  s16;
        uint8_t*  u8;
    };
} audio_buffer_t;

typedef int32_t (*buffer_function_t)(void *cookie, audio_buffer_t *buffer);

typedef struct buffer_provider_s {
    buffer_function_t getBuffer;
    buffer_function_t releaseBuffer;
    void *cookie;
} buffer_provider_t;

enum {
    EFFECT_CONFIG_BUFFER   = 1 << 0,
    EFFECT_CONFIG_SMP_RATE = 1 << 1,
    EFFECT_CONFIG_CHANNELS = 1 << 2,
    EFFECT_CONFIG_FORMAT   = 1 << 3,
    EFFECT_CONFIG_ACC_MODE = 1 << 4,
    EFFECT_CONFIG_ALL      = EFFECT_CONFIG_BUFFER | EFFECT_CONFIG_SMP_RATE |
                             EFFECT_CONFIG_CHANNELS | EFFECT_CONFIG_FORMAT | EFFECT_CONFIG_ACC_MODE
};

typedef struct buffer_config_s {
    audio_buffer_t buffer;
    uint32_t samplingRate;
    uint32_t channels;
    buffer_provider_t bufferProvider;
    uint8_t format;
    uint8_t accessMode;
    uint16_t mask;
} buffer_config_t;

typedef struct effect_config_s {
    buffer_config_t inputCfg;
    buffer_config_t outputCfg;
} effect_config_t;

/* طلب المعامل: `data[]` تحمل المعامل ثمّ القيمة، والقيمة تبدأ **على حدّ 32-بت**:
 * padding = ((psize - 1)/sizeof(int) + 1) * sizeof(int) - psize. */
typedef struct effect_param_s {
    int32_t  status;
    uint32_t psize;
    uint32_t vsize;
    char     data[];
} effect_param_t;

#define EFFECT_PARAM_SIZE_MAX 65536

/* ── من `system/audio_effect-base.h`: أعلام المؤثّر — القيم العدديّة كما هي ─────────── */

enum {
    EFFECT_FLAG_TYPE_SHIFT = 0,
    EFFECT_FLAG_TYPE_SIZE = 3,
    EFFECT_FLAG_TYPE_MASK = 7,
    EFFECT_FLAG_TYPE_INSERT = 0,
    EFFECT_FLAG_TYPE_AUXILIARY = 1,
    EFFECT_FLAG_TYPE_REPLACE = 2,
    EFFECT_FLAG_TYPE_PRE_PROC = 3,
    EFFECT_FLAG_TYPE_POST_PROC = 4,
    EFFECT_FLAG_INSERT_SHIFT = 3,
    EFFECT_FLAG_INSERT_SIZE = 3,
    EFFECT_FLAG_INSERT_MASK = 56,
    EFFECT_FLAG_INSERT_ANY = 0,
    EFFECT_FLAG_INSERT_FIRST = 8,
    EFFECT_FLAG_INSERT_LAST = 16,
    EFFECT_FLAG_INSERT_EXCLUSIVE = 24,
    EFFECT_FLAG_VOLUME_SHIFT = 6,
    EFFECT_FLAG_VOLUME_SIZE = 3,
    EFFECT_FLAG_VOLUME_MASK = 448,
    EFFECT_FLAG_VOLUME_CTRL = 64,
    EFFECT_FLAG_VOLUME_IND = 128,
    EFFECT_FLAG_VOLUME_MONITOR = 192,
    EFFECT_FLAG_VOLUME_NONE = 0,
    EFFECT_FLAG_DEVICE_SHIFT = 9,
    EFFECT_FLAG_DEVICE_SIZE = 3,
    EFFECT_FLAG_DEVICE_MASK = 3584,
    EFFECT_FLAG_DEVICE_IND = 512,
    EFFECT_FLAG_DEVICE_NONE = 0,
    EFFECT_FLAG_INPUT_SHIFT = 12,
    EFFECT_FLAG_INPUT_SIZE = 2,
    EFFECT_FLAG_INPUT_MASK = 12288,
    EFFECT_FLAG_INPUT_DIRECT = 4096,
    EFFECT_FLAG_INPUT_PROVIDER = 8192,
    EFFECT_FLAG_INPUT_BOTH = 12288,
    EFFECT_FLAG_OUTPUT_SHIFT = 14,
    EFFECT_FLAG_OUTPUT_SIZE = 2,
    EFFECT_FLAG_OUTPUT_MASK = 49152,
    EFFECT_FLAG_OUTPUT_DIRECT = 16384,
    EFFECT_FLAG_OUTPUT_PROVIDER = 32768,
    EFFECT_FLAG_OUTPUT_BOTH = 49152,
    EFFECT_FLAG_HW_ACC_SHIFT = 16,
    EFFECT_FLAG_HW_ACC_SIZE = 2,
    EFFECT_FLAG_HW_ACC_MASK = 196608,
    EFFECT_FLAG_HW_ACC_SIMPLE = 65536,
    EFFECT_FLAG_HW_ACC_TUNNEL = 131072,
    EFFECT_FLAG_AUDIO_MODE_SHIFT = 18,
    EFFECT_FLAG_AUDIO_MODE_SIZE = 2,
    EFFECT_FLAG_AUDIO_MODE_MASK = 786432,
    EFFECT_FLAG_AUDIO_MODE_IND = 262144,
    EFFECT_FLAG_AUDIO_MODE_NONE = 0,
    EFFECT_FLAG_AUDIO_SOURCE_SHIFT = 20,
    EFFECT_FLAG_AUDIO_SOURCE_SIZE = 2,
    EFFECT_FLAG_AUDIO_SOURCE_MASK = 3145728,
    EFFECT_FLAG_AUDIO_SOURCE_IND = 1048576,
    EFFECT_FLAG_AUDIO_SOURCE_NONE = 0,
    EFFECT_FLAG_OFFLOAD_SHIFT = 22,
    EFFECT_FLAG_OFFLOAD_SIZE = 1,
    EFFECT_FLAG_OFFLOAD_MASK = 4194304,
    EFFECT_FLAG_OFFLOAD_SUPPORTED = 4194304,
    EFFECT_FLAG_NO_PROCESS_SHIFT = 23,
    EFFECT_FLAG_NO_PROCESS_SIZE = 1,
    EFFECT_FLAG_NO_PROCESS_MASK = 8388608,
    EFFECT_FLAG_NO_PROCESS = 8388608,
};

typedef enum {
    EFFECT_BUFFER_ACCESS_WRITE = 0,
    EFFECT_BUFFER_ACCESS_READ = 1,
    EFFECT_BUFFER_ACCESS_ACCUMULATE = 2,
} effect_buffer_access_e;

/* ── من `hardware/audio_effect.h`: واجهة التحكّم ومكتبة المؤثّرات ─────────────────── */

#define EFFECT_MAKE_API_VERSION(M, m)  (((M)<<16) | ((m) & 0xFFFF))
#define EFFECT_API_VERSION_MAJOR(v)    ((v)>>16)
#define EFFECT_API_VERSION_MINOR(v)    (((v)) & 0xFFFF)

#define EFFECT_CONTROL_API_VERSION EFFECT_MAKE_API_VERSION(2,0)

typedef struct effect_interface_s **effect_handle_t;

struct effect_interface_s {
    int32_t (*process)(effect_handle_t self,
                       audio_buffer_t *inBuffer,
                       audio_buffer_t *outBuffer);
    int32_t (*command)(effect_handle_t self,
                       uint32_t cmdCode,
                       uint32_t cmdSize,
                       void *pCmdData,
                       uint32_t *replySize,
                       void *pReplyData);
    int32_t (*get_descriptor)(effect_handle_t self,
                              effect_descriptor_t *pDescriptor);
    int32_t (*process_reverse)(effect_handle_t self,
                               audio_buffer_t *inBuffer,
                               audio_buffer_t *outBuffer);
};

#define EFFECT_LIBRARY_API_VERSION EFFECT_MAKE_API_VERSION(3,0)

#define AUDIO_EFFECT_LIBRARY_TAG ((('A') << 24) | (('E') << 16) | (('L') << 8) | ('T'))

typedef struct audio_effect_library_s {
    uint32_t tag;
    uint32_t version;
    const char *name;
    const char *implementor;

    int32_t (*create_effect)(const effect_uuid_t *uuid,
                             int32_t sessionId,
                             int32_t ioId,
                             effect_handle_t *pHandle);
    int32_t (*release_effect)(effect_handle_t handle);
    int32_t (*get_descriptor)(const effect_uuid_t *uuid,
                              effect_descriptor_t *pDescriptor);
    /* حقل 3.1 — نُبقي ترتيبه للتطابق مع المكتبات الحديثة، ولا نملؤه (المؤثّر لا يحتاجه). */
    int32_t (*create_effect_3_1)(const effect_uuid_t *uuid,
                                 int32_t sessionId,
                                 int32_t ioId,
                                 int32_t deviceId,
                                 effect_handle_t *pHandle);
} audio_effect_library_t;

/* اسم رمز المكتبة الذي يبحث عنه `EffectsFactory` — **لا يُغيَّر أبدًا**. */
#define AUDIO_EFFECT_LIBRARY_INFO_SYM         AELI
#define AUDIO_EFFECT_LIBRARY_INFO_SYM_AS_STR  "AELI"

#ifdef __cplusplus
}
#endif

#endif /* MAXFX_EFFECT_ABI_H */
