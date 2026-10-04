/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * MaxFx — تنفيذ نواة المعالجة (`maxfx_dsp`).
 *
 * صيغ المرشّحات الثنائيّة من **وصفة Bristow-Johnson** (وثيقة عامّة المجال — المعادلات مُعلَنة
 * للجميع، ولا كودٌ هنا منقول من مشروع). وسلسلة المعالجة مُثبَتة الترتيب:
 *
 *   دخل ← كسب دخل ← معادل (٥ نطاقات) ← جير ← وضوح ← عرض (M/S) ← أنبوب ← ضاغط ← كسب خرج ← محدِّد
 *
 * والوحدات كلّها **تُتخطّى بقيمها المحايدة** (علامات `active`/المقارنات بالضبط)، فسلسلةٌ
 * محايدة بالكامل = **هوية حرفيّة** (يُقيَّس بتغييرٍ بتغيير في `dsp_test.c`)، والمحدِّد وحده
 * دائمٌ لمعالجةٍ نشطة لأنّه سقف أمان لا مزية.
 *
 * وجدول المعاملات واحدٌ هنا: `maxfx_config_default` و`maxfx_config_clamp` و`set/get_param`
 * كلّها تقرأ منه، والاختبار يقارنه بصفّ العقد في `fixtures/contracts/maxfx_params.tsv` —
 * فثلاثة أطراف (C · Kotlin · TSV) لا يفترق اثنان منها دون أن يسقط قياس.
 */
#include "maxfx_dsp.h"

#include <math.h>
#include <stddef.h>
#include <string.h>

#define MAXFX_PI 3.14159265358979323846f

/* تيرة التردد الآمنة فوقها لا يُبنى مرشّح: 0.45·fs (هامش تحت نصف التيرة). */
#define MAXFX_F0_SAFETY 0.45f
#define MAXFX_F0_MIN 20.0f
#define MAXFX_F0_MAX 20000.0f

/* ── جدول المعاملات — مطابقٌ لصفوف العقد حرفيًّا (id · key · kind · default · min · max) ── */

#define P_INT(id_, key_, def_, mn_, mx_, field_) \
    { (id_), (key_), 1, (def_), (mn_), (mx_), offsetof(maxfx_config_t, field_) }
#define P_FLT(id_, key_, def_, mn_, mx_, field_) \
    { (id_), (key_), 0, (def_), (mn_), (mx_), offsetof(maxfx_config_t, field_) }

static const maxfx_param_def_t PARAMS[] = {
    P_INT(1,  "enable",            0.0f,   0.0f,   1.0f,     enable),
    P_FLT(2,  "input_gain_db",     0.0f, -24.0f,  12.0f,     input_gain_db),
    P_FLT(3,  "output_gain_db",    0.0f, -24.0f,  12.0f,     output_gain_db),
    P_FLT(4,  "bass_gain_db",      0.0f, -12.0f,  15.0f,     bass_gain_db),
    P_FLT(5,  "bass_freq_hz",    120.0f,  40.0f, 300.0f,     bass_freq_hz),
    P_FLT(6,  "width",             1.0f,   0.0f,   2.0f,     width),
    P_FLT(7,  "clarity_db",        0.0f, -12.0f,  12.0f,     clarity_db),
    P_FLT(8,  "tube_amount",       0.0f,   0.0f,   1.0f,     tube_amount),
    P_FLT(9,  "comp_threshold_db", -3.0f, -60.0f,  0.0f,     comp_threshold_db),
    P_FLT(10, "comp_ratio",        1.0f,   1.0f,  20.0f,     comp_ratio),
    P_FLT(11, "comp_attack_ms",   10.0f,   0.5f, 200.0f,     comp_attack_ms),
    P_FLT(12, "comp_release_ms", 120.0f,  10.0f, 2000.0f,    comp_release_ms),
    P_FLT(13, "limiter_ceiling_db", -0.5f, -24.0f, 0.0f,     limiter_ceiling_db),
    P_FLT(14, "eq1_gain_db",       0.0f, -12.0f,  12.0f,     eq_gain_db[0]),
    P_FLT(15, "eq2_gain_db",       0.0f, -12.0f,  12.0f,     eq_gain_db[1]),
    P_FLT(16, "eq3_gain_db",       0.0f, -12.0f,  12.0f,     eq_gain_db[2]),
    P_FLT(17, "eq4_gain_db",       0.0f, -12.0f,  12.0f,     eq_gain_db[3]),
    P_FLT(18, "eq5_gain_db",       0.0f, -12.0f,  12.0f,     eq_gain_db[4]),
    P_FLT(19, "eq1_freq_hz",      60.0f,  20.0f, 20000.0f,   eq_freq_hz[0]),
    P_FLT(20, "eq2_freq_hz",     230.0f,  20.0f, 20000.0f,   eq_freq_hz[1]),
    P_FLT(21, "eq3_freq_hz",     910.0f,  20.0f, 20000.0f,   eq_freq_hz[2]),
    P_FLT(22, "eq4_freq_hz",    3600.0f,  20.0f, 20000.0f,   eq_freq_hz[3]),
    P_FLT(23, "eq5_freq_hz",   14000.0f,  20.0f, 20000.0f,   eq_freq_hz[4]),
    P_FLT(24, "eq1_q",             1.0f,   0.3f,  10.0f,     eq_q[0]),
    P_FLT(25, "eq2_q",             1.0f,   0.3f,  10.0f,     eq_q[1]),
    P_FLT(26, "eq3_q",             1.0f,   0.3f,  10.0f,     eq_q[2]),
    P_FLT(27, "eq4_q",             1.0f,   0.3f,  10.0f,     eq_q[3]),
    P_FLT(28, "eq5_q",             1.0f,   0.3f,  10.0f,     eq_q[4]),
};

