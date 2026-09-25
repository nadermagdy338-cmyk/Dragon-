//! تحليل سطور السجلّات **دفعةً واحدة** داخل العملية — عمل النصوص خارج JVM.
//!
//! # لماذا دفعة، ولماذا Rust
//!
//! المُحلِّل في Kotlin كان يعمل **سطرًا بسطر عند الوصول** (regex على كل سطر)، وسطور logcat تصل
//! بعشرات في الثانية ⇒ آلاف مطابقات regex وعشرات آلاف الكائنات الوسيطة في الدقيقة على خيط
//! لا يتوقف. ونقل **سطر واحد** إلى Rust كان سيزيد الأمر سوءًا (عبور JNI لكل سطر)، فالتصميم هنا
//! **دفعي**: السطور تُجمَّع في نافذة الواجهة (٣٠٠ مللي) ثم تُحلَّل كلها في **نداء واحد**.
//!
//! # الصدق قبل السرعة
//!
//! ولمّا كان المُحلِّل الأصلي يعمل على أجهزة حقيقية وهو المرجع، فلا يُعاد تفسير الصيغة بالاجتهاد:
//! التطابق هنا **مُحاكاة حرفية** لنمطَي Kotlin:
//!
//! * logcat: `^(\d{2}-\d{2})\s+(\d{2}:\d{2}:\d{2}\.\d{3})\s+(\d+)\s+(\d+)\s+([A-Z])\s+(.*?):\s?(.*)$`
//! * السجلّ الموقَّع: `^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})\s+([DIWEF])\s+(\S+?):\s?(.*)$`
//!
//! فحيث يقول `\s` فهو مجموعة ASCII عينها التي تستعملها Java (`\s` الافتراضي غير يونيكوديّ):
//! `[ \t\n\x0B\f\r]`، و`\d` = `[0-9]`، و`. ` = أي محرف عدا السطر الجديد، و`(.*?):` تتوقف عند
//! **أول** نقطتين نقطتين.
//!
//! و`isBlank()` وحدها استثناء، وهي **يونيكودية أكثـر مما يُظن**: Kotlin تكتبها
//! `all { it.isWhitespace() }` و`Char.isWhitespace()` عنده `Character.isWhitespace(c) || Character.isSpaceChar(c)` —
//! فالاتّحاد يشمل **كل** فواصل المسافة اليونيكودية **بما فيها غير الفاصلة** (U+00A0 · U+2007 · U+202F)،
//! ويستثني U+0085 (NEL: أبيض في Unicode، وليس فراغًا في Java). وهذا لم يُستنتج بالاجتهاد:
//! **اختبار JVM على الجانبين أمسك الانحراف** قبل أن يصل إلى جهاز — فصارت `kotlin_blank` هي المرآة الصحيحة.
//!
//! # الحزمة (مشتركة مع Kotlin حرفيًّا)
//!
//! سطر لكل سطر مُدخل: `S` = تُسقَط · `F` = احتياط (يُبنى من النصّ الخام) · `P` + حقول مفصولة
//! بـ`\u{1}` بالترتيب: logcat = (تاريخ · وقت · pid · tid · مستوى · وسم · نصّ)، والسجلّ الموقَّع =
//! (ختم · مستوى · وسم · نصّ). والفواصل داخل الحقول تُزحزح: `\u{2}`⇒`\u{3}` ثم `\u{1}`⇒`\u{2}`
//! (وKotlin تعكسها بالترتيب المعاكس)، فلا يكسر محرف تحكّم نادر عدّ الحقول.

/// فاصل الحقول داخل السطر الواحد.
pub const FIELD: char = '\u{1}';
const ESCAPED_FIELD: char = '\u{2}';
const ESCAPED_ESCAPE: char = '\u{3}';

/// محرف مسافة عند Java (يعادل `\s` الافتراضي في تعبيراتها النمطية).
fn java_space(c: char) -> bool {
    matches!(c, ' ' | '\t' | '\n' | '\u{0B}' | '\u{0C}' | '\r')
}

