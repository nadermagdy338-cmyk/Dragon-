/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */

//! الجدول الخالص — بلا أي إدخال/إخراج — لما تفعله `sys.maxmanager-utilityconf` لكل argv.
//!
//! **لماذا وُجدت هذه الوحدة:** كان الجدول يوزّع الأوامر في `main.rs`، وكل دالة تنفّذ
//! أثرها في مكانه، فلم يكن في المستودع **قياس واحد** لما تفعله الثنائية مقابل ما
//! يتوقّعه مستدعوها. والخادم C يرسل `sys.maxmanager-utilityconf setrender vulkan` ثم
//! **يفترض** أن ذلك يضبط `debug.hwui.renderer` — والافتراض مكتوب صريحًا في
//! `archdaemon/tests/parity_test.c` («ما تفعله `setrender vulkan` على الجهاز»)، أي
//! نموذج لا شهادة. فصارت الأوامر والأوامر الصدفية وأثرها على الخصائص **دوالًّ خالصة**
//! تُقاس على المضيف بلا جهاز ولا NDK، ومسار التنفيذ الحقيقي يستعمل **نفس الثوابت** —
//! فلا نسخة ثانية من المنطق، بل سطح واحد يقرؤه المُنفَّذ والـfixture معًا.
//!
//! والعقد المرجعي في `fixtures/contracts/binutils_cli.tsv`، ويُقاس بـ`cargo test`
//! داخل `binutils` (وخطوة CI نفسها). ومستدعو الأسماء أدناه (مقيسون من المصدر):
//! الخادم C (`ConfigHandler/RenderingHandler.c` · `ConfigHandler/RefreshRateHandler.c` ·
//! `SystemProfile/SystemProfiles.c` · `BinaryCLI/CLIUtility.c`) والتطبيق Kotlin
//! (`ui/viewmodel/TweakViewmodel.kt`)، والصدفة (`binprofiles/src/profiles/mod.rs`).

use std::process::Command as ProcessCommand;

// ── مسارات sysfs التي تكتبها الثنائية (نصوص حرّة، لا قائمة مغلقة كما في الخصائص) ──
// وُضعت ثوابت لأن §٣.٤ في تدقيق المعمار يسردها **بعينها**؛ ونصّ خاطئ = كتابة صامتة
// إلى لا شيء (لا خطأ، لا أثر) — وهو صنف عطب لا يكشفه إلا قياس النصّ نفسه.
pub const CPU_GOVERNOR_GLOB: &str = "/sys/devices/system/cpu/cpu*/cpufreq/scaling_governor";
pub const MALI_GOVERNOR_GLOB: &str = "/sys/class/devfreq/*.mali/governor";
/// المجلّد الذي **يُستطلع** (لا يُكتب) ليقول `checkmalipath` هل للجهاز Mali.
/// واستطلاع بمسار خاطئ = `false` صامتة ⇒ يختفي قسم Mali من الواجهة بلا خطأ، ولذلك يُثبَّت كنصّ.
pub const MALI_DIR_GLOB: &str = "/sys/class/devfreq/*.mali";
/// أجهزة الكتلة التي يجرّب `setsIO` كتّابتها بالترتيب (ما وُجد منها فقط).
pub const IO_BLOCK_DEVICES: [&str; 5] = ["sda", "sdb", "sdc", "mmcblk0", "mmcblk1"];
/// الوضع الذي يُكتب به ثم يُستعاد: كتابة ثم قراءة فقط (يُثبَّت لأن قيمة خاطئة =
/// عقدة تبقى مفتوحة للكتابة، أو عقدة تصبح غير قابلة للكتابة أبدًا).
pub const SYSFS_WRITE_MODE: u32 = 0o644;
pub const SYSFS_RESTORE_MODE: u32 = 0o444;

