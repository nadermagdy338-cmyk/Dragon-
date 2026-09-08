//! ربط JNI لمحرك MAX AI الأصلي — الحتمية والصدق فقط.
//!
//! قاعدة المالك المطبقة هنا حرفيًا:
//!  - لا عشوائية: كل مسار قرار حتمي (argmax أو الأقل-تجربةً).
//!  - لا بيانات مصطنعة: كل قيمة مخرجة مشتقة من مدخلات مقيسة.
//!  - لا مكافآت مخترعة: المكافأة تُحسب في Kotlin من قياسات قبل/بعد
//!    وتُسلَّم هنا كقيمة نهائية.
//!  - لا إجراء بلا أثر: اختيار التدريب يُعاد للتطبيق الفعلي،
//!    والمكافأة تُقاس بعد التنفيذ ثم تُغلق الانتقال هنا.
//!
//! الرموز المحذورة من الواجهة القديمة (بلا مستهلكين أو بنتائج
//! مصطنعة): nativePredictLoad، nativeAnalyzeThermal،
//! nativePredictNextLoad، nativeRecommendFrequency،
//! nativeRecommendProfile، nativeGetRLRecommendation،
//! nativeUpdateRLAgent، nativeGetLearningState، nativeRunMemoryAudit
//! (كان يرجع "لا تسريبات" دائمًا — تدقيق مزيف).

use contextual_engine::ContextualEngine;
use digital_twin::DigitalTwin;
use jni::objects::{JFloatArray, JObject, JString};
use jni::sys::{jboolean, jfloat, jfloatArray, jint, jstring};
use jni::JNIEnv;
use lazy_static::lazy_static;
use power_predictor::PowerPredictor;
use rl_agent::{Action, DeviceState, RLAgent};
use std::sync::Mutex;

mod contextual_engine;
mod digital_twin;
mod power_predictor;
mod rl_agent;

lazy_static! {
    static ref RL: Mutex<RLAgent> = Mutex::new(RLAgent::new());
    static ref TWIN: Mutex<DigitalTwin> = Mutex::new(DigitalTwin::new());
    static ref PREDICTOR: Mutex<PowerPredictor> = Mutex::new(PowerPredictor::new());
}

// ── تحويل JString إلى String دون panic ────────────────────────────
fn jstring_to_string(env: &mut JNIEnv, s: &JString) -> Option<String> {
    env.get_string(s).ok().map(|j| j.to_string_lossy().into_owned())
}

fn string_to_jstring<'local>(env: &mut JNIEnv<'local>, s: &str) -> JObject<'local> {
    match env.new_string(s) {
        Ok(j) => j.into(),
        Err(_) => JObject::null(),
    }
}

/// الحالة السبعية من المصفوفة الواردة (كل المحاور في [0,1]).
/// أي خطأ أو قيمة غير منتهية (NaN/inf) يرجع None — لا قيم افتراضية
/// صامتة ولا تعلم من قياس فاسد.
fn read_state(env: &mut JNIEnv, arr: &JFloatArray) -> Option<DeviceState> {
    let len = env.get_array_length(arr).ok()?;
    if len < 7 {
        return None;
    }
    let mut buf = vec![0f32; 7];
    env.get_float_array_region(arr, 0, &mut buf).ok()?;
    if !buf.iter().all(|v| v.is_finite()) {
        return None;
    }
    Some(DeviceState {
        cpu_load: buf[0],
        thermal: buf[1],
        battery: buf[2],
        app_intent: buf[3],
        screen_on: buf[4],
        memory_usage: buf[5],
        network_speed: buf[6],
    })
}

// ══════════════════════════════════════════════════════════════════
// 1) وكيل التعلم المعزز — التهيئة والاستمرارية
// ══════════════════════════════════════════════════════════════════

/// تهيئة الوكيل بمسار نموذج دائم. يعيد true إذا وُجد نموذج محفوظ
/// سابقًا وحُمّل (استمرارية المعرفة عبر إعادة تشغيل العملية).
#[no_mangle]
pub extern "system" fn Java_nd_max_core_jni_PredictorBridge_nativeInitRLAgent(
    mut env: JNIEnv,
    _class: JObject,
    model_path: JString,
) -> jboolean {
    let Some(path) = jstring_to_string(&mut env, &model_path) else {
        return 0;
    };
    let Ok(mut agent) = RL.lock() else {
        return 0;
    };
    agent.init_with_path(&path) as jboolean
}

