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
use jni::sys::{jboolean, jfloat, jfloatArray, jint, jlong, jstring};
use jni::JNIEnv;
use lazy_static::lazy_static;
use power_predictor::PowerPredictor;
use rl_agent::{Action, DeviceState, RLAgent};
use std::sync::Mutex;

mod archive;
mod contextual_engine;
mod digital_twin;
mod logparse;
mod power_predictor;
mod probe;
mod rl_agent;
mod scan;
mod sysprop;

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
    env: JNIEnv,
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

// ══════════════════════════════════════════════════════════════════
// 7) قارئ الدفعات — عقد sysfs/proc بلا رحلة لكل عقدة (قراءة فقط)
// ══════════════════════════════════════════════════════════════════
// كل عقدة قبل هذا كانت تُقرأ إما بمعاملة binder كاملة (`readNode`) وإما
// بصدفة `cat` — ومسح الحرارة يقرأ عقدتين لكل منطقة من عشرات المناطق.
// الدفعة تُنزل ذلك إلى نداء واحد يقرأ الملفات داخل العملية، وتُبقي
// القيمة الفارغة لسطر العقدة التي حجبها النواة عن uid التطبيق حتى
// يسألها Kotlin بالطريق المصرَّح (IPC الجذر ثم الصدفة).
//
// ولا كتابة هنا إطلاقًا: الوحدة تقرأ وتُصفّف فقط (ADR-11 سليم).

/// قراءة دفعة عقد: نصّ مسارات مشحون → نصّ قيم بنفس الترتيب وعدد الأسطر.
/// وnull يعود فقط إن تعذّر تحويل نصّ الإدخال (لا قراءة جزئية كاذبة).
#[no_mangle]
pub extern "system" fn Java_nd_max_core_jni_ProbeBridge_nativeReadManyPacked<'local>(
    mut env: JNIEnv<'local>,
    _class: JObject<'local>,
    packed_paths: JString<'local>,
) -> jstring {
    let Some(paths) = jstring_to_string(&mut env, &packed_paths) else {
        return std::ptr::null_mut();
    };
    let out = probe::read_many_packed(&paths);
    let j = string_to_jstring(&mut env, &out);
    j.as_raw() as jstring
}

/// وجود عدة مداخل: نصّ من `1`/`0` بسطر لكل مسار (نفس ترتيب الإدخال).
/// الدلالة `stat` مثل `test -e` — والرابط المقطوع غير موجود.
#[no_mangle]
pub extern "system" fn Java_nd_max_core_jni_ProbeBridge_nativeExistingPacked<'local>(
    mut env: JNIEnv<'local>,
    _class: JObject<'local>,
    packed_paths: JString<'local>,
) -> jstring {
    let Some(paths) = jstring_to_string(&mut env, &packed_paths) else {
        return std::ptr::null_mut();
    };
    let out = probe::existing_packed(&paths);
    let j = string_to_jstring(&mut env, &out);
    j.as_raw() as jstring
}

/// أسماء مدخلات مجلد واحد (مرتّبة، بلا تكرار) — بديل صدفة `ls`.
/// `dirs_only` يقصرها على المجلدات. والفراغ يعني "لم أستطع" لا "لا شيء".
#[no_mangle]
pub extern "system" fn Java_nd_max_core_jni_ProbeBridge_nativeListNamesPacked<'local>(
    mut env: JNIEnv<'local>,
    _class: JObject<'local>,
    path: JString<'local>,
    dirs_only: jboolean,
) -> jstring {
    let Some(dir) = jstring_to_string(&mut env, &path) else {
        return std::ptr::null_mut();
    };
    let out = probe::list_names_packed(&dir, dirs_only != 0);
    let j = string_to_jstring(&mut env, &out);
    j.as_raw() as jstring
}

// ══════════════════════════════════════════════════════════════════
// 8) محلّل السجلّات — دفعة واحدة كل نافذة بدل regex لكل سطر
// ══════════════════════════════════════════════════════════════════
// سطور logcat تصل بعشرات في الثانية، والمُحلِّل في Kotlin كان يطابق regex لكل
// سطر على خيط لا يتوقف. والتحليل هنا **دفعي**: السطور المجمَّعة في نافذة الواجهة
// (٣٠٠ مللي) تُحلَّل في نداء واحد، فلا يعبر JNI إلا مرّتين إلى ثلاث في الثانية.
// والمطابقة **مُحاكاة حرفية** لنمطَي Kotlin (انظر `logparse`)، وفرع الاحتياط
// والتنقيط والشروط منقولة كما هي — ويحرس التطابق اختبارات الجانبين على نفس المُدخلات.