// ── أوامر الصدفة التي تكتبها الثنائية، مستخرجة إلى ثوابت لأن العقد يقيس نصّها ──
pub const THERMALCORE_SPAWN: &str = "sys.maxmanager-rianixiathermalcore &";
/// انتظار `setthermalcore` بعد التشغيل قبل `pgrep` — **محدود وثابت** (لا حلقة تنتظر
/// حدثًا ولا مهلة تُحسب)، وهو الانتظار الوحيد في هذه الثنائية، فيُثبَّت هنا (§١٢.٤).
pub const THERMALCORE_SETTLE_SECS: u64 = 1;
pub const THERMALCORE_PROBE: &str = "pgrep -f sys.maxmanager-rianixiathermalcore";
pub const THERMALCORE_KILL: &str = "pkill -9 -f sys.maxmanager-rianixiathermalcore";
pub const SERVICE_KILL: &str = "pkill -9 -f sys.maxmanager-service";
pub const APPMONITORING_KILL: &str = "pkill -9 -f sys.maxmanager-appmonitoring";
pub const RESTART_SCRIPT: &str = "sh /data/adb/modules/MaxManager/service.sh &";
pub const FSTRIM_VDC: &str = "vdc fstrim dotrim";
pub const FSTRIM_SM: &str = "sm fstrim";
pub const DND_ENABLE: &str = "cmd notification set_dnd priority";
pub const DND_DISABLE: &str = "cmd notification set_dnd off";

/// أي أداة تكتب الخصيصة: `setprop` يُبقي القيمة الفارغة فارغةً، أما `resetprop`
/// فالقيمة الفارغة عنده تعني **حذف** الخصيصة (`--delete`) — وهو فرق مقيس أوقعه
/// `setrender default` (يمسح الثلاثة بـ`setprop`، ويحذف `ro.hwui.use_vulkan` بـ`resetprop`).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PropTool {
    SetProp,
    ResetProp,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PropWrite {
    pub tool: PropTool,
    pub key: &'static str,
    pub value: String,
}

/// الأثر الواحد، بوصف خالص. والـfixture يقارن هذه القيم بالجدول المرجعي.
/// `Shell` و`Path` يُنشَآن في دالّات التخطيط فقط (لا في مسار التنفيذ)، ولذلك
/// تُوسم `dead_code` في نسخة الجهاز — وهي **سطح القياس** لا كود ميت فعليًّا.
#[derive(Debug, Clone, PartialEq, Eq)]
#[allow(dead_code)]
pub enum Effect {
    /// كتابة خصيصة عبر `setprop`/`resetprop`.
    Prop(PropWrite),
    /// نصّ يُسلَّم إلى `/system/bin/sh -c`.
    Shell(String),
    /// مسار sysfs يُكتب مباشرة (بلا صدفة).
    Path(String),
    /// مسار يُستطلَع فقط ليُبنى عليه قرار (بلا كتابة).
    Probe(String),
    /// نصّ يُطبع على `stdout` ثم رمز الخروج.
    Stdout(&'static str, i32),
}

/// الأمر المُفكَّك من argv — لا تنفيذ هنا إطلاقًا.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Command {
    SetsGov(String),
    SetsIo(String),
    SetsMaliGov(String),
    SetThermalCore(String),
    CheckMaliPath,
    FsTrim,
    EnableDnd,
    DisableDnd,
    SetRefreshRates(String),
    RestartService,
    SetRender(String),
    /// طريق الاحتياط: `argv[1]` يُنفَّذ كبرنامج بـ`argv[2..]` (كما كان حرفيًّا).
    External { program: String, args: Vec<String> },
    /// أمر معروف ناقص الوسيط ⇒ لا شيء يُنفَّذ (وخروج ٠) — لا خطأ ولا تخمين.
    MissingArgument,
    /// لا argv إطلاقًا.
    Empty,
}