/// مسافة عند **Kotlin** (`Character.isWhitespace || Character.isSpaceChar`).
///
/// والفرق عن مجموعة Unicode (White_Space) في حدّين لا ثالث لهما:
///
/// * `\u{1C}`..`\u{1F}` فراغ عند Java **وليست** في White_Space.
/// * `\u{0085}` (NEL) أبيض في White_Space و**ليس** فراغًا عند Java.
///
/// وكل ما عداهما متساويان: White_Space تحوي كل فواصل المسافة (Zs/Zl/Zp) **بما فيها** NBSP
/// و`\u{2007}` و`\u{202F}` — وهي أيضًا `isSpaceChar` ⇒ فراغ عند Kotlin.
fn kotlin_whitespace(c: char) -> bool {
    match c {
        '\u{1C}' | '\u{1D}' | '\u{1E}' | '\u{1F}' => true,
        '\u{0085}' => false,
        _ => c.is_whitespace(),
    }
}

/// `String.isBlank()` في Kotlin = كل المحارف مسافة عند Kotlin.
pub fn kotlin_blank(line: &str) -> bool {
    line.chars().all(kotlin_whitespace)
}

fn escape_field(value: &str) -> String {
    let mut out = String::with_capacity(value.len());
    for ch in value.chars() {
        match ch {
            ESCAPED_FIELD => out.push(ESCAPED_ESCAPE),
            FIELD => out.push(ESCAPED_FIELD),
            _ => out.push(ch),
        }
    }
    out
}

fn digits_at(bytes: &[u8], start: usize, count: usize) -> Option<usize> {
    let end = start + count;
    if end > bytes.len() || !bytes[start..end].iter().all(u8::is_ascii_digit) {
        return None;
    }
    Some(end)
}

/// يقفز فوق محارف `\s` (Java) ويعيد الموضع بعدها، أو `None` إن لم يوجد واحد.
fn skip_spaces(bytes: &[u8], mut at: usize) -> Option<usize> {
    let start = at;
    while at < bytes.len() && java_space(bytes[at] as char) {
        at += 1;
    }
    if at == start {
        None
    } else {
        Some(at)
    }
}

/// حقول سطر logcat بعد التطابق: (تاريخ، وقت، pid، tid، مستوى، وسم، نصّ).
pub type LogcatFields<'a> = (&'a str, &'a str, &'a str, &'a str, &'a str, &'a str, &'a str);

/// مطابقة `LOG_PATTERN` حرفيًّا: `MM-DD HH:MM:SS.mmm PID TID L TAG: message`.
pub fn parse_logcat(line: &str) -> Option<LogcatFields<'_>> {
    let bytes = line.as_bytes();
    let date_start = 0usize;
    let after_month = digits_at(bytes, date_start, 2)?;
    if bytes.get(after_month) != Some(&b'-') {
        return None;
    }
    let date_end = digits_at(bytes, after_month + 1, 2)?;
    let time_start = skip_spaces(bytes, date_end)?;

    let hh = digits_at(bytes, time_start, 2)?;
    if bytes.get(hh) != Some(&b':') {
        return None;
    }
    let mm = digits_at(bytes, hh + 1, 2)?;
    if bytes.get(mm) != Some(&b':') {
        return None;
    }
    let ss = digits_at(bytes, mm + 1, 2)?;
    if bytes.get(ss) != Some(&b'.') {
        return None;
    }
    let time_end = digits_at(bytes, ss + 1, 3)?;

    let pid_start = skip_spaces(bytes, time_end)?;
    let mut pid_end = digits_at(bytes, pid_start, 1)?;
    while pid_end < bytes.len() && bytes[pid_end].is_ascii_digit() {
        pid_end += 1;
    }

    let tid_start = skip_spaces(bytes, pid_end)?;
    let mut tid_end = digits_at(bytes, tid_start, 1)?;
    while tid_end < bytes.len() && bytes[tid_end].is_ascii_digit() {
        tid_end += 1;
    }

    // `([A-Z])` — حرف واحد كبير.
    let level_start = skip_spaces(bytes, tid_end)?;
    if !bytes.get(level_start).is_some_and(u8::is_ascii_uppercase) {
        return None;
    }
    let level_end = level_start + 1;
    let tag_start = skip_spaces(bytes, level_end)?;

    // `(.*?):\s?(.*)$` — أول نقطتين نقطتين تفصل الوسم عن النصّ.
    let colon = line[tag_start..].find(':').map(|offset| tag_start + offset)?;
    let tag = &line[tag_start..colon];
    let mut message_at = colon + 1;
    if let Some(ch) = line[message_at..].chars().next() {
        if java_space(ch) {
            message_at += ch.len_utf8();
        }
    }
    Some((
        &line[date_start..date_end],
        &line[time_start..time_end],
        &line[pid_start..pid_end],
        &line[tid_start..tid_end],
        &line[level_start..level_end],
        tag,
        &line[message_at..],
    ))
}

