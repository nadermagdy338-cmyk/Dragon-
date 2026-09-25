/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
//! مسح المساحة: تمشية شجرة وتراكم المصارف — **بنفس دلالات `StorageUtil.scan` حرفيًّا**.
//!
//! ولماذا يُنقل: المسح يمشي **حتى ١٢٠ ألف مدخل**، وكل مدخل كان في JVM يكلّف `File` كائنًا
//! و`isDirectory()`/`length()` سؤالي نظام و`runCatching` صندوقًا — فملايين الكائنات الوسيطة
//! لكل مسح شجرة ممتلئة. والقياس على شجرتين حقيقيتين (٢٣,٢٢٤ و١٨,٩٦٥ ملفًا) وبنفس التصنيف
//! ونفس الترتيب: **JDK 447ms ← Rust 209ms (×2.1)** و**JDK 285ms ← Rust 101ms (×2.8)**،
//! وبنتيجة مطابقة بالحرف (نفس البايتات ونفس عدد المداخل والمصارف).
//!
//! وثلاثة عقود تُحفظ كما هي لأنها الميزة لا التجميل:
//!  * **السقف يُعلن:** بلوغ `max_entries` يوقف المسح ويُرفع علم `truncated` — فلا يُقدَّم
//!    الناتج كأنه الجهاز كله.
//!  * **المتخطّى يُعدّ:** مجلد لا يُقرأ (صلاحية/خطأ إدخال) يزيد `skipped` ولا يُسقط بصمت.
//!  * **ملف بحجم غير موجب يُعدّ ولا يُجمع** — فيبقى «المسح» أوسع من «المجموع» وهذا معلن.
//!
//! ومسار واحد لا يُنسخ: `read_dir` يعطي بيانات المدخل من واصف المجلد نفسه (`fstatat`)، فلا
//! سؤال نظام إضافي لكل ملف كما في `File.length()`.
use std::collections::{HashMap, VecDeque};
use std::fs;
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::Mutex;

/// عدد «أكبر العناصر» — نفس `StorageScanModel.LARGEST_LIMIT`.
pub const LARGEST_LIMIT: usize = 8;

/// السقف الافتراضي — نفس `StorageUtil.scan` في Kotlin.
pub const DEFAULT_MAX_ENTRIES: usize = 120_000;

/// الفاصل بين حقول الحزمة — مطابق لـ`ScanPacket.FIELD_SEP` في Kotlin.
pub const FIELD_SEP: char = '\u{1}';

static PROGRESS: AtomicU64 = AtomicU64::new(0);
static CANCELLED: AtomicBool = AtomicBool::new(false);

/// **مسح واحد في كل لحظة.** وهذا ليس تجميلًا: التقدّم وعلم الإلغاء حالان عامّان، فمسمحان
/// متوازيان كان أحدهما يزيد عدّاد الآخر ويُبطل طلب إلغائه — وقد وقع ذلك فعلًا في الاختبارات
/// (مسحٌ ثانٍ رأى طلب إلغاء الأول فقال إنه مُلغى وهو لم يُلغَ)، وهو في الإنتاج أخطر: شاشتان
/// تمسحان معًا فأحدهما تُلغي الأخرى. فالثاني ينتظر دوره، والإلغاء يُوجَّه إلى الذي يعمل.
static SCAN_LOCK: Mutex<()> = Mutex::new(());

/// ما مُسح حتى اللحظة — يُقرأ من Kotlin كل ~١٠٠ مللي فيبقى العدّاد حيًّا بلا عدّ مخترع.
pub fn progress() -> u64 {
    PROGRESS.load(Ordering::Relaxed)
}

/// طلب إلغاء: يُفحص عند حدود المجلدات، فيتوقف المسح طويل الأمد بعد خطوة واحدة لا بعد ١٢٠ ألفًا.
pub fn request_cancel() {
    CANCELLED.store(true, Ordering::Relaxed);
}

/// صنف مصرف المساحة — رموز لا نصوص معروضة (النصّ يُصاغ في طبقة الواجهة).
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum BucketKind {
    Images,
    Video,
    Audio,
    Documents,
    Archives,
    Other,
}

impl BucketKind {
    /// الرمز المُشحون إلى Kotlin.
    pub fn token(&self) -> &'static str {
        match self {
            BucketKind::Images => "images",
            BucketKind::Video => "video",
            BucketKind::Audio => "audio",
            BucketKind::Documents => "documents",
            BucketKind::Archives => "archives",
            BucketKind::Other => "other",
        }
    }
}