/// يحلّل argv (متضمّنًا اسم البرنامج في الفهرس ٠) إلى `Command` بلا أثر جانبي.
pub fn parse(argv: &[String]) -> Command {
    let Some(function) = argv.get(1).map(String::as_str) else {
        return Command::Empty;
    };
    let arg = argv.get(2);
    let with_arg = |c: fn(String) -> Command| match arg {
        Some(a) => c(a.clone()),
        None => Command::MissingArgument,
    };
    match function {
        "setsgov" => with_arg(Command::SetsGov),
        "setsIO" => with_arg(Command::SetsIo),
        "setsMaliGov" => with_arg(Command::SetsMaliGov),
        "setthermalcore" => with_arg(Command::SetThermalCore),
        "checkmalipath" => Command::CheckMaliPath,
        "FSTrim" => Command::FsTrim,
        "enableDND" => Command::EnableDnd,
        "disableDND" => Command::DisableDnd,
        "setrefreshrates" => with_arg(Command::SetRefreshRates),
        "restartservice" => Command::RestartService,
        "setrender" => with_arg(Command::SetRender),
        other => Command::External {
            program: other.to_string(),
            args: argv.get(2..).unwrap_or(&[]).to_vec(),
        },
    }
}

// ── مساعدات بنيوية (تُستعمل في التنفيذ وفي الـfixture) ──

pub fn io_scheduler_path(block: &str) -> String {
    format!("/sys/block/{}/queue/scheduler", block)
}

/// قرار فرع `setthermalcore`: `"1"` يشغّل، وأي شيء آخر (**بما فيه `"0"`**) يوقف.
pub fn thermalcore_starts(state: &str) -> bool {
    state == "1"
}

/// `am broadcast` لطلب معدّل تحديث — وكل ما لا يُفكَّك كعدد صحيح يهبط إلى ٦٠ هرتز.
pub fn refresh_rate_command(rate: &str) -> String {
    let target_fps = rate.parse::<i32>().unwrap_or(60);
    format!(
        "am broadcast -a nd.max.SET_FPS -n nd.max/.RefreshRateReceiver --ei fps {}",
        target_fps
    )
}

/// محاولتا `FSTrim` بالترتيب: الثانية فقط إن فشلت الأولى (غير صفر).
pub fn fstrim_attempts() -> [&'static str; 2] {
    [FSTRIM_VDC, FSTRIM_SM]
}

pub fn dnd_command(enable: bool) -> &'static str {
    if enable { DND_ENABLE } else { DND_DISABLE }
}

/// عقد `checkmalipath`: نصّ على `stdout` **و**رمز خروج — والتطبيق يقرأ النصّ
/// (`TweakViewmodel.kt`: `== "true"`) والقارئ C يعتمد رمز الخروج.
pub fn mali_path_result(found: bool) -> (&'static str, i32) {
    if found { ("true", 0) } else { ("false", 1) }
}

// ── أثره على الخصائص ──

fn sp(out: &mut Vec<Effect>, key: &'static str, value: &str) {
    out.push(Effect::Prop(PropWrite {
        tool: PropTool::SetProp,
        key,
        value: value.to_string(),
    }));
}

fn rp(out: &mut Vec<Effect>, key: &'static str, value: &str) {
    out.push(Effect::Prop(PropWrite {
        tool: PropTool::ResetProp,
        key,
        value: value.to_string(),
    }));
}

