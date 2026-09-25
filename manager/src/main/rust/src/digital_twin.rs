use std::collections::HashMap;
use ndarray::Array2;

#[derive(Clone, Debug)]
pub struct BayesianNode {
    /// ⚠️ **مُعلن لا مُستهلك** (`I-76`): الاسم مكرَّر مع مفتاح الخريطة في `nodes`، ولا يُقرأ
    /// في أي حكم.
    #[allow(dead_code)]
    pub name: String,
    pub parents: Vec<String>,
    /// ⚠️ **جداول الاحتمال تُبنى ولا تُقرأ** (`I-76`): كلها أصفار (`Array2::zeros`)،
    /// والاستدلال في `infer_unobserved` **متوسط حسابي لقيم الآباء** لا بحثٌ في جدول شرطي.
    /// فـ«البايزي» هنا اسمٌ لا حساب — وهذا يُقال في الكود لا في سجلّ بعيد. والقرار للمالك:
    /// تُستهلك فعلًا (تُقدَّر من العيّنات) أو تُزال؛ والحقل باقٍ فلا يُحذف عملٌ لإسكات تحذير
    /// (ADR-18).
    #[allow(dead_code)]
    pub cpt: Array2<f32>,
    pub current_value: f32,
}

pub struct DigitalTwin {
    nodes: HashMap<String, BayesianNode>,
    history: Vec<HashMap<String, f32>>,
    max_history: usize,
}

impl DigitalTwin {
    pub fn new() -> Self {
        let mut nodes = HashMap::new();
        
        let cpu_node = BayesianNode {
            name: "cpu_load".to_string(),
            parents: vec!["app_intent".to_string(), "thermal".to_string()],
            cpt: Array2::zeros((3, 3)),
            current_value: 0.0,
        };
        nodes.insert("cpu_load".to_string(), cpu_node);
        
        let thermal_node = BayesianNode {
            name: "thermal".to_string(),
            parents: vec!["cpu_load".to_string(), "battery".to_string()],
            cpt: Array2::zeros((3, 3)),
            current_value: 0.0,
        };
        nodes.insert("thermal".to_string(), thermal_node);
        
        let battery_node = BayesianNode {
            name: "battery".to_string(),
            parents: vec!["cpu_load".to_string(), "screen_on".to_string()],
            cpt: Array2::zeros((3, 3)),
            current_value: 0.0,
        };
        nodes.insert("battery".to_string(), battery_node);
        
        let intent_node = BayesianNode {
            name: "app_intent".to_string(),
            parents: vec!["foreground_pkg".to_string()],
            cpt: Array2::zeros((4, 1)),
            current_value: 0.0,
        };
        nodes.insert("app_intent".to_string(), intent_node);
        
        let pkg_node = BayesianNode {
            name: "foreground_pkg".to_string(),
            parents: vec![],
            cpt: Array2::zeros((1, 1)),
            current_value: 0.0,
        };
        nodes.insert("foreground_pkg".to_string(), pkg_node);
        
        let screen_node = BayesianNode {
            name: "screen_on".to_string(),
            parents: vec![],
            cpt: Array2::zeros((1, 1)),
            current_value: 0.0,
        };
        nodes.insert("screen_on".to_string(), screen_node);

        // القياسان المرسلان من حلقة التعلم (memory_usage و
        // network_speed) لم يكن لهما عقد، فكانا يُرميان بصمت في
        // كل تحديث — التوأم الآن يتتبع الحالة السبعية كاملة.
        let memory_node = BayesianNode {
            name: "memory_usage".to_string(),
            parents: vec![],
            cpt: Array2::zeros((1, 1)),
            current_value: 0.0,
        };
        nodes.insert("memory_usage".to_string(), memory_node);

        let network_node = BayesianNode {
            name: "network_speed".to_string(),
            parents: vec![],
            cpt: Array2::zeros((1, 1)),
            current_value: 0.0,
        };
        nodes.insert("network_speed".to_string(), network_node);

        Self {
            nodes,
            history: Vec::new(),
            max_history: 100,
        }
    }

