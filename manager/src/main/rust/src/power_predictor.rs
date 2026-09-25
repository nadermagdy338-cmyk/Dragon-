/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
//! مُتنبئ القياسات فوق التاريخ المقيس فعلًا — لا نماذج بلا تدريب.
//!
//! سجل LSTM القديم حُذف: أوزانه كانت تُهيَّأ عشوائيًا بلا مسار تدريب،
//! فأي "تنبؤ" منها كان ضجيجًا. البديل: استقراء موزون فوق التاريخ
//! الحقيقي المقيس (متوسط متحرك موزون + ميل اتجاه خطي) — رياضيات
//! معلنة، مدخلات مقيسة، حدود صادقة (مصفوفة فارغة قبل اكتمال النصاب
/// لا أصفارًا مزيفة).

/// نظام التنبؤ بالقياسات: يحفظ تاريخًا حقيقيًا لثلاثة محاور
/// (حمل CPU%، حرارة °C، بطارية%) ويستقرئ عليها.
pub struct PowerPredictor {
    cpu_history: Vec<f32>,
    thermal_history: Vec<f32>,
    battery_history: Vec<f32>,
    max_history: usize,
}

impl PowerPredictor {
    pub fn new() -> Self {
        Self {
            cpu_history: Vec::with_capacity(100),
            thermal_history: Vec::with_capacity(100),
            battery_history: Vec::with_capacity(100),
            max_history: 100,
        }
    }

    /// تحديث التواريخ الثلاثة بقياسات حقيقية. (الحرارة والبطارية كانت
    /// تُقبلان وتُرميان في النسخة القديمة — الآن تُسجلان فعليًا.)
    pub fn update(&mut self, cpu_load: f32, thermal: f32, battery: f32) {
        push_capped(&mut self.cpu_history, cpu_load, self.max_history);
        push_capped(&mut self.thermal_history, thermal, self.max_history);
        push_capped(&mut self.battery_history, battery, self.max_history);
    }

    /// تنبؤ حراري لعدد خطوات قادمة: استقراء موزون فوق التاريخ الحراري
    /// المقيس. النصاب: 12 قراءة — قبلها تُرجع Vec فارغ (صادق) لا أصفارًا.
    ///
    /// المستهلك: محرك الأمان في Kotlin (SafetyEngine) كنظرة أمامية
    /// كي يتدخل قبل تجاوز الحد لا بعده.
    pub fn predict_thermal(&self, steps: usize) -> Vec<f32> {
        if steps == 0 {
            return Vec::new();
        }
        weighted_forecast(&self.thermal_history, steps)
    }

    /// تنبؤ حمل CPU — نفس المنهج فوق تاريخ الحمل المقيس.
    ///
    /// ⚠️ **قدرة مبنيّة وغير مُستهلكة** (`I-77`): لا تصدير JNI لها ولا مستدعي في الإنتاج
    /// (المُستهلك الوحيد `predict_thermal` من محرك الأمان). تبقى لأنها مقيسة باختبار،
    /// وسطرها هنا إقرارٌ بالحالة لا ادّعاءُ استخدام.
    #[allow(dead_code)]
    pub fn predict_cpu(&self, steps: usize) -> Vec<f32> {
        if steps == 0 {
            return Vec::new();
        }
        weighted_forecast(&self.cpu_history, steps)
    }

    /// عدد قراءات التاريخ الحراري المتوفرة — أساس حارس النصاب في الاختبارات.
    ///
    /// و`#[cfg(test)]` لأنّ **لا تصدير JNI يعرضها**: الوصف السابق هنا («يعرضها Kotlin
    /// بصدق») كان ادّعاءً غير صحيح — Kotlin لا يرى هذه الأعداد إطلاقًا (`I-77`).
    #[cfg(test)]
    pub fn thermal_samples(&self) -> usize {
        self.thermal_history.len()
    }