/// أثر `setrender` على الخصائص بالترتيب الحرفي. وفيه فرقان مقيسان مقصودان:
/// (١) `default`/الفارغ يمسح أربعًا و**يحذف** `ro.hwui.use_vulkan`؛
/// (٢) فرع `software` **لا يمسّ** `ro.hwui.use_vulkan` إطلاقًا (بخلاف بقية الفروع).
pub fn setrender_effects(renderer: &str) -> Vec<Effect> {
    let mut out = Vec::new();

    if renderer == "default" || renderer.is_empty() {
        sp(&mut out, "debug.hwui.renderer", "");
        sp(&mut out, "debug.renderengine.backend", "");
        sp(&mut out, "debug.hwui.render_thread", "");
        sp(&mut out, "debug.skia.threaded_mode", "");
        rp(&mut out, "ro.hwui.use_vulkan", "");
        return out;
    }

    sp(&mut out, "debug.hwui.renderer", renderer);

    if renderer.contains("threaded") {
        sp(&mut out, "debug.hwui.render_thread", "true");
        if renderer.contains("skia") {
            sp(&mut out, "debug.skia.threaded_mode", "true");
        } else {
            sp(&mut out, "debug.skia.threaded_mode", "false");
        }
    } else {
        sp(&mut out, "debug.hwui.render_thread", "false");
        sp(&mut out, "debug.skia.threaded_mode", "false");
    }

    match renderer {
        "skiavk" | "skiavkthreaded" | "vulkan" => {
            sp(&mut out, "debug.renderengine.backend", "vulkan");
            rp(&mut out, "ro.hwui.use_vulkan", "true");
        }
        "skiagl" | "skiaglthreaded" | "gles" | "opengl" | "openglthreaded" => {
            sp(&mut out, "debug.renderengine.backend", "gles");
            rp(&mut out, "ro.hwui.use_vulkan", "false");
        }
        "software" => {
            sp(&mut out, "debug.renderengine.backend", "");
        }
        _ => {
            if renderer.contains("vk") || renderer.contains("vulkan") {
                sp(&mut out, "debug.renderengine.backend", "vulkan");
                rp(&mut out, "ro.hwui.use_vulkan", "true");
            } else if renderer.contains("gl") || renderer.contains("gles") {
                sp(&mut out, "debug.renderengine.backend", "gles");
                rp(&mut out, "ro.hwui.use_vulkan", "false");
            } else {
                sp(&mut out, "debug.renderengine.backend", "");
            }
        }
    }

    out
}

#[cfg(test)]
pub fn setthermalcore_effects(state: &str) -> Vec<Effect> {
    if thermalcore_starts(state) {
        vec![
            Effect::Shell(THERMALCORE_SPAWN.to_string()),
            Effect::Shell(THERMALCORE_PROBE.to_string()),
        ]
    } else {
        vec![Effect::Shell(THERMALCORE_KILL.to_string())]
    }
}

#[cfg(test)]
pub fn restartservice_effects() -> Vec<Effect> {
    vec![
        Effect::Shell(THERMALCORE_KILL.to_string()),
        Effect::Shell(SERVICE_KILL.to_string()),
        Effect::Shell(APPMONITORING_KILL.to_string()),
        Effect::Prop(PropWrite {
            tool: PropTool::SetProp,
            key: crate::utils::PROP_STATE,
            value: "stopped".to_string(),
        }),
        Effect::Shell(RESTART_SCRIPT.to_string()),
    ]
}

/// الأثر الكامل لأمر مُفكَّك — وهذا هو ما يقارنه الجدول المرجعي.
#[cfg(test)]
pub fn effects(command: &Command) -> Vec<Effect> {
    match command {
        Command::SetsGov(_) => vec![Effect::Path(CPU_GOVERNOR_GLOB.to_string())],
        Command::SetsIo(_) => IO_BLOCK_DEVICES
            .iter()
            .map(|b| Effect::Path(io_scheduler_path(b)))
            .collect(),
        Command::SetsMaliGov(_) => vec![Effect::Path(MALI_GOVERNOR_GLOB.to_string())],
        Command::SetThermalCore(s) => setthermalcore_effects(s),
        Command::CheckMaliPath => vec![Effect::Probe(MALI_DIR_GLOB.to_string())],
        Command::FsTrim => fstrim_attempts().iter().map(|c| Effect::Shell(c.to_string())).collect(),
        Command::EnableDnd => vec![Effect::Shell(dnd_command(true).to_string())],
        Command::DisableDnd => vec![Effect::Shell(dnd_command(false).to_string())],
        Command::SetRefreshRates(r) => vec![Effect::Shell(refresh_rate_command(r))],
        Command::RestartService => restartservice_effects(),
        Command::SetRender(r) => setrender_effects(r),
        Command::External { .. } | Command::MissingArgument | Command::Empty => vec![],
    }
}

