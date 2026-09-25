//! ضغط zip داخل العملية — **كتابة أرشيف واحد** من مصادر يراها التطبيق.
//!
//! المستهلك الإنتاجي: `FileSystemEngine.compress` ← `FileArchiveEngine.createZip` ← مدير
//! الملفات. فلا وُجدت هذه الوحدة لذاتها: ADR-45 يشترط مُستدعيًا إنتاجيًّا قبل أي نقل.
//!
//! **والفكّ لا يُنقل — وهذا قرار مقيس لا تهاون (ADR-49):** فكّ الأرشيف يحمل قاعدة
//! `zip-slip` (رفض مدخل غير آمن **قبل** أول بايت يُكتب)، وإبقاء تلك القاعدة في موضع واحد
//! (Kotlin) أرجح من ربح مقيس ‎٠.١–٠.٤‎ ثانية مقابل تنفيذ ثانٍ لقاعدة أمنية.
//!
//! **والمطابقة حرفية لدلالات `FileArchiveEngine`:** المداخل نسبية إلى أب المصدر وتبدأ باسمه
//! (كما `tar -C`) · ترتيب أبجدي بـ**وحدات UTF-16** (مقارنة Kotlin's `String.compareTo`
//! حرفيًّا) · مجلد فارغ يُكتب بشرطة أخيرة · الأرشيف الناتج نفسه يُستثنى · سقف عمق ٤٠ ·
//! والمستوى ٦ (وهو الافتراضي في `Deflater` عند JDK). فأي انحراف في هذه القائمة يعني أرشيفًا
//! يختلف عن أرشيف Kotlin لنفس المدخلات — ولذلك تُقاس، ولا تُوصف.
use std::fs::{self, File};
use std::io::{BufReader, BufWriter, Read, Write};
use std::path::Path;
use zip::write::SimpleFileOptions;
use zip::{CompressionMethod, ZipWriter};

/// حجم القطعة المقروءة — نفس `CHUNK` في `FileArchiveEngine`.
pub const CHUNK: usize = 64 * 1024;

/// سقف عمق التمشية — نفس `MAX_WALK_DEPTH` المعلن في Kotlin.
pub const MAX_WALK_DEPTH: usize = 40;

/// مستوى الضغط — `Deflater.DEFAULT_COMPRESSION` عند JDK يساوي ٦ (و`i64` لأن zip يطلبه).
pub const LEVEL: i64 = 6;

/// لماذا فشل الضغط. الأسباب منفصلة كما في `ArchiveFailure`، ولا تُدمج في «فشل».
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum CreateFailure {
    /// لا مصادر أصلًا (أو لا مدخل واحد بعد التمشية).
    NoSources,
    /// مصدر لا يُقرأ — بعينته المعلنة كما في Kotlin (`path` أو `unreadable: path`).
    Unreadable(String),
    /// فشل كتابة على القرص (والأرشيف النصف مكتوب يُزال ولا يُترك).
    WriteFailed(String),
}

impl CreateFailure {
    /// الوصف الرمزي الذي يُنقل إلى Kotlin — رموز لا جمل: النصّ الظاهر للمستخدم يُصاغ هناك.
    pub fn reason(&self) -> &'static str {
        match self {
            CreateFailure::NoSources => "no_sources",
            CreateFailure::Unreadable(_) => "unreadable",
            CreateFailure::WriteFailed(_) => "write_failed",
        }
    }

    /// العينة (المسار أو نصّ الخطأ) — وقد تكون فارغة.
    pub fn subject(&self) -> &str {
        match self {
            CreateFailure::NoSources => "",
            CreateFailure::Unreadable(subject) | CreateFailure::WriteFailed(subject) => subject,
        }
    }
}

/// نتيجة ضغط ناجحة: عدد المداخل والبایتات المقروءة من المصادر.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct CreateReport {
    pub entries: usize,
    pub bytes: u64,
}

/// `FileBrowser.normalize`: طيّ الشرطات، وحدّ `/` يبقى `/`، وبلا شرطة أخيرة.
pub fn normalize(path: &str) -> String {
    let mut collapsed = String::with_capacity(path.len());
    let mut last_was_slash = false;
    for ch in path.trim().chars() {
        if ch == '/' {
            if last_was_slash {
                continue;
            }
            last_was_slash = true;
        } else {
            last_was_slash = false;
        }
        collapsed.push(ch);
    }
    if collapsed.is_empty() {
        return "/".to_string();
    }
    if collapsed.len() > 1 {
        while collapsed.ends_with('/') {
            collapsed.pop();
        }
    }
    collapsed
}