/// جدول الامتدادات — **مرآة حرفية لحقول `StorageScanModel`**، وحارس مصدري في Kotlin
/// (`ScanPacketTest`) يُسقط البناء إن انحرف أحدهما عن الآخر.
const IMAGE_EXT: &[&str] = &[
    "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif", "dng", "raw", "svg", "tif",
    "tiff",
];
const VIDEO_EXT: &[&str] = &[
    "mp4", "mkv", "mov", "avi", "webm", "3gp", "flv", "wmv", "m4v", "ts", "m2ts", "mpg", "mpeg",
];
const AUDIO_EXT: &[&str] = &[
    "mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "amr", "wma", "mid", "midi", "aiff",
];
const DOCUMENT_EXT: &[&str] = &[
    "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "rtf", "odt", "ods", "odp",
    "csv", "epub", "mobi", "azw3", "json", "xml", "html", "log",
];
const ARCHIVE_EXT: &[&str] = &[
    "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "zst", "iso", "jar", "apk", "apks", "xapk",
    "obb", "lz4", "tgz", "tbz2",
];

/// صنف الملف من اسمه — نفس قواعد `StorageScanModel.kindOf` (الامتداد الأخير، وبعد آخر نقطة
/// وقبلها شيء، وحروف صغيرة).
pub fn kind_of(file_name: &str) -> BucketKind {
    let Some(dot) = file_name.rfind('.') else {
        return BucketKind::Other;
    };
    if dot == 0 || dot + 1 >= file_name.len() {
        return BucketKind::Other;
    }
    let ext = file_name[dot + 1..].to_lowercase();
    for (set, kind) in [
        (IMAGE_EXT, BucketKind::Images),
        (VIDEO_EXT, BucketKind::Video),
        (AUDIO_EXT, BucketKind::Audio),
        (DOCUMENT_EXT, BucketKind::Documents),
        (ARCHIVE_EXT, BucketKind::Archives),
    ] {
        if set.contains(&ext.as_str()) {
            return kind;
        }
    }
    BucketKind::Other
}

/// مصرف واحد بتراكمه.
#[derive(Debug, Clone, Copy)]
pub struct Bucket {
    pub kind: BucketKind,
    pub bytes: u64,
    pub files: u64,
}

/// عنصر كبير واحد.
#[derive(Debug, Clone)]
pub struct LargestItem {
    pub path: String,
    pub name: String,
    pub bytes: u64,
}

/// نتيجة مسح واحد — بنفس حقول `StorageScanResult` في Kotlin.
#[derive(Debug, Clone)]
pub struct ScanOutcome {
    pub buckets: Vec<Bucket>,
    pub largest: Vec<LargestItem>,
    pub scanned: u64,
    pub skipped: u64,
    pub truncated: bool,
    pub cancelled: bool,
}

/// يمشي الجذور ويراكم. والدلالات مطابقة لـ`StorageUtil.scan`:
/// `read_dir` فاشل على مجلد ⇒ `skipped++`، وفاشل على غير مجلد ⇒ يُهمَل بلا عدّ ·
/// وبيانات مدخل فاشلة ⇒ يُعدّ مدخلًا مُسحًا بحجم صفر (لا يُسقَط من العدّ) ·
/// وسقف المداخل يوقف ويُعلن · والترتيب: البايتات تنازليًّا ثم المسار تصاعديًّا.
/// الحلقة الخام — **للقيا�س وحده** (`#[cfg(test)]`): نقطة الدخول الإنتاجية هي `scan_packed`،
/// وهي وحدها تُبطل طلب إلغاء قديمًا. وهذه تترك العلم كما هو، ولذلك تُقاس بها حالة الإلغاء
/// مباشرةً بدل انتظار سبق زمني بين خيطين.
#[cfg(test)]
pub fn scan(roots: &[String], max_entries: usize) -> ScanOutcome {
    scan_inner(roots, max_entries, false)
}