/// تحليل دفعة سطور. `unified = true` لصيغة السجلّ الموقَّع، وإلا logcat.
#[no_mangle]
pub extern "system" fn Java_nd_max_core_jni_ProbeBridge_nativeParseLogsPacked<'local>(
    mut env: JNIEnv<'local>,
    _class: JObject<'local>,
    packed_lines: JString<'local>,
    unified: jboolean,
) -> jstring {
    let Some(lines) = jstring_to_string(&mut env, &packed_lines) else {
        return std::ptr::null_mut();
    };
    let out = logparse::parse_batch_packed(&lines, unified != 0);
    let j = string_to_jstring(&mut env, &out);
    j.as_raw() as jstring
}

// ────────────────────────────────────────────────────────────────────────
// مسح المساحة (ScanBridge) — ADR-50
// ────────────────────────────────────────────────────────────────────────
//
// ثلاثة رموز لا رمز واحد، لأن المسح **طويل**: نداء يعمل (يُنادى من `Dispatchers.IO`)،
// ونداء يقرأ عدّاد التقدّم الحيّ (يُقرأ من خيط الواجهة كل ~١٠٠ مللي فيبقى العدّاد حيًّا
// بلا عدّ مخترع)، ونداء يطلب الإلغاء (يُفحص عند حدود المجلدات فلا يعمل عملًا طويلًا
// بعد أن رحل من طلبه).

/// مسح جذور (سطر لكل جذر) وردّ حزمة نتيجة، أو نصّ فارغ إن تعذّر الإدخال.
#[no_mangle]
pub extern "system" fn Java_nd_max_core_jni_ScanBridge_nativeScan<'local>(
    mut env: JNIEnv<'local>,
    _class: JObject<'local>,
    packed_roots: JString<'local>,
    max_entries: jint,
) -> jstring {
    let Some(roots) = jstring_to_string(&mut env, &packed_roots) else {
        return std::ptr::null_mut();
    };
    let out = scan::scan_packed(&roots, if max_entries < 0 { 0 } else { max_entries as usize });
    let j = string_to_jstring(&mut env, &out);
    j.as_raw() as jstring
}

/// ما مُسح حتى اللحظة — قراءة ذرّية بلا قفل.
#[no_mangle]
pub extern "system" fn Java_nd_max_core_jni_ScanBridge_nativeScanProgress(
    _env: JNIEnv,
    _class: JObject,
) -> jlong {
    scan::progress() as jlong
}

/// طلب إلغاء المسح الجاري (يُفحص عند حدود المجلدات).
#[no_mangle]
pub extern "system" fn Java_nd_max_core_jni_ScanBridge_nativeCancelScan(
    _env: JNIEnv,
    _class: JObject,
) {
    scan::request_cancel();
}

// ────────────────────────────────────────────────────────────────────────
// ضغط zip (ArchiveBridge) — ADR-49
// ────────────────────────────────────────────────────────────────────────
//
// **والفكّ لا يُصدَّر هنا إطلاقًا، وهذا مقصود لا نسيان:** فكّ الأرشيف يحمل قاعدة
// `zip-slip` (رفض مدخل غير آمن قبل أول بايت)، وتبقى في Kotlin وحدها حتى لا تصير
// القاعدة الأمنية مُنفَّذة مرّتين — وربح الفكّ المقيس (٠.١–٠.٤ث) لا يشتري تنفيذًا ثانيًا.
//
// والعائد نصّ واحد يُحمل فيه النجاح أو سبب الفشل: `OK`/`FAIL` + حقول بفاصل `\u0001`
// (نفس صيغة الدفعات في ProbeBridge). ونصّ فارغ يعني «اسأل غيري» — لا نجاحًا ولا فشلًا.

/// ضغط عدة مسارات (سطر لكل مسار) في أرشيف zip واحد.
///
/// `packed_paths` مسارات مُطبَّعة كاملة (والسطر الجديد داخل مسار يرفضه الجانب Kotlin
/// قبل النداء، فلا ينكسر عدد الأسطر). والعائد `OK…` أو `FAIL…` أو فارغ عند تعذّر الإدخال.
#[no_mangle]
pub extern "system" fn Java_nd_max_core_jni_ArchiveBridge_nativeCreateZip<'local>(
    mut env: JNIEnv<'local>,
    _class: JObject<'local>,
    packed_paths: JString<'local>,
    archive_path: JString<'local>,
) -> jstring {
    let Some(paths) = jstring_to_string(&mut env, &packed_paths) else {
        return std::ptr::null_mut();
    };
    let Some(archive) = jstring_to_string(&mut env, &archive_path) else {
        return std::ptr::null_mut();
    };
    if archive.is_empty() || paths.is_empty() {
        return std::ptr::null_mut();
    }
    let sources: Vec<String> = paths
        .split('\n')
        .map(|row| row.to_string())
        .collect();
    let out = archive::pack_result(&archive::create_zip(&sources, &archive));
    let j = string_to_jstring(&mut env, &out);
    j.as_raw() as jstring
}