/// `FileBrowser.nameOf`: آخر مقطع، و`/` للجذر.
fn name_of(path: &str) -> String {
    let normalized = normalize(path);
    if normalized == "/" {
        return "/".to_string();
    }
    match normalized.rsplit_once('/') {
        Some((_, name)) => name.to_string(),
        None => normalized,
    }
}

/// الأب كما في `File.parentFile` بعد التطبيع، و`None` في الجذر.
fn parent_of(path: &str) -> Option<String> {
    let normalized = normalize(path);
    if normalized == "/" {
        return None;
    }
    match normalized.rsplit_once('/') {
        Some(("", _)) => Some("/".to_string()),
        Some((parent, _)) => Some(parent.to_string()),
        None => Some("/".to_string()),
    }
}

/// الاسم النسبي إلى الأب — نفس `relativeToParent`.
fn relative_to_parent(child: &str, parent: Option<&str>) -> String {
    match parent {
        None => name_of(child),
        Some(parent) => {
            let parent = parent.trim_end_matches('/');
            match child.strip_prefix(&format!("{parent}/")) {
                Some(rest) => rest.to_string(),
                None => name_of(child),
            }
        }
    }
}

/// مقارنة Kotlin's `String.compareTo`: وحدات UTF-16 لا بایتات UTF-8.
///
/// والفرق يظهر فقط في المحارف خارج المستوى الأساسي (إيموجي في أسماء الملفات) — وهي حالة
/// نادرة لكنها حقيقية، وترتيبها المختلف يعني أرشيفين مختلفين لنفس المدخلات.
fn kotlin_order(left: &str, right: &str) -> std::cmp::Ordering {
    left.encode_utf16().cmp(right.encode_utf16())
}

fn same_file(left: &Path, right: &Path) -> bool {
    match (left.canonicalize(), right.canonicalize()) {
        (Ok(a), Ok(b)) => a == b,
        _ => false,
    }
}

fn walk(
    root: &str,
    parent: Option<&str>,
    root_name: &str,
    archive: &str,
    depth: usize,
    out: &mut Vec<(String, String)>,
) -> Result<(), CreateFailure> {
    if depth > MAX_WALK_DEPTH {
        return Ok(());
    }
    let children = fs::read_dir(root)
        .map_err(|io| CreateFailure::Unreadable(format!("unreadable: {root}: {io}")))?;
    let mut names: Vec<String> = Vec::new();
    for child in children.flatten() {
        names.push(child.path().to_string_lossy().to_string());
    }
    names.sort_by(|a, b| kotlin_order(&name_of(a), &name_of(b)));

    for child in names {
        if same_file(Path::new(&child), Path::new(archive)) {
            continue;
        }
        let relative = relative_to_parent(&child, parent);
        let entry_name = if relative.starts_with(&format!("{root_name}/")) || relative == root_name
        {
            relative
        } else {
            format!("{root_name}/{relative}")
        };
        let meta = fs::symlink_metadata(&child)
            .map_err(|io| CreateFailure::Unreadable(format!("unreadable: {child}: {io}")))?;
        if !meta.is_dir() {
            out.push((child, entry_name));
            continue;
        }
        let before = out.len();
        walk(&child, parent, root_name, archive, depth + 1, out)?;
        if out.len() == before {
            // مجلد فارغ (أو كل محتواه مستثنى): مدخل بشرطة أخيرة — وإلا اختفى من الأرشيف.
            out.push((child, format!("{entry_name}/")));
        }
    }
    Ok(())
}

