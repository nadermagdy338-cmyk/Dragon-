/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */

//! الجدول الخالص لعقد CLI الثنائية `sys.maxmanager-profilesettings` — التفكيك، وبوّابة الملكية،
//! وقاعدة الاحتياط — **بلا أي إدخال/إخراج** إلا موضع واحد مُعلَن (`should_run_external`).
//!
//! **لماذا وُجدت:** العقد كان **مكتوبًا ولا يُقاس**: `ExecOwnershipContractTest` (Kotlin) يقرأ
//! المصدر ويتأكّد أنّ **الشرطين موجودان كنصّ** — ولا يقيس **ما يقرّره الشرطان**. والأسماء الستّة
//! (والمرادفات) يكتبها C كأرقام (`sys.maxmanager-profilesettings %d`) وبالأسماء
//! (`applyfreqbalance`)، وأيّ خطأ بينهما = أمر لا يصل ⇒ **ملف لا يُطبَّق** بلا خطأ ظاهر.
//! فصار القرار دالّة تُنادى وتُقاس على المضيف (`cargo test`، بلا جهاز)، والعقد المرجعي في
//! `fixtures/contracts/profilesettings_cli.tsv`.
//!
//! **ولا يُفهَم من هذا أن الملكية صارت آمنة:** الشرط الثاني `contains("sys.maxmanager")`
//! **فضفاض عن قصد** (أيّ سطر أوامر يحوي البادئة يمرّ) — وهو سلوك مقيس ومُثبَّت في الجدول
//! بصفّ صريح، لا مُصلَّح هنا (ADR-18)، ويبقى ضمن الخطر المُعلَن في §١٦ («`uid 0` واسع»).

use std::path::Path;

/// الأمر المُفكَّك من `argv[1]`.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Command {
    Initialize,
    Performance,
    Balanced,
    Eco,
    ApplyFreqBalance,
    ApplyFreqGame,
}

/// نتيجة التصنيف الخالص — بلا قرار تشغيل.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Classification {
    /// أمر معروف.
    Known(Command),
    /// وسيط غير معروف: يُشغَّل كبرنامج **إن** استوفى `should_run_external`.
    Candidate(String),
    /// لا `argv[1]` إطلاقًا.
    Empty,
}

/// يفكّك argv (متضمّنًا اسم البرنامج في الفهرس ٠) — **ولا يلمس القرص**.
///
/// والمرادفات مقصودة ومقيسة: الخادم يرسل **أرقامًا** (`ProfileUtility.c`:
/// `sys.maxmanager-profilesettings %d`) و**أسماءً** (`System.c`: `… applyfreqbalance`)،
/// فالاثنان عقد لا رفاهية.
pub fn classify(argv: &[String]) -> Classification {
    let Some(arg) = argv.get(1) else {
        return Classification::Empty;
    };
    match arg.as_str() {
        "0" | "initialize" => Classification::Known(Command::Initialize),
        "1" | "performance_profile" => Classification::Known(Command::Performance),
        "2" | "balanced_profile" => Classification::Known(Command::Balanced),
        "3" | "eco_mode" => Classification::Known(Command::Eco),
        "applyfreqbalance" => Classification::Known(Command::ApplyFreqBalance),
        "applyfreqgame" => Classification::Known(Command::ApplyFreqGame),
        other => Classification::Candidate(other.to_string()),
    }
}

/// قاعدة الاحتياط الأصلية: يُشغَّل الوسيط كبرنامج إن **حوى نقطة** أو كان **مسارًا موجودًا**.
///
/// والشرط الأول وحده كافٍ (ينفّذ باختصار الدائرة `||`)، فوسيط بامتداد **غير موجود** يُشغَّل
/// ويُنتج فشلًا صامتًا — وهو سلوك مقيس ومُثبَّت في الجدول، لا مُصلَّح (ADR-18).
pub fn should_run_external(arg: &str) -> bool {
    arg.contains('.') || Path::new(arg).exists()
}