/// ما يُنفَّذ فعليًّا لأثر خصيصة: البرنامج ثم وسائطه — خالصة، فتُقاس بلا جهاز وبلا
/// صدفة على `PATH`. وفيه الفرق المقيس الذي يسهل إسقاطه في إعادة كتابة: `setprop`
/// بقيمة فارغة **يُبقيها فارغة**، أما `resetprop` بقيمة فارغة **فيحذف الخصيصة**
/// (`--delete`) — وعليه يعتمد `setrender default` في مسحه للأربعة.
pub fn prop_invocation(tool: PropTool, key: &str, value: &str) -> (&'static str, Vec<String>) {
    match tool {
        PropTool::SetProp => ("setprop", vec![key.to_string(), value.to_string()]),
        PropTool::ResetProp if value.is_empty() => {
            ("resetprop", vec!["--delete".to_string(), key.to_string()])
        }
        PropTool::ResetProp => ("resetprop", vec![key.to_string(), value.to_string()]),
    }
}

/// تطبيق أثر خصيصة واحدة عبر الأداتين الحقيقيتين.
pub fn apply_prop(write: &PropWrite) {
    match write.tool {
        PropTool::SetProp => crate::utils::setprop(write.key, &write.value),
        PropTool::ResetProp => crate::utils::resetprop(write.key, &write.value),
    }
}

/// تنفيذ برنامج خارجي (طريق الاحتياط) — نفس ما كان `main.rs` يفعله حرفيًّا.
pub fn run_external(program: &str, args: &[String]) {
    let _ = ProcessCommand::new(program).args(args).status();
}

#[cfg(test)]
mod tests {
    use super::*;

    /// الجدول المرجعي مُضمَّن **وقت الترجمة** (`include_str!`) لا يُقرأ وقت التشغيل:
    /// فمُصرّف Rust يتعقّب الملف كمُدخَل للمهمة، وتعديله **يُبطل** النتيجة المخزّنة.
    /// وهذا مقصود — وهو بعينه العطب الذي أُعلن في `fixtures/contracts/README.md` عن
    /// حرّاس Gradle التي تقرأ ملفات المستودع ولا تتعقّبها فتطبع نجاحًا كاذبًا.
    const TABLE: &str = include_str!("../../../fixtures/contracts/binutils_cli.tsv");
    /// جدول سطح الخصائص (§٤) — يُضمَّن وقت الترجمة أيضًا، فيُصبح تعديله مُدخَلًا متعقَّبًا.
    const PROPS_TABLE: &str = include_str!("../../../fixtures/contracts/system_properties.tsv");

    // أعداد مُقاسة من الجدول نفسه، وتُفرض **بالضبط** لا كحدّ أدنى: جدول ينقص سطرًا
    // بصمت هو جدول يتوقّف عن القياس، والمساواة الدقيقة تُسقط ذلك. (٦٧ أثرًا في
    // ٢٣ مجموعة، منها ٤٤ كتابة خصيصة.)
    const EXPECTED_GROUPS: usize = 23;
    const EXPECTED_EFFECTS: usize = 67;
    const EXPECTED_PROP_WRITES: usize = 44;

    /// صفّ في الجدول: `label · subcommand · arg · kind · value`
    /// و`-` في `arg` = لا وسيط · و`""` = وسيط فارغ بحرفيته.
    struct Row {
        label: String,
        subcommand: String,
        arg: Option<String>,
        kind: String,
        value: String,
        line: usize,
    }

    fn parse_table() -> Vec<Row> {
        let mut rows = Vec::new();
        for (i, raw) in TABLE.lines().enumerate() {
            let line = i + 1;
            if raw.trim().is_empty() || raw.starts_with('#') {
                continue;
            }
            let cols: Vec<&str> = raw.split('\t').collect();
            assert_eq!(cols.len(), 5, "row {} has {} columns, expected 5: {raw}", line, cols.len());
            // سطر العناوين (`label · subcommand · arg · kind · value`) ليس حالة عقد.
            if cols[3] == "kind" {
                continue;
            }
            rows.push(Row {
                label: cols[0].to_string(),
                subcommand: cols[1].to_string(),
                arg: match cols[2] {
                    "-" => None,
                    "\"\"" => Some(String::new()),
                    other => Some(other.to_string()),
                },
                kind: cols[3].to_string(),
                value: cols[4].to_string(),
                line,
            });
        }
        assert!(!rows.is_empty(), "the contract table is empty — a fixture that asserts nothing");
        rows
    }

