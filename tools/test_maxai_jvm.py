#!/usr/bin/env python3
"""Run focused production Kotlin/JUnit tests without Gradle, Android, or mocks.

Uses existing Gradle-cached compiler/runtime jars; never downloads dependencies.
This is not an Android compilation or device test.
"""

import argparse
import os
from pathlib import Path
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "manager/app/src/main/java/nd/max"
TEST = ROOT / "manager/app/src/test/java/nd/max"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--work-dir", type=Path, default=Path(tempfile.gettempdir()))
    args = parser.parse_args()
    cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1"

    def jar(group, artifact, version):
        matches = sorted((cache / group / artifact / version).glob(f"*/{artifact}-{version}.jar"))
        if not matches:
            parser.error(f"Missing cached {group}:{artifact}:{version}; no dependency was downloaded")
        return str(matches[0])

    stdlib = jar("org.jetbrains.kotlin", "kotlin-stdlib", "2.3.10")
    coroutines = jar("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.10.2")
    annotations = jar("org.jetbrains", "annotations", "13.0")
    junit = jar("junit", "junit", "4.13.2")
    hamcrest = jar("org.hamcrest", "hamcrest-core", "1.3")
    compiler = jar("org.jetbrains.kotlin", "kotlin-compiler-embeddable", "2.3.10")
    reflect = jar("org.jetbrains.kotlin", "kotlin-reflect", "1.6.10")
    compiler_cp = os.pathsep.join([compiler, stdlib, coroutines, annotations, reflect])
    runtime_cp = os.pathsep.join([stdlib, coroutines, annotations, junit, hamcrest])
    sources = [
        SOURCE / "core/maxai/CoalescingCycleRunner.kt",
        SOURCE / "core/maxai/MaxAiEpisode.kt",
        SOURCE / "ui/mainscreens/MaxAiPresentation.kt",
        TEST / "core/maxai/CoalescingCycleRunnerTest.kt",
        TEST / "ui/mainscreens/MaxAiPresentationTest.kt",
        TEST / "ui/mainscreens/MaxAiTimelineFilterTest.kt",
        TEST / "ui/viewmodel/MaxAiPresentationArchitectureTest.kt",
        TEST / "core/maxai/MaxAiInterruptSafetyTest.kt",
        SOURCE / "ui/navigation/LaunchRoutes.kt",
        TEST / "ui/navigation/LaunchRouteTest.kt",
        # مكان الأرشيف وسياسة التقليم خالصان عن قصد: يُترجمان ويُقاسان هنا بلا Android
        # ولا جهاز. والقاعدة الثانية هي الوحيدة التي تحذف بيانات بلا سؤال.
        SOURCE / "ui/util/MaxBackupStorage.kt",
        TEST / "ui/util/MaxBackupStorageTest.kt",
        SOURCE / "ui/util/MaxBackupRetention.kt",
        TEST / "ui/util/MaxBackupRetentionTest.kt",
        # `OCR-01`/`OCR-02`: قراءة الوقت وحساب الموعد القادم وقواعد المجموعات وتخزينها
        # النصّي — أربعة أشياء تُحسب ولا تُرى، فتخطئ بصمت إن لم تُقَس في JVM عادي.
        SOURCE / "ui/util/MaxBackupSchedule.kt",
        SOURCE / "ui/util/MaxBackupFolders.kt",
        TEST / "ui/util/MaxBackupSchedulePlanTest.kt",
        # `OCR-04`: المفضّلة — التحقّق بالاسم، والترتيب، والملف. لا شيء منها يحتاج Android.
        SOURCE / "ui/util/MaxBackupFavorites.kt",
        TEST / "ui/util/MaxBackupFavoritesTest.kt",
        # `OCR-06`: الحصيلة وصفّ التجميع — رقمان يقفان بين المستخدم وقائمة بـ١٩١ نسخة.
        SOURCE / "ui/util/MaxBackupCounts.kt",
        TEST / "ui/util/MaxBackupCountsTest.kt",
        # نموذج اللوحين وحرس العمليات: قرار الترتيب يحدّد أيّ شاشتين تُركَّبان، والحرس
        # هو آخر ما يقف بين نقرة وحذف شجرة. كلاهما خالص فلا حاجة إلى Android لقياسه.
        SOURCE / "ui/util/FileSystemModel.kt",
        SOURCE / "ui/util/FileActionModel.kt",
        # `OCR-10`: مرشّح البحث خالص ويعتمد عليه `FilePaneModel.visible()`، فلو لم يُترجم
        # هنا لبقي قرار «ما يراه المستخدم» بلا قياس.
        SOURCE / "ui/util/FileSearchFilters.kt",
        TEST / "ui/util/FileSearchFiltersTest.kt",
        SOURCE / "ui/util/FilePaneModel.kt",
        TEST / "ui/util/FilePaneModelTest.kt",
        # حرّاس شكل على قائمة الاختيار الواحد: الصنف الذي أسقط شاشة مدير الملفات كان
        # «فهرسة قائمة أيقونات أقصر من الأسماء» — وهو عطبٌ لا يراه مصرّف ولا اختبار جهاز.
        TEST / "ui/design/MaxViewMenuContractTest.kt",
    ]
    tests = [
        "nd.max.core.maxai.CoalescingCycleRunnerTest",
        "nd.max.core.maxai.MaxAiInterruptSafetyTest",
        "nd.max.ui.mainscreens.MaxAiPresentationTest",
        "nd.max.ui.navigation.LaunchRouteTest",
        "nd.max.ui.mainscreens.MaxAiTimelineFilterTest",
        "nd.max.ui.mainscreens.MaxAiTimelineSearchTest",
        "nd.max.ui.viewmodel.MaxAiPresentationArchitectureTest",
        "nd.max.ui.util.MaxBackupStorageTest",
        "nd.max.ui.util.MaxBackupRetentionTest",
        "nd.max.ui.util.MaxBackupSchedulePlanTest",
        "nd.max.ui.util.MaxBackupFavoritesTest",
        "nd.max.ui.util.MaxBackupCountsTest",
        "nd.max.ui.util.FileSearchFiltersTest",
        "nd.max.ui.util.FilePaneModelTest",
        "nd.max.ui.design.MaxViewMenuContractTest",
    ]
    live_test = TEST / "ui/mainscreens/MaxLivePresentationArchitectureTest.kt"
    if live_test.exists():
        sources.append(live_test)
        tests.append("nd.max.ui.mainscreens.MaxLivePresentationArchitectureTest")

    # شاشة التطبيقات: حرس عائلة الحالات الثلاث يقرأ الشاشة نصًّا بعد تنقيتها من التعليقات،
    # فلا يحتاج Compose ولا جهازًا — وكان تضاربها اللغوي هو سبب وجوده.
    applist_test = TEST / "ui/mainscreens/ApplistPresentationArchitectureTest.kt"
    if applist_test.exists():
        sources.append(applist_test)
        tests.append("nd.max.ui.mainscreens.ApplistPresentationArchitectureTest")

    with tempfile.TemporaryDirectory(prefix="maxai-jvm-", dir=args.work_dir) as output:
        # Parse the Android-facing code with Kotlin's actual parser, without
        # substituting fake Android/Compose APIs or claiming type-check coverage.
        syntax = Path(output) / "CheckKotlinSyntax.java"
        syntax.write_text('''
import java.nio.file.*;
import org.jetbrains.kotlin.cli.jvm.compiler.*;
import org.jetbrains.kotlin.config.CompilerConfiguration;
import org.jetbrains.kotlin.psi.KtPsiFactory;
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer;
import org.jetbrains.kotlin.com.intellij.psi.PsiErrorElement;
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil;
class CheckKotlinSyntax {
    public static void main(String[] paths) throws Exception {
        var disposable = Disposer.newDisposable();
        try {
            var env = KotlinCoreEnvironment.createForProduction(disposable,
                new CompilerConfiguration(), EnvironmentConfigFiles.JVM_CONFIG_FILES);
            var factory = new KtPsiFactory(env.getProject(), false);
            int errors = 0;
            for (var name : paths) {
                var path = Path.of(name);
                var file = factory.createFile(path.getFileName().toString(), Files.readString(path));
                for (var error : PsiTreeUtil.findChildrenOfType(file, PsiErrorElement.class)) {
                    System.err.println(name + ": " + error.getErrorDescription() + " at " + error.getTextOffset());
                    errors++;
                }
            }
            if (errors != 0) throw new AssertionError(errors + " Kotlin syntax errors");
            System.out.println("Kotlin syntax: " + paths.length + " files, no parser errors (not type checking)");
        } finally { Disposer.dispose(disposable); }
    }
}
''', encoding="utf-8")
        android_sources = [
            "core/maxai/MaxAiEngine.kt", "core/maxai/MaxAiModels.kt",
            "core/maxai/MaxAiJournal.kt", "ui/viewmodel/MaxAiViewModel.kt",
            "ui/mainscreens/MaxAiScreen.kt", "ui/mainscreens/MaxLiveScreen.kt",
            "ui/mainscreens/MaxAiRuntimeStatus.kt",
            # الملفات التي مسّها إصلاح مسار الإطلاق: تُعرَب بمُحلِّل Kotlin الحقيقي
            # حتى لو تعذّرت الترجمة لغياب Android SDK (لا فحص أنواع، إعراب فقط).
            "ui/navigation/LaunchRoutes.kt", "ui/navigation/MaxNavActions.kt",
            "ui/navigation/MaxDestinations.kt", "ui/navigation/MaxNavGraph.kt",
            "ui/mainscreens/HomeScreen.kt",
            "ui/mainscreens/ControlLayoutModel.kt", "ui/mainscreens/ControlScreen.kt",
            "ui/subscreens/MaxBackupScreen.kt", "ui/subscreens/PermissionsScreen.kt",
            # Max Backup بعد إعادة التصميم: الشاشة الرئيسية والمنتقي، ومكان الأرشيف،
            # والمحرّك والمستند اللذان مسّهما التغيير. إعراب فقط — لا فحص أنواع.
            "ui/subscreens/MaxBackupHubScreen.kt", "ui/subscreens/MaxBackupPickerScreen.kt",
            "ui/util/MaxBackupStorage.kt", "ui/util/MaxBackupEngine.kt",
            "ui/util/MaxBackupModel.kt",
            # المجموعات والجدول: الشاشتان الجديدتان والمُجدوِل الذي ينفّذهما. إعراب فقط.
            "ui/subscreens/MaxBackupScheduleSection.kt", "ui/subscreens/MaxBackupSystemSection.kt",
            "ui/util/MaxBackupScheduler.kt", "ui/util/MaxBackupSystemModel.kt",
            "ui/util/MaxBackupFolders.kt", "ui/util/MaxBackupSchedule.kt",
            "ui/util/MaxBackupFavorites.kt", "ui/subscreens/MaxBackupPickerScreen.kt",
            "ui/util/MaxBackupCounts.kt", "ui/subscreens/MaxBackupRecentItem.kt",
            # مدير الملفات بعد إضافة ترتيب اللوحين والتنقّل المرتبط: الشاشة واللغة ورؤوس
            # الألواح. إعراب فقط — لا فحص أنواع (لا Android SDK في هذه البيئة).
            "ui/subscreens/FileManagerScreen.kt", "ui/component/FilePaneColumn.kt",
            # شريط اللوح وخرائط الرفض والفرز انتقلت من الشاشة إلى مكوّناتها: تُعرَب في
            # مكانها الجديد حتى لا يصير النقل نقلًا أعمى.
            "ui/component/FileManagerPanels.kt",
            "ui/design/MaxViewMenu.kt", "ui/util/FileSearchFilters.kt",
            "ui/util/FilePaneModel.kt",
            # شاشة التطبيقات: إعراب فقط، لا فحص أنواع — يكفي ليمسك إعرابًا مكسورًا في
            # الشاشة التي عُدِّلت حالتها المشتركة.
            "ui/mainscreens/ApplistScreen.kt",
        ]
        subprocess.run([
            "java", "-cp", compiler_cp, str(syntax),
            *map(str, [SOURCE / name for name in android_sources]),
        ], cwd=ROOT, check=True)
        subprocess.run([
            "java", "-cp", compiler_cp, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
            "-no-stdlib", "-no-reflect", "-jvm-target", "17", "-classpath", runtime_cp,
            "-d", output, *map(str, sources),
        ], cwd=ROOT, check=True)
        subprocess.run([
            "java", "-cp", output + os.pathsep + runtime_cp,
            "org.junit.runner.JUnitCore", *tests,
        ], cwd=ROOT, check=True)


if __name__ == "__main__":
    main()
