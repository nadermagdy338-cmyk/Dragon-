//! قراءة عُقد sysfs/proc في **دفعة واحدة داخل العملية** — بديلُ رحلةٍ لكل عقدة.
//!
//! **ما يُقاس لا ما يُدَّعى:** كل عقدة اليوم تُقرأ إما بـ`readNode` عبر binder (معاملة كاملة)
//! وإما بصدفة `cat`. ومسح الحرارة يقرأ عقدتين لكل منطقة من عشرات المناطق، ودورة الرئيسية كل
//! ثانيتين ⇒ مئات الرحلات. الدفعة تُنزل ذلك إلى **نداء JNI واحد** يقرأ الملفات مباشرة.
//!
//! **وهذه الوحدة قراءة فقط:** لا كتابة ولا chmod ولا حذف — فلا تمسّ قاعدة ADR-11 (كل الكتابات
//! عبر المُحكِّم) ولا تفتح بابًا جانبيًّا للامتياز. وإن تعذّرت قراءة عقدة (حجبها النواة عن uid
//! التطبيق) عاد سطرها فارغًا، فيسألها Kotlin بالطريق المصرَّح (IPC الجذر ← صدفة). **لا تُخفى
//! عقدة لصالح السرعة.**
//!
//! # صيغة الحزمة — مشتركة مع Kotlin **حرفيًّا**
//!
//! * `paths`: سطر لكل مسار؛ الأسطر الفارغة تُتجاهل.
//! * `values`: سطر لكل مسار **بالترتيب**؛ السطر الفارغ = لم تُقرأ.
//! * `\u{1}` داخل قيمة = سطر جديد داخلها (عقدة متعددة الأسطر) — فلا ينكسر العدّ.
//! * `list`: اسم لكل سطر، مرتّب، بلا تكرار.
//!
//! والصيغة محروسة من الانحراف باختبارات الجانبين على **نفس المُدخلات** (انظر `same_vectors`).

use std::fs;

/// فاصل السطر-الجديد داخل القيمة — يمنع سطرًا داخليًّا من إرباك محاذاة الأسطر.
pub const NEWLINE_ESCAPE: char = '\u{1}';

/// مسارات الدفعة من النصّ المشحون: يرفض الفارغ، ويتحمّل نهايات `\r\n`.
pub fn unpack_paths(packed: &str) -> Vec<String> {
    packed
        .lines()
        .map(|line| line.trim_end_matches('\r'))
        .filter(|line| !line.trim().is_empty())
        .map(|line| line.trim().to_string())
        .collect()
}

/// تطبيع القيمة: قصّ الأطراف، وقصر السطر الجديد الداخلي إلى الفاصل المعلن.
pub fn escape_value(raw: &str) -> String {
    let trimmed = raw.trim();
    if trimmed.is_empty() {
        return String::new();
    }
    let mut out = String::with_capacity(trimmed.len());
    for ch in trimmed.chars() {
        match ch {
            '\n' => out.push(NEWLINE_ESCAPE),
            '\r' => {}
            _ => out.push(ch),
        }
    }
    out
}

/// قراءة واحدة: القيمة المطبَّعة، أو `None` إن تعذّرت القراءة أو كانت فارغة.
///
/// والمحتوى غير الصالح UTF-8 لا يُسقط القراءة (`from_utf8_lossy`) — عقد sysfs
/// تُخرج بايتات خام أحيانًا، وإسقاطها كان سيُظهر "غير مدعوم" على عقدة موجودة.
pub fn read_one(path: &str) -> Option<String> {
    let raw = fs::read(path).ok()?;
    let text = String::from_utf8_lossy(&raw);
    let escaped = escape_value(&text);
    if escaped.is_empty() {
        None
    } else {
        Some(escaped)
    }
}

/// الدفعة كاملة: **عدد الأسطر يساوي عدد المسارات دائمًا** والترتيب محفوظ
/// بمحاذاة الفهرس — وهو العقد الذي يعتمد عليه Kotlin.
pub fn read_many_packed(packed_paths: &str) -> String {
    let paths = unpack_paths(packed_paths);
    let mut out = String::new();
    for (index, path) in paths.iter().enumerate() {
        if index > 0 {
            out.push('\n');
        }
        if let Some(value) = read_one(path) {
            out.push_str(&value);
        }
    }
    out
}