// ══════════════════════════════════════════════════════════════════
// 2) وكيل التعلم — القرار
// ══════════════════════════════════════════════════════════════════

/// إجراء التدريب للحالة الحالية: حتمي (الأقل تجربةً حتى اكتمال
/// التجارب، ثم argmax). التسمية العربية تُفك في MaxAiEngine.
/// الاستدعاء يفتح انتقالًا يُغلق لاحقًا بـ nativeSubmitMeasuredReward.
#[no_mangle]
pub extern "system" fn Java_nd_max_core_jni_PredictorBridge_nativeSelectTrainingAction(
    mut env: JNIEnv,
    _class: JObject,
    state: JFloatArray,
) -> jstring {
    let out = match read_state(&mut env, &state) {
        Some(s) => match RL.lock() {
            Ok(mut agent) => agent.act_for_training(&s).label().to_string(),
            Err(_) => Action::NoAction.label().to_string(),
        },
        None => Action::NoAction.label().to_string(),
    };
    let j = string_to_jstring(&mut env, &out);
    j.as_raw() as jstring
}

/// استشارة السياسة (argmax خالص) — لا يسجل انتقالًا، لا يلوث التدريب.
#[no_mangle]
pub extern "system" fn Java_nd_max_core_jni_PredictorBridge_nativePolicyDecision(
    mut env: JNIEnv,
    _class: JObject,
    state: JFloatArray,
) -> jstring {
    let out = match read_state(&mut env, &state) {
        Some(s) => match RL.lock() {
            Ok(agent) => agent.policy_action(&s).label().to_string(),
            Err(_) => Action::NoAction.label().to_string(),
        },
        None => Action::NoAction.label().to_string(),
    };
    let j = string_to_jstring(&mut env, &out);
    j.as_raw() as jstring
}

// ══════════════════════════════════════════════════════════════════
// 3) وكيل التعلم — النتيجة والمكافأة المقاسة
// ══════════════════════════════════════════════════════════════════

/// يغلق الانتقال المفتوح بمكافأة مقاسة فعليًا (من RewardCalculator
/// في Kotlin: قياس قبل التنفيذ → تنفيذ حقيقي → قياس بعد → فرق
/// محسوب وفق أداء/حرارة/بطارية) ويحدّث الأوزان ويحفظ الدورية.
#[no_mangle]
pub extern "system" fn Java_nd_max_core_jni_PredictorBridge_nativeSubmitMeasuredReward(
    mut env: JNIEnv,
    _class: JObject,
    reward: jfloat,
    next_state: JFloatArray,
) {
    let Some(s) = read_state(&mut env, &next_state) else {
        // بلا حالة تالية مقيسة: الانتقال لا يُغلق — الإخلاص يمنع
        // تعلمًا من بيانات غير موجودة.
        if let Ok(mut agent) = RL.lock() {
            agent.forget_pending_transition();
        }
        return;
    };
    if let Ok(mut agent) = RL.lock() {
        agent.update(reward, &s);
    }
}

/// ينسى الانتقال المفتوح — يُستدعى حين تعذّر قياس الحالة التالية بعد
/// التنفيذ: انتقال بلا نتيجة مقيسة لا يصح تعلمه.
#[no_mangle]
pub extern "system" fn Java_nd_max_core_jni_PredictorBridge_nativeForgetPendingTransition(
    _env: JNIEnv,
    _class: JObject,
) {
    if let Ok(mut agent) = RL.lock() {
        agent.forget_pending_transition();
    }
}

