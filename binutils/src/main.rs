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

use std::env;
use utils::*;

fn main() {
    let args: Vec<String> = env::args().collect();

    // التفكيك خالص في `utils::plan::parse` (ويقيسه `cargo test` بجدول العقود)،
    // وهنا التنفيذ وحده — فالأسماء الأحد عشر صارت سطحًا مُختبرًا لا سلاسل في ذراع match.
    match plan::parse(&args) {
        plan::Command::SetsGov(gov) => setsgov(&gov),
        plan::Command::SetsIo(scheduler) => sets_io(&scheduler),
        plan::Command::SetsMaliGov(gov) => sets_mali_gov(&gov),
        plan::Command::SetThermalCore(state) => setthermalcore(&state),
        plan::Command::CheckMaliPath => check_mali_path(),
        plan::Command::FsTrim => fstrim(),
        plan::Command::EnableDnd => enable_dnd(),
        plan::Command::DisableDnd => disable_dnd(),
        plan::Command::SetRefreshRates(rate) => setrefreshrates(&rate),
        plan::Command::RestartService => restartservice(),
        plan::Command::SetRender(renderer) => setrender(&renderer),
        plan::Command::External { program, args } => plan::run_external(&program, &args),
        plan::Command::MissingArgument | plan::Command::Empty => {}
    }
}