/// حقول السجلّ الموقَّع: (ختم زمني، مستوى، وسم، نصّ).
pub type UnifiedFields<'a> = (&'a str, &'a str, &'a str, &'a str);

/// مطابقة `UNIFIED_LOG_PATTERN`: `YYYY-MM-DD HH:MM:SS L TAG: message`.
pub fn parse_unified(line: &str) -> Option<UnifiedFields<'_>> {
    let bytes = line.as_bytes();
    let after_year = digits_at(bytes, 0, 4)?;
    if bytes.get(after_year) != Some(&b'-') {
        return None;
    }
    let after_month = digits_at(bytes, after_year + 1, 2)?;
    if bytes.get(after_month) != Some(&b'-') {
        return None;
    }
    let after_day = digits_at(bytes, after_month + 1, 2)?;
    if bytes.get(after_day) != Some(&b' ') {
        return None;
    }
    let hh = digits_at(bytes, after_day + 1, 2)?;
    if bytes.get(hh) != Some(&b':') {
        return None;
    }
    let mm = digits_at(bytes, hh + 1, 2)?;
    if bytes.get(mm) != Some(&b':') {
        return None;
    }
    let stamp_end = digits_at(bytes, mm + 1, 2)?;

    let level_start = skip_spaces(bytes, stamp_end)?;
    let level = *bytes.get(level_start)? as char;
    if !matches!(level, 'D' | 'I' | 'W' | 'E' | 'F') {
        return None;
    }
    let level_end = level_start + 1;

    // `(\S+?):` — الوسم بلا مسافة، وينتهي عند أول نقطتين نقطتين.
    let tag_start = skip_spaces(bytes, level_end)?;
    let mut tag_end = tag_start;
    loop {
        let ch = *bytes.get(tag_end)? as char;
        if ch == ':' {
            break;
        }
        if java_space(ch) {
            return None;
        }
        tag_end += 1;
    }
    if tag_end == tag_start {
        return None;
    }
    let mut message_at = tag_end + 1;
    if let Some(ch) = line[message_at..].chars().next() {
        if java_space(ch) {
            message_at += ch.len_utf8();
        }
    }
    Some((
        &line[..stamp_end],
        &line[level_start..level_end],
        &line[tag_start..tag_end],
        &line[message_at..],
    ))
}

/// نتيجة سطر logcat كما تستهلكها Kotlin.
pub enum LogcatOutcome<'a> {
    Skip,
    Fallback,
    Parsed(LogcatFields<'a>),
}

/// منطق `parseLine` كاملًا — بما فيه فرع الاحتياط وشروطه.
pub fn logcat_outcome(line: &str) -> LogcatOutcome<'_> {
    match parse_logcat(line) {
        Some(fields) => LogcatOutcome::Parsed(fields),
        None => {
            if kotlin_blank(line) || line.starts_with("---------") {
                LogcatOutcome::Skip
            } else {
                LogcatOutcome::Fallback
            }
        }
    }
}