/// وجود كل مدخل: `1` أو `0` في سطر لكل مسار — **سؤال الوجود غير سؤال القيمة**.
///
/// وشاشات الاكتشاف تُجرّب مرشّحين بالتتابع (`test -e` عبر صدفة لكل واحد، وبعضها يقرأ أيضًا)،
/// فالدفعة تجيب عن القائمة كلها في نداء واحد وتحفظ الدلالة حرفيًّا: `stat` (يتبع الروابط)
/// مثل `test -e` و`File.exists()` — لا `lstat`، وإلا عُدّ رابط مقطوع موجودًا.
pub fn existing_packed(packed_paths: &str) -> String {
    let paths = unpack_paths(packed_paths);
    let mut out = String::new();
    for (index, path) in paths.iter().enumerate() {
        if index > 0 {
            out.push('\n');
        }
        out.push(if fs::metadata(path).is_ok() { '1' } else { '0' });
    }
    out
}

/// أسماء مدخلات مجلد واحد — بديل `ls` (صدفة) في مسارات التصفّح.
/// `dirs_only` يقصرها على المجلدات (نظير `RootFileAccess.listDirectories`).
/// وما تعذّرت قراءته يعود نصًّا فارغًا، فيبقى للطريق الاحتياطي أن يجيب.
pub fn list_names_packed(path: &str, dirs_only: bool) -> String {
    let Ok(entries) = fs::read_dir(path) else {
        return String::new();
    };
    let mut names: Vec<String> = Vec::new();
    for entry in entries.flatten() {
        if dirs_only && !entry.file_type().map(|t| t.is_dir()).unwrap_or(false) {
            continue;
        }
        let name = entry.file_name().to_string_lossy().into_owned();
        if name.is_empty() || name == "." || name == ".." {
            continue;
        }
        names.push(name);
    }
    names.sort();
    names.dedup();
    names.join("\n")
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::path::PathBuf;
    use std::sync::atomic::{AtomicU32, Ordering};

    static COUNTER: AtomicU32 = AtomicU32::new(0);

    /// شجرة مؤقتة حقيقية — القياس على قراءة ملفات فعلية لا على محاكاة.
    struct Fixture {
        root: PathBuf,
    }

    impl Fixture {
        fn new() -> Self {
            let unique = format!(
                "mm-probe-{}-{}",
                std::process::id(),
                COUNTER.fetch_add(1, Ordering::SeqCst)
            );
            let root = std::env::temp_dir().join(unique);
            fs::create_dir_all(&root).expect("fixture root");
            Self { root }
        }

        fn write(&self, name: &str, content: &str) -> String {
            let path = self.root.join(name);
            fs::write(&path, content).expect("fixture file");
            path.to_string_lossy().into_owned()
        }

        fn dir(&self, name: &str) -> String {
            let path = self.root.join(name);
            fs::create_dir_all(&path).expect("fixture dir");
            path.to_string_lossy().into_owned()
        }
    }

    impl Drop for Fixture {
        fn drop(&mut self) {
            let _ = fs::remove_dir_all(&self.root);
        }
    }

    #[test]
    fn batch_keeps_index_alignment_when_a_node_is_unreadable() {
        let fixture = Fixture::new();
        let a = fixture.write("temp", "42000\n");
        let missing = fixture.root.join("does_not_exist").to_string_lossy().into_owned();
        let b = fixture.write("type", "cpu-0-0");

        let packed = format!("{a}\n{missing}\n{b}");
        let out = read_many_packed(&packed);
        let lines: Vec<&str> = out.split('\n').collect();
        assert_eq!(lines.len(), 3, "سطر لكل مسار حتى لو فشلت القراءة");
        assert_eq!(lines[0], "42000");
        assert_eq!(lines[1], "", "المفقود يبقى موضعه فارغًا لا يُحذف");
        assert_eq!(lines[2], "cpu-0-0".trim_end_matches('\n'));
    }

    #[test]
    fn multiline_node_does_not_break_row_count() {
        let fixture = Fixture::new();
        let multi = fixture.write("maps", "line1\nline2\nline3\n");
        let single = fixture.write("freq", "1300000");

        let out = read_many_packed(&format!("{multi}\n{single}"));
        let lines: Vec<&str> = out.split('\n').collect();
        assert_eq!(lines.len(), 2, "سطر داخلي واحد لا يُنتج سطرين");
        assert_eq!(lines[0], format!("line1{NEWLINE_ESCAPE}line2{NEWLINE_ESCAPE}line3"));
        assert_eq!(lines[1], "1300000");
    }

    #[test]
    fn blank_lines_and_crlf_never_shift_the_map() {
        let fixture = Fixture::new();
        let one = fixture.write("one", "1");
        let two = fixture.write("two", "2");
        // سطر فارغ في الوسط + نهاية CRLF: يجب أن يسقطا بلا إزاحة.
        let out = read_many_packed(&format!("{one}\r\n\n   \n{two}\r\n"));
        assert_eq!(out, "1\n2");
    }

    #[test]
    fn empty_batch_is_empty_not_a_panic() {
        assert_eq!(read_many_packed(""), "");
        assert_eq!(read_many_packed("\n\n"), "");
        assert_eq!(unpack_paths("   ").len(), 0);
    }

    #[test]
    fn empty_file_reads_as_absent_not_as_empty_value() {
        let fixture = Fixture::new();
        let empty = fixture.write("hollow", "   \n");
        let real = fixture.write("real", "1");
        let out = read_many_packed(&format!("{empty}\n{real}"));
        let lines: Vec<&str> = out.split('\n').collect();
        assert_eq!(lines[0], "", "عقدة فارغة = غير مقروءة");
        assert_eq!(lines[1], "1");
    }

    #[test]
    fn listing_is_sorted_deduped_and_can_be_dirs_only() {
        let fixture = Fixture::new();
        fixture.write("b_file", "x");
        fixture.write("a_file", "x");
        fixture.dir("z_dir");
        fixture.dir("m_dir");

        let all: Vec<String> = list_names_packed(&fixture.root.to_string_lossy(), false)
            .split('\n')
            .map(|s| s.to_string())
            .collect();
        assert_eq!(all, vec!["a_file", "b_file", "m_dir", "z_dir"]);

        let dirs_only: Vec<String> = list_names_packed(&fixture.root.to_string_lossy(), true)
            .split('\n')
            .map(|s| s.to_string())
            .collect();
        assert_eq!(dirs_only, vec!["m_dir", "z_dir"]);
    }

    #[test]
    fn missing_directory_returns_empty_not_error() {
        let fixture = Fixture::new();
        let gone = fixture.root.join("nope").to_string_lossy().into_owned();
        assert_eq!(list_names_packed(&gone, false), "");
    }

    #[test]
    fn binary_content_is_lossy_not_dropped() {
        let fixture = Fixture::new();
        let path = fixture.root.join("raw");
        fs::write(&path, [0x31u8, 0xff, 0x32]).expect("binary fixture");
        let out = read_many_packed(&path.to_string_lossy());
        assert!(!out.is_empty(), "بايت غير صالح لا يُلغي القراءة");
        assert!(out.starts_with('1') && out.ends_with('2'));
    }

    #[test]
    fn existence_is_per_row_and_follows_symlinks() {
        let fixture = Fixture::new();
        let present = fixture.write("present", "1");
        let missing = fixture.root.join("gone").to_string_lossy().into_owned();
        let empty_dir = fixture.dir("adir");

        let out = existing_packed(&format!("{present}\n{missing}\n{empty_dir}"));
        assert_eq!(out, "1\n0\n1", "مجلدٌ موجود يعني «موجود» أيضًا");

        // رابط مقطوع: `test -e` يقول غير موجود — ولا `lstat` تقول موجود.
        let dangling = fixture.root.join("dangling_link");
        #[cfg(unix)]
        {
            std::os::unix::fs::symlink(fixture.root.join("nothing_here"), &dangling).expect("symlink");
            let flags = existing_packed(&dangling.to_string_lossy());
            assert_eq!(flags, "0");
        }
    }

    #[test]
    fn existence_batch_keeps_alignment_and_handles_empty_input() {
        let fixture = Fixture::new();
        let a = fixture.write("a", "x");
        let b = fixture.write("b", "x");
        assert_eq!(existing_packed(&format!("{a}\n\n{b}")), "1\n1", "السطر الفارغ يُتجاهل");
        assert_eq!(existing_packed(""), "");
    }

    /// المُدخلات المشتركة مع اختبار Kotlin (`ProbePacketTest`) — نفس الأحرف بالحرف.
    /// وأي انحراف في الصيغة على أي من الجانبين يُسقط هذا الاختبار أو ذاك.
    #[test]
    fn same_vectors() {
        assert_eq!(unpack_paths("/a/one\n/b/two\n"), vec!["/a/one", "/b/two"]);
        assert_eq!(escape_value("754000 \n"), "754000");
        assert_eq!(escape_value("l1\nl2"), format!("l1{NEWLINE_ESCAPE}l2"));
        assert_eq!(escape_value("  \n "), "");
    }
}