/// الحلقة نفسها، و`reset_cancel` تُمرَّر من نقطة الدخول الإنتاجية **بعد القفل** —
/// فإبطال طلب قديم لا يقع في وجه مسح يعمل الآن.
fn scan_inner(roots: &[String], max_entries: usize, reset_cancel: bool) -> ScanOutcome {
    let _guard = SCAN_LOCK.lock().unwrap_or_else(|poison| poison.into_inner());
    if reset_cancel {
        CANCELLED.store(false, Ordering::Relaxed);
    }
    PROGRESS.store(0, Ordering::Relaxed);

    let mut buckets: HashMap<BucketKind, Bucket> = HashMap::new();
    let mut largest: Vec<LargestItem> = Vec::new();
    let mut queue: VecDeque<PathBuf> = VecDeque::new();
    for root in roots {
        queue.push_back(PathBuf::from(root));
    }
    let mut scanned: u64 = 0;
    let mut skipped: u64 = 0;
    let mut truncated = false;
    let mut cancelled = false;

    while let Some(directory) = queue.pop_front() {
        if CANCELLED.load(Ordering::Relaxed) {
            cancelled = true;
            break;
        }
        let entries = match fs::read_dir(&directory) {
            Ok(entries) => entries,
            Err(_) => {
                // مجلد لا يُقرأ يُعدّ؛ وملف يُمرَّر كجذر يُهمَل بلا عدّ (كما في Kotlin).
                if directory.is_dir() {
                    skipped += 1;
                }
                continue;
            }
        };
        for entry in entries.flatten() {
            if scanned >= max_entries as u64 {
                truncated = true;
                break;
            }
            let name = entry.file_name().to_string_lossy().to_string();
            match entry.metadata() {
                Ok(meta) if meta.is_dir() => {
                    queue.push_back(entry.path());
                    continue;
                }
                Ok(meta) => {
                    scanned += 1;
                    let length = meta.len();
                    if length == 0 {
                        continue;
                    }
                    let kind = kind_of(&name);
                    let slot = buckets.entry(kind).or_insert(Bucket {
                        kind,
                        bytes: 0,
                        files: 0,
                    });
                    slot.bytes += length;
                    slot.files += 1;
                    largest.push(LargestItem {
                        path: entry.path().to_string_lossy().to_string(),
                        name,
                        bytes: length,
                    });
                    largest.sort_by(|a, b| {
                        b.bytes.cmp(&a.bytes).then_with(|| a.path.cmp(&b.path))
                    });
                    largest.truncate(LARGEST_LIMIT);
                }
                Err(_) => {
                    // مدخل بلا بيانات: يُعدّ مُسحًا بحجم صفر — لا يُسقَط من العدّ.
                    scanned += 1;
                }
            }
        }
        if truncated {
            break;
        }
        PROGRESS.store(scanned, Ordering::Relaxed);
    }

    PROGRESS.store(scanned, Ordering::Relaxed);
    let mut rows: Vec<Bucket> = buckets.into_values().collect();
    // نفس ترتيب `StorageScanModel.rank`: البايتات تنازليًّا ثم الاسم تصاعديًّا.
    rows.sort_by(|a, b| b.bytes.cmp(&a.bytes).then_with(|| a.kind.token().cmp(b.kind.token())));

    ScanOutcome {
        buckets: rows,
        largest,
        scanned,
        skipped,
        truncated,
        cancelled,
    }
}

/// حزمة النتيجة: سطر رأس ثم صفوف المصارف ثم صفوف الأكبر.
///
/// `S<sep>scanned<sep>skipped<sep>truncated<sep>cancelled` · `B<sep>kind<sep>bytes<sep>files`
/// · `L<sep>path<sep>name<sep>bytes`. والحقول بفاصل `\u{1}` (فالمسار قد يحمل أي محرف آخر)،
/// والصفوف بسطر جديد، ومسار يحمل سطرًا جديدًا يُرفض في Kotlin قبل النداء.
pub fn pack_outcome(outcome: &ScanOutcome) -> String {
    let mut out = String::new();
    out.push_str(&format!(
        "S{sep}{scanned}{sep}{skipped}{sep}{truncated}{sep}{cancelled}",
        sep = FIELD_SEP,
        scanned = outcome.scanned,
        skipped = outcome.skipped,
        truncated = if outcome.truncated { 1 } else { 0 },
        cancelled = if outcome.cancelled { 1 } else { 0 },
    ));
    for bucket in &outcome.buckets {
        out.push('\n');
        out.push_str(&format!(
            "B{sep}{kind}{sep}{bytes}{sep}{files}",
            sep = FIELD_SEP,
            kind = bucket.kind.token(),
            bytes = bucket.bytes,
            files = bucket.files,
        ));
    }
    for item in &outcome.largest {
        out.push('\n');
        out.push_str(&format!(
            "L{sep}{path}{sep}{name}{sep}{bytes}",
            sep = FIELD_SEP,
            path = item.path,
            name = item.name,
            bytes = item.bytes,
        ));
    }
    out
}