    pub fn update(&mut self, observations: HashMap<String, f32>) {
        let observed: std::collections::HashSet<String> =
            observations.keys().cloned().collect();
        for (name, value) in &observations {
            if let Some(node) = self.nodes.get_mut(name) {
                node.current_value = *value;
            }
        }
        // الاستدلال يملأ العقد غير المُلاحظة في هذه الدورة فقط: القياس
        // الحقيقي أرضية الحقيقة. الصيغة السابقة كانت تستبدل قيم
        // cpu_load/thermal/battery المُقاسة بمتوسط آبائها في كل تحديث —
        // أي أن التوأم كان "ينسى" ملاحظاته في الخطوة نفسها ويحلل
        // قيمًا مستنتجة بلا معنى بدل القياسات الفعلية.
        self.infer_unobserved(&observed);
        self.history.push(observations);
        if self.history.len() > self.max_history {
            self.history.remove(0);
        }
    }

    fn infer_unobserved(&mut self, observed: &std::collections::HashSet<String>) {
        let node_names: Vec<String> = self.nodes.keys().cloned().collect();
        let mut updates = Vec::new();

        for name in node_names {
            if observed.contains(&name) {
                continue;
            }
            if let Some(node) = self.nodes.get(&name) {
                if node.parents.is_empty() {
                    continue;
                }
                let parent_values: Vec<f32> = node.parents.iter()
                    .filter_map(|p| self.nodes.get(p).map(|n| n.current_value))
                    .collect();
                if parent_values.len() == node.parents.len() {
                    let prob = parent_values.iter().sum::<f32>() / parent_values.len() as f32;
                    updates.push((name, prob));
                }
            }
        }

        for (name, prob) in updates {
            if let Some(node) = self.nodes.get_mut(&name) {
                node.current_value = prob;
            }
        }
    }
    
    pub fn predict_future(&self, horizon_seconds: u64) -> Vec<f32> {
        let mut predictions = Vec::new();
        let mut current = self.get_state();
        let intent = current.get("app_intent").copied().unwrap_or(0.5);

        for _ in 0..horizon_seconds {
            if let Some(cpu) = current.get_mut("cpu_load") {
                if intent > 0.7 {
                    *cpu = (*cpu + 0.1).min(1.0);
                } else if intent < 0.3 {
                    *cpu = (*cpu - 0.05).max(0.0);
                }
            }
            predictions.push(current.get("cpu_load").copied().unwrap_or(0.0));
        }
        predictions
    }
    
    pub fn get_state(&self) -> HashMap<String, f32> {
        self.nodes.iter()
            .map(|(k, v)| (k.clone(), v.current_value))
            .collect()
    }
    
    pub fn analyze_behavior(&self) -> Vec<String> {
        let mut recommendations = Vec::new();
        let state = self.get_state();
        
        if let (Some(cpu), Some(thermal)) = (state.get("cpu_load"), state.get("thermal")) {
            if *cpu > 0.8 && *thermal > 0.7 {
                recommendations.push("الحمل مرتفع جداً والحرارة مرتفعة. يُنصح بتخفيف الحمل أو تبريد الجهاز.".to_string());
            } else if *cpu > 0.8 {
                recommendations.push("الحمل مرتفع. قد تحتاج إلى إغلاق بعض التطبيقات.".to_string());
            }
        }
        
        if let Some(battery) = state.get("battery") {
            if *battery < 0.2 {
                recommendations.push("البطارية منخفضة. يُنصح بتفعيل وضع توفير الطاقة.".to_string());
            }
        }

        if let Some(mem) = state.get("memory_usage") {
            if *mem > 0.85 {
                recommendations.push("استهلاك الذاكرة مرتفع. يُنصح بإيقاف التطبيقات غير المستخدمة.".to_string());
            }
        }

        if let Some(intent) = state.get("app_intent") {
            // صياغة استشارية: هذه نصوص تحليل تُعرض ولا يُنفّذها أحد
            // آليًا — "سيتم رفع التردد" كانت وعدًا زائفًا بإجراء
            // لا مُنفّذ له.
            if *intent > 0.7 {
                recommendations.push("تم الكشف عن نية ألعاب. يُنصح برفع التردد لتحسين الأداء.".to_string());
            } else if *intent < 0.3 {
                recommendations.push("الجهاز في وضع خمول. يُنصح بخفض التردد لتوفير الطاقة.".to_string());
            }
        }
        
        recommendations
    }
}
