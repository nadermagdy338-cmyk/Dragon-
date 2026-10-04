/*
 * Copyright (C) 2025-2026 Ryanistr
 *
 * Derived from Rianixia-ThermalCore (https://github.com/ryanistr/Rianixia-ThermalCore),
 * modified for MaxManager (modifications: Copyright (C) 2026 Nader Magdy).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
use super::android_ffi::Logger;
use super::constants::*;

// ============================================================================
// PID CONTROLLER
// ============================================================================

pub struct PidController {
    pub kp: f32,
    pub ki: f32,
    pub kd: f32,

    integral: f32,
    prev_error: f32,
}

impl PidController {
    pub fn new(kp: f32, ki: f32, kd: f32) -> Self {
        PidController {
            kp,
            ki,
            kd,
            integral: 0.0,
            prev_error: 0.0,
        }
    }

    pub fn reset(&mut self) {
        self.integral = 0.0;
        self.prev_error = 0.0;
    }

    pub fn compute(
        &mut self,
        current_temp: i32,
        target_temp: i32,
        dt_seconds: f32,
        _logger: &Logger
    ) -> f32 {
        if dt_seconds <= 0.0 {
            return 0.0;
        }

        let error = (current_temp - target_temp) as f32;

        let p_term = self.kp * error;

        self.integral += error * dt_seconds;
        self.integral = self.integral.clamp(-PID_INTEGRAL_LIMIT, PID_INTEGRAL_LIMIT);
        let i_term = self.ki * self.integral;

        let derivative = (error - self.prev_error) / dt_seconds;
        let d_term = self.kd * derivative;

        self.prev_error = error;

        let output = p_term + i_term + d_term;
        let clamped = output.clamp(0.0, 1.0);

        clamped
    }
}

pub struct PolicyManager {
    pub pid: PidController,
}

impl PolicyManager {
    pub fn new() -> Self {
        PolicyManager {
            pid: PidController::new(PID_KP, PID_KI, PID_KD),
        }
    }

    pub fn update_params(&mut self, kp: f32, ki: f32, kd: f32) {
        self.pid.kp = kp;
        self.pid.ki = ki;
        self.pid.kd = kd;
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// العقد يُقرأ من الملف **وقت الترجمة** (`include_str!`): تعديل الجدول يُسقط الاختبار، فلا يوجد
    /// «تمرير كاذب» من نسخة مخزّنة. وهذا ما يتعذّر على الجدول المُقرأ وقت التشغيل من مسار قد لا يوجد.
    const CONTRACT: &str = include_str!("../../fixtures/contracts/thermal_policy.tsv");

    /// تسامح f32 المُعلَن في ترويسة الجدول.
    const TOLERANCE: f32 = 1e-4;

    #[derive(Debug)]
    enum Step {
        Compute { current: i32, target: i32, dt: f32, expected: f32, line: usize },
        // لا تحمل `line`: `parse_row` يذكر رقم السطر في كل خطأ بنفسه، فحقلٌ هنا
        // لا يُقرأ ولا يُضيف تشخيصًا — وكان `cargo test` يشتكي منه (`field `line` is
        // never read`) لأن رمز الاختبار وحده يُصرَّف في `cargo test` لا في `cargo build`.
        Reset,
    }

    fn parse_row(line_no: usize, line: &str) -> Result<(String, Step), String> {
        let f: Vec<&str> = line.split('\t').collect();
        if f.len() != 7 {
            return Err(format!("row {}: expected 7 columns, got {}", line_no, f.len()));
        }
        let run = f[0].to_string();
        let step = match f[1] {
            "reset" => Step::Reset,
            "compute" => {
                let current = f[2].parse::<i32>().map_err(|e| format!("row {}: current: {}", line_no, e))?;
                let target = f[3].parse::<i32>().map_err(|e| format!("row {}: target: {}", line_no, e))?;
                let dt = f[4].parse::<f32>().map_err(|e| format!("row {}: dt: {}", line_no, e))?;
                let expected = f[5].parse::<f32>().map_err(|e| format!("row {}: expected: {}", line_no, e))?;
                Step::Compute { current, target, dt, expected, line: line_no }
            }
            other => return Err(format!("row {}: unknown action {:?}", line_no, other)),
        };
        Ok((run, step))
    }

    fn contract() -> Vec<(String, Step)> {
        CONTRACT
            .lines()
            .enumerate()
            .filter(|(_, l)| !l.trim().is_empty() && !l.starts_with('#'))
            .map(|(i, l)| parse_row(i + 1, l).expect("contract table must parse"))
            .collect()
    }

    /// الحجم مفروض بالمساواة الدقيقة: حذف صفّ — أو «إصلاح» جدول بتقليصه — يُسقط الدعوى بدل أن يمرّ بصمت.
    #[test]
    fn contract_size_is_exactly_pinned() {
        let rows = contract();
        assert_eq!(rows.len(), 13, "contract rows");
        let runs: std::collections::BTreeSet<&str> =
            rows.iter().map(|(r, _)| r.as_str()).collect();
        assert_eq!(runs.len(), 6, "distinct runs: {:?}", runs);
    }

    /// الجوهر: أعِد تشغيل كل `run` على وحدة تحكّم واحدة بالترتيب وقارن المخرَج بالمتوقّع المشتقّ.
    #[test]
    fn pid_outputs_match_the_hand_derived_contract() {
        let logger = Logger::new();
        let mut current_run = String::new();
        let mut pid = PidController::new(PID_KP, PID_KI, PID_KD);
        let mut checked = 0;

        for (run, step) in contract() {
            if run != current_run {
                current_run = run;
                pid = PidController::new(PID_KP, PID_KI, PID_KD);
            }
            match step {
                Step::Reset => pid.reset(),
                Step::Compute { current, target, dt, expected, line } => {
                    let got = pid.compute(current, target, dt, &logger);
                    assert!(
                        (got - expected).abs() < TOLERANCE,
                        "line {}: compute({}, {}, {}) = {} but contract says {} (|Δ| = {})",
                        line, current, target, dt, got, expected, (got - expected).abs()
                    );
                    checked += 1;
                }
            }
        }
        assert_eq!(checked, 12, "every compute row must have been evaluated");
    }

    /// الأداة تقيس نفسها: صفّ مُشوَّه أو عمل مجهول **يُرفض** لا يُتجاهل.
    /// (درس `--self-test` في بوابات المستودع: أداة تمرّ على كل شيء لا تُثبت شيئًا.)
    #[test]
    fn malformed_rows_are_rejected_not_skipped() {
        assert!(parse_row(1, "A\tcompute\t60\t40\t1.0\t0.57").is_err(), "6 columns must be rejected");
        assert!(parse_row(1, "A\tcompute\tx\t40\t1.0\t0.57\tnote").is_err(), "non-numeric current must be rejected");
        assert!(parse_row(1, "A\tteleport\t60\t40\t1.0\t0.57\tnote").is_err(), "unknown action must be rejected");
        assert!(parse_row(1, "A\tcompute\t60\t40\t1.0\t0.57\tnote").is_ok(), "a well-formed row must parse");
    }
}