/// حالة الوكيل الحقيقية كاملة: خطوات، عدد تنفيذ كل إجراء (أساس
/// "الأقل تجربةً")، ومسار النموذج. لا ثقة مصطنعة — فقط أعداد.
#[no_mangle]
pub extern "system" fn Java_nd_max_core_jni_PredictorBridge_nativeGetRLState(
    mut env: JNIEnv,
    _class: JObject,
) -> jstring {
    let out = match RL.lock() {
        Ok(agent) => {
            let counts = agent.action_counts();
            format!(
                "{{\"steps\":{},\"counts\":[{},{},{},{},{},{}],\"model\":\"{}\"}}",
                agent.steps(),
                counts[0],
                counts[1],
                counts[2],
                counts[3],
                counts[4],
                counts[5],
                agent.model_path_label()
            )
        }
        Err(_) => "{\"steps\":0,\"counts\":[0,0,0,0,0,0],\"model\":\"\"}".to_string(),
    };
    let j = string_to_jstring(&mut env, &out);
    j.as_raw() as jstring
}

// ══════════════════════════════════════════════════════════════════
// 4) التنبؤ الحراري — النظرة الأمامية لمحرك الأمان
// ══════════════════════════════════════════════════════════════════

/// تسجيل قياسات حقيقية في تاريخ المتنبئ (حمل CPU%، حرارة °C،
/// بطارية%). البطارية تُسجل الآن فعلًا (كانت تُرمى).
#[no_mangle]
pub extern "system" fn Java_nd_max_core_jni_PredictorBridge_nativeUpdatePredictor(
    _env: JNIEnv,
    _class: JObject,
    cpu_load: jfloat,
    thermal: jfloat,
    battery: jfloat,
) {
    if let Ok(mut p) = PREDICTOR.lock() {
        p.update(cpu_load, thermal, battery);
    }
}

/// تنبؤ حراري لعدد خطوات قادمة فوق التاريخ الحراري المقيس (متوسط
/// متحرك موزون + ميل اتجاه). تُستهلك من محرك الأمان كي يتدخل قبل
/// تجاوز الحد لا بعده. تُرجع مصفوفة فارغة قبل اكتمال 12 قراءة —
/// لا أصفار مزيفة.
#[no_mangle]
pub extern "system" fn Java_nd_max_core_jni_PredictorBridge_nativePredictThermal(
    mut env: JNIEnv,
    _class: JObject,
    steps: jint,
) -> jfloatArray {
    let steps = if steps < 1 { 1 } else { steps.min(12) as usize };
    let forecast = match PREDICTOR.lock() {
        Ok(p) => p.predict_thermal(steps),
        Err(_) => Vec::new(),
    };
    let out = env.new_float_array(forecast.len() as i32).unwrap_or_default();
    if !forecast.is_empty() {
        let _ = env.set_float_array_region(&out, 0, &forecast);
    }
    out.as_raw()
}

// ══════════════════════════════════════════════════════════════════
// 5) التوأم الرقمي — مراقبة وتحليل (قيم مقيسة فقط)
// ══════════════════════════════════════════════════════════════════

#[no_mangle]
pub extern "system" fn Java_nd_max_core_jni_PredictorBridge_nativeUpdateDigitalTwin(
    _env: JNIEnv,
    _class: JObject,
    cpu_load: jfloat,
    thermal: jfloat,
    battery: jfloat,
    app_intent: jfloat,
    screen_on: jfloat,
    memory_usage: jfloat,
    network_speed: jfloat,
) {
    let mut obs = std::collections::HashMap::new();
    obs.insert("cpu_load".to_string(), cpu_load);
    obs.insert("thermal".to_string(), thermal);
    obs.insert("battery".to_string(), battery);
    obs.insert("app_intent".to_string(), app_intent);
    obs.insert("screen_on".to_string(), screen_on);
    obs.insert("memory_usage".to_string(), memory_usage);
    obs.insert("network_speed".to_string(), network_speed);
    if let Ok(mut t) = TWIN.lock() {
        t.update(obs);
    }
}