static const int PARAM_COUNT = (int)(sizeof(PARAMS) / sizeof(PARAMS[0]));

int maxfx_param_count(void) { return PARAM_COUNT; }

const maxfx_param_def_t *maxfx_param_at(int index)
{
    if (index < 0 || index >= PARAM_COUNT) return NULL;
    return &PARAMS[index];
}

const maxfx_param_def_t *maxfx_param_by_id(int32_t id)
{
    for (int i = 0; i < PARAM_COUNT; i++) {
        if (PARAMS[i].id == id) return &PARAMS[i];
    }
    return NULL;
}

/* قراءة/كتابة الحقل حسب النوع — لا punning في موضعٍ آخر. */
static float field_get(const maxfx_config_t *cfg, const maxfx_param_def_t *p)
{
    const char *base = (const char *)cfg;
    return p->is_int ? (float)*(const int *)(base + p->offset)
                     : *(const float *)(base + p->offset);
}

static void field_set(maxfx_config_t *cfg, const maxfx_param_def_t *p, float v)
{
    char *base = (char *)cfg;
    if (p->is_int) {
        *(int *)(base + p->offset) = (int)lroundf(v);
    } else {
        *(float *)(base + p->offset) = v;
    }
}

/* التطبيع: `NaN`/`Inf` ⇒ الافتراض (لا صفر — فالصفر قد يكون خارج السياق)، وإلّا القصّ. */
static float sanitize_param(const maxfx_param_def_t *p, float v)
{
    if (!isfinite(v)) return p->def;
    if (v < p->min) return p->min;
    if (v > p->max) return p->max;
    return v;
}

void maxfx_config_default(maxfx_config_t *cfg)
{
    memset(cfg, 0, sizeof(*cfg));
    for (int i = 0; i < PARAM_COUNT; i++) {
        field_set(cfg, &PARAMS[i], PARAMS[i].def);
    }
}

int maxfx_config_clamp(maxfx_config_t *cfg, float sample_rate)
{
    int changed = 0;
    float f0_max = MAXFX_F0_MAX;
    if (isfinite(sample_rate) && sample_rate > 0.0f) {
        float nyquist_safe = MAXFX_F0_SAFETY * sample_rate;
        if (nyquist_safe < f0_max) f0_max = nyquist_safe;
        if (f0_max < MAXFX_F0_MIN) f0_max = MAXFX_F0_MIN;
    }
    for (int i = 0; i < PARAM_COUNT; i++) {
        const maxfx_param_def_t *p = &PARAMS[i];
        float cur = field_get(cfg, p);
        float want = sanitize_param(p, cur);
        /* والترددات تُقيَّد بتيرة العيّنات فوق نطاقها الجدوليّ — فلا يُبنى مرشّح غير مستقرّ. */
        if (!p->is_int && p->min == MAXFX_F0_MIN && p->max == MAXFX_F0_MAX && want > f0_max) {
            want = f0_max;
        }
        if (!isfinite(cur) || want != cur) {
            field_set(cfg, p, want);
            changed++;
        }
    }
    return changed;
}

