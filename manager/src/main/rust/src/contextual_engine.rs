/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */

/// تصنيف وضع الاستخدام الحالي بناءً على بيانات السياق.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum UsageMode {
    Idle,       // الجهاز خامل أو الشاشة مغلقة
    Browsing,   // تصفح عام (وسائل تواصل، إنترنت)
    Gaming,     // ألعاب عالية الأداء
    Media,      // تشغيل فيديو أو صوت
    Calling,    // مكالمة صوتية أو فيديو
    Unknown,
}

/// بنية تحتوي على بيانات السياق المدخلة.
#[derive(Debug, Clone)]
pub struct ContextInput {
    pub foreground_package: String,
    pub is_screen_on: bool,
    /// ⚠️ **مُدخل ميت مُعلن** (`I-75`): تُمرّره Kotlin في العقد ثم يُسقَط — معامل JNI هنا
    /// `_ambient_light`، وبانية `ContextInput` في `lib.rs` تضع `0f32` ثابتًا، ولا Rust ولا
    /// البديل القاعدي في `ContextBridge` يقرأه في أي حكم. فالحقل يُبقى في العقد ولا يُحذف
    /// من طرف واحد (حذفه تغييرُ توقيع JNI وواجهة Kotlin — قرار المالك)، ويُعلام صراحةً
    /// بدل إخفائه: `#[allow(dead_code)]` هنا **إقرارٌ لا تجميل**.
    #[allow(dead_code)]
    pub ambient_light: f32,
    pub audio_volume: i32,
    pub is_charging: bool,
    pub battery_level: i32,
    pub cpu_load_avg: f32,
    pub thermal_max: f32,
}

/// محرك تحليل السياق: يصنف الوضع الحالي ويقترح إجراءات.
pub struct ContextualEngine {
    // قوائم التطبيقات المعروفة (يمكن توسيعها)
    game_packages: Vec<String>,
    media_packages: Vec<String>,
    social_packages: Vec<String>,
    call_packages: Vec<String>,
}

impl ContextualEngine {
    pub fn new() -> Self {
        // أمثلة على حزم معروفة (يمكن تحميلها من ملف خارجي لاحقاً)
        let game_packages = vec![
            "com.activision.callofduty".to_string(),
            "com.tencent.ig".to_string(),
            "com.dts.freefiremax".to_string(),
            "com.roblox.client".to_string(),
        ];
        let media_packages = vec![
            "com.google.android.youtube".to_string(),
            "com.spotify.music".to_string(),
            "com.netflix.mediaclient".to_string(),
        ];
        let social_packages = vec![
            "com.whatsapp".to_string(),
            "com.facebook.katana".to_string(),
            "com.instagram.android".to_string(),
            "com.twitter.android".to_string(),
        ];
        // حزم واجهة الاتصال الظاهرة أثناء مكالمة جارية — بدونها كان
        // متغير Calling غير قابل للوصول أبدًا (فرع ميت في التصنيف)
        let call_packages = vec![
            "com.android.incallui".to_string(),
            "com.samsung.android.incallui".to_string(),
            "com.google.android.dialer".to_string(),
            "com.android.dialer".to_string(),
        ];
        Self { game_packages, media_packages, social_packages, call_packages }
    }

    /// تصنيف الوضع الحالي بناءً على بيانات السياق.
    pub fn classify(&self, input: &ContextInput) -> UsageMode {
        if !input.is_screen_on {
            return UsageMode::Idle;
        }

        // تحقق من التطبيق النشط
        let pkg = &input.foreground_package;
        // مكالمة جارية تتقدم على كل شيء: واجهة الاتصال تظهر فوق
        // أي تطبيق عند ورود المكالمة
        if self.call_packages.iter().any(|c| pkg.contains(c.as_str())) {
            return UsageMode::Calling;
        }
        if self.game_packages.iter().any(|g| pkg.contains(g.as_str())) {
            return UsageMode::Gaming;
        }
        if self.media_packages.iter().any(|m| pkg.contains(m.as_str())) {
            return UsageMode::Media;
        }
        if self.social_packages.iter().any(|s| pkg.contains(s.as_str())) {
            return UsageMode::Browsing;
        }

        // تحليل إضافي: إذا كان الصوت مرتفعاً والحمل منخفضاً -> وسائط
        if input.audio_volume > 50 && input.cpu_load_avg < 2.0 {
            return UsageMode::Media;
        }

        // إذا كان الحمل مرتفعاً والحرارة مرتفعة -> ألعاب (حتى لو لم تكن في القائمة)
        if input.cpu_load_avg > 5.0 && input.thermal_max > 45.0 {
            return UsageMode::Gaming;
        }

        UsageMode::Unknown
    }