    fn expected_effect(row: &Row) -> (String, String) {
        match row.kind.as_str() {
            "setprop" | "resetprop" => {
                let (key, value) = row
                    .value
                    .split_once('=')
                    .unwrap_or_else(|| panic!("row {}: `{}` needs `key=value`", row.line, row.value));
                (row.kind.clone(), format!("{key}={value}"))
            }
            "shell" | "path" | "probe" => (row.kind.clone(), row.value.clone()),
            other => panic!("row {}: unknown kind `{other}`", row.line),
        }
    }

    /// ترميز الأثر الفعلي إلى نفس المفردات — و`panic` إن ظهر صنف غير مُرمَّز،
    /// فلا يمرّ أثر جديد بلا تمثيل في الجدول (فجوةٌ تُعلن لا تُطوى).
    fn actual_effect(effect: &Effect) -> (String, String) {
        match effect {
            Effect::Prop(w) => {
                let kind = match w.tool {
                    PropTool::SetProp => "setprop",
                    PropTool::ResetProp => "resetprop",
                };
                (kind.to_string(), format!("{}={}", w.key, w.value))
            }
            Effect::Shell(c) => ("shell".to_string(), c.clone()),
            Effect::Path(p) => ("path".to_string(), p.clone()),
            Effect::Probe(p) => ("probe".to_string(), p.clone()),
            Effect::Stdout(text, code) => ("stdout".to_string(), format!("{text} {code}")),
        }
    }

    /// الحالات المستخرجة من الجدول بترتيب الأسطر، مع تجميع أسطر العنوان نفسه
    /// في قائمة أثر **مرتّبة** (الترتيب جزء من العقد، لا تفصيل عرض).
    fn cases() -> Vec<(String, Command, Vec<(String, String)>, usize)> {
        let rows = parse_table();
        let mut out: Vec<(String, Command, Vec<(String, String)>, usize)> = Vec::new();
        for row in &rows {
            let expected = expected_effect(row);
            let argv = match &row.arg {
                Some(a) => vec!["sys.maxmanager-utilityconf".to_string(), row.subcommand.clone(), a.clone()],
                None => vec!["sys.maxmanager-utilityconf".to_string(), row.subcommand.clone()],
            };
            let command = parse(&argv);
            match out.last_mut() {
                Some((label, last_command, effects, _first_line)) if label == &row.label => {
                    assert!(last_command == &command, "row {}: label `{}` mixes different argv", row.line, row.label);
                    effects.push(expected);
                }
                _ => out.push((row.label.clone(), command, vec![expected], row.line)),
            }
        }
        out
    }

    /// **الدعوى الأساسية:** كل سطر في الجدول يقابل أثرًا حقيقيًّا من `plan::effects`.
    #[test]
    fn contract_table_matches_the_planned_effects() {
        let mut checked = 0usize;
        for (label, command, expected, first_line) in cases() {
            let actual: Vec<(String, String)> = effects(&command).iter().map(actual_effect).collect();
            assert_eq!(
                actual, expected,
                "`{label}` (row {first_line}) drifted from fixtures/contracts/binutils_cli.tsv"
            );
            checked += 1;
        }
        assert_eq!(checked, EXPECTED_GROUPS, "the table must carry exactly {EXPECTED_GROUPS} ordering groups");
    }

    /// **التكذيب:** لو كان الجدول لا يقيس شيئًا لمرّ أي تغيير. فتُقلَب قيمة كل أثر
    /// متوقَّع ويُشترَط أن تسقط المقارنة — وهذا ما يفصل «جدولًا يعمل» عن «جدول يُطبع».
    #[test]
    fn mutating_any_expected_effect_breaks_the_comparison() {
        let mut mutations = 0usize;
        for (label, command, expected, _) in cases() {
            let actual: Vec<(String, String)> = effects(&command).iter().map(actual_effect).collect();
            for i in 0..expected.len() {
                let mut mutated = expected.clone();
                let (kind, value) = &mut mutated[i];
                *kind = format!("{kind}!");
                *value = format!("{value}!");
                assert_ne!(
                    actual, mutated,
                    "mutating effect #{i} of `{label}` did not change the comparison — the check is vacuous"
                );
                mutations += 1;
            }
        }
        assert_eq!(mutations, EXPECTED_EFFECTS, "every one of the {EXPECTED_EFFECTS} effects must be falsifiable");
    }