int maxfx_config_set_param(maxfx_config_t *cfg, int32_t id, const void *value, size_t vsize)
{
    const maxfx_param_def_t *p = maxfx_param_by_id(id);
    if (p == NULL || value == NULL) return 0;
    float v;
    if (p->is_int) {
        if (vsize < sizeof(int32_t)) return 0;
        int32_t raw;
        memcpy(&raw, value, sizeof(raw));
        v = (float)raw;
    } else {
        if (vsize < sizeof(float)) return 0;
        memcpy(&v, value, sizeof(v));
    }
    float before = field_get(cfg, p);
    float want = sanitize_param(p, v);
    field_set(cfg, p, want);
    return (!isfinite(before)) || (want != before);
}

int maxfx_config_get_param(const maxfx_config_t *cfg, int32_t id, void *value, size_t *vsize)
{
    const maxfx_param_def_t *p = maxfx_param_by_id(id);
    if (p == NULL || value == NULL || vsize == NULL) return 0;
    float cur = field_get(cfg, p);
    if (p->is_int) {
        if (*vsize < sizeof(int32_t)) return 0;
        int32_t raw = (int32_t)lroundf(cur);
        memcpy(value, &raw, sizeof(raw));
        *vsize = sizeof(raw);
    } else {
        if (*vsize < sizeof(float)) return 0;
        memcpy(value, &cur, sizeof(cur));
        *vsize = sizeof(cur);
    }
    return 1;
}

/* ── المرشّحات الثنائيّة — وصفة Bristow-Johnson (عامّة المجال) ───────────────────────── */

static void biquad_set(maxfx_biquad_t *b, float b0, float b1, float b2,
                       float a0, float a1, float a2, int active)
{
    float inv = 1.0f / a0;
    b->b0 = b0 * inv;
    b->b1 = b1 * inv;
    b->b2 = b2 * inv;
    b->a1 = a1 * inv;
    b->a2 = a2 * inv;
    b->active = active;
}

static void biquad_identity(maxfx_biquad_t *b)
{
    b->b0 = 1.0f; b->b1 = 0.0f; b->b2 = 0.0f;
    b->a1 = 0.0f; b->a2 = 0.0f;
    b->active = 0;
}

static void biquad_reset(maxfx_biquad_t *b)
{
    for (int ch = 0; ch < MAXFX_MAX_CHANNELS; ch++) {
        b->s1[ch] = 0.0f;
        b->s2[ch] = 0.0f;
    }
}

static void biquad_peaking(maxfx_biquad_t *b, float fs, float f0, float q, float gain_db)
{
    if (gain_db == 0.0f) { biquad_identity(b); return; }
    float a = powf(10.0f, gain_db / 40.0f);
    float w0 = 2.0f * MAXFX_PI * f0 / fs;
    float cw = cosf(w0), sw = sinf(w0);
    float alpha = sw / (2.0f * q);
    biquad_set(b,
               1.0f + alpha * a, -2.0f * cw, 1.0f - alpha * a,
               1.0f + alpha / a, -2.0f * cw, 1.0f - alpha / a, 1);
}

static void biquad_lowshelf(maxfx_biquad_t *b, float fs, float f0, float gain_db)
{
    if (gain_db == 0.0f) { biquad_identity(b); return; }
    float a = powf(10.0f, gain_db / 40.0f);
    float w0 = 2.0f * MAXFX_PI * f0 / fs;
    float cw = cosf(w0), sw = sinf(w0);
    /* الميل S=1 ⇒ alpha = sin(w0)/2 · sqrt(2). */
    float alpha = sw * 0.5f * 1.41421356237f;
    float two_sqrt_a_alpha = 2.0f * sqrtf(a) * alpha;
    biquad_set(b,
               a * ((a + 1.0f) - (a - 1.0f) * cw + two_sqrt_a_alpha),
               2.0f * a * ((a - 1.0f) - (a + 1.0f) * cw),
               a * ((a + 1.0f) - (a - 1.0f) * cw - two_sqrt_a_alpha),
               (a + 1.0f) + (a - 1.0f) * cw + two_sqrt_a_alpha,
               -2.0f * ((a - 1.0f) + (a + 1.0f) * cw),
               (a + 1.0f) + (a - 1.0f) * cw - two_sqrt_a_alpha, 1);
}

