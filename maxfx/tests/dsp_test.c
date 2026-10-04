/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * MaxFx — حزمة القياس على المضيف (`make -C maxfx/tests run self-check`).
 *
 * ثلاث طبقات من الدعاوى، كلّها قابلة للتكذيب:
 *   ① **العقد:** جدول المعاملات في C يطابق `fixtures/contracts/maxfx_params.tsv` صفاً صفّ،
 *     والتطبيع يفعل بالضبط ما يقول الجدول (الحدّ · الافتراض · `NaN` ⇐ الافتراض لا صفر).
 *   ② **النواة:** الحياد حرفيّ · التجاوز حرفيّ · الكسب · المعادل · العرض · الأنبوب · الضاغط ·
 *     المحدِّد · دخولٌ مشوّهٌ يخرج سليمًا — وكلٌّ **برقمٍ يُقاس** لا بعبارة «يعمل».
 *   ③ **الغلاف:** عقد `audioserver` (الرمز `AELI` · الوصف · `SET_CONFIG` · `SET_PARAM`/`GET_PARAM`)
 *     ودورة الخاصيّة الكاملة على مسوّدات `s16` حقيقيّة.
 *
 * و`self-check` يحقن طفراتٍ في المصدر ويستوجب أن **تسقط** المجموعة — فمجموعةٌ لا تفشل أبدًا
 * لا تُثبت شيئًا (درس `archdaemon/tests` نفسه).
 */
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "maxfx_dsp.h"
#include "maxfx_effect.h"

#ifndef FIXTURE_DIR
#error "FIXTURE_DIR must point at fixtures/contracts (see Makefile)"
#endif

/* من props_stub.c — الخريطة التي يكتبها الاختبار كما يكتب الجذر `setprop`. */
void test_prop_set(const char *key, const char *value);
void test_prop_clear(void);

static int g_pass = 0, g_fail = 0;

static void check(int cond, const char *name)
{
    if (cond) {
        g_pass++;
    } else {
        g_fail++;
        printf("  ✗ FAIL %s\n", name);
    }
}

static void check_near(double got, double want, double tol, const char *name)
{
    double err = fabs(got - want);
    if (err <= tol) {
        g_pass++;
    } else {
        g_fail++;
        printf("  ✗ FAIL %s: got=%.6f want=%.6f tol=%.6f\n", name, got, want, tol);
    }
}

/* ── أدوات الإشارة ─────────────────────────────────────────────────────────────── */

static void gen_sine(float *l, float *r, int frames, float fs, float freq, float amp_l, float amp_r)
{
    for (int i = 0; i < frames; i++) {
        float s = sinf(2.0f * 3.14159265358979323846f * freq * (float)i / fs);
        l[i] = amp_l * s;
        r[i] = amp_r * s;
    }
}

static double rms_of(const float *x, int from, int to)
{
    double acc = 0.0;
    for (int i = from; i < to; i++) acc += (double)x[i] * (double)x[i];
    return sqrt(acc / (double)(to - from));
}

static double peak_of(const float *x, int from, int to)
{
    double p = 0.0;
    for (int i = from; i < to; i++) {
        double a = fabs((double)x[i]);
        if (a > p) p = a;
    }
    return p;
}

/* معالجة كاملة على كتل — كما يفعل الغلاف. */
static void run_all(maxfx_state_t *st, float *l, float *r, int frames)
{
    int done = 0;
    while (done < frames) {
        int block = frames - done;
        if (block > 512) block = 512;
        maxfx_process(st, l + done, r + done, block);
        done += block;
    }
}

/* ── ① العقد: الجدول ⇐ TSV ─────────────────────────────────────────────────────── */

typedef struct {
    char key[64];
    int id;
    int is_int;
    float def, min, max;
} tsv_row_t;

