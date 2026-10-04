/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * MaxFx — **نواة المعالجة الصوتيّة النقّية** (`maxfx_dsp`).
 *
 * هذه الطبقة **بلا أندرويد وبلا عقد مؤثّرات**: تأخذ عيّنات `float` خطة (planar) وتكتبها،
 * وتُقاس على المضيف بادّعاءاتٍ قابلة للتكذيب (`maxfx/tests/dsp_test.c`). وما فوقها — غلاف
 * المؤثّر `maxfx_effect.c` الذي يترجم إليها مسوّدات `audioserver` المتشابكة.
 *
 * **والقواعد الثلاث التي لا تُكسر هنا:**
 * ① **المحايد حرفيّ:** كل كتلةٍ لها قيمة محايدة تُتخطّى بها تمامًا (`enable=0` ⇒ نسخٌ حرفيّ؛
 *   `width=1` ⇒ الكتلة تُتخطّى لا تُعامل)، فلا «معالجةٌ خفيفة» تغيّر الصوت وهي تدّعي الحياد.
 * ② **ما لا يُقرأ لا يُخترع:** كل معاملٍ يُقصّ إلى نطاقه (`maxfx_config_clamp`)، و`NaN` يعود
 *   إلى افتراضه لا إلى صفرٍ ولا إلى ما قبله — فالقيم المريضة تُشفى عند الباب.
 * ③ **لا `malloc` في المسار الحيّ:** الحالة كلّها في `maxfx_state_t` يملكها المستدعي، والمعالجة
 *   كتلةً كتلةً على مكدّسها — ف`process()` آمنة للزمن الحقيقيّ (شرط عقد المؤثّرات).
 *
 * وصيغ المرشّحات من **وصفة Bristow-Johnson للمرشّحات الثنائيّة** (وثيقة عامّة المجال، لا كود
 * مأخوذ) — والتفصيل الرياضيّ لكل مرحلة في تعليقها.
 */
#ifndef MAXFX_DSP_H
#define MAXFX_DSP_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/** عدد نطاقات المعادل — خمسة، وكلٌّ محدَّد بثلاثة (تيرة · تيرة مركز · جودة). */
#define MAXFX_EQ_BANDS 5

/** أقصى قنوات يعالجها النواة: أحاديّ أو ستيريو. وما عداهما يُتخطّى في الغلاف لا هنا. */
#define MAXFX_MAX_CHANNELS 2

/** معاملات المعالجة — والقيم هنا **مُطبَّعة** (انظر `maxfx_config_clamp`). */
typedef struct {
    int enable;                    /**< 0 = تجاوز حرفيّ، 1 = معالجة. */
    float input_gain_db;
    float output_gain_db;
    float bass_gain_db;            /**< رفعة/خفض الرفوف السفليّ عند [bass_freq_hz]. */
    float bass_freq_hz;
    float width;                   /**< 1 = محايد حرفيّ (تُتخطّى الكتلة)، 0 = أحاديّ، 2 = أعرض. */
    float clarity_db;              /**< رفوع/خفض رفوف علويّ عند 4kHz. */
    float tube_amount;             /**< 0 = محايد حرفيّ، 1 = تشويه `tanh` كامل. */
    float comp_threshold_db;
    float comp_ratio;              /**< 1 = محايد حرفيّ (تُتخطّى الكتلة). */
    float comp_attack_ms;
    float comp_release_ms;
    float limiter_ceiling_db;      /**< سقف الأمان — دائم النشاط أثناء المعالجة. */
    float eq_gain_db[MAXFX_EQ_BANDS];
    float eq_freq_hz[MAXFX_EQ_BANDS];
    float eq_q[MAXFX_EQ_BANDS];
} maxfx_config_t;

/**
 * مرشّحٌ ثنائيّ (transposed direct form II) — معاملاتٌ **وحالةٌ لكل قناة**.
 *
 * **والحالة لكل قناةٍ على حِدة عقدٌ لا تفصيل:** حالةٌ مشتركة تجعل كلّ قناة تقرأ مخلّلات
 * الأخرى، فيصير المرشّح كأنّه يعمل بتيرتين على إشارةٍ واحدة — وهذا عطبٌ **أمسكته** مجموعة
 * القياس (استجابة ‎+6dB‎ جاءت ‎+1.95dB‎) قبل أن يصل إلى جهاز.
 */
typedef struct {
    float b0, b1, b2, a1, a2;
    float s1[MAXFX_MAX_CHANNELS], s2[MAXFX_MAX_CHANNELS];
    int active;                    /**< 0 = هوية حرفيّة (تُتخطّى)، 1 = محسوبة. */
} maxfx_biquad_t;