static void biquad_highshelf(maxfx_biquad_t *b, float fs, float f0, float gain_db)
{
    if (gain_db == 0.0f) { biquad_identity(b); return; }
    float a = powf(10.0f, gain_db / 40.0f);
    float w0 = 2.0f * MAXFX_PI * f0 / fs;
    float cw = cosf(w0), sw = sinf(w0);
    float alpha = sw * 0.5f * 1.41421356237f;
    float two_sqrt_a_alpha = 2.0f * sqrtf(a) * alpha;
    biquad_set(b,
               a * ((a + 1.0f) + (a - 1.0f) * cw + two_sqrt_a_alpha),
               -2.0f * a * ((a - 1.0f) + (a + 1.0f) * cw),
               a * ((a + 1.0f) + (a - 1.0f) * cw - two_sqrt_a_alpha),
               (a + 1.0f) - (a - 1.0f) * cw + two_sqrt_a_alpha,
               2.0f * ((a - 1.0f) - (a + 1.0f) * cw),
               (a + 1.0f) - (a - 1.0f) * cw - two_sqrt_a_alpha, 1);
}

static inline float biquad_run(maxfx_biquad_t *b, int ch, float x)
{
    if (!b->active) return x;
    float y = b->b0 * x + b->s1[ch];
    b->s1[ch] = b->b1 * x - b->a1 * y + b->s2[ch];
    b->s2[ch] = b->b2 * x - b->a2 * y;
    return y;
}

/* ── دورة الحياة ─────────────────────────────────────────────────────────────────── */

void maxfx_state_init(maxfx_state_t *st, float sample_rate)
{
    memset(st, 0, sizeof(*st));
    st->sample_rate = (isfinite(sample_rate) && sample_rate > 0.0f) ? sample_rate : 48000.0f;
    maxfx_config_t cfg;
    maxfx_config_default(&cfg);
    maxfx_apply_config(st, &cfg);
    maxfx_state_reset(st);
}

void maxfx_state_reset(maxfx_state_t *st)
{
    for (int i = 0; i < MAXFX_EQ_BANDS; i++) biquad_reset(&st->eq[i]);
    biquad_reset(&st->bass_shelf);
    biquad_reset(&st->clarity_shelf);
    st->side_s = 0.0f;
    st->comp_env = 0.0f;
    st->lim_gain = 1.0f;
}

void maxfx_apply_config(maxfx_state_t *st, const maxfx_config_t *cfg)
{
    maxfx_config_t c = *cfg;
    maxfx_config_clamp(&c, st->sample_rate);
    st->cfg = c;

    float fs = st->sample_rate;

    st->in_gain = (c.input_gain_db == 0.0f) ? 1.0f : powf(10.0f, c.input_gain_db / 20.0f);
    st->out_gain = (c.output_gain_db == 0.0f) ? 1.0f : powf(10.0f, c.output_gain_db / 20.0f);

    for (int i = 0; i < MAXFX_EQ_BANDS; i++) {
        biquad_peaking(&st->eq[i], fs, c.eq_freq_hz[i], c.eq_q[i], c.eq_gain_db[i]);
    }
    biquad_lowshelf(&st->bass_shelf, fs, c.bass_freq_hz, c.bass_gain_db);
    /* الوضوح: رفوفٌ علويّ عند 4kHz — مُثبَت التيرة لأنّ المقبض قوّةٌ لا تيرة. */
    biquad_highshelf(&st->clarity_shelf, fs, 4000.0f, c.clarity_db);

    st->width_active = (c.width != 1.0f);
    /* ترشيح جانبيّ أحاديّ البعد عند 150Hz: الجهير يبقى في المنتصف مهما اتّسعت الصورة. */
    st->side_hp_a = 1.0f - expf(-2.0f * MAXFX_PI * 150.0f / fs);

    st->tube_active = (c.tube_amount > 0.0f);
    st->comp_active = (c.comp_ratio != 1.0f);
    st->comp_attack_coef = 1.0f - expf(-1.0f / (fs * c.comp_attack_ms / 1000.0f));
    st->comp_release_coef = 1.0f - expf(-1.0f / (fs * c.comp_release_ms / 1000.0f));
    /* المحدِّد: تحريرٌ مُثبَت 80ms وهجومٌ فوريّ (السقف أمانٌ لا مزية). */
    st->lim_release_coef = 1.0f - expf(-1.0f / (fs * 0.080f));
    st->ceiling = powf(10.0f, c.limiter_ceiling_db / 20.0f);
}