static int load_tsv(tsv_row_t *rows, int cap)
{
    char path[512];
    snprintf(path, sizeof(path), "%s/maxfx_params.tsv", FIXTURE_DIR);
    FILE *f = fopen(path, "r");
    if (f == NULL) {
        printf("  ✗ لا يُفتح العقد: %s\n", path);
        return -1;
    }
    char line[512];
    int n = 0;
    while (fgets(line, sizeof(line), f) != NULL) {
        if (line[0] == '#' || line[0] == '\n') continue;
        if (strncmp(line, "key\t", 4) == 0) continue;
        tsv_row_t row;
        char kind[16];
        if (sscanf(line, "%63[^\t]\t%d\t%15[^\t]\t%f\t%f\t%f",
                   row.key, &row.id, kind, &row.def, &row.min, &row.max) == 6) {
            row.is_int = (strcmp(kind, "int") == 0) ? 1 : 0;
            if (n < cap) rows[n++] = row;
        }
    }
    fclose(f);
    return n;
}

static void test_contract(void)
{
    printf("── ① العقد: maxfx_params.tsv ⇐ جدول C ──\n");
    tsv_row_t rows[64];
    int n = load_tsv(rows, 64);
    check(n > 0, "العقد يُقرأ");
    check(n == maxfx_param_count(), "عدد الصفوف == عدد المعاملات في C");
    for (int i = 0; i < n; i++) {
        const maxfx_param_def_t *p = maxfx_param_by_id(rows[i].id);
        char name[128];
        snprintf(name, sizeof(name), "صفّ %s(id=%d) موجود في C", rows[i].key, rows[i].id);
        check(p != NULL, name);
        if (p == NULL) continue;
        snprintf(name, sizeof(name), "%s: الاسم مطابق", rows[i].key);
        check(strcmp(p->key, rows[i].key) == 0, name);
        snprintf(name, sizeof(name), "%s: النوع مطابق", rows[i].key);
        check(p->is_int == rows[i].is_int, name);
        snprintf(name, sizeof(name), "%s: الافتراض والنطاق مطابقان حرفيًّا", rows[i].key);
        check(p->def == rows[i].def && p->min == rows[i].min && p->max == rows[i].max, name);
    }

    /* والتطبيع يفعل ما يقوله الجدول لا ما يُتخيَّل: قصٌّ للطرفين، و`NaN` ⇐ الافتراض. */
    printf("── ①-b التطبيع على كل صفّ من الجدول ──\n");
    maxfx_config_t cfg;
    maxfx_config_default(&cfg);
    for (int i = 0; i < n; i++) {
        const maxfx_param_def_t *p = maxfx_param_by_id(rows[i].id);
        if (p == NULL) continue;
        char name[128];
        if (p->is_int) {
            int32_t over = (int32_t)rows[i].max + 7;
            maxfx_config_set_param(&cfg, p->id, &over, sizeof(over));
            int32_t got = 0;
            size_t sz = sizeof(got);
            maxfx_config_get_param(&cfg, p->id, &got, &sz);
            snprintf(name, sizeof(name), "%s: القيمة فوق الحدّ تُقصّ إلى max", p->key);
            check((float)got == rows[i].max, name);
        } else {
            float over = rows[i].max + 123.0f;
            maxfx_config_set_param(&cfg, p->id, &over, sizeof(over));
            float got = 0.0f;
            size_t sz = sizeof(got);
            maxfx_config_get_param(&cfg, p->id, &got, &sz);
            snprintf(name, sizeof(name), "%s: فوق الحدّ ⇒ max", p->key);
            check(got == rows[i].max, name);

            float under = rows[i].min - 123.0f;
            maxfx_config_set_param(&cfg, p->id, &under, sizeof(under));
            maxfx_config_get_param(&cfg, p->id, &got, &sz);
            snprintf(name, sizeof(name), "%s: تحت الحدّ ⇒ min", p->key);
            check(got == rows[i].min, name);

            float nan_value = NAN;
            maxfx_config_set_param(&cfg, p->id, &nan_value, sizeof(nan_value));
            maxfx_config_get_param(&cfg, p->id, &got, &sz);
            snprintf(name, sizeof(name), "%s: NaN ⇒ الافتراض لا صفر", p->key);
            check(got == rows[i].def, name);
        }
    }

    /* والترددات تُقيَّد بتيرة العيّنات فوق نطاقها الجدوليّ. */
    printf("── ①-c القيد بتيرة العيّنات ──\n");
    maxfx_config_default(&cfg);
    cfg.eq_freq_hz[0] = 15000.0f;
    int changed = maxfx_config_clamp(&cfg, 24000.0f);
    check(changed >= 1, "تقييد Nyquist يُحسب تغييرًا");
    check(cfg.eq_freq_hz[0] == 10800.0f, "eq1_freq عند fs=24k ⇒ 0.45·fs = 10800");
}

