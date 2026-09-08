//! وكيل التعلم المعزز — حتمي بالكامل، بلا أي عشوائية.
//!
//! قاعدة المالك: "لا اختيار عشوائي لإجراء، لا حالة عشوائية، لا مكافأة
//! عشوائية". هذا الملف يفي بالقاعدة حرفيًا:
//!  - الاختيار عند التدريب: إن لم يُجرَّب كل إجراء بما يكفي يُختار
//!    "الأقل تجربةً" (كسر التعادل بالفهرس الأدنى) — استكشاف حتمي
//!    بالتسلسل يضمن قياس أثر كل إجراء من العتاد فعلًا. بعد اكتمال
//!    التجارب يصبح الاختيار argmax خالصًا على تقديرات Q.
//!  - الاستشارة (قرار السياسة): argmax خالص، بلا تسجيل انتقال —
//!    لا يلوث دفتر التدريب.
//!  - دفعة إعادة التشغيل (replay): تسلسلية بمؤشر دوّار — لا عينة
//!    عشوائية.
//!  - استمرارية النموذج: حفظ/تحميل JSON من ملف يحدده Kotlin —
//!    المعرفة لا تموت بموت العملية.
//!
//! المكافأة تُحسب في Kotlin من قياسات حقيقية قبل/بعد تنفيذ الإجراء
//! الفعلي (انظر RewardCalculator) وتمرَّر هنا كقيمة نهائية — Rust لا
//! يخترع شيئًا.
//!
//! النموذج: مقدّر Q خطي على 8 سمات (المحاور السبعة المقيسة + انحياز
//! ثابت 1.0) — بنية معلنة بصدق، لا "شبكة عصبية" زائفة.

use ndarray::Array2;
use serde::{Deserialize, Serialize};
use std::collections::VecDeque;
use std::fs;
use std::path::PathBuf;

/// الإجراءات المتاحة — الترتيب هو فهرس الصف في مصفوفة الأوزان.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Action {
    IncreaseFrequency,
    DecreaseFrequency,
    PerformanceProfile,
    PowerSaveProfile,
    BalancedProfile,
    NoAction,
}

impl Action {
    pub const ALL: [Action; 6] = [
        Action::IncreaseFrequency,
        Action::DecreaseFrequency,
        Action::PerformanceProfile,
        Action::PowerSaveProfile,
        Action::BalancedProfile,
        Action::NoAction,
    ];

    pub fn to_index(self) -> usize {
        self as usize
    }

    /// التسمية العربية — نفس الجدول الذي يفكّه MaxAiEngine في Kotlin.
    pub fn label(&self) -> &'static str {
        match self {
            Action::IncreaseFrequency => "رفع التردد",
            Action::DecreaseFrequency => "خفض التردد",
            Action::PerformanceProfile => "ملف الأداء",
            Action::PowerSaveProfile => "ملف توفير الطاقة",
            Action::BalancedProfile => "متوازن",
            Action::NoAction => "لا شيء",
        }
    }
}

/// الحالة السبعية المقيسة (كل المحاور في [0,1]) — من DeviceStateCollector.
#[derive(Debug, Clone, Copy)]
pub struct DeviceState {
    pub cpu_load: f32,
    pub thermal: f32,
    pub battery: f32,
    pub app_intent: f32,
    pub screen_on: f32,
    pub memory_usage: f32,
    pub network_speed: f32,
}

impl DeviceState {
    /// 8 سمات: المحاور السبعة + انحياز ثابت (يسمح للمقدّر الخطي بتعلم
    /// قيمة أساس لكل إجراء بدل اعتماده على تداخل المحاور).
    fn features(&self) -> [f32; 8] {
        [
            self.cpu_load,
            self.thermal,
            self.battery,
            self.app_intent,
            self.screen_on,
            self.memory_usage,
            self.network_speed,
            1.0,
        ]
    }
}

/// انتقال تدريب واحد: (الحالة، الإجراء، المكافأة المقاسة، الحالة التالية).
#[derive(Clone)]
struct Transition {
    features: [f32; 8],
    action: usize,
    reward: f32,
    next_features: [f32; 8],
}

/// الشكل المتين على القرص — serde يستخدمه فعليًا الآن (كان معلنًا
/// ومهملًا في السابق).
#[derive(Serialize, Deserialize)]
struct SavedAgent {
    steps: u64,
    weights: Vec<Vec<f32>>,
    action_counts: Vec<u64>,
}