/** حالة المعالجة — يملكها المستدعي، ولا تخصيص ذاكرة داخليّ. */
typedef struct {
    float sample_rate;
    maxfx_config_t cfg;            /**< آخر إعدادٍ مُطبَّع ومُطبَّق. */
    maxfx_biquad_t eq[MAXFX_EQ_BANDS];
    maxfx_biquad_t bass_shelf;
    maxfx_biquad_t clarity_shelf;
    /* كتلة العرض (M/S): ترشيح جانبيّ أحاديّ البعد (إبقاء الجهير في المنتصف). */
    float side_hp_a;               /**< معامل المرشّح الأحاديّ للقناة الجانبيّة. */
    float side_s;                  /**< حالته. */
    int width_active;
    int tube_active;
    /* الضاغط: مُتتبِّع ذروة مشترك بين القنوات. */
    float comp_attack_coef, comp_release_coef;
    int comp_active;
    float comp_env;
    /* المحدِّد: مُتتبِّع ذروة سريع + سقف. */
    float lim_release_coef;
    float lim_gain;                /**< كسب المحدِّد الحاليّ (≤ 1). */
    float ceiling;                 /**< السقف الخطيّ (10^(ceiling_db/20)). */
    float in_gain, out_gain;
} maxfx_state_t;

/** الإعداد الافتراضي — كل الكتل محايدة، والمعالجة معطّلة. */
void maxfx_config_default(maxfx_config_t *cfg);

/**
 * يُطبّع الإعداد: كل قيمةٍ إلى نطاقها، و`NaN`/`Inf` إلى افتراضها، والتردد إلى أقلّ من
 * نصف التيرة (`0.45 * sample_rate`) فلا يبني مرشّحًا غير مستقرّ.
 *
 * @return عدد القيم التي تغيّرت فعلًا — والاختبارات تقيس الرقم نفسه، فلا «تطبيعٌ» صامت.
 */
int maxfx_config_clamp(maxfx_config_t *cfg, float sample_rate);

/** يُهيئة الحالة على تيرة العيّنات (يُستدعى مرّة واحدة)، ويُطبّق الإعداد الافتراضيّ. */
void maxfx_state_init(maxfx_state_t *st, float sample_rate);

/** يُصفّر الحالات (الترشيحات والمُتتبِّعات) ويُبقي المعاملات — نداء `EFFECT_CMD_RESET`. */
void maxfx_state_reset(maxfx_state_t *st);

/**
 * يُطبّق إعدادًا (يُطبّعه أوّلًا) ويبني معاملات المرشّحات منه. الكتل المحايدة تُعطَّل
 * صراحةً بعلامات `active`، فلا معالجة رمزيّة حيث يُعدَّد الحياد.
 */
void maxfx_apply_config(maxfx_state_t *st, const maxfx_config_t *cfg);

/**
 * معالجة كتلة ستيريو خطة (`frames` عيّنة لكل قناة). القناتان **تُشيران إلى متاحَين
 * مختلفين** (الغلاف يفكّ التشابك)، والمعالجة **في الموضع** آمنة حين يتساوى المؤشّران.
 *
 * وشرط الخروج المُقاس: لا `NaN` ولا `Inf` في الخارج مهما كان الداخل، وذروة الخارج
 * ≤ السقف (+ هامش دقيق عشريّ) ما دامت المعالجة نشطة. ومع `enable=0` **لا تُلمس
 * المدخلات أصلًا** — والنسخ من مدخلٍ إلى مخرجٍ منفصل مسؤوليّة الغلاف.
 */
void maxfx_process(maxfx_state_t *st, float *left, float *right, int frames);

/* ── عقد المعاملات: الجدول واحدٌ هنا، وتُقاس مطابقته لـ`fixtures/contracts/maxfx_params.tsv` ── */

/** تعريف معاملٍ واحد — مطابقٌ لصفٍّ في العقد المشترك (key · id · kind · default · min · max). */
typedef struct {
    int32_t id;            /**< مفتاح `effect_param_t` و`persist.audio.maxfx.<key>`. */
    const char *key;       /**< اسم المعامل = لاحقة الخاصية. */
    int is_int;            /**< 1 = قيمة صحيحة (`int32`)، 0 = عشريّ (`float`). */
    float def, min, max;   /**< الافتراض والنطاق — كما في العقد حرفيًّا. */
    size_t offset;         /**< إزاحة الحقل داخل `maxfx_config_t` (داخليّ الاستعمال). */
} maxfx_param_def_t;

int maxfx_param_count(void);
const maxfx_param_def_t *maxfx_param_at(int index);
const maxfx_param_def_t *maxfx_param_by_id(int32_t id);

/**
 * يكتب معاملًا من بايتات قيمته (`float` أو `int32` حسب `is_int`) ويُطبّعه إلى نطاقه —
 * و`NaN`/`Inf` يعود إلى الافتراض لا إلى صفر. ويعيد 1 إن تغيّرت القيمة فعليًّا.
 */
int maxfx_config_set_param(maxfx_config_t *cfg, int32_t id, const void *value, size_t vsize);

/** يقرأ معاملًا إلى بايتات (حجمُها المطلوب في `*vsize` عند الدخول والمُستخدَم عند الخروج). */
int maxfx_config_get_param(const maxfx_config_t *cfg, int32_t id, void *value, size_t *vsize);

#ifdef __cplusplus
}
#endif

#endif /* MAXFX_DSP_H */