/* عقد الهويّة: ما يراه `audioserver` بالضبط، مقروءٌ من الـTSV لا مُعادٍ هنا. */
static void test_identity(void)
{
    printf("── ①-d عقد الهويّة: maxfx_identity.tsv ⇐ ما يراه audioserver ──\n");
    char path[512];
    snprintf(path, sizeof(path), "%s/maxfx_identity.tsv", FIXTURE_DIR);
    FILE *f = fopen(path, "r");
    check(f != NULL, "عقد الهويّة يُقرأ");
    if (f == NULL) return;
    char line[512], field[128], value[256];
    int seen = 0;
    while (fgets(line, sizeof(line), f) != NULL) {
        if (line[0] == '#' || line[0] == '\n' || strncmp(line, "field\t", 6) == 0) continue;
        if (sscanf(line, "%127[^\t]\t%255[^\t\n]", field, value) != 2) continue;
        seen++;
        if (strcmp(field, "type_uuid") == 0) {
            check(strcmp(value, MAXFX_TYPE_UUID_STR) == 0, "type_uuid مطابق للعقد");
        } else if (strcmp(field, "impl_uuid") == 0) {
            check(strcmp(value, MAXFX_IMPL_UUID_STR) == 0, "impl_uuid مطابق للعقد");
        } else if (strcmp(field, "library_name") == 0) {
            check(strcmp(value, MAXFX_LIBRARY_NAME) == 0, "library_name مطابق للعقد");
        } else if (strcmp(field, "effect_name") == 0) {
            check(strcmp(value, MAXFX_EFFECT_NAME) == 0, "effect_name مطابق للعقد");
        } else if (strcmp(field, "prop_prefix") == 0) {
            check(strcmp(value, MAXFX_PROP_PREFIX) == 0, "prop_prefix مطابق للعقد");
        }
        /* و`library_file`/`effect_config_name` يقاسهما طرف Kotlin (لا حاجة بهما في C). */
    }
    fclose(f);
    check(seen == 7, "العقد يحمل حقوله السبعة لا أقلّ");
}

/* ── ② النواة: الدعاوى الرقميّة ────────────────────────────────────────────────── */

#define FS 48000.0f
#define FRAMES 48000
static float BUF_L[FRAMES], BUF_R[FRAMES], REF_L[FRAMES], REF_R[FRAMES];

static maxfx_config_t neutral_with_processing(void)
{
    maxfx_config_t cfg;
    maxfx_config_default(&cfg);
    cfg.enable = 1;
    cfg.limiter_ceiling_db = 0.0f; /* سقف 0dB — فالمحدِّد لا يلمس ما دونه. */
    return cfg;
}