    /// توليد توصيات بناءً على الوضع المصنف.
    pub fn generate_recommendations(&self, input: &ContextInput) -> Vec<String> {
        let mode = self.classify(input);
        let mut recommendations = Vec::new();

        match mode {
            UsageMode::Gaming => {
                // صياغات مطابقة لعقد الكلمات المفتاحية في
                // RecommendationTextClassifier:
                // "وضع الألعاب" → EnableGamingMode (ملف أداء + boost)
                recommendations.push("تفعيل وضع الألعاب لتحسين الأداء".to_string());
                if input.thermal_max > 50.0 {
                    recommendations.push("خفض تردد المعالج قليلاً لتقليل الحرارة ومنع الخنق الحراري".to_string());
                }
                if input.battery_level < 20 && !input.is_charging {
                    recommendations.push("البطارية منخفضة، يُنصح بتوصيل الشاحن".to_string());
                }
            }
            UsageMode::Media => {
                // "الوضع المتوازن" وحده (بلا "توفير الطاقة" في النص نفسه)
                // كي تصنَّف متوازنة — سابقًا كانت تطابق "توفير الطاقة"
                // أولًا فتُصنَّفpowersave خلافًا لمعناها الصريح.
                recommendations.push("ضبط التردد على الوضع المتوازن أثناء تشغيل الوسائط".to_string());
                recommendations.push("تفعيل وضع توفير الطاقة لتقليل الاستهلاك أثناء الفيديو".to_string());
                if input.audio_volume > 80 {
                    // نصيحة استشارية بلا كلمات إجراء — سابقًا كانت تطابق
                    // "توفير الطاقة" فتُصنَّف ApplyPowerSaveProfile، أي أن
                    // نصيحة صوت كانت ستُبدّل الملف العام آليًا مع AI مفعّل!
                    recommendations.push("خفض مستوى الصوت قليلاً لحماية السمع".to_string());
                }
            }
            UsageMode::Browsing => {
                recommendations.push("الحمل منخفض، يُنصح بخفض التردد لتوفير البطارية".to_string());
                recommendations.push("تفعيل وضع توفير الطاقة (Power Save)".to_string());
            }
            UsageMode::Idle => {
                // "إيقاف التطبيقات" → SuggestClosingApps (كانت "تعليق
                // العمليات" لا تطابق شيئًا فتُرمى)
                recommendations.push("الجهاز في وضع الخمول، يُنصح بإيقاف التطبيقات غير المستخدمة".to_string());
                recommendations.push("تأجيل التحديثات والنسخ الاحتياطي حتى الاستخدام التالي".to_string());
            }
            UsageMode::Calling => {
                recommendations.push("تحسين أولوية المكالمة عبر ضبط الوضع المتوازن".to_string());
            }
            UsageMode::Unknown => {
                recommendations.push("لا يمكن تحديد الوضع بدقة، يُنصح بالحفاظ على الإعدادات الحالية".to_string());
                recommendations.push("مراقبة الأداء لبضع دقائق لتحديد النمط".to_string());
            }
        }

        // توصيات عامة
        if input.is_charging && input.cpu_load_avg > 2.0 {
            // "رفع تردد" → IncreaseCpuFrequency: تحرير السقف أثناء الشحن
            recommendations.push("الجهاز متصل بالشاحن: يُنصح برفع تردد المعالج للاستفادة من الطاقة الخارجية".to_string());
        }

        if input.thermal_max > 55.0 {
            recommendations.push("تحذير: درجة الحرارة مرتفعة جداً، يُنصح بإيقاف تشغيل التطبيقات الثقيلة".to_string());
        }

        recommendations
    }
}