/// يبني قائمة المداخل كما يفعل `createZip` في Kotlin: (المسار على القرص، اسم المدخل).
fn build_entries(sources: &[String], archive: &str) -> Result<Vec<(String, String)>, CreateFailure> {
    let mut entries: Vec<(String, String)> = Vec::new();
    for raw in sources {
        let source = normalize(raw);
        let path = Path::new(&source);
        let meta = match fs::metadata(path) {
            Ok(meta) => meta,
            Err(_) => return Err(CreateFailure::Unreadable(source)),
        };
        let is_dir = meta.is_dir();
        if !is_dir && File::open(path).is_err() {
            return Err(CreateFailure::Unreadable(source));
        }
        let root_name = name_of(&source);
        if !is_dir {
            entries.push((source, root_name));
            continue;
        }
        walk(
            &source,
            parent_of(&source).as_deref(),
            &root_name,
            archive,
            0,
            &mut entries,
        )?;
    }
    Ok(entries)
}

/// يضغط المصادر في `archive_path` — ويُرجع الفشل بأسبابه المنفصلة، ولا يترك أرشيفًا ناقصًا.
pub fn create_zip(sources: &[String], archive_path: &str) -> Result<CreateReport, CreateFailure> {
    if sources.is_empty() {
        return Err(CreateFailure::NoSources);
    }
    let archive = normalize(archive_path);
    let entries = build_entries(sources, &archive)?;
    if entries.is_empty() {
        return Err(CreateFailure::NoSources);
    }
    let mut bytes = 0u64;
    for (path, name) in &entries {
        if !name.ends_with('/') {
            match fs::metadata(path) {
                Ok(meta) => bytes += meta.len(),
                Err(_) => return Err(CreateFailure::Unreadable(path.clone())),
            }
        }
    }

    if let Some(parent) = parent_of(&archive) {
        let _ = fs::create_dir_all(parent);
    }
    let result = write_archive(&archive, &entries);
    match result {
        Ok(()) => Ok(CreateReport {
            entries: entries.len(),
            bytes,
        }),
        Err(failure) => {
            // أرشيف نصفه مكتوب أسوأ من لا أرشيف: يُزال فلا يظنّ المستخدم أنه يملك نسخة.
            let _ = fs::remove_file(&archive);
            Err(failure)
        }
    }
}

fn write_archive(archive: &str, entries: &[(String, String)]) -> Result<(), CreateFailure> {
    let file = File::create(archive)
        .map_err(|io| CreateFailure::WriteFailed(format!("{archive}: {io}")))?;
    let mut zip = ZipWriter::new(BufWriter::new(file));
    let options = SimpleFileOptions::default()
        .compression_method(CompressionMethod::Deflated)
        .compression_level(Some(LEVEL));
    let mut buffer = vec![0u8; CHUNK];

    for (path, name) in entries {
        if name.ends_with('/') {
            zip.add_directory(name.trim_end_matches('/'), options)
                .map_err(|io| CreateFailure::WriteFailed(io.to_string()))?;
            continue;
        }
        zip.start_file(name, options)
            .map_err(|io| CreateFailure::WriteFailed(io.to_string()))?;
        let source = File::open(path).map_err(|_| CreateFailure::Unreadable(path.clone()))?;
        let mut input = BufReader::new(source);
        loop {
            let read = input
                .read(&mut buffer)
                .map_err(|io| CreateFailure::WriteFailed(io.to_string()))?;
            if read == 0 {
                break;
            }
            zip.write_all(&buffer[..read])
                .map_err(|io| CreateFailure::WriteFailed(io.to_string()))?;
        }
    }
    zip.finish()
        .map_err(|io| CreateFailure::WriteFailed(io.to_string()))?;
    Ok(())
}

/// صيغة الردّ بين اللغتين: `OK\u{1}<entries>\u{1}<bytes>` أو `FAIL\u{1}<reason>\u{1}<subject>`.
pub const FIELD_SEP: char = '\u{1}';

/// يُهيّئ العينة للسفر: الفاصل والسطر الجديد يُستبدلان بمسافة (فالحزمة واحدة السطر).
fn safe_subject(subject: &str) -> String {
    subject
        .replace(FIELD_SEP, " ")
        .replace('\n', " ")
        .replace('\r', " ")
}

pub fn pack_result(result: &Result<CreateReport, CreateFailure>) -> String {
    match result {
        Ok(report) => format!(
            "OK{sep}{entries}{sep}{bytes}",
            sep = FIELD_SEP,
            entries = report.entries,
            bytes = report.bytes
        ),
        Err(failure) => format!(
            "FAIL{sep}{reason}{sep}{subject}",
            sep = FIELD_SEP,
            reason = failure.reason(),
            subject = safe_subject(failure.subject())
        ),
    }
}