/// تحليل دفعة كاملة: سطر نتيجة لكل سطر مُدخل، بنفس الترتيب — فلا تنزاح المحاذاة أبدًا.
pub fn parse_batch_packed(packed_lines: &str, unified: bool) -> String {
    let lines: Vec<&str> = packed_lines.split('\n').collect();
    let mut rows: Vec<String> = Vec::with_capacity(lines.len());
    for line in lines {
        let row = if unified {
            match parse_unified(line) {
                Some((stamp, level, tag, message)) => format!(
                    "P{FIELD}{}{FIELD}{}{FIELD}{}{FIELD}{}",
                    escape_field(stamp),
                    escape_field(level),
                    escape_field(tag),
                    escape_field(message)
                ),
                None => "S".to_string(),
            }
        } else {
            match logcat_outcome(line) {
                LogcatOutcome::Skip => "S".to_string(),
                LogcatOutcome::Fallback => "F".to_string(),
                LogcatOutcome::Parsed((date, time, pid, tid, level, tag, message)) => format!(
                    "P{FIELD}{}{FIELD}{}{FIELD}{}{FIELD}{}{FIELD}{}{FIELD}{}{FIELD}{}",
                    escape_field(date),
                    escape_field(time),
                    escape_field(pid),
                    escape_field(tid),
                    escape_field(level),
                    escape_field(tag),
                    escape_field(message)
                ),
            }
        };
        rows.push(row);
    }
    rows.join("\n")
}

#[cfg(test)]
mod tests {
    use super::*;

    fn fields(line: &str) -> Option<Vec<&str>> {
        parse_logcat(line).map(|(d, t, p, tid, l, tag, m)| vec![d, t, p, tid, l, tag, m])
    }

    #[test]
    fn logcat_line_yields_the_same_fields_as_the_kotlin_pattern() {
        let got = fields("09-24 18:12:03.456  1234  5678 I MaxManager: EVENT=BOOST sw=1").unwrap();
        assert_eq!(
            got,
            vec!["09-24", "18:12:03.456", "1234", "5678", "I", "MaxManager", "EVENT=BOOST sw=1"]
        );
    }

    #[test]
    fn tabs_and_repeated_spaces_separate_fields_like_java_s() {
        let got = fields("09-24\t18:12:03.456\t1234\t5678\tW\tPower: message").unwrap();
        assert_eq!(got, vec!["09-24", "18:12:03.456", "1234", "5678", "W", "Power", "message"]);
        // وبلا نقطتين نقطتين لا مطابقة أصلًا — وهو ما يطلبه نمط Kotlin أيضًا.
        assert!(parse_logcat("09-24\t18:12:03.456\t1234\t5678\tW\tPower message").is_none());
    }

    #[test]
    fn the_tag_ends_at_the_first_colon_and_message_keeps_the_rest() {
        let got = fields("09-24 18:12:03.456 1 2 E My:Tag: inner: text").unwrap();
        assert_eq!(got[5], "My");
        assert_eq!(got[6], "Tag: inner: text");
    }

    #[test]
    fn empty_tag_is_a_match_and_message_starts_after_the_colon() {
        let got = fields("09-24 18:12:03.456 1 2 I :hello").unwrap();
        assert_eq!(got[5], "");
        assert_eq!(got[6], "hello");
    }

    #[test]
    fn a_missing_colon_or_bad_shape_is_not_a_match() {
        assert!(parse_logcat("09-24 18:12:03.456 1 2 I no colon here").is_none());
        assert!(parse_logcat("09-24 18:12:03.456 1 2 i lower: x").is_none(), "المستوى حرف كبير");
        assert!(parse_logcat("9-24 18:12:03.456 1 2 I tag: x").is_none(), "تاريخ من رقمين");
        assert!(parse_logcat("09-24 18:12:03.45 1 2 I tag: x").is_none(), "مللي من ثلاثة");
        assert!(parse_logcat("09-24 18:12:03.456 1 I tag: x").is_none(), "tid مفقود");
    }

    #[test]
    fn fallback_and_skip_follow_the_kotlin_branches() {
        assert!(matches!(logcat_outcome("some plain text"), LogcatOutcome::Fallback));
        assert!(matches!(logcat_outcome(""), LogcatOutcome::Skip));
        assert!(matches!(logcat_outcome("   \t  "), LogcatOutcome::Skip));
        assert!(matches!(logcat_outcome("--------- begin ---------"), LogcatOutcome::Skip));
    }