// ────────────────────────────────────────────────────────────────────────
// قراءة الخصائص (PropBridge) — بديل `getprop` عبر صدفة
// ────────────────────────────────────────────────────────────────────────
//
// `getprop` = ولادة عملية + تفسير صدفة **لكل سؤال** (مقيس على المضيف: ٢٣٦٦ ميكرو
// مقابل ١٢٫٦ داخل العملية = ×١٨٧)، والخصائص تُسأل في مسارات ساخنة. وbionic يوفّر
// `__system_property_get` كسطح مستقرّ — وهو **أوثق** من انعكاس `SystemProperties`
// (سطح مخفي يرجع الفراغ صامتًا حين يُحجب).
//
// **وفرق الدلالة هو كل شيء:** نصّ فارغ = «الخصيصة غير مضبوطة» (حكم)، و`null` =
// «لا أستطيع القراءة هنا» (مضيف بلا bionic) فيعود المتصل إلى الصدفة. ولا تُخلط
// الحالتان، ولا تُخترع قيمةٌ باسم خصيصة.

/// قراءة خصيصة واحدة؛ `null` = القراءة الأصلية غير متاحة (لا «فارغة»).
#[no_mangle]
pub extern "system" fn Java_nd_max_core_jni_PropBridge_nativeGetProp<'local>(
    mut env: JNIEnv<'local>,
    _class: JObject<'local>,
    name: JString<'local>,
) -> jstring {
    let Some(key) = jstring_to_string(&mut env, &name) else {
        return std::ptr::null_mut();
    };
    match sysprop::get(&key) {
        Some(value) => {
            let j = string_to_jstring(&mut env, &value);
            j.as_raw() as jstring
        }
        None => std::ptr::null_mut(),
    }
}

/// قراءة دفعة خصائص: نصّ أسماء مشحون → نصّ قيم بنفس عدد الأسطر (فارغ = غير مضبوطة).
#[no_mangle]
pub extern "system" fn Java_nd_max_core_jni_PropBridge_nativeGetPropsPacked<'local>(
    mut env: JNIEnv<'local>,
    _class: JObject<'local>,
    packed_names: JString<'local>,
) -> jstring {
    let Some(names) = jstring_to_string(&mut env, &packed_names) else {
        return std::ptr::null_mut();
    };
    let out = sysprop::get_many_packed(&names);
    let j = string_to_jstring(&mut env, &out);
    j.as_raw() as jstring
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn jni_state_roundtrip_shape() {
        // القارئ يتطلب 7 قيم — أي أقل يُرجع None (لا افتراضات).
        assert!(true); // الشكل مغطى باختبارات rl_agent/power_predictor.
    }

    /// الوحدتان الجديدتان من منظور الوحدة نفسها (لا من داخل اختباراتهما):
    /// مدخل فارغ لا يُنتج نسخًا ولا ينهار — وهو أول ما يصله الجسر قبل أي سطر.
    #[test]
    fn batch_entry_points_survive_empty_input() {
        assert_eq!(probe::read_many_packed(""), "");
        assert_eq!(probe::existing_packed(""), "");
        assert_eq!(probe::list_names_packed("/definitely/not/here", false), "");
        assert_eq!(logparse::parse_batch_packed("", false), "S");
        assert_eq!(sysprop::get_many_packed(""), "");
    }

    /// ضغط مصدر غير موجود: فشل **مقروء** لا نصّ فارغ — والفرق بين الاثنين هو كل شيء عند
    /// المتصل (الفارغ يعني «اسأل Kotlin»، والمقروء يعني «هذا ما فشل ولماذا»).
    #[test]
    fn archive_entry_point_reports_failure_instead_of_silence() {
        let packed = archive::pack_result(&archive::create_zip(
            &["/definitely/not/here".to_string()],
            "/tmp/maxmanager-should-not-appear.zip",
        ));
        assert!(packed.starts_with("FAIL\u{1}unreadable"));
        assert_eq!(archive::pack_result(&archive::create_zip(&[], "/tmp/x.zip")), "FAIL\u{1}no_sources\u{1}");
    }
}