/// هل المستدعي هو الخادم؟ عقد الملكية في `docs/ai/ARCHITECTURE-AUDIT.md` §١٢.١.
///
/// **ولا تحذف الشرط الثاني لأنه يبدو زائدًا.** ظاهريًّا `contains("sys.maxmanager-service")`
/// يستلزم `contains("sys.maxmanager")`، فيبدو الثاني تكرارًا ميتًا — وهو ليس كذلك، لأن
/// **الأب ليس الخادم دائمًا**: `systemv()` في الخادم
/// (`archdaemon/jni/src/ShellUtility/SystemvUtility.c`) يعمل بـ
/// `execle("/system/bin/sh", "sh", "-c", command, …)`، فإن لم يعمل الصدف بتقنية
/// "تنفيذ آخر أمر في نفسه" فالعمليّة التي تُشغّل هذه الثنائية هي **`sh`**، وسطر أوامرها هو
/// `sh -c sys.maxmanager-profilesettings 2` — **لا يحوي `-service` بل يحوي `maxmanager`**.
///
/// فالشرطان يغطيان الحالتين المقصودتين معًا:
///   ١) أب = الخادم مباشرةً  ⇒  `sys.maxmanager-service`
///   ٢) أب = `sh` يلفّ الأمر ⇒  `sys.maxmanager`
/// وحذف الثاني يُسقط **كل طلبات الملف على الجهاز** برمز خروج ١. وحرّاسه: هذا التعليق،
/// ودعاوى `cargo test` التي **تقيّم الشرطين** على سطري الأوامر الحقيقيين، ودعوى
/// `nd.max.contract.ExecOwnershipContractTest`.
pub fn caller_is_trusted(cmdline: &str) -> bool {
    cmdline.contains("sys.maxmanager-service") || cmdline.contains("sys.maxmanager")
}

/// تشغيل المرشّح كبرنامج خارجي — نفس ما كان `main.rs` يفعله حرفيًّا.
pub fn run_external(program: &str, args: &[String]) {
    let _ = std::process::Command::new(program).args(args).status();
}

#[cfg(test)]
mod tests {
    use super::*;

    /// الجدول المرجعي مُضمَّن **وقت الترجمة** (`include_str!`) لا يُقرأ وقت التشغيل:
    /// فتعديله مُدخَل متعقَّب لمهمة cargo، ولا يُخزَّن نجاح كاذب.
    const TABLE: &str = include_str!("../../fixtures/contracts/profilesettings_cli.tsv");

    // أعداد مُقاسة من الجدول نفسه، وتُفرض **بالضبط** لا كحدّ أدنى (جدول ينقص صفًّا بصمت
    // هو جدول يتوقّف عن القياس).
    const EXPECTED_ROWS: usize = 20;