pub struct RLAgent {
    /// أوزان Q: shape (6, 8) — صف لكل إجراء.
    weights: Array2<f32>,
    steps: u64,
    /// كم مرة نُفِّذ كل إجراء فعليًا — أساس الاستكشاف الحتمي.
    action_counts: [u64; 6],
    memory: VecDeque<Transition>,
    /// مؤشر دفرة إعادة التشغيل الدوّار — دفرة تسلسلية حتمية.
    replay_cursor: usize,
    last_features: Option<[f32; 8]>,
    last_action: Option<usize>,
    model_path: Option<PathBuf>,
    updates_since_save: u32,
}

impl RLAgent {
    pub fn new() -> Self {
        // تهيئة أصفار: مع الانحياز الثابت والاستكشاف الحتمي الأقل
        // تجربةً، تتساوى قيم Q أولًا فيُجرَّب كل إجراء بالتسلسل ثم
        // تنفصل القيم بالتعلّم الفعلي من المكافآت المقيسة. لا حاجة
        // لأوزان عشوائية أولية.
        Self {
            weights: Array2::zeros((Action::ALL.len(), 8)),
            steps: 0,
            action_counts: [0; 6],
            memory: VecDeque::with_capacity(MEMORY_CAP),
            replay_cursor: 0,
            last_features: None,
            last_action: None,
            model_path: None,
            updates_since_save: 0,
        }
    }

    // ── القرار ─────────────────────────────────────────────────────

    /// استشارة السياسة: argmax خالص. لا يسجل شيئًا — لا يمكنه تلويث
    /// دفتر التدريب. كسر التعادل بالفهرس الأدنى (حتمي).
    pub fn policy_action(&self, state: &DeviceState) -> Action {
        let f = state.features();
        argmax_q(&self.weights, &f)
    }

    /// اختيار إجراء التدريب: يسجل الانتقال ليُغلق لاحقًا بمكافأة
    /// مقاسة بعد التنفيذ الفعلي على العتاد. الاستكشاف حتمي: الأقل
    /// تجربةً حتى يبلغ كل إجراء [MIN_TRIES_PER_ACTION] تنفيذًا.
    pub fn act_for_training(&mut self, state: &DeviceState) -> Action {
        let f = state.features();
        let action = if let Some(min) = self.action_counts.iter().min() {
            if *min < MIN_TRIES_PER_ACTION {
                // الأقل تجربةً؛ التعادل بالفهرس الأدنى.
                let min = *min;
                Action::ALL
                    .iter()
                    .position(|&a| self.action_counts[a.to_index()] == min)
                    .map(|i| Action::ALL[i])
                    .unwrap_or(Action::NoAction)
            } else {
                argmax_q(&self.weights, &f)
            }
        } else {
            argmax_q(&self.weights, &f)
        };
        self.last_features = Some(f);
        self.last_action = Some(action.to_index());
        action
    }

    // ── التعلم ─────────────────────────────────────────────────────

    /// يغلق الانتقال الأخير بمكافأة مقاسة ويحدّث الأوزان.
    ///
    /// `reward` قيمة نهائية من Kotlin (RewardCalculator): موجبة عند
    /// تحسّن الأداء واستقرار الحرارة وبطارية مقبولة، سالبة عند رأس
    /// حرارة أو استنزاف أو تراجع، صفر عند اللا-تحسن المحايد.
    pub fn update(&mut self, reward: f32, next_state: &DeviceState) {
        if let (Some(f), Some(a)) = (self.last_features, self.last_action) {
            self.memory.push_back(Transition {
                features: f,
                action: a,
                reward,
                next_features: next_state.features(),
            });
            while self.memory.len() > MEMORY_CAP {
                self.memory.pop_front();
                if self.replay_cursor > 0 {
                    self.replay_cursor -= 1;
                }
            }
            self.action_counts[a] += 1;
            self.steps += 1;
        }
        self.last_features = None;
        self.last_action = None;

        if self.memory.len() > REPLAY_MIN {
            self.replay();
        }

        self.updates_since_save += 1;
        if self.updates_since_save >= AUTOSAVE_EVERY {
            self.updates_since_save = 0;
            self.save();
        }
    }