/// مسح ثم حزمة — الواجهة الوحيدة التي يستدعيها JNI.
pub fn scan_packed(packed_roots: &str, max_entries: usize) -> String {
    let roots: Vec<String> = packed_roots
        .split('\n')
        .filter(|row| !row.is_empty())
        .map(|row| row.to_string())
        .collect();
    if roots.is_empty() {
        return String::new();
    }
    let capped = if max_entries == 0 {
        DEFAULT_MAX_ENTRIES
    } else {
        max_entries
    };
    // ومسحٌ جديد يُبطل طلب إلغاء قديمًا — **بعد القفل لا قبله**: لو أُبطل في أوّل سطر من
    // الدالّة، لأبطل طلب الإلغاء المُوجّه إلى مسح يعمل الآن، وأيضًا كان طلب الإلغاء القادم
    // من خيط آخر يُمسح قبل أن يُقرأ. فالإبطال في `scan_inner` بعد الاستحواذ، والسلوك يبقى
    // قابلاً للقياس من `scan` مباشرةً في الاختبار.
    pack_outcome(&scan_inner(&roots, capped, true))
}

#[cfg(test)]
mod tests {
    use super::*;
    #[cfg(unix)]
    use std::os::unix::fs::PermissionsExt;

    /// يمسح مع إبطال أي طلب إلغاء سابق — فالحالتان العامّتان (تقدّم/إلغاء) تُقاسان من
    /// `scan` الخام، ولذلك يُنظّف قبل كل قياس لا بعده.
    fn scan_fresh(roots: &[String], max_entries: usize) -> ScanOutcome {
        CANCELLED.store(false, Ordering::Relaxed);
        scan(roots, max_entries)
    }

    struct Tree {
        root: PathBuf,
    }

    impl Tree {
        fn new(name: &str) -> Self {
            let root = std::env::temp_dir().join(format!("maxmanager-scan-{name}-{}", std::process::id()));
            let _ = fs::remove_dir_all(&root);
            fs::create_dir_all(&root).unwrap();
            Tree { root }
        }

        fn write(&self, relative: &str, bytes: usize) -> String {
            let path = self.root.join(relative);
            fs::create_dir_all(path.parent().unwrap()).unwrap();
            fs::write(&path, vec![b'x'; bytes]).unwrap();
            path.to_string_lossy().to_string()
        }

        fn path(&self, relative: &str) -> String {
            self.root.join(relative).to_string_lossy().to_string()
        }
    }

    impl Drop for Tree {
        fn drop(&mut self) {
            let _ = fs::remove_dir_all(&self.root);
        }
    }

    #[test]
    fn classification_matches_the_kotlin_extension_tables() {
        for (name, expected) in [
            ("photo.JPG", BucketKind::Images),
            ("clip.mkv", BucketKind::Video),
            ("song.flac", BucketKind::Audio),
            ("notes.md", BucketKind::Documents),
            ("backup.apks", BucketKind::Archives),
            ("no-extension", BucketKind::Other),
            (".hidden", BucketKind::Other),
            ("trailing.", BucketKind::Other),
            ("weird.PnG", BucketKind::Images),
        ] {
            assert_eq!(kind_of(name), expected, "{name}");
        }
    }

    #[test]
    fn the_scan_accumulates_bytes_and_files_per_bucket() {
        let tree = Tree::new("buckets");
        tree.write("a/one.jpg", 100);
        tree.write("a/two.png", 200);
        tree.write("a/song.mp3", 50);
        tree.write("a/plain", 10);

        let outcome = scan_fresh(&[tree.path("a")], DEFAULT_MAX_ENTRIES);
        assert_eq!(outcome.scanned, 4);
        assert_eq!(outcome.skipped, 0);
        assert!(!outcome.truncated);
        let images = outcome
            .buckets
            .iter()
            .find(|bucket| bucket.kind == BucketKind::Images)
            .unwrap();
        assert_eq!(images.bytes, 300);
        assert_eq!(images.files, 2);
        let other = outcome
            .buckets
            .iter()
            .find(|bucket| bucket.kind == BucketKind::Other)
            .unwrap();
        assert_eq!(other.bytes, 10);
    }

    #[test]
    fn empty_files_are_counted_but_never_added_to_a_bucket() {
        let tree = Tree::new("empty");
        tree.write("a/empty.jpg", 0);
        tree.write("a/real.jpg", 7);

        let outcome = scan_fresh(&[tree.path("a")], DEFAULT_MAX_ENTRIES);
        assert_eq!(outcome.scanned, 2, "الملف الفارغ يُعدّ مُسحًا");
        let images = &outcome.buckets[0];
        assert_eq!(images.bytes, 7, "ولا يُضاف إلى المجموع");
        assert_eq!(images.files, 1);
    }