static void test_core(void)
{
    printf("── ②-a الحياد الحرفيّ: كل الكتل محايدة ⇒ هوية بتغييرٍ بتغيير ──\n");
    /* مدخلٌ حتميّ (LCG) داخل [-0.99, 0.99] + حدّ 1.0 — فلا اعتماد على `rand()`. */
    unsigned long seed = 20261001UL;
    for (int i = 0; i < FRAMES; i++) {
        seed = seed * 6364136223846793005UL + 1442695040888963407UL;
        float v = (float)((seed >> 33) & 0xFFFF) / 65535.0f * 1.98f - 0.99f;
        BUF_L[i] = v;
        BUF_R[i] = -v * 0.75f;
        REF_L[i] = BUF_L[i];
        REF_R[i] = BUF_R[i];
    }
    BUF_L[0] = REF_L[0] = 1.0f;   /* والحدّ الأعلى نفسه لا يُقتطع. */
    BUF_R[1] = REF_R[1] = -1.0f;
    {
        maxfx_config_t cfg = neutral_with_processing();
        maxfx_state_t st;
        maxfx_state_init(&st, FS);
        maxfx_apply_config(&st, &cfg);
        run_all(&st, BUF_L, BUF_R, FRAMES);
        check(memcmp(BUF_L, REF_L, sizeof(BUF_L)) == 0, "المخرج الأيسر مطابق حرفيًّا");
        check(memcmp(BUF_R, REF_R, sizeof(BUF_R)) == 0, "المخرج الأيمن مطابق حرفيًّا");
    }

    printf("── ②-b التجاوز حرفيّ: enable=0 ⇒ لا تُلمس المدخلات (حتى NaN يبقى) ──\n");
    {
        maxfx_config_t cfg;
        maxfx_config_default(&cfg); /* enable=0 */
        maxfx_state_t st;
        maxfx_state_init(&st, FS);
        maxfx_apply_config(&st, &cfg);
        for (int i = 0; i < 64; i++) {
            BUF_L[i] = (i % 3 == 0) ? NAN : (float)i * 0.01f;
            BUF_R[i] = (i % 5 == 0) ? INFINITY : -(float)i * 0.01f;
            REF_L[i] = BUF_L[i];
            REF_R[i] = BUF_R[i];
        }
        run_all(&st, BUF_L, BUF_R, 64);
        check(memcmp(BUF_L, REF_L, 64 * sizeof(float)) == 0, "التجاوز: البافر غير ملموس");
        check(memcmp(BUF_R, REF_R, 64 * sizeof(float)) == 0, "التجاوز: القناة الثانية غير ملموسة");
    }

    printf("── ②-c كسب الدخل ×2 (+6.0206dB) ──\n");
    {
        maxfx_config_t cfg = neutral_with_processing();
        cfg.input_gain_db = 6.0206f;
        gen_sine(BUF_L, BUF_R, FRAMES, FS, 1000.0f, 0.3f, 0.3f);
        memcpy(REF_L, BUF_L, sizeof(BUF_L));
        maxfx_state_t st;
        maxfx_state_init(&st, FS);
        maxfx_apply_config(&st, &cfg);
        run_all(&st, BUF_L, BUF_R, FRAMES);
        double ratio = rms_of(BUF_L, FRAMES / 2, FRAMES) / rms_of(REF_L, FRAMES / 2, FRAMES);
        check_near(ratio, 2.0, 0.01, "RMS الخارج = 2× الداخل ±1%");
    }

    printf("── ②-d المعادل: +6dB عند المركز وحده ──\n");
    {
        maxfx_config_t cfg = neutral_with_processing();
        cfg.eq_gain_db[2] = 6.0f;
        cfg.eq_freq_hz[2] = 1000.0f;
        cfg.eq_q[2] = 1.0f;
        gen_sine(BUF_L, BUF_R, FRAMES, FS, 1000.0f, 0.2f, 0.2f);
        memcpy(REF_L, BUF_L, sizeof(BUF_L));
        maxfx_state_t st;
        maxfx_state_init(&st, FS);
        maxfx_apply_config(&st, &cfg);
        run_all(&st, BUF_L, BUF_R, FRAMES);
        double ratio = rms_of(BUF_L, FRAMES / 2, FRAMES) / rms_of(REF_L, FRAMES / 2, FRAMES);
        check_near(ratio, pow(10.0, 6.0 / 20.0), 0.08, "الكسب عند 1kHz ≈ +6dB");

        gen_sine(BUF_L, BUF_R, FRAMES, FS, 8000.0f, 0.2f, 0.2f);
        memcpy(REF_L, BUF_L, sizeof(BUF_L));
        maxfx_state_init(&st, FS);
        maxfx_apply_config(&st, &cfg);
        run_all(&st, BUF_L, BUF_R, FRAMES);
        ratio = rms_of(BUF_L, FRAMES / 2, FRAMES) / rms_of(REF_L, FRAMES / 2, FRAMES);
        check_near(ratio, 1.0, 0.08, "وبعيدًا (8kHz) ≈ 0dB — لا رفعٌ عامّ");
    }

    printf("── ②-e العرض: width=0 ⇒ أحاديّ حرفيًّا ──\n");
    {
        maxfx_config_t cfg = neutral_with_processing();
        cfg.width = 0.0f;
        gen_sine(BUF_L, BUF_R, FRAMES, FS, 1000.0f, 0.5f, 0.25f);
        /* قناة ثانية مختلفة النبرة كي لا تكون المطابقة هويّةً بلا معالجة. */
        for (int i = 0; i < FRAMES; i++) {
            BUF_R[i] = 0.25f * sinf(2.0f * 3.14159265358979323846f * 2000.0f * (float)i / FS);
        }
        maxfx_state_t st;
        maxfx_state_init(&st, FS);
        maxfx_apply_config(&st, &cfg);
        run_all(&st, BUF_L, BUF_R, FRAMES);
        int identical = 1;
        for (int i = 0; i < FRAMES; i++) {
            if (BUF_L[i] != BUF_R[i]) { identical = 0; break; }
        }
        check(identical, "القناتان متطابقتان بتغييرٍ بتغيير عند width=0");
    }

    printf("── ٢-و الأنبوب: tube=1 ⇒ tanh حرفيًّا لكل عيّنة ──\n");
    {
        maxfx_config_t cfg = neutral_with_processing();
        cfg.tube_amount = 1.0f;
        gen_sine(BUF_L, BUF_R, FRAMES, FS, 700.0f, 0.8f, 0.8f);
        memcpy(REF_L, BUF_L, sizeof(BUF_L));
        maxfx_state_t st;
        maxfx_state_init(&st, FS);
        maxfx_apply_config(&st, &cfg);
        run_all(&st, BUF_L, BUF_R, FRAMES);
        int ok = 1;
        for (int i = 0; i < FRAMES; i++) {
            if (fabsf(BUF_L[i] - tanhf(REF_L[i])) > 1e-6f) { ok = 0; break; }
        }
        check(ok, "الخارج == tanh(الداخل) ±1e-6");
    }

    printf("── ٢-ز الضاغط: منحنى النقل المُعلن (‎-20dB · 4:1 ⇒ ‎-15dB) ──\n");
    {
        maxfx_config_t cfg = neutral_with_processing();
        cfg.comp_threshold_db = -20.0f;
        cfg.comp_ratio = 4.0f;
        /* موجة مربّعة ±1.0: المُتتبِّع يرى ذروةً ثابتة فيستقرّ على القيمة حرفيًّا. */
        for (int i = 0; i < FRAMES; i++) {
            BUF_L[i] = (i % 2 == 0) ? 1.0f : -1.0f;
            BUF_R[i] = BUF_L[i];
        }
        maxfx_state_t st;
        maxfx_state_init(&st, FS);
        maxfx_apply_config(&st, &cfg);
        run_all(&st, BUF_L, BUF_R, FRAMES);
        double peak = peak_of(BUF_L, FRAMES * 3 / 4, FRAMES);
        check_near(peak, pow(10.0, -15.0 / 20.0), 0.005, "الذروة بعد الاستقرار ≈ −15dB");
    }

    printf("── ٢-ح المحدِّد: السقف حدٌّ صارم ──\n");
    {
        maxfx_config_t cfg = neutral_with_processing();
        cfg.limiter_ceiling_db = -0.5f;
        gen_sine(BUF_L, BUF_R, FRAMES, FS, 500.0f, 1.5f, 1.5f);
        maxfx_state_t st;
        maxfx_state_init(&st, FS);
        maxfx_apply_config(&st, &cfg);
        run_all(&st, BUF_L, BUF_R, FRAMES);
        double peak = peak_of(BUF_L, 0, FRAMES);
        double ceil_lin = pow(10.0, -0.5 / 20.0);
        check(peak <= ceil_lin + 1e-6, "لا عيّنة تتجاوز السقف");
        check_near(peak, ceil_lin, 0.001, "والإشارة المشبعة تصل إلى السقف لا أدنى");
    }

    printf("── ٢-ط دخولٌ مشوّهٌ (NaN/Inf/ضخامة) ⇒ خروجٌ محدود سليم ──\n");
    {
        maxfx_config_t cfg = neutral_with_processing();
        cfg.eq_gain_db[0] = 12.0f;
        cfg.tube_amount = 0.7f;
        cfg.width = 1.7f;
        cfg.comp_ratio = 8.0f;
        cfg.limiter_ceiling_db = -1.0f;
        for (int i = 0; i < FRAMES; i++) {
            BUF_L[i] = (i % 7 == 0) ? NAN : ((i % 11 == 0) ? INFINITY : (float)((i * 17) % 2000) - 1000.0f);
            BUF_R[i] = (i % 13 == 0) ? -INFINITY : -(float)((i * 29) % 2000) + 1000.0f;
        }
        maxfx_state_t st;
        maxfx_state_init(&st, FS);
        maxfx_apply_config(&st, &cfg);
        run_all(&st, BUF_L, BUF_R, FRAMES);
        int finite = 1, bounded = 1;
        double ceil_lin = pow(10.0, -1.0 / 20.0);
        for (int i = 0; i < FRAMES; i++) {
            if (!isfinite(BUF_L[i]) || !isfinite(BUF_R[i])) { finite = 0; break; }
            if (fabs(BUF_L[i]) > ceil_lin + 1e-6 || fabs(BUF_R[i]) > ceil_lin + 1e-6) { bounded = 0; break; }
        }
        check(finite, "كل العيّنات محدَّدة (لا NaN ولا Inf)");
        check(bounded, "وكلها تحت السقف");
    }
}