    /// عدد قراءات تاريخ الحمل المتوفرة — للاختبار وحده (السبب أعلاه).
    #[cfg(test)]
    pub fn cpu_samples(&self) -> usize {
        self.cpu_history.len()
    }

    /// عدد قراءات تاريخ البطارية المتوفرة — للاختبار وحده (السبب أعلاه).
    #[cfg(test)]
    pub fn battery_samples(&self) -> usize {
        self.battery_history.len()
    }
}

fn push_capped(v: &mut Vec<f32>, value: f32, cap: usize) {
    if !value.is_finite() {
        return; // لا ندخل NaN/inf في التاريخ أصلًا
    }
    v.push(value);
    if v.len() > cap {
        v.remove(0);
    }
}

/// استقراء موزون: متوسط متحرك موزون (الأحدث أثقل) فوق آخر 12 قراءة
/// + ميل الاتجاه الخطي بين طرفي النافذة، مدى الخطوات المطلوب.
/// القيم مقيدة بمدى الإدخال المرصود [min, max] للتاريخ — لا تتجاوز
/// أبدًا ما رآه الجهاز فعلًا.
fn weighted_forecast(history: &[f32], steps: usize) -> Vec<f32> {
    const WINDOW: usize = 12;
    if history.len() < WINDOW {
        return Vec::new();
    }
    let n = history.len();
    let recent = &history[n - WINDOW..];

    let mut wma = 0.0_f32;
    let mut wsum = 0.0_f32;
    for (i, &v) in recent.iter().enumerate() {
        let w = (i + 1) as f32;
        wma += v * w;
        wsum += w;
    }
    wma /= wsum;

    let trend = (recent[recent.len() - 1] - recent[0]) / (recent.len() as f32 - 1.0);

    // القيد بالمدى المرصود: التنبؤ لا يخترع قيمًا خارج تجربة الجهاز.
    let min_seen = recent.iter().cloned().fold(f32::INFINITY, f32::min);
    let max_seen = recent.iter().cloned().fold(f32::NEG_INFINITY, f32::max);

    (1..=steps)
        .map(|k| (wma + trend * k as f32).clamp(min_seen, max_seen))
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn cold_start_is_empty_not_fake_zeros() {
        let mut p = PowerPredictor::new();
        for i in 0..5 {
            p.update(50.0 + i as f32, 40.0, 80.0);
        }
        // قبل النصاب: مصفوفة فارغة — لا [0,0,0...] مزيفة.
        assert!(p.predict_thermal(6).is_empty());
        assert_eq!(p.thermal_samples(), 5);
    }

    #[test]
    fn thermal_forecast_follows_measured_trend() {
        let mut p = PowerPredictor::new();
        // ارتفاع حراري مطّرد مقيس: 40 → 51
        for i in 0..12 {
            p.update(50.0, 40.0 + i as f32, 80.0);
        }
        let f = p.predict_thermal(6);
        assert_eq!(f.len(), 6);
        // الاتجاه صاعد: كل خطوة ≥ سابقتها تقريبًا (سماحية تراكم صغيرة)
        assert!(f[0] < f[5] || (f[5] - f[0]).abs() < 0.5);
        // ولا يتجاوز أعلى قيمة مقيسة
        assert!(f.iter().all(|&v| v <= 51.0 + 1e-3));
    }

    #[test]
    fn nan_never_enters_history() {
        let mut p = PowerPredictor::new();
        // NaN في محور المعالج وinf في محور البطارية: لا يدخلان،
        // والمحور السليم (42.0) يدخل فعلًا — وإلا كان "الرفض" رفضًا للكل.
        p.update(f32::NAN, 42.0, f32::INFINITY);
        assert_eq!(p.cpu_samples(), 0);
        assert_eq!(p.thermal_samples(), 1);
        assert_eq!(p.battery_samples(), 0);
        // والتنبؤ لا يبنى على قياس مرفوض: النصاب لم يكتمل بعدُ.
        assert!(p.predict_cpu(6).is_empty());
    }
}