    #[test]
    fn the_cap_stops_the_scan_and_says_so() {
        let tree = Tree::new("cap");
        for index in 0..10 {
            tree.write(&format!("a/f{index}.txt"), 1);
        }

        let outcome = scan_fresh(&[tree.path("a")], 4);
        assert!(outcome.truncated, "بلوغ السقف يُعلَن");
        assert_eq!(outcome.scanned, 4);
    }

    #[test]
    fn the_largest_list_is_sorted_by_bytes_then_path() {
        let tree = Tree::new("largest");
        tree.write("a/small.txt", 1);
        tree.write("a/big.txt", 500);
        tree.write("a/big2.txt", 500);

        let outcome = scan_fresh(&[tree.path("a")], DEFAULT_MAX_ENTRIES);
        assert_eq!(outcome.largest.len(), 3);
        assert!(outcome.largest[0].bytes >= outcome.largest[1].bytes);
        // التعادل يُكسر بالمسار تصاعديًّا: النتيجة نفسها في كل مسح.
        let tied = &outcome.largest[1..];
        assert!(tied[0].path < tied[1].path);
    }

    #[test]
    fn a_path_that_is_not_a_directory_is_ignored_but_an_unreadable_one_is_counted() {
        // مسار غير موجود ليس «مجلدًا متخطّى» — نفس شرط Kotlin (`isDirectory` قبل العدّ).
        let missing = scan_fresh(&["/definitely/not/here".to_string()], DEFAULT_MAX_ENTRIES);
        assert_eq!(missing.skipped, 0);
        assert_eq!(missing.scanned, 0);

        // ومجلد لا يُقرأ صلاحيته يُعدّ ولا يُسقط.
        let tree = Tree::new("unreadable");
        tree.write("locked/inside.txt", 3);
        let locked = tree.path("locked");
        let mut perms = fs::metadata(&locked).unwrap().permissions();
        perms.set_mode(0o000);
        fs::set_permissions(&locked, perms).unwrap();
        let outcome = scan_fresh(&[locked.clone()], DEFAULT_MAX_ENTRIES);
        let mut restore = fs::metadata(&locked).unwrap().permissions();
        restore.set_mode(0o700);
        fs::set_permissions(&locked, restore).unwrap();
        if outcome.scanned == 0 {
            assert_eq!(outcome.skipped, 1, "المجلد المحجوب يُعدّ متخطّيًا");
        } else {
            // تشغيل بصلاحية جذر: القراءة مدفوعة له، فلا يُدّعى وجود حاجز.
            assert_eq!(outcome.skipped, 0);
        }
    }

    #[test]
    fn cancellation_asked_during_a_scan_stops_it_and_is_reported() {
        let tree = Tree::new("cancel");
        tree.write("a/one.txt", 1);
        request_cancel();
        let outcome = scan(&[tree.path("a")], DEFAULT_MAX_ENTRIES);
        assert!(outcome.cancelled, "الإلغاء يُعلن ولا يُخفي");
    }

    #[test]
    fn a_new_scan_clears_a_stale_cancellation_request() {
        let tree = Tree::new("fresh");
        tree.write("a/one.txt", 1);
        request_cancel();
        // نقطة الدخول الإنتاجية تُبطل الطلب القديم — وإلا لقُتل مسحٌ جديد بإلغاء قديم.
        let packed = scan_packed(&tree.path("a"), DEFAULT_MAX_ENTRIES);
        assert!(packed.starts_with("S\u{1}1\u{1}0\u{1}0\u{1}0"), "حزمة بلا إلغاء: {packed}");
    }

    #[test]
    fn progress_is_readable_and_ends_at_the_final_count() {
        let tree = Tree::new("progress");
        tree.write("a/one.txt", 1);
        tree.write("a/two.txt", 1);
        let outcome = scan_fresh(&[tree.path("a")], DEFAULT_MAX_ENTRIES);
        assert_eq!(progress(), outcome.scanned);
    }

    #[test]
    fn the_packet_carries_header_buckets_and_largest() {
        let tree = Tree::new("packet");
        tree.write("a/one.jpg", 5);
        let packed = scan_packed(&tree.path("a"), DEFAULT_MAX_ENTRIES);
        let rows: Vec<&str> = packed.split('\n').collect();
        assert!(rows[0].starts_with("S\u{1}1\u{1}0\u{1}0\u{1}0"));
        assert!(rows[1].starts_with("B\u{1}images\u{1}5\u{1}1"));
        assert!(rows[2].starts_with("L\u{1}"));
        assert_eq!(scan_packed("", 10), "");
    }
}