    struct Row {
        label: String,
        arg1: Option<String>,
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
            assert_eq!(cols.len(), 4, "row {} has {} columns, expected 4: {raw}", line, cols.len());
            if cols[2] == "kind" {
                continue; // سطر العناوين (`label · arg1 · kind · value`)
            }
            rows.push(Row {
                label: cols[0].to_string(),
                arg1: match cols[1] {
                    "-" => None,
                    "\"\"" => Some(String::new()),
                    other => Some(other.to_string()),
                },
                kind: cols[2].to_string(),
                value: cols[3].to_string(),
                line,
            });
        }
        assert!(!rows.is_empty(), "the contract table is empty — a fixture that asserts nothing");
        rows
    }

    fn expected_command(name: &str) -> Command {
        match name {
            "initialize" => Command::Initialize,
            "performance" => Command::Performance,
            "balanced" => Command::Balanced,
            "eco" => Command::Eco,
            "applyfreqbalance" => Command::ApplyFreqBalance,
            "applyfreqgame" => Command::ApplyFreqGame,
            other => panic!("unknown expected command `{other}`"),
        }
    }

    fn argv_for(row: &Row) -> Vec<String> {
        match &row.arg1 {
            Some(a) => vec!["sys.maxmanager-profilesettings".to_string(), a.clone()],
            None => vec!["sys.maxmanager-profilesettings".to_string()],
        }
    }

    /// هل يحمل الصفّ ما يدّعيه؟ **دالّة واحدة** يستدعيها الاختبار والطفرة معًا — فلا
    /// يمكن أن تكون المقارنة صحيحة في أحدهما ومعلَّقة في الآخر (وهو خطأ كلّفني سابقًا).
    fn row_holds(row: &Row) -> bool {
        match row.kind.as_str() {
            "command" => classify(&argv_for(row)) == Classification::Known(expected_command(&row.value)),
            "candidate" => matches!(classify(&argv_for(row)), Classification::Candidate(_)),
            "runs" => should_run_external(row.arg1.as_deref().unwrap()),
            "inert" => !should_run_external(row.arg1.as_deref().unwrap()),
            "trusted" => caller_is_trusted(&row.value),
            "untrusted" => !caller_is_trusted(&row.value),
            other => panic!("row {}: unknown kind `{other}`", row.line),
        }
    }

    /// **الدعوى الأساسية:** كل صفّ في الجدول يقابل قرارًا حقيقيًّا من `plan`.
    #[test]
    fn the_contract_table_matches_the_real_decisions() {
        let rows = parse_table();
        assert_eq!(rows.len(), EXPECTED_ROWS, "the table must carry exactly {EXPECTED_ROWS} rows");
        for row in &rows {
            assert!(
                row_holds(row),
                "`{}` (row {}) drifted from fixtures/contracts/profilesettings_cli.tsv",
                row.label,
                row.line
            );
        }
    }

    /// **التكذيب:** لو كان الجدول لا يقيس شيئًا لمرّ أي تغيير. فيُقلب كل صفّ **إلى ما
    /// يناقضه** ويُشترَط أن تسقط `row_holds` — وهذا ما يفصل «جدولًا يعمل» عن «جدول يُطبع».
    #[test]
    fn flipping_any_row_makes_its_check_fail() {
        let rows = parse_table();
        let mut mutated_ok = 0usize;
        for row in &rows {
            let mut flipped = Row {
                label: row.label.clone(),
                arg1: row.arg1.clone(),
                kind: row.kind.clone(),
                value: row.value.clone(),
                line: row.line,
            };
            match row.kind.as_str() {
                "command" => {
                    // اسم أمر آخر معروف ⇒ التصنيف لا يطابق
                    flipped.value = if row.value == "initialize" { "eco".into() } else { "initialize".into() };
                }
                "candidate" => {
                    // يصير أمرًا معروفًا ⇒ لم يبقَ مرشّحًا
                    flipped.arg1 = Some("initialize".into());
                }
                "runs" => {
                    // بلا نقطة وغير موجود ⇒ لا يُشغَّل
                    flipped.arg1 = Some("no-such-command-xyz".into());
                }
                "inert" => {
                    // بنقطة ⇒ يُشغَّل
                    flipped.arg1 = Some("does-not-exist.sh".into());
                }
                "trusted" => flipped.value = "/system/bin/sh -c echo hi".into(),
                "untrusted" => flipped.value = "sh -c sys.maxmanager-profilesettings 2".into(),
                other => panic!("row {}: unknown kind `{other}`", row.line),
            }
            assert!(!row_holds(&flipped), "flipping `{}` did not change the verdict — the check is vacuous", row.label);
            mutated_ok += 1;
        }
        assert_eq!(mutated_ok, EXPECTED_ROWS, "every one of the {EXPECTED_ROWS} rows must be falsifiable");
    }

    /// الشرطان يُقيَّمان فعلًا على سطري الأوامر الحقيقيين — وهذا ما لم يكن يقيسه أي حارس:
    /// Kotlin كان يتأكّد أن **النصّ موجود**، لا أن القرار **صحيح**.
    #[test]
    fn both_real_launch_shapes_are_accepted_and_nothing_else_is() {
        // (١) الأب = الخادم مباشرةً
        assert!(caller_is_trusted("/system/bin/sys.maxmanager-service --run"));
        // (٢) الأب = `sh` الذي يلفّه `systemv()` — الشرط الثاني هو الوحيد المطابق هنا
        let wrapped = "sh -c sys.maxmanager-profilesettings 2";
        assert!(caller_is_trusted(wrapped));
        assert!(
            !wrapped.contains("sys.maxmanager-service"),
            "the shell-wrapper shape must NOT match the -service clause; that is why the second clause exists"
        );
        // ورفض ما ليس الخادم
        assert!(!caller_is_trusted("/system/bin/sh -c echo hi"));
        assert!(!caller_is_trusted(""));
        assert!(!caller_is_trusted("SYS.MAXMANAGER-SERVICE"), "the check is case-sensitive");
    }

    /// الشرط الثاني **فضفاض مقيسًا** — أي سطر يحوي البادئة يمرّ. يُثبَّت كما هو (ADR-18)
    /// ليُقرأ الخطر لا ليُخفى: من يستطيع تسمية عملية تضمّ البادئة يمرّ من هذه البوّابة.
    #[test]
    fn the_second_clause_is_loose_and_that_is_declared() {
        assert!(caller_is_trusted("sys.maxmanagerAnythingElse"));
        assert!(caller_is_trusted("/data/local/tmp/sys.maxmanager-fake"));
    }

    /// الاحتياط: النقطة تكفي وحدها (فبامتداد غير موجود يُشغَّل ويُنتج فشلًا صامتًا)،
    /// وكلمة عارية لا نقطة ولا ملف ⇒ لا شيء.
    #[test]
    fn the_fallthrough_rule_is_a_dot_or_an_existing_path() {
        assert!(should_run_external("./run.sh"), "a dotted argument runs even if it does not exist");
        assert!(should_run_external("does-not-exist.sh"));
        assert!(!should_run_external("no-such-command-xyz"));
        let dir = std::env::temp_dir().join("mm-plan-external-probe");
        std::fs::create_dir_all(&dir).expect("temp dir");
        let f = dir.join("probe-no-dot");
        std::fs::write(&f, b"x").expect("temp file");
        assert!(should_run_external(f.to_str().unwrap()), "an existing path runs even without a dot");
        assert!(classify(&["p".to_string(), "banana".to_string()]) == Classification::Candidate("banana".to_string()));
        assert_eq!(classify(&["p".to_string()]), Classification::Empty);
        assert_eq!(classify(&[]), Classification::Empty);
    }
}