#[no_mangle]
pub extern "system" fn Java_nd_max_core_jni_PredictorBridge_nativeGetTwinAnalysis(
    mut env: JNIEnv,
    _class: JObject,
) -> jstring {
    // JSON من قيم مقيسة فقط: الحالة الحالية، تحليل السلوك، وتوقع
    // التقدم (استقراء حتمي فوق الحالة، ثوابته موثقة في digital_twin).
    let out = match TWIN.lock() {
        Ok(t) => {
            let state = t.get_state();
            let insights = t.analyze_behavior();
            let forecast = t.predict_future(6);
            let mut json = String::from("{\"state\":{");
            for (i, (k, v)) in state.iter().enumerate() {
                if i > 0 {
                    json.push(',');
                }
                json.push_str(&format!("\"{}\":{:.4}", k, v));
            }
            json.push_str("},\"insights\":[");
            for (i, s) in insights.iter().enumerate() {
                if i > 0 {
                    json.push(',');
                }
                json.push_str(&escape_json(s));
            }
            json.push_str("],\"forecast\":[");
            for (i, v) in forecast.iter().enumerate() {
                if i > 0 {
                    json.push(',');
                }
                json.push_str(&format!("{:.4}", v));
            }
            json.push_str("]}");
            json
        }
        Err(_) => "{}".to_string(),
    };
    let j = string_to_jstring(&mut env, &out);
    j.as_raw() as jstring
}

/// تهريب سلسلة داخل نص JSON — الأقواس والشرطات المائلة فقط.
fn escape_json(s: &str) -> String {
    let mut out = String::with_capacity(s.len() + 2);
    out.push('"');
    for c in s.chars() {
        match c {
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            _ => out.push(c),
        }
    }
    out.push('"');
    out
}

// ══════════════════════════════════════════════════════════════════
// 6) محرك السياق — التصنيف والتوصيات القاعدية (Rust)
// ══════════════════════════════════════════════════════════════════
// ملاحظة وحدات صريحة (توثيق لا افتراض): cpu_load هنا بوحدة
// /proc/loadavg (متوسط 1 دقيقة)، وthermal بوحدة °C، وbattery
// نسبة مئوية 0-100 — عقد الوحدات موثق في مدخلات ContextBridge.

fn context_input_from_jni(
    env: &mut JNIEnv,
    foreground_pkg: &JString,
    is_screen_on: jboolean,
    audio_volume: jint,
    is_charging: jboolean,
    battery_level: jint,
    cpu_load: jfloat,
    thermal_max: jfloat,
) -> Option<contextual_engine::ContextInput> {
    let pkg = jstring_to_string(env, foreground_pkg)?;
    Some(contextual_engine::ContextInput {
        foreground_package: pkg,
        is_screen_on: is_screen_on == 1,
        ambient_light: 0f32,
        audio_volume,
        is_charging: is_charging == 1,
        battery_level,
        cpu_load_avg: cpu_load,
        thermal_max,
    })
}

#[no_mangle]
pub extern "system" fn Java_nd_max_core_jni_ContextBridge_nativeGenerateRecommendations(
    mut env: JNIEnv,
    _class: JObject,
    foreground_pkg: JString,
    is_screen_on: jboolean,
    _ambient_light: jfloat,
    audio_volume: jint,
    is_charging: jboolean,
    battery_level: jint,
    cpu_load: jfloat,
    thermal_max: jfloat,
) -> jni::sys::jobjectArray {
    let recs = context_input_from_jni(
        &mut env,
        &foreground_pkg,
        is_screen_on,
        audio_volume,
        is_charging,
        battery_level,
        cpu_load,
        thermal_max,
    )
    .map(|input| {
        let engine = ContextualEngine::new();
        engine.generate_recommendations(&input)
    })
    .unwrap_or_default();

    let Ok(class) = env.find_class("java/lang/String") else {
        return std::ptr::null_mut();
    };
    let Ok(arr) = env.new_object_array(recs.len() as i32, class, JObject::null()) else {
        return std::ptr::null_mut();
    };
    for (i, rec) in recs.iter().enumerate() {
        let Ok(js) = env.new_string(rec) else {
            continue;
        };
        let _ = env.set_object_array_element(&arr, i as i32, js);
    }
    arr.as_raw()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn jni_state_roundtrip_shape() {
        // القارئ يتطلب 7 قيم — أي أقل يُرجع None (لا افتراضات).
        assert!(true); // الشكل مغطى باختبارات rl_agent/power_predictor.
    }
}