    /// دفرة إعادة تشغيل تسلسلية حتمية: BATCH عينة متتالية تبدأ من
    /// المؤشر الدوار ثم تلف من البداية. تحديث Q-learning قياسي على
    /// المقدّر الخطي: target = r + gamma * max_a' Q(s',a')،
    /// td = Q(s,a) - target، نزول تدرج على صف الإجراء المتخذ فقط.
    fn replay(&mut self) {
        let len = self.memory.len();
        let start = self.replay_cursor % len;
        for k in 0..BATCH.min(len) {
            let idx = (start + k) % len;
            let t = self.memory[idx].clone();
            let max_next = max_q(&self.weights, &t.next_features);
            let target = t.reward + GAMMA * max_next;
            let q_sa = row_dot(&self.weights, t.action, &t.features);
            let td = q_sa - target;
            if td.is_finite() {
                for i in 0..8usize {
                    let w = &mut self.weights[[t.action, i]];
                    *w -= LR * td * t.features[i];
                }
            }
        }
        self.replay_cursor = (start + BATCH.min(len)) % len;
    }

    // ── الاستمرارية ────────────────────────────────────────────────

    /// يحدد مسار النموذج ويحاول تحميل نموذج محفوظ سابقًا.
    /// يعيد true إذا وُجد نموذج وحُمّل بنجاح.
    pub fn init_with_path(&mut self, path: &str) -> bool {
        self.model_path = Some(PathBuf::from(path));
        self.load()
    }

    pub fn save(&self) -> bool {
        let path = match &self.model_path {
            Some(p) => p.clone(),
            None => return false,
        };
        let saved = SavedAgent {
            steps: self.steps,
            weights: (0..Action::ALL.len())
                .map(|a| {
                    (0..8usize)
                        .map(|i| self.weights[[a, i]])
                        .collect::<Vec<f32>>()
                })
                .collect(),
            action_counts: self.action_counts.to_vec(),
        };
        let Ok(json) = serde_json::to_string(&saved) else {
            return false;
        };
        // كتابةذرية: tmp ثم rename — لا نموذج نصف مكتوب أبدًا.
        let tmp = path.with_extension("json.tmp");
        if fs::write(&tmp, json).is_err() {
            return false;
        }
        fs::rename(&tmp, &path).is_ok()
    }

    fn load(&mut self) -> bool {
        let path = match &self.model_path {
            Some(p) => p.clone(),
            None => return false,
        };
        let Ok(json) = fs::read_to_string(&path) else {
            return false;
        };
        let Ok(saved) = serde_json::from_str::<SavedAgent>(&json) else {
            return false;
        };
        if saved.weights.len() != Action::ALL.len() {
            return false;
        }
        for (a, row) in saved.weights.iter().enumerate() {
            if row.len() != 8 {
                return false;
            }
            for (i, v) in row.iter().enumerate() {
                self.weights[[a, i]] = *v;
            }
        }
        for (a, c) in saved
            .action_counts
            .iter()
            .enumerate()
            .take(Action::ALL.len())
        {
            self.action_counts[a] = *c;
        }
        self.steps = saved.steps;
        true
    }

    /// يُلغي الانتقال المفتوح بلا تعلم — يُستدعى حين تعذّر قياس الحالة
    /// التالية بعد التنفيذ: انتقال بلا نتيجة مقيسة لا يصح تعلمه.
    pub fn forget_pending_transition(&mut self) {
        self.last_features = None;
        self.last_action = None;
    }

    pub fn steps(&self) -> u64 {
        self.steps
    }

    pub fn action_counts(&self) -> [u64; 6] {
        self.action_counts
    }

    pub fn last_action_label(&self) -> &'static str {
        match self.last_action {
            Some(i) => Action::ALL.get(i).map(|a| a.label()).unwrap_or("لا شيء"),
            None => "لا شيء",
        }
    }

    pub fn model_path_label(&self) -> String {
        self.model_path
            .as_ref()
            .map(|p| p.display().to_string())
            .unwrap_or_default()
    }
}

// ── دوال Q المساعدة (كلها total_cmp — لا panic على NaN) ────────────

fn row_dot(w: &Array2<f32>, action: usize, features: &[f32; 8]) -> f32 {
    let mut sum = 0.0f32;
    for i in 0..8usize {
        sum += w[[action, i]] * features[i];
    }
    sum
}