/* ── المعالجة ───────────────────────────────────────────────────────────────────── */

static inline float sanitize_sample(float x)
{
    return isfinite(x) ? x : 0.0f;
}

void maxfx_process(maxfx_state_t *st, float *left, float *right, int frames)
{
    const maxfx_config_t *c = &st->cfg;
    if (!c->enable) return;

    const int use_eq = st->eq[0].active || st->eq[1].active || st->eq[2].active ||
                       st->eq[3].active || st->eq[4].active;
    const int use_in = (st->in_gain != 1.0f);
    const int use_out = (st->out_gain != 1.0f);
    const int use_bass = st->bass_shelf.active;
    const int use_clarity = st->clarity_shelf.active;
    const float width = c->width;
    const float tube = c->tube_amount;
    const float comp_thr = c->comp_threshold_db;
    const float comp_ratio = c->comp_ratio;

    for (int i = 0; i < frames; i++) {
        float l = sanitize_sample(left[i]);
        float r = sanitize_sample(right[i]);

        if (use_in) { l *= st->in_gain; r *= st->in_gain; }

        if (use_eq) {
            for (int b = 0; b < MAXFX_EQ_BANDS; b++) {
                l = biquad_run(&st->eq[b], 0, l);
                r = biquad_run(&st->eq[b], 1, r);
            }
        }
        if (use_bass) {
            l = biquad_run(&st->bass_shelf, 0, l);
            r = biquad_run(&st->bass_shelf, 1, r);
        }
        if (use_clarity) {
            l = biquad_run(&st->clarity_shelf, 0, l);
            r = biquad_run(&st->clarity_shelf, 1, r);
        }

        if (st->width_active) {
            float mid = 0.5f * (l + r);
            float side = 0.5f * (l - r);
            st->side_s += st->side_hp_a * (side - st->side_s);
            side = (side - st->side_s) * width;
            l = mid + side;
            r = mid - side;
        }

        if (st->tube_active) {
            /* مزيجٌ خطيّ مع `tanh` — الحياد عند 0 حرفيّ (يُتخطّى أصلًا)، والتشويه المحدود عند 1. */
            l = (1.0f - tube) * l + tube * tanhf(l);
            r = (1.0f - tube) * r + tube * tanhf(r);
        }

        if (st->comp_active) {
            float det = fmaxf(fabsf(l), fabsf(r));
            float coef = (det > st->comp_env) ? st->comp_attack_coef : st->comp_release_coef;
            st->comp_env += (det - st->comp_env) * coef;
            if (st->comp_env > 1e-9f) {
                float lvl_db = 20.0f * log10f(st->comp_env);
                float over = lvl_db - comp_thr;
                if (over > 0.0f) {
                    float gr_db = over * (1.0f / comp_ratio - 1.0f);
                    float g = powf(10.0f, gr_db / 20.0f);
                    l *= g;
                    r *= g;
                }
            }
        }

        if (use_out) { l *= st->out_gain; r *= st->out_gain; }

        /* المحدِّد: هجومٌ فوريّ (خفض الكسب عند التجاوز) وتحريرٌ تدريجيّ، ثمّ **قَصٌّ أمانٍ**
         * يضمن الشرط المُقاس حرفيًّا: |الخارج| ≤ السقف. */
        {
            float det = fmaxf(fabsf(l), fabsf(r));
            if (det > st->ceiling && det > 0.0f) {
                float need = st->ceiling / det;
                if (need < st->lim_gain) st->lim_gain = need;
            } else {
                st->lim_gain += (1.0f - st->lim_gain) * st->lim_release_coef;
            }
            l *= st->lim_gain;
            r *= st->lim_gain;
            if (l > st->ceiling) l = st->ceiling;
            else if (l < -st->ceiling) l = -st->ceiling;
            if (r > st->ceiling) r = st->ceiling;
            else if (r < -st->ceiling) r = -st->ceiling;
        }

        left[i] = l;
        right[i] = r;
    }
}