    /// الأسماء الأحد عشر هي **السطح العام** (C وKotlin يكتبونها نصًّا):
    /// فإعادة تسمية ذراع في `parse` تُسقط هذا الاختبار لا تُمرَّر بصمت.
    #[test]
    fn every_documented_subcommand_is_recognised() {
        let names = [
            "setsgov", "setsIO", "setsMaliGov", "setthermalcore", "checkmalipath", "FSTrim",
            "enableDND", "disableDND", "setrefreshrates", "restartservice", "setrender",
        ];
        for name in names {
            let argv = vec!["sys.maxmanager-utilityconf".to_string(), name.to_string(), "x".to_string()];
            assert!(
                !matches!(parse(&argv), Command::External { .. } | Command::MissingArgument),
                "`{name}` is called by C/Kotlin but is no longer a known subcommand"
            );
        }
    }

    /// الوسيط الناقص **بلا أثر** لا بخطأ — وهو ما كان `if args.len() > 2` يفعله.
    #[test]
    fn a_recognised_subcommand_without_its_argument_is_a_no_op() {
        for name in ["setsgov", "setsIO", "setsMaliGov", "setthermalcore", "setrefreshrates", "setrender"] {
            let argv = vec!["sys.maxmanager-utilityconf".to_string(), name.to_string()];
            assert_eq!(parse(&argv), Command::MissingArgument, "`{name}` without an argument");
            assert!(effects(&parse(&argv)).is_empty(), "`{name}` must have no effect without an argument");
        }
    }

    /// طريق الاحتياط باقٍ (لا كسر)، ولا argv ⇒ لا شيء.
    #[test]
    fn unknown_program_falls_through_and_empty_argv_is_inert() {
        let argv = vec!["sys.maxmanager-utilityconf".to_string(), "am".to_string(), "broadcast".to_string()];
        assert_eq!(
            parse(&argv),
            Command::External { program: "am".to_string(), args: vec!["broadcast".to_string()] }
        );
        assert_eq!(parse(&["sys.maxmanager-utilityconf".to_string()]), Command::Empty);
        assert_eq!(parse(&[]), Command::Empty);
    }

    /// عقد `checkmalipath`: النصّ **ورمز الخروج** معًا — التطبيق يقرأ النصّ
    /// (`== "true"`) والقارئ C يعتمد رمز الخروج، فخلط أحدهما يُسقط الاختبار.
    #[test]
    fn mali_path_reports_text_and_exit_code_together() {
        assert_eq!(mali_path_result(true), ("true", 0));
        assert_eq!(mali_path_result(false), ("false", 1));
        assert_eq!(
            effects(&Command::CheckMaliPath),
            vec![Effect::Probe(MALI_DIR_GLOB.to_string())],
            "`checkmalipath` decides from exactly one probed path"
        );
    }

    /// **الثغرة التي وجدتُها بعد التسليم الأول:** ملفات الخصائص الثلاثة في هذه الحزمة
    /// (`PROP_DEBUG_MODE` · `PROP_STATE` · `PROP_CONF_FSTRIM`) كانت **خارج** حارس سطح
    /// الخصائص (`SystemPropertiesContractTest` يقرأ `binprofiles/src/props.rs` وحده).
    /// ومفتاح مكتوب خطأً هنا = قراءة أو كتابة إلى خصيصة لا وجود لها ⇒ "غطاء صامت":
    /// `fstrim` لا ينطلق أبدًا، أو حالة الخادم لا تُقرأ. فيُقيَّد الآن بجدول السطح نفسه.
    #[test]
    fn the_three_property_keys_are_declared_in_the_surface_table() {
        let declared: Vec<&str> = PROPS_TABLE
            .lines()
            .filter(|l| !l.starts_with('#') && !l.trim().is_empty() && l.contains('\t'))
            .map(|l| l.split('\t').next().unwrap_or(""))
            .collect();
        assert!(declared.len() > 80, "the surface table looks truncated: {} rows", declared.len());
        for key in [crate::utils::PROP_DEBUG_MODE, crate::utils::PROP_STATE, crate::utils::PROP_CONF_FSTRIM] {
            assert!(
                declared.contains(&key),
                "`{key}` is used by this binary but is not declared in fixtures/contracts/system_properties.tsv"
            );
        }
    }