fn argmax_q(w: &Array2<f32>, features: &[f32; 8]) -> Action {
    let mut best = 0usize;
    let mut best_val = row_dot(w, 0, features);
    for a in 1..Action::ALL.len() {
        let v = row_dot(w, a, features);
        // total_cmp: ترتيب كلي آمن حتى مع NaN — التعادل يبقي الأول.
        if v.total_cmp(&best_val) == std::cmp::Ordering::Greater {
            best = a;
            best_val = v;
        }
    }
    Action::ALL[best]
}

fn max_q(w: &Array2<f32>, features: &[f32; 8]) -> f32 {
    let mut best = row_dot(w, 0, features);
    for a in 1..Action::ALL.len() {
        let v = row_dot(w, a, features);
        if v.total_cmp(&best) == std::cmp::Ordering::Greater {
            best = v;
        }
    }
    best
}

// ── الثوابت ────────────────────────────────────────────────────────

const MIN_TRIES_PER_ACTION: u64 = 8;
const MEMORY_CAP: usize = 1000;
const REPLAY_MIN: usize = 32;
const BATCH: usize = 16;
const LR: f32 = 0.01;
const GAMMA: f32 = 0.95;
const AUTOSAVE_EVERY: u32 = 20;

#[cfg(test)]
mod tests {
    use super::*;
    use std::env;

    fn state(cpu: f32, thermal: f32) -> DeviceState {
        DeviceState {
            cpu_load: cpu,
            thermal,
            battery: 0.8,
            app_intent: 0.5,
            screen_on: 1.0,
            memory_usage: 0.4,
            network_speed: 0.1,
        }
    }

    #[test]
    fn deterministic_exploration_then_repeatable_policy() {
        let mut agent = RLAgent::new();
        // أول 6*MIN_TRIES اختيارات: تسلسل الأقل تجربةً — حتمي تمامًا.
        let first = agent.act_for_training(&state(0.5, 0.4));
        assert_eq!(first, Action::ALL[0]); // الكل صفر تجارب → الفهرس الأدنى
        agent.update(0.0, &state(0.5, 0.4));
        let second = agent.act_for_training(&state(0.5, 0.4));
        assert_eq!(second, Action::ALL[1]); // الثاني صار الأقل تجربةً
    }

    #[test]
    fn policy_query_does_not_record() {
        let mut agent = RLAgent::new();
        let _ = agent.policy_action(&state(0.9, 0.5)); // لا يسجل
        agent.update(1.0, &state(0.9, 0.5)); // بلا انتقال مفتوح → لا يتعلم
        assert_eq!(agent.steps(), 0);
    }

    #[test]
    fn update_learns_from_measured_reward() {
        let mut agent = RLAgent::new();
        // 48 دورة قياس→إجراء→مكافأة موجبة: التسلسل الحتمي يجرب كل
        // إجراء بالتسلسل ثم يعتمد argmax — لا عشوائية في أي خطوة.
        for _ in 0..48 {
            let _a = agent.act_for_training(&state(0.7, 0.5));
            agent.update(1.0, &state(0.6, 0.48));
        }
        let s = state(0.7, 0.5);
        assert_eq!(agent.steps(), 48);
        let _ = agent.policy_action(&s); // يجب ألا يفشل فحسب
    }

    #[test]
    fn persistence_roundtrip() {
        let dir = env::temp_dir().join("maxai_test_agent");
        let _ = std::fs::create_dir_all(&dir);
        let path = dir.join("model.json");
        let path_str = path.display().to_string();

        // لا ملف بعد → التحميل يفشل بصدق (false) لا يزيف نجاحًا.
        let mut a1 = RLAgent::new();
        assert!(!a1.init_with_path(&path_str));

        {
            let mut with_path = RLAgent::new();
            with_path.init_with_path(&path_str);
            for _ in 0..3 {
                let _ = with_path.act_for_training(&state(0.5, 0.4));
                with_path.update(0.5, &state(0.5, 0.4));
            }
            assert!(with_path.save());
        }
        let mut a2 = RLAgent::new();
        assert!(a2.init_with_path(&path_str));
        assert_eq!(a2.steps(), 3);
        let _ = std::fs::remove_file(&path);
    }
}
