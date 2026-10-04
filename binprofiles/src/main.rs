//
// Copyright (C) 2026-2027 Zexshia
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

mod utils;
mod chipsets;
mod profiles;
mod props;
mod plan;

use std::env;
use std::fs;
use utils::*;
use profiles::*;

fn get_parent_pid() -> Option<u32> {
    fs::read_to_string("/proc/self/stat")
        .ok()
        .and_then(|stat| {
            stat.split_whitespace()
                .nth(3)
                .and_then(|ppid| ppid.parse::<u32>().ok())
        })
}

fn get_process_cmdline(pid: u32) -> Option<String> {
    fs::read_to_string(format!("/proc/{}/cmdline", pid))
        .ok()
        .map(|s| s.replace('\0', " ").trim().to_string())
}

/// هل المستدعي هو الخادم؟ عقد الملكية في `docs/ai/ARCHITECTURE-AUDIT.md` §١٢.١.
///
/// قراءة المصدر هنا (من أين نعرف الأب)، و**القرار** في `plan::caller_is_trusted` حيث
/// يُقاس بـ`cargo test` على سطري الأوامر الحقيقيين. والشرح الكامل لِـ«لماذا الشرط الثاني
/// ليس تكرارًا ميتًا» مكتوب هناك مع الشرط نفسه — لأن التعليق يجب أن يعيش حيث يعيش الكود
/// الذي يحرسه، لا في ملف آخر يفقد صلة القراءة.
fn verify_caller() -> bool {
    if let Some(ppid) = get_parent_pid() {
        if let Some(cmdline) = get_process_cmdline(ppid) {
            return plan::caller_is_trusted(&cmdline);
        }
    }
    false
}

fn main() {
    let args: Vec<String> = env::args().collect();

    if !verify_caller() {
        eprintln!("\x1b[31mError: This utility can only be called by sys.maxmanager-service\x1b[0m");
        std::process::exit(1);
    }

    // التفكيك والقاعدة الخالصة في `plan` (ويقيسهما `cargo test` على جدول العقود)،
    // وهنا التنفيذ وحده.
    match plan::classify(&args) {
        plan::Classification::Known(plan::Command::Initialize) => initialize(),
        plan::Classification::Known(plan::Command::Performance) => performance_profile(),
        plan::Classification::Known(plan::Command::Balanced) => balanced_profile(),
        plan::Classification::Known(plan::Command::Eco) => eco_mode(),
        plan::Classification::Known(plan::Command::ApplyFreqBalance) => applyfreqbalance(),
        plan::Classification::Known(plan::Command::ApplyFreqGame) => applyfreqgame(),
        plan::Classification::Candidate(arg) => {
            if plan::should_run_external(&arg) {
                plan::run_external(&arg, &args[2..]);
            }
        }
        plan::Classification::Empty => {}
    }
}