/* ── ③ الغلاف: عقد audioserver والخاصيّات ──────────────────────────────────────── */

static void build_config(effect_config_t *cfg, uint32_t rate, uint32_t channels, uint8_t format)
{
    memset(cfg, 0, sizeof(*cfg));
    cfg->inputCfg.samplingRate = rate;
    cfg->inputCfg.channels = channels;
    cfg->inputCfg.format = format;
    cfg->inputCfg.accessMode = EFFECT_BUFFER_ACCESS_WRITE;
    cfg->inputCfg.mask = EFFECT_CONFIG_ALL;
    cfg->outputCfg = cfg->inputCfg;
}

static int32_t cmd_set_param(effect_handle_t h, int32_t id, const void *value, size_t vsize)
{
    char buf[256];
    effect_param_t *ep = (effect_param_t *)buf;
    ep->status = 0;
    ep->psize = sizeof(int32_t);
    ep->vsize = (uint32_t)vsize;
    memcpy(ep->data, &id, sizeof(id));
    size_t voff = ((ep->psize - 1) / sizeof(int32_t) + 1) * sizeof(int32_t);
    memcpy(ep->data + voff, value, vsize);
    uint32_t cmd_size = (uint32_t)(sizeof(effect_param_t) + voff + vsize);
    int32_t status = -1;
    uint32_t reply_size = sizeof(status);
    int32_t rc = (*h)->command(h, EFFECT_CMD_SET_PARAM, cmd_size, ep, &reply_size, &status);
    return (rc == 0) ? status : rc;
}