    #[test]
    fn blank_mirrors_kotlin_not_java_iswhitespace_alone() {
        // Kotlin's isBlank = Character.isWhitespace(c) || Character.isSpaceChar(c)
        // ⇒ فواصل المسافة اليونيكودية (بما فيها NBSP) فراغ، وU+0085 ليس فراغًا.
        assert!(kotlin_blank("\u{00A0}"), "NBSP فراغ عند Kotlin");
        assert!(matches!(logcat_outcome("\u{00A0}"), LogcatOutcome::Skip));
        assert!(kotlin_blank("\u{202F}") && kotlin_blank("\u{2007}"));
        assert!(kotlin_blank("\u{2028}"), "فاصل السطر اليونيكودي فراغ أيضًا");
        assert!(kotlin_blank(" \t\r"));
        assert!(kotlin_blank("\u{1C}\u{1F}"), "محارف التحكّم التي يعدّها Java فراغًا");
        assert!(!kotlin_blank("\u{0085}"), "NEL أبيض في Unicode وليس فراغًا في Java");
        assert!(matches!(logcat_outcome("\u{0085}"), LogcatOutcome::Fallback));
    }

    #[test]
    fn unified_lines_match_the_signed_log_format() {
        let (stamp, level, tag, message) =
            parse_unified("2026-08-27 10:15:32 I MaxManager: EVENT=PROFILE key=v").unwrap();
        assert_eq!(stamp, "2026-08-27 10:15:32");
        assert_eq!(level, "I");
        assert_eq!(tag, "MaxManager");
        assert_eq!(message, "EVENT=PROFILE key=v");

        // الوسم لا يحتمل مسافة عند `\S+?`، والمستوى محصور في DIWEF، والختم كامل.
        assert!(parse_unified("2026-08-27 10:15:32 X Tag: m").is_none());
        assert!(parse_unified("2026-08-27 10:15:32 I Ta g: m").is_none());
        assert!(parse_unified("2026-8-27 10:15:32 I Tag: m").is_none());
        assert!(parse_unified("no timestamp here").is_none());
    }

    #[test]
    fn a_batch_keeps_one_row_per_line_in_order() {
        let batch = "09-24 18:12:03.456 1 2 I Tag: first\nplain text\n\n2026-08-27 10:15:32 I MaxManager: second";
        let logcat = parse_batch_packed(batch, false);
        let rows: Vec<&str> = logcat.split('\n').collect();
        assert_eq!(rows.len(), 4);
        assert!(rows[0].starts_with("P\u{1}09-24"));
        assert_eq!(rows[1], "F");
        // سطر فارغ: Kotlin تقول `isBlank ⇒ null` ⇒ يُسقط — لا يصير احتياطًا.
        assert_eq!(rows[2], "S");

        let unified = parse_batch_packed(batch, true);
        let unified_rows: Vec<&str> = unified.split('\n').collect();
        assert_eq!(unified_rows.len(), 4);
        assert_eq!(unified_rows[0], "S");
        assert_eq!(unified_rows[3], "P\u{1}2026-08-27 10:15:32\u{1}I\u{1}MaxManager\u{1}second");
    }

    #[test]
    fn skipped_lines_are_skipped_in_logcat_mode_too() {
        let packed = parse_batch_packed("   \n---------\nx", false);
        let rows: Vec<&str> = packed.split('\n').collect();
        assert_eq!(rows, vec!["S", "S", "F"]);
    }

    #[test]
    fn field_separators_inside_a_message_are_shifted_and_never_split_a_row() {
        let odd = "09-24 18:12:03.456 1 2 I Tag: has\u{1}sep and\u{2}escape";
        let row = parse_batch_packed(odd, false);
        assert_eq!(row.split('\n').count(), 1, "الفاصل لا يصنع سطرًا ثانيًا");
        assert_eq!(row.matches('\u{1}').count(), 7, "سبعة حقول فقط: ٦ فواصل للبيانات");
        let last = row.rsplit('\u{1}').next().unwrap();
        assert_eq!(last, "has\u{2}sep and\u{3}escape");
    }

    #[test]
    fn empty_batch_is_empty_output() {
        assert_eq!(parse_batch_packed("", false), "S");
        assert_eq!(parse_batch_packed("", true), "S");
    }
}