    /// الأداة والوسائط التي يكتبها المنفّذ فعلًا لكل أثر خصيصة — وهذا ما يربط
    /// مفردات الجدول (`setprop`/`resetprop`) بالتنفيذ الحقيقي، ويثبّت فرق الحذف.
    #[test]
    fn every_prop_effect_uses_the_tool_the_table_names() {
        // فرق الحذف، مقيسًا وحده لأنه أسهل ما يُسقطه إعادة كتابة:
        assert_eq!(
            prop_invocation(PropTool::ResetProp, "ro.hwui.use_vulkan", ""),
            ("resetprop", vec!["--delete".to_string(), "ro.hwui.use_vulkan".to_string()])
        );
        assert_eq!(
            prop_invocation(PropTool::SetProp, "debug.hwui.renderer", ""),
            ("setprop", vec!["debug.hwui.renderer".to_string(), String::new()]),
            "setprop with an empty value must NOT become a delete"
        );

        let mut checked = 0usize;
        for (label, command, expected, _) in cases() {
            let actual = effects(&command);
            for (effect, (kind, value)) in actual.iter().zip(expected.iter()) {
                if let Effect::Prop(write) = effect {
                    let (program, args) = prop_invocation(write.tool, write.key, &write.value);
                    assert_eq!(&program, kind, "`{label}` must be applied with `{kind}`");
                    if write.tool == PropTool::ResetProp && write.value.is_empty() {
                        // القيمة الفارغة مع `resetprop` تُرمَّز في الجدول `key=`،
                        // لكن تنفيذها الحقيقي **حذف** — وهذا موضع الالتحام بينهما.
                        assert_eq!(
                            args,
                            vec!["--delete".to_string(), write.key.to_string()],
                            "`{label}` must delete `{}` rather than set it empty",
                            write.key
                        );
                    } else {
                        assert_eq!(args.join("="), *value, "`{label}` args drifted");
                    }
                    checked += 1;
                }
            }
        }
        assert_eq!(checked, EXPECTED_PROP_WRITES, "the table must carry exactly {EXPECTED_PROP_WRITES} property writes");
    }

    /// §١٢.٤ «لا انتظار بلا حدّ» — في هذه الثنائية انتظار واحد، وهو ثابت ومحدود:
    /// يُثبَّت بالرقم كي لا يُستبدل يومًا بحلقة تنتظر حدثًا بلا مهلة.
    #[test]
    fn the_only_wait_is_a_fixed_bounded_settle() {
        assert_eq!(THERMALCORE_SETTLE_SECS, 1);
        assert!(THERMALCORE_SETTLE_SECS <= 5, "a settle longer than a few seconds is a stall, not a settle");
    }

    /// فرق مقيس يخصّ الجهاز: أوضاع sysfs تُكتب ثم تُستعاد — وتبديلهما يجعل العقدة
    /// إما مفتوحة للكتابة دائمًا أو غير قابلة للكتابة أبدًا.
    #[test]
    fn sysfs_modes_are_write_then_read_only() {
        assert_eq!(SYSFS_WRITE_MODE, 0o644);
        assert_eq!(SYSFS_RESTORE_MODE, 0o444);
        assert!(IO_BLOCK_DEVICES.contains(&"mmcblk0"), "the UFS/eMMC node must stay in the io list");
        assert_eq!(io_scheduler_path("sda"), "/sys/block/sda/queue/scheduler");
    }
}