static int32_t cmd_get_param(effect_handle_t h, int32_t id, void *value, size_t vsize)
{
    char req_buf[64];
    effect_param_t *req = (effect_param_t *)req_buf;
    req->status = 0;
    req->psize = sizeof(int32_t);
    req->vsize = (uint32_t)vsize;
    memcpy(req->data, &id, sizeof(id));
    char reply_buf[256];
    uint32_t reply_size = sizeof(reply_buf);
    int32_t rc = (*h)->command(h, EFFECT_CMD_GET_PARAM,
                               (uint32_t)(sizeof(effect_param_t) + sizeof(int32_t)),
                               req, &reply_size, reply_buf);
    if (rc != 0) return rc;
    effect_param_t *reply = (effect_param_t *)reply_buf;
    if (reply->status != 0 || reply->vsize != vsize) return -1;
    size_t voff = ((reply->psize - 1) / sizeof(int32_t) + 1) * sizeof(int32_t);
    memcpy(value, reply->data + voff, vsize);
    return 0;
}

static void test_effect(void)
{
    printf("── ③-a الهويّة: الرمز والوصف ومطابقة نصّ الـUUID للحقول ──\n");
    check(AELI.tag == AUDIO_EFFECT_LIBRARY_TAG, "وسم المكتبة هو AUDIO_EFFECT_LIBRARY_TAG");
    effect_uuid_t type_from_str, impl_from_str;
    check(maxfx_uuid_parse(MAXFX_TYPE_UUID_STR, &type_from_str) == 0, "نصّ UUID النوع يُحلّ");
    check(maxfx_uuid_parse(MAXFX_IMPL_UUID_STR, &impl_from_str) == 0, "نصّ UUID المُنفِّذ يُحلّ");
    effect_descriptor_t d;
    check(AELI.get_descriptor(&impl_from_str, &d) == 0, "get_descriptor يقبل مُعرّفنا");
    check(memcmp(&d.type, &type_from_str, sizeof(d.type)) == 0, "حقل النوع == نصّ النوع");
    check(memcmp(&d.uuid, &impl_from_str, sizeof(d.uuid)) == 0, "حقل المُنفِّذ == نصّ المُنفِّذ");
    effect_uuid_t other = impl_from_str;
    other.timeLow ^= 1u;
    check(AELI.get_descriptor(&other, &d) == -ENOENT, "مُعرّفٌ غريب ⇒ -ENOENT لا وصفٌ مُختلَق");

    printf("── ③-b الدورة: إنشاء ← SET_CONFIG ← معالجة s16 ──\n");
    test_prop_clear();
    effect_handle_t h = NULL;
    check(AELI.create_effect(&impl_from_str, 0, 0, &h) == 0 && h != NULL, "الإنشاء ينجح");
    effect_config_t ecfg;
    build_config(&ecfg, 48000, MAXFX_CHANNEL_OUT_STEREO, MAXFX_FORMAT_PCM_16_BIT);
    int32_t status = -1;
    uint32_t reply_size = sizeof(status);
    check((*h)->command(h, EFFECT_CMD_SET_CONFIG, sizeof(ecfg), &ecfg, &reply_size, &status) == 0,
          "SET_CONFIG يُقبل");
    check(status == 0, "وحالته 0");
    check((*h)->command(h, EFFECT_CMD_ENABLE, 0, NULL, NULL, NULL) == 0, "ENABLE يُقبل");

    /* معطّلة (لا خاصيّة enable بعد) ⇒ النسخ حرفيّ من مدخلٍ إلى مخرجٍ. */
    {
        int16_t in[256 * 2], out[256 * 2];
        for (int i = 0; i < 256; i++) {
            in[i * 2] = (int16_t)(i * 37 - 4000);
            in[i * 2 + 1] = (int16_t)(-(i * 53) + 3000);
        }
        memset(out, 0x5A, sizeof(out));
        audio_buffer_t ab_in = { .frameCount = 256, .s16 = in };
        audio_buffer_t ab_out = { .frameCount = 256, .s16 = out };
        check((*h)->process(h, &ab_in, &ab_out) == 0, "process تُنفَّذ");
        check(memcmp(in, out, sizeof(in)) == 0, "معطّلة ⇒ النسخ حرفيّ (بلا NaN يُقتطع)");
    }

    printf("── ③-c SET_PARAM يقود النواة: width=0 ⇒ أحاديّ في المسوّدات ──\n");
    {
        int32_t one = 1;
        check(cmd_set_param(h, 1, &one, sizeof(one)) == 0, "enable=1 عبر SET_PARAM");
        float zero = 0.0f;
        check(cmd_set_param(h, 6, &zero, sizeof(zero)) == 0, "width=0 عبر SET_PARAM");
        int16_t in[256 * 2], out[256 * 2];
        for (int i = 0; i < 256; i++) {
            in[i * 2] = (int16_t)(sinf((float)i * 0.13f) * 8000.0f);
            in[i * 2 + 1] = (int16_t)(sinf((float)i * 0.07f) * 2000.0f);
        }
        audio_buffer_t ab_in = { .frameCount = 256, .s16 = in };
        audio_buffer_t ab_out = { .frameCount = 256, .s16 = out };
        check((*h)->process(h, &ab_in, &ab_out) == 0, "process بعد التمكين");
        int identical = 1;
        for (int i = 0; i < 256; i++) {
            if (out[i * 2] != out[i * 2 + 1]) { identical = 0; break; }
        }
        check(identical, "القناتان متطابقتان — المعامل وصل إلى النواة فعلًا");
    }

    printf("── ③-d GET_PARAM يقرأ ما كُتب ──\n");
    {
        float g = 4.5f;
        check(cmd_set_param(h, 16, &g, sizeof(g)) == 0, "eq3_gain=4.5");
        float back = 0.0f;
        check(cmd_get_param(h, 16, &back, sizeof(back)) == 0, "GET_PARAM ينجح");
        check(back == 4.5f, "والقيمة 4.5 حرفيًّا");
        check(cmd_set_param(h, 999, &g, sizeof(g)) != 0, "معرّفٌ مجهول ⇒ رفض لا قيمة مُختلَقة");
    }

    printf("── ٣-هـ الخاصيّة: setprop ⇐ الإعداد (والغياب ليس صفرًا) ──\n");
    {
        check(AELI.release_effect(h) == 0, "التحرير ينجح");
        test_prop_clear();
        test_prop_set("persist.audio.maxfx.enable", "1");
        test_prop_set("persist.audio.maxfx.width", "0");
        h = NULL;
        check(AELI.create_effect(&impl_from_str, 0, 0, &h) == 0 && h != NULL, "إنشاءٌ ثانٍ");
        build_config(&ecfg, 48000, MAXFX_CHANNEL_OUT_STEREO, MAXFX_FORMAT_PCM_16_BIT);
        reply_size = sizeof(status);
        (*h)->command(h, EFFECT_CMD_SET_CONFIG, sizeof(ecfg), &ecfg, &reply_size, &status);
        (*h)->command(h, EFFECT_CMD_ENABLE, 0, NULL, NULL, NULL);
        int16_t in[256 * 2], out[256 * 2];
        for (int i = 0; i < 256; i++) {
            in[i * 2] = (int16_t)(sinf((float)i * 0.11f) * 9000.0f);
            in[i * 2 + 1] = (int16_t)(sinf((float)i * 0.05f) * 1500.0f);
        }
        audio_buffer_t ab_in = { .frameCount = 256, .s16 = in };
        audio_buffer_t ab_out = { .frameCount = 256, .s16 = out };
        (*h)->process(h, &ab_in, &ab_out);
        int identical = 1;
        for (int i = 0; i < 256; i++) {
            if (out[i * 2] != out[i * 2 + 1]) { identical = 0; break; }
        }
        check(identical, "خاصيّتا enable وwidth قُرئتا قبل المعالجة ⇒ أحاديّ");
        float gain = -7.0f;
        check(cmd_get_param(h, 3, &gain, sizeof(gain)) == 0, "output_gain يُقرأ");
        check(gain == 0.0f, "خاصيّة غائبة ⇒ الافتراض (0dB) لا صفرٌ مُختلَق");
        check(AELI.release_effect(h) == 0, "التحرير");
    }
}

int main(void)
{
    printf("== MaxFx host suite ==\n");
    test_contract();
    test_identity();
    test_core();
    test_effect();
    printf("── النتيجة: %d ناجحة · %d فاشلة ──\n", g_pass, g_fail);
    return (g_fail == 0 && g_pass > 0) ? 0 : 1;
}