/// قراءة الردّ — **جانب مرجعي للاختبار وحده** (`#[cfg(test)]`): القارئ الإنتاجي لهذه
/// الصيغة هو `ArchivePacket` في Kotlin، وهذه الدالّة تُبقي الصيغة **مقيسة من الجانبين**
/// بنفس المتجهات (فانحراف حقل واحد يسقط اختبارًا على أي من اللغتين).
#[cfg(test)]
pub fn parse_result(text: &str) -> Option<Result<CreateReport, (String, String)>> {
    let mut parts = text.split(FIELD_SEP);
    let kind = parts.next()?;
    match kind {
        "OK" => {
            let entries = parts.next()?.parse::<usize>().ok()?;
            let bytes = parts.next()?.parse::<u64>().ok()?;
            if parts.next().is_some() {
                return None;
            }
            Some(Ok(CreateReport { entries, bytes }))
        }
        "FAIL" => {
            let reason = parts.next()?.to_string();
            let subject = parts.next()?.to_string();
            if parts.next().is_some() {
                return None;
            }
            Some(Err((reason, subject)))
        }
        _ => None,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;
    use std::io::Read;
    use std::path::PathBuf;

    struct Tree {
        root: PathBuf,
    }

    impl Tree {
        fn new(name: &str) -> Self {
            let root = std::env::temp_dir().join(format!("maxmanager-archive-{name}-{}", std::process::id()));
            let _ = fs::remove_dir_all(&root);
            fs::create_dir_all(&root).unwrap();
            Tree { root }
        }

        fn write(&self, relative: &str, contents: &str) -> String {
            let path = self.root.join(relative);
            fs::create_dir_all(path.parent().unwrap()).unwrap();
            fs::write(&path, contents).unwrap();
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

    fn names(archive: &str) -> Vec<String> {
        let file = File::open(archive).unwrap();
        let mut zip = zip::ZipArchive::new(BufReader::new(file)).unwrap();
        let mut out = Vec::new();
        for index in 0..zip.len() {
            out.push(zip.by_index(index).unwrap().name().to_string());
        }
        out
    }

    fn contents(archive: &str, entry: &str) -> String {
        let file = File::open(archive).unwrap();
        let mut zip = zip::ZipArchive::new(BufReader::new(file)).unwrap();
        let mut text = String::new();
        zip.by_name(entry).unwrap().read_to_string(&mut text).unwrap();
        text
    }

    #[test]
    fn entries_are_rooted_sorted_and_keep_empty_directories() {
        let tree = Tree::new("entries");
        tree.write("src/b.txt", "bbb");
        tree.write("src/a.txt", "aaa");
        tree.write("src/nested/deep.txt", "deep");
        fs::create_dir_all(tree.path("src/empty")).unwrap();
        let archive = tree.path("out.zip");

        let report = create_zip(&[tree.path("src")], &archive).unwrap();
        assert_eq!(
            names(&archive),
            vec![
                "src/a.txt".to_string(),
                "src/b.txt".to_string(),
                "src/empty/".to_string(),
                "src/nested/deep.txt".to_string(),
            ]
        );
        assert_eq!(report.entries, 4);
        assert_eq!(report.bytes, 3 + 3 + 4);
        assert_eq!(contents(&archive, "src/nested/deep.txt"), "deep");
    }

    #[test]
    fn sorted_by_kotlin_utf16_order_not_utf8_bytes() {
        let tree = Tree::new("order");
        // محرف خارج المستوى الأساسي (إيموجي): ترتيبه بوحدات UTF-16 يخالف ترتيبه ببایتات UTF-8
        tree.write("src/a.txt", "1");
        tree.write("src/\u{1F600}.txt", "2");
        tree.write("src/\u{E000}.txt", "3");
        let archive = tree.path("out.zip");

        create_zip(&[tree.path("src")], &archive).unwrap();
        let listed = names(&archive);
        let mut expected = vec!["\u{1F600}.txt", "\u{E000}.txt", "a.txt"];
        expected.sort_by(|a, b| kotlin_order(a, b));
        let expected: Vec<String> = expected
            .into_iter()
            .map(|name| format!("src/{name}"))
            .collect();
        assert_eq!(listed, expected);
    }

    #[test]
    fn the_archive_itself_is_never_packed_into_itself() {
        let tree = Tree::new("self");
        tree.write("src/keep.txt", "keep");
        let archive = tree.path("src/self.zip");

        create_zip(&[tree.path("src")], &archive).unwrap();
        assert_eq!(names(&archive), vec!["src/keep.txt".to_string()]);
    }

    #[test]
    fn a_missing_source_is_declared_unreadable_and_leaves_no_archive() {
        let tree = Tree::new("missing");
        let archive = tree.path("out.zip");
        let failure = create_zip(&[tree.path("ghost")], &archive).unwrap_err();
        assert_eq!(failure.reason(), "unreadable");
        assert_eq!(failure.subject(), tree.path("ghost"));
        assert!(!Path::new(&archive).exists(), "لا أرشيف ناقص يُترك على القرص");
    }

    #[test]
    fn no_sources_is_its_own_failure() {
        let tree = Tree::new("nosources");
        assert_eq!(
            create_zip(&[], &tree.path("out.zip")).unwrap_err(),
            CreateFailure::NoSources
        );
    }

    #[test]
    fn depth_cap_drops_what_is_deeper_and_says_so() {
        let tree = Tree::new("depth");
        let mut deep = String::from("src");
        for level in 0..(MAX_WALK_DEPTH + 3) {
            deep = format!("{deep}/d{level}");
        }
        tree.write(&format!("{deep}/leaf.txt"), "leaf");
        tree.write("src/top.txt", "top");
        let archive = tree.path("out.zip");

        create_zip(&[tree.path("src")], &archive).unwrap();
        let listed = names(&archive);
        assert!(listed.contains(&"src/top.txt".to_string()));
        assert!(
            !listed.iter().any(|name| name.ends_with("leaf.txt")),
            "ما هو أعمق من السقف يُهمَل (وهو معلن لا مخفيّ)"
        );
    }

    #[test]
    fn round_trip_through_the_reader_keeps_bytes_and_sizes() {
        let tree = Tree::new("roundtrip");
        let payload = "x".repeat(CHUNK * 2 + 17);
        tree.write("src/big.bin", &payload);
        let archive = tree.path("out.zip");

        let report = create_zip(&[tree.path("src")], &archive).unwrap();
        assert_eq!(report.bytes, payload.len() as u64);
        assert_eq!(contents(&archive, "src/big.bin"), payload);
    }

    #[test]
    fn result_packet_round_trips_and_rejects_malformed() {
        let ok = pack_result(&Ok(CreateReport {
            entries: 7,
            bytes: 4096,
        }));
        assert_eq!(
            parse_result(&ok),
            Some(Ok(CreateReport {
                entries: 7,
                bytes: 4096
            }))
        );

        let failed = pack_result(&Err(CreateFailure::Unreadable("/a/b\nc.txt".to_string())));
        match parse_result(&failed) {
            Some(Err((reason, subject))) => {
                assert_eq!(reason, "unreadable");
                // السطر الجديد لا يمرّ: الحزمة سطر واحد، ومحاذاتها أهم من العينة حرفيًّا.
                assert_eq!(subject, "/a/b c.txt");
            }
            other => panic!("متوقّع فشل مقروء، حاصل {other:?}"),
        }

        assert_eq!(parse_result(""), None);
        assert_eq!(parse_result("OK\u{1}3"), None);
        assert_eq!(parse_result("MAYBE\u{1}1\u{1}2"), None);
        assert_eq!(parse_result("OK\u{1}x\u{1}2"), None);
    }

    #[test]
    fn normalize_and_name_mirror_file_browser() {
        assert_eq!(normalize("/a/b//"), "/a/b");
        assert_eq!(normalize("/"), "/");
        assert_eq!(normalize("   "), "/");
        assert_eq!(normalize("/a/"), "/a");
        assert_eq!(name_of("/a/b.txt"), "b.txt");
        assert_eq!(name_of("/"), "/");
        assert_eq!(parent_of("/a/b.txt"), Some("/a".to_string()));
        assert_eq!(parent_of("/a"), Some("/".to_string()));
        assert_eq!(parent_of("/"), None);
    }
}
